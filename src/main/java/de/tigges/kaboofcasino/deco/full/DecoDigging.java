package de.tigges.kaboofcasino.deco.full;

import de.tigges.kaboofcasino.deco.full.FullDecoStates.Entry;
import de.tigges.kaboofcasino.util.Keys;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;

/**
 * Digging a deco block takes the mod's time. The block is a mushroom block (hardness 0.2, axe, no tool needed); the
 * mod's block copies wool, polished andesite or dark-oak planks. Vanilla's per-tick progress is
 * {@code speed / hardness / (30, or 100 without the right tool)} with {@code speed} = the tool's speed for the block
 * (+ Efficiency where that is above 1), then Haste/Mining Fatigue, the {@code block_break_speed} attribute,
 * underwater and airborne factors. All but the first are the same for both blocks, so one multiplicative
 * {@code block_break_speed} modifier ({@link #factor}) makes vanilla digging of the mushroom block - on the server
 * and in the client's own prediction - take exactly as long as the mod's block.
 *
 * <p>The modifier ({@link #KEY}, {@code MULTIPLY_SCALAR_1}) is TRANSIENT (never saved with the player). It is set
 * while the player looks at a deco block within reach (one ray per tick for survival/adventure players, so the
 * client has the value before the click) and on the left click itself ({@code PlayerInteractEvent}, which Paper
 * fires before it computes the start progress, so the server never insta-mines a deco block); while a deco block
 * is being dug it stays even if the ray slips off. It is removed by key on join, quit, death, respawn, world and
 * game-mode change, held-slot and hand swaps (then re-applied for the new tool in the same tick), abort, break,
 * when the Paper switch is off and on plugin disable. Thread: MAIN.
 */
public final class DecoDigging implements Listener {

    /** {@code kaboofcasino:full_deco_dig}; the display deco's break timer ignores every {@code kaboofcasino} key. */
    public static final NamespacedKey KEY = Keys.key("full_deco_dig");

    /** {@code Blocks.RED_MUSHROOM_BLOCK / BROWN / MUSHROOM_STEM}: {@code strength(0.2F)}. */
    static final float MUSHROOM_HARDNESS = 0.2f;

    /** What touches the server: the attribute, the reach and the factor (tests replace it). */
    interface Access {
        /** Sets the transient modifier to {@code factor}. */
        void apply(Player player, double factor);

        /** Removes the modifier by key. */
        void clear(Player player);

        /** The player's block reach ({@code block_interaction_range}). */
        double reach(Player player);

        /** {@link DecoDigging#factor(DecoBlock, BlockData, ItemStack)} for the block. */
        double factor(DecoBlock deco, Block block, ItemStack tool);
    }

    /** The real server. */
    static final Access BUKKIT = new Access() {
        @Override
        public void apply(Player player, double factor) {
            DecoDigging.apply(player, factor);
        }

        @Override
        public void clear(Player player) {
            DecoDigging.clear(player);
        }

        @Override
        public double reach(Player player) {
            AttributeInstance reach = player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE);
            return reach == null ? 4.5 : reach.getValue();
        }

