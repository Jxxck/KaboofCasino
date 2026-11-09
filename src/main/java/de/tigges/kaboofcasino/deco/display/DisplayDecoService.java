package de.tigges.kaboofcasino.deco.display;

import de.tigges.kaboofcasino.CasinoContext;
import de.tigges.kaboofcasino.gui.DangerSweep;
import de.tigges.kaboofcasino.gui.MenuSafety;
import de.tigges.kaboofcasino.machine.Facing;
import de.tigges.kaboofcasino.music.SpeakerHooks;
import de.tigges.kaboofcasino.seat.SeatService;
import de.tigges.kaboofcasino.util.Keys;
import de.tigges.kaboofcasino.util.Log;
import de.tigges.kaboofcasino.util.Pos;
import de.tigges.kaboofcasino.util.Threads;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Light;
import org.bukkit.block.data.type.Slab;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * The display-entity decoration (step 4, the TVs and the dealer station of step 8): every non-full-cube block of
 * {@code casinodeco}, the casino chair and stool, the casino TVs, the dealer station and the speaker. Keeps the index of loaded decorations (records in the chunk PDC, cells as plain-int positions),
 * places and removes them, recomputes connections (tables, rope posts, bars, the sofa layout) and support when
 * neighbours change, verifies carriers and support round-robin (never loading a chunk), and runs the seats, the
 * candle flames and the break timers. Thread: MAIN.
 */
public final class DisplayDecoService {

    /** Rig spawns per tick after chunk loads (spread spawn bursts like the machines). */
    static final int SPAWNS_PER_TICK = 16;
    /** Neighbour checks per tick (a big explosion queues many). */
    static final int CHECKS_PER_TICK = 256;

    private static final BlockFace[] AROUND = {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.EAST,
            BlockFace.SOUTH, BlockFace.WEST};

    /** How a removed decoration's items leave. */
    public enum Drop {
        /** Nothing (verifier, creative, admin). */
        NONE,
        /** At the block like the mod's loot (support lost, explosions). */
        AT_BLOCK,
        /** Into the breaker's inventory, the rest at his feet (player breaks). */
        TO_PLAYER
    }

    private final CasinoContext ctx;
    private final MenuSafety safety;
    private final DangerSweep sweep;
    private final SpeakerHooks music;
    private final Map<UUID, DecoBlock> byId = new HashMap<>();
    private final Map<Pos, DecoBlock> byCell = new HashMap<>();
    /** Loaded candle blocks (chandelier, sconce): the only ones {@link DecoFlames} looks at. */
    private final Map<UUID, DecoBlock> candles = new java.util.LinkedHashMap<>();
    /** Loaded dealer stations, for {@link #dealerStations()} and {@link #dealerStationsNear}. */
    private final Map<UUID, DecoBlock> stations = new java.util.LinkedHashMap<>();
    private DealerStationHook dealerHook = DealerStationHook.NONE;
    /** Loaded decorations by anchor chunk ({@code world chunkKey}), for chunk unloads. */
    private final Map<String, Set<UUID>> byChunk = new HashMap<>();
    private final Deque<UUID> pendingSpawns = new ArrayDeque<>();
    private final Set<Pos> pendingChecks = new LinkedHashSet<>();
    private final Deque<UUID> verifyQueue = new ArrayDeque<>();
    private final Set<UUID> pendingSofas = new LinkedHashSet<>();
    private final SeatService seats;
    private final DecoFlames flames;
    private final DecoBreaks breaks;
    private final DecoPlacer placer;
    private final List<Listener> listeners = new ArrayList<>();

    public DisplayDecoService(CasinoContext ctx, MenuSafety safety, DangerSweep sweep, SpeakerHooks music) {
        this.ctx = ctx;
        this.safety = safety;
        this.sweep = sweep;
        this.music = music == null ? SpeakerHooks.NONE : music;
        this.seats = new SeatService(this, safety, sweep, ctx.messages());
        this.flames = new DecoFlames(this);
        this.breaks = new DecoBreaks(this);
        this.placer = new DecoPlacer(this);
    }

    public CasinoContext ctx() {
        return ctx;
    }

    public SeatService seats() {
        return seats;
    }

    DecoBreaks breaks() {
        return breaks;
    }

    DecoPlacer placer() {
        return placer;
    }

    SpeakerHooks music() {
        return music;
    }

    MenuSafety safety() {
        return safety;
    }

    DangerSweep sweep() {
        return sweep;
    }

