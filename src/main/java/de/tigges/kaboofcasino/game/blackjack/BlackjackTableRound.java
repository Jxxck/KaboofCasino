package de.tigges.kaboofcasino.game.blackjack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One round at a table with several players (PLAN-2 C5, not in MineVegas, which deals to one player). {@link
 * BlackjackRound} with its timing and rules, widened to seats: the opening deal goes seat by seat with the dealer last
 * in each pass, then the seats take their turns in order (a natural or a player who has left has none), the dealer
 * plays once for all if at least one hand can still be beaten, and every seat is resolved against that one dealer.
 * Bias is the single player's setting and does not exist here. {@link #standResults} predicts what every seat gets if
 * nobody takes another card, on a copy of the shoe. Pure.
 */
public final class BlackjackTableRound {

    /** The seat number the listener gets for the dealer's own cards. */
    public static final int DEALER = -1;
    /** Ticks between two opening cards with more than two seats (the mod's 22 would take 13 seconds at five). */
    static final int CROWDED_DEAL = 16;

    /** What the table has to show and pay. */
    public interface Listener {
        void shuffled(int at);

        void cardDealt(int seat, int index, int card, boolean faceUp, int at);

        void holeCardRevealed(int card, int at);

        void dealerPeek(boolean blackjack, int at);

        void turnOpened(int seat, int deadline, int at);

        void turnTimedOut(int seat, int at);

        void phaseChanged(BlackjackRound.Phase phase, int at);

        void decided(int seat, BlackjackRound.Result result, int at);

        void reaction(BlackjackRound.Reaction reaction, int at);

        void cleared(int at);
    }

    private final BlackjackShoe shoe;
    private final Listener listener;
    private final int seats;
    private final List<List<Integer>> hands = new ArrayList<>();
    private final List<Integer> dealer = new ArrayList<>();
    private final boolean[] doubled;
    private final boolean[] bust;
    private final boolean[] gone;
    private final BlackjackRound.Result[] results;
    private final List<Step> steps = new ArrayList<>();
    private int sequence;
    private int now;
    private int deadline;
    private int current = -1;
    private boolean holeRevealed;
    private BlackjackRound.Phase phase = BlackjackRound.Phase.DEALING;

    private record Step(int at, int order, Runnable run) {
    }

    public BlackjackTableRound(BlackjackShoe shoe, Listener listener, int seats) {
        this.shoe = shoe;
        this.listener = listener;
        this.seats = seats;
        this.doubled = new boolean[seats];
        this.bust = new boolean[seats];
        this.gone = new boolean[seats];
        this.results = new BlackjackRound.Result[seats];
        for (int s = 0; s < seats; s++) {
            hands.add(new ArrayList<>());
            results[s] = BlackjackRound.Result.NONE;
        }
        int t = 0;
        if (shoe.needsShuffle()) {
            shoe.shuffle();
            listener.shuffled(0);
            t += BlackjackRound.SHUFFLE;
        }
        t += BlackjackRound.OPENING;
        int step = seats > 2 ? CROWDED_DEAL : BlackjackRound.DEAL_ACTION;
        for (int pass = 0; pass < 2; pass++) {
            for (int s = 0; s < seats; s++) {
                int seat = s;
                at(t, () -> deal(seat, true));
                t += step;
            }
            boolean faceUp = pass == 0;
            at(t, () -> deal(DEALER, faceUp));
            if (pass == 0) {
                t += step;
            }
        }
        at(t + BlackjackRound.LANDING + 4, this::checkNaturals);
    }

    public BlackjackRound.Phase phase() {
        return phase;
    }

    public BlackjackRound.Result result(int seat) {
        return results[seat];
    }

    public int now() {
        return now;
    }

    public int deadline() {
        return deadline;
    }

    public boolean doubled(int seat) {
        return doubled[seat];
    }

    public boolean isOver() {
        return phase == BlackjackRound.Phase.DONE;
    }

    public List<Integer> hand(int seat) {
        return Collections.unmodifiableList(hands.get(seat));
    }

    public List<Integer> dealer() {
        return Collections.unmodifiableList(dealer);
    }

    public List<Integer> dealerVisible() {
        List<Integer> out = new ArrayList<>(dealer);
        if (!holeRevealed && out.size() >= 2) {
            out.set(1, -1);
        }
        return out;
    }

