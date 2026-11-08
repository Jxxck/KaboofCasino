package de.tigges.kaboofcasino.game.derby;

import de.tigges.kaboofcasino.CasinoContext;
import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.game.GameMenu;
import de.tigges.kaboofcasino.game.MachineController;
import de.tigges.kaboofcasino.game.StakeMenu;
import de.tigges.kaboofcasino.game.slot.IdleLights;
import de.tigges.kaboofcasino.gui.MenuService;
import de.tigges.kaboofcasino.gui.Usability;
import de.tigges.kaboofcasino.machine.Heartbeat;
import de.tigges.kaboofcasino.machine.Machine;
import de.tigges.kaboofcasino.machine.MachineSettings;
import de.tigges.kaboofcasino.util.Log;
import de.tigges.kaboofcasino.wager.ItemAmount;
import de.tigges.kaboofcasino.wager.LedgerCodec;
import de.tigges.kaboofcasino.wager.PlayerAccount;
import de.tigges.kaboofcasino.wager.RoundRecord;
import de.tigges.kaboofcasino.wager.RoundState;
import de.tigges.kaboofcasino.wager.SettleReason;
import de.tigges.kaboofcasino.wager.WagerEngine;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;

/**
 * Runtime of one duck derby ({@code DuckDerbyBlockEntity}, games.md 7): four ducks, one lane per player, 1st pays
 * 3 x the stake, 2nd returns it, 3rd and 4th lose it.
 * <ul>
 *   <li><b>Solo</b> (button 0): the winner is drawn ({@link DerbyRules#draw}: natural {@code nextInt(4)} or the solo
 *       win chance) together with the seed and the plan, the stakes are committed in one tick as DECIDED records
 *       holding that result, and the race starts at once; every other menu on the machine closes.</li>
 *   <li><b>Group</b> (button 1, "Gruppe (10s)" / "Bestätigen"): each entrant's stakes are committed on his own as
 *       PLAYING records paying the refund (the mod's held escrow); the 200-tick countdown runs from the first
 *       entrant and is never extended; up to 64 entrants on any ducks. Withdraw (button 2) settles the refund at once.
 *       Entrants who quit or die meanwhile were settled by the quit/death hooks (refund) and are dropped. At the end
 *       of the countdown the bet window is closed and only then the winner is drawn ({@code nextInt(4)}, groups use
 *       natural odds), the seed and the plan; each entrant's records are rewritten to DECIDED with his result in his
 *       own ledger write ({@link WagerEngine#revise}): one player's quit, death or crash never touches another's
 *       records.</li>
 *   <li>The race: the mod's plan drives quacks, splashes, the finish line, the result messages and confetti; the
 *       records settle 12 ticks after the result ({@code resultTick + 12}, when the mod's credits become due) into
 *       the inventory, the rest owed; the machine unlocks at {@code endTick}. Quit, kick, stop, crash, death and the
 *       watchdog settle the stored result, which is what the race shows. Its chunk is held loaded from the first
 *       entrant to the end, so a race with nobody near still ends and pays on time.</li>
 * </ul>
 * Nothing about the result exists before the bets are locked, so neither the rig nor the menus can leak it. One clock:
 * the world's game time. Thread: MAIN.
 */
public final class DerbyController extends MachineController {

    static final String GAME = "derby";
    static final int ENROLL_TICKS = 200;
    static final int MAX_PLAYERS = 64;
    static final int PANEL_AFTER_RESULT = 70;
    static final int DUE_AFTER_RESULT = 12;
    /** The payout is spread over these ticks after the result ({@code resultTick + 12 .. + 16}). */
    static final int PAYOUT_TICKS = 5;
    /** A record's watchdog end: the race's end (or the countdown's plus the longest race) + this. */
    static final long RECORD_ENDS_MILLIS = 60_000;
    /** The longest race ({@code endTick} of the slowest plan, games.md 7: 243..276). */
    static final int LONGEST_RACE_TICKS = 300;
    static final int NEAR = 16;
    static final double[] START_SPOT = {24, 14, 32};
    static final double[] MIDDLE = {24, 20, 32};
    static final double[] RAIN = {24, 24, 32};
    /**
     * A race older than its {@code endTick} + this, or a countdown older than its 200 ticks + this, is stuck (an error
     * in a later tick step): its entrants settle as stored (TIMEOUT), the machine is free again.
     */
    static final int STUCK_AFTER_TICKS = 600;
    /** How long a viewer's owed count is reused by the menu snapshot. */
    static final int OWED_CACHE_TICKS = 10;
    /** {@code DuckDerbyBlockEntity} 51-59, the mod's ints as they are. */
    private static final Particle.DustOptions GOLD_CONFETTI = new Particle.DustOptions(Color.fromRGB(16765288), 1.25f);
    private static final Particle.DustOptions RED_CONFETTI = new Particle.DustOptions(Color.fromRGB(14629707), 1.15f);
    private static final Particle.DustOptions GREEN_CONFETTI = new Particle.DustOptions(Color.fromRGB(3594374), 1.15f);
    private static final Particle.DustOptions[] DUCK_CONFETTI = {
        new Particle.DustOptions(Color.fromRGB(16765000), 1.3f),
        new Particle.DustOptions(Color.fromRGB(14170696), 1.3f),
        new Particle.DustOptions(Color.fromRGB(15920342), 1.3f),
        new Particle.DustOptions(Color.fromRGB(8161440), 1.3f)
    };

