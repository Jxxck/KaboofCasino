package de.tigges.kaboofcasino.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The croupier's hand writes the type values into config.yml: only the edited lines change, every comment (block and
 * inline, German umlauts included) and every other value stays, and the result loads as the new config.
 */
class ConfigEditsTest {

    private static String shipped() throws IOException {
        try (InputStream in = ConfigEditsTest.class.getClassLoader().getResourceAsStream("config.yml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<String> comments(String text) {
        List<String> out = new ArrayList<>();
        for (String line : text.split("\n", -1)) {
            if (line.strip().startsWith("#")) {
                out.add(line.strip());
            }
        }
        return out;
    }

    /** Lines that differ (by position; the edits never add or remove a line). */
    private static List<String> changed(String before, String after) {
        String[] a = before.split("\n", -1);
        String[] b = after.split("\n", -1);
        assertEquals(a.length, b.length, "line count");
        List<String> out = new ArrayList<>();
        for (int i = 0; i < a.length; i++) {
            if (!a[i].equals(b[i])) {
                out.add(a[i] + " -> " + b[i]);
            }
        }
        return out;
    }

    /**
     * Bukkit writes block sequences without the extra indent ("  - x" instead of "    - x"); the live file has that form
     * since its first key merge ({@link YamlFiles}). Nothing else changes, and from then on a no-op edit gives the
     * same bytes.
     */
    @Test
    void aNoOpEditOnlyNormalisesListIndentation() throws IOException {
        String text = shipped();
        String once = ConfigEdits.apply(text, Map.of());
        for (String line : changed(text, once)) {
            String[] parts = line.split(" -> ");
            assertEquals(parts[0].strip(), parts[1].strip(), line);
            assertTrue(parts[0].strip().startsWith("- minecraft:"), line);
        }
        assertEquals(once, ConfigEdits.apply(once, Map.of()), "the normalised file round-trips byte for byte");
        String live = System.getProperty("casino.liveConfigCopy");
        if (live != null) {
            String liveText = Files.readString(Path.of(live), StandardCharsets.UTF_8);
            assertEquals(liveText, ConfigEdits.apply(liveText, Map.of()), "the live file round-trips byte for byte");
        }
    }

    @Test
    void editsChangeOnlyTheirLinesAndKeepEveryComment() throws IOException {
        String text = ConfigEdits.apply(shipped(), Map.of());
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("slot.win-chance", 30);
        values.put("roulette.win-chance", 0);
        values.put("blackjack.bias", -20);
        values.put("poker.bias", 100);
        values.put("derby.win-chance", 25);
        values.put("wheel.win-chance", -1);
        values.put("plinko.win-chance", 100);
        values.put("machines.breakable", false);
        String after = ConfigEdits.apply(text, values);
        assertEquals(comments(text), comments(after));
        assertEquals(List.of(
                "  breakable: true ->   breakable: false",
                "  win-chance: -1 ->   win-chance: 30",
                "  win-chance: -1 ->   win-chance: 0",
                "  bias: 0 ->   bias: -20",
                "  bias: 0 ->   bias: 100",
                "  win-chance: -1 ->   win-chance: 100",
                "  win-chance: -1 ->   win-chance: 25"), changed(text, after));
        CasinoConfig c = ConfigLoader.read(YamlFiles.parse(after, "test"), new ArrayList<>());
        assertEquals(30, c.slot().winChance());
        assertEquals(0, c.roulette().winChance());
        assertEquals(-20, c.blackjack().bias());
        assertEquals(100, c.poker().bias());
        assertEquals(25, c.derby().winChance());
        assertEquals(-1, c.wheel().winChance());
        assertEquals(100, c.plinko().winChance());
        assertFalse(c.machines().breakable());
        // everything else as before
        assertEquals(CasinoConfig.DEFAULTS.pack(), c.pack());
        assertEquals(CasinoConfig.DEFAULTS.bets(), c.bets());
        assertEquals(CasinoConfig.DEFAULTS.slot().idleLightTicks(), c.slot().idleLightTicks());
        assertTrue(c.roulette().needsSupport());
    }

    @Test
    void aHandEditedFileKeepsItsOwnCommentsAndValues(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("config.yml");
        String text = """
                # Jacks Notiz
                slot:
                  # eigene Zeile über dem Wert
                  win-chance: 12 # inline
                  idle-light-ticks: 8
                blackjack:
                  bias: 5
                """;
        Files.writeString(file, text, StandardCharsets.UTF_8);
        ConfigEdits.write(file, Map.of("slot.win-chance", 40, "blackjack.bias", -5));
        String after = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(after.contains("# Jacks Notiz"), after);
        assertTrue(after.contains("# eigene Zeile über dem Wert"), after);
        assertTrue(after.contains("win-chance: 40 # inline"), after);
        assertTrue(after.contains("idle-light-ticks: 8"), after);
        assertTrue(after.contains("bias: -5"), after);
        assertFalse(Files.exists(dir.resolve("config.yml.tmp")));
    }

    @Test
    void onlyTheHandsKeysAndPlainValues() throws IOException {
        String text = shipped();
        assertThrows(IllegalArgumentException.class, () -> ConfigEdits.apply(text, Map.of("pack.enforce", true)));
        assertThrows(IllegalArgumentException.class, () -> ConfigEdits.apply(text, Map.of("slot.win-chance", "30")));
    }
}
