package de.tigges.kaboofcasino.machine;

import de.tigges.kaboofcasino.util.Pos;
import java.util.List;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.block.data.Directional;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;

/**
 * Protection of machine carriers (server.md 3.3, PostMarkt Postkasten rules): explosions (block list filtered),
 * pistons including the neighbour rule for the headless/permanent-break exploits, fluids flowing into or bucketed
 * into a carrier (also from dispensers), portals created over carriers, fire, and entities changing blocks (endermen,
 * ravagers, withers, falling blocks). Hoppers need
 * nothing: barriers have no inventory and machines hold no items. What arrives without an event (/fill, /setblock,
 * structure blocks, exploits) is caught by {@link MachineService#verify}. Thread: MAIN.
 */
public final class ProtectionListener implements Listener {

    private final MachineService machines;

    public ProtectionListener(MachineService machines) {
        this.machines = machines;
    }

    private boolean carrier(Block b) {
        return machines.isCarrier(Pos.of(b));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::carrier);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::carrier);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (ProtectionRules.pistonMustCancel(Pos.of(event.getBlock()), positions(event.getBlocks()),
                machines::isCarrier)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (ProtectionRules.pistonMustCancel(Pos.of(event.getBlock()), positions(event.getBlocks()),
                machines::isCarrier)) {
            event.setCancelled(true);
        }
    }

    private static List<Pos> positions(List<Block> blocks) {
        return blocks.stream().map(Pos::of).toList();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (carrier(event.getToBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent event) {
        if (carrier(event.getBlockClicked()) || carrier(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (carrier(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (carrier(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(PortalCreateEvent event) {
        for (org.bukkit.block.BlockState state : event.getBlocks()) {
            if (machines.isCarrier(Pos.of(state.getLocation()))) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /** Dispensers emptying buckets (water, lava, powder snow, fish) into a carrier. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        if (!(event.getBlock().getBlockData() instanceof Directional d)) {
            return;
        }
        String type = event.getItem().getType().name();
        if (type.endsWith("_BUCKET") && carrier(event.getBlock().getRelative(d.getFacing()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent event) {
        if (carrier(event.getBlock())) {
            event.setCancelled(true);
        }
    }
}