    /** Registers items (ItemCatalog), listeners and hooks; registers the decorations of loaded chunks. */
    public void enable() {
        DecoItems.register();
        listeners.add(new DecoListener(this));
        listeners.add(new DecoProtection(this));
        listeners.add(seats.listener());
        for (Listener l : listeners) {
            Bukkit.getPluginManager().registerEvents(l, ctx.plugin());
        }
        sweep.casinoSeats(seats::isSeat);
        ctx.machines().foreignCarriers(this::isCell);
        ctx.machines().extraRescue(seats::rescue);
        scanLoaded();
        Log.get().info("Display decoration enabled: {} type(s), {} block(s) in loaded chunks", DecoType.values().length,
                byId.size());
    }

    /** Sets what happens when a dealer station is placed, removed or used ({@code null}: nothing, as in the mod). */
    public void dealerStationHook(DealerStationHook hook) {
        this.dealerHook = hook == null ? DealerStationHook.NONE : hook;
    }

    /** Stands every seated player up (safe spot), removes every entity. */
    public void disable() {
        seats.disable();
        breaks.clear();
        for (DecoBlock b : List.copyOf(byId.values())) {
            DecoRig.despawn(b);
        }
        byId.clear();
        byCell.clear();
        byChunk.clear();
        candles.clear();
        stations.clear();
        pendingSpawns.clear();
        pendingChecks.clear();
        verifyQueue.clear();
        pendingSofas.clear();
    }

    // ---- index -----------------------------------------------------------------------------------------------------

    public Optional<DecoBlock> at(Pos pos) {
        return Optional.ofNullable(byCell.get(pos));
    }

    public Optional<DecoBlock> at(Block block) {
        return at(Pos.of(block));
    }

    public boolean isCell(Pos pos) {
        return byCell.containsKey(pos);
    }