    private DerbyRig rig;
    /** Entrants in join order ({@code participants}): the solo player, or the group. */
    private final Map<UUID, Entrant> entrants = new LinkedHashMap<>();
    /** Game tick at which the group countdown ends, -1 when none runs. */
    private long enrollmentUntil = -1;
    private boolean groupRace;
    private Race race;
    private int lastCount;
    /** The 12 shown bets of a solo race (slot = row * 4 + lane) and when they clear. */
    private final ItemAmount[] displayed = new ItemAmount[DerbyView.SLOTS];
    private long displayUntil = -1;
    private boolean chunkHeld;
    /** Owed item count per menu viewer and the tick it was read ({@link #snapshot}). */
    private final Map<UUID, long[]> owedCache = new java.util.HashMap<>();

    /** One staked bet row of an entrant. */
    record Seat(int slot, ItemAmount stake, UUID round) {
    }

    /** One player in the countdown or the race. */
    static final class Entrant {
        final UUID player;
        final String name;
        final int lane;
        final List<Seat> seats;
        long endsMillis;
        boolean settled;

        Entrant(UUID player, String name, int lane, List<Seat> seats, long endsMillis) {
            this.player = player;
            this.name = name;
            this.lane = lane;
            this.seats = List.copyOf(seats);
            this.endsMillis = endsMillis;
        }

        DerbyMember member() {
            return new DerbyMember(player, name, lane, seats.stream().map(Seat::stake).toList());
        }
    }

    /** The running race ({@code locked}, {@code raceStart}, {@code winner}, {@code seed}, {@code stage}). */
    static final class Race {
        final DerbyRules.Race drawn;
        final int second;
        long start;
        int stage;
        int quackIndex;
        boolean won;
        boolean refunded;
        boolean paid;
        /** Entrants settled so far by the spread payout, and the time it took. */
        int payIndex;
        long payNanos;
        long payMaxNanos;

        Race(DerbyRules.Race drawn, long start) {
            this.drawn = drawn;
            this.second = drawn.second();
            this.start = start;
        }

        DuckDerbyRace.Plan plan() {
            return drawn.plan();
        }
    }

    public DerbyController(Machine machine, CasinoContext ctx) {
        super(machine, ctx);
    }

    // ---- MachineGame -----------------------------------------------------------------------------------------------

    /** {@code isRacing()}: the race runs; the machine cannot be opened and its menus close. */
    @Override
    protected boolean running() {
        return race != null;
    }

    /** Breaking is refused while a race runs or entrants wait (their records are open). */
    @Override
    public boolean busy() {
        return race != null || enrolling();
    }

    @Override
    protected Msg busyMessage() {
        return Msg.DERBY_BUSY;
    }

    @Override
    protected double[] toBlock(double x, double y, double z) {
        return DuckDerbyLayout.toBlock(x, y, z, machine.facing().turns());
    }

    @Override
    protected GameMenu newMenu(Player player) {
        return new DerbyBetMenu(player.getUniqueId(), this, ctx);
    }

    /**
     * {@code DuckDerbyBlock.useWithoutItem}: a running race says "Das Rennen läuft."; otherwise everyone may open the
     * machine at the same time (each has his own selection; the countdown is shared).
     */
    @Override
    public void interact(Player player) {
        if (race != null) {
            ctx.messages().get().actionBar(player, Msg.DERBY_BUSY);
            return;
        }
        if (!mayOpen(player)) {
            return;
        }
        ctx.menus().open(player, newMenu(player));
    }

    @Override
    public void menuClosed(Player player) {
        // no claim: the derby is shared
    }

    @Override
    public void spawn() {
        World world = world();
        if (world == null) {
            return;
        }
        if (rig == null) {
            rig = new DerbyRig(machine.record(), world,
                    gameTime -> ctx.lights().grant(machine.id(), Bukkit.getCurrentTick()), ctx.plugin());
        }
        rig.spawn();
    }

    @Override
    public void despawn() {
        if (rig != null) {
            rig.despawn();
        }
        holdChunk(false);
        closeMenus();
    }

    @Override
    public void checkRig() {
        if (rig != null && rig.spawned() && rig.broken()) {
            Log.get().info("duck derby #{}: rig entities vanished, respawning", machine.record().number());
            rig.despawn();
            spawn();
        }
    }

    @Override
    public boolean ownsRound(UUID roundId) {
        long now = ctx.clock().getAsLong();
        for (Entrant e : entrants.values()) {
            if (!e.settled && e.seats.stream().anyMatch(s -> s.round().equals(roundId))) {
                return owns(heartbeat, now, e.endsMillis);
            }
        }
        return false;
    }

