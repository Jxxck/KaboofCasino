package de.tigges.kaboofcasino.game.roulette;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.game.CasinoRandom;
import de.tigges.kaboofcasino.game.OddsTestSupport;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Roulette odds (games.md 5 "Return to player"): exact enumeration, the forced pools, the spin plan, simulations. */
class RouletteOddsTest {

    @Test
    void everyColourReturns36Of37() {
        int[] pockets = new int[3];
        for (int p = 0; p < 37; p++) {
            pockets[RouletteGame.colour(p)]++;
        }
        assertEquals(18, pockets[RouletteGame.RED]);
        assertEquals(18, pockets[RouletteGame.BLACK]);
        assertEquals(1, pockets[RouletteGame.GREEN]);
        for (int c = 0; c < 3; c++) {
            for (long stake : de.tigges.kaboofcasino.game.wheel.WheelOddsTest.stakes()) {
                long paid = 0;
                for (int p = 0; p < 37; p++) {
                    paid += RouletteGame.payout(c, stake, p);
                }
                assertEquals(36 * stake, paid, "colour " + c + " stake " + stake + ": RTP 36/37");
            }
        }
    }

    @Test
    void forcedPoolsGive2pOver100OnEveryColour() {
        for (int c = 0; c < 3; c++) {
            int colour = c;
            assertEquals(c == RouletteGame.GREEN ? 1 : 18, RouletteGame.pool(c).wins().length);
            for (int p : List.of(0, 25, 48, 50, 100)) {
                double rtp = OddsTestSupport.forcedMean(RouletteGame.pool(c), p, RouletteGame.parts(c),
                        pocket -> RouletteGame.payout(colour, 1, pocket));
                assertEquals(2.0 * p / 100.0, rtp, 1e-12, "colour " + c + " chance " + p);
            }
        }
    }

    @Test
    void thePlanEndsOnTheDrawnPocketAfterAtLeastOneTurn() {
        for (int initial = 0; initial < 37; initial++) {
            for (int roll = 0; roll < 37; roll++) {
                RouletteSpin.Plan plan = RouletteSpin.plan(initial, roll);
                assertTrue(plan.steps() >= 37 && plan.steps() <= 73);
                assertEquals(roll, RouletteSpin.pocket(plan.finalFrame()));
                assertEquals(initial + plan.steps(), plan.phase(plan.resultTick()), 1e-9);
            }
        }
    }

    private static double simulate(int colour, int winChance, long seed) {
        CasinoRandom random = new CasinoRandom(seed);
        long staked = 0;
        long paid = 0;
        for (int i = 0; i < 10_000_000; i++) {
            int pocket = RouletteGame.draw(colour, winChance, random::nextInt);
            int stake = 1 + random.nextInt(64);
            staked += stake;
            paid += RouletteGame.payout(colour, stake, pocket);
        }
        return (double) paid / staked;
    }

    @Test
    void simulationsMatchTheExactReturn() {
        assertEquals(36.0 / 37.0, simulate(RouletteGame.RED, -1, 1L), 0.003);
        assertEquals(36.0 / 37.0, simulate(RouletteGame.BLACK, -1, 2L), 0.003);
        // green: payout 36 with P 1/37, standard deviation about 5.8 per item: wider tolerance
        assertEquals(36.0 / 37.0, simulate(RouletteGame.GREEN, -1, 3L), 0.012);
        assertEquals(0.96, simulate(RouletteGame.RED, 48, 4L), 0.003);
        assertEquals(0.96, simulate(RouletteGame.GREEN, 48, 5L), 0.012);
    }
}
