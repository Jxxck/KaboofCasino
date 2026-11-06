package de.tigges.kaboofcasino.game.wheel;

import de.tigges.kaboofcasino.CasinoContext;
import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.game.FairPayout;
import de.tigges.kaboofcasino.game.GameMenu;
import de.tigges.kaboofcasino.game.MachineController;
import de.tigges.kaboofcasino.game.Odds;
import de.tigges.kaboofcasino.game.PanelText;
import de.tigges.kaboofcasino.game.StakeMenu;
import de.tigges.kaboofcasino.game.slot.IdleLights;
import de.tigges.kaboofcasino.machine.Machine;
import de.tigges.kaboofcasino.util.Log;
import de.tigges.kaboofcasino.wager.ItemAmount;
import de.tigges.kaboofcasino.wager.PlayerAccount;
import de.tigges.kaboofcasino.wager.SettleReason;
import de.tigges.kaboofcasino.wager.WagerEngine;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.IntUnaryOperator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Runtime of one fortune wheel ({@code FortuneWheelBlockEntity}): the menu with three bet slots, the spin (decide,
 * commit one record per filled slot in one tick, play the mod's spin plan), the settlement per slot (a paying slot
 * at {@code resultTick + 12}, where the mod starts ejecting; a slot paying nothing at the result), the coin shower at
 * the mod's pace and the unlock once everything is out and {@code readyTick} passed. The floating panel shows the
 * stakes until 70 ticks after the result. Thread: MAIN.
 */
public final class WheelController extends MachineController {

    static final String GAME = "fortune_wheel";
    static final double[] AXLE = {8, 11, 14};
    static final double[] POINTER = {8, 26.6, 14};
    static final double[] SPARK = {8, 25.6, 15};
    static final double[] MOUTH = {8, -12, 14.5};
    private static final Particle.DustOptions GOLD_CONFETTI = new Particle.DustOptions(Color.fromRGB(0xFFD168), 1.25f);
    private static final Particle.DustOptions RED_CONFETTI = new Particle.DustOptions(Color.fromRGB(0xDF3B4B), 1.15f);
    private static final Particle.DustOptions GREEN_CONFETTI = new Particle.DustOptions(Color.fromRGB(0x36D886),
            1.15f);
    /** Result line colours by kind NONE, PARTIAL, PUSH, WIN, JACKPOT ({@code FortuneWheelRenderer}). */
    private static final int[] RESULT_COLOR = {0xE79882, 0xFFAA00, 0xF2CC72, 0x88CF86, 0xFFAA00};

    /** {@code CasinoOdds.WHEEL}: win = more than the stake back (quarters > 4), tickets = the segment weights. */
    public static final Odds.Pool POOL = Odds.pool(FortuneWheelGame.SEGMENTS, s -> FortuneWheelGame.quarters(s) > 4,
            FortuneWheelGame::weight, FortuneWheelGame::quarters);

    private WheelRig rig;
    private int stop;
    private Spin spin;
    /** The plan whose lights still blend back to idle after the spin unlocked. */
    private FortuneWheelSpin.Plan lightsPlan;
    private long lightsStart;
    private long panelUntil = -1;

    /** One filled bet slot of a running spin. */
    private static final class Slip {
        final int slot;
        final ItemAmount stake;
        final List<ItemAmount> payout;
        final UUID round;
        final int settleTick;
        boolean settled;

        Slip(int slot, ItemAmount stake, List<ItemAmount> payout, UUID round, int settleTick) {
            this.slot = slot;
            this.stake = stake;
            this.payout = payout;
            this.round = round;
            this.settleTick = settleTick;
        }
    }

    /** A running spin. */
    private static final class Spin {
        final UUID player;
        final String name;
        final List<Slip> slips;
        final FortuneWheelSpin.Plan plan;
        final FortuneWheelGame.Kind kind;
        final long start;
        final long ends;
        int cue;
        boolean announced;
        /** One entry per won stack still to fly (the stack's item). */
        final java.util.ArrayDeque<String> coins = new java.util.ArrayDeque<>();

