package de.tigges.kaboofcasino.game.blackjack;

import de.tigges.kaboofcasino.machine.Facing;
import de.tigges.kaboofcasino.visual.RigMath;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The dealer's forward kinematics on the server: one display transformation per part, exactly as
 * {@code BlackjackTableRenderer} draws {@code BlackjackDealerModel} (pose per frame: {@code DealerIdleAnimation}
 * at {@code gameTime + phase} (renderer 70-71), then {@code BlackjackTableState.dealerInput} +
 * {@code DealerPlayAnimation.apply} (renderer 83, model 76-77)). Pure.
 *
 * <p>The mod's chain for a cube vertex v (px, ModelPart frame) of a part with ancestors P0..Pn: {@code
 * DealerPlacement} (translate to (0.5, 0, -1.7875) turned with the facing, rotate {@code 180 - facing.toYRot()}, scale
 * 0.9375, scale (-1, -1, 1), translate (0, -1.501, 0)), then per part {@code translate(offset / 16)} and {@code
 * rotationZYX(zRot, yRot, xRot)} ({@code ModelPart.translateAndRotate}). With X = the 180 degree turn about X
 * ({@code R180 * scale(-1, -1, 1)}) and the pack's part models in {@code m = 8 + X v} (see the pack tool's
 * {@code BlackjackDealerModel}), the part is the display transformation
 * <pre>
 * pivot = feet + 0.9375 * (X (0, -1.501, 0) + sum_k (X R0..R(k-1) X) X offset_k / 16)     (machine frame)
 * Q     = prod_k X R_k X = prod_k rotationZYX(-zRot_k, -yRot_k, xRot_k)
 * RigMath.part(facing, pivot, Q) with scale 0.9375
 * </pre>
 * relative to the anchor's bottom centre ({@code feet} = table px (32, 0, -4.6) = (0.5, 0, -1.7875) blocks).
 */
public final class BlackjackDealerRig {

    /** Display parts, in the pack's model order. */
    public static final String[] PARTS = {"torso", "head", "right_arm", "right_forearm", "left_arm", "left_forearm",
        "right_leg", "left_leg"};
    public static final float SCALE = 0.9375F;
    /** {@code DealerPlacement.translate(0, -1.501, 0)} seen through the flip. */
    private static final float LIFT = 1.501F;
    /** {@code DealerPlacement} baseline (0.5, -1.7875) = table px (32, -4.6), from the anchor's bottom centre. */
    private static final Vector3f FEET = new Vector3f(0.5F, 0F, -1.7875F);
    /** {@code BlackjackTableRenderer} 70: the idle loop's per-table phase. */
    static final int LOOP = 480;

    private BlackjackDealerRig() {
    }

    /** {@code BlackjackTableRenderer.extractRenderState} 70: {@code floorMod(x * 31 + y * 7 + z * 17, 480)}. */
    public static long phase(int x, int y, int z) {
        return Math.floorMod((long) x * 31L + (long) y * 7L + (long) z * 17L, (long) LOOP);
    }

    /**
     * The dealer's pose at {@code now} (game ticks; the renderer's {@code time + partialTick}): idle at
     * {@code (float) now + (float) phase} (the mod's float arithmetic), then the play input of {@code view}.
     */
    public static DealerIdleAnimation.Pose pose(BlackjackTableState view, double now, long phase) {
        DealerPlayAnimation.Input input = new DealerPlayAnimation.Input();
        view.dealerInput(now, input);
        return pose(input, now, phase);
    }

    /**
     * The dealer's pose at {@code now} for a play input another table view produced at {@code now} (the poker table:
     * {@code PokerTableState.dealerInput}; same model, idle loop and gestures): idle at {@code (float) now +
     * (float) phase}, then {@code input} applied.
     */
    public static DealerIdleAnimation.Pose pose(DealerPlayAnimation.Input input, double now, long phase) {
        DealerIdleAnimation.Pose pose = new DealerIdleAnimation.Pose();
        DealerIdleAnimation.evaluate((float) now + (float) phase, pose);
        DealerPlayAnimation.apply(input, pose);
        return pose;
    }

