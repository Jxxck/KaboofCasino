package de.tigges.kaboofcasino.admin;

import de.tigges.kaboofcasino.machine.Machine;
import de.tigges.kaboofcasino.machine.MachineListener;
import de.tigges.kaboofcasino.machine.MachineService;
import de.tigges.kaboofcasino.util.FakePlayers;
import de.tigges.kaboofcasino.util.Log;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import java.util.Optional;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Events of the croupier's hand. A right click with it (the event's hand holds it):
 * <ul>
 *   <li>on a machine, main hand only (the mod: the machine block's {@code useItemOn} with the hand; with the hand in
 *       the off hand the main hand's click opens the game first, as in the mod): {@link AdminService#useOnMachine}.
 *       {@link MachineListener} (HIGH) has already denied the block and skipped the game for the hand;</li>
 *   <li>on another block whose own use does not come first (not denied by a casino listener, not a door, chest or
 *       work station unless sneaking; vanilla: the block's use comes before the item's):
 *       {@link AdminService#useOnBlock};</li>
 *   <li>in the air: {@link AdminService#useInAir}.</li>
 * </ul>
 * Dialog clicks arrive as {@link PlayerCustomClickEvent} on the main thread; only {@link AdminClick#ID} counts.
 * Thread: MAIN.
 */
final class HandListener implements Listener {

    private final AdminService service;
    private final MachineService machines;

    HandListener(AdminService service, MachineService machines) {
        this.service = service;
        this.machines = machines;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK
                || !AdminService.isHand(event.getItem())) {
            return;
        }
        Player player = event.getPlayer();
        if (FakePlayers.isFake(player) || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        try {
            if (action == Action.RIGHT_CLICK_BLOCK) {
                Block block = event.getClickedBlock();
                Optional<Machine> machine = block == null ? Optional.empty() : machines.at(block);
                if (machine.isPresent()) {
                    event.setUseInteractedBlock(Event.Result.DENY);
                    event.setUseItemInHand(Event.Result.DENY);
                    if (event.getHand() == EquipmentSlot.HAND) {
                        service.useOnMachine(player, machine.get(), player.isSneaking());
                    }
                    return;
                }
                if (block == null || event.useInteractedBlock() == Event.Result.DENY
                        || !player.isSneaking() && MachineListener.interactable(block)) {
                    return;
                }
                event.setUseItemInHand(Event.Result.DENY);
                service.useOnBlock(player, player.isSneaking());
            } else {
                if (event.useItemInHand() == Event.Result.DENY) {
                    return;
                }
                event.setUseItemInHand(Event.Result.DENY);
                service.useInAir(player, player.isSneaking());
            }
        } catch (RuntimeException e) {
            Log.incident("croupier's hand use by " + player.getName(), e);
        }
    }

    @EventHandler
    public void onCustomClick(PlayerCustomClickEvent event) {
        if (!AdminClick.ID.equals(event.getIdentifier().asString())
                || !(event.getCommonConnection() instanceof PlayerGameConnection connection)) {
            return;
        }
        Player player = connection.getPlayer();
        try {
            service.click(player, event.getDialogResponseView());
        } catch (RuntimeException e) {
            Log.incident("croupier's hand click of " + player.getName(), e);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        service.forget(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        service.forget(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        service.quit(event.getPlayer());
    }
}
