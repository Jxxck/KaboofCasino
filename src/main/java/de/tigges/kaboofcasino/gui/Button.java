package de.tigges.kaboofcasino.gui;

import org.bukkit.inventory.ItemStack;

/**
 * A top-inventory button: icon (display copy), action and whether it is a COMMIT button (only LEFT/RIGHT, click guard
 * after opening, busy while it runs; {@code shift}: Shift-clicks commit too, the Chip-Shop's "a stack").
 * {@code action} null = display only.
 */
public record Button(ItemStack icon, ClickAction action, boolean commit, boolean shift) {

    public static Button of(ItemStack icon, ClickAction action) {
        return new Button(icon, action, false, false);
    }

    public static Button commit(ItemStack icon, ClickAction action) {
        return new Button(icon, action, true, false);
    }

    /** A commit button that also takes SHIFT_LEFT and SHIFT_RIGHT (the action sees the click type). */
    public static Button commitWithShift(ItemStack icon, ClickAction action) {
        return new Button(icon, action, true, true);
    }

    public static Button display(ItemStack icon) {
        return new Button(icon, null, false, false);
    }
}
