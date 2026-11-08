package de.tigges.kaboofcasino.gui;

import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.util.Log;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;

/**
 * The one listener for all casino menus (PostMarkt {@code MenuListener}): every click and drag in a casino view is
 * cancelled first, top and bottom; then buttons and bottom selections are routed by {@link ClickRoute}. Unusable
 * players never get a menu; switching to creative/spectator closes it; the close runs the display-copy safety net.
 * Thread: MAIN.
 */
public final class MenuListener implements Listener {

    private final MenuService menus;
    private final IntSupplier currentTick;
    private final IntSupplier guardTicks;
    private final Supplier<Messages> messages;

    public MenuListener(MenuService menus, IntSupplier currentTick, IntSupplier guardTicks,
                        Supplier<Messages> messages) {
        this.menus = menus;
        this.currentTick = currentTick;
        this.guardTicks = guardTicks;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Menu menu)) {
            return;
        }
        event.setCancelled(true); // ALWAYS and first: no vanilla item movement in a casino view
        if (!(event.getWhoClicked() instanceof Player player) || !menu.viewer().equals(player.getUniqueId())) {
            return;
        }
        try {
            route(event, menu, player);
        } catch (RuntimeException e) {
            String code = Log.incident("click in " + menu.getClass().getSimpleName(), e);
            messages.get().send(player, Msg.ERROR_INTERNAL, Messages.text("code", code));
        }
    }

    private void route(InventoryClickEvent event, Menu menu, Player player) {
        int raw = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();
        Button button = raw >= 0 && raw < topSize ? menu.button(raw) : null;
        boolean shortcut = false;
        if (raw >= 0 && raw < topSize
                && (event.getClick() == ClickType.NUMBER_KEY || event.getClick() == ClickType.SWAP_OFFHAND)) {
            // keyboard shortcuts of the round menus (PokerScreen/BlackjackScreen.keyPressed); the swap stays cancelled
            button = menu.keyButton(event.getClick(), event.getHotbarButton());
            shortcut = button != null && button.action() != null;
            if (!shortcut) {
                button = null;
            }
        }
        if (button != null && button.commit() && button.shift()
                && (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT)) {
            shortcut = true; // a commit that takes Shift (the Chip-Shop's stack): guard and busy still apply
        }
        long tick = currentTick.getAsInt();
        ClickRoute.Click click = new ClickRoute.Click(event.getClick(), event.getAction(), raw, topSize,
                event.getSlot(), button != null, button != null && button.commit(), Usability.usable(player),
                menu.isBusy(), menu.openedAtTick() < 0 ? -1 : tick - menu.openedAtTick(), guardTicks.getAsInt(),
                shortcut);
        switch (ClickRoute.route(click)) {
            case NOTHING, TOO_EARLY, BUSY -> {
                // stays cancelled
            }
            case BUTTON -> {
                menus.beginInteraction(player);
                button.action().click(player, event.getClick());
            }
            case COMMIT -> {
                menus.beginInteraction(player);
                menu.setBusy(true);
                try {
                    button.action().click(player, event.getClick());
                } finally {
                    menu.setBusy(false);
                }
            }
            case SELECTION -> {
                menus.beginInteraction(player);
                menu.onBottomClick(player, event.getSlot(), event.getClick(), event.getCurrentItem());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Menu) {
            event.setCancelled(true);
        }
    }

    /** Only opens started by {@link MenuService#open} (interaction tick, usable, safe) are allowed. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof Menu menu)) {
            return;
        }
        try {
            if (!(event.getPlayer() instanceof Player player) || !menu.viewer().equals(player.getUniqueId())
                    || !Usability.usable(player) || !menus.isOpening(menu)) {
                event.setCancelled(true);
            }
        } catch (RuntimeException e) {
            event.setCancelled(true);
            Log.incident("open check of " + menu.getClass().getSimpleName(), e);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof Menu menu)
                || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        try {
            menu.onClose(player);
        } catch (RuntimeException e) {
            Log.incident("close of " + menu.getClass().getSimpleName(), e);
        }
        try {
            DisplayPurge.purge(player, event.getView());
        } catch (RuntimeException e) {
            Log.incident("close safety net for " + player.getName(), e);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        GameMode mode = event.getNewGameMode();
        if ((mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR)
                && MenuService.openMenu(event.getPlayer()) != null) {
            event.getPlayer().closeInventory(InventoryCloseEvent.Reason.CANT_USE);
        }
    }
}