        @Override
        public double factor(DecoBlock deco, Block block, ItemStack tool) {
            return DecoDigging.factor(deco, block.getBlockData(), tool == null ? ItemStack.empty() : tool);
        }
    };

    private final BooleanSupplier active;
    private final Access access;
    private final Supplier<Collection<? extends Player>> players;
    private final Map<UUID, Block> digging = new HashMap<>();

    DecoDigging(BooleanSupplier active) {
        this(active, BUKKIT, Bukkit::getOnlinePlayers);
    }

    DecoDigging(BooleanSupplier active, Access access, Supplier<Collection<? extends Player>> players) {
        this.active = active;
        this.access = access;
        this.players = players;
    }

    /**
     * The {@code block_break_speed} multiplier that makes a mushroom block dig like the mod's block.
     *
     * @param modSpeed      the tool's speed on the mod's block (with Efficiency, {@code getDestroySpeed(tool, true)})
     * @param mushroomSpeed the tool's speed on the mushroom block (same)
     * @param harvest       the tool may harvest the mod's block (else vanilla divides by 100 instead of 30)
     */
    static double factor(float modSpeed, float mushroomSpeed, float modHardness, boolean harvest) {
        return (modSpeed / (double) mushroomSpeed) * (MUSHROOM_HARDNESS / (double) modHardness)
                * (harvest ? 1.0 : 30.0 / 100.0);
    }

    static Material material(DecoBlock.ModMaterial m) {
        return switch (m) {
            case WOOL -> Material.RED_WOOL;
            case STONE -> Material.POLISHED_ANDESITE;
            case PLANKS -> Material.DARK_OAK_PLANKS;
        };
    }

    /** The mod's harvest rule ({@code requiresCorrectToolForDrops}): stone floors only with a pickaxe. */
    static boolean harvests(DecoBlock deco, ItemStack tool) {
        return !deco.material().requiresTool() || material(deco.material()).createBlockData().isPreferredTool(tool);
    }

    static double factor(DecoBlock deco, BlockData mushroom, ItemStack tool) {
        BlockData mod = material(deco.material()).createBlockData();
        return factor(mod.getDestroySpeed(tool, true), mushroom.getDestroySpeed(tool, true),
                deco.material().hardness(), harvests(deco, tool));
    }

    // ---- per tick ----------------------------------------------------------------------------------------------

    void tick() {
        boolean on = active.getAsBoolean();
        for (Player p : players.get()) {
            update(p, on, p.getInventory().getItemInMainHand());
        }
    }

    private void update(Player p, boolean on, ItemStack tool) {
        if (!on || !eligible(p)) {
            reset(p);
            return;
        }
        Block target = digging.get(p.getUniqueId());
        if (target != null && (!target.getWorld().equals(p.getWorld()) || DecoWorld.entry(target).isEmpty()
                || p.getEyeLocation().distanceSquared(target.getLocation().toCenterLocation()) > 64)) {
            digging.remove(p.getUniqueId());
            target = null;
        }
        if (target == null) {
            target = looked(p);
        }
        Optional<Entry> entry = target == null ? Optional.empty() : DecoWorld.entry(target);
        if (entry.isEmpty()) {
            access.clear(p);
        } else {
            access.apply(p, access.factor(entry.get().block(), target, tool));
        }
    }

    static boolean eligible(Player p) {
        return (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) && !p.isDead();
    }

    /** The block the player's crosshair is on within his block reach (outline shapes, fluids ignored: vanilla pick). */
    private Block looked(Player p) {
        RayTraceResult hit = p.rayTraceBlocks(access.reach(p), FluidCollisionMode.NEVER);
        return hit == null ? null : hit.getHitBlock();
    }

    /** Sets the transient modifier to {@code factor} (nothing is sent when it is already that). */
    static void apply(Player p, double factor) {
        AttributeInstance a = p.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (a == null) {
            return;
        }
        double amount = factor - 1.0;
        AttributeModifier current = a.getModifier(KEY);
        if (current != null) {
            if (current.getOperation() == AttributeModifier.Operation.MULTIPLY_SCALAR_1
                    && Math.abs(current.getAmount() - amount) < 1e-9) {
                return;
            }
            a.removeModifier(KEY);
        }
        a.addTransientModifier(new AttributeModifier(KEY, amount, AttributeModifier.Operation.MULTIPLY_SCALAR_1,
                EquipmentSlotGroup.ANY));
    }

    /** Removes the modifier by key (no-op if absent). */
    static void clear(Player p) {
        AttributeInstance a = p.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (a != null && a.getModifier(KEY) != null) {
            a.removeModifier(KEY);
        }
    }

    private void reset(Player p) {
        digging.remove(p.getUniqueId());
        access.clear(p);
    }

    /** Plugin disable (and the switch going off): nobody keeps the modifier. */
    void disable() {
        digging.clear();
        players.get().forEach(access::clear);
    }

    // ---- digging -----------------------------------------------------------------------------------------------

    /**
     * START_DESTROY_BLOCK: Paper fires this before it computes the start progress (insta-mine check), so the
     * modifier is in place for that check even if the ray had not caught up yet.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onLeftClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Player p = event.getPlayer();
        Block block = event.getClickedBlock();
        Optional<Entry> entry = DecoWorld.entry(block);
        if (entry.isEmpty() || !eligible(p) || !active.getAsBoolean()) {
            reset(p);
            return;
        }
        digging.put(p.getUniqueId(), block);
        access.apply(p, access.factor(entry.get().block(), block, p.getInventory().getItemInMainHand()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAbort(BlockDamageAbortEvent event) {
        Player p = event.getPlayer();
        reset(p);
        update(p, active.getAsBoolean(), p.getInventory().getItemInMainHand());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        reset(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        Player p = event.getPlayer();
        Block was = digging.get(p.getUniqueId());
        reset(p);
        if (was != null) {
            digging.put(p.getUniqueId(), was);
        }
        update(p, active.getAsBoolean(), p.getInventory().getItem(event.getNewSlot()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player p = event.getPlayer();
        Block was = digging.get(p.getUniqueId());
        reset(p);
        if (was != null) {
            digging.put(p.getUniqueId(), was);
        }
        update(p, active.getAsBoolean(), event.getMainHandItem());
    }

    // ---- never left behind -------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        reset(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        reset(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        reset(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        reset(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent event) {
        reset(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent event) {
        reset(event.getPlayer());
    }

    /** For tests: the players currently locked on a block. */
    int diggingCount() {
        return digging.size();
    }
}
