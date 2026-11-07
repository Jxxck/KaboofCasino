package de.tigges.kaboofcasino.game.poker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.game.blackjack.BlackjackShoe;
import de.tigges.kaboofcasino.game.blackjack.ShoeRandom;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/**
 * {@link PokerCall} predicts what the live {@link PokerRound} decides when the player calls, with and without bias:
 * the records written at the CALL (quit, stop, crash, death after it) hold exactly what the table then shows. Before
 * the call a hand settles as a fold, which needs no prediction.
 */
class PokerCallTest {

    private static final PokerRound.Listener QUIET = new PokerRound.Listener() {
        @Override
        public void cardDealt(boolean toDealer, int index, int card, boolean faceUp, int at) {
        }

        @Override
        public void holeCardRevealed(int index, int card, int at) {
        }

        @Override
        public void turnOpened(int deadline, int at) {
        }

        @Override
        public void phaseChanged(PokerRound.Phase phase, int at) {
        }

        @Override
        public void decided(PokerRound.Result result, int at) {
        }

        @Override
        public void reaction(de.tigges.kaboofcasino.game.blackjack.BlackjackRound.Reaction reaction, int at) {
        }

        @Override
        public void cleared(int at) {
        }
    };

    private static final int[] BIASES = {0, 0, 0, 15, -30, 60, 100, -100};

    @Test
    void thePredictionIsWhatTheTableDecides() {
        SplittableRandom pick = new SplittableRandom(20261107L);
        int hands = 40_000;
        int[] results = new int[6];
        // one shoe per table, as in the controller: the reshuffle happens in every round's constructor
        BlackjackShoe shoe = new BlackjackShoe(new ShoeRandom(pick.nextLong()), 1);
        for (int hand = 0; hand < hands; hand++) {
            int bias = BIASES[hand % BIASES.length];
            PokerRound round = new PokerRound(shoe, QUIET, bias);
            PokerCall.Outcome predicted = null;
            int guard = 0;
            while (!round.isOver()) {
                if (++guard > 5_000) {
                    throw new AssertionError("hand " + hand + " does not end");
                }
                round.tick();
                if (round.canCall() && predicted == null) {
                    // wait a random part of the 30 s decision; the prediction does not depend on when
                    for (int w = pick.nextInt(500); w > 0 && round.canCall(); w--) {
                        round.tick();
                    }
                    if (!round.canCall()) {
                        break;
                    }
                    PokerCall.Outcome first = PokerCall.call(round.player(), round.dealer(), round.board(),
                            shoe.copy(), bias);
                    predicted = PokerCall.call(round.player(), round.dealer(), round.board(), shoe.copy(), bias);
                    assertEquals(first, predicted, "the prediction does not consume the live shoe");
                    assertTrue(round.call());
                }
            }
            if (predicted == null) {
                assertEquals(PokerRound.Result.FOLD, round.result(), "timed out: a fold");
                continue;
            }
            assertEquals(predicted.result(), round.result(), "hand " + hand + " bias " + bias);
            assertEquals(predicted.thirds(), round.payoutThirds(), "hand " + hand + " bias " + bias);
            assertEquals(5, round.board().size());
            results[round.result().code]++;
        }
        // every called result occurs
        for (PokerRound.Result r : List.of(PokerRound.Result.WIN, PokerRound.Result.NO_QUALIFY,
                PokerRound.Result.PUSH, PokerRound.Result.LOSE)) {
            assertTrue(results[r.code] > 0, r + " never happened");
        }
    }

    @Test
    void theTimeoutFolds() {
        BlackjackShoe shoe = new BlackjackShoe(new ShoeRandom(7L), 1);
        PokerRound round = new PokerRound(shoe, QUIET, 0);
        int decisionAt = -1;
        while (!round.isOver()) {
            round.tick();
            if (decisionAt < 0 && round.canCall()) {
                decisionAt = round.now();
            }
        }
        assertEquals(209, decisionAt, "games.md 4: DECISION at t = 209 (36 shuffle + 14 + 6 x 22 + 27)");
        assertEquals(PokerRound.Result.FOLD, round.result());
        assertEquals(0, round.payoutThirds());
        assertEquals(List.of(-1, -1), round.dealerVisible(), "a fold never shows the dealer's cards");
    }

    @Test
    void resolveMatchesTheRound() {
        assertEquals(14, PokerHands.value(card(14, 2)));
        assertEquals(4, PokerHands.value(card(4, 1)));
        assertEquals(13, PokerHands.value(card(13, 3)));
        // a pair of 4s qualifies, a pair of 3s does not (games.md 4)
        List<Integer> board = List.of(card(14, 0), card(13, 1), card(9, 2), card(7, 3), card(2, 0));
        PokerCall.Outcome fours = PokerCall.resolve(List.of(card(12, 0), card(12, 1)), List.of(card(4, 0),
                card(4, 1)), board);
        assertEquals(PokerRound.Result.WIN, fours.result());
        assertEquals(6, fours.thirds(), "pair of queens beats pair of 4s: ante 1:1 + call 1:1");
        PokerCall.Outcome threes = PokerCall.resolve(List.of(card(12, 0), card(12, 1)), List.of(card(3, 0),
                card(3, 1)), board);
        assertEquals(PokerRound.Result.NO_QUALIFY, threes.result());
        assertEquals(4, threes.thirds(), "ante 1:1, call pushes");
    }

    /** Card id of rank (2..14, ace 14) and suit (0..3): {@code BlackjackCards} rank index 0 = ace, 1 = two. */
    static int card(int rank, int suit) {
        return suit * 13 + (rank == 14 ? 0 : rank - 1);
    }
}
