package de.tigges.kaboofcasino.deco.display;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.tigges.kaboofcasino.machine.Facing;
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
}
