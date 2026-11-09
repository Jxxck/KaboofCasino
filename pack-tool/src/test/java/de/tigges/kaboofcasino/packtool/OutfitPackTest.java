package de.tigges.kaboofcasino.packtool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.tigges.kaboofcasino.packtool.OutfitArt.Piece;
import de.tigges.kaboofcasino.packtool.OutfitArt.Style;
import java.awt.image.BufferedImage;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/** The outfits' pack part: every asset the plugin's items point at exists, in the client's formats. */
class OutfitPackTest {

    @Test
    void everyStyleHasItsAssets() {
        PackTree tree = new PackTree();
        OutfitPack.add(tree);
        for (Style s : OutfitArt.STYLES) {
            String asset = "outfit_" + s.id();
            JsonObject equipment = Json.parse(tree.get("assets/kaboofcasino/equipment/" + asset + ".json"));
            assertEquals("kaboofcasino:" + asset, equipment.getAsJsonObject("layers").getAsJsonArray("humanoid").get(0)
                    .getAsJsonObject().get("texture").getAsString());
            for (String layer : new String[] {"humanoid", "humanoid_leggings"}) {
                BufferedImage image = Images.read(tree.get("assets/kaboofcasino/textures/entity/equipment/" + layer
                        + "/" + asset + ".png"));
                assertEquals(64, image.getWidth());
                assertEquals(32, image.getHeight());
            }
            for (Piece p : Piece.values()) {
                String name = s.id() + "_" + p.name().toLowerCase(Locale.ROOT);
                assertTrue(tree.has("assets/kaboofcasino/items/outfit/" + name + ".json"), name);
                assertTrue(tree.has("assets/kaboofcasino/models/item/outfit/" + name + ".json"), name);
                assertEquals(32, Images.read(tree.get("assets/kaboofcasino/textures/item/outfit/" + name + ".png"))
                        .getWidth());
            }
        }
    }

    @Test
    void wornHatsStayInTheModelLimits() {
        for (Style s : OutfitArt.STYLES) {
            JsonObject model = OutfitPack.hatModel(s, "x");
            for (JsonElement e : model.getAsJsonArray("elements")) {
                for (String end : new String[] {"from", "to"}) {
                    for (JsonElement v : e.getAsJsonObject().getAsJsonArray(end)) {
                        assertTrue(v.getAsDouble() >= -16 && v.getAsDouble() <= 32, s.id() + " " + v);
                    }
                }
            }
        }
    }

    @Test
    void theHatShowsItsModelOnlyOnTheHead() {
        JsonObject select = OutfitPack.hatDefinition("x").getAsJsonObject("model");
        assertEquals("minecraft:display_context", select.get("property").getAsString());
        assertEquals("head", select.getAsJsonArray("cases").get(0).getAsJsonObject().get("when").getAsString());
    }

    @Test
    void theTrousersLeaveTheChestToTheJacket() {
        for (Style s : OutfitArt.STYLES) {
            BufferedImage legs = OutfitArt.leggings(s);
            // the body's upper rows stay empty, or the trousers would show through an open vest or a bare torso
            for (int x = 20; x < 28; x++) {
                assertEquals(0, legs.getRGB(x, 21) >>> 24, s.id());
            }
        }
    }
}
