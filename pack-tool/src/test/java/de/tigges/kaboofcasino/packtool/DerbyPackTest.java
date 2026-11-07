package de.tigges.kaboofcasino.packtool;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The duck derby's pack part: the twelve block models of the mod's blockstate, byte for byte, each with an item
 * definition that names it, the six items, the entity sheet in the blocks atlas, the four ducks (lane UV offsets), the
 * gates, the sign overlay with its 12 tinted self-lit bulbs, the panel font; deterministic.
 */
class DerbyPackTest {

    private static ModJar jar;
    private static PackTree tree;

    private static PackTree build() {
        PackTree t = new PackTree();
        DerbyPack.add(jar, t, Path.of(System.getProperty("kaboofcasino.packSrc")));
        return t;
    }

    @BeforeAll
    static void open() {
        jar = ModJar.open(Path.of(System.getProperty("kaboofcasino.modJar")),
                System.getProperty("kaboofcasino.modJarSha1"));
        tree = build();
    }

    private static JsonObject json(String path) {
        assertTrue(tree.has(path), path);
        return Json.parse(tree.get(path));
    }

    @Test
    void deterministic() {
        PackTree again = build();
        assertEquals(tree.files().keySet(), again.files().keySet());
        for (Map.Entry<String, byte[]> e : tree.files().entrySet()) {
            assertArrayEquals(e.getValue(), again.get(e.getKey()), e.getKey());
        }
    }

    @Test
    void blockstateModelsAreCopiedVerbatimWithItemDefinitions() {
        JsonObject variants = Json.parse(jar.bytes("assets/roulettecasino/blockstates/duck_derby.json"))
                .getAsJsonObject("variants");
        TreeSet<String> named = new TreeSet<>();
        for (Map.Entry<String, JsonElement> v : variants.entrySet()) {
            named.add(v.getValue().getAsJsonObject().get("model").getAsString());
            // the blockstate turns every part by 90 x turns (S 0, W 90, N 180, E 270) - what the rig's facing turn does
            String facing = v.getKey().replaceAll("facing=(\\w+),.*", "$1");
            int y = v.getValue().getAsJsonObject().has("y") ? v.getValue().getAsJsonObject().get("y").getAsInt() : 0;
            assertEquals(switch (facing) {
                case "south" -> 0;
                case "west" -> 90;
                case "north" -> 180;
                default -> 270;
            }, y, v.getKey());
        }
        assertEquals(DerbyPack.PARTS, named.size());
        for (int part = 0; part < DerbyPack.PARTS; part++) {
            String key = DerbyPack.bodyModel(part);
            assertTrue(named.contains(key), key);
            String path = "assets/roulettecasino/models/block/duck_derby_" + part + ".json";
            assertArrayEquals(jar.bytes(path), tree.get(path), path);
            JsonObject def = json("assets/roulettecasino/items/block/duck_derby_" + part + ".json")
                    .getAsJsonObject("model");
            assertEquals(key, def.get("model").getAsString());
            for (Map.Entry<String, JsonElement> t : json(path).getAsJsonObject("textures").entrySet()) {
                String tex = t.getValue().getAsString();
                String texPath = "assets/" + tex.replace(":", "/textures/") + ".png";
                assertTrue(tree.has(texPath), texPath);
            }
        }
        for (String id : new String[] {"duck_derby", "duck_derby_mechanism", "derby_duck_yellow", "derby_duck_red",
            "derby_duck_white", "derby_duck_black"}) {
            assertTrue(tree.has("assets/roulettecasino/items/" + id + ".json"), id);
        }
        assertArrayEquals(jar.bytes("assets/roulettecasino/textures/entity/duck_derby.png"),
                tree.get("assets/roulettecasino/textures/block/casino_entity/duck_derby.png"));
    }

    private static void inLimits(JsonArray elements, String what) {
        for (JsonElement e : elements) {
            for (String end : new String[] {"from", "to"}) {
                for (JsonElement c : e.getAsJsonObject().getAsJsonArray(end)) {
                    assertTrue(c.getAsDouble() >= -16 && c.getAsDouble() <= 32, what + " " + e);
                }
            }
        }
    }

