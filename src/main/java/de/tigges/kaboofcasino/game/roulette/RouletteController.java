package de.tigges.kaboofcasino.game.roulette;

import de.tigges.kaboofcasino.CasinoContext;
import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.game.GameMenu;
import de.tigges.kaboofcasino.game.MachineController;
import de.tigges.kaboofcasino.game.StakeMenu;
import de.tigges.kaboofcasino.machine.Machine;
import de.tigges.kaboofcasino.util.Log;
import de.tigges.kaboofcasino.wager.ItemAmount;
import de.tigges.kaboofcasino.wager.PlayerAccount;
import de.tigges.kaboofcasino.wager.SettleReason;
import de.tigges.kaboofcasino.wager.WagerEngine;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Runtime of one roulette table ({@code RouletteBlockEntity}): the menu with nine bet slots (three per colour, one
 * colour per spin), the spin (draw the pocket, commit one record per filled slot in one tick, play the mod's 160-tick
 * plan), the result at tick 160 (the resting frame is saved, every record settles, confetti and win/lose sound), the
 * payout stacks every third game tick from the next tick on, the unlock once all are out, and the floating panel
 * until 20 ticks after the result. One player per spin, as in the mod (the stake owner). Thread: MAIN.
 */
public final class RouletteController extends MachineController {

    static final String GAME = "roulette";
    /** Sounds play at the origin block ({@code playSound(pos)}: its centre). */
    static final double[] ORIGIN = {8, 8, 8};
    /** Confetti at origin + (1, 1.1, 1); payout stacks leave at origin + (1, 1, 1). */
    static final double[] CELEBRATE = {16, 17.6, 16};
    static final double[] MOUTH = {16, 16, 16};
    /** The panel hides this many ticks after the result ({@code displayTicks = 20}). */
    static final int PANEL_AFTER_RESULT = 20;
    private static final Particle.DustOptions RED_CONFETTI = new Particle.DustOptions(Color.fromRGB(0xDF3B4B), 1.15f);
    private static final Particle.DustOptions GOLD_CONFETTI = new Particle.DustOptions(Color.fromRGB(0xFFD268), 1.25f);
    private static final Particle.DustOptions GREEN_CONFETTI = new Particle.DustOptions(Color.fromRGB(0x36D886),
            1.15f);

    private RouletteRig rig;
    private int frame;
    private Spin spin;
    private long panelUntil = -1;

    /** One filled bet slot of a running spin. */
    private record Slip(int slot, ItemAmount stake, List<ItemAmount> payout, UUID round) {
    }

    /** A running spin. */
    private static final class Spin {
        final UUID player;
        final String name;
        final int colour;
        final int pocket;
        final List<Slip> slips;
        final RouletteSpin.Plan plan;
        final long start;
        final long ends;
        boolean decided;
        final ArrayDeque<String> coins = new ArrayDeque<>();

        Spin(UUID player, String name, int colour, int pocket, List<Slip> slips, RouletteSpin.Plan plan, long start,
             long ends) {
            this.player = player;
            this.name = name;
            this.colour = colour;
            this.pocket = pocket;
            this.slips = slips;
            this.plan = plan;
            this.start = start;
            this.ends = ends;
        }
    }

    public RouletteController(Machine machine, CasinoContext ctx) {
        super(machine, ctx);
        this.frame = parseFrame(machine.record().data());
    }

