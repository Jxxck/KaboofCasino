package de.tigges.kaboofcasino.game.derby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.tigges.kaboofcasino.pack.PackFiles;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * Every model the derby rig uses exists in the pack (item definition and model), the sign takes 12 tints, the panel's
 * font has the code points and advances {@link DerbyPanel} relies on, and the panel's words are in the pack's
 * language file with the German fallbacks the panel carries.
 */
class DerbyPackKeysTest {

    private static JsonObject json(String path) {
        byte[] data = PackFiles.all().get(path);
        assertNotNull(data, path);
        return JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static String path(String key) {
        return key.substring(key.indexOf(':') + 1);
    }

    private static String ns(String key) {
        return key.substring(0, key.indexOf(':'));
    }

    @Test
    void rigModelsExist() {
        List<String> keys = DerbyRig.modelKeys();
        assertEquals(12 + 4 + 1 + 1, keys.size());
        assertEquals(keys.size(), new HashSet<>(keys).size());
        for (String key : keys) {
            assertTrue(PackFiles.hasItemModel(key), key);
            assertTrue(PackFiles.all().containsKey("assets/" + ns(key) + "/models/" + path(key) + ".json"), key);
        }
    }

    @Test
    void keysByPart() {
        assertEquals("roulettecasino:block/duck_derby_0", DerbyRig.bodyModel(0));
        assertEquals("roulettecasino:block/duck_derby_11", DerbyRig.bodyModel(11));
        assertEquals("kaboofcasino:derby/duck_0", DerbyRig.duckModel(0));
        assertEquals("kaboofcasino:derby/duck_3", DerbyRig.duckModel(3));
        assertEquals("kaboofcasino:derby/gates", DerbyRig.GATES);
        assertEquals("kaboofcasino:derby/sign", DerbyRig.SIGN);
    }

    /** The tub displays show the mod's block models themselves (the item definition names the block model). */
    @Test
    void tubItemsNameTheBlockModels() {
        for (int part = 0; part < DerbyRig.PARTS; part++) {
            String key = DerbyRig.bodyModel(part);
            JsonObject model = json("assets/roulettecasino/items/" + path(key) + ".json").getAsJsonObject("model");
            assertEquals("minecraft:model", model.get("type").getAsString());
            assertEquals(key, model.get("model").getAsString());
            assertTrue(json("assets/roulettecasino/models/" + path(key) + ".json").getAsJsonArray("elements").size() > 0);
        }
        // the ducks' sheet is in the blocks atlas at its own size (192 x 176, no padding: UVs unchanged)
        BufferedImage sheet = png("assets/roulettecasino/textures/block/casino_entity/duck_derby.png");
        assertEquals(192, sheet.getWidth());
        assertEquals(176, sheet.getHeight());
    }

    @Test
    void signTakesTwelveTints() {
        JsonObject model = json("assets/kaboofcasino/items/derby/sign.json").getAsJsonObject("model");
        assertEquals("kaboofcasino:derby/sign", model.get("model").getAsString());
        JsonArray tints = model.getAsJsonArray("tints");
        assertEquals(DuckDerbySignLights.BULBS, tints.size());
        for (int i = 0; i < tints.size(); i++) {
            JsonObject t = tints.get(i).getAsJsonObject();
            assertEquals("minecraft:custom_model_data", t.get("type").getAsString());
            assertEquals(i, t.get("index").getAsInt());
        }
    }

    private static BufferedImage png(String path) {
        byte[] data = PackFiles.all().get(path);
        assertNotNull(data, path);
        try {
            return ImageIO.read(new ByteArrayInputStream(data));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The client's bitmap advance: rightmost column with alpha, plus one, plus one (scale 1). */
    private static int advance(BufferedImage img) {
        for (int w = img.getWidth() - 1; w >= 0; w--) {
            for (int y = 0; y < img.getHeight(); y++) {
                if ((img.getRGB(w, y) >>> 24) != 0) {
                    return w + 2;
                }
            }
        }
        return 1;
    }

    @Test
    void panelFontHasTheGlyphsThePanelUses() {
        JsonObject font = json("assets/kaboofcasino/font/derby_panel.json");
        Map<Integer, Integer> advances = new HashMap<>();
        Map<Integer, int[]> boxes = new HashMap<>();
        for (JsonElement e : font.getAsJsonArray("providers")) {
            JsonObject p = e.getAsJsonObject();
            if (p.get("type").getAsString().equals("space")) {
                for (Map.Entry<String, JsonElement> a : p.getAsJsonObject("advances").entrySet()) {
                    advances.put(a.getKey().codePointAt(0), a.getValue().getAsInt());
                }
                continue;
            }
            assertEquals("bitmap", p.get("type").getAsString());
            String file = p.get("file").getAsString();
            BufferedImage img = png("assets/" + file.replace(":", "/textures/"));
            int height = p.get("height").getAsInt();
            int ascent = p.get("ascent").getAsInt();
            assertEquals(img.getHeight(), height, file);
            assertTrue(ascent <= height, file);
            int cp = p.getAsJsonArray("chars").get(0).getAsString().codePointAt(0);
            advances.put(cp, advance(img));
            boxes.put(cp, new int[] {img.getWidth(), height, ascent});
        }
        for (int i = 0; i < 8; i++) {
            assertEquals(-(1 << i), advances.get(DerbyPanel.NEG_SPACE + i));
            assertEquals(1 << i, advances.get(DerbyPanel.POS_SPACE + i));
        }
        assertEquals(DerbyPanel.EDGE_ADVANCE, advances.get(DerbyPanel.EDGE));
        assertEquals(DerbyPanel.FILL_ADVANCE, advances.get(DerbyPanel.FILL));
        assertEquals(DerbyPanel.STRIP_ADVANCE, advances.get(DerbyPanel.STRIP));
        assertEquals(DerbyPanel.TRACK_ADVANCE, advances.get(DerbyPanel.TRACK));
        for (int k = 0; k < 5; k++) {
            assertEquals(DerbyPanel.BAR_STEP_PX * (1 << k) + 1, advances.get(DerbyPanel.BAR + k));
        }
        // geometry against a 10 px line whose caps are rows 0..7: the edge's bronze row right under the fill, the
        // strip under an 8 px head, the countdown fill inside the frame's rows 1..3
        assertEquals(List.of(56, 12, 9), List.of(boxes.get(DerbyPanel.EDGE)[0], boxes.get(DerbyPanel.EDGE)[1],
                boxes.get(DerbyPanel.EDGE)[2]));
        assertEquals(List.of(54, 11, 9), List.of(boxes.get(DerbyPanel.FILL)[0], boxes.get(DerbyPanel.FILL)[1],
                boxes.get(DerbyPanel.FILL)[2]));
        int trackTop = 7 - boxes.get(DerbyPanel.TRACK)[2];
        int barTop = 7 - boxes.get(DerbyPanel.BAR)[2];
        assertEquals(trackTop + 1, barTop);
        BufferedImage bar = png("assets/kaboofcasino/textures/font/derby_panel/bar_5.png");
        int rows = 0;
        for (int y = 0; y < bar.getHeight(); y++) {
            rows += (bar.getRGB(0, y) >>> 24) != 0 ? 1 : 0;
        }
        assertEquals(boxes.get(DerbyPanel.TRACK)[1] - 2, rows, "the fill covers the frame's inner rows");
        assertEquals(DerbyPanel.TRACK_ADVANCE - 3, DerbyPanel.BAR_STEPS * DerbyPanel.BAR_STEP_PX);
        assertEquals("kaboofcasino:derby_panel", DerbyPanel.FONT.asString());
    }

    @Test
    void panelWordsAreInThePackLang() {
        JsonObject de = json("assets/roulettecasino/lang/de_de.json");
        for (int lane = 0; lane < 4; lane++) {
            assertEquals(DerbyPanel.NAMES[lane], de.get(DerbyPanel.NAME_KEYS[lane]).getAsString());
        }
        assertEquals(DerbyPanel.PAYOUT, de.get(DerbyPanel.PAYOUT_KEY).getAsString());
        assertEquals(DerbyPanel.SECOND, de.get(DerbyPanel.SECOND_KEY).getAsString());
        assertEquals(DerbyPanel.RESULT_LINE, de.get(DerbyPanel.RESULT_LINE_KEY).getAsString());
        assertEquals(DerbyPanel.COUNTDOWN, de.get(DerbyPanel.COUNTDOWN_KEY).getAsString());
        assertEquals(DerbyPanel.RACING, de.get(DerbyPanel.RACING_KEY).getAsString());
        assertEquals(DerbyPanel.MORE, de.get(DerbyPanel.MORE_KEY).getAsString());
        for (String k : List.of("person", "people", "people_few")) {
            assertEquals(DerbyPanel.PEOPLE, de.get("gui.roulettecasino.derby." + k).getAsString());
        }
        JsonObject en = json("assets/roulettecasino/lang/en_us.json");
        assertEquals("YELLOW", en.get(DerbyPanel.NAME_KEYS[0]).getAsString());
        assertEquals("WINNER", en.get(DerbyPanel.PAYOUT_KEY).getAsString());
        assertEquals("%s player", en.get("gui.roulettecasino.derby.person").getAsString());
        // the header label is centred with the German widths of the default font
        assertEquals(24, DerbyPanel.width("GELB"));
        assertEquals(18, DerbyPanel.width("ROT"));
        assertEquals(28, DerbyPanel.width("WEISS"));
        assertEquals(42, DerbyPanel.width("SCHWARZ"));
    }
}
