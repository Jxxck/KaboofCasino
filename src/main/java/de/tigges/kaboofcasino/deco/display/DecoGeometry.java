package de.tigges.kaboofcasino.deco.display;

import de.tigges.kaboofcasino.machine.Facing;
import de.tigges.kaboofcasino.util.Pos;
import java.util.ArrayList;
import java.util.List;

/**
 * Where a decoration's cells are and how big its click boxes are (the mod's {@code LargeCasinoSignBlock.partPos},
 * {@code CasinoChandelierBlock.bodyPos/chainPos}, {@code DecorShapes.turn} and the outline shapes of
 * {@code CasinoDecorGeometry}, {@code CasinoSignBlock}, {@code SpeakerBlock}, {@code DecorGeometry}). Pure.
 */
public final class DecoGeometry {

    /** A click box (Interaction entity) in blocks relative to the anchor cell's minimum corner. */
    public record Box(double cx, double y0, double cz, double width, double height) {
    }

    // the mod's outline boxes (south frame, px)
    static final double[] SCONCE = {2.5, 0.5, 0.0, 13.5, 14.75, 9.0};
    static final double[] VIP_ONLY_PLAQUE = {1.0, 3.0, 0.0, 15.0, 13.0, 2.0};
    /** {@code SpeakerBlock}: body plus foot / wall bracket / ceiling mount, as one bounding box each. */
    static final double[][] SPEAKER = {{2.0, 0.0, 3.0, 14.0, 14.0, 13.0}, {2.0, 2.0, 0.0, 14.0, 14.0, 13.0},
            {2.0, 2.0, 3.0, 14.0, 16.0, 13.0}};
    /** {@code CasinoSignBlock.getShape}: bottom and top in px. */
    static final double SIGN_BOTTOM = 2.8333333333333335;
    static final double SIGN_TOP = 13.166666666666666;
    static final double SIGN_DEPTH = 1.65;
    /** The chandelier body's outline over all nine parts (px from the anchor cell's corner). */
    static final double[] CHANDELIER_BODY = {-14.25, -11.2, -14.25, 30.25, 15.94, 30.25};

    private DecoGeometry() {
    }

    /** {@code DecorShapes.turn}: a box turned {@code steps} quarter turns ({@code Direction.get2DDataValue}). */
    public static double[] turn(double[] b, int steps) {
        double x0 = b[0];
        double z0 = b[2];
        double x1 = b[3];
        double z1 = b[5];
        for (int i = 0; i < steps; i++) {
            double nx0 = 16.0 - z1;
            double nx1 = 16.0 - z0;
            z0 = x0;
            z1 = x1;
            x0 = nx0;
            x1 = nx1;
        }
        return new double[] {x0, b[1], z0, x1, b[4], z1};
    }

    /** {@code DecorShapes.turnPoint}. */
    public static double[] turnPoint(double[] p, int steps) {
        double x = p[0];
        double z = p[2];
        for (int i = 0; i < steps; i++) {
            double nx = 16.0 - z;
            z = x;
            x = nx;
        }
        return new double[] {x, p[1], z};
    }

    /** {@code Direction.getCounterClockWise()} as a step (dx, dz). */
    public static int[] ccw(Facing f) {
        return switch (f) {
            case SOUTH -> new int[] {1, 0};
            case EAST -> new int[] {0, -1};
            case NORTH -> new int[] {-1, 0};
            case WEST -> new int[] {0, 1};
        };
    }

    /** {@code LargeCasinoSignBlock.partPos}: part 0..5 (3 wide to the counter-clockwise side, 2 high). */
    public static Pos signPart(Pos anchor, Facing face, int part) {
        int[] c = ccw(face);
        return anchor.offset(c[0] * (part % 3), part / 3, c[1] * (part % 3));
    }

    /** {@code CasinoChandelierBlock.bodyPos}: part 0..8 around the anchor (part 4). */
    public static Pos chandelierBody(Pos anchor, int part) {
        return anchor.offset(part % 3 - 1, 0, part / 3 - 1);
    }

    /** Every cell of a decoration: anchor first; the sign's six parts; the chandelier's body then chain upwards. */
    public static List<Pos> cells(DecoType type, Pos anchor, Facing facing, int state) {
        List<Pos> out = new ArrayList<>();
        switch (type.place()) {
            case LARGE_SIGN -> {
                for (int part = 0; part < 6; part++) {
                    out.add(signPart(anchor, facing, part));
                }
            }
            case CHANDELIER -> {
                out.add(anchor);
                for (int part = 0; part < 9; part++) {
                    if (part != 4) {
                        out.add(chandelierBody(anchor, part));
                    }
                }
                int length = Math.max(1, Math.min(2, state));
                for (int k = 1; k <= length; k++) {
                    out.add(anchor.offset(0, k, 0));
                }
            }
            default -> out.add(anchor);
        }
        return out;
    }