    /** The table logic ran within 2 s and the record's end + 60 s grace has not passed. */
    static boolean owns(Heartbeat heartbeat, long nowMillis, long endsMillis) {
        return heartbeat.owns(nowMillis, endsMillis);
    }

    /**
     * {@code onBroken} (admin removal while loaded): menus close; countdown entrants get their refund (the records'
     * payout), race entrants their stored result at once.
     */
    @Override
    public void removed() {
        closeMenus();
        for (Entrant e : List.copyOf(entrants.values())) {
            settle(e, SettleReason.MACHINE_GONE, false);
        }
        entrants.clear();
        enrollmentUntil = -1;
        race = null;
        groupRace = false;
        Arrays.fill(displayed, null);
        displayUntil = -1;
        holdChunk(false);
    }

    // ---- SOLO, GROUP, WITHDRAW, CLAIM ------------------------------------------------------------------------------

    /** {@code startSolo}: not racing, no countdown; the staked lane is committed with the drawn result and starts. */
    void solo(Player player, DerbyBetMenu menu) {
        Messages m = ctx.messages().get();
        World world = world();
        if (world == null || race != null) {
            m.actionBar(player, Msg.DERBY_BUSY);
            return;
        }
        if (enrolling() || !mayBet(player)) {
            return;
        }
        int lane = menu.stakedLane();
        List<StakeMenu.Stake> stakes = menu.stakes();
        if (lane < 0 || stakes.isEmpty()) {
            m.actionBar(player, Msg.DERBY_NO_BET);
            return;
        }
        int chance = MachineSettings.winChance(ctx.config().get(), machine);
        DerbyRules.Race drawn = DerbyRules.draw(false, lane, chance, ctx.random()::nextInt, ctx.random()::nextLong);
        int second = drawn.second();
        long ends = ctx.clock().getAsLong() + drawn.plan().endTick() * 50L + RECORD_ENDS_MILLIS;
        List<WagerEngine.Request> requests = new ArrayList<>();
        for (StakeMenu.Stake s : stakes) {
            requests.add(new WagerEngine.Request(machine.id(), GAME, s.amount(),
                    DerbyRules.payout(s.amount(), lane, drawn.winner(), second), ends,
                    detail(s.slot(), lane, drawn, "solo"), RoundState.DECIDED).withTake(s.chips()));
        }
        List<UUID> rounds = commit(player, requests, Msg.DERBY_NO_BET);
        if (rounds == null) {
            menu.render();
            return;
        }
        entrants.clear();
        groupRace = false;
        entrants.put(player.getUniqueId(), new Entrant(player.getUniqueId(), player.getName(), lane,
                seats(stakes, rounds), ends));
        menu.clearStakes();
        Log.get().info("{} races solo on duck derby #{} with {} on lane {} (chance {})", player.getName(),
                machine.record().number(), stakes.stream().map(s -> s.amount().toString()).toList(), lane, chance);
        launch(world, drawn, world.getGameTime());
    }

    /**
     * {@code joinGroup}: the staked lane joins the countdown (starting it if none runs) as a refund until the start;
     * the menu closes like the mod's client after the confirm.
     */
    void group(Player player, DerbyBetMenu menu) {
        Messages m = ctx.messages().get();
        World world = world();
        if (world == null || race != null) {
            m.actionBar(player, Msg.DERBY_BUSY);
            return;
        }
        long now = world.getGameTime();
        if (!DerbyRules.groupAllowed(MachineSettings.winChance(ctx.config().get(), machine))) {
            m.actionBar(player, Msg.DERBY_GROUP_UNAVAILABLE);
            return;
        }
        if (entrants.containsKey(player.getUniqueId())) {
            return;
        }
        if (entrants.size() >= MAX_PLAYERS) {
            m.actionBar(player, Msg.DERBY_GROUP_FULL);
            return;
        }
        if (enrolling() && now >= enrollmentUntil) {
            m.actionBar(player, Msg.DERBY_GROUP_CLOSED);
            return;
        }
        if (!mayBet(player)) {
            return;
        }
        int lane = menu.stakedLane();
        List<StakeMenu.Stake> stakes = menu.stakes();
        if (lane < 0 || stakes.isEmpty()) {
            m.actionBar(player, Msg.DERBY_NO_BET);
            return;
        }
        long until = enrolling() ? enrollmentUntil : now + ENROLL_TICKS;
        long ends = ctx.clock().getAsLong() + (until - now + LONGEST_RACE_TICKS) * 50L + RECORD_ENDS_MILLIS;
        List<WagerEngine.Request> requests = new ArrayList<>();
        for (StakeMenu.Stake s : stakes) {
            requests.add(new WagerEngine.Request(machine.id(), GAME, s.amount(), List.of(s.amount()), ends,
                    "slot=" + s.slot() + " lane=" + lane + " group, refund until the start", RoundState.PLAYING)
                    .withTake(s.chips()));
        }
        List<UUID> rounds = commit(player, requests, Msg.DERBY_NO_BET);
        if (rounds == null) {
            menu.render();
            return;
        }
        if (!enrolling()) {
            enrollmentUntil = until;
            groupRace = true;
            lastCount = 0;
            Arrays.fill(displayed, null);
            displayUntil = -1;
            holdChunk(true);
            Log.get().info("duck derby #{}: group countdown started by {}", machine.record().number(),
                    player.getName());
        }
        entrants.put(player.getUniqueId(), new Entrant(player.getUniqueId(), player.getName(), lane,
                seats(stakes, rounds), ends));
        menu.clearStakes();
        Log.get().info("{} joins the group race on duck derby #{} with {} on lane {} ({} entrant(s))",
                player.getName(), machine.record().number(), stakes.stream().map(s -> s.amount().toString()).toList(),
                lane, entrants.size());
        player.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
    }

