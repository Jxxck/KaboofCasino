package de.tigges.kaboofcasino.packtool;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The guide's recipe pictures, checked against a model of the client's text layout ({@code FocusableTextWidget}:
 * centred lines of 9 px, glyph top = line y + 7 - ascent, bitmap advance = actual width x scale + 1): every icon in
 * its slot for any width the client computes for a vanilla texture, the card centred and inside the text box, no line
 * that could wrap, the count right-aligned like an item's, shapeless ingredients from the top left, tags as their
 * first member; and the pack side: every texture is ours or a known vanilla one, nothing of Mojang's in the pack,
 * byte-identical builds, a small size.
 */
class GuidePicturesTest {

    private static PackTool.Result first;
    private static Map<String, byte[]> pack;
    private static JsonObject manifest;
    private static JsonObject book;
    private static ModJar jar;
    /** codepoint -> glyph of the recipe font. */
    private static final Map<Integer, FontGlyph> font = new HashMap<>();

    /** A bitmap glyph: provider texture, grid cell, drawn size, ascent and the advance (-1: the client's). */
    record FontGlyph(String texture, int cols, int rows, int col, int row, int height, int ascent, int width,
                     int advance) {
    }

    /** One drawn glyph of a simulated line: segment kind and cell, picture texture and position. */
    record Drawn(int line, String kind, int cell, FontGlyph glyph, int x, int y) {
    }

    private static Path modJar() {
        return Path.of(System.getProperty("kaboofcasino.modJar"));
    }

