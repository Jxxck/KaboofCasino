package de.tigges.kaboofcasino.game.poker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.tigges.kaboofcasino.game.blackjack.BlackjackCardRig;
import de.tigges.kaboofcasino.game.blackjack.BlackjackDealerRig;
import de.tigges.kaboofcasino.pack.PackFiles;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Every model the poker rig builds at run time exists in the pack, and the panel's words are in its language file. */
class PokerPackKeysTest {

    @Test
    void rigModelsExist() {
        List<String> keys = PokerRig.modelKeys();
        assertEquals(12 + 8 + 53, keys.size());
        assertEquals(keys.size(), new HashSet<>(keys).size());
        for (String key : keys) {
            assertTrue(PackFiles.hasItemModel(key), key);
        }
    }

    @Test
    void keysByPart() {
        assertEquals("roulettecasino:block/poker_0", PokerRig.bodyModel(0));
        assertEquals("roulettecasino:block/poker_11", PokerRig.bodyModel(11));
        assertEquals("kaboofcasino:blackjack/dealer_torso", BlackjackDealerRig.model(0));
        assertEquals("kaboofcasino:blackjack/card_back", BlackjackCardRig.model(-1));
    }

    private static JsonObject json(String path) {
        byte[] data = PackFiles.all().get(path);
        assertTrue(data != null, path);
        return JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    /** The table displays show the mod's block models themselves (the item definition names the block model). */
    @Test
    void tableItemsNameTheBlockModels() {
        for (int part = 0; part < PokerRig.PARTS; part++) {
            String key = PokerRig.bodyModel(part);
            String path = key.substring(key.indexOf(':') + 1);
            JsonObject model = json("assets/roulettecasino/items/" + path + ".json").getAsJsonObject("model");
            assertEquals("minecraft:model", model.get("type").getAsString());
            assertEquals(key, model.get("model").getAsString());
            JsonObject block = json("assets/roulettecasino/models/" + path + ".json");
            assertTrue(block.getAsJsonArray("elements").size() > 0, key);
            for (JsonElement e : block.getAsJsonArray("elements")) {
                for (String end : new String[] {"from", "to"}) {
                    for (JsonElement c : e.getAsJsonObject().getAsJsonArray(end)) {
                        assertTrue(c.getAsDouble() >= -16 && c.getAsDouble() <= 32, key + " " + e);
                    }
                }
            }
        }
    }

    @Test
    void panelWordsAreInThePackLang() {
        String de = new String(PackFiles.all().get("assets/roulettecasino/lang/de_de.json"), StandardCharsets.UTF_8);
        JsonObject lang = JsonParser.parseString(de).getAsJsonObject();
        assertEquals(PokerPanel.YOU, lang.get(PokerPanel.YOU_KEY).getAsString());
        assertEquals(PokerPanel.DEALER, lang.get(PokerPanel.DEALER_KEY).getAsString());
        for (int hand = 0; hand < PokerHands.CATEGORIES; hand++) {
            assertEquals(PokerPanel.HANDS[hand], lang.get("gui.roulettecasino.poker.hand_" + hand).getAsString());
        }
        for (int code = 1; code <= 5; code++) {
            assertEquals(PokerPanel.RESULTS[code], lang.get("gui.roulettecasino.poker.result_" + code).getAsString());
        }
        String en = new String(PackFiles.all().get("assets/roulettecasino/lang/en_us.json"), StandardCharsets.UTF_8);
        JsonObject english = JsonParser.parseString(en).getAsJsonObject();
        assertEquals("Royal flush", english.get("gui.roulettecasino.poker.hand_9").getAsString());
        assertEquals("DEALER DOESN'T QUALIFY", english.get("gui.roulettecasino.poker.result_2").getAsString());
    }
}