        Spin(UUID player, String name, List<Slip> slips, FortuneWheelSpin.Plan plan, FortuneWheelGame.Kind kind,
             long start, long ends) {
            this.player = player;
            this.name = name;
            this.slips = slips;
            this.plan = plan;
            this.kind = kind;
            this.start = start;
            this.ends = ends;
        }

        boolean allSettled() {
            return slips.stream().allMatch(s -> s.settled);
        }
    }

    public WheelController(Machine machine, CasinoContext ctx) {
        super(machine, ctx);
        this.stop = parseStop(machine.record().data());
    }

    static int parseStop(String data) {
        if (data.startsWith("stop=")) {
            try {
                return Math.floorMod(Integer.parseInt(data.substring(5).strip()), FortuneWheelGame.SEGMENTS);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    /** The stop segment: natural odds = the weight table ({@code nextInt(64)}), else the mod's forced pools. */
    public static int draw(int winChance, IntUnaryOperator nextInt) {
        if (winChance >= 0) {
            return Odds.draw(POOL, winChance, 100, nextInt);
        }
        return FortuneWheelGame.segmentForRoll(nextInt.applyAsInt(FortuneWheelGame.TOTAL_WEIGHT));
    }

    /** What slot {@code slot} with {@code count} items pays at {@code quarters} ({@code CasinoExchange} default). */
    public static int payout(long seed, int slot, int count, int quarters) {
        return FortuneWheelGame.payout(count, quarters, FairPayout.roll(seed, slot, 4));
    }

    /** {@code FortuneWheelBlockEntity.resultKind}: a partial result that pays nothing in any slot shows as NONE. */
    static FortuneWheelGame.Kind resultKind(int quarters, List<Integer> paid) {
        FortuneWheelGame.Kind kind = FortuneWheelGame.kindOf(quarters);
        if (kind == FortuneWheelGame.Kind.PARTIAL && paid.stream().allMatch(p -> p == 0)) {
            return FortuneWheelGame.Kind.NONE;
        }
        return kind;
    }

    /** When a slot settles: paid slots where the mod starts ejecting, the others at the result. */
    static int settleTick(FortuneWheelSpin.Plan plan, boolean pays) {
        return pays ? plan.resultTick() + 12 : plan.resultTick();
    }

    // ---- MachineGame -----------------------------------------------------------------------------------------------

    @Override
    protected boolean running() {
        return spin != null;
    }

    @Override
    protected Msg busyMessage() {
        return Msg.WHEEL_BUSY;
    }

    @Override
    protected GameMenu newMenu(Player player) {
        return new WheelMenu(player.getUniqueId(), WheelMenu.title(ctx, stop), this, ctx);
    }

    @Override
    public void spawn() {
        World world = world();
        if (world == null) {
            return;
        }
        if (rig == null) {
            rig = new WheelRig(machine.record(), world);
        }
        rig.spawn(stop, idleLevels(world.getGameTime()));
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
        if (spin == null || !heartbeat.owns(ctx.clock().getAsLong(), spin.ends)) {
            return false;
        }
        for (Slip s : spin.slips) {
            if (!s.settled && s.round.equals(roundId)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void checkRig() {
        if (rig != null && rig.spawned() && rig.broken()) {
            Log.get().info("fortune wheel #{}: rig entities vanished, respawning", machine.record().number());
            spawn();
        }
    }

    @Override
    public void removed() {
        if (spin != null) {
            for (Slip s : spin.slips) {
                if (!s.settled) {
                    s.settled = true;
                    settleRound(spin.player, spin.name, s.round, SettleReason.MACHINE_GONE);
                }
            }
        }
        spin = null;
        claimant = null;
        closeMenus();
    }

    // ---- SPIN ------------------------------------------------------------------------------------------------------

    /** The SPIN button: decide, commit every filled slot (one tick), start the animation. */
    void spin(Player player, WheelMenu menu) {
        Messages m = ctx.messages().get();
        if (!mayCommit(player)) {
            return;
        }
        List<StakeMenu.Stake> stakes = menu.stakes();
        if (stakes.isEmpty()) {
            m.actionBar(player, Msg.WHEEL_NO_BET);
            return;
        }
        int next = draw(ctx.config().get().wheel().winChance(), ctx.random()::nextInt);
        int quarters = FortuneWheelGame.quarters(next);
        long seed = ctx.random().nextLong();
        FortuneWheelSpin.Plan plan = FortuneWheelSpin.plan(stop, next, seed);
        long nowMillis = ctx.clock().getAsLong();
        List<WagerEngine.Request> requests = new ArrayList<>();
        List<List<ItemAmount>> payouts = new ArrayList<>();
        List<Integer> paid = new ArrayList<>();
        long ends = nowMillis;
        for (StakeMenu.Stake s : stakes) {
            int n = payout(seed, s.slot(), (int) s.amount().count(), quarters);
            paid.add(n);
            List<ItemAmount> payout = n > 0 ? List.of(new ItemAmount(s.amount().item(), n)) : List.of();
            payouts.add(payout);
            long slipEnds = nowMillis + settleTick(plan, n > 0) * 50L;
            ends = Math.max(ends, slipEnds);
            requests.add(new WagerEngine.Request(machine.id(), GAME, s.amount(), payout, slipEnds,
                    "seg=" + next + " " + PanelText.multiplier(quarters) + " slot=" + s.slot()));
        }
        List<UUID> rounds = commit(player, requests, Msg.WHEEL_NO_BET);
        if (rounds == null) {
            menu.render();
            return;
        }
        List<Slip> slips = new ArrayList<>();
        for (int i = 0; i < stakes.size(); i++) {
            slips.add(new Slip(stakes.get(i).slot(), stakes.get(i).amount(), payouts.get(i), rounds.get(i),
                    settleTick(plan, !payouts.get(i).isEmpty())));
        }
        long now = Bukkit.getCurrentTick();
        spin = new Spin(player.getUniqueId(), player.getName(), slips, plan, resultKind(quarters, paid), now, ends);
        lightsPlan = plan;
        lightsStart = now;
        stop = next;
        ctx.machines().saveData(machine, "stop=" + next);
        menu.clearStakes();
        closeMenus();
        if (rig != null) {
            rig.panel(panel(spin, false));
        }
        panelUntil = -1;
        Log.get().info("{} spins fortune wheel #{} with {}: segment {} {}", player.getName(),
                machine.record().number(), stakes.stream().map(s -> s.amount().toString()).toList(), next,
                PanelText.multiplier(quarters));
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
        FortuneWheelSpin.Plan plan = s.plan;
        long elapsed = now - s.start;
        List<FortuneWheelSpin.Cue> cues = plan.cues();
        while (s.cue < cues.size() && cues.get(s.cue).tick() <= elapsed) {
            cue(world, cues.get(s.cue++));
        }
        sparks(world, plan, elapsed);
        if (elapsed <= plan.landTick() + FortuneWheelSpin.RECOIL_TICKS + 2) {
            rig.plate(plan.turn(elapsed + 1), plan.speed(elapsed + 1), 1);
        }
        if (elapsed <= plan.landTick() + 60) {
            rig.pennant(plan.flapper(elapsed + 1), 1);
        }
        if (!s.announced && elapsed >= plan.resultTick()) {
            s.announced = true;
            rig.panel(panel(s, true));
            panelUntil = s.start + plan.resultTick() + 70;
            if (elapsed - plan.resultTick() <= 10) {
                announce(world, s.kind, plan.result().quarters());
            }
        }
        if (s.announced) {
            afterburst(world, s.kind, elapsed - plan.resultTick());
        }
        for (Slip slip : s.slips) {
            if (!slip.settled && elapsed >= slip.settleTick) {
                slip.settled = true;
                settleRound(s.player, s.name, slip.round, SettleReason.NORMAL);
                queueCoins(s.coins, slip.payout);
            }
        }
        if (!s.coins.isEmpty()) {
            int interval = s.coins.size() > 48 ? 1 : s.coins.size() > 16 ? 2 : 3;
            if (now % interval == 0) {
                rig.coin(PlayerAccount.material(s.coins.poll()), coinTarget(world, s.player), now);
                if (interval > 1 || now % 2 == 0) {
                    sound(world, "wheel_payout", MOUTH, 0.5f, 0.9f + ctx.random().nextFloat() * 0.25f);
                }
            }
        }
        if (s.allSettled() && s.coins.isEmpty() && elapsed >= plan.readyTick()) {
            spin = null;
            claimant = null;
        }
    }

    /** The panel: player, "EINSATZ", the stakes; from the result on the result line. */
    private Component panel(Spin s, boolean decided) {
        Messages m = ctx.messages().get();
        Component nl = Component.newline();
        var text = Component.text().append(PanelText.header(s.player, s.name)).append(nl)
                .append(m.render(Msg.PANEL_BET).color(PanelText.TITLE)).append(nl)
                .append(PanelText.stakes(s.slips.stream().map(x -> x.stake).toList()));
        if (decided) {
            text.append(nl).append(resultLine(m, s.kind, s.plan.result().quarters()));
        }
        return text.build();
    }

    static Component resultLine(Messages m, FortuneWheelGame.Kind kind, int quarters) {
        Component number = Component.text(PanelText.multiplier(quarters),
                TextColor.color(kind == FortuneWheelGame.Kind.WIN ? 0xFFFF55 : 0xFFFFFF));
        Component line = switch (kind) {
            case NONE -> m.render(Msg.PANEL_LOSE);
            case PARTIAL -> m.render(Msg.PANEL_PARTIAL, Messages.component("mult", number));
            case PUSH -> m.render(Msg.PANEL_PUSH);
            case WIN -> m.render(Msg.PANEL_WIN, Messages.component("mult", number));
            case JACKPOT -> m.render(Msg.PANEL_JACKPOT, Messages.component("mult", number));
        };
        return line.colorIfAbsent(TextColor.color(RESULT_COLOR[kind.ordinal()]))
                .decoration(TextDecoration.BOLD, true);
    }

    // ---- lights ----------------------------------------------------------------------------------------------------

    private void lights(World world, long now) {
        if (lightsPlan != null) {
            double e = now - lightsStart;
            if (e % 2 == 0) {
                double idle = idleTime(world.getGameTime());
                double since = e - lightsPlan.endTick();
                float[] levels = new float[FortuneWheelSpin.BULBS];
                for (int i = 0; i < levels.length; i++) {
                    levels[i] = (float) IdleLights.afterGame(lightsPlan.bulb(i, e),
                            IdleLights.bulb(i, FortuneWheelSpin.BULBS, idle), since);
                }
                rig.bulbs(levels);
                rig.plateFlash(lightsPlan.flash(e) < 1.0);
            }
            if (e > lightsPlan.endTick() + 14) {
                lightsPlan = null;
                rig.plateFlash(false);
            }
            return;
        }
        if (idleLightsDue(world, ctx.config().get().wheel(), now)) {
            rig.bulbs(idleLevels(world.getGameTime()));
        }
    }

    private double idleTime(long gameTime) {
        return IdleLights.clock(gameTime, machine.anchor().x(), machine.anchor().y(), machine.anchor().z());
    }

    private float[] idleLevels(long gameTime) {
        double t = idleTime(gameTime);
        float[] levels = new float[FortuneWheelSpin.BULBS];
        for (int i = 0; i < levels.length; i++) {
            levels[i] = (float) IdleLights.bulb(i, FortuneWheelSpin.BULBS, t);
        }
        return levels;
    }

    // ---- sounds and particles ({@code FortuneWheelBlockEntity.cues/sparks/announce/burst}) -------------------------

    private void cue(World world, FortuneWheelSpin.Cue cue) {
        switch (cue.sound()) {
            case SPIN -> sound(world, "wheel_spin", AXLE, cue.volume(), cue.pitch());
            case WHIRR -> sound(world, "wheel_whirr", AXLE, cue.volume(), cue.pitch());
            case CLICK -> sound(world, "wheel_click", POINTER, cue.volume(), cue.pitch());
            case STOP -> {
                sound(world, "wheel_stop", POINTER, cue.volume(), cue.pitch());
                particle(world, Particle.CRIT, SPARK, 6, 0.12, 0.1, 0.04, 0.12, null);
            }
        }
    }

    private void sparks(World world, FortuneWheelSpin.Plan plan, long elapsed) {
        if (elapsed % 4 != 0) {
            return;
        }
        double speed = plan.speed(elapsed);
        if (speed >= 0.7) {
            double a = Math.toRadians(elapsed * 53L % 360L);
            particle(world, Particle.ELECTRIC_SPARK, new double[] {8 + 21 * Math.sin(a), 11 + 21 * Math.cos(a), 13},
                    2, 0.05, 0.05, 0.05, 0.12, null);
        } else if (speed > 0.02 && speed < 0.16) {
            particle(world, Particle.END_ROD, SPARK, 2, 0.3, 0.14, 0.04, 0.02, null);
        }
    }

    private void announce(World world, FortuneWheelGame.Kind kind, int quarters) {
        switch (kind) {
            case JACKPOT -> {
                sound(world, "wheel_jackpot", AXLE, 1.5f, 1.0f);
                burst(world, 1.0, true);
            }
            case WIN -> {
                boolean big = quarters >= 8;
                sound(world, "wheel_win", AXLE, big ? 1.25f : 1.05f, 1.0f);
                burst(world, big ? 0.75 : 0.5, false);
            }
            case PUSH -> {
                sound(world, "wheel_push", AXLE, 0.9f, 1.0f);
                particle(world, Particle.END_ROD, new double[] {8, 11, 15}, 10, 0.8, 0.8, 0.1, 0.03, null);
            }
            case PARTIAL -> {
                sound(world, "wheel_partial", AXLE, 0.9f, 1.0f);
                particle(world, Particle.ITEM, new double[] {8, -10.5, 15}, 6, 0.2, 0.08, 0.1, 0.12,
                        ItemStack.of(Material.GOLD_NUGGET));
            }
            case NONE -> {
                sound(world, "wheel_lose", AXLE, 0.95f, 1.0f);
                particle(world, Particle.SMOKE, new double[] {8, 31.5, 14}, 8, 0.15, 0.05, 0.12, 0.01, null);
                particle(world, Particle.ELECTRIC_SPARK, SPARK, 6, 0.35, 0.2, 0.05, 0.1, null);
            }
        }
    }

    private void afterburst(World world, FortuneWheelGame.Kind kind, long sinceResult) {
        if (kind == FortuneWheelGame.Kind.JACKPOT) {
            if (sinceResult >= 5 && sinceResult <= 35 && sinceResult % 5 == 0) {
                burst(world, 0.8 - 0.1 * (sinceResult / 5 - 1), false);
            }
        } else if (kind == FortuneWheelGame.Kind.WIN && sinceResult == 8) {
            burst(world, 0.35, false);
        }
    }

    private void burst(World world, double strength, boolean jackpot) {
        ItemStack coin = ItemStack.of(Material.GOLD_NUGGET);
        particle(world, Particle.ITEM, new double[] {8, -10.5, 15.5}, (int) Math.round(26.0 * strength), 0.3, 0.1,
                0.12, 0.2, coin);
        double[] face = {8, 11, 15.5};
        int n = (int) Math.round(24.0 * strength);
        particle(world, Particle.DUST, face, n, 0.9, 0.9, 0.25, 0.16, GOLD_CONFETTI);
        particle(world, Particle.DUST, face, n, 0.9, 0.9, 0.25, 0.16, RED_CONFETTI);
        particle(world, Particle.DUST, face, n, 0.9, 0.9, 0.25, 0.16, GREEN_CONFETTI);
        double[] cap = {8, 32, 14};
        particle(world, Particle.FIREWORK, cap, (int) Math.round(22.0 * strength), 0.25, 0.2, 0.25, 0.22, null);
        particle(world, Particle.END_ROD, cap, (int) Math.round(14.0 * strength), 0.25, 0.2, 0.25, 0.16, null);
        if (strength >= 0.7) {
            double[] bulbs = WheelGeometry.BULBS;
            for (int i = 0; i < 16; i++) {
                particle(world, Particle.FIREWORK, new double[] {bulbs[2 * i], bulbs[2 * i + 1], 14.5}, 1, 0.04,
                        0.04, 0.04, 0.1, null);
            }
        }
        if (jackpot) {
            particle(world, Particle.TOTEM_OF_UNDYING, face, 40, 0.8, 0.8, 0.3, 0.35, null);
            world.spawnParticle(Particle.ITEM, at(world, cap).subtract(0, 0.3, 0), 30, 0.3, 0.1, 0.3, 0.28, coin);
        }
    }
}
