package de.tigges.kaboofcasino.deco.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.tigges.kaboofcasino.pack.PackFiles;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Every model key the display decoration can ask for at run time is in the built pack, every placing item's
 * {@code item_model} exists, and the pack has every name the items' translatable names use (in German too).
 */
class DecoPackKeysTest {

    @Test
    void everyDisplayModelExists() {
        List<String> missing = new ArrayList<>();
        Set<String> keys = new HashSet<>(DecoModels.allKeys());
        for (String key : keys) {
            if (!PackFiles.hasItemModel(key)) {
                missing.add(key);
            }
        }
        assertTrue(keys.size() > 900, "only " + keys.size() + " keys");
        assertTrue(missing.isEmpty(), "missing: " + missing);
    }

    @Test
    void everyItemModelAndNameExists() {
        for (DecoType t : DecoType.values()) {
            assertTrue(PackFiles.hasItemModel(t.id()), t.id());
            if (t.own()) {
                continue; // no language key: the German fallback of the item name
            }
            String ns = t.id().substring(0, t.id().indexOf(':'));
            JsonObject de = JsonParser.parseString(new String(PackFiles.all().get("assets/" + ns
                    + "/lang/de_de.json"), StandardCharsets.UTF_8)).getAsJsonObject();
            assertTrue(de.has(t.translationKey()), t.translationKey());
            assertEquals(t.german(), de.get(t.translationKey()).getAsString(), t.id());
        }
    }
}
