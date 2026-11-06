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
        assertEquals(18, loaded.config().bets().items().size());
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
                  max-stake: 65
                  items: [diamond, "not a key!", minecraft:emerald]
                typo: 1
                """, StandardCharsets.UTF_8);
        ConfigLoader.Loaded loaded = ConfigLoader.load(file, ConfigLoaderTest::jar);
        CasinoConfig c = loaded.config();
        assertTrue(c.pack().enforce());
        assertEquals(25580, c.pack().port());
        assertEquals(64, c.bets().maxStake());
        assertEquals(java.util.List.of("minecraft:diamond", "minecraft:emerald"), c.bets().items());
        assertTrue(loaded.added() > 5);
        assertTrue(loaded.warnings().stream().anyMatch(w -> w.contains("typo")));
        assertTrue(loaded.warnings().stream().anyMatch(w -> w.contains("pack.port")));
        assertTrue(Files.readString(file).contains("timeout-seconds"), "merged keys are written back");
    }
}
