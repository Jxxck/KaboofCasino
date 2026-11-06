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
    void drumHas48FacetsCoveringOneFullTurnOfTheStrip() {
        JsonArray drum = SlotMachinePack.drum(1, 2);
        assertEquals(48, drum.size());
        withinExtents(drum);
        double first = drum.get(0).getAsJsonObject().getAsJsonObject("faces").getAsJsonObject("south")
                .getAsJsonArray("uv").get(1).getAsDouble();
        double last = drum.get(47).getAsJsonObject().getAsJsonObject("faces").getAsJsonObject("south")
                .getAsJsonArray("uv").get(3).getAsDouble();
        // one turn = 8 faces x 44 px = 352 px of the 1056 px sheet
        assertEquals(352.0 / 1056 * 16, last - first, 1e-3);
        assertTrue(first >= 0 && last <= 16);
        // facet 24 is centred on the front (+Z, angle ~ 0) and shows face 0 at row 682
        JsonObject front = drum.get(24).getAsJsonObject();
        assertEquals(3.75, front.getAsJsonObject("rotation").get("angle").getAsDouble(), 1e-6);
        double v0 = front.getAsJsonObject("faces").getAsJsonObject("south").getAsJsonArray("uv").get(1)
                .getAsDouble();
        assertEquals(682.0 / 1056 * 16, v0, 1e-4);
        // reel 1, blur level 2 uses strip x = 200 (STRIP_X[3k + level])
        double u0 = front.getAsJsonObject("faces").getAsJsonObject("south").getAsJsonArray("uv").get(0)
                .getAsDouble();
        assertEquals(200.5 / 512 * 16, u0, 1e-4);
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
        // ascent = 17 - y must stay <= the 64 px cell for the highest row
        assertTrue(17 - TextGlyphs.MIN_Y <= TextGlyphs.CELL_H);
    }

    @Test
    void slotGuiCompositionKeepsTheInventoryAndShiftsThePayoutPanel() throws Exception {
        try (var zip = new java.util.zip.ZipFile(System.getProperty("kaboofcasino.modJar"))) {
            byte[] png = zip.getInputStream(zip.getEntry("assets/roulettecasino/textures/gui/slot_machine.png"))
                    .readAllBytes();
            var mod = Images.read(png);
            var out = SlotMachineGui.compose(mod);
            assertEquals(358, out.getWidth());
            assertEquals(266, out.getHeight());
            // composite origin = window (-23, -24); the inventory art (mod 31,176 -> window 8,140) and the frame's
            // bottom line (mod y 156) moved 12 rows up in the composite
            assertEquals(mod.getRGB(40, 200), out.getRGB(40, 188));
            assertEquals(mod.getRGB(60, 156), out.getRGB(60, 144));
            assertEquals(mod.getRGB(100, 140), out.getRGB(100, 128));
            // the top part of the left panel is unchanged in the composite (it moved down in the window)
            assertEquals(mod.getRGB(50, 40), out.getRGB(50, 40));
            // the payout panel moved 8 px left
            assertEquals(mod.getRGB(300, 100), out.getRGB(292, 100));
            // the bet slot frame (mod 101,93) is now at (100,93)
            assertEquals(mod.getRGB(101, 93), out.getRGB(100, 93));
        }
    }
}
