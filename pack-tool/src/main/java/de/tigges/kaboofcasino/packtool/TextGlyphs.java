package de.tigges.kaboofcasino.packtool;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GUI text fonts built from the vanilla bitmap fonts (vendored in {@code pack-src/vanilla}, see its README).
 *
 * <p>A chest title is drawn at y = 6 of the window; a bitmap glyph's top is {@code 6 + 7 - ascent}, and the client
 * rejects {@code ascent > height}. To put text anywhere from y = -47 to 232 (the mod's headlines sit above the
 * window) every glyph the casino needs is copied into one sheet with tall cells: 9 x 64 px, the baseline at row 11
 * (ascii/nonlatin glyphs start at row 4, accented ones at row 1), the rest transparent. One font per text row then
 * reuses that sheet with {@code height 64} and {@code ascent = 17 - y}. Advances are computed exactly like the
 * client ({@code actual width + 1}, scale 1), so the plugin can centre and right-align text.
 */
final class TextGlyphs {

    static final int CELL_W = 9;
    static final int CELL_H = 64;
    static final int BASELINE = 11;
    static final int COLS = 16;
    static final int MIN_Y = -47;
    static final int MAX_Y = 232;
    static final String SHEET = "kaboofcasino:font/text.png";
    static final String SHEET_PATH = "assets/kaboofcasino/textures/font/text.png";
    static final String FONT_PREFIX = "kaboofcasino:t_";
    static final int SPACE_ADVANCE = 4;
    /** Characters beyond ASCII that German text and the casino's labels use. */
    private static final String EXTRA = "–—‘’‚“”„…•€";

    private record Glyph(char c, BufferedImage cell, int advance) {
    }

    private final List<Glyph> glyphs = new ArrayList<>();
    private final Map<Character, Glyph> byChar = new LinkedHashMap<>();
    private BufferedImage sheet;

    /** Reads {@code fonts.json} and the three PNGs from {@code vanillaDir}. */
    static TextGlyphs load(Path vanillaDir) {
        JsonObject fonts = Json.parse(ModJar.readAll(vanillaDir.resolve("fonts.json")));
        TextGlyphs out = new TextGlyphs();
        for (JsonElement p : fonts.getAsJsonArray("providers")) {
            JsonObject provider = p.getAsJsonObject();
            String file = provider.get("file").getAsString();
            String name = file.substring(file.lastIndexOf('/') + 1);
            BufferedImage image = Images.read(ModJar.readAll(vanillaDir.resolve(name)));
            out.addProvider(image, provider.get("height").getAsInt(), provider.get("ascent").getAsInt(),
                    provider.getAsJsonArray("chars"));
        }
        out.buildSheet();
        return out;
    }

    private static boolean wanted(char c) {
        return c >= 0x21 && c <= 0x7e || c >= 0xa1 && c <= 0xff || EXTRA.indexOf(c) >= 0;
    }

    private void addProvider(BufferedImage image, int height, int ascent, JsonArray rows) {
        int rowCount = rows.size();
        int colCount = rows.get(0).getAsString().length();
        int cw = image.getWidth() / colCount;
        int ch = image.getHeight() / rowCount;
        if (ch != height) {
            throw new IllegalStateException("vanilla provider with scale != 1 (" + height + " vs " + ch + ")");
        }
        if (cw > CELL_W || BASELINE - ascent < 0 || BASELINE - ascent + ch > CELL_H) {
            throw new IllegalStateException("vanilla glyph cell " + cw + "x" + ch + " does not fit");
        }
        for (int r = 0; r < rowCount; r++) {
            String row = rows.get(r).getAsString();
            for (int col = 0; col < row.length(); col++) {
                char c = row.charAt(col);
                if (c == 0 || !wanted(c) || byChar.containsKey(c)) {
                    continue; // the first provider that has a character wins, as in the client
                }
                int actual = Images.actualGlyphWidth(image, col * cw, r * ch, cw, ch);
                BufferedImage cell = Images.blank(CELL_W, CELL_H);
                Images.copy(image, col * cw, r * ch, cw, ch, cell, 0, BASELINE - ascent);
                Glyph glyph = new Glyph(c, cell, actual + 1);
                glyphs.add(glyph);
                byChar.put(c, glyph);
            }
        }
    }

    private void buildSheet() {
        glyphs.sort((a, b) -> Character.compare(a.c(), b.c()));
        int rows = (glyphs.size() + COLS - 1) / COLS;
        sheet = Images.blank(COLS * CELL_W, rows * CELL_H);
        for (int i = 0; i < glyphs.size(); i++) {
            Images.copy(glyphs.get(i).cell(), 0, 0, CELL_W, CELL_H, sheet, i % COLS * CELL_W, i / COLS * CELL_H);
        }
    }

