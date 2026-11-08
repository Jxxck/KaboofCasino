package de.tigges.kaboofcasino.deco.display;

import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.machine.Facing;
import de.tigges.kaboofcasino.machine.MachineService;
import de.tigges.kaboofcasino.util.Log;
import de.tigges.kaboofcasino.util.Pos;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Fluid;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.SoundGroup;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

/**
 * Placing decoration on plugin carriers (barrier, light) from its paper item, by the mod's rules
 * ({@code getStateForPlacement} + {@code canSurvive} of each block) plus the casino's: spawn protection for non-ops, no
 * barrier where an offline player logged out (or within a block of it), no entity inside a barrier, multi-block types
 * inside one chunk, fluids only where the mod waterlogs. Every carrier is announced with a {@code BlockPlaceEvent}
 * (protection and LOD plugins; any veto restores all). Stairs and slabs are placed by vanilla; {@link DecoListener}
 * only adds the casino rules and the record. Thread: MAIN.
 */
final class DecoPlacer {

    /** Outcome of a placement. */
    enum Result {
        PLACED,
        /** The mod would not place it (no space, no support, no wall): silent like the mod. */
        INVALID,
        CHUNK_BORDER, LOGOUT_SPOT, DENIED,
        /** Another player within two blocks of an unsupported barrier deco ({@link DecoType#playerClearance}). */
        PLAYER_NEAR
    }

    /** A planned placement: cells, record data, carrier data per cell. */
    record Plan(DecoType type, Pos anchor, Facing facing, int state, List<Pos> cells, List<BlockData> carriers) {
    }

    private final DisplayDecoService service;
    private int[] lastShift;

    DecoPlacer(DisplayDecoService service) {
        this.service = service;
    }

    // ---- planning (the mod's getStateForPlacement) -----------------------------------------------------------------

