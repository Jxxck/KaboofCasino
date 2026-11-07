package de.tigges.kaboofcasino.packtool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;

/**
 * Duck derby models (render.md 1.2/2.2/3.2; mod {@code DuckDerbyRenderer}): the six items, the twelve static tub
 * models {@code roulettecasino:block/duck_derby_0..11} (copied verbatim with their textures, one item definition
 * {@code roulettecasino:block/duck_derby_<p>} each, one display per part like the poker table), the entity sheet
 * {@code duck_derby.png} (192 x 176) copied into the blocks atlas, the renderer's parts ({@link DerbyModels}: four
 * ducks, the gates, the sign overlay with its 12 bulbs) and the floating panel's font.
 *
 * <p>Panel font {@code kaboofcasino:derby_panel} (the derby rig's text display draws the renderer's coloured bars with
 * it; fixed code points, mirrored by the plugin's {@code DerbyPanel} and checked by its {@code DerbyPackKeysTest}):
 * <ul>
 *   <li>U+E000..E007 spaces -1, -2, -4 ... -128 px; U+E008..E00F spaces +1 ... +128 px;</li>
 *   <li>U+E010 the lane header's edge and U+E011 its fill. {@code CasinoFloatingPanels.background} draws the colour
 *       {@code HEADER_EDGE} only as a 1 px line 0xB38A45 along the rectangle's bottom, so the header is the lane
 *       colour box with a bronze line under it, 1 px wider on each side: the edge is a 56 x 12 cell whose last row is
 *       bronze (ascent 9), the fill white 54 x 11 (ascent 9, tinted with {@code HEADER[lane]} by the text colour);
 *       the mod's 14 px box is 11 px here so it fits a 10 px text line;</li>
 *   <li>U+E012 the lane strip under a bettor's head (white 8 x 2 at 8 px below the line top: an 8 x 10 cell, ascent
 *       7);</li>
 *   <li>U+E013 the countdown bar's frame (102 x 5, ascent 5): {@code CasinoFloatingPanels.frame} painted at that
 *       size (three brass rings 0x6E4D26 / 0xD5B475 / 0x72592F, the 0xF6D98E highlight, the 0xEAD08A corners) and
 *       U+E014..E018 its fill (white 5, 10, 20, 40, 80 x 3 over the frame's rows 1..3: a cell 4 high whose last row
 *       is empty, ascent 4; tinted 0xF2C65C).</li>
 * </ul>
 */
final class DerbyPack {

    static final List<String> SOUND_EVENTS = List.of("duck_quack", "duck_start", "duck_finish", "duck_win",
            "duck_lose", "duck_payout", "duck_push");
    /** Prefixes, or exact keys ending in "=". */
    static final List<String> LANG_PREFIXES = List.of("block.roulettecasino.duck_derby",
            "item.roulettecasino.duck_derby_mechanism", "item.roulettecasino.derby_duck_", "gui.roulettecasino.derby.",
            "message.roulettecasino.derby.", "subtitles.roulettecasino.duck_");
    /** {@code DuckDerbyBlock.PART} 0..11: 3 columns x 4 rows. */
    static final int PARTS = 12;
    static final String FONT = "assets/kaboofcasino/font/derby_panel.json";
    static final int NEG_SPACE = 0xE000;
    static final int POS_SPACE = 0xE008;
    static final int EDGE = 0xE010;
    static final int FILL = 0xE011;
    static final int STRIP = 0xE012;
    static final int TRACK = 0xE013;
    /** U+E014 + k: the countdown fill of 5 x 2^k px. */
    static final int BAR = 0xE014;
    static final int BAR_GLYPHS = 5;
    /** {@code background} of {@code HEADER_EDGE}: {@code rect(x1, y2 - 1, x2, y2, -5010875)}. */
    static final int EDGE_LINE = 0xFFB38A45;
    /** {@code CasinoFloatingPanels.frame}: the rings, the highlight and the corners. */
    static final int[] BRASS = {0xFF6E4D26, 0xFFD5B475, 0xFF72592F};
    static final int HIGHLIGHT = 0xFFF6D98E;
    static final int CORNER = 0xFFEAD08A;

    private DerbyPack() {
    }

    /** {@code roulettecasino:block/duck_derby_<part>}: the mod's block model, also the item model key of its display. */
    static String bodyModel(int part) {
        return "roulettecasino:block/duck_derby_" + part;
    }