    /** {@code withdraw}: before the start the escrow comes back at once; the last one out cancels the countdown. */
    void withdraw(Player player) {
        World world = world();
        Entrant e = entrants.get(player.getUniqueId());
        if (world == null || e == null || !enrolling() || world.getGameTime() >= enrollmentUntil
                || !Usability.usable(player)) {
            return;
        }
        entrants.remove(player.getUniqueId());
        settle(e, SettleReason.NORMAL, false);
        Log.get().info("{} withdraws from the group race on duck derby #{}", player.getName(),
                machine.record().number());
        if (entrants.isEmpty()) {
            cancelEnrollment("everybody withdrew");
        }
    }

    /** "Gewinn abholen": delivers the viewer's owed items (the mod's {@code pay(player, true)}). */
    void claim(Player player) {
        if (Usability.usable(player)) {
            ctx.ledger().collect(player);
            owedCache.remove(player.getUniqueId());
        }
    }

    /** The bet checks of a click: usable, no danger (closes the menu if unsafe). */
    private boolean mayBet(Player player) {
        if (!Usability.usable(player)) {
            return false;
        }
        if (!ctx.menus().safe(player)) {
            ctx.messages().get().actionBar(player, Msg.ERROR_UNSAFE);
            player.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
            return false;
        }
        return true;
    }

    private static List<Seat> seats(List<StakeMenu.Stake> stakes, List<UUID> rounds) {
        List<Seat> out = new ArrayList<>();
        for (int i = 0; i < stakes.size(); i++) {
            out.add(new Seat(stakes.get(i).slot(), stakes.get(i).amount(), rounds.get(i)));
        }
        return out;
    }

    static String detail(int slot, int lane, DerbyRules.Race drawn, String why) {
        return "slot=" + slot + " lane=" + lane + " winner=" + drawn.winner() + " second=" + drawn.second()
                + " seed=" + drawn.seed() + " " + why;
    }

    // ---- countdown and launch --------------------------------------------------------------------------------------

    boolean enrolling() {
        return enrollmentUntil >= 0;
    }

    /**
     * {@code personalTick} during the countdown: entrants who quit or died were settled by those hooks (the refund)
     * and drop out; nobody left ends the countdown; at its end the race launches.
     */
    private void enrollTick(World world, long now) {
        entrants.values().removeIf(e -> {
            Player p = Bukkit.getPlayer(e.player);
            boolean gone = p == null || !p.isOnline() || p.isDead();
            if (gone) {
                Log.get().info("duck derby #{}: {} left the countdown (refunded by the quit/death)",
                        machine.record().number(), e.name);
            }
            return gone;
        });
        if (entrants.isEmpty()) {
            cancelEnrollment("nobody left");
        } else if (now >= enrollmentUntil) {
            launchGroup(world, now);
        }
    }

    private void cancelEnrollment(String why) {
        enrollmentUntil = -1;
        groupRace = false;
        holdChunk(false);
        Log.get().info("duck derby #{}: countdown ended without a race ({})", machine.record().number(), why);
    }

    /**
     * The countdown is over: the bets are locked, and only now are the winner ({@code nextInt(4)}), the seed and the
     * plan drawn; each entrant's records get his result in his own ledger write.
     */
    private void launchGroup(World world, long now) {
        int chance = MachineSettings.winChance(ctx.config().get(), machine);
        if (!DerbyRules.groupAllowed(chance)) {
            for (Entrant e : List.copyOf(entrants.values())) {
                settle(e, SettleReason.NORMAL, false);
            }
            entrants.clear();
            cancelEnrollment("group races no longer allowed: refunded");
            return;
        }
        long t0 = System.nanoTime();
        long tPlan;
        DerbyRules.Race drawn;
        try {
            drawn = DerbyRules.draw(true, -1, chance, ctx.random()::nextInt, ctx.random()::nextLong);
        } catch (IllegalStateException e) {
            // 16 seeds without a plan (about 1e-81 each): no race, everybody gets the refund
            for (Entrant en : List.copyOf(entrants.values())) {
                settle(en, SettleReason.NORMAL, false);
            }
            entrants.clear();
            cancelEnrollment("no race plan: refunded");
            return;
        }
        tPlan = System.nanoTime();
        int second = drawn.second();
        long ends = ctx.clock().getAsLong() + drawn.plan().endTick() * 50L + RECORD_ENDS_MILLIS;
        for (Entrant e : List.copyOf(entrants.values())) {
            Player p = Bukkit.getPlayer(e.player);
            WagerEngine.Revision r = p == null || !p.isOnline() || p.isDead() ? WagerEngine.Revision.GONE
                    : decide(p, e, drawn, second, ends);
            if (r != WagerEngine.Revision.DONE) {
                entrants.remove(e.player);
                Log.get().info("duck derby #{}: {} drops out at the start (records already settled)",
                        machine.record().number(), e.name);
            } else {
                e.endsMillis = ends;
            }
        }
        if (entrants.isEmpty()) {
            cancelEnrollment("nobody left at the start");
            return;
        }
        Log.get().info("duck derby #{}: group race starts with {} entrant(s) (draw and plan {} ms, records {} ms)",
                machine.record().number(), entrants.size(), ms(tPlan - t0), ms(System.nanoTime() - tPlan));
        launch(world, drawn, now);
    }