    public Optional<DecoBlock> byId(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Collection<DecoBlock> all() {
        return List.copyOf(byId.values());
    }

    /** The loaded chandeliers and sconces (live view; the flames only read it). */
    Collection<DecoBlock> flameBlocks() {
        return java.util.Collections.unmodifiableCollection(candles.values());
    }

    /** The loaded dealer stations (records at this moment, in load order). */
    public List<DecoRecord> dealerStations() {
        List<DecoRecord> out = new ArrayList<>(stations.size());
        for (DecoBlock b : stations.values()) {
            out.add(b.record());
        }
        return out;
    }

    /**
     * The loaded dealer stations in {@code centre}'s world whose block centre lies within {@code radius} blocks of
     * {@code centre}'s block centre, nearest first (equal distances in load order).
     */
    public List<DecoRecord> dealerStationsNear(Pos centre, double radius) {
        return near(dealerStations(), centre, radius);
    }

    /** The records whose anchor lies within {@code radius} blocks of {@code centre}, nearest first (stable). Pure. */
    static List<DecoRecord> near(List<DecoRecord> records, Pos centre, double radius) {
        List<DecoRecord> out = new ArrayList<>();
        for (DecoRecord r : records) {
            if (distanceSquared(centre, r.anchor()) <= radius * radius) {
                out.add(r);
            }
        }
        out.sort(java.util.Comparator.comparingDouble(r -> distanceSquared(centre, r.anchor())));
        return out;
    }

    /** Squared distance of two block positions; infinite between worlds. */
    static double distanceSquared(Pos a, Pos b) {
        if (!a.world().equals(b.world())) {
            return Double.POSITIVE_INFINITY;
        }
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        double dz = a.z() - b.z();
        return dx * dx + dy * dy + dz * dz;
    }

    /** {@link DealerStationHook#onPlaced}, guarded. */
    void dealerStationPlaced(DecoRecord station, Player placer) {
        try {
            dealerHook.onPlaced(station, placer);
        } catch (RuntimeException e) {
            Log.incident("dealer station hook (placed) at " + station.anchor(), e);
        }
    }

    /** {@link DealerStationHook#onUse} (an exception goes to the caller, which reports it to the player). */
    boolean dealerStationUsed(Player player, DecoBlock station) {
        return dealerHook.onUse(player, station.record());
    }

    /** Live view for read-only loops that do not add or remove decorations. */
    Collection<DecoBlock> values() {
        return java.util.Collections.unmodifiableCollection(byId.values());
    }

    /** The decoration an entity (display or Interaction) belongs to, by its {@code kaboofcasino:part} tag. */
    public Optional<DecoBlock> owner(Entity e) {
        String id = e.getPersistentDataContainer().get(Keys.PART, PersistentDataType.STRING);
        if (id == null) {
            return Optional.empty();
        }
        try {
            DecoBlock b = byId.get(UUID.fromString(id));
            return b != null && b.clicks.contains(e) ? Optional.of(b) : Optional.empty();
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    static World world(Pos p) {
        return Bukkit.getWorld(NamespacedKey.fromString(p.world()));
    }

    private static String chunkOf(Pos p) {
        return p.world() + " " + p.chunkKey();
    }

    private void register(DecoBlock b) {
        byId.put(b.id(), b);
        if (DecoFlames.burns(b.type())) {
            candles.put(b.id(), b);
        }
        if (b.type() == DecoType.DEALER_STATION) {
            stations.put(b.id(), b);
        }
        byChunk.computeIfAbsent(chunkOf(b.anchor()), k -> new HashSet<>()).add(b.id());
        for (Pos p : b.cells()) {
            byCell.put(p, b);
        }
        verifyQueue.add(b.id());
    }

    private void unregister(DecoBlock b) {
        byId.remove(b.id());
        candles.remove(b.id());
        stations.remove(b.id());
        Set<UUID> inChunk = byChunk.get(chunkOf(b.anchor()));
        if (inChunk != null) {
            inChunk.remove(b.id());
            if (inChunk.isEmpty()) {
                byChunk.remove(chunkOf(b.anchor()));
            }
        }
        for (Pos p : b.cells()) {
            byCell.remove(p, b);
        }
    }

    // ---- adding, updating, removing --------------------------------------------------------------------------------

    /** A new decoration (carriers are in place): record into the anchor chunk, index, entities, neighbour updates. */
    DecoBlock add(DecoRecord record, World world) {
        Threads.requireMain();
        DecoBlock b = new DecoBlock(record);
        Pos a = record.anchor();
        DecoRecords.put(world.getChunkAt(a.chunkX(), a.chunkZ()), record);
        register(b);
        if (record.type().connect() == DecoType.Connect.SAME || record.type().connect() == DecoType.Connect.BAR) {
            int mask = connections(b, world);
            if (mask != record.mask()) {
                record = record.withState(mask);
                b.record(record);
                DecoRecords.put(world.getChunkAt(a.chunkX(), a.chunkZ()), record);
            }
        }
        DecoRig.spawn(b, world);
        if (record.type() == DecoType.CASINO_SOFA) {
            rebuildSofa(b, world);
        }
        if (record.type() == DecoType.CASINO_SPEAKER) {
            music.loaded(a, record.facing(), record.on());
        }
        checkAround(b.cells());
        return b;
    }

    /** Persists a changed record and refreshes the entities and light carriers. */
    void update(DecoBlock b, DecoRecord updated, World world) {
        persist(b, updated, world);
        repairLight(b, world);
        if (b.spawned()) {
            DecoRig.refresh(b, world); // a block still waiting for its first spawn is drawn by that spawn
        }
    }

    /** Stores a changed record (the cells of a record never change). */
    private void persist(DecoBlock b, DecoRecord updated, World world) {
        b.record(updated);
        Pos a = updated.anchor();
        if (world.isChunkLoaded(a.chunkX(), a.chunkZ())) {
            DecoRecords.put(world.getChunkAt(a.chunkX(), a.chunkZ()), updated);
        }
    }

    /**
     * Removes a decoration: seated players stand up, a speaker leaves the station, entities go, plugin carriers become
     * air (or water if they were waterlogged), the record leaves the chunk, the items drop as asked; neighbours
     * update. {@code keepCarrier}: vanilla removes the carrier itself (a player breaking stairs or slabs).
     */
    void remove(DecoBlock b, String why, Drop drop, int count, Player receiver, boolean keepCarrier) {
        Threads.requireMain();
        if (!byId.containsKey(b.id())) {
            return;
        }
        World world = world(b.anchor());
        seats.removed(b);
        breaks.forget(b);
        if (b.type() == DecoType.CASINO_SPEAKER) {
            music.disable(b.anchor());
        }
        DecoRig.despawn(b);
        unregister(b);
        if (world != null) {
            if (!keepCarrier) {
                for (Pos p : b.cells()) {
                    if (!world.isChunkLoaded(p.chunkX(), p.chunkZ())) {
                        continue;
                    }
                    clearCarrier(b.type(), p.block(world));
                }
            }
            Pos a = b.anchor();
            if (world.isChunkLoaded(a.chunkX(), a.chunkZ())) {
                DecoRecords.remove(world.getChunkAt(a.chunkX(), a.chunkZ()), b.id());
            }
            if (count > 0 && drop != Drop.NONE) {
                ItemStack items = DecoItems.create(b.type(), count);
                if (drop == Drop.TO_PLAYER && receiver != null && receiver.isOnline()) {
                    for (ItemStack rest : receiver.getInventory().addItem(items).values()) {
                        receiver.getWorld().dropItem(receiver.getLocation(), rest);
                    }
                } else {
                    world.dropItemNaturally(new Location(world, a.x() + 0.5, a.y() + 0.5, a.z() + 0.5), items);
                }
            }
        }
        if (b.type() == DecoType.DEALER_STATION) {
            try {
                dealerHook.onRemoved(b.record());
            } catch (RuntimeException e) {
                Log.incident("dealer station hook (removed) at " + b.anchor(), e);
            }
        }
        checkAround(b.cells());
        if (b.type() == DecoType.CASINO_SOFA && world != null) {
            for (int d = 0; d < 4; d++) {
                Pos n = b.anchor().offset(SofaLayout.STEP[d][0], 0, SofaLayout.STEP[d][1]);
                DecoBlock nb = byCell.get(n);
                if (nb != null && nb.type() == DecoType.CASINO_SOFA) {
                    pendingSofas.add(nb.id());
                }
            }
        }
        Log.get().debug("{} at {} removed ({})", b.type().id(), b.anchor(), why);
    }

    /** The number of items a decoration drops (stacked decor: its count; slabs: the deco slabs in the cell). */
    static int dropCount(DecoBlock b, World world) {
        if (b.type().place() == DecoType.Place.STACKED) {
            return b.record().count();
        }
        if (b.type().carrier() == DecoType.Carrier.SLAB) {
            return slabDrop(b.record().state(), world != null && carrierDouble(b.anchor().block(world)));
        }
        return 1;
    }

    /**
     * Deco slabs a slab cell drops: the count the record stores (1, or 2 after the same deco slab was added), never
     * more than the carrier holds; a record from 0.5.0 (state 0) falls back to the carrier (double = 2).
     */
    static int slabDrop(int stored, boolean carrierDouble) {
        int carrier = carrierDouble ? 2 : 1;
        return stored <= 0 ? carrier : Math.min(stored, carrier);
    }

    private static boolean carrierDouble(Block block) {
        return block.getBlockData() instanceof Slab s && s.getType() == Slab.Type.DOUBLE;
    }

    /** Turns a cell that still holds {@code type}'s carrier into air (water if waterlogged); anything else stays. */
    private static void clearCarrier(DecoType type, Block block) {
        if (!carrierMaterial(type, block.getType()) && !legacyShelf(type, block.getType())) {
            return;
        }
        boolean wet = block.getBlockData() instanceof Waterlogged w && w.isWaterlogged();
        block.setType(wet ? Material.WATER : Material.AIR, true);
    }

    // ---- chunk lifecycle -------------------------------------------------------------------------------------------

    /** Registers the decorations of a loaded chunk; their entities spawn over the next ticks. */
    public void chunkLoaded(Chunk chunk) {
        if (!DecoRecords.has(chunk)) {
            return;
        }
        for (DecoRecord record : DecoRecords.read(chunk)) {
            if (byId.containsKey(record.id())) {
                continue;
            }
            DecoBlock b = new DecoBlock(record);
            register(b);
            pendingSpawns.add(b.id());
            if (record.type() == DecoType.CASINO_SOFA) {
                pendingSofas.add(b.id());
            }
            if (record.type() == DecoType.CASINO_SPEAKER) {
                music.loaded(record.anchor(), record.facing(), record.on());
            }
        }
    }

    public void chunkUnloaded(Chunk chunk) {
        Set<UUID> inChunk = byChunk.get(chunk.getWorld().getKey().asString() + " "
                + Pos.chunkKey(chunk.getX(), chunk.getZ()));
        if (inChunk == null) {
            return;
        }
        for (UUID id : List.copyOf(inChunk)) {
            DecoBlock b = byId.get(id);
            if (b != null) {
                Pos a = b.anchor();
                seats.removed(b);
                breaks.forget(b);
                DecoRig.despawn(b);
                unregister(b);
                if (b.type() == DecoType.CASINO_SPEAKER) {
                    music.unloaded(a);
                }
            }
        }
    }

    void scanLoaded() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                chunkLoaded(chunk);
            }
        }
    }

