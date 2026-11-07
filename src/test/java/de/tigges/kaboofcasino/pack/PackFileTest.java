package de.tigges.kaboofcasino.pack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The embedded packs are present, their SHA-1 matches the build's and the URL carries the hash. */
class PackFileTest {

    private static final Set<String> TRACKS = Set.of("airport_lounge", "backbay_lounge", "jazz_brunch",
            "george_street_shuffle", "cool_vibes", "bossa_antigua");

    @Test
    void embeddedPackMatchesItsHash() {
        PackFile pack = PackFile.load(PackFileTest.class.getClassLoader());
        assertEquals(40, pack.sha1().length());
        assertTrue(pack.size() > 100_000);
        assertEquals("/pack/" + pack.sha1() + ".zip", pack.path());
        assertEquals(UUID.nameUUIDFromBytes("kaboofcasino-pack".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                PackFile.PACK_ID);
        assertEquals(PackFile.PACK_ID, pack.id());
    }

    @Test
    void embeddedMusicPackIsASeparatePack() {
        PackFile pack = PackFile.load(PackFileTest.class.getClassLoader());
        PackFile music = PackFile.loadMusic(PackFileTest.class.getClassLoader());
        assertEquals(40, music.sha1().length());
        assertNotEquals(pack.sha1(), music.sha1());
        assertNotEquals(pack.path(), music.path());
        assertEquals(UUID.nameUUIDFromBytes("kaboofcasino-music".getBytes(StandardCharsets.UTF_8)), PackFile.MUSIC_ID);
        assertEquals(PackFile.MUSIC_ID, music.id());
        assertNotEquals(PackFile.PACK_ID, PackFile.MUSIC_ID);
        assertTrue(music.size() > 20_000_000, "six streamed tracks: " + music.size());
        assertTrue(music.size() < 250 * 1024 * 1024, "client limit");
    }

    @Test
    void theMusicPackHoldsExactlyTheSixEventsAndTheirFiles() {
        Map<String, byte[]> files = PackFiles.music();
        JsonObject sounds = JsonParser.parseString(new String(files.get("assets/casinosound/sounds.json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(TRACKS, new TreeSet<>(sounds.keySet()));
        Set<String> oggs = new TreeSet<>();
        for (String path : files.keySet()) {
            if (path.endsWith(".ogg")) {
                oggs.add(path);
            }
            assertTrue(path.startsWith("assets/casinosound/") || path.startsWith("credits/")
                    || path.equals("pack.mcmeta") || path.equals("pack.png"), "nothing else: " + path);
        }
        assertEquals(6, oggs.size());
        for (String id : TRACKS) {
            assertTrue(oggs.contains("assets/casinosound/sounds/music/" + id + ".ogg"), id);
        }
        String meta = new String(files.get("pack.mcmeta"), StandardCharsets.UTF_8);
        assertTrue(meta.contains("Kevin MacLeod") && meta.contains("CC BY 4.0")
                && meta.contains("https://creativecommons.org/licenses/by/4.0/"), meta);
        String credits = new String(files.get("credits/CREDITS.md"), StandardCharsets.UTF_8);
        for (String title : new String[] {"Airport Lounge", "Backbay Lounge", "Jazz Brunch", "George Street Shuffle",
            "Cool Vibes", "Bossa Antigua"}) {
            assertTrue(credits.contains(title), title);
        }
        assertTrue(files.containsKey("credits/LICENSE-MUSIC-CC-BY-4.0.txt"));
        assertTrue(files.containsKey("credits/MUSIC-CREDITS.md"));
    }

    @Test
    void theCasinoPackHasNoMusic() {
        for (String path : PackFiles.all().keySet()) {
            assertTrue(!path.contains("sounds/music/"), path);
            assertTrue(!path.equals("assets/casinosound/sounds.json"), path);
            assertTrue(!path.startsWith("assets/casinosound/") || !path.endsWith(".ogg"), path);
        }
    }
}