    static void add(ModJar jar, PackTree tree, Path packSrc) {
        ModelCopier copier = new ModelCopier(jar, tree);
        for (String id : List.of("duck_derby", "duck_derby_mechanism", "derby_duck_yellow", "derby_duck_red",
                "derby_duck_white", "derby_duck_black")) {
            copier.itemDefinition("roulettecasino:" + id);
        }
        for (int part = 0; part < PARTS; part++) {
            String model = bodyModel(part);
            copier.model(model);
            tree.put("assets/roulettecasino/items/block/duck_derby_" + part + ".json",
                    Json.bytes(Json.itemDefinition(model)));
        }
        copier.copyAs("assets/roulettecasino/textures/entity/duck_derby.png",
                "assets/roulettecasino/textures/block/casino_entity/duck_derby.png");
        DerbyModels.add(tree, packSrc);
        panelFont(tree);
    }

    private static JsonObject bitmap(String file, int height, int ascent, int codePoint) {
        JsonArray chars = new JsonArray();
        chars.add(Character.toString(codePoint));
        JsonObject p = new JsonObject();
        p.addProperty("type", "bitmap");
        p.addProperty("file", "kaboofcasino:font/derby_panel/" + file + ".png");
        p.addProperty("height", height);
        p.addProperty("ascent", ascent);
        p.add("chars", chars);
        return p;
    }

    private static JsonObject glyph(PackTree tree, String file, BufferedImage image, int ascent, int codePoint) {
        tree.put("assets/kaboofcasino/textures/font/derby_panel/" + file + ".png", Images.write(image));
        return bitmap(file, image.getHeight(), ascent, codePoint);
    }

    /**
     * {@code CasinoFloatingPanels.frame(0, 0, w, h)} in whole pixels: three rings (a 1.06 px rect covers its pixel),
     * the highlight row 1 from x 4 to w - 4, then the 2 x 2 corners.
     */
    static BufferedImage brass(int w, int h) {
        BufferedImage img = Images.blank(w, h);
        for (int i = 0; i < 3; i++) {
            int a = i;
            int b = i;
            int c = w - i;
            int d = h - i;
            Images.fill(img, a, b, c - a, 1, BRASS[i]);
            Images.fill(img, a, d - 1, c - a, 1, BRASS[i]);
            if (d - 1 > b + 1) {
                Images.fill(img, a, b + 1, 1, d - 1 - (b + 1), BRASS[i]);
                Images.fill(img, c - 1, b + 1, 1, d - 1 - (b + 1), BRASS[i]);
            }
        }
        Images.fill(img, 4, 1, w - 8, 1, HIGHLIGHT);
        for (int x : new int[] {1, w - 3}) {
            for (int y : new int[] {1, h - 3}) {
                Images.fill(img, x, y, 2, 2, CORNER);
            }
        }
        return img;
    }

    static void panelFont(PackTree tree) {
        JsonArray providers = new JsonArray();
        JsonObject advances = new JsonObject();
        for (int i = 0; i < 8; i++) {
            advances.add(Character.toString(NEG_SPACE + i), new JsonPrimitive(-(1 << i)));
            advances.add(Character.toString(POS_SPACE + i), new JsonPrimitive(1 << i));
        }
        JsonObject space = new JsonObject();
        space.addProperty("type", "space");
        space.add("advances", advances);
        providers.add(space);
        BufferedImage edge = Images.blank(56, 12);
        Images.fill(edge, 0, 11, 56, 1, EDGE_LINE);
        providers.add(glyph(tree, "edge", edge, 9, EDGE));
        BufferedImage fill = Images.blank(54, 11);
        Images.fill(fill, 0, 0, 54, 11, 0xFFFFFFFF);
        providers.add(glyph(tree, "fill", fill, 9, FILL));
        BufferedImage strip = Images.blank(8, 10);
        Images.fill(strip, 0, 8, 8, 2, 0xFFFFFFFF);
        providers.add(glyph(tree, "strip", strip, 7, STRIP));
        providers.add(glyph(tree, "track", brass(102, 5), 5, TRACK));
        for (int k = 0; k < BAR_GLYPHS; k++) {
            // 3 white rows over a transparent one: ascent 4 needs a cell at least 4 high (the client's ascent <= height)
            BufferedImage bar = Images.blank(5 << k, 4);
            Images.fill(bar, 0, 0, 5 << k, 3, 0xFFFFFFFF);
            providers.add(glyph(tree, "bar_" + (5 << k), bar, 4, BAR + k));
        }
        tree.put(FONT, Json.bytes(Json.object("providers", providers)));
    }
}
