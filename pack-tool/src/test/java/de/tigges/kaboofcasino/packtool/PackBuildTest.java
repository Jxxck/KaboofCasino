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
                "assets/kaboofcasino/models/slot/drum_2_2.json",
                "assets/kaboofcasino/items/slot/drum_0_0.json",
                "assets/roulettecasino/sounds/slot_lever.ogg",
                "assets/roulettecasino/sounds/lose.ogg",
                "assets/roulettecasino/sounds/blackjack_push.ogg",
                "assets/roulettecasino/lang/de_de.json",
                "assets/kaboofcasino/font/gui.json",
                "assets/kaboofcasino/font/t_m24.json",
                "assets/kaboofcasino/font/t_157.json",
                "assets/kaboofcasino/textures/font/slot/bg_1_1.png",
                "assets/kaboofcasino/items/gui/spin_on.json")) {
            assertTrue(entries.containsKey(path), path);
        }
        assertTrue(entries.keySet().stream().noneMatch(p -> p.contains("/blockstates/")));
        assertTrue(entries.keySet().stream().noneMatch(p -> p.contains("sounds/music")), "no music in step 1");
    }

    @Test
    void langIsReducedToSlotKeys() {
        JsonObject de = Json.parse(entries.get("assets/roulettecasino/lang/de_de.json"));
        assertEquals("Spielautomat", de.get("block.roulettecasino.slot_machine").getAsString());
        assertTrue(de.has("subtitles.roulettecasino.slot_payout"));
        assertTrue(de.keySet().stream().noneMatch(k -> k.contains("blackjack")));
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
        assertEquals(-24, bg.get("y").getAsInt());
        assertEquals(SlotMachineGui.TILE_W + 1, bg.get("advance").getAsInt());
        assertEquals(4, layout.getAsJsonObject("text").getAsJsonObject("advances").get(" ").getAsInt());
    }
}
