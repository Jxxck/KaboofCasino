package de.tigges.kaboofcasino.game.blackjack;

import de.tigges.kaboofcasino.CasinoContext;
import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.game.GameMenu;
import de.tigges.kaboofcasino.game.MachineController;
import de.tigges.kaboofcasino.game.StakeMenu;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;

/**
 * Runtime of one blackjack table ({@code BlackjackTableBlockEntity}). One player per table, one hand against the
 * drawn dealer, as in the mod (games.md 3 "Multiplayer": the five spots are drawn, only the owner plays; others get
 * "busy").
 * <ul>
 *   <li><b>DEAL</b>: the stakes of the three bet slots are committed in one tick as PLAYING records whose payout is
 *       the hand's stand result ({@link BlackjackStand} on a copy of the shoe), then the mod's
 *       {@link BlackjackRound} runs on the table's shoe and the round menu opens.</li>
 *   <li><b>HIT / DOUBLE</b>: the new stand result is computed on a copy first, the records are rewritten (double:
 *       together with the second stake, {@link WagerEngine#revise}) and only then the live round deals the card;
 *       the live round deals exactly the predicted cards. STAND changes no record.</li>
 *   <li><b>Quit, kick, stop, crash, death, watchdog</b> settle the records as they stand: the stand result (PLAN
 *       decision 2). Once the owner is gone the live hand stands at once, so the table shows what was paid.</li>
 *   <li>The decision: the payout is checked against the records (rewritten if they differ, which only a bug could
 *       cause), the records settle 22 ticks later with the first payout coin, one coin every 3 ticks, the table is
 *       free 15 ticks after the round is over and the coins are out (or at once on the owner's next click).</li>
 * </ul>
 * The listener mirrors the mod's {@code Events}: the table view ({@link BlackjackTableState}) for the rig, sounds at
 * their scheduled round ticks, confetti on a win.
 *
 * <p>Several players (PLAN-2 C5, not in the mod): alone at the table everything above applies. With somebody else near,
 * the first bet opens a window of {@code blackjack.countdown-seconds}, up to five players take a spot each; until it
 * closes a bet is a PLAYING record paying the stake back, so quit, death or stop refund it. Then one
 * {@link BlackjackTableRound} runs: each seat's records hold what it gets if nobody draws another card (rewritten for
 * everybody when somebody draws), the seats play in spot order, one dealer plays for all, and each seat is confirmed,
 * settled and paid on its own; quit or death of one settles his records as they stand and his hand stands, the others
 * play on. Thread: MAIN.
 */
public final class BlackjackController extends MachineController {

    static final String GAME = "blackjack";
    /** {@code PAYOUT_DELAY}, {@code PAYOUT_STEP}, {@code ++endHold > 14}. */
    static final int PAYOUT_DELAY = 22;
    static final int PAYOUT_STEP = 3;
    static final int END_HOLD = 14;
    /** Sounds play at the anchor block ({@code playSound(null, worldPosition)}: its centre), in table pixels. */
    static final double[] ANCHOR_CENTRE = {24, 8, 24};
    /** {@code isInRange}: the menu stays valid within 8 blocks of this table point. */
    static final double[] RANGE_POINT = {32, 14, 12};
    static final double RANGE = 8.0;
    /** A PLAYING record's watchdog end: this long after its last revision (the controller owns it while it ticks). */
    static final long RECORD_ENDS_MILLIS = 60_000;
    /** Five seats of 30 s each plus the deal: a table's records may wait this long for their next rewrite. */
    static final long TABLE_RECORD_ENDS_MILLIS = 300_000;
    /** Whoever stands this near the table (blocks) makes a hand wait for bets. */
    static final double NEAR = 8.0;
    /** Once everybody near has bet, the cards come this soon (ticks). */
    static final int LAST_CALL_TICKS = 40;
    private static final Particle.DustOptions RED_CONFETTI = new Particle.DustOptions(Color.fromRGB(0xDF3B4B), 1.15f);
    private static final Particle.DustOptions GOLD_CONFETTI = new Particle.DustOptions(Color.fromRGB(0xFFD168), 1.25f);
    private static final Particle.DustOptions GREEN_CONFETTI = new Particle.DustOptions(Color.fromRGB(0x36D886),
            1.15f);

    private BlackjackRig rig;
    /** Created at the first DEAL ({@code BlackjackTableBlockEntity} 355-357); not saved, as in the mod. */
    private BlackjackShoe shoe;
    private Hand hand;
    private final BlackjackTableState idleView = new BlackjackTableState();
    private BlackjackPanel.Content shownPanel;
    /** A running round with several seats. */
    private Table table;
    /** Who has bet in the open window, in the order they joined. */
    private final Map<UUID, TableSeat> waiting = new LinkedHashMap<>();
    /** The game tick the betting window closes at, -1 when none is open. */
    private long windowEnd = -1;
    private BlackjackPanel.TableContent shownTable;

    /** One player's place at a table with several players. */
    private static final class TableSeat {
        final UUID player;
        final String name;
        final int spot;
        final long seed;
        List<Seat> stakes;
        long endsMillis;
        boolean gone;
        BlackjackRound.Result result = BlackjackRound.Result.NONE;

        TableSeat(UUID player, String name, int spot, long seed, List<Seat> stakes, long endsMillis) {
            this.player = player;
            this.name = name;
            this.spot = spot;
            this.seed = seed;
            this.stakes = stakes;
            this.endsMillis = endsMillis;
        }

        boolean holds(UUID round) {
            return stakes.stream().anyMatch(s -> s.round().equals(round));
        }

        List<ItemAmount> staked() {
            return ItemAmount.merge(stakes.stream().map(Seat::stake).toList());
        }
    }

    private record TableCoin(String item, int spot, UUID player) {
    }

    /** A running round with several seats: what {@link Hand} is for one. */
    private final class Table implements BlackjackTableRound.Listener {
        final List<TableSeat> seats;
        final long start;
        long ticked;
        final BlackjackTableState view;
        final List<ScheduledSound> sounds = new ArrayList<>();
        final Set<String> resultSounds = new HashSet<>();
        final ArrayDeque<TableCoin> coins = new ArrayDeque<>();
        BlackjackTableRound round;
        boolean decided;
        int payoutWait;
        boolean settled;
        int endHold;

        Table(List<TableSeat> seats, long start) {
            this.seats = seats;
            this.start = start;
            this.ticked = start;
            this.view = new BlackjackTableState(start, seats.getFirst().spot);
            this.view.phaseChanged(BlackjackRound.Phase.DEALING, 0);
        }

        int index(UUID player) {
            for (int i = 0; i < seats.size(); i++) {
                if (seats.get(i).player.equals(player)) {
                    return i;
                }
            }
            return -1;
        }

        private void schedule(int at, String id, float volume, float pitch) {
            sounds.add(new ScheduledSound(at, id, volume, pitch));
        }

        @Override
        public void shuffled(int at) {
            view.shuffled(at);
            schedule(at, "card_shuffle", 0.9f, 1.0f);
        }

        @Override
        public void cardDealt(int seat, int index, int card, boolean faceUp, int at) {
            boolean toDealer = seat == BlackjackTableRound.DEALER;
            if (!toDealer) {
                view.spot = seats.get(seat).spot;
            }
            view.cardDealt(toDealer, index, card, faceUp, at);
            float pitch = 0.94f + index * 0.04f + (toDealer ? 0.05f : 0.0f);
            schedule(at + 7, "card_flip", 0.3f, 0.75f);
            schedule(at + 14, "card_deal", 0.8f, pitch);
        }

