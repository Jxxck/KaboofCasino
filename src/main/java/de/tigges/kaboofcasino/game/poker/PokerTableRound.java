package de.tigges.kaboofcasino.game.poker;

import de.tigges.kaboofcasino.game.blackjack.BlackjackRound;
import de.tigges.kaboofcasino.game.blackjack.BlackjackShoe;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One Casino Hold'em hand at a table with several players (PLAN-2 C5, not in MineVegas, which deals to one player).
 * {@link PokerRound} with its timing and rules, widened to seats: the opening deal goes seat by seat with the dealer
 * last in each pass, then the flop; one decision window for everybody ({@link PokerRound#TURN_TIMEOUT} ticks), in
 * which every seat calls or folds on its own and which closes early once all have decided; a seat that has not decided
 * when it closes folds, a player who has left folds at once. If at least one seat called, turn and river come and the
 * dealer shows his cards once for all; every called seat is resolved against that one dealer. Nobody called: the hand
 * ends without them. Bias is the single player's setting and does not exist here. The turn and the river are the next
 * two cards of the shoe whoever calls, so {@link PokerCall#call} on a copy of the shoe predicts a seat's outcome at its
 * call ({@code PokerTableRoundTest}). Pure.
 */
public final class PokerTableRound {

    /** The seat number the listener gets for the dealer's cards and the board ({@code index} 0-1 dealer, 2-6 board). */
    public static final int DEALER = -1;
    /** Ticks between two opening cards with more than two seats (the mod's 22 would drag at three). */
    static final int CROWDED_DEAL = 16;

    /** What the table has to show and pay. */
    public interface Listener {
        void shuffled(int at);

        /** A card to {@code seat}, or with {@link #DEALER} to the dealer ({@code index} 0-1) or the board (2-6). */
        void cardDealt(int seat, int index, int card, boolean faceUp, int at);

        void holeCardRevealed(int index, int card, int at);

        void decisionOpened(int deadline, int at);

        /** {@code seat} had not decided when the window closed: it folds. */
        void decisionTimedOut(int seat, int at);

        void phaseChanged(PokerRound.Phase phase, int at);

        /** A seat's result: a fold at once (or when the window closes), the others at the showdown. */
        void decided(int seat, PokerRound.Result result, int thirds, int at);

        void reaction(BlackjackRound.Reaction reaction, int at);

        void cleared(int at);
    }

    /** A seat's own decision. */
    public enum Choice {
        OPEN, CALLED, FOLDED
    }

    private final BlackjackShoe shoe;
    private final Listener listener;
    private final int seats;
    private final List<List<Integer>> hands = new ArrayList<>();
    private final List<Integer> dealer = new ArrayList<>();
    private final List<Integer> board = new ArrayList<>();
    private final Choice[] choices;
    private final boolean[] gone;
    private final PokerRound.Result[] results;
    private final int[] thirds;
    private final int[] scores;
    private final boolean[] revealed = new boolean[2];
    private final List<Step> steps = new ArrayList<>();
    private int sequence;
    private int now;
    private int deadline = -1;
    private int dealerScore = -1;
    private PokerRound.Phase phase = PokerRound.Phase.DEALING;

    private record Step(int at, int order, Runnable run) {
    }

    public PokerTableRound(BlackjackShoe shoe, Listener listener, int seats) {
        if (seats < 1 || seats > PokerLayout.SEATS) {
            throw new IllegalArgumentException("seats: " + seats);
        }
        this.shoe = shoe;
        this.listener = listener;
        this.seats = seats;
        this.choices = new Choice[seats];
        this.gone = new boolean[seats];
        this.results = new PokerRound.Result[seats];
        this.thirds = new int[seats];
        this.scores = new int[seats];
        for (int s = 0; s < seats; s++) {
            hands.add(new ArrayList<>());
            choices[s] = Choice.OPEN;
            results[s] = PokerRound.Result.NONE;
            scores[s] = -1;
        }
        int t = 0;
        if (shoe.needsShuffle()) {
            shoe.shuffle();
            listener.shuffled(0);
            t += 36;
        }
        t += 14;
        int step = seats > 2 ? CROWDED_DEAL : 22;
        for (int pass = 0; pass < 2; pass++) {
            for (int s = 0; s < seats; s++) {
                int seat = s;
                at(t, () -> dealTo(seat));
                t += step;
            }
            at(t, () -> dealTo(DEALER));
            t += step;
        }
        for (int i = 0; i < 3; i++) {
            at(t, this::dealBoard);
            if (i < 2) {
                t += 22;
            }
        }
        at(t + 23 + 4, this::openDecision);
    }

    public PokerRound.Phase phase() {
        return phase;
    }

    public int seats() {
        return seats;
    }

    public int now() {
        return now;
    }

    public int deadline() {
        return deadline;
    }

    public boolean isOver() {
        return phase == PokerRound.Phase.DONE;
    }

    public List<Integer> hand(int seat) {
        return Collections.unmodifiableList(hands.get(seat));
    }

    public List<Integer> dealer() {
        return Collections.unmodifiableList(dealer);
    }

    public List<Integer> dealerVisible() {
        List<Integer> out = new ArrayList<>(dealer);
        for (int i = 0; i < out.size(); i++) {
            if (!revealed[i]) {
                out.set(i, -1);
            }
        }
        return out;
    }

    public List<Integer> board() {
        return Collections.unmodifiableList(board);
    }

    public Choice choice(int seat) {
        return choices[seat];
    }

    public boolean called(int seat) {
        return choices[seat] == Choice.CALLED;
    }

    public PokerRound.Result result(int seat) {
        return results[seat];
    }

    /** The payout in thirds of the seat's (called, 3 x ante) stake; 0 until it is decided. */
    public int payoutThirds(int seat) {
        return thirds[seat];
    }

    /** The seat's hand score after the showdown ({@link PokerHands#score}), -1 before or after a fold. */
    public int score(int seat) {
        return scores[seat];
    }

    public int dealerScore() {
        return dealerScore;
    }

    public boolean canCall(int seat) {
        return phase == PokerRound.Phase.DECISION && choices[seat] == Choice.OPEN && !gone[seat];
    }

    public boolean canFold(int seat) {
        return canCall(seat);
    }

    public boolean call(int seat) {
        if (!canCall(seat)) {
            return false;
        }
        choices[seat] = Choice.CALLED;
        closeIfAllDecided();
        return true;
    }

    public boolean fold(int seat) {
        if (!canFold(seat)) {
            return false;
        }
        foldSeat(seat);
        closeIfAllDecided();
        return true;
    }

    /** The player has left (quit after the call, death, his records gone): an open seat folds now or at the window. */
    public void leave(int seat) {
        gone[seat] = true;
        if (phase == PokerRound.Phase.DECISION && choices[seat] == Choice.OPEN) {
            foldSeat(seat);
            closeIfAllDecided();
        }
    }

    public boolean gone(int seat) {
        return gone[seat];
    }

    public void abort() {
        steps.clear();
        phase = PokerRound.Phase.DONE;
    }

    public void tick() {
        if (phase == PokerRound.Phase.DONE) {
            return;
        }
        now++;
        if (phase == PokerRound.Phase.DECISION && now >= deadline) {
            for (int s = 0; s < seats; s++) {
                if (choices[s] == Choice.OPEN) {
                    listener.decisionTimedOut(s, now);
                    foldSeat(s);
                }
            }
            closeDecision();
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
        } while (phase != PokerRound.Phase.DONE);
    }

    private void at(int time, Runnable run) {
        steps.add(new Step(time, sequence++, run));
    }

    private void after(int delay, Runnable run) {
        at(now + delay, run);
    }

    private void setPhase(PokerRound.Phase next) {
        phase = next;
        listener.phaseChanged(next, now);
    }

    private void dealTo(int seat) {
        int card = shoe.draw();
        if (seat == DEALER) {
            dealer.add(card);
            listener.cardDealt(DEALER, dealer.size() - 1, -1, false, now);
        } else {
            List<Integer> hand = hands.get(seat);
            hand.add(card);
            listener.cardDealt(seat, hand.size() - 1, card, true, now);
        }
    }

    private void dealBoard() {
        int card = shoe.draw();
        board.add(card);
        listener.cardDealt(DEALER, 2 + board.size() - 1, card, true, now);
    }

    private void openDecision() {
        setPhase(PokerRound.Phase.DECISION);
        deadline = now + PokerRound.TURN_TIMEOUT;
        listener.decisionOpened(deadline, now);
        // whoever left during the deal folds as the window opens
        for (int s = 0; s < seats; s++) {
            if (gone[s] && choices[s] == Choice.OPEN) {
                foldSeat(s);
            }
        }
        closeIfAllDecided();
    }

    private void foldSeat(int seat) {
        choices[seat] = Choice.FOLDED;
        results[seat] = PokerRound.Result.FOLD;
        thirds[seat] = 0;
        listener.decided(seat, PokerRound.Result.FOLD, 0, now);
    }

    private void closeIfAllDecided() {
        if (phase != PokerRound.Phase.DECISION) {
            return;
        }
        for (Choice c : choices) {
            if (c == Choice.OPEN) {
                return;
            }
        }
        closeDecision();
    }

    /** The window is closed: turn, river and the showdown if anybody called, else the hand ends. */
    private void closeDecision() {
        deadline = -1;
        boolean anyCalled = false;
        for (Choice c : choices) {
            anyCalled |= c == Choice.CALLED;
        }
        if (!anyCalled) {
            finish(BlackjackRound.Reaction.COLLECT);
            return;
        }
        setPhase(PokerRound.Phase.BOARD);
        dealBoard();
        after(22, this::dealBoard);
        after(49, this::showdown);
    }

    private void reveal(int index) {
        if (!revealed[index]) {
            revealed[index] = true;
            listener.holeCardRevealed(index, dealer.get(index), now);
        }
    }

    private void showdown() {
        setPhase(PokerRound.Phase.SHOWDOWN);
        reveal(1);
        after(22, () -> reveal(0));
        after(46, this::resolve);
    }

    private void resolve() {
        List<Integer> his = new ArrayList<>(dealer);
        his.addAll(board);
        dealerScore = PokerHands.score(his);
        boolean celebrate = false;
        boolean win = false;
        boolean push = false;
        setPhase(PokerRound.Phase.RESULT);
        for (int s = 0; s < seats; s++) {
            if (choices[s] != Choice.CALLED) {
                continue;
            }
            PokerCall.Outcome o = PokerCall.resolve(hands.get(s), dealer, board);
            List<Integer> mine = new ArrayList<>(hands.get(s));
            mine.addAll(board);
            scores[s] = PokerHands.score(mine);
            results[s] = o.result();
            thirds[s] = o.thirds();
            celebrate |= o.result().isWin() && PokerHands.antePays(scores[s]) >= 3;
            win |= o.result().isWin();
            push |= o.result().isPush();
            listener.decided(s, o.result(), o.thirds(), now);
        }
        finish(celebrate ? BlackjackRound.Reaction.CELEBRATE : win ? BlackjackRound.Reaction.PAY
                : push ? BlackjackRound.Reaction.PUSH : BlackjackRound.Reaction.COLLECT);
    }

    private void finish(BlackjackRound.Reaction reaction) {
        if (phase != PokerRound.Phase.RESULT) {
            setPhase(PokerRound.Phase.RESULT);
        }
        listener.reaction(reaction, now);
        after(BlackjackRound.resultHold(reaction), () -> {
            setPhase(PokerRound.Phase.CLEARING);
            listener.cleared(now);
            after(18, () -> setPhase(PokerRound.Phase.DONE));
        });
    }
}
