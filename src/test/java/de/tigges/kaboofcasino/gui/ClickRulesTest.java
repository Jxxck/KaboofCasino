package de.tigges.kaboofcasino.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.junit.jupiter.api.Test;

/** Every 26.3 click type x action x region: never a vanilla move; the routing of PostMarkt's ClickRoute. */
class ClickRulesTest {

    @Test
    void noClickEverMovesItems() {
        for (ClickType type : ClickType.values()) {
            for (InventoryAction action : InventoryAction.values()) {
                for (int raw : new int[] {-999, -1, 0, 31, 49, 53, 54, 80, 89}) {
                    assertFalse(ClickRules.allowVanillaMove(type, action, raw, 54));
                }
            }
        }
    }

    private static ClickRoute.Outcome route(ClickType type, int raw, int index, boolean button, boolean commit,
                                            boolean usable, boolean busy, long sinceOpen) {
        return ClickRoute.route(new ClickRoute.Click(type, InventoryAction.PICKUP_ALL, raw, 54, index, button, commit,
                usable, busy, sinceOpen, 4));
    }

    @Test
    void routing() {
        assertEquals(ClickRoute.Outcome.COMMIT, route(ClickType.LEFT, 49, 49, true, true, true, false, 10));
        assertEquals(ClickRoute.Outcome.TOO_EARLY, route(ClickType.LEFT, 49, 49, true, true, true, false, 2));
        assertEquals(ClickRoute.Outcome.NOTHING, route(ClickType.SHIFT_LEFT, 49, 49, true, true, true, false, 10));
        assertEquals(ClickRoute.Outcome.BUSY, route(ClickType.RIGHT, 49, 49, true, true, true, true, 10));
        assertEquals(ClickRoute.Outcome.BUTTON, route(ClickType.SHIFT_RIGHT, 31, 31, true, false, true, false, 0));
        assertEquals(ClickRoute.Outcome.NOTHING, route(ClickType.NUMBER_KEY, 31, 31, true, false, true, false, 0));
        assertEquals(ClickRoute.Outcome.SELECTION, route(ClickType.LEFT, 60, 15, false, false, true, false, 0));
        assertEquals(ClickRoute.Outcome.SELECTION, route(ClickType.SHIFT_LEFT, 81, 0, false, false, true, false, 0));
        assertEquals(ClickRoute.Outcome.NOTHING, route(ClickType.DROP, 60, 15, false, false, true, false, 0));
        assertEquals(ClickRoute.Outcome.NOTHING, route(ClickType.LEFT, -999, -999, false, false, true, false, 0));
        // creative/spectator/fake viewers never trigger anything
        assertEquals(ClickRoute.Outcome.NOTHING, route(ClickType.LEFT, 49, 49, true, true, false, false, 10));
        assertEquals(ClickRoute.Outcome.NOTHING, route(ClickType.LEFT, 60, 15, false, false, false, false, 0));
    }

    @Test
    void openRules() {
        assertEquals(OpenRules.Verdict.OPEN, OpenRules.check(10, 10, true, true));
        assertEquals(OpenRules.Verdict.NOT_INTERACTION_TICK, OpenRules.check(9, 10, true, true));
        assertEquals(OpenRules.Verdict.UNUSABLE, OpenRules.check(10, 10, false, true));
        assertEquals(OpenRules.Verdict.UNSAFE, OpenRules.check(10, 10, true, false));
        assertFalse(MenuSafety.safe(false, 3.5f, false));
        assertFalse(MenuSafety.safe(true, 0f, false));
        assertFalse(MenuSafety.safe(false, 0f, true));
    }
}