    static int parseFrame(String data) {
        if (data.startsWith("frame=")) {
            try {
                return Math.floorMod(Integer.parseInt(data.substring(6).strip()), RouletteSpin.POCKETS);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    // ---- MachineGame -----------------------------------------------------------------------------------------------

    @Override
    protected boolean running() {
        return spin != null;
    }

    @Override
    protected Msg busyMessage() {
        return Msg.ROULETTE_BUSY;
    }

    /** {@code RouletteMenu.stillValid}: block interaction range + 1.5 from the origin block. */
    @Override
    protected double menuReach() {
        return 1.5;
    }

    @Override
    protected GameMenu newMenu(Player player) {
        return new RouletteMenu(player.getUniqueId(), RouletteMenu.title(ctx), this, ctx);
    }

    @Override
    public void spawn() {
        World world = world();
        if (world == null) {
            return;
        }
        if (rig == null) {
            rig = new RouletteRig(machine.record(), world);
        }
        rig.spawn(frame);
    }

    @Override
    public void despawn() {
        if (rig != null) {
            rig.despawn();
        }
        closeMenus();
    }

    @Override
    public boolean ownsRound(UUID roundId) {
        if (spin == null || spin.decided || !heartbeat.owns(ctx.clock().getAsLong(), spin.ends)) {
            return false;
        }
        return spin.slips.stream().anyMatch(s -> s.round().equals(roundId));
    }

    @Override
    public void checkRig() {
        if (rig != null && rig.spawned() && rig.broken()) {
            Log.get().info("roulette #{}: rig entities vanished, respawning", machine.record().number());
            spawn();
        }
    }

    @Override
    public void removed() {
        if (spin != null && !spin.decided) {
            spin.decided = true;
            for (Slip s : spin.slips) {
                settleRound(spin.player, spin.name, s.round(), SettleReason.MACHINE_GONE);
            }
        }
        spin = null;
        claimant = null;
        closeMenus();
    }

    // ---- SPIN ------------------------------------------------------------------------------------------------------

    /** The SPIN button: draw, commit every filled slot (one tick), start the 160-tick spin. */
    void spin(Player player, RouletteMenu menu) {
        Messages m = ctx.messages().get();
        if (!mayCommit(player)) {
            return;
        }
        List<StakeMenu.Stake> stakes = menu.stakes();
        if (stakes.isEmpty()) {
            m.actionBar(player, Msg.ROULETTE_NO_BET);
            return;
        }
        int colour = stakes.getFirst().slot() % RouletteGame.COLOURS;
        if (stakes.stream().anyMatch(s -> s.slot() % RouletteGame.COLOURS != colour)) {
            m.actionBar(player, Msg.ROULETTE_SEVERAL);
            return;
        }
        int pocket = RouletteGame.draw(colour, ctx.config().get().roulette().winChance(), ctx.random()::nextInt);
        RouletteSpin.Plan plan = RouletteSpin.plan(frame, pocket);
        long ends = ctx.clock().getAsLong() + plan.resultTick() * 50L;
        List<WagerEngine.Request> requests = new ArrayList<>();
        List<List<ItemAmount>> payouts = new ArrayList<>();
        for (StakeMenu.Stake s : stakes) {
            long n = RouletteGame.payout(colour, s.amount().count(), pocket);
            List<ItemAmount> payout = n > 0 ? List.of(new ItemAmount(s.amount().item(), n)) : List.of();
            payouts.add(payout);
            requests.add(new WagerEngine.Request(machine.id(), GAME, s.amount(), payout, ends,
                    "pocket=" + pocket + " colour=" + colour + " slot=" + s.slot()));
        }
        List<UUID> rounds = commit(player, requests, Msg.ROULETTE_NO_BET);
        if (rounds == null) {
            menu.render();
            return;
        }
        List<Slip> slips = new ArrayList<>();
        for (int i = 0; i < stakes.size(); i++) {
            slips.add(new Slip(stakes.get(i).slot(), stakes.get(i).amount(), payouts.get(i), rounds.get(i)));
        }
        long now = Bukkit.getCurrentTick();
        spin = new Spin(player.getUniqueId(), player.getName(), colour, pocket, slips, plan, now, ends);
        menu.clearStakes();
        closeMenus();
        World world = world();
        if (world != null) {
            sound(world, "start", ORIGIN, 1.0f, 1.0f);
        }
        if (rig != null) {
            rig.panel(RoulettePanel.render(ctx.messages().get(), player.getUniqueId(), player.getName(), colour,
                    slips.stream().map(Slip::stake).toList()));
        }
        panelUntil = -1;
        Log.get().info("{} spins roulette #{} with {} on colour {}: pocket {}", player.getName(),
                machine.record().number(), stakes.stream().map(s -> s.amount().toString()).toList(), colour, pocket);
    }

    // ---- ticking ---------------------------------------------------------------------------------------------------

    @Override
    public void tick(long now) {
        heartbeat.beat(ctx.clock().getAsLong());
        World world = world();
        if (world == null || rig == null) {
            return;
        }
        rig.tickCoins(now);
        if (spin != null) {
            runSpin(world, now);
        }
        if (panelUntil >= 0 && now >= panelUntil) {
            rig.hidePanel();
            panelUntil = -1;
        }
    }

    private void runSpin(World world, long now) {
        Spin s = spin;
        RouletteSpin.Plan plan = s.plan;
        long elapsed = now - s.start;
        if (!s.decided) {
            // the wheel's tick: every change of floor(phase x 3), at most one per tick
            if (Math.floor(plan.phase(elapsed - 1.0) * 3.0) != Math.floor(plan.phase(elapsed) * 3.0)) {
                sound(world, "tick", ORIGIN, 0.44f, (float) (0.8 + Math.min(0.45, plan.speed(elapsed) * 0.4)));
            }
            if (elapsed % 2 == 0) {
                rig.rotor(plan.phase(elapsed + 2), 3);
            }
            rig.ball(plan, elapsed + 1, 1);
        }
        if (!s.decided && elapsed >= plan.resultTick()) {
            s.decided = true;
            frame = plan.finalFrame();
            ctx.machines().saveData(machine, "frame=" + frame);
            rig.rotor(frame, 1);
            rig.ball(plan, plan.resultTick() + 40, 1);
            boolean paid = false;
            for (Slip slip : s.slips) {
                settleRound(s.player, s.name, slip.round(), SettleReason.NORMAL);
                queueCoins(s.coins, slip.payout());
                paid |= !slip.payout().isEmpty();
            }
            if (paid) {
                celebrate(world);
            }
            sound(world, paid ? "win" : "lose", ORIGIN, paid ? 1.2f : 0.95f, 1.0f);
            panelUntil = s.start + plan.resultTick() + PANEL_AFTER_RESULT;
            return;
        }
        if (s.decided) {
            if (!s.coins.isEmpty()) {
                if (now % 3 == 0) {
                    rig.coin(PlayerAccount.material(s.coins.poll()), coinTarget(world, s.player), now);
                    sound(world, "payout", ORIGIN, 0.43f, 0.95f + ctx.random().nextFloat() * 0.2f);
                }
            } else {
                spin = null;
                claimant = null;
            }
        }
    }

    /** The table centre one block up (coin target, proximity). */
    @Override
    protected Location centre(World world) {
        return new Location(world, machine.anchor().x() + 1.0, machine.anchor().y() + 1.0,
                machine.anchor().z() + 1.0);
    }

    private void celebrate(World world) {
        particle(world, Particle.DUST, CELEBRATE, 18, 0.52, 0.16, 0.52, 0.18, RED_CONFETTI);
        particle(world, Particle.DUST, CELEBRATE, 18, 0.52, 0.16, 0.52, 0.18, GOLD_CONFETTI);
        particle(world, Particle.DUST, CELEBRATE, 18, 0.52, 0.16, 0.52, 0.18, GREEN_CONFETTI);
        particle(world, Particle.FIREWORK, CELEBRATE, 20, 0.42, 0.2, 0.42, 0.2, null);
    }

    /** Where the coins leave the table (origin + (1, 1, 1)). */
    Location mouth(World world) {
        return at(world, MOUTH);
    }
}
