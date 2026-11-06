package de.tigges.kaboofcasino.packtool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Drum, lights, text fonts and GUI composition: geometry the client would reject or render wrong. */
class GeometryTest {

    private static void withinExtents(JsonArray elements) {
        for (JsonElement e : elements) {
            JsonObject el = e.getAsJsonObject();
            for (String key : new String[] {"from", "to"}) {
                for (JsonElement c : el.getAsJsonArray(key)) {
                    double v = c.getAsDouble();
                    assertTrue(v >= -16 && v <= 32, key + " " + el);
                }
            }
        }
    }

    @Test
    void partialDrumsCoverTheWindowAndStayInsideTheCabinet() {
        for (int face = 0; face < 8; face++) {
            JsonArray drum = SlotMachinePack.drum(1, 2, face);
            assertEquals(22, drum.size());
            withinExtents(drum);
            // the facet starting at the front (angle 0) shows face `face` at row 682 - 44 face
            JsonObject front = drum.get(SlotMachinePack.DRUM_HALF_FACETS).getAsJsonObject();
            assertEquals(3.75, front.getAsJsonObject("rotation").get("angle").getAsDouble(), 1e-6);
            double v0 = front.getAsJsonObject("faces").getAsJsonObject("south").getAsJsonArray("uv").get(1)
                    .getAsDouble();
            assertEquals((682.0 - 44 * face) / 1056 * 16, v0, 1e-4);
            double u0 = front.getAsJsonObject("faces").getAsJsonObject("south").getAsJsonArray("uv").get(0)
                    .getAsDouble();
            assertEquals(200.5 / 512 * 16, u0, 1e-4); // reel 1, blur 2: STRIP_X[5] = 200
            for (JsonElement e : drum) {
                JsonObject el = e.getAsJsonObject();
                double v = el.getAsJsonObject("faces").getAsJsonObject("south").getAsJsonArray("uv").get(3)
                        .getAsDouble();
                assertTrue(v > 0 && v < 16, "uv inside the sheet");
            }
        }
        // corners of every facet, turned by the display (at most half a symbol, 22.5 degrees), in machine px
        double minZ = Double.MAX_VALUE;
        double coverLow = Double.MAX_VALUE;
        double coverHigh = -Double.MAX_VALUE;
        for (JsonElement e : SlotMachinePack.drum(0, 0, 0)) {
            JsonObject el = e.getAsJsonObject();
            double mid = Math.toRadians(el.getAsJsonObject("rotation").get("angle").getAsDouble());
            double y0 = el.getAsJsonArray("from").get(1).getAsDouble() - 8;
            double y1 = el.getAsJsonArray("to").get(1).getAsDouble() - 8;
            double z = el.getAsJsonArray("from").get(2).getAsDouble() - 8;
            for (double phiDeg : new double[] {-22.5, 0, 22.5}) {
                double a = mid + Math.toRadians(phiDeg);
                for (double y : new double[] {y0, y1}) {
                    // rotate (y, z) about X by a: y' = y cos a - z sin a, z' = y sin a + z cos a
                    double zz = y * Math.sin(a) + z * Math.cos(a);
                    minZ = Math.min(minZ, SlotMachinePack.AXIS_Z + zz);
                }
            }
            coverLow = Math.min(coverLow, Math.toDegrees(mid) - 3.75 + 22.5);
            coverHigh = Math.max(coverHigh, Math.toDegrees(mid) + 3.75 - 22.5);
        }
        assertTrue(minZ >= 2.0, "a facet reaches behind the back plate (z 2): " + minZ);
        assertTrue(minZ > 0, "a facet leaves the block (back-to-back machines would intersect)");
        // at any turn within +-22.5 degrees the arc still covers the mod's window of +-60 degrees
        assertTrue(coverLow <= -60 && coverHigh >= 60, coverLow + ".." + coverHigh);
    }

    @Test
    void lightsHaveSixteenTintsAndGlow() {
        JsonObject lamp = Json.parse(ModJar.readAll(Path.of(System.getProperty("kaboofcasino.packSrc"))
                .resolve("meshes/slot_lamp.json")));
        JsonArray lights = SlotMachinePack.lights(lamp);
        withinExtents(lights);
        int maxTint = -1;
        for (JsonElement e : lights) {
            JsonObject el = e.getAsJsonObject();
            assertEquals(15, el.get("light_emission").getAsInt());
            for (Map.Entry<String, JsonElement> f : el.getAsJsonObject("faces").entrySet()) {
                maxTint = Math.max(maxTint, f.getValue().getAsJsonObject().get("tintindex").getAsInt());
            }
        }
        assertEquals(15, maxTint);
    }

    @Test
    void textFontsCoverTheWholeWindowAndAdvancesMatchVanilla() {
        TextGlyphs text = TextGlyphs.load(Path.of(System.getProperty("kaboofcasino.packSrc")).resolve("vanilla"));
        Map<Character, Integer> adv = text.advances();
        assertEquals(6, adv.get('A'));
        assertEquals(2, adv.get('i'));
        assertEquals(3, adv.get('l'));
        assertEquals(4, adv.get(' '));
        assertEquals(6, adv.get('ü'));
        assertTrue(adv.containsKey('×'));
        assertEquals("kaboofcasino:t_m24", TextGlyphs.fontId(-24));
        assertEquals("kaboofcasino:t_10", TextGlyphs.fontId(10));
        // ascent = 17 - y must stay <= the cell height: the smallest sheet that allows it
        assertEquals(16, TextGlyphs.sheetHeight(1));
        assertEquals(32, TextGlyphs.sheetHeight(0));
        assertEquals(32, TextGlyphs.sheetHeight(-15));
        assertEquals(64, TextGlyphs.sheetHeight(-16));
        assertEquals(16, TextGlyphs.sheetHeight(200));
    }

    @Test
    void slotGuiCompositionKeepsTheInventoryAndShiftsThePayoutPanel() throws Exception {
        try (var zip = new java.util.zip.ZipFile(System.getProperty("kaboofcasino.modJar"))) {
            byte[] png = zip.getInputStream(zip.getEntry("assets/roulettecasino/textures/gui/slot_machine.png"))
                    .readAllBytes();
            var mod = Images.read(png);
            var out = SlotMachineGui.compose(mod);
            assertEquals(324, out.getWidth());
            assertEquals(266, out.getHeight());
            // composite origin = window (-23, -20); the inventory art (mod 31,176 -> window 8,140) and the frame's
            // bottom line (mod y 156) moved 16 rows up in the composite
            assertEquals(mod.getRGB(40, 200), out.getRGB(40, 184));
            assertEquals(mod.getRGB(60, 156), out.getRGB(60, 140));
            assertEquals(mod.getRGB(100, 140), out.getRGB(100, 124));
            // the top part of the left panel is unchanged in the composite (it moved down in the window)
            assertEquals(mod.getRGB(50, 40), out.getRGB(50, 40));
            // the left frame's right border moved 19 px left, the payout panel 27 px (left of its cut) and 42 px
            assertEquals(mod.getRGB(212, 100), out.getRGB(193, 100));
            assertEquals(mod.getRGB(250, 100), out.getRGB(223, 100));
            assertEquals(mod.getRGB(345, 100), out.getRGB(303, 100));
            // the bet slot frame (mod 101,93) moved by (-1, -4)
            assertEquals(mod.getRGB(101, 93), out.getRGB(100, 89));
            // the JACKPOT lettering is complete and centred in the narrower panel (mod 262..336 -> 227..301)
            assertEquals(mod.getRGB(300, 238), out.getRGB(205 + 22 + 38, 238));
        }
    }
}
