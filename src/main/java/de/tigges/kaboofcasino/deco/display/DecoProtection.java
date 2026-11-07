package de.tigges.kaboofcasino.deco.display;

import de.tigges.kaboofcasino.machine.ProtectionRules;
import de.tigges.kaboofcasino.util.Pos;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.world.PortalCreateEvent;

/**
 * Protection of decoration cells (server.md 3.3/3.4). Barrier and light carriers resist explosions, the wither and the
 * dragon by themselves; light carriers are replaceable, so everything that could replace them is refused here.
 * <ul>
 *   <li>pistons: refused when they would move a decoration, push a block into one, or stand next to one (the
 *       headless and permanent-break exploits arrive with an empty block list; same rule as the machines) - the mod
 *       lets pistons move its stairs and slabs and pops its small decor; here nothing moves or pops, so no display can
 *       stay behind and nothing doubles;</li>
 *   <li>fluids: none flows into a plugin carrier; buckets into a light or barrier carrier only with water for the
 *       mod's waterloggable blocks (sconce, stacked decor); stairs and slabs waterlog like vanilla (= the mod);</li>
 *   <li>fire, endermen, ravagers, withers, falling blocks, portals: refused on every decoration cell.</li>
 * </ul>
 * What arrives without an event ({@code /fill}, structures, exploits) is caught by the verifier. Thread: MAIN.
 */
final class DecoProtection implements Listener {

    private final DisplayDecoService service;

    DecoProtection(DisplayDecoService service) {
        this.service = service;
    }

    private boolean cell(Block b) {
        return service.isCell(Pos.of(b));
    }

    private boolean pluginCell(Block b) {
        Optional<DecoBlock> d = service.at(b);
        return d.isPresent() && d.get().type().pluginCarrier();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (pistonTouches(event, event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (pistonTouches(event, event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    /** Moved blocks, their destinations, the head cell and the piston's six neighbours. */
    private boolean pistonTouches(BlockPistonEvent event, List<Block> moved) {
        BlockFace dir = event.getDirection();
        List<Pos> touched = new ArrayList<>();
        for (Block b : moved) {
            touched.add(Pos.of(b));
            touched.add(Pos.of(b.getRelative(dir)));
        }
        touched.add(Pos.of(event.getBlock().getRelative(dir)));
        return ProtectionRules.pistonMustCancel(Pos.of(event.getBlock()), touched, service::isCell);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (pluginCell(event.getToBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent event) {
        Optional<DecoBlock> d = service.at(event.getBlock());
        if (d.isEmpty() || d.get().type().vanillaCarrier()) {
            return;
        }
        boolean water = event.getBucket() == Material.WATER_BUCKET;
        if (!water || !d.get().type().waterloggable()) {
            event.setCancelled(true);
        } else {
            service.checkAround(d.get().anchor()); // the sconce goes dark when waterlogged
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (pluginCell(event.getBlock())) {
            service.checkAround(Pos.of(event.getBlock())); // the sconce lights up again when drained
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        if (!(event.getBlock().getBlockData() instanceof Directional d)) {
            return;
        }
        String type = event.getItem().getType().name();
        if (type.endsWith("_BUCKET") && cell(event.getBlock().getRelative(d.getFacing()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (cell(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (cell(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent event) {
        if (cell(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(PortalCreateEvent event) {
        for (org.bukkit.block.BlockState state : event.getBlocks()) {
            if (service.isCell(Pos.of(state.getLocation()))) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
