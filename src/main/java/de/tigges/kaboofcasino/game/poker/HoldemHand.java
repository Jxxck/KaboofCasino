package de.tigges.kaboofcasino.game.poker;

import de.tigges.kaboofcasino.game.blackjack.BlackjackShoe;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

/**
 * One hand of No-Limit Texas Hold'em between the players at a poker table (Jack 2026-11-10, not in MineVegas: "so
 * bauen, dass man es im Multiplayer gegeneinander spielen kann"). The croupier only deals; the players play against
 * each other for their own chips.
 * <ul>
 *   <li>Seats are 0..n-1 in table order; {@code button} deals. Heads-up the button posts the small blind and acts
 *       first before the flop, last after it; with three the blinds are the two seats after the button and the seat
 *       after the big blind opens. A blind larger than the stack goes all-in for what there is; the bet to call stays
 *       the big blind.</li>
 *   <li>Four streets (pre-flop, flop, turn, river), each a betting round: check, call, bet or raise (at least the big
 *       blind, a raise at least the last full raise), all-in at any time. An all-in raise smaller than a full raise does
 *       not reopen the betting for a player who has already acted on the last full raise. A round ends when every
 *       player who can still act has acted on the current bet. Nobody acts for {@code turnTicks}: a check if allowed,
 *       else a fold.</li>
 *   <li>One player left: all pots go to that player, the cards stay hidden. Nobody (or one) can act any more and the bets
 *       match: the board runs out. Showdown: the hands in play are shown, every pot (main pot and side pots by
 *       commitment level, merged when the same players can win them) goes to the best hand among the players in it,
 *       split evenly on a tie, odd chips one by one from the seat after the button. An uncalled bet comes back as
 *       a pot of its own.</li>
 *   <li>A player who leaves folds at once (the chips in the pot stay there).</li>
 * </ul>
 * The deal follows the mod's poker timing ({@link PokerRound}: shuffle, 14 ticks, 22 ticks a card, 16 with three
 * seats). Pure: chips are plain numbers (chip value), the controller keeps the books.
 */
public final class HoldemHand {

    /** The seat number the listener gets for a board card. */
    public static final int BOARD = -1;
    static final int SHUFFLE = 36;
    static final int OPENING = 14;
    static final int DEAL_STEP = 22;
    static final int CROWDED_DEAL = 16;
    /** After the last card of a street the betting opens this much later (the mod: 23 + 4 after the flop). */
    static final int STREET_PAUSE = 27;
    static final int REVEAL_STEP = 18;
    static final int RESOLVE_PAUSE = 24;
    static final int RESULT_HOLD = 70;
    static final int CLEAR = 18;

    public enum Street {
        DEALING, PREFLOP, FLOP, TURN, RIVER, SHOWDOWN, RESULT, CLEARING, DONE;

        public boolean betting() {
            return this == PREFLOP || this == FLOP || this == TURN || this == RIVER;
        }
    }

    public enum Action {
        SMALL_BLIND, BIG_BLIND, FOLD, CHECK, CALL, BET, RAISE, ALL_IN
    }

    /** One pot as it was paid: its size, the seats that could win it, the winners and what each of them got. */
    public record Award(int amount, List<Integer> eligible, List<Integer> winners, List<Integer> shares) {
        public Award {
            eligible = List.copyOf(eligible);
            winners = List.copyOf(winners);
            shares = List.copyOf(shares);
        }
    }

    public interface Listener {
        void shuffled(int at);

        void blind(int seat, int amount, boolean big, int at);

        /** A hole card to {@code seat} (face down on the felt), or with {@link #BOARD} the board card {@code index}. */
        void cardDealt(int seat, int index, int card, int at);

        void streetChanged(Street street, int at);

        void turnOpened(int seat, int deadline, int at);

        void turnTimedOut(int seat, int at);

        /** {@code amount}: the seat's bet on this street after the action (0 for check and fold). */
        void acted(int seat, Action action, int amount, int at);

