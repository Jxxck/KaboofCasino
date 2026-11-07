package de.tigges.kaboofcasino.seat;

import org.bukkit.Location;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * The events of sitting: every dismount from a casino seat is steered to the safe spot (vanilla computes its own
 * dismount location after {@code EntityDismountEvent} and moves the player with a {@code DISMOUNT} teleport, which is
 * redirected here); damage, a targeting mob, death and quit stand the sitter up at once (quit before the player file
 * is written, death before the drops and the grave are placed). Thread: MAIN.
 */
final class SeatListener implements Listener {

    private final SeatService seats;

    SeatListener(SeatService seats) {
        this.seats = seats;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player player && seats.isSeat(event.getDismounted())) {
            seats.dismounted(player, event.getDismounted());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDismountTeleport(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.DISMOUNT) {
            return;
        }
        Location to = seats.landingOf(event.getPlayer());
        if (to != null) {
            event.setTo(to);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && seats.seated(player)) {
            seats.stand(player, "damage " + event.getCause().name());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getEntity() instanceof Mob && event.getTarget() instanceof Player player && seats.seated(player)) {
            seats.stand(player, "targeted by " + event.getEntity().getType().getKey().getKey());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent event) {
        if (seats.seated(event.getPlayer())) {
            seats.stand(event.getPlayer(), "death");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        if (seats.seated(event.getPlayer())) {
            seats.stand(event.getPlayer(), "quit");
        }
    }
}
