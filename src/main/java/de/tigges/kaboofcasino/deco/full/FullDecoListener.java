package de.tigges.kaboofcasino.deco.full;

import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.deco.full.FullDecoStates.Entry;
import io.papermc.paper.event.player.PlayerPickBlockEvent;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.bukkit.ExplosionResult;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * The full-block deco in the world (render.md 4.4, server.md 3.4). The state is the identity: a block is deco
 * exactly when its state is in {@link FullDecoStates}; nothing else is ever touched. Everything below depends on
 * Paper's {@code disable-mushroom-block-updates}, read live per event ({@link PaperSwitch}): while it is off nothing is
 * placed and nothing is converted - reserved states then place, break, explode and pick like vanilla mushroom blocks
 * (vanilla loot), so no deco item can be minted from blocks that neighbour updates may have rewritten.
 * <ul>
 *   <li>Placing: vanilla places the item's {@code block_state} (vanilla rules, spawn protection and
 *       {@code BlockPlaceEvent} apply). Refused while Paper's {@code disable-mushroom-block-updates} is off. Stars
 *       take the placer's horizontal facing, pilasters their connections; pilaster neighbours are refreshed.</li>
 *   <li>Breaking (survival): the mushroom loot (also silk touch / fortune) is replaced by one deco item in
 *       {@code BlockDropItemEvent}, which Paper fires only after the block was removed. Creative: nothing. Stone
 *       floors follow the mod's harvest rule (polished andesite: a pickaxe, checked with the tool the block was
 *       broken with), otherwise nothing drops. Digging time: {@link DecoDigging}.</li>
 *   <li>Explosions: deco blocks leave the vanilla block list and are removed here, dropping the deco item with the
 *       explosion's yield; {@code TRIGGER_BLOCK}/{@code KEEP} explosions leave them alone (like vanilla).</li>
 *   <li>Other destruction ({@code Level.destroyBlock}: withers, {@code /setblock ... destroy}): the deco item
 *       instead of the loot. Pistons move the state unchanged (updates off); fire and endermen cannot take mushroom
 *       blocks.</li>
 *   <li>Pick block: the deco item (hotbar, inventory, or a new one in creative). Composters do not take deco.</li>
 * </ul>
 * Thread: MAIN.
 */
final class FullDecoListener implements Listener {

    private record Taken(Block block, BlockData data, DecoBlock deco) {
    }

    private final DecoWorld world;
    private final PilasterUpdates updates;
    private final Supplier<Messages> messages;
    private final BooleanSupplier active;
    private final Map<Object, List<Taken>> explosions = new IdentityHashMap<>();
    private final Map<Object, DecoBlock> drops = new IdentityHashMap<>();
    /** The block of the running player break and whether its tool harvests it (BlockBreakEvent -> drop event). */
    private Block breaking;
    private boolean breakingHarvests;

    /**
     * @param active Paper's switch, read live ({@link FullDecoService#active()})
     */
    FullDecoListener(DecoWorld world, PilasterUpdates updates, Supplier<Messages> messages, BooleanSupplier active) {
        this.world = world;
        this.updates = updates;
        this.messages = messages;
        this.active = active;
    }

    // ---- placing -----------------------------------------------------------------------------------------------

