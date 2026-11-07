package de.tigges.kaboofcasino.seat;

import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.deco.display.DecoBlock;
import de.tigges.kaboofcasino.deco.display.DecoRecord;
import de.tigges.kaboofcasino.deco.display.DecoType;
import de.tigges.kaboofcasino.deco.display.DisplayDecoService;
import de.tigges.kaboofcasino.gui.DangerSweep;
import de.tigges.kaboofcasino.gui.MenuSafety;
import de.tigges.kaboofcasino.machine.Facing;
import de.tigges.kaboofcasino.machine.Rescue;
import de.tigges.kaboofcasino.util.FakePlayers;
import de.tigges.kaboofcasino.util.Keys;
import de.tigges.kaboofcasino.util.Log;
import de.tigges.kaboofcasino.util.Pos;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;
import org.bukkit.block.data.type.Campfire;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;

/**
 * Sitting on the casino chair, stool and sofa (games.md 17, server.md 4) for unmodified clients. The vehicle is a
 * non-persistent, item-less item display (zero size, so the rider sits exactly where the mod's seat entity puts him),
 * spawned when a player sits and removed when he stands up; never saved with the player (server.md F12).
 *
 * <p>Safety (permadeath): sitting is refused in danger or combat ({@link DangerSweep#safeToOpen}); a sitter stands up at
 * once on damage or a mob targeting him, and every {@code safety.sweep-ticks} on any danger of the casino's sweep;
 * also when the seat block goes, at quit/kick (before the player is saved), death, plugin disable, teleport and world
 * change. Every dismount, also vanilla's sneak dismount, is steered (the {@code DISMOUNT} teleport) to the mod's spot:
 * front, right, left, behind of the seat at dy 0, +1, -1 with vanilla's safe-dismount test, never into a block, lava,
 * fire or the void; if none is free, {@link Rescue#nearest} (two free cells on safe ground), and only then the top of
 * the seat block. The rescue net ({@code MachineService.rescue}) moves a player found inside a decoration barrier at
 * join (a crash while sitting) or on suffocation. Thread: MAIN.
 */
public final class SeatService {

    static final NamespacedKey SEAT = Keys.key("seat");

    /** One occupied seat. */
    record Seat(UUID player, ItemDisplay vehicle, UUID deco, Pos cell) {
    }

    private final DisplayDecoService deco;
    private final MenuSafety safety;
    private final DangerSweep sweep;
    private final Supplier<Messages> messages;
    private final Map<UUID, Seat> byPlayer = new HashMap<>();
    private final Map<Pos, Seat> byCell = new HashMap<>();
    /** Players whose dismount is under way: where the DISMOUNT teleport must take them. */
    private final Map<UUID, Location> landing = new HashMap<>();
    /** Players {@link #stand} is dismounting right now (their landing is already set). */
    private final java.util.Set<UUID> standing = new java.util.HashSet<>();
    /** Vehicles whose rider left; removed on the next tick (never inside the dismount event). */
    private final List<ItemDisplay> vacated = new java.util.ArrayList<>();
    private final SeatListener listener;

    public SeatService(DisplayDecoService deco, MenuSafety safety, DangerSweep sweep, Supplier<Messages> messages) {
        this.deco = deco;
        this.safety = safety;
        this.sweep = sweep;
        this.messages = messages;
        this.listener = new SeatListener(this);
    }

    public Listener listener() {
        return listener;
    }

    /** True if {@code vehicle} is a casino seat (the danger sweep's vehicle rule exempts it). */
    public boolean isSeat(Entity vehicle) {
        return vehicle != null && vehicle.getPersistentDataContainer().has(SEAT);
    }

    public boolean seated(Player player) {
        return byPlayer.containsKey(player.getUniqueId());
    }

    // ---- sitting ---------------------------------------------------------------------------------------------------

