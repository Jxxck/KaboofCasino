package de.tigges.kaboofcasino.packtool;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The recipe pictures of the casino guide (Jack: a picture of the crafting grid instead of a text listing): every
 * recipe widget of the mod's {@code casino_guide.json} becomes the lines of one dialog text body in the font
 * {@value #FONT}. The picture is the mod's recipe widget ({@code CasinoGuideScreen}: 3 x 3 slots of 18 px, the gold
 * arrow, the 26 px output slot with its frame outline, the shapeless sign above the arrow, the result's count like
 * an item stack's) on a small felt card in the guide's frame colours, with the icons of {@link GuideIcons}. Shapeless
 * ingredients fill the grid from the top left.
 *
 * <p>Geometry (render.md 5; the dialog's {@code minecraft:plain_message} is a centred {@code FocusableTextWidget}):
 * every line is centred on its own width, a glyph's top is its line's y + 7 - ascent, and the ascent may not exceed
 * the glyph's height. So a glyph can only hang below its line (or 9 px above it for a 16 px icon), and its x depends
 * on the advances before it and on the line's total width.
 * <ul>
 *   <li>Lines whose glyphs all have known advances (and blank ones) are padded to {@value #WIDTH} px with the space
 *       glyphs, so picture x maps to screen x = centre - {@value #WIDTH}/2 + x. No line runs wider, and at least one
 *       is exactly that wide: the widget measures its height by splitting its text at its widest line.</li>
 *   <li>A vanilla texture's advance is the client's ({@link GuideIcons.Layer#UNKNOWN}). Such a glyph is the last of
 *       its line and also drawn once before everything else, {@value #DECOY} px to the left, far off screen. The line
 *       then holds it twice, so centring takes off exactly its advance and the visible copy lands at the same x for
 *       any width. One unknown glyph per line, so the layout spreads them over the lines above the card
 *       (earliest deadline first); recipes with many vanilla items get more lines and blank space above the caption.
 *       </li>
 *   <li>The caption ("Rezept · Name", the plugin's) sits on its own line right above the card, between two runs of
 *       -{@value #DECOY} px: a negative line width centres it all the same and it can never wrap (the line break
 *       finder only breaks above the width), so the lines below it keep their places.</li>
 *   <li>The box of the text (9 px per line + 2 x 4 px padding) contains the card, so the icons are inside the widget
 *       and their hover texts work.</li>
 * </ul>
 * The plugin reads {@value #FILE} (embedded as {@code pack/guide-pictures.json}): per recipe widget ("page:index")
 * the lines as segments, each plain, an icon of cell i or of the result (gets the item's tooltip and the link to its
 * spread), the shapeless sign (its tooltip), the count (the vanilla text shadow) or the caption's place. Same jar and
 * pack, same bytes.
 */
final class GuidePictures {

    static final String FILE = "guide-pictures.json";
    static final String FONT = "kaboofcasino:guide_recipe";
    static final String FONT_PATH = "assets/kaboofcasino/font/guide_recipe.json";
    static final String BOOK_PNG = "assets/roulettecasino/textures/gui/casino_guide/book.png";
    static final String CARD_TEXTURE = "kaboofcasino:font/guide/card";
    static final String HINT_TEXTURE = "kaboofcasino:font/guide/shapeless";
    static final String DIGITS_TEXTURE = "kaboofcasino:font/text_h16";

    /** Width every known line is padded to (even: the line's centre is an integer). */
    static final int WIDTH = 130;
    static final int CARD_X = 1;
    static final int CARD_W = 128;
    static final int CARD_H = 68;
    /** Frame (4) and felt margin (3) around the mod's widget inside the card. */
    static final int INSET = 7;
    /** Card top below the caption line's top (8 px of text and shadow, 3 px air, 1 more). */
    static final int CAPTION_GAP = 12;
    static final int LINE = 9;
    /** How far left the hidden copy of an unknown glyph goes. */
    static final int DECOY = 8192;
    static final int NEG_SPACE = 0xF801;
    static final int POS_SPACE = 0xF821;
    static final int SPACE_BITS = 11;
    static final int FIRST_GLYPH = 0xE000;

    /** The mod's colours ({@code CasinoGuideScreen.slot}, {@code FRAME}) and the guide book's frame. */
    static final int SLOT_OUTER = 0xFF15261E;
    static final int SLOT_INNER = 0xFF49594D;
    static final int SLOT_LIGHT = 0xFF81917C;
    static final int SLOT_SHADE = 0xFF354337;
    static final int FRAME = 0xFFA77A37;
    static final int[] CARD_FRAME = {0xFF0D1C16, 0xFF765629, 0xFFC4A25D, 0xFF102E23};

    /** What a segment is for the plugin. */
    enum Kind {
        PLAIN, CELL, RESULT, HINT, COUNT, CAPTION
    }

    /** One drawable glyph: a cell of a texture grid at a height; advance -1 = the client's. */
    record Glyph(String texture, int cols, int rows, int col, int row, int height, int advance) {
        String key() {
            return texture + "|" + cols + "x" + rows + "|" + col + "," + row + "|" + height;
        }

        boolean known() {
            return advance >= 0;
        }
    }

    /** A glyph at picture position x, y (the top left of its cell). */
    record Place(Glyph glyph, int x, int y, Kind kind, int cell, int order) {
    }

    /** The glyph char of a placement and its line. */
    record Placed(Place place, int line, int ascent) {
    }

    /** A recipe's picture: caption line, card top, line count and placements with their lines. */
    record Layout(int captionLine, int cardTop, int lines, List<Placed> placed) {
    }

    /** One segment of a line for the plugin. */
    record Segment(String text, Kind kind, int cell) {
    }

    /** The recipe data the picture shows. */
    record RecipeData(String key, String result, int count, List<String> cells, boolean shapeless) {
    }

    /** The build result: the manifest bytes and statistics. */
    record Result(byte[] manifest, int pictures, int glyphs, int providers, Map<String, String> fallbacks,
                  List<String> vanillaTextures) {
    }

    private final GuideIcons icons;
    private final Glyph card;
    private final Glyph hint;
    private final Map<Character, Glyph> digits = new LinkedHashMap<>();

    private GuidePictures(PackTree tree, TextGlyphs text) {
        this.icons = new GuideIcons(tree);
        this.card = art(tree, CARD_TEXTURE);
        this.hint = art(tree, HINT_TEXTURE);
        JsonArray rows = text.charRows();
        for (int r = 0; r < rows.size(); r++) {
            String row = rows.get(r).getAsString();
            for (int c = 0; c < row.length(); c++) {
                char ch = row.charAt(c);
                if (ch >= '0' && ch <= '9') {
                    digits.put(ch, new Glyph(DIGITS_TEXTURE, TextGlyphs.COLS, rows.size(), c, r, TextGlyphs.CELL_H,
                            text.advances().get(ch)));
                }
            }
        }
    }

    /** One of our art images as a single glyph at scale 1 (advance as the client computes it). */
    private static Glyph art(PackTree tree, String texture) {
        BufferedImage image = Images.read(tree.get(GuideIcons.path(texture, "textures", ".png")));
        return new Glyph(texture, 1, 1, 0, 0, image.getHeight(),
                Images.actualGlyphWidth(image, 0, 0, image.getWidth(), image.getHeight()) + 1);
    }

    /**
     * Adds the art, the fallback glyph and the font to the pack and returns the manifest. Runs after every other
     * generator (it reads the final item definitions, models and textures, and the text sheet).
     */
    static Result add(ModJar jar, PackTree tree, TextGlyphs text) {
        if (!tree.has(GuideIcons.path(DIGITS_TEXTURE, "textures", ".png"))) {
            throw new IllegalStateException("the recipe counts need the text sheet " + DIGITS_TEXTURE);
        }
        tree.put(GuideIcons.path(CARD_TEXTURE, "textures", ".png"), Images.write(cardArt(jar)));
        tree.put(GuideIcons.path(HINT_TEXTURE, "textures", ".png"),
                Images.write(Images.crop(Images.read(jar.bytes(BOOK_PNG)), 392, 0, 16, 10)));
        GuidePictures pictures = new GuidePictures(tree, text);
        Result result = pictures.build(recipes(Json.parse(jar.bytes(GuideData.BOOK))), tree);
        if (!result.fallbacks().isEmpty()) {
            tree.put(GuideIcons.path(GuideIcons.FALLBACK_TEXTURE, "textures", ".png"), Images.write(fallbackArt(text)));
        }
        return result;
    }

    /** Every recipe widget of the book, keyed "page:index" (1-based page, index in its widget list). */
    static List<RecipeData> recipes(JsonObject book) {
        List<RecipeData> out = new ArrayList<>();
        JsonArray pages = book.getAsJsonArray("pages");
        for (int p = 0; p < pages.size(); p++) {
            JsonArray widgets = pages.get(p).getAsJsonObject().getAsJsonArray("widgets");
            for (int i = 0; i < widgets.size(); i++) {
                JsonObject w = widgets.get(i).getAsJsonObject();
                if (!w.get("kind").getAsString().equals("recipe")) {
                    continue;
                }
                List<String> cells = new ArrayList<>();
                for (JsonElement c : w.getAsJsonArray("cells")) {
                    cells.add(c.isJsonNull() ? null : c.getAsString());
                }
                out.add(new RecipeData((p + 1) + ":" + i, w.get("result").getAsString(),
                        w.has("count") ? w.get("count").getAsInt() : 1, cells,
                        w.has("shapeless") && w.get("shapeless").getAsBoolean()));
            }
        }
        return out;
    }

    private Result build(List<RecipeData> recipes, PackTree tree) {
        Map<String, Layout> layouts = new LinkedHashMap<>();
        for (RecipeData r : recipes) {
            layouts.put(r.key(), layout(r));
        }
        // one codepoint per glyph and ascent, in a stable order
        Map<String, Placed> distinct = new TreeMap<>();
        for (Layout l : layouts.values()) {
            for (Placed p : l.placed()) {
                distinct.putIfAbsent(p.place().glyph().key() + "|" + p.ascent(), p);
            }
        }
        Map<String, Integer> codepoints = new LinkedHashMap<>();
        int next = FIRST_GLYPH;
        for (String key : distinct.keySet()) {
            codepoints.put(key, next++);
        }
        if (next >= NEG_SPACE) {
            throw new IllegalStateException("too many recipe glyphs: " + (next - FIRST_GLYPH));
        }
        int providers = writeFont(tree, distinct, codepoints);
        JsonObject manifest = new JsonObject();
        manifest.addProperty("format", 1);
        manifest.addProperty("font", FONT);
        manifest.addProperty("width", WIDTH);
        JsonObject all = new JsonObject();
        for (Map.Entry<String, Layout> e : layouts.entrySet()) {
            JsonArray lines = new JsonArray();
            for (List<Segment> line : lines(e.getValue(), codepoints)) {
                JsonArray segments = new JsonArray();
                for (Segment s : line) {
                    JsonObject o = new JsonObject();
                    if (s.kind() == Kind.CAPTION) {
                        o.addProperty("caption", true);
                    } else {
                        o.addProperty("t", s.text());
                        switch (s.kind()) {
                            case CELL -> o.addProperty("cell", s.cell());
                            case RESULT -> o.addProperty("result", true);
                            case HINT -> o.addProperty("hint", true);
                            case COUNT -> o.addProperty("count", true);
                            default -> {
                            }
                        }
                    }
                    segments.add(o);
                }
                lines.add(segments);
            }
            all.add(e.getKey(), lines);
        }
        manifest.add("pictures", all);
        Map<String, String> fallbacks = new TreeMap<>();
        Set<String> vanilla = new TreeSet<>();
        for (RecipeData r : recipes) {
            List<String> ids = new ArrayList<>();
            ids.add(r.result());
            for (String c : r.cells()) {
                if (c != null) {
                    ids.add(GuideIcons.shown(c));
                }
            }
            for (String id : ids) {
                GuideIcons.Icon icon = icons.icon(id);
                if (icon.source().equals("fallback")) {
                    fallbacks.put(id, r.key());
                }
                for (GuideIcons.Layer layer : icon.layers()) {
                    if (layer.texture().startsWith("minecraft:")) {
                        vanilla.add(layer.texture());
                    }
                }
            }
        }
        return new Result(Json.bytes(manifest), layouts.size(), codepoints.size(), providers, fallbacks,
                List.copyOf(vanilla));
    }

    // ---- layout ----------------------------------------------------------------------------------------------------

    /** The placements of a recipe whose card top is at {@code top}. */
    List<Place> places(RecipeData r, int top) {
        List<Place> out = new ArrayList<>();
        int order = 0;
        out.add(new Place(card, CARD_X, top, Kind.PLAIN, -1, order++));
        int x0 = CARD_X + INSET;
        int y0 = top + INSET;
        if (r.shapeless()) {
            out.add(new Place(hint, x0 + 62, y0 + 4, Kind.HINT, -1, order++));
        }
        List<Integer> shown = slots(r.cells(), r.shapeless());
        for (int slot = 0; slot < 9; slot++) {
            int cell = shown.get(slot);
            if (cell < 0) {
                continue;
            }
            for (GuideIcons.Layer l : icons.icon(GuideIcons.shown(r.cells().get(cell))).layers()) {
                out.add(new Place(glyph(l), x0 + 1 + 18 * (slot % 3), y0 + 1 + 18 * (slot / 3) + l.dy(),
                        Kind.CELL, cell, order++));
            }
        }
        int rx = x0 + 86 + 5;
        int ry = y0 + 14 + 5;
        for (GuideIcons.Layer l : icons.icon(r.result()).layers()) {
            out.add(new Place(glyph(l), rx, ry + l.dy(), Kind.RESULT, -1, order++));
        }
        if (r.count() > 1) {
            String count = Integer.toString(r.count());
            int width = 0;
            for (char c : count.toCharArray()) {
                width += digits.get(c).advance();
            }
            // ItemRenderer: count at (x + 19 - 2 - width, y + 6 + 3); the sheet's digits start 4 rows into the cell
            int pen = rx + 17 - width;
            for (char c : count.toCharArray()) {
                Glyph d = digits.get(c);
                out.add(new Place(d, pen, ry + 9 - (TextGlyphs.BASELINE - 7), Kind.COUNT, -1, order++));
                pen += d.advance();
            }
        }
        return out;
    }

    /**
     * Which cell each of the nine slots shows (-1 = empty): the cells as they are, or for a shapeless recipe its
     * ingredients from the top left in the recipe's order.
     */
    static List<Integer> slots(List<String> cells, boolean shapeless) {
        List<Integer> out = new ArrayList<>();
        if (shapeless) {
            for (int i = 0; i < 9; i++) {
                if (cells.get(i) != null) {
                    out.add(i);
                }
            }
            while (out.size() < 9) {
                out.add(-1);
            }
        } else {
            for (int i = 0; i < 9; i++) {
                out.add(cells.get(i) == null ? -1 : i);
            }
        }
        return out;
    }

    private static Glyph glyph(GuideIcons.Layer l) {
        return new Glyph(l.texture(), l.cols(), l.rows(), l.col(), l.row(), l.height(), l.advance());
    }

    /**
     * Places the recipe: the caption on line k with the card 12 px below it, as few lines as possible: every glyph
     * with a known advance on the first line that is not the caption's (in drawing order: card, sign, icons, result,
     * count), each unknown one alone at the end of a line it can hang from (earliest deadline first).
     */
    Layout layout(RecipeData r) {
        for (int caption = 0; caption < 8; caption++) {
            int top = caption * LINE + CAPTION_GAP;
            int lines = Math.ceilDiv(top + CARD_H - 4, LINE);
            List<Place> places = places(r, top);
            int first = caption == 0 ? 1 : 0;
            List<Place> unknown = new ArrayList<>();
            List<Placed> placed = new ArrayList<>();
            boolean ok = true;
            for (Place p : places) {
                if (p.glyph().known()) {
                    int ascent = first * LINE + 7 - p.y();
                    if (ascent > p.glyph().height()) {
                        ok = false;
                    }
                    placed.add(new Placed(p, first, ascent));
                } else {
                    unknown.add(p);
                }
            }
            unknown.sort(Comparator.comparingInt(GuidePictures::deadline).thenComparingInt(Place::order));
            int line = 0;
            for (Place p : unknown) {
                while (line == caption) {
                    line++;
                }
                if (line > deadline(p) || line >= lines) {
                    ok = false;
                    break;
                }
                placed.add(new Placed(p, line, line * LINE + 7 - p.y()));
                line++;
            }
            if (ok && orderHolds(placed)) {
                // the widget measures its height by splitting at its widest line: one line must be exactly WIDTH
                // wide (a line without an unknown glyph); if every line has one, add a blank line
                Set<Integer> unknownLines = new HashSet<>();
                for (Placed p : placed) {
                    if (!p.place().glyph().known()) {
                        unknownLines.add(p.line());
                    }
                }
                boolean widthLine = false;
                for (int l = 0; l < lines; l++) {
                    widthLine |= l != caption && !unknownLines.contains(l);
                }
                return new Layout(caption, top, widthLine ? lines : lines + 1, List.copyOf(placed));
            }
        }
        throw new IllegalStateException("no layout for the recipe picture " + r.key());
    }

    /** The last line a glyph can hang from (its ascent may not exceed its height). */
    private static int deadline(Place p) {
        return Math.floorDiv(p.y() + p.glyph().height() - 7, LINE);
    }

    /**
     * Overlapping placements are drawn in their order: on an earlier line, or on the same line before it (a line draws
     * its known glyphs in order and its unknown one last).
     */
    static boolean orderHolds(List<Placed> placed) {
        for (Placed a : placed) {
            for (Placed b : placed) {
                if (a.place().order() < b.place().order() && overlaps(a.place(), b.place())
                        && !(a.line() < b.line() || a.line() == b.line() && a.place().glyph().known())) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean overlaps(Place a, Place b) {
        return a.x() < b.x() + width(b) && b.x() < a.x() + width(a) && a.y() < b.y() + b.glyph().height()
                && b.y() < a.y() + a.glyph().height();
    }

    /** The drawn width of a placement's cell (the card is the only wide one; icons are at most 16). */
    private static int width(Place p) {
        return p.glyph().texture().equals(CARD_TEXTURE) ? CARD_W : GuideIcons.ICON;
    }

    // ---- lines -----------------------------------------------------------------------------------------------------

    /** The segments of every line. */
    static List<List<Segment>> lines(Layout layout, Map<String, Integer> codepoints) {
        List<List<Segment>> out = new ArrayList<>();
        for (int line = 0; line < layout.lines(); line++) {
            List<Segment> segments = new ArrayList<>();
            if (line == layout.captionLine()) {
                segments.add(new Segment(spaces(-DECOY), Kind.PLAIN, -1));
                segments.add(new Segment("", Kind.CAPTION, -1));
                segments.add(new Segment(spaces(-DECOY), Kind.PLAIN, -1));
                out.add(segments);
                continue;
            }
            List<Placed> known = new ArrayList<>();
            Placed unknown = null;
            for (Placed p : layout.placed()) {
                if (p.line() != line) {
                    continue;
                }
                if (p.place().glyph().known()) {
                    known.add(p);
                } else if (unknown != null) {
                    throw new IllegalStateException("two unknown glyphs on line " + line);
                } else {
                    unknown = p;
                }
            }
            known.sort(Comparator.comparingInt(p -> p.place().order()));
            if (unknown == null) {
                int pen = 0;
                for (Placed p : known) {
                    add(segments, spaces(p.place().x() - pen), Kind.PLAIN, -1);
                    add(segments, ch(codepoints, p), p.place().kind(), p.place().cell());
                    pen = p.place().x() + p.place().glyph().advance();
                }
                // padded to WIDTH, also a line nothing hangs from (it then sets the width the widget splits at)
                add(segments, spaces(WIDTH - pen), Kind.PLAIN, -1);
            } else {
                int xu = unknown.place().x();
                // the hidden copy far left; it moves the pen by its own advance, which the visible copy cancels
                add(segments, spaces(-DECOY), Kind.PLAIN, -1);
                add(segments, ch(codepoints, unknown), Kind.PLAIN, -1);
                int pen = -DECOY;
                for (Placed p : known) {
                    int v = p.place().x() + xu - WIDTH;
                    add(segments, spaces(v - pen), Kind.PLAIN, -1);
                    add(segments, ch(codepoints, p), p.place().kind(), p.place().cell());
                    pen = v + p.place().glyph().advance();
                }
                add(segments, spaces(2 * xu - WIDTH - pen), Kind.PLAIN, -1);
                add(segments, ch(codepoints, unknown), unknown.place().kind(), unknown.place().cell());
            }
            out.add(segments);
        }
        return out;
    }

    private static String ch(Map<String, Integer> codepoints, Placed p) {
        Integer cp = codepoints.get(p.place().glyph().key() + "|" + p.ascent());
        if (cp == null) {
            throw new IllegalStateException("no codepoint for " + p);
        }
        return Character.toString(cp);
    }

    private static void add(List<Segment> segments, String text, Kind kind, int cell) {
        if (text.isEmpty()) {
            return;
        }
        if (!segments.isEmpty()) {
            Segment last = segments.getLast();
            if (last.kind() == kind && last.cell() == cell && kind != Kind.CAPTION) {
                segments.set(segments.size() - 1, new Segment(last.text() + text, kind, cell));
                return;
            }
        }
        segments.add(new Segment(text, kind, cell));
    }

    /** Space glyphs moving the pen by {@code delta} (largest first, as GuiLayout.spaces). */
    static String spaces(int delta) {
        StringBuilder out = new StringBuilder();
        int first = delta < 0 ? NEG_SPACE : POS_SPACE;
        int rest = Math.abs(delta);
        for (int bit = SPACE_BITS - 1; bit >= 0; bit--) {
            while (rest >= 1 << bit) {
                out.appendCodePoint(first + bit);
                rest -= 1 << bit;
            }
        }
        return out.toString();
    }

    /** The advance of a space char, or null for other chars. */
    static Integer spaceAdvance(int cp) {
        if (cp >= NEG_SPACE && cp < NEG_SPACE + SPACE_BITS) {
            return -(1 << (cp - NEG_SPACE));
        }
        if (cp >= POS_SPACE && cp < POS_SPACE + SPACE_BITS) {
            return 1 << (cp - POS_SPACE);
        }
        return null;
    }

    // ---- font ------------------------------------------------------------------------------------------------------

    /** Writes the font: the spaces, then one bitmap provider per texture grid, height and ascent. */
    private static int writeFont(PackTree tree, Map<String, Placed> distinct, Map<String, Integer> codepoints) {
        JsonArray providers = new JsonArray();
        JsonObject advances = new JsonObject();
        for (int i = 0; i < SPACE_BITS; i++) {
            advances.add(Character.toString(NEG_SPACE + i), new JsonPrimitive(-(1 << i)));
            advances.add(Character.toString(POS_SPACE + i), new JsonPrimitive(1 << i));
        }
        JsonObject space = new JsonObject();
        space.addProperty("type", "space");
        space.add("advances", advances);
        providers.add(space);
        // group the glyphs by texture grid, height and ascent
        Map<String, int[][]> grids = new TreeMap<>();
        Map<String, Placed> first = new TreeMap<>();
        for (Map.Entry<String, Placed> e : distinct.entrySet()) {
            Glyph g = e.getValue().place().glyph();
            String provider = g.texture() + "|" + g.cols() + "x" + g.rows() + "|" + g.height() + "|"
                    + e.getValue().ascent();
            int[][] grid = grids.computeIfAbsent(provider, k -> new int[g.rows()][g.cols()]);
            grid[g.row()][g.col()] = codepoints.get(e.getKey());
            first.putIfAbsent(provider, e.getValue());
        }
        for (Map.Entry<String, int[][]> e : grids.entrySet()) {
            Placed p = first.get(e.getKey());
            Glyph g = p.place().glyph();
            JsonArray chars = new JsonArray();
            for (int[] row : e.getValue()) {
                StringBuilder s = new StringBuilder();
                for (int cp : row) {
                    s.appendCodePoint(cp);
                }
                chars.add(s.toString());
            }
            JsonObject bitmap = new JsonObject();
            bitmap.addProperty("type", "bitmap");
            bitmap.addProperty("file", g.texture() + ".png");
            bitmap.addProperty("height", g.height());
            bitmap.addProperty("ascent", p.ascent());
            bitmap.add("chars", chars);
            providers.add(bitmap);
        }
        tree.put(FONT_PATH, Json.bytes(Json.object("providers", providers)));
        return providers.size() - 1;
    }

    // ---- art -------------------------------------------------------------------------------------------------------

    /**
     * The card: the guide's felt (from the book texture's left page) in a 4 px frame of the book's colours, the mod's
     * nine slots, its arrow and its framed output slot, at the positions of {@code CasinoGuideScreen}'s recipe.
     */
    static BufferedImage cardArt(ModJar jar) {
        BufferedImage book = Images.read(jar.bytes(BOOK_PNG));
        BufferedImage out = Images.crop(book, 30, 40, CARD_W, CARD_H);
        for (int i = 0; i < CARD_FRAME.length; i++) {
            outline(out, i, i, CARD_W - i, CARD_H - i, CARD_FRAME[i]);
        }
        int x0 = INSET;
        int y0 = INSET;
        for (int i = 0; i < 9; i++) {
            slot(out, x0 + 18 * (i % 3), y0 + 18 * (i / 3), 18);
        }
        for (int y = 0; y < 15; y++) {
            for (int x = 0; x < 22; x++) {
                Images.blend(out, x0 + 60 + x, y0 + 20 + y, book.getRGB(368 + x, y));
            }
        }
        slot(out, x0 + 86, y0 + 14, 26);
        outline(out, x0 + 85, y0 + 13, x0 + 113, y0 + 41, FRAME);
        return out;
    }

    /** {@code CasinoGuideScreen.slot}: dark rim, light bottom and right edge, shaded top and left inside. */
    private static void slot(BufferedImage img, int x, int y, int size) {
        fill(img, x, y, x + size, y + size, SLOT_OUTER);
        fill(img, x + 1, y + 1, x + size - 1, y + size - 1, SLOT_INNER);
        fill(img, x + 1, y + size - 1, x + size, y + size, SLOT_LIGHT);
        fill(img, x + size - 1, y + 1, x + size, y + size, SLOT_LIGHT);
        fill(img, x + 1, y + 1, x + size - 1, y + 2, SLOT_SHADE);
        fill(img, x + 1, y + 2, x + 2, y + size - 1, SLOT_SHADE);
    }

    private static void fill(BufferedImage img, int x0, int y0, int x1, int y1, int argb) {
        Images.fill(img, x0, y0, x1 - x0, y1 - y0, argb);
    }

    private static void outline(BufferedImage img, int x0, int y0, int x1, int y1, int argb) {
        fill(img, x0, y0, x1, y0 + 1, argb);
        fill(img, x0, y1 - 1, x1, y1, argb);
        fill(img, x0, y0 + 1, x0 + 1, y1 - 1, argb);
        fill(img, x1 - 1, y0 + 1, x1, y1 - 1, argb);
    }

    /**
     * For an item without a flat picture: a gold "?" in the vanilla font's shape on a tile of the slot's shade, opaque
     * to its right edge (its glyph is 17 px wide, as {@link GuideIcons} counts it). Only shipped when used.
     */
    static BufferedImage fallbackArt(TextGlyphs text) {
        BufferedImage out = Images.blank(GuideIcons.ICON, GuideIcons.ICON);
        Images.fill(out, 0, 0, GuideIcons.ICON, GuideIcons.ICON, SLOT_SHADE);
        text.draw(out, "?", 5, 4, 0xFFF2C65A, true);
        return out;
    }
}
