package de.tigges.kaboofcasino.pack;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every pack key the plugin's code names literally resolves in the built pack: item models
 * ({@code "kaboofcasino:..."}, {@code "roulettecasino:..."} string literals that are complete keys) and sound
 * events ({@code sound(world, "<event>", ...)}, {@code schedule(at, "<event>", ...)}). Keys built at run time are checked by the rigs' own tests.
 */
class PackKeysTest {

    /** Literal keys that are not item models (fonts, prefixes in parsing code). */
    private static final Set<String> NOT_MODELS = Set.of("kaboofcasino:gui");

    private static List<String> sources() throws IOException {
        List<String> out = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                out.add(Files.readString(p, StandardCharsets.UTF_8));
            }
        }
        return out;
    }

    @Test
    void literalItemModelsExist() throws IOException {
        Pattern key = Pattern.compile("\"((?:kaboofcasino|roulettecasino):[a-z0-9_/]+)\"");
        List<String> missing = new ArrayList<>();
        int checked = 0;
        for (String src : sources()) {
            Matcher m = key.matcher(src);
            while (m.find()) {
                String k = m.group(1);
                if (k.endsWith("_") || k.endsWith("/") || NOT_MODELS.contains(k)) {
                    continue;
                }
                checked++;
                if (!PackFiles.hasItemModel(k)) {
                    missing.add(k);
                }
            }
        }
        assertTrue(checked > 10, "found only " + checked + " keys");
        assertTrue(missing.isEmpty(), "item models missing from the pack: " + missing);
    }

    @Test
    void soundEventsExist() throws IOException {
        JsonObject sounds = JsonParser.parseString(new String(PackFiles.all().get(
                "assets/roulettecasino/sounds.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        // sound(world, "<event>", ...) and the blackjack's schedule(at + n, "<event>", ...)
        Pattern call = Pattern.compile("(?:sound\\(world|schedule\\([a-z0-9 +]+), \"([a-z_]+)\"");
        List<String> missing = new ArrayList<>();
        int checked = 0;
        for (String src : sources()) {
            Matcher m = call.matcher(src);
            while (m.find()) {
                checked++;
                if (!sounds.has(m.group(1))) {
                    missing.add(m.group(1));
                }
            }
        }
        assertTrue(checked > 20, "found only " + checked + " sound calls");
        assertTrue(missing.isEmpty(), "sound events missing from the pack: " + missing);
    }
}