    /**
     * {@code CasinoSeatBlock/CasinoSofaBlock.useWithoutItem}: not sneaking (checked by the caller), not riding, not a
     * spectator; one rider per seat (an occupied seat consumes the click); the block above without collision; plus the
     * casino's danger and combat rule. Returns true if the click was consumed.
     */
    public boolean sit(Player player, DecoBlock seat) {
        if (player.isSneaking() || player.getGameMode() == GameMode.SPECTATOR || player.isInsideVehicle()
                || player.isDead() || player.isSleeping() || FakePlayers.isFake(player)) {
            return false;
        }
        Pos cell = seat.anchor();
        Seat taken = byCell.get(cell);
        if (taken != null && taken.vehicle().isValid() && !taken.vehicle().getPassengers().isEmpty()) {
            return true;
        }
        if (taken != null) {
            vacate(taken, false); // a stale seat (rider gone without an event): the mod discards it too
        }
        World world = player.getWorld();
        if (!aboveFree(world, cell)) {
            return true;
        }
        if (!sweep.safeToOpen(player)) {
            messages.get().actionBar(player, Msg.ERROR_UNSAFE);
            return true;
        }
        DecoRecord r = seat.record();
        double[] at = SeatMath.position(r.type(), r.facing(), r.sofaBack());
        float yaw = SeatMath.yaw(r.type(), r.facing(), r.sofaBack());
        Location loc = new Location(world, cell.x() + at[0], cell.y() + at[1], cell.z() + at[2], yaw, 0);
        ItemDisplay vehicle = world.spawn(loc, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.getPersistentDataContainer().set(Keys.PART, PersistentDataType.STRING, seat.id().toString());
            d.getPersistentDataContainer().set(SEAT, PersistentDataType.STRING, seat.id().toString());
        });
        Location turned = player.getLocation();
        turned.setYaw(yaw);
        player.teleport(turned);
        if (!vehicle.addPassenger(player)) {
            vehicle.remove();
            return true;
        }
        Seat s = new Seat(player.getUniqueId(), vehicle, seat.id(), cell);
        byPlayer.put(player.getUniqueId(), s);
        byCell.put(cell, s);
        world.playSound(new Location(world, cell.x() + 0.5, cell.y() + 0.5, cell.z() + 0.5), Sound.ENTITY_CUSHION_SIT,
                SoundCategory.BLOCKS, 0.7F, 1.0F);
        return true;
    }

    /** {@code getCollisionShape(above).isEmpty()} (light carriers above have no collision; barriers have). */
    private boolean aboveFree(World world, Pos cell) {
        Pos above = cell.offset(0, 1, 0);
        if (above.y() >= world.getMaxHeight()) {
            return false;
        }
        Optional<DecoBlock> d = deco.at(above);
        if (d.isPresent()) {
            return d.get().type().carrier() == DecoType.Carrier.LIGHT;
        }
        return above.block(world).getCollisionShape().getBoundingBoxes().isEmpty();
    }

    // ---- standing up -----------------------------------------------------------------------------------------------

    /** Stands a sitter up at the mod's dismount spot (safe), with the cushion sound. */
    public void stand(Player player, String why) {
        Seat s = byPlayer.get(player.getUniqueId());
        if (s == null) {
            return;
        }
        Location spot = dismountSpot(player, s);
        landing.put(player.getUniqueId(), spot);
        standing.add(player.getUniqueId());
        try {
            player.leaveVehicle();
        } finally {
            standing.remove(player.getUniqueId());
            landing.remove(player.getUniqueId());
        }
        vacate(s, true);
        if (player.isValid() && !player.isDead() && player.getLocation().distanceSquared(spot) > 1e-4) {
            player.teleport(spot);
        }
        Log.get().debug("{} stood up from {} ({})", player.getName(), s.cell(), why);
    }

    /** A seat block went away (broken, verifier, chunk unload): its rider stands up first. */
    public void removed(DecoBlock block) {
        if (!block.type().seat()) {
            return;
        }
        Seat s = byCell.get(block.anchor());
        if (s == null) {
            return;
        }
        Player p = Bukkit.getPlayer(s.player());
        if (p != null && p.getVehicle() != null && p.getVehicle().equals(s.vehicle())) {
            stand(p, "seat removed");
        } else {
            vacate(s, false);
        }
    }

    /** A rider left the vehicle (any cause): forget the seat, the vehicle goes next tick. */
    private void vacate(Seat s, boolean sound) {
        byPlayer.remove(s.player(), s);
        byCell.remove(s.cell(), s);
        if (s.vehicle().isValid()) {
            if (sound) {
                s.vehicle().getWorld().playSound(s.vehicle().getLocation(), Sound.ENTITY_CUSHION_GET_UP,
                        SoundCategory.BLOCKS, 0.7F, 1.0F);
            }
            vacated.add(s.vehicle());
        }
    }

    /** EntityDismountEvent (not cancelled): steer the DISMOUNT teleport that follows to the safe spot. */
    void dismounted(Player player, Entity vehicle) {
        Seat s = byPlayer.get(player.getUniqueId());
        if (s == null || !s.vehicle().equals(vehicle)) {
            return;
        }
        if (!standing.contains(player.getUniqueId())) {
            landing.put(player.getUniqueId(), dismountSpot(player, s));
            vacate(s, true);
        }
    }

    /** The DISMOUNT teleport of a casino seat's rider: where it must go, or null. */
    Location landingOf(Player player) {
        return landing.remove(player.getUniqueId());
    }

    /**
     * The mod's dismount search ({@link SeatMath#candidates}) with vanilla's {@code findSafeDismountLocation}, then
     * the rescue search, then the top of the seat block. Keeps the player's view.
     */
    Location dismountSpot(Player player, Seat s) {
        World w = s.vehicle().isValid() ? s.vehicle().getWorld() : player.getWorld();
        Location v = s.vehicle().isValid() ? s.vehicle().getLocation() : player.getLocation();
        Pos origin = s.cell();
        float yaw = v.getYaw();
        Location view = player.getLocation();
        for (int[] c : SeatMath.candidates(yaw)) {
            Location safe = safeDismount(w, origin.x() + c[0], origin.y() + c[1], origin.z() + c[2]);
            if (safe != null) {
                safe.setYaw(view.getYaw());
                safe.setPitch(view.getPitch());
                return safe;
            }
        }
        Facing front = SeatMath.fromYRot(yaw);
        int[] spot = Rescue.nearest(origin.x(), origin.y() + 1, origin.z(), front.step(),
                (x, y, z) -> standable(w, x, y, z));
        if (spot != null) {
            return new Location(w, spot[0] + 0.5, spot[1], spot[2] + 0.5, view.getYaw(), view.getPitch());
        }
        return new Location(w, v.getX(), origin.y() + 1.0, v.getZ(), view.getYaw(), view.getPitch());
    }

    /**
     * Vanilla {@code DismountHelper.findSafeDismountLocation(PLAYER, level, pos, true)}: no dangerous block in the
     * cell (or below it when standing on it), a floor below 1.0 (the cell's collision top or the full block below),
     * the player's box free there, no portal, inside the world border; null otherwise.
     */
    static Location safeDismount(World w, int x, int y, int z) {
        if (y - 1 < w.getMinHeight() || y + 2 >= w.getMaxHeight()) {
            return null;
        }
        Block cell = w.getBlockAt(x, y, z);
        if (dangerous(cell)) {
            return null;
        }
        Block below = cell.getRelative(BlockFace.DOWN);
        double floor = SeatMath.floorHeight(top(cell), hasCollision(cell), top(below), hasCollision(below));
        if (!SeatMath.floorValid(floor)) {
            return null;
        }
        if (floor <= 0.0 && dangerous(below)) {
            return null;
        }
        Location at = new Location(w, x + 0.5, y + floor, z + 0.5);
        BoundingBox box = new BoundingBox(at.getX() - 0.3, at.getY(), at.getZ() - 0.3, at.getX() + 0.3,
                at.getY() + 1.8, at.getZ() + 0.3);
        if (w.hasCollisionsIn(box) || Tag.INVALID_SPAWN_INSIDE.isTagged(cell.getType())
                || Tag.INVALID_SPAWN_INSIDE.isTagged(cell.getRelative(BlockFace.UP).getType())
                || !w.getWorldBorder().isInside(at)) {
            return null;
        }
        return at;
    }

    /** {@code DismountHelper.nonClimbableShape}: ladders, vines and open trapdoors count as no floor. */
    private static boolean hasCollision(Block b) {
        if (Tag.CLIMBABLE.isTagged(b.getType()) || b.getBlockData() instanceof TrapDoor t && t.isOpen()) {
            return false;
        }
        return !b.getCollisionShape().getBoundingBoxes().isEmpty();
    }

    private static double top(Block b) {
        double top = Double.NEGATIVE_INFINITY;
        if (Tag.CLIMBABLE.isTagged(b.getType()) || b.getBlockData() instanceof TrapDoor t && t.isOpen()) {
            return top;
        }
        for (BoundingBox box : b.getCollisionShape().getBoundingBoxes()) {
            top = Math.max(top, box.getMaxY());
        }
        return top;
    }

    /** {@code EntityType.isBlockDangerous} for a player: burning blocks, wither rose, berry bush, cactus, powder snow. */
    static boolean dangerous(Block b) {
        Material m = b.getType();
        if (Tag.FIRE.isTagged(m) || m == Material.LAVA || m == Material.MAGMA_BLOCK || m == Material.LAVA_CAULDRON
                || m == Material.WITHER_ROSE || m == Material.SWEET_BERRY_BUSH || m == Material.CACTUS
                || m == Material.POWDER_SNOW) {
            return true;
        }
        return b.getBlockData() instanceof Campfire c && c.isLit();
    }

    /** Feet at (x, y, z): two free cells without fluid or danger, safe full ground below (the rescue net's rule). */
    static boolean standable(World w, int x, int y, int z) {
        if (y <= w.getMinHeight() || y + 1 >= w.getMaxHeight()) {
            return false;
        }
        for (int dy = 0; dy < 2; dy++) {
            Block b = w.getBlockAt(x, y + dy, z);
            if (!b.isPassable() || b.isLiquid() || dangerous(b)
                    || w.getFluidData(x, y + dy, z).getFluidType() != org.bukkit.Fluid.EMPTY) {
                return false;
            }
        }
        Block ground = w.getBlockAt(x, y - 1, z);
        return ground.getBlockData().isFaceSturdy(BlockFace.UP, BlockSupport.FULL) && !dangerous(ground)
                && !ground.isLiquid();
    }

    // ---- rescue net --------------------------------------------------------------------------------------------------

    /**
     * {@code MachineService.rescue}'s second half: a player whose box overlaps a decoration barrier (logged in after a
     * crash while sitting, an ender pearl, a teleport) is moved to the nearest standable spot; returns true if moved.
     */
    public boolean rescue(Player player, String cause) {
        if (seated(player)) {
            return false;
        }
        World world = player.getWorld();
        BoundingBox box = player.getBoundingBox();
        String w = world.getKey().asString();
        DecoBlock inside = null;
        for (int x = (int) Math.floor(box.getMinX()); x <= (int) Math.floor(box.getMaxX() - 1e-6) && inside == null;
             x++) {
            for (int y = (int) Math.floor(box.getMinY()); y <= (int) Math.floor(box.getMaxY() - 1e-6)
                    && inside == null; y++) {
                for (int z = (int) Math.floor(box.getMinZ()); z <= (int) Math.floor(box.getMaxZ() - 1e-6); z++) {
                    Optional<DecoBlock> d = deco.at(new Pos(w, x, y, z));
                    if (d.isPresent() && d.get().type().carrier() == DecoType.Carrier.BARRIER) {
                        inside = d.get();
                        break;
                    }
                }
            }
        }
        if (inside == null) {
            return false;
        }
        Location at = player.getLocation();
        int[] spot = Rescue.nearest(at.getBlockX(), at.getBlockY(), at.getBlockZ(), inside.record().facing().step(),
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
        Log.get().warn("{} was inside {} at {} ({}): moved to {} {} {}", player.getName(), inside.type().id(),
                inside.anchor(), cause, target.getBlockX(), target.getBlockY(), target.getBlockZ());
        return true;
    }

    // ---- ticking -----------------------------------------------------------------------------------------------------

    /**
     * Every tick: removes vacated vehicles and seats whose rider is gone (teleport, world change, death without an
     * event); every {@code safety.sweep-ticks} stands up sitters in danger or combat.
     */
    public void tick(long tick) {
        landing.clear(); // a dismount steers its teleport in the same tick; a teleport-away leaves none behind
        for (ItemDisplay v : vacated) {
            if (v.isValid()) {
                v.remove();
            }
        }
        vacated.clear();
        boolean sweepNow = tick % Math.max(1, deco.ctx().config().get().safety().sweepTicks()) == 0;
        for (Seat s : List.copyOf(byPlayer.values())) {
            Player p = Bukkit.getPlayer(s.player());
            if (p == null || !s.vehicle().isValid() || p.getVehicle() == null || !p.getVehicle().equals(s.vehicle())) {
                vacate(s, false);
                continue;
            }
            if (deco.at(s.cell()).isEmpty()) {
                stand(p, "seat gone");
                continue;
            }
            if (sweepNow) {
                String danger = !safety.canOpen(p) ? "combat" : sweep.danger(p);
                if (danger != null) {
                    stand(p, "danger: " + danger);
                    messages.get().actionBar(p, Msg.DECO_SEAT_DANGER);
                }
            }
        }
    }

    /** Plugin disable: every sitter stands up at a safe spot (Paper saves the players after onDisable). */
    public void disable() {
        for (Seat s : List.copyOf(byPlayer.values())) {
            Player p = Bukkit.getPlayer(s.player());
            if (p != null) {
                stand(p, "plugin disable");
            } else {
                vacate(s, false);
            }
        }
        for (ItemDisplay v : vacated) {
            if (v.isValid()) {
                v.remove();
            }
        }
        vacated.clear();
        landing.clear();
    }
}
