package de.tigges.kaboofcasino.pack;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * The resource pack embedded in the plugin jar ({@code pack/KaboofCasino-pack.zip} + {@code .sha1}, built by the
 * {@code buildPack} Gradle task). Re-hashed at load; a mismatch with the build's SHA-1 refuses to start (a broken jar
 * would send clients a hash they reject). Served from memory.
 */
public final class PackFile {

    public static final String ZIP = "pack/KaboofCasino-pack.zip";
    public static final String SHA1 = "pack/KaboofCasino-pack.zip.sha1";
    /** Fixed pack id, so clients keep one cache slot (render.md 8.4). */
    public static final UUID PACK_ID = UUID.nameUUIDFromBytes("kaboofcasino-pack".getBytes(StandardCharsets.UTF_8));

    private final byte[] zip;
    private final String sha1;

    private PackFile(byte[] zip, String sha1) {
        this.zip = zip;
        this.sha1 = sha1;
    }

    public static PackFile load(ClassLoader resources) {
        byte[] zip = read(resources, ZIP);
        String expected = new String(read(resources, SHA1), StandardCharsets.US_ASCII).strip();
        String actual = sha1(zip);
        if (!actual.equals(expected)) {
            throw new IllegalStateException("embedded pack has SHA-1 " + actual + " but the build wrote " + expected);
        }
        return new PackFile(zip, actual);
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

    /** {@code /pack/<sha1>.zip}: the hash in the path keeps proxies from serving an old pack. */
    public String path() {
        return "/pack/" + sha1 + ".zip";
    }
}