    /** The {@code chars} grid of the sheet (unused cells are U+0000). */
    JsonArray charRows() {
        JsonArray rows = new JsonArray();
        int count = (glyphs.size() + COLS - 1) / COLS;
        for (int r = 0; r < count; r++) {
            StringBuilder row = new StringBuilder();
            for (int col = 0; col < COLS; col++) {
                int i = r * COLS + col;
                row.append(i < glyphs.size() ? glyphs.get(i).c() : '\0');
            }
            rows.add(row.toString());
        }
        return rows;
    }

    /** Font id for a text row whose ascii glyph tops are at chest-window y {@code y}. */
    static String fontId(int y) {
        return FONT_PREFIX + (y < 0 ? "m" + -y : Integer.toString(y));
    }

    /** Writes the sheet and one font per row y in [MIN_Y, MAX_Y]. */
    void writeTo(PackTree tree) {
        tree.put(SHEET_PATH, Images.write(sheet));
        JsonArray rows = charRows();
        for (int y = MIN_Y; y <= MAX_Y; y++) {
            JsonObject space = new JsonObject();
            space.addProperty("type", "space");
            space.add("advances", Json.object(" ", new com.google.gson.JsonPrimitive(SPACE_ADVANCE)));
            JsonObject bitmap = new JsonObject();
            bitmap.addProperty("type", "bitmap");
            bitmap.addProperty("file", SHEET);
            bitmap.addProperty("height", CELL_H);
            bitmap.addProperty("ascent", 17 - y);
            bitmap.add("chars", rows);
            JsonArray providers = new JsonArray();
            providers.add(space);
            providers.add(bitmap);
            String id = fontId(y);
            String path = "assets/kaboofcasino/font/" + id.substring(id.indexOf(':') + 1) + ".json";
            tree.put(path, Json.bytes(Json.object("providers", providers)));
        }
    }

    /** Advance of every character the text fonts know, plus the space. */
    Map<Character, Integer> advances() {
        Map<Character, Integer> out = new LinkedHashMap<>();
        out.put(' ', SPACE_ADVANCE);
        for (Glyph g : glyphs) {
            out.put(g.c(), g.advance());
        }
        return out;
    }

    int width(String text) {
        int w = 0;
        for (char c : text.toCharArray()) {
            w += c == ' ' ? SPACE_ADVANCE : glyph(c).advance();
        }
        return w;
    }

    private Glyph glyph(char c) {
        Glyph g = byChar.get(c);
        if (g == null) {
            throw new IllegalArgumentException("no glyph for '" + c + "'");
        }
        return g;
    }

    /**
     * Draws {@code text} like the client's {@code Font.drawInBatch}: glyph pixels times {@code argb}, with the
     * vanilla shadow ({@code (rgb & 0xFCFCFC) >> 2}, offset 1,1) underneath when asked.
     *
     * @param top y of the ascii glyph tops
     */
    void draw(BufferedImage target, String text, int x, int top, int argb, boolean shadow) {
        if (shadow) {
            int shade = argb & 0xff000000 | (argb & 0xfcfcfc) >> 2;
            drawRun(target, text, x + 1, top + 1, shade);
        }
        drawRun(target, text, x, top, argb);
    }

    private void drawRun(BufferedImage target, String text, int x, int top, int argb) {
        int pen = x;
        for (char c : text.toCharArray()) {
            if (c == ' ') {
                pen += SPACE_ADVANCE;
                continue;
            }
            Glyph g = glyph(c);
            for (int cy = 0; cy < CELL_H; cy++) {
                for (int cx = 0; cx < CELL_W; cx++) {
                    int src = g.cell().getRGB(cx, cy);
                    if ((src >>> 24) == 0) {
                        continue;
                    }
                    int tx = pen + cx;
                    int ty = top - (BASELINE - 7) + cy;
                    if (tx >= 0 && ty >= 0 && tx < target.getWidth() && ty < target.getHeight()) {
                        Images.blend(target, tx, ty, multiply(src, argb));
                    }
                }
            }
            pen += g.advance();
        }
    }

    private static int multiply(int a, int b) {
        int al = (a >>> 24) * (b >>> 24) / 255;
        int r = (a >> 16 & 255) * (b >> 16 & 255) / 255;
        int g = (a >> 8 & 255) * (b >> 8 & 255) / 255;
        int bl = (a & 255) * (b & 255) / 255;
        return al << 24 | r << 16 | g << 8 | bl;
    }
}
