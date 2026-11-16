package de.tigges.kaboofcasino;

import de.tigges.kaboofcasino.config.Language;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * The shipped texts of the language the tests run in ({@code messages_en.yml} or {@code messages_de.yml}), for tests
 * that lay out a screen with the words the plugin really shows: the same test then checks the German and, in the
 * English run, the English wording.
 */
public final class TestTexts {

    private static final Map<Language, YamlConfiguration> FILES = new EnumMap<>(Language.class);

    private TestTexts() {
    }

    /** The raw text of {@code key} in the current language. */
    public static synchronized String get(String key) {
        YamlConfiguration yaml = FILES.computeIfAbsent(Language.current(), l -> {
            InputStream in = TestTexts.class.getClassLoader().getResourceAsStream(l.messagesFile());
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        });
        String text = yaml.getString(key);
        if (text == null) {
            throw new IllegalArgumentException(Language.current().messagesFile() + " has no " + key);
        }
        return text;
    }
}
