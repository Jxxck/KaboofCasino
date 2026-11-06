package de.tigges.kaboofcasino.game.slot;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.game.CasinoRandom;
import de.tigges.kaboofcasino.game.Odds;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Paytable and odds against games.md section 6 (and SlotMachineGame / CasinoOdds of the mod). */
class SlotGameTest {

    private static final int S = SlotGame.SEVEN;
    private static final int D = SlotGame.DIAMOND;
    private static final int B = SlotGame.BAR;
    private static final int L = SlotGame.BELL;
    private static final int M = SlotGame.LEMON;
    private static final int C = SlotGame.CHERRY;

    @Test
    void stripsMatchGamesMd() {
        int[][] expected = {
            {S, C, L, M, B, C, D, M},
            {S, M, D, C, L, M, B, C},
            {S, C, B, M, D, C, L, M},
        };
        for (int reel = 0; reel < 3; reel++) {
            for (int face = 0; face < 8; face++) {
                assertEquals(expected[reel][face], SlotGame.symbol(reel, face), "reel " + reel + " face " + face);
            }
            assertEquals(expected[reel][3], SlotGame.symbol(reel, 3 + 8 * 5)); // faces wrap mod 8
        }
    }

    @Test
    void everyPaytableRow() {
        assertEquals(100, SlotGame.evaluate(S, S, S).multiplier());
        assertEquals(30, SlotGame.evaluate(D, D, D).multiplier());
        assertEquals(15, SlotGame.evaluate(B, B, B).multiplier());
        assertEquals(10, SlotGame.evaluate(L, L, L).multiplier());
        assertEquals(6, SlotGame.evaluate(M, M, M).multiplier());
        assertEquals(4, SlotGame.evaluate(C, C, C).multiplier());
        assertEquals(3, SlotGame.evaluate(C, C, S).multiplier());
        assertEquals(2, SlotGame.evaluate(C, S, C).multiplier());
        assertEquals(2, SlotGame.evaluate(C, M, M).multiplier());
        // cherries count only consecutively from the left reel
        assertEquals(0, SlotGame.evaluate(S, C, C).multiplier());
        assertEquals(0, SlotGame.evaluate(M, C, C).multiplier());
        assertEquals(0, SlotGame.evaluate(S, S, D).multiplier());
        assertEquals(0, SlotGame.evaluate(M, M, L).multiplier());
    }

    @Test
    void kinds() {
        assertEquals(SlotGame.Kind.JACKPOT, SlotGame.evaluate(S, S, S).kind());
        assertEquals(SlotGame.Kind.BIG, SlotGame.evaluate(D, D, D).kind());
        assertEquals(SlotGame.Kind.BIG, SlotGame.evaluate(B, B, B).kind());
        assertEquals(SlotGame.Kind.WIN, SlotGame.evaluate(L, L, L).kind());
        assertEquals(SlotGame.Kind.WIN, SlotGame.evaluate(C, S, S).kind());
        assertEquals(SlotGame.Kind.NONE, SlotGame.evaluate(S, S, D).kind());
        assertEquals(SlotGame.Kind.PUSH, SlotGame.kindOf(1)); // dead branch: no row pays x1
    }

    @Test
    void exactReturnIs499Over512() {
        long total = 0;
        int hits = 0;
        Map<Integer, Integer> count = new HashMap<>();
        for (int e = 0; e < 512; e++) {
            int m = SlotGame.evaluate(SlotGame.stops(e)).multiplier();
            total += m;
            hits += m > 0 ? 1 : 0;
            count.merge(m, 1, Integer::sum);
        }
        assertEquals(499, total);
        assertEquals(140, hits);
        assertEquals(499.0 / 512.0, SlotGame.expectedReturn(), 1e-15);
        // natural probabilities of games.md's table
        assertEquals(Map.of(100, 1, 30, 1, 15, 1, 10, 1, 6, 8, 4, 8, 3, 24, 2, 96, 0, 372), count);
    }

    @Test
    void forcedChancePoolsMatchCasinoOdds() {
        Odds.Pool pool = SlotGame.POOL;
        assertEquals(140, pool.wins().length);
        assertEquals(372, pool.losses().length);
        assertEquals(499.0 / 140.0, pool.winningReturn(), 1e-12);
        assertEquals(0.0, pool.losingReturn(), 1e-12);
        assertEquals(0.25 * 499.0 / 140.0, pool.expected(0.25), 1e-12);   // p=25 -> 89.11 %
        assertEquals(0.8911, pool.expected(0.25), 1e-4);
        assertEquals(0.28 * 499 / 140, pool.expected(0.28), 1e-12);       // break-even near 28.06
    }

    @Test
    void naturalDrawUsesThreeIndependentReels() {
        int[] calls = {0};
        int[] stops = SlotGame.draw(-1, bound -> {
            assertEquals(8, bound);
            return calls[0]++ % 8;
        });
        assertArrayEquals(new int[] {0, 1, 2}, stops);
    }

    @Test
    void forcedDrawPicksFromTheRightPool() {
        CasinoRandom random = new CasinoRandom(7);
        for (int i = 0; i < 2000; i++) {
            assertTrue(SlotGame.evaluate(SlotGame.draw(100, random::nextInt)).multiplier() > 1);
            assertEquals(0, SlotGame.evaluate(SlotGame.draw(0, random::nextInt)).multiplier());
        }
    }
}