    /**
     * The mod's placement of {@code type} for a click on {@code clicked}'s {@code face}: target cell (the clicked block
     * if replaceable, else the one in front of the face), facing and state; null if the mod refuses.
     */
    Plan plan(Player player, DecoType type, Block clicked, BlockFace face) {
        World w = clicked.getWorld();
        boolean replaceClicked = clicked.isReplaceable() && !service.isCell(Pos.of(clicked));
        Block target = replaceClicked ? clicked : clicked.getRelative(face);
        Pos pos = Pos.of(target);
        Facing towardsPlayer = Facing.towardsPlayer(player.getFacing());
        float yaw = player.getLocation().getYaw();
        float pitch = player.getLocation().getPitch();
        return switch (type.place()) {
            case FLOOR_PLAIN -> floor(type, w, pos, Facing.SOUTH, 0);
            case FLOOR -> floor(type, w, pos, towardsPlayer, 0);
            case STACKED -> floor(type, w, pos, towardsPlayer, 1);
            case SEAT -> aboveFree(w, pos) ? floor(type, w, pos, towardsPlayer, 0) : null;
            case SOFA -> {
                if (!aboveFree(w, pos)) {
                    yield null;
                }
                int facing = DecoModels.cardinal(towardsPlayer);
                yield floor(type, w, pos, towardsPlayer, DecoRecord.sofaState(0, SofaLayout.placedBack(facing)));
            }
            case WALL -> {
                for (BlockFace look : LookDirections.placing(yaw, pitch, face, replaceClicked)) {
                    if (look == BlockFace.UP || look == BlockFace.DOWN) {
                        continue;
                    }
                    Facing f = Facing.of(look.getOppositeFace());
                    if (service.wall(w, pos, f)) {
                        yield plan(type, pos, f, 0, w);
                    }
                }
                yield null;
            }
            case SIGN -> {
                if (face == BlockFace.UP || face == BlockFace.DOWN) {
                    yield null;
                }
                Facing f = Facing.of(face);
                yield service.wall(w, pos, f) ? plan(type, pos, f, 0, w) : null;
            }
            case LARGE_SIGN -> {
                if (face == BlockFace.UP || face == BlockFace.DOWN) {
                    yield null;
                }
                Facing f = Facing.of(face);
                for (int part = 0; part < 6; part++) {
                    if (!service.wall(w, DecoGeometry.signPart(pos, f, part), f)) {
                        yield null;
                    }
                }
                yield plan(type, pos, f, 0, w);
            }
            case SPEAKER -> {
                for (BlockFace look : LookDirections.placing(yaw, pitch, face, replaceClicked)) {
                    int attach;
                    Facing f;
                    if (look == BlockFace.UP || look == BlockFace.DOWN) {
                        attach = look == BlockFace.UP ? DecoRecord.FACE_CEILING : DecoRecord.FACE_FLOOR;
                        f = towardsPlayer; // SpeakerBlock: floor and ceiling face the player
                    } else {
                        attach = DecoRecord.FACE_WALL;
                        f = Facing.of(look.getOppositeFace());
                    }
                    boolean ok = switch (attach) {
                        case DecoRecord.FACE_CEILING -> service.sturdy(w, pos.offset(0, 1, 0), BlockFace.DOWN, false);
                        case DecoRecord.FACE_FLOOR -> service.sturdy(w, pos.offset(0, -1, 0), BlockFace.UP, false);
                        default -> service.wall(w, pos, f);
                    };
                    if (ok) {
                        yield plan(type, pos, f, DecoRecord.speakerState(false, attach), w);
                    }
                }
                yield null;
            }
            case TV -> {
                // CasinoTelevisionBlock.getStateForPlacement: the clicked side, a sturdy wall behind every cell
                if (face == BlockFace.UP || face == BlockFace.DOWN) {
                    yield null;
                }
                Facing f = Facing.of(face);
                int columns = type.tvColumns();
                for (int part = 0; part < columns * columns; part++) {
                    if (!service.wall(w, DecoGeometry.wallPart(pos, f, columns, part), f)) {
                        yield null;
                    }
                }
                yield plan(type, pos, f, 0, w);
            }
            case FREE -> plan(type, pos, Facing.SOUTH, 0, w);
            case CHANDELIER -> {
                // the clicked cell is the top (under the ceiling); the body hangs 1 or 2 chain blocks lower
                if (!service.sturdy(w, pos.offset(0, 1, 0), BlockFace.DOWN, true)) {
                    yield null;
                }
                int length = chainFor(w, pos);
                yield plan(type, pos.offset(0, -length, 0), Facing.SOUTH, length, w);
            }
            case VANILLA -> null;
        };
    }

    private Plan floor(DecoType type, World w, Pos pos, Facing facing, int state) {
        return service.sturdy(w, pos.offset(0, -1, 0), BlockFace.UP, true) ? plan(type, pos, facing, state, w) : null;
    }

    /** {@code getCollisionShape(above).isEmpty()} (seats and the sofa need room for the sitter's head). */
    boolean aboveFree(World w, Pos pos) {
        Pos above = pos.offset(0, 1, 0);
        if (above.y() >= w.getMaxHeight()) {
            return false;
        }
        if (service.isCell(above)) {
            return service.at(above).map(d -> d.type().carrier() == DecoType.Carrier.LIGHT).orElse(true);
        }
        return above.block(w).getCollisionShape().getBoundingBoxes().isEmpty();
    }

    /** {@code CasinoChandelierBlock.chainFor}: 2 chain blocks if at least 6 free cells hang below the top, else 1. */
    static int chainFor(World w, Pos top) {
        int free = 0;
        Pos p = top;
        while (free < 6 && p.y() >= w.getMinHeight() && p.y() < w.getMaxHeight()
                && (!w.isChunkLoaded(p.chunkX(), p.chunkZ())
                || p.block(w).getCollisionShape().getBoundingBoxes().isEmpty())) {
            free++;
            p = p.offset(0, -1, 0);
        }
        return free >= 6 ? 2 : 1;
    }