    /** One entrant's records: stake unchanged, the result as payout, DECIDED, in one write. */
    private WagerEngine.Revision decide(Player p, Entrant e, DerbyRules.Race drawn, int second, long ends) {
        try {
            PlayerAccount account = new PlayerAccount(p);
            de.tigges.kaboofcasino.wager.Ledger ledger = account.ledger();
            List<RoundRecord> revised = new ArrayList<>();
            for (Seat s : e.seats) {
                RoundRecord old = ledger.round(s.round()).orElse(null);
                if (old == null) {
                    return WagerEngine.Revision.GONE;
                }
                revised.add(new RoundRecord(old.id(), old.machine(), old.game(), RoundState.DECIDED, s.stake(),
                        DerbyRules.payout(s.stake(), e.lane, drawn.winner(), second), old.created(), ends,
                        detail(s.slot(), e.lane, drawn, "group")));
            }
            return ctx.engine().revise(account, revised, List.of());
        } catch (LedgerCodec.CorruptLedgerException ex) {
            Log.critical("LEDGER_UNREADABLE", "derby result for {} not recorded: {}", e.name, ex.getMessage());
            return WagerEngine.Revision.GONE;
        }
    }

    /** {@code launchPersonal}'s end: the race runs from now; every derby menu on this machine closes. */
    private void launch(World world, DerbyRules.Race drawn, long now) {
        race = new Race(drawn, now);
        enrollmentUntil = -1;
        lastCount = entrants.size();
        Arrays.fill(displayed, null);
        displayUntil = -1;
        for (Entrant e : entrants.values()) {
            race.won |= e.lane == drawn.winner();
            race.refunded |= e.lane == race.second;
            if (!groupRace) {
                for (Seat s : e.seats) {
                    displayed[s.slot()] = s.stake();
                }
            }
        }
        holdChunk(true);
        closeMenus();
        sound(world, "duck_start", START_SPOT, 1.0f, 1.0f);
        Log.get().info("duck derby #{}: race (winner {}, second {}, seed {}, {} entrant(s))",
                machine.record().number(), drawn.winner(), race.second, drawn.seed(), entrants.size());
    }

    // ---- the race --------------------------------------------------------------------------------------------------

    /** Every server tick while loaded; the world's game time is the clock; the heartbeat beats after the logic. */
    @Override
    public void tick(long tick) {
        World world = world();
        if (world == null || rig == null) {
            return;
        }
        long now = world.getGameTime();
        if (race != null ? raceStuck(now, race.start, race.plan().endTick())
                : enrolling() && countdownStuck(now, enrollmentUntil)) {
            unstick(now);
        }
        if (enrolling()) {
            enrollTick(world, now);
        }
        if (race != null) {
            raceTick(world, race, now);
        }
        if (displayUntil >= 0 && now >= displayUntil) {
            Arrays.fill(displayed, null);
            displayUntil = -1;
        }
        heartbeat.beat(ctx.clock().getAsLong());
        rig.update(view(now), now);
        if (idleLightsDue(world, ctx.config().get().derby(), now)) {
            rig.bulbs(bulbLevels(now));
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (MenuService.openMenu(p) instanceof DerbyBetMenu menu && menu.controller() == this) {
                menu.refresh(snapshot(p));
            }
        }
    }

    /**
     * The payout ({@code resultTick + 12}, when the mod's credits fall due), spread over the ticks + 12 .. + 16 in join
     * order so 64 entrants never cost one tick ~30 ms: step k settles the first {@code ceil(n * (k + 1) / 5)}.
     */
    private void payBatch(Race r, int step) {
        List<Entrant> all = List.copyOf(entrants.values());
        int upTo = payUpTo(all.size(), step);
        long t0 = System.nanoTime();
        while (r.payIndex < upTo) {
            settle(all.get(r.payIndex++), SettleReason.NORMAL, true);
        }
        long took = System.nanoTime() - t0;
        r.payNanos += took;
        r.payMaxNanos = Math.max(r.payMaxNanos, took);
        if (r.payIndex >= all.size()) {
            r.paid = true;
            Log.get().info("duck derby #{}: payout of {} entrant(s) took {} ms over {} tick(s) (largest tick {} ms)",
                    machine.record().number(), all.size(), ms(r.payNanos), step + 1, ms(r.payMaxNanos));
        }
    }

