package de.tigges.kaboofcasino.pack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The embedded pack is present, its SHA-1 matches the build's and the URL carries the hash. */
class PackFileTest {

    @Test
    void embeddedPackMatchesItsHash() {
        PackFile pack = PackFile.load(PackFileTest.class.getClassLoader());
        assertEquals(40, pack.sha1().length());
        assertTrue(pack.size() > 100_000);
        assertEquals("/pack/" + pack.sha1() + ".zip", pack.path());
        assertEquals(UUID.nameUUIDFromBytes("kaboofcasino-pack".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                PackFile.PACK_ID);
    }
}
