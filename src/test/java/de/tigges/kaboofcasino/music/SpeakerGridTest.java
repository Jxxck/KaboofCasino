package de.tigges.kaboofcasino.music;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.machine.Facing;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Nearest-speaker selection within 48 blocks: the bucketed search equals a brute-force scan. */
class SpeakerGridTest {

    private static MusicStation.Cell c(int x, int y, int z) {
        return new MusicStation.Cell(x, y, z);
    }

    @Test
    void nearestIsMeasuredToTheBlockCentre() {
        SpeakerGrid g = new SpeakerGrid();
        g.put(c(10, 64, 10), Facing.NORTH);
        g.put(c(30, 64, 10), Facing.SOUTH);
        SpeakerGrid.Nearest n = g.nearest(12.5, 64.5, 10.5, 48);
        assertEquals(c(10, 64, 10), n.speaker().pos());
        assertEquals(4.0, n.distanceSq(), 1e-9);
        assertEquals(c(30, 64, 10), g.nearest(25, 64.5, 10.5, 48).speaker().pos());
    }

    @Test
    void theRangeIsStrict() {
        SpeakerGrid g = new SpeakerGrid();
        g.put(c(0, 64, 0), Facing.NORTH);
        assertNull(g.nearest(48.5, 64.5, 0.5, 48), "exactly 48 blocks: out (d^2 < 2304 in the mod)");
        assertEquals(c(0, 64, 0), g.nearest(48.49, 64.5, 0.5, 48).speaker().pos());
        assertNull(g.nearest(0.5, 64.5 + 48, 0.5, 48), "vertical distance counts too");
        assertEquals(c(0, 64, 0), g.nearest(0.5, 64.5 - 47.9, 0.5, 48).speaker().pos());
    }

    @Test
    void negativeCoordinatesAndBucketBorders() {
        SpeakerGrid g = new SpeakerGrid();
        g.put(c(-1, 70, -1), Facing.EAST);
        g.put(c(-65, 70, 63), Facing.WEST);
        g.put(c(64, 70, -64), Facing.NORTH);
        assertEquals(c(-1, 70, -1), g.nearest(0.5, 70.5, 0.5, 48).speaker().pos());
        assertEquals(c(-65, 70, 63), g.nearest(-40, 70.5, 60, 48).speaker().pos());
        assertEquals(c(64, 70, -64), g.nearest(100, 70.5, -64, 48).speaker().pos());
    }

    @Test
    void removedAndReplacedSpeakers() {
        SpeakerGrid g = new SpeakerGrid();
        g.put(c(5, 64, 5), Facing.NORTH);
        g.put(c(5, 64, 5), Facing.EAST);
        assertEquals(1, g.size());
        assertEquals(Facing.EAST, g.nearest(5, 64, 5, 48).speaker().facing());
        assertTrue(g.remove(c(5, 64, 5)));
        assertFalse(g.remove(c(5, 64, 5)));
        assertTrue(g.isEmpty());
        assertNull(g.nearest(5, 64, 5, 48));
    }

    @Test
    void matchesABruteForceScan() {
        Random r = new Random(11);
        SpeakerGrid g = new SpeakerGrid();
        List<MusicStation.Cell> all = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            MusicStation.Cell cell = c(r.nextInt(600) - 300, r.nextInt(200) - 60, r.nextInt(600) - 300);
            g.put(cell, Facing.values()[r.nextInt(4)]);
            if (!all.contains(cell)) {
                all.add(cell);
            }
        }
        for (int i = 0; i < 5000; i++) {
            double x = r.nextDouble() * 700 - 350;
            double y = r.nextDouble() * 260 - 70;
            double z = r.nextDouble() * 700 - 350;
            double best = 48 * 48;
            for (MusicStation.Cell cell : all) {
                double dx = cell.x() + 0.5 - x;
                double dy = cell.y() + 0.5 - y;
                double dz = cell.z() + 0.5 - z;
                best = Math.min(best, dx * dx + dy * dy + dz * dz);
            }
            SpeakerGrid.Nearest n = g.nearest(x, y, z, 48);
            if (best >= 48 * 48) {
                assertNull(n);
            } else {
                assertEquals(best, n.distanceSq(), 1e-9);
            }
        }
    }
}
