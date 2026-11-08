package de.tigges.kaboofcasino.wager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/** Chip values, denominations, the payout breakdown and paying a value out of a chip selection (PLAN-2 C1/C2). */
class ChipsTest {

    private static ItemAmount chips(int d, long n) {
        return new ItemAmount(Chips.key(d), n);
    }

    @Test
    void keysAndDenominations() {
        assertEquals("kaboofcasino:chip_25", Chips.key(25));
        assertEquals(25, Chips.denomination("kaboofcasino:chip_25").orElseThrow());
        for (String bad : List.of("kaboofcasino:chip_2", "kaboofcasino:chip_", "kaboofcasino:chip_0025x",
                "kaboofcasino:chip_10000", "minecraft:paper", "kaboofcasino:chips", "roulettecasino:chip_25")) {
            assertTrue(Chips.denomination(bad).isEmpty(), bad);
        }
        assertThrows(IllegalArgumentException.class, () -> Chips.key(10));
        assertTrue(Chips.isChips(Chips.VALUE));
        assertTrue(Chips.isChips("kaboofcasino:chip_1000"));
        assertFalse(Chips.isChips("minecraft:diamond"));
        // every denomination divides the next (the exact-payment proof needs it)
        for (int i = 1; i < Chips.DENOMINATIONS.length; i++) {
            assertEquals(0, Chips.DENOMINATIONS[i] % Chips.DENOMINATIONS[i - 1]);
        }
    }

    @Test
    void payoutsComeInTheLargestDenominationsFirst() {
        assertEquals(List.of(), Chips.breakdown(0));
        assertEquals(List.of(chips(1000, 2), chips(500, 1), chips(100, 3), chips(25, 1), chips(5, 1), chips(1, 4)),
                Chips.breakdown(2834));
        assertEquals(List.of(chips(25, 3)), Chips.breakdown(75));
        assertEquals(List.of(chips(1000, 1_000_000)), Chips.breakdown(1_000_000_000L));
        SplittableRandom r = new SplittableRandom(9);
        for (int i = 0; i < 10_000; i++) {
            long v = r.nextLong(0, 5_000_000);
            List<ItemAmount> parts = Chips.breakdown(v);
            assertEquals(v, Chips.valueOf(parts));
            // never five 1s, five 5s, four 25s, five 100s or two 500s: the fewest chips
            for (ItemAmount a : parts) {
                int d = Chips.denomination(a.item()).orElseThrow();
                int idx = Chips.index(d);
                if (idx < Chips.DENOMINATIONS.length - 1) {
                    assertTrue(a.count() * d < Chips.DENOMINATIONS[idx + 1], v + ": " + parts);
                }
            }
        }
        assertThrows(IllegalArgumentException.class, () -> Chips.breakdown(-1));
    }

    @Test
    void valuesFoldAndUnfold() {
        List<ItemAmount> mixed = List.of(new ItemAmount("minecraft:diamond", 3), chips(25, 2),
                new ItemAmount(Chips.VALUE, 7), chips(1, 3));
        assertEquals(60, Chips.valueOf(mixed));
        assertEquals(List.of(new ItemAmount("minecraft:diamond", 3), new ItemAmount(Chips.VALUE, 60)),
                Chips.normalize(mixed));
        assertEquals(List.of(new ItemAmount("minecraft:diamond", 3), chips(25, 2), chips(5, 2)),
                Chips.expand(List.of(new ItemAmount("minecraft:diamond", 3), new ItemAmount(Chips.VALUE, 60))));
        assertEquals(List.of(), Chips.normalize(List.of()));
    }

    @Test
    void selections() {
        Chips.Selection s = Chips.Selection.EMPTY.plus(25, 2).plus(1, 3).plus(1000, 1);
        assertEquals(1053, s.value());
        assertEquals(6, s.chips());
        assertEquals(1000, s.largest());
        assertEquals(List.of(chips(1000, 1), chips(25, 2), chips(1, 3)), s.amounts());
        assertEquals(new ItemAmount(Chips.VALUE, 1053), s.asValue());
        assertEquals(1052, s.minusSmallest().value());
        assertEquals(Chips.Selection.EMPTY, Chips.Selection.EMPTY.minusSmallest());
        assertNull(Chips.Selection.EMPTY.asValue());
        assertEquals(s, Chips.Selection.of(Map.of(1000, 1L, 25, 2L, 1, 3L)));
        assertThrows(IllegalArgumentException.class, () -> new Chips.Selection(new long[] {1, 2}));
        assertThrows(IllegalArgumentException.class, () -> new Chips.Selection(new long[] {0, 0, -1, 0, 0, 0}));
    }