    public static List<Pos> cells(DecoRecord r) {
        return cells(r.type(), r.anchor(), r.facing(), r.state());
    }

    /** The chain cells of a chandelier (they emit no light: {@code part < 9 ? 14 : 0}). */
    public static boolean chandelierChain(DecoRecord r, Pos cell) {
        return r.type() == DecoType.CASINO_CHANDELIER && cell.y() > r.anchor().y();
    }

    /**
     * The Interaction boxes of a light-carried decoration (barrier carriers are their own click target). Square in x/z
     * (an Interaction cannot be thinner in one direction), centred on the mod's outline.
     */
    public static List<Box> clickBoxes(DecoRecord r) {
        List<Box> out = new ArrayList<>();
        int turns = r.facing().turns();
        switch (r.type()) {
            case SMALL_CASINO_SIGN -> out.add(box(signShape(r.facing(), SIGN_BOTTOM, SIGN_TOP), 0, 0));
            case CASINO_SIGN -> {
                int[] c = ccw(r.facing());
                for (int col = 0; col < 3; col++) {
                    out.add(box(signShape(r.facing(), 0.5, 31.5), c[0] * col, c[1] * col));
                }
            }
            case CHANDELIER_WALL_SCONCE -> out.add(box(turn(SCONCE, turns), 0, 0));
            case VIP_ONLY_PLAQUE -> out.add(box(turn(VIP_ONLY_PLAQUE, turns), 0, 0));
            case CASINO_SPEAKER -> out.add(box(turn(SPEAKER[r.face()], turns), 0, 0));
            case CASINO_CHANDELIER -> {
                out.add(box(CHANDELIER_BODY, 0, 0));
                int length = r.chain();
                out.add(new Box(0.5, 1.0, 0.5, 0.875, length));
            }
            default -> {
            }
        }
        return out;
    }

    /** {@code CasinoSignBlock.localShape}: the plate against the wall behind a sign facing {@code face}. */
    static double[] signShape(Facing face, double bottom, double top) {
        return switch (face) {
            case SOUTH -> new double[] {0.5, bottom, 0.0, 15.5, top, SIGN_DEPTH};
            case NORTH -> new double[] {0.5, bottom, 16 - SIGN_DEPTH, 15.5, top, 16.0};
            case EAST -> new double[] {0.0, bottom, 0.5, SIGN_DEPTH, top, 15.5};
            case WEST -> new double[] {16 - SIGN_DEPTH, bottom, 0.5, 16.0, top, 15.5};
        };
    }

    /** A px box (in the cell at offset dx, dz) as an Interaction box: centred, the larger horizontal extent. */
    static Box box(double[] b, int dx, int dz) {
        double w = Math.max(b[3] - b[0], b[5] - b[2]) / 16.0;
        return new Box(dx + (b[0] + b[3]) / 32.0, b[1] / 16.0, dz + (b[2] + b[5]) / 32.0, w, (b[4] - b[1]) / 16.0);
    }

    /**
     * The smallest shift (dx, dz) that brings all cells into the anchor's chunk, along {@code axes} (unit steps in
     * the order they are tried), or null if they already fit. A shift of 0 cells on both = cannot fit.
     */
    public static int[] chunkFit(List<Pos> cells, List<int[]> axes) {
        if (sameChunk(cells, 0, 0)) {
            return null;
        }
        for (int distance = 1; distance <= 2; distance++) {
            for (int[] axis : axes) {
                for (int sign : new int[] {1, -1}) {
                    int dx = axis[0] * distance * sign;
                    int dz = axis[1] * distance * sign;
                    if (sameChunk(cells, dx, dz)) {
                        return new int[] {dx, dz};
                    }
                }
            }
        }
        return new int[] {0, 0};
    }

    private static boolean sameChunk(List<Pos> cells, int dx, int dz) {
        Pos first = cells.getFirst().offset(dx, 0, dz);
        for (Pos p : cells) {
            Pos q = p.offset(dx, 0, dz);
            if (q.chunkX() != first.chunkX() || q.chunkZ() != first.chunkZ()) {
                return false;
            }
        }
        return true;
    }
}
