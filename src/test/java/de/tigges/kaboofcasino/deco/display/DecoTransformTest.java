package de.tigges.kaboofcasino.deco.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.machine.Facing;
import java.util.List;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/**
 * A display of a block model with the blockstate's {@code y} lands exactly where the client draws the block: model
 * point m (px) turned about the block centre like {@code BlockModelRotation} ({@code DecorShapes.turn} steps, clockwise
 * from above), relative to the entity at the cell's bottom centre. The vanilla-carrier scale grows the model about its
 * centre only.
 */
class DecoTransformTest {

    private static final double EPS = 1e-5;

    @Test
    void everyBlockstateTurnMatches() {
        double[][] points = {{0, 0, 0}, {16, 16, 16}, {3, 7, 11}, {16, 0, 0}, {0, 16, 16}, {8, 8, 8}, {-3.4, -11.2, 19.4}};
        for (Facing f : Facing.values()) {
            int y = DecoModels.yRot(f);
            Transformation t = DecoModels.transformation(y, 1f);
            for (double[] m : points) {
                double[] want = DecoGeometry.turnPoint(m, f.turns());
                Vector3f got = DecoModels.apply(t, m[0], m[1], m[2]);
                assertEquals(want[0] / 16 - 0.5, got.x, EPS, f + " " + java.util.Arrays.toString(m));
                assertEquals(want[1] / 16, got.y, EPS, f + " " + java.util.Arrays.toString(m));
                assertEquals(want[2] / 16 - 0.5, got.z, EPS, f + " " + java.util.Arrays.toString(m));
            }
        }
    }

    /** A {@code y} of the bars' multipart (not a facing) behaves the same. */
    @Test
    void anyQuarterTurn() {
        for (int y = 0; y < 360; y += 90) {
            Transformation t = DecoModels.transformation(y, 1f);
            Vector3f got = DecoModels.apply(t, 8, 4, 0); // north edge centre
            double[] want = DecoGeometry.turnPoint(new double[] {8, 4, 0}, y / 90);
            assertEquals(want[0] / 16 - 0.5, got.x, EPS);
            assertEquals(want[2] / 16 - 0.5, got.z, EPS);
        }
    }

    /** The chandelier body is stored at half size about the centre and shown at scale 2: the same points. */
    @Test
    void halfSizeBodyAtScaleTwo() {
        Transformation full = DecoModels.transformation(0, 1f);
        Transformation body = DecoModels.transformation(0, DecoModels.BODY_SCALE);
        for (double[] m : new double[][] {{-16.3, -11.2, 30.25}, {8, 8, 8}, {32.3, 15.9, -14.25}}) {
            Vector3f want = DecoModels.apply(full, m[0], m[1], m[2]);
            Vector3f got = DecoModels.apply(body, 8 + (m[0] - 8) / 2, 8 + (m[1] - 8) / 2, 8 + (m[2] - 8) / 2);
            assertEquals(want.x, got.x, EPS);
            assertEquals(want.y, got.y, EPS);
            assertEquals(want.z, got.z, EPS);
        }
    }

    @Test
    void vanillaScaleKeepsTheCentreAndCoversTheCarrier() {
        Transformation t = DecoModels.transformation(0, DecoModels.VANILLA_SCALE);
        Vector3f centre = DecoModels.apply(t, 8, 8, 8);
        assertEquals(0, centre.x, EPS);
        assertEquals(0.5, centre.y, EPS);
        assertEquals(0, centre.z, EPS);
        Vector3f corner = DecoModels.apply(t, 16, 16, 16);
        assertEquals(0.5 * DecoModels.VANILLA_SCALE, corner.x, EPS);
        assertEquals(0.5 + 0.5 * DecoModels.VANILLA_SCALE, corner.y, EPS);
        Vector3f low = DecoModels.apply(t, 0, 0, 0);
        assertEquals(0.5 - 0.5 * DecoModels.VANILLA_SCALE, low.y, EPS); // just below the carrier's bottom face
    }

    // ---- hotfix 0.9.1: covers over vanilla stairs and slabs ------------------------------------------------------

    /** The solid eighths ([x][y][z], 0 = low half) of a vanilla stair: facing, top half or not, shape. */
    static boolean[][][] stairs(String facing, boolean top, String shape) {
        boolean[][][] out = new boolean[2][2][2];
        int full = top ? 1 : 0;
        for (int x = 0; x < 2; x++) {
            for (int z = 0; z < 2; z++) {
                out[x][full][z] = true;
                boolean f = in(facing, x, z);
                boolean ccw = in(turn(facing, 3), x, z);
                boolean cw = in(turn(facing, 1), x, z);
                out[x][1 - full][z] = switch (shape) {
                    case "straight" -> f;
                    case "outer_left" -> f && ccw;
                    case "outer_right" -> f && cw;
                    case "inner_left" -> f || ccw;
                    default -> f || cw;
                };
            }
        }
        return out;
    }