    @BeforeAll
    static void build() throws IOException {
        String sha1 = System.getProperty("kaboofcasino.modJarSha1");
        first = PackTool.build(modJar(), sha1, Path.of(System.getProperty("kaboofcasino.packSrc")));
        jar = ModJar.open(modJar(), sha1);
        pack = new TreeMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(first.zip()))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                pack.put(e.getName(), zip.readAllBytes());
            }
        }
        manifest = Json.parse(first.pictures().manifest());
        book = Json.parse(jar.bytes(GuideData.BOOK));
        JsonObject json = Json.parse(pack.get(GuidePictures.FONT_PATH));
        for (JsonElement p : json.getAsJsonArray("providers")) {
            JsonObject provider = p.getAsJsonObject();
            if (!provider.get("type").getAsString().equals("bitmap")) {
                continue;
            }
            String file = provider.get("file").getAsString();
            String texture = file.substring(0, file.length() - ".png".length());
            JsonArray chars = provider.getAsJsonArray("chars");
            int rows = chars.size();
            for (int r = 0; r < rows; r++) {
                int[] cps = chars.get(r).getAsString().codePoints().toArray();
                for (int c = 0; c < cps.length; c++) {
                    if (cps[c] != 0) {
                        int height = provider.get("height").getAsInt();
                        int ascent = provider.get("ascent").getAsInt();
                        int[] size = cellSize(texture, cps.length, rows, height);
                        int advance = advance(texture, cps.length, rows, c, r, height);
                        assertFalse(font.containsKey(cps[c]), "U+" + Integer.toHexString(cps[c]) + " twice");
                        font.put(cps[c], new FontGlyph(texture, cps.length, rows, c, r, height, ascent, size[0],
                                advance));
                    }
                }
            }
        }
    }

    /** Drawn width and height of a cell; a vanilla texture is 16 x 16 on a vanilla client. */
    private static int[] cellSize(String texture, int cols, int rows, int height) {
        int w = texture.startsWith("minecraft:") ? 16 : image(texture).getWidth();
        int h = texture.startsWith("minecraft:") ? 16 : image(texture).getHeight();
        double scale = (double) height / (h / rows);
        return new int[] {(int) Math.round(w / cols * scale), height};
    }

    /** The client's advance for a pack texture; for a vanilla one 17 if the guide counts on an opaque face, else -1. */
    private static int advance(String texture, int cols, int rows, int col, int row, int height) {
        if (texture.startsWith("minecraft:")) {
            boolean opaque = GuideIcons.VANILLA.values().stream().anyMatch(v -> v.texture().equals(texture)
                    && (v.shape() == GuideIcons.Shape.BLOCK || v.shape() == GuideIcons.Shape.SLAB));
            return opaque ? 17 : -1;
        }
        BufferedImage image = image(texture);
        int cw = image.getWidth() / cols;
        int ch = image.getHeight() / rows;
        int actual = Images.actualGlyphWidth(image, col * cw, row * ch, cw, ch);
        return (int) (0.5 + actual * ((double) height / ch)) + 1;
    }

    private static BufferedImage image(String texture) {
        byte[] png = pack.get(GuideIcons.path(texture, "textures", ".png"));
        assertNotNull(png, "the font uses " + texture + ", which is not in the pack");
        return Images.read(png);
    }

    /**
     * Lays out the lines of one picture like the client: each line centred on x = 0 by its total advance (Java's
     * division, as {@code TextAlignment.CENTER}), unknown advances from {@code widths}.
     */
    private static List<Drawn> simulate(JsonArray lines, Map<String, Integer> widths, int[] running) {
        List<Drawn> out = new ArrayList<>();
        for (int l = 0; l < lines.size(); l++) {
            List<int[]> glyphs = new ArrayList<>(); // cp, segment index
            List<JsonObject> segments = new ArrayList<>();
            for (JsonElement s : lines.get(l).getAsJsonArray()) {
                JsonObject seg = s.getAsJsonObject();
                segments.add(seg);
                if (seg.has("t")) {
                    for (int cp : seg.get("t").getAsString().codePoints().toArray()) {
                        glyphs.add(new int[] {cp, segments.size() - 1});
                    }
                }
            }
            int total = 0;
            int max = Integer.MIN_VALUE;
            for (int[] g : glyphs) {
                total += advanceOf(g[0], widths);
                max = Math.max(max, total);
            }
            running[0] = Math.max(running[0], max);
            int pen = -(total / 2);
            for (int[] g : glyphs) {
                Integer space = GuidePictures.spaceAdvance(g[0]);
                if (space == null) {
                    FontGlyph f = font.get(g[0]);
                    assertNotNull(f, "U+" + Integer.toHexString(g[0]) + " is not in the font");
                    JsonObject seg = segments.get(g[1]);
                    String kind = seg.has("cell") ? "cell" : seg.has("result") ? "result" : seg.has("hint")
                            ? "hint" : seg.has("count") ? "count" : "plain";
                    out.add(new Drawn(l, kind, seg.has("cell") ? seg.get("cell").getAsInt() : -1, f, pen,
                            9 * l + 7 - f.ascent()));
                }
                pen += advanceOf(g[0], widths);
            }
        }
        return out;
    }

    private static int advanceOf(int cp, Map<String, Integer> widths) {
        Integer space = GuidePictures.spaceAdvance(cp);
        if (space != null) {
            return space;
        }
        FontGlyph f = font.get(cp);
        if (f == null) {
            throw new AssertionError("U+" + Integer.toHexString(cp) + " is not in the font");
        }
        return f.advance() >= 0 ? f.advance() : widths.get(f.texture() + "|" + f.row() + "," + f.col());
    }

    /** Random client widths for every vanilla glyph whose width the pack cannot know. */
    private static Map<String, Integer> clientWidths(Random random) {
        Map<String, Integer> out = new HashMap<>();
        for (FontGlyph f : font.values()) {
            if (f.advance() < 0) {
                out.putIfAbsent(f.texture() + "|" + f.row() + "," + f.col(), 1 + random.nextInt(17));
            }
        }
        return out;
    }

    private static List<JsonObject> recipeWidgets() {
        List<JsonObject> out = new ArrayList<>();
        JsonArray pages = book.getAsJsonArray("pages");
        for (int p = 0; p < pages.size(); p++) {
            JsonArray widgets = pages.get(p).getAsJsonObject().getAsJsonArray("widgets");
            for (int i = 0; i < widgets.size(); i++) {
                JsonObject w = widgets.get(i).getAsJsonObject();
                if (w.get("kind").getAsString().equals("recipe")) {
                    JsonObject copy = w.deepCopy();
                    copy.addProperty("key", (p + 1) + ":" + i);
                    out.add(copy);
                }
            }
        }
        return out;
    }

    @Test
    void everyIconLandsInItsSlotForAnyClientWidth() {
        Random random = new Random(26_3);
        List<JsonObject> recipes = recipeWidgets();
        assertEquals(89, recipes.size());
        assertEquals(89, manifest.getAsJsonObject("pictures").size());
        for (int trial = 0; trial < 12; trial++) {
            Map<String, Integer> widths = trial == 0 ? clientWidths(new Random(0)) : clientWidths(random);
            if (trial == 1) {
                widths.replaceAll((k, v) -> 17); // the widest any 16 px texture can be
            } else if (trial == 2) {
                widths.replaceAll((k, v) -> 1); // an empty texture
            }
            for (JsonObject r : recipes) {
                String key = r.get("key").getAsString();
                JsonArray lines = manifest.getAsJsonObject("pictures").getAsJsonArray(key);
                List<Drawn> drawn = simulate(lines, widths, new int[] {Integer.MIN_VALUE});
                List<Drawn> visible = drawn.stream().filter(d -> d.x() > -1000).toList();
                Drawn card = only(visible.stream().filter(d -> d.glyph().texture().equals(GuidePictures.CARD_TEXTURE))
                        .toList(), key + " card");
                // the card is centred (128 px on a centred 130 px line) and the first glyph drawn
                assertEquals(-GuidePictures.CARD_W / 2, card.x(), key);
                assertEquals(card, visible.getFirst(), key + ": the card is drawn under everything");
                int x0 = card.x() + GuidePictures.INSET;
                int y0 = card.y() + GuidePictures.INSET;
                // the nine slots: the recipe's cells, or the shapeless ingredients from the top left
                List<String> cells = new ArrayList<>();
                for (JsonElement c : r.getAsJsonArray("cells")) {
                    cells.add(c.isJsonNull() ? null : c.getAsString());
                }
                boolean shapeless = r.has("shapeless") && r.get("shapeless").getAsBoolean();
                List<Integer> slotOf = new ArrayList<>(Arrays.asList(new Integer[9]));
                int next = 0;
                for (int i = 0; i < 9; i++) {
                    if (cells.get(i) != null) {
                        slotOf.set(i, shapeless ? next++ : i);
                    }
                }
                Set<Integer> seen = new TreeSet<>();
                for (Drawn d : visible) {
                    switch (d.kind()) {
                        case "cell" -> {
                            Integer slot = slotOf.get(d.cell());
                            assertNotNull(slot, key + " draws the empty cell " + d.cell());
                            int top = y0 + 1 + 18 * (slot / 3) + (d.glyph().height() == 8 && d.glyph().row() % 2 == 1
                                    ? 8 : 0);
                            assertEquals(x0 + 1 + 18 * (slot % 3), d.x(), key + " cell " + d.cell() + " x");
                            assertEquals(top, d.y(), key + " cell " + d.cell() + " y");
                            assertTrue(expectedTextures(cells.get(d.cell())).contains(d.glyph().texture()),
                                    key + " cell " + d.cell() + " shows " + d.glyph().texture());
                            seen.add(d.cell());
                        }
                        case "result" -> {
                            assertEquals(x0 + 91, d.x(), key + " result x");
                            assertEquals(y0 + 19 + (d.glyph().height() == 8 && d.glyph().row() % 2 == 1 ? 8 : 0),
                                    d.y(), key + " result y");
                        }
                        case "hint" -> {
                            assertTrue(shapeless, key);
                            assertEquals(x0 + 62, d.x(), key);
                            assertEquals(y0 + 4, d.y(), key);
                        }
                        case "count", "plain" -> {
                        }
                        default -> throw new AssertionError(d.kind());
                    }
                }
                Set<Integer> filled = new TreeSet<>();
                for (int i = 0; i < 9; i++) {
                    if (cells.get(i) != null) {
                        filled.add(i);
                    }
                }
                assertEquals(filled, seen, key);
                assertEquals(shapeless, visible.stream().anyMatch(d -> d.kind().equals("hint")), key);
                // the count: the result's count, right-aligned 17 px right of the result's left edge, 9 px down
                // (item decorations), the sheet's digits start 4 px into their cell
                int count = r.has("count") ? r.get("count").getAsInt() : 1;
                List<Drawn> digits = visible.stream().filter(d -> d.kind().equals("count")).toList();
                assertEquals(count > 1 ? Integer.toString(count).length() : 0, digits.size(), key);
                if (count > 1) {
                    StringBuilder text = new StringBuilder();
                    int right = 0;
                    for (Drawn d : digits) {
                        text.append(sheetChar(d.glyph()));
                        assertEquals(y0 + 19 + 9 - 4, d.y(), key + " count y");
                        right = d.x() + d.glyph().advance();
                    }
                    assertEquals(Integer.toString(count), text.toString(), key);
                    assertEquals(x0 + 91 + 17, right, key + " count right edge");
                    Drawn result = visible.stream().filter(d -> d.kind().equals("result")).reduce((a, b) -> b)
                            .orElseThrow();
                    assertTrue(visible.indexOf(digits.getFirst()) > visible.indexOf(result) || digits.getFirst().line()
                            > result.line(), key + ": the count is drawn over the result");
                }
                // every hidden copy is far off screen and belongs to a visible glyph of its line
                for (Drawn d : drawn) {
                    if (d.x() <= -1000) {
                        assertTrue(d.x() < -GuidePictures.DECOY / 2, key);
                        assertEquals(-1, d.glyph().advance(), key + ": only glyphs of unknown width are hidden");
                        assertEquals("plain", d.kind(), key + ": a hidden copy has no tooltip");
                    }
                }
            }
        }
    }

    private static Drawn only(List<Drawn> list, String what) {
        assertEquals(1, list.size(), what + " " + list);
        return list.getFirst();
    }

    /** The textures a cell may show: a tag its first member's, a vanilla item the table's, else its icon's layers. */
    private static Set<String> expectedTextures(String ref) {
        String id = GuideIcons.shown(ref);
        if (id.startsWith("minecraft:")) {
            return Set.of(GuideIcons.VANILLA.get(id).texture());
        }
        Set<String> out = new TreeSet<>();
        icons().icon(id).layers().forEach(l -> out.add(l.texture()));
        return out;
    }

    private static GuideIcons iconCache;

    private static GuideIcons icons() {
        if (iconCache == null) {
            iconCache = new GuideIcons(treeOf(pack));
        }
        return iconCache;
    }

    private static PackTree treeCache;

    private static PackTree treeOf(Map<String, byte[]> files) {
        if (treeCache == null) {
            PackTree tree = new PackTree();
            files.forEach(tree::put);
            treeCache = tree;
        }
        return treeCache;
    }

    private static TextGlyphs textCache;

    /** The character of a count glyph: its cell in the text sheet. */
    private static char sheetChar(FontGlyph g) {
        if (textCache == null) {
            textCache = TextGlyphs.load(Path.of(System.getProperty("kaboofcasino.packSrc")).resolve("vanilla"));
        }
        TextGlyphs text = textCache;
        assertEquals(GuidePictures.DIGITS_TEXTURE, g.texture());
        return text.charRows().get(g.row()).getAsString().charAt(g.col());
    }

    @Test
    void tagsShowTheirFirstMember() {
        assertEquals("minecraft:block/oak_planks", GuideIcons.VANILLA.get(GuideIcons.shown("#minecraft:planks"))
                .texture());
        GuideIcons.Vanilla slab = GuideIcons.VANILLA.get(GuideIcons.shown("#minecraft:wooden_slabs"));
        assertEquals("minecraft:block/oak_planks", slab.texture());
        assertEquals(GuideIcons.Shape.SLAB, slab.shape());
        assertEquals("minecraft:item/oak_sign", GuideIcons.VANILLA.get(GuideIcons.shown("#minecraft:signs"))
                .texture());
        // a slab is the lower half of the face, drawn in the slot's lower half
        GuideIcons.Icon icon = icons().icon("minecraft:oak_slab");
        assertEquals(List.of(new GuideIcons.Layer("minecraft:block/oak_planks", 1, 2, 0, 1, 8, 8, 17)),
                icon.layers());
        // casino stairs: the lower half and the upper left quarter of their side
        GuideIcons.Icon stairs = icons().icon("casinodeco:emerald_velvet_stairs");
        assertEquals(2, stairs.layers().size());
        assertEquals("casinodeco:block/emerald_velvet", stairs.layers().get(0).texture());
        assertEquals(List.of(2, 1, 8), List.of(stairs.layers().get(0).rows(), stairs.layers().get(0).row(),
                stairs.layers().get(0).dy()));
        assertEquals(List.of(2, 2, 0, 0), List.of(stairs.layers().get(1).cols(), stairs.layers().get(1).rows(),
                stairs.layers().get(1).col(), stairs.layers().get(1).dy()));
    }

    @Test
    void shapelessIngredientsFillFromTheTopLeft() {
        List<String> cells = Arrays.asList(null, "a", null, "b", null, null, null, null, "c");
        assertEquals(List.of(1, 3, 8, -1, -1, -1, -1, -1, -1), GuidePictures.slots(cells, true));
        assertEquals(List.of(-1, 1, -1, 3, -1, -1, -1, -1, 8), GuidePictures.slots(cells, false));
    }

    @Test
    void noLineWrapsAndTheCardIsInsideTheTextBox() {
        int widest = 0;
        for (Map.Entry<String, JsonElement> e : manifest.getAsJsonObject("pictures").entrySet()) {
            JsonArray lines = e.getValue().getAsJsonArray();
            int[] running = {Integer.MIN_VALUE};
            Map<String, Integer> wide = clientWidths(new Random(1));
            wide.replaceAll((k, v) -> 17);
            List<Drawn> drawn = simulate(lines, wide, running);
            int lineWidth = 0;
            int captions = 0;
            for (JsonElement l : lines) {
                int total = 0;
                boolean caption = false;
                for (JsonElement s : l.getAsJsonArray()) {
                    JsonObject seg = s.getAsJsonObject();
                    if (seg.has("caption")) {
                        caption = true;
                        captions++;
                    } else {
                        for (int cp : seg.get("t").getAsString().codePoints().toArray()) {
                            total += advanceOf(cp, wide);
                        }
                    }
                }
                if (caption) {
                    // the caption's runs: -8192, caption, -8192; any caption under 16000 px keeps the line negative
                    assertEquals(-2 * GuidePictures.DECOY, total, e.getKey());
                } else {
                    lineWidth = Math.max(lineWidth, total);
                }
            }
            assertEquals(1, captions, e.getKey());
            assertEquals(GuidePictures.WIDTH, lineWidth, e.getKey() + ": the known lines set the label width");
            // the line break finder breaks only when the running width exceeds the widest line
            assertTrue(running[0] <= GuidePictures.WIDTH, e.getKey() + " running width " + running[0]);
            widest = Math.max(widest, running[0]);
            // the text box: 9 px per line plus 4 px padding above and below; line 0 is 4 px below its top
            int box = 9 * lines.size() + 8;
            Drawn card = drawn.stream().filter(d -> d.glyph().texture().equals(GuidePictures.CARD_TEXTURE))
                    .findFirst().orElseThrow();
            assertTrue(card.y() + 4 >= 0 && card.y() + 4 + GuidePictures.CARD_H <= box, e.getKey() + " card y "
                    + card.y() + " box " + box);
            for (Drawn d : drawn) {
                if (d.x() > -1000 && !d.kind().equals("plain")) {
                    assertTrue(d.y() + 4 >= 0 && d.y() + 4 + d.glyph().height() <= box, e.getKey() + " " + d);
                }
            }
        }
        assertEquals(GuidePictures.WIDTH, widest);
    }

    /** Every texture of the font is ours (in the pack) or a vanilla one the guide knows; no vanilla file is shipped. */
    @Test
    void texturesAreOursOrVanillaResourceLocations() {
        Set<String> vanilla = new TreeSet<>();
        for (FontGlyph g : font.values()) {
            if (g.texture().startsWith("minecraft:")) {
                vanilla.add(g.texture());
            } else {
                assertTrue(pack.containsKey(GuideIcons.path(g.texture(), "textures", ".png")), g.texture());
            }
            assertTrue(g.ascent() <= g.height(), g + ": the client rejects an ascent above the height");
        }
        Set<String> table = new TreeSet<>();
        GuideIcons.VANILLA.values().forEach(v -> table.add(v.texture()));
        assertEquals(table, vanilla, "every vanilla texture of the table is used, no other");
        assertEquals(List.copyOf(vanilla), first.pictures().vanillaTextures());
        assertTrue(pack.keySet().stream().noneMatch(p -> p.startsWith("assets/minecraft/textures/")),
                "no Mojang texture in the pack");
        // every vanilla item the guide's recipes show is in the table, and nothing else
        Set<String> shown = new TreeSet<>();
        for (JsonObject r : recipeWidgets()) {
            for (JsonElement c : r.getAsJsonArray("cells")) {
                if (!c.isJsonNull() && GuideIcons.shown(c.getAsString()).startsWith("minecraft:")) {
                    shown.add(GuideIcons.shown(c.getAsString()));
                }
            }
        }
        assertEquals(new TreeSet<>(GuideIcons.VANILLA.keySet()), shown);
        assertEquals(Map.of(), first.pictures().fallbacks(), "every casino item has a flat picture");
        // our art fits the client's font pages; the fallback tile is only shipped when used, and is as wide as counted
        for (String art : List.of(GuidePictures.CARD_TEXTURE, GuidePictures.HINT_TEXTURE)) {
            BufferedImage image = image(art);
            assertTrue(image.getWidth() <= 256 && image.getHeight() <= 256, art);
        }
        assertFalse(pack.containsKey(GuideIcons.path(GuideIcons.FALLBACK_TEXTURE, "textures", ".png")));
        BufferedImage fallback = GuidePictures.fallbackArt(TextGlyphs.load(
                Path.of(System.getProperty("kaboofcasino.packSrc")).resolve("vanilla")));
        assertEquals(GuideIcons.ICON + 1, Images.actualGlyphWidth(fallback, 0, 0, 16, 16) + 1);
        assertEquals(GuidePictures.CARD_W, image(GuidePictures.CARD_TEXTURE).getWidth());
        assertEquals(GuidePictures.CARD_H, image(GuidePictures.CARD_TEXTURE).getHeight());
    }

    @Test
    void sameJarSameBytesAndSmall() {
        PackTool.Result second = PackTool.build(modJar(), System.getProperty("kaboofcasino.modJarSha1"),
                Path.of(System.getProperty("kaboofcasino.packSrc")));
        assertArrayEquals(first.pictures().manifest(), second.pictures().manifest());
        assertArrayEquals(first.zip(), second.zip());
        int added = 0;
        for (String path : List.of(GuidePictures.FONT_PATH, GuideIcons.path(GuidePictures.CARD_TEXTURE, "textures",
                ".png"), GuideIcons.path(GuidePictures.HINT_TEXTURE, "textures", ".png"))) {
            assertTrue(pack.containsKey(path), path);
            added += pack.get(path).length;
        }
        assertTrue(added < 64 * 1024, "the pictures add " + added + " bytes to the pack");
        assertTrue(first.pictures().manifest().length < 96 * 1024, "manifest " + first.pictures().manifest().length);
        assertTrue(first.pictures().providers() < 400, "providers " + first.pictures().providers());
    }

    /**
     * With {@code -Pcasino.clientJar=<client.jar>}: every vanilla texture exists in that client, is 16 x 16 and has
     * one frame, and the faces the layout counts as opaque are opaque in their right column.
     */
    @Test
    void vanillaTexturesMatchTheClient() throws IOException {
        String client = System.getProperty("kaboofcasino.clientJar");
        assumeTrue(client != null && !client.isBlank(), "no client jar given (-Pcasino.clientJar)");
        try (ZipFile zip = new ZipFile(client)) {
            for (Map.Entry<String, GuideIcons.Vanilla> e : GuideIcons.VANILLA.entrySet()) {
                String path = GuideIcons.path(e.getValue().texture(), "textures", ".png");
                ZipEntry entry = zip.getEntry(path);
                assertNotNull(entry, e.getKey() + ": " + path);
                BufferedImage image;
                try (InputStream in = zip.getInputStream(entry)) {
                    image = Images.read(in.readAllBytes());
                }
                assertEquals(16, image.getWidth(), path);
                assertEquals(16, image.getHeight(), path + " (an animation strip would be squeezed)");
                if (e.getValue().shape() == GuideIcons.Shape.BLOCK || e.getValue().shape() == GuideIcons.Shape.SLAB) {
                    int from = e.getValue().shape() == GuideIcons.Shape.SLAB ? 8 : 0;
                    boolean any = false;
                    for (int y = from; y < 16; y++) {
                        any |= (image.getRGB(15, y) >>> 24) != 0;
                    }
                    assertTrue(any, path + " has no opaque pixel in its right column: its glyph is not 17 px wide");
                }
            }
        }
    }
}
