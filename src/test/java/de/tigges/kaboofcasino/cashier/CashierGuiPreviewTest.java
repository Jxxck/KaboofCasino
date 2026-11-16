package de.tigges.kaboofcasino.cashier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.gui.GuiLayout;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Draws the cashier menu like the 26.3 client from the embedded pack onto the common scaled screens (the derby
 * preview's renderer): title glyphs, pen advances, spaces and style shadows; slot items through the pack's item
 * definitions and models (number displays, marker, buttons), vanilla items and chips as marked squares with their
 * count. Writes {@code build/reports/gui/<language>/cashier-*.png} and asserts that the art and the receipt are on screen at all
 * three sizes, no picture is clipped and no two overlap (except the target's frame around its chip).
 */
class CashierGuiPreviewTest {

    /** Preview pixels per GUI pixel. */
    static final int S = 2;

    record GlyphImage(BufferedImage image, int x, int y, int w, int h, int ascent, int advance, double scale) {
    }

    /** A rectangle in window coordinates that must be fully on screen. */
    record Important(String name, int x0, int y0, int x1, int y1) {
    }

    /** A picture an item drew: its kind for the overlap rules and its window rectangle. */
    record Drawn(int slot, String kind, int x0, int y0, int x1, int y1) {
        boolean overlaps(Drawn o) {
            return x0 < o.x1 && o.x0 < x1 && y0 < o.y1 && o.y0 < y1;
        }
    }

    static final int[][] SIZES = {{480, 270}, {456, 256}, {427, 240}};

    static final List<Important> IMPORTANT = List.of(
            new Important("tabs and grid", 7, 17, 169, 125),
            new Important("brand", 216, -8, 281, 0),
            new Important("receipt", 200, 4, 297, 130),
            new Important("inventory", 0, 128, 176, 222));

    private final Map<String, byte[]> pack = new HashMap<>();
    private final Map<String, Map<Integer, GlyphImage>> fonts = new HashMap<>();
    private GuiLayout layout;
    private Function<Msg, String> raw;

    @BeforeEach
    void load() throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(de.tigges.kaboofcasino.config.Language.current().packResource("KaboofCasino-pack.zip"));
             ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                pack.put(e.getName(), zip.readAllBytes());
            }
        }
        layout = GuiLayout.load(getClass().getClassLoader().getResourceAsStream(de.tigges.kaboofcasino.config.Language.current().packResource("gui-layout.json")));
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(
                getClass().getClassLoader().getResourceAsStream(de.tigges.kaboofcasino.config.Language.current().messagesFile()), StandardCharsets.UTF_8));
        raw = m -> yaml.getString(m.key());
    }

    // ---- the states (what the menus put into their slots) ----------------------------------------------------------

    private static Map<Integer, Item> common() {
        Map<Integer, Item> out = new HashMap<>();
        for (int slot = 0; slot < 9; slot++) {
            out.put(slot, Item.model("kaboofcasino:gui/blank"));
        }
        return out;
    }

    private static void commit(Map<Integer, Item> items, String model, boolean on) {
        for (int slot : CashierView.COMMIT) {
            items.put(slot, Item.model(slot == CashierView.COMMIT_ART ? "kaboofcasino:gui/cashier/" + model
                    + (on ? "_on" : "_off") : "kaboofcasino:gui/blank"));
        }
    }

    private static void chipRow(Map<Integer, Item> items, int first, long[] row) {
        for (int k = 0; k < row.length; k++) {
            if (row[k] > 0) {
                items.put(first + k, Item.stack(de.tigges.kaboofcasino.wager.Chips.key(
                        CashierView.ROW_DENOMINATIONS[k]), (int) Math.min(99, row[k])));
            }
        }
    }

    static Map<Integer, Item> exchange(boolean selected) {
        Map<Integer, Item> items = common();
        long value = 0;
        if (selected) {
            items.put(9, Item.stack("minecraft:diamond", 40));
            items.put(10, Item.stack("minecraft:coal", 7));
            value = 40 * 32 + 7;
            chipRow(items, CashierView.PREVIEW_FIRST, CashierView.breakdownRow(value));
        }
        items.put(CashierView.RATES_BOOK, Item.stack("minecraft:book", 1));
        items.put(CashierView.CLEAR, Item.model("kaboofcasino:gui/cashier/clear"));
        commit(items, "exchange", selected);
        items.put(CashierView.NUM_A, new Item("kaboofcasino:gui/cashier/num_a", null, 1, CashierView.digits(value)));
        return items;
    }

    static Map<Integer, Item> change() {
        Map<Integer, Item> items = common();
        chipRow(items, CashierView.SELECTED_FIRST, new long[] {0, 0, 1, 0, 0, 3});
        chipRow(items, CashierView.TARGET_FIRST, new long[] {1, 1, 1, 1, 1, 1});
        items.put(CashierView.MARKER, new Item("kaboofcasino:gui/cashier/marker", null, 1, List.of("3")));
        chipRow(items, CashierView.RESULT_FIRST, CashierView.row(CashierMath.change(103, 25)));
        items.put(CashierView.CLEAR, Item.model("kaboofcasino:gui/cashier/clear"));
        commit(items, "exchange", true);
        items.put(CashierView.NUM_A, new Item("kaboofcasino:gui/cashier/num_a", null, 1, CashierView.digits(103)));
        return items;
    }

    static Map<Integer, Item> shop() {
        Map<Integer, Item> items = common();
        for (int k = 0; k < CashierView.PAGE_SIZE; k++) {
            items.put(CashierView.PRIZES_FIRST + k, Item.stack("minecraft:cake", 1 + k % 3));
        }
        items.put(CashierView.PREV, Item.model("kaboofcasino:gui/cashier/prev"));
        items.put(CashierView.NEXT, Item.model("kaboofcasino:gui/cashier/next"));
        commit(items, "buy", true);
        items.put(CashierView.NUM_A, new Item("kaboofcasino:gui/cashier/num_a", null, 1,
                CashierView.digits(1_234_567)));
        items.put(CashierView.NUM_B, new Item("kaboofcasino:gui/cashier/num_b", null, 1, CashierView.digits(64)));
        return items;
    }

    @Test
    void everyTabFitsTheCommonScreens() throws IOException {
        for (int[] size : SIZES) {
            render("cashier-exchange-empty", CashierMenu.title(layout, raw, CashierView.Tab.EXCHANGE), size[0],
                    size[1], IMPORTANT, exchange(false));
            render("cashier-exchange", CashierMenu.title(layout, raw, CashierView.Tab.EXCHANGE), size[0], size[1],
                    IMPORTANT, exchange(true));
            render("cashier-change", CashierMenu.title(layout, raw, CashierView.Tab.CHANGE), size[0], size[1],
                    IMPORTANT, change());
            render("cashier-shop", CashierMenu.title(layout, raw, CashierView.Tab.SHOP), size[0], size[1],
                    IMPORTANT, shop());
        }
    }

    @Test
    void theNumbersLandRightAlignedInTheReceipt() throws IOException {
        Map<Integer, Item> items = shop();
        List<Drawn> drawn = render("cashier-numbers", CashierMenu.title(layout, raw, CashierView.Tab.SHOP), 480, 270,
                IMPORTANT, items);
        Drawn a = drawn.stream().filter(d -> d.slot() == CashierView.NUM_A).findFirst().orElseThrow();
        Drawn b = drawn.stream().filter(d -> d.slot() == CashierView.NUM_B).findFirst().orElseThrow();
        assertEquals(293, a.x1());
        assertEquals(293, b.x1());
        assertTrue(b.y0() > a.y1(), "row B below row A");
        GuiLayout.Anchor labelA = layout.anchor("cashier_label_a");
        assertTrue(a.y0() >= labelA.y() + 8, "the number below its label");
    }

    /** A slot item: a pack model with custom model data strings, or a vanilla item. */
    record Item(String model, String vanilla, int amount, List<String> strings) {
        static Item model(String key) {
            return new Item(key, null, 1, List.of());
        }

        static Item stack(String key, int amount) {
            return new Item(null, key, amount, List.of());
        }
    }

    private JsonObject json(String path) {
        byte[] b = pack.get(path);
        assertNotNull(b, "pack lacks " + path);
        return JsonParser.parseString(new String(b, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private BufferedImage texture(String id) throws IOException {
        byte[] b = pack.get(path(id, "textures", ".png"));
        assertNotNull(b, "pack lacks texture " + id);
        return ImageIO.read(new ByteArrayInputStream(b));
    }

    private static String path(String id, String dir, String ext) {
        Key k = Key.key(id);
        return "assets/" + k.namespace() + "/" + dir + "/" + k.value() + ext;
    }

    /** Resolves an item model tree to model ids like {@code ItemModelResolver} (select on strings, composite). */
    private void resolve(JsonObject m, Item item, List<String> out) {
        String type = m.get("type").getAsString();
        switch (type) {
            case "minecraft:empty" -> {
            }
            case "minecraft:model" -> out.add(m.get("model").getAsString());
            case "minecraft:composite" -> {
                for (JsonElement e : m.getAsJsonArray("models")) {
                    resolve(e.getAsJsonObject(), item, out);
                }
            }
            case "minecraft:select" -> {
                assertEquals("minecraft:custom_model_data", m.get("property").getAsString());
                int index = m.get("index").getAsInt();
                String value = index < item.strings().size() ? item.strings().get(index) : null;
                for (JsonElement e : m.getAsJsonArray("cases")) {
                    JsonObject c = e.getAsJsonObject();
                    if (c.get("when").getAsString().equals(value)) {
                        resolve(c.getAsJsonObject("model"), item, out);
                        return;
                    }
                }
                if (m.has("fallback")) {
                    resolve(m.getAsJsonObject("fallback"), item, out);
                }
            }
            default -> throw new AssertionError("unsupported item model type " + type);
        }
    }

    /** Draws one slot item; returns the window rectangle the oversized item covers (null: nothing drawn). */
    private Drawn drawItem(BufferedImage screen, int left, int top, int slot, Item item) throws IOException {
        int sx = 8 + 18 * (slot % 9);
        int sy = 18 + 18 * (slot / 9);
        if (item.vanilla() != null) {
            if (item.vanilla().equals("minecraft:player_head")) {
                // a stand-in for the vanilla 3D head icon (only a real client shows the skin)
                fill(screen, left + sx + 3, top + sy + 1, 10, 10, 0xffc69c6d);
                fill(screen, left + sx + 5, top + sy + 4, 2, 2, 0xff3b2a1e);
                fill(screen, left + sx + 9, top + sy + 4, 2, 2, 0xff3b2a1e);
                fill(screen, left + sx + 3, top + sy + 1, 10, 3, 0xff5a3a22);
                return new Drawn(slot, "head", sx, sy, sx + 16, sy + 16);
            }
            fill(screen, left + sx + 1, top + sy + 1, 14, 14, 0xff3a7bd5);
            outline(screen, left + sx + 1, top + sy + 1, 14, 14, 0xffffffff);
            if (item.amount() != 1) {
                String count = Integer.toString(item.amount());
                drawText(screen, left + sx + 17 - width(count), top + sy + 9, count, 0xffffff, true);
            }
            return new Drawn(slot, "stake", sx, sy, sx + 16, sy + 16);
        }
        JsonObject definition = json(path(item.model(), "items", ".json"));
        boolean oversized = definition.has("oversized_in_gui") && definition.get("oversized_in_gui").getAsBoolean();
        List<String> models = new ArrayList<>();
        resolve(definition.getAsJsonObject("model"), item, models);
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        record Face(double z, BufferedImage tex, double[] uv, double x0, double y0, double x1, double y1) {
        }
        List<Face> faces = new ArrayList<>();
        for (String id : models) {
            JsonObject model = json(path(id, "models", ".json"));
            if (!model.has("elements") && model.get("parent").getAsString().equals("minecraft:item/generated")) {
                BufferedImage tex = texture(model.getAsJsonObject("textures").get("layer0").getAsString());
                blit(screen, tex, 0, 0, tex.getWidth(), tex.getHeight(), left + sx, top + sy, left + sx + 16,
                        top + sy + 16, 0xffffff);
                return new Drawn(slot, "plain", sx, sy, sx + 16, sy + 16);
            }
            assertTrue(model.has("elements"), id);
            JsonObject gui = model.getAsJsonObject("display").getAsJsonObject("gui");
            double s = gui.getAsJsonArray("scale").get(0).getAsDouble();
            double tx = gui.getAsJsonArray("translation").get(0).getAsDouble();
            double ty = gui.getAsJsonArray("translation").get(1).getAsDouble();
            assertTrue(s >= -4 && s <= 4, id + ": scale beyond the client's +-4");
            assertTrue(Math.abs(tx) <= 80 && Math.abs(ty) <= 80, id + ": translation beyond the client's +-80");
            JsonObject textures = model.getAsJsonObject("textures");
            for (JsonElement e : model.getAsJsonArray("elements")) {
                JsonObject el = e.getAsJsonObject();
                JsonArray from = el.getAsJsonArray("from");
                JsonArray to = el.getAsJsonArray("to");
                for (int i = 0; i < 3; i++) {
                    assertTrue(from.get(i).getAsDouble() >= -16 && to.get(i).getAsDouble() <= 32, id);
                }
                JsonObject face = el.getAsJsonObject("faces").getAsJsonObject("south");
                String tex = textures.get(face.get("texture").getAsString().substring(1)).getAsString();
                JsonArray uv = face.getAsJsonArray("uv");
                double x0 = sx + 8 + (from.get(0).getAsDouble() - 8) * s + tx;
                double x1 = sx + 8 + (to.get(0).getAsDouble() - 8) * s + tx;
                double y0 = sy + 8 - (to.get(1).getAsDouble() - 8) * s - ty;
                double y1 = sy + 8 - (from.get(1).getAsDouble() - 8) * s - ty;
                assertEquals(Math.rint(x0), x0, 1e-6, id + ": quad not on whole pixels");
                assertEquals(Math.rint(y0), y0, 1e-6, id + ": quad not on whole pixels");
                assertEquals(Math.rint(x1), x1, 1e-6, id + ": quad not on whole pixels");
                assertEquals(Math.rint(y1), y1, 1e-6, id + ": quad not on whole pixels");
                faces.add(new Face(from.get(2).getAsDouble(), texture(tex), new double[] {uv.get(0).getAsDouble(),
                        uv.get(1).getAsDouble(), uv.get(2).getAsDouble(), uv.get(3).getAsDouble()}, x0, y0, x1, y1));
                minX = Math.min(minX, x0);
                minY = Math.min(minY, y0);
                maxX = Math.max(maxX, x1);
                maxY = Math.max(maxY, y1);
            }
        }
        if (faces.isEmpty()) {
            return null;
        }
        assertTrue(oversized && (maxX - minX > 16 || maxY - minY > 16), item.model() + " is not drawn oversized");
        faces.sort((a, b) -> Double.compare(a.z(), b.z()));
        for (Face f : faces) {
            double tu0 = f.uv()[0] / 16 * f.tex().getWidth();
            double tv0 = f.uv()[1] / 16 * f.tex().getHeight();
            double tu1 = f.uv()[2] / 16 * f.tex().getWidth();
            double tv1 = f.uv()[3] / 16 * f.tex().getHeight();
            assertTrue(tu1 > tu0 && tv1 > tv0, item.model() + ": mirrored UVs");
            // whole texels per pixel (the UVs of padded textures are rounded to 5 decimals)
            double ratio = (tu1 - tu0) / (f.x1() - f.x0());
            assertTrue(Math.abs(ratio - Math.rint(ratio)) < 1e-4 && Math.rint(ratio) >= 1, item.model()
                    + ": not a whole number of texels per pixel: " + ratio);
            assertEquals(ratio, (tv1 - tv0) / (f.y1() - f.y0()), 1e-4, item.model() + ": texels not square");
            blit(screen, f.tex(), tu0, tv0, tu1, tv1, left + f.x0(), top + f.y0(), left + f.x1(), top + f.y1(),
                    0xffffff);
        }
        String name = item.model().substring(item.model().lastIndexOf('/') + 1);
        String kind = name.replaceAll("_.*", "");
        return new Drawn(slot, kind, (int) Math.round(minX), (int) Math.round(minY), (int) Math.round(maxX),
                (int) Math.round(maxY));
    }

    // ---- title text ------------------------------------------------------------------------------------------------

    private Map<Integer, GlyphImage> font(Key key) throws IOException {
        Map<Integer, GlyphImage> cached = fonts.get(key.asString());
        if (cached != null) {
            return cached;
        }
        Map<Integer, GlyphImage> out = new HashMap<>();
        JsonObject json = json("assets/" + key.namespace() + "/font/" + key.value() + ".json");
        for (JsonElement p : json.getAsJsonArray("providers")) {
            JsonObject provider = p.getAsJsonObject();
            if (provider.get("type").getAsString().equals("space")) {
                for (Map.Entry<String, JsonElement> a : provider.getAsJsonObject("advances").entrySet()) {
                    out.put(a.getKey().codePointAt(0), new GlyphImage(null, 0, 0, 0, 0, 0, a.getValue().getAsInt(),
                            1));
                }
                continue;
            }
            BufferedImage image = texture(provider.get("file").getAsString().replace(".png", ""));
            JsonArray rows = provider.getAsJsonArray("chars");
            int cols = rows.get(0).getAsString().codePointCount(0, rows.get(0).getAsString().length());
            int cw = image.getWidth() / cols;
            int ch = image.getHeight() / rows.size();
            double scale = provider.get("height").getAsDouble() / ch;
            for (int r = 0; r < rows.size(); r++) {
                int[] cps = rows.get(r).getAsString().codePoints().toArray();
                for (int c = 0; c < cps.length; c++) {
                    if (cps[c] == 0 || out.containsKey(cps[c])) {
                        continue;
                    }
                    int actual = 0;
                    for (int w = cw - 1; w >= 0 && actual == 0; w--) {
                        for (int y = 0; y < ch; y++) {
                            if ((image.getRGB(c * cw + w, r * ch + y) >>> 24) != 0) {
                                actual = w + 1;
                                break;
                            }
                        }
                    }
                    out.put(cps[c], new GlyphImage(image, c * cw, r * ch, cw, ch, provider.get("ascent").getAsInt(),
                            (int) (0.5 + actual * scale) + 1, scale));
                }
            }
        }
        fonts.put(key.asString(), out);
        return out;
    }

    /** Count decorations: text with its cap tops at screen y {@code top} (GUI px) in any text font of the pack. */
    private void drawText(BufferedImage screen, int x, int top, String text, int rgb, boolean shadow)
            throws IOException {
        Map<Integer, GlyphImage> f = font(Key.key(layout.textFont(layout.anchor("cashier_head").y()).asString()));
        int pen = x;
        for (int cp : text.codePoints().toArray()) {
            GlyphImage g = f.get(cp);
            // the text sheets keep every glyph's cap top at row 4 of its cell
            glyph(screen, g, pen, top - 4, rgb, false, shadow ? (rgb & 0xFCFCFC) >> 2 | 0xff000000 : 0);
            pen += g.advance();
        }
    }

    private int width(String text) throws IOException {
        Map<Integer, GlyphImage> f = font(Key.key(layout.textFont(layout.anchor("cashier_head").y()).asString()));
        int w = 0;
        for (int cp : text.codePoints().toArray()) {
            w += f.get(cp).advance();
        }
        return w;
    }

    /** A glyph whose cell top-left is at screen (sx, sy) GUI px; its texels scaled by the provider's scale. */
    private static void glyph(BufferedImage screen, GlyphImage g, double sx, double sy, int rgb, boolean bold,
                              int shadow) {
        if (g.image() == null) {
            return;
        }
        double w = g.w() * g.scale();
        double h = g.h() * g.scale();
        if (shadow != 0) {
            blit(screen, g.image(), g.x(), g.y(), g.x() + g.w(), g.y() + g.h(), sx + 1, sy + 1, sx + 1 + w,
                    sy + 1 + h, shadow);
            if (bold) {
                blit(screen, g.image(), g.x(), g.y(), g.x() + g.w(), g.y() + g.h(), sx + 2, sy + 1, sx + 2 + w,
                        sy + 1 + h, shadow);
            }
        }
        blit(screen, g.image(), g.x(), g.y(), g.x() + g.w(), g.y() + g.h(), sx, sy, sx + w, sy + h, rgb);
        if (bold) {
            blit(screen, g.image(), g.x(), g.y(), g.x() + g.w(), g.y() + g.h(), sx + 1, sy, sx + 1 + w, sy + h, rgb);
        }
    }

    // ---- the screen ------------------------------------------------------------------------------------------------

    private List<Drawn> render(String name, Component title, int width, int height, List<Important> important,
                               Map<Integer, Item> items) throws IOException {
        int left = (width - 176) / 2;
        int top = (height - 222) / 2;
        BufferedImage screen = new BufferedImage(width * S, height * S, BufferedImage.TYPE_INT_ARGB);
        fill(screen, 0, 0, width, height, 0xff202020);
        fill(screen, left, top, 176, 222, 0xffc6c6c6);
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int pen = 8;
        for (Component c : title.children()) {
            TextComponent text = (TextComponent) c;
            Map<Integer, GlyphImage> font = font(text.font());
            int color = text.color() == null ? 0xffffff : text.color().value();
            boolean bold = text.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE;
            ShadowColor shadow = text.shadowColor();
            for (int cp : text.content().codePoints().toArray()) {
                GlyphImage g = font.get(cp);
                assertTrue(g != null, "font " + text.font() + " lacks U+" + Integer.toHexString(cp));
                if (g.image() != null) {
                    int gx = left + pen;
                    int gy = top + 6 + 7 - g.ascent();
                    glyph(screen, g, gx, gy, color, bold, shadow == null ? 0 : shadow.value());
                    minX = Math.min(minX, gx);
                    maxX = Math.max(maxX, gx + (int) Math.ceil(g.w() * g.scale()));
                }
                pen += g.advance() + (bold ? 1 : 0);
            }
        }
        drawText(screen, left + 8, top + 128, "Inventar", 0x404040, false);
        for (int r = 0; r < 6; r++) {
            for (int col = 0; col < 9; col++) {
                outline(screen, left + 8 + 18 * col, top + 18 + 18 * r, 16, 16, 0x30ffffff);
            }
        }
        List<Drawn> drawn = new ArrayList<>();
        for (int slot = 0; slot < 54; slot++) {
            Item item = items.get(slot);
            if (item != null) {
                Drawn d = drawItem(screen, left, top, slot, item);
                if (d != null) {
                    drawn.add(d);
                }
            }
        }
        Path out = Path.of("build/reports/gui/" + de.tigges.kaboofcasino.config.Language.current().code() + "/" + name + "-preview-" + width + "x" + height + ".png");
        Files.createDirectories(out.getParent());
        ImageIO.write(screen, "png", out.toFile());
        String where = name + " " + width + "x" + height + ": ";
        assertTrue(minX >= 0, where + "art left of the screen: " + minX);
        assertTrue(maxX <= width, where + "art right of the screen: " + maxX);
        assertTrue(maxX - (left + 176) <= 125, where + "right overhang beyond the chest: " + (maxX - left - 176));
        for (Important i : important) {
            assertTrue(left + i.x0() >= 0 && left + i.x1() <= width && top + i.y0() >= 0 && top + i.y1() <= height,
                    where + i.name() + " is clipped");
        }
        for (Drawn d : drawn) {
            assertTrue(left + d.x0() >= 0 && left + d.x1() <= width && top + d.y0() >= 0 && top + d.y1() <= height,
                    where + "item in slot " + d.slot() + " (" + d.kind() + ") is clipped");
        }
        for (int i = 0; i < drawn.size(); i++) {
            for (int j = i + 1; j < drawn.size(); j++) {
                Drawn a = drawn.get(i);
                Drawn b = drawn.get(j);
                boolean allowed = a.kind().equals("marker") || b.kind().equals("marker");
                assertTrue(!a.overlaps(b) || allowed, where + "slot " + a.slot() + " (" + a.kind() + ") overlaps slot "
                        + b.slot() + " (" + b.kind() + ")");
            }
        }
        return drawn;
    }

    /** Fills a GUI-pixel rectangle (screen GUI coordinates). */
    private static void fill(BufferedImage img, int x, int y, int w, int h, int argb) {
        for (int j = y * S; j < (y + h) * S; j++) {
            for (int i = x * S; i < (x + w) * S; i++) {
                if (i >= 0 && j >= 0 && i < img.getWidth() && j < img.getHeight()) {
                    blend(img, i, j, argb);
                }
            }
        }
    }

    private static void outline(BufferedImage img, int x, int y, int w, int h, int argb) {
        fill(img, x, y, w, 1, argb);
        fill(img, x, y + h - 1, w, 1, argb);
        fill(img, x, y + 1, 1, h - 2, argb);
        fill(img, x + w - 1, y + 1, 1, h - 2, argb);
    }

    /**
     * Draws texels {@code (u0, v0)..(u1, v1)} of {@code tex} over the GUI rectangle (x0, y0)..(x1, y1): every preview
     * pixel whose centre lies inside takes the nearest texel (the GPU's nearest sampling), tinted.
     */
    private static void blit(BufferedImage screen, BufferedImage tex, double u0, double v0, double u1, double v1,
                             double x0, double y0, double x1, double y1, int tint) {
        for (int py = (int) Math.floor(y0 * S); py < Math.ceil(y1 * S); py++) {
            for (int px = (int) Math.floor(x0 * S); px < Math.ceil(x1 * S); px++) {
                double cx = (px + 0.5) / S;
                double cy = (py + 0.5) / S;
                if (cx < x0 || cx >= x1 || cy < y0 || cy >= y1) {
                    continue;
                }
                int u = (int) Math.floor(u0 + (cx - x0) / (x1 - x0) * (u1 - u0) + 1e-4);
                int v = (int) Math.floor(v0 + (cy - y0) / (y1 - y0) * (v1 - v0) + 1e-4);
                int p = tex.getRGB(u, v);
                if ((p >>> 24) == 0) {
                    continue;
                }
                int r = (p >> 16 & 255) * (tint >> 16 & 255) / 255;
                int g = (p >> 8 & 255) * (tint >> 8 & 255) / 255;
                int b = (p & 255) * (tint & 255) / 255;
                blend(screen, px, py, (p & 0xff000000) | r << 16 | g << 8 | b);
            }
        }
    }

    private static void blend(BufferedImage img, int x, int y, int argb) {
        if (x < 0 || y < 0 || x >= img.getWidth() || y >= img.getHeight()) {
            return;
        }
        double a = (argb >>> 24) / 255.0;
        int dst = img.getRGB(x, y);
        int r = (int) Math.round((argb >> 16 & 255) * a + (dst >> 16 & 255) * (1 - a));
        int g = (int) Math.round((argb >> 8 & 255) * a + (dst >> 8 & 255) * (1 - a));
        int b = (int) Math.round((argb & 255) * a + (dst & 255) * (1 - a));
        img.setRGB(x, y, 0xff000000 | r << 16 | g << 8 | b);
    }
}
