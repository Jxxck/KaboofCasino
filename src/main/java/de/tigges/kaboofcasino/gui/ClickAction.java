package de.tigges.kaboofcasino.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** A button's action. Runs on MAIN inside the (already cancelled) click event. */
@FunctionalInterface
public interface ClickAction {

    void click(Player player, ClickType click);
}
