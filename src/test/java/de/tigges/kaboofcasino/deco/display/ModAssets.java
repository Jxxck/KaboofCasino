package de.tigges.kaboofcasino.deco.display;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The pinned MineVegas jar (read as a zip, as the pack tool does): {@code casino.modJar} of gradle.properties relative
 * to the project, else the same file name next to the research checkout. Tests compare the plugin's pure tables with
 * the mod's own blockstates and models.
 */
final class ModAssets {

    private static final String FALLBACK = "/root/Jack_DONT_TOUCH/plugins-src/Casino-work/mod/";
    private static Map<String, byte[]> files;

    private ModAssets() {
    }

    static synchronized Map<String, byte[]> all() {
        if (files != null) {
            return files;
        }
        Path jar = locate();
        Map<String, byte[]> out = new HashMap<>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            for (ZipEntry e : java.util.Collections.list(zip.entries())) {
                if (e.isDirectory() || !e.getName().startsWith("assets/")) {
                    continue;
                }
                try (InputStream in = zip.getInputStream(e)) {
                    out.put(e.getName(), in.readAllBytes());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        files = out;
        return out;
    }

    private static Path locate() {
        try (InputStream in = Files.newInputStream(Path.of("gradle.properties"))) {
            Properties p = new Properties();
            p.load(in);
            Path configured = Path.of(p.getProperty("casino.modJar"));
            if (Files.isRegularFile(configured)) {
                return configured;
            }
            Path fallback = Path.of(FALLBACK).resolve(configured.getFileName());
            if (Files.isRegularFile(fallback)) {
                return fallback;
            }
            throw new IllegalStateException("mod jar not found: " + configured.toAbsolutePath() + " or " + fallback);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static JsonObject json(String path) {
        byte[] data = all().get(path);
        if (data == null) {
            throw new IllegalStateException("not in the mod jar: " + path);
        }
        return JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    static JsonObject blockstate(String id) {
        int colon = id.indexOf(':');
        return json("assets/" + id.substring(0, colon) + "/blockstates/" + id.substring(colon + 1) + ".json");
    }

    /** {@code facing=east,half=bottom} -> map. */
    static Map<String, String> props(String key) {
        Map<String, String> out = new HashMap<>();
        if (key.isEmpty()) {
            return out;
        }
        for (String kv : key.split(",")) {
            String[] p = kv.split("=");
            out.put(p[0], p[1]);
        }
        return out;
    }

    /** The first (or only) entry of a variant. */
    static JsonObject first(JsonElement variant) {
        return variant.isJsonArray() ? variant.getAsJsonArray().get(0).getAsJsonObject() : variant.getAsJsonObject();
    }

    static int y(JsonObject entry) {
        return entry.has("y") ? entry.get("y").getAsInt() : 0;
    }

    /** {@code casinodeco:block/sofa_a0} -> {@code kaboofcasino:deco/sofa_a0}. */
    static String displayKey(String model) {
        return DecoModels.PREFIX + model.substring(model.indexOf("block/") + "block/".length());
    }
}
