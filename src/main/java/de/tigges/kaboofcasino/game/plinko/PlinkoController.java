package de.tigges.kaboofcasino.game.plinko;

import de.tigges.kaboofcasino.CasinoContext;
import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.game.FairPayout;
import de.tigges.kaboofcasino.game.GameMenu;
import de.tigges.kaboofcasino.game.MachineController;
import de.tigges.kaboofcasino.game.Odds;
import de.tigges.kaboofcasino.game.PanelText;
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
 * Runtime of one Plinko machine ({@code PlinkoBlockEntity}): the menu with three bet slots (one ball each), the drop
 * (decide every ball's path, commit one record per ball in one tick, play the mod's drop plan), the settlement per
 * ball (a paying ball at {@code landTick + 10}, where the mod starts ejecting its stacks; a ball paying nothing when
 * it lands), each ball's coin shower at the mod's pace, the result announcement and the unlock once nothing is owed,
 * the result is shown and {@code readyTick} passed. Thread: MAIN.
 */
public final class PlinkoController extends MachineController {

    static final String GAME = "plinko";
    private static final Particle.DustOptions GOLD_CONFETTI = new Particle.DustOptions(Color.fromRGB(0xFFD168), 1.2f);
    private static final Particle.DustOptions RED_CONFETTI = new Particle.DustOptions(Color.fromRGB(0xDF3B4B), 1.1f);
    private static final Particle.DustOptions GREEN_CONFETTI = new Particle.DustOptions(Color.fromRGB(0x36D886), 1.1f);
    private static final Particle.DustOptions LAMP_SPARK = new Particle.DustOptions(Color.fromRGB(0xFFF0B0), 0.55f);
    /** Result line colours by kind NONE, PARTIAL, WIN, BIG, JACKPOT ({@code PlinkoRenderer}). */
    private static final int[] RESULT_COLOR = {0xE79882, 0xFFAA00, 0x88CF86, 0xF2CC72, 0xFFAA00};
    static final double[] BOARD = {24, 26, 14.5};
    static final double[] PAYOUT_MOUTH = {24, 6.5, 14.5};
    /** {@code PlinkoRenderGeometry.BULBS} (board px). */
    static final double[] BULBS = {14, 47, 19, 47, 24, 47, 29, 47, 34, 47, 3, 41.5, 3, 43.5, 3, 45.5, 45, 41.5, 45,
        43.5, 45, 45.5};

    /** {@code CasinoOdds.PLINKO}: win = more than the stake back, one ticket per path (74 winning, 182 losing). */
    public static final Odds.Pool POOL = Odds.pool(PlinkoGame.PATHS,
            p -> PlinkoGame.quarters(PlinkoGame.pocketOf(p)) > 4, p -> 1,
            p -> PlinkoGame.quarters(PlinkoGame.pocketOf(p)));

    private PlinkoRig rig;
    private Drop drop;
    private PlinkoDrop.Plan lightsPlan;
    private long lightsStart;
    private long panelUntil = -1;

    /** One ball of a running drop (= one filled bet slot). */
    private static final class Ball {
        final int slot;
        final ItemAmount stake;
        final List<ItemAmount> payout;
        final int paid;
        final UUID round;
        final int settleTick;
        boolean landed;
        boolean settled;
        final ArrayDeque<String> coins = new ArrayDeque<>();

        Ball(int slot, ItemAmount stake, List<ItemAmount> payout, int paid, UUID round, int settleTick) {
            this.slot = slot;
            this.stake = stake;
            this.payout = payout;
            this.paid = paid;
            this.round = round;
            this.settleTick = settleTick;
        }
    }

    /** A running drop. */
    private static final class Drop {
        final UUID player;
        final String name;
        final List<Ball> balls;
        final PlinkoDrop.Plan plan;
        final long start;
        final long ends;
        int cue;
        boolean announced;

        Drop(UUID player, String name, List<Ball> balls, PlinkoDrop.Plan plan, long start, long ends) {
            this.player = player;
            this.name = name;
            this.balls = balls;
            this.plan = plan;
            this.start = start;
            this.ends = ends;
        }
    }

    public PlinkoController(Machine machine, CasinoContext ctx) {
        super(machine, ctx);
    }