    // ---- ticking ---------------------------------------------------------------------------------------------------

    /** Every server tick. */
    public void tick(long tick) {
        for (int i = 0; i < SPAWNS_PER_TICK && !pendingSpawns.isEmpty(); i++) {
            DecoBlock b = byId.get(pendingSpawns.poll());
            if (b != null) {
                guarded(b, "spawn", () -> {
                    World w = world(b.anchor());
                    if (w != null && verify(b, w)) {
                        int state = connectedState(b, w);
                        if (state != b.record().state()) {
                            persist(b, b.record().withState(state), w);
                        }
                        repairLight(b, w);
                        DecoRig.spawn(b, w);
                    }
                });
            }
        }
        runChecks();
        for (UUID id : List.copyOf(pendingSofas)) {
            DecoBlock b = byId.get(id);
            if (b != null) {
                World w = world(b.anchor());
                if (w != null) {
                    guarded(b, "sofa", () -> rebuildSofa(b, w));
                }
            }
        }
        pendingSofas.clear();
        verifySlice();
        flames.tick();
        seats.tick(tick);
        breaks.tick();
    }

    private void runChecks() {
        Set<UUID> done = new HashSet<>();
        Iterator<Pos> it = pendingChecks.iterator();
        int n = 0;
        while (it.hasNext() && n++ < CHECKS_PER_TICK) {
            Pos p = it.next();
            it.remove();
            DecoBlock b = byCell.get(p);
            if (b == null || !done.add(b.id())) {
                continue;
            }
            World w = world(b.anchor());
            if (w == null || !w.isChunkLoaded(b.anchor().chunkX(), b.anchor().chunkZ())) {
                continue;
            }
            guarded(b, "check", () -> check(b, w));
        }
    }

