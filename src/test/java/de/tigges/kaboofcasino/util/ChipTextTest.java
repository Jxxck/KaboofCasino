package de.tigges.kaboofcasino.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The one number format: dot grouping everywhere, "1 Chip" in the singular. */
class ChipTextTest {

    @Test
    void numbersGroupWithDots() {
        assertEquals("0", ChipText.number(0));
        assertEquals("999", ChipText.number(999));
        assertEquals("1.000", ChipText.number(1000));
        assertEquals("1.234.567", ChipText.number(1_234_567));
        assertEquals("100.000.000", ChipText.number(100_000_000));
        assertEquals("-1.250", ChipText.number(-1250));
        assertEquals("9.223.372.036.854.775.807", ChipText.number(Long.MAX_VALUE));
        // the old helpers say the same
        assertEquals("2.048", ItemNames.value(2048));
        assertEquals("2.048", de.tigges.kaboofcasino.cashier.CashierMath.format(2048));
    }

    @Test
    void chipAmountsHaveTheRightNoun() {
        assertEquals("1 Chip", ChipText.chips(1));
        assertEquals("0 Chips", ChipText.chips(0));
        assertEquals("2 Chips", ChipText.chips(2));
        assertEquals("1.250 Chips", ChipText.chips(1250));
    }
}