    /** Machine-local px of a board position (the mod's {@code world(x, y, z)} uses {@code toBlock(x-16, y-16, z)}). */
    @Override
    protected double[] toBlock(double x, double y, double z) {
        return machine.facing().toBlock(x - 16, y - 16, z);
    }

    /** A ball's path: natural = {@code nextInt(256)} (each bounce 50:50), else the mod's forced pools. */
    public static int path(int winChance, IntUnaryOperator nextInt) {
        if (winChance >= 0) {
            return Odds.draw(POOL, winChance, 100, nextInt);
        }
        return nextInt.applyAsInt(PlinkoGame.PATHS);
    }

    /** What the ball of slot {@code slot} pays (the fair-rounding roll uses the slot, not the ball index). */
    public static int payout(long seed, int slot, int count, int quarters) {
        return PlinkoGame.payout(count, quarters, FairPayout.roll(seed, slot, 4));
    }

    /** When a ball settles: paid balls where the mod starts ejecting them, the others when they land. */
    static int settleTick(PlinkoDrop.Ball ball, boolean pays) {
        return (int) Math.ceil(ball.landTick()) + (pays ? PlinkoDrop.PAYOUT_DELAY : 0);
    }

    private static PlinkoGame.Kind kind(Ball b, PlinkoDrop.Plan plan, int index) {
        int quarters = PlinkoGame.quarters(plan.ball(index).pocket());
        return PlinkoGame.kindFor(quarters, b.paid);
    }

    /** {@code PlinkoBlockEntity.resultKind}: the best ball. */
    private static PlinkoGame.Kind resultKind(Drop d) {
        PlinkoGame.Kind best = PlinkoGame.Kind.NONE;
        for (int i = 0; i < d.balls.size(); i++) {
            PlinkoGame.Kind k = kind(d.balls.get(i), d.plan, i);
            if (k.ordinal() > best.ordinal()) {
                best = k;
            }
        }
        return best;
    }

    // ---- MachineGame -----------------------------------------------------------------------------------------------

    @Override
    protected boolean running() {
        return drop != null;
    }

    @Override
    protected Msg busyMessage() {
        return Msg.PLINKO_BUSY;
    }

    @Override
    protected GameMenu newMenu(Player player) {
        return new PlinkoMenu(player.getUniqueId(), PlinkoMenu.title(ctx), this, ctx);
    }

