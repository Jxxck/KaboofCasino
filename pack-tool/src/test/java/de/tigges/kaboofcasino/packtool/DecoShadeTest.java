package de.tigges.kaboofcasino.packtool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The block shading of the stair and slab covers: the client's numbers, the face names, the brightened copies. */
class DecoShadeTest {

    /** The display shade of the 26.3 level lights, and the block shade divided by it. */
    @Test
    void displaysAreShadedLikeEntitiesAndTheFactorsUndoIt() {
        assertEquals(1.0, DecoShade.displayShade("up"), 1e-9);
        assertEquals(0.4, DecoShade.displayShade("down"), 1e-9);
        assertEquals(0.7396, DecoShade.displayShade("north"), 1e-4);
        assertEquals(0.7396, DecoShade.displayShade("south"), 1e-4);
        assertEquals(0.4970, DecoShade.displayShade("east"), 1e-4);
        assertEquals(0.4970, DecoShade.displayShade("west"), 1e-4);
        assertEquals(1.25, DecoShade.factor("down"), 1e-9);
        assertEquals(1.0817, DecoShade.factor("ns"), 1e-4);
        assertEquals(1.2072, DecoShade.factor("ew"), 1e-4);
        for (String dir : DecoDisplayBake.DIRECTIONS) {
            String side = DecoShade.side(dir);
            double shown = DecoShade.displayShade(dir) * (side == null ? 1.0 : DecoShade.factor(side));
            assertEquals(DecoShade.blockShade(dir), shown, 1e-9, dir);
        }
    }

    /** Faces that do not look up name their side class; up faces keep the role. */
    @Test
    void facesNameTheirSide() {
        JsonArray shaded = DecoShade.faces(DecoDisplayBake.slab(0, 8));
        JsonObject faces = shaded.get(0).getAsJsonObject().getAsJsonObject("faces");
        assertEquals("#top", faces.getAsJsonObject("up").get("texture").getAsString());
        assertEquals("#bottom_down", faces.getAsJsonObject("down").get("texture").getAsString());
        assertEquals("#half_ns", faces.getAsJsonObject("north").get("texture").getAsString());
        assertEquals("#half_ew", faces.getAsJsonObject("east").get("texture").getAsString());
        assertEquals(Set.of("top", "bottom_down", "half_ns", "half_ew"), DecoShade.variables(shaded));
        JsonArray odd = DecoDisplayBake.slab(0, 8).deepCopy();
        odd.get(0).getAsJsonObject().getAsJsonObject("faces").getAsJsonObject("north").addProperty("texture",
                "casinodeco:block/ruby_velvet");
        assertThrows(IllegalStateException.class, () -> DecoShade.faces(odd));
    }

    /** Every channel is multiplied and capped at 255; alpha stays. */
    @Test
    void brightenKeepsAlphaAndCaps() {
        BufferedImage image = Images.blank(2, 1);
        image.setRGB(0, 0, 0xFF263038);
        image.setRGB(1, 0, 0x80E0D0C0);
        BufferedImage out = DecoShade.brighten(image, 1.25);
        assertEquals(0xFF303C46, out.getRGB(0, 0)); // 38 * 1.25 = 47.5 rounds up
        assertEquals(0x80FFFFF0, out.getRGB(1, 0));
    }

    /** Mod textures get a brightened copy (once), vanilla ones keep their texture. */
    @Test
    void childTexturesPointAtTheCopies() {
        PackTree tree = new PackTree();
        BufferedImage marble = Images.blank(16, 16);
        for (int i = 0; i < 16; i++) {
            marble.setRGB(i, i, 0xFF202020);
        }
        tree.put("assets/casinodeco/textures/block/obsidian_marble.png", Images.write(marble));
        JsonObject textures = new JsonObject();
        textures.addProperty("bottom", "minecraft:block/dark_oak_planks");
        textures.addProperty("top", "minecraft:block/dark_oak_planks");
        textures.addProperty("side", "casinodeco:block/obsidian_marble");
        DecoShade.textures(tree, textures, Set.of("top", "bottom_down", "side_ew", "side_ns"));
        assertEquals("#bottom", textures.get("bottom_down").getAsString());
        assertEquals("kaboofcasino:block/shade/casinodeco/obsidian_marble_ew", textures.get("side_ew").getAsString());
        assertEquals("kaboofcasino:block/shade/casinodeco/obsidian_marble_ns", textures.get("side_ns").getAsString());
        assertTrue(!textures.has("top_down"));
        BufferedImage ew = Images.read(tree.get("assets/kaboofcasino/textures/block/shade/casinodeco/"
                + "obsidian_marble_ew.png"));
        int expected = (int) Math.round(0x20 * DecoShade.factor("ew"));
        assertEquals(0xFF000000 | expected << 16 | expected << 8 | expected, ew.getRGB(3, 3));
        assertEquals(0, ew.getRGB(3, 4) >>> 24, "transparent stays transparent");
        JsonObject again = new JsonObject();
        again.addProperty("side", "casinodeco:block/obsidian_marble");
        DecoShade.textures(tree, again, Set.of("side_ew"));
        assertEquals(textures.get("side_ew"), again.get("side_ew"));
    }
}
