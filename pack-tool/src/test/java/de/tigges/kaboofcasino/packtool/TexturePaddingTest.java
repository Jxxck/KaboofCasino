package de.tigges.kaboofcasino.packtool;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class TexturePaddingTest {

    private static byte[] png(int w, int h) {
        BufferedImage image = Images.blank(w, h);
        Images.fill(image, 0, 0, w, h, 0xff336699);
        return Images.write(image);
    }

    private static double[] uv(PackTree tree, String path, int element, String face) {
        JsonObject model = Json.parse(tree.get(path));
        JsonArray uv = model.getAsJsonArray("elements").get(element).getAsJsonObject().getAsJsonObject("faces")
                .getAsJsonObject(face).getAsJsonArray("uv");
        double[] out = new double[4];
        for (int i = 0; i < 4; i++) {
            out[i] = uv.get(i).getAsDouble();
        }
        return out;
    }

    @Test
    void padsOddTexturesAndRescalesExplicitAndDefaultUvs() {
        PackTree tree = new PackTree();
        tree.put("assets/x/textures/block/odd.png", png(24, 40));
        tree.put("assets/x/textures/block/even.png", png(32, 16));
        tree.putText("assets/x/models/block/m.json", """
                {"textures":{"a":"x:block/odd","b":"#a","c":"x:block/even"},"elements":[
                 {"from":[0,0,0],"to":[16,8,16],"faces":{
                   "north":{"uv":[0,0,16,16],"texture":"#b"},
                   "up":{"texture":"#a"},
                   "south":{"uv":[1,2,3,4],"texture":"#c"}}}]}""");
        List<TexturePadding.Padded> padded = TexturePadding.apply(tree);
        assertEquals(1, padded.size());
        assertEquals("x:block/odd", padded.getFirst().id());
        BufferedImage image = Images.read(tree.get("assets/x/textures/block/odd.png"));
        assertEquals(32, image.getWidth());
        assertEquals(48, image.getHeight());
        assertEquals(0xff336699, image.getRGB(23, 39));
        assertEquals(0, image.getRGB(24, 39) >>> 24);
        String m = "assets/x/models/block/m.json";
        assertArrayEquals(new double[] {0, 0, 12, 40.0 / 3}, uv(tree, m, 0, "north"), 1e-4);
        // "up" had no uv: default from the bounds [0,0,16,16], then scaled
        assertArrayEquals(new double[] {0, 0, 12, 40.0 / 3}, uv(tree, m, 0, "up"), 1e-4);
        assertArrayEquals(new double[] {1, 2, 3, 4}, uv(tree, m, 0, "south"), 1e-9);
    }

    @Test
    void refusesAnimatedOddTextures() {
        PackTree tree = new PackTree();
        tree.put("assets/x/textures/block/anim.png", png(8, 24));
        tree.put("assets/x/textures/block/anim.png.mcmeta", "{\"animation\":{}}".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalStateException.class, () -> TexturePadding.apply(tree));
    }

    @Test
    void refusesOddTexturesUsedThroughForeignElements() {
        PackTree tree = new PackTree();
        tree.put("assets/x/textures/item/odd.png", png(20, 20));
        tree.putText("assets/x/models/item/i.json",
                "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"x:item/odd\"}}");
        assertThrows(IllegalStateException.class, () -> TexturePadding.apply(tree));
    }

    @Test
    void onlyAtlasTexturesCount() {
        assertEquals("x:block/a/b", TexturePadding.atlasTextureId("assets/x/textures/block/a/b.png"));
        assertEquals("x:item/c", TexturePadding.atlasTextureId("assets/x/textures/item/c.png"));
        assertNull(TexturePadding.atlasTextureId("assets/x/textures/font/c.png"));
        assertNull(TexturePadding.atlasTextureId("assets/x/textures/entity/c.png"));
        assertNull(TexturePadding.atlasTextureId("pack.png"));
    }
}
