package de.tigges.kaboofcasino.deco.display;

import de.tigges.kaboofcasino.util.Pos;
import java.util.List;
import java.util.Random;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Player;

/**
 * The candles of the chandelier and the wall sconce ({@code CasinoChandelierBlock.animate/candle},
 * {@code CasinoWallSconceBlock.animate}): the mod draws them in the client's {@code animateTick}, which the client calls
 * for 667 random blocks per tick in two triangular distributions around the player (radius 16 and 32). Here every
 * candle block rolls, per player in range, that player's expected number of calls and sends the particles (and the
 * occasional crackle) to that player only, so each player sees what his own client would draw. The switched-on
 * speaker's note particle is the music's ({@code music.SpeakerNotes}). Thread: MAIN.
 */
final class DecoFlames {

    /** {@code DecorGeometry.CHANDELIER_FLAMES} (px in each body part's cell; part 4 has none). */
    static final double[][][] CHANDELIER = {
            {{4.598, 12.42, 15.964}, {9.151, 12.42, 9.151}, {15.964, 12.42, 4.598}, {15.515, 15.08, 15.515}},
            {{8.0, 12.42, 3.0}, {8.0, 15.08, 12.0}},
            {{0.036, 12.42, 4.598}, {6.849, 12.42, 9.151}, {11.402, 12.42, 15.964}, {0.485, 15.08, 15.515}},
            {{3.0, 12.42, 8.0}, {12.0, 15.08, 8.0}},
            {},
            {{13.0, 12.42, 8.0}, {4.0, 15.08, 8.0}},
            {{15.964, 12.42, 11.402}, {9.151, 12.42, 6.849}, {4.598, 12.42, 0.036}, {15.515, 15.08, 0.485}},
            {{8.0, 12.42, 13.0}, {8.0, 15.08, 4.0}},
            {{11.402, 12.42, 0.036}, {6.849, 12.42, 6.849}, {0.036, 12.42, 11.402}, {0.485, 15.08, 0.485}}};
    /** {@code CasinoDecorGeometry.SCONCE_FLAMES}, south frame. */
    static final double[][] SCONCE = {{4.0, 15.52, 7.5}, {12.0, 15.52, 7.5}};
    static final float SMOKE_CHANCE = 0.3F;
    static final float CHANDELIER_CRACKLE = 0.06F;
    static final float SCONCE_CRACKLE = 0.17F;

    private final DisplayDecoService service;
    private final Random random = new Random();

    DecoFlames(DisplayDecoService service) {
        this.service = service;
    }

    /**
     * Expected {@code animateTick} calls per client tick for a block at offset (dx, dy, dz) from the player's block:
     * {@code ClientLevel.animateTick} picks 667 positions per radius with {@code nextInt(r) - nextInt(r)} per axis,
     * so an offset d has probability {@code (r - |d|) / r^2} per axis.
     */
    static double rate(int dx, int dy, int dz) {
        return 667.0 * (axis(dx, 16) * axis(dy, 16) * axis(dz, 16) + axis(dx, 32) * axis(dy, 32) * axis(dz, 32));
    }

    private static double axis(int d, int r) {
        int a = Math.abs(d);
        return a >= r ? 0.0 : (double) (r - a) / ((double) r * r);
    }

    /** The blocks with candles (the only ones in {@link DisplayDecoService#flameBlocks}). */
    static boolean burns(DecoType type) {
        return type == DecoType.CASINO_CHANDELIER || type == DecoType.CHANDELIER_WALL_SCONCE;
    }

    /**
     * True if a player whose block is (dx, dy, dz) away from a candle block's anchor can see its flames: the client
     * animates blocks less than 32 blocks away per axis; the chandelier's cells lie one block around its anchor.
     */
    static boolean inRange(int dx, int dy, int dz) {
        return Math.abs(dx) <= 32 && Math.abs(dy) <= 32 && Math.abs(dz) <= 32;
    }

    /**
     * Every tick, only over the loaded chandeliers and sconces; the players of a world are fetched once per tick and
     * each candle block keeps only those in range before rolling per cell.
     */
    void tick() {
        java.util.Collection<DecoBlock> blocks = service.flameBlocks();
        if (blocks.isEmpty()) {
            return;
        }
        java.util.Map<String, List<Player>> playersByWorld = new java.util.HashMap<>();
        java.util.Map<String, World> worlds = new java.util.HashMap<>();
        List<Player> near = new java.util.ArrayList<>();
        for (DecoBlock b : blocks) {
            if (!b.spawned()) {
                continue;
            }
            Pos a = b.anchor();
            World world = worlds.computeIfAbsent(a.world(), k -> DisplayDecoService.world(a));
            if (world == null) {
                continue;
            }
            List<Player> players = playersByWorld.computeIfAbsent(a.world(), k -> world.getPlayers());
            near.clear();
            for (Player player : players) {
                Location at = player.getLocation();
                if (inRange(a.x() - at.getBlockX(), a.y() - at.getBlockY(), a.z() - at.getBlockZ())) {
                    near.add(player);
                }
            }
            if (near.isEmpty()) {
                continue;
            }
            if (b.type() == DecoType.CHANDELIER_WALL_SCONCE) {
                if (a.block(world).getBlockData() instanceof Waterlogged w && w.isWaterlogged()) {
                    continue;
                }
                double[][] points = new double[SCONCE.length][];
                for (int i = 0; i < SCONCE.length; i++) {
                    points[i] = DecoGeometry.turnPoint(SCONCE[i], b.record().facing().turns());
                }
                roll(near, a, points, SCONCE_CRACKLE);
            } else {
                for (int part = 0; part < 9; part++) {
                    if (CHANDELIER[part].length > 0) {
                        roll(near, DecoGeometry.chandelierBody(a, part), CHANDELIER[part], CHANDELIER_CRACKLE);
                    }
                }
            }
        }
    }

    private void roll(List<Player> players, Pos cell, double[][] points, float crackle) {
        for (Player player : players) {
            Location at = player.getLocation();
            int dx = cell.x() - at.getBlockX();
            int dy = cell.y() - at.getBlockY();
            int dz = cell.z() - at.getBlockZ();
            if (Math.abs(dx) >= 32 || Math.abs(dy) >= 32 || Math.abs(dz) >= 32) {
                continue;
            }
            double expected = rate(dx, dy, dz);
            if (random.nextDouble() >= expected) {
                continue;
            }
            for (double[] f : points) {
                candle(player, cell.x() + f[0] / 16.0, cell.y() + f[1] / 16.0, cell.z() + f[2] / 16.0, crackle);
            }
        }
    }

    /** {@code CasinoChandelierBlock.candle}. */
    private void candle(Player player, double x, double y, double z, float crackleChance) {
        float chance = random.nextFloat();
        if (chance < SMOKE_CHANCE) {
            player.spawnParticle(Particle.SMOKE, x, y, z, 1, 0, 0, 0, 0);
            if (chance < crackleChance) {
                player.playSound(new Location(player.getWorld(), x, y, z), Sound.BLOCK_CANDLE_AMBIENT,
                        SoundCategory.BLOCKS, 1.0F + random.nextFloat(), random.nextFloat() * 0.7F + 0.3F);
            }
        }
        player.spawnParticle(Particle.SMALL_FLAME, x, y, z, 1, 0, 0, 0, 0);
    }
}
