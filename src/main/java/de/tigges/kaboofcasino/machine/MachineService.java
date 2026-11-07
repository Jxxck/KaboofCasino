package de.tigges.kaboofcasino.machine;

import de.tigges.kaboofcasino.config.CasinoConfig;
import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.storage.MachineIndex;
import de.tigges.kaboofcasino.util.Log;
import de.tigges.kaboofcasino.util.Pos;
import de.tigges.kaboofcasino.util.Threads;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.BlockSupport;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

/**
 * Machines in the world (PLAN decision 4, server.md 3): barrier carriers on the footprint, a record in the anchor
 * chunk's PDC, and a non-persistent display rig rebuilt at every chunk load. Keeps the in-memory index of loaded
 * machines (positions are plain ints, never block keys) and runs their games. Thread: MAIN.
 */
public final class MachineService {

    /** Rig spawns per tick after chunk loads (render.md 3.1: spread spawn bursts). */
    static final int SPAWNS_PER_TICK = 8;

    /** Outcome of {@link #place}. */
    public enum PlaceResult {
        PLACED, NO_SPACE, NO_SUPPORT, CROWDED, DENIED, CHUNK_BORDER, PLAYER_NEAR, LOGOUT_SPOT
    }

    /** A refused placement and, for {@link PlaceResult#CHUNK_BORDER}, where to move the machine (player-relative). */
    public record Placement(PlaceResult result, String direction, int blocks) {
        static Placement of(PlaceResult result) {
            return new Placement(result, null, 0);
        }
    }

    /** Other players must keep this distance (blocks, box to box) from every carrier while a machine is placed. */
    public static final double PLAYER_CLEARANCE = 2.0;

    private final Supplier<CasinoConfig> config;
    private final Supplier<Messages> messages;
    private final MachineIndex index;
    private final LongSupplier clock;
    private final Map<UUID, Machine> byId = new HashMap<>();
    private final Map<Pos, Machine> byCarrier = new HashMap<>();
    private final Deque<UUID> pendingSpawns = new ArrayDeque<>();
    private Function<Machine, MachineGame> games;

    private final LogoutSpots logouts;
    /** Cells of the display decoration a machine must not take (its light carriers are replaceable). */
    private java.util.function.Predicate<Pos> foreignCarriers = p -> false;
    /** A second rescue (players inside decoration barriers), asked when no machine carrier holds the player. */
    private java.util.function.BiPredicate<Player, String> extraRescue = (p, cause) -> false;

    public MachineService(Supplier<CasinoConfig> config, Supplier<Messages> messages, MachineIndex index,
                          LongSupplier clock, LogoutSpots logouts) {
        this.config = config;
        this.messages = messages;
        this.index = index;
        this.clock = clock;
        this.logouts = logouts;
    }

    public LogoutSpots logouts() {
        return logouts;
    }

    /** Wiring (display decoration): cells a machine may not be placed into. */
    public void foreignCarriers(java.util.function.Predicate<Pos> cells) {
        this.foreignCarriers = cells;
    }

    /** Wiring (display decoration): the rescue of players inside decoration carriers. */
    public void extraRescue(java.util.function.BiPredicate<Player, String> rescue) {
        this.extraRescue = rescue;
    }

    /** Wiring: creates the game of a machine (by its type). */
    public void games(Function<Machine, MachineGame> factory) {
        this.games = factory;
    }

    public Optional<Machine> at(Pos pos) {
        return Optional.ofNullable(byCarrier.get(pos));
    }

    public Optional<Machine> at(Block block) {
        return block.getType() == Material.BARRIER ? at(Pos.of(block)) : Optional.empty();
    }

    public boolean isCarrier(Pos pos) {
        return byCarrier.containsKey(pos);
    }

