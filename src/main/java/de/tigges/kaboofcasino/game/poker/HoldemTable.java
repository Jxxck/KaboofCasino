package de.tigges.kaboofcasino.game.poker;

import de.tigges.kaboofcasino.CasinoContext;
import de.tigges.kaboofcasino.config.CasinoConfig;
import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.game.StakeMenu;
import de.tigges.kaboofcasino.game.blackjack.BlackjackCardMotion;
import de.tigges.kaboofcasino.game.blackjack.BlackjackCards;
import de.tigges.kaboofcasino.game.blackjack.BlackjackRound;
import de.tigges.kaboofcasino.game.blackjack.BlackjackShoe;
import de.tigges.kaboofcasino.game.blackjack.ShoeRandom;
import de.tigges.kaboofcasino.gui.MenuService;
import de.tigges.kaboofcasino.gui.Usability;
import de.tigges.kaboofcasino.machine.Machine;
import de.tigges.kaboofcasino.util.ChipText;
import de.tigges.kaboofcasino.util.Log;
import de.tigges.kaboofcasino.wager.Chips;
import de.tigges.kaboofcasino.wager.ItemAmount;
import de.tigges.kaboofcasino.wager.LedgerCodec;
import de.tigges.kaboofcasino.wager.PlayerAccount;
import de.tigges.kaboofcasino.wager.RoundRecord;
import de.tigges.kaboofcasino.wager.RoundState;
import de.tigges.kaboofcasino.wager.SettleReason;
import de.tigges.kaboofcasino.wager.WagerEngine;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;

/**
 * Texas Hold'em at a poker table (Jack 2026-11-10: players against each other, the croupier only deals; not in
 * MineVegas). Up to three players sit at the table's three seats with chips they buy in with; hands of
 * {@link HoldemHand} follow one another while at least two have chips; a player stands up (GEHEN) with what is in front
 * of him.
 *
 * <p><b>The books.</b> Each seated player has one PLAYING record: stake = everything bought in, payout = the chips at
 * the table at the start of the current hand. The buy-in leaves the inventory when he sits down; every hand's result is
 * written into all players' records in the same tick; standing up settles the record (the chips come back). So a stop,
 * a crash or a removed table in the middle of a hand voids that hand for everybody (each gets the chips of its start),
 * and nothing can pay twice. A quit or a death in the middle of a hand is a fold: before the ledger settles, the
 * table's {@link #playerLeaving} hook lets the hand handle it ({@link HoldemHand#leave}: with nothing left to decide
 * his result is fixed by the shoe and booked for every seat, else he folds after getting back a bet nobody matched)
 * and writes his chips into his record before the ledger settles it. After every hand and every leave the seated
 * players' files are saved one per tick, so no file can hold a win whose losses another file does not yet hold.
 *
 * <p>The felt shows every player's two cards face down at his seat (the client never gets their faces), the board,
 * and at the showdown the hands still in play turned over. Thread: MAIN.
 */
final class HoldemTable implements HoldemHand.Listener {

    static final String GAME = PokerController.GAME;
    /** A seat's record may wait this long for its next rewrite (each hand's result and every two idle minutes). */
    static final long SEAT_RECORD_ENDS_MILLIS = 600_000;
    static final long EXTEND_EVERY_TICKS = 2_400;
    /** Between hands a seated player farther away than this (blocks, or in another world) stands up. */
    static final double AWAY = 16.0;

    /** One player at the table. */
    static final class Seat {
        final UUID player;
        final String name;
        final int spot;
        final UUID round;
        final int boughtIn;
        /** The chips in front of him at the start of the current hand (between hands: now). */
        int stack;
        long endsMillis;
        /** His index in the running hand, -1 if he does not play it. */
        int index = -1;
        /** He has left the table (quit, death, GEHEN): his record is settled, the hand skips him. */
        boolean gone;
        String action = "";
        boolean winner;
        int won;
        /** Turns in a row that ran out: at two he stands up after the hand (nobody waits for an empty chair). */
        int timeouts;
        /** What the last revision of his record changed (losers' files are saved first, see queueSaves). */
        int gain;
        /** His hole cards as turned up at the showdown of the running hand (empty before). */
        List<Integer> shown = List.of();

        Seat(UUID player, String name, int spot, UUID round, int boughtIn, long endsMillis) {
            this.player = player;
            this.name = name;
            this.spot = spot;
            this.round = round;
            this.boughtIn = boughtIn;
            this.stack = boughtIn;
            this.endsMillis = endsMillis;
        }
    }

    private record ScheduledSound(int at, String id, float volume, float pitch) {
    }

