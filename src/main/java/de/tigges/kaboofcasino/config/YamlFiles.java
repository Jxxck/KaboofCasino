package de.tigges.kaboofcasino.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Shared file handling of config.yml and messages_de.yml (PostMarkt pitfall "Paper config"): create the live file
 * byte-identical from the jar, merge keys the live file lacks (with the jar's comments), rewrite only when something
 * was merged (temp file + atomic move). Leaves unknown to the jar are reported.
 */
final class YamlFiles {

    record Merged(YamlConfiguration yaml, List<String> warnings, int added) {
    }

    private YamlFiles() {
    }

    static Merged loadMerged(Path file, Supplier<InputStream> jarDefaults, String name) {
        byte[] jarBytes;
        try (InputStream in = jarDefaults.get()) {
            if (in == null) {
                throw new UncheckedIOException(new IOException(name + " is missing in the plugin jar"));
            }
            jarBytes = in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + name + " from the plugin jar", e);
        }
        YamlConfiguration defaults = parse(new String(jarBytes, StandardCharsets.UTF_8), name + " (jar)");
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.toAbsolutePath().getParent());
                Files.write(file, jarBytes);
            }
            YamlConfiguration live = parse(Files.readString(file, StandardCharsets.UTF_8), name);
            List<String> warnings = new ArrayList<>();
            int added = 0;
            for (String path : defaults.getKeys(true)) {
                if (defaults.isConfigurationSection(path)) {
                    continue;
                }
                if (!live.contains(path, true)) {
                    live.set(path, defaults.get(path));
                    live.setComments(path, defaults.getComments(path));
                    added++;
                }
            }
            for (String path : live.getKeys(true)) {
                if (!live.isConfigurationSection(path) && !defaults.contains(path, true)) {
                    warnings.add(name + ": unknown setting " + path + " is ignored");
                }
            }
            if (added > 0) {
                Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
                Files.writeString(tmp, live.saveToString(), StandardCharsets.UTF_8);
                try {
                    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            return new Merged(live, warnings, added);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read or write " + file, e);
        }
    }

    static YamlConfiguration parse(String text, String name) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().parseComments(true);
        try {
            yaml.loadFromString(text);
        } catch (InvalidConfigurationException e) {
            throw new UncheckedIOException(new IOException(name + " is not valid YAML: " + e.getMessage(), e));
        }
        return yaml;
    }
}
