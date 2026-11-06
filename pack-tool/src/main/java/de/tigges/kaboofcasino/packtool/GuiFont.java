package de.tigges.kaboofcasino.packtool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The {@code kaboofcasino:gui} font: GUI art as bitmap glyphs (render.md 5) plus negative/positive spaces, and the
 * manifest ({@code gui-layout.json}) that tells the plugin each glyph's character, advance and intended position.
 *
 * <p>Geometry: a chest title is drawn at (8, 6) of the window. A glyph's top is {@code 6 + 7 - ascent}, so art whose
 * top-left should sit at window (x, y) gets {@code ascent = 13 - y} and is reached by moving the pen to x with
 * spaces. Glyphs are at most 256 x 256 px (client {@code FontTexture} page size). The advance is the client's
 * {@code actual width + 1}.
 */
final class GuiFont {

    static final String FONT = "kaboofcasino:gui";
    static final String FONT_PATH = "assets/kaboofcasino/font/gui.json";
    /** First private-use code point for art glyphs. */
    private static final int FIRST_GLYPH = 0xE000;
    /** Space glyphs: U+F801.. = -1, -2, -4 ... -1024; U+F821.. = +1 ... +1024. */
    static final int NEG_SPACE = 0xF801;
    static final int POS_SPACE = 0xF821;
    static final int SPACE_BITS = 11;

    /** One glyph for the manifest. {@code x}/{@code y} are the intended window position (or 0 for free glyphs). */
    record Glyph(String name, int codePoint, int advance, int width, int height, int x, int y) {
    }

    private final JsonArray providers = new JsonArray();
    private final java.util.List<Anchor> anchors = new java.util.ArrayList<>();

    /**
     * A named position of a screen for the plugin: window x (left, centre or right edge per {@code align}) and y (the
     * cap top of text, the top of a glyph). Text anchors also decide which text fonts the pack contains.
     */
    record Anchor(String name, int x, int y, String align, boolean text) {
        static Anchor text(String name, int x, int y, String align) {
            return new Anchor(name, x, y, align, true);
        }

        static Anchor glyph(String name, int x, int y) {
            return new Anchor(name, x, y, "left", false);
        }
    }
    private final Map<String, Glyph> glyphs = new LinkedHashMap<>();
    private final PackTree tree;
    private int next = FIRST_GLYPH;

    GuiFont(PackTree tree) {
        this.tree = tree;
        JsonObject advances = new JsonObject();
        for (int i = 0; i < SPACE_BITS; i++) {
            advances.add(Character.toString(NEG_SPACE + i), new JsonPrimitive(-(1 << i)));
            advances.add(Character.toString(POS_SPACE + i), new JsonPrimitive(1 << i));
        }
        JsonObject space = new JsonObject();
        space.addProperty("type", "space");
        space.add("advances", advances);
        providers.add(space);
    }

    /**
     * Adds one image as one glyph drawn with its top-left at window (x, y).
     *
     * @param texture path below {@code assets/kaboofcasino/textures/}, e.g. {@code font/slot/bg_0_0.png}
     */
    Glyph art(String name, BufferedImage image, String texture, int x, int y) {
        checkSize(image.getWidth(), image.getHeight(), name);
        tree.put("assets/kaboofcasino/textures/" + texture, Images.write(image));
        int cp = next++;
        int ascent = 13 - y;
        providers.add(bitmap(texture, image.getHeight(), ascent, row(cp)));
        int advance = Images.actualGlyphWidth(image, 0, 0, image.getWidth(), image.getHeight()) + 1;
        Glyph g = new Glyph(name, cp, advance, image.getWidth(), image.getHeight(), x, y);
        glyphs.put(name, g);
        return g;
    }