    /** The seat whose turn or card it is, -1 outside the players' part. */
    public int currentSeat() {
        return (phase == BlackjackRound.Phase.PLAYER_TURN || phase == BlackjackRound.Phase.PLAYER_DRAW) ? current : -1;
    }

    public boolean canHit(int seat) {
        return phase == BlackjackRound.Phase.PLAYER_TURN && current == seat;
    }

    public boolean canStand(int seat) {
        return canHit(seat);
    }

    public boolean canDouble(int seat) {
        return canHit(seat) && hands.get(seat).size() == 2 && !doubled[seat];
    }

    public boolean hit(int seat) {
        if (!canHit(seat)) {
            return false;
        }
        drawForSeat(seat);
        return true;
    }

    public boolean stand(int seat) {
        if (!canStand(seat)) {
            return false;
        }
        nextTurn();
        return true;
    }

    public boolean doubleDown(int seat) {
        if (!canDouble(seat)) {
            return false;
        }
        doubled[seat] = true;
        drawForSeat(seat);
        return true;
    }

    /** The player has left: his hand stands now or when its turn would come. */
    public void leave(int seat) {
        gone[seat] = true;
        stand(seat);
    }

    public void abort() {
        steps.clear();
        phase = BlackjackRound.Phase.DONE;
    }

    public void tick() {
        if (phase == BlackjackRound.Phase.DONE) {
            return;
        }
        now++;
        if (phase == BlackjackRound.Phase.PLAYER_TURN && now >= deadline) {
            listener.turnTimedOut(current, now);
            stand(current);
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
        } while (phase != BlackjackRound.Phase.DONE);
    }

    private void at(int time, Runnable run) {
        steps.add(new Step(time, sequence++, run));
    }

    private void after(int delay, Runnable run) {
        at(now + delay, run);
    }

    private void setPhase(BlackjackRound.Phase next) {
        phase = next;
        listener.phaseChanged(next, now);
    }

    private void deal(int seat, boolean faceUp) {
        int card = shoe.draw();
        List<Integer> hand = seat == DEALER ? dealer : hands.get(seat);
        hand.add(card);
        listener.cardDealt(seat, hand.size() - 1, faceUp ? card : -1, faceUp, now);
    }

    private void checkNaturals() {
        boolean dealerNatural = BlackjackCards.isBlackjack(dealer);
        int up = dealer.get(0);
        if (BlackjackCards.isAce(up) || BlackjackCards.isTenValue(up)) {
            listener.dealerPeek(dealerNatural, now);
            after(BlackjackRound.PEEK, () -> {
                if (dealerNatural) {
                    revealThenResolve();
                } else {
                    nextTurn();
                }
            });
        } else {
            nextTurn();
        }
    }

    /** The next seat with a decision to make, or the dealer. */
    private void nextTurn() {
        for (int s = current + 1; s < seats; s++) {
            if (!gone[s] && !BlackjackCards.isBlackjack(hands.get(s))) {
                current = s;
                openTurn();
                return;
            }
        }
        current = seats;
        finishPlayers();
    }

    private void openTurn() {
        setPhase(BlackjackRound.Phase.PLAYER_TURN);
        deadline = now + BlackjackRound.TURN_TIMEOUT;
        listener.turnOpened(current, deadline, now);
    }

    private void drawForSeat(int seat) {
        setPhase(BlackjackRound.Phase.PLAYER_DRAW);
        deal(seat, true);
        after(27, () -> {
            int total = BlackjackCards.total(hands.get(seat));
            if (total > 21) {
                bust[seat] = true;
                nextTurn();
            } else if (total == 21 || doubled[seat] || gone[seat]) {
                nextTurn();
            } else {
                openTurn();
            }
        });
    }

    private void finishPlayers() {
        boolean live = false;
        for (int s = 0; s < seats; s++) {
            live |= !bust[s] && !BlackjackCards.isBlackjack(hands.get(s));
        }
        if (live) {
            setPhase(BlackjackRound.Phase.DEALER_TURN);
            reveal();
            after(24, this::dealerPlay);
        } else {
            revealThenResolve();
        }
    }

    private void reveal() {
        if (!holeRevealed) {
            holeRevealed = true;
            listener.holeCardRevealed(dealer.get(1), now);
        }
    }

    private void revealThenResolve() {
        setPhase(BlackjackRound.Phase.DEALER_TURN);
        reveal();
        after(24, this::resolve);
    }

    private void dealerPlay() {
        if (BlackjackCards.total(dealer) < 17) {
            deal(DEALER, true);
            after(29, this::dealerPlay);
        } else {
            resolve();
        }
    }

