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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 *   <li><b>CALL</b>: the outcome after turn and river is computed on a copy of the shoe ({@link PokerCall}), the records
 *       get the call stake (2 x the ante, taken in the same write, {@link WagerEngine#revise}) and that payout; then the
 *       live round deals exactly those cards. FOLD changes no record.</li>
 *   <li><b>Quit, kick, stop, crash, death, watchdog</b> settle the records as they stand: a fold before the call, the
 *       fixed outcome after it. Once the owner is gone the live hand folds at once during the decision (the mod waits
 *       for its 600-tick timeout), so the table shows what was paid; after the call nothing is left to decide.</li>
 *   <li>The decision: the payout is checked against the records, the records settle 21 ticks later with the first coin,
 *       one coin every 3 ticks, the table is free 15 ticks after the round is over (or on the owner's next click).</li>
 * </ul>
 * One clock: the world's game time (the view, the rig, the menu). Thread: MAIN.
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
        boolean ownerGone;

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
                    cards(round.board()), round.called() ? ", called" : "", ownerGone ? ", owner gone" : "");
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

    public PokerController(Machine machine, CasinoContext ctx) {
        super(machine, ctx);
    }

    // ---- MachineGame -----------------------------------------------------------------------------------------------

    @Override
    protected boolean running() {
        return hand != null;
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
        if (h != null && h.player.equals(player.getUniqueId())) {
            return new PokerRoundMenu(player.getUniqueId(), this, ctx);
        }
        return new PokerBetMenu(player.getUniqueId(), this, ctx);
    }

    /**
     * {@code PokerTableBlockEntity.use}: a finished hand of the clicking owner ends at once; someone else's hand, or
     * someone else holding the betting menu, is "busy"; the owner reopens his running hand.
     */
    @Override
    public void interact(Player player) {
        Messages m = ctx.messages().get();
        UUID id = player.getUniqueId();
        Hand h = hand;
        if (h != null && h.player.equals(id) && h.round.isOver() && h.settled && h.coins.isEmpty()) {
            finish();
            h = null;
        }
        if (h != null && !h.player.equals(id)) {
            m.actionBar(player, Msg.POKER_BUSY, Messages.text("name", h.name));
            return;
        }
        if (h == null && claimant != null && !claimant.equals(id) && claimantActive()) {
            Player other = Bukkit.getPlayer(claimant);
            m.actionBar(player, Msg.POKER_BUSY, Messages.text("name", other == null ? "?" : other.getName()));
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
        }
        hand = null;
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
        if (shoe == null) {
            shoe = new BlackjackShoe(new ShoeRandom(ctx.random().nextLong()), 1);
        }
        int bias = ctx.config().get().poker().bias();
        long seed = ctx.random().nextLong();
        long ends = ctx.clock().getAsLong() + RECORD_ENDS_MILLIS;
        List<WagerEngine.Request> requests = new ArrayList<>();
        for (StakeMenu.Stake s : stakes) {
            requests.add(new WagerEngine.Request(machine.id(), GAME, s.amount(), List.of(), ends,
                    detail(s.slot(), List.of(), List.of(), List.of(), false, "deal, then fold"), RoundState.PLAYING));
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
            ctx.messages().get().actionBar(player, Msg.POKER_CANNOT_CALL);
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
        Hand h = ownHand(player);
        if (h == null || !h.round.canFold()) {
            return;
        }
        h.round.fold();
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
        try {
            PlayerAccount account = new PlayerAccount(player);
            long ends = ctx.clock().getAsLong() + RECORD_ENDS_MILLIS;
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
        Hand h = hand;
        if (h != null) {
            runHand(world, h, now);
        }
        heartbeat.beat(ctx.clock().getAsLong());
        Hand current = hand;
        rig.update(current != null ? current.view : idleView, now);
        panel(current);
        if (current != null) {
            Player owner = Bukkit.getPlayer(current.player);
            if (owner != null && MenuService.openMenu(owner) instanceof PokerRoundMenu menu
                    && menu.controller() == this) {
                menu.refresh(snapshot(owner));
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
                    settleRound(h.player, h.name, s.round(), SettleReason.NORMAL);
                }
            }
            h.round.abort();
            h.coins.clear();
            finish();
            return;
        }
        Player owner = Bukkit.getPlayer(h.player);
        if (!h.ownerGone && (owner == null || !owner.isOnline() || owner.isDead())) {
            // quit/kick/death settled his records (a fold, or the called outcome); the table shows the same hand
            h.ownerGone = true;
            Log.get().info("poker #{}: {} left the hand{}", machine.record().number(), h.name,
                    h.decided != PokerRound.Result.NONE ? " after its result" : h.round.called()
                            ? " after the call" : ", it folds");
        }
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
                rig.coin(PlayerAccount.material(h.coins.poll()), h.seat, coinTarget(world, h.player), now);
                sound(world, "poker_payout", ANCHOR_CENTRE, 0.43f, 0.95f + ctx.random().nextFloat() * 0.2f);
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
        boolean afford = r.canCall() && afford(viewer, h);
        return new PokerSnapshot(phase, r.player(), r.dealerVisible(), r.board(), playerAt, dealerAt, boardAt,
                revealedAt, left, r.result().code, r.canCall(), r.canFold(), r.called(), afford, r.payoutThirds(),
                h.seats.stream().map(Seat::stake).toList(), h.decided == PokerRound.Result.NONE ? List.of() : h.net,
                now);
    }

    /** {@code PokerMenu.canAffordCall}: 2 x the ante of every item kind as plain items in the storage slots. */
    private static boolean afford(Player player, Hand h) {
        PlayerAccount account = new PlayerAccount(player);
        for (ItemAmount need : ItemAmount.merge(h.seats.stream()
                .map(s -> new ItemAmount(s.stake().item(), s.stake().count() * 2)).toList())) {
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
