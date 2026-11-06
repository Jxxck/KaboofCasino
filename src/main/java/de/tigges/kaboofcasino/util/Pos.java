package de.tigges.kaboofcasino.util;

import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Block position with plain int coordinates. Never {@code Block#getBlockKey}: on this server the overworld reaches
 * Y 1023 and the packed key collides at y and y + 1024 (server.md F17, PostMarkt B13). Used as map key and stored as
 * separate fields/columns.
 *
 * @param world the world key, e.g. {@code minecraft:overworld} (never the Bukkit name)
 */
public record Pos(String world, int x, int y, int z) {

    public Pos {
        Objects.requireNonNull(world, "world");
    }

    public static Pos of(Block block) {
        return new Pos(block.getWorld().getKey().asString(), block.getX(), block.getY(), block.getZ());
    }

    public static Pos of(Location location) {
        return new Pos(location.getWorld().getKey().asString(), location.getBlockX(), location.getBlockY(),
                location.getBlockZ());
    }

    public Pos offset(int dx, int dy, int dz) {
        return new Pos(world, x + dx, y + dy, z + dz);
    }

    public int chunkX() {
        return x >> 4;
    }

    public int chunkZ() {
        return z >> 4;
    }

    /** Same packing as {@code Chunk#getChunkKey(int, int)}: x in the low 32 bits, z in the high 32 bits. */
    public long chunkKey() {
        return chunkKey(chunkX(), chunkZ());
    }

    public static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX & 0xffffffffL) | (((long) chunkZ & 0xffffffffL) << 32);
    }

    /** The block in {@code world}; the caller made sure the world matches. MAIN. */
    public Block block(World w) {
        return w.getBlockAt(x, y, z);
    }

    @Override
    public String toString() {
        return world + " " + x + " " + y + " " + z;
    }
}
