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
        PLACED, NO_SPACE, NO_SUPPORT, CROWDED, DENIED
    }

    private final Supplier<CasinoConfig> config;
    private final Supplier<Messages> messages;
    private final MachineIndex index;
    private final LongSupplier clock;
    private final Map<UUID, Machine> byId = new HashMap<>();
    private final Map<Pos, Machine> byCarrier = new HashMap<>();
    private final Deque<UUID> pendingSpawns = new ArrayDeque<>();
    private Function<Machine, MachineGame> games;

    public MachineService(Supplier<CasinoConfig> config, Supplier<Messages> messages, MachineIndex index,
                          LongSupplier clock) {
        this.config = config;
        this.messages = messages;
        this.index = index;
        this.clock = clock;
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
    public PlaceResult place(Player player, MachineType type, Block clicked, BlockFace face, EquipmentSlot hand,
                             ItemStack item) {
        Threads.requireMain();
        World world = clicked.getWorld();
        Block placedBlock = clicked.isReplaceable() ? clicked : clicked.getRelative(face);
        Block anchorBlock = placedBlock.getRelative(0, type.rise(), 0);
        Facing facing = Facing.towardsPlayer(player.getFacing());
        Pos anchor = Pos.of(anchorBlock);
        List<Pos> carriers = type.carriers(anchor, facing);
        for (Pos p : carriers) {
            if (p.y() < world.getMinHeight() || p.y() >= world.getMaxHeight()) {
                return PlaceResult.NO_SPACE;
            }
            Block b = p.block(world);
            if (!b.isReplaceable() || isCarrier(p) || liquid(b)
                    || !world.getWorldBorder().isInside(new Location(world, p.x() + 0.5, p.y(), p.z() + 0.5))) {
                return PlaceResult.NO_SPACE;
            }
            BoundingBox box = new BoundingBox(p.x(), p.y(), p.z(), p.x() + 1, p.y() + 1, p.z() + 1);
            for (Entity e : world.getNearbyEntities(box)) {
                if (e instanceof LivingEntity living && !(living instanceof Player pl && pl.getGameMode()
                        == org.bukkit.GameMode.SPECTATOR)) {
                    return PlaceResult.NO_SPACE;
                }
            }
        }
        // the mod's canSurvive: every cell of the bottom row needs centre support below
        for (Pos p : carriers) {
            if (p.y() == anchor.y() + type.bottom() && !p.block(world).getRelative(BlockFace.DOWN).getBlockData()
                    .isFaceSturdy(BlockFace.UP, BlockSupport.CENTER)) {
                return PlaceResult.NO_SUPPORT;
            }
        }
        if (type == MachineType.SLOT_MACHINE && slotCrowded(anchor, facing)) {
            return PlaceResult.CROWDED;
        }
        if (!player.isOp() && underSpawnProtection(world, anchor)) {
            return PlaceResult.DENIED;
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
                return PlaceResult.DENIED;
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
        return PlaceResult.PLACED;
    }

    /** Water, lava or a waterlogged block: a barrier there would hold the fluid (refused instead). */
    static boolean liquid(Block b) {
        return b.isLiquid() || b.getBlockData() instanceof Waterlogged w && w.isWaterlogged();
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
            for (Pos p : machine.carriers()) {
                Block b = p.block(world);
                if (b.getType() == Material.BARRIER) {
                    b.setType(Material.AIR, false);
                }
            }
            Pos a = machine.anchor();
            ChunkRecords.remove(world.getChunkAt(a.chunkX(), a.chunkZ()), machine.id());
            if (drop != null) {
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
                return true; // the other part's chunk is not loaded: cannot tell, check again later
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
    public void tell(Player player, PlaceResult result) {
        Msg msg = switch (result) {
            case NO_SPACE -> Msg.MACHINE_NO_SPACE;
            case NO_SUPPORT -> Msg.MACHINE_NO_SUPPORT;
            case CROWDED -> Msg.SLOT_SPACING;
            case DENIED -> Msg.MACHINE_PLACE_DENIED;
            case PLACED -> null;
        };
        if (msg != null) {
            messages.get().actionBar(player, msg);
        }
    }
}
