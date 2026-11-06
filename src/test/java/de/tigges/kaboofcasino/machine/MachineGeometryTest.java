package de.tigges.kaboofcasino.machine;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.util.Pos;
import java.util.List;
import java.util.Set;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

class MachineGeometryTest {

    @Test
    void facingFollowsTheMod() {
        assertEquals(Facing.SOUTH, Facing.towardsPlayer(BlockFace.NORTH));
        assertEquals(Facing.EAST, Facing.towardsPlayer(BlockFace.WEST));
        assertArrayEquals(new int[] {0, 1}, Facing.SOUTH.step());
        assertArrayEquals(new int[] {-1, 0}, Facing.WEST.step());
        assertArrayEquals(new int[] {0, -1}, Facing.NORTH.step());
        assertArrayEquals(new int[] {1, 0}, Facing.EAST.step());
        // SlotMachineLayout.toBlock: one turn maps (x, z) -> (16 - z, x)
        assertArrayEquals(new double[] {1, 0.5, 0.5}, Facing.WEST.toBlock(8, 8, 0), 1e-9);
        assertArrayEquals(new double[] {0.5, 0.5, 1}, Facing.SOUTH.toBlock(8, 8, 16), 1e-9);
        assertTrue(Facing.NORTH.sameAxis(Facing.SOUTH));
        assertFalse(Facing.NORTH.sameAxis(Facing.EAST));
    }

    @Test
    void slotFootprintIsAColumnUpToTheBuildLimit() {
        Pos anchor = new Pos("minecraft:overworld", 10, 1021, -5);
        for (Facing f : Facing.values()) {
            assertEquals(List.of(anchor, anchor.offset(0, 1, 0), anchor.offset(0, 2, 0)),
                    MachineType.SLOT_MACHINE.carriers(anchor, f));
        }
        assertEquals(0, MachineType.SLOT_MACHINE.bottom());
        assertEquals(0, MachineType.SLOT_MACHINE.rise());
    }

    @Test
    void wheelAndPlinkoAre3x3AroundTheAnchorAcrossTheFacing() {
        Pos anchor = new Pos("minecraft:overworld", 10, 64, -5);
        for (MachineType type : List.of(MachineType.FORTUNE_WHEEL, MachineType.PLINKO)) {
            assertEquals(1, type.rise(), "placed cell = part 1, anchor = part 4 one block up");
            assertEquals(-1, type.bottom());
            // facing south/north: the row runs along x; west/east: along z (the machine is one block deep)
            for (Facing f : Facing.values()) {
                List<Pos> c = type.carriers(anchor, f);
                assertEquals(9, c.size());
                assertEquals(9, new java.util.HashSet<>(c).size());
                for (Pos p : c) {
                    if (f == Facing.SOUTH || f == Facing.NORTH) {
                        assertEquals(anchor.z(), p.z());
                        assertTrue(Math.abs(p.x() - anchor.x()) <= 1);
                    } else {
                        assertEquals(anchor.x(), p.x());
                        assertTrue(Math.abs(p.z() - anchor.z()) <= 1);
                    }
                    assertTrue(Math.abs(p.y() - anchor.y()) <= 1);
                }
                // part 1 (bottom middle) is the placed cell below the anchor
                assertEquals(anchor.offset(0, -1, 0), c.get(1));
                assertEquals(anchor, c.get(4));
            }
        }
    }

    @Test
    void positionsNeverCollideAtY1023() {
        Pos a = new Pos("minecraft:overworld", 0, 1023, 0);
        Pos b = new Pos("minecraft:overworld", 0, -1, 0);
        assertFalse(a.equals(b));
        assertEquals(a.offset(0, 1, 0).y(), 1024);
        assertEquals(Pos.chunkKey(-1, 2), new Pos("w", -1, 0, 40).chunkKey());
    }

    @Test
    void pistonNeighbourRule() {
        Pos carrier = new Pos("w", 5, 64, 5);
        Set<Pos> carriers = Set.of(carrier);
        assertTrue(ProtectionRules.pistonMustCancel(new Pos("w", 0, 0, 0), List.of(carrier), carriers::contains));
        assertTrue(ProtectionRules.pistonMustCancel(new Pos("w", 5, 63, 5), List.of(), carriers::contains));
        assertTrue(ProtectionRules.pistonMustCancel(new Pos("w", 6, 64, 5), List.of(), carriers::contains));
        assertFalse(ProtectionRules.pistonMustCancel(new Pos("w", 7, 64, 5), List.of(), carriers::contains));
        assertFalse(ProtectionRules.pistonMustCancel(new Pos("w", 6, 65, 5), List.of(new Pos("w", 1, 1, 1)),
                carriers::contains));
    }

    @Test
    void breakSpeedLikeOakPlanksWithStrength3() {
        // by hand on the ground: 1 / 3 / 30 per tick = 90 ticks (4.5 s)
        assertEquals(90, Math.round(1 / BreakProgress.perTick(1, 0, 0, 1, 1, true)), 0);
        // iron axe (speed 6): 15 ticks
        assertEquals(15, Math.round(1 / BreakProgress.perTick(6, 0, 0, 1, 1, true)), 0);
        // in the air: five times slower
        assertEquals(450, Math.round(1 / BreakProgress.perTick(1, 0, 0, 1, 1, false)), 0);
        // haste II: x1.4
        assertEquals(1.4 / 90, BreakProgress.perTick(1, 2, 0, 1, 1, true), 1e-12);
        // mining fatigue I: x0.3
        assertEquals(0.3 / 90, BreakProgress.perTick(1, 0, 1, 1, 1, true), 1e-12);
    }
}