    /** With mushroom updates on, neighbours would rewrite deco faces into other states: refuse. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlaceGuard(BlockPlaceEvent event) {
        if (DecoWorld.entry(event.getBlockPlaced()).isEmpty() && FullDecoItems.of(event.getItemInHand()).isEmpty()) {
            return;
        }
        if (active.getAsBoolean()) {
            return;
        }
        event.setCancelled(true);
        messages.get().actionBar(event.getPlayer(), Msg.DECO_SWITCH_OFF);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlaced(BlockPlaceEvent event) {
        if (!active.getAsBoolean()) {
            return;
        }
        Block block = event.getBlockPlaced();
        Optional<Entry> entry = DecoWorld.entry(block);
        if (entry.isPresent()) {
            switch (entry.get().block().kind()) {
                case STARS -> {
                    Entry wanted = FullDecoStates.stars(entry.get().block(),
                            DecoWorld.side(event.getPlayer().getFacing()));
                    if (!wanted.equals(entry.get())) {
                        block.setBlockData(world.blockData(wanted.state()), false);
                    }
                }
                case PILASTER -> world.refresh(block);
                case PLAIN -> {
                }
            }
        }
        if (event instanceof BlockMultiPlaceEvent multi) {
            multi.getReplacedBlockStates().forEach(s -> updates.changed(s.getBlock()));
        } else {
            updates.changed(block);
        }
    }

    // ---- breaking ----------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        breaking = null;
        Optional<Entry> entry = DecoWorld.entry(event.getBlock());
        if (entry.isPresent()) {
            // the tool before mineBlock (it may break on this block): the mod's playerDestroy uses that one
            breaking = event.getBlock();
            breakingHarvests = DecoDigging.harvests(entry.get().block(),
                    event.getPlayer().getInventory().getItemInMainHand());
        }
        if (active.getAsBoolean()) {
            updates.changed(event.getBlock());
        }
    }

    /**
     * Fired after {@code ServerPlayerGameMode.destroyBlock} removed the block (only if the break event kept its
     * drops). The block's old state says what it was; if it is still there, nothing was broken and nothing drops.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDropItems(BlockDropItemEvent event) {
        Block brokenBlock = breaking;
        boolean brokenHarvests = breakingHarvests;
        breaking = null;
        BlockData before = event.getBlockState().getBlockData();
        Optional<Entry> entry = DecoWorld.entry(before);
        if (entry.isEmpty() || !active.getAsBoolean()) {
            return; // vanilla loot
        }
        event.getItems().clear();
        if (event.getBlock().getBlockData().equals(before) || event.getPlayer().getGameMode() == GameMode.CREATIVE) {
            return; // not broken / creative: nothing
        }
        boolean harvests = event.getBlock().equals(brokenBlock) ? brokenHarvests
                : DecoDigging.harvests(entry.get().block(), event.getPlayer().getInventory().getItemInMainHand());
        if (harvests) {
            drops.put(event, entry.get().block());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDroppedItems(BlockDropItemEvent event) {
        DecoBlock deco = drops.remove(event);
        if (deco != null && !event.isCancelled()) {
            drop(event.getBlock(), deco);
        }
    }

    /** {@code Level.destroyBlock} (withers, {@code /setblock ... destroy}): the deco item instead of the loot. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDestroy(BlockDestroyEvent event) {
        if (!active.getAsBoolean()) {
            return;
        }
        Optional<Entry> entry = DecoWorld.entry(event.getBlock());
        if (entry.isPresent() && event.willDrop()) {
            event.setWillDrop(false);
            drops.put(event, entry.get().block());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDestroyed(BlockDestroyEvent event) {
        DecoBlock deco = drops.remove(event);
        if (event.isCancelled()) {
            return;
        }
        if (deco != null) {
            drop(event.getBlock(), deco);
        }
        if (active.getAsBoolean()) {
            updates.changed(event.getBlock());
        }
    }

    // ---- explosions --------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        take(event, event.blockList(), event.getExplosionResult());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityExploded(EntityExplodeEvent event) {
        finish(event, event.isCancelled(), event.getYield(), event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        take(event, event.blockList(), event.getExplosionResult());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockExploded(BlockExplodeEvent event) {
        finish(event, event.isCancelled(), event.getYield(), event.blockList());
    }

    /** Deco blocks leave the vanilla list (no mushroom loot); destroying explosions only. */
    private void take(Event event, List<Block> blocks, ExplosionResult result) {
        if (result != ExplosionResult.DESTROY && result != ExplosionResult.DESTROY_WITH_DECAY
                || !active.getAsBoolean()) {
            return;
        }
        List<Taken> taken = new ArrayList<>();
        blocks.removeIf(b -> {
            BlockData data = b.getBlockData();
            Optional<Entry> entry = DecoWorld.entry(data);
            entry.ifPresent(e -> taken.add(new Taken(b, data, e.block())));
            return entry.isPresent();
        });
        if (!taken.isEmpty()) {
            explosions.put(event, taken);
        }
    }