    /** One decoration after a neighbour change: carriers, support, connections, light, models. */
    void check(DecoBlock b, World w) {
        if (!verify(b, w)) {
            return;
        }
        int state = connectedState(b, w);
        if (state != b.record().state()) {
            update(b, b.record().withState(state), w);
        } else {
            repairLight(b, w);
            if (b.spawned()) {
                DecoRig.refresh(b, w);
            }
        }
        if (b.type() == DecoType.CASINO_SOFA) {
            pendingSofas.add(b.id());
        }
    }

    /** Verifies a share of all decorations per tick, so each is checked about every {@code machines.verify-ticks}. */
    private void verifySlice() {
        if (verifyQueue.isEmpty()) {
            return;
        }
        int period = Math.max(1, ctx.config().get().machines().verifyTicks());
        int count = Math.max(1, (byId.size() + period - 1) / period);
        for (int i = 0; i < count && !verifyQueue.isEmpty(); i++) {
            UUID id = verifyQueue.poll();
            DecoBlock b = byId.get(id);
            if (b == null) {
                continue;
            }
            verifyQueue.add(id);
            World w = world(b.anchor());
            if (w != null && w.isChunkLoaded(b.anchor().chunkX(), b.anchor().chunkZ())) {
                guarded(b, "verify", () -> check(b, w));
            }
        }
    }

    /** Queues a check of every decoration at or next to these cells (next tick); other cells cost one lookup. */
    void checkAround(Collection<Pos> cells) {
        for (Pos p : cells) {
            queueCheck(p);
            queueCheck(p.offset(0, 1, 0));
            queueCheck(p.offset(0, -1, 0));
            queueCheck(p.offset(1, 0, 0));
            queueCheck(p.offset(-1, 0, 0));
            queueCheck(p.offset(0, 0, 1));
            queueCheck(p.offset(0, 0, -1));
        }
    }

    private void queueCheck(Pos p) {
        if (byCell.containsKey(p)) {
            pendingChecks.add(p);
        }
    }

    void checkAround(Pos p) {
        checkAround(List.of(p));
    }

    /**
     * The /fill checker: every carrier must still be there (light levels are repaired) and the support the mod needs
     * must hold. A missing carrier (/fill, /setblock, a structure, Litematica, a piston exploit) removes the decoration
     * without a drop, and every other cell that still holds its carrier (the other light blocks of a sign or
     * chandelier) becomes air; the block that replaced the missing one stays. Lost support pops the decoration with
     * its item at the block, as the mod's {@code updateShape} does. A display shelf from 0.5.0 still on its light
     * carrier gets its barrier ({@link #convertLegacyShelf}).
     *
     * @return true if the decoration is intact
     */
    boolean verify(DecoBlock b, World w) {
        for (Pos p : b.cells()) {
            if (!w.isChunkLoaded(p.chunkX(), p.chunkZ())) {
                continue;
            }
            Block block = p.block(w);
            if (legacyShelf(b.type(), block.getType())) {
                convertLegacyShelf(b, block);
                continue;
            }
            if (!carrierPresent(b.type(), block)) {
                Log.get().warn("{} at {}: carrier {} is {} (no event: /fill or an exploit); decoration removed",
                        b.type().id(), b.anchor(), p, block.getType().getKey());
                remove(b, "carrier missing at " + p, Drop.NONE, 0, null, false);
                return false;
            }
        }
        if (!supported(b.record(), w)) {
            remove(b, "support lost", Drop.AT_BLOCK, dropCount(b, w), null, false);
            return false;
        }
        return true;
    }

