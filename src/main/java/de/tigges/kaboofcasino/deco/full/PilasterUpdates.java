package de.tigges.kaboofcasino.deco.full;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.plugin.Plugin;

/**
 * The mod recomputes a pilaster on every horizontal neighbour change ({@code updateShape}); with mushroom updates off
 * vanilla does nothing, so the plugin does it for the changes it sees (place, break, explosions, pistons, burning,
 * entities changing blocks). Positions are collected and refreshed once on the next tick (a neighbour that is being
 * broken is gone by then). A pilaster that misses an update (e.g. {@code /setblock}) is only out of date in its
 * looks and corrects itself on the next neighbour change. Thread: MAIN.
 */
final class PilasterUpdates {

    private static final BlockFace[] HORIZONTAL = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    private final Plugin plugin;
    private final DecoWorld world;
    private final Set<Block> dirty = new LinkedHashSet<>();
    private boolean scheduled;

    PilasterUpdates(Plugin plugin, DecoWorld world) {
        this.plugin = plugin;
        this.world = world;
    }

    /** A block changed: its pilaster neighbours (and the block itself, if a pilaster arrived there) need a look. */
    void changed(Block block) {
        dirty.add(block);
        for (BlockFace f : HORIZONTAL) {
            dirty.add(block.getRelative(f));
        }
        if (!scheduled && plugin.isEnabled()) {
            scheduled = true;
            Bukkit.getScheduler().runTask(plugin, this::flush);
        }
    }

    /** Like {@link #changed} but {@code ticks} later (pistons finish moving after 2-3 ticks). */
    void changedLater(List<Block> blocks, long ticks) {
        if (!plugin.isEnabled() || blocks.isEmpty()) {
            return;
        }
        List<Block> copy = List.copyOf(blocks);
        Bukkit.getScheduler().runTaskLater(plugin, () -> copy.forEach(this::changed), ticks);
    }

    void flush() {
        scheduled = false;
        List<Block> todo = new ArrayList<>(dirty);
        dirty.clear();
        for (Block b : todo) {
            if (b.getWorld().isChunkLoaded(b.getX() >> 4, b.getZ() >> 4)) {
                world.refresh(b);
            }
        }
    }

    void clear() {
        dirty.clear();
        scheduled = false;
    }
}
