package de.tigges.kaboofcasino.game.derby;

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
import java.util.UUID;
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
 * Draws the derby menu like the 26.3 client from the embedded pack onto the common scaled screens, at GUI scale 2 (so
 * the cards' half pixels and the guide's 32 px texture show): title glyphs (also the scaled card glyphs), pen
 * advances, spaces and style shadows; slot items through the pack's item definitions and models, with their display
 * scale and translation; vanilla stakes as marked squares with their count, member heads as a stand-in face. Writes
 * {@code build/reports/gui/derby-*.png} and asserts that the content is on screen at all three sizes, that no
 * picture is clipped, that the lane pictures cover exactly their cards and that no two pictures overlap except a
 * member's cell behind his head.
 */
class DerbyGuiPreviewTest {

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

    /** The menu's content (window coordinates; composition in the pack tool's DerbyGuiArt / DerbyGui). */
    static final List<Important> IMPORTANT = List.of(
            new Important("duck cards", -1, -4, 177, 33),
            new Important("bets", 7, 35, 169, 89),
            new Important("buttons", -4, 89, 180, 107),
            new Important("lobby strip", 7, 107, 169, 125),
            new Important("brand", 216, -8, 281, 0),
            new Important("hint", 200, 2, 297, 24),
            new Important("people and page", 200, 31, 297, 53),
            new Important("inventory", 0, 128, 176, 222));
    static final List<Important> HELP_IMPORTANT = List.of(
            new Important("help panel", -9, -8, 301, 125),
            new Important("inventory", 0, 128, 176, 222));

    private final Map<String, byte[]> pack = new HashMap<>();
    private final Map<String, Map<Integer, GlyphImage>> fonts = new HashMap<>();
    private GuiLayout layout;
    private Function<Msg, String> raw;