    static boolean carrierPresent(DecoType type, Block block) {
        return carrierMaterial(type, block.getType());
    }

    /** True if {@code m} is the carrier of {@code type} (also the test of what {@code remove} turns into air). */
    static boolean carrierMaterial(DecoType type, Material m) {
        return switch (type.carrier()) {
            case BARRIER -> m == Material.BARRIER;
            case LIGHT -> m == Material.LIGHT;
            case STAIRS, SLAB -> m == type.vanilla();
        };
    }

    /** A bottle or glass display shelf placed by 0.5.0, still standing on the light block that carried it then. */
    static boolean legacyShelf(DecoType type, Material m) {
        return (type == DecoType.BOTTLE_DISPLAY_SHELF || type == DecoType.GLASS_DISPLAY_SHELF) && m == Material.LIGHT;
    }

    /**
     * Gives a 0.5.0 shelf its barrier, unless a living entity is in the cell or an offline player logged out within a
     * block of it (it would be inside a solid block): then the light block stays until a later check finds the cell
     * clear. Until converted such a shelf has no click box (it cannot be broken), nothing else changes.
     */
    private void convertLegacyShelf(DecoBlock b, Block block) {
        Pos p = Pos.of(block);
        org.bukkit.util.BoundingBox box = new org.bukkit.util.BoundingBox(p.x(), p.y(), p.z(), p.x() + 1, p.y() + 1,
                p.z() + 1);
        for (Entity e : block.getWorld().getNearbyEntities(box)) {
            if (e instanceof org.bukkit.entity.LivingEntity) {
                return;
            }
        }
        if (ctx.machines().logouts().blocks(p)) {
            return;
        }
        block.setType(Material.BARRIER, false);
        Log.get().info("{} at {}: light carrier of 0.5.0 replaced by a barrier", b.type().id(), b.anchor());
    }

    /** The light level a light carrier cell must have (the mod's {@code lightLevel} of that block state). */
    static int lightLevel(DecoRecord r, Pos cell, boolean waterlogged) {
        return switch (r.type()) {
            case CASINO_CHANDELIER -> DecoGeometry.chandelierChain(r, cell) ? 0 : 14;
            case CHANDELIER_WALL_SCONCE -> waterlogged ? 0 : 14;
            case CASINO_SPEAKER -> r.on() ? 2 : 0;
            case CASINO_TV, CASINO_TV_LARGE, CASINO_TV_3X3 -> r.tvOn() ? r.type().light() : 0;
            default -> r.type().light();
        };
    }

    /** Sets every light carrier of {@code b} to its level (switched speaker, waterlogged sconce, creative edits). */
    void repairLight(DecoBlock b, World w) {
        if (b.type().carrier() != DecoType.Carrier.LIGHT) {
            return;
        }
        for (Pos p : b.cells()) {
            if (!w.isChunkLoaded(p.chunkX(), p.chunkZ())) {
                continue;
            }
            Block block = p.block(w);
            if (block.getBlockData() instanceof Light light) {
                int want = lightLevel(b.record(), p, light.isWaterlogged());
                if (light.getLevel() != want) {
                    light.setLevel(want);
                    block.setBlockData(light, false);
                }
            }
        }
    }

    /** A light carrier's block data. */
    static BlockData lightData(int level, boolean waterlogged) {
        Light light = (Light) Material.LIGHT.createBlockData();
        light.setLevel(level);
        light.setWaterlogged(waterlogged);
        return light;
    }

    // ---- support (the mod's canSurvive) ----------------------------------------------------------------------------

