package de.tigges.kaboofcasino.gui;

import org.bukkit.inventory.ItemStack;

/**
 * A top-inventory button: icon (display copy), action and whether it is a COMMIT button (only LEFT/RIGHT, click guard
 * after opening, busy while it runs). {@code action} null = display only.
 */
public record Button(ItemStack icon, ClickAction action, boolean commit) {

    public static Button of(ItemStack icon, ClickAction action) {
        return new Button(icon, action, false);
    }

    public static Button commit(ItemStack icon, ClickAction action) {
        return new Button(icon, action, true);
    }

    public static Button display(ItemStack icon) {
        return new Button(icon, null, false);
    }
}