    static boolean in(String dir, int x, int z) {
        return switch (dir) {
            case "north" -> z == 0;
            case "south" -> z == 1;
            case "west" -> x == 0;
            default -> x == 1;
        };
    }

    static String turn(String dir, int quarters) {
        List<String> order = List.of("north", "east", "south", "west");
        return order.get((order.indexOf(dir) + quarters) % 4);
    }

    static boolean[][][] slab(int layer) {
        boolean[][][] out = new boolean[2][2][2];
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 2; y++) {
                for (int z = 0; z < 2; z++) {
                    out[x][y][z] = layer < 0 || y == layer;
                }
            }
        }
        return out;
    }

    /**
     * Every exposed face of every stair and slab shape moves outward under the cover's scale about its pivot, by at
     * least a quarter of the growth (0.008 blocks) and at most three quarters; none stays on the carrier's face.
     */
    @Test
    void everyCarrierFaceIsCoveredFromOutside() {
        List<boolean[][][]> shapes = new java.util.ArrayList<>(List.of(slab(0), slab(1), slab(-1)));
        for (String f : List.of("north", "east", "south", "west")) {
            for (boolean top : List.of(false, true)) {
                for (String shape : List.of("straight", "outer_left", "outer_right", "inner_left", "inner_right")) {
                    shapes.add(stairs(f, top, shape));
                }
            }
        }
        float s = DecoModels.VANILLA_SCALE;
        int faces = 0;
        for (boolean[][][] solid : shapes) {
            Vector3f q = DecoModels.coverPivot(solid);
            float[] pivot = {q.x + 0.5f, q.y + 0.5f, q.z + 0.5f};
            for (int x = 0; x < 2; x++) {
                for (int y = 0; y < 2; y++) {
                    for (int z = 0; z < 2; z++) {
                        if (!solid[x][y][z]) {
                            continue;
                        }
                        int[] cell = {x, y, z};
                        for (int axis = 0; axis < 3; axis++) {
                            for (int dir : new int[] {-1, 1}) {
                                int[] n = cell.clone();
                                n[axis] += dir;
                                boolean open = n[axis] < 0 || n[axis] > 1 || !solid[n[0]][n[1]][n[2]];
                                if (!open) {
                                    continue;
                                }
                                float plane = cell[axis] * 0.5f + (dir > 0 ? 0.5f : 0f);
                                float moved = (s - 1) * (plane - pivot[axis]) * dir;
                                assertTrue(moved >= (s - 1) * 0.25f - 1e-6f, "face moves out enough: " + moved);
                                assertTrue(moved <= (s - 1) * 0.75f + 1e-6f, "face moves out at most 3/4: " + moved);
                                faces++;
                            }
                        }
                    }
                }
            }
        }
        assertTrue(faces > 300, "faces checked: " + faces);
        assertEquals(0.008f, (s - 1) * 0.25f, 1e-5f);
    }

    @Test
    void theCoverScalesAboutItsPivot() {
        Vector3f q = DecoModels.coverPivot(slab(0)); // a bottom slab: a quarter block up from the bottom
        assertEquals(new Vector3f(0, -0.25f, 0), q);
        Transformation t = DecoModels.transformation(0, DecoModels.VANILLA_SCALE, q);
        Vector3f fixed = DecoModels.apply(t, 8, 4, 8);
        assertEquals(0, fixed.x, EPS);
        assertEquals(0.25, fixed.y, EPS);
        assertEquals(0, fixed.z, EPS);
        Vector3f top = DecoModels.apply(t, 8, 8, 8); // the slab's top face, 0.9.0's coplanar face
        assertEquals(0.5 + 0.25 * (DecoModels.VANILLA_SCALE - 1), top.y, EPS);
        Vector3f bottom = DecoModels.apply(t, 8, 0, 8);
        assertEquals(-0.25 * (DecoModels.VANILLA_SCALE - 1), bottom.y, EPS);
        // a double slab (full cube) keeps the centre
        assertEquals(new Vector3f(), DecoModels.coverPivot(slab(-1)));
        // a bottom stair facing north: pivot in the solid low back eighth row
        assertEquals(new Vector3f(0, -0.25f, -0.25f), DecoModels.coverPivot(stairs("north", false, "straight")));
    }

    @Test
    void eighthsComeFromTheCollisionBoxes() {
        boolean[][][] e = DecoRig.eighths(List.of(new org.bukkit.util.BoundingBox(0, 0, 0, 1, 0.5, 1),
                new org.bukkit.util.BoundingBox(0, 0.5, 0, 1, 1, 0.5)));
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 2; y++) {
                for (int z = 0; z < 2; z++) {
                    assertEquals(stairs("north", false, "straight")[x][y][z], e[x][y][z]);
                }
            }
        }
    }
}
