package de.tigges.kaboofcasino.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigLoaderTest {

    private static InputStream jar() {
        return ConfigLoaderTest.class.getClassLoader().getResourceAsStream("config.yml");
    }

    @Test
    void shippedFileIsTheDefaults(@TempDir Path dir) {
        ConfigLoader.Loaded loaded = ConfigLoader.load(dir.resolve("config.yml"), ConfigLoaderTest::jar);
        assertEquals(CasinoConfig.DEFAULTS, loaded.config());
        assertEquals(0, loaded.warnings().size(), loaded.warnings().toString());
        assertEquals(2000, loaded.config().bets().maxStake());
        assertTrue(!loaded.config().pack().enforce(), "the kill switch ships off");
        assertEquals(25580, loaded.config().pack().port());
    }

    @Test
    void missingKeysAreMergedAndBadValuesFallBack(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.yml");
        Files.writeString(file, """
                pack:
                  enforce: yes
                  port: 99999
                bets:
                  max-stake: 1000001
                typo: 1
                """, StandardCharsets.UTF_8);
        ConfigLoader.Loaded loaded = ConfigLoader.load(file, ConfigLoaderTest::jar);
        CasinoConfig c = loaded.config();
        assertTrue(c.pack().enforce());
        assertEquals(25580, c.pack().port());
        assertEquals(2000, c.bets().maxStake(), "out of range: the default");
        assertTrue(loaded.added() > 5);
        assertTrue(loaded.warnings().stream().anyMatch(w -> w.contains("typo")));
        assertTrue(loaded.warnings().stream().anyMatch(w -> w.contains("pack.port")));
        assertTrue(Files.readString(file).contains("timeout-seconds"), "merged keys are written back");
    }

    @Test
    void theMergeKeepsTheFileMode(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.yml");
        Files.writeString(file, "bets:\n  max-stake: 500\n", StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(file, java.nio.file.attribute.PosixFilePermissions.fromString("rw-rw-r--"));
        ConfigLoader.Loaded loaded = ConfigLoader.load(file, ConfigLoaderTest::jar);
        assertTrue(loaded.added() > 5, "keys were merged, so the file was rewritten");
        assertEquals(500, loaded.config().bets().maxStake());
        assertEquals("rw-rw-r--", java.nio.file.attribute.PosixFilePermissions.toString(
                Files.getPosixFilePermissions(file)));
    }
}
