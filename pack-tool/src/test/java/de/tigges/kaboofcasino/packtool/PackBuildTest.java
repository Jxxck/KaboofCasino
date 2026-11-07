package de.tigges.kaboofcasino.packtool;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Builds the real pack from the pinned mod jar (as Gradle passes it) and checks determinism and content. */
class PackBuildTest {

    private static PackTool.Result first;
    private static Map<String, byte[]> entries;
    private static List<String> order;

    private static Path modJar() {
        return Path.of(System.getProperty("kaboofcasino.modJar"));
    }

    private static Path packSrc() {
        return Path.of(System.getProperty("kaboofcasino.packSrc"));
    }

    @BeforeAll
    static void build() throws IOException {
        first = PackTool.build(modJar(), System.getProperty("kaboofcasino.modJarSha1"), packSrc());
        entries = new HashMap<>();
        order = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(first.zip()))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                entries.put(e.getName(), zip.readAllBytes());
                order.add(e.getName());
            }
        }
    }

    @Test
    void twoBuildsAreByteIdentical() {
        PackTool.Result second = PackTool.build(modJar(), System.getProperty("kaboofcasino.modJarSha1"), packSrc());
        assertArrayEquals(first.zip(), second.zip());
        assertEquals(first.sha1(), second.sha1());
        assertArrayEquals(first.layout(), second.layout());
        assertEquals(40, first.sha1().length());
    }

    @Test
    void entriesAreSortedAndUnique() {
        List<String> sorted = new ArrayList<>(order);
        sorted.sort(String::compareTo);
        assertEquals(sorted, order);
        assertEquals(order.size(), entries.size());
    }

    @Test
    void attributionIsInMetaAndCredits() {
        String meta = new String(entries.get("pack.mcmeta"), StandardCharsets.UTF_8);
        assertTrue(meta.contains(Credits.MOD_URL), meta);
        assertTrue(meta.contains("\"min_format\":97"), meta);
        String credits = new String(entries.get("credits/CREDITS.md"), StandardCharsets.UTF_8);
        assertTrue(credits.contains(Credits.MOD_URL));
        assertTrue(entries.containsKey("credits/LICENSE.txt"));
    }

    @Test
    void slotPartsArePresent() {
        for (String path : List.of(
                "assets/roulettecasino/models/block/slot_machine_lower.json",
                "assets/roulettecasino/items/block/slot_machine_upper.json",
                "assets/roulettecasino/items/slot_machine.json",
                "assets/roulettecasino/textures/block/casino_entity/slot_machine.png",
                "assets/kaboofcasino/models/slot/lever.json",
                "assets/kaboofcasino/models/slot/lights.json",
                "assets/kaboofcasino/items/slot/lights.json",
                "assets/kaboofcasino/models/slot/drum_2_2_7.json",
                "assets/kaboofcasino/items/slot/drum_0_0_0.json",
                "assets/roulettecasino/sounds/slot_lever.ogg",
                "assets/roulettecasino/sounds/lose.ogg",
                "assets/roulettecasino/sounds/blackjack_push.ogg",
                "assets/roulettecasino/lang/de_de.json",
                "assets/kaboofcasino/font/gui.json",
                "assets/kaboofcasino/font/t_m8.json",
                "assets/kaboofcasino/font/t_173.json",
                "assets/kaboofcasino/textures/font/text_h16.png",
                "assets/kaboofcasino/textures/font/text_h32.png",
                "assets/kaboofcasino/textures/font/slot/bg_1_1.png",
                "assets/kaboofcasino/items/gui/spin_on.json")) {
            assertTrue(entries.containsKey(path), path);
        }
        assertTrue(entries.keySet().stream().filter(p -> p.contains("/blockstates/"))
                .allMatch(DecoFullPack.BLOCKSTATE_FILES::contains), "only the three mushroom blockstates");
        assertTrue(entries.keySet().stream().noneMatch(p -> p.contains("sounds/music")), "no music in step 1");
        assertTrue(!entries.containsKey("assets/kaboofcasino/font/t_101.json"), "only the rows the GUIs use");
        for (String path : List.of("assets/roulettecasino/items/block/fortune_wheel_8.json",
                "assets/roulettecasino/items/block/plinko_0.json", "assets/roulettecasino/items/fortune_wheel.json",
                "assets/roulettecasino/items/plinko.json", "assets/roulettecasino/items/roulette_mechanism.json",
                "assets/kaboofcasino/items/wheel/plate_2.json", "assets/kaboofcasino/items/wheel/pennant.json",
                "assets/kaboofcasino/items/wheel/bulbs.json", "assets/kaboofcasino/items/plinko/ball.json",
                "assets/kaboofcasino/items/plinko/lights.json", "assets/kaboofcasino/items/plinko/pockets.json",
                "assets/kaboofcasino/items/wheel/plate.json",
                "assets/kaboofcasino/textures/block/plinko_glow_4.png",
                "assets/kaboofcasino/textures/block/wheel_plate_1_3.png",
                "assets/roulettecasino/textures/block/casino_entity/fortune_wheel.png",
                "assets/roulettecasino/textures/block/casino_entity/plinko.png",
                "assets/kaboofcasino/items/gui/wheel_on.json", "assets/kaboofcasino/items/gui/plinko_off.json",
                "assets/kaboofcasino/items/gui/collect_low.json", "assets/kaboofcasino/items/gui/stake_active.json")) {
            assertTrue(entries.containsKey(path), path);
        }
        assertTrue(!entries.containsKey("assets/kaboofcasino/textures/font/text_h64.png"), "no unused sheet");
    }

    /** Every model an item definition names (also inside composite/range_dispatch) and every texture exists. */
    @Test
    void everyReferenceResolves() {
        java.util.List<String> missing = new java.util.ArrayList<>();
        java.util.Set<String> models = new java.util.TreeSet<>();
        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            if (e.getKey().matches("assets/[a-z_]+/items/.+\\.json")) {
                collectModels(Json.parse(e.getValue()), models);
            }
        }
        java.util.Deque<String> todo = new java.util.ArrayDeque<>(models);
        java.util.Set<String> seen = new java.util.HashSet<>();
        while (!todo.isEmpty()) {
            String id = todo.poll();
            if (!seen.add(id) || id.startsWith("minecraft:")) {
                continue;
            }
            String path = "assets/" + id.substring(0, id.indexOf(':')) + "/models/" + id.substring(id.indexOf(':') + 1)
                    + ".json";
            byte[] json = entries.get(path);
            if (json == null) {
                missing.add(path);
                continue;
            }
            JsonObject model = Json.parse(json);
            if (model.has("parent")) {
                todo.add(withNamespace(model.get("parent").getAsString()));
            }
            if (model.has("textures")) {
                for (Map.Entry<String, com.google.gson.JsonElement> t : model.getAsJsonObject("textures").entrySet()) {
                    String tex = t.getValue().getAsString();
                    if (tex.startsWith("#")) {
                        continue;
                    }
                    tex = withNamespace(tex);
                    if (!tex.startsWith("minecraft:")) {
                        String texPath = "assets/" + tex.substring(0, tex.indexOf(':')) + "/textures/"
                                + tex.substring(tex.indexOf(':') + 1) + ".png";
                        if (!entries.containsKey(texPath)) {
                            missing.add(texPath);
                        }
                    }
                }
            }
        }
        assertTrue(models.size() > 100, "only " + models.size() + " models referenced");
        assertTrue(missing.isEmpty(), "unresolved: " + missing);
    }

    private static String withNamespace(String id) {
        return id.contains(":") ? id : "minecraft:" + id;
    }

    private static void collectModels(com.google.gson.JsonElement e, java.util.Set<String> out) {
        if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            if (o.has("type") && o.get("type").getAsString().equals("minecraft:model") && o.has("model")) {
                out.add(withNamespace(o.get("model").getAsString()));
            }
            for (Map.Entry<String, com.google.gson.JsonElement> c : o.entrySet()) {
                collectModels(c.getValue(), out);
            }
        } else if (e.isJsonArray()) {
            for (com.google.gson.JsonElement c : e.getAsJsonArray()) {
                collectModels(c, out);
            }
        }
    }

    @Test
    void langIsReducedToSlotKeys() {
        JsonObject de = Json.parse(entries.get("assets/roulettecasino/lang/de_de.json"));
        assertEquals("Spielautomat", de.get("block.roulettecasino.slot_machine").getAsString());
        assertTrue(de.has("subtitles.roulettecasino.slot_payout"));
        // the built games only: blackjack's and poker's texts are in, the derby's are not yet
        assertEquals("Blackjack-Tisch", de.get("block.roulettecasino.blackjack_table").getAsString());
        assertEquals("Pokertisch", de.get("block.roulettecasino.poker_table").getAsString());
        assertTrue(de.keySet().stream().noneMatch(k -> k.contains("derby")));
    }

    @Test
    void oddTexturesArePaddedAndEveryAtlasTextureIsAMultipleOf16() {
        assertTrue(first.padded().stream().anyMatch(p -> p.id().equals("kaboofcasino:item/gui/spin_on")),
                first.padded().toString());
        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            if (TexturePadding.atlasTextureId(e.getKey()) != null) {
                var image = Images.read(e.getValue());
                assertEquals(0, image.getWidth() % 16, e.getKey());
                assertEquals(0, image.getHeight() % 16, e.getKey());
            }
        }
        // the button quad used the whole 72x18 texture: after padding to 80x32 the UVs end at 14.4 / 9.0
        String model = new String(entries.get("assets/kaboofcasino/models/gui/spin_on.json"), StandardCharsets.UTF_8);
        assertTrue(model.contains("[0.0,0.0,14.4,9.0]"), model);
    }

    @Test
    void fontGlyphsFitTheClientLimits() {
        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            if (e.getKey().startsWith("assets/kaboofcasino/textures/font/slot/bg_")) {
                var image = Images.read(e.getValue());
                assertTrue(image.getWidth() <= 256 && image.getHeight() <= 256, e.getKey());
            }
        }
        JsonObject layout = Json.parse(first.layout());
        JsonObject bg = layout.getAsJsonObject("glyphs").getAsJsonObject("slot_bg_0_0");
        assertEquals(-23, bg.get("x").getAsInt());
        assertEquals(-20, bg.get("y").getAsInt());
        assertEquals(SlotMachineGui.TILE_W + 1, bg.get("advance").getAsInt());
        assertEquals(4, layout.getAsJsonObject("text").getAsJsonObject("advances").get(" ").getAsInt());
    }
}
