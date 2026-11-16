package de.tigges.kaboofcasino.cashier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.wager.Chips;
import de.tigges.kaboofcasino.wager.ItemAmount;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The exchange value at the configured rates, the change into a target denomination, the German digits. */
class CashierMathTest {

    static ItemAmount a(String item, long n) {
        return new ItemAmount(item, n);
    }

    @Test
    void theExchangeValueIsCountTimesRate() {
        CashierSettings s = CashierSettings.DEFAULTS;
        assertEquals(1, CashierMath.value(List.of(a("minecraft:coal", 1)), s::rate));
        assertEquals(64 * 32 + 9 * 2304, CashierMath.value(List.of(a("minecraft:diamond", 64),
                a("minecraft:netherite_block", 9)), s::rate));
        assertEquals(0, CashierMath.value(List.of(), s::rate));
        assertEquals(-1, CashierMath.value(List.of(a("minecraft:dirt", 1)), s::rate), "no rate");
        assertEquals(-1, CashierMath.value(List.of(a(Chips.key(25), 1)), s::rate), "chips have no rate");
        // PLAN-2 C3: each block is worth nine of its item
        for (CashierSettings.Rate r : s.rates()) {
            if (r.item().endsWith("_block")) {
                String base = r.item().replace("_block", "").replace("copper", "copper_ingot")
                        .replace("iron", "iron_ingot").replace("gold", "gold_ingot")
                        .replace("netherite", "netherite_ingot").replace("lapis", "lapis_lazuli");
                assertEquals(9 * s.rate(base).orElseThrow(), r.value(), r.item());
            }
        }
        assertThrows(ArithmeticException.class, () -> CashierMath.value(List.of(a("minecraft:coal",
                Long.MAX_VALUE / 2), a("minecraft:diamond", Long.MAX_VALUE / 2)), s::rate));
    }

    @Test
    void changeGivesTheTargetAsFarAsItGoesAndTheRestSmaller() {
        assertEquals(List.of(a(Chips.key(25), 4)), CashierMath.change(100, 25));
        assertEquals(List.of(a(Chips.key(100), 1)), CashierMath.change(100, 100));
        assertEquals(List.of(a(Chips.key(1000), 1)), CashierMath.change(1000, 1000));
        assertEquals(List.of(a(Chips.key(25), 12), a(Chips.key(5), 2)), CashierMath.change(310, 25));
        assertEquals(List.of(a(Chips.key(1), 7)), CashierMath.change(7, 1));
        // the target does not fit at all: all of it in the next smaller ones
        assertEquals(List.of(a(Chips.key(5), 1), a(Chips.key(1), 1)), CashierMath.change(6, 25));
        assertEquals(List.of(), CashierMath.change(0, 5));
        assertThrows(IllegalArgumentException.class, () -> CashierMath.change(10, 3));
        Random r = new Random(3);
        for (int n = 0; n < 5000; n++) {
            long value = r.nextInt(1_000_000);
            int target = Chips.DENOMINATIONS[r.nextInt(Chips.DENOMINATIONS.length)];
            List<ItemAmount> out = CashierMath.change(value, target);
            assertEquals(value, Chips.valueOf(out), value + " into " + target);
            for (ItemAmount x : out) {
                assertTrue(Chips.denomination(x.item()).orElseThrow() <= target);
            }
        }
    }

    @Test
    void sameDetectsAChangeThatMovesNothing() {
        assertTrue(CashierMath.same(Chips.Selection.of(Map.of(1, 3L)), CashierMath.change(3, 5)));
        assertTrue(CashierMath.same(Chips.Selection.of(Map.of(25, 2L)), CashierMath.change(50, 25)));
        assertFalse(CashierMath.same(Chips.Selection.of(Map.of(100, 1L)), CashierMath.change(100, 25)));
        assertFalse(CashierMath.same(Chips.Selection.of(Map.of(5, 2L)), CashierMath.change(10, 1)));
    }

    @Tag("german")
    @Test
    void valuesInGermanDigits() {
        assertEquals("0", CashierMath.format(0));
        assertEquals("999", CashierMath.format(999));
        assertEquals("1.000", CashierMath.format(1000));
        assertEquals("1.234.567", CashierMath.format(1_234_567));
        assertEquals("-12.000", CashierMath.format(-12_000));
    }
}
