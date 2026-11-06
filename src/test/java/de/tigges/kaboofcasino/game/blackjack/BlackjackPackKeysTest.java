package de.tigges.kaboofcasino.game.blackjack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.pack.PackFiles;
import org.junit.jupiter.api.Test;

/** Every model the blackjack rig builds at run time exists in the pack. */
class BlackjackPackKeysTest {

    @Test
    void rigModelsExist() {
        assertEquals(8 + 8 + 53, BlackjackRig.modelKeys().size());
        for (String key : BlackjackRig.modelKeys()) {
            assertTrue(PackFiles.hasItemModel(key), key);
        }
    }

    @Test
    void cardModelsByValue() {
        assertEquals("kaboofcasino:blackjack/card_back", BlackjackCardRig.model(-1));
        assertEquals("kaboofcasino:blackjack/card_0", BlackjackCardRig.model(0));
        assertEquals("kaboofcasino:blackjack/card_51", BlackjackCardRig.model(51));
    }

    @Test
    void panelWordsAreInThePackLang() {
        String de = new String(PackFiles.all().get("assets/roulettecasino/lang/de_de.json"),
                java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(de.contains("\"gui.roulettecasino.blackjack.you\":\"" + BlackjackPanel.YOU + "\""), de);
        assertTrue(de.contains("\"gui.roulettecasino.blackjack.dealer\":\"" + BlackjackPanel.DEALER + "\""));
        for (int code = 1; code <= 8; code++) {
            assertTrue(de.contains("\"gui.roulettecasino.blackjack.result_" + code + "\":\""
                    + BlackjackPanel.RESULTS[code] + "\""), "result_" + code);
        }
    }
}
