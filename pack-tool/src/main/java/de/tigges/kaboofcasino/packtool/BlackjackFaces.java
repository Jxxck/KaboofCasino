package de.tigges.kaboofcasino.packtool;

import com.google.gson.JsonObject;

/**
 * Turns a textured axis-aligned quad (four corners with their UVs, as the mod's renderers emit them) into one face of
 * a block-model element: the face direction from the corners, and the {@code uv} rectangle plus {@code rotation}
 * that make the 26.3 {@code FaceBakery} give every corner exactly its UV (FaceInfo vertex order, {@code CuboidFace}
 * corner selection {@code (i + rotation / 90) % 4}: 0 = (u0, v0), 1 = (u0, v1), 2 = (u1, v1), 3 = (u1, v0); the same
 * rules as {@code tools/meshes/mesh2json.py}). Pure.
 */
final class BlackjackFaces {

    /** FaceInfo vertex order per direction: for each of the 4 vertices, x/y/z taken from max (true) or min (false). */
    static final String[] DIRECTIONS = {"down", "up", "north", "south", "west", "east"};
    private static final boolean[][][] FACE = {
        {{false, false, true}, {false, false, false}, {true, false, false}, {true, false, true}},   // down
        {{false, true, false}, {false, true, true}, {true, true, true}, {true, true, false}},       // up
        {{true, true, false}, {true, false, false}, {false, false, false}, {false, true, false}},   // north
        {{false, true, true}, {false, false, true}, {true, false, true}, {true, true, true}},       // south
        {{false, true, false}, {false, false, false}, {false, false, true}, {false, true, true}},   // west
        {{true, true, true}, {true, false, true}, {true, false, false}, {true, true, false}},       // east
    };
    private static final double EPS = 1e-4;

    private BlackjackFaces() {
    }

    /** One solved face. */
    record Face(String direction, double[] uv, int rotation) {
        JsonObject json(String texture) {
            JsonObject face = new JsonObject();
            face.add("uv", Json.array(uv));
            if (rotation != 0) {
                face.addProperty("rotation", rotation);
            }
            face.addProperty("texture", texture);
            return face;
        }
    }

    /** The direction of a quad lying on one side of the box {@code from..to} (model units). */
    static String direction(double[] from, double[] to, double[][] corners) {
        for (int axis = 0; axis < 3; axis++) {
            if (all(corners, axis, from[axis])) {
                return new String[] {"west", "down", "north"}[axis];
            }
            if (all(corners, axis, to[axis])) {
                return new String[] {"east", "up", "south"}[axis];
            }
        }
        throw new IllegalStateException("quad is not on a side of its box");
    }

    private static boolean all(double[][] corners, int axis, double value) {
        for (double[] c : corners) {
            if (Math.abs(c[axis] - value) > EPS) {
                return false;
            }
        }
        return true;
    }

    /**
     * Solves the face whose 4 {@code corners} (model units) must carry {@code uvs} (model UV units, 0..16 across the
     * texture). Prefers rotation 0 and the plain u/v order; throws if no encoding reproduces every corner.
     */
    static Face solve(double[] from, double[] to, double[][] corners, double[][] uvs) {
        String direction = direction(from, to, corners);
        double uMin = Double.MAX_VALUE;
        double uMax = -Double.MAX_VALUE;
        double vMin = Double.MAX_VALUE;
        double vMax = -Double.MAX_VALUE;
        for (double[] uv : uvs) {
            uMin = Math.min(uMin, uv[0]);
            uMax = Math.max(uMax, uv[0]);
            vMin = Math.min(vMin, uv[1]);
            vMax = Math.max(vMax, uv[1]);
        }
        for (int rotation = 0; rotation < 360; rotation += 90) {
            for (double[] us : new double[][] {{uMin, uMax}, {uMax, uMin}}) {
                for (double[] vs : new double[][] {{vMin, vMax}, {vMax, vMin}}) {
                    double[] uv = {us[0], vs[0], us[1], vs[1]};
                    if (matches(bake(direction, from, to, uv, rotation), corners, uvs)) {
                        return new Face(direction, uv, rotation);
                    }
                }
            }
        }
        throw new IllegalStateException("no uv/rotation reproduces the quad on " + direction);
    }

    /** {@code FaceBakery}: the 4 baked vertices of a face as {x, y, z, u, v} (model units). */
    static double[][] bake(String direction, double[] from, double[] to, double[] uv, int rotation) {
        int d = java.util.Arrays.asList(DIRECTIONS).indexOf(direction);
        double[][] out = new double[4][];
        for (int i = 0; i < 4; i++) {
            boolean[] sel = FACE[d][i];
            int j = (i + rotation / 90) % 4;
            out[i] = new double[] {sel[0] ? to[0] : from[0], sel[1] ? to[1] : from[1], sel[2] ? to[2] : from[2],
                j == 0 || j == 1 ? uv[0] : uv[2], j == 0 || j == 3 ? uv[1] : uv[3]};
        }
        return out;
    }

    private static boolean matches(double[][] baked, double[][] corners, double[][] uvs) {
        boolean[] used = new boolean[corners.length];
        for (double[] b : baked) {
            boolean found = false;
            for (int k = 0; k < corners.length && !found; k++) {
                if (!used[k] && close(b[0], corners[k][0]) && close(b[1], corners[k][1]) && close(b[2], corners[k][2])
                        && close(b[3], uvs[k][0]) && close(b[4], uvs[k][1])) {
                    used[k] = true;
                    found = true;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private static boolean close(double a, double b) {
        return Math.abs(a - b) < EPS;
    }
}