    private void finish(Event event, boolean cancelled, float yield, List<Block> destroyed) {
        List<Taken> taken = explosions.remove(event);
        if (cancelled || !active.getAsBoolean()) {
            return;
        }
        if (taken != null) {
            for (Taken t : taken) {
                if (!t.block().getBlockData().equals(t.data())) {
                    continue; // changed meanwhile: not ours to remove
                }
                t.block().setBlockData(Material.AIR.createBlockData(), true);
                if (ThreadLocalRandom.current().nextFloat() < yield) {
                    drop(t.block(), t.deco());
                }
                updates.changed(t.block());
            }
        }
        destroyed.forEach(updates::changed);
    }

    // ---- neighbour changes for pilasters -----------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        moved(event.getBlock(), event.getBlocks(), event.getDirection());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        moved(event.getBlock(), event.getBlocks(), event.getDirection());
    }

    private void moved(Block piston, List<Block> blocks, BlockFace direction) {
        if (!active.getAsBoolean()) {
            return;
        }
        List<Block> touched = new ArrayList<>();
        touched.add(piston.getRelative(direction));
        for (Block b : blocks) {
            touched.add(b);
            touched.add(b.getRelative(direction));
            touched.add(b.getRelative(direction.getOppositeFace()));
        }
        updates.changedLater(touched, 4);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (active.getAsBoolean()) {
            updates.changed(event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent event) {
        if (active.getAsBoolean()) {
            updates.changed(event.getBlock());
        }
    }

    // ---- pick block and composters -----------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPick(PlayerPickBlockEvent event) {
        Optional<Entry> entry = DecoWorld.entry(event.getBlock());
        if (entry.isEmpty() || !active.getAsBoolean()) {
            return;
        }
        event.setCancelled(true);
        int target = event.getTargetSlot();
        Player player = event.getPlayer();
        pick(player.getInventory(), entry.get().block(), target >= 0 && target < 9 ? target
                : player.getInventory().getHeldItemSlot(), player.getGameMode() == GameMode.CREATIVE);
    }

    /** Vanilla's pick: select it in the hotbar, swap it in from the inventory, or (creative) add one. */
    static void pick(PlayerInventory inv, DecoBlock deco, int target, boolean creative) {
        for (int i = 0; i < 9; i++) {
            if (FullDecoItems.of(inv.getItem(i)).orElse(null) == deco) {
                inv.setHeldItemSlot(i);
                return;
            }
        }
        for (int i = 9; i < 36; i++) {
            ItemStack found = inv.getItem(i);
            if (FullDecoItems.of(found).orElse(null) == deco) {
                inv.setItem(i, inv.getItem(target));
                inv.setItem(target, found);
                inv.setHeldItemSlot(target);
                return;
            }
        }
        if (creative) {
            ItemStack old = inv.getItem(target);
            if (old != null && !old.isEmpty()) {
                int free = inv.firstEmpty();
                if (free >= 0) {
                    inv.setItem(free, old);
                }
            }
            inv.setItem(target, FullDecoItems.create(deco, 1));
            inv.setHeldItemSlot(target);
        }
    }

    /** Mushroom blocks are compostable, the mod's blocks are not: the click places instead (vanilla for others). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onComposter(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null
                && event.getClickedBlock().getType() == Material.COMPOSTER
                && FullDecoItems.of(event.getItem()).isPresent()) {
            event.setUseInteractedBlock(Event.Result.DENY);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHopperToComposter(InventoryMoveItemEvent event) {
        if (event.getDestination().getType() == InventoryType.COMPOSTER
                && FullDecoItems.of(event.getItem()).isPresent()) {
            event.setCancelled(true);
        }
    }

    // ---- drops -------------------------------------------------------------------------------------------------

    private static void drop(Block block, DecoBlock deco) {
        if (!Boolean.TRUE.equals(block.getWorld().getGameRuleValue(GameRules.BLOCK_DROPS))) {
            return;
        }
        block.getWorld().dropItemNaturally(block.getLocation().toCenterLocation(), FullDecoItems.create(deco, 1));
    }

    void clear() {
        explosions.clear();
        drops.clear();
        breaking = null;
    }

    /** For tests: deco drops waiting for their MONITOR handler. */
    int pendingDrops() {
        return drops.size();
    }
}
