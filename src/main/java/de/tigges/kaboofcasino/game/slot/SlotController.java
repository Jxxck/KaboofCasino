package de.tigges.kaboofcasino.game.slot;

import de.tigges.kaboofcasino.CasinoContext;
import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.gui.MenuService;
import de.tigges.kaboofcasino.gui.Usability;
import de.tigges.kaboofcasino.machine.Heartbeat;
import de.tigges.kaboofcasino.machine.Machine;
import de.tigges.kaboofcasino.game.GameMenu;
import de.tigges.kaboofcasino.game.MachineController;
import de.tigges.kaboofcasino.util.ItemNames;
import de.tigges.kaboofcasino.util.Log;
import de.tigges.kaboofcasino.wager.ItemAmount;
import de.tigges.kaboofcasino.wager.LedgerCodec;
import de.tigges.kaboofcasino.wager.PlayerAccount;
import de.tigges.kaboofcasino.wager.SettleReason;
import de.tigges.kaboofcasino.wager.WagerEngine;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

/**
 * One slot machine (games.md 6). The round is decided and committed at SPIN (stake out, record in, one tick); the
 * world then plays the mod's spin plan: cues, reels, lever, lights, tease sparks, the result announcement with its
 * bursts, and the settlement (a win at {@code resultTick + 12}: payout into the inventory, overflow owed, then the
 * cosmetic coin shower at the mod's pace; a loss at the result). The machine unlocks when the shower is done and
 * {@code readyTick} passed; the panel disappears 70 ticks after the result.
 *
 * <p>The round never depends on this object: a quit, death, crash or removal settles it through the ledger. One
 * player per machine (the mod's {@code CasinoWagerRouting}): the claim is taken when the menu opens and released
 * when it closes without a spin, or when the spin ends. Thread: MAIN.
 */
public final class SlotController extends MachineController {

    static final String GAME = "slot_machine";
    private static final Particle.DustOptions GOLD_CONFETTI = new Particle.DustOptions(Color.fromRGB(16765288), 1.25f);
    private static final Particle.DustOptions RED_CONFETTI = new Particle.DustOptions(Color.fromRGB(14629707), 1.15f);
    private static final Particle.DustOptions GREEN_CONFETTI = new Particle.DustOptions(Color.fromRGB(3594374),
            1.15f);

    private SlotRig rig;
    private int[] stops;
    private Spin spin;
    /** The plan whose lights still blend back to idle after the spin unlocked. */
    private SlotSpin.Plan lightsPlan;
    private long lightsStart;
    private long panelUntil = -1;

    /** A running spin. */
    private static final class Spin {
        final UUID player;
        final String name;
        final UUID round;
        final ItemAmount stake;
        final List<ItemAmount> payout;
        final SlotSpin.Plan plan;
        final long start;
        final long ends;
        int cue;
        boolean announced;
        boolean settled;
        int coins;

        Spin(UUID player, String name, UUID round, ItemAmount stake, List<ItemAmount> payout, SlotSpin.Plan plan,
             long start, long ends) {
            this.ends = ends;
            this.player = player;
            this.name = name;
            this.round = round;
            this.stake = stake;
            this.payout = payout;
            this.plan = plan;
            this.start = start;
        }
    }

    public SlotController(Machine machine, CasinoContext ctx) {
        super(machine, ctx);
        this.stops = parseStops(machine.record().data());
    }

    static int[] parseStops(String data) {
        int[] out = new int[3];
        if (data.startsWith("stops=")) {
            String[] parts = data.substring(6).split(",");
            for (int i = 0; i < 3 && i < parts.length; i++) {
                try {
                    out[i] = Math.floorMod(Integer.parseInt(parts[i].strip()), 8);
                } catch (NumberFormatException e) {
                    out[i] = 0;
                }
            }
        }
        return out;
    }

    // ---- MachineGame -----------------------------------------------------------------------------------------------

    @Override
    public void spawn() {
        World world = world();
        if (world == null) {
            return;
        }
        if (rig == null) {
            rig = new SlotRig(machine.record(), world);
        }
        rig.spawn(stops, idleColors(world.getGameTime()));
    }

    @Override
    public void despawn() {
        if (rig != null) {
            rig.despawn();
        }
        closeMenus();
    }

