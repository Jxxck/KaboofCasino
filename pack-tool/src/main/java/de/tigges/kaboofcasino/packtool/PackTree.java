package de.tigges.kaboofcasino.packtool;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * The files of the resource pack, keyed by their path inside the zip ({@code assets/ns/...}). Sorted, so iteration
 * order and therefore the zip are deterministic. Putting a different content under an existing path is a build error
 * (two generators fighting over one file); putting identical bytes again is allowed.
 */
final class PackTree {

    private final NavigableMap<String, byte[]> files = new TreeMap<>();

    void put(String path, byte[] content) {
        if (path.startsWith("/") || path.contains("\\") || path.contains("..")) {
            throw new IllegalArgumentException("bad pack path " + path);
        }
        byte[] old = files.get(path);
        if (old != null && !Arrays.equals(old, content)) {
            throw new IllegalStateException("two different contents for " + path);
        }
        files.put(path, content.clone());
    }

    /** Replaces an existing file (used by post-processing passes such as texture padding). */
    void replace(String path, byte[] content) {
        if (!files.containsKey(path)) {
            throw new IllegalStateException("cannot replace missing " + path);
        }
        files.put(path, content.clone());
    }

    void putText(String path, String text) {
        put(path, text.getBytes(StandardCharsets.UTF_8));
    }

    boolean has(String path) {
        return files.containsKey(path);
    }

    byte[] get(String path) {
        byte[] content = files.get(path);
        if (content == null) {
            throw new IllegalStateException("missing pack file " + path);
        }
        return content.clone();
    }

    String text(String path) {
        return new String(get(path), StandardCharsets.UTF_8);
    }

    /** Read-only, sorted view. */
    Map<String, byte[]> files() {
        return Collections.unmodifiableMap(files);
    }

    int size() {
        return files.size();
    }
}
