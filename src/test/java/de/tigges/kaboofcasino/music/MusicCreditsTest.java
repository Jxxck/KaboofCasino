package de.tigges.kaboofcasino.music;

import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.config.CasinoConfig;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** CC BY 4.0 attribution in /casino credits, the music pack prompt and the shipped switch. */
class MusicCreditsTest {

    private static YamlConfiguration yaml(String file) {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                MusicCreditsTest.class.getClassLoader().getResourceAsStream(file), StandardCharsets.UTF_8));
    }

    @Test
    void creditsNameEveryTrackTheAuthorAndTheLicence() {
        String credits = yaml("messages_de.yml").getString("command.credits");
        for (String title : new String[] {"Airport Lounge", "Backbay Lounge", "Jazz Brunch", "George Street Shuffle",
            "Cool Vibes", "Bossa Antigua"}) {
            assertTrue(credits.contains(title), title);
        }
        assertTrue(credits.contains("Kevin MacLeod"));
        assertTrue(credits.contains("incompetech.com"));
        assertTrue(credits.contains("CC BY 4.0"));
        assertTrue(credits.contains("https://creativecommons.org/licenses/by/4.0/"));
        assertTrue(credits.contains("Ogg Vorbis") && credits.contains("-20 LUFS"), "the changes are stated");
        assertTrue(!credits.contains("kommt später"));
        String prompt = yaml("messages_de.yml").getString("pack.music-prompt");
        assertTrue(prompt.contains("Kevin MacLeod") && prompt.contains("CC BY 4.0"));
    }

    @Test
    void musicShipsSwitchedOn() {
        assertTrue(yaml("config.yml").getBoolean("music.enabled"));
        assertTrue(CasinoConfig.DEFAULTS.music().enabled());
    }
}
