package de.tigges.kaboofcasino.game.blackjack;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/**
 * The several-player round: deal order, turn order, a player leaving, and above all that the stored prediction is what
 * the live round pays if nobody takes another card, from any point of the round.
 */
class BlackjackTableRoundTest {

    private static final class Log implements BlackjackTableRound.Listener {
        final List<Integer> dealt = new ArrayList<>();
        final List<Integer> turns = new ArrayList<>();
        final List<BlackjackRound.Result> decided = new ArrayList<>();
        final List<Integer> timedOut = new ArrayList<>();

        @Override
        public void shuffled(int at) {
        }

        @Override
        public void cardDealt(int seat, int index, int card, boolean faceUp, int at) {
            dealt.add(seat);
        }

        @Override
        public void holeCardRevealed(int card, int at) {
        }

        @Override
        public void dealerPeek(boolean blackjack, int at) {
        }

        @Override
        public void turnOpened(int seat, int deadline, int at) {
            turns.add(seat);
        }

        @Override
        public void turnTimedOut(int seat, int at) {
            timedOut.add(seat);
        }

        @Override
        public void phaseChanged(BlackjackRound.Phase phase, int at) {
        }

        @Override
        public void decided(int seat, BlackjackRound.Result result, int at) {
            decided.add(result);
        }

        @Override
        public void reaction(BlackjackRound.Reaction reaction, int at) {
        }

        @Override
        public void cleared(int at) {
        }
    }

    private static BlackjackShoe shoe(long seed) {
        return new BlackjackShoe(new ShoeRandom(seed));
    }

    private static void run(BlackjackTableRound r, int ticks) {
        for (int i = 0; i < ticks && !r.isOver(); i++) {
            r.tick();
        }
    }

    /** Ticks until somebody's turn is open (or the round is over). */
    private static void toTurn(BlackjackTableRound r) {
        int guard = 0;
        while (!r.isOver() && r.phase() != BlackjackRound.Phase.PLAYER_TURN && guard++ < 4000) {
            r.tick();
        }
    }

    @Test
    void theOpeningDealGoesSeatBySeatWithTheDealerLast() {
        Log log = new Log();
        BlackjackTableRound r = new BlackjackTableRound(shoe(7), log, 3);
        run(r, 200);
        assertEquals(List.of(0, 1, 2, BlackjackTableRound.DEALER, 0, 1, 2, BlackjackTableRound.DEALER),
                log.dealt.subList(0, 8));
        for (int s = 0; s < 3; s++) {
            assertTrue(r.hand(s).size() >= 2);
        }
    }

    @Test
    void theSeatsTakeTheirTurnsInOrder() {
        for (long seed = 0; seed < 40; seed++) {
            Log log = new Log();
            BlackjackTableRound r = new BlackjackTableRound(shoe(seed), log, 4);
            while (!r.isOver()) {
                toTurn(r);
                if (r.isOver()) {
                    break;
                }
                r.stand(r.currentSeat());
            }
            List<Integer> sorted = new ArrayList<>(log.turns);
            sorted.sort(null);
            assertEquals(sorted, log.turns, "seed " + seed);
            assertEquals(4, log.decided.size(), "seed " + seed);
        }
    }

    @Test
    void onlyTheSeatWhoseTurnItIsMayAct() {
        BlackjackTableRound r = null;
        long seed = 0;
        do {
            r = new BlackjackTableRound(shoe(seed++), new Log(), 3);
            toTurn(r);
        } while (r.isOver() || r.currentSeat() != 0);
        assertTrue(r.canHit(0));
        assertFalse(r.canHit(1));
        assertFalse(r.hit(2));
        assertFalse(r.stand(1));
    }

    @Test
    void aTurnTimesOutToStand() {
        Log log = new Log();
        long seed = 0;
        BlackjackTableRound r;
        do {
            log = new Log();
            r = new BlackjackTableRound(shoe(seed++), log, 2);
            toTurn(r);
        } while (r.isOver() || r.currentSeat() != 0);
        for (int i = 0; i <= BlackjackRound.TURN_TIMEOUT; i++) {
            r.tick();
        }
        assertEquals(List.of(0), log.timedOut);
    }

    @Test
    void whoHasLeftStandsAndGetsNoTurn() {
        for (long seed = 0; seed < 30; seed++) {
            Log log = new Log();
            BlackjackTableRound r = new BlackjackTableRound(shoe(seed), log, 3);
            r.leave(1); // before the deal
            while (!r.isOver()) {
                toTurn(r);
                if (r.isOver()) {
                    break;
                }
                r.stand(r.currentSeat());
            }
            assertFalse(log.turns.contains(1), "seed " + seed);
            assertEquals(3, log.decided.size(), "seed " + seed);
        }
    }

    @Test
    void whoLeavesDuringHisTurnStandsAtOnce() {
        long seed = 0;
        BlackjackTableRound r;
        do {
            r = new BlackjackTableRound(shoe(seed++), new Log(), 2);
            toTurn(r);
        } while (r.isOver() || r.currentSeat() != 0);
        r.leave(0);
        assertFalse(r.canHit(0));
    }

    private static BlackjackRound.Result[] finished(BlackjackTableRound r, int seats) {
        BlackjackRound.Result[] out = new BlackjackRound.Result[seats];
        for (int s = 0; s < seats; s++) {
            out[s] = r.result(s);
        }
        return out;
    }

    /** From the start, and after any random hits: the prediction is exactly what the table pays if everybody stands. */
    @Test
    void thePredictionIsWhatTheTablePaysIfNobodyDraws() {
        SplittableRandom random = new SplittableRandom(11);
        for (int round = 0; round < 1500; round++) {
            int seats = 1 + random.nextInt(5);
            BlackjackShoe shoe = shoe(random.nextLong());
            for (int burn = random.nextInt(250); burn > 0; burn--) {
                shoe.draw();
            }
            BlackjackShoe sim = shoe.copy();
            if (sim.needsShuffle()) {
                sim.shuffle();
            }
            BlackjackRound.Result[] predicted = BlackjackTableRound.standResultsAtStart(seats, sim);
            BlackjackTableRound r = new BlackjackTableRound(shoe, new Log(), seats);
            int hits = random.nextInt(3);
            while (!r.isOver()) {
                toTurn(r);
                if (r.isOver()) {
                    break;
                }
                int seat = r.currentSeat();
                if (hits > 0 && random.nextBoolean()) {
                    hits--;
                    predicted = r.standResults(shoe.copy(), seat);
                    if (random.nextBoolean() && r.canDouble(seat)) {
                        r.doubleDown(seat);
                    } else {
                        r.hit(seat);
                    }
                } else {
                    r.stand(seat);
                }
            }
            assertArrayEquals(predicted, finished(r, seats), "round " + round);
        }
    }

    @Test
    void whenAllAreBustOrNaturalTheDealerDrawsNothing() {
        // every hand of this seed set is played to a bust; the dealer then keeps his two cards
        for (long seed = 0; seed < 200; seed++) {
            BlackjackTableRound r = new BlackjackTableRound(shoe(seed), new Log(), 1);
            while (!r.isOver()) {
                toTurn(r);
                if (r.isOver()) {
                    break;
                }
                r.hit(r.currentSeat());
                run(r, 28);
            }
            if (r.result(0) == BlackjackRound.Result.LOSE_BUST) {
                assertEquals(2, r.dealer().size(), "seed " + seed);
            }
        }
    }
}
