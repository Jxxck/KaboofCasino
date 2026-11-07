package de.tigges.kaboofcasino.gui;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;

/** What a click does after {@link MenuListener} cancelled it (PostMarkt {@code ClickRoute}, pure, unit-tested). */
final class ClickRoute {

    enum Outcome {
        NOTHING, BUTTON, COMMIT, SELECTION, BUSY, TOO_EARLY
    }

    /**
     * One click; {@code ticksSinceOpen} is negative if the menu was never opened through {@link MenuService}.
     * {@code shortcut}: a key (number key or the offhand swap) over a top slot that the menu mapped to a button
     * ({@link Menu#keyButton}); {@code button}/{@code commitButton} then describe that button.
     */
    record Click(ClickType type, InventoryAction action, int rawSlot, int topSize, int inventoryIndex, boolean button,
                 boolean commitButton, boolean usable, boolean busy, long ticksSinceOpen, int guardTicks,
                 boolean shortcut) {

        Click(ClickType type, InventoryAction action, int rawSlot, int topSize, int inventoryIndex, boolean button,
              boolean commitButton, boolean usable, boolean busy, long ticksSinceOpen, int guardTicks) {
            this(type, action, rawSlot, topSize, inventoryIndex, button, commitButton, usable, busy, ticksSinceOpen,
                    guardTicks, false);
        }
    }

    private ClickRoute() {
    }

    static Outcome route(Click c) {
        if (!c.usable()) {
            return Outcome.NOTHING;
        }
        if (c.rawSlot() >= 0 && c.rawSlot() < c.topSize()) {
            if (!c.button()) {
                return Outcome.NOTHING;
            }
            if (c.commitButton()) {
                if (!c.shortcut() && !ClickRules.triggersCommit(c.type())) {
                    return Outcome.NOTHING;
                }
                if (c.busy()) {
                    return Outcome.BUSY;
                }
                return c.ticksSinceOpen() < 0 || c.ticksSinceOpen() < c.guardTicks() ? Outcome.TOO_EARLY
                        : Outcome.COMMIT;
            }
            if (!c.shortcut() && !ClickRules.triggersButton(c.type())) {
                return Outcome.NOTHING;
            }
            return c.busy() ? Outcome.BUSY : Outcome.BUTTON;
        }
        if (ClickRules.triggersSelection(c.type(), c.rawSlot(), c.topSize(), c.inventoryIndex())) {
            return c.busy() ? Outcome.BUSY : Outcome.SELECTION;
        }
        return Outcome.NOTHING;
    }
}
