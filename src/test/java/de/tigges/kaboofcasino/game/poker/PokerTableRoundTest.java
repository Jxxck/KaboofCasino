package de.tigges.kaboofcasino.game.poker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.game.blackjack.BlackjackRound;
import de.tigges.kaboofcasino.game.blackjack.BlackjackShoe;
import de.tigges.kaboofcasino.game.blackjack.ShoeRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/**
 * The several-player hand: deal order, one decision window for all that closes early, folds and timeouts, a player
 * leaving, and above all that the outcome predicted at a seat's call (on a copy of the shoe) is what the live hand
 * pays, whoever else calls or folds and in whatever order.
 */
class PokerTableRoundTest {

    private static final class Log implements PokerTableRound.Listener {
        final List<Integer> dealt = new ArrayList<>();
        final List<Integer> timedOut = new ArrayList<>();
        final List<String> decided = new ArrayList<>();
        final List<BlackjackRound.Reaction> reactions = new ArrayList<>();
        int opened;
        int revealed;

        @Override
        public void shuffled(int at) {
        }

        @Override
        public void cardDealt(int seat, int index, int card, boolean faceUp, int at) {
            dealt.add(seat);
            assertEquals(!(seat == PokerTableRound.DEALER && index < 2), faceUp,
                    "only the dealer's hole cards are face down");
        }

        @Override
        public void holeCardRevealed(int index, int card, int at) {
            revealed++;
        }

        @Override
        public void decisionOpened(int deadline, int at) {
            opened++;
            assertEquals(at + PokerRound.TURN_TIMEOUT, deadline);
        }

        @Override
        public void decisionTimedOut(int seat, int at) {
            timedOut.add(seat);
        }

        @Override
        public void phaseChanged(PokerRound.Phase phase, int at) {
        }

        @Override
        public void decided(int seat, PokerRound.Result result, int thirds, int at) {
            decided.add(seat + ":" + result);
        }

        @Override
        public void reaction(BlackjackRound.Reaction reaction, int at) {
            reactions.add(reaction);
        }

        @Override
        public void cleared(int at) {
        }
    }

    private static BlackjackShoe shoe(long seed) {
        return new BlackjackShoe(new ShoeRandom(seed), 1);
    }

    private static void run(PokerTableRound r, int ticks) {
        for (int i = 0; i < ticks && !r.isOver(); i++) {
            r.tick();
        }
    }

    private static void toDecision(PokerTableRound r) {
        for (int i = 0; i < 2_000 && r.phase() != PokerRound.Phase.DECISION && !r.isOver(); i++) {
            r.tick();
        }
    }

    @Test
    void dealsSeatBySeatWithTheDealerLastThenTheFlop() {
        Log log = new Log();
        PokerTableRound r = new PokerTableRound(shoe(1), log, 3);
        toDecision(r);
        assertEquals(PokerRound.Phase.DECISION, r.phase());
        assertEquals(List.of(0, 1, 2, -1, 0, 1, 2, -1, -1, -1, -1), log.dealt);
        assertEquals(3, r.board().size());
        for (int s = 0; s < 3; s++) {
            assertEquals(2, r.hand(s).size());
            assertTrue(r.canCall(s));
        }
        assertEquals(List.of(-1, -1), r.dealerVisible(), "the dealer's cards stay hidden until the showdown");
        assertEquals(1, log.opened);
    }

    @Test
    void theWindowClosesOnceEverybodyDecidedAndNobodyCalledEndsTheHand() {
        Log log = new Log();
        PokerTableRound r = new PokerTableRound(shoe(2), log, 2);
        toDecision(r);
        assertTrue(r.fold(0));
        assertFalse(r.fold(0), "one decision per seat");
        assertEquals(PokerRound.Phase.DECISION, r.phase(), "seat 1 still decides");
        assertTrue(r.fold(1));
        assertEquals(PokerRound.Phase.RESULT, r.phase());
        assertEquals(3, r.board().size(), "nobody called: no turn, no river");
        assertEquals(List.of(BlackjackRound.Reaction.COLLECT), log.reactions);
        run(r, 1_000);
        assertTrue(r.isOver());
        assertEquals(0, log.revealed);
        assertEquals(List.of("0:FOLD", "1:FOLD"), log.decided);
    }