    @Override
    protected boolean running() {
        return spin != null;
    }

    @Override
    protected Msg busyMessage() {
        return Msg.SLOT_BUSY;
    }

    @Override
    protected GameMenu newMenu(Player player) {
        return new SlotMenu(player.getUniqueId(), SlotMenu.title(ctx, stops), this, ctx);
    }

    @Override
    public boolean ownsRound(UUID roundId) {
        return spin != null && !spin.settled && spin.round.equals(roundId)
                && heartbeat.owns(ctx.clock().getAsLong(), spin.ends);
    }

    @Override
    public void checkRig() {
        if (rig != null && rig.spawned() && rig.broken()) {
            Log.get().info("slot machine #{}: rig entities vanished, respawning", machine.record().number());
            spawn();
        }
    }

    @Override
    public void removed() {
        if (spin != null && !spin.settled) {
            settle(SettleReason.MACHINE_GONE);
        }
        spin = null;
        claimant = null;
        closeMenus();
    }

    // ---- SPIN ------------------------------------------------------------------------------------------------------

    /** The SPIN button (commit click): decide, commit (stake out + record in, one tick), start the animation. */
    void spin(Player player, SlotMenu menu) {
        Messages m = ctx.messages().get();
        if (!mayCommit(player)) {
            return;
        }
        ItemAmount stake = menu.selection();
        if (stake == null) {
            m.actionBar(player, Msg.SLOT_NO_BET);
            return;
        }
        int[] next = SlotGame.draw(ctx.config().get().slot().winChance(), ctx.random()::nextInt);
        SlotGame.Result result = SlotGame.evaluate(next);
        List<ItemAmount> payout = result.multiplier() > 0
                ? List.of(new ItemAmount(stake.item(), stake.count() * result.multiplier())) : List.of();
        long seed = ctx.random().nextLong();
        SlotSpin.Plan plan = SlotSpin.plan(stops, next, seed);
        long now = Bukkit.getCurrentTick();
        long ends = ctx.clock().getAsLong() + settleTick(plan, payout) * 50L;
        String detail = "stops=" + next[0] + "," + next[1] + "," + next[2] + " x" + result.multiplier();
        List<UUID> rounds = commit(player, List.of(new WagerEngine.Request(machine.id(), GAME, stake, payout, ends,
                detail)), Msg.SLOT_NO_BET);
        if (rounds == null) {
            menu.render();
            return;
        }
        spin = new Spin(player.getUniqueId(), player.getName(), rounds.getFirst(), stake, payout, plan, now, ends);
        lightsPlan = plan;
        lightsStart = now;
        stops = next;
        ctx.machines().saveData(machine, "stops=" + next[0] + "," + next[1] + "," + next[2]);
        menu.clearSelection();
        closeMenus();
        if (rig != null) {
            rig.panel(SlotPanel.render(m, ctx.layout(), player.getUniqueId(), player.getName(), stake, null, next));
        }
        panelUntil = -1;
        Log.get().info("{} spins slot machine #{} with {}: {}", player.getName(), machine.record().number(), stake,
                detail);
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
        lights(world, now);
        if (panelUntil >= 0 && now >= panelUntil) {
            rig.hidePanel();
            panelUntil = -1;
        }
    }

