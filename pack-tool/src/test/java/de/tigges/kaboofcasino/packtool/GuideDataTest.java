package de.tigges.kaboofcasino.packtool;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** guide.json for the plugin: the mod's guide unchanged, complete German texts, and the pack ships every key. */
class GuideDataTest {

    private static ModJar jar;
    private static JsonObject data;
    private static Map<String, byte[]> pack;

    @BeforeAll
    static void build() throws IOException {
        Path modJar = Path.of(System.getProperty("kaboofcasino.modJar"));
        String sha1 = System.getProperty("kaboofcasino.modJarSha1");
        jar = ModJar.open(modJar, sha1);
        data = GuideData.data(jar);
        pack = new TreeMap<>();
        byte[] zip = PackTool.build(modJar, sha1, Path.of(System.getProperty("kaboofcasino.packSrc"))).zip();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                pack.put(e.getName(), in.readAllBytes());
            }
        }
    }

    @Test
    void theBookIsTheModsUnchanged() {
        assertEquals(Json.parse(jar.bytes(GuideData.BOOK)), data.getAsJsonObject("book"));
        assertEquals(34, data.getAsJsonObject("book").getAsJsonArray("pages").size());
        assertEquals(1, data.get("format").getAsInt());
    }

    @Test
    void sameJarSameBytes() {
        assertArrayEquals(GuideData.build(jar), GuideData.build(jar));
        List<String> keys = new ArrayList<>(data.getAsJsonObject("texts").keySet());
        List<String> sorted = new ArrayList<>(keys);
        sorted.sort(String::compareTo);
        assertEquals(sorted, keys);
    }

    @Test
    void everyKeyHasTheModsGermanText() {
        Map<String, String> german = GuideData.german(jar);
        JsonObject de = data.getAsJsonObject("texts");
        assertEquals(134, de.size());
        for (Map.Entry<String, JsonElement> e : de.entrySet()) {
            assertEquals(german.get(e.getKey()), e.getValue().getAsString(), e.getKey());
        }
        JsonObject names = data.getAsJsonObject("names");
        assertEquals(91, names.size());
        for (Map.Entry<String, JsonElement> e : names.entrySet()) {
            assertTrue(de.has(e.getValue().getAsString()), e.getKey());
            assertTrue(!e.getKey().startsWith("minecraft:"), e.getKey());
        }
        assertEquals("block.casinosound.casino_speaker", names.get("casinosound:casino_speaker").getAsString());
        assertEquals("item.roulettecasino.croupier_badge", names.get("roulettecasino:croupier_badge").getAsString());
        assertEquals("Croupier", de.get("entity.roulettecasino.villager.croupier").getAsString());
    }

    @Test
    void thePackShipsEveryKeyInEveryLanguage() {
        JsonObject de = data.getAsJsonObject("texts");
        int files = 0;
        for (String ns : GuideData.NAMESPACES) {
            for (String path : jar.list("assets/" + ns + "/lang/")) {
                files++;
                JsonObject mod = Json.parse(jar.bytes(path));
                JsonObject shipped = Json.parse(pack.get(path));
                for (String key : de.keySet()) {
                    if (key.startsWith(prefix(ns)) || key.contains("." + ns + ".")) {
                        assertEquals(mod.get(key), shipped.get(key), path + " " + key);
                    }
                }
            }
        }
        assertEquals(36, files, "3 namespaces x 12 languages");
        // the German file of each namespace holds exactly the guide's texts for its keys
        for (String key : de.keySet()) {
            String ns = key.split("\\.")[1];
            JsonObject shipped = Json.parse(pack.get("assets/" + ns + "/lang/de_de.json"));
            assertEquals(de.get(key), shipped.get(key), key);
        }
        assertTrue(pack.containsKey("assets/roulettecasino/items/casino_guide.json"));
        assertTrue(pack.containsKey("assets/roulettecasino/items/croupier_hand.json"));
        assertTrue(pack.containsKey("assets/roulettecasino/textures/item/croupier_hand.png"));
    }

    private static String prefix(String ns) {
        return "guide." + ns + ".";
    }
}
