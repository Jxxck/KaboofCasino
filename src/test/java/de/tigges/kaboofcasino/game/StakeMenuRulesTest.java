package de.tigges.kaboofcasino.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.tigges.kaboofcasino.wager.ItemAmount;
import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.Test;

/** The three-slot selection: counts per click, capped by the round's total and by what the player owns. */
class StakeMenuRulesTest {

    private static final String D = "minecraft:diamond";

    @Test
    void clicksSetTheActiveSlot() {
        // left: the clicked stack; right: +1 of the same item, else 1; shift-left: everything still free
        assertEquals(20, StakeMenu.countFor(ClickType.LEFT, null, D, 20, 0, 50, 64));
        assertEquals(6, StakeMenu.countFor(ClickType.RIGHT, new ItemAmount(D, 5), D, 20, 0, 50, 64));
        assertEquals(1, StakeMenu.countFor(ClickType.RIGHT, new ItemAmount("minecraft:iron_ingot", 5), D, 20, 0, 50,
                64));
        assertEquals(50, StakeMenu.countFor(ClickType.SHIFT_LEFT, null, D, 20, 0, 50, 64));
    }

    @Test
    void theRoundTotalAndTheOwnedItemsCap() {
        // 40 items in the other slots: at most 24 more in this one
        assertEquals(24, StakeMenu.countFor(ClickType.SHIFT_LEFT, null, D, 64, 40, 100, 64));
        assertEquals(24, StakeMenu.countFor(ClickType.LEFT, null, D, 64, 40, 100, 64));
        // only 7 diamonds not already staked in another slot
        assertEquals(7, StakeMenu.countFor(ClickType.LEFT, null, D, 64, 0, 7, 64));
        assertEquals(0, StakeMenu.countFor(ClickType.RIGHT, null, D, 1, 64, 10, 64));
    }
}
