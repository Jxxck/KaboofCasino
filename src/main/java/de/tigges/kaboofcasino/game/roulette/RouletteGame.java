package de.tigges.kaboofcasino.game.roulette;

import de.tigges.kaboofcasino.game.Odds;
import java.util.function.IntUnaryOperator;

/**
 * The roulette rules (games.md 5; {@code RouletteBlockEntity.start}, {@code CasinoOdds.ROULETTE}): colour-only bets on
 * RED (column 0), BLACK (1) or GREEN (2); 37 pockets, 0 green, odd red, even black (parity, not a real wheel);
 * winning stakes pay 2x (red, black) or 36x (green) of the same item; RTP 36/37 on every colour. Pure.
 */
public final class RouletteGame {

    public static final int RED = 0;
    public static final int BLACK = 1;
    public static final int GREEN = 2;
    public static final int COLOURS = 3;
    public static final int POCKETS = RouletteSpin.POCKETS;

    /** {@code CasinoOdds.ROULETTE[c]}: winning pockets = colour c, losing = the others, one ticket each. */
    private static final Odds.Pool[] POOLS = new Odds.Pool[COLOURS];

    static {
        for (int c = 0; c < COLOURS; c++) {
            int colour = c;
            POOLS[c] = Odds.pool(POCKETS, p -> colour(p) == colour, p -> 1,
                    p -> colour(p) == colour ? multiplier(colour) * 4 : 0);
        }
    }

    private RouletteGame() {
    }

    public static int colour(int pocket) {
        return RouletteSpin.color(pocket);
    }

    /** What a winning stake returns per item: green 36, red and black 2 ({@code RouletteBlockEntity.java:191}). */
    public static int multiplier(int colour) {
        return colour == GREEN ? 36 : 2;
    }

    public static Odds.Pool pool(int colour) {
        return POOLS[colour];
    }

    /** The forced draw's first number range: 1800 for green, 100 for red and black ({@code CasinoOdds}). */
    public static int parts(int colour) {
        return colour == GREEN ? 1800 : 100;
    }

    /**
     * The pocket: natural odds = {@code nextInt(37)}; with a configured chance p the mod's forced pools for the staked
     * colour (P(win) = p/100 on red/black, p/1800 on green).
     */
    public static int draw(int stakedColour, int winChance, IntUnaryOperator nextInt) {
        if (winChance >= 0) {
            return Odds.draw(POOLS[stakedColour], winChance, parts(stakedColour), nextInt);
        }
        return nextInt.applyAsInt(POCKETS);
    }

    /** What {@code count} items on {@code colour} return when the ball lands in {@code pocket}. */
    public static long payout(int colour, long count, int pocket) {
        return colour(pocket) == colour ? count * multiplier(colour) : 0;
    }
}
