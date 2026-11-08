package de.tigges.kaboofcasino.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Writes values the croupier's hand sets for the whole casino (the type win chances and biases, "breakable") into the
 * live {@code config.yml}: parsed with Bukkit's comment-aware {@link YamlConfiguration} (as {@link YamlFiles} merges
 * new keys), the existing keys set in place so their comments stay, written with a temp file and an atomic move. Only
 * the paths in {@link #EDITABLE} can be written. The caller applies the file like {@code /casino reload}. Thread:
 * MAIN.
 */
public final class ConfigEdits {

    /** The keys the croupier's hand edits. */
    public static final Set<String> EDITABLE = Set.of("roulette.win-chance", "blackjack.bias", "poker.bias",
            "derby.win-chance", "slot.win-chance", "wheel.win-chance", "plinko.win-chance", "machines.breakable");

    private ConfigEdits() {
    }

    /** The text of {@code config.yml} with {@code values} set; everything else (comments included) kept. Pure. */
    public static String apply(String text, Map<String, Object> values) {
        for (String path : values.keySet()) {
            if (!EDITABLE.contains(path)) {
                throw new IllegalArgumentException("not editable here: " + path);
            }
        }
        YamlConfiguration yaml = YamlFiles.parse(text, ConfigLoader.FILE);
        for (Map.Entry<String, Object> e : values.entrySet()) {
            Object value = e.getValue();
            if (!(value instanceof Integer || value instanceof Boolean)) {
                throw new IllegalArgumentException(e.getKey() + " must be a number or true/false");
            }
            yaml.set(e.getKey(), value);
        }
        return yaml.saveToString();
    }

    /** Rewrites the live file with {@code values} (temp file + atomic move). */
    public static void write(Path file, Map<String, Object> values) throws IOException {
        String before = Files.readString(file, StandardCharsets.UTF_8);
        String after = apply(before, values);
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, after, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