    private Plan plan(DecoType type, Pos anchor, Facing facing, int state, World w) {
        List<Pos> cells = DecoGeometry.cells(type, anchor, facing, state);
        List<BlockData> carriers = new ArrayList<>();
        DecoRecord probe = new DecoRecord(new UUID(0, 0), type, anchor, facing, state, new UUID(0, 0), 0);
        for (Pos p : cells) {
            if (p.y() < w.getMinHeight() || p.y() >= w.getMaxHeight()) {
                return null;
            }
            boolean wet = waterSource(p.block(w)) && type.waterloggable();
            if (type.carrier() == DecoType.Carrier.BARRIER) {
                BlockData barrier = Material.BARRIER.createBlockData();
                if (barrier instanceof Waterlogged wl) {
                    wl.setWaterlogged(wet);
                }
                carriers.add(barrier);
            } else {
                carriers.add(DisplayDecoService.lightData(DisplayDecoService.lightLevel(probe, p, wet), wet));
            }
        }
        return new Plan(type, anchor, facing, state, cells, carriers);
    }

    /** {@code getFluidState(pos).getType() == Fluids.WATER}: a water source (also in seagrass or kelp). */
    private static boolean waterSource(Block b) {
        return b.getWorld().getFluidData(b.getX(), b.getY(), b.getZ()).getFluidType() == Fluid.WATER;
    }

    // ---- placing ---------------------------------------------------------------------------------------------------

