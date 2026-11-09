package de.tigges.kaboofcasino.game.roulette;

import de.tigges.kaboofcasino.CasinoContext;
import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.game.GameMenu;
import de.tigges.kaboofcasino.game.MachineController;
import de.tigges.kaboofcasino.game.StakeMenu;
import de.tigges.kaboofcasino.gui.Usability;
import de.tigges.kaboofcasino.machine.Machine;
import de.tigges.kaboofcasino.machine.MachineSettings;
import de.tigges.kaboofcasino.util.FakePlayers;
import de.tigges.kaboofcasino.util.Log;
import de.tigges.kaboofcasino.wager.ItemAmount;
import de.tigges.kaboofcasino.wager.Ledger;
import de.tigges.kaboofcasino.wager.LedgerCodec;
import de.tigges.kaboofcasino.wager.PlayerAccount;
import de.tigges.kaboofcasino.wager.RoundRecord;
import de.tigges.kaboofcasino.wager.RoundState;
import de.tigges.kaboofcasino.wager.SettleReason;
import de.tigges.kaboofcasino.wager.WagerEngine;
import java.util.ArrayDeque;
import java.util.ArrayList;
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
 * Runtime of one roulette table ({@code RouletteBlockEntity}): the menu with nine bet slots (three per colour, one
 * colour per spin), the spin (draw the pocket, commit one record per filled slot in one tick, play the mod's 160-tick
 * plan), the result at tick 160 (the resting frame is saved, every record settles, confetti and win/lose sound), the
 * payout stacks every third game tick from the next tick on, the unlock once all are out, and the floating panel
 * until 20 ticks after the result.
 *
 * <p>Several players (PLAN-2 C5, not in the mod): whoever stands alone at the table spins at once as in the mod. With
 * somebody else near, the first bet opens a window of {@code roulette.countdown-seconds} that is never extended (it
 * shortens once everybody near has bet), up to {@link #MAX_PLAYERS} players bet on their own colours. Until it closes
 * each bet is a PLAYING record paying the stake back (like the derby's group), so quit, death, stop or crash refund it;
 * only when the window has closed is the one pocket drawn, and each player's records get their result in his own ledger
 * write. One spin for all. Thread: MAIN.
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
    /** At most this many players on one spin. */
    static final int MAX_PLAYERS = 8;
    /** Whoever stands this near the table (blocks) makes a spin wait for bets. */
    static final double NEAR = 8.0;
    /** Once everybody near has bet, the ball rolls this soon (ticks). */
    static final int LAST_CALL_TICKS = 40;
    private static final Particle.DustOptions RED_CONFETTI = new Particle.DustOptions(Color.fromRGB(0xDF3B4B), 1.15f);
    private static final Particle.DustOptions GOLD_CONFETTI = new Particle.DustOptions(Color.fromRGB(0xFFD168), 1.25f);
    private static final Particle.DustOptions GREEN_CONFETTI = new Particle.DustOptions(Color.fromRGB(0x36D886),
            1.15f);

    private RouletteRig rig;
    private int frame;
    private Spin spin;
    private long panelUntil = -1;
    /** Who bet on the coming or the running spin, in the order they joined. */
    private final Map<UUID, Bettor> bettors = new LinkedHashMap<>();
    /** The server tick the betting window closes at, -1 when none is open. */
    private long windowEnd = -1;
    /** The seconds the panel shows now. */
    private long shownSeconds = -1;
    private boolean chunkHeld;

    /** One filled bet slot; its payout is empty until the pocket is drawn. */
    private record Slip(int slot, ItemAmount stake, List<ItemAmount> payout, UUID round) {
    }

    /** One player's bet on a spin. */
    private static final class Bettor {
        final UUID player;
        final String name;
        final int colour;
        List<Slip> slips;
        long endsMillis;
        boolean settled;

        Bettor(UUID player, String name, int colour, List<Slip> slips, long endsMillis) {
            this.player = player;
            this.name = name;
            this.colour = colour;
            this.slips = slips;
            this.endsMillis = endsMillis;
        }
    }

    /** One payout stack on its way to a winner. */
    private record Coin(String item, UUID player) {
    }

    /** A running spin. */
    private static final class Spin {
        final int pocket;
        final RouletteSpin.Plan plan;
        final long start;
        boolean decided;
        final ArrayDeque<Coin> coins = new ArrayDeque<>();

        Spin(int pocket, RouletteSpin.Plan plan, long start) {
            this.pocket = pocket;
            this.plan = plan;
            this.start = start;
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

    private boolean window() {
        return windowEnd >= 0;
    }

    /** Breaking is refused while the ball rolls or bets wait for it (their records are open). */
    @Override
    public boolean busy() {
        return spin != null || window();
    }

    /** Everybody opens his own menu: nobody holds the table. */
    @Override
    protected boolean holdsClaim(Player player) {
        return true;
    }

    @Override
    public void interact(Player player) {
        Messages m = ctx.messages().get();
        if (running()) {
            m.actionBar(player, busyMessage());
            return;
        }
        if (!mayOpen(player)) {
            return;
        }
        if (bettors.containsKey(player.getUniqueId())) {
            m.actionBar(player, Msg.ROULETTE_ALREADY, Messages.text("seconds", Long.toString(secondsLeft())));
            return;
        }
        if (bettors.size() >= MAX_PLAYERS) {
            m.actionBar(player, Msg.ROULETTE_FULL);
            return;
        }
        ctx.menus().open(player, newMenu(player));
    }

    private long secondsLeft() {
        return window() ? Math.max(0, windowEnd - Bukkit.getCurrentTick() + 19) / 20 : 0;
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
        for (Bettor b : bettors.values()) {
            if (!b.settled && b.slips.stream().anyMatch(s -> s.round().equals(roundId))) {
                return heartbeat.owns(ctx.clock().getAsLong(), b.endsMillis);
            }
        }
        return false;
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
        if (spin == null || !spin.decided) {
            // before the draw the records pay the stake back, after it the result
            settleAll(SettleReason.MACHINE_GONE);
        }
        spin = null;
        windowEnd = -1;
        bettors.clear();
        holdChunk(false);
        claimant = null;
        closeMenus();
    }

    private void settleAll(SettleReason reason) {
        for (Bettor b : bettors.values()) {
            for (Slip s : b.slips) {
                settleRound(b.player, b.name, s.round(), reason);
            }
            b.settled = true;
        }
    }

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

    // ---- SPIN ------------------------------------------------------------------------------------------------------

    /** The SPIN button: alone at the table the spin starts now, otherwise the bet joins the betting window. */
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
        if (bettors.containsKey(player.getUniqueId())) {
            m.actionBar(player, Msg.ROULETTE_ALREADY, Messages.text("seconds", Long.toString(secondsLeft())));
            return;
        }
        if (bettors.size() >= MAX_PLAYERS) {
            m.actionBar(player, Msg.ROULETTE_FULL);
            return;
        }
        World world = world();
        if (world == null) {
            return;
        }
        if (!window() && startsAlone(world, player)) {
            solo(player, menu, stakes, colour, world);
        } else {
            join(player, menu, stakes, colour, world);
        }
    }

    /** No betting window: it is switched off, the admin forces the odds of one colour, or nobody else is near. */
    private boolean startsAlone(World world, Player player) {
        var config = ctx.config().get();
        return config.roulette().countdownSeconds() <= 0 || MachineSettings.winChance(config, machine) >= 0
                || waiting(world, player.getUniqueId()) == 0;
    }

    /** Players near the table who could still bet ({@code self} and those who have bet do not count). */
    private long waiting(World world, UUID self) {
        Location c = centre(world);
        return world.getPlayers().stream().filter(p -> !p.getUniqueId().equals(self)
                && !bettors.containsKey(p.getUniqueId()) && !FakePlayers.isFake(p) && Usability.usable(p)
                && p.getLocation().distanceSquared(c) <= NEAR * NEAR).count();
    }

    /** The mod's spin: draw, commit every filled slot (one tick), start the 160-tick spin. */
    private void solo(Player player, RouletteMenu menu, List<StakeMenu.Stake> stakes, int colour, World world) {
        int pocket = RouletteGame.draw(colour, MachineSettings.winChance(ctx.config().get(), machine),
                ctx.random()::nextInt);
        RouletteSpin.Plan plan = RouletteSpin.plan(frame, pocket);
        long ends = ctx.clock().getAsLong() + plan.resultTick() * 50L;
        List<WagerEngine.Request> requests = new ArrayList<>();
        List<List<ItemAmount>> payouts = new ArrayList<>();
        for (StakeMenu.Stake s : stakes) {
            List<ItemAmount> payout = payout(s.amount(), colour, pocket);
            payouts.add(payout);
            requests.add(new WagerEngine.Request(machine.id(), GAME, s.amount(), payout, ends,
                    detail(pocket, colour, s.slot())).withTake(s.chips()));
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
        bettors.put(player.getUniqueId(), new Bettor(player.getUniqueId(), player.getName(), colour, slips, ends));
        menu.clearStakes();
        start(world, pocket, plan);
        Log.get().info("{} spins roulette #{} with {} on colour {}: pocket {}", player.getName(),
                machine.record().number(), stakes.stream().map(s -> s.amount().toString()).toList(), colour, pocket);
    }

    /** A bet into the betting window (opened by the first): the chips leave now, as a record paying them back. */
    private void join(Player player, RouletteMenu menu, List<StakeMenu.Stake> stakes, int colour, World world) {
        long now = Bukkit.getCurrentTick();
        long until = window() ? windowEnd : now + ctx.config().get().roulette().countdownSeconds() * 20L;
        long ends = ctx.clock().getAsLong() + (until - now + RouletteSpin.plan(frame, 0).resultTick()) * 50L + 5_000L;
        List<WagerEngine.Request> requests = new ArrayList<>();
        for (StakeMenu.Stake s : stakes) {
            requests.add(new WagerEngine.Request(machine.id(), GAME, s.amount(), List.of(s.amount()), ends,
                    "colour=" + colour + " slot=" + s.slot() + " refund until the start", RoundState.PLAYING)
                    .withTake(s.chips()));
        }
        List<UUID> rounds = commit(player, requests, Msg.ROULETTE_NO_BET);
        if (rounds == null) {
            menu.render();
            return;
        }
        if (!window()) {
            windowEnd = until;
            holdChunk(true);
            panelUntil = -1;
            Log.get().info("roulette #{}: betting window opened by {}", machine.record().number(), player.getName());
        }
        List<Slip> slips = new ArrayList<>();
        for (int i = 0; i < stakes.size(); i++) {
            slips.add(new Slip(stakes.get(i).slot(), stakes.get(i).amount(), List.of(), rounds.get(i)));
        }
        bettors.put(player.getUniqueId(), new Bettor(player.getUniqueId(), player.getName(), colour, slips, ends));
        menu.clearStakes();
        if (waiting(world, player.getUniqueId()) == 0) {
            windowEnd = Math.min(windowEnd, now + LAST_CALL_TICKS);
        }
        Log.get().info("{} joins roulette #{} with {} on colour {} ({} player(s))", player.getName(),
                machine.record().number(), stakes.stream().map(s -> s.amount().toString()).toList(), colour,
                bettors.size());
        player.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
        ctx.messages().get().actionBar(player, Msg.ROULETTE_JOINED, Messages.text("seconds",
                Long.toString(secondsLeft())));
        shownSeconds = -1; // the panel shows the new line at the next tick
    }

    private static List<ItemAmount> payout(ItemAmount stake, int colour, int pocket) {
        long n = RouletteGame.payout(colour, stake.count(), pocket);
        return n > 0 ? List.of(new ItemAmount(stake.item(), n)) : List.of();
    }

    private static String detail(int pocket, int colour, int slot) {
        return "pocket=" + pocket + " colour=" + colour + " slot=" + slot;
    }

    /** The ball rolls: every menu of this table closes and the panel shows who plays. */
    private void start(World world, int pocket, RouletteSpin.Plan plan) {
        spin = new Spin(pocket, plan, Bukkit.getCurrentTick());
        closeMenus();
        sound(world, "start", ORIGIN, 1.0f, 1.0f);
        if (rig != null) {
            rig.panel(panel(-1));
        }
        panelUntil = -1;
    }

    /** The panel: the mod's for a single player without a window, else one line per player. */
    private net.kyori.adventure.text.Component panel(long seconds) {
        Messages m = ctx.messages().get();
        List<Bettor> all = List.copyOf(bettors.values());
        if (all.size() == 1 && seconds < 0) {
            Bettor b = all.getFirst();
            return RoulettePanel.render(m, ctx.layout(), b.player, b.name, b.colour,
                    b.slips.stream().map(Slip::stake).toList());
        }
        return RoulettePanel.table(m, all.stream().map(b -> new RoulettePanel.Line(b.player, b.name, b.colour,
                b.slips.stream().mapToLong(s -> s.stake().count()).sum())).toList(), seconds);
    }

    /** Every tick of the window: who quit or died was refunded by that hook; at its end the spin starts. */
    private void windowTick(World world, long now) {
        bettors.values().removeIf(b -> {
            Player p = Bukkit.getPlayer(b.player);
            boolean gone = p == null || !p.isOnline() || p.isDead();
            if (gone) {
                Log.get().info("roulette #{}: {} left the betting window (refunded by the quit/death)",
                        machine.record().number(), b.name);
                shownSeconds = -1;
            }
            return gone;
        });
        if (bettors.isEmpty()) {
            closeWindow("nobody left");
            return;
        }
        if (now >= windowEnd) {
            startShared(world);
            return;
        }
        long seconds = secondsLeft();
        if (seconds != shownSeconds) {
            shownSeconds = seconds;
            rig.panel(panel(seconds));
            if (seconds <= 3) {
                sound(world, "tick", ORIGIN, 0.6f, 1.2f);
            }
        }
    }

    private void closeWindow(String why) {
        windowEnd = -1;
        shownSeconds = -1;
        bettors.clear();
        holdChunk(false);
        rig.hidePanel();
        Log.get().info("roulette #{}: betting window closed without a spin ({})", machine.record().number(), why);
    }

    /** The window has closed: only now the pocket is drawn; each player's records get the result in his own write. */
    private void startShared(World world) {
        windowEnd = -1;
        shownSeconds = -1;
        if (MachineSettings.winChance(ctx.config().get(), machine) >= 0) {
            // an admin set forced odds during the window: they are per colour, so nobody spins and all get their bet
            settleAll(SettleReason.NORMAL);
            closeWindow("forced odds set meanwhile: refunded");
            return;
        }
        int pocket = RouletteGame.draw(0, -1, ctx.random()::nextInt);
        RouletteSpin.Plan plan = RouletteSpin.plan(frame, pocket);
        long ends = ctx.clock().getAsLong() + plan.resultTick() * 50L;
        for (Bettor b : List.copyOf(bettors.values())) {
            Player p = Bukkit.getPlayer(b.player);
            if (p == null || !p.isOnline() || p.isDead() || !decide(p, b, pocket, ends)) {
                bettors.remove(b.player);
                Log.get().info("roulette #{}: {} drops out at the start (records already settled)",
                        machine.record().number(), b.name);
            }
        }
        if (bettors.isEmpty()) {
            closeWindow("nobody left at the start");
            return;
        }
        start(world, pocket, plan);
        Log.get().info("roulette #{} spins for {} player(s): pocket {}", machine.record().number(), bettors.size(),
                pocket);
    }

    /** One player's records: same stakes, the result as payout, DECIDED, in one ledger write. */
    private boolean decide(Player p, Bettor b, int pocket, long ends) {
        try {
            PlayerAccount account = new PlayerAccount(p);
            Ledger ledger = account.ledger();
            List<RoundRecord> revised = new ArrayList<>();
            List<Slip> slips = new ArrayList<>();
            for (Slip s : b.slips) {
                RoundRecord old = ledger.round(s.round()).orElse(null);
                if (old == null) {
                    return false;
                }
                List<ItemAmount> payout = payout(s.stake(), b.colour, pocket);
                revised.add(new RoundRecord(old.id(), old.machine(), old.game(), RoundState.DECIDED, s.stake(), payout,
                        old.created(), ends, detail(pocket, b.colour, s.slot())));
                slips.add(new Slip(s.slot(), s.stake(), payout, s.round()));
            }
            if (ctx.engine().revise(account, revised, List.of()) != WagerEngine.Revision.DONE) {
                return false;
            }
            b.slips = slips;
            b.endsMillis = ends;
            return true;
        } catch (LedgerCodec.CorruptLedgerException e) {
            Log.critical("LEDGER_UNREADABLE", "roulette result for {} not recorded: {}", b.name, e.getMessage());
            return false;
        }
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
        if (window()) {
            windowTick(world, now);
        }
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
            for (Bettor b : bettors.values()) {
                ArrayDeque<String> stacks = new ArrayDeque<>();
                for (Slip slip : b.slips) {
                    settleRound(b.player, b.name, slip.round(), SettleReason.NORMAL);
                    queueCoins(stacks, slip.payout());
                    paid |= !slip.payout().isEmpty();
                }
                b.settled = true;
                stacks.forEach(item -> s.coins.add(new Coin(item, b.player)));
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
                    Coin coin = s.coins.poll();
                    rig.coin(coin.item(), coinTarget(world, coin.player()), now);
                    sound(world, "payout", ORIGIN, 0.43f, 0.95f + ctx.random().nextFloat() * 0.2f);
                }
            } else {
                spin = null;
                bettors.clear();
                holdChunk(false);
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