        @Override
        public void holeCardRevealed(int card, int at) {
            view.holeCardRevealed(card, at);
            schedule(at + 4 + 2, "card_flip", 0.9f, 1.0f);
        }

        @Override
        public void dealerPeek(boolean blackjack, int at) {
            view.dealerPeek(blackjack, at);
            schedule(at + 5, "card_flip", 0.4f, 0.7f);
        }

        @Override
        public void turnOpened(int seat, int deadline, int at) {
            view.spot = seats.get(seat).spot;
            view.turnOpened(deadline, at);
            schedule(at, "tick", 0.5f, 1.5f);
            Messages m = ctx.messages().get();
            TableSeat acting = seats.get(seat);
            Log.get().info("blackjack #{}: turn of {}", machine.record().number(), acting.name);
            for (TableSeat s : seats) {
                Player p = Bukkit.getPlayer(s.player);
                if (p != null && !s.gone) {
                    if (s == acting) {
                        m.actionBar(p, Msg.BLACKJACK_YOUR_TURN);
                    } else {
                        m.actionBar(p, Msg.BLACKJACK_WAITING, Messages.text("name", acting.name));
                    }
                }
            }
        }

        @Override
        public void turnTimedOut(int seat, int at) {
            view.turnTimedOut(at);
            TableSeat s = seats.get(seat);
            Player p = Bukkit.getPlayer(s.player);
            if (p != null && !s.gone) {
                ctx.messages().get().actionBar(p, Msg.BLACKJACK_TIMEOUT);
            }
        }

        @Override
        public void phaseChanged(BlackjackRound.Phase phase, int at) {
            view.phaseChanged(phase, at);
        }

        @Override
        public void decided(int seat, BlackjackRound.Result result, int at) {
            view.decided(result, at);
            TableSeat s = seats.get(seat);
            s.result = result;
            decided = true;
            payoutWait = PAYOUT_DELAY;
            Log.get().info("blackjack #{}: hand of {} ends {} (player {} = {}, dealer {} = {}{}{})",
                    machine.record().number(), s.name, result, cards(round.hand(seat)),
                    BlackjackCards.total(round.hand(seat)), cards(round.dealer()),
                    BlackjackCards.total(round.dealer()), round.doubled(seat) ? ", doubled" : "",
                    s.gone ? ", owner gone" : "");
            Player p = Bukkit.getPlayer(s.player);
            if (p != null && !p.isDead() && !s.gone) {
                reviseSeat(p, this, seat, s.stakes, z -> BlackjackPayout.payout(z.stake(), result,
                        BlackjackPayout.roll(s.seed, z.slot())), "result " + result, List.of());
            }
            World world = world();
            if (world != null) {
                String sound = result.isWin() ? "win" : result.isPush() ? "blackjack_push" : "lose";
                if (result.isWin()) {
                    celebrate(world, s.spot, result == BlackjackRound.Result.BLACKJACK);
                }
                if (resultSounds.add(sound)) {
                    schedule(at + 6, sound, result.isWin() ? 1.1f : 0.95f, 1.0f);
                }
            }
        }

        @Override
        public void reaction(BlackjackRound.Reaction reaction, int at) {
            view.reaction(reaction, at);
        }

        @Override
        public void cleared(int at) {
            view.cleared(at);
            schedule(at + 7, "card_deal", 0.55f, 0.8f);
        }

        BlackjackPanel.TableContent content(long now) {
            List<BlackjackPanel.SeatLine> lines = new ArrayList<>();
            for (int i = 0; i < seats.size(); i++) {
                TableSeat s = seats.get(i);
                int result = view.resultAt >= 0 && now >= view.resultAt + 4 ? s.result.code : 0;
                lines.add(new BlackjackPanel.SeatLine(s.player, s.name, s.staked(),
                        BlackjackPanel.seatTotal(view, s.spot, now), result, round.currentSeat() == i));
            }
            return new BlackjackPanel.TableContent(lines, BlackjackPanel.dealerTotal(view, now), -1);
        }

        BlackjackSnapshot snapshot(int seat, Player viewer, long now) {
            TableSeat s = seats.get(seat);
            BlackjackTableRound r = round;
            boolean mine = r.currentSeat() == seat;
            int phase = switch (r.phase()) {
                case DEALING -> BlackjackSnapshot.PHASE_DEALING;
                case PLAYER_TURN -> mine ? BlackjackSnapshot.PHASE_TURN : BlackjackSnapshot.PHASE_DEALING;
                case PLAYER_DRAW -> mine ? BlackjackSnapshot.PHASE_DRAWING : BlackjackSnapshot.PHASE_DEALING;
                case DEALER_TURN -> BlackjackSnapshot.PHASE_DEALER;
                default -> BlackjackSnapshot.PHASE_RESULT;
            };
            List<Long> playerAt = new ArrayList<>();
            List<Long> dealerAt = new ArrayList<>();
            for (BlackjackCardMotion.Card c : view.cards) {
                if (c.toDealer) {
                    dealerAt.add(c.dealtAt);
                } else if (c.spot == s.spot) {
                    playerAt.add(c.dealtAt);
                }
            }
            int left = mine && r.phase() == BlackjackRound.Phase.PLAYER_TURN ? Math.max(0, r.deadline() - r.now()) : 0;
            List<ItemAmount> shown = s.stakes.stream().map(Seat::stake).toList();
            return new BlackjackSnapshot(phase, r.hand(seat), r.dealerVisible(), playerAt, dealerAt, left,
                    r.result(seat).code, r.canHit(seat), r.canStand(seat), r.canDouble(seat), r.doubled(seat),
                    r.canDouble(seat) && afford(viewer, shown), shown, now);
        }
    }

    /** One filled bet slot of the running hand. */
    record Seat(int slot, ItemAmount stake, UUID round) {
    }

    private record ScheduledSound(int at, String id, float volume, float pitch) {
    }

    /** The running hand ({@code round != null} in the mod). */
    private final class Hand implements BlackjackRound.Listener {
        final UUID player;
        final String name;
        final int spot;
        final long seed;
        /**
         * World game time of the DEAL click (the clock of the view, the rig and the menu, like the mod's level game
         * time); the round's clock starts on the next game tick (round tick 1 = start + 1).
         */
        final long start;
        /** The last game tick the round ticked at: the round pauses while the world does not tick (/tick freeze). */
        long ticked;
        /** The records' scheduled end (last revision + 60 s): the watchdog's grace counts from here. */
        long endsMillis;
        final int bias;
        final BlackjackTableState view;
        final List<Seat> seats;
        final List<ScheduledSound> sounds = new ArrayList<>();
        final ArrayDeque<String> coins = new ArrayDeque<>();
        BlackjackRound round;
        BlackjackRound.Result decided = BlackjackRound.Result.NONE;
        int payoutWait;
        boolean settled;
        int endHold;
        /** The owner quit, died or was kicked: his records are settled, the hand stands at its next chance. */
        boolean ownerGone;
        boolean dirty = true;

        Hand(UUID player, String name, int spot, long seed, int bias, long start, List<Seat> seats) {
            this.player = player;
            this.name = name;
            this.spot = spot;
            this.seed = seed;
            this.bias = bias;
            this.start = start;
            this.ticked = start;
            this.view = new BlackjackTableState(start, spot);
            this.view.phaseChanged(BlackjackRound.Phase.DEALING, 0);
            this.seats = new ArrayList<>(seats);
        }

