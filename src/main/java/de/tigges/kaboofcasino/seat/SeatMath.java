package de.tigges.kaboofcasino.seat;

import de.tigges.kaboofcasino.deco.display.DecoModels;
import de.tigges.kaboofcasino.deco.display.DecoType;
import de.tigges.kaboofcasino.deco.display.SofaLayout;
import de.tigges.kaboofcasino.machine.Facing;
import java.util.ArrayList;
import java.util.List;

/**
 * Where a sitter sits and where he gets up (games.md 17): the mod's {@code CasinoSeatBlock} / {@code CasinoSofaBlock}
 * seat position and yaw, the stool's 2 px shift towards the back ({@code CasinoSeatEntity.getPassengerAttachmentPoint}),
 * and the dismount search order of {@code CasinoSeatEntity/SofaSeatEntity.getDismountLocationForPassenger}. Pure.
 */
public final class SeatMath {

    /** {@code CasinoSeatBlock.seatHeight}: 10 px for the chair, 12 px for the stool. */
    public static final double CHAIR_HEIGHT = 10.0 / 16.0;
    public static final double STOOL_HEIGHT = 12.0 / 16.0;
    /** The stool rider sits 2 px towards the back. */
    public static final double STOOL_SHIFT = 2.0 / 16.0;
    /** {@code getDismountLocationForPassenger}'s fallback: the seat's x/z at the block's y + 1.5. */
    public static final double FALLBACK_HEIGHT = 1.5;

    private SeatMath() {
    }

    /**
     * Where the seat entity stands (blocks from the seat block's minimum corner). The vehicle is a zero-size display,
     * whose passenger attachment is its own position like the mod's seat entity ({@code Vec3.ZERO}), so the rider's
     * hips are where the mod puts them; the stool's attachment shift is folded into the entity position.
     */
    public static double[] position(DecoType type, Facing facing, int sofaBack) {
        int[] step = facing.step();
        return switch (type) {
            case CASINO_CHAIR -> new double[] {0.5, CHAIR_HEIGHT, 0.5};
            case CASINO_STOOL -> new double[] {0.5 - step[0] * STOOL_SHIFT, STOOL_HEIGHT, 0.5 - step[1] * STOOL_SHIFT};
            case CASINO_SOFA -> {
                double[] shift = SofaLayout.seatShift(sofaBack);
                yield new double[] {0.5 + shift[0], SofaLayout.SEAT_HEIGHT, 0.5 + shift[1]};
            }
            default -> throw new IllegalArgumentException("not a seat: " + type);
        };
    }

    /** The seat's yaw (and the rider's at sit-down): {@code facing.toYRot()}, the sofa's {@code seatYaw}. */
    public static float yaw(DecoType type, Facing facing, int sofaBack) {
        if (type == DecoType.CASINO_SOFA) {
            return SofaLayout.seatYaw(sofaBack, DecoModels.cardinal(facing));
        }
        return facing.turns() * 90f;
    }

    /** {@code Direction.fromYRot}: the horizontal direction nearest to a yaw. */
    public static Facing fromYRot(double yRot) {
        int data = (int) Math.floor(yRot / 90.0 + 0.5) & 3;
        for (Facing f : Facing.values()) {
            if (f.turns() == data) {
                return f;
            }
        }
        throw new IllegalStateException();
    }

    /** The mod's dismount candidates in order: front, right, left, behind of the seat yaw, each at dy 0, +1, -1. */
    public static List<int[]> candidates(float seatYaw) {
        Facing front = fromYRot(seatYaw);
        Facing[] order = {front, turn(front, 1), turn(front, 3), turn(front, 2)};
        List<int[]> out = new ArrayList<>();
        for (Facing d : order) {
            int[] s = d.step();
            for (int dy : new int[] {0, 1, -1}) {
                out.add(new int[] {s[0], dy, s[1]});
            }
        }
        return out;
    }

    /** {@code getClockWise} (one turn) etc. in the turn order south, west, north, east. */
    static Facing turn(Facing f, int quarterTurns) {
        int t = (f.turns() + quarterTurns) & 3;
        for (Facing g : Facing.values()) {
            if (g.turns() == t) {
                return g;
            }
        }
        throw new IllegalStateException();
    }

    /**
     * {@code BlockGetter.getBlockFloorHeight}: the floor a player would stand on in a cell, from the cell's own
     * collision top (if it has collision) or the top of the block below minus 1 (only if that reaches the full
     * height); {@code NEGATIVE_INFINITY} if there is no floor.
     */
    public static double floorHeight(double cellTop, boolean cellHasCollision, double belowTop,
                                     boolean belowHasCollision) {
        if (cellHasCollision) {
            return cellTop;
        }
        if (!belowHasCollision) {
            return Double.NEGATIVE_INFINITY;
        }
        return belowTop >= 1.0 ? belowTop - 1.0 : Double.NEGATIVE_INFINITY;
    }

    /** {@code DismountHelper.isBlockFloorValid}. */
    public static boolean floorValid(double floor) {
        return !Double.isInfinite(floor) && floor < 1.0;
    }
}