    @Override
    public void spawn() {
        World world = world();
        if (world == null) {
            return;
        }
        if (rig == null) {
            rig = new PlinkoRig(machine.record(), world);
        }
        double t = world.getGameTime();
        rig.spawn(idleLamps(t), idleBulbs(t));
        rig.ball(PlinkoRig.BALLS - 1, new double[] {PlinkoGame.CHUTE_X, PlinkoGame.CHUTE_Y}, 0);
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
        if (drop == null || !heartbeat.owns(ctx.clock().getAsLong(), drop.ends)) {
            return false;
        }
        for (Ball b : drop.balls) {
            if (!b.settled && b.round.equals(roundId)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void checkRig() {
        if (rig != null && rig.spawned() && rig.broken()) {
            Log.get().info("plinko #{}: rig entities vanished, respawning", machine.record().number());
            spawn();
        }
    }

    @Override
    public void removed() {
        if (drop != null) {
            for (Ball b : drop.balls) {
                if (!b.settled) {
                    b.settled = true;
                    settleRound(drop.player, drop.name, b.round, SettleReason.MACHINE_GONE);
                }
            }
        }
        drop = null;
        claimant = null;
        closeMenus();
    }

    // ---- DROP ------------------------------------------------------------------------------------------------------

    /** The DROP button: decide every ball, commit one record per ball (one tick), start the animation. */
    void drop(Player player, PlinkoMenu menu) {
        Messages m = ctx.messages().get();
        if (!mayCommit(player)) {
            return;
        }
        List<StakeMenu.Stake> stakes = menu.stakes();
        if (stakes.isEmpty()) {
            m.actionBar(player, Msg.PLINKO_NO_BET);
            return;
        }
        long seed = ctx.random().nextLong();
        int winChance = ctx.config().get().plinko().winChance();
        int[] paths = new int[stakes.size()];
        for (int b = 0; b < paths.length; b++) {
            paths[b] = path(winChance, ctx.random()::nextInt);
        }
        PlinkoDrop.Plan plan = PlinkoDrop.plan(paths, seed);
        long nowMillis = ctx.clock().getAsLong();
        List<WagerEngine.Request> requests = new ArrayList<>();
        List<List<ItemAmount>> payouts = new ArrayList<>();
        int[] paid = new int[paths.length];
        long ends = nowMillis;
        for (int b = 0; b < paths.length; b++) {
            StakeMenu.Stake s = stakes.get(b);
            int pocket = PlinkoGame.pocketOf(paths[b]);
            int quarters = PlinkoGame.quarters(pocket);
            paid[b] = payout(seed, s.slot(), (int) s.amount().count(), quarters);
            List<ItemAmount> payout = paid[b] > 0 ? List.of(new ItemAmount(s.amount().item(), paid[b])) : List.of();
            payouts.add(payout);
            long ballEnds = nowMillis + settleTick(plan.ball(b), paid[b] > 0) * 50L;
            ends = Math.max(ends, ballEnds);
            requests.add(new WagerEngine.Request(machine.id(), GAME, s.amount(), payout, ballEnds,
                    "pocket=" + pocket + " " + PanelText.multiplier(quarters) + " slot=" + s.slot()));
        }
        List<UUID> rounds = commit(player, requests, Msg.PLINKO_NO_BET);
        if (rounds == null) {
            menu.render();
            return;
        }
        List<Ball> balls = new ArrayList<>();
        for (int b = 0; b < paths.length; b++) {
            balls.add(new Ball(stakes.get(b).slot(), stakes.get(b).amount(), payouts.get(b), paid[b], rounds.get(b),
                    settleTick(plan.ball(b), !payouts.get(b).isEmpty())));
        }
        long now = Bukkit.getCurrentTick();
        drop = new Drop(player.getUniqueId(), player.getName(), balls, plan, now, ends);
        lightsPlan = plan;
        lightsStart = now;
        menu.clearStakes();
        closeMenus();
        if (rig != null) {
            rig.panel(panel(drop, false));
        }
        panelUntil = -1;
        List<Integer> pockets = new ArrayList<>();
        for (int p : paths) {
            pockets.add(PlinkoGame.pocketOf(p));
        }
        Log.get().info("{} drops {} ball(s) on plinko #{} with {}: pockets {}", player.getName(), paths.length,
                machine.record().number(), stakes.stream().map(s -> s.amount().toString()).toList(), pockets);
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
        if (drop != null) {
            runDrop(world, now);
        }
        lights(world, now);
        if (panelUntil >= 0 && now >= panelUntil) {
            rig.hidePanel();
            panelUntil = -1;
        }
    }

    private void runDrop(World world, long now) {
        Drop d = drop;
        PlinkoDrop.Plan plan = d.plan;
        long elapsed = now - d.start;
        List<PlinkoDrop.Cue> cues = plan.cues();
        while (d.cue < cues.size() && cues.get(d.cue).tick() <= elapsed) {
            cue(world, plan, cues.get(d.cue++));
        }
        balls(plan, elapsed + 1, 1);
        boolean panelChanged = false;
        for (int i = 0; i < d.balls.size(); i++) {
            Ball b = d.balls.get(i);
            if (!b.landed && elapsed >= plan.ball(i).landTick()) {
                b.landed = true;
                panelChanged = true;
                if (elapsed - plan.ball(i).landTick() <= 10.0) {
                    landed(world, plan, i, kind(b, plan, i));
                }
            }
            if (!b.settled && elapsed >= b.settleTick) {
                b.settled = true;
                settleRound(d.player, d.name, b.round, SettleReason.NORMAL);
                queueCoins(b.coins, b.payout);
            }
            if (!b.coins.isEmpty()) {
                int interval = b.coins.size() > 48 ? 1 : b.coins.size() > 16 ? 2 : 3;
                if ((now + i) % interval == 0) {
                    rig.coin(PlayerAccount.material(b.coins.poll()), coinTarget(world, d.player), now);
                    if (interval > 1 || now % 2 == 0) {
                        sound(world, "plinko_payout", PAYOUT_MOUTH, 0.5f, 0.9f + ctx.random().nextFloat() * 0.25f);
                    }
                }
            }
        }
        PlinkoGame.Kind kind = resultKind(d);
        if (!d.announced && elapsed >= plan.resultTick()) {
            d.announced = true;
            panelChanged = true;
            panelUntil = d.start + plan.resultTick() + 70;
            if (elapsed - plan.resultTick() <= 10) {
                announce(world, kind);
            }
        }
        if (panelChanged) {
            rig.panel(panel(d, d.announced));
        }
        if (d.announced) {
            afterburst(world, kind, elapsed - plan.resultTick());
        }
        boolean owed = false;
        for (Ball b : d.balls) {
            owed |= !b.settled || !b.coins.isEmpty();
        }
        if (!owed && d.announced && elapsed >= plan.readyTick()) {
            drop = null;
            claimant = null;
        }
    }

    /** Balls in play and the one waiting in the chute ({@code PlinkoRenderer}). */
    private void balls(PlinkoDrop.Plan plan, double at, int duration) {
        for (int i = 0; i < PlinkoRig.BALLS - 1; i++) {
            if (i < plan.ballCount() && plan.ball(i).visible(at)) {
                rig.ball(i, plan.ball(i).position(at), duration);
            } else {
                rig.ball(i, null, 0);
            }
        }
        rig.ball(PlinkoRig.BALLS - 1, plan.chuteFull(at) ? new double[] {PlinkoGame.CHUTE_X, PlinkoGame.CHUTE_Y}
                : null, 0);
    }

    /** The panel: player, "EINSÄTZE", the stakes, the landed balls' multipliers, from the result on the result. */
    private Component panel(Drop d, boolean decided) {
        Messages m = ctx.messages().get();
        Component nl = Component.newline();
        var text = Component.text().append(PanelText.header(d.player, d.name)).append(nl)
                .append(m.render(Msg.PANEL_BETS).color(PanelText.TITLE)).append(nl)
                .append(PanelText.stakes(d.balls.stream().map(b -> b.stake).toList()));
        boolean any = d.balls.stream().anyMatch(b -> b.landed);
        if (any) {
            var line = Component.text();
            for (int i = 0; i < d.balls.size(); i++) {
                if (i > 0) {
                    line.append(Component.text("      "));
                }
                if (d.balls.get(i).landed) {
                    int q = PlinkoGame.quarters(d.plan.ball(i).pocket());
                    int color = q >= 32 ? 0xFFAA00 : q >= 8 ? 0x88CF86 : 0xE79882;
                    line.append(Component.text(PanelText.multiplier(q), TextColor.color(color))
                            .decoration(TextDecoration.BOLD, true));
                } else {
                    line.append(Component.text("…", PanelText.WHITE));
                }
            }
            text.append(nl).append(line.build());
        }
        if (decided) {
            text.append(nl).append(resultLine(m, resultKind(d), d.plan.bestQuarters()));
        }
        return text.build();
    }

    static Component resultLine(Messages m, PlinkoGame.Kind kind, int quarters) {
        Component number = Component.text(PanelText.multiplier(quarters),
                TextColor.color(kind == PlinkoGame.Kind.JACKPOT ? 0xFFFF55 : 0xFFFFFF));
        Component line = switch (kind) {
            case NONE -> m.render(Msg.PANEL_LOSE);
            case PARTIAL -> m.render(Msg.PANEL_PARTIAL, Messages.component("mult", number));
            case WIN -> m.render(Msg.PANEL_WIN, Messages.component("mult", number));
            case BIG -> m.render(Msg.PANEL_BIG, Messages.component("mult", number));
            case JACKPOT -> m.render(Msg.PANEL_JACKPOT, Messages.component("mult", number));
        };
        return line.colorIfAbsent(TextColor.color(RESULT_COLOR[kind.ordinal()]))
                .decoration(TextDecoration.BOLD, true);
    }

    // ---- lights ({@code PlinkoRenderer.extract}) -------------------------------------------------------------------

    private static double smooth(double x) {
        double c = Math.clamp(x, 0.0, 1.0);
        return c * c * (3.0 - 2.0 * c);
    }

    private static float[] idleLamps(double time) {
        float[] out = new float[PlinkoGame.PIN_COUNT];
        for (int pin = 0; pin < out.length; pin++) {
            out[pin] = (float) PlinkoLights.lamp(pin, time);
        }
        return out;
    }

    private static float[] idleBulbs(double time) {
        float[] out = new float[PlinkoLights.BULBS];
        for (int b = 0; b < out.length; b++) {
            out[b] = (float) PlinkoLights.bulb(b, time);
        }
        return out;
    }

    private void lights(World world, long now) {
        double time = world.getGameTime();
        if (lightsPlan != null) {
            double e = now - lightsStart;
            if (e % 2 == 0) {
                double since = e - lightsPlan.endTick();
                float[] lamps = idleLamps(time);
                float[] bulbs = idleBulbs(time);
                for (int pin = 0; pin < lamps.length; pin++) {
                    double game = lightsPlan.lamp(pin, e);
                    lamps[pin] = (float) (since < 0 ? PlinkoLights.into(lamps[pin], game, e)
                            : PlinkoLights.back(game, lamps[pin], since));
                }
                for (int b = 0; b < bulbs.length; b++) {
                    double game = lightsPlan.bulb(b, e);
                    bulbs[b] = (float) (since < 0 ? PlinkoLights.into(bulbs[b], game, e)
                            : PlinkoLights.back(game, bulbs[b], since));
                }
                float[] pockets = new float[PlinkoGame.POCKETS];
                double fade = since < 0 ? 1.0 : 1.0 - smooth(since / 14.0);
                for (int k = 0; k < pockets.length; k++) {
                    pockets[k] = (float) (lightsPlan.pocket(k, e) * fade);
                }
                rig.lights(lamps, bulbs);
                rig.pockets(pockets);
            }
            if (e > lightsPlan.endTick() + 14) {
                lightsPlan = null;
                rig.pockets(new float[PlinkoGame.POCKETS]);
                if (drop == null) {
                    rig.ball(PlinkoRig.BALLS - 1, new double[] {PlinkoGame.CHUTE_X, PlinkoGame.CHUTE_Y}, 0);
                }
            }
            return;
        }
        int every = ctx.config().get().plinko().idleLightTicks();
        if (every > 0 && now % every == 0 && playerNear(world, ctx.config().get().plinko().idleLightRange())) {
            rig.lights(idleLamps(time), idleBulbs(time));
        }
    }

    // ---- sounds and particles ({@code PlinkoBlockEntity.cues/landed/announce/burst}) -------------------------------

    private void cue(World world, PlinkoDrop.Plan plan, PlinkoDrop.Cue cue) {
        switch (cue.sound()) {
            case DROP -> sound(world, "plinko_drop", new double[] {24, 38.5, 14.5}, cue.volume(), cue.pitch());
            case PEG -> {
                int pin = plan.ball(cue.ball()).hitPin(cue.index());
                double x = PlinkoLights.pinX(pin);
                double y = PlinkoLights.pinY(pin);
                sound(world, "plinko_peg", new double[] {x, y, 14.5}, cue.volume(), cue.pitch());
                particle(world, Particle.DUST, new double[] {x, y, 14.3}, 2, 0.02, 0.02, 0.01, 0.0, LAMP_SPARK);
            }
            case POCKET -> sound(world, "plinko_pocket", new double[] {PlinkoGame.pocketX(cue.index()), 14.5, 14.5},
                    cue.volume(), cue.pitch());
        }
    }

    private void landed(World world, PlinkoDrop.Plan plan, int ball, PlinkoGame.Kind kind) {
        double[] at = {PlinkoGame.pocketX(plan.ball(ball).pocket()), 15, 15};
        switch (kind) {
            case JACKPOT -> {
                particle(world, Particle.TOTEM_OF_UNDYING, at, 30, 0.12, 0.25, 0.08, 0.35, null);
                particle(world, Particle.DUST, at, 30, 0.2, 0.3, 0.1, 0.12, GOLD_CONFETTI);
                particle(world, Particle.FIREWORK, at, 18, 0.1, 0.2, 0.1, 0.18, null);
            }
            case BIG -> {
                particle(world, Particle.DUST, at, 16, 0.15, 0.25, 0.08, 0.1, GOLD_CONFETTI);
                particle(world, Particle.DUST, at, 12, 0.15, 0.25, 0.08, 0.1, GREEN_CONFETTI);
                particle(world, Particle.FIREWORK, at, 10, 0.1, 0.15, 0.08, 0.14, null);
            }
            case WIN -> {
                particle(world, Particle.DUST, at, 10, 0.12, 0.2, 0.08, 0.08, GOLD_CONFETTI);
                particle(world, Particle.END_ROD, at, 6, 0.1, 0.15, 0.05, 0.04, null);
            }
            case PARTIAL -> particle(world, Particle.ITEM, at, 5, 0.08, 0.05, 0.05, 0.08,
                    ItemStack.of(Material.GOLD_NUGGET));
            case NONE -> particle(world, Particle.SMOKE, at, 5, 0.08, 0.05, 0.05, 0.01, null);
        }
    }

    private void announce(World world, PlinkoGame.Kind kind) {
        switch (kind) {
            case JACKPOT -> {
                sound(world, "plinko_jackpot", BOARD, 1.5f, 1.0f);
                burst(world, 1.0, true);
            }
            case BIG -> {
                sound(world, "plinko_win", BOARD, 1.25f, 1.0f);
                burst(world, 0.75, false);
            }
            case WIN -> {
                sound(world, "plinko_win", BOARD, 1.05f, 1.0f);
                burst(world, 0.5, false);
            }
            case PARTIAL -> {
                sound(world, "plinko_partial", BOARD, 0.9f, 1.0f);
                particle(world, Particle.ITEM, new double[] {24, 8, 15}, 6, 0.2, 0.08, 0.1, 0.12,
                        ItemStack.of(Material.GOLD_NUGGET));
            }
            case NONE -> {
                sound(world, "plinko_lose", BOARD, 0.95f, 1.0f);
                particle(world, Particle.SMOKE, new double[] {24, 47, 12}, 8, 0.4, 0.05, 0.1, 0.01, null);
            }
        }
    }

    private void afterburst(World world, PlinkoGame.Kind kind, long since) {
        switch (kind) {
            case JACKPOT -> {
                if (since >= 5 && since <= PlinkoDrop.LAST_BURST[kind.ordinal()] && since % 5 == 0) {
                    burst(world, 0.8 - 0.1 * (since / 5 - 1), false);
                }
            }
            case BIG -> {
                if (since == 7 || since == PlinkoDrop.LAST_BURST[kind.ordinal()]) {
                    burst(world, 0.4, false);
                }
            }
            case WIN -> {
                if (since == PlinkoDrop.LAST_BURST[kind.ordinal()]) {
                    burst(world, 0.3, false);
                }
            }
            default -> {
            }
        }
    }

    private void burst(World world, double strength, boolean jackpot) {
        ItemStack coin = ItemStack.of(Material.GOLD_NUGGET);
        particle(world, Particle.ITEM, new double[] {24, 8, 15.5}, (int) Math.round(24.0 * strength), 0.4, 0.1, 0.12,
                0.2, coin);
        double[] board = {24, 26, 15.5};
        int n = (int) Math.round(24.0 * strength);
        particle(world, Particle.DUST, board, n, 0.9, 0.7, 0.25, 0.16, GOLD_CONFETTI);
        particle(world, Particle.DUST, board, n, 0.9, 0.7, 0.25, 0.16, RED_CONFETTI);
        particle(world, Particle.DUST, board, n, 0.9, 0.7, 0.25, 0.16, GREEN_CONFETTI);
        double[] crest = {24, 47.5, 12};
        particle(world, Particle.FIREWORK, crest, (int) Math.round(20.0 * strength), 0.6, 0.2, 0.25, 0.22, null);
        particle(world, Particle.END_ROD, crest, (int) Math.round(12.0 * strength), 0.6, 0.2, 0.25, 0.16, null);
        if (strength >= 0.7) {
            for (int i = 0; i < 11; i++) {
                particle(world, Particle.FIREWORK, new double[] {BULBS[2 * i], BULBS[2 * i + 1], 12.5}, 1, 0.04, 0.04,
                        0.04, 0.1, null);
            }
        }
        if (jackpot) {
            particle(world, Particle.TOTEM_OF_UNDYING, board, 40, 0.8, 0.7, 0.3, 0.35, null);
            world.spawnParticle(Particle.ITEM, at(world, crest).subtract(0, 0.3, 0), 30, 0.5, 0.1, 0.3, 0.28, coin);
        }
    }
}
