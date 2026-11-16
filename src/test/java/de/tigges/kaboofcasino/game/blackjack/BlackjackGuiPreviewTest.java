package de.tigges.kaboofcasino.game.blackjack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.gui.GuiLayout;
import de.tigges.kaboofcasino.wager.ItemAmount;
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
 * Draws both blackjack menus like the 26.3 client from the embedded pack: the title (glyph tops at 6 + 7 - ascent,
 * pen advances, spaces, bold = one more pixel and a copy one pixel right, style shadow colour one pixel down-right)
 * and every slot item of the real menu code ({@link BlackjackRoundMenu.View} cells; the bet menu's stake markers and
 * buttons) resolved through the pack's item definitions ({@code model}, {@code select}, {@code range_dispatch},
 * {@code composite}, custom model data tints) and models (south faces, GUI display scale, UVs) onto the common scaled
 * screens. Writes {@code build/reports/gui/<language>/blackjack-*.png} (2x) and asserts that the content is on screen at all three
 * sizes and that no two pictures overlap except where the mod's layout overlaps (cards of one hand, a compact result
 * label over its stake).
 */
class BlackjackGuiPreviewTest {

    record GlyphImage(BufferedImage image, int x, int y, int w, int h, int ascent, int advance) {
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

    /** The bet view's content (window coordinates; composition in the pack tool's BlackjackGuiArt). */
    static final List<Important> BET_IMPORTANT = List.of(
            new Important("headline", 60, -8, 118, 0),
            new Important("payouts header", 220, -6, 262, 2),
            new Important("card picture", 65, 15, 112, 52),
            new Important("EINSATZ and bet slots", 24, 58, 152, 91),
            new Important("button", 53, 109, 125, 123),
            new Important("payout rows", 187, 24, 297, 177),
            new Important("BLACKJACK lettering", 200, 210, 285, 225),
            new Important("inventory", 0, 128, 176, 222));