    private void resolve() {
        boolean anyNatural = false;
        boolean anyWin = false;
        boolean anyPush = false;
        setPhase(BlackjackRound.Phase.RESULT);
        for (int s = 0; s < seats; s++) {
            results[s] = result(hands.get(s), dealer, bust[s]);
            anyNatural |= results[s] == BlackjackRound.Result.BLACKJACK;
            anyWin |= results[s].isWin();
            anyPush |= results[s].isPush();
            listener.decided(s, results[s], now);
        }
        BlackjackRound.Reaction reaction = anyNatural ? BlackjackRound.Reaction.CELEBRATE
                : anyWin ? BlackjackRound.Reaction.PAY : anyPush ? BlackjackRound.Reaction.PUSH
                : BlackjackRound.Reaction.COLLECT;
        listener.reaction(reaction, now);
        after(BlackjackRound.resultHold(reaction), () -> {
            setPhase(BlackjackRound.Phase.CLEARING);
            listener.cleared(now);
            after(BlackjackRound.CLEAR, () -> setPhase(BlackjackRound.Phase.DONE));
        });
    }

    /** {@code BlackjackRound.resolve}, same order. */
    static BlackjackRound.Result result(List<Integer> player, List<Integer> dealer, boolean bust) {
        boolean playerNatural = BlackjackCards.isBlackjack(player);
        boolean dealerNatural = BlackjackCards.isBlackjack(dealer);
        int p = BlackjackCards.total(player);
        int d = BlackjackCards.total(dealer);
        if (bust) {
            return BlackjackRound.Result.LOSE_BUST;
        } else if (playerNatural && dealerNatural) {
            return BlackjackRound.Result.PUSH_BLACKJACKS;
        } else if (dealerNatural) {
            return BlackjackRound.Result.LOSE_DEALER_BLACKJACK;
        } else if (playerNatural) {
            return BlackjackRound.Result.BLACKJACK;
        } else if (d > 21) {
            return BlackjackRound.Result.WIN_DEALER_BUST;
        } else if (p <= d && (p != 21 || d != 21)) {
            return p < d ? BlackjackRound.Result.LOSE : BlackjackRound.Result.PUSH;
        }
        return BlackjackRound.Result.WIN;
    }

    /**
     * What every seat gets if {@code hitSeat} (or nobody, -1) takes one more card and then nobody takes another: the
     * rest of the opening deal in its order, that card, the dealer's cards as the live round would draw them. Run it on
     * a {@link BlackjackShoe#copy()}; it consumes cards.
     */
    public BlackjackRound.Result[] standResults(BlackjackShoe copy, int hitSeat) {
        return predict(hands, dealer, hitSeat, copy);
    }

    /** {@link #standResults} before any card is dealt. */
    public static BlackjackRound.Result[] standResultsAtStart(int seats, BlackjackShoe copy) {
        List<List<Integer>> empty = new ArrayList<>();
        for (int s = 0; s < seats; s++) {
            empty.add(List.of());
        }
        return predict(empty, List.of(), -1, copy);
    }

    private static BlackjackRound.Result[] predict(List<List<Integer>> hands, List<Integer> dealer, int hitSeat,
                                                   BlackjackShoe shoe) {
        List<List<Integer>> sim = new ArrayList<>();
        hands.forEach(h -> sim.add(new ArrayList<>(h)));
        List<Integer> house = new ArrayList<>(dealer);
        for (int pass = 1; pass <= 2; pass++) {
            for (List<Integer> h : sim) {
                if (h.size() < pass) {
                    h.add(shoe.draw());
                }
            }
            if (house.size() < pass) {
                house.add(shoe.draw());
            }
        }
        if (hitSeat >= 0) {
            sim.get(hitSeat).add(shoe.draw());
        }
        boolean live = false;
        for (List<Integer> h : sim) {
            live |= BlackjackCards.total(h) <= 21 && !BlackjackCards.isBlackjack(h);
        }
        if (live && !BlackjackCards.isBlackjack(house)) {
            while (BlackjackCards.total(house) < 17) {
                house.add(shoe.draw());
            }
        }
        BlackjackRound.Result[] out = new BlackjackRound.Result[sim.size()];
        for (int s = 0; s < out.length; s++) {
            out[s] = result(sim.get(s), house, BlackjackCards.total(sim.get(s)) > 21);
        }
        return out;
    }
}