    private final PokerController controller;
    private final CasinoContext ctx;
    private final Machine machine;
    private final TreeMap<Integer, Seat> seats = new TreeMap<>();
    private final List<ScheduledSound> sounds = new ArrayList<>();
    private BlackjackShoe shoe;
    private HoldemHand hand;
    private List<Seat> playing = List.of();
    private PokerTableState view = new PokerTableState();
    private long handStart;
    private long ticked;
    private int handNumber;
    private int buttonSpot = -1;
    private long nextHandAt = -1;
    private long lastExtend;
    /** A leaver's exit booked the result the shoe has fixed into every record of the running hand. */
    private boolean shoeBooked;
    private int turnSerial;
    private Component shownPanel;
    /** The seat whose turn just ran out (its automatic check or fold does not count as his own action). */
    private int timingOut = -1;
    /** Players whose file is saved next, one per tick (every file then holds the same hands; see the class comment). */
    private final java.util.ArrayDeque<UUID> saveQueue = new java.util.ArrayDeque<>();

    HoldemTable(PokerController controller, CasinoContext ctx, Machine machine) {
        this.controller = controller;
        this.ctx = ctx;
        this.machine = machine;
    }

    CasinoConfig.Holdem settings() {
        return ctx.config().get().poker().holdem();
    }

    boolean active() {
        return !seats.isEmpty();
    }

    boolean handRunning() {
        return hand != null;
    }

    PokerTableState view() {
        return view;
    }

    Seat seat(UUID player) {
        for (Seat s : seats.values()) {
            if (s.player.equals(player)) {
                return s;
            }
        }
        return null;
    }

    boolean seated(UUID player) {
        return seat(player) != null;
    }

    boolean full() {
        return seats.size() >= PokerLayout.SEATS;
    }

    boolean ownsRound(UUID round) {
        for (Seat s : seats.values()) {
            if (s.round.equals(round)) {
                return controller.holdemOwns(s.endsMillis);
            }
        }
        return false;
    }

    // ---- sitting down and standing up -------------------------------------------------------------------------------