    /** The play input at {@code now} (what decides the update cadence). */
    static DealerPlayAnimation.Input input(BlackjackTableState view, double now) {
        DealerPlayAnimation.Input input = new DealerPlayAnimation.Input();
        view.dealerInput(now, input);
        return input;
    }

    /**
     * True while the dealer moves fast: a gesture runs, the watch fades in or out (10 ticks), or the watch's finger
     * tap runs ({@code DealerPlayAnimation.watch}: cycle 95..123 of 150).
     */
    public static boolean fast(DealerPlayAnimation.Input in) {
        if (in.kind != DealerPlayAnimation.Kind.NONE) {
            return true;
        }
        if (in.watch > 0F && in.watch < 1F) {
            return true;
        }
        if (in.watch > 0F) {
            float cycle = in.watchTime % 150.0F;
            return cycle > 93.0F && cycle < 125.0F;
        }
        return false;
    }

    private static Quaternionf rot(float zRot, float yRot, float xRot) {
        return new Quaternionf().rotationZYX(-zRot, -yRot, xRot);
    }

    /** A ModelPart offset (px) through the flip, in blocks. */
    private static Vector3f offset(float x, float y, float z) {
        return new Vector3f(x / 16F, -y / 16F, -z / 16F);
    }

    /** Pivot (machine frame, from the feet, before scaling) and rotation of each part; index as {@link #PARTS}. */
    static void chain(DealerIdleAnimation.Pose p, Vector3f[] pivots, Quaternionf[] rotations) {
        Vector3f torsoT = offset(p.torsoX, 12F + p.torsoY, 0F);
        Quaternionf torsoQ = rot(p.torsoZRot, p.torsoYRot, p.torsoXRot);
        pivots[0] = torsoT;
        rotations[0] = torsoQ;
        pivots[1] = new Vector3f(torsoT).add(torsoQ.transform(offset(0F, -12F, 0F)));
        rotations[1] = new Quaternionf(torsoQ).mul(rot(p.headZRot, p.headYRot, p.headXRot));
        for (int side = 0; side < 2; side++) {
            float sx = side == 0 ? -6F : 6F;
            Vector3f armT = new Vector3f(torsoT).add(torsoQ.transform(offset(sx, -10F, 0F)));
            Quaternionf armQ = new Quaternionf(torsoQ).mul(rot(p.shoulderZRot[side], p.shoulderYRot[side],
                    p.shoulderXRot[side]));
            pivots[2 + 2 * side] = armT;
            rotations[2 + 2 * side] = armQ;
            pivots[3 + 2 * side] = new Vector3f(armT).add(new Quaternionf(armQ).transform(offset(0F, 4F, 0F)));
            rotations[3 + 2 * side] = new Quaternionf(armQ).mul(rot(0F, 0F, p.elbowXRot[side]));
            float lx = (side == 0 ? -2F : 2F) + p.legX[side];
            pivots[6 + side] = offset(lx, 12F, 0F);
            rotations[6 + side] = rot(p.legZRot[side], 0F, 0F);
        }
    }

    /** The eight part transformations (index as {@link #PARTS}), relative to the anchor's bottom centre. */
    public static Transformation[] transforms(Facing facing, DealerIdleAnimation.Pose pose) {
        Vector3f[] pivots = new Vector3f[PARTS.length];
        Quaternionf[] rotations = new Quaternionf[PARTS.length];
        chain(pose, pivots, rotations);
        Transformation[] out = new Transformation[PARTS.length];
        Vector3f scale = new Vector3f(SCALE, SCALE, SCALE);
        for (int i = 0; i < PARTS.length; i++) {
            Vector3f pivot = new Vector3f(pivots[i]).add(0F, LIFT, 0F).mul(SCALE).add(FEET);
            Transformation t = RigMath.part(facing, pivot, rotations[i]);
            out[i] = new Transformation(t.getTranslation(), t.getLeftRotation(), new Vector3f(scale),
                    t.getRightRotation());
        }
        return out;
    }

    /** Model key of part {@code i}. */
    public static String model(int i) {
        return "kaboofcasino:blackjack/dealer_" + PARTS[i];
    }
}
