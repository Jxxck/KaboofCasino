package de.tigges.kaboofcasino.deco.display;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.machine.Facing;
import de.tigges.kaboofcasino.util.Pos;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

/** Cells, click boxes, chunk fit, the speaker/sconce shapes of the mod, and the candle rate. */
class DecoGeometryTest {

    private static final Pos A = new Pos("minecraft:overworld", 100, 64, 200);

    private static DecoRecord record(DecoType t, Facing f, int state) {
        return new DecoRecord(UUID.randomUUID(), t, A, f, state, new UUID(0, 0), 0);
    }

    /** {@code DecorShapes.turn} of the mod, one step: (x, z) -> (16 - z, x). */
    @Test
    void turnIsTheModsDecorShapesTurn() {
        double[] b = {1, 2, 3, 4, 5, 6};
        assertArrayEquals(new double[] {10, 2, 1, 13, 5, 4}, DecoGeometry.turn(b, 1), 1e-9);
        assertArrayEquals(b, DecoGeometry.turn(b, 4), 1e-9);
        assertArrayEquals(new double[] {16 - 3, 7, 1}, DecoGeometry.turnPoint(new double[] {1, 7, 3}, 1), 1e-9);
    }

    @Test
    void largeSignHasSixCellsToTheCounterClockwiseSide() {
        for (Facing f : Facing.values()) {
            List<Pos> cells = DecoGeometry.cells(record(DecoType.CASINO_SIGN, f, 0));
            assertEquals(6, new HashSet<>(cells).size());
            assertEquals(A, cells.getFirst());
            int[] ccw = DecoGeometry.ccw(f);
            // Direction.getCounterClockWise: south -> east, east -> north, north -> west, west -> south
            BlockFace expected = switch (f) {
                case SOUTH -> BlockFace.EAST;
                case EAST -> BlockFace.NORTH;
                case NORTH -> BlockFace.WEST;
                case WEST -> BlockFace.SOUTH;
            };
            assertEquals(expected.getModX(), ccw[0]);
            assertEquals(expected.getModZ(), ccw[1]);
            assertEquals(A.offset(2 * ccw[0], 1, 2 * ccw[1]), cells.get(5));
        }
    }

    /**
     * {@code CasinoTelevisionBlock.cellPos}: part {@code p} is {@code p % columns} steps toward
     * {@code facing.getCounterClockWise()} (the right of a viewer looking at the screen) and {@code p / columns} up.
     */
    @Test
    void televisionsGrowToTheViewersRightAndUp() {
        for (DecoType t : List.of(DecoType.CASINO_TV, DecoType.CASINO_TV_LARGE, DecoType.CASINO_TV_3X3)) {
            int columns = t.tvColumns();
            for (Facing f : Facing.values()) {
                List<Pos> cells = DecoGeometry.cells(record(t, f, 0));
                assertEquals(columns * columns, new HashSet<>(cells).size(), t.id());
                assertEquals(A, cells.getFirst());
                // the viewer looks at the screen (against the facing); his right is the clockwise turn of that
                BlockFace looks = f.blockFace().getOppositeFace();
                BlockFace right = switch (looks) {
                    case NORTH -> BlockFace.EAST;
                    case EAST -> BlockFace.SOUTH;
                    case SOUTH -> BlockFace.WEST;
                    default -> BlockFace.NORTH;
                };
                for (int part = 0; part < columns * columns; part++) {
                    int col = part % columns;
                    int row = part / columns;
                    assertEquals(A.offset(right.getModX() * col, row, right.getModZ() * col), cells.get(part),
                            t.id() + " " + f + " part " + part);
                }
            }
        }
        assertEquals(List.of(1, 2, 3, 0), List.of(DecoType.CASINO_TV.tvColumns(),
                DecoType.CASINO_TV_LARGE.tvColumns(), DecoType.CASINO_TV_3X3.tvColumns(),
                DecoType.CASINO_SIGN.tvColumns()));
    }

