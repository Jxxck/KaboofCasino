package de.tigges.kaboofcasino.gui;

import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.util.Log;
import de.tigges.kaboofcasino.util.Threads;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * The only way to open casino menus (PostMarkt {@code MenuService}): only in the player's interaction tick, only for
 * usable players, only when safe. Thread: MAIN.
 */
public final class MenuService implements Listener {

    private final IntSupplier currentTick;
    private final DangerSweep danger;
    private final Supplier<Messages> messages;
    private final Map<UUID, Long> interaction = new HashMap<>();
    /** The menu {@link #open} is opening right now; any other casino menu open is cancelled (MenuListener). */
    private Menu opening;

    public MenuService(IntSupplier currentTick, DangerSweep danger, Supplier<Messages> messages) {
        this.currentTick = currentTick;
        this.danger = danger;
        this.messages = messages;
    }

    public void beginInteraction(Player player) {
        Threads.requireMain();
        interaction.put(player.getUniqueId(), (long) currentTick.getAsInt());
    }

    /** Opens {@code menu} if allowed now; tells the player why not otherwise. */
    public boolean open(Player player, Menu menu) {
        Threads.requireMain();
        if (!menu.viewer().equals(player.getUniqueId())) {
            Log.get().warn("KaboofCasino refused to open {} for {}: it belongs to {}", menu.getClass().getSimpleName(),
                    player.getName(), menu.viewer());
            return false;
        }
        long tick = currentTick.getAsInt();
        OpenRules.Verdict verdict = OpenRules.check(interaction.getOrDefault(player.getUniqueId(), -1L), tick,
                Usability.usable(player), danger.safeToOpen(player));
        switch (verdict) {
            case NOT_INTERACTION_TICK -> {
                Log.get().warn("KaboofCasino refused to open {} for {} outside an interaction tick",
                        menu.getClass().getSimpleName(), player.getName());
                return false;
            }
            case UNUSABLE -> {
                refuse(player, Usability.creativeOrSpectator(player) ? Msg.ERROR_CREATIVE : Msg.ERROR_NOT_USABLE);
                return false;
            }
            case UNSAFE -> {
                refuse(player, Msg.ERROR_UNSAFE);
                return false;
            }
            case OPEN -> {
                // below
            }
        }
        menu.render();
        if (menu.isOpenFor(player)) {
            return true;
        }
        opening = menu;
        try {
            player.openInventory(menu.getInventory());
        } finally {
            opening = null;
        }
        if (!menu.isOpenFor(player)) {
            return false;
        }
        menu.markOpened(tick);
        return true;
    }

    /**
     * Replaces the menu the player has open by {@code menu}, a fresh copy of the same menu (new texts in its title):
     * not an opening, so no interaction tick is needed, but only if {@code same} accepts the open menu, the player can
     * use menus and is safe. A menu never appears by itself this way.
     */
    public boolean replace(Player player, Menu menu, java.util.function.Predicate<Menu> same) {
        Threads.requireMain();
        Menu current = openMenu(player);
        if (current == null || !menu.viewer().equals(player.getUniqueId()) || !same.test(current)
                || !Usability.usable(player) || !danger.safeToOpen(player)) {
            return false;
        }
        menu.render();
        opening = menu;
        try {
            player.openInventory(menu.getInventory());
        } finally {
            opening = null;
        }
        if (!menu.isOpenFor(player)) {
            return false;
        }
        menu.markOpened(currentTick.getAsInt());
        return true;
    }

    /** True if the player is safe right now (combat and environment); commits check it too (server.md 1.2). */
    public boolean safe(Player player) {
        return danger.safeToOpen(player);
    }

    private void refuse(Player player, Msg msg) {
        messages.get().actionBar(player, msg);
        player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
    }

    /** True while {@link #open} opens exactly this menu. */
    boolean isOpening(Menu menu) {
        return opening == menu;
    }

    /** The viewer's own open casino menu, or null. */
    public static Menu openMenu(Player player) {
        return player.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu menu
                && menu.viewer().equals(player.getUniqueId()) ? menu : null;
    }

    /** onDisable: close every casino menu (nothing to give back: selection model). */
    public void closeAll() {
        for (Player player : List.copyOf(Bukkit.getOnlinePlayers())) {
            if (openMenu(player) != null) {
                player.closeDialog();
                player.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
            }
        }
        interaction.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        interaction.remove(event.getPlayer().getUniqueId());
    }
}
