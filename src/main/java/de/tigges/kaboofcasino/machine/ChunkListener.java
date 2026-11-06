package de.tigges.kaboofcasino.machine;

import de.tigges.kaboofcasino.util.Log;
import de.tigges.kaboofcasino.visual.Displays;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;

/**
 * Chunk lifecycle of machines (server.md 3.5): load -> register from the chunk PDC (O(1) for chunks without the key),
 * rig spawn on the next ticks; unload -> drop runtime state (rig entities are non-persistent and vanish with the
 * chunk). Orphan rule: an entity with {@code kaboofcasino:part} loaded from disk can only come from a bug; it is
 * removed. Thread: MAIN.
 */
public final class ChunkListener implements Listener {

    private final MachineService machines;

    public ChunkListener(MachineService machines) {
        this.machines = machines;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLoad(ChunkLoadEvent event) {
        machines.chunkLoaded(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onUnload(ChunkUnloadEvent event) {
        machines.chunkUnloaded(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntities(EntitiesLoadEvent event) {
        for (Entity e : event.getEntities()) {
            if (Displays.isPart(e)) {
                Log.get().warn("removed an orphaned casino rig entity {} at {}", e.getType().getKey(),
                        e.getLocation().toVector());
                e.remove();
            }
        }
    }
}
