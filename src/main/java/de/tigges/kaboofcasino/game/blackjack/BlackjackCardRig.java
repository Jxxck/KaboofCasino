package de.tigges.kaboofcasino.game.blackjack;

import de.tigges.kaboofcasino.machine.Facing;
import de.tigges.kaboofcasino.visual.RigMath;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * One 3D card as a display transformation ({@code BlackjackCardGeometry.quads} of a {@code BlackjackCardMotion.Pose},
 * drawn by {@code BlackjackTableRenderer.cards} 160-212 at {@code BlackjackLayout.toBlock}). Pure.
 *
 * <p>The pack's card model lies in the mod's card frame at yaw -pi/2 and flip 0 (x = width axis u, y = normal,
 * z = minus the length axis). The mod's frame for (yaw, flip) is {@code u = (-sin yaw, 0, cos yaw)},
 * {@code vf = v cos flip + n sin flip}, {@code nf = -v sin flip + n cos flip} with {@code v = (cos yaw, 0, sin yaw)},
 * i.e. the rotation {@code Ry(-yaw - pi/2) Rx(flip)}; the peek lift then turns every vertex by {@code Rx(lift)} about
 * the line through the card centre + (0, 0, 3.2 scale) (table px). Width and length scale with the pose's scale, the
 * 0.06 px thickness does not. All in table px, then {@code toBlock}: (x - 24, y, z - 24) / 16 turned with the facing.
 */
public final class BlackjackCardRig {

    private BlackjackCardRig() {
    }

    /** {@code kaboofcasino:blackjack/card_<id>}, or {@code card_back} for a face-down hole card (id -1). */
    public static String model(int card) {
        return card < 0 || card > 51 ? "kaboofcasino:blackjack/card_back" : "kaboofcasino:blackjack/card_" + card;
    }

    /** The card's rotation in the table frame (without the facing). */
    static Quaternionf rotation(BlackjackCardMotion.Pose pose) {
        return new Quaternionf().rotationX((float) pose.lift).rotateY((float) (-pose.yaw - Math.PI / 2))
                .rotateX((float) pose.flip);
    }

    /** The display transformation relative to the anchor's bottom centre. */
    public static Transformation transform(Facing facing, BlackjackCardMotion.Pose pose) {
        double s = pose.scale;
        double hz = 3.2 * s;
        double x = pose.x;
        double y = pose.y + hz * Math.sin(pose.lift);
        double z = pose.z + hz * (1.0 - Math.cos(pose.lift));
        Transformation t = RigMath.part(facing, RigMath.point(x - 16.0, y, z - 16.0), rotation(pose));
        return new Transformation(t.getTranslation(), t.getLeftRotation(),
                new Vector3f((float) s, 1F, (float) s), t.getRightRotation());
    }
}