        /** At the showdown: the hole cards of a seat still in the hand. */
        void revealed(int seat, List<Integer> cards, int at);

        /** The hand is decided: the pots as paid and every seat's stack afterwards. */
        void decided(List<Award> awards, int[] stacks, boolean showdown, int at);

        void cleared(int at);
    }

    private final BlackjackShoe shoe;
    private final Listener listener;
    private final int seats;
    private final int button;
    private final int smallBlind;
    private final int bigBlind;
    private final int turnTicks;
    private final int[] stacks;
    private final int[] bets;
    private final int[] committed;
    private final boolean[] folded;
    private final boolean[] acted;
    private final boolean[] raiseOpen;
    private final List<List<Integer>> holes = new ArrayList<>();
    private final List<Integer> board = new ArrayList<>();
    private final int[] won;
    private final int[] scores;
    private final List<Step> steps = new ArrayList<>();
    private List<Award> awards = List.of();
    /** The stacks before the awards while the showdown is still being shown (the books already hold the awards). */
    private int[] shown;
    /** Players who left while nothing was left to decide: still in the hand, their result booked at their leave. */
    private final boolean[] gone;
    /** The blind each seat posted (a blind is forced: it stays in the pot when the seat leaves). */
    private final int[] blinds;
    /** The turn's one extension (a player in danger) is used. */
    private boolean extended;
    private int sequence;
    private int now;
    private int deadline = -1;
    private int current = -1;
    private int currentBet;
    private int lastRaise;
    private boolean showdown;
    private Street street = Street.DEALING;

    private record Step(int at, int order, Runnable run) {
    }