    private void runSpin(World world, long now) {
        Spin s = spin;
        SlotSpin.Plan plan = s.plan;
        long elapsed = now - s.start;
        List<SlotSpin.Cue> cues = plan.cues();
        while (s.cue < cues.size() && cues.get(s.cue).tick() <= elapsed) {
            cue(world, cues.get(s.cue++));
        }
        if (plan.tease() && elapsed % 3 == 0) {
            double speed = plan.speed(2, elapsed);
            if (speed > 0 && speed < 0.55) {
                particle(world, Particle.END_ROD, SlotGeometry.WINDOW_FRONT, 3, 0.38, 0.22, 0.04, 0.02, null);
            }
        }
        if (elapsed <= plan.land(2) + 14) {
            // every tick while a reel is fast (face swaps jump), else every 2 ticks with interpolation
            boolean fast = plan.speed(0, elapsed) > 0.3 || plan.speed(1, elapsed) > 0.3 || plan.speed(2, elapsed) > 0.3;
            if (fast) {
                rig.reels(plan, elapsed + 1, 1);
            } else if (elapsed % 2 == 0) {
                rig.reels(plan, elapsed + 2, 3);
            }
        }
        if (elapsed <= 28) {
            rig.lever(plan.lever(elapsed + 1), 1);
        }
        if (!s.announced && elapsed >= plan.resultTick()) {
            s.announced = true;
            rig.panel(SlotPanel.render(ctx.messages().get(), ctx.layout(), s.player, s.name, s.stake, plan.result(),
                    plan.stops()));
            panelUntil = s.start + plan.resultTick() + SlotSpin.PANEL_AFTER_RESULT;
            if (elapsed - plan.resultTick() <= 10) {
                announce(world, plan.result().kind());
            }
        }
        if (s.announced) {
            afterburst(world, plan.result().kind(), elapsed - plan.resultTick());
        }
        if (!s.settled && elapsed >= settleTick(plan, s.payout)) {
            settle(SettleReason.NORMAL);
        }
        if (s.settled && s.coins > 0) {
            int interval = s.coins > 48 ? 1 : s.coins > 16 ? 2 : 3;
            if (now % interval == 0) {
                s.coins--;
                rig.coin(PlayerAccount.material(s.payout.getFirst().item()), coinTarget(world, s.player), now);
                if (interval > 1 || now % 2 == 0) {
                    sound(world, "slot_payout", SlotGeometry.MOUTH_SOUND, 0.5f,
                            0.9f + ctx.random().nextFloat() * 0.25f);
                }
            }
        }
        if (s.settled && s.coins == 0 && elapsed >= plan.readyTick()) {
            spin = null;
            claimant = null;
        }
    }

    /**
     * When the round settles: a win at {@code resultTick + 12} (where the mod starts ejecting), a loss at the result
     * (nothing to pay; the mod unlocks at {@code readyTick} = result).
     */
    static int settleTick(SlotSpin.Plan plan, List<ItemAmount> payout) {
        return payout.isEmpty() ? plan.resultTick() : plan.payoutTick();
    }

    /** Settles the spin's round now and queues the coin shower. */
    private void settle(SettleReason reason) {
        Spin s = spin;
        if (s == null || s.settled) {
            return;
        }
        s.settled = true;
        settleRound(s.player, s.name, s.round, reason);
        s.coins = stacks(s.payout);
    }

    private void lights(World world, long now) {
        if (lightsPlan != null) {
            double e = now - lightsStart;
            if (e % 2 == 0) {
                double idleTime = idleTime(world.getGameTime());
                float[] colors = new float[16];
                double since = e - lightsPlan.endTick();
                colors[0] = (float) IdleLights.afterGame(lightsPlan.lamp(e), IdleLights.beacon(idleTime), since);
                for (int i = 0; i < 15; i++) {
                    colors[i + 1] = (float) IdleLights.afterGame(lightsPlan.bulb(i, e),
                            IdleLights.bulb(i, 15, idleTime), since);
                }
                rig.lights(colors);
                rig.drumFlash(lightsPlan.flash(e) < 1.0);
            }
            if (e > lightsPlan.endTick() + 14) {
                lightsPlan = null;
                rig.drumFlash(false);
            }
            return;
        }
        if (idleLightsDue(world, ctx.config().get().slot(), now)) {
            rig.lights(idleColors(world.getGameTime()));
        }
    }

    private double idleTime(long gameTime) {
        return IdleLights.clock(gameTime, machine.anchor().x(), machine.anchor().y(), machine.anchor().z());
    }

    private float[] idleColors(long gameTime) {
        double t = idleTime(gameTime);
        float[] colors = new float[16];
        colors[0] = (float) IdleLights.beacon(t);
        for (int i = 0; i < 15; i++) {
            colors[i + 1] = (float) IdleLights.bulb(i, 15, t);
        }
        return colors;
    }

    // ---- sounds and particles (positions: SlotMachineBlockEntity.world -> SlotMachineLayout.toBlock) --------------