    /**
     * Adds a grid of equal cells as glyphs with a common {@code height}/{@code ascent} (scale height / cell height).
     *
     * @param names row-major glyph names, one per cell
     */
    void grid(String[] names, BufferedImage image, String texture, int cols, int rows, int height, int ascent, int x,
              int y) {
        int cw = image.getWidth() / cols;
        int ch = image.getHeight() / rows;
        checkSize(cw, ch, texture);
        if (names.length != cols * rows) {
            throw new IllegalArgumentException("need " + cols * rows + " names for " + texture);
        }
        tree.put("assets/kaboofcasino/textures/" + texture, Images.write(image));
        JsonArray chars = new JsonArray();
        double scale = (double) height / ch;
        for (int r = 0; r < rows; r++) {
            StringBuilder row = new StringBuilder();
            for (int c = 0; c < cols; c++) {
                int cp = next++;
                row.appendCodePoint(cp);
                int actual = Images.actualGlyphWidth(image, c * cw, r * ch, cw, ch);
                int advance = (int) (0.5 + actual * scale) + 1;
                String name = names[r * cols + c];
                glyphs.put(name, new Glyph(name, cp, advance, cw, ch, x, y));
            }
            chars.add(row.toString());
        }
        JsonObject provider = new JsonObject();
        provider.addProperty("type", "bitmap");
        provider.addProperty("file", "kaboofcasino:" + texture);
        provider.addProperty("height", height);
        provider.addProperty("ascent", ascent);
        provider.add("chars", chars);
        providers.add(provider);
    }

    private static void checkSize(int w, int h, String what) {
        if (w > 256 || h > 256) {
            throw new IllegalArgumentException(what + " is " + w + "x" + h + "; font glyphs are limited to 256x256");
        }
    }

    private static JsonArray row(int cp) {
        JsonArray chars = new JsonArray();
        chars.add(Character.toString(cp));
        return chars;
    }

    private static JsonObject bitmap(String texture, int height, int ascent, JsonArray chars) {
        if (ascent > height) {
            throw new IllegalArgumentException(texture + ": ascent " + ascent + " > height " + height);
        }
        JsonObject provider = new JsonObject();
        provider.addProperty("type", "bitmap");
        provider.addProperty("file", "kaboofcasino:" + texture);
        provider.addProperty("height", height);
        provider.addProperty("ascent", ascent);
        provider.add("chars", chars);
        return provider;
    }

    /** A named text/glyph position for the plugin; text anchors also select which text fonts are built. */
    void anchor(Anchor a) {
        anchors.add(a);
    }

    /** The window rows text is drawn at (one text font each). */
    java.util.SortedSet<Integer> textRows() {
        java.util.SortedSet<Integer> rows = new java.util.TreeSet<>();
        for (Anchor a : anchors) {
            if (a.text()) {
                rows.add(a.y());
            }
        }
        return rows;
    }

    void write() {
        tree.put(FONT_PATH, Json.bytes(Json.object("providers", providers)));
    }

    /** The manifest the plugin reads ({@code pack/gui-layout.json} inside the plugin jar). */
    JsonObject manifest(TextGlyphs text) {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.addProperty("guiFont", FONT);
        JsonObject spaces = new JsonObject();
        spaces.addProperty("negativeFirst", NEG_SPACE);
        spaces.addProperty("positiveFirst", POS_SPACE);
        spaces.addProperty("bits", SPACE_BITS);
        root.add("spaces", spaces);
        JsonObject all = new JsonObject();
        for (Glyph g : glyphs.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("codePoint", g.codePoint());
            o.addProperty("advance", g.advance());
            o.addProperty("width", g.width());
            o.addProperty("height", g.height());
            o.addProperty("x", g.x());
            o.addProperty("y", g.y());
            all.add(g.name(), o);
        }
        root.add("glyphs", all);
        JsonObject anchorJson = new JsonObject();
        for (Anchor a : anchors) {
            JsonObject o = new JsonObject();
            o.addProperty("x", a.x());
            o.addProperty("y", a.y());
            o.addProperty("align", a.align());
            anchorJson.add(a.name(), o);
        }
        root.add("anchors", anchorJson);
        JsonObject t = new JsonObject();
        t.addProperty("fontPrefix", TextGlyphs.FONT_PREFIX);
        JsonArray rows = new JsonArray();
        textRows().forEach(rows::add);
        t.add("rows", rows);
        JsonObject advances = new JsonObject();
        for (Map.Entry<Character, Integer> e : text.advances().entrySet()) {
            advances.addProperty(e.getKey().toString(), e.getValue());
        }
        t.add("advances", advances);
        root.add("text", t);
        return root;
    }
}