    /** The mod's {@code canSurvive} of a placed decoration. */
    boolean supported(DecoRecord r, World w) {
        Pos a = r.anchor();
        return switch (r.type().place()) {
            case VANILLA -> true;
            case FLOOR, FLOOR_PLAIN, STACKED, SEAT, SOFA -> sturdy(w, a.offset(0, -1, 0), BlockFace.UP, true);
            case WALL -> wall(w, a, r.facing());
            case SIGN -> wallOrUnloaded(w, a, r.facing());
            case LARGE_SIGN -> {
                for (int part = 0; part < 6; part++) {
                    if (!wallOrUnloaded(w, DecoGeometry.signPart(a, r.facing(), part), r.facing())) {
                        yield false;
                    }
                }
                yield true;
            }
            case TV -> {
                // CasinoTelevisionBlock.tick: every cell keeps a sturdy wall behind it
                int columns = r.type().tvColumns();
                for (int part = 0; part < columns * columns; part++) {
                    if (!wallOrUnloaded(w, DecoGeometry.wallPart(a, r.facing(), columns, part), r.facing())) {
                        yield false;
                    }
                }
                yield true;
            }
            case SQUARE_SIGN -> {
                int columns = r.type().signColumns();
                for (int part = 0; part < columns * columns; part++) {
                    if (!wallOrUnloaded(w, DecoGeometry.wallPart(a, r.facing(), columns, part), r.facing())) {
                        yield false;
                    }
                }
                yield true;
            }
            case FREE -> true;
            case SPEAKER -> switch (r.face()) {
                case DecoRecord.FACE_CEILING -> sturdy(w, a.offset(0, 1, 0), BlockFace.DOWN, false);
                case DecoRecord.FACE_WALL -> wall(w, a, r.facing());
                default -> sturdy(w, a.offset(0, -1, 0), BlockFace.UP, false);
            };
            case CHANDELIER -> {
                Pos above = a.offset(0, r.chain() + 1, 0);
                yield !w.isChunkLoaded(above.chunkX(), above.chunkZ()) || sturdy(w, above, BlockFace.DOWN, true);
            }
        };
    }

    /** The wall behind a decoration facing {@code facing} is sturdy towards it. */
    boolean wall(World w, Pos cell, Facing facing) {
        int[] s = facing.step();
        return sturdy(w, cell.offset(-s[0], 0, -s[1]), facing.blockFace(), false);
    }

    private boolean wallOrUnloaded(World w, Pos cell, Facing facing) {
        int[] s = facing.step();
        Pos wall = cell.offset(-s[0], 0, -s[1]);
        return !w.isChunkLoaded(wall.chunkX(), wall.chunkZ()) || sturdy(w, wall, facing.blockFace(), false);
    }

    /**
     * {@code isFaceSturdy} of the block at {@code p} towards {@code face} ({@code centre}: {@code canSupportCenter},
     * else full). A decoration cell answers with the mod's shape, not its barrier: bars are full cubes, the tall
     * tables' tops are full, the rope post's post carries the centre; seats, low tables and small decor carry nothing.
     */
    boolean sturdy(World w, Pos p, BlockFace face, boolean centre) {
        if (p.y() < w.getMinHeight() || p.y() >= w.getMaxHeight()) {
            return false;
        }
        if (!w.isChunkLoaded(p.chunkX(), p.chunkZ())) {
            return true; // never load a chunk: support in an unloaded chunk is checked once it loads
        }
        DecoBlock d = byCell.get(p);
        if (d != null && d.type().pluginCarrier()) {
            return decoSturdy(d.type(), face, centre);
        }
        return p.block(w).getBlockData().isFaceSturdy(face, centre ? BlockSupport.CENTER : BlockSupport.FULL);
    }

    static boolean decoSturdy(DecoType type, BlockFace face, boolean centre) {
        return switch (type) {
            case WALNUT_BAR, IVORY_MARBLE_BAR, BLACK_MARBLE_BAR -> true;
            case WOOD_TABLE, GLASS_TABLE -> face == BlockFace.UP;
            case VELVET_ROPE_POST -> face == BlockFace.UP && centre;
            // DealerStationBlock: the 12 px post reaches the bottom (centre support below), the top ends at 11.7 px
            case DEALER_STATION -> face == BlockFace.DOWN && centre;
            default -> false;
        };
    }

    // ---- connections -----------------------------------------------------------------------------------------------

    /** The record state with the connection mask recomputed (tables, rope posts, bars); other types unchanged. */
    int connectedState(DecoBlock b, World w) {
        if (b.type().carrier() == DecoType.Carrier.SLAB && b.record().state() <= 0) {
            // a 0.5.0 slab record (no count stored): take the carrier's, as 0.5.0 did, and store it from now on
            return slabDrop(0, carrierDouble(b.anchor().block(w)));
        }
        DecoType.Connect c = b.type().connect();
        if (c != DecoType.Connect.SAME && c != DecoType.Connect.BAR) {
            return b.record().state();
        }
        return connections(b, w);
    }