    /** How many entrants are paid after payout step {@code step} (0 = the due tick): all by step 4. */
    static int payUpTo(int entrants, int step) {
        if (step >= PAYOUT_TICKS - 1) {
            return entrants;
        }
        return (int) Math.min(entrants, ((long) entrants * (step + 1) + PAYOUT_TICKS - 1) / PAYOUT_TICKS);
    }

    private static String ms(long nanos) {
        return String.format(java.util.Locale.ROOT, "%.2f", nanos / 1e6);
    }

    /** A race past {@code endTick} + 600 ticks (its start counts as tick 0). */
    static boolean raceStuck(long now, long raceStart, int endTick) {
        return now - raceStart > (long) endTick + STUCK_AFTER_TICKS;
    }

    /** A countdown past its 200 ticks + 600. */
    static boolean countdownStuck(long now, long enrollmentUntil) {
        return enrollmentUntil >= 0 && now - (enrollmentUntil - ENROLL_TICKS) > ENROLL_TICKS + STUCK_AFTER_TICKS;
    }

    /** The guard: every entrant settles as stored (TIMEOUT), race and countdown end, the chunk is released. */
    private void unstick(long now) {
        Log.get().warn("duck derby #{}: {} stuck at tick {} ({} entrant(s)); they settle as stored, the machine is "
                + "free again", machine.record().number(), race != null ? "race" : "countdown",
                race != null ? now - race.start : now - (enrollmentUntil - ENROLL_TICKS), entrants.size());
        for (Entrant e : List.copyOf(entrants.values())) {
            settle(e, SettleReason.TIMEOUT, false);
        }
        entrants.clear();
        race = null;
        enrollmentUntil = -1;
        groupRace = false;
        Arrays.fill(displayed, null);
        displayUntil = -1;
        holdChunk(false);
    }

    private void raceTick(World world, Race r, long now) {
        DuckDerbyRace.Plan plan = r.plan();
        long elapsed = now - r.start;
        if (elapsed < 0) {
            r.start = now;
            elapsed = 0;
        }
        quacks(world, r, elapsed);
        splashes(world, plan, elapsed);
        if (r.stage < 1 && elapsed >= plan.winnerFinishTick()) {
            r.stage = 1;
            if (elapsed - plan.winnerFinishTick() <= 10) {
                finishLine(world, r);
            }
        }
        if (r.stage < 2 && elapsed >= plan.resultTick()) {
            r.stage = 2;
            Messages m = ctx.messages().get();
            for (Entrant e : entrants.values()) {
                Player p = Bukkit.getPlayer(e.player);
                if (p != null && p.isOnline()) {
                    m.actionBar(p, e.lane == plan.winner() ? Msg.DERBY_WIN
                            : e.lane == r.second ? Msg.DERBY_PUSH : Msg.DERBY_LOSS);
                }
            }
            displayUntil = now + PANEL_AFTER_RESULT;
            if (elapsed - plan.resultTick() <= 10) {
                announce(world, r);
            }
        }
        long sinceResult = elapsed - plan.resultTick();
        if (r.stage >= 2 && r.won && sinceResult >= 7 && sinceResult <= 14 && sinceResult % 7 == 0) {
            confetti(world, r, elapsed, 0.7);
        }
        if (r.stage >= 2 && !r.paid && sinceResult >= DUE_AFTER_RESULT) {
            payBatch(r, (int) (sinceResult - DUE_AFTER_RESULT));
        }
        if (r.stage >= 2 && r.paid && elapsed >= plan.endTick()) {
            race = null;
            entrants.clear();
            groupRace = false;
            holdChunk(false);
            Log.get().info("duck derby #{}: race over after {} ticks", machine.record().number(), elapsed);
        }
    }

