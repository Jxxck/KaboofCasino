package de.tigges.kaboofcasino.seat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.deco.display.DecoType;
import de.tigges.kaboofcasino.deco.display.SofaLayout;
import de.tigges.kaboofcasino.machine.Facing;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Seat positions, yaw and the dismount search of the mod's seat entities (games.md 17). */
class SeatMathTest {

    @Test
    void chairAndStoolHeightsAndTheStoolShift() {
        for (Facing f : Facing.values()) {
            assertArrayEquals(new double[] {0.5, 10.0 / 16, 0.5}, SeatMath.position(DecoType.CASINO_CHAIR, f, 0),
                    1e-12);
            int[] s = f.step();
            // CasinoSeatEntity: (-stepX * 2/16, 0, -stepZ * 2/16): towards the back
            assertArrayEquals(new double[] {0.5 - s[0] * 2.0 / 16, 12.0 / 16, 0.5 - s[1] * 2.0 / 16},
                    SeatMath.position(DecoType.CASINO_STOOL, f, 0), 1e-12);
            assertEquals(f.turns() * 90f, SeatMath.yaw(DecoType.CASINO_CHAIR, f, 0), 1e-6);
        }
    }

    @Test
    void sofaSeatShiftsTowardsTheBack() {
        int north = SofaLayout.bit(0);
        double[] p = SeatMath.position(DecoType.CASINO_SOFA, Facing.SOUTH, north);
        assertArrayEquals(new double[] {0.5, 0.625, 0.5 - 0.1375}, p, 1e-12);
        assertEquals(0f, SeatMath.yaw(DecoType.CASINO_SOFA, Facing.SOUTH, north), 1e-6);
        int corner = SofaLayout.bit(0) | SofaLayout.bit(1);
        assertArrayEquals(new double[] {0.6, 0.625, 0.4}, SeatMath.position(DecoType.CASINO_SOFA, Facing.SOUTH,
                corner), 1e-12);
    }

    /** {@code Direction.fromYRot}: floor(yaw / 90 + 0.5) & 3 (a corner sofa's 45 degrees rounds up). */
    @Test
    void fromYRot() {
        assertEquals(Facing.SOUTH, SeatMath.fromYRot(0));
        assertEquals(Facing.WEST, SeatMath.fromYRot(90));
        assertEquals(Facing.NORTH, SeatMath.fromYRot(180));
        assertEquals(Facing.EAST, SeatMath.fromYRot(-90));
        assertEquals(Facing.WEST, SeatMath.fromYRot(45));
        assertEquals(Facing.SOUTH, SeatMath.fromYRot(-45));
        assertEquals(Facing.EAST, SeatMath.fromYRot(270));
    }

    /** front, front.getClockWise(), front.getCounterClockWise(), front.getOpposite(); dy 0, +1, -1 each. */
    @Test
    void dismountOrderIsTheMods() {
        List<int[]> c = SeatMath.candidates(0); // facing south
        assertEquals(12, c.size());
        int[][] want = {{0, 0, 1}, {0, 1, 1}, {0, -1, 1}, {-1, 0, 0}, {-1, 1, 0}, {-1, -1, 0}, {1, 0, 0}, {1, 1, 0},
                {1, -1, 0}, {0, 0, -1}, {0, 1, -1}, {0, -1, -1}};
        for (int i = 0; i < 12; i++) {
            assertArrayEquals(want[i], c.get(i), "candidate " + i);
        }
        // facing east: right of east is south
        List<int[]> east = SeatMath.candidates(270);
        assertArrayEquals(new int[] {1, 0, 0}, east.get(0));
        assertArrayEquals(new int[] {0, 0, 1}, east.get(3));
        assertArrayEquals(new int[] {0, 0, -1}, east.get(6));
        assertArrayEquals(new int[] {-1, 0, 0}, east.get(9));
    }

    @Test
    void floorHeightLikeBlockGetter() {
        assertEquals(0.5, SeatMath.floorHeight(0.5, true, 1.0, true), 1e-12); // a slab in the cell
        assertEquals(0.0, SeatMath.floorHeight(Double.NEGATIVE_INFINITY, false, 1.0, true), 1e-12);
        assertEquals(Double.NEGATIVE_INFINITY, SeatMath.floorHeight(Double.NEGATIVE_INFINITY, false, 0.5, true));
        assertEquals(Double.NEGATIVE_INFINITY, SeatMath.floorHeight(0, false, 0, false)); // the void below
        assertTrue(SeatMath.floorValid(0.0));
        assertTrue(SeatMath.floorValid(0.9375));
        assertTrue(!SeatMath.floorValid(1.0)); // a full block in the cell
        assertTrue(!SeatMath.floorValid(Double.NEGATIVE_INFINITY));
        assertEquals(0.5, SeatMath.floorHeight(0.5, true, 1.5, true), 1e-12); // a fence below does not matter
    }
}
