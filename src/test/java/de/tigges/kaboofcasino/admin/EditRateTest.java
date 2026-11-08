package de.tigges.kaboofcasino.admin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The mod's limit: 20 edits per player in one game second ({@code gameTime / 20}), the rest dropped. */
class EditRateTest {

    @Test
    void twentyPerGameSecondPerPlayer() {
        EditRate rate = new EditRate();
        UUID a = new UUID(0, 1);
        UUID b = new UUID(0, 2);
        for (int i = 0; i < 20; i++) {
            assertFalse(rate.limited(a, 2000 + i % 20), "edit " + i);
        }
        assertTrue(rate.limited(a, 2019));
        assertTrue(rate.limited(a, 2005));
        assertFalse(rate.limited(b, 2019), "another player has his own count");
        assertFalse(rate.limited(a, 2020), "the next game second starts again");
        for (int i = 1; i < 20; i++) {
            assertFalse(rate.limited(a, 2039));
        }
        assertTrue(rate.limited(a, 2039));
        rate.forget(a);
        assertFalse(rate.limited(a, 2039));
        // game time going back (another world) is just another second
        assertFalse(rate.limited(a, 100));
    }
}
