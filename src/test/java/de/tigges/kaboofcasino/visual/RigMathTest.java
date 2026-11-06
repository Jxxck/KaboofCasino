package de.tigges.kaboofcasino.visual;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.tigges.kaboofcasino.machine.Facing;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/**
 * The display transformation (with the client's 180 degree turn) must put every model point exactly where the mod's
 * {@code SlotMachineLayout.toBlock} puts it, for all four facings: that is the "matches the blockstate rotation"
 * property render.md 9.1 asks for.
 */
class RigMathTest {

    private static void same(double[] expectedBlock, Vector3f actualFromEntity) {
        // entity stands at the anchor's bottom centre: block corner + (0.5, 0, 0.5)
        assertEquals(expectedBlock[0], actualFromEntity.x + 0.5, 1e-5);
        assertEquals(expectedBlock[1], actualFromEntity.y, 1e-5);
        assertEquals(expectedBlock[2], actualFromEntity.z + 0.5, 1e-5);
    }

    @Test
    void staticBodyPartsMatchTheModsRotation() {
        double[][] points = {{0, 0, 0}, {16, 16, 16}, {-4, 3, 15.5}, {20.6, 9.5, 8}, {3, 12.25, 1}};
        for (Facing f : Facing.values()) {
            for (int part = 0; part < 3; part++) {
                Transformation t = RigMath.part(f, RigMath.point(8, 8 + 16 * part, 8));
                for (double[] p : points) {
                    Vector3f actual = RigMath.apply(t, p[0], p[1], p[2]);
                    same(f.toBlock(p[0], p[1] + 16 * part, p[2]), actual);
                }
            }
        }
    }

    @Test
    void leverPivotStaysPutWhileTheArmTurnsAboutX() {
        for (Facing f : Facing.values()) {
            Vector3f pivot = RigMath.point(20.6, 9.5, 8);
            Transformation t = RigMath.part(f, pivot, new Quaternionf().rotationX((float) Math.toRadians(90)));
            // the model centre (8,8,8) is the pivot
            same(f.toBlock(20.6, 9.5, 8), RigMath.apply(t, 8, 8, 8));
            // the arm tip (pivot + 12 px up) goes to pivot + 12 px towards +Z (south frame) at +90 degrees
            same(f.toBlock(20.6, 9.5, 8 + 12), RigMath.apply(t, 8, 20, 8));
        }
    }

    @Test
    void drumFrontFaceMovesDownAsTheTurnGrows() {
        // a point on the drum front (model z = 8 + r) at turn 0 sits at the window centre; at turn 2 (90 degrees) it
        // has moved to the bottom of the drum (the mod's symbols move down)
        double r = 7.0;
        Facing f = Facing.SOUTH;
        Vector3f pivot = RigMath.point(8, 25.75, 4.5);
        Transformation t0 = RigMath.part(f, pivot, new Quaternionf().rotationX(0));
        same(f.toBlock(8, 25.75, 4.5 + r), RigMath.apply(t0, 8, 8, 8 + r));
        Transformation t2 = RigMath.part(f, pivot, new Quaternionf().rotationX((float) (2 * Math.PI / 4)));
        same(f.toBlock(8, 25.75 - r, 4.5), RigMath.apply(t2, 8, 8, 8 + r));
    }
}
