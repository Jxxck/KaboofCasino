package de.tigges.kaboofcasino.music;

import de.tigges.kaboofcasino.machine.Facing;

/**
 * The note particles of a switched-on speaker ({@code CasinoSoundClient.tick}): one {@code minecraft:note} every 32
 * ticks, staggered by {@code floorMod(gameTime + pos.asLong(), 32) == 0}, at the block centre + facing x 0.34
 * horizontally and y + 0.12, colour {@code nextInt(24) / 24}, seen within 32 blocks (the server's particle range is
 * exactly that). Pure.
 */
final class SpeakerNotes {

    static final int PERIOD = 32;
    static final double RANGE = 32.0;
    static final double FRONT = 0.34;
    static final double UP = 0.12;
    static final int COLOURS = 24;

    private SpeakerNotes() {
    }

    /**
     * {@code BlockPos.asLong} of 26.3 (checked with javap: x 26 bits at 38, z 26 bits at 12, y 12 bits at 0). Only
     * the mod's stagger uses it; stored positions are plain ints.
     */
    static long asLong(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) y & 0xFFFL) | ((long) z & 0x3FFFFFFL) << 12;
    }

    /** True on the speaker's note tick. */
    static boolean due(long gameTime, MusicStation.Cell pos) {
        return Math.floorMod(gameTime + asLong(pos.x(), pos.y(), pos.z()), PERIOD) == 0;
    }

    /** x, y, z of the note. */
    static double[] position(MusicStation.Cell pos, Facing facing) {
        int[] step = facing.step();
        return new double[] {pos.x() + 0.5 + step[0] * FRONT, pos.y() + 0.5 + UP, pos.z() + 0.5 + step[1] * FRONT};
    }

    /** The note particle's colour parameter for {@code roll} in 0..23. */
    static double colour(int roll) {
        return roll / (double) COLOURS;
    }
}
