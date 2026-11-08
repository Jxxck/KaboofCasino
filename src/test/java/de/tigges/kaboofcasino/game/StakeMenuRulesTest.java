package de.tigges.kaboofcasino.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.Test;

/**
 * The bet slots' chip selection ({@link ChipStakes#add}): chips per click, capped by what the player has unselected
 * and by {@code bets.max-stake} in chip value (per slot, or the round's total).
 */
class StakeMenuRulesTest {

    @Test
    void clicksAddChipsToTheActiveSlot() {
        // left: the clicked stack; right: one chip; shift-left: every unselected chip of that kind
        assertEquals(20, ChipStakes.add(ClickType.LEFT, 25, 20, 50, 0, 0, 2000));
        assertEquals(1, ChipStakes.add(ClickType.RIGHT, 25, 20, 50, 125, 0, 2000));
        assertEquals(50, ChipStakes.add(ClickType.SHIFT_LEFT, 5, 20, 50, 0, 0, 2000));
        // never more than the unselected chips
        assertEquals(7, ChipStakes.add(ClickType.LEFT, 1, 64, 7, 0, 0, 2000));
        assertEquals(0, ChipStakes.add(ClickType.RIGHT, 100, 1, 0, 0, 0, 2000));
    }

    @Test
    void theValueCapCountsWholeChips() {
        // 1 950 in the slot: room for 50 = two 25s, no 100
        assertEquals(2, ChipStakes.add(ClickType.SHIFT_LEFT, 25, 10, 10, 1950, 0, 2000));
        assertEquals(0, ChipStakes.add(ClickType.RIGHT, 100, 3, 3, 1950, 0, 2000));
        assertTrue(ChipStakes.capped(ClickType.RIGHT, 100, 3, 3, 1950, 0, 2000));
        assertFalse(ChipStakes.capped(ClickType.LEFT, 25, 2, 2, 1950, 0, 2000), "exactly fits");
        // a full slot takes nothing more
        assertEquals(0, ChipStakes.add(ClickType.RIGHT, 1, 64, 64, 2000, 0, 2000));
        // two 1000s fill a slot of 2000
        assertEquals(2, ChipStakes.add(ClickType.LEFT, 1000, 5, 5, 0, 0, 2000));
    }

    @Test
    void perSlotCapIgnoresTheOtherSlotsTheRoundCapDoesNot() {
        // cap per slot (default): the menu passes others = 0, so every slot can hold the full maximum
        assertEquals(20, ChipStakes.add(ClickType.LEFT, 100, 20, 64, 0, 0, 2000));
        // cap per round: 1 500 in the other slots, at most 500 more here
        assertEquals(5, ChipStakes.add(ClickType.LEFT, 100, 20, 64, 0, 1500, 2000));
        assertEquals(0, ChipStakes.add(ClickType.LEFT, 1000, 20, 64, 0, 1500, 2000));
        // others already past the maximum (config lowered): nothing, never negative
        assertEquals(0, ChipStakes.add(ClickType.LEFT, 1, 20, 64, 0, 2500, 2000));
    }
}
