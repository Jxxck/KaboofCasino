package de.tigges.kaboofcasino.music;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The verbatim port against the mod's specification (games.md 15) and golden values computed independently: a
 * re-implementation of {@code java.util.Random}'s LCG and the Fisher-Yates walk in Python
 * ({@code s4-music/tools/timeline_golden.py}), so a broken port and a broken check cannot agree by accident.
 */
class MusicTimelineTest {

    @Test
    void tracksAndCycleAreTheMods() {
        assertArrayEquals(new String[] {"airport_lounge", "backbay_lounge", "jazz_brunch", "george_street_shuffle",
            "cool_vibes", "bossa_antigua"}, MusicTimeline.IDS);
        assertArrayEquals(new long[] {307700, 266600, 323200, 268400, 218400, 283400}, MusicTimeline.LENGTH_MS);
        assertEquals(1_667_700L, Arrays.stream(MusicTimeline.LENGTH_MS).sum());
        assertEquals(Arrays.stream(MusicTimeline.LENGTH_MS).sum(), MusicTimeline.CYCLE_MS);
        for (long ms : MusicTimeline.LENGTH_MS) {
            assertEquals(0, ms % 50, "whole ticks");
        }
    }

    @Test
    void orderMatchesTheGoldenValues() {
        assertArrayEquals(new int[] {4, 1, 2, 5, 3, 0}, MusicTimeline.order(0L));
        assertArrayEquals(new int[] {4, 2, 0, 1, 5, 3}, MusicTimeline.order(1L));
        assertArrayEquals(new int[] {1, 0, 4, 5, 3, 2}, MusicTimeline.order(42L));
        assertArrayEquals(new int[] {3, 1, 2, 4, 0, 5}, MusicTimeline.order(-1L));
        assertArrayEquals(new int[] {0, 2, 1, 3, 5, 4}, MusicTimeline.order(123456789012345L));
        assertArrayEquals(new int[] {4, 1, 2, 5, 3, 0}, MusicTimeline.order(Long.MIN_VALUE));
        assertArrayEquals(new int[] {3, 1, 2, 4, 0, 5}, MusicTimeline.order(Long.MAX_VALUE));
        assertArrayEquals(new int[] {0, 2, 1, 4, 3, 5}, MusicTimeline.order(-7046029254386353131L));
    }

    @Test
    void positionsMatchTheGoldenValues() {
        long[][] golden = {
            // seed, elapsed ms, track, offset, cycle
            {42, -5, 1, 0, 0}, {42, 0, 1, 0, 0}, {42, 1, 1, 1, 0}, {42, 307699, 0, 41099, 0},
            {42, 307700, 0, 41100, 0}, {42, 999999, 5, 207299, 0}, {42, 1667699, 2, 323199, 0},
            {42, 1667700, 1, 0, 1}, {42, 5000000, 2, 320100, 2}, {42, 123456789, 1, 46989, 74},
            {-1, -5, 3, 0, 0}, {-1, 307699, 1, 39299, 0}, {-1, 999999, 4, 141799, 0}, {-1, 1667699, 5, 283399, 0},
            {-1, 1667700, 3, 0, 1}, {-1, 5000000, 5, 280300, 2}, {-1, 123456789, 3, 46989, 74},
            {123456789012345L, 307699, 0, 307699, 0}, {123456789012345L, 307700, 2, 0, 0},
            {123456789012345L, 999999, 3, 102499, 0}, {123456789012345L, 1667699, 4, 218399, 0},
            {123456789012345L, 5000000, 4, 215300, 2}, {123456789012345L, 123456789, 0, 46989, 74},
        };
        for (long[] g : golden) {
            MusicTimeline.Phase p = MusicTimeline.at(g[1], g[0]);
            assertEquals(new MusicTimeline.Phase((int) g[2], g[3], g[4]), p, "seed " + g[0] + " at " + g[1]);
        }
    }

    @Test
    void theWalkFollowsTheOrderAndCoversEveryMillisecondOnce() {
        Random r = new Random(7);
        for (int n = 0; n < 200; n++) {
            long seed = r.nextLong();
            int[] order = MusicTimeline.order(seed);
            int[] sorted = order.clone();
            Arrays.sort(sorted);
            assertArrayEquals(new int[] {0, 1, 2, 3, 4, 5}, sorted, "a permutation");
            long start = 0;
            for (int track : order) {
                assertEquals(new MusicTimeline.Phase(track, 0, 0), MusicTimeline.at(start, seed));
                long last = start + MusicTimeline.LENGTH_MS[track] - 1;
                assertEquals(new MusicTimeline.Phase(track, MusicTimeline.LENGTH_MS[track] - 1, 0),
                        MusicTimeline.at(last, seed));
                start += MusicTimeline.LENGTH_MS[track];
            }
            assertEquals(MusicTimeline.CYCLE_MS, start);
            assertEquals(new MusicTimeline.Phase(order[0], 0, 3), MusicTimeline.at(3 * MusicTimeline.CYCLE_MS, seed));
        }
    }

    @Test
    void gameTimeTicksAreFiftyMilliseconds() {
        // CasinoSoundClient: MusicTimeline.at((gameTime - epoch) * 50, seed)
        long epoch = 33_000_000L;
        long seed = 42L;
        int[] order = MusicTimeline.order(seed);
        long gameTime = epoch + MusicTimeline.LENGTH_MS[order[0]] / 50 + 20;
        MusicTimeline.Phase p = MusicTimeline.at((gameTime - epoch) * 50L, seed);
        assertEquals(new MusicTimeline.Phase(order[1], 1000, 0), p);
        assertTrue(MusicTimeline.at((epoch - 5 - epoch) * 50L, seed).offsetMs() == 0, "before the epoch: start");
    }
}
