package de.tigges.kaboofcasino.game.roulette;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The roulette's moving parts stay inside the table's own 2 x 2 x 1 cells (px 0..32 x 0..16 x 0..32 from the
 * origin corner) at every angle, and the ball comes to rest over the pocket the server drew (the drawn colour).
 */
class RouletteGeometryTest {

    @Test
    void theRotorStaysInsideTheTableAtEveryAngle() throws IOException {
        JsonObject mesh = JsonParser.parseString(Files.readString(Path.of("pack-src/meshes/roulette_rotor.json")))
                .getAsJsonObject();
        double maxRadius = 0;
        for (JsonElement e : mesh.getAsJsonArray("elements")) {
            JsonObject el = e.getAsJsonObject();
            for (String corner : new String[] {"from", "to"}) {
                var v = el.getAsJsonArray(corner);
                double x = v.get(0).getAsDouble() - 8;
                double y = v.get(1).getAsDouble() - 8;
                double z = v.get(2).getAsDouble() - 8;
                // the rotor turns about Y through the model centre: a corner's distance from the axis never changes
                maxRadius = Math.max(maxRadius, Math.hypot(x, z));
                assertTrue(y >= 0 && y <= 16, "rotor y " + y);
            }
        }
        // element rotations are about the same axis (origin 8, 8, 8), so rotated corners keep their radius too
        assertTrue(RouletteRig.CENTRE - maxRadius >= 0 && RouletteRig.CENTRE + maxRadius <= 32,
                "rotor radius " + maxRadius);
    }

    @Test
    void theBallStaysInsideTheTableAndRestsOnTheDrawnPocket() {
        for (int initial = 0; initial < 37; initial++) {
            for (int roll = 0; roll < 37; roll++) {
                RouletteSpin.Plan plan = RouletteSpin.plan(initial, roll);
                for (double t = 0; t <= 170; t += 0.5) {
                    double[] p = RouletteRig.ballPoint(plan.ballPhase(t), RouletteSpin.ballRadius(plan.track(t)),
                            plan.ballHeight(t));
                    assertTrue(p[0] - 0.5 >= 0 && p[0] + 0.5 <= 32 && p[2] - 0.5 >= 0 && p[2] + 0.5 <= 32);
                    assertTrue(p[1] - 0.5 >= 0 && p[1] + 0.5 <= 16, "ball y " + p[1]);
                }
                // at the result the ball sits over the centre of pocket `roll` of the rotor at its final frame:
                // pocket k's world angle at rotor phase f is (k + 0.5) A - 90 deg - f A (RouletteBetRenderer)
                double f = plan.phase(plan.resultTick());
                double pocketAngle = (roll + 0.5) * RouletteSpin.ANGLE - Math.PI / 2 - f * RouletteSpin.ANGLE;
                double ballAngle = RouletteRig.ballAngle(plan.ballPhase(plan.resultTick()));
                double diff = Math.IEEEremainder(ballAngle - pocketAngle, 2 * Math.PI);
                assertEquals(0, diff, 1e-6, "initial " + initial + " roll " + roll);
            }
        }
    }
}