    @BeforeEach
    void load() throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("pack/KaboofCasino-pack.zip");
             ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                pack.put(e.getName(), zip.readAllBytes());
            }
        }
        layout = GuiLayout.load(getClass().getClassLoader().getResourceAsStream("pack/gui-layout.json"));
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(
                getClass().getClassLoader().getResourceAsStream("messages_de.yml"), StandardCharsets.UTF_8));
        raw = m -> yaml.getString(m.key());
    }

    // ---- the states ------------------------------------------------------------------------------------------------

    static final UUID VIEWER = new UUID(0, 1);

    static DerbyMember member(int n, int lane) {
        return new DerbyMember(new UUID(1, n), "Spieler" + n, lane, List.of(new ItemAmount("minecraft:diamond", n)));
    }

    static List<DerbyMember> members(int n, boolean withViewer) {
        List<DerbyMember> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(member(i + 1, i % 4));
        }
        if (withViewer) {
            out.add(1, new DerbyMember(VIEWER, "Jxxq", 2, List.of(new ItemAmount("minecraft:gold_ingot", 12))));
        }
        return out;
    }

    static DerbySnapshot idle(int credits) {
        return new DerbySnapshot(false, false, 0, false, true, credits, List.of(), -1, List.of(), 1000);
    }

    static ItemAmount[] stakes(Object... indexItemCount) {
        ItemAmount[] out = new ItemAmount[12];
        for (int i = 0; i < indexItemCount.length; i += 3) {
            out[(Integer) indexItemCount[i]] = new ItemAmount((String) indexItemCount[i + 1],
                    (Integer) indexItemCount[i + 2]);
        }
        return out;
    }

    record State(String name, DerbySnapshot snapshot, ItemAmount[] stakes, int active, int page, boolean help,
                 boolean several) {
        DerbyMenuView.Cell[] cells() {
            return DerbyMenuView.cells(snapshot, VIEWER, stakes, active, page, help, several);
        }
    }

    static List<State> states() {
        ItemAmount[] three = stakes(1, "minecraft:diamond", 12, 5, "minecraft:gold_ingot", 64, 9, "minecraft:emerald",
                3);
        ItemAmount[] one = stakes(6, "minecraft:iron_ingot", 20);
        DerbySnapshot countdown = new DerbySnapshot(false, true, 7, false, true, 0, members(9, false), -1, List.of(),
                1000);
        DerbySnapshot confirmed = new DerbySnapshot(false, true, 4, true, true, 0, members(2, true), 2,
                List.of(new ItemAmount("minecraft:gold_ingot", 12), new ItemAmount("minecraft:diamond", 5)), 1000);
        DerbySnapshot unavailable = new DerbySnapshot(false, false, 0, false, false, 0, List.of(), -1, List.of(), 1000);
        return List.of(
                new State("empty", idle(0), new ItemAmount[12], 0, 0, false, false),
                new State("staked", idle(0), three, 9, 0, false, false),
                new State("countdown-page1", countdown, one, 10, 0, false, false),
                new State("countdown-page2", countdown, one, 10, 1, false, false),
                new State("confirmed", confirmed, new ItemAmount[12], 0, 0, false, false),
                new State("claim", idle(2), new ItemAmount[12], 0, 0, false, false),
                new State("several", idle(0), one, 6, 0, false, true),
                new State("group-unavailable", unavailable, one, 10, 0, false, false),
                new State("help", idle(0), three, 9, 0, true, false));
    }

    // ---- tests -----------------------------------------------------------------------------------------------------

    @Test
    void everyStateFitsTheCommonScreens() throws IOException {
        Component title = DerbyBetMenu.title(layout, raw);
        for (State s : states()) {
            for (int[] size : SIZES) {
                render("derby-" + s.name(), title, size[0], size[1], s.help() ? HELP_IMPORTANT : IMPORTANT,
                        items(s.cells()));
            }
        }
    }

    /** The lane pictures cover exactly their card and column, the buttons and the head cells their places. */
    @Test
    void picturesLandWhereTheLayoutPutsThem() throws IOException {
        Component title = DerbyBetMenu.title(layout, raw);
        State staked = states().get(1);
        List<Drawn> drawn = render("derby-staked", title, 480, 270, IMPORTANT, items(staked.cells()));
        int[] cardX = {-1, 45, 89, 135};
        int lanes = 0;
        for (Drawn d : drawn) {
            switch (d.kind()) {
                case "veil" -> {
                    int lane = lane(d.slot());
                    assertEquals(List.of(cardX[lane], -4, cardX[lane] + 42, 89), List.of(d.x0(), d.y0(), d.x1(),
                            d.y1()), "veil of lane " + lane);
                    lanes++;
                }
                case "chosen" -> {
                    int lane = lane(d.slot());
                    assertEquals(1, lane);
                    assertEquals(List.of(cardX[lane], -4, cardX[lane] + 42, 33), List.of(d.x0(), d.y0(), d.x1(),
                            d.y1()), "gold lines of lane " + lane);
                    lanes++;
                }
                case "solo" -> assertEquals(List.of(-4, 89, 79, 107), List.of(d.x0(), d.y0(), d.x1(), d.y1()));
                case "group" -> assertEquals(List.of(97, 89, 180, 107), List.of(d.x0(), d.y0(), d.x1(), d.y1()));
                case "help" -> assertEquals(List.of(79, 89, 97, 107), List.of(d.x0(), d.y0(), d.x1(), d.y1()));
                case "hint" -> assertEquals(List.of(200, 2, 297, 24), List.of(d.x0(), d.y0(), d.x1(), d.y1()));
                // "0 Spieler" (cap top 32, shadow) and the empty lobby (ducks at 60, "Noch keine Spieler" at 84)
                case "info" -> assertEquals(List.of(200, 31, 297, 94), List.of(d.x0(), d.y0(), d.x1(), d.y1()));
                default -> {
                }
            }
        }
        assertEquals(4, lanes);
        List<Drawn> lobby = render("derby-countdown-page1", title, 480, 270, IMPORTANT, items(states().get(2).cells()));
        int cells = 0;
        for (Drawn d : lobby) {
            if (d.kind().equals("lobby")) {
                assertEquals(List.of(7, 107, 151, 125), List.of(d.x0(), d.y0(), d.x1(), d.y1()));
                cells++;
            }
            if (d.kind().equals("pager")) {
                assertEquals(List.of(151, 107, 169, 125), List.of(d.x0(), d.y0(), d.x1(), d.y1()));
            }
        }
        assertEquals(1, cells);
        List<Drawn> help = render("derby-help", title, 480, 270, HELP_IMPORTANT, items(states().get(8).cells()));
        List<List<Integer>> halves = new ArrayList<>();
        for (Drawn d : help) {
            halves.add(List.of(d.x0(), d.y0(), d.x1(), d.y1()));
        }
        assertEquals(List.of(List.of(-9, -8, 146, 125), List.of(146, -8, 301, 125)), halves);
    }

    private static int lane(int slot) {
        for (int lane = 0; lane < 4; lane++) {
            if (DerbyMenuView.LANE_SLOTS[lane] == slot) {
                return lane;
            }
        }
        throw new AssertionError("no lane slot " + slot);
    }

    /** Every model key the menu can show resolves to an item definition in the pack. */
    @Test
    void everyMenuModelIsInThePack() {
        List<String> keys = new ArrayList<>(List.of("hint", "info", "lobby", "help", "help_left", "help_right",
                "pager", "solo_on", "solo_off", "claim_on", "group_on", "group_off", "confirm_on", "confirm_off",
                "withdraw_on"));
        for (int lane = 0; lane < 4; lane++) {
            keys.add("veil_" + lane);
            keys.add("chosen_" + lane);
        }
        for (String k : keys) {
            assertTrue(pack.containsKey("assets/kaboofcasino/items/gui/derby/" + k + ".json"), k);
        }
        for (State s : states()) {
            for (DerbyMenuView.Cell c : s.cells()) {
                if (c != null && c.model() != null) {
                    assertTrue(pack.containsKey(path(c.model(), "items", ".json")), c.model());
                }
            }
        }
    }

    /** Every hint, count and page string the menu sends has its picture (the select cases of the pack). */
    @Test
    void everyPictureStringHasACase() {
        JsonObject hint = json("assets/kaboofcasino/items/gui/derby/hint.json").getAsJsonObject("model");
        List<String> hints = whens(hint);
        for (String h : List.of("pick", "several", "confirmed")) {
            assertTrue(hints.contains(h), h);
        }
        for (int i = 0; i < 4; i++) {
            assertTrue(hints.contains("picked_" + i));
        }
        for (int s = 0; s <= DerbyMenuView.MAX_SECONDS; s++) {
            assertTrue(hints.contains("countdown_" + s));
        }
        JsonArray info = json("assets/kaboofcasino/items/gui/derby/info.json").getAsJsonObject("model")
                .getAsJsonArray("models");
        List<String> counts = whens(info.get(0).getAsJsonObject());
        List<String> pages = whens(info.get(1).getAsJsonObject());
        for (int n = 0; n <= DerbyMenuView.MAX_PLAYERS; n++) {
            assertTrue(counts.contains(Integer.toString(n)), "count " + n);
            int total = DerbyMenuView.pages(n);
            for (int p = 0; p < total && total > 1; p++) {
                assertTrue(pages.contains((p + 1) + "/" + total), "page " + (p + 1) + "/" + total);
            }
        }
    }

    private static List<String> whens(JsonObject select) {
        List<String> out = new ArrayList<>();
        for (JsonElement e : select.getAsJsonArray("cases")) {
            out.add(e.getAsJsonObject().get("when").getAsString());
        }
        return out;
    }

    private static Map<Integer, Item> items(DerbyMenuView.Cell[] cells) {
        Map<Integer, Item> out = new HashMap<>();
        for (int i = 0; i < cells.length; i++) {
            DerbyMenuView.Cell c = cells[i];
            if (c == null) {
                continue;
            }
            switch (c.kind()) {
                case PICTURE, BUTTON -> out.put(i, new Item(c.model(), null, 1, c.strings()));
                case BET -> {
                    if (c.stake() != null) {
                        out.put(i, Item.stack(c.stake().item(), (int) c.stake().count()));
                    } else if (c.active() && c.state().equals("free")) {
                        out.put(i, Item.model("kaboofcasino:gui/stake_active"));
                    }
                }
                case ESCROW -> out.put(i, Item.stack(c.stake().item(), (int) c.stake().count()));
                case HEAD -> out.put(i, Item.stack("minecraft:player_head", 1));
            }
        }
        return out;
    }

    // ---- items -----------------------------------------------------------------------------------------------------

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
        Map<Integer, GlyphImage> f = font(Key.key(layout.textFont(layout.anchor("derby_brand").y()).asString()));
        int pen = x;
        for (int cp : text.codePoints().toArray()) {
            GlyphImage g = f.get(cp);
            // the text sheets keep every glyph's cap top at row 4 of its cell
            glyph(screen, g, pen, top - 4, rgb, false, shadow ? (rgb & 0xFCFCFC) >> 2 | 0xff000000 : 0);
            pen += g.advance();
        }
    }

    private int width(String text) throws IOException {
        Map<Integer, GlyphImage> f = font(Key.key(layout.textFont(layout.anchor("derby_brand").y()).asString()));
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
        Path out = Path.of("build/reports/gui/" + name + "-preview-" + width + "x" + height + ".png");
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
                boolean allowed = a.kind().equals("lobby") && b.kind().equals("head")
                        || a.kind().equals("help") && b.kind().equals("help");
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
