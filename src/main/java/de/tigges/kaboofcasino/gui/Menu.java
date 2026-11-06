package de.tigges.kaboofcasino.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Base of every casino chest menu (PostMarkt {@code Menu}). The holder object is the menu's identity; the inventory is
 * created lazily (no {@code this} escape). Subclasses fill slots in {@link #render()} with display copies only.
 * Thread: MAIN.
 */
public abstract class Menu implements InventoryHolder {

    private final UUID viewer;
    private final int rows;
    private final Component title;
    private final Map<Integer, Button> buttons = new HashMap<>();
    private Inventory inventory;
    private boolean busy;
    private long openedAtTick = -1;

    protected Menu(UUID viewer, int rows, Component title) {
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("a chest menu has 1-6 rows, not " + rows);
        }
        this.viewer = Objects.requireNonNull(viewer, "viewer");
        this.rows = rows;
        this.title = Objects.requireNonNull(title, "title");
    }

    @Override
    public final Inventory getInventory() {
        if (inventory == null) {
            inventory = Bukkit.createInventory(this, rows * 9, title);
        }
        return inventory;
    }

    public final UUID viewer() {
        return viewer;
    }

    public final Component title() {
        return title;
    }

    /** (Re)draws every slot. */
    public abstract void render();

    protected final void set(int slot, Button button) {
        Inventory inv = getInventory();
        if (slot < 0 || slot >= inv.getSize()) {
            throw new IllegalArgumentException("slot " + slot + " is outside a menu of " + inv.getSize() + " slots");
        }
        inv.setItem(slot, button == null ? null : button.icon());
        if (button != null && button.action() != null) {
            buttons.put(slot, button);
        } else {
            buttons.remove(slot);
        }
    }

    protected final void clear() {
        buttons.clear();
        getInventory().clear();
    }

    final Button button(int rawSlot) {
        return buttons.get(rawSlot);
    }

    /** Click on the player's own inventory (index 0-35) with LEFT/SHIFT_LEFT/RIGHT. Selection only. */
    public void onBottomClick(Player player, int index, ClickType click, ItemStack clicked) {
    }

    /** The menu was closed or replaced (any reason). */
    public void onClose(Player player) {
    }

    /** Checked by the danger sweep every few ticks; false closes the menu (machine gone, too far, spinning). */
    public boolean stillValid(Player player) {
        return true;
    }

    /** True for menus that close after {@code gui.idle-close-seconds} (machine menus release their machine). */
    public boolean closesWhenIdle() {
        return false;
    }

    public final boolean isBusy() {
        return busy;
    }

    public final void setBusy(boolean busy) {
        this.busy = busy;
    }

    public final long openedAtTick() {
        return openedAtTick;
    }

    final void markOpened(long tick) {
        this.openedAtTick = tick;
    }

    public final boolean isOpenFor(Player player) {
        return player.getOpenInventory().getTopInventory().getHolder(false) == this;
    }
}
