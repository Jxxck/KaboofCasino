package de.tigges.kaboofcasino.game.wheel;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * The wheel's moving parts stay inside the machine's own 3x3x1 footprint (wheel-local px x -16..32, y -16..32,
 * z 0..16) at every angle they reach, so nothing sticks out of the model or into a neighbouring block (the 3x3
 * machines have no spacing rule; any neighbour is a full block next to the footprint).
 */
class WheelGeometryTest {

    static byte[] packEntry(String path) throws IOException {
        try (InputStream in = WheelGeometryTest.class.getClassLoader().getResourceAsStream("pack/KaboofCasino-pack.zip");
             ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if (e.getName().equals(path)) {
                    return zip.readAllBytes();
                }
            }
        }
        throw new IllegalStateException("pack has no " + path);
    }

    @Test
    void theOpaqueDiscFitsTheFootprintAtEveryAngle() throws IOException {
        BufferedImage sheet = ImageIO.read(new ByteArrayInputStream(
                packEntry("assets/roulettecasino/textures/block/casino_entity/fortune_wheel.png")));
        double texelsPerPx = sheet.getWidth() / 1024.0 * 8;
        for (int level = 0; level < 3; level++) {
            double max = 0;
            int x0 = (int) Math.round(320 * level * sheet.getWidth() / 1024.0);
            int size = (int) Math.round(320 * sheet.getWidth() / 1024.0);
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    if ((sheet.getRGB(x0 + x, y) >>> 24) != 0) {
                        max = Math.max(max, Math.hypot(x + 0.5 - size / 2.0, y + 0.5 - size / 2.0));
                    }
                }
            }
            double radius = max / texelsPerPx;
            assertTrue(radius <= 20.5, "level " + level + ": opaque radius " + radius + " px");
            // any rotation of a disc of that radius about the axle (8, 11)
            assertTrue(8 - radius >= -16 && 8 + radius <= 32 && 11 - radius >= -16 && 11 + radius <= 32);
        }
    }

    @Test
    void thePennantSwingStaysInsideTheFootprint() throws IOException {
        JsonObject mesh = JsonParser.parseString(Files.readString(Path.of("pack-src/meshes/wheel_pennant.json")))
                .getAsJsonObject();
        double maxAngle = 0;
        java.util.Random r = new java.util.Random(1);
        for (int i = 0; i < 600; i++) {
            FortuneWheelSpin.Plan plan = FortuneWheelSpin.plan(r.nextInt(16), r.nextInt(16), r.nextLong());
            for (double t = 0; t < plan.landTick() + 80; t += 0.25) {
                maxAngle = Math.max(maxAngle, Math.abs(plan.flapper(t)));
            }
        }
        assertTrue(maxAngle <= FortuneWheelSpin.FLAP_LIMIT + 1e-9, "flapper " + maxAngle);
        // the mod reaches its clamp (36 degrees); the display clamps at WheelRig.PENNANT_MAX
        maxAngle = Math.min(maxAngle, WheelRig.PENNANT_MAX);
        for (JsonElement e : mesh.getAsJsonArray("elements")) {
            JsonObject el = e.getAsJsonObject();
            for (String corner : new String[] {"from", "to"}) {
                var v = el.getAsJsonArray(corner);
                double x = v.get(0).getAsDouble() - 8;
                double y = v.get(1).getAsDouble() - 8;
                double z = v.get(2).getAsDouble() - 8;
                assertTrue(z >= 0 && z <= 16, "pennant z " + z);
                for (double a : new double[] {-maxAngle, 0, maxAngle}) {
                    double rad = Math.toRadians(a);
                    for (double cx : new double[] {x, el.getAsJsonArray("from").get(0).getAsDouble() - 8}) {
                        double wx = 8 + cx * Math.cos(rad) - y * Math.sin(rad);
                        double wy = 30 + cx * Math.sin(rad) + y * Math.cos(rad);
                        assertTrue(wx >= -16 && wx <= 32, "pennant x " + wx + " at " + a);
                        assertTrue(wy >= -16 && wy <= 32, "pennant y " + wy + " at " + a + " (max angle "
                                + maxAngle + ")");
                    }
                }
            }
        }
    }

    @Test
    void bulbsAndPlateLieInFrontOfTheBodyInsideTheBlock() {
        for (int i = 0; i < 16; i++) {
            double x = WheelGeometry.BULBS[2 * i];
            double y = WheelGeometry.BULBS[2 * i + 1];
            assertTrue(x - 0.5 >= -16 && x + 0.5 <= 32 && y - 0.5 >= -16 && y + 0.5 <= 32);
        }
        assertTrue(WheelGeometry.PLATE_Z > 2.5 && WheelGeometry.PLATE_Z < 16, "plate in front of the back panel");
    }
}
