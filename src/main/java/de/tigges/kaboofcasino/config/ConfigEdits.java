package de.tigges.kaboofcasino.config;

import de.tigges.kaboofcasino.util.Log;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Writes values the croupier's hand sets for the whole casino (the type win chances and biases, "breakable", the
 * cashier's prize list) into the live {@code config.yml}: parsed with Bukkit's comment-aware
 * {@link YamlConfiguration} (as {@link YamlFiles} merges new keys), the existing keys set in place so their comments
 * stay, written with a temp file and an atomic move. Only the paths in {@link #EDITABLE} and
 * {@link #EDITABLE_LISTS} can be written. The caller applies the file like {@code /casino reload}. Thread:
 * MAIN.
 */
public final class ConfigEdits {

    /** The keys the croupier's hand edits. */
    public static final Set<String> EDITABLE = Set.of("roulette.win-chance", "blackjack.bias", "poker.bias",
            "derby.win-chance", "slot.win-chance", "wheel.win-chance", "plinko.win-chance", "machines.breakable");
    /** The lists the croupier's hand edits: the cashier's prizes (a list of maps, written whole). */
    public static final Set<String> EDITABLE_LISTS = Set.of("shop.items");

    private ConfigEdits() {
    }

    /** The text of {@code config.yml} with {@code values} set; everything else (comments included) kept. Pure. */
    public static String apply(String text, Map<String, Object> values) {
        for (String path : values.keySet()) {
            if (!EDITABLE.contains(path) && !EDITABLE_LISTS.contains(path)) {
                throw new IllegalArgumentException("not editable here: " + path);
            }
        }
        YamlConfiguration yaml = YamlFiles.parse(text, ConfigLoader.FILE);
        for (Map.Entry<String, Object> e : values.entrySet()) {
            Object value = e.getValue();
            if (EDITABLE_LISTS.contains(e.getKey()) ? !(value instanceof java.util.List<?>)
                    : !(value instanceof Integer || value instanceof Boolean)) {
                throw new IllegalArgumentException(e.getKey() + " must be " + (EDITABLE_LISTS.contains(e.getKey())
                        ? "a list" : "a number or true/false"));
            }
            yaml.set(e.getKey(), value);
        }
        return yaml.saveToString();
    }

    /**
     * Rewrites the live file with {@code values} (temp file + atomic move). The new file keeps the old one's POSIX
     * permissions (e.g. 664), owner and group, so an admin's file mode survives the edit.
     */
    public static void write(Path file, Map<String, Object> values) throws IOException {
        String before = Files.readString(file, StandardCharsets.UTF_8);
        String after = apply(before, values);
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, after, StandardCharsets.UTF_8);
        copyAttributes(file, tmp);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Owner, group and permissions of {@code from} onto {@code to} (POSIX file systems; elsewhere nothing). Owner and
     * group first (a change of owner may clear permission bits), then the permissions. A refused owner change (the
     * server runs as another user than the file's owner) is logged and leaves the server's user as owner.
     */
    static void copyAttributes(Path from, Path to) throws IOException {
        PosixFileAttributeView source = Files.getFileAttributeView(from, PosixFileAttributeView.class);
        PosixFileAttributeView target = Files.getFileAttributeView(to, PosixFileAttributeView.class);
        if (source == null || target == null) {
            return;
        }
        PosixFileAttributes old = source.readAttributes();
        try {
            PosixFileAttributes now = target.readAttributes();
            if (!now.owner().equals(old.owner())) {
                target.setOwner(old.owner());
            }
            if (!now.group().equals(old.group())) {
                target.setGroup(old.group());
            }
        } catch (IOException | SecurityException e) {
            Log.get().warn("config.yml: could not keep owner {} / group {} ({})", old.owner().getName(),
                    old.group().getName(), e.toString());
        }
        target.setPermissions(old.permissions());
    }
}
