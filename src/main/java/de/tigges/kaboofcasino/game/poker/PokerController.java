package de.tigges.kaboofcasino.game.poker;

import de.tigges.kaboofcasino.CasinoContext;
import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.game.GameMenu;
import de.tigges.kaboofcasino.game.MachineController;
import de.tigges.kaboofcasino.game.StakeMenu;
import de.tigges.kaboofcasino.game.blackjack.BlackjackCardMotion;
import de.tigges.kaboofcasino.game.blackjack.BlackjackCards;
import de.tigges.kaboofcasino.game.blackjack.BlackjackLayout;
import de.tigges.kaboofcasino.game.blackjack.BlackjackRound;
import de.tigges.kaboofcasino.game.blackjack.BlackjackShoe;
import de.tigges.kaboofcasino.game.blackjack.ShoeRandom;
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
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;

/**
 * Runtime of one poker table ({@code PokerTableBlockEntity}): Casino Hold'em, one player against the drawn house
 * dealer, as in the mod (games.md 4 "Multiplayer": three seats are positions only, one owner plays; others get "busy").
 * <ul>
 *   <li><b>DEAL</b>: the ante of the three bet slots is committed in one tick as PLAYING records whose payout is the
 *       fold result (nothing: the ante is lost, the mod's timeout rule and PLAN decision 2), then the mod's
 *       {@link PokerRound} runs on the table's one-deck shoe (reshuffled every hand) and the round menu opens.</li>
 *   <li><b>CALL</b>: the outcome after turn and river is computed on a copy of the shoe ({@link PokerCall}), the
 *       records get the call stake (2 x the ante, taken in the same write, {@link WagerEngine#revise}) and that
 *       payout; then the live round deals exactly those cards. FOLD changes no record.</li>
 *   <li><b>Quit or kick before the call</b> (dealing or decision): the records stay open ({@link #holdsForReconnect};
 *       they pay the fold, so they can never pay too much) and the hand waits for the mod's 600-tick timeout with its
 *       chunk held loaded. An owner who rejoins in time is re-attached and may call or fold; otherwise the timeout
 *       folds and the records settle as a fold at his next join. The join's recovery leaves held records alone and
 *       runs in the join event, before the next table tick sees the owner, so only one of them ever acts.</li>
 *   <li><b>Death, stop, crash, watchdog</b>, and a quit after the call, settle the records as they stand: a fold
 *       before the call, the fixed outcome after it. A dead owner's hand folds at once, so the table shows what was
 *       paid; after the call nothing is left to decide.</li>
 *   <li>The decision: the payout is checked against the records, the records settle 21 ticks later with the first coin,
 *       one coin every 3 ticks, the table is free 15 ticks after the round is over (or on the owner's next click).</li>
 * </ul>
 * One clock: the world's game time (the view, the rig, the menu).
 *
 * <p>Several players (PLAN-2 C5, not in the mod): alone at the table everything above applies. With somebody else near,
 * the first bet opens a window of {@code poker.countdown-seconds}, up to three players take a seat each; until it
 * closes an ante is a PLAYING record paying it back, so quit, death or stop refund it. Then one {@link PokerTableRound}
 * runs: the records pay the fold, everybody sees the same flop and decides on his own in one window; a call stores
 * that seat's outcome after turn and river ({@link PokerCall} on a copy of the shoe: the same two cards whoever calls)
 * and takes the call stake; one dealer shows down for all and each seat is confirmed, settled and paid on its own. A
 * quit before the seat's call keeps its records for a reconnect until the window closes (then it folds); death, or a
 * quit after the call, settles his records as they stand; the others play on. Thread: MAIN.
 */
public final class PokerController extends MachineController {

    static final String GAME = "poker";
    static final int PAYOUT_DELAY = 22;
    static final int PAYOUT_STEP = 3;
    static final int END_HOLD = 14;
    /** Sounds play at the anchor block (its centre), in table pixels. */
    static final double[] ANCHOR_CENTRE = {24, 8, 24};
    /** {@code isInRange}: the menu stays valid within 8 blocks of this table point. */
    static final double[] RANGE_POINT = {32, 14, 16};
    static final double RANGE = 8.0;
    /** A PLAYING record's watchdog end: this long after its last revision. */
    static final long RECORD_ENDS_MILLIS = 60_000;
    /** At most this many NET lines ({@code PokerScreen}: 6 kinds). */
    static final int NET_LINES = 6;
    /**
     * A hand older than this is stuck (the longest real one: 809 ticks to the timeout, or ~1 000 with a call, plus at
     * most 3 x 200 coin stacks at 3 ticks): it ends, its records settle as stored, the table is free again.
     */
    static final long MAX_HAND_TICKS = 6_000;
    /**
     * The records' end while the decision is open (set when it opens and when the owner comes back): the 600-tick
     * decision even at 5 TPS; the table owns the records another 60 s beyond it.
     */
    static final long DECISION_ENDS_MILLIS = 120_000;
    /** A shared hand (deal, 30 s decision, board, payout): its records may wait this long for their next rewrite. */
    static final long TABLE_RECORD_ENDS_MILLIS = 300_000;
    /** Whoever stands this near the table (blocks) makes a hand wait for bets. */
    static final double NEAR = 8.0;
    /** Once everybody near has bet, the cards come this soon (ticks). */
    static final int LAST_CALL_TICKS = 40;
    private static final Particle.DustOptions RED_CONFETTI = new Particle.DustOptions(Color.fromRGB(0xDF3B4B), 1.15f);
    private static final Particle.DustOptions GOLD_CONFETTI = new Particle.DustOptions(Color.fromRGB(0xFFD168), 1.25f);
    private static final Particle.DustOptions GREEN_CONFETTI = new Particle.DustOptions(Color.fromRGB(0x36D886),
            1.15f);

    private PokerRig rig;
    /** One deck, created at the first DEAL ({@code PokerTableBlockEntity} 356-358); not saved, as in the mod. */
    private BlackjackShoe shoe;
    private Hand hand;
    private final PokerTableState idleView = new PokerTableState();
    private PokerPanel.Content shownPanel;
    /** A running hand with several seats. */
    private Table table;
    /** Who has bet in the open window, in the order they joined. */
    private final Map<UUID, TableSeat> waiting = new LinkedHashMap<>();
    /** The game tick the betting window closes at, -1 when none is open. */
    private long windowEnd = -1;
    private PokerPanel.TableContent shownTable;

    /** One player's place at a table with several players. */
    private static final class TableSeat {
        final UUID player;
        final String name;
        final int spot;
        final long seed;
        List<Seat> stakes;
        long endsMillis;
        /** His records are settled (death, a quit after his decision, gone elsewhere): no more decisions, no coins. */
        boolean gone;
        /** He quit before deciding: his records wait in his file for a reconnect until the window closes. */
        boolean away;
        PokerRound.Result result = PokerRound.Result.NONE;
        int thirds;

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

    /** One filled bet slot of the running hand. */
    record Seat(int slot, ItemAmount stake, UUID round) {
    }

    private record ScheduledSound(int at, String id, float volume, float pitch) {
    }