    /** The round view's content. */
    static final List<Important> ROUND_IMPORTANT = List.of(
            new Important("title", 56, -8, 120, 0),
            new Important("dealer band", -47, 0, 223, 36),
            new Important("player band", -47, 36, 223, 71),
            new Important("stake band", -47, 71, 223, 90),
            new Important("status", 18, 90, 158, 102),
            new Important("bar", 19, 101, 157, 106),
            new Important("buttons", 8, 108, 168, 125),
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

    // ---- the states ------------------------------------------------------------------------------------------------

    private static final long NOW = 10_000;

    private static BlackjackSnapshot snapshot(int phase, List<Integer> player, List<Integer> dealer, int turnLeft,
                                              int result, boolean canDouble, boolean doubled, boolean afford,
                                              List<ItemAmount> stakes) {
        List<Long> pAt = new ArrayList<>();
        List<Long> dAt = new ArrayList<>();
        player.forEach(c -> pAt.add(NOW - 100));
        dealer.forEach(c -> dAt.add(NOW - 100));
        return new BlackjackSnapshot(phase, player, dealer, pAt, dAt, turnLeft, result, phase == 2, phase == 2,
                canDouble, doubled, afford, stakes, NOW);
    }

    /** Ace of spades = 0, ranks 0..12 (A..K), suits 0..3 (spades, hearts, diamonds, clubs). */
    private static int card(int rank, int suit) {
        return suit * 13 + rank;
    }

    static List<ItemAmount> stakes(Object... itemCount) {
        List<ItemAmount> out = new ArrayList<>();
        for (int i = 0; i < itemCount.length; i += 2) {
            out.add(new ItemAmount((String) itemCount[i], (Integer) itemCount[i + 1]));
        }
        return out;
    }

    private BlackjackSnapshot dealing() {
        return snapshot(1, List.of(card(8, 1), card(1, 3)), List.of(card(9, 0), BlackjackCards.HIDDEN), 0, 0, false,
                false, false, stakes("minecraft:diamond", 12));
    }

    private BlackjackSnapshot turn() {
        return snapshot(2, List.of(card(8, 1), card(1, 3)), List.of(card(9, 0), BlackjackCards.HIDDEN), 410, 0, true,
                false, true, stakes("minecraft:diamond", 12, "minecraft:gold_ingot", 64));
    }

    private BlackjackSnapshot doubledHand() {
        return snapshot(4, List.of(card(5, 2), card(4, 0), card(9, 1)), List.of(card(9, 0), card(6, 3)), 0, 0, false,
                true, false, stakes("minecraft:diamond", 24, "minecraft:iron_block", 128));
    }

    private BlackjackSnapshot blackjack() {
        return snapshot(5, List.of(card(0, 0), card(12, 1)), List.of(card(9, 2), card(6, 3)), 0, 3, false, false,
                false, stakes("minecraft:emerald", 5));
    }

    private BlackjackSnapshot loss() {
        return snapshot(5, List.of(card(9, 1), card(5, 2), card(7, 3)),
                List.of(card(1, 0), card(2, 1), card(1, 2), card(0, 3), card(3, 0), card(2, 2)), 0, 5, false, false,
                false, stakes("minecraft:diamond", 3, "minecraft:coal_block", 64, "minecraft:netherite_ingot", 1));
    }

    private BlackjackSnapshot longHands() {
        List<Integer> nine = List.of(card(0, 0), card(0, 1), card(0, 2), card(0, 3), card(1, 0), card(1, 1),
                card(1, 2), card(1, 3), card(2, 0));
        return snapshot(2, nine, List.of(card(5, 1), BlackjackCards.HIDDEN), 30, 0, false, false, false,
                stakes("minecraft:copper_ingot", 64, "minecraft:lapis_lazuli", 9, "minecraft:redstone", 33));
    }

    // ---- tests -----------------------------------------------------------------------------------------------------

    @Test
    void betViewFitsTheCommonScreens() throws IOException {
        Component title = BlackjackBetMenu.title(layout, raw);
        Map<Integer, Item> items = new HashMap<>();
        items.put(29, Item.model("kaboofcasino:gui/stake_active"));
        items.put(31, Item.stack("minecraft:diamond", 12));
        items.put(BlackjackBetMenu.BUTTON_ART, Item.model("kaboofcasino:gui/blackjack/deal_on"));
        items.put(BlackjackBetMenu.BETS, Item.stack("minecraft:book", 1));
        for (int[] size : SIZES) {
            render("blackjack-bet", title, size[0], size[1], BET_IMPORTANT, items);
        }
    }

    @Test
    void roundViewFitsTheCommonScreens() throws IOException {
        Component title = BlackjackRoundMenu.title(layout, raw);
        for (int[] size : SIZES) {
            render("blackjack-turn", title, size[0], size[1], ROUND_IMPORTANT, cells(turn()));
        }
        render("blackjack-dealing", title, 427, 240, ROUND_IMPORTANT, cells(dealing()));
        render("blackjack-doubled", title, 427, 240, ROUND_IMPORTANT, cells(doubledHand()));
        render("blackjack-result-blackjack", title, 427, 240, ROUND_IMPORTANT, cells(blackjack()));
        render("blackjack-result-loss", title, 427, 240, ROUND_IMPORTANT, cells(loss()));
        render("blackjack-long-hand", title, 427, 240, ROUND_IMPORTANT, cells(longHands()));
    }

    /** Every model key the round menu can show resolves to an item definition in the pack. */
    @Test
    void everyRoundModelIsInThePack() {
        List<String> keys = new ArrayList<>(List.of("bar", "doubled", "net_wide", "net_compact", "card_back",
                "card_back_9"));
        for (int i = 0; i < 52; i++) {
            keys.add("card_" + i);
            keys.add("card_" + i + "_9");
        }
        for (int t = 0; t <= 31; t++) {
            keys.add("badge_" + t);
        }
        for (int p = 1; p <= 5; p++) {
            keys.add("status_" + p);
        }
        for (int r = 1; r <= 8; r++) {
            keys.add("result_" + r);
        }
        for (BlackjackRoundMenu.Action a : BlackjackRoundMenu.Action.values()) {
            keys.add(a.model + "_on");
            keys.add(a.model + "_off");
        }
        keys.add("deal_on");
        keys.add("deal_off");
        for (String k : keys) {
            assertTrue(pack.containsKey("assets/kaboofcasino/items/gui/blackjack/" + k + ".json"), k);
        }
    }

    private Map<Integer, Item> cells(BlackjackSnapshot s) {
        BlackjackRoundMenu.Cell[] cells = new BlackjackRoundMenu.View().cells(s, false);
        Map<Integer, Item> out = new HashMap<>();
        for (int i = 0; i < cells.length; i++) {
            BlackjackRoundMenu.Cell c = cells[i];
            if (c != null) {
                out.put(i, c.item() != null ? Item.stack(c.item(), c.amount())
                        : new Item(c.model(), null, 1, c.strings(), c.colors(), c.floats()));
            }
        }
        return out;
    }

    // ---- items -----------------------------------------------------------------------------------------------------

    /** A slot item: a pack model with custom model data, or a vanilla item (drawn as a marked square + count). */
    record Item(String model, String vanilla, int amount, List<String> strings, List<Integer> colors,
                List<Float> floats) {
        static Item model(String key) {
            return new Item(key, null, 1, List.of(), List.of(), List.of());
        }

        static Item stack(String key, int amount) {
            return new Item(null, key, amount, List.of(), List.of(), List.of());
        }
    }

    private JsonObject json(String path) {
        byte[] b = pack.get(path);
        assertNotNull(b, "pack lacks " + path);
        return JsonParser.parseString(new String(b, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static String path(String id, String dir, String ext) {
        Key k = Key.key(id);
        return "assets/" + k.namespace() + "/" + dir + "/" + k.value() + ext;
    }

    /** Resolves an item model tree to (model id, tint colours) pairs like {@code ItemModelResolver}. */
    private void resolve(JsonObject m, Item item, List<Map.Entry<String, int[]>> out) {
        String type = m.get("type").getAsString();
        switch (type) {
            case "minecraft:empty" -> {
            }
            case "minecraft:model" -> {
                List<Integer> tints = new ArrayList<>();
                if (m.has("tints")) {
                    for (JsonElement t : m.getAsJsonArray("tints")) {
                        JsonObject tint = t.getAsJsonObject();
                        assertEquals("minecraft:custom_model_data", tint.get("type").getAsString());
                        int index = tint.has("index") ? tint.get("index").getAsInt() : 0;
                        tints.add(index < item.colors().size() ? item.colors().get(index)
                                : tint.get("default").getAsInt());
                    }
                }
                out.add(Map.entry(m.get("model").getAsString(), tints.stream().mapToInt(Integer::intValue).toArray()));
            }
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
            case "minecraft:range_dispatch" -> {
                assertEquals("minecraft:custom_model_data", m.get("property").getAsString());
                int index = m.has("index") ? m.get("index").getAsInt() : 0;
                float value = index < item.floats().size() ? item.floats().get(index) : 0;
                JsonObject chosen = m.has("fallback") ? m.getAsJsonObject("fallback") : null;
                float best = Float.NEGATIVE_INFINITY;
                for (JsonElement e : m.getAsJsonArray("entries")) {
                    JsonObject entry = e.getAsJsonObject();
                    float threshold = entry.get("threshold").getAsFloat();
                    if (threshold <= value && threshold > best) {
                        best = threshold;
                        chosen = entry.getAsJsonObject("model");
                    }
                }
                if (chosen != null) {
                    resolve(chosen, item, out);
                }
            }
            default -> throw new AssertionError("unsupported item model type " + type);
        }
    }

    /** Draws one slot item (window item area at sx, sy); returns the rectangle the oversized item covers. */
    private Drawn drawItem(BufferedImage screen, int left, int top, int slot, Item item) throws IOException {
        int sx = 8 + 18 * (slot % 9);
        int sy = 18 + 18 * (slot / 9);
        if (item.vanilla() != null) {
            fill(screen, left + sx + 1, top + sy + 1, 14, 14, 0xff3a7bd5);
            outline(screen, left + sx + 1, top + sy + 1, 14, 14, 0xffffffff);
            if (item.amount() != 1) {
                String count = Integer.toString(item.amount());
                drawText(screen, left + sx + 17 - width(count), top + sy + 9, count, 0xffffff, false, true);
            }
            return new Drawn(slot, "stake", sx, sy, sx + 16, sy + 16);
        }
        JsonObject definition = json(path(item.model(), "items", ".json"));
        boolean oversized = definition.has("oversized_in_gui") && definition.get("oversized_in_gui").getAsBoolean();
        List<Map.Entry<String, int[]>> models = new ArrayList<>();
        resolve(definition.getAsJsonObject("model"), item, models);
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        record Face(double z, BufferedImage tex, double[] uv, double x0, double y0, double x1, double y1, int tint) {
        }
        List<Face> faces = new ArrayList<>();
        for (Map.Entry<String, int[]> m : models) {
            JsonObject model = json(path(m.getKey(), "models", ".json"));
            if (!model.has("elements") && model.get("parent").getAsString().equals("minecraft:item/generated")) {
                // a plain 16x16 item (the active stake marker)
                String layer0 = model.getAsJsonObject("textures").get("layer0").getAsString();
                BufferedImage tex = ImageIO.read(new ByteArrayInputStream(pack.get(path(layer0, "textures", ".png"))));
                for (int y = 0; y < 16; y++) {
                    for (int x = 0; x < 16; x++) {
                        blend(screen, left + sx + x, top + sy + y, tex.getRGB(x * tex.getWidth() / 16,
                                y * tex.getHeight() / 16));
                    }
                }
                return new Drawn(slot, "plain", sx, sy, sx + 16, sy + 16);
            }
            assertTrue(model.has("elements"), m.getKey());
            JsonObject gui = model.getAsJsonObject("display").getAsJsonObject("gui");
            double s = gui.getAsJsonArray("scale").get(0).getAsDouble();
            double tx = gui.getAsJsonArray("translation").get(0).getAsDouble();
            double ty = gui.getAsJsonArray("translation").get(1).getAsDouble();
            JsonObject textures = model.getAsJsonObject("textures");
            for (JsonElement e : model.getAsJsonArray("elements")) {
                JsonObject el = e.getAsJsonObject();
                JsonArray from = el.getAsJsonArray("from");
                JsonArray to = el.getAsJsonArray("to");
                for (int i = 0; i < 3; i++) {
                    assertTrue(from.get(i).getAsDouble() >= -16 && to.get(i).getAsDouble() <= 32, m.getKey());
                }
                JsonObject face = el.getAsJsonObject("faces").getAsJsonObject("south");
                String texture = textures.get(face.get("texture").getAsString().substring(1)).getAsString();
                BufferedImage tex = ImageIO.read(new ByteArrayInputStream(pack.get(path(texture, "textures", ".png"))));
                JsonArray uv = face.getAsJsonArray("uv");
                double x0 = sx + 8 + (from.get(0).getAsDouble() - 8) * s + tx;
                double x1 = sx + 8 + (to.get(0).getAsDouble() - 8) * s + tx;
                double y0 = sy + 8 - (to.get(1).getAsDouble() - 8) * s - ty;
                double y1 = sy + 8 - (from.get(1).getAsDouble() - 8) * s - ty;
                int tint = face.has("tintindex") ? m.getValue()[face.get("tintindex").getAsInt()] : 0xffffff;
                faces.add(new Face(from.get(2).getAsDouble(), tex, new double[] {uv.get(0).getAsDouble(),
                        uv.get(1).getAsDouble(), uv.get(2).getAsDouble(), uv.get(3).getAsDouble()}, x0, y0, x1, y1,
                        tint));
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
            if (transparent(f.tex())) {
                continue; // the clear quads that only fix an item's bounds
            }
            int w = (int) Math.round(f.x1() - f.x0());
            int h = (int) Math.round(f.y1() - f.y0());
            assertEquals(f.x1() - f.x0(), w, 1e-6, item.model() + ": quad not on whole pixels");
            assertEquals(f.y1() - f.y0(), h, 1e-6, item.model() + ": quad not on whole pixels");
            double tu0 = f.uv()[0] / 16 * f.tex().getWidth();
            double tv0 = f.uv()[1] / 16 * f.tex().getHeight();
            double tu1 = f.uv()[2] / 16 * f.tex().getWidth();
            double tv1 = f.uv()[3] / 16 * f.tex().getHeight();
            assertTrue(tu1 > tu0 && tv1 > tv0, item.model() + ": mirrored UVs");
            assertEquals(Math.abs(tu1 - tu0), w, 1e-3, item.model() + ": texel/pixel ratio");
            assertEquals(Math.abs(tv1 - tv0), h, 1e-3, item.model() + ": texel/pixel ratio");
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int p = f.tex().getRGB((int) Math.floor(tu0 + x + 0.5), (int) Math.floor(tv0 + y + 0.5));
                    int t = f.tint();
                    int r = (p >> 16 & 255) * (t >> 16 & 255) / 255;
                    int g = (p >> 8 & 255) * (t >> 8 & 255) / 255;
                    int b = (p & 255) * (t & 255) / 255;
                    blend(screen, left + (int) Math.round(f.x0()) + x, top + (int) Math.round(f.y0()) + y,
                            (p & 0xff000000) | r << 16 | g << 8 | b);
                }
            }
        }
        String kind = item.model().substring(item.model().lastIndexOf('/') + 1).replaceAll("_.*", "");
        return new Drawn(slot, kind, (int) Math.round(minX), (int) Math.round(minY), (int) Math.round(maxX),
                (int) Math.round(maxY));
    }

    private static boolean transparent(BufferedImage img) {
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if ((img.getRGB(x, y) >>> 24) != 0) {
                    return false;
                }
            }
        }
        return true;
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
                    out.put(a.getKey().codePointAt(0), new GlyphImage(null, 0, 0, 0, 0, 0, a.getValue().getAsInt()));
                }
                continue;
            }
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(pack.get(path(
                    provider.get("file").getAsString(), "textures", ""))));
            JsonArray rows = provider.getAsJsonArray("chars");
            int cols = rows.get(0).getAsString().codePointCount(0, rows.get(0).getAsString().length());
            int cw = image.getWidth() / cols;
            int ch = image.getHeight() / rows.size();
            if (provider.get("height").getAsInt() != ch) {
                continue;
            }
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
                            actual + 1));
                }
            }
        }
        fonts.put(key.asString(), out);
        return out;
    }

    /** Count decorations etc.: text with its cap tops at screen y {@code top} in any text font of the pack. */
    private void drawText(BufferedImage screen, int x, int top, String text, int rgb, boolean bold, boolean shadow)
            throws IOException {
        Map<Integer, GlyphImage> f = font(Key.key(layout.textFont(layout.anchor("bj_round_stake").y()).asString()));
        int pen = x;
        for (int cp : text.codePoints().toArray()) {
            GlyphImage g = f.get(cp);
            glyph(screen, g, pen, top - 4, rgb, bold, shadow ? (rgb & 0xFCFCFC) >> 2 | 0xff000000 : 0);
            pen += g.advance() + (bold ? 1 : 0);
        }
    }

    private int width(String text) throws IOException {
        Map<Integer, GlyphImage> f = font(Key.key(layout.textFont(layout.anchor("bj_round_stake").y()).asString()));
        int w = 0;
        for (int cp : text.codePoints().toArray()) {
            w += f.get(cp).advance();
        }
        return w;
    }

    private static void glyph(BufferedImage screen, GlyphImage g, int sx, int sy, int rgb, boolean bold, int shadow) {
        if (g.image() == null) {
            return;
        }
        if (shadow != 0) {
            draw(screen, g, sx + 1, sy + 1, shadow);
            if (bold) {
                draw(screen, g, sx + 2, sy + 1, shadow);
            }
        }
        draw(screen, g, sx, sy, rgb);
        if (bold) {
            draw(screen, g, sx + 1, sy, rgb);
        }
    }

    // ---- the screen ------------------------------------------------------------------------------------------------

    private void render(String name, Component title, int width, int height, List<Important> important,
                        Map<Integer, Item> items) throws IOException {
        int left = (width - 176) / 2;
        int top = (height - 222) / 2;
        BufferedImage screen = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
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
                    maxX = Math.max(maxX, gx + g.w());
                }
                pen += g.advance() + (bold ? 1 : 0);
            }
        }
        // vanilla draws the inventory label and the slots after the title
        drawText(screen, left + 8, top + 128, "Inventar", 0x404040, false, false);
        for (int r = 0; r < 6; r++) {
            for (int col = 0; col < 9; col++) {
                outline(screen, left + 8 + 18 * col, top + 18 + 18 * r, 16, 16, 0x40ffffff);
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
        BufferedImage big = new BufferedImage(width * 2, height * 2, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height * 2; y++) {
            for (int x = 0; x < width * 2; x++) {
                big.setRGB(x, y, screen.getRGB(x / 2, y / 2));
            }
        }
        ImageIO.write(big, "png", out.toFile());
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
                    where + "item in slot " + d.slot() + " is clipped");
        }
        for (int i = 0; i < drawn.size(); i++) {
            for (int j = i + 1; j < drawn.size(); j++) {
                Drawn a = drawn.get(i);
                Drawn b = drawn.get(j);
                boolean allowed = a.kind().equals("card") && b.kind().equals("card") && a.slot() / 9 == b.slot() / 9
                        || a.kind().equals("stake") && b.kind().equals("net") && b.slot() == a.slot() + 1;
                assertTrue(!a.overlaps(b) || allowed, where + "slot " + a.slot() + " (" + a.kind() + ") overlaps slot "
                        + b.slot() + " (" + b.kind() + ")");
            }
        }
    }

    private static void fill(BufferedImage img, int x, int y, int w, int h, int argb) {
        for (int j = y; j < y + h; j++) {
            for (int i = x; i < x + w; i++) {
                if (i >= 0 && j >= 0 && i < img.getWidth() && j < img.getHeight()) {
                    img.setRGB(i, j, argb);
                }
            }
        }
    }

    private static void outline(BufferedImage img, int x, int y, int w, int h, int argb) {
        for (int i = x; i < x + w; i++) {
            blend(img, i, y, argb);
            blend(img, i, y + h - 1, argb);
        }
        for (int j = y; j < y + h; j++) {
            blend(img, x, j, argb);
            blend(img, x + w - 1, j, argb);
        }
    }

    private static void draw(BufferedImage screen, GlyphImage g, int sx, int sy, int rgb) {
        for (int y = 0; y < g.h(); y++) {
            for (int x = 0; x < g.w(); x++) {
                int px = g.image().getRGB(g.x() + x, g.y() + y);
                if ((px >>> 24) == 0) {
                    continue;
                }
                int r = (px >> 16 & 255) * (rgb >> 16 & 255) / 255;
                int gg = (px >> 8 & 255) * (rgb >> 8 & 255) / 255;
                int b = (px & 255) * (rgb & 255) / 255;
                blend(screen, sx + x, sy + y, (px & 0xff000000) | r << 16 | gg << 8 | b);
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
