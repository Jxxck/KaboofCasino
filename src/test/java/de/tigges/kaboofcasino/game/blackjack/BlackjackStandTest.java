package de.tigges.kaboofcasino.game.blackjack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/**
 * {@link BlackjackStand} predicts what the live {@link BlackjackRound} pays when the player stands, from any moment
 * of the hand, with and without bias: the record payout of a PLAYING hand (quit, stop, crash, death) is exactly what
 * the table then shows. Random hands, random decisions at random ticks, random shoe depth (incl. the reshuffle).
 */
class BlackjackStandTest {

    private static final BlackjackRound.Listener QUIET = new BlackjackRound.Listener() {
        @Override
        public void cardDealt(boolean toDealer, int index, int card, boolean faceUp, int at) {
        }

        @Override
        public void holeCardRevealed(int card, int at) {
        }

        @Override
        public void turnOpened(int deadline, int at) {
        }

        @Override
        public void phaseChanged(BlackjackRound.Phase phase, int at) {
        }

        @Override
        public void decided(BlackjackRound.Result result, int at) {
        }

        @Override
        public void reaction(BlackjackRound.Reaction reaction, int at) {
        }

        @Override
        public void cleared(int at) {
        }
    };

    private static final int[] BIASES = {0, 0, 0, 15, -30, 60, 100, -100};

    @Test
    void thePredictionIsWhatTheTablePays() {
        SplittableRandom pick = new SplittableRandom(20261106L);
        int hands = 60_000;
        int[] actions = new int[4];
        int[] results = new int[9];
        for (int hand = 0; hand < hands; hand++) {
            int bias = BIASES[hand % BIASES.length];
            BlackjackShoe shoe = new BlackjackShoe(new ShoeRandom(pick.nextLong()));
            int burn = pick.nextInt(300);
            for (int i = 0; i < burn; i++) {
                shoe.draw();
            }
            BlackjackShoe sim = shoe.copy();
            if (sim.needsShuffle()) {
                sim.shuffle();
            }
            BlackjackRound.Result predicted = BlackjackStand.stand(List.of(), List.of(), false, sim, bias);
            BlackjackRound round = new BlackjackRound(shoe, QUIET, bias);
            boolean stood = false;
            int guard = 0;
            while (!round.isOver()) {
                if (++guard > 20_000) {
                    throw new AssertionError("hand " + hand + " does not end");
                }
                // at any moment before the decision, standing now pays the current prediction
                if (round.result() == BlackjackRound.Result.NONE && pick.nextInt(7) == 0) {
                    BlackjackRound.Result now = BlackjackStand.stand(round.player(), round.dealer(), round.doubled(),
                            shoe.copy(), bias);
                    assertEquals(predicted, now, "hand " + hand + " at tick " + round.now() + " phase "
                            + round.phase());
                }
                if (round.phase() == BlackjackRound.Phase.PLAYER_TURN && !stood && pick.nextInt(3) == 0) {
                    int choice = pick.nextInt(10);
                    if (choice < 3) {
                        predicted = BlackjackStand.hitThenStand(round.player(), round.dealer(), round.doubled(),
                                shoe.copy(), bias);
                        assertTrue(round.hit());
                        actions[0]++;
                    } else if (choice < 5 && round.canDouble()) {
                        predicted = BlackjackStand.doubleDown(round.player(), round.dealer(), shoe.copy(), bias);
                        assertTrue(round.doubleDown());
                        actions[1]++;
                    } else if (choice < 9) {
                        assertTrue(round.stand());
                        stood = true;
                        actions[2]++;
                    } else {
                        // the owner left: the controller stands at the next chance
                        assertTrue(round.stand());
                        stood = true;
                        actions[3]++;
                    }
                }
                round.tick();
            }
            assertEquals(predicted, round.result(), "hand " + hand + " bias " + bias);
            results[round.result().code]++;
        }
        // every branch was exercised
        for (int a : actions) {
            assertTrue(a > 1000, java.util.Arrays.toString(actions));
        }
        for (int code = 1; code <= 8; code++) {
            assertTrue(results[code] > 40, "result " + code + ": " + java.util.Arrays.toString(results));
        }
    }

    @Test
    void timeoutStandsLikeTheMod() {
        // nobody acts: the 600-tick timer stands (BlackjackRound 171-174); the prediction made at the deal holds
        SplittableRandom pick = new SplittableRandom(7);
        for (int hand = 0; hand < 2_000; hand++) {
            BlackjackShoe shoe = new BlackjackShoe(new ShoeRandom(pick.nextLong()));
            BlackjackShoe sim = shoe.copy();
            BlackjackRound.Result predicted = BlackjackStand.stand(List.of(), List.of(), false, sim, 0);
            BlackjackRound round = new BlackjackRound(shoe, QUIET, 0);
            while (!round.isOver()) {
                round.tick();
            }
            assertEquals(predicted, round.result());
        }
    }
}