    /**
     * The mod's {@code Connections.around} / {@code CasinoBarBlock.joins}: bit d set if the neighbour in direction d
     * (0 north, 1 east, 2 south, 3 west) is the same block (tables, rope post) or any bar (bars). A neighbour in an
     * unloaded chunk keeps the stored bit (the mod updates shapes only from loaded neighbours).
     */
    int connections(DecoBlock b, World w) {
        int mask = b.record().mask();
        Pos a = b.anchor();
        for (int d = 0; d < 4; d++) {
            Pos n = a.offset(SofaLayout.STEP[d][0], 0, SofaLayout.STEP[d][1]);
            if (!w.isChunkLoaded(n.chunkX(), n.chunkZ())) {
                continue;
            }
            DecoBlock nb = byCell.get(n);
            boolean joins = nb != null && nb.anchor().equals(n) && joins(b.type(), nb.type());
            mask = joins ? mask | 1 << d : mask & ~(1 << d);
        }
        return mask;
    }

    static boolean joins(DecoType self, DecoType neighbour) {
        if (self.connect() == DecoType.Connect.BAR) {
            return neighbour.connect() == DecoType.Connect.BAR;
        }
        return self.connect() == DecoType.Connect.SAME && neighbour == self;
    }

    /**
     * The mod's {@code CasinoSofaBlock.rebuild}: walks the sofas joined to {@code start} (same height), with ghosts
     * for joined neighbours in unloaded chunks, lays them out ({@link SofaLayout#calculate}) and stores each cell's
     * {@code joins}/{@code back}.
     */
    void rebuildSofa(DecoBlock start, World w) {
        if (start.type() != DecoType.CASINO_SOFA || !byId.containsKey(start.id())) {
            return;
        }
        Map<SofaLayout.Cell, Integer> facing = new HashMap<>();
        Map<SofaLayout.Cell, DecoBlock> blocks = new HashMap<>();
        Map<SofaLayout.Cell, Integer> joins = new HashMap<>();
        Map<SofaLayout.Cell, Integer> continuation = new HashMap<>();
        Deque<DecoBlock> queue = new ArrayDeque<>();
        Set<UUID> seen = new HashSet<>();
        queue.add(start);
        seen.add(start.id());
        int y = start.anchor().y();
        while (!queue.isEmpty()) {
            DecoBlock s = queue.remove();
            Pos p = s.anchor();
            SofaLayout.Cell cell = new SofaLayout.Cell(p.x(), p.z());
            facing.put(cell, DecoModels.cardinal(s.record().facing()));
            blocks.put(cell, s);
            for (int d = 0; d < 4; d++) {
                Pos n = p.offset(SofaLayout.STEP[d][0], 0, SofaLayout.STEP[d][1]);
                if (!w.isChunkLoaded(n.chunkX(), n.chunkZ())) {
                    if ((s.record().sofaJoins() & SofaLayout.bit(d)) != 0) {
                        SofaLayout.Cell ghost = new SofaLayout.Cell(n.x(), n.z());
                        int back = s.record().sofaBack();
                        int ghostFacing = Integer.bitCount(back) == 1 ? Integer.numberOfTrailingZeros(back) + 2 & 3
                                : DecoModels.cardinal(s.record().facing());
                        facing.putIfAbsent(ghost, ghostFacing);
                        joins.merge(cell, SofaLayout.bit(d), (x, z) -> x | z);
                        joins.merge(ghost, SofaLayout.bit(d + 2), (x, z) -> x | z);
                        continuation.put(cell, back);
                    }
                } else {
                    DecoBlock nb = byCell.get(n);
                    if (nb != null && nb.type() == DecoType.CASINO_SOFA && nb.anchor().y() == y) {
                        joins.merge(cell, SofaLayout.bit(d), (x, z) -> x | z);
                        if (seen.add(nb.id())) {
                            queue.add(nb);
                        }
                    }
                }
            }
        }
        for (SofaLayout.Cell cell : facing.keySet()) {
            joins.putIfAbsent(cell, 0);
        }
        Map<SofaLayout.Cell, SofaLayout.Layout> layout = SofaLayout.calculate(facing, joins, continuation);
        for (Map.Entry<SofaLayout.Cell, SofaLayout.Layout> e : layout.entrySet()) {
            DecoBlock s = blocks.get(e.getKey());
            if (s == null) {
                continue;
            }
            int state = DecoRecord.sofaState(e.getValue().joins(), e.getValue().backs());
            if (state != s.record().state()) {
                update(s, s.record().withState(state), w);
            }
            pendingSofas.remove(s.id());
        }
    }

    private void guarded(DecoBlock b, String what, Runnable r) {
        try {
            r.run();
        } catch (RuntimeException e) {
            Log.incident(what + " of " + b.type().id() + " at " + b.anchor(), e);
        }
    }
}