    /**
     * A TV's click boxes: one per column, over the screen's rows ({@code CasinoTelevisionBlock.getShape}: small 3.5 to
     * 12.5 px, large 7 to 25, 3 x 3 10.5 to 37.5), centred on the 2 px panel in front of the wall.
     */
    @Test
    void televisionClickBoxes() {
        double[][] screen = {{3.5, 12.5}, {7.0, 25.0}, {10.5, 37.5}};
        for (DecoType t : List.of(DecoType.CASINO_TV, DecoType.CASINO_TV_LARGE, DecoType.CASINO_TV_3X3)) {
            int columns = t.tvColumns();
            for (Facing f : Facing.values()) {
                int[] s = f.step();
                int[] c = DecoGeometry.ccw(f);
                List<DecoGeometry.Box> boxes = DecoGeometry.clickBoxes(record(t, f, 0));
                assertEquals(columns, boxes.size(), t.id());
                for (int col = 0; col < columns; col++) {
                    DecoGeometry.Box b = boxes.get(col);
                    assertEquals(screen[columns - 1][0] / 16, b.y0(), 1e-9, t.id());
                    assertEquals((screen[columns - 1][1] - screen[columns - 1][0]) / 16, b.height(), 1e-9, t.id());
                    // the panel centre is 1 px in front of the wall, which lies at -facing
                    double centre = s[0] != 0 ? b.cx() : b.cz();
                    int dir = s[0] != 0 ? s[0] : s[1];
                    assertEquals(dir > 0 ? 1.0 / 16 : 15.0 / 16, centre, 1e-9, t.id() + " " + f);
                    // the box lies in its column's cell
                    double across = c[0] != 0 ? b.cx() - c[0] * col : b.cz() - c[1] * col;
                    assertTrue(across > 0 && across < 1, t.id() + " " + f + " column " + col);
                    double width = col == 0 && col == columns - 1 ? 15 : col == 0 || col == columns - 1 ? 15.5 : 16;
                    assertEquals(width / 16, b.width(), 1e-9, t.id() + " " + f + " column " + col);
                }
            }
        }
    }

    @Test
    void chandelierCellsBodyAndChain() {
        List<Pos> one = DecoGeometry.cells(record(DecoType.CASINO_CHANDELIER, Facing.SOUTH, 1));
        assertEquals(10, one.size());
        assertTrue(one.contains(A.offset(-1, 0, -1)) && one.contains(A.offset(1, 0, 1)) && one.contains(A.offset(0, 1,
                0)));
        List<Pos> two = DecoGeometry.cells(record(DecoType.CASINO_CHANDELIER, Facing.SOUTH, 2));
        assertEquals(11, two.size());
        assertTrue(two.contains(A.offset(0, 2, 0)));
        assertTrue(DecoGeometry.chandelierChain(record(DecoType.CASINO_CHANDELIER, Facing.SOUTH, 2), A.offset(0, 2,
                0)));
        assertTrue(!DecoGeometry.chandelierChain(record(DecoType.CASINO_CHANDELIER, Facing.SOUTH, 2), A.offset(1, 0,
                1)));
    }

