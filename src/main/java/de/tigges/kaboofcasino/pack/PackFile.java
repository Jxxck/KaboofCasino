package de.tigges.kaboofcasino.pack;

import de.tigges.kaboofcasino.config.Language;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * A resource pack embedded in the plugin jar (built by the {@code buildPack} Gradle task): the required casino pack
 * ({@code pack/<language>/KaboofCasino-pack.zip} + {@code .sha1}) and the optional music pack
 * ({@code pack/KaboofCasino-music.zip} + {@code .sha1}, render.md 6). Re-hashed at load; a mismatch with the build's
 * SHA-1 refuses to start (a broken jar would send clients a hash they reject). Served from memory.
 */
public final class PackFile {

    /** The casino pack's file name; the plugin jar has one per language ({@link Language#packResource}). */
    public static final String ZIP = "KaboofCasino-pack.zip";
    /** Fixed pack id, so clients keep one cache slot (render.md 8.4). */
    public static final UUID PACK_ID = UUID.nameUUIDFromBytes("kaboofcasino-pack".getBytes(StandardCharsets.UTF_8));

    public static final String MUSIC_ZIP = "pack/KaboofCasino-music.zip";
    public static final String MUSIC_SHA1 = "pack/KaboofCasino-music.zip.sha1";
    /** Fixed id of the optional music pack (its own cache slot; independent of the casino pack). */
    public static final UUID MUSIC_ID = UUID.nameUUIDFromBytes("kaboofcasino-music".getBytes(StandardCharsets.UTF_8));

    private final byte[] zip;
    private final String sha1;
    private final UUID id;
    private final String name;

    private PackFile(byte[] zip, String sha1, UUID id, String name) {
        this.zip = zip;
        this.sha1 = sha1;
        this.id = id;
        this.name = name;
    }

    /** The required casino pack in the casino's language. */
    public static PackFile load(ClassLoader resources) {
        return load(resources, Language.current());
    }

    /** The casino pack of one language. */
    public static PackFile load(ClassLoader resources, Language language) {
        String zip = language.packResource(ZIP);
        return load(resources, zip, zip + ".sha1", PACK_ID, "casino");
    }

    /** The optional music pack. */
    public static PackFile loadMusic(ClassLoader resources) {
        return load(resources, MUSIC_ZIP, MUSIC_SHA1, MUSIC_ID, "music");
    }

    private static PackFile load(ClassLoader resources, String zipName, String sha1Name, UUID id, String name) {
        byte[] zip = read(resources, zipName);
        String expected = new String(read(resources, sha1Name), StandardCharsets.US_ASCII).strip();
        String actual = sha1(zip);
        if (!actual.equals(expected)) {
            throw new IllegalStateException("embedded " + name + " pack has SHA-1 " + actual + " but the build wrote "
                    + expected);
        }
        return new PackFile(zip, actual, id, name);
    }

    private static byte[] read(ClassLoader resources, String name) {
        try (InputStream in = resources.getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException(name + " is missing in the plugin jar");
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + name, e);
        }
    }

    static String sha1(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The bytes (shared, never modified). */
    byte[] bytes() {
        return zip;
    }

    public int size() {
        return zip.length;
    }

    public String sha1() {
        return sha1;
    }

    /** The pack id clients cache it under ({@link #PACK_ID} or {@link #MUSIC_ID}). */
    public UUID id() {
        return id;
    }

    /** {@code casino} or {@code music} (log lines). */
    public String name() {
        return name;
    }

    /** {@code /pack/<sha1>.zip}: the hash in the path keeps proxies from serving an old pack. */
    public String path() {
        return "/pack/" + sha1 + ".zip";
    }
}