    @Test
    void ducksGatesAndSign() {
        for (int lane = 0; lane < 4; lane++) {
            JsonObject duck = json("assets/kaboofcasino/models/derby/duck_" + lane + ".json");
            assertEquals(DerbyModels.SHEET, duck.getAsJsonObject("textures").get("sheet").getAsString());
            assertEquals(48, duck.getAsJsonArray("elements").size());
            inLimits(duck.getAsJsonArray("elements"), "duck " + lane);
            assertTrue(tree.has("assets/kaboofcasino/items/derby/duck_" + lane + ".json"));
        }
        JsonArray gates = json("assets/kaboofcasino/models/derby/gates.json").getAsJsonArray("elements");
        assertEquals(16, gates.size());
        inLimits(gates, "gates");
        // gate l is gate 0 moved by GATE_X[l] - GATE_X[0] (12 px steps)
        for (int lane = 1; lane < 4; lane++) {
            for (int q = 0; q < 4; q++) {
                JsonArray a = gates.get(q).getAsJsonObject().getAsJsonArray("from");
                JsonArray b = gates.get(4 * lane + q).getAsJsonObject().getAsJsonArray("from");
                assertEquals(a.get(0).getAsDouble() + 12 * lane, b.get(0).getAsDouble(), 1e-9);
                assertEquals(a.get(1).getAsDouble(), b.get(1).getAsDouble(), 1e-9);
            }
        }
        JsonObject sign = json("assets/kaboofcasino/models/derby/sign.json");
        JsonArray els = sign.getAsJsonArray("elements");
        assertEquals(13, els.size());
        inLimits(els, "sign");
        for (int i = 0; i < 12; i++) {
            JsonObject el = els.get(1 + i).getAsJsonObject();
            assertEquals(15, el.get("light_emission").getAsInt());
            assertEquals(i, el.getAsJsonObject("faces").getAsJsonObject("south").get("tintindex").getAsInt());
            double cx = (el.getAsJsonArray("from").get(0).getAsDouble() + el.getAsJsonArray("to").get(0).getAsDouble())
                    / 2 + 16;
            assertEquals(DerbyModels.bulbX(i), cx, 1e-4);
        }
        JsonArray tints = json("assets/kaboofcasino/items/derby/sign.json").getAsJsonObject("model")
                .getAsJsonArray("tints");
        assertEquals(12, tints.size());
        // DuckDerbySignLights: two on the left post, eight along the top, two on the right
        assertEquals(4.5, DerbyModels.bulbX(0));
        assertEquals(26.0, DerbyModels.bulbY(1));
        assertEquals(30.5, DerbyModels.bulbY(5));
        assertEquals(43.5, DerbyModels.bulbX(11));
        assertEquals(21.0, DerbyModels.bulbY(11));
    }

    @Test
    void panelFont() {
        JsonObject font = json(DerbyPack.FONT);
        int bitmaps = 0;
        for (JsonElement e : font.getAsJsonArray("providers")) {
            JsonObject p = e.getAsJsonObject();
            if (!p.get("type").getAsString().equals("bitmap")) {
                assertEquals(16, p.getAsJsonObject("advances").size());
                continue;
            }
            bitmaps++;
            String file = p.get("file").getAsString();
            BufferedImage img = Images.read(tree.get("assets/" + file.replace(":", "/textures/")));
            assertEquals(img.getHeight(), p.get("height").getAsInt(), file);
            assertTrue(p.get("ascent").getAsInt() <= img.getHeight(), file);
            assertTrue(img.getWidth() <= 256, file);
        }
        assertEquals(4 + DerbyPack.BAR_GLYPHS, bitmaps);
        // the header edge is only its bottom row (HEADER_EDGE drawn as a line), the frame the brass ribbon
        BufferedImage edge = Images.read(tree.get("assets/kaboofcasino/textures/font/derby_panel/edge.png"));
        assertEquals(0, edge.getRGB(10, 5) >>> 24);
        assertEquals(DerbyPack.EDGE_LINE, edge.getRGB(10, 11));
        BufferedImage track = Images.read(tree.get("assets/kaboofcasino/textures/font/derby_panel/track.png"));
        assertEquals(DerbyPack.BRASS[0], track.getRGB(50, 0));
        assertEquals(DerbyPack.HIGHLIGHT, track.getRGB(50, 1));
        assertEquals(DerbyPack.BRASS[2], track.getRGB(50, 2));
        assertEquals(DerbyPack.BRASS[1], track.getRGB(50, 3));
        assertEquals(DerbyPack.CORNER, track.getRGB(1, 1));
    }
}
