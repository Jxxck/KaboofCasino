package de.tigges.kaboofcasino.packtool;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The cashier's pack part (step 9): the item (icon in the GUI, the mod's register elsewhere), the screen model with
 * the KASSE LEDs inside the cashier's upper row, the menu art (three overlays over the chest grid, the receipt's
 * anchors, buttons and number displays); deterministic.
 */
class CashierPackTest {

    private static ModJar jar;
    private static PackTree tree;
    private static GuiFont font;
    private static TextGlyphs text;

    private static PackTree build() {
        PackTree t = new PackTree();
        TextGlyphs glyphs = TextGlyphs.load(Path.of(System.getProperty("kaboofcasino.packSrc")).resolve("vanilla"));
        GuiFont f = new GuiFont(t);
        DecoDisplayPack.add(jar, t, Path.of(System.getProperty("kaboofcasino.packSrc")));
        CashierPack.add(jar, t, Path.of(System.getProperty("kaboofcasino.packSrc")));
        CashierGui.add(jar, t, f, glyphs);
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

    @Test
    void deterministic() {
        PackTree first = tree;
        PackTree again = build();
        assertEquals(first.files().keySet(), again.files().keySet());
        for (Map.Entry<String, byte[]> e : first.files().entrySet()) {
            assertArrayEquals(e.getValue(), again.get(e.getKey()), e.getKey());
        }
    }

    @Test
    void theItemIsAnIconInTheGuiAndTheRegisterElsewhere() {
        JsonObject select = json("assets/kaboofcasino/items/cashier.json").getAsJsonObject("model");
        assertEquals("minecraft:display_context", select.get("property").getAsString());
        assertEquals("kaboofcasino:item/cashier_icon", select.getAsJsonArray("cases").get(0).getAsJsonObject()
                .getAsJsonObject("model").get("model").getAsString());
        assertEquals("casinodeco:item/cash_register", select.getAsJsonObject("fallback").get("model").getAsString());
        assertTrue(tree.has("assets/casinodeco/models/item/cash_register.json"));
        BufferedImage icon = Images.read(tree.get("assets/kaboofcasino/textures/item/cashier.png"));
        assertEquals(32, icon.getWidth());
        assertEquals(32, icon.getHeight());
    }

    @Test
    void theLedsSpellKasseInTheModsLetters() {
        boolean[][] grid = CashierPack.leds(CashierPack.WORD);
        assertEquals(7, grid.length);
        assertEquals(5 * 6 - 1, grid[0].length);
        StringBuilder top = new StringBuilder();
        for (boolean b : grid[0]) {
            top.append(b ? '#' : '.');
        }
        // K A S S E: the A and the S as small_sign's CASINO has them
        assertEquals("#...#..###...####..####.#####", top.toString());
        // every letter column pair is separated by a dark column
        for (int letter = 1; letter < 5; letter++) {
            for (boolean[] row : grid) {
                assertTrue(!row[letter * 6 - 1], "gap before letter " + letter);
            }
        }
    }

    @Test
    void theScreenStaysInTheUpperRowWithSelfLitLeds() {
        JsonObject model = json("assets/kaboofcasino/models/cashier/screen.json");
        Set<String> textures = new TreeSet<>();
        int leds = 0;
        for (JsonElement e : model.getAsJsonArray("elements")) {
            JsonObject el = e.getAsJsonObject();
            double[] from = DecoDisplayBake.doubles(el.getAsJsonArray("from"));
            double[] to = DecoDisplayBake.doubles(el.getAsJsonArray("to"));
            assertTrue(from[0] >= 0 && to[0] <= 32 && from[1] >= 16 && to[1] <= 32 && from[2] >= 0 && to[2] <= 16,
                    el.toString());
            for (int i = 0; i < 3; i++) {
                assertTrue(from[i] <= to[i] && from[i] >= -16 && to[i] <= 32);
            }
            String texture = el.getAsJsonObject("faces").getAsJsonObject("south").get("texture").getAsString();
            textures.add(texture);
            if (texture.equals("#led") || texture.equals("#bulb")) {
                assertEquals(15, el.get("light_emission").getAsInt());
                leds += texture.equals("#led") ? 1 : 0;
            }
        }
        assertTrue(leds > 20, "LED runs: " + leds);
        for (String t : textures) {
            String id = model.getAsJsonObject("textures").get(t.substring(1)).getAsString();
            String ns = id.substring(0, id.indexOf(':'));
            assertTrue(tree.has("assets/" + ns + "/textures/" + id.substring(id.indexOf(':') + 1) + ".png"), id);
        }
        assertEquals(CashierPack.SCREEN, json("assets/kaboofcasino/items/cashier/screen.json")
                .getAsJsonObject("model").get("model").getAsString());
    }

    @Test
    void theMenuArtSitsOverTheChestAndTheReceipt() {
        Set<String> names = new TreeSet<>();
        JsonObject manifest = font.manifest(text);
        manifest.getAsJsonObject("glyphs").keySet().forEach(names::add);
        for (String tab : CashierGui.TABS) {
            assertTrue(names.contains("cashier_overlay_" + tab), tab);
            JsonObject g = manifest.getAsJsonObject("glyphs").getAsJsonObject("cashier_overlay_" + tab);
            assertEquals(CashierGui.OVERLAY_X, g.get("x").getAsInt());
            assertEquals(CashierGui.OVERLAY_Y, g.get("y").getAsInt());
            assertTrue(g.get("width").getAsInt() <= 256 && g.get("height").getAsInt() <= 256);
        }
        assertTrue(names.contains("cashier_bg_0") && names.contains("cashier_bg_1"));
        JsonObject anchors = manifest.getAsJsonObject("anchors");
        for (String a : new String[] {"cashier_brand", "cashier_head", "cashier_help_0", "cashier_help_4",
                "cashier_label_a", "cashier_label_b"}) {
            assertTrue(anchors.has(a), a);
            int x = anchors.getAsJsonObject(a).get("x").getAsInt();
            assertTrue(x >= DerbyGuiArt.RIGHT_X && x < DerbyGuiArt.RIGHT_X + DerbyGuiArt.RIGHT_W, a);
        }
        // the receipt's numbers: ten cells right-aligned inside the right panel, below their labels
        assertTrue(CashierGui.numLeft() > DerbyGuiArt.RIGHT_X + 4);
        assertTrue(CashierGui.NUM_A_Y > CashierGui.LABEL_A_Y + 8 && CashierGui.NUM_B_Y > CashierGui.LABEL_B_Y + 8);
        for (String name : new String[] {"exchange_on", "exchange_off", "buy_on", "buy_off", "clear", "prev", "next",
                "num_a", "num_b", "marker"}) {
            JsonObject def = json("assets/kaboofcasino/items/gui/cashier/" + name + ".json");
            assertTrue(def.get("oversized_in_gui").getAsBoolean(), name);
        }
        // one digit model per display, cell and digit
        for (String d : new String[] {"a", "b"}) {
            for (int k = 0; k < CashierGui.NUM_CELLS; k++) {
                for (int digit = 0; digit < 10; digit++) {
                    assertTrue(tree.has("assets/kaboofcasino/models/gui/cashier/num_" + d + "_" + k + "_" + digit
                            + ".json"));
                }
            }
        }
        // the tabs' labels fit their buttons, the commit labels theirs
        for (String label : CashierGui.TAB_LABELS) {
            assertTrue(text.width(label) <= CashierGui.TAB_W - 8, label);
        }
        JsonArray models = json("assets/kaboofcasino/models/gui/cashier/exchange_on.json")
                .getAsJsonArray("elements");
        assertEquals(1, models.size());
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

    /**
     * The thousands dots: strings 10, 11, 12 of both number displays are the gaps after cells 2, 5 and 8; "." draws
     * the client's '.' (one cream pixel on the digits' baseline, its shadow right below-right) in the gap's first
     * column, anything else nothing. The digit cells keep their places.
     */
    @Test
    void theThousandsDotsSitInTheGaps() {
        assertEquals(10, CashierGui.NUM_CELLS);
        assertEquals(3, CashierGui.NUM_GAPS);
        BufferedImage dot = Images.read(tree.get(CashierGui.TEXTURE_DIR + "dot.png"));
        BufferedImage glyph = Images.blank(2, CashierGui.NUM_H);
        BlackjackGuiText.draw(glyph, text, ".", 0, 0, DerbyGuiArt.CREAM, false, true);
        BufferedImage digit = Images.read(tree.get(CashierGui.TEXTURE_DIR + "digit_8.png"));
        int baseline = -1;
        for (int y = 0; y < CashierGui.NUM_H; y++) {
            for (int x = 0; x < 6; x++) {
                if (digit.getRGB(x, y) == (0xFF000000 | DerbyGuiArt.CREAM)) {
                    baseline = y;
                }
            }
        }
        assertEquals(6, baseline, "the digits' bottom row");
        for (int y = 0; y < CashierGui.NUM_H; y++) {
            for (int x = 0; x < 2; x++) {
                assertEquals(glyph.getRGB(x, y), dot.getRGB(x, y), x + "," + y);
                boolean lit = x == 0 && y == baseline;
                assertEquals(lit, dot.getRGB(x, y) == (0xFF000000 | DerbyGuiArt.CREAM), x + "," + y);
            }
        }
        assertEquals(BlackjackGuiText.shadow(0xFF000000 | DerbyGuiArt.CREAM), dot.getRGB(1, baseline + 1));
        for (String display : new String[] {"a", "b"}) {
            int slot = display.equals("a") ? CashierGui.NUM_A_SLOT : CashierGui.NUM_B_SLOT;
            int numY = display.equals("a") ? CashierGui.NUM_A_Y : CashierGui.NUM_B_Y;
            JsonArray parts = json(CashierGui.ITEM_DIR + "num_" + display + ".json").getAsJsonObject("model")
                    .getAsJsonArray("models");
            assertEquals(CashierGui.NUM_CELLS + CashierGui.NUM_GAPS, parts.size());
            for (int k = 0; k < CashierGui.NUM_CELLS + CashierGui.NUM_GAPS; k++) {
                JsonObject select = parts.get(k).getAsJsonObject();
                assertEquals("minecraft:custom_model_data", select.get("property").getAsString());
                assertEquals(k, select.get("index").getAsInt());
                assertEquals("minecraft:empty", select.getAsJsonObject("fallback").get("type").getAsString());
                JsonArray cases = select.getAsJsonArray("cases");
                assertEquals(k < CashierGui.NUM_CELLS ? 10 : 1, cases.size());
                if (k < CashierGui.NUM_CELLS) {
                    continue;
                }
                int g = k - CashierGui.NUM_CELLS;
                JsonObject c = cases.get(0).getAsJsonObject();
                assertEquals(".", c.get("when").getAsString());
                String id = c.getAsJsonObject("model").get("model").getAsString();
                assertEquals(CashierGui.MODEL_ID + "num_" + display + "_gap_" + g, id);
                JsonObject model = json(CashierGui.MODEL_DIR + id.substring(CashierGui.MODEL_ID.length()) + ".json");
                assertEquals(2, model.getAsJsonArray("elements").size());
                // the spacer over the whole display (drawn oversized like the digits), then the dot
                assertArrayEquals(new double[] {CashierGui.numLeft(), numY, CashierGui.NUM_RIGHT,
                        numY + CashierGui.NUM_H}, windowRect(model, 0, slot), 1e-9);
                double[] r = windowRect(model, 1, slot);
                assertArrayEquals(new double[] {CashierGui.gapX(g), numY, CashierGui.gapX(g) + 2,
                        numY + CashierGui.NUM_H}, r, 1e-9);
                // the gap: right after cell 3g + 3, right before cell 3g + 2
                assertEquals(CashierGui.cellX(3 * g + 3) + 6, CashierGui.gapX(g));
                assertEquals(CashierGui.cellX(3 * g + 2), CashierGui.gapX(g) + 2);
                assertEquals(CashierGui.TEXTURE_ID + "dot", model.getAsJsonObject("textures").get("t1")
                        .getAsString());
            }
            // the digits' cells where they were
            for (int k = 0; k < CashierGui.NUM_CELLS; k++) {
                JsonObject model = json(CashierGui.MODEL_DIR + "num_" + display + "_" + k + "_8.json");
                assertArrayEquals(new double[] {CashierGui.NUM_RIGHT - 6 * (k + 1) - 2 * (k / 3), numY,
                        CashierGui.NUM_RIGHT - 6 * k - 2 * (k / 3), numY + CashierGui.NUM_H},
                        windowRect(model, 1, slot), 1e-9);
            }
        }
    }
}
