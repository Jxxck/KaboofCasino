package de.tigges.kaboofcasino.pack;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** The built pack (embedded in the test classpath under pack/) as path -> bytes. */
public final class PackFiles {

    private static Map<String, byte[]> files;

    private PackFiles() {
    }

    public static synchronized Map<String, byte[]> all() {
        if (files == null) {
            Map<String, byte[]> out = new TreeMap<>();
            try (InputStream in = PackFiles.class.getClassLoader().getResourceAsStream("pack/KaboofCasino-pack.zip");
                 ZipInputStream zip = new ZipInputStream(in)) {
                ZipEntry e;
                while ((e = zip.getNextEntry()) != null) {
                    out.put(e.getName(), zip.readAllBytes());
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            files = out;
        }
        return files;
    }

    /** True if the item model key {@code ns:path} has an item definition in the pack. */
    public static boolean hasItemModel(String key) {
        int colon = key.indexOf(':');
        return all().containsKey("assets/" + key.substring(0, colon) + "/items/" + key.substring(colon + 1) + ".json");
    }
}
