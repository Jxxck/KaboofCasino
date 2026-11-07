package de.tigges.kaboofcasino.packtool;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The MineVegas Casino jar, read as a plain zip archive. Only {@code assets/**} and the licence files are loaded; the
 * mod's classes are never loaded or executed. The jar is pinned by SHA-1 so the pack stays reproducible.
 */
final class ModJar {

    private final Map<String, byte[]> entries;

    private ModJar(Map<String, byte[]> entries) {
        this.entries = entries;
    }

    /**
     * @param expectedSha1 lower-case hex SHA-1 the jar must have
     * @throws IllegalStateException on a SHA-1 mismatch
     */
    static ModJar open(Path jar, String expectedSha1) {
        String actual = sha1Hex(readAll(jar));
        if (!actual.equalsIgnoreCase(expectedSha1)) {
            throw new IllegalStateException("mod jar " + jar + " has SHA-1 " + actual + ", expected " + expectedSha1);
        }
        Map<String, byte[]> entries = new TreeMap<>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> all = zip.entries();
            while (all.hasMoreElements()) {
                ZipEntry entry = all.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !(name.startsWith("assets/") || name.startsWith("LICENSE")
                        || name.equals("MUSIC-CREDITS.md") || modData(name))) {
                    continue;
                }
                try (InputStream in = zip.getInputStream(entry)) {
                    entries.put(name, in.readAllBytes());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + jar, e);
        }
        return new ModJar(entries);
    }

    boolean has(String path) {
        return entries.containsKey(path);
    }

    byte[] bytes(String path) {
        byte[] data = entries.get(path);
        if (data == null) {
            throw new IllegalStateException("the mod jar has no " + path);
        }
        return data.clone();
    }

    Optional<byte[]> optional(String path) {
        byte[] data = entries.get(path);
        return data == null ? Optional.empty() : Optional.of(data.clone());
    }

    String text(String path) {
        return new String(bytes(path), StandardCharsets.UTF_8);
    }

    /** Entry names below {@code prefix}, sorted. */
    Iterable<String> list(String prefix) {
        return entries.keySet().stream().filter(n -> n.startsWith(prefix)).sorted().toList();
    }

    static byte[] readAll(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file, e);
        }
    }

    static String sha1Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 is missing in this JVM", e);
        }
    }

    /** The mod's recipes and recipe advancements of its three namespaces (the rest of data/ is not read). */
    static boolean modData(String name) {
        for (String ns : new String[] {"roulettecasino", "casinodeco", "casinosound"}) {
            if (name.startsWith("data/" + ns + "/recipe/") || name.startsWith("data/" + ns + "/advancement/recipes/")) {
                return true;
            }
        }
        return false;
    }
}