    @Test
    void anUndecidedSeatFoldsWhenTheWindowRunsOut() {
        Log log = new Log();
        PokerTableRound r = new PokerTableRound(shoe(3), log, 3);
        toDecision(r);
        assertTrue(r.call(1));
        int opened = r.now();
        run(r, PokerRound.TURN_TIMEOUT - 1);
        assertEquals(PokerRound.Phase.DECISION, r.phase());
        run(r, 1);
        assertEquals(opened + PokerRound.TURN_TIMEOUT, r.now());
        assertEquals(List.of(0, 2), log.timedOut);
        assertEquals(PokerRound.Phase.BOARD, r.phase());
        run(r, 2_000);
        assertTrue(r.isOver());
        assertEquals(5, r.board().size());
        assertEquals(PokerRound.Result.FOLD, r.result(0));
        assertEquals(PokerRound.Result.FOLD, r.result(2));
        assertTrue(r.result(1) != PokerRound.Result.NONE && r.result(1) != PokerRound.Result.FOLD);
        assertEquals(2, log.revealed);
    }

    @Test
    void aPlayerWhoLeavesFoldsAndTheOthersPlayOn() {
        Log log = new Log();
        PokerTableRound r = new PokerTableRound(shoe(4), log, 3);
        run(r, 30);
        r.leave(2);
        toDecision(r);
        assertEquals(PokerRound.Result.FOLD, r.result(2), "left during the deal: folds as the window opens");
        assertFalse(r.canCall(2));
        assertTrue(r.call(0));
        r.leave(1);
        assertEquals(PokerRound.Phase.BOARD, r.phase(), "the last open seat left: the window closes");
        run(r, 2_000);
        assertEquals(PokerRound.Result.FOLD, r.result(1));
        assertTrue(r.result(0).isWin() || r.result(0).isPush() || r.result(0) == PokerRound.Result.LOSE);
    }

    @Test
    void aCalledSeatThatLeavesKeepsItsOutcome() {
        Log log = new Log();
        PokerTableRound r = new PokerTableRound(shoe(5), log, 2);
        toDecision(r);
        assertTrue(r.call(0));
        r.leave(0);
        assertTrue(r.call(1));
        run(r, 2_000);
        assertTrue(r.isOver());
        assertTrue(r.result(0) != PokerRound.Result.FOLD, "the call stands: its outcome was settled at the quit");
    }

    /** The core: what a seat's records hold at its call is exactly what the live hand decides, over many hands. */
    @Test
    void thePredictionAtEveryCallIsWhatTheTableDecides() {
        SplittableRandom choices = new SplittableRandom(42);
        int checked = 0;
        for (int hand = 0; hand < 3_000; hand++) {
            int seats = 1 + hand % 3;
            BlackjackShoe shoe = shoe(1_000 + hand);
            Log log = new Log();
            PokerTableRound r = new PokerTableRound(shoe, log, seats);
            toDecision(r);
            PokerCall.Outcome[] predicted = new PokerCall.Outcome[seats];
            // the seats decide in a random order, some fold, some never decide
            List<Integer> order = new ArrayList<>();
            for (int s = 0; s < seats; s++) {
                order.add(s);
            }
            java.util.Collections.shuffle(order, new java.util.Random(choices.nextLong()));
            for (int s : order) {
                int what = choices.nextInt(4);
                if (what <= 1) {
                    predicted[s] = PokerCall.call(r.hand(s), r.dealer(), r.board(), shoe.copy(), 0);
                    assertTrue(r.call(s));
                } else if (what == 2) {
                    assertTrue(r.fold(s));
                }
                run(r, choices.nextInt(40));
            }
            run(r, 3_000);
            assertTrue(r.isOver());
            for (int s = 0; s < seats; s++) {
                if (predicted[s] != null) {
                    assertEquals(predicted[s].result(), r.result(s), "hand " + hand + " seat " + s);
                    assertEquals(predicted[s].thirds(), r.payoutThirds(s), "hand " + hand + " seat " + s);
                    checked++;
                } else {
                    assertEquals(PokerRound.Result.FOLD, r.result(s));
                    assertEquals(0, r.payoutThirds(s));
                }
            }
        }
        assertTrue(checked > 2_000, "calls checked: " + checked);
    }
}
