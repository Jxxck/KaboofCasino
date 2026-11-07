package de.tigges.kaboofcasino.deco.display;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** The mod's sofa layout (hand-checked cases from {@code SofaLayout}/{@code CasinoSofaBlock}) and its invariants. */
class SofaLayoutTest {

    private static final int N = 0;
    private static final int E = 1;
    private static final int S = 2;
    private static final int W = 3;

    private static SofaLayout.Cell c(int x, int z) {
        return new SofaLayout.Cell(x, z);
    }

    @Test
    void singleSofaHasItsBackBehindTheSitterAndBothArms() {
        Map<SofaLayout.Cell, SofaLayout.Layout> l = SofaLayout.calculate(Map.of(c(0, 0), S));
        SofaLayout.Layout one = l.get(c(0, 0));
        assertEquals(0, one.joins());
        assertEquals(SofaLayout.bit(N), one.backs());
        assertEquals(SofaLayout.bit(E) | SofaLayout.bit(W), one.arms());
        assertEquals(SofaLayout.placedBack(S), one.backs());
    }

    @Test
    void straightCouchSharesOneBackAndHasArmsAtTheEnds() {
        Map<SofaLayout.Cell, Integer> facing = new HashMap<>();
        for (int x = 0; x < 3; x++) {
            facing.put(c(x, 0), S);
        }
        Map<SofaLayout.Cell, SofaLayout.Layout> l = SofaLayout.calculate(facing);
        for (int x = 0; x < 3; x++) {
            assertEquals(SofaLayout.bit(N), l.get(c(x, 0)).backs(), "x " + x);
        }
        assertEquals(SofaLayout.bit(E), l.get(c(0, 0)).joins());
        assertEquals(SofaLayout.bit(W), l.get(c(0, 0)).arms()); // the open end gets the arm
        assertEquals(0, l.get(c(1, 0)).arms());
        assertEquals(SofaLayout.bit(E), l.get(c(2, 0)).arms());
    }

    @Test
    void cornerTakesTheOutsideAsBack() {
        Map<SofaLayout.Cell, Integer> facing = new HashMap<>();
        facing.put(c(0, 0), S);
        facing.put(c(1, 0), S);
        facing.put(c(1, 1), W);
        Map<SofaLayout.Cell, SofaLayout.Layout> l = SofaLayout.calculate(facing);
        SofaLayout.Layout corner = l.get(c(1, 0));
        assertEquals(SofaLayout.bit(W) | SofaLayout.bit(S), corner.joins());
        assertEquals(15 ^ corner.joins(), corner.backs());
        assertEquals(0, corner.arms());
        // the straight neighbours continue the corner's outside: north for the row, east for the column
        assertEquals(SofaLayout.bit(N), l.get(c(0, 0)).backs());
        assertEquals(SofaLayout.bit(E), l.get(c(1, 1)).backs());
    }

    @Test
    void seatYawAndShift() {
        assertEquals(0f, SofaLayout.seatYaw(SofaLayout.bit(N), S), 1e-4);
        assertEquals(-90f, SofaLayout.seatYaw(SofaLayout.bit(W), E), 1e-4);
        assertEquals(45f, SofaLayout.seatYaw(SofaLayout.bit(N) | SofaLayout.bit(E), S), 1e-4);
        assertEquals(0f, SofaLayout.seatYaw(0, S), 1e-4);
        assertEquals(180f, SofaLayout.seatYaw(0, N), 1e-4);
        assertArrayEquals(new double[] {0, -SofaLayout.SEAT_BACK_SHIFT}, SofaLayout.seatShift(SofaLayout.bit(N)),
                1e-9);
        assertArrayEquals(new double[] {SofaLayout.SEAT_CORNER_SHIFT, -SofaLayout.SEAT_CORNER_SHIFT},
                SofaLayout.seatShift(SofaLayout.bit(N) | SofaLayout.bit(E)), 1e-9);
    }

    /** Random sofa groups: every cell gets a layout, backs never point into a joined neighbour, layouts are stable. */
    @Test
    void randomGroupsAreConsistent() {
        Random random = new Random(42);
        for (int round = 0; round < 2000; round++) {
            Map<SofaLayout.Cell, Integer> facing = new HashMap<>();
            int n = 1 + random.nextInt(9);
            for (int i = 0; i < n; i++) {
                facing.put(c(random.nextInt(4), random.nextInt(4)), random.nextInt(4));
            }
            Map<SofaLayout.Cell, SofaLayout.Layout> l = SofaLayout.calculate(facing);
            assertEquals(facing.keySet(), l.keySet());
            for (Map.Entry<SofaLayout.Cell, SofaLayout.Layout> e : l.entrySet()) {
                SofaLayout.Layout x = e.getValue();
                assertEquals(0, x.joins() & x.backs(), "back into a neighbour");
                assertTrue(x.joins() > 2 && Integer.bitCount(x.joins()) > 2 || Integer.bitCount(x.backs()) <= 2,
                        "back " + x.backs());
                assertEquals(x, SofaLayout.calculate(facing).get(e.getKey()));
            }
        }
    }

    @Test
    void turnMaskRotatesClockwise() {
        assertEquals(SofaLayout.bit(E), SofaLayout.turnMask(SofaLayout.bit(N), 1));
        assertEquals(SofaLayout.bit(N), SofaLayout.turnMask(SofaLayout.bit(W), 1));
        assertEquals(0b1001, SofaLayout.turnMask(0b0011, 3));
    }
}