    /** HINSETZEN in the buy-in menu: the chips of its slots become one record, the player takes the nearest seat. */
    void sit(Player player, StakeMenu menu) {
        Messages m = ctx.messages().get();
        CasinoConfig.Holdem h = settings();
        if (!Usability.usable(player)) {
            return;
        }
        if (!ctx.menus().safe(player)) {
            m.actionBar(player, Msg.ERROR_UNSAFE);
            player.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
            return;
        }
        if (seated(player.getUniqueId())) {
            m.actionBar(player, Msg.POKER_HOLDEM_ALREADY);
            return;
        }
        int spot = freeSpot(player);
        if (spot < 0) {
            m.actionBar(player, Msg.POKER_FULL);
            return;
        }
        List<StakeMenu.Stake> stakes = menu.stakes();
        long total = stakes.stream().mapToLong(s -> s.amount().count()).sum();
        if (total < h.minBuyIn() || total > h.maxBuyIn()) {
            m.actionBar(player, Msg.POKER_HOLDEM_BUY_IN, Messages.text("min", ChipText.chips(h.minBuyIn())),
                    Messages.text("max", ChipText.chips(h.maxBuyIn())));
            return;
        }
        List<ItemAmount> take = new ArrayList<>();
        stakes.forEach(s -> take.addAll(s.chips()));
        long ends = ctx.clock().getAsLong() + SEAT_RECORD_ENDS_MILLIS;
        ItemAmount chips = new ItemAmount(Chips.VALUE, total);
        List<UUID> rounds = controller.holdemCommit(player, List.of(new WagerEngine.Request(machine.id(), GAME,
                chips, List.of(chips), ends, "holdem seat=" + spot + " buy-in " + total, RoundState.PLAYING)
                .withTake(take)), Msg.POKER_NO_BET);
        if (rounds == null) {
            menu.render();
            return;
        }
        Seat s = new Seat(player.getUniqueId(), player.getName(), spot, rounds.getFirst(), (int) total, ends);
        seats.put(spot, s);
        controller.holdemKeepChunk(true);
        menu.clearStakes();
        Log.get().info("{} sits down at poker #{} (Texas Hold'em) with {} chips at seat {} ({} seated)",
                player.getName(), machine.record().number(), total, spot, seats.size());
        ctx.menus().open(player, new HoldemMenu(player.getUniqueId(), controller, ctx));
        m.actionBar(player, Msg.POKER_HOLDEM_SEATED);
        // the first time at a Hold'em table: how it works, in the chat (the ? in the menu has it too)
        var pdc = player.getPersistentDataContainer();
        if (!pdc.has(RULES_SEEN, org.bukkit.persistence.PersistentDataType.BYTE)) {
            pdc.set(RULES_SEEN, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
            m.send(player, Msg.POKER_HOLDEM_RULES);
        }
    }

    /** Player PDC: he has been shown the Hold'em rules (on his first sit-down). */
    static final org.bukkit.NamespacedKey RULES_SEEN = de.tigges.kaboofcasino.util.Keys.key("holdem_rules_seen");

    private int freeSpot(Player player) {
        double[] p = controller.tablePosition(player.getLocation());
        int best = PokerLayout.nearestSeat(p[0], p[1]);
        for (int d = 0; d < PokerLayout.SEATS; d++) {
            for (int spot : new int[] {best - d, best + d}) {
                if (spot >= 0 && spot < PokerLayout.SEATS && !seats.containsKey(spot)) {
                    return spot;
                }
            }
        }
        return -1;
    }

    /** True if the player still plays the running hand (dealt in, not folded, not decided yet). */
    boolean inHand(Seat s) {
        return hand != null && s.index >= 0 && !s.gone && !hand.folded(s.index)
                && hand.street().ordinal() < HoldemHand.Street.RESULT.ordinal();
    }

    /**
     * The chips he would take with him now: in a running hand his stack less what he put in (from the showdown on with
     * what he wins, which the hand has already booked), else his stack.
     */
    private int behind(Seat s) {
        return hand != null && s.index >= 0 ? hand.stack(s.index) : s.stack;
    }

    /** GEHEN: not while he still plays a hand (he folds first); the record settles with what is in front of him. */
    void leave(Player player) {
        Seat s = seat(player.getUniqueId());
        if (s == null) {
            return;
        }
        if (inHand(s)) {
            ctx.messages().get().actionBar(player, Msg.POKER_HOLDEM_FOLD_FIRST);
            return;
        }
        standUp(player, s, Msg.POKER_HOLDEM_LEFT, "stands up");
    }

    private void standUp(Player player, Seat s, Msg message, String why) {
        int chips = behind(s);
        revise(player, s, chips, why);
        controller.holdemSettle(player.getUniqueId(), player.getName(), s.round, SettleReason.NORMAL);
        remove(s, why + " with " + chips);
        queueSaves(player);
        ctx.messages().get().send(player, message, Messages.text("chips", ChipText.chips(chips)));
        if (MenuService.openMenu(player) instanceof HoldemMenu menu && menu.controller() == controller) {
            player.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
        }
    }

    /**
     * Quit, kick or death, before the ledger settles: a player in a running hand folds now (if that leaves one player,
     * the pot goes to that one at once), his record pays what he has behind, the seat is free.
     */
    void playerLeaving(Player player, boolean death) {
        Seat s = seat(player.getUniqueId());
        if (s == null) {
            return;
        }
        int chips = behind(s);
        if (inHand(s) || hand != null && s.index >= 0 && hand.street() == HoldemHand.Street.SHOWDOWN) {
            chips = hand.leave(s.index);
            if (hand != null && hand.settledByShoe()) {
                // the result is fixed by the shoe: every record holds it now, so no file can disagree after a crash
                shoeBooked = true;
                int[] end = hand.projected();
                for (Seat other : seats.values()) {
                    if (other != s && !other.gone && other.index >= 0) {
                        other.gain = end[other.index] - other.stack;
                        revise(Bukkit.getPlayer(other.player), other, end[other.index], "hand " + handNumber
                                + " decided by the shoe");
                    }
                }
            }
        }
        revise(player, s, chips, death ? "died at the table" : "quit at the table");
        remove(s, (death ? "died" : "quit") + " with " + chips);
        queueSaves(null);
    }

    private void remove(Seat s, String why) {
        s.gone = true;
        seats.remove(s.spot);
        Log.get().info("poker #{} (Texas Hold'em): {} leaves seat {} ({})", machine.record().number(), s.name, s.spot,
                why);
        if (seats.isEmpty()) {
            controller.holdemKeepChunk(false);
            nextHandAt = -1;
        }
    }

    /**
     * Saves the files of the seated players (and of {@code also}) one per tick, at the front of the queue: Paper saves
     * every player on his own schedule, so without this a crash could restore one file from after a hand and another
     * from before it. {@code Player.saveData} is slow on this disk, hence one per tick. Losers first: an unsaved
     * loser's old record would pay his old, bigger stack again; an unsaved winner only misses his win.
     */
    private void queueSaves(Player also) {
        List<Seat> order = new ArrayList<>(seats.values());
        order.sort(java.util.Comparator.comparingInt(s -> s.gain));
        List<UUID> ids = new ArrayList<>();
        for (Seat s : order) {
            ids.add(s.player);
        }
        if (also != null) {
            ids.add(also.getUniqueId());
        }
        saveQueue.removeAll(ids);
        for (int i = ids.size() - 1; i >= 0; i--) {
            saveQueue.addFirst(ids.get(i));
        }
    }

    private void saveNext() {
        UUID id = saveQueue.poll();
        Player p = id == null ? null : Bukkit.getPlayer(id);
        if (p != null && p.isOnline()) {
            p.saveData();
        }
    }

    /** The table is removed: a running, undecided hand is void (the records hold its start); every record settles. */
    void removed() {
        if (hand != null) {
            Log.get().info("poker #{} (Texas Hold'em): table removed during hand {} ({})", machine.record().number(),
                    handNumber, hand.street().ordinal() < HoldemHand.Street.RESULT.ordinal() ? "void" : "decided");
            hand.abort();
            hand = null;
        }
        for (Seat s : List.copyOf(seats.values())) {
            controller.holdemSettle(s.player, s.name, s.round, SettleReason.MACHINE_GONE);
            s.gone = true;
        }
        seats.clear();
        controller.holdemKeepChunk(false);
        closeMenus();
        view = new PokerTableState();
    }

    // ---- records ----------------------------------------------------------------------------------------------------

    /**
     * The seat's record now pays {@code chips} (and lives another 10 minutes). GONE: settled elsewhere. A dying player
     * is still written (the death hook runs while he is dead, before the ledger drains his records).
     */
    private WagerEngine.Revision revise(Player player, Seat s, int chips, String why) {
        if (player == null || !player.isOnline()) {
            return WagerEngine.Revision.GONE;
        }
        try {
            PlayerAccount account = new PlayerAccount(player);
            RoundRecord old = account.ledger().round(s.round).orElse(null);
            if (old == null) {
                return WagerEngine.Revision.GONE;
            }
            long ends = ctx.clock().getAsLong() + SEAT_RECORD_ENDS_MILLIS;
            List<ItemAmount> payout = chips > 0 ? List.of(new ItemAmount(Chips.VALUE, chips)) : List.of();
            WagerEngine.Revision r = ctx.engine().revise(account, List.of(new RoundRecord(old.id(), old.machine(),
                    old.game(), RoundState.PLAYING, old.stake(), payout, old.created(), ends,
                    "holdem seat=" + s.spot + " stack=" + chips + " " + why)), List.of());
            if (r == WagerEngine.Revision.DONE) {
                s.endsMillis = ends;
            }
            return r;
        } catch (LedgerCodec.CorruptLedgerException e) {
            Log.critical("LEDGER_UNREADABLE", "holdem {} for {} not recorded: {}", why, s.name, e.getMessage());
            return WagerEngine.Revision.GONE;
        }
    }

    // ---- the hands --------------------------------------------------------------------------------------------------

    void tick(World world, long now) {
        // a seat whose player is gone without the hook (should not happen): its record was settled by the ledger
        for (Seat s : List.copyOf(seats.values())) {
            Player p = Bukkit.getPlayer(s.player);
            if (p == null || !p.isOnline() || p.isDead()) {
                if (inHand(s)) {
                    hand.leave(s.index);
                }
                remove(s, "gone");
            }
        }
        saveNext();
        if (hand == null) {
            // between hands: whoever walked away from the table or let two turns run out stands up
            for (Seat s : List.copyOf(seats.values())) {
                Player p = Bukkit.getPlayer(s.player);
                if (p != null && !controller.holdemNear(p, AWAY)) {
                    standUp(p, s, Msg.POKER_HOLDEM_AWAY, "walked away");
                } else if (p != null && s.timeouts >= 2) {
                    standUp(p, s, Msg.POKER_HOLDEM_IDLE, "let two turns run out");
                }
            }
        } else {
            // a player in danger cannot act (his menu closes): his turn gets more time (a few times)
            Seat acting = seatOf(hand.current());
            Player p = acting == null ? null : Bukkit.getPlayer(acting.player);
            if (p != null && !ctx.menus().safe(p) && hand.deadline() - hand.now() <= 100 && hand.extendTurn(400)) {
                ctx.messages().get().actionBar(p, Msg.POKER_HOLDEM_DANGER_TIME);
            }
        }
        if (hand != null) {
            runHand(world, now);
        } else {
            long ready = seats.values().stream().filter(s -> s.stack > 0).count();
            if (ready < 2) {
                nextHandAt = -1;
            } else if (nextHandAt < 0) {
                nextHandAt = now + settings().startSeconds() * 20L;
            } else if (now >= nextHandAt) {
                startHand(now);
            }
        }
        if (now - lastExtend >= EXTEND_EVERY_TICKS) {
            // the records keep paying the stack at the start of the hand (or the result the shoe has fixed, which a
            // leaver's exit booked); only their end moves on
            lastExtend = now;
            int[] end = hand != null && shoeBooked ? hand.projected() : null;
            for (Seat s : List.copyOf(seats.values())) {
                Player p = Bukkit.getPlayer(s.player);
                int chips = end != null && s.index >= 0 ? end[s.index] : s.stack;
                if (revise(p, s, chips, hand == null ? "waiting" : "hand " + handNumber)
                        == WagerEngine.Revision.GONE && p != null && p.isOnline()) {
                    // his record was paid out elsewhere (the start of the hand): the hand cannot go on with his chips
                    Log.critical("HOLDEM_RECORD_GONE", "poker #{} (Texas Hold'em): the record of {} was settled "
                            + "outside the table; hand {} is void, the seat is closed", machine.record().number(), s.name,
                            handNumber);
                    voidHand(now);
                    remove(s, "record settled elsewhere");
                }
            }
        }
    }

    private void startHand(long now) {
        CasinoConfig.Holdem h = settings();
        List<Seat> in = new ArrayList<>();
        for (Seat s : seats.values()) {
            s.index = -1;
            s.action = "";
            s.winner = false;
            s.won = 0;
            s.gain = 0;
            s.shown = List.of();
            if (s.stack > 0) {
                in.add(s);
            }
        }
        if (in.size() < 2) {
            nextHandAt = -1;
            return;
        }
        int button = 0;
        for (int i = 0; i < in.size(); i++) {
            if (in.get(i).spot > buttonSpot) {
                button = i;
                break;
            }
        }
        buttonSpot = in.get(button).spot;
        int[] stacks = new int[in.size()];
        for (int i = 0; i < in.size(); i++) {
            in.get(i).index = i;
            stacks[i] = in.get(i).stack;
        }
        // a fresh deck from a fresh seed every hand: nobody can learn the generator from the cards he has seen
        shoe = new BlackjackShoe(new ShoeRandom(ctx.random().nextLong()), 1);
        playing = List.copyOf(in);
        handNumber++;
        handStart = now;
        ticked = now;
        sounds.clear();
        view = new PokerTableState(now, in.getFirst().spot);
        view.phaseChanged(PokerRound.Phase.DEALING, 0);
        nextHandAt = -1;
        lastExtend = now;
        shoeBooked = false;
        hand = new HoldemHand(shoe, this, stacks, button, h.smallBlind(), h.bigBlind(), h.turnSeconds() * 20);
        Log.get().info("poker #{} (Texas Hold'em): hand {} for {} (button {}, blinds {}/{})",
                machine.record().number(), handNumber, playing.stream().map(s -> s.name + "=" + s.stack).toList(),
                playing.get(button).name, h.smallBlind(), h.bigBlind());
    }

    private void runHand(World world, long now) {
        if (now <= ticked) {
            return;
        }
        ticked = now;
        hand.tick();
        int at = hand.now();
        for (int i = sounds.size() - 1; i >= 0; i--) {
            ScheduledSound s = sounds.get(i);
            if (s.at() <= at) {
                sounds.remove(i);
                controller.holdemSound(world, s.id(), s.volume(), s.pitch());
            }
        }
        if (hand.isOver()) {
            finishHand(now);
        }
    }

    /**
     * The running hand does not count: everybody at the table keeps the chips of its start (which the records hold). A
     * player who left during it keeps what his exit booked (his chips in the pot are lost, as with a fold).
     */
    private void voidHand(long now) {
        if (hand == null) {
            return;
        }
        hand.abort();
        hand = null;
        for (Seat s : seats.values()) {
            s.index = -1;
            s.action = "";
        }
        playing = List.of();
        view = new PokerTableState();
        nextHandAt = now + settings().nextHandSeconds() * 20L;
    }

    private void finishHand(long now) {
        hand = null;
        for (Seat s : List.copyOf(seats.values())) {
            s.index = -1;
            if (s.stack <= 0) {
                Player p = Bukkit.getPlayer(s.player);
                controller.holdemSettle(s.player, s.name, s.round, SettleReason.NORMAL);
                remove(s, "no chips left");
                if (p != null) {
                    ctx.messages().get().actionBar(p, Msg.POKER_HOLDEM_BUSTED);
                    if (MenuService.openMenu(p) instanceof HoldemMenu menu && menu.controller() == controller) {
                        p.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
                    }
                }
            }
        }
        playing = List.of();
        view = new PokerTableState();
        nextHandAt = seats.size() >= 2 ? now + settings().nextHandSeconds() * 20L : -1;
    }

    private Seat seatOf(int index) {
        return index >= 0 && index < playing.size() ? playing.get(index) : null;
    }

    private void schedule(int at, String id, float volume, float pitch) {
        sounds.add(new ScheduledSound(at, id, volume, pitch));
    }

    // ---- what the players do ----------------------------------------------------------------------------------------

    private int acting(Player player) {
        Seat s = seat(player.getUniqueId());
        return s != null && hand != null && !s.gone && s.index >= 0 && hand.current() == s.index
                && Usability.usable(player) ? s.index : -1;
    }

    void fold(Player player) {
        int i = acting(player);
        if (i >= 0) {
            hand.fold(i);
        }
    }

    void checkOrCall(Player player) {
        int i = acting(player);
        if (i >= 0) {
            hand.checkOrCall(i);
        }
    }

    void raiseTo(Player player, int to) {
        int i = acting(player);
        if (i >= 0) {
            hand.raiseTo(i, Math.max(hand.minRaiseTo(i), Math.min(hand.maxRaiseTo(i), to)));
        }
    }

    // ---- the hand's events ------------------------------------------------------------------------------------------

    private int spotOf(int index) {
        Seat s = seatOf(index);
        return s == null ? 1 : s.spot;
    }

    private static String chips(int n) {
        return ChipText.chips(n);
    }

    private String word(Msg msg, int amount) {
        return ctx.messages().get().raw(msg).replace("<chips>", chips(amount));
    }

    @Override
    public void shuffled(int at) {
        view.shuffled(at);
        schedule(at, "poker_shuffle", 0.9f, 1.0f);
    }

    @Override
    public void blind(int index, int amount, boolean big, int at) {
        Seat s = seatOf(index);
        if (s != null) {
            s.action = word(big ? Msg.POKER_HOLDEM_ACT_BB : Msg.POKER_HOLDEM_ACT_SB, amount);
        }
    }

    @Override
    public void cardDealt(int index, int cardIndex, int card, int at) {
        boolean board = index == HoldemHand.BOARD;
        if (board) {
            view.cardDealt(true, 2 + cardIndex, card, true, at);
        } else {
            view.seat = spotOf(index);
            // face down for everybody: the face reaches the client only at the showdown
            view.cardDealt(false, cardIndex, BlackjackCards.HIDDEN, false, at);
        }
        float pitch = 0.94f + 0.04f * Math.min(cardIndex, 4) + (board ? 0.05f : 0.0f);
        schedule(at + 7, "poker_card_flip", 0.3f, 0.75f);
        schedule(at + 14, "poker_card_deal", 0.8f, pitch);
    }

    @Override
    public void streetChanged(HoldemHand.Street street, int at) {
        PokerRound.Phase phase = switch (street) {
            case DEALING -> PokerRound.Phase.DEALING;
            case PREFLOP, FLOP, TURN, RIVER -> PokerRound.Phase.DECISION;
            case SHOWDOWN -> PokerRound.Phase.SHOWDOWN;
            case RESULT -> PokerRound.Phase.RESULT;
            case CLEARING -> PokerRound.Phase.CLEARING;
            case DONE -> PokerRound.Phase.DONE;
        };
        view.phaseChanged(phase, at);
        if (street == HoldemHand.Street.FLOP || street == HoldemHand.Street.TURN
                || street == HoldemHand.Street.RIVER) {
            for (Seat s : playing) {
                if (!s.gone && hand != null && !hand.folded(s.index) && !hand.allIn(s.index)) {
                    s.action = "";
                }
            }
        }
    }

    @Override
    public void turnOpened(int index, int deadline, int at) {
        Seat s = seatOf(index);
        view.seat = spotOf(index);
        view.watchFrom = -1;
        view.turnOpened(deadline, at);
        turnSerial++;
        if (s != null) {
            Log.get().info("poker #{} (Texas Hold'em) hand {}: turn of {} (to call {}, pot {})",
                    machine.record().number(), handNumber, s.name, hand == null ? 0 : hand.toCall(index),
                    hand == null ? 0 : hand.pot());
            Player p = Bukkit.getPlayer(s.player);
            if (p != null) {
                ctx.messages().get().actionBar(p, Msg.POKER_HOLDEM_YOUR_TURN);
                p.playSound(p, org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.5f);
            }
        }
        schedule(at, "poker_turn", 0.4f, 1.5f);
    }

    @Override
    public void turnTimedOut(int index, int at) {
        Seat s = seatOf(index);
        timingOut = index;
        if (s != null) {
            s.timeouts++;
            Player p = Bukkit.getPlayer(s.player);
            if (p != null) {
                ctx.messages().get().actionBar(p, Msg.POKER_HOLDEM_TIMEOUT);
            }
        }
    }

    @Override
    public void acted(int index, HoldemHand.Action action, int amount, int at) {
        Seat s = seatOf(index);
        if (s == null) {
            return;
        }
        if (index == timingOut) {
            timingOut = -1;
        } else if (action != HoldemHand.Action.SMALL_BLIND && action != HoldemHand.Action.BIG_BLIND) {
            s.timeouts = 0;
        }
        s.action = switch (action) {
            case FOLD -> word(Msg.POKER_HOLDEM_ACT_FOLD, 0);
            case CHECK -> word(Msg.POKER_HOLDEM_ACT_CHECK, 0);
            case CALL -> word(Msg.POKER_HOLDEM_ACT_CALL, amount);
            case BET -> word(Msg.POKER_HOLDEM_ACT_BET, amount);
            case RAISE -> word(Msg.POKER_HOLDEM_ACT_RAISE, amount);
            case ALL_IN -> word(Msg.POKER_HOLDEM_ACT_ALLIN, amount);
            default -> s.action;
        };
        if (action != HoldemHand.Action.FOLD && action != HoldemHand.Action.CHECK) {
            schedule(at, "poker_payout", 0.35f, 1.1f);
        }
        Log.get().info("poker #{} (Texas Hold'em) hand {}: {} {}{}", machine.record().number(), handNumber, s.name,
                action, amount > 0 ? " " + amount : "");
    }

    @Override
    public void revealed(int index, List<Integer> cards, int at) {
        Seat shownBy = seatOf(index);
        if (shownBy != null) {
            shownBy.shown = List.copyOf(cards);
        }
        int spot = spotOf(index);
        for (int i = 0; i < cards.size(); i++) {
            for (BlackjackCardMotion.Card c : view.cards) {
                if (!c.toDealer && c.spot == spot && c.index == i) {
                    c.value = cards.get(i);
                    c.flipAt = view.start + at;
                }
            }
        }
        schedule(at + 6, "poker_card_flip", 0.9f, 1.0f);
    }

    @Override
    public void decided(List<HoldemHand.Award> awards, int[] stacks, boolean showdown, int at) {
        Messages m = ctx.messages().get();
        World world = controller.holdemWorld();
        int best = -1;
        for (int i = 0; i < playing.size(); i++) {
            Seat s = playing.get(i);
            s.won = hand.won(i);
            // a win is more back than put in (an uncalled bet coming back, or a split of one's own chips, is none)
            s.winner = s.won > hand.committed(i);
            if (s.gone) {
                continue;
            }
            s.gain = stacks[i] - s.stack;
            s.stack = stacks[i];
            Player p = Bukkit.getPlayer(s.player);
            if (revise(p, s, s.stack, "after hand " + handNumber) == WagerEngine.Revision.GONE && p != null
                    && p.isOnline()) {
                Log.critical("HOLDEM_RECORD_GONE", "poker #{} (Texas Hold'em): the record of {} was settled outside "
                        + "the table; the seat is closed", machine.record().number(), s.name);
                remove(s, "record settled elsewhere");
            }
        }
        queueSaves(null);
        List<String> log = new ArrayList<>();
        for (int i = 0; i < playing.size(); i++) {
            Seat s = playing.get(i);
            if (!s.winner) {
                continue;
            }
            String handName = showdown && hand.score(i) >= 0
                    ? m.raw(Msg.POKER_HOLDEM_WITH).replace("<hand>", PokerPanel.hand(PokerHands.category(hand.score(i))))
                    : "";
            s.action = word(Msg.POKER_HOLDEM_ACT_WON, s.won);
            log.add(s.name + " +" + s.won + (handName.isEmpty() ? "" : " (" + PokerHands.category(hand.score(i)) + ")"));
            for (Seat other : seats.values()) {
                Player p = Bukkit.getPlayer(other.player);
                if (p != null) {
                    m.send(p, Msg.POKER_HOLDEM_WON, Messages.text("name", s.name),
                            Messages.text("chips", chips(s.won)), Messages.text("hand", handName));
                }
            }
            if (world != null) {
                controller.holdemCelebrate(world, s.spot, showdown && PokerHands.category(hand.score(i))
                        >= PokerHands.FULL_HOUSE);
            }
            if (best < 0 || s.won > playing.get(best).won) {
                best = i;
            }
        }
        view.seat = best >= 0 ? playing.get(best).spot : 1;
        view.reaction(showdown ? BlackjackRound.Reaction.PAY : BlackjackRound.Reaction.COLLECT, at);
        view.decided(PokerRound.Result.WIN, at);
        schedule(at + 6, showdown ? "poker_win" : "poker_push", 1.0f, 1.0f);
        Log.get().info("poker #{} (Texas Hold'em) hand {} decided{}: {} (stacks {})", machine.record().number(),
                handNumber, showdown ? " at the showdown" : " by folds", log,
                playing.stream().map(s -> s.name + "=" + (s.gone ? "gone" : Integer.toString(s.stack))).toList());
    }

    @Override
    public void cleared(int at) {
        view.cleared(at);
        schedule(at + 7, "poker_card_deal", 0.55f, 0.8f);
    }

    // ---- what the players see ---------------------------------------------------------------------------------------

    /** The floating panel's text (null: nothing to show). */
    Component panel(long now) {
        if (seats.isEmpty()) {
            return null;
        }
        CasinoConfig.Holdem h = settings();
        List<HoldemPanel.Line> lines = new ArrayList<>();
        for (Seat s : seats.values()) {
            boolean in = hand != null && s.index >= 0;
            int stack = in ? hand.shownStack(s.index) : s.stack;
            int category = in && hand.showdown() && hand.score(s.index) >= 0
                    && view.resultAt >= 0 ? PokerHands.category(hand.score(s.index)) : -1;
            lines.add(new HoldemPanel.Line(s.player, s.name, stack, s.action,
                    in && hand.current() == s.index, in && hand.folded(s.index), s.spot == buttonSpot && hand != null,
                    category, s.winner && hand != null));
        }
        long seconds = hand == null && nextHandAt >= 0 ? Math.max(0, nextHandAt - now + 19) / 20 : -1;
        int pot = hand != null && hand.street().ordinal() < HoldemHand.Street.RESULT.ordinal() ? hand.pot() : -1;
        return HoldemPanel.render(ctx.messages().get(), new HoldemPanel.Content(h.smallBlind(), h.bigBlind(), pot,
                seconds, hand == null && seats.size() < 2, lines));
    }

    void updatePanel(PokerRig rig, long now) {
        Component text = panel(now);
        if (text == null ? shownPanel != null : !text.equals(shownPanel)) {
            rig.tablePanel(text);
            shownPanel = text;
        }
    }

    void panelRespawned() {
        shownPanel = null;
    }

    /** The menu view of a seated player. */
    HoldemView viewFor(Player viewer, long now) {
        Seat me = seat(viewer.getUniqueId());
        List<HoldemView.Seat> lines = new ArrayList<>();
        for (Seat s : seats.values()) {
            boolean in = hand != null && s.index >= 0;
            boolean live = in && hand.street().ordinal() < HoldemHand.Street.RESULT.ordinal();
            List<Integer> shown = in ? s.shown : List.of();
            int category = !shown.isEmpty() && hand.score(s.index) >= 0 ? PokerHands.category(hand.score(s.index))
                    : -1;
            lines.add(new HoldemView.Seat(s.player, s.name, s.spot, in ? hand.shownStack(s.index) : s.stack,
                    live ? hand.bet(s.index) : 0, in && hand.current() == s.index, in && hand.folded(s.index),
                    live && hand.allIn(s.index), in && s.spot == buttonSpot, s.winner && in, s == me, s.action, shown,
                    category));
        }
        List<Integer> board = new ArrayList<>();
        List<Long> boardAt = new ArrayList<>();
        List<Long> holeAt = new ArrayList<>();
        for (BlackjackCardMotion.Card c : view.cards) {
            if (c.toDealer && c.index >= 2) {
                board.add(c.value);
                boardAt.add(c.dealtAt);
            } else if (!c.toDealer && me != null && c.spot == me.spot) {
                holeAt.add(c.dealtAt);
            }
        }
        if (me == null || hand == null || me.index < 0) {
            String status = hand != null ? "waiting" : seats.size() < 2 ? "lobby" : "next";
            return new HoldemView(status, List.of(), List.of(), board, boardAt, hand == null ? 0 : hand.pot(), 0,
                    false, false, false, false, false, false, 0, 0, 0, false, turnSerial, lines, now);
        }
        int i = me.index;
        HoldemHand.Street street = hand.street();
        boolean decided = street.ordinal() >= HoldemHand.Street.RESULT.ordinal();
        boolean myTurn = hand.current() == i;
        String status;
        if (decided) {
            boolean split = hand.awards().stream().anyMatch(a -> a.winners().size() > 1 && a.winners().contains(i));
            status = hand.folded(i) ? "folded" : me.winner ? "won" : split ? "split" : "lost";
        } else if (hand.folded(i)) {
            status = "folded";
        } else if (street == HoldemHand.Street.SHOWDOWN) {
            status = "showdown";
        } else if (myTurn) {
            status = "your_turn";
        } else if (hand.allIn(i)) {
            status = "all_in";
        } else if (street == HoldemHand.Street.DEALING) {
            status = "dealing";
        } else {
            status = "waiting";
        }
        int left = myTurn ? Math.max(0, hand.deadline() - hand.now()) : 0;
        return new HoldemView(status, hand.hole(i), holeAt, board, boardAt, decided ? 0 : hand.pot(),
                myTurn ? hand.toCall(i) : 0, myTurn, myTurn, hand.canCheck(i), hand.canCall(i), hand.canRaise(i),
                hand.currentBet() == 0, hand.minRaiseTo(i), hand.maxRaiseTo(i), left, inHand(me), turnSerial, lines,
                now);
    }

    void refreshMenus(long now) {
        for (Seat s : seats.values()) {
            Player p = Bukkit.getPlayer(s.player);
            if (p != null && MenuService.openMenu(p) instanceof HoldemMenu menu && menu.controller() == controller) {
                menu.refresh(viewFor(p, now));
            }
        }
    }

    void closeMenus() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (MenuService.openMenu(p) instanceof HoldemMenu menu && menu.controller() == controller
                    || MenuService.openMenu(p) instanceof HoldemBuyInMenu buy && buy.controller() == controller) {
                p.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
            }
        }
    }

    int seatsTaken() {
        return seats.size();
    }

    List<String> names() {
        return seats.values().stream().map(s -> s.name).toList();
    }
}
