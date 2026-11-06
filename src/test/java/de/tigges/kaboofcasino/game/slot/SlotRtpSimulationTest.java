package de.tigges.kaboofcasino.game.slot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.game.CasinoRandom;
import org.junit.jupiter.api.Test;

/**
 * RTP simulation with the casino's own generator (seeded, deterministic): 10 million spins must land within 4 sigma
 * of the exact 499/512 (sigma per spin ~4.79 multiplier units, so 4 sigma of the mean ~0.006). Also the forced
 * chance p = 25 (RTP 0.25 x 499/140) and the hit frequency 140/512.
 */
class SlotRtpSimulationTest {

    private static final int SPINS = 10_000_000;

    private static double sigmaOfMean(double mean, double meanOfSquares, int n) {
        return Math.sqrt((meanOfSquares - mean * mean) / n);
    }

    @Test
    void naturalOddsReturn499Over512() {
        CasinoRandom random = new CasinoRandom(20261106L);
        long paid = 0;
        long hits = 0;
        long squares = 0;
        for (int i = 0; i < SPINS; i++) {
            int m = SlotGame.evaluate(SlotGame.draw(-1, random::nextInt)).multiplier();
            paid += m;
            squares += (long) m * m;
            if (m > 0) {
                hits++;
            }
        }
        double exact = 499.0 / 512.0;
        double observed = (double) paid / SPINS;
        double sigma = sigmaOfMean(exact, 12241.0 / 512.0, SPINS);
        assertEquals(12241.0 / 512.0, (double) squares / SPINS, 0.1);
        assertTrue(Math.abs(observed - exact) < 4 * sigma, "RTP " + observed + " vs " + exact + " (4 sigma = "
                + 4 * sigma + ")");
        double hitRate = (double) hits / SPINS;
        assertEquals(140.0 / 512.0, hitRate, 4 * Math.sqrt(0.2734 * 0.7266 / SPINS));
        System.out.printf("slot RTP over %,d spins: %.5f (exact %.5f), hit rate %.5f%n", SPINS, observed, exact,
                hitRate);
    }

    @Test
    void forcedChance25Returns89Percent() {
        CasinoRandom random = new CasinoRandom(42L);
        long paid = 0;
        int n = 2_000_000;
        for (int i = 0; i < n; i++) {
            paid += SlotGame.evaluate(SlotGame.draw(25, random::nextInt)).multiplier();
        }
        double exact = 0.25 * 499.0 / 140.0;
        // per-spin second moment: 0.25 x mean of m^2 over the 140 winning outcomes
        double winSquares = (12241.0) / 140.0;
        double sigma = sigmaOfMean(exact, 0.25 * winSquares, n);
        double observed = (double) paid / n;
        assertTrue(Math.abs(observed - exact) < 4 * sigma, "forced RTP " + observed + " vs " + exact);
    }
}