    public Optional<Machine> byId(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Collection<Machine> all() {
        return List.copyOf(byId.values());
    }

    /** True if a loaded machine will settle this round itself (watchdog). */
    public boolean ownsRound(UUID roundId) {
        for (Machine m : byId.values()) {
            if (m.game().ownsRound(roundId)) {
                return true;
            }
        }
        return false;
    }

    // ---- placing ---------------------------------------------------------------------------------------------------

    /**
     * Places a machine from the item in {@code hand} at the clicked block face (games.md 6 "Placement checks"). Refuses
     * liquid or waterlogged positions, sets the carriers, fires a {@code BlockPlaceEvent} for every carrier
     * (protection and LOD plugins; any veto restores all), writes the record, spawns the rig. The caller takes the
     * item on {@link PlaceResult#PLACED}.
     */
    public Placement place(Player player, MachineType type, Block clicked, BlockFace face, EquipmentSlot hand,
                           ItemStack item) {
        Threads.requireMain();
        World world = clicked.getWorld();
        Block placedBlock = clicked.isReplaceable() ? clicked : clicked.getRelative(face);
        Block anchorBlock = placedBlock.getRelative(0, type.rise(), 0);
        Facing facing = type.fixedFacing() ? Facing.SOUTH : Facing.towardsPlayer(player.getFacing());
        Pos anchor = Pos.of(anchorBlock);
        List<Pos> carriers = type.carriers(anchor, facing);
        // every part in one chunk: the record lives in the anchor chunk, verify/remove never load a neighbour
        int[] fit = type.chunkFit(anchor, facing);
        if (fit != null) {
            return new Placement(PlaceResult.CHUNK_BORDER, direction(player.getFacing(), fit),
                    Math.max(Math.abs(fit[0]), Math.abs(fit[1])));
        }
        for (Pos p : carriers) {
            if (p.y() < world.getMinHeight() || p.y() >= world.getMaxHeight()) {
                return Placement.of(PlaceResult.NO_SPACE);
            }
            Block b = p.block(world);
            if (config.get().machines().refuseLogoutSpots() && logouts.blocks(p)) {
                return Placement.of(PlaceResult.LOGOUT_SPOT);
            }
            if (!b.isReplaceable() || isCarrier(p) || foreignCarriers.test(p) || liquid(b)
                    || !world.getWorldBorder().isInside(new Location(world, p.x() + 0.5, p.y(), p.z() + 0.5))) {
                return Placement.of(PlaceResult.NO_SPACE);
            }
            BoundingBox box = new BoundingBox(p.x(), p.y(), p.z(), p.x() + 1, p.y() + 1, p.z() + 1);
            for (Entity e : world.getNearbyEntities(box)) {
                if (e instanceof LivingEntity living && !(living instanceof Player pl && pl.getGameMode()
                        == org.bukkit.GameMode.SPECTATOR)) {
                    return Placement.of(PlaceResult.NO_SPACE);
                }
            }
        }
        // the mod's canSurvive: every cell of the bottom row needs centre support below; the mod's roulette table
        // needs none, here (roulette.needs-support, permadeath) at least one of its cells
        int supported = 0;
        int bottom = 0;
        for (Pos p : carriers) {
            if (p.y() == anchor.y() + type.bottom()) {
                bottom++;
                if (p.block(world).getRelative(BlockFace.DOWN).getBlockData().isFaceSturdy(BlockFace.UP,
                        BlockSupport.CENTER)) {
                    supported++;
                }
            }
        }
        boolean tableNeedsSupport = switch (type) {
            case BLACKJACK_TABLE -> config.get().blackjack().needsSupport();
            case POKER_TABLE -> config.get().poker().needsSupport();
            default -> config.get().roulette().needsSupport();
        };
        if (!supportOk(type, supported, bottom, tableNeedsSupport)) {
            return Placement.of(PlaceResult.NO_SUPPORT);
        }
        if (type == MachineType.SLOT_MACHINE && slotCrowded(anchor, facing)) {
            return Placement.of(PlaceResult.CROWDED);
        }
        if (!player.isOp()) {
            for (Pos p : carriers) {
                if (underSpawnProtection(world, p)) {
                    return Placement.of(PlaceResult.DENIED);
                }
            }
        }
        for (Player other : world.getPlayers()) {
            if (!other.equals(player) && other.getGameMode() != org.bukkit.GameMode.SPECTATOR
                    && near(other.getBoundingBox(), carriers, PLAYER_CLEARANCE)) {
                return Placement.of(PlaceResult.PLAYER_NEAR);
            }
        }
        List<BlockState> before = new ArrayList<>();
        for (Pos p : carriers) {
            Block b = p.block(world);
            before.add(b.getState());
            b.setType(Material.BARRIER, false);
        }
        // every carrier is a placed block for protection and LOD plugins; any veto restores all of them
        for (int i = 0; i < carriers.size(); i++) {
            Block placed = carriers.get(i).block(world);
            BlockPlaceEvent event = new BlockPlaceEvent(placed, before.get(i), clicked, item, player, true, hand);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled() || !event.canBuild()) {
                for (BlockState state : before) {
                    state.update(true, false);
                }
                return Placement.of(PlaceResult.DENIED);
            }
        }
        MachineRecord record = new MachineRecord(UUID.randomUUID(), type, anchor, facing, player.getUniqueId(),
                clock.getAsLong(), index.nextNumber(type.id()), "");
        ChunkRecords.put(anchorBlock.getChunk(), record);
        Machine machine = register(record);
        machine.game().spawn();
        index.placed(record);
        world.playSound(anchorBlock.getLocation().add(0.5, 0.5, 0.5), org.bukkit.Sound.BLOCK_METAL_PLACE, 1f, 0.8f);
        Log.get().info("{} placed {} #{} at {} facing {}", player.getName(), type.id(), record.number(), anchor,
                facing);
        return Placement.of(PlaceResult.PLACED);
    }