    /**
     * Settles an entrant's records (refund in the countdown, the result in the race) with one ledger read and one write
     * ({@link WagerEngine#settleRounds}). With {@code effects} the mod's delivery cues follow ({@code DerbyCredits.pay}):
     * "N Gegenstände erhalten" and, within 16 blocks of the anchor block's centre, the payout sound there and five
     * happy-villager particles from the machine to the player. Offline or dead: the quit/death hooks settled them.
     */
    private void settle(Entrant e, SettleReason reason, boolean effects) {
        if (e.settled) {
            return;
        }
        e.settled = true;
        Player p = Bukkit.getPlayer(e.player);
        if (p == null || p.isDead()) {
            return;
        }
        WagerEngine.Settlement done;
        PlayerAccount account = new PlayerAccount(p);
        try {
            done = ctx.engine().settleRounds(account, e.seats.stream().map(Seat::round).toList(), reason);
        } catch (LedgerCodec.CorruptLedgerException ex) {
            Log.critical("LEDGER_UNREADABLE", "derby settlement of {} failed: {}", e.name, ex.getMessage());
            return;
        }
        owedCache.remove(e.player);
        // only this race's items count as "received" (deliverOwed may also hand out older owed items)
        long delivered = WagerEngine.deliveredOf(done);
        if (!done.rounds().isEmpty()) {
            try {
                List<ItemAmount> owed = account.ledger().owed();
                if (!owed.isEmpty()) {
                    ctx.messages().get().send(p, Msg.OWED_REMAINING, Messages.component("items",
                            de.tigges.kaboofcasino.util.ItemNames.list(owed)));
                }
            } catch (LedgerCodec.CorruptLedgerException ex) {
                Log.critical("LEDGER_UNREADABLE", "derby owed of {} unreadable: {}", e.name, ex.getMessage());
            }
        }
        if (!effects || delivered <= 0) {
            return;
        }
        ctx.messages().get().actionBar(p, Msg.DERBY_RECEIVED, Messages.text("count", Long.toString(delivered)));
        World world = world();
        if (world == null || !p.getWorld().equals(world)) {
            return;
        }
        Location centre = new Location(world, machine.anchor().x() + 0.5, machine.anchor().y() + 0.5,
                machine.anchor().z() + 0.5);
        if (p.getLocation().distanceSquared(centre) > (double) NEAR * NEAR) {
            return;
        }
        world.playSound(net.kyori.adventure.sound.Sound.sound(net.kyori.adventure.key.Key.key("roulettecasino",
                "duck_payout"), net.kyori.adventure.sound.Sound.Source.BLOCK, 0.43f, 1.0f),
                centre.getX(), centre.getY(), centre.getZ());
        double ox = machine.anchor().x();
        double oy = machine.anchor().y();
        double oz = machine.anchor().z();
        for (int n = 1; n <= 5; n++) {
            double f = n / 5.0;
            world.spawnParticle(Particle.HAPPY_VILLAGER, ox + 0.5 + (p.getX() - ox - 0.5) * f,
                    oy + 1 + (p.getY() + 0.7 - oy - 1.0) * f, oz + 0.5 + (p.getZ() - oz - 0.5) * f, 1, 0.03, 0.03,
                    0.03, 0.0);
        }
    }

    /** {@code quacks}: every planned quack up to now, at its duck. */
    private void quacks(World world, Race r, long elapsed) {
        List<DuckDerbyRace.Quack> quacks = r.plan().quacks();
        DuckDerbyRace.Pose pose = new DuckDerbyRace.Pose();
        while (r.quackIndex < quacks.size() && quacks.get(r.quackIndex).tick() <= elapsed) {
            DuckDerbyRace.Quack q = quacks.get(r.quackIndex++);
            r.plan().pose(q.lane(), q.tick(), pose);
            sound(world, "duck_quack", new double[] {DuckDerbyGeometry.LANE_CENTERS[q.lane()], 16.5, pose.z},
                    q.volume(), q.pitch());
        }
    }

    /** {@code splashes}: every 4 ticks from 30 to the return, two splashes behind every moving duck. */
    private void splashes(World world, DuckDerbyRace.Plan plan, long elapsed) {
        if (elapsed < 30 || elapsed >= plan.returnStartTick() || elapsed % 4 != 0) {
            return;
        }
        DuckDerbyRace.Pose pose = new DuckDerbyRace.Pose();
        for (int lane = 0; lane < DerbyRules.LANES; lane++) {
            if (!(plan.pace(lane, elapsed) * 46.0 < 0.12)) {
                plan.pose(lane, elapsed, pose);
                particle(world, Particle.SPLASH, new double[] {DuckDerbyGeometry.LANE_CENTERS[lane], 13.1,
                        pose.z + 3.4}, 2, 0.05, 0.0, 0.05, 0.01, null);
            }
        }
    }

    private void finishLine(World world, Race r) {
        double[] at = {DuckDerbyGeometry.LANE_CENTERS[r.plan().winner()], 17.5, 8.5};
        sound(world, "duck_finish", at, 1.1f, 1.0f);
        particle(world, Particle.FIREWORK, at, 14, 0.12, 0.12, 0.12, 0.12, null);
        particle(world, Particle.END_ROD, at, 10, 0.15, 0.15, 0.15, 0.08, null);
    }

    private void announce(World world, Race r) {
        DuckDerbyRace.Plan plan = r.plan();
        if (r.won) {
            sound(world, "duck_win", MIDDLE, 1.2f, 1.0f);
            confetti(world, r, plan.resultTick(), 1.0);
        } else if (r.refunded) {
            sound(world, "duck_push", MIDDLE, 0.95f, 1.0f);
            particle(world, Particle.END_ROD, winnerSpot(r, plan.resultTick(), 6.0), 8, 0.12, 0.12, 0.12, 0.06,
                    null);
        } else {
            sound(world, "duck_lose", MIDDLE, 0.95f, 1.0f);
            double[] at = winnerSpot(r, plan.resultTick(), 6.0);
            particle(world, Particle.FIREWORK, at, 10, 0.12, 0.12, 0.12, 0.1, null);
            particle(world, Particle.END_ROD, at, 8, 0.12, 0.12, 0.12, 0.06, null);
        }
    }

