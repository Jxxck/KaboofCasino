package de.tigges.kaboofcasino.machine;

import de.tigges.kaboofcasino.util.FakePlayers;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;

/**
 * Keeps players out of machine carriers (permadeath, safety 1): records where a player logs out (no machine is placed
 * there while he is offline), forgets it at his next join, moves him out of carriers at the join and one tick later
 * (the client's position settles), and on suffocation inside a carrier (ender pearls, teleports) cancels the damage
 * and moves him out. Bots are not recorded. Thread: MAIN.
 */
public final class SafetyListener implements Listener {

    private final MachineService machines;
    private final Plugin plugin;

    public SafetyListener(MachineService machines, Plugin plugin) {
        this.machines = machines;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        if (FakePlayers.isFake(p)) {
            return;
        }
        BoundingBox b = p.getBoundingBox();
        machines.logouts().record(p.getUniqueId(), LogoutSpots.Box.of(p.getWorld().getKey().asString(), b.getMinX(),
                b.getMinY(), b.getMinZ(), b.getMaxX(), b.getMaxY(), b.getMaxZ()), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        machines.logouts().forget(p.getUniqueId());
        machines.rescue(p, "join");
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) {
                machines.rescue(p, "join + 1 tick");
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSuffocate(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION && event.getEntity() instanceof Player p
                && machines.rescue(p, "suffocation")) {
            event.setCancelled(true);
        }
    }
}
