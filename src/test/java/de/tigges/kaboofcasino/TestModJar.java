package de.tigges.kaboofcasino;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Where the pinned MineVegas Casino jar is for tests that read the mod's own files from it (as a zip, like the pack
 * tool): the system property {@code kaboofcasino.modJar} the Gradle test task sets, else {@code casino.modJar} of
 * gradle.properties relative to the working directory (a test started from an IDE in the project directory). The
 * Gradle task {@code fetchModJar} puts the jar there.
 */
public final class TestModJar {

    private TestModJar() {
    }

    /**
     * The mod's texts of one language ({@code en_us}, {@code de_de}), its three namespaces merged: what the plugin's
     * own tables of names and fallbacks are compared with.
     */
    public static java.util.Map<String, String> lang(String code) {
        java.util.Map<String, String> out = new java.util.TreeMap<>();
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(path().toFile())) {
            for (String ns : new String[] {"roulettecasino", "casinodeco", "casinosound"}) {
                try (InputStream in = zip.getInputStream(zip.getEntry("assets/" + ns + "/lang/" + code + ".json"))) {
                    com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(new String(
                            in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                    for (java.util.Map.Entry<String, com.google.gson.JsonElement> e : json.entrySet()) {
                        out.put(e.getKey(), e.getValue().getAsString());
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    public static Path path() {
        String given = System.getProperty("kaboofcasino.modJar", "");
        if (!given.isEmpty()) {
            Path jar = Path.of(given);
            if (Files.isRegularFile(jar)) {
                return jar;
            }
            throw new IllegalStateException("mod jar not found: " + jar.toAbsolutePath());
        }
        try (InputStream in = Files.newInputStream(Path.of("gradle.properties"))) {
            Properties p = new Properties();
            p.load(in);
            Path configured = Path.of(p.getProperty("casino.modJar"));
            if (Files.isRegularFile(configured)) {
                return configured;
            }
            throw new IllegalStateException("mod jar not found: " + configured.toAbsolutePath()
                    + " (run the Gradle task fetchModJar)");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