        private void schedule(int at, String id, float volume, float pitch) {
            sounds.add(new ScheduledSound(at, id, volume, pitch));
        }

        @Override
        public void shuffled(int at) {
            view.shuffled(at);
            schedule(at, "card_shuffle", 0.9f, 1.0f);
            dirty = true;
        }

        @Override
        public void cardDealt(boolean toDealer, int index, int card, boolean faceUp, int at) {
            view.cardDealt(toDealer, index, card, faceUp, at);
            float pitch = 0.94f + index * 0.04f + (toDealer ? 0.05f : 0.0f);
            schedule(at + 7, "card_flip", 0.3f, 0.75f);
            schedule(at + 14, "card_deal", 0.8f, pitch);
            dirty = true;
        }

        @Override
        public void holeCardRevealed(int card, int at) {
            view.holeCardRevealed(card, at);
            schedule(at + 4 + 2, "card_flip", 0.9f, 1.0f);
            dirty = true;
        }

        @Override
        public void dealerPeek(boolean blackjack, int at) {
            view.dealerPeek(blackjack, at);
            schedule(at + 5, "card_flip", 0.4f, 0.7f);
            dirty = true;
        }

        @Override
        public void turnOpened(int deadline, int at) {
            view.turnOpened(deadline, at);
            schedule(at, "tick", 0.5f, 1.5f);
            dirty = true;
        }

        @Override
        public void turnTimedOut(int at) {
            view.turnTimedOut(at);
            Player p = Bukkit.getPlayer(player);
            if (p != null && !ownerGone) {
                ctx.messages().get().actionBar(p, Msg.BLACKJACK_TIMEOUT);
            }
            dirty = true;
        }

        @Override
        public void phaseChanged(BlackjackRound.Phase phase, int at) {
            view.phaseChanged(phase, at);
            dirty = true;
        }

        @Override
        public void decided(BlackjackRound.Result result, int at) {
            view.decided(result, at);
            view.doubled = round != null && round.doubled();
            decided = result;
            payoutWait = PAYOUT_DELAY;
            Log.get().info("blackjack #{}: hand of {} ends {} (player {} = {}, dealer {} = {}{}{})",
                    machine.record().number(), name, result, cards(round.player()), BlackjackCards.total(round.player()),
                    cards(round.dealer()), BlackjackCards.total(round.dealer()), round.doubled() ? ", doubled" : "",
                    ownerGone ? ", owner gone" : "");
            confirm(this, result);
            World world = world();
            if (world != null) {
                if (result.isWin()) {
                    schedule(at + 6, "win", 1.1f, 1.0f);
                    celebrate(world, spot, result == BlackjackRound.Result.BLACKJACK);
                } else if (result.isPush()) {
                    schedule(at + 6, "blackjack_push", 0.95f, 1.0f);
                } else {
                    schedule(at + 6, "lose", 0.95f, 1.0f);
                }
            }
            dirty = true;
        }

        @Override
        public void reaction(BlackjackRound.Reaction reaction, int at) {
            view.reaction(reaction, at);
            dirty = true;
        }

        @Override
        public void cleared(int at) {
            view.cleared(at);
            schedule(at + 7, "card_deal", 0.55f, 0.8f);
            dirty = true;
        }
    }

    public BlackjackController(Machine machine, CasinoContext ctx) {
        super(machine, ctx);
    }

    // ---- MachineGame -----------------------------------------------------------------------------------------------

    @Override
    protected boolean running() {
        return hand != null || table != null;
    }

    private boolean windowOpen() {
        return windowEnd >= 0;
    }

    /** Breaking is refused while cards are out or bets wait for them (their records are open). */
    @Override
    public boolean busy() {
        return running() || windowOpen();
    }

    /** Everybody opens his own betting menu: nobody holds the table. */
    @Override
    protected boolean holdsClaim(Player player) {
        return true;
    }

    @Override
    protected Msg busyMessage() {
        return Msg.BLACKJACK_BUSY;
    }

    /** Table pixels ({@code BlackjackLayout.toBlock}: (24, 24) is the anchor block's centre) to anchor offsets. */
    @Override
    protected double[] toBlock(double x, double y, double z) {
        return BlackjackLayout.toBlock(x, y, z, machine.facing().turns());
    }

    @Override
    protected GameMenu newMenu(Player player) {
        Hand h = hand;
        Table t = table;
        if (h != null && h.player.equals(player.getUniqueId()) || waiting.containsKey(player.getUniqueId())
                || t != null && t.index(player.getUniqueId()) >= 0) {
            return new BlackjackRoundMenu(player.getUniqueId(), this, ctx);
        }
        return new BlackjackBetMenu(player.getUniqueId(), this, ctx);
    }

    /**
     * {@code BlackjackTableBlockEntity.use}: a finished hand of the clicking owner ends at once; someone else's hand,
     * or someone else holding the betting menu, is "busy"; the owner reopens his running hand.
     */
    @Override
    public void interact(Player player) {
        Messages m = ctx.messages().get();
        UUID id = player.getUniqueId();
        Table t = table;
        if (t != null) {
            if (t.index(id) < 0) {
                m.actionBar(player, Msg.BLACKJACK_BUSY, Messages.text("name", t.seats.getFirst().name));
            } else if (mayOpen(player)) {
                ctx.menus().open(player, newMenu(player));
            }
            return;
        }
        if (waiting.containsKey(id)) {
            if (mayOpen(player)) {
                ctx.menus().open(player, newMenu(player));
            }
            return;
        }
        if (waiting.size() >= BlackjackLayout.SPOTS) {
            m.actionBar(player, Msg.BLACKJACK_FULL);
            return;
        }
        Hand h = hand;
        if (h != null && h.player.equals(id) && h.round.isOver() && h.settled && h.coins.isEmpty()) {
            finish();
            h = null;
        }
        if (h != null && !h.player.equals(id)) {
            m.actionBar(player, Msg.BLACKJACK_BUSY, Messages.text("name", h.name));
            return;
        }
        if (!mayOpen(player)) {
            return;
        }
        if (h == null) {
            claimant = id;
        }
        if (!ctx.menus().open(player, newMenu(player)) && hand == null) {
            claimant = null;
        }
    }

    /**
     * {@code BlackjackMenu.stillValid} / {@code isInRange}: the table exists, the viewer is within 8 blocks of table
     * point (32, 14, 12) and, while a hand runs, he is its owner.
     */
    @Override
    public boolean menuValid(Player player) {
        if (ctx.machines().byId(machine.id()).isEmpty()) {
            return false;
        }
        World w = world();
        if (w == null || !player.getWorld().equals(w)) {
            return false;
        }
        Hand h = hand;
        if (h != null && !h.player.equals(player.getUniqueId())) {
            return false;
        }
        Table t = table;
        if (t != null && t.index(player.getUniqueId()) < 0) {
            return false;
        }
        return player.getLocation().distanceSquared(at(w, RANGE_POINT)) <= RANGE * RANGE;
    }