    private static double[] winnerSpot(Race r, long elapsed, double above) {
        DuckDerbyRace.Pose pose = new DuckDerbyRace.Pose();
        int winner = r.plan().winner();
        r.plan().pose(winner, elapsed, pose);
        return new double[] {DuckDerbyGeometry.LANE_CENTERS[winner], 12.5 + above, pose.z};
    }

    private void confetti(World world, Race r, long elapsed, double strength) {
        double[] duck = winnerSpot(r, elapsed, 8.0);
        int n = (int) Math.round(30.0 * strength);
        particle(world, Particle.DUST, duck, n, 0.35, 0.3, 0.35, 0.22, DUCK_CONFETTI[r.plan().winner()]);
        particle(world, Particle.DUST, duck, n, 0.4, 0.3, 0.4, 0.22, GOLD_CONFETTI);
        particle(world, Particle.FIREWORK, duck, (int) Math.round(34.0 * strength), 0.3, 0.3, 0.3, 0.26, null);
        particle(world, Particle.END_ROD, duck, (int) Math.round(24.0 * strength), 0.3, 0.3, 0.3, 0.2, null);
        if (strength >= 1.0) {
            particle(world, Particle.TOTEM_OF_UNDYING, duck, 26, 0.3, 0.35, 0.3, 0.3, null);
        }
        particle(world, Particle.DUST, RAIN, n, 1.2, 0.25, 1.5, 0.14, RED_CONFETTI);
        particle(world, Particle.DUST, RAIN, n, 1.2, 0.25, 1.5, 0.14, GOLD_CONFETTI);
        particle(world, Particle.DUST, RAIN, n, 1.2, 0.25, 1.5, 0.14, GREEN_CONFETTI);
    }

    /** {@code DuckDerbyRenderer}: {@code 0.28 + 0.72 * CasinoIdleLights.bulb(i, 12, lightTime)}. */
    private float[] bulbLevels(long gameTime) {
        double t = IdleLights.clock(gameTime, machine.anchor().x(), machine.anchor().y(), machine.anchor().z());
        float[] levels = new float[DuckDerbySignLights.BULBS];
        for (int i = 0; i < levels.length; i++) {
            levels[i] = (float) (0.28 + 0.72 * IdleLights.bulb(i, DuckDerbySignLights.BULBS, t));
        }
        return levels;
    }

    /** Holds or releases the anchor chunk while entrants wait or a race runs (counted plugin chunk ticket). */
    private void holdChunk(boolean hold) {
        World w = world();
        if (w == null || hold == chunkHeld) {
            return;
        }
        if (hold) {
            ctx.machines().holdChunk(w, machine.anchor().chunkX(), machine.anchor().chunkZ(), ctx.plugin());
        } else {
            ctx.machines().releaseChunk(w, machine.anchor().chunkX(), machine.anchor().chunkZ(), ctx.plugin());
        }
        chunkHeld = hold;
    }

    // ---- what the rig and the menus see ----------------------------------------------------------------------------

    /** {@code groupSession()}: a group race enrolls, runs or still shows its result. */
    private boolean shared(long now) {
        return groupRace && (race != null || enrolling() || (displayUntil >= 0 && now < displayUntil));
    }

    DerbyView view(long now) {
        List<DerbyMember> bettors = entrants.values().stream().map(Entrant::member).toList();
        return new DerbyView(race == null ? -1 : race.start, race == null ? null : race.plan(), shared(now),
                enrolling(), enrollmentUntil, entrants.isEmpty() ? lastCount : entrants.size(), bettors,
                Arrays.asList(displayed));
    }

    /** The viewer's owed entries, decoded at most every {@link #OWED_CACHE_TICKS} ticks (the menu asks every tick). */
    private int owedEntries(Player viewer, long now) {
        long[] cached = owedCache.get(viewer.getUniqueId());
        if (cached != null && now - cached[0] < OWED_CACHE_TICKS && now >= cached[0]) {
            return (int) cached[1];
        }
        int n;
        try {
            n = new PlayerAccount(viewer).ledger().owed().size();
        } catch (LedgerCodec.CorruptLedgerException e) {
            n = 0;
        }
        owedCache.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
        owedCache.put(viewer.getUniqueId(), new long[] {now, n});
        return n;
    }

    /** The lobby data of {@code viewer}'s bet menu ({@code DerbyLobby.json}). */
    DerbySnapshot snapshot(Player viewer) {
        World w = world();
        long now = w != null ? w.getGameTime() : viewer.getWorld().getGameTime();
        int seconds = enrolling() ? (int) Math.max(0, (enrollmentUntil - now + 19) / 20) : 0;
        Entrant own = enrolling() ? entrants.get(viewer.getUniqueId()) : null;
        int credits = owedEntries(viewer, now);
        return new DerbySnapshot(race != null, enrolling(), seconds, own != null,
                DerbyRules.groupAllowed(MachineSettings.winChance(ctx.config().get(), machine)), credits,
                entrants.values().stream().map(Entrant::member).toList(), own == null ? -1 : own.lane,
                own == null ? List.of() : own.seats.stream().map(Seat::stake).toList(), now);
    }
}
