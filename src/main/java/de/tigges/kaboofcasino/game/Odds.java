package de.tigges.kaboofcasino.game;

import java.util.Arrays;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;

/**
 * The mod's forced win-chance pools ({@code CasinoOdds}, games.md 1.5): with {@code win_chance = p} the draw first
 * picks win or lose with {@code nextInt(parts) < p}, then a uniform ticket of that pool. Natural odds (p = -1) never
 * come here.
 */
public final class Odds {

    /**
     * @param wins            winning outcome tickets
     * @param losses          losing outcome tickets
     * @param winningReturn   mean multiplier of the winning tickets
     * @param losingReturn    mean multiplier of the losing tickets
     */
    public record Pool(int[] wins, int[] losses, double winningReturn, double losingReturn) {
        public Pool {
            wins = wins.clone();
            losses = losses.clone();
        }

        /** {@code CasinoOdds.expectedReturn}: RTP at chance p (0..1). */
        public double expected(double chance) {
            return chance * winningReturn + (1 - chance) * losingReturn;
        }

        @Override
        public int[] wins() {
            return wins.clone();
        }

        @Override
        public int[] losses() {
            return losses.clone();
        }
    }

    private Odds() {
    }

    /** {@code CasinoOdds.pool}: weight = tickets per outcome, quarters = multiplier x 4. */
    public static Pool pool(int outcomes, IntPredicate wins, IntUnaryOperator weight, IntUnaryOperator quarters) {
        int capacity = 0;
        for (int o = 0; o < outcomes; o++) {
            capacity = Math.addExact(capacity, weight.applyAsInt(o));
        }
        int[] winning = new int[capacity];
        int[] losing = new int[capacity];
        int won = 0;
        int lost = 0;
        long winQ = 0;
        long loseQ = 0;
        for (int o = 0; o < outcomes; o++) {
            int tickets = weight.applyAsInt(o);
            if (wins.test(o)) {
                Arrays.fill(winning, won, won + tickets, o);
                won += tickets;
                winQ += (long) tickets * quarters.applyAsInt(o);
            } else {
                Arrays.fill(losing, lost, lost + tickets, o);
                lost += tickets;
                loseQ += (long) tickets * quarters.applyAsInt(o);
            }
        }
        if (won == 0 || lost == 0) {
            throw new IllegalStateException("a game needs winning and losing outcomes");
        }
        return new Pool(Arrays.copyOf(winning, won), Arrays.copyOf(losing, lost), winQ / (4.0 * won),
                loseQ / (4.0 * lost));
    }

    /** {@code CasinoOdds.draw}. */
    public static int draw(Pool pool, int percent, int parts, IntUnaryOperator nextInt) {
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("win chance outside 0..100: " + percent);
        }
        int[] outcomes = roll(nextInt, parts) < percent ? pool.wins : pool.losses;
        return outcomes[roll(nextInt, outcomes.length)];
    }

    /**
     * {@code CasinoOdds.derby} (solo races with a forced chance only): {@code nextInt(100) < percent} lets the player's
     * {@code lane} win, otherwise {@code nextInt(3)} picks one of the other three lanes uniformly.
     */
    public static int derby(int lane, int percent, IntUnaryOperator nextInt) {
        if (lane < 0 || lane >= 4) {
            throw new IllegalArgumentException("Invalid derby lane: " + lane);
        }
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("percent outside 0..100: " + percent);
        }
        if (roll(nextInt, 100) < percent) {
            return lane;
        }
        int other = roll(nextInt, 3);
        return other < lane ? other : other + 1;
    }

    private static int roll(IntUnaryOperator nextInt, int bound) {
        int r = nextInt.applyAsInt(bound);
        if (r < 0 || r >= bound) {
            throw new IllegalArgumentException("random result outside 0.." + (bound - 1));
        }
        return r;
    }
}
