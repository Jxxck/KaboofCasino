package de.tigges.kaboofcasino.gui;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;

/** Pure click classification (PostMarkt {@code ClickRules}). No vanilla item movement is ever allowed. */
public final class ClickRules {

    private ClickRules() {
    }

    /** Always false (selection model). */
    public static boolean allowVanillaMove(ClickType click, InventoryAction action, int rawSlot, int topSize) {
        return false;
    }

    /** LEFT, RIGHT, SHIFT_LEFT, SHIFT_RIGHT. */
    public static boolean triggersButton(ClickType click) {
        return click == ClickType.LEFT || click == ClickType.RIGHT || click == ClickType.SHIFT_LEFT
                || click == ClickType.SHIFT_RIGHT;
    }

    /** LEFT, RIGHT only. */
    public static boolean triggersCommit(ClickType click) {
        return click == ClickType.LEFT || click == ClickType.RIGHT;
    }

    /** Bottom storage slot 0-35 with LEFT, SHIFT_LEFT or RIGHT. */
    public static boolean triggersSelection(ClickType click, int rawSlot, int topSize, int inventoryIndex) {
        return rawSlot >= topSize && inventoryIndex >= 0 && inventoryIndex <= 35
                && (click == ClickType.LEFT || click == ClickType.SHIFT_LEFT || click == ClickType.RIGHT);
    }
}