    @Override
    public void spawn() {
        World world = world();
        if (world == null) {
            return;
        }
        if (rig == null) {
            // the budget is shared with the other machines' idle lights, which count server ticks: same clock here
            rig = new BlackjackRig(machine.record(), world,
                    gameTime -> ctx.lights().grant(machine.id(), Bukkit.getCurrentTick()));
        }
        rig.spawn();
        shownPanel = null;
        shownTable = null;
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
        long clock = ctx.clock().getAsLong();
        for (TableSeat s : waiting.values()) {
            if (s.holds(roundId)) {
                return owns(heartbeat, clock, s.endsMillis, false);
            }
        }
        Table t = table;
        if (t != null) {
            for (TableSeat s : t.seats) {
                if (s.holds(roundId)) {
                    return owns(heartbeat, clock, s.endsMillis, t.settled);
                }
            }
        }
        Hand h = hand;
        return h != null && owns(heartbeat, ctx.clock().getAsLong(), h.endsMillis, h.settled)
                && h.seats.stream().anyMatch(s -> s.round().equals(roundId));
    }

    /**
     * The hand still settles its records itself: not settled yet, the controller ran its hand logic within the
     * last 2 s, and the records were revised (deal, hit, double, result) less than their 60 s end plus the 60 s grace
     * ago ({@link Heartbeat#owns}); a stuck hand (exception loop, no rig) hands them to the ledger watchdog.
     */
    static boolean owns(Heartbeat heartbeat, long nowMillis, long endsMillis, boolean settled) {
        return !settled && heartbeat.owns(nowMillis, endsMillis);
    }

    @Override
    public void checkRig() {
        if (rig != null && rig.spawned() && rig.broken()) {
            Log.get().info("blackjack #{}: rig entities vanished, respawning", machine.record().number());
            rig.despawn();
            spawn();
        }
    }

    /**
     * The table is removed while loaded ({@code onTableBroken}): an undecided hand is refunded (the mod ejects the
     * wager), a decided one pays its result.
     */
    @Override
    public void removed() {
        Hand h = hand;
        if (h != null && !h.settled) {
            h.settled = true;
            if (h.decided == BlackjackRound.Result.NONE) {
                Player p = Bukkit.getPlayer(h.player);
                if (p != null && !p.isDead()) {
                    revise(p, h, s -> List.of(s.stake()), "refund (table removed)", List.of());
                }
            }
            for (Seat s : h.seats) {
                settleRound(h.player, h.name, s.round(), SettleReason.MACHINE_GONE);
            }
        }
        if (h != null) {
            h.round.abort();
        }
        hand = null;
        for (TableSeat s : waiting.values()) {
            for (Seat z : s.stakes) {
                settleRound(s.player, s.name, z.round(), SettleReason.MACHINE_GONE);
            }
        }
        waiting.clear();
        windowEnd = -1;
        Table t = table;
        if (t != null) {
            if (!t.settled) {
                t.settled = true;
                for (int i = 0; i < t.seats.size(); i++) {
                    TableSeat s = t.seats.get(i);
                    Player p = Bukkit.getPlayer(s.player);
                    if (!t.decided && p != null && !p.isDead()) {
                        reviseSeat(p, t, i, s.stakes, z -> List.of(z.stake()), "refund (table removed)", List.of());
                    }
                    for (Seat z : s.stakes) {
                        settleRound(s.player, s.name, z.round(), SettleReason.MACHINE_GONE);
                    }
                }
            }
            t.round.abort();
            table = null;
        }
        keepChunkLoaded(false);
        claimant = null;
        closeMenus();
    }

    // ---- DEAL, HIT, STAND, DOUBLE ----------------------------------------------------------------------------------

    /** DEAL (button 0): commits the stakes and starts the hand; opens the round menu. */
    void deal(Player player, StakeMenu menu) {
        Messages m = ctx.messages().get();
        if (!mayCommit(player)) {
            return;
        }
        List<StakeMenu.Stake> stakes = menu.stakes();
        if (stakes.isEmpty()) {
            m.actionBar(player, Msg.BLACKJACK_NO_BET);
            return;
        }
        if (waiting.containsKey(player.getUniqueId())) {
            m.actionBar(player, Msg.BLACKJACK_ALREADY, Messages.text("seconds", Long.toString(secondsLeft())));
            return;
        }
        if (windowOpen() || playsWithOthers(player)) {
            join(player, menu, stakes);
            return;
        }
        if (shoe == null) {
            shoe = new BlackjackShoe(new ShoeRandom(ctx.random().nextLong()));
        }
        int bias = MachineSettings.bias(ctx.config().get(), machine);
        long seed = ctx.random().nextLong();
        // the live round shuffles in its constructor when the shoe is low: the copy does the same with the same random
        BlackjackShoe sim = shoe.copy();
        if (sim.needsShuffle()) {
            sim.shuffle();
        }
        BlackjackRound.Result stand = BlackjackStand.stand(List.of(), List.of(), false, sim, bias);
        long ends = ctx.clock().getAsLong() + RECORD_ENDS_MILLIS;
        List<WagerEngine.Request> requests = new ArrayList<>();
        for (StakeMenu.Stake s : stakes) {
            requests.add(new WagerEngine.Request(machine.id(), GAME, s.amount(),
                    BlackjackPayout.payout(s.amount(), stand, BlackjackPayout.roll(seed, s.slot())), ends,
                    detail(s.slot(), List.of(), List.of(), false, stand, "deal"), RoundState.PLAYING)
                    .withTake(s.chips()));
        }
        List<UUID> rounds = commit(player, requests, Msg.BLACKJACK_NO_BET);
        if (rounds == null) {
            menu.render();
            return;
        }
        List<Seat> seats = new ArrayList<>();
        for (int i = 0; i < stakes.size(); i++) {
            seats.add(new Seat(stakes.get(i).slot(), stakes.get(i).amount(), rounds.get(i)));
        }
        double[] p = tablePosition(player.getLocation());
        int spot = BlackjackLayout.nearestSpot(p[0], p[1]);
        World w = player.getWorld();
        Hand h = new Hand(player.getUniqueId(), player.getName(), spot, seed, bias, w.getGameTime(), seats);
        h.endsMillis = ends;
        hand = h;
        h.round = new BlackjackRound(shoe, h, bias);
        claimant = player.getUniqueId();
        menu.clearStakes();
        Log.get().info("{} deals blackjack #{} with {} at spot {} (bias {}, shoe {} cards)", player.getName(),
                machine.record().number(), stakes.stream().map(s -> s.amount().toString()).toList(), spot, bias,
                shoe.remaining());
        ctx.menus().open(player, new BlackjackRoundMenu(player.getUniqueId(), this, ctx));
    }

    // ---- a table with several players ---------------------------------------------------------------------------------

    /** True if this bet waits for others: the window exists, nobody forces the cards, and somebody else is near. */
    private boolean playsWithOthers(Player player) {
        var config = ctx.config().get();
        return config.blackjack().countdownSeconds() > 0 && MachineSettings.bias(config, machine) == 0
                && othersWaiting(player.getWorld(), player.getUniqueId()) > 0;
    }

    private long othersWaiting(World world, UUID self) {
        return playersNear(world, NEAR, id -> id.equals(self) || waiting.containsKey(id));
    }

    private long secondsLeft() {
        World w = world();
        return windowOpen() && w != null ? Math.max(0, windowEnd - w.getGameTime() + 19) / 20 : 0;
    }

    /** The free spot nearest to where the player stands. */
    private int freeSpot(Player player) {
        double[] p = tablePosition(player.getLocation());
        int best = BlackjackLayout.nearestSpot(p[0], p[1]);
        Set<Integer> taken = new HashSet<>();
        waiting.values().forEach(s -> taken.add(s.spot));
        for (int d = 0; d < BlackjackLayout.SPOTS; d++) {
            for (int spot : new int[] {best - d, best + d}) {
                if (spot >= 0 && spot < BlackjackLayout.SPOTS && !taken.contains(spot)) {
                    return spot;
                }
            }
        }
        return -1;
    }