    /** The running hand ({@code round != null} in the mod). */
    private final class Hand implements PokerRound.Listener {
        final UUID player;
        final String name;
        final int seat;
        final long seed;
        final int bias;
        /** World game time of the DEAL click; the round's tick 1 is the next game tick. */
        final long start;
        long ticked;
        long endsMillis;
        final PokerTableState view;
        final List<Seat> seats;
        final List<ScheduledSound> sounds = new ArrayList<>();
        final ArrayDeque<String> coins = new ArrayDeque<>();
        PokerRound round;
        PokerRound.Result decided = PokerRound.Result.NONE;
        int thirds;
        List<PokerSnapshot.Net> net = List.of();
        int payoutWait;
        boolean settled;
        int endHold;
        /** The owner's records are settled (death, a quit after the call, elsewhere): the hand folds at once. */
        boolean ownerGone;
        /** The owner quit before the call: his records are held for a reconnect, the table chunk is held loaded. */
        boolean away;
        /** This hand holds a chunk ticket ({@link MachineService#holdChunk}). */
        boolean chunkHeld;

        Hand(UUID player, String name, int seat, long seed, int bias, long start, List<Seat> seats) {
            this.player = player;
            this.name = name;
            this.seat = seat;
            this.seed = seed;
            this.bias = bias;
            this.start = start;
            this.ticked = start;
            this.view = new PokerTableState(start, seat);
            this.view.phaseChanged(PokerRound.Phase.DEALING, 0);
            this.seats = new ArrayList<>(seats);
        }

        private void schedule(int at, String id, float volume, float pitch) {
            sounds.add(new ScheduledSound(at, id, volume, pitch));
        }

        @Override
        public void shuffled(int at) {
            view.shuffled(at);
            schedule(at, "poker_shuffle", 0.9f, 1.0f);
        }

        @Override
        public void cardDealt(boolean toDealer, int index, int card, boolean faceUp, int at) {
            view.cardDealt(toDealer, index, card, faceUp, at);
            float pitch = 0.94f + 0.04f * Math.min(index, 4) + (toDealer ? 0.05f : 0.0f);
            schedule(at + 7, "poker_card_flip", 0.3f, 0.75f);
            schedule(at + 14, "poker_card_deal", 0.8f, pitch);
        }

        @Override
        public void holeCardRevealed(int index, int card, int at) {
            view.holeCardRevealed(index, card, at);
            schedule(at + 4 + 2, "poker_card_flip", 0.9f, 1.0f);
        }

        @Override
        public void turnOpened(int deadline, int at) {
            view.turnOpened(deadline, at);
            schedule(at, "poker_turn", 0.5f, 1.5f);
            extendForDecision(this, "decision open");
        }

        @Override
        public void turnTimedOut(int at) {
            Player p = Bukkit.getPlayer(player);
            if (p != null && !ownerGone) {
                ctx.messages().get().actionBar(p, Msg.POKER_TIMEOUT);
            }
        }

        @Override
        public void phaseChanged(PokerRound.Phase phase, int at) {
            view.phaseChanged(phase, at);
        }