    /**
     * Any fluid in the cell: water, lava, waterlogged blocks and blocks with a fluid state such as seagrass, kelp or
     * a bubble column (a barrier there would hold or hide the fluid; refused instead).
     */
    static boolean liquid(Block b) {
        return wet(b.isLiquid(), b.getBlockData() instanceof Waterlogged w && w.isWaterlogged(),
                b.getWorld().getFluidData(b.getX(), b.getY(), b.getZ()).getFluidType() != org.bukkit.Fluid.EMPTY);
    }

    /** @param fluidState the cell's fluid state is not empty (seagrass, kelp, bubble columns, waterlogged blocks) */
    static boolean wet(boolean liquid, boolean waterlogged, boolean fluidState) {
        return liquid || waterlogged || fluidState;
    }

    /**
     * Support below the bottom row: every cell for machines with {@link MachineType#needsSupport()}; for the roulette
     * and blackjack tables at least one cell when their {@code needs-support} is on, none otherwise (the mod).
     */
    static boolean supportOk(MachineType type, int supported, int bottomCells, boolean tableNeedsSupport) {
        if (type.needsSupport()) {
            return supported == bottomCells;
        }
        return !tableNeedsSupport || supported >= 1;
    }

    /**
     * The mod's {@code SlotMachineBlock.crowded}: a slot machine part in any horizontal neighbour of the anchor at
     * dy -2..+2 blocks the placement, unless that neighbour lies in front of or behind along this machine's facing
     * axis and faces along the same axis (back-to-back and face-to-face are allowed, side by side is not).
     */
    boolean slotCrowded(Pos anchor, Facing facing) {
        for (Facing side : Facing.values()) {
            int[] step = side.step();
            for (int dy = -2; dy <= 2; dy++) {
                Machine other = byCarrier.get(anchor.offset(step[0], dy, step[1]));
                if (other != null && other.type() == MachineType.SLOT_MACHINE
                        && (!side.sameAxis(facing) || !side.sameAxis(other.facing()))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * A world shift {dx, dz} in words for the player looking along {@code looks}: "links"/"rechts" across, "vorne"
     * (towards him) / "hinten" (away) along the view; {0, 0} = "zur Seite".
     */
    static String direction(BlockFace looks, int[] shift) {
        int lx = looks.getModX();
        int lz = looks.getModZ();
        int along = shift[0] * lx + shift[1] * lz;
        int across = shift[0] * -lz + shift[1] * lx;
        String side = across > 0 ? "rechts" : across < 0 ? "links" : "";
        String depth = along > 0 ? "hinten" : along < 0 ? "vorne" : "";
        String both = (side + " " + depth).strip();
        return both.isEmpty() ? "zur Seite" : both;
    }

    /** Gives {@code item}; whatever the inventory returns as not fitting goes to {@code drop}. */
    static <T> void giveOrDrop(T item, java.util.function.Function<T, Map<Integer, T>> add,
                               java.util.function.Consumer<T> drop) {
        for (T rest : add.apply(item).values()) {
            drop.accept(rest);
        }
    }

    // ---- rescue (safety 1a) ----------------------------------------------------------------------------------------

    /** The carrier blocks a bounding box overlaps, or null. */
    Machine carrierIn(BoundingBox box, String world) {
        for (int x = (int) Math.floor(box.getMinX()); x <= (int) Math.floor(box.getMaxX() - 1e-6); x++) {
            for (int y = (int) Math.floor(box.getMinY()); y <= (int) Math.floor(box.getMaxY() - 1e-6); y++) {
                for (int z = (int) Math.floor(box.getMinZ()); z <= (int) Math.floor(box.getMaxZ() - 1e-6); z++) {
                    Machine m = byCarrier.get(new Pos(world, x, y, z));
                    if (m != null) {
                        return m;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Moves a player who stands inside a machine's carriers (logged in on a spot a machine was placed on, an ender
     * pearl, a teleport) to the nearest free standing spot, the machine's front first, else onto the highest safe
     * block above. Returns true if he was moved.
     */
    public boolean rescue(Player player, String cause) {
        World world = player.getWorld();
        Machine m = carrierIn(player.getBoundingBox(), world.getKey().asString());
        if (m == null) {
            return extraRescue.test(player, cause);
        }
        Location at = player.getLocation();
        int[] spot = Rescue.nearest(at.getBlockX(), at.getBlockY(), at.getBlockZ(), m.facing().step(),
                (x, y, z) -> standable(world, x, y, z));
        Location target;
        if (spot != null) {
            target = new Location(world, spot[0] + 0.5, spot[1], spot[2] + 0.5, at.getYaw(), at.getPitch());
        } else {
            int top = world.getHighestBlockYAt(at.getBlockX(), at.getBlockZ());
            target = new Location(world, at.getBlockX() + 0.5, top + 1, at.getBlockZ() + 0.5, at.getYaw(),
                    at.getPitch());
        }
        player.setFallDistance(0);
        player.teleport(target);
        Log.get().warn("{} was inside {} #{} at {} ({}): moved to {} {} {}", player.getName(), m.type().id(),
                m.record().number(), m.anchor(), cause, target.getBlockX(), target.getBlockY(), target.getBlockZ());
        return true;
    }

    /** Feet at (x, y, z): two free cells (no carrier, nothing solid, no fluid, no fire) on safe ground. */
    private boolean standable(World world, int x, int y, int z) {
        if (y <= world.getMinHeight() || y + 1 >= world.getMaxHeight()) {
            return false;
        }
        String w = world.getKey().asString();
        for (int dy = 0; dy < 2; dy++) {
            Block b = world.getBlockAt(x, y + dy, z);
            if (isCarrier(new Pos(w, x, y + dy, z)) || !b.isPassable() || liquid(b) || hot(b.getType())) {
                return false;
            }
        }
        Block ground = world.getBlockAt(x, y - 1, z);
        return !isCarrier(new Pos(w, x, y - 1, z)) && ground.getBlockData().isFaceSturdy(BlockFace.UP,
                BlockSupport.FULL) && !hot(ground.getType()) && !liquid(ground);
    }

    private static boolean hot(Material m) {
        return m == Material.FIRE || m == Material.SOUL_FIRE || m == Material.LAVA || m == Material.MAGMA_BLOCK
                || m == Material.CAMPFIRE || m == Material.SOUL_CAMPFIRE || m == Material.POWDER_SNOW
                || m == Material.SWEET_BERRY_BUSH || m == Material.WITHER_ROSE || m == Material.CACTUS;
    }

    /** True if {@code box} comes within {@code distance} blocks of any carrier block (box to box). */
    static boolean near(BoundingBox box, List<Pos> carriers, double distance) {
        for (Pos p : carriers) {
            double dx = Math.max(0, Math.max(p.x() - box.getMaxX(), box.getMinX() - (p.x() + 1)));
            double dy = Math.max(0, Math.max(p.y() - box.getMaxY(), box.getMinY() - (p.y() + 1)));
            double dz = Math.max(0, Math.max(p.z() - box.getMaxZ(), box.getMinZ() - (p.z() + 1)));
            if (dx * dx + dy * dy + dz * dz <= distance * distance) {
                return true;
            }
        }
        return false;
    }

    private static boolean underSpawnProtection(World world, Pos pos) {
        int radius = Bukkit.getSpawnRadius();
        if (radius <= 0 || !world.equals(Bukkit.getWorlds().getFirst())) {
            return false;
        }
        Location spawn = world.getSpawnLocation();
        return Math.max(Math.abs(pos.x() - spawn.getBlockX()), Math.abs(pos.z() - spawn.getBlockZ())) <= radius;
    }

    // ---- removing --------------------------------------------------------------------------------------------------

    /**
     * Removes a machine: settles what runs ({@link MachineGame#removed}), removes the rig, the remaining barrier
     * carriers and the record, optionally drops the machine item at the anchor (survival break only).
     */
    public void remove(Machine machine, String why, ItemStack drop) {
        remove(machine, why, drop, null);
    }

    /**
     * Removes a machine; {@code drop} (the machine item, or null) goes into {@code receiver}'s inventory, only what
     * does not fit is dropped at his feet (never at the anchor, which may hang over lava or the void).
     */
    public void remove(Machine machine, String why, ItemStack drop, Player receiver) {
        Threads.requireMain();
        World world = Bukkit.getWorld(org.bukkit.NamespacedKey.fromString(machine.anchor().world()));
        try {
            machine.game().removed();
        } catch (RuntimeException e) {
            Log.incident("settling on removal of " + machine.id(), e);
        }
        machine.game().despawn();
        unregister(machine);
        if (world != null) {
            // never load a chunk here: carriers in unloaded chunks (only old data across a chunk border) stay
            for (Pos p : machine.carriers()) {
                if (!world.isChunkLoaded(p.chunkX(), p.chunkZ())) {
                    Log.get().warn("{} #{}: carrier {} is in an unloaded chunk and stays a barrier",
                            machine.type().id(), machine.record().number(), p);
                    continue;
                }
                Block b = p.block(world);
                if (b.getType() == Material.BARRIER) {
                    b.setType(Material.AIR, false);
                }
            }
            Pos a = machine.anchor();
            if (world.isChunkLoaded(a.chunkX(), a.chunkZ())) {
                ChunkRecords.remove(world.getChunkAt(a.chunkX(), a.chunkZ()), machine.id());
            }
            if (drop != null && receiver != null && receiver.isOnline()) {
                giveOrDrop(drop, receiver.getInventory()::addItem,
                        rest -> receiver.getWorld().dropItem(receiver.getLocation(), rest));
            } else if (drop != null) {
                world.dropItemNaturally(new Location(world, a.x() + 0.5, a.y() + 0.5, a.z() + 0.5), drop);
            }
        }
        index.gone(machine.id(), why, clock.getAsLong());
        Log.get().info("{} #{} at {} removed ({})", machine.type().id(), machine.record().number(),
                machine.anchor(), why);
    }

    // ---- chunk lifecycle -------------------------------------------------------------------------------------------

    /** Registers the machines of a loaded chunk; their rigs spawn over the next ticks. */
    public void chunkLoaded(Chunk chunk) {
        if (!ChunkRecords.has(chunk)) {
            return;
        }
        for (MachineRecord record : ChunkRecords.read(chunk)) {
            if (!byId.containsKey(record.id())) {
                register(record);
                pendingSpawns.add(record.id());
            }
        }
    }

    public void chunkUnloaded(Chunk chunk) {
        String world = chunk.getWorld().getKey().asString();
        for (Machine m : List.copyOf(byId.values())) {
            Pos a = m.anchor();
            if (a.world().equals(world) && a.chunkX() == chunk.getX() && a.chunkZ() == chunk.getZ()) {
                m.game().despawn();
                unregister(m);
            }
        }
    }

    /** onEnable: machines of chunks that are already loaded (plugin reload, spawn chunks). */
    public void scanLoaded() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                chunkLoaded(chunk);
            }
        }
    }

    private Machine register(MachineRecord record) {
        Machine machine = new Machine(record, games);
        byId.put(record.id(), machine);
        for (Pos p : machine.carriers()) {
            byCarrier.put(p, machine);
        }
        return machine;
    }

    private void unregister(Machine machine) {
        byId.remove(machine.id());
        for (Pos p : machine.carriers()) {
            byCarrier.remove(p, machine);
        }
    }

    /** Persists game data in the record (chunk PDC). */
    public void saveData(Machine machine, String data) {
        MachineRecord updated = machine.record().withData(data);
        machine.record(updated);
        World world = Bukkit.getWorld(org.bukkit.NamespacedKey.fromString(updated.anchor().world()));
        if (world != null && world.isChunkLoaded(updated.anchor().chunkX(), updated.anchor().chunkZ())) {
            ChunkRecords.put(world.getChunkAt(updated.anchor().chunkX(), updated.anchor().chunkZ()), updated);
        }
    }

    // ---- ticking ---------------------------------------------------------------------------------------------------

    /** Every tick: pending rig spawns, games, and every {@code machines.verify-ticks} the verifier. */
    public void tick(long tick) {
        for (int i = 0; i < SPAWNS_PER_TICK && !pendingSpawns.isEmpty(); i++) {
            Machine m = byId.get(pendingSpawns.poll());
            if (m != null) {
                guarded(m, "spawn", () -> {
                    if (verify(m)) {
                        m.game().spawn();
                    }
                });
            }
        }
        boolean verifyNow = tick % config.get().machines().verifyTicks() == 0;
        for (Machine m : List.copyOf(byId.values())) {
            if (verifyNow && byId.containsKey(m.id())) {
                guarded(m, "verify", () -> {
                    if (verify(m)) {
                        m.game().checkRig();
                    }
                });
            }
            if (byId.containsKey(m.id())) {
                guarded(m, "tick", () -> m.game().tick(tick));
            }
        }
    }

    /**
     * The /fill checker (server.md 3.3): every carrier must still be a barrier. A machine with a missing carrier was
     * hit by /fill, /setblock, a structure, Litematica or a piston exploit: rig removed, remaining barriers removed,
     * record dropped, logged, no drop.
     *
     * @return true if the machine is intact
     */
    public boolean verify(Machine machine) {
        World world = Bukkit.getWorld(org.bukkit.NamespacedKey.fromString(machine.anchor().world()));
        if (world == null) {
            return false;
        }
        for (Pos p : machine.carriers()) {
            if (!world.isChunkLoaded(p.chunkX(), p.chunkZ())) {
                continue; // that part's chunk is not loaded (old data across a chunk border): check it later
            }
            if (p.block(world).getType() != Material.BARRIER) {
                Log.get().warn("{} #{} at {}: carrier {} is {} (no event: /fill or an exploit); machine removed",
                        machine.type().id(), machine.record().number(), machine.anchor(), p,
                        p.block(world).getType().getKey());
                remove(machine, "carrier missing at " + p, null);
                return false;
            }
        }
        return true;
    }

    private void guarded(Machine m, String what, Runnable r) {
        try {
            r.run();
        } catch (RuntimeException e) {
            Log.incident(what + " of " + m.type().id() + " " + m.id(), e);
        }
    }

    /** onDisable: removes every rig (entities are non-persistent anyway); rounds were settled by the ledger. */
    public void disable() {
        for (Machine m : List.copyOf(byId.values())) {
            guarded(m, "disable", () -> m.game().despawn());
        }
        byId.clear();
        byCarrier.clear();
        pendingSpawns.clear();
    }

    /** Message for a refused placement. */
    public void tell(Player player, Placement placement) {
        PlaceResult result = placement.result();
        Msg msg = switch (result) {
            case NO_SPACE -> Msg.MACHINE_NO_SPACE;
            case NO_SUPPORT -> Msg.MACHINE_NO_SUPPORT;
            case CROWDED -> Msg.SLOT_SPACING;
            case DENIED -> Msg.MACHINE_PLACE_DENIED;
            case CHUNK_BORDER -> Msg.MACHINE_CHUNK_BORDER;
            case PLAYER_NEAR -> Msg.MACHINE_PLAYER_NEAR;
            case LOGOUT_SPOT -> Msg.MACHINE_LOGOUT_SPOT;
            case PLACED -> null;
        };
        if (msg == Msg.MACHINE_CHUNK_BORDER && placement.blocks() > 1) {
            messages.get().actionBar(player, Msg.MACHINE_CHUNK_BORDER_FAR, Messages.text("direction",
                    placement.direction()), Messages.text("blocks", Integer.toString(placement.blocks())));
        } else if (msg == Msg.MACHINE_CHUNK_BORDER) {
            messages.get().actionBar(player, msg, Messages.text("direction", placement.direction()));
        } else if (msg != null) {
            messages.get().actionBar(player, msg);
        }
    }
}
