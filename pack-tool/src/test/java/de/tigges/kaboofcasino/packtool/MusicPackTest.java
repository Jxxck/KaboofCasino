package de.tigges.kaboofcasino.packtool;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The optional music pack from the pinned mod jar: deterministic, complete, attributed, nothing else. */
class MusicPackTest {

    private static ModJar jar;
    private static MusicPack.Result first;
    private static Map<String, byte[]> entries;
    private static Map<String, Integer> methods;
    private static List<String> order;

    @BeforeAll
    static void build() throws IOException {
        jar = ModJar.open(Path.of(System.getProperty("kaboofcasino.modJar")),
                System.getProperty("kaboofcasino.modJarSha1"));
        first = MusicPack.build(jar);
        entries = new TreeMap<>();
        methods = new TreeMap<>();
        order = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(first.zip()))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                entries.put(e.getName(), zip.readAllBytes());
                methods.put(e.getName(), e.getMethod());
                order.add(e.getName());
            }
        }
    }

    @Test
    void twoBuildsAreByteIdentical() {
        MusicPack.Result second = MusicPack.build(ModJar.open(Path.of(System.getProperty("kaboofcasino.modJar")),
                System.getProperty("kaboofcasino.modJarSha1")));
        assertArrayEquals(first.zip(), second.zip());
        assertEquals(first.sha1(), second.sha1());
        assertEquals(ModJar.sha1Hex(first.zip()), first.sha1());
        List<String> sorted = new ArrayList<>(order);
        sorted.sort(String::compareTo);
        assertEquals(sorted, order, "sorted entries");
    }

    @Test
    void containsExactlyTheMusicAndItsCredits() {
        List<String> expected = new ArrayList<>(List.of("assets/casinosound/music-credits.json",
                "assets/casinosound/sounds.json", "credits/CREDITS.md", "credits/LICENSE-MUSIC-CC-BY-4.0.txt",
                "credits/LICENSE.txt", "credits/MUSIC-CREDITS.md", "pack.mcmeta", "pack.png"));
        for (String id : MusicPack.TRACKS) {
            expected.add("assets/casinosound/sounds/music/" + id + ".ogg");
        }
        expected.sort(String::compareTo);
        assertEquals(expected, List.copyOf(entries.keySet()));
        assertEquals(expected.size(), first.files());
    }

    @Test
    void soundsAndOggsAreTheModsUnchangedAndStored() {
        assertArrayEquals(jar.bytes("assets/casinosound/sounds.json"), entries.get("assets/casinosound/sounds.json"));
        long total = 0;
        for (String id : MusicPack.TRACKS) {
            String path = "assets/casinosound/sounds/music/" + id + ".ogg";
            assertArrayEquals(jar.bytes(path), entries.get(path), path);
            assertEquals(ZipEntry.STORED, methods.get(path), "oggs are stored, not deflated");
            total += entries.get(path).length;
        }
        assertTrue(total > 20_000_000 && total < 25_000_000, "about 22 MB of music: " + total);
        JsonObject sounds = Json.parse(entries.get("assets/casinosound/sounds.json"));
        assertEquals(6, sounds.size());
        assertTrue(sounds.getAsJsonObject("jazz_brunch").getAsJsonArray("sounds").get(0).getAsJsonObject()
                .get("stream").getAsBoolean());
    }

    @Test
    void attributionIsInMetaAndCredits() {
        String meta = new String(entries.get("pack.mcmeta"), StandardCharsets.UTF_8);
        assertTrue(meta.contains("Kevin MacLeod"), meta);
        assertTrue(meta.contains("CC BY 4.0") && meta.contains(MusicPack.CC_BY), meta);
        assertTrue(meta.contains(Credits.MOD_URL), meta);
        assertTrue(meta.contains("\"min_format\":97") && meta.contains("\"max_format\":97"), meta);
        String credits = new String(entries.get("credits/CREDITS.md"), StandardCharsets.UTF_8);
        for (String title : List.of("Airport Lounge", "Backbay Lounge", "Jazz Brunch", "George Street Shuffle",
                "Cool Vibes", "Bossa Antigua")) {
            assertTrue(credits.contains("\"" + title + "\" - Kevin MacLeod"), title);
        }
        assertTrue(credits.contains(MusicPack.CC_BY));
        assertTrue(credits.contains("-20 LUFS") && credits.contains("Ogg Vorbis"), "changes stated");
        assertTrue(credits.contains("https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1100806"));
        assertTrue(credits.contains(Credits.MOD_URL));
        String legal = new String(entries.get("credits/LICENSE-MUSIC-CC-BY-4.0.txt"), StandardCharsets.UTF_8);
        assertTrue(legal.contains("Attribution 4.0 International"));
    }

    @Test
    void theModsDataIsChecked() {
        JsonObject wrong = Json.parse(jar.bytes("assets/casinosound/sounds.json"));
        wrong.remove("cool_vibes");
        assertThrows(IllegalStateException.class, () -> MusicPack.checkSounds(wrong));
        JsonObject notStreamed = Json.parse(jar.bytes("assets/casinosound/sounds.json"));
        notStreamed.getAsJsonObject("cool_vibes").getAsJsonArray("sounds").get(0).getAsJsonObject()
                .addProperty("stream", false);
        assertThrows(IllegalStateException.class, () -> MusicPack.checkSounds(notStreamed));
        assertEquals(MusicPack.TRACKS, MusicPack.tracks(Json.parse(jar.bytes("assets/casinosound/music-credits.json")))
                .stream().map(MusicPack.Track::id).toList());
    }
}