        @Override
        public void decided(PokerRound.Result result, int at) {
            view.decided(result, at);
            decided = result;
            thirds = round == null ? 0 : round.payoutThirds();
            payoutWait = PAYOUT_DELAY;
            Log.get().info("poker #{}: hand of {} ends {} (player {}, dealer {}, board {}{}{})",
                    machine.record().number(), name, result, cards(round.player()), cards(round.dealer()),
                    cards(round.board()), round.called() ? ", called" : "",
                    ownerGone ? ", owner gone" : away ? ", owner away" : "");
            confirm(this, result, thirds);
            net = net(this);
            World world = world();
            if (world != null) {
                if (result.isWin()) {
                    schedule(at + 6, "poker_win", 1.1f, 1.0f);
                    celebrate(world, seat, round != null && PokerHands.antePays(round.playerScore()) >= 3);
                } else if (result.isPush()) {
                    schedule(at + 6, "poker_push", 0.95f, 1.0f);
                } else {
                    schedule(at + 6, "poker_lose", 0.95f, 1.0f);
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
            schedule(at + 7, "poker_card_deal", 0.55f, 0.8f);
        }
    }

    /** A running hand with several seats: what {@link Hand} is for one. */
    private final class Table implements PokerTableRound.Listener {
        final List<TableSeat> seats;
        final long start;
        long ticked;
        final PokerTableState view;
        final List<ScheduledSound> sounds = new ArrayList<>();
        final Set<String> resultSounds = new HashSet<>();
        final ArrayDeque<TableCoin> coins = new ArrayDeque<>();
        PokerTableRound round;
        /** The hand is decided for every seat (its reaction came): the payout runs. */
        boolean decided;
        int payoutWait;
        boolean settled;
        int endHold;

        Table(List<TableSeat> seats, long start) {
            this.seats = seats;
            this.start = start;
            this.ticked = start;
            this.view = new PokerTableState(start, seats.getFirst().spot);
            this.view.phaseChanged(PokerRound.Phase.DEALING, 0);
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
            schedule(at, "poker_shuffle", 0.9f, 1.0f);
        }

        @Override
        public void cardDealt(int seat, int index, int card, boolean faceUp, int at) {
            boolean toDealer = seat == PokerTableRound.DEALER;
            if (!toDealer) {
                view.seat = seats.get(seat).spot;
            }
            view.cardDealt(toDealer, index, card, faceUp, at);
            float pitch = 0.94f + 0.04f * Math.min(index, 4) + (toDealer ? 0.05f : 0.0f);
            schedule(at + 7, "poker_card_flip", 0.3f, 0.75f);
            schedule(at + 14, "poker_card_deal", 0.8f, pitch);
        }

        @Override
        public void holeCardRevealed(int index, int card, int at) {
            view.holeCardRevealed(index, card, at);
            schedule(at + 4 + 2, "poker_card_flip", 0.9f, 1.0f);
        }

        @Override
        public void decisionOpened(int deadline, int at) {
            // the dealer watches the middle of the table while everybody decides
            view.seat = 1;
            view.turnOpened(deadline, at);
            schedule(at, "poker_turn", 0.5f, 1.5f);
            Messages m = ctx.messages().get();
            String seconds = Long.toString(PokerRound.TURN_TIMEOUT / 20);
            Log.get().info("poker #{}: decision open for {} player(s)", machine.record().number(), seats.size());
            for (int i = 0; i < seats.size(); i++) {
                TableSeat s = seats.get(i);
                Player p = Bukkit.getPlayer(s.player);
                if (p != null && !s.gone && !s.away && !p.isDead()) {
                    m.actionBar(p, Msg.POKER_DECIDE, Messages.text("seconds", seconds));
                    // the records' end moves on (still the fold), so nothing releases them during the decision
                    if (reviseSeat(p, this, i, s.stakes, z -> List.of(), "decision open, then fold", List.of())
                            == WagerEngine.Revision.GONE) {
                        dropSeat(this, i, "records already settled at the decision");
                    }
                }
            }
        }

        @Override
        public void decisionTimedOut(int seat, int at) {
            TableSeat s = seats.get(seat);
            Player p = Bukkit.getPlayer(s.player);
            if (p != null && !s.gone && !s.away) {
                ctx.messages().get().actionBar(p, Msg.POKER_TIMEOUT);
            }
        }

        @Override
        public void phaseChanged(PokerRound.Phase phase, int at) {
            view.phaseChanged(phase, at);
        }

        @Override
        public void decided(int seat, PokerRound.Result result, int thirds, int at) {
            TableSeat s = seats.get(seat);
            s.result = result;
            s.thirds = thirds;
            Log.get().info("poker #{}: hand of {} ends {} (player {}, dealer {}, board {}{}{})",
                    machine.record().number(), s.name, result, cards(round.hand(seat)), cards(round.dealer()),
                    cards(round.board()), round.called(seat) ? ", called" : "",
                    s.gone ? ", owner gone" : s.away ? ", owner away" : "");
            if (result == PokerRound.Result.FOLD) {
                // the records already pay the fold
                return;
            }
            Player p = Bukkit.getPlayer(s.player);
            if (p != null && !p.isDead() && !s.gone) {
                confirmSeat(p, this, seat, result, thirds);
            }
            World world = world();
            if (world != null) {
                String sound = result.isWin() ? "poker_win" : result.isPush() ? "poker_push" : "poker_lose";
                if (result.isWin()) {
                    celebrate(world, s.spot, PokerHands.antePays(round.score(seat)) >= 3);
                }
                if (resultSounds.add(sound)) {
                    schedule(at + 6, sound, result.isWin() ? 1.1f : 0.95f, 1.0f);
                }
            }
        }

        @Override
        public void reaction(BlackjackRound.Reaction reaction, int at) {
            view.reaction(reaction, at);
            PokerRound.Result shown = PokerRound.Result.FOLD;
            for (TableSeat s : seats) {
                if (s.result != PokerRound.Result.FOLD && s.result != PokerRound.Result.NONE) {
                    shown = s.result;
                }
            }
            // the view's result time drives when the panel shows the results (as for one player)
            view.decided(shown, at);
            decided = true;
            payoutWait = PAYOUT_DELAY;
        }

        @Override
        public void cleared(int at) {
            view.cleared(at);
            schedule(at + 7, "poker_card_deal", 0.55f, 0.8f);
        }

        PokerPanel.TableContent content(long now) {
            List<PokerPanel.SeatLine> lines = new ArrayList<>();
            boolean shown = view.resultAt >= 0 && now >= view.resultAt + PokerPanel.RESULT_DELAY;
            for (TableSeat s : seats) {
                int result = s.result == PokerRound.Result.FOLD || shown && s.result != PokerRound.Result.NONE
                        ? s.result.code : 0;
                lines.add(new PokerPanel.SeatLine(s.player, s.name, s.stakes.stream().map(Seat::stake).toList(),
                        PokerPanel.seatHand(view, s.spot, now), result));
            }
            PokerPanel.Hands dealer = PokerPanel.dealerHand(view, now);
            long decide = round.phase() == PokerRound.Phase.DECISION
                    ? Math.max(0, round.deadline() - round.now() + 19) / 20 : -1;
            return new PokerPanel.TableContent(lines, dealer.dealer(), dealer.qualifies(), -1, decide);
        }

        PokerSnapshot snapshot(int seat, Player viewer, long now) {
            TableSeat s = seats.get(seat);
            PokerTableRound r = round;
            boolean folded = r.choice(seat) == PokerTableRound.Choice.FOLDED;
            int phase = switch (r.phase()) {
                case DEALING -> PokerSnapshot.PHASE_DEALING;
                case DECISION -> folded ? PokerSnapshot.PHASE_RESULT : PokerSnapshot.PHASE_DECISION;
                case BOARD -> folded ? PokerSnapshot.PHASE_RESULT : PokerSnapshot.PHASE_BOARD;
                case SHOWDOWN -> folded ? PokerSnapshot.PHASE_RESULT : PokerSnapshot.PHASE_SHOWDOWN;
                default -> PokerSnapshot.PHASE_RESULT;
            };
            List<Long> playerAt = new ArrayList<>();
            List<Long> dealerAt = new ArrayList<>(List.of(-1L, -1L));
            List<Long> boardAt = new ArrayList<>();
            List<Long> revealedAt = new ArrayList<>(List.of(-1L, -1L));
            for (BlackjackCardMotion.Card c : view.cards) {
                if (!c.toDealer) {
                    if (c.spot == s.spot) {
                        playerAt.add(c.dealtAt);
                    }
                } else if (c.index < 2) {
                    dealerAt.set(c.index, c.dealtAt);
                    revealedAt.set(c.index, c.flipAt);
                } else {
                    boardAt.add(c.dealtAt);
                }
            }
            dealerAt = dealerAt.subList(0, Math.min(2, r.dealer().size()));
            revealedAt = revealedAt.subList(0, dealerAt.size());
            boolean open = r.canCall(seat) && !s.gone;
            int left = r.phase() == PokerRound.Phase.DECISION && open ? Math.max(0, r.deadline() - r.now()) : 0;
            List<ItemAmount> shown = s.stakes.stream().map(Seat::stake).toList();
            boolean known = s.result != PokerRound.Result.NONE;
            return new PokerSnapshot(phase, r.hand(seat), r.dealerVisible(), r.board(), playerAt, dealerAt, boardAt,
                    revealedAt, left, known ? s.result.code : 0, open, open, r.called(seat),
                    open && afford(viewer, shown), s.thirds, shown,
                    known ? net(s.stakes, s.result, s.thirds, s.seed) : List.of(), now);
        }
    }

    public PokerController(Machine machine, CasinoContext ctx) {
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

    /** Breaking is refused while cards are out or antes wait for them (their records are open). */
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
        return Msg.POKER_BUSY;
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
        UUID id = player.getUniqueId();
        if (h != null && h.player.equals(id) || waiting.containsKey(id) || t != null && t.index(id) >= 0) {
            return new PokerRoundMenu(id, this, ctx);
        }
        return new PokerBetMenu(id, this, ctx);
    }

    /**
     * {@code PokerTableBlockEntity.use}: a finished hand of the clicking owner ends at once; someone else's hand, or
     * someone else holding the betting menu, is "busy"; the owner reopens his running hand.
     */
    @Override
    public void interact(Player player) {
        Messages m = ctx.messages().get();
        UUID id = player.getUniqueId();
        Table t = table;
        if (t != null) {
            if (t.index(id) < 0) {
                m.actionBar(player, Msg.POKER_BUSY, Messages.text("name", t.seats.getFirst().name));
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
        if (waiting.size() >= PokerLayout.SEATS) {
            m.actionBar(player, Msg.POKER_FULL);
            return;
        }
        Hand h = hand;
        if (h != null && h.player.equals(id) && h.round.isOver() && h.settled && h.coins.isEmpty()) {
            finish();
            h = null;
        }
        if (h != null && !h.player.equals(id)) {
            m.actionBar(player, Msg.POKER_BUSY, Messages.text("name", h.name));
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

    /** {@code PokerMenu.stillValid}: the table exists, within 8 blocks of (32, 14, 16) px, the owner during a hand. */
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
            // the idle budget is shared with the other machines' idle lights, which count server ticks
            rig = new PokerRig(machine.record(), world,
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
        if (hand != null) {
            // chunk unload or disable: the hand ends with this controller; its held records settle at the next join
            holdChunk(hand, false);
        }
        closeMenus();
    }

    /** Poker before the call keeps its records over the owner's quit and rejoin (the mod keeps the round). */
    @Override
    public boolean holdsForReconnect(UUID roundId) {
        Table t = table;
        if (t != null) {
            for (int i = 0; i < t.seats.size(); i++) {
                TableSeat s = t.seats.get(i);
                if (s.holds(roundId)) {
                    // a seat that has not decided yet waits for him until the window closes (its records pay the fold)
                    PokerRound.Phase phase = t.round.phase();
                    return !s.gone && t.round.choice(i) == PokerTableRound.Choice.OPEN
                            && (phase == PokerRound.Phase.DEALING || phase == PokerRound.Phase.DECISION)
                            && ownsRound(roundId);
                }
            }
        }
        Hand h = hand;
        return h != null && !h.ownerGone && h.decided == PokerRound.Result.NONE && !h.round.called()
                && ownsRound(roundId);
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

    /** Like blackjack: not settled, hand logic ran within 2 s, last revision + 60 s + 60 s grace. */
    static boolean owns(Heartbeat heartbeat, long nowMillis, long endsMillis, boolean settled) {
        return !settled && heartbeat.owns(nowMillis, endsMillis);
    }

    @Override
    public void checkRig() {
        if (rig != null && rig.spawned() && rig.broken()) {
            Log.get().info("poker #{}: rig entities vanished, respawning", machine.record().number());
            rig.despawn();
            spawn();
        }
    }

    /**
     * The table is removed while loaded ({@code onTableBroken}): an undecided hand is refunded in full (ante, and the
     * call if paid, as the mod ejects the wager), a decided one pays its result.
     */
    @Override
    public void removed() {
        Hand h = hand;
        if (h != null && !h.settled) {
            h.settled = true;
            if (h.decided == PokerRound.Result.NONE) {
                Player p = Bukkit.getPlayer(h.player);
                if (p != null && !p.isDead()) {
                    revise(p, h, h.seats, s -> List.of(s.stake()), "refund (table removed)", List.of());
                }
            }
            for (Seat s : h.seats) {
                settleRound(h.player, h.name, s.round(), SettleReason.MACHINE_GONE);
            }
        }
        if (h != null) {
            h.round.abort();
            holdChunk(h, false);
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
                    if (s.result == PokerRound.Result.NONE && p != null && !p.isDead() && !s.gone) {
                        // undecided: ante and call back in full, as for one player
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

    // ---- DEAL, CALL, FOLD ------------------------------------------------------------------------------------------

    /** DEAL (button 0): commits the ante stakes (each settling as a fold until the call) and starts the hand. */
    void deal(Player player, StakeMenu menu) {
        Messages m = ctx.messages().get();
        if (!mayCommit(player)) {
            return;
        }
        List<StakeMenu.Stake> stakes = menu.stakes();
        if (stakes.isEmpty()) {
            m.actionBar(player, Msg.POKER_NO_BET);
            return;
        }
        if (waiting.containsKey(player.getUniqueId())) {
            m.actionBar(player, Msg.POKER_ALREADY, Messages.text("seconds", Long.toString(secondsLeft())));
            return;
        }
        if (windowOpen() || playsWithOthers(player)) {
            join(player, menu, stakes);
            return;
        }
        if (shoe == null) {
            shoe = new BlackjackShoe(new ShoeRandom(ctx.random().nextLong()), 1);
        }
        int bias = MachineSettings.bias(ctx.config().get(), machine);
        long seed = ctx.random().nextLong();
        long ends = ctx.clock().getAsLong() + RECORD_ENDS_MILLIS;
        List<WagerEngine.Request> requests = new ArrayList<>();
        for (StakeMenu.Stake s : stakes) {
            requests.add(new WagerEngine.Request(machine.id(), GAME, s.amount(), List.of(), ends,
                    detail(s.slot(), List.of(), List.of(), List.of(), false, "deal, then fold"), RoundState.PLAYING)
                    .withTake(s.chips()));
        }
        List<UUID> rounds = commit(player, requests, Msg.POKER_NO_BET);
        if (rounds == null) {
            menu.render();
            return;
        }
        List<Seat> seats = new ArrayList<>();
        for (int i = 0; i < stakes.size(); i++) {
            seats.add(new Seat(stakes.get(i).slot(), stakes.get(i).amount(), rounds.get(i)));
        }
        double[] p = tablePosition(player.getLocation());
        int seat = PokerLayout.nearestSeat(p[0], p[1]);
        Hand h = new Hand(player.getUniqueId(), player.getName(), seat, seed, bias, player.getWorld().getGameTime(),
                seats);
        h.endsMillis = ends;
        hand = h;
        h.round = new PokerRound(shoe, h, bias);
        claimant = player.getUniqueId();
        menu.clearStakes();
        Log.get().info("{} deals poker #{} with {} at seat {} (bias {})", player.getName(),
                machine.record().number(), stakes.stream().map(s -> s.amount().toString()).toList(), seat, bias);
        ctx.menus().open(player, new PokerRoundMenu(player.getUniqueId(), this, ctx));
    }

    /**
     * CALL (button 1, {@code takeCallStake}): 2 x the ante of every stake from the storage slots (plain items, per item
     * kind) and the records with 3 x the ante and the outcome after turn and river, in one tick; then the live call.
     * Not enough items: the mod's message, nothing changes.
     */
    void call(Player player) {
        Table t = table;
        if (t != null) {
            tableCall(player, t);
            return;
        }
        Hand h = ownHand(player);
        if (h == null || !h.round.canCall() || !safeDecision(player)) {
            return;
        }
        PokerCall.Outcome next = PokerCall.call(h.round.player(), h.round.dealer(), h.round.board(), shoe.copy(),
                h.bias);
        List<ItemAmount> extra = h.seats.stream().map(s -> new ItemAmount(s.stake().item(), s.stake().count() * 2))
                .toList();
        List<Seat> called = h.seats.stream().map(s -> new Seat(s.slot(),
                new ItemAmount(s.stake().item(), s.stake().count() * 3), s.round())).toList();
        WagerEngine.Revision r = revise(player, h, called, s -> PokerPayout.payout(s.stake(), next.result(),
                next.thirds(), PokerPayout.roll(h.seed, s.slot())), "call, then " + next.result(), extra);
        if (r == WagerEngine.Revision.NOT_ENOUGH) {
            boolean chips = h.seats.stream()
                    .anyMatch(s -> de.tigges.kaboofcasino.wager.Chips.isValue(s.stake().item()));
            ctx.messages().get().actionBar(player, chips ? Msg.CHIPS_CANNOT_CALL : Msg.POKER_CANNOT_CALL);
            return;
        }
        if (r == WagerEngine.Revision.GONE) {
            // the records were settled elsewhere (watchdog, an admin): they paid the fold, so the hand folds
            ctx.messages().get().actionBar(player, Msg.POKER_HAND_SETTLED);
            Log.get().warn("poker #{}: {} called, but the hand's records were already settled; it folds",
                    machine.record().number(), h.name);
            h.ownerGone = true;
            return;
        }
        if (r == WagerEngine.Revision.DONE) {
            h.seats.clear();
            h.seats.addAll(called);
            h.round.call();
            h.view.called = true;
        }
    }

    /** FOLD (button 2): the records already hold the fold. Allowed in danger: it only gives up the ante. */
    void fold(Player player) {
        Table t = table;
        if (t != null) {
            int seat = actingSeat(player, t);
            if (seat >= 0 && t.round.fold(seat)) {
                Log.get().info("poker #{}: {} folds", machine.record().number(), player.getName());
            }
            return;
        }
        Hand h = ownHand(player);
        if (h == null || !h.round.canFold()) {
            return;
        }
        h.round.fold();
    }

    // ---- a table with several players ---------------------------------------------------------------------------------

    /** True if this bet waits for others: the window exists, nobody forces the cards, and somebody else is near. */
    private boolean playsWithOthers(Player player) {
        var config = ctx.config().get();
        return config.poker().countdownSeconds() > 0 && MachineSettings.bias(config, machine) == 0
                && othersWaiting(player.getWorld(), player.getUniqueId()) > 0;
    }

    private long othersWaiting(World world, UUID self) {
        return playersNear(world, NEAR, id -> id.equals(self) || waiting.containsKey(id));
    }

    private long secondsLeft() {
        World w = world();
        return windowOpen() && w != null ? Math.max(0, windowEnd - w.getGameTime() + 19) / 20 : 0;
    }

    /** The free seat nearest to where the player stands. */
    private int freeSeat(Player player) {
        double[] p = tablePosition(player.getLocation());
        int best = PokerLayout.nearestSeat(p[0], p[1]);
        Set<Integer> taken = new HashSet<>();
        waiting.values().forEach(s -> taken.add(s.spot));
        for (int d = 0; d < PokerLayout.SEATS; d++) {
            for (int seat : new int[] {best - d, best + d}) {
                if (seat >= 0 && seat < PokerLayout.SEATS && !taken.contains(seat)) {
                    return seat;
                }
            }
        }
        return -1;
    }

    /** An ante into the betting window (opened by the first): the chips leave now, as records paying them back. */
    private void join(Player player, StakeMenu menu, List<StakeMenu.Stake> stakes) {
        Messages m = ctx.messages().get();
        int seat = freeSeat(player);
        if (waiting.size() >= PokerLayout.SEATS || seat < 0) {
            m.actionBar(player, Msg.POKER_FULL);
            return;
        }
        World world = player.getWorld();
        long now = world.getGameTime();
        long until = windowOpen() ? windowEnd : now + ctx.config().get().poker().countdownSeconds() * 20L;
        long ends = ctx.clock().getAsLong() + (until - now) * 50L + TABLE_RECORD_ENDS_MILLIS;
        List<WagerEngine.Request> requests = new ArrayList<>();
        for (StakeMenu.Stake s : stakes) {
            requests.add(new WagerEngine.Request(machine.id(), GAME, s.amount(), List.of(s.amount()), ends,
                    "slot=" + s.slot() + " refund until the start", RoundState.PLAYING).withTake(s.chips()));
        }
        List<UUID> rounds = commit(player, requests, Msg.POKER_NO_BET);
        if (rounds == null) {
            menu.render();
            return;
        }
        if (!windowOpen()) {
            windowEnd = until;
            keepChunkLoaded(true);
            Log.get().info("poker #{}: betting window opened by {}", machine.record().number(), player.getName());
        }
        List<Seat> seats = new ArrayList<>();
        for (int i = 0; i < stakes.size(); i++) {
            seats.add(new Seat(stakes.get(i).slot(), stakes.get(i).amount(), rounds.get(i)));
        }
        waiting.put(player.getUniqueId(), new TableSeat(player.getUniqueId(), player.getName(), seat,
                ctx.random().nextLong(), seats, ends));
        menu.clearStakes();
        if (othersWaiting(world, player.getUniqueId()) == 0 || waiting.size() >= PokerLayout.SEATS) {
            windowEnd = Math.min(windowEnd, now + LAST_CALL_TICKS);
        }
        Log.get().info("{} joins poker #{} with {} at seat {} ({} player(s))", player.getName(),
                machine.record().number(), stakes.stream().map(s -> s.amount().toString()).toList(), seat,
                waiting.size());
        // the round menu opens now, in the click: a menu may not open by itself later, and it stays open for the hand
        ctx.menus().open(player, new PokerRoundMenu(player.getUniqueId(), this, ctx));
        m.actionBar(player, Msg.POKER_JOINED, Messages.text("seconds", Long.toString(secondsLeft())));
    }

    /** Every tick of the window: who quit or died was refunded by that hook; at its end the cards come. */
    private void windowTick(World world, long now) {
        waiting.values().removeIf(s -> {
            Player p = Bukkit.getPlayer(s.player);
            boolean gone = p == null || !p.isOnline() || p.isDead();
            if (gone) {
                Log.get().info("poker #{}: {} left the betting window (refunded by the quit/death)",
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
        Log.get().info("poker #{}: betting window closed without a hand ({})", machine.record().number(), why);
    }

    /** The window has closed: the hand starts for everybody whose records are still open. */
    private void startTable(World world, long now) {
        windowEnd = -1;
        if (MachineSettings.bias(ctx.config().get(), machine) != 0) {
            // an admin set a bias during the window: it is for one player, so nobody plays and all get their ante
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
                Log.get().info("poker #{}: {} drops out at the start (records already settled)",
                        machine.record().number(), s.name);
            }
        }
        if (seated.isEmpty()) {
            closeWindow("nobody left at the start");
            return;
        }
        seated.sort(Comparator.comparingInt(s -> s.spot));
        if (shoe == null) {
            shoe = new BlackjackShoe(new ShoeRandom(ctx.random().nextLong()), 1);
        }
        Table t = new Table(seated, now);
        table = t;
        waiting.clear();
        t.round = new PokerTableRound(shoe, t, seated.size());
        closeBetMenus();
        for (int i = 0; i < seated.size(); i++) {
            TableSeat s = seated.get(i);
            Player p = Bukkit.getPlayer(s.player);
            // from now on the records pay the fold, as for one player (no card is out yet)
            if (p == null || reviseSeat(p, t, i, s.stakes, z -> List.of(), "deal, then fold", List.of())
                    != WagerEngine.Revision.DONE) {
                dropSeat(t, i, "records gone at the deal");
            }
        }
        Log.get().info("poker #{}: hand for {} player(s): {}", machine.record().number(), seated.size(),
                seated.stream().map(s -> s.name + "@" + s.spot).toList());
    }

    /** The bets are closed: whoever is still choosing an ante has missed this hand. */
    private void closeBetMenus() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (MenuService.openMenu(p) instanceof PokerBetMenu menu && menu.controller() == this) {
                p.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
            }
        }
    }

    private boolean recordsOpen(Player p, TableSeat s) {
        try {
            var ledger = new PlayerAccount(p).ledger();
            return s.stakes.stream().allMatch(z -> ledger.round(z.round()).isPresent());
        } catch (LedgerCodec.CorruptLedgerException e) {
            Log.critical("LEDGER_UNREADABLE", "poker seat of {} dropped: {}", s.name, e.getMessage());
            return false;
        }
    }

    /** A seat whose records are settled: it decides nothing more (an open seat folds) and gets no coins. */
    private void dropSeat(Table t, int seat, String why) {
        TableSeat s = t.seats.get(seat);
        if (!s.gone) {
            s.gone = true;
            t.round.leave(seat);
            Log.get().info("poker #{}: {} is out of the hand ({})", machine.record().number(), s.name, why);
        }
    }

    /** One seat's records: {@code stakes} and payouts from {@code pay}, plus {@code extra} taken, in one ledger write. */
    private WagerEngine.Revision reviseSeat(Player player, Table t, int seat, List<Seat> stakes,
                                           Function<Seat, List<ItemAmount>> pay, String why, List<ItemAmount> extra) {
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
                        t.round.board(), t.round.called(seat) || !extra.isEmpty(), why)));
            }
            WagerEngine.Revision r = ctx.engine().revise(account, revised, extra);
            if (r == WagerEngine.Revision.DONE) {
                s.endsMillis = ends;
            }
            return r;
        } catch (LedgerCodec.CorruptLedgerException e) {
            Log.critical("LEDGER_UNREADABLE", "poker {} for {} refused: {}", why, s.name, e.getMessage());
            return WagerEngine.Revision.GONE;
        }
    }

    /** At a seat's showdown: its records must hold this result; the live hand deals the predicted cards, so they do. */
    private void confirmSeat(Player p, Table t, int seat, PokerRound.Result result, int thirds) {
        TableSeat s = t.seats.get(seat);
        try {
            PlayerAccount account = new PlayerAccount(p);
            boolean differs = false;
            for (Seat z : s.stakes) {
                RoundRecord r = account.ledger().round(z.round()).orElse(null);
                if (r != null && !r.payout().equals(PokerPayout.payout(z.stake(), result, thirds,
                        PokerPayout.roll(s.seed, z.slot())))) {
                    differs = true;
                }
            }
            if (differs) {
                Log.get().warn("poker #{}: the hand of {} ended {} but its records held another result; records "
                        + "follow the table", machine.record().number(), s.name, result);
            }
            reviseSeat(p, t, seat, s.stakes, z -> PokerPayout.payout(z.stake(), result, thirds,
                    PokerPayout.roll(s.seed, z.slot())), "result " + result, List.of());
        } catch (LedgerCodec.CorruptLedgerException e) {
            Log.critical("LEDGER_UNREADABLE", "poker result of {} not recorded: {}", s.name, e.getMessage());
        }
    }

    /** The seat of a player who may decide now, or -1. */
    private int actingSeat(Player player, Table t) {
        int seat = t.index(player.getUniqueId());
        return seat >= 0 && !t.seats.get(seat).gone && Usability.usable(player) ? seat : -1;
    }

    /**
     * CALL at a table: the seat's outcome after turn and river (the next two cards of the shoe, whoever else calls)
     * and the call stake in one write, then the seat is called.
     */
    private void tableCall(Player player, Table t) {
        int seat = actingSeat(player, t);
        if (seat < 0 || !t.round.canCall(seat) || !safeDecision(player)) {
            return;
        }
        TableSeat s = t.seats.get(seat);
        PokerCall.Outcome next = PokerCall.call(t.round.hand(seat), t.round.dealer(), t.round.board(), shoe.copy(), 0);
        List<ItemAmount> extra = s.stakes.stream().map(z -> new ItemAmount(z.stake().item(), z.stake().count() * 2))
                .toList();
        List<Seat> called = s.stakes.stream().map(z -> new Seat(z.slot(),
                new ItemAmount(z.stake().item(), z.stake().count() * 3), z.round())).toList();
        WagerEngine.Revision r = reviseSeat(player, t, seat, called, z -> PokerPayout.payout(z.stake(), next.result(),
                next.thirds(), PokerPayout.roll(s.seed, z.slot())), "call, then " + next.result(), extra);
        if (r == WagerEngine.Revision.NOT_ENOUGH) {
            boolean chips = s.stakes.stream()
                    .anyMatch(z -> de.tigges.kaboofcasino.wager.Chips.isValue(z.stake().item()));
            ctx.messages().get().actionBar(player, chips ? Msg.CHIPS_CANNOT_CALL : Msg.POKER_CANNOT_CALL);
            return;
        }
        if (r == WagerEngine.Revision.GONE) {
            ctx.messages().get().actionBar(player, Msg.POKER_HAND_SETTLED);
            dropSeat(t, seat, "records already settled at the call");
            return;
        }
        if (r == WagerEngine.Revision.DONE) {
            s.stakes = new ArrayList<>(called);
            t.round.call(seat);
            Log.get().info("poker #{}: {} calls", machine.record().number(), s.name);
            if (t.round.phase() == PokerRound.Phase.DECISION) {
                ctx.messages().get().actionBar(player, Msg.POKER_CALLED_WAIT);
            }
        }
    }

    /**
     * Where each seated player is. Dead: his death drained the records (a fold, or his called outcome), the seat is
     * out. Offline after his decision: the quit settled what is stored. Offline before it: the quit kept the records
     * ({@link #holdsForReconnect}), the seat waits until the window closes and folds then. Back before that: he
     * decides again (records' end renewed).
     */
    private void followSeats(Table t) {
        for (int i = 0; i < t.seats.size(); i++) {
            TableSeat s = t.seats.get(i);
            if (s.gone) {
                continue;
            }
            Player p = Bukkit.getPlayer(s.player);
            boolean online = p != null && p.isOnline();
            boolean open = t.round.choice(i) == PokerTableRound.Choice.OPEN;
            if (online && p.isDead()) {
                dropSeat(t, i, "died in the hand");
            } else if (!online && !open) {
                // decided before the quit (settled there), or the window closed while he was away (folded)
                String why = s.away ? "away when the decision closed (folds; settled at the join)"
                        : "left after deciding (settled at the quit)";
                s.away = false;
                dropSeat(t, i, why);
            } else if (!online && !s.away) {
                s.away = true;
                Log.get().info("poker #{}: {} left the hand before deciding; the seat waits until the decision "
                        + "closes (records kept)", machine.record().number(), s.name);
            } else if (online && s.away) {
                s.away = false;
                if (open && t.round.canCall(i) || t.round.phase() == PokerRound.Phase.DEALING) {
                    Log.get().info("poker #{}: {} is back in the hand", machine.record().number(), s.name);
                    if (reviseSeat(p, t, i, s.stakes, z -> List.of(), "rejoined, then fold", List.of())
                            == WagerEngine.Revision.GONE) {
                        dropSeat(t, i, "records gone at the rejoin");
                    }
                } else {
                    // decided (timed out) while he was away: the join settled the fold
                    dropSeat(t, i, "back after the decision closed");
                }
            }
        }
    }

    private void runTable(World world, Table t, long now) {
        // up to three seats of coins: twice the single hand's limit
        if (now - t.start > 2 * MAX_HAND_TICKS) {
            Log.get().warn("poker #{}: the shared hand is stuck in {} after {} ticks; it ends, its records settle as "
                    + "stored", machine.record().number(), t.round.phase(), now - t.start);
            if (!t.settled) {
                t.settled = true;
                for (TableSeat s : t.seats) {
                    for (Seat z : s.stakes) {
                        settleRound(s.player, s.name, z.round(), SettleReason.TIMEOUT);
                    }
                }
            }
            t.round.abort();
            t.coins.clear();
            finishTable(t);
            return;
        }
        followSeats(t);
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
                        if (!s.gone && !s.away) {
                            queueCoins(stacks, PokerPayout.payout(z.stake(), s.result, s.thirds,
                                    PokerPayout.roll(s.seed, z.slot())));
                        }
                    }
                    stacks.forEach(item -> t.coins.add(new TableCoin(item, s.spot, s.player)));
                }
            }
            if (!t.coins.isEmpty()) {
                TableCoin c = t.coins.poll();
                rig.coin(c.item(), c.spot(), coinTarget(world, c.player()), now);
                sound(world, "poker_payout", ANCHOR_CENTRE, 0.43f, 0.95f + ctx.random().nextFloat() * 0.2f);
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
            if (p != null && MenuService.openMenu(p) instanceof PokerRoundMenu menu && menu.controller() == this) {
                p.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
            }
        }
    }

    /** The panel of a table with several players, or of the betting window; sent only when something changed. */
    private void tablePanel(Table t, long now) {
        PokerPanel.TableContent content = null;
        if (t != null) {
            content = t.content(now);
        } else if (windowOpen() && !waiting.isEmpty()) {
            content = new PokerPanel.TableContent(waiting.values().stream().map(s -> new PokerPanel.SeatLine(
                    s.player, s.name, s.stakes.stream().map(Seat::stake).toList(), -1, 0)).toList(), -1, false,
                    secondsLeft(), -1);
        }
        if (content == null ? shownTable != null : !content.equals(shownTable)) {
            rig.tablePanel(content == null ? null : PokerPanel.table(ctx.messages().get(), content));
            shownTable = content;
        }
    }

    private Hand ownHand(Player player) {
        Hand h = hand;
        if (h == null || h.ownerGone || !h.player.equals(player.getUniqueId()) || !Usability.usable(player)) {
            return null;
        }
        return h;
    }

    /** A call is a commit: never in danger (the menu closes; the hand runs on and times out to a fold, as the mod). */
    private boolean safeDecision(Player player) {
        if (ctx.menus().safe(player)) {
            return true;
        }
        ctx.messages().get().actionBar(player, Msg.ERROR_UNSAFE);
        player.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
        return false;
    }

    // ---- records ---------------------------------------------------------------------------------------------------

    /**
     * Rewrites the hand's records: {@code seats} (stakes) and payouts from {@code pay}, plus {@code extra} taken, in
     * one ledger write ({@link WagerEngine#revise}). GONE (already settled) and errors change nothing here.
     */
    private WagerEngine.Revision revise(Player player, Hand h, List<Seat> seats,
                                        Function<Seat, List<ItemAmount>> pay, String why, List<ItemAmount> extra) {
        return revise(player, h, seats, pay, why, extra, ctx.clock().getAsLong() + RECORD_ENDS_MILLIS);
    }

    private WagerEngine.Revision revise(Player player, Hand h, List<Seat> seats,
                                        Function<Seat, List<ItemAmount>> pay, String why, List<ItemAmount> extra,
                                        long ends) {
        try {
            PlayerAccount account = new PlayerAccount(player);
            List<RoundRecord> revised = new ArrayList<>();
            for (Seat s : seats) {
                RoundRecord old = account.ledger().round(s.round()).orElse(null);
                if (old == null) {
                    return WagerEngine.Revision.GONE;
                }
                revised.add(new RoundRecord(old.id(), old.machine(), old.game(), RoundState.PLAYING, s.stake(),
                        pay.apply(s), old.created(), ends, detail(s.slot(), h.round.player(), h.round.dealer(),
                        h.round.board(), h.round.called() || !extra.isEmpty(), why)));
            }
            WagerEngine.Revision r = ctx.engine().revise(account, revised, extra);
            if (r == WagerEngine.Revision.DONE) {
                h.endsMillis = ends;
            }
            return r;
        } catch (LedgerCodec.CorruptLedgerException e) {
            Log.critical("LEDGER_UNREADABLE", "poker {} for {} refused: {}", why, player.getName(), e.getMessage());
            return WagerEngine.Revision.GONE;
        }
    }

    /**
     * The decision opens or the owner is back before the call: the records' end moves to now + 120 s (still the fold),
     * so neither the watchdog nor the ownership releases them during the decision, even at low TPS. With the owner
     * away only the table's own end moves (his file is written at his rejoin). Records gone: the hand folds.
     */
    private void extendForDecision(Hand h, String why) {
        long ends = ctx.clock().getAsLong() + DECISION_ENDS_MILLIS;
        Player p = Bukkit.getPlayer(h.player);
        if (h.ownerGone || h.round == null || h.round.called()) {
            return;
        }
        if (h.away || p == null || !p.isOnline() || p.isDead()) {
            h.endsMillis = Math.max(h.endsMillis, ends);
            return;
        }
        if (revise(p, h, h.seats, s -> List.of(), why + ", then fold", List.of(), ends)
                == WagerEngine.Revision.GONE) {
            h.ownerGone = true;
            Log.get().info("poker #{}: the records of {} were already settled ({}); the hand folds",
                    machine.record().number(), h.name, why);
        }
    }

    /** Holds or releases the table's chunk while the hand waits for its owner (one counted ticket per chunk). */
    private void holdChunk(Hand h, boolean hold) {
        World w = world();
        if (w == null || hold == h.chunkHeld) {
            return;
        }
        int cx = machine.anchor().chunkX();
        int cz = machine.anchor().chunkZ();
        if (hold) {
            ctx.machines().holdChunk(w, cx, cz, ctx.plugin());
        } else {
            ctx.machines().releaseChunk(w, cx, cz, ctx.plugin());
        }
        h.chunkHeld = hold;
    }

    /** At the decision the records must hold this result; the live round deals the predicted cards, so they do. */
    private void confirm(Hand h, PokerRound.Result result, int thirds) {
        Player p = Bukkit.getPlayer(h.player);
        if (p == null || p.isDead() || h.ownerGone) {
            return;
        }
        try {
            PlayerAccount account = new PlayerAccount(p);
            boolean differs = false;
            for (Seat s : h.seats) {
                RoundRecord r = account.ledger().round(s.round()).orElse(null);
                if (r != null && !r.payout().equals(PokerPayout.payout(s.stake(), result, thirds,
                        PokerPayout.roll(h.seed, s.slot())))) {
                    differs = true;
                }
            }
            if (differs) {
                Log.get().warn("poker #{}: the hand of {} ended {} but its records held another result; records "
                        + "follow the table", machine.record().number(), h.name, result);
            }
            revise(p, h, h.seats, s -> PokerPayout.payout(s.stake(), result, thirds,
                    PokerPayout.roll(h.seed, s.slot())), "result " + result, List.of());
        } catch (LedgerCodec.CorruptLedgerException e) {
            Log.critical("LEDGER_UNREADABLE", "poker result of {} not recorded: {}", h.name, e.getMessage());
        }
    }

    /** {@code addResultDelta}: per item kind, payouts minus stakes (0-lines dropped unless all are 0), at most 6. */
    static List<PokerSnapshot.Net> net(Hand h) {
        return net(h.seats, h.decided, h.thirds, h.seed);
    }

    /** {@code addResultDelta} for the seats of a decided hand. */
    static List<PokerSnapshot.Net> net(List<Seat> seats, PokerRound.Result decided, int thirds, long seed) {
        Map<String, Long> delta = new LinkedHashMap<>();
        for (Seat s : seats) {
            delta.merge(s.stake().item(), -s.stake().count(), Long::sum);
            for (ItemAmount a : PokerPayout.payout(s.stake(), decided, thirds, PokerPayout.roll(seed, s.slot()))) {
                delta.merge(a.item(), a.count(), Long::sum);
            }
        }
        boolean anyNonZero = delta.values().stream().anyMatch(v -> v != 0);
        List<PokerSnapshot.Net> out = new ArrayList<>();
        for (Map.Entry<String, Long> e : delta.entrySet()) {
            if ((!anyNonZero || e.getValue() != 0) && out.size() < NET_LINES) {
                out.add(new PokerSnapshot.Net(e.getKey(), e.getValue()));
            }
        }
        return out;
    }

    static String detail(int slot, List<Integer> player, List<Integer> dealer, List<Integer> board, boolean called,
                         String why) {
        return "slot=" + slot + " p=" + cards(player) + " d=" + cards(dealer) + " b=" + cards(board)
                + (called ? " x3" : "") + " " + why;
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

    /** Every server tick while loaded; the world's game time is the clock; the heartbeat beats after the hand ran. */
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
            if (owner != null && MenuService.openMenu(owner) instanceof PokerRoundMenu menu
                    && menu.controller() == this) {
                menu.refresh(snapshot(owner));
            }
        }
        List<TableSeat> seats = currentTable != null ? currentTable.seats : List.copyOf(waiting.values());
        for (TableSeat s : seats) {
            Player p = Bukkit.getPlayer(s.player);
            if (p != null && MenuService.openMenu(p) instanceof PokerRoundMenu menu && menu.controller() == this) {
                menu.refresh(snapshot(p));
            }
        }
    }

    private void runHand(World world, Hand h, long now) {
        if (now - h.start > MAX_HAND_TICKS) {
            Log.get().warn("poker #{}: the hand of {} is stuck in {} after {} ticks; it ends, its records settle as "
                    + "stored", machine.record().number(), h.name, h.round.phase(), now - h.start);
            if (!h.settled) {
                h.settled = true;
                for (Seat s : h.seats) {
                    settleRound(h.player, h.name, s.round(), SettleReason.TIMEOUT);
                }
            }
            h.round.abort();
            h.coins.clear();
            finish();
            return;
        }
        followOwner(h);
        if (h.ownerGone && h.round.canFold()) {
            h.round.fold();
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
        if (h.decided != PokerRound.Result.NONE && (!h.settled || !h.coins.isEmpty()) && --h.payoutWait <= 0) {
            if (!h.settled) {
                h.settled = true;
                for (Seat s : h.seats) {
                    settleRound(h.player, h.name, s.round(), SettleReason.NORMAL);
                    queueCoins(h.coins, PokerPayout.payout(s.stake(), h.decided, h.thirds,
                            PokerPayout.roll(h.seed, s.slot())));
                }
            }
            if (!h.coins.isEmpty()) {
                rig.coin(h.coins.poll(), h.seat, coinTarget(world, h.player), now);
                sound(world, "poker_payout", ANCHOR_CENTRE, 0.43f, 0.95f + ctx.random().nextFloat() * 0.2f);
            }
            h.payoutWait = PAYOUT_STEP;
        }
        if (h.round.isOver() && h.settled && h.coins.isEmpty() && ++h.endHold > END_HOLD) {
            finish();
        }
    }

    /**
     * Where the owner is. Dead: his death drained the records (a fold, or the called outcome), the hand folds at once.
     * Offline after the call or the result: the quit settled what is stored. Offline before the call: the quit kept
     * the records ({@link #holdsForReconnect}), the hand waits for the timeout with its chunk held. Back online before
     * the result: re-attached (records' end renewed); if his records are gone meanwhile, the hand folds.
     */
    private void followOwner(Hand h) {
        if (h.ownerGone) {
            return;
        }
        Player owner = Bukkit.getPlayer(h.player);
        boolean online = owner != null && owner.isOnline();
        int number = machine.record().number();
        if (online && owner.isDead()) {
            h.ownerGone = true;
            Log.get().info("poker #{}: {} died in the hand{}", number, h.name, h.decided != PokerRound.Result.NONE
                    ? " after its result" : h.round.called() ? " after the call" : ", it folds");
        } else if (!online && (h.round.called() || h.decided != PokerRound.Result.NONE)) {
            h.ownerGone = true;
            Log.get().info("poker #{}: {} left the hand {}", number, h.name, h.round.called() && !h.away
                    ? "after the call (its outcome was settled at the quit)" : "after its result");
        } else if (!online && !h.away) {
            h.away = true;
            holdChunk(h, true);
            Log.get().info("poker #{}: {} left the hand before the call; it waits for a reconnect until the timeout "
                    + "(records kept, chunk held)", number, h.name);
        } else if (online && h.away) {
            h.away = false;
            holdChunk(h, false);
            if (h.decided != PokerRound.Result.NONE || h.round.called()) {
                // decided while he was away: the join settled the fold
                h.ownerGone = true;
                Log.get().info("poker #{}: {} is back after the hand's result", number, h.name);
            } else {
                Log.get().info("poker #{}: {} is back in the hand", number, h.name);
                extendForDecision(h, "rejoined");
            }
        }
    }

    /** {@code finishRound}: the table is free; the owner's open round menu closes. */
    private void finish() {
        Hand h = hand;
        hand = null;
        claimant = null;
        if (h != null) {
            holdChunk(h, false);
            Player p = Bukkit.getPlayer(h.player);
            if (p != null && MenuService.openMenu(p) instanceof PokerRoundMenu menu && menu.controller() == this) {
                p.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
            }
        }
    }

    /** The spectator panel: the owner and his stake while a hand runs (the rig adds hands and result); else hidden. */
    private void panel(Hand h) {
        PokerPanel.Content content = h == null ? null
                : new PokerPanel.Content(h.player, h.name, h.seats.stream().map(Seat::stake).toList());
        if (content == null ? shownPanel != null : !content.equals(shownPanel)) {
            rig.panel(content);
            shownPanel = content;
        }
    }

    /** The round menu's state ({@code refreshSnapshot}). */
    PokerSnapshot snapshot(Player viewer) {
        World w = world();
        long now = w != null ? w.getGameTime() : viewer.getWorld().getGameTime();
        Table t = table;
        if (t != null) {
            int seat = t.index(viewer.getUniqueId());
            return seat >= 0 ? t.snapshot(seat, viewer, now) : PokerSnapshot.betting(now);
        }
        TableSeat waits = waiting.get(viewer.getUniqueId());
        if (waits != null) {
            return new PokerSnapshot(PokerSnapshot.PHASE_DEALING, List.of(), List.of(), List.of(), List.of(),
                    List.of(), List.of(), List.of(), 0, 0, false, false, false, false, 0,
                    waits.stakes.stream().map(Seat::stake).toList(), List.of(), now);
        }
        Hand h = hand;
        if (h == null || !h.player.equals(viewer.getUniqueId())) {
            return PokerSnapshot.betting(now);
        }
        PokerRound r = h.round;
        int phase = switch (r.phase()) {
            case DEALING -> PokerSnapshot.PHASE_DEALING;
            case DECISION -> PokerSnapshot.PHASE_DECISION;
            case BOARD -> PokerSnapshot.PHASE_BOARD;
            case SHOWDOWN -> PokerSnapshot.PHASE_SHOWDOWN;
            default -> PokerSnapshot.PHASE_RESULT;
        };
        List<Long> playerAt = new ArrayList<>();
        List<Long> dealerAt = new ArrayList<>(List.of(-1L, -1L));
        List<Long> boardAt = new ArrayList<>();
        List<Long> revealedAt = new ArrayList<>(List.of(-1L, -1L));
        for (BlackjackCardMotion.Card c : h.view.cards) {
            if (!c.toDealer) {
                playerAt.add(c.dealtAt);
            } else if (c.index < 2) {
                dealerAt.set(c.index, c.dealtAt);
                revealedAt.set(c.index, c.flipAt);
            } else {
                boardAt.add(c.dealtAt);
            }
        }
        dealerAt = dealerAt.subList(0, Math.min(2, r.dealer().size()));
        revealedAt = revealedAt.subList(0, dealerAt.size());
        int left = r.phase() == PokerRound.Phase.DECISION ? Math.max(0, r.deadline() - r.now()) : 0;
        boolean afford = r.canCall() && afford(viewer, h.seats.stream().map(Seat::stake).toList());
        return new PokerSnapshot(phase, r.player(), r.dealerVisible(), r.board(), playerAt, dealerAt, boardAt,
                revealedAt, left, r.result().code, r.canCall(), r.canFold(), r.called(), afford, r.payoutThirds(),
                h.seats.stream().map(Seat::stake).toList(), h.decided == PokerRound.Result.NONE ? List.of() : h.net,
                now);
    }

    /** {@code PokerMenu.canAffordCall}: 2 x the ante of every item kind as plain items in the storage slots. */
    private static boolean afford(Player player, List<ItemAmount> antes) {
        PlayerAccount account = new PlayerAccount(player);
        for (ItemAmount need : ItemAmount.merge(antes.stream()
                .map(s -> new ItemAmount(s.item(), s.count() * 2)).toList())) {
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
        return at(world, new double[] {32, 16, 16});
    }

    /** {@code celebrate}: confetti at the seat's bet spot, y 18 px; full house or better adds fireworks. */
    private void celebrate(World world, int seat, boolean big) {
        double[] c = PokerLayout.seatCenter(seat);
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
