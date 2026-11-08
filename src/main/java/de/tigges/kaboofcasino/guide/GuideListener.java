package de.tigges.kaboofcasino.guide;

import de.tigges.kaboofcasino.machine.CasinoItem;
import de.tigges.kaboofcasino.machine.CasinoItems;
import de.tigges.kaboofcasino.machine.MachineListener;
import de.tigges.kaboofcasino.util.Log;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import java.util.Optional;
import org.bukkit.block.Block;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Events of the guide book. Using the guide (either hand) opens it, as the mod's client does on {@code use}: in the
 * air, or on a block whose own use does not come first, i.e. not on a machine or a casino decoration (their listeners
 * run at HIGH and deny the block: game, seat, chip case ...) and not on a door, chest or work station unless the
 * player sneaks (vanilla: the block's use comes before the item's). Nothing is consumed. Clicks in the dialog arrive
 * as {@link PlayerCustomClickEvent} on the main thread; only {@link GuideAction#ID} with a valid payload counts.
 * Thread: MAIN.
 */
final class GuideListener implements Listener {

    private final GuideService service;

    GuideListener(GuideService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK
                || CasinoItems.of(event.getItem()).orElse(null) != CasinoItem.CASINO_GUIDE) {
            return;
        }
        Player player = event.getPlayer();
        if (action == Action.RIGHT_CLICK_BLOCK) {
            Block block = event.getClickedBlock();
            if (block == null || event.useInteractedBlock() == Event.Result.DENY
                    || !player.isSneaking() && MachineListener.interactable(block)) {
                return;
            }
        } else if (event.useItemInHand() == Event.Result.DENY) {
            return;
        }
        event.setUseItemInHand(Event.Result.DENY);
        try {
            service.use(player);
        } catch (RuntimeException e) {
            Log.incident("opening the guide for " + player.getName(), e);
        }
    }

    @EventHandler
    public void onCustomClick(PlayerCustomClickEvent event) {
        if (!GuideAction.ID.equals(event.getIdentifier().asString())
                || !(event.getCommonConnection() instanceof PlayerGameConnection connection)) {
            return;
        }
        Player player = connection.getPlayer();
        DialogResponseView view = event.getDialogResponseView();
        Optional<GuideAction> action = view == null ? Optional.empty() : GuideAction.parse(view::getText,
                service.pages());
        if (action.isEmpty()) {
            Log.get().debug("KaboofCasino guide: invalid click payload from {}", player.getName());
            return;
        }
        try {
            service.click(player, action.get());
        } catch (RuntimeException e) {
            Log.incident("guide click " + action.get() + " of " + player.getName(), e);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            service.danger(player, "damage " + event.getCause().name());
        }
    }

    /** Any mob that targets the reader closes the guide (as MenuSafety does for menus). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getEntity() instanceof Mob && event.getTarget() instanceof Player player) {
            service.danger(player, "targeted (" + event.getReason() + ")");
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
