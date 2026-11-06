package de.tigges.kaboofcasino.game.slot;

import de.tigges.kaboofcasino.game.Odds;

/**
 * The slot machine's rules (games.md 6, port of {@code SlotMachineGame}): 3 reels of 8 faces, one centre line read
 * from the left, paytable checked top to bottom (first match wins), -1 = any symbol. Natural RTP 499/512.
 */
public final class SlotGame {

    public static final int REELS = 3;
    public static final int FACES = 8;
    public static final int SEVEN = 0;
    public static final int DIAMOND = 1;
    public static final int BAR = 2;
    public static final int BELL = 3;
    public static final int LEMON = 4;
    public static final int CHERRY = 5;
    public static final int ANY = -1;
    public static final String[] NAMES = {"seven", "diamond", "bar", "bell", "lemon", "cherry"};

    /** Face index 0..7 -> symbol, per reel ({@code SlotMachineGame.STRIPS}). */
    private static final int[][] STRIPS = {
        {SEVEN, CHERRY, BELL, LEMON, BAR, CHERRY, DIAMOND, LEMON},
        {SEVEN, LEMON, DIAMOND, CHERRY, BELL, LEMON, BAR, CHERRY},
        {SEVEN, CHERRY, BAR, LEMON, DIAMOND, CHERRY, BELL, LEMON},
    };

    /** One paytable row. */
    public record Row(int a, int b, int c, int multiplier) {
    }

    public static final Row[] PAYTABLE = {
        new Row(SEVEN, SEVEN, SEVEN, 100),
        new Row(DIAMOND, DIAMOND, DIAMOND, 30),
        new Row(BAR, BAR, BAR, 15),
        new Row(BELL, BELL, BELL, 10),
        new Row(LEMON, LEMON, LEMON, 6),
        new Row(CHERRY, CHERRY, CHERRY, 4),
        new Row(CHERRY, CHERRY, ANY, 3),
        new Row(CHERRY, ANY, ANY, 2),
    };

    /** Result kinds; PUSH (x1) exists in the mod but no row produces it. */
    public enum Kind {
        NONE, PUSH, WIN, BIG, JACKPOT
    }

    /** @param row paytable row, -1 for no win */
    public record Result(int multiplier, int row, Kind kind) {
    }

    /** {@code CasinoOdds.SLOTS}: winning pool = multiplier > 1 (140 outcomes), losing pool = the other 372. */
    public static final Odds.Pool POOL = Odds.pool(512, e -> evaluate(stops(e)).multiplier() > 1, e -> 1,
            e -> evaluate(stops(e)).multiplier() * 4);

    private SlotGame() {
    }

    public static int symbol(int reel, int face) {
        return STRIPS[reel][Math.floorMod(face, FACES)];
    }

    public static int[] symbols(int[] stops) {
        return new int[] {symbol(0, stops[0]), symbol(1, stops[1]), symbol(2, stops[2])};
    }

    private static boolean fits(int want, int got) {
        return want == ANY || want == got;
    }

    public static Result evaluate(int a, int b, int c) {
        for (int i = 0; i < PAYTABLE.length; i++) {
            Row row = PAYTABLE[i];
            if (fits(row.a(), a) && fits(row.b(), b) && fits(row.c(), c)) {
                return new Result(row.multiplier(), i, kindOf(row.multiplier()));
            }
        }
        return new Result(0, -1, Kind.NONE);
    }

    public static Result evaluate(int[] stops) {
        int[] s = symbols(stops);
        return evaluate(s[0], s[1], s[2]);
    }

    public static Kind kindOf(int multiplier) {
        if (multiplier >= 100) {
            return Kind.JACKPOT;
        }
        if (multiplier >= 15) {
            return Kind.BIG;
        }
        if (multiplier >= 2) {
            return Kind.WIN;
        }
        return multiplier == 1 ? Kind.PUSH : Kind.NONE;
    }

    /** {@code CasinoOdds.stops}: outcome 0..511 -> (e/64, e/8 % 8, e % 8). */
    public static int[] stops(int encoded) {
        return new int[] {encoded / 64, encoded / 8 % 8, encoded % 8};
    }

    /**
     * Draws the three stops: natural odds = each reel {@code nextInt(8)} independently (the mod's
     * {@code SlotMachineBlockEntity.start}); a configured chance 0..100 uses the forced pools.
     */
    public static int[] draw(int winChance, java.util.function.IntUnaryOperator nextInt) {
        if (winChance >= 0) {
            return stops(Odds.draw(POOL, winChance, 100, nextInt));
        }
        return new int[] {nextInt.applyAsInt(FACES), nextInt.applyAsInt(FACES), nextInt.applyAsInt(FACES)};
    }

    /** {@code SlotMachineGame.expectedReturn()}: sum of all 512 multipliers / 512 = 499/512. */
    public static double expectedReturn() {
        long total = 0;
        for (int e = 0; e < 512; e++) {
            total += evaluate(stops(e)).multiplier();
        }
        return total / 512.0;
    }
}
