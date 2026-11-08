package de.tigges.kaboofcasino.croupier;

import io.papermc.paper.event.player.PlayerPurchaseEvent;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

/**
 * Croupier events: chunk lifecycle, clicks on a croupier's click box, trades, closed trade screens, quits. Thread:
 * MAIN.
 */
final class CroupierListener implements Listener {

    private final CroupierService service;

    CroupierListener(CroupierService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        service.chunkLoaded(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        service.chunkUnloaded(event.getChunk());
    }

    /** 26.3 sends one interact packet per click; Paper fires it as {@code PlayerInteractAtEntityEvent} (seen here). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof Interaction interaction
                && service.click(event.getPlayer(), interaction, event.getHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPurchase(PlayerPurchaseEvent event) {
        service.purchased(event.getPlayer(), event.getMerchant(), event.willIncreaseTradeUses());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            service.closed(player, event.getView());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        service.quit(event.getPlayer());
    }
}
