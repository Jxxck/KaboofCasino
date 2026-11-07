package de.tigges.kaboofcasino.deco.display;

import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.util.FakePlayers;
import de.tigges.kaboofcasino.util.Log;
import de.tigges.kaboofcasino.util.Pos;
import io.papermc.paper.event.player.PlayerPickBlockEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Comparator;
import org.bukkit.block.data.type.NoteBlock;
import org.bukkit.block.data.type.Repeater;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Switch;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Player and world events of the display decoration: placing (plugin carriers from paper items; stairs/slabs through
 * vanilla with the casino's rules on top), using (sit, chip case, stacking, speaker), breaking (barrier timer,
 * Interaction hits, creative, vanilla carriers with the deco item instead of the carrier's drop), explosions (vanilla
 * carriers are blown up by the plugin with the mod's {@code survives_explosion}/{@code explosion_decay} drop), creative
 * pick-block, chunk load/unload, and the neighbour checks every block change queues. Thread: MAIN.
 */
final class DecoListener implements Listener {

    private final DisplayDecoService service;
    private final Random random = new Random();
    /** Vanilla carriers being broken: drop decision taken at HIGH (before our setDropItems), used at MONITOR. */
    private final Map<Pos, Boolean> breaking = new HashMap<>();
    /** Vanilla carriers an explosion takes (removed from its block list at HIGH, destroyed at MONITOR). */
    private final Map<Pos, Float> exploding = new HashMap<>();

    DecoListener(DisplayDecoService service) {
        this.service = service;
    }

    private Messages messages() {
        return service.ctx().messages().get();
    }

    // ---- right clicks ----------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        Optional<DecoBlock> deco = service.at(block);
        ItemStack item = event.getItem();
        Optional<DecoType> held = DecoItems.of(item);
        if (deco.isPresent() && deco.get().type().pluginCarrier()) {
            event.setUseInteractedBlock(Event.Result.DENY);
            if (event.getHand() != EquipmentSlot.HAND || FakePlayers.isFake(player)) {
                event.setUseItemInHand(Event.Result.DENY);
                return;
            }
            try {
                if (use(player, deco.get(), item, held)) {
                    event.setUseItemInHand(Event.Result.DENY);
                    return;
                }
            } catch (RuntimeException e) {
                String code = Log.incident("decoration use at " + deco.get().anchor(), e);
                messages().send(player, Msg.ERROR_INTERNAL, Messages.text("code", code));
                event.setUseItemInHand(Event.Result.DENY);
                return;
            }
        }
        if (held.isEmpty() || held.get().vanillaCarrier()) {
            return; // vanilla places stairs and slabs; onPlace adds the casino rules
        }
        event.setUseItemInHand(Event.Result.DENY);
        if (FakePlayers.isFake(player) || player.getGameMode() == GameMode.SPECTATOR
                || player.getGameMode() == GameMode.ADVENTURE) {
            return;
        }
        if (deco.isEmpty() && !player.isSneaking() && interactable(block)) {
            event.setUseItemInHand(Event.Result.DEFAULT);
            return; // doors, chests ... work as usual (the block's use comes before the item's)
        }
        event.setUseInteractedBlock(Event.Result.DENY);
        if (!player.hasPermission("kaboofcasino.use")) {
            messages().actionBar(player, Msg.ERROR_NO_PERMISSION);
            return;
        }
        DecoPlacer.Plan plan = service.placer().plan(player, held.get(), block, event.getBlockFace());
        if (plan == null) {
            return; // the mod would not place it here: nothing happens, as in the mod
        }
        DecoPlacer.Result result = service.placer().place(player, plan, block, event.getHand(), item);
        if (result == DecoPlacer.Result.PLACED) {
            if (player.getGameMode() != GameMode.CREATIVE) {
                ItemStack rest = item.clone();
                rest.setAmount(item.getAmount() - 1);
                player.getInventory().setItem(event.getHand(), rest.getAmount() <= 0 ? null : rest);
            }
        } else {
            service.placer().tell(player, result, messages());
        }
    }

    /**
     * The mod's {@code useWithoutItem} (only with the main hand and unless the player sneaks holding something) and
     * the stacked decor's {@code canBeReplaced} (the same item, not sneaking, fewer than four). Returns true if
     * handled (the item is then not used).
     */
    private boolean use(Player player, DecoBlock deco, ItemStack item, Optional<DecoType> held) {
        boolean sneaking = player.isSneaking();
        boolean hands = item != null && !item.isEmpty() || !player.getInventory().getItemInOffHand().isEmpty();
        DecoType type = deco.type();
        World world = DisplayDecoService.world(deco.anchor());
        if (type.place() == DecoType.Place.STACKED && held.isPresent() && held.get() == type && !sneaking
                && deco.record().count() < 4 && player.getGameMode() != GameMode.SPECTATOR
                && player.getGameMode() != GameMode.ADVENTURE) {
            service.update(deco, deco.record().withState(deco.record().count() + 1), world);
            DecoPlacer.placeSound(type, world, deco.anchor());
            if (player.getGameMode() != GameMode.CREATIVE) {
                ItemStack rest = item.clone();
                rest.setAmount(item.getAmount() - 1);
                player.getInventory().setItemInMainHand(rest.getAmount() <= 0 ? null : rest);
            }
            return true;
        }
        if (sneaking && hands) {
            return false; // secondary use: the held item is used (placed against the block)
        }
        if (type.seat()) {
            return service.seats().sit(player, deco);
        }
        if (type == DecoType.CHIP_CASE && player.getGameMode() != GameMode.SPECTATOR) {
            boolean opening = !deco.record().open();
            service.update(deco, deco.record().withState(opening ? 1 : 0), world);
            world.playSound(center(deco.anchor(), world), opening ? Sound.BLOCK_CHEST_OPEN : Sound.BLOCK_CHEST_CLOSE,
                    SoundCategory.BLOCKS, 0.55F, 1.2F);
            return true;
        }
        return false;
    }

    /** Right click on a light carrier's Interaction: the speaker toggles ({@code SpeakerBlock.useWithoutItem}). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Interaction interaction)) {
            return;
        }
        Optional<DecoBlock> deco = service.owner(interaction);
        if (deco.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        // 26.3 sends one interact packet per click; Paper fires it as PlayerInteractAtEntityEvent (handled here)
        if (event.getHand() != EquipmentSlot.HAND || FakePlayers.isFake(player)
                || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        boolean hands = !player.getInventory().getItemInMainHand().isEmpty()
                || !player.getInventory().getItemInOffHand().isEmpty();
        if (deco.get().type() == DecoType.CASINO_SPEAKER && !(player.isSneaking() && hands)) {
            toggleSpeaker(deco.get());
        }
    }

    /** {@code SpeakerBlock.useWithoutItem}: station, block state (light 2 when on), lever click 0.22 / 1.1 or 0.8. */
    void toggleSpeaker(DecoBlock deco) {
        World world = DisplayDecoService.world(deco.anchor());
        DecoRecord r = deco.record();
        boolean on = !r.on();
        if (on) {
            service.music().enable(r.anchor(), r.facing());
        } else {
            service.music().disable(r.anchor());
        }
        service.update(deco, r.withState(DecoRecord.speakerState(on, r.face())), world);
        world.playSound(center(r.anchor(), world), Sound.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS, 0.22F,
                on ? 1.1F : 0.8F);
    }

    /** Blocks whose right click does something in vanilla (as {@code MachineListener.interactable}). */
    static boolean interactable(Block block) {
        BlockData data = block.getBlockData();
        if (data instanceof Openable || data instanceof Switch || data instanceof Bed || data instanceof Repeater
                || data instanceof Comparator || data instanceof NoteBlock) {
            return true;
        }
        if (block.getState(false) instanceof InventoryHolder) {
            return true;
        }
        return switch (block.getType()) {
            case CRAFTING_TABLE, ENCHANTING_TABLE, ANVIL, CHIPPED_ANVIL, DAMAGED_ANVIL, GRINDSTONE, STONECUTTER, LOOM,
                 CARTOGRAPHY_TABLE, SMITHING_TABLE, LECTERN, BELL, ENDER_CHEST, BEACON, RESPAWN_ANCHOR, CAKE,
                 DAYLIGHT_DETECTOR, COMMAND_BLOCK, CHAIN_COMMAND_BLOCK, REPEATING_COMMAND_BLOCK -> true;
            default -> false;
        };
    }

    // ---- breaking --------------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(BlockDamageEvent event) {
        Optional<DecoBlock> deco = service.at(event.getBlock());
        if (deco.isEmpty() || !deco.get().type().pluginCarrier()) {
            return;
        }
        Player player = event.getPlayer();
        if (FakePlayers.isFake(player) || player.getGameMode() == GameMode.ADVENTURE
                || player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        service.breaks().start(player, deco.get(), Pos.of(event.getBlock()));
    }

    @EventHandler
    public void onAbort(BlockDamageAbortEvent event) {
        service.breaks().stop(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        service.breaks().stop(event.getPlayer());
    }

    /** A left click on a light carrier's Interaction breaks it (like an item frame). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onAttack(PrePlayerAttackEntityEvent event) {
        Optional<DecoBlock> deco = service.owner(event.getAttacked());
        if (deco.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (FakePlayers.isFake(player) || player.getGameMode() == GameMode.ADVENTURE
                || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        service.breaks().breakNow(player, deco.get());
    }

    /** Interactions take no damage (defence in depth; the attack is cancelled before). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Interaction && service.owner(event.getEntity()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Optional<DecoBlock> deco = service.at(block);
        if (deco.isEmpty()) {
            return;
        }
        Player player = event.getPlayer();
        if (deco.get().type().pluginCarrier()) {
            // creative players break barriers (and light blocks they target with a light item) directly
            event.setCancelled(true);
            if (player.getGameMode() == GameMode.CREATIVE && !FakePlayers.isFake(player)) {
                service.breaks().breakNow(player, deco.get());
            }
            return;
        }
        if (event.isCancelled()) {
            return;
        }
        boolean drops = event.isDropItems() && player.getGameMode() != GameMode.CREATIVE
                && !block.getDrops(player.getInventory().getItemInMainHand(), player).isEmpty();
        event.setDropItems(false);
        breaking.clear(); // one break at a time; a later veto leaves no stale entry behind
        breaking.put(Pos.of(block), drops);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBroken(BlockBreakEvent event) {
        Block block = event.getBlock();
        Pos pos = Pos.of(block);
        boolean drops = Boolean.TRUE.equals(breaking.remove(pos));
        Optional<DecoBlock> deco = service.at(pos);
        if (deco.isPresent() && deco.get().type().vanillaCarrier()) {
            World w = block.getWorld();
            service.remove(deco.get(), "broken by " + event.getPlayer().getName(),
                    drops ? DisplayDecoService.Drop.TO_PLAYER : DisplayDecoService.Drop.NONE,
                    DisplayDecoService.dropCount(deco.get(), w), event.getPlayer(), true);
        }
        service.checkAround(pos);
    }

    // ---- placing through vanilla (stairs, slabs) and placements into decoration cells ----------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block placed = event.getBlockPlaced();
        Pos pos = Pos.of(placed);
        if (event instanceof org.bukkit.event.block.BlockMultiPlaceEvent multi) {
            // beds, doors, tall plants: their second half may land in a light carrier (replaceable)
            for (org.bukkit.block.BlockState state : multi.getReplacedBlockStates()) {
                if (service.isCell(Pos.of(state.getLocation()))) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
        Optional<DecoBlock> existing = service.at(pos);
        Optional<DecoType> held = DecoItems.of(event.getItemInHand());
        boolean vanillaDeco = held.isPresent() && held.get().vanillaCarrier();
        if (existing.isPresent()) {
            // only the mod's slab doubling: the same slab onto its single half
            boolean doubling = vanillaDeco && existing.get().type() == held.get()
                    && held.get().carrier() == DecoType.Carrier.SLAB
                    && event.getBlockReplacedState().getBlockData() instanceof Slab before
                    && before.getType() != Slab.Type.DOUBLE
                    && placed.getBlockData() instanceof Slab after && after.getType() == Slab.Type.DOUBLE;
            if (!doubling) {
                event.setCancelled(true);
            }
            return;
        }
        if (vanillaDeco && service.ctx().config().get().machines().refuseLogoutSpots()
                && service.ctx().machines().logouts().blocks(pos)) {
            event.setCancelled(true);
            messages().actionBar(event.getPlayer(), Msg.DECO_LOGOUT_SPOT);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlaced(BlockPlaceEvent event) {
        Block placed = event.getBlockPlaced();
        Pos pos = Pos.of(placed);
        Optional<DecoType> held = DecoItems.of(event.getItemInHand());
        if (held.isPresent() && held.get().vanillaCarrier() && placed.getType() == held.get().vanilla()) {
            Optional<DecoBlock> existing = service.at(pos);
            if (existing.isEmpty()) {
                DecoRecord record = new DecoRecord(java.util.UUID.randomUUID(), held.get(), pos,
                        de.tigges.kaboofcasino.machine.Facing.SOUTH, 0, event.getPlayer().getUniqueId(),
                        System.currentTimeMillis());
                service.add(record, placed.getWorld());
            }
        }
        service.checkAround(pos);
    }

    // ---- explosions ------------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        exploding.clear();
        take(event.blockList(), event.getYield());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        exploding.clear();
        take(event.blockList(), event.getYield());
    }

    private void take(List<Block> blocks, float yield) {
        blocks.removeIf(b -> {
            Optional<DecoBlock> d = service.at(b);
            if (d.isEmpty()) {
                return false;
            }
            if (d.get().type().vanillaCarrier()) {
                exploding.put(Pos.of(b), yield);
            }
            return true;
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExploded(EntityExplodeEvent event) {
        blowUp(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExploded(BlockExplodeEvent event) {
        blowUp(event.blockList());
    }

    /**
     * Destroys the vanilla carriers the explosion would have taken: the carrier goes (air, or water if waterlogged),
     * and the deco item drops like the mod's loot: stairs with {@code survives_explosion} (probability = yield), slabs
     * with {@code explosion_decay} (each item on its own).
     */
    private void blowUp(List<Block> rest) {
        for (Map.Entry<Pos, Float> e : Map.copyOf(exploding).entrySet()) {
            Optional<DecoBlock> d = service.at(e.getKey());
            if (d.isEmpty()) {
                continue;
            }
            World w = DisplayDecoService.world(e.getKey());
            float yield = e.getValue();
            int count = DisplayDecoService.dropCount(d.get(), w);
            int dropped = 0;
            if (d.get().type().carrier() == DecoType.Carrier.SLAB) {
                for (int i = 0; i < count; i++) {
                    if (random.nextFloat() < yield) {
                        dropped++;
                    }
                }
            } else if (random.nextFloat() < yield) {
                dropped = 1;
            }
            service.remove(d.get(), "explosion", DisplayDecoService.Drop.AT_BLOCK, dropped, null, false);
        }
        exploding.clear();
        for (Block b : rest) {
            service.checkAround(Pos.of(b));
        }
    }

    // ---- creative pick-block -----------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPick(PlayerPickBlockEvent event) {
        Optional<DecoBlock> deco = service.at(event.getBlock());
        if (deco.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) {
            int slot = event.getTargetSlot();
            player.getInventory().setItem(slot, DecoItems.create(deco.get().type(), 1));
            player.getInventory().setHeldItemSlot(slot);
        }
    }

    // ---- chunks ----------------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        service.chunkLoaded(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        service.chunkUnloaded(event.getChunk());
    }

    static Location center(Pos p, World w) {
        return new Location(w, p.x() + 0.5, p.y() + 0.5, p.z() + 0.5);
    }
}