    private void cue(World world, SlotSpin.Cue cue) {
        double drumX = cue.reel() >= 0 ? SlotGeometry.drumCentreX(cue.reel()) : 8.0;
        switch (cue.sound()) {
            case LEVER -> sound(world, "slot_lever", SlotGeometry.LEVER_SOUND, cue.volume(), cue.pitch());
            case WHIRR -> sound(world, "slot_whirr", SlotGeometry.WINDOW_SOUND, cue.volume(), cue.pitch());
            case CLICK -> sound(world, "slot_click", new double[] {drumX, 25.75, 14.5}, cue.volume(), cue.pitch());
            case STOP -> {
                sound(world, "slot_stop", new double[] {drumX, 25.75, 14.5}, cue.volume(), cue.pitch());
                particle(world, Particle.CRIT, new double[] {drumX, 25.75, 15.5}, 5, 0.12, 0.1, 0.04, 0.12, null);
            }
        }
    }

    private void announce(World world, SlotGame.Kind kind) {
        switch (kind) {
            case JACKPOT -> {
                sound(world, "slot_jackpot", SlotGeometry.WINDOW_SOUND, 1.5f, 1.0f);
                burst(world, 1.0, true);
            }
            case BIG -> {
                sound(world, "slot_win", SlotGeometry.WINDOW_SOUND, 1.3f, 1.0f);
                burst(world, 0.8, false);
            }
            case WIN -> {
                sound(world, "slot_win", SlotGeometry.WINDOW_SOUND, 1.05f, 1.0f);
                burst(world, 0.5, false);
            }
            case PUSH -> {
                sound(world, "slot_push", SlotGeometry.WINDOW_SOUND, 0.9f, 1.0f);
                particle(world, Particle.END_ROD, SlotGeometry.PUSH_POINT, 8, 0.25, 0.1, 0.1, 0.03, null);
            }
            case NONE -> {
                sound(world, "slot_lose", SlotGeometry.WINDOW_SOUND, 0.95f, 1.0f);
                particle(world, Particle.SMOKE, SlotGeometry.LOSE_LAMP, 6, 0.12, 0.05, 0.12, 0.01, null);
                particle(world, Particle.ELECTRIC_SPARK, SlotGeometry.WINDOW_FRONT, 6, 0.4, 0.2, 0.05, 0.1, null);
            }
        }
    }

    private void afterburst(World world, SlotGame.Kind kind, long sinceResult) {
        if (kind == SlotGame.Kind.JACKPOT) {
            if (sinceResult >= 5 && sinceResult <= 35 && sinceResult % 5 == 0) {
                burst(world, 0.8 - 0.1 * (sinceResult / 5 - 1), false);
            }
        } else if (kind == SlotGame.Kind.BIG) {
            if (sinceResult == 7 || sinceResult == 14) {
                burst(world, 0.5, false);
            }
        } else if (kind == SlotGame.Kind.WIN && sinceResult == 8) {
            burst(world, 0.35, false);
        }
    }

    private void burst(World world, double strength, boolean jackpot) {
        int n = (int) Math.round(26.0 * strength);
        ItemStack coin = ItemStack.of(Material.GOLD_NUGGET);
        particle(world, Particle.ITEM, SlotGeometry.BURST_MOUTH, n, 0.22, 0.1, 0.12, 0.2, coin);
        particle(world, Particle.DUST, SlotGeometry.BURST_WINDOW, n, 0.55, 0.45, 0.25, 0.16, GOLD_CONFETTI);
        particle(world, Particle.DUST, SlotGeometry.BURST_WINDOW, n, 0.55, 0.45, 0.25, 0.16, RED_CONFETTI);
        particle(world, Particle.DUST, SlotGeometry.BURST_WINDOW, n, 0.55, 0.45, 0.25, 0.16, GREEN_CONFETTI);
        particle(world, Particle.FIREWORK, SlotGeometry.BURST_LAMP, (int) Math.round(22.0 * strength), 0.25, 0.2,
                0.25, 0.22, null);
        particle(world, Particle.END_ROD, SlotGeometry.BURST_LAMP, (int) Math.round(14.0 * strength), 0.25, 0.2,
                0.25, 0.16, null);
        if (jackpot) {
            particle(world, Particle.TOTEM_OF_UNDYING, SlotGeometry.BURST_WINDOW, 36, 0.5, 0.5, 0.3, 0.35, null);
            Location lamp = at(world, SlotGeometry.BURST_LAMP).subtract(0, 0.3, 0);
            world.spawnParticle(Particle.ITEM, lamp, 30, 0.3, 0.1, 0.3, 0.28, coin);
        }
    }

}
