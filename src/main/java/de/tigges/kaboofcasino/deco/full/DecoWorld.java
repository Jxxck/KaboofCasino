package de.tigges.kaboofcasino.deco.full;

import de.tigges.kaboofcasino.deco.full.FullDecoStates.Entry;
import de.tigges.kaboofcasino.deco.full.FullDecoStates.Pilaster;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.structure.StructureRotation;

/**
 * Reading and writing deco states in the world. A block is deco only if its state is in {@link FullDecoStates};
 * every other mushroom state (natural caps and stems, the all-true default of vanilla placement, the spare states) is
 * never touched. Thread: MAIN.
 */
final class DecoWorld {

    private final Map<MushroomState, BlockData> data = new HashMap<>();

    DecoWorld() {
        for (Entry e : FullDecoStates.entries()) {
            data.put(e.state(), Bukkit.createBlockData(e.state().key()));
        }
    }

    static Material material(MushroomBlock block) {
        return switch (block) {
            case RED -> Material.RED_MUSHROOM_BLOCK;
            case BROWN -> Material.BROWN_MUSHROOM_BLOCK;
            case STEM -> Material.MUSHROOM_STEM;
        };
    }

    /** The mushroom state of block data; empty for every other block. */
    static Optional<MushroomState> state(BlockData blockData) {
        MushroomBlock block = switch (blockData.getMaterial()) {
            case RED_MUSHROOM_BLOCK -> MushroomBlock.RED;
            case BROWN_MUSHROOM_BLOCK -> MushroomBlock.BROWN;
            case MUSHROOM_STEM -> MushroomBlock.STEM;
            default -> null;
        };
        if (block == null || !(blockData instanceof MultipleFacing f)) {
            return Optional.empty();
        }
        return Optional.of(new MushroomState(block, f.hasFace(BlockFace.DOWN), f.hasFace(BlockFace.UP),
                f.hasFace(BlockFace.NORTH), f.hasFace(BlockFace.EAST), f.hasFace(BlockFace.SOUTH),
                f.hasFace(BlockFace.WEST)));
    }

    /** The deco entry of block data (exact reserved state only). */
    static Optional<Entry> entry(BlockData blockData) {
        return state(blockData).flatMap(FullDecoStates::lookup);
    }

    static Optional<Entry> entry(Block block) {
        return entry(block.getBlockData());
    }

    /** A fresh copy of a reserved state's block data. */
    BlockData blockData(MushroomState state) {
        BlockData d = data.get(state);
        if (d == null) {
            throw new IllegalArgumentException("not a reserved state: " + state);
        }
        return d.clone();
    }

    /**
     * True if Paper's {@code block-updates.disable-mushroom-block-updates} is on. Probed without side effects: with the
     * switch on, {@code HugeMushroomBlock.rotate} returns the state unchanged (the same flag that also disables
     * {@code updateShape} and {@code getStateForPlacement}); with it off a cap with only its north face turns east.
     */
    static boolean mushroomUpdatesDisabled() {
        BlockData probe = Bukkit.createBlockData(
                "minecraft:red_mushroom_block[down=false,east=false,north=true,south=false,up=false,west=false]");
        BlockData turned = probe.clone();
        turned.rotate(StructureRotation.CLOCKWISE_90);
        return turned.equals(probe);
    }

    static BlockFace face(Side side) {
        return switch (side) {
            case NORTH -> BlockFace.NORTH;
            case EAST -> BlockFace.EAST;
            case SOUTH -> BlockFace.SOUTH;
            case WEST -> BlockFace.WEST;
        };
    }

    static Side side(BlockFace face) {
        return switch (face) {
            case EAST -> Side.EAST;
            case SOUTH -> Side.SOUTH;
            case WEST -> Side.WEST;
            default -> Side.NORTH;
        };
    }

    /** The band a neighbour contributes to a pilaster (the mod's {@code CasinoFloorsAndWalls.band}). */
    static int band(Block block) {
        return entry(block).map(e -> e.block().band()).orElse(0);
    }

    /** The connections a pilaster at {@code block} shows now. */
    static Pilaster connections(Block block) {
        Pilaster target = PilasterChoice.target(side -> band(block.getRelative(face(side))));
        Set<Side> visible = EnumSet.noneOf(Side.class);
        for (Side side : Side.values()) {
            Block n = block.getRelative(face(side));
            if (!(n.getType().isOccluding() && n.getBlockData().isOccluding())) {
                visible.add(side);
            }
        }
        return PilasterChoice.choose(target, visible, FullDecoStates.PILASTERS);
    }

    /** Re-reads a pilaster's connections and writes the state if it changed; anything else is left alone. */
    boolean refresh(Block block) {
        Optional<Entry> entry = entry(block);
        if (entry.isEmpty() || entry.get().block().kind() != DecoBlock.Kind.PILASTER) {
            return false;
        }
        for (Side side : Side.values()) {
            Block n = block.getRelative(face(side));
            if (!n.getWorld().isChunkLoaded(n.getX() >> 4, n.getZ() >> 4)) {
                return false; // never load a chunk for looks; the next change there fixes it
            }
        }
        Entry wanted = FullDecoStates.pilaster(entry.get().block(), connections(block));
        if (wanted == null || wanted.equals(entry.get())) {
            return false;
        }
        block.setBlockData(blockData(wanted.state()), false);
        return true;
    }
}
