package de.tigges.kaboofcasino.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class MessagesTest {

    @Test
    void everyKeyExistsAndRenders() throws Exception {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(
                MessagesTest.class.getClassLoader().getResourceAsStream(de.tigges.kaboofcasino.config.Language.current().messagesFile()), StandardCharsets.UTF_8));
        Messages messages = Messages.of(yaml);
        for (Msg m : Msg.values()) {
            assertTrue(yaml.isString(m.key()), "missing " + m.key());
            String plain = PlainTextComponentSerializer.plainText().serialize(messages.render(m));
            assertFalse(plain.contains("?" + m.key()), m.key());
            assertFalse(plain.contains("️"), "emoji selector renders as a box: " + m.key());
        }
    }

    @Tag("german")
    @Test
    void infoNamesTheAuthorAndTheCreditsNameTheMod() throws Exception {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(
                MessagesTest.class.getClassLoader().getResourceAsStream(de.tigges.kaboofcasino.config.Language.current().messagesFile()), StandardCharsets.UTF_8));
        assertTrue(yaml.getString("command.info").contains("Jxxck"));
        assertFalse(yaml.getString("command.info").contains("MineVegas"));
        assertTrue(yaml.getString("command.credits").contains("MineVegas Casino</yellow> von byMehd"));
        // texts of the mod's de_de.json
        assertTrue(yaml.getString("slot.no-bet").contains("Setze einen Einsatz, bevor du den Hebel ziehst."));
        assertTrue(yaml.getString("gui.slot.title").equals("Spielautomat"));
    }
}