    @Test
    void chunkFitShiftsTheSmallestDistance() {
        // a 3-wide sign starting at x = 14 of a chunk reaches x = 16: one block to -x fits
        Pos edge = new Pos("w", 14, 64, 5);
        List<Pos> cells = DecoGeometry.cells(DecoType.CASINO_SIGN, edge, Facing.SOUTH, 0);
        int[] shift = DecoGeometry.chunkFit(cells, List.of(DecoGeometry.ccw(Facing.SOUTH)));
        assertArrayEquals(new int[] {-1, 0}, shift);
        assertNull(DecoGeometry.chunkFit(DecoGeometry.cells(DecoType.CASINO_SIGN, new Pos("w", 3, 64, 5),
                Facing.SOUTH, 0), List.of(DecoGeometry.ccw(Facing.SOUTH))));
        // a 3 x 3 TV facing north grows to -x: from x = 1 it reaches x = -1, one block to +x fits (2, 1, 0)
        List<Pos> tv = DecoGeometry.cells(DecoType.CASINO_TV_3X3, new Pos("w", 1, 64, 5), Facing.NORTH, 0);
        assertEquals(List.of(1, 0, -1), tv.subList(0, 3).stream().map(Pos::x).toList());
        assertArrayEquals(new int[] {1, 0}, DecoGeometry.chunkFit(tv, List.of(DecoGeometry.ccw(Facing.NORTH))));
        // from x = 0 (0, -1, -2) one block further to -x fits the chunk on the other side (-1, -2, -3)
        List<Pos> tv0 = DecoGeometry.cells(DecoType.CASINO_TV_3X3, new Pos("w", 0, 64, 5), Facing.NORTH, 0);
        assertArrayEquals(new int[] {-1, 0}, DecoGeometry.chunkFit(tv0, List.of(DecoGeometry.ccw(Facing.NORTH))));
        // the chandelier body around x = 0 (chunk edge at x = 0 / -1): one block to +x
        List<Pos> body = DecoGeometry.cells(DecoType.CASINO_CHANDELIER, new Pos("w", 0, 64, 8), Facing.SOUTH, 1);
        assertArrayEquals(new int[] {1, 0}, DecoGeometry.chunkFit(body, List.of(new int[] {1, 0}, new int[] {0, 1})));
    }

    /** Click boxes lie on the mod's outline: wall pieces against the wall behind them, the speaker by its face. */
    @Test
    void clickBoxesFollowTheOutline() {
        for (Facing f : Facing.values()) {
            int[] s = f.step();
            DecoGeometry.Box sign = DecoGeometry.clickBoxes(record(DecoType.SMALL_CASINO_SIGN, f, 0)).getFirst();
            // the plate centre is 0.825 px in front of the wall, which lies at -facing
            double wallX = 0.5 - s[0] * 0.5;
            double wallZ = 0.5 - s[1] * 0.5;
            assertEquals(s[0] == 0 ? 0.5 : wallX + s[0] * DecoGeometry.SIGN_DEPTH / 32.0, sign.cx(), 1e-9, f.name());
            assertEquals(s[1] == 0 ? 0.5 : wallZ + s[1] * DecoGeometry.SIGN_DEPTH / 32.0, sign.cz(), 1e-9, f.name());
            assertEquals(15.0 / 16.0, sign.width(), 1e-9);
            List<DecoGeometry.Box> large = DecoGeometry.clickBoxes(record(DecoType.CASINO_SIGN, f, 0));
            assertEquals(3, large.size());
            for (DecoGeometry.Box b : large) {
                assertTrue(b.height() > 1.9 && b.height() < 2.0);
            }
        }
        DecoGeometry.Box floor = DecoGeometry.clickBoxes(record(DecoType.CASINO_SPEAKER, Facing.SOUTH,
                DecoRecord.speakerState(false, DecoRecord.FACE_FLOOR))).getFirst();
        assertEquals(0.0, floor.y0(), 1e-9);
        assertEquals(14.0 / 16.0, floor.height(), 1e-9);
        DecoGeometry.Box ceiling = DecoGeometry.clickBoxes(record(DecoType.CASINO_SPEAKER, Facing.SOUTH,
                DecoRecord.speakerState(true, DecoRecord.FACE_CEILING))).getFirst();
        assertEquals(2.0 / 16.0, ceiling.y0(), 1e-9);
        assertEquals(1.0, ceiling.y0() + ceiling.height(), 1e-9);
        List<DecoGeometry.Box> chandelier = DecoGeometry.clickBoxes(record(DecoType.CASINO_CHANDELIER, Facing.SOUTH,
                2));
        assertEquals(2, chandelier.size());
        assertEquals(0.5, chandelier.getFirst().cx(), 1e-9);
        assertEquals(-11.2 / 16, chandelier.getFirst().y0(), 1e-9);
        assertEquals(2.0, chandelier.get(1).height(), 1e-9);
        // barrier carriers have no Interaction
        assertTrue(DecoGeometry.clickBoxes(record(DecoType.CASINO_CHAIR, Facing.SOUTH, 0)).isEmpty());
    }

