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
 * Shared file handling of config.yml and the messages files (PostMarkt pitfall "Paper config"): create the live file
 * byte-identical from the jar, merge keys the live file lacks (with the jar's comments), rewrite only when something
 * was merged (temp file + atomic move, keeping the file's mode, owner and group). Leaves unknown to the jar are
 * reported. A file that an older version wrote is first brought up to date by a {@link Migration} (renamed and
 * removed keys), so nothing an admin set is lost.
 */
final class YamlFiles {

    record Merged(YamlConfiguration yaml, List<String> warnings, int added) {
    }

    /** Changes to a file an older version wrote, applied before the merge; returns how many things it changed. */
    interface Migration {
        Migration NONE = live -> 0;

        int apply(YamlConfiguration live);
    }

    private YamlFiles() {
    }

    static Merged loadMerged(Path file, Supplier<InputStream> jarDefaults, String name) {
        return loadMerged(file, jarDefaults, name, Migration.NONE);
    }

    /**
     * Moves every leaf below {@code from} to the same place below {@code to} (a leaf or a whole section), unless the
     * file already has something there; the old key is removed. Returns the number of leaves moved or dropped.
     */
    static int rename(YamlConfiguration live, String from, String to) {
        if (!live.contains(from, true)) {
            return 0;
        }
        int n = 0;
        if (live.isConfigurationSection(from)) {
            for (String leaf : live.getConfigurationSection(from).getKeys(true)) {
                String old = from + "." + leaf;
                if (live.isConfigurationSection(old)) {
                    continue;
                }
                if (!live.contains(to + "." + leaf, true)) {
                    live.set(to + "." + leaf, live.get(old));
                }
                n++;
            }
        } else {
            if (!live.contains(to, true)) {
                live.set(to, live.get(from));
            }
            n++;
        }
        live.set(from, null);
        return Math.max(n, 1);
    }

    /** Removes a key or a whole section an older version had; 1 if it was there. */
    static int remove(YamlConfiguration live, String path) {
        if (!live.contains(path, true)) {
            return 0;
        }
        live.set(path, null);
        return 1;
    }

    static Merged loadMerged(Path file, Supplier<InputStream> jarDefaults, String name, Migration migration) {
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
            boolean fresh = !Files.exists(file);
            if (fresh) {
                Files.createDirectories(file.toAbsolutePath().getParent());
                Files.write(file, jarBytes);
            }
            YamlConfiguration live = parse(Files.readString(file, StandardCharsets.UTF_8), name);
            List<String> warnings = new ArrayList<>();
            int added = 0;
            if (!fresh) {
                java.util.Set<String> before = new java.util.HashSet<>(live.getKeys(true));
                added += migration.apply(live);
                // keys the migration created get the jar's comments like merged ones
                for (String path : live.getKeys(true)) {
                    if (!before.contains(path) && defaults.contains(path, true)
                            && !defaults.getComments(path).isEmpty()) {
                        live.setComments(path, defaults.getComments(path));
                    }
                }
            }
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
                ConfigEdits.copyAttributes(file, tmp); // the merged file keeps its mode, owner and group
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