    /** Places a planned decoration for {@code player} (the caller takes the item on {@link Result#PLACED}). */
    Result place(Player player, Plan plan, Block clicked, EquipmentSlot hand, ItemStack item) {
        World w = clicked.getWorld();
        if (plan.cells().size() > 1) {
            boolean onWall = plan.type().place() == DecoType.Place.LARGE_SIGN
                    || plan.type().place() == DecoType.Place.TV;
            int[] shift = DecoGeometry.chunkFit(plan.cells(), onWall ? List.of(DecoGeometry.ccw(plan.facing()))
                    : List.of(new int[] {1, 0}, new int[] {0, 1}));
            if (shift != null) {
                lastShift = shift;
                return Result.CHUNK_BORDER;
            }
        }
        boolean collision = plan.type().carrier() == DecoType.Carrier.BARRIER;
        for (Pos p : plan.cells()) {
            Block b = p.block(w);
            if (!b.isReplaceable() || service.isCell(p) || service.ctx().machines().isCarrier(p)
                    || !w.getWorldBorder().isInside(new Location(w, p.x() + 0.5, p.y(), p.z() + 0.5))) {
                return Result.INVALID;
            }
            if (fluidRefused(plan.type(), b)) {
                return Result.INVALID;
            }
            if (collision) {
                BoundingBox box = new BoundingBox(p.x(), p.y(), p.z(), p.x() + 1, p.y() + 1, p.z() + 1);
                for (Entity e : w.getNearbyEntities(box)) {
                    if (e instanceof LivingEntity living && !(living instanceof Player pl
                            && pl.getGameMode() == GameMode.SPECTATOR)) {
                        return Result.INVALID;
                    }
                }
                if (service.ctx().config().get().machines().refuseLogoutSpots()
                        && service.ctx().machines().logouts().blocks(p)) {
                    return Result.LOGOUT_SPOT;
                }
            }
        }
        if (plan.type().playerClearance()) {
            for (Player other : w.getPlayers()) {
                if (!other.equals(player) && other.getGameMode() != GameMode.SPECTATOR
                        && MachineService.near(other.getBoundingBox(), plan.cells(), MachineService.PLAYER_CLEARANCE)) {
                    return Result.PLAYER_NEAR;
                }
            }
        }
        if (!player.isOp()) {
            for (Pos p : plan.cells()) {
                if (underSpawnProtection(w, p)) {
                    return Result.DENIED;
                }
            }
        }
        List<BlockState> before = new ArrayList<>();
        for (int i = 0; i < plan.cells().size(); i++) {
            Block b = plan.cells().get(i).block(w);
            before.add(b.getState());
            b.setBlockData(plan.carriers().get(i), false);
        }
        for (int i = 0; i < plan.cells().size(); i++) {
            Block placed = plan.cells().get(i).block(w);
            BlockPlaceEvent event = new BlockPlaceEvent(placed, before.get(i), clicked, item, player, true, hand);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled() || !event.canBuild()) {
                for (BlockState state : before) {
                    state.update(true, false);
                }
                return Result.DENIED;
            }
        }
        DecoRecord record = new DecoRecord(UUID.randomUUID(), plan.type(), plan.anchor(), plan.facing(), plan.state(),
                player.getUniqueId(), System.currentTimeMillis());
        service.add(record, w);
        placeSound(plan.type(), w, plan.anchor());
        if (plan.type() == DecoType.DEALER_STATION) {
            service.dealerStationPlaced(record, player);
        }
        Log.get().debug("{} placed {} at {} facing {}", player.getName(), plan.type().id(), plan.anchor(),
                plan.facing());
        return Result.PLACED;
    }

    /**
     * Fluids: only the mod's waterloggable blocks (sconce, stacked decor) go into water (a source makes the carrier
     * waterlogged, flowing water is replaced); every other cell with any fluid (water, lava, waterlogged blocks,
     * seagrass, bubble columns) is refused, as for the machines (a barrier would hold or hide it).
     */
    static boolean fluidRefused(DecoType type, Block b) {
        Fluid fluid = b.getWorld().getFluidData(b.getX(), b.getY(), b.getZ()).getFluidType();
        if (fluid == Fluid.EMPTY) {
            return false;
        }
        boolean water = fluid == Fluid.WATER || fluid == Fluid.FLOWING_WATER;
        return !(water && type.waterloggable());
    }

    static void placeSound(DecoType type, World w, Pos p) {
        SoundGroup g = type.sound().createBlockData().getSoundGroup();
        w.playSound(new Location(w, p.x() + 0.5, p.y() + 0.5, p.z() + 0.5), g.getPlaceSound(), SoundCategory.BLOCKS,
                (g.getVolume() + 1.0F) / 2.0F, g.getPitch() * 0.8F);
    }

    /** Vanilla spawn protection ({@code spawn-protection} around the overworld spawn) for non-ops. */
    static boolean underSpawnProtection(World world, Pos pos) {
        int radius = Bukkit.getSpawnRadius();
        if (radius <= 0 || !world.equals(Bukkit.getWorlds().getFirst())) {
            return false;
        }
        Location spawn = world.getSpawnLocation();
        return Math.max(Math.abs(pos.x() - spawn.getBlockX()), Math.abs(pos.z() - spawn.getBlockZ())) <= radius;
    }

    /** Tells the player why a placement was refused (the mod's own refusals stay silent, like the mod). */
    void tell(Player player, Result result, Messages messages) {
        switch (result) {
            case CHUNK_BORDER -> {
                int[] s = lastShift == null ? new int[] {0, 0} : lastShift;
                int blocks = Math.max(Math.abs(s[0]), Math.abs(s[1]));
                String direction = direction(player.getFacing(), s);
                if (blocks > 1) {
                    messages.actionBar(player, Msg.DECO_CHUNK_BORDER_FAR, Messages.text("direction", direction),
                            Messages.text("blocks", Integer.toString(blocks)));
                } else {
                    messages.actionBar(player, Msg.DECO_CHUNK_BORDER, Messages.text("direction", direction));
                }
            }
            case LOGOUT_SPOT -> messages.actionBar(player, Msg.DECO_LOGOUT_SPOT);
            case PLAYER_NEAR -> messages.actionBar(player, Msg.DECO_PLAYER_NEAR);
            case DENIED -> messages.actionBar(player, Msg.MACHINE_PLACE_DENIED);
            default -> {
            }
        }
    }

    /** A world shift in words for the player looking along {@code looks} (as {@code MachineService.direction}). */
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
}