    @Test
    void payingExactlyWheneverPossible() {
        Chips.Selection have = Chips.Selection.EMPTY.plus(100, 2).plus(25, 3).plus(5, 1).plus(1, 4);
        Chips.Payment p = Chips.pay(have, 134);
        assertEquals(0, p.change());
        assertEquals(134, p.take().value());
        // only a 100 for 25: one chip, 75 change
        p = Chips.pay(Chips.Selection.EMPTY.plus(100, 1), 25);
        assertEquals(Chips.Selection.EMPTY.plus(100, 1), p.take());
        assertEquals(75, p.change());
        // 25 + 5 = 30 needed, only 25 + 1000: the smallest single chip worth at least 30 is the 1000
        p = Chips.pay(Chips.Selection.EMPTY.plus(25, 1).plus(1000, 1), 30);
        assertEquals(Chips.Selection.EMPTY.plus(1000, 1), p.take());
        assertEquals(970, p.change());
        // 64 out of 1000 + 11 x 25 + 2 x 5 + 2 x 1: not exact; three 25s (11 change), not the 1000 (936)
        p = Chips.pay(Chips.Selection.EMPTY.plus(1000, 1).plus(25, 11).plus(5, 2).plus(1, 2), 64);
        assertEquals(Chips.Selection.EMPTY.plus(25, 3), p.take());
        assertEquals(11, p.change());
        // two 5s for 7: no single chip is enough, so 5 + one more 5, 3 change
        p = Chips.pay(Chips.Selection.EMPTY.plus(5, 2), 7);
        assertEquals(10, p.take().value());
        assertEquals(3, p.change());
        assertNull(Chips.pay(Chips.Selection.EMPTY.plus(5, 1), 7), "worth less");
        assertEquals(0, Chips.pay(Chips.Selection.EMPTY, 0).take().value());
    }

    /** Against brute force on small piles: exact whenever any exact combination exists, else value-neutral change. */
    @Test
    void payingMatchesBruteForce() {
        SplittableRandom r = new SplittableRandom(4);
        for (int round = 0; round < 3000; round++) {
            long[] counts = new long[Chips.DENOMINATIONS.length];
            for (int i = 0; i < counts.length; i++) {
                counts[i] = r.nextInt(i < 3 ? 6 : 3);
            }
            Chips.Selection have = new Chips.Selection(counts);
            long value = r.nextLong(0, Math.max(1, have.value() + 3));
            Chips.Payment p = Chips.pay(have, value);
            if (have.value() < value) {
                assertNull(p);
                continue;
            }
            for (int i = 0; i < counts.length; i++) {
                assertTrue(p.take().counts()[i] <= counts[i], "takes only what is there");
            }
            assertEquals(value + p.change(), p.take().value(), "value-neutral");
            assertEquals(exactPossible(counts, value), p.change() == 0, have + " pay " + value);
            long least = value;
            while (!exactPossible(counts, least)) {
                least++;
            }
            assertEquals(least - value, p.change(), have + " pay " + value + ": the least change");
            assertTrue(p.change() < Chips.DENOMINATIONS[Chips.DENOMINATIONS.length - 1], "less than the largest chip");
        }
    }

    private static boolean exactPossible(long[] counts, long value) {
        return exact(counts, 0, value);
    }

    private static boolean exact(long[] counts, int i, long rest) {
        if (rest == 0) {
            return true;
        }
        if (i == counts.length) {
            return false;
        }
        for (long n = 0; n <= counts[i] && n * Chips.DENOMINATIONS[i] <= rest; n++) {
            if (exact(counts, i + 1, rest - n * Chips.DENOMINATIONS[i])) {
                return true;
            }
        }
        return false;
    }
}