    /** Only chandeliers and sconces are in the flame index; players up to 32 blocks per axis see them. */
    @Test
    void flameIndexAndRange() {
        for (DecoType t : DecoType.values()) {
            assertEquals(t == DecoType.CASINO_CHANDELIER || t == DecoType.CHANDELIER_WALL_SCONCE, DecoFlames.burns(t),
                    t.id());
        }
        assertTrue(DecoFlames.inRange(32, -32, 0));
        assertTrue(!DecoFlames.inRange(33, 0, 0));
        assertTrue(!DecoFlames.inRange(0, 0, -40));
    }

    @Test
    void candleRateIsTheClientsAnimateTickDistribution() {
        double atPlayer = 667.0 * (Math.pow(16.0 / 256, 3) + Math.pow(32.0 / 1024, 3));
        assertEquals(atPlayer, DecoFlames.rate(0, 0, 0), 1e-12);
        assertEquals(0.0, DecoFlames.rate(32, 0, 0), 1e-12);
        assertEquals(667.0 * (16.0 / 1024) * Math.pow(32.0 / 1024, 2), DecoFlames.rate(16, 0, 0), 1e-12);
        assertTrue(DecoFlames.rate(3, 1, 2) < atPlayer);
        assertTrue(atPlayer < 0.2, "at most one roll per tick");
    }

    @Test
    void variantsAreTheBlockRenderersChoice() {
        assertEquals(0L, DecoVariants.seed(0, 0, 0));
        int[] seen = new int[4];
        for (int x = 0; x < 40; x++) {
            for (int z = 0; z < 40; z++) {
                int v = DecoVariants.simple(x, 64, z, 4);
                seen[v]++;
                assertEquals(v, DecoVariants.simple(x, 64, z, 4));
            }
        }
        for (int n : seen) {
            assertTrue(n > 300, java.util.Arrays.toString(seen));
        }
        assertEquals(new java.util.Random(DecoVariants.seed(5, 6, 7)).nextInt(4), DecoVariants.simple(5, 6, 7, 4));
        assertEquals("", DecoVariants.suffix(0));
        assertEquals("_v3", DecoVariants.suffix(3));
    }

    @Test
    void lookDirectionsLikeVanilla() {
        // looking straight north and level: north, then (yaw 180 -> xPos false: west), up/down ...
        BlockFace[] north = LookDirections.orderedByNearest(180f, 0f);
        assertEquals(BlockFace.NORTH, north[0]);
        assertEquals(BlockFace.SOUTH, north[5]);
        BlockFace[] down = LookDirections.orderedByNearest(0f, 80f);
        assertEquals(BlockFace.DOWN, down[0]);
        assertEquals(BlockFace.UP, down[5]);
        BlockFace[] east = LookDirections.orderedByNearest(-90f, 10f);
        assertEquals(BlockFace.EAST, east[0]);
        // clicking the top of a block (face UP) while looking north: DOWN (into the clicked block) comes first
        BlockFace[] placing = LookDirections.placing(180f, 0f, BlockFace.UP, false);
        assertEquals(BlockFace.DOWN, placing[0]);
        assertEquals(BlockFace.NORTH, placing[1]);
        assertEquals(6, new HashSet<>(List.of(placing)).size());
        assertArrayEquals(north, LookDirections.placing(180f, 0f, BlockFace.UP, true));
    }
}
