package de.tigges.kaboofcasino.game;

import java.util.function.IntUnaryOperator;

/** Exact expectations of {@link Odds#draw} by enumerating every pair of random numbers it consumes. */
public final class OddsTestSupport {

    private OddsTestSupport() {
    }

    /**
     * The exact mean of {@code value(outcome)} over all draws at chance {@code percent}: every first number
     * {@code a in 0..99} and every second number {@code b in 0..len-1} of the chosen bucket, equally likely.
     */
    public static double forcedMean(Odds.Pool pool, int percent, java.util.function.IntToDoubleFunction value) {
        double sum = 0;
        for (int a = 0; a < 100; a++) {
            int len = a < percent ? pool.wins().length : pool.losses().length;
            double bucket = 0;
            for (int b = 0; b < len; b++) {
                int[] script = {a, b};
                int[] i = {0};
                IntUnaryOperator next = bound -> script[i[0]++];
                bucket += value.applyAsDouble(Odds.draw(pool, percent, 100, next));
            }
            sum += bucket / len;
        }
        return sum / 100;
    }
}
