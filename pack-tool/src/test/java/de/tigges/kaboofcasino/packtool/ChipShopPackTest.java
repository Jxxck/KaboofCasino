package de.tigges.kaboofcasino.packtool;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The chip shop's pack part (step 11): the sign (CHIP-SHOP LEDs in the cashier screen's place), the display case on
 * the counter (inside the counter, behind the sign's posts, the miniatures inside the glass), the item icon, the menu
 * art (backgrounds with tabs, plate and grid frames; six overlays that differ only in row 0; the cashier's anchors;
 * the quantity toggle in slot 47; the cashier's buttons and number displays reused from the same slots);
 * deterministic.
 */
class ChipShopPackTest {

    private static ModJar jar;
    private static PackTree tree;
    private static GuiFont font;
    private static TextGlyphs text;

    private static PackTree build() {
        PackTree t = new PackTree();
        Path src = Path.of(System.getProperty("kaboofcasino.packSrc"));
        TextGlyphs glyphs = TextGlyphs.load(src.resolve("vanilla"));
        GuiFont f = new GuiFont(t);
        DecoDisplayPack.add(jar, t, src);
        CashierPack.add(jar, t, src);
        CashierGui.add(jar, t, f, glyphs);
        ChipShopPack.add(jar, t, src);
        ChipShopGui.add(jar, t, f, glyphs);
        f.write();
        font = f;
        text = glyphs;
        return t;
    }

    @BeforeAll
    static void open() {
        jar = ModJar.open(Path.of(System.getProperty("kaboofcasino.modJar")),
                System.getProperty("kaboofcasino.modJarSha1"));
        tree = build();
    }

    private static JsonObject json(String path) {
        assertTrue(tree.has(path), path);
        return Json.parse(tree.get(path));
    }

    private static BufferedImage image(String path) {
        assertTrue(tree.has(path), path);
        return Images.read(tree.get(path));
    }

    @Test
    void deterministic() {
        PackTree first = tree;
        GuiFont firstFont = font;
        PackTree again = build();
        assertEquals(first.files().keySet(), again.files().keySet());
        for (Map.Entry<String, byte[]> e : first.files().entrySet()) {
            assertArrayEquals(e.getValue(), again.get(e.getKey()), e.getKey());
        }
        assertEquals(firstFont.manifest(text), font.manifest(text));
    }

    // ---- models and items -----------------------------------------------------------------------------------------

    @Test
    void theModelsAndItemsExist() {
        assertEquals(ChipShopPack.SIGN, json("assets/kaboofcasino/items/chipshop/sign.json").getAsJsonObject("model")
                .get("model").getAsString());
        assertEquals(ChipShopPack.DISPLAY, json("assets/kaboofcasino/items/chipshop/display.json")
                .getAsJsonObject("model").get("model").getAsString());
        assertTrue(tree.has("assets/kaboofcasino/models/chipshop/sign.json"));
        assertTrue(tree.has("assets/kaboofcasino/models/chipshop/display.json"));
        // the placing item: the flat icon everywhere
        assertEquals("kaboofcasino:item/chip_shop", json("assets/kaboofcasino/items/chip_shop.json")
                .getAsJsonObject("model").get("model").getAsString());
        JsonObject model = json("assets/kaboofcasino/models/item/chip_shop.json");
        assertEquals("minecraft:item/generated", model.get("parent").getAsString());
        assertEquals("kaboofcasino:item/chip_shop", model.getAsJsonObject("textures").get("layer0").getAsString());
        BufferedImage icon = image("assets/kaboofcasino/textures/item/chip_shop.png");
        assertEquals(32, icon.getWidth());
        assertEquals(32, icon.getHeight());
        int opaque = 0;
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                int a = icon.getRGB(x, y) >>> 24;
                assertTrue(a == 0 || a == 255, "icon alpha at " + x + "," + y);
                opaque += a == 255 ? 1 : 0;
            }
        }
        assertTrue(opaque > 600, "icon pixels: " + opaque);
        assertEquals("kaboofcasino:chip_shop", ChipShopPack.ITEM);
    }

    /** Every texture an element face names resolves to a texture in the tree; returns the face textures used. */
    private static Set<String> checkTextures(JsonObject model) {
        Set<String> used = new TreeSet<>();
        JsonObject textures = model.getAsJsonObject("textures");
        assertTrue(textures.has("particle"));
        for (JsonElement e : model.getAsJsonArray("elements")) {
            for (Map.Entry<String, JsonElement> f : e.getAsJsonObject().getAsJsonObject("faces").entrySet()) {
                JsonObject face = f.getValue().getAsJsonObject();
                String ref = face.get("texture").getAsString();
                used.add(ref);
                String id = textures.get(ref.substring(1)).getAsString();
                assertTrue(tree.has(ChipShopPack.texturePath(id)), id);
                double[] uv = DecoDisplayBake.doubles(face.getAsJsonArray("uv"));
                for (double v : uv) {
                    assertTrue(v >= 0 && v <= 16, ref + " uv " + v);
                }
            }
        }
        return used;
    }

    @Test
    void theSignStaysInTheScreensPlaceAndSpellsChipShop() {
        JsonObject model = json("assets/kaboofcasino/models/chipshop/sign.json");
        int leds = 0;
        for (JsonElement e : model.getAsJsonArray("elements")) {
            JsonObject el = e.getAsJsonObject();
            double[] from = DecoDisplayBake.doubles(el.getAsJsonArray("from"));
            double[] to = DecoDisplayBake.doubles(el.getAsJsonArray("to"));
            // the cashier screen's room: both cells, the upper block, inside the cell's depth
            assertTrue(from[0] >= 0 && to[0] <= 32 && from[1] >= 16 && to[1] <= 32 && from[2] >= 0 && to[2] <= 16,
                    el.toString());
            String texture = el.getAsJsonObject("faces").getAsJsonObject("south").get("texture").getAsString();
            if (texture.equals("#led") || texture.equals("#bulb")) {
                assertEquals(15, el.get("light_emission").getAsInt());
                leds += texture.equals("#led") ? 1 : 0;
            } else {
                assertTrue(!el.has("light_emission"), el.toString());
            }
        }
        assertTrue(leds > 40, "LED runs: " + leds);
        checkTextures(model);
        boolean[][] grid = ChipShopPack.leds(ChipShopPack.WORD);
        assertEquals(7, grid.length);
        StringBuilder middle = new StringBuilder();
        for (boolean b : grid[3]) {
            middle.append(b ? '#' : '.');
        }
        // C H I P - S H O P at the middle row: the hyphen lit, the letters one dark column apart
        assertEquals("#.....#####...#...#..##.###..###..#####.#...#.#..##", middle.toString());
        assertEquals(51, grid[3].length);
        // the word fits the panel inside the frame with room to spare
        double width = grid[0].length * ChipShopPack.LED;
        assertTrue(width <= ChipShopPack.SIGN_X1 - ChipShopPack.SIGN_X0 - 2, "LED word " + width + " px");
    }

    @Test
    void theDisplayStandsOnTheCounterBehindTheSign() {
        JsonObject model = json("assets/kaboofcasino/models/chipshop/display.json");
        JsonObject sign = json("assets/kaboofcasino/models/chipshop/sign.json");
        // the sign's lowest element above the counter top (its rail) and its frontmost post face, in the display frame
        double signBottom = Double.MAX_VALUE;
        double postBack = Double.MAX_VALUE;
        for (JsonElement e : sign.getAsJsonArray("elements")) {
            double[] from = DecoDisplayBake.doubles(e.getAsJsonObject().getAsJsonArray("from"));
            double[] to = DecoDisplayBake.doubles(e.getAsJsonObject().getAsJsonArray("to"));
            if (to[0] - from[0] > 20) {
                signBottom = Math.min(signBottom, from[1] - 16);
            }
            postBack = Math.min(postBack, from[2] + ChipShopPack.Z_SHIFT);
        }
        int lit = 0;
        int inside = 0;
        double glassX0 = ChipShopPack.GLASS_X0 + ChipShopPack.PANE;
        double glassX1 = ChipShopPack.GLASS_X1 - ChipShopPack.PANE;
        double glassZ0 = ChipShopPack.CASE_Z0 + ChipShopPack.PANE;
        double glassZ1 = ChipShopPack.CASE_Z1 - ChipShopPack.PANE;
        double glassY1 = ChipShopPack.GLASS_Y1 - ChipShopPack.PANE;
        for (JsonElement e : model.getAsJsonArray("elements")) {
            JsonObject el = e.getAsJsonObject();
            double[] from = DecoDisplayBake.doubles(el.getAsJsonArray("from"));
            double[] to = DecoDisplayBake.doubles(el.getAsJsonArray("to"));
            for (int i = 0; i < 3; i++) {
                assertTrue(from[i] <= to[i] && from[i] >= -16 && to[i] <= 32, el.toString());
            }
            // on the counter top (y 0), over the two cells, inside the counter's depth (z - shift in 0..16)
            assertTrue(from[0] >= 0 && to[0] <= 32 && from[1] >= 0, el.toString());
            assertTrue(from[2] - ChipShopPack.Z_SHIFT >= 0 && to[2] - ChipShopPack.Z_SHIFT <= 16, el.toString());
            // under the sign's rail and behind its posts
            assertTrue(to[1] < signBottom, el + " reaches the sign's rail at " + signBottom);
            assertTrue(to[2] < postBack, el + " reaches the posts at z " + postBack);
            if (el.has("light_emission")) {
                lit++;
            }
            Set<String> textures = new TreeSet<>();
            el.getAsJsonObject("faces").entrySet().forEach(f -> textures.add(f.getValue().getAsJsonObject()
                    .get("texture").getAsString()));
            boolean miniature = textures.stream().anyMatch(t -> t.equals("#sheet") || t.startsWith("#sample_"));
            if (miniature) {
                inside++;
                assertTrue(from[0] >= glassX0 && to[0] <= glassX1 && from[1] >= ChipShopPack.FLOOR - 1e-9
                        && to[1] <= glassY1 && from[2] >= glassZ0 && to[2] <= glassZ1, "outside the glass: " + el);
            }
        }
        assertEquals(1, lit, "the light strip only");
        assertEquals(3 + 1 + 3 + ChipShopPack.STACKS.length, inside, "machines, lever, samples and stacks");
        assertTrue(model.getAsJsonArray("elements").size() <= 64, "keep it light: "
                + model.getAsJsonArray("elements").size() + " elements");
        Set<String> used = checkTextures(model);
        assertTrue(used.containsAll(List.of("#glass", "#brass", "#velvet", "#sheet", "#sample_0", "#sample_1",
                "#sample_2", "#wood", "#bulb")), used.toString());
        assertEquals("casinodeco:block/casino_glass_clear", model.getAsJsonObject("textures").get("glass")
                .getAsString());
    }

    @Test
    void theMiniatureSheetIsOpaqueOrClearAndAMultipleOf16() {
        BufferedImage sheet = image(ChipShopPack.texturePath(ChipShopPack.SHEET));
        assertEquals(ChipShopPack.SHEET_W, sheet.getWidth());
        assertEquals(ChipShopPack.SHEET_H, sheet.getHeight());
        assertEquals(0, sheet.getWidth() % 16);
        assertEquals(0, sheet.getHeight() % 16);
        for (int y = 0; y < sheet.getHeight(); y++) {
            for (int x = 0; x < sheet.getWidth(); x++) {
                int a = sheet.getRGB(x, y) >>> 24;
                assertTrue(a == 0 || a == 255, x + "," + y);
            }
        }
        // the boxes' faces show only opaque texels: the machine and plinko fronts and every chip strip
        for (int[] r : new int[][] {ChipShopPack.MACHINE, {ChipShopPack.PLINKO_X + ChipShopPack.PLINKO[0],
            ChipShopPack.PLINKO[1], ChipShopPack.PLINKO_X + ChipShopPack.PLINKO[2], ChipShopPack.PLINKO[3]},
            {0, ChipShopPack.CHIPS_Y, ChipShopPack.CHIP_W, ChipShopPack.CHIP_TOP_Y + ChipShopPack.CHIP_W}}) {
            for (int y = r[1]; y < r[3]; y++) {
                for (int x = r[0]; x < r[2]; x++) {
                    assertEquals(255, sheet.getRGB(x, y) >>> 24, "clear texel on a box face at " + x + "," + y);
                }
            }
        }
        // the chip strips: the mod's dark line under every chip, the denomination's insert in each insert row
        for (int k = 0; k < ChipShopPack.CHIP_ORDER.length; k++) {
            ChipPack.Palette p = ChipPack.palette(ChipShopPack.CHIP_ORDER[k]);
            int x0 = k * ChipShopPack.CHIP_PITCH;
            for (int chip = 0; chip < ChipShopPack.CHIP_MAX; chip++) {
                int row = ChipShopPack.CHIPS_Y + chip * ChipShopPack.CHIP_ROWS;
                assertEquals(p.insert(), sheet.getRGB(x0 + 2, row));
                assertEquals(p.body(), sheet.getRGB(x0 + 2, row + 1));
                assertEquals(ChipShopPack.CHIP_LINE, sheet.getRGB(x0 + 2, row + 2));
            }
        }
    }

    // ---- the menu ---------------------------------------------------------------------------------------------------

    private static JsonObject glyph(String name) {
        JsonObject glyphs = font.manifest(text).getAsJsonObject("glyphs");
        assertTrue(glyphs.has(name), name);
        return glyphs.getAsJsonObject(name);
    }

    @Test
    void theAnchorsAreTheCashiers() {
        JsonObject anchors = font.manifest(text).getAsJsonObject("anchors");
        List<String> suffixes = new ArrayList<>(List.of("brand", "head", "label_a", "label_b"));
        for (int i = 0; i < 5; i++) {
            suffixes.add("help_" + i);
        }
        for (String s : suffixes) {
            assertTrue(anchors.has("chipshop_" + s), s);
            assertEquals(anchors.get("cashier_" + s), anchors.get("chipshop_" + s), s);
        }
        // no text row of its own: the cashier's text fonts serve
        Set<Integer> rows = new TreeSet<>();
        for (GuiFont.Anchor a : CashierGui.anchors()) {
            rows.add(a.y());
        }
        for (GuiFont.Anchor a : ChipShopGui.anchors()) {
            assertTrue(rows.contains(a.y()), a.name());
        }
        // the brand and the longest category name fit the receipt
        int room = DerbyGuiArt.RIGHT_W - 14;
        assertTrue(BlackjackGuiText.width(text, "CHIP-SHOP", true) <= room);
        assertTrue(text.width("Licht & Schilder") <= room);
    }

    /** The menu art as the client composes it: both backgrounds, then the overlay of {@code tab} (or none if < 0). */
    private static BufferedImage compose(int tab) {
        BufferedImage canvas = Images.blank(DerbyGuiArt.ART_W, DerbyGuiArt.ART_H);
        List<String[]> parts = new ArrayList<>(List.of(new String[] {"chipshop_bg_0", "bg_0"},
                new String[] {"chipshop_bg_1", "bg_1"}));
        if (tab >= 0) {
            parts.add(new String[] {"chipshop_overlay_" + tab, "overlay"});
        }
        for (String[] p : parts) {
            JsonObject g = glyph(p[0]);
            BufferedImage img = image("assets/kaboofcasino/textures/font/chipshop/" + p[1] + ".png");
            assertEquals(g.get("width").getAsInt(), img.getWidth());
            assertEquals(g.get("height").getAsInt(), img.getHeight());
            int ox = g.get("x").getAsInt() - DerbyGuiArt.ART_X;
            int oy = g.get("y").getAsInt() - DerbyGuiArt.ART_Y;
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    int argb = img.getRGB(x, y);
                    if ((argb >>> 24) != 0) {
                        Images.blend(canvas, ox + x, oy + y, argb);
                    }
                }
            }
        }
        return canvas;
    }

    private static int at(BufferedImage art, int windowX, int windowY) {
        return art.getRGB(windowX - DerbyGuiArt.ART_X, windowY - DerbyGuiArt.ART_Y);
    }

    @Test
    void theBackgroundsHoldTabsPlateGridAndTheInventoryPlate() {
        JsonObject bg0 = glyph("chipshop_bg_0");
        JsonObject bg1 = glyph("chipshop_bg_1");
        assertEquals(DerbyGuiArt.ART_X, bg0.get("x").getAsInt());
        assertEquals(DerbyGuiArt.ART_Y, bg0.get("y").getAsInt());
        assertEquals(bg0.get("x").getAsInt() + bg0.get("width").getAsInt(), bg1.get("x").getAsInt());
        assertEquals(DerbyGuiArt.ART_W, bg0.get("width").getAsInt() + bg1.get("width").getAsInt());
        assertEquals(glyph("cashier_bg_0").get("width"), bg0.get("width"));
        BufferedImage art = compose(-1);
        // the vanilla "Inventar" label's plate, exactly as in every felt menu
        for (int y = InventoryLabelPlate.Y0; y <= InventoryLabelPlate.Y1; y++) {
            for (int x = InventoryLabelPlate.X0; x <= InventoryLabelPlate.X1; x++) {
                assertEquals(InventoryLabelPlate.colour(x, y), at(art, x, y), "plate at " + x + "," + y);
            }
        }
        // six unselected tabs (the mod's button: dark outline, gold-brown border), slot frames on the item grid
        for (int t = 0; t < ChipShopGui.TABS; t++) {
            int x = ChipShopGui.tabX(t);
            assertEquals(0xFF000000 | -15719140, at(art, x, ChipShopGui.TAB_Y), "tab " + t);
            assertEquals(0xFF000000 | -4878005, at(art, x + 1, ChipShopGui.TAB_Y + 1), "tab " + t);
        }
        for (int s = ChipShopGui.FIRST_ITEM; s <= ChipShopGui.LAST_ITEM; s++) {
            assertEquals(0xFF000000 | -15391202, at(art, DerbyGuiArt.slotX(s) - 1, DerbyGuiArt.slotY(s) - 1), "slot "
                    + s);
            assertEquals(0xFF000000 | -11970227, at(art, DerbyGuiArt.slotX(s) + 8, DerbyGuiArt.slotY(s) + 8), "slot "
                    + s);
        }
        // the plate's word over slots 6-8: cream pixels there, inside the three slots
        int cream = 0;
        for (int y = ChipShopGui.TAB_Y; y < ChipShopGui.TAB_Y + 18; y++) {
            for (int x = ChipShopGui.PLATE_X; x < ChipShopGui.PLATE_X + ChipShopGui.PLATE_W; x++) {
                cream += at(art, x, y) == (0xFF000000 | DerbyGuiArt.CREAM) ? 1 : 0;
            }
        }
        assertTrue(cream > 30, "plate text pixels: " + cream);
        // row 5 stays felt where the buttons and the numbers are items: no frame at 45-53
        assertNotEquals(0xFF000000 | -15391202, at(art, DerbyGuiArt.slotX(47) + 8, DerbyGuiArt.slotY(47) - 1));
    }

    @Test
    void theSixOverlaysDifferOnlyInRowZero() {
        BufferedImage overlay = image("assets/kaboofcasino/textures/font/chipshop/overlay.png");
        BufferedImage plain = compose(-1);
        List<BufferedImage> tabs = new ArrayList<>();
        for (int t = 0; t < ChipShopGui.TABS; t++) {
            JsonObject g = glyph("chipshop_overlay_" + t);
            assertEquals(ChipShopGui.tabX(t) - ChipShopGui.OVERLAY_PAD, g.get("x").getAsInt(), "tab " + t);
            assertEquals(ChipShopGui.TAB_Y - ChipShopGui.OVERLAY_PAD, g.get("y").getAsInt(), "tab " + t);
            assertEquals(overlay.getWidth(), g.get("width").getAsInt());
            tabs.add(compose(t));
        }
        // row 0 (the tabs' 18 rows and the ring's pixel above and below), inside the tab bar and the ring around it
        int rowTop = ChipShopGui.TAB_Y - ChipShopGui.OVERLAY_PAD;
        int rowBottom = ChipShopGui.TAB_Y + ChipShopGui.TAB_W + ChipShopGui.OVERLAY_PAD;
        for (int t = 0; t < ChipShopGui.TABS; t++) {
            BufferedImage a = tabs.get(t);
            int changed = 0;
            for (int y = 0; y < DerbyGuiArt.ART_H; y++) {
                for (int x = 0; x < DerbyGuiArt.ART_W; x++) {
                    int wx = x + DerbyGuiArt.ART_X;
                    int wy = y + DerbyGuiArt.ART_Y;
                    boolean own = wx >= ChipShopGui.overlayX(t) && wx < ChipShopGui.overlayX(t) + overlay.getWidth()
                            && wy >= rowTop && wy < rowBottom;
                    if (a.getRGB(x, y) != plain.getRGB(x, y)) {
                        changed++;
                        assertTrue(own, "tab " + t + " changes " + wx + "," + wy);
                    }
                    for (int u = t + 1; u < ChipShopGui.TABS; u++) {
                        if (a.getRGB(x, y) != tabs.get(u).getRGB(x, y)) {
                            assertTrue(wy >= rowTop && wy < rowBottom, "tabs " + t + "/" + u + " at " + wx + "," + wy);
                        }
                    }
                }
            }
            assertTrue(changed > 100, "tab " + t + " changes only " + changed + " pixels");
            // the selected tab: a gold ring all round and the lit fill under the icon's corner
            int x = ChipShopGui.tabX(t);
            assertEquals(0xFF000000 | DerbyGuiArt.GOLD_LINE, at(a, x - 1, ChipShopGui.TAB_Y - 1));
            assertEquals(0xFF000000 | DerbyGuiArt.GOLD_LINE, at(a, x + ChipShopGui.TAB_W, ChipShopGui.TAB_Y + 17));
            assertEquals(0xFF000000 | -12619437, at(a, x + 3, ChipShopGui.TAB_Y + 8));
        }
    }

    /** Window rectangle {x0, y0, x1, y1} of element {@code i} of a GUI model drawn from {@code slot}. */
    private static double[] windowRect(JsonObject model, int i, int slot) {
        JsonObject gui = model.getAsJsonObject("display").getAsJsonObject("gui");
        double s = gui.getAsJsonArray("scale").get(0).getAsDouble();
        double tx = gui.getAsJsonArray("translation").get(0).getAsDouble();
        double ty = gui.getAsJsonArray("translation").get(1).getAsDouble();
        JsonObject e = model.getAsJsonArray("elements").get(i).getAsJsonObject();
        double[] from = DecoDisplayBake.doubles(e.getAsJsonArray("from"));
        double[] to = DecoDisplayBake.doubles(e.getAsJsonArray("to"));
        int sx = BlackjackGuiItems.slotX(slot);
        int sy = BlackjackGuiItems.slotY(slot);
        return new double[] {sx + 8 + tx + s * (from[0] - 8), sy + 8 - ty - s * (to[1] - 8),
                sx + 8 + tx + s * (to[0] - 8), sy + 8 - ty - s * (from[1] - 8)};
    }

    @Test
    void theQuantityToggleSitsInSlot47AndTheCashiersButtonsAreReused() {
        int x = DerbyGuiArt.slotX(ChipShopGui.QTY_SLOT) - 1;
        int y = DerbyGuiArt.slotY(ChipShopGui.QTY_SLOT) - 1;
        for (String name : new String[] {"qty_one", "qty_stack"}) {
            JsonObject def = json(ChipShopGui.ITEM_DIR + name + ".json");
            assertTrue(def.get("oversized_in_gui").getAsBoolean(), name);
            assertEquals(ChipShopGui.MODEL_ID + name, def.getAsJsonObject("model").get("model").getAsString());
            JsonObject model = json(ChipShopGui.MODEL_DIR + name + ".json");
            assertEquals(1, model.getAsJsonArray("elements").size());
            assertArrayEquals(new double[] {x, y, x + 18, y + 18}, windowRect(model, 0, ChipShopGui.QTY_SLOT), 1e-9);
            assertEquals(18, image(ChipShopGui.TEXTURE_DIR + name + ".png").getWidth());
        }
        assertTrue(text.width(ChipShopGui.QTY_ONE) <= 14 && text.width(ChipShopGui.QTY_STACK) <= 14);
        // between the next page button (46) and KAUFEN (48-51)
        assertTrue(x >= DerbyGuiArt.slotX(CashierGui.NEXT_SLOT) + 17 && x + 18 <= CashierGui.COMMIT_X);
        // the cashier's items, drawn from the slots the chip shop puts them in
        for (String id : ChipShopGui.REUSED) {
            String name = id.substring(id.lastIndexOf('/') + 1);
            JsonObject def = json(CashierGui.ITEM_DIR + name + ".json");
            assertTrue(def.get("oversized_in_gui").getAsBoolean(), id);
        }
        assertEquals(45, CashierGui.PREV_SLOT);
        assertEquals(46, CashierGui.NEXT_SLOT);
        assertEquals(49, CashierGui.COMMIT_ART);
        assertArrayEquals(new int[] {48, 49, 50, 51}, CashierGui.COMMIT_SLOTS);
        assertEquals(53, CashierGui.NUM_A_SLOT);
        assertEquals(52, CashierGui.NUM_B_SLOT);
        JsonObject buy = json(CashierGui.MODEL_DIR + "buy_on.json");
        assertArrayEquals(new double[] {CashierGui.COMMIT_X, CashierGui.COMMIT_Y,
            CashierGui.COMMIT_X + CashierGui.COMMIT_W, CashierGui.COMMIT_Y + CashierGui.COMMIT_H},
            windowRect(buy, 0, CashierGui.COMMIT_ART), 1e-9);
    }
}