    /** A bet into the betting window (opened by the first): the chips leave now, as records paying them back. */
    private void join(Player player, StakeMenu menu, List<StakeMenu.Stake> stakes) {
        Messages m = ctx.messages().get();
        int spot = freeSpot(player);
        if (waiting.size() >= BlackjackLayout.SPOTS || spot < 0) {
            m.actionBar(player, Msg.BLACKJACK_FULL);
            return;
        }
        World world = player.getWorld();
        long now = world.getGameTime();
        long until = windowOpen() ? windowEnd : now + ctx.config().get().blackjack().countdownSeconds() * 20L;
        long ends = ctx.clock().getAsLong() + (until - now) * 50L + TABLE_RECORD_ENDS_MILLIS;
        List<WagerEngine.Request> requests = new ArrayList<>();
        for (StakeMenu.Stake s : stakes) {
            requests.add(new WagerEngine.Request(machine.id(), GAME, s.amount(), List.of(s.amount()), ends,
                    "slot=" + s.slot() + " refund until the start", RoundState.PLAYING).withTake(s.chips()));
        }
        List<UUID> rounds = commit(player, requests, Msg.BLACKJACK_NO_BET);
        if (rounds == null) {
            menu.render();
            return;
        }
        if (!windowOpen()) {
            windowEnd = until;
            keepChunkLoaded(true);
            Log.get().info("blackjack #{}: betting window opened by {}", machine.record().number(), player.getName());
        }
        List<Seat> seats = new ArrayList<>();
        for (int i = 0; i < stakes.size(); i++) {
            seats.add(new Seat(stakes.get(i).slot(), stakes.get(i).amount(), rounds.get(i)));
        }
        waiting.put(player.getUniqueId(), new TableSeat(player.getUniqueId(), player.getName(), spot,
                ctx.random().nextLong(), seats, ends));
        menu.clearStakes();
        if (othersWaiting(world, player.getUniqueId()) == 0) {
            windowEnd = Math.min(windowEnd, now + LAST_CALL_TICKS);
        }
        Log.get().info("{} joins blackjack #{} with {} at spot {} ({} player(s))", player.getName(),
                machine.record().number(), stakes.stream().map(s -> s.amount().toString()).toList(), spot,
                waiting.size());
        // the round menu opens now, in the click: a menu may not open by itself later, and it stays open for the round
        ctx.menus().open(player, new BlackjackRoundMenu(player.getUniqueId(), this, ctx));
        m.actionBar(player, Msg.BLACKJACK_JOINED, Messages.text("seconds", Long.toString(secondsLeft())));
    }

    /** Every tick of the window: who quit or died was refunded by that hook; at its end the cards come. */
    private void windowTick(World world, long now) {
        waiting.values().removeIf(s -> {
            Player p = Bukkit.getPlayer(s.player);
            boolean gone = p == null || !p.isOnline() || p.isDead();
            if (gone) {
                Log.get().info("blackjack #{}: {} left the betting window (refunded by the quit/death)",
                        machine.record().number(), s.name);
            }
            return gone;
        });
        if (waiting.isEmpty()) {
            closeWindow("nobody left");
        } else if (now >= windowEnd) {
            startTable(world, now);
        }
    }

    private void closeWindow(String why) {
        windowEnd = -1;
        waiting.clear();
        keepChunkLoaded(false);
        closeMenus();
        Log.get().info("blackjack #{}: betting window closed without a hand ({})", machine.record().number(), why);
    }

    /** The window has closed: the round starts for everybody whose records are still open. */
    private void startTable(World world, long now) {
        windowEnd = -1;
        if (MachineSettings.bias(ctx.config().get(), machine) != 0) {
            // an admin set a bias during the window: it is for one hand, so nobody plays and all get their bet
            for (TableSeat s : waiting.values()) {
                for (Seat z : s.stakes) {
                    settleRound(s.player, s.name, z.round(), SettleReason.NORMAL);
                }
            }
            closeWindow("bias set meanwhile: refunded");
            return;
        }
        List<TableSeat> seated = new ArrayList<>();
        for (TableSeat s : waiting.values()) {
            Player p = Bukkit.getPlayer(s.player);
            if (p != null && p.isOnline() && !p.isDead() && recordsOpen(p, s)) {
                seated.add(s);
            } else {
                Log.get().info("blackjack #{}: {} drops out at the start (records already settled)",
                        machine.record().number(), s.name);
            }
        }
        if (seated.isEmpty()) {
            closeWindow("nobody left at the start");
            return;
        }
        seated.sort(Comparator.comparingInt(s -> s.spot));
        if (shoe == null) {
            shoe = new BlackjackShoe(new ShoeRandom(ctx.random().nextLong()));
        }
        // the live round shuffles in its constructor when the shoe is low: the copy does the same with the same random
        BlackjackShoe sim = shoe.copy();
        if (sim.needsShuffle()) {
            sim.shuffle();
        }
        BlackjackRound.Result[] stand = BlackjackTableRound.standResultsAtStart(seated.size(), sim);
        Table t = new Table(seated, now);
        table = t;
        waiting.clear();
        t.round = new BlackjackTableRound(shoe, t, seated.size());
        closeBetMenus();
        for (int i = 0; i < seated.size(); i++) {
            TableSeat s = seated.get(i);
            BlackjackRound.Result mine = stand[i];
            Player p = Bukkit.getPlayer(s.player);
            if (p == null || reviseSeat(p, t, i, s.stakes, z -> BlackjackPayout.payout(z.stake(), mine,
                    BlackjackPayout.roll(s.seed, z.slot())), "deal", List.of()) != WagerEngine.Revision.DONE) {
                s.gone = true;
                t.round.leave(i);
            }
        }
        Log.get().info("blackjack #{}: round for {} player(s) (shoe {} cards)", machine.record().number(),
                seated.size(), shoe.remaining());
    }