    /**
     * Starts a hand: posts the blinds now and schedules the deal. {@code stacks}: each seat's chips (at least 1),
     * {@code button}: the dealing seat.
     */
    public HoldemHand(BlackjackShoe shoe, Listener listener, int[] stacks, int button, int smallBlind, int bigBlind,
                      int turnTicks) {
        if (stacks.length < 2 || stacks.length > PokerLayout.SEATS) {
            throw new IllegalArgumentException("seats: " + stacks.length);
        }
        if (smallBlind < 1 || bigBlind < smallBlind || button < 0 || button >= stacks.length) {
            throw new IllegalArgumentException("blinds " + smallBlind + "/" + bigBlind + ", button " + button);
        }
        for (int s : stacks) {
            if (s < 1) {
                throw new IllegalArgumentException("a seat without chips: " + Arrays.toString(stacks));
            }
        }
        this.shoe = shoe;
        this.listener = listener;
        this.seats = stacks.length;
        this.button = button;
        this.smallBlind = smallBlind;
        this.bigBlind = bigBlind;
        this.turnTicks = turnTicks;
        this.stacks = stacks.clone();
        this.bets = new int[seats];
        this.committed = new int[seats];
        this.folded = new boolean[seats];
        this.acted = new boolean[seats];
        this.raiseOpen = new boolean[seats];
        this.won = new int[seats];
        this.scores = new int[seats];
        this.gone = new boolean[seats];
        this.blinds = new int[seats];
        Arrays.fill(scores, -1);
        Arrays.fill(raiseOpen, true);
        for (int s = 0; s < seats; s++) {
            holes.add(new ArrayList<>());
        }
        int sb = seats == 2 ? button : next(button);
        int bb = next(sb);
        post(sb, smallBlind, false);
        post(bb, bigBlind, true);
        // a big blind all-in for less: nobody has to pay more than was really put in (the raise size stays the blind)
        currentBet = bets[bb] < bigBlind ? Math.max(bets[sb], bets[bb]) : bigBlind;
        lastRaise = bigBlind;
        int t = 0;
        if (shoe.needsShuffle()) {
            shoe.shuffle();
            listener.shuffled(0);
            t += SHUFFLE;
        }
        t += OPENING;
        int step = seats > 2 ? CROWDED_DEAL : DEAL_STEP;
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 1; i <= seats; i++) {
                int seat = (button + i) % seats;
                at(t, () -> deal(seat));
                t += step;
            }
        }
        at(t + STREET_PAUSE - step, () -> openStreet(Street.PREFLOP, seats == 2 ? button : next(bb)));
    }

    // ---- queries ----------------------------------------------------------------------------------------------------

    public int seats() {
        return seats;
    }

    public int button() {
        return button;
    }

    public int smallBlind() {
        return smallBlind;
    }

    public int bigBlind() {
        return bigBlind;
    }

    public Street street() {
        return street;
    }

    public boolean isOver() {
        return street == Street.DONE;
    }

    public int now() {
        return now;
    }

    public int deadline() {
        return deadline;
    }

    /** The seat whose turn it is, -1 outside a betting round. */
    public int current() {
        return street.betting() ? current : -1;
    }

    /** The seat's chips: from the start of the showdown with its winnings (the books), see {@link #shownStack}. */
    public int stack(int seat) {
        return stacks[seat];
    }

    /** The chips to show: without the winnings until the hand is decided (the cards are still being turned). */
    public int shownStack(int seat) {
        return shown != null ? shown[seat] : stacks[seat];
    }

    /** The seat's bet on this street. */
    public int bet(int seat) {
        return bets[seat];
    }

    /** Everything the seat has put into the pot this hand (this street included). */
    public int committed(int seat) {
        return committed[seat];
    }

    public boolean folded(int seat) {
        return folded[seat];
    }

    public boolean allIn(int seat) {
        return !folded[seat] && stacks[seat] == 0;
    }

    public int currentBet() {
        return currentBet;
    }

    /** All chips in the middle (every seat's commitment). */
    public int pot() {
        int p = 0;
        for (int c : committed) {
            p += c;
        }
        return p;
    }

    public List<Integer> hole(int seat) {
        return Collections.unmodifiableList(holes.get(seat));
    }

    public List<Integer> board() {
        return Collections.unmodifiableList(board);
    }

    /** What the seat got from the pots (0 until the hand is decided). */
    public int won(int seat) {
        return won[seat];
    }

    /** The seat's hand score at the showdown ({@link PokerHands#score}), -1 otherwise. */
    public int score(int seat) {
        return scores[seat];
    }

    public boolean showdown() {
        return showdown;
    }

    public List<Award> awards() {
        return awards;
    }

    public int toCall(int seat) {
        return Math.min(stacks[seat], Math.max(0, currentBet - bets[seat]));
    }

    private boolean acting(int seat) {
        return street.betting() && current == seat && !folded[seat] && stacks[seat] > 0;
    }

    public boolean canCheck(int seat) {
        return acting(seat) && bets[seat] >= currentBet;
    }

    public boolean canCall(int seat) {
        return acting(seat) && bets[seat] < currentBet;
    }

    /** True if the seat may bet or raise now (it has chips beyond the call and the betting is open to it). */
    public boolean canRaise(int seat) {
        return acting(seat) && raiseOpen[seat] && stacks[seat] > currentBet - bets[seat]
                && othersCanAct(seat);
    }

    /** The smallest legal bet or raise (the bet on this street after it), capped at the seat's all-in. */
    public int minRaiseTo(int seat) {
        int min = currentBet == 0 ? bigBlind : currentBet + lastRaise;
        return Math.min(min, maxRaiseTo(seat));
    }

    /** The seat's all-in as a bet on this street. */
    public int maxRaiseTo(int seat) {
        return bets[seat] + stacks[seat];
    }

    // ---- actions ----------------------------------------------------------------------------------------------------

    public boolean fold(int seat) {
        if (!acting(seat)) {
            return false;
        }
        folded[seat] = true;
        listener.acted(seat, Action.FOLD, 0, now);
        afterAction(seat);
        return true;
    }

    /** Check, or call the current bet (all-in when the stack is short). */
    public boolean checkOrCall(int seat) {
        if (canCheck(seat)) {
            acted[seat] = true;
            listener.acted(seat, Action.CHECK, 0, now);
            afterAction(seat);
            return true;
        }
        if (!canCall(seat)) {
            return false;
        }
        int pay = toCall(seat);
        put(seat, pay);
        acted[seat] = true;
        listener.acted(seat, stacks[seat] == 0 ? Action.ALL_IN : Action.CALL, bets[seat], now);
        afterAction(seat);
        return true;
    }

    /**
     * Bet or raise so that the seat's bet on this street becomes {@code to}: at least {@link #minRaiseTo} unless it
     * is the seat's all-in, at most {@link #maxRaiseTo}.
     */
    public boolean raiseTo(int seat, int to) {
        if (!canRaise(seat) || to > maxRaiseTo(seat) || to <= currentBet) {
            return false;
        }
        boolean allIn = to == maxRaiseTo(seat);
        if (to < minRaiseTo(seat) && !allIn) {
            return false;
        }
        boolean opening = currentBet == 0;
        int increment = to - currentBet;
        boolean full = increment >= lastRaise || opening && to >= bigBlind;
        put(seat, to - bets[seat]);
        currentBet = to;
        acted[seat] = true;
        if (full) {
            lastRaise = Math.max(lastRaise, increment);
        }
        for (int s = 0; s < seats; s++) {
            if (s != seat && !folded[s] && stacks[s] > 0) {
                if (full) {
                    raiseOpen[s] = true;
                } else if (acted[s]) {
                    // an incomplete all-in raise: who has acted may only call or fold
                    raiseOpen[s] = false;
                }
                acted[s] = false;
            }
        }
        raiseOpen[seat] = true;
        listener.acted(seat, allIn ? Action.ALL_IN : opening ? Action.BET : Action.RAISE, to, now);
        afterAction(seat);
        return true;
    }

    /** All-in: a raise if the chips go beyond the bet, else a call. */
    public boolean goAllIn(int seat) {
        if (canRaise(seat)) {
            return raiseTo(seat, maxRaiseTo(seat));
        }
        return checkOrCall(seat);
    }

    /**
     * The player has left (quit, death, standing up is only possible outside a hand). Returns his chips from now on:
     * <ul>
     *   <li>nothing is left to decide at the table (everybody all-in, or one player with chips who has matched every
     *       bet) and all hole cards are out: he stays in the hand, the rest of the board is already in the shoe, so his
     *       result is certain and booked now ({@link #projected()} has everybody's);</li>
     *   <li>from the showdown on: the pots are booked, his stack is final;</li>
     *   <li>else he folds: first the part of his bet nobody else matched comes back (never his blind, which is
     *       forced), the bet the others face is the highest one still alive; his chips in the pot stay there.</li>
     * </ul>
     */
    public int leave(int seat) {
        if (folded[seat] || gone[seat]) {
            return stacks[seat];
        }
        if (street.ordinal() >= Street.SHOWDOWN.ordinal()) {
            gone[seat] = true;
            return stacks[seat];
        }
        if (holesComplete() && !decisionsLeft()) {
            gone[seat] = true;
            return projected()[seat];
        }
        int others = blinds[seat];
        for (int s = 0; s < seats; s++) {
            if (s != seat && !folded[s]) {
                others = Math.max(others, committed[s]);
            }
        }
        int excess = committed[seat] - others;
        if (excess > 0) {
            stacks[seat] += excess;
            committed[seat] -= excess;
            bets[seat] -= Math.min(excess, bets[seat]);
        }
        folded[seat] = true;
        int live = 0;
        for (int s = 0; s < seats; s++) {
            if (!folded[s]) {
                live = Math.max(live, bets[s]);
            }
        }
        currentBet = Math.min(currentBet, live);
        listener.acted(seat, Action.FOLD, 0, now);
        if (street == Street.DEALING) {
            if (inHand() == 1) {
                steps.clear();
                endByFolds();
            }
            return stacks[seat];
        }
        afterAction(seat);
        return stacks[seat];
    }

    /** True if the seat left the table but stays in the hand (its result was booked at the leave). */
    public boolean gone(int seat) {
        return gone[seat];
    }

    private boolean holesComplete() {
        for (int s = 0; s < seats; s++) {
            if (!folded[s] && holes.get(s).size() < 2) {
                return false;
            }
        }
        return true;
    }

    /** Somebody with chips still has to or may act (a bet to answer, or two players who can still bet). */
    private boolean decisionsLeft() {
        int able = canAct();
        if (able >= 2) {
            return true;
        }
        for (int s = 0; s < seats; s++) {
            if (!folded[s] && stacks[s] > 0 && bets[s] < currentBet) {
                return true;
            }
        }
        return false;
    }

    /**
     * Everybody's stack at the end of this hand if nobody decides anything more: the rest of the board from a copy
     * of the shoe (the live hand deals exactly these cards), the pots to the best hands. With decisions left it is
     * only a guess; callers use it when nothing is left to decide.
     */
    public int[] projected() {
        if (street.ordinal() >= Street.SHOWDOWN.ordinal()) {
            return stacks.clone();
        }
        BlackjackShoe copy = shoe.copy();
        List<Integer> brd = new ArrayList<>(board);
        while (brd.size() < 5) {
            brd.add(copy.draw());
        }
        int[] score = new int[seats];
        for (int s = 0; s < seats; s++) {
            if (!folded[s]) {
                List<Integer> cards = new ArrayList<>(holes.get(s));
                cards.addAll(brd);
                score[s] = PokerHands.score(cards);
            }
        }
        int[] out = stacks.clone();
        for (Pot p : pots(committed, folded)) {
            int best = -1;
            for (int s : p.eligible) {
                best = Math.max(best, score[s]);
            }
            List<Integer> winners = new ArrayList<>();
            for (int i = 1; i <= seats; i++) {
                int s = (button + i) % seats;
                if (p.eligible.contains(s) && score[s] == best) {
                    winners.add(s);
                }
            }
            int each = p.amount / winners.size();
            int odd = p.amount % winners.size();
            for (int i = 0; i < winners.size(); i++) {
                out[winners.get(i)] += each + (i < odd ? 1 : 0);
            }
        }
        return out;
    }

    /** True once nothing is left to decide (the result is fixed by the shoe): see {@link #projected()}. */
    public boolean settledByShoe() {
        return street.ordinal() >= Street.SHOWDOWN.ordinal() || holesComplete() && !decisionsLeft()
                && inHand() >= 2;
    }

    /** Gives the player whose turn it is more time once per turn (he is in danger and cannot act). */
    public boolean extendTurn(int ticks) {
        if (!street.betting() || current < 0 || extended) {
            return false;
        }
        extended = true;
        deadline += ticks;
        return true;
    }

    public void abort() {
        steps.clear();
        street = Street.DONE;
    }

    public void tick() {
        if (street == Street.DONE) {
            return;
        }
        now++;
        if (street.betting() && current >= 0 && now >= deadline) {
            int seat = current;
            listener.turnTimedOut(seat, now);
            if (!checkOrCallIfCheck(seat)) {
                fold(seat);
            }
        }
        do {
            Step due = null;
            for (Step s : steps) {
                if (s.at <= now && (due == null || s.at < due.at || s.at == due.at && s.order < due.order)) {
                    due = s;
                }
            }
            if (due == null) {
                return;
            }
            steps.remove(due);
            due.run.run();
        } while (street != Street.DONE);
    }

    private boolean checkOrCallIfCheck(int seat) {
        return canCheck(seat) && checkOrCall(seat);
    }

    // ---- the flow ---------------------------------------------------------------------------------------------------

    private void at(int time, Runnable run) {
        steps.add(new Step(time, sequence++, run));
    }

    private void after(int delay, Runnable run) {
        at(now + delay, run);
    }

    private int next(int seat) {
        return (seat + 1) % seats;
    }

    private void post(int seat, int amount, boolean big) {
        int pay = Math.min(amount, stacks[seat]);
        put(seat, pay);
        blinds[seat] = pay;
        listener.blind(seat, pay, big, now);
    }

    private void put(int seat, int amount) {
        stacks[seat] -= amount;
        bets[seat] += amount;
        committed[seat] += amount;
    }

    private void deal(int seat) {
        if (folded[seat]) {
            // a player who left during the deal gets no more cards
            return;
        }
        int card = shoe.draw();
        holes.get(seat).add(card);
        listener.cardDealt(seat, holes.get(seat).size() - 1, card, now);
    }

    private void dealBoard() {
        int card = shoe.draw();
        board.add(card);
        listener.cardDealt(BOARD, board.size() - 1, card, now);
    }

    private int inHand() {
        int n = 0;
        for (boolean f : folded) {
            n += f ? 0 : 1;
        }
        return n;
    }

    /** Seats still in the hand with chips behind (they can act). */
    private int canAct() {
        int n = 0;
        for (int s = 0; s < seats; s++) {
            n += !folded[s] && stacks[s] > 0 ? 1 : 0;
        }
        return n;
    }

    private boolean othersCanAct(int seat) {
        for (int s = 0; s < seats; s++) {
            if (s != seat && !folded[s] && stacks[s] > 0) {
                return true;
            }
        }
        return false;
    }

    private void setStreet(Street next) {
        street = next;
        listener.streetChanged(next, now);
    }

    private void openStreet(Street next, int first) {
        if (inHand() == 1) {
            endByFolds();
            return;
        }
        setStreet(next);
        if (next != Street.PREFLOP) {
            Arrays.fill(bets, 0);
            currentBet = 0;
            lastRaise = bigBlind;
        }
        for (int s = 0; s < seats; s++) {
            acted[s] = false;
            raiseOpen[s] = true;
        }
        current = -1;
        if (roundComplete()) {
            finishStreet();
            return;
        }
        current = first;
        if (folded[current] || stacks[current] == 0) {
            current = nextToAct(current);
        }
        openTurn();
    }

    private int nextToAct(int from) {
        for (int i = 1; i <= seats; i++) {
            int s = (from + i) % seats;
            if (!folded[s] && stacks[s] > 0 && (!acted[s] || bets[s] < currentBet)) {
                return s;
            }
        }
        return -1;
    }

    private boolean roundComplete() {
        int able = canAct();
        if (able == 0) {
            return true;
        }
        for (int s = 0; s < seats; s++) {
            if (!folded[s] && stacks[s] > 0 && (!acted[s] || bets[s] < currentBet)) {
                // one player alone with chips who already matches every bet has nobody left to bet against
                return able == 1 && bets[s] >= currentBet;
            }
        }
        return true;
    }

    private void openTurn() {
        deadline = now + turnTicks;
        extended = false;
        listener.turnOpened(current, deadline, now);
    }

    private void afterAction(int seat) {
        if (inHand() == 1) {
            current = -1;
            endByFolds();
            return;
        }
        if (roundComplete()) {
            current = -1;
            finishStreet();
            return;
        }
        if (current == seat || folded[current] || stacks[current] == 0) {
            current = nextToAct(seat);
            if (current < 0) {
                finishStreet();
                return;
            }
            openTurn();
        }
    }

    /** The betting round is over: the next street, the run-out, or the showdown. */
    private void finishStreet() {
        deadline = -1;
        current = -1;
        Street next = switch (street) {
            case PREFLOP -> Street.FLOP;
            case FLOP -> Street.TURN;
            case TURN -> Street.RIVER;
            default -> Street.SHOWDOWN;
        };
        if (next == Street.SHOWDOWN) {
            startShowdown();
            return;
        }
        int cards = next == Street.FLOP ? 3 : 1;
        // the street's cards come at once (one every 22 ticks), its betting after the pause
        street = Street.DEALING;
        listener.streetChanged(Street.DEALING, now);
        for (int i = 0; i < cards; i++) {
            after(22 * i, this::dealBoard);
        }
        after(22 * (cards - 1) + STREET_PAUSE, () -> openStreet(next, next(button)));
    }

    private void startShowdown() {
        showdown = true;
        shown = stacks.clone();
        award();
        setStreet(Street.SHOWDOWN);
        int delay = 0;
        for (int i = 1; i <= seats; i++) {
            int seat = (button + i) % seats;
            if (!folded[seat]) {
                List<Integer> cards = List.copyOf(holes.get(seat));
                after(delay, () -> listener.revealed(seat, cards, now));
                delay += REVEAL_STEP;
            }
        }
        after(delay + RESOLVE_PAUSE, this::finish);
    }

    private void endByFolds() {
        int winner = -1;
        for (int s = 0; s < seats; s++) {
            if (!folded[s]) {
                winner = s;
            }
        }
        int pot = pot();
        stacks[winner] += pot;
        won[winner] = pot;
        awards = List.of(new Award(pot, List.of(winner), List.of(winner), List.of(pot)));
        finish();
    }

    /** The pots to the best hands (the books change now, the players see it at the end of the showdown). */
    private void award() {
        for (int s = 0; s < seats; s++) {
            if (!folded[s]) {
                List<Integer> cards = new ArrayList<>(holes.get(s));
                cards.addAll(board);
                scores[s] = PokerHands.score(cards);
            }
        }
        List<Award> out = new ArrayList<>();
        for (Pot p : pots(committed, folded)) {
            int best = -1;
            for (int s : p.eligible) {
                best = Math.max(best, scores[s]);
            }
            List<Integer> winners = new ArrayList<>();
            // odd chips go one by one from the seat after the button
            for (int i = 1; i <= seats; i++) {
                int s = (button + i) % seats;
                if (p.eligible.contains(s) && scores[s] == best) {
                    winners.add(s);
                }
            }
            List<Integer> shares = new ArrayList<>();
            int each = p.amount / winners.size();
            int odd = p.amount % winners.size();
            for (int i = 0; i < winners.size(); i++) {
                int share = each + (i < odd ? 1 : 0);
                shares.add(share);
                stacks[winners.get(i)] += share;
                won[winners.get(i)] += share;
            }
            out.add(new Award(p.amount, p.eligible, winners, shares));
        }
        awards = List.copyOf(out);
    }

    private void finish() {
        deadline = -1;
        current = -1;
        shown = null;
        setStreet(Street.RESULT);
        listener.decided(awards, stacks.clone(), showdown, now);
        after(RESULT_HOLD, () -> {
            setStreet(Street.CLEARING);
            listener.cleared(now);
            after(CLEAR, () -> setStreet(Street.DONE));
        });
    }

    /** One pot: its chips and the seats (still in the hand) that can win it. */
    record Pot(int amount, List<Integer> eligible) {
    }

    /**
     * The main pot and the side pots: one per commitment level (each seat pays up to the level), won by the seats in
     * the hand that reached it; neighbouring pots with the same seats are merged. Every chip committed is in a pot.
     */
    static List<Pot> pots(int[] committed, boolean[] folded) {
        TreeSet<Integer> levels = new TreeSet<>();
        for (int s = 0; s < committed.length; s++) {
            if (!folded[s] && committed[s] > 0) {
                levels.add(committed[s]);
            }
        }
        List<Pot> out = new ArrayList<>();
        int previous = 0;
        int max = levels.isEmpty() ? 0 : levels.last();
        for (int level : levels) {
            int amount = 0;
            List<Integer> eligible = new ArrayList<>();
            for (int s = 0; s < committed.length; s++) {
                int top = level == max ? committed[s] : Math.min(committed[s], level);
                amount += Math.max(0, top - Math.min(committed[s], previous));
                if (!folded[s] && committed[s] >= level) {
                    eligible.add(s);
                }
            }
            previous = level;
            if (amount == 0) {
                continue;
            }
            if (!out.isEmpty() && out.getLast().eligible.equals(eligible)) {
                Pot last = out.removeLast();
                out.add(new Pot(last.amount + amount, eligible));
            } else {
                out.add(new Pot(amount, eligible));
            }
        }
        return out;
    }
}