    /** The bets are closed: whoever is still choosing a bet has missed this round. */
    private void closeBetMenus() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (MenuService.openMenu(p) instanceof BlackjackBetMenu menu && menu.controller() == this) {
                p.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
            }
        }
    }

    private boolean recordsOpen(Player p, TableSeat s) {
        try {
            var ledger = new PlayerAccount(p).ledger();
            return s.stakes.stream().allMatch(z -> ledger.round(z.round()).isPresent());
        } catch (LedgerCodec.CorruptLedgerException e) {
            Log.critical("LEDGER_UNREADABLE", "blackjack seat of {} dropped: {}", s.name, e.getMessage());
            return false;
        }
    }

    /** One seat's records: {@code stakes} and payouts from {@code pay}, plus {@code extra} taken, in one ledger write. */
    private WagerEngine.Revision reviseSeat(Player player, Table t, int seat, List<Seat> stakes,
                                           java.util.function.Function<Seat, List<ItemAmount>> pay, String why,
                                           List<ItemAmount> extra) {
        TableSeat s = t.seats.get(seat);
        try {
            PlayerAccount account = new PlayerAccount(player);
            long ends = ctx.clock().getAsLong() + TABLE_RECORD_ENDS_MILLIS;
            List<RoundRecord> revised = new ArrayList<>();
            for (Seat z : stakes) {
                RoundRecord old = account.ledger().round(z.round()).orElse(null);
                if (old == null) {
                    return WagerEngine.Revision.GONE;
                }
                revised.add(new RoundRecord(old.id(), old.machine(), old.game(), RoundState.PLAYING, z.stake(),
                        pay.apply(z), old.created(), ends, detail(z.slot(), t.round.hand(seat), t.round.dealer(),
                        t.round.doubled(seat) || !extra.isEmpty(), null, why)));
            }
            WagerEngine.Revision r = ctx.engine().revise(account, revised, extra);
            if (r == WagerEngine.Revision.DONE) {
                s.endsMillis = ends;
            }
            return r;
        } catch (LedgerCodec.CorruptLedgerException e) {
            Log.critical("LEDGER_UNREADABLE", "blackjack {} for {} refused: {}", why, s.name, e.getMessage());
            return WagerEngine.Revision.GONE;
        }
    }

    /** The seat of a player who may decide now, or -1. */
    private int actingSeat(Player player, Table t) {
        int seat = t.index(player.getUniqueId());
        return seat >= 0 && !t.seats.get(seat).gone && Usability.usable(player) ? seat : -1;
    }

    /** HIT or DOUBLE at a table: the drawing seat's records first, then everybody's stored result, then the card. */
    private void tableDraw(Player player, Table t, boolean doubling) {
        int seat = actingSeat(player, t);
        if (seat < 0 || !(doubling ? t.round.canDouble(seat) : t.round.canHit(seat)) || !safeDecision(player)) {
            return;
        }
        TableSeat s = t.seats.get(seat);
        BlackjackRound.Result[] next = t.round.standResults(shoe.copy(), seat);
        BlackjackRound.Result mine = next[seat];
        List<Seat> stakes = s.stakes;
        List<ItemAmount> extra = List.of();
        if (doubling) {
            extra = s.stakes.stream().map(Seat::stake).toList();
            stakes = s.stakes.stream().map(z -> new Seat(z.slot(), new ItemAmount(z.stake().item(),
                    z.stake().count() * 2), z.round())).toList();
        }
        WagerEngine.Revision r = reviseSeat(player, t, seat, stakes, z -> BlackjackPayout.payout(z.stake(), mine,
                BlackjackPayout.roll(s.seed, z.slot())), (doubling ? "double" : "hit") + ", then stand=" + mine, extra);
        if (r == WagerEngine.Revision.NOT_ENOUGH) {
            ctx.messages().get().actionBar(player, Msg.CHIPS_CANNOT_DOUBLE);
            return;
        }
        if (r != WagerEngine.Revision.DONE) {
            return;
        }
        s.stakes = new ArrayList<>(stakes);
        for (int i = 0; i < t.seats.size(); i++) {
            TableSeat other = t.seats.get(i);
            Player p = Bukkit.getPlayer(other.player);
            if (i != seat && !other.gone && p != null && !p.isDead()) {
                BlackjackRound.Result theirs = next[i];
                reviseSeat(p, t, i, other.stakes, z -> BlackjackPayout.payout(z.stake(), theirs,
                        BlackjackPayout.roll(other.seed, z.slot())), "after " + s.name + " drew", List.of());
            }
        }
        if (doubling) {
            t.round.doubleDown(seat);
        } else {
            t.round.hit(seat);
        }
    }

    private void runTable(World world, Table t, long now) {
        for (int i = 0; i < t.seats.size(); i++) {
            TableSeat s = t.seats.get(i);
            Player p = Bukkit.getPlayer(s.player);
            if (!s.gone && (p == null || !p.isOnline() || p.isDead())) {
                // quit, kick or death settled his records as they stand; the others play on
                s.gone = true;
                t.round.leave(i);
                Log.get().info("blackjack #{}: {} left the table, the hand stands", machine.record().number(), s.name);
            }
        }
        if (now <= t.ticked) {
            return;
        }
        t.ticked = now;
        t.round.tick();
        int at = t.round.now();
        for (int i = t.sounds.size() - 1; i >= 0; i--) {
            ScheduledSound s = t.sounds.get(i);
            if (s.at() <= at) {
                t.sounds.remove(i);
                sound(world, s.id(), ANCHOR_CENTRE, s.volume(), s.pitch());
            }
        }
        if (t.decided && (!t.settled || !t.coins.isEmpty()) && --t.payoutWait <= 0) {
            if (!t.settled) {
                t.settled = true;
                for (TableSeat s : t.seats) {
                    ArrayDeque<String> stacks = new ArrayDeque<>();
                    for (Seat z : s.stakes) {
                        settleRound(s.player, s.name, z.round(), SettleReason.NORMAL);
                        if (!s.gone) {
                            queueCoins(stacks, BlackjackPayout.payout(z.stake(), s.result,
                                    BlackjackPayout.roll(s.seed, z.slot())));
                        }
                    }
                    stacks.forEach(item -> t.coins.add(new TableCoin(item, s.spot, s.player)));
                }
            }
            if (!t.coins.isEmpty()) {
                TableCoin c = t.coins.poll();
                rig.coin(c.item(), c.spot(), coinTarget(world, c.player()), now);
                sound(world, "payout", ANCHOR_CENTRE, 0.43f, 0.95f + ctx.random().nextFloat() * 0.2f);
            }
            t.payoutWait = PAYOUT_STEP;
        }
        if (t.round.isOver() && t.settled && t.coins.isEmpty() && ++t.endHold > END_HOLD) {
            finishTable(t);
        }
    }

    private void finishTable(Table t) {
        table = null;
        claimant = null;
        keepChunkLoaded(false);
        for (TableSeat s : t.seats) {
            Player p = Bukkit.getPlayer(s.player);
            if (p != null && MenuService.openMenu(p) instanceof BlackjackRoundMenu menu && menu.controller() == this) {
                p.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
            }
        }
    }

    /** The panel of a table with several players, or of the betting window; sent only when something changed. */
    private void tablePanel(Table t, long now) {
        BlackjackPanel.TableContent content = null;
        if (t != null) {
            content = t.content(now);
        } else if (windowOpen() && !waiting.isEmpty()) {
            content = new BlackjackPanel.TableContent(waiting.values().stream().map(s -> new BlackjackPanel.SeatLine(
                    s.player, s.name, s.staked(), -1, 0, false)).toList(), -1, secondsLeft());
        }
        if (content == null ? shownTable != null : !content.equals(shownTable)) {
            rig.tablePanel(content == null ? null : BlackjackPanel.table(ctx.messages().get(), content));
            shownTable = content;
        }
    }

    /** HIT (button 1): the records first (stand result after this card), then the live card. */
    void hit(Player player) {
        Table t = table;
        if (t != null) {
            tableDraw(player, t, false);
            return;
        }
        Hand h = ownHand(player);
        if (h == null || !h.round.canHit() || !safeDecision(player)) {
            return;
        }
        BlackjackRound.Result next = BlackjackStand.hitThenStand(h.round.player(), h.round.dealer(), h.round.doubled(),
                shoe.copy(), h.bias);
        if (revise(player, h, s -> BlackjackPayout.payout(s.stake(), next, BlackjackPayout.roll(h.seed, s.slot())),
                "hit, then stand=" + next, List.of()) == WagerEngine.Revision.DONE) {
            h.round.hit();
            h.dirty = true;
        }
    }

    /** STAND (button 2): the records already hold the stand result. */
    void stand(Player player) {
        Table t = table;
        if (t != null) {
            int seat = actingSeat(player, t);
            if (seat >= 0) {
                t.round.stand(seat);
            }
            return;
        }
        Hand h = ownHand(player);
        if (h == null || !h.round.canStand()) {
            return;
        }
        h.round.stand();
        h.dirty = true;
    }

    /**
     * DOUBLE (button 3, {@code takeExtraStake}): the same stakes again from the storage slots and the records with
     * doubled stakes and the result after one card, in one tick; then the live card. Not enough items: the mod's
     * message, nothing changes.
     */
    void doubleDown(Player player) {
        Table t = table;
        if (t != null) {
            tableDraw(player, t, true);
            return;
        }
        Hand h = ownHand(player);
        if (h == null || !h.round.canDouble() || !safeDecision(player)) {
            return;
        }
        BlackjackRound.Result next = BlackjackStand.doubleDown(h.round.player(), h.round.dealer(), shoe.copy(),
                h.bias);
        List<ItemAmount> extra = h.seats.stream().map(Seat::stake).toList();
        List<Seat> doubled = h.seats.stream().map(s -> new Seat(s.slot(),
                new ItemAmount(s.stake().item(), s.stake().count() * 2), s.round())).toList();
        WagerEngine.Revision r = revise(player, h, doubled, s -> BlackjackPayout.payout(s.stake(), next,
                BlackjackPayout.roll(h.seed, s.slot())), "double, then stand=" + next, extra);
        if (r == WagerEngine.Revision.NOT_ENOUGH) {
            boolean chips = h.seats.stream()
                    .anyMatch(s -> de.tigges.kaboofcasino.wager.Chips.isValue(s.stake().item()));
            ctx.messages().get().actionBar(player, chips ? Msg.CHIPS_CANNOT_DOUBLE : Msg.BLACKJACK_CANNOT_DOUBLE);
            return;
        }
        if (r == WagerEngine.Revision.DONE) {
            h.seats.clear();
            h.seats.addAll(doubled);
            h.round.doubleDown();
            h.view.doubled = true;
            h.dirty = true;
        }
    }

    private Hand ownHand(Player player) {
        Hand h = hand;
        if (h == null || h.ownerGone || !h.player.equals(player.getUniqueId()) || !Usability.usable(player)) {
            return null;
        }
        return h;
    }

    /** A decision is a commit: never in danger (the menu closes; the hand runs on and times out to stand). */
    private boolean safeDecision(Player player) {
        if (ctx.menus().safe(player)) {
            return true;
        }
        ctx.messages().get().actionBar(player, Msg.ERROR_UNSAFE);
        player.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
        return false;
    }

    // ---- records ---------------------------------------------------------------------------------------------------

    private WagerEngine.Revision revise(Player player, Hand h, java.util.function.Function<Seat, List<ItemAmount>> pay,
                                       String why, List<ItemAmount> extra) {
        return revise(player, h, h.seats, pay, why, extra);
    }

    /**
     * Rewrites the hand's records: {@code seats} (stakes) and payouts from {@code pay}, plus {@code extra} taken, in
     * one ledger write ({@link WagerEngine#revise}). GONE (already settled) and errors change nothing here.
     */
    private WagerEngine.Revision revise(Player player, Hand h, List<Seat> seats,
                                        java.util.function.Function<Seat, List<ItemAmount>> pay, String why,
                                        List<ItemAmount> extra) {
        try {
            PlayerAccount account = new PlayerAccount(player);
            long ends = ctx.clock().getAsLong() + RECORD_ENDS_MILLIS;
            List<RoundRecord> revised = new ArrayList<>();
            for (Seat s : seats) {
                RoundRecord old = account.ledger().round(s.round()).orElse(null);
                if (old == null) {
                    return WagerEngine.Revision.GONE;
                }
                List<ItemAmount> payout = pay.apply(s);
                revised.add(new RoundRecord(old.id(), old.machine(), old.game(), RoundState.PLAYING, s.stake(),
                        payout, old.created(), ends, detail(s.slot(), h.round.player(), h.round.dealer(),
                        h.round.doubled() || !extra.isEmpty(), null, why)));
            }
            WagerEngine.Revision r = ctx.engine().revise(account, revised, extra);
            if (r == WagerEngine.Revision.DONE) {
                h.endsMillis = ends;
            }
            return r;
        } catch (LedgerCodec.CorruptLedgerException e) {
            Log.critical("LEDGER_UNREADABLE", "blackjack {} for {} refused: {}", why, player.getName(),
                    e.getMessage());
            return WagerEngine.Revision.GONE;
        }
    }

    /**
     * At the decision: the paid result must be the stored stand result. The live hand deals the predicted cards, so
     * they only differ after a bug; then the records follow the table (the owner online) and it is logged.
     */
    private void confirm(Hand h, BlackjackRound.Result result) {
        Player p = Bukkit.getPlayer(h.player);
        if (p == null || p.isDead() || h.ownerGone) {
            return;
        }
        try {
            PlayerAccount account = new PlayerAccount(p);
            boolean differs = false;
            for (Seat s : h.seats) {
                RoundRecord r = account.ledger().round(s.round()).orElse(null);
                if (r != null && !r.payout().equals(BlackjackPayout.payout(s.stake(), result,
                        BlackjackPayout.roll(h.seed, s.slot())))) {
                    differs = true;
                }
            }
            if (differs) {
                Log.get().warn("blackjack #{}: the hand of {} ended {} but its records held another stand result; "
                        + "records follow the table", machine.record().number(), h.name, result);
            }
            revise(p, h, s -> BlackjackPayout.payout(s.stake(), result, BlackjackPayout.roll(h.seed, s.slot())),
                    "result " + result, List.of());
        } catch (LedgerCodec.CorruptLedgerException e) {
            Log.critical("LEDGER_UNREADABLE", "blackjack result of {} not recorded: {}", h.name, e.getMessage());
        }
    }

    /** The round log's detail: slot, cards, doubled, and the stand result the record pays. */
    static String detail(int slot, List<Integer> player, List<Integer> dealer, boolean doubled,
                         BlackjackRound.Result stand, String why) {
        return "slot=" + slot + " p=" + cards(player) + " d=" + cards(dealer) + (doubled ? " x2" : "")
                + (stand == null ? "" : " stand=" + stand) + " " + why;
    }

    private static String cards(List<Integer> cards) {
        StringBuilder out = new StringBuilder();
        for (int c : cards) {
            if (!out.isEmpty()) {
                out.append(',');
            }
            out.append(c < 0 ? "?" : BlackjackCards.rankName(c) + "cdhs".charAt(BlackjackCards.suit(c)));
        }
        return out.toString();
    }

    // ---- ticking ---------------------------------------------------------------------------------------------------

    /**
     * Every server tick while loaded. Blackjack runs on the world's game time like the mod's block entity and
     * renderer (the view's times, the dealer's idle phase, the round menu's card delays): one clock everywhere. The
     * heartbeat beats only after the hand logic ran, so an exception loop or a missing rig stops owning the records.
     */
    @Override
    public void tick(long tick) {
        World world = world();
        if (world == null || rig == null) {
            return;
        }
        long now = world.getGameTime();
        rig.tickCoins(now);
        if (windowOpen()) {
            windowTick(world, now);
        }
        Hand h = hand;
        if (h != null) {
            runHand(world, h, now);
        }
        Table running = table;
        if (running != null) {
            runTable(world, running, now);
        }
        heartbeat.beat(ctx.clock().getAsLong());
        Hand current = hand;
        Table currentTable = table;
        rig.update(currentTable != null ? currentTable.view : current != null ? current.view : idleView, now);
        panel(current);
        tablePanel(currentTable, now);
        if (current != null) {
            Player owner = Bukkit.getPlayer(current.player);
            if (owner != null && MenuService.openMenu(owner) instanceof BlackjackRoundMenu menu
                    && menu.controller() == this) {
                menu.refresh(snapshot(owner));
            }
        }
        List<TableSeat> seats = currentTable != null ? currentTable.seats : List.copyOf(waiting.values());
        if (!seats.isEmpty()) {
            for (TableSeat s : seats) {
                Player p = Bukkit.getPlayer(s.player);
                if (p != null && MenuService.openMenu(p) instanceof BlackjackRoundMenu menu
                        && menu.controller() == this) {
                    menu.refresh(snapshot(p));
                }
            }
        }
    }

    private void runHand(World world, Hand h, long now) {
        Player owner = Bukkit.getPlayer(h.player);
        if (!h.ownerGone && (owner == null || !owner.isOnline() || owner.isDead())) {
            // quit/kick/death settled his records (the stand result); the table shows the same hand
            h.ownerGone = true;
            Log.get().info("blackjack #{}: {} left the hand, it stands", machine.record().number(), h.name);
        }
        if (h.ownerGone && h.round.canStand()) {
            h.round.stand();
        }
        if (now <= h.ticked) {
            return;
        }
        h.ticked = now;
        h.round.tick();
        int at = h.round.now();
        for (int i = h.sounds.size() - 1; i >= 0; i--) {
            ScheduledSound s = h.sounds.get(i);
            if (s.at() <= at) {
                h.sounds.remove(i);
                sound(world, s.id(), ANCHOR_CENTRE, s.volume(), s.pitch());
            }
        }
        if (h.decided != BlackjackRound.Result.NONE && (!h.settled || !h.coins.isEmpty()) && --h.payoutWait <= 0) {
            if (!h.settled) {
                h.settled = true;
                for (Seat s : h.seats) {
                    settleRound(h.player, h.name, s.round(), SettleReason.NORMAL);
                    queueCoins(h.coins, BlackjackPayout.payout(s.stake(), h.decided,
                            BlackjackPayout.roll(h.seed, s.slot())));
                }
            }
            if (!h.coins.isEmpty()) {
                rig.coin(h.coins.poll(), h.spot, coinTarget(world, h.player), now);
                sound(world, "payout", ANCHOR_CENTRE, 0.43f, 0.95f + ctx.random().nextFloat() * 0.2f);
            }
            h.payoutWait = PAYOUT_STEP;
        }
        if (h.round.isOver() && h.settled && h.coins.isEmpty() && ++h.endHold > END_HOLD) {
            finish();
        }
    }

    /** {@code finishRound}: the table is free; the owner's open round menu closes. */
    private void finish() {
        Hand h = hand;
        hand = null;
        claimant = null;
        if (h != null) {
            Player p = Bukkit.getPlayer(h.player);
            if (p != null && MenuService.openMenu(p) instanceof BlackjackRoundMenu menu && menu.controller() == this) {
                p.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
            }
        }
    }

    /** The spectator panel: the owner and his stake while a hand runs (the rig adds totals and result); else hidden. */
    private void panel(Hand h) {
        BlackjackPanel.Content content = null;
        if (h != null) {
            // totals and the result come from the view in the rig (the renderer's appear delay and flip hiding)
            content = new BlackjackPanel.Content(h.player, h.name, h.seats.stream().map(Seat::stake).toList());
        }
        if (content == null ? shownPanel != null : !content.equals(shownPanel)) {
            rig.panel(content);
            shownPanel = content;
        }
    }

    /** The round menu's state ({@code refreshSnapshot}). */
    BlackjackSnapshot snapshot(Player viewer) {
        World w = world();
        long now = w != null ? w.getGameTime() : viewer.getWorld().getGameTime();
        Table t = table;
        if (t != null) {
            int seat = t.index(viewer.getUniqueId());
            return seat >= 0 ? t.snapshot(seat, viewer, now) : BlackjackSnapshot.betting(now);
        }
        TableSeat waits = waiting.get(viewer.getUniqueId());
        if (waits != null) {
            return new BlackjackSnapshot(BlackjackSnapshot.PHASE_DEALING, List.of(), List.of(), List.of(), List.of(), 0,
                    0, false, false, false, false, false, waits.stakes.stream().map(Seat::stake).toList(), now);
        }
        Hand h = hand;
        if (h == null || !h.player.equals(viewer.getUniqueId())) {
            return BlackjackSnapshot.betting(now);
        }
        BlackjackRound r = h.round;
        int phase = switch (r.phase()) {
            case DEALING -> BlackjackSnapshot.PHASE_DEALING;
            case PLAYER_TURN -> BlackjackSnapshot.PHASE_TURN;
            case PLAYER_DRAW -> BlackjackSnapshot.PHASE_DRAWING;
            case DEALER_TURN -> BlackjackSnapshot.PHASE_DEALER;
            default -> BlackjackSnapshot.PHASE_RESULT;
        };
        List<Long> playerAt = new ArrayList<>();
        List<Long> dealerAt = new ArrayList<>();
        for (BlackjackCardMotion.Card c : h.view.cards) {
            (c.toDealer ? dealerAt : playerAt).add(c.dealtAt);
        }
        int left = r.phase() == BlackjackRound.Phase.PLAYER_TURN ? Math.max(0, r.deadline() - r.now()) : 0;
        boolean afford = r.canDouble() && afford(viewer, h.seats.stream().map(Seat::stake).toList());
        return new BlackjackSnapshot(phase, r.player(), r.dealerVisible(), playerAt, dealerAt, left, r.result().code,
                r.canHit(), r.canStand(), r.canDouble(), r.doubled(), afford,
                h.seats.stream().map(Seat::stake).toList(), now);
    }

    /** {@code BlackjackMenu.canAffordDouble}: the same stakes again as plain items in the storage slots. */
    private static boolean afford(Player player, List<ItemAmount> stakes) {
        PlayerAccount account = new PlayerAccount(player);
        for (ItemAmount need : ItemAmount.merge(stakes)) {
            if (account.countPlain(need.item()) < need.count()) {
                return false;
            }
        }
        return true;
    }

    /** {@code tablePosition}: the player's feet in table pixels (x, z). */
    double[] tablePosition(Location l) {
        double dx = l.getX() - (machine.anchor().x() + 0.5);
        double dz = l.getZ() - (machine.anchor().z() + 0.5);
        for (int i = 0; i < machine.facing().turns(); i++) {
            double nx = dz;
            dz = -dx;
            dx = nx;
        }
        return new double[] {24.0 + dx * 16.0, 24.0 + dz * 16.0};
    }

    /** The table centre one block up (coin target fallback, proximity). */
    @Override
    protected Location centre(World world) {
        return at(world, new double[] {32, 16, 14});
    }

    /** {@code celebrate}: confetti at the player's spot, y 18 px; a blackjack adds fireworks. */
    private void celebrate(World world, int spot, boolean big) {
        double[] c = BlackjackLayout.spotCenter(spot);
        double[] px = {c[0], 18.0, c[1]};
        int n = big ? 26 : 14;
        particle(world, Particle.DUST, px, n, 0.42, 0.18, 0.42, 0.18, RED_CONFETTI);
        particle(world, Particle.DUST, px, n, 0.42, 0.18, 0.42, 0.18, GOLD_CONFETTI);
        particle(world, Particle.DUST, px, n, 0.42, 0.18, 0.42, 0.18, GREEN_CONFETTI);
        if (big) {
            particle(world, Particle.FIREWORK, px, 26, 0.36, 0.22, 0.36, 0.2, null);
        }
    }
}
