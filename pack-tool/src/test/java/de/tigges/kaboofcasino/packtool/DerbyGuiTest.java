package de.tigges.kaboofcasino.packtool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The duck derby GUI art and item geometry against the mod's felt, texts and the bet menu's slots. */
class DerbyGuiTest {

    private static ModJar jar;
    private static BufferedImage felt;
    private static BufferedImage bg;
    private static TextGlyphs text;
    private static JsonObject lang;
    private static PackTree tree;

    @BeforeAll
    static void open() {
        jar = ModJar.open(Path.of(System.getProperty("kaboofcasino.modJar")),
                System.getProperty("kaboofcasino.modJarSha1"));
        felt = Images.read(jar.bytes("assets/roulettecasino/textures/gui/duck_derby_felt.png"));
        bg = DerbyGuiArt.background(felt);
        text = TextGlyphs.load(Path.of(System.getProperty("kaboofcasino.packSrc")).resolve("vanilla"));
        lang = Json.parse(jar.bytes("assets/roulettecasino/lang/de_de.json"));
        tree = new PackTree();
        DerbyGui.add(jar, tree, new GuiFont(tree), text);
    }

    private static int at(int windowX, int windowY) {
        return bg.getRGB(windowX - DerbyGuiArt.ART_X, windowY - DerbyGuiArt.ART_Y);
    }

    /** A removed felt column/row holds felt only (or continues the line of its neighbour). */
    private static boolean plainColumn(int x, int reference) {
        for (int y = 0; y < 266; y++) {
            int p = felt.getRGB(x, y);
            if (!Lettering.felt(p) && p != felt.getRGB(reference, y)) {
                return false;
            }
        }
        return true;
    }

    private static boolean plainRow(int y, int reference) {
        for (int x = 0; x < 366; x++) {
            int p = felt.getRGB(x, y);
            if ((p >>> 24) != 0 && !Lettering.felt(p) && p != felt.getRGB(x, reference)) {
                return false;
            }
        }
        return true;
    }

    @Test
    void theFeltLosesOnlyPlainColumnsAndRows() {
        for (int x = DerbyGuiArt.LEFT_CUT[0]; x < DerbyGuiArt.LEFT_CUT[0] + DerbyGuiArt.LEFT_CUT[1]; x++) {
            assertTrue(plainColumn(x, 60), "left column " + x);
        }
        for (int x = DerbyGuiArt.RIGHT_CUT[0]; x < DerbyGuiArt.RIGHT_CUT[0] + DerbyGuiArt.RIGHT_CUT[1]; x++) {
            assertTrue(plainColumn(x, 260), "right column " + x);
        }
        for (int[] cut : DerbyGuiArt.ROW_CUTS) {
            for (int y = cut[0]; y < cut[0] + cut[1]; y++) {
                assertTrue(plainRow(y, 60), "row " + y);
            }
        }
    }

    /** The gold box (mod 5..218 x 19..161) frames cards, bets, buttons and the lobby strip; the panels end at 229. */
    @Test
    void panelsAroundTheChest() {
        assertEquals(DerbyGuiArt.ART_W, bg.getWidth());
        assertEquals(DerbyGuiArt.ART_H, bg.getHeight());
        assertEquals(-12, DerbyGuiArt.windowY(19));
        assertEquals(125, DerbyGuiArt.windowY(159));
        assertEquals(229, DerbyGuiArt.windowY(265));
        // box corners and borders where the layout puts them
        assertEquals(59, DerbyGuiArt.windowY(90));
        assertEquals(felt.getRGB(6, 90), at(-8, 59));
        assertEquals(felt.getRGB(217, 90), at(183, 59));
        assertEquals(felt.getRGB(74, 20), at(60, -11));
        assertEquals(felt.getRGB(74, 160), at(60, 126));
        // the transparent gap and the right panel's frame
        for (int x = 190; x < DerbyGuiArt.RIGHT_X; x++) {
            assertEquals(0, at(x, 50) >>> 24, "gap column " + x);
        }
        assertEquals(felt.getRGB(232, 90), at(DerbyGuiArt.RIGHT_X, 59));
        assertEquals(felt.getRGB(365, 90), at(DerbyGuiArt.RIGHT_X + DerbyGuiArt.RIGHT_W - 1, 59));
        // the art covers the whole chest
        for (int y = 0; y < 222; y++) {
            for (int x = 0; x < 176; x++) {
                assertEquals(0xFF, at(x, y) >>> 24, "chest pixel " + x + "," + y);
            }
        }
    }

    /** Bet and inventory frames ({@code DerbyUiArt.slot}) sit exactly around the vanilla item areas. */
    @Test
    void slotFramesOnTheGrid() {
        for (int i = 0; i < 12; i++) {
            int slot = DerbyGuiArt.betSlot(i);
            int x = DerbyGuiArt.slotX(slot);
            int y = DerbyGuiArt.slotY(slot);
            assertEquals(0xFF000000 | -15391202, at(x - 1, y - 1), "bet " + i);
            assertEquals(0xFF000000 | -8285828, at(x + 16, y + 16), "bet " + i);
            assertEquals(0xFF000000 | -11970227, at(x + 8, y + 8), "bet " + i);
        }
        assertEquals(List.of(9, 12, 14, 17, 18, 21, 23, 26, 27, 30, 32, 35), java.util.stream.IntStream.range(0, 12)
                .map(DerbyGuiArt::betSlot).boxed().toList());
        for (int c = 0; c < 9; c++) {
            assertEquals(0xFF000000 | -15391202, at(7 + 18 * c, 139), "inventory column " + c);
            assertEquals(0xFF000000 | -15391202, at(7 + 18 * c, 197), "hotbar column " + c);
        }
    }

    /**
     * The cards: 42x37 at twice the resolution, the bet column under each card (4 px off its centre), 2 px between
     * the middle pair; SCHWARZ shrunk into its banner like {@code CasinoText.fitted}.
     */
    @Test
    void cards() {
        for (int lane = 0; lane < 4; lane++) {
            BufferedImage card = DerbyGuiArt.card(text, lane, 2);
            assertEquals(84, card.getWidth());
            assertEquals(74, card.getHeight());
            int column = DerbyGuiArt.slotX(DerbyGuiArt.betSlot(lane));
            int centre = DerbyGuiArt.CARD_X[lane] + 21;
            assertEquals(4, Math.abs(column + 8 - centre), "lane " + lane);
            // the name stays inside the banner's inner columns (texels 2..81)
            for (int y = 50; y < 72; y++) {
                assertEquals(card.getRGB(0, 60), card.getRGB(0, y), "left border row " + y);
                assertEquals(card.getRGB(83, 60), card.getRGB(83, y), "right border row " + y);
            }
        }
        assertEquals(2, DerbyGuiArt.CARD_X[2] - (DerbyGuiArt.CARD_X[1] + DerbyGuiArt.CARD_W));
        assertEquals("SCHWARZ", DerbyGui.DUCKS[3]);
        assertTrue(text.width("SCHWARZ") > 40, "SCHWARZ needs the mod's shrinking");
    }

    /** {@code DuckDerbyScreen.veiled} on the mod's name colours. */
    @Test
    void veiledColours() {
        int veil = DerbyGuiArt.VEIL;
        for (int lane = 0; lane < 4; lane++) {
            int rgb = DerbyGuiArt.NAME_COLOR[lane];
            int v = DerbyGuiArt.veiled(rgb);
            for (int shift = 0; shift <= 16; shift += 8) {
                int under = rgb >> shift & 0xFF;
                int over = veil >> shift & 0xFF;
                assertEquals(under + (over - under) * 180 / 255, v >> shift & 0xFF);
            }
        }
    }

    /** Every derby model fits the client's limits (elements -16..32, scale <= 4, translation +-80). */
    @Test
    void modelsFitTheClientLimits() {
        int models = 0;
        for (Map.Entry<String, byte[]> e : tree.files().entrySet()) {
            if (!e.getKey().startsWith(DerbyGuiItems.MODEL_DIR)) {
                continue;
            }
            models++;
            JsonObject model = Json.parse(e.getValue());
            JsonObject gui = model.getAsJsonObject("display").getAsJsonObject("gui");
            assertTrue(Math.abs(gui.getAsJsonArray("scale").get(0).getAsDouble()) <= 4, e.getKey());
            JsonArray t = gui.getAsJsonArray("translation");
            for (JsonElement v : t) {
                assertTrue(Math.abs(v.getAsDouble()) <= 80, e.getKey());
            }
            for (JsonElement el : model.getAsJsonArray("elements")) {
                for (String k : List.of("from", "to")) {
                    for (JsonElement v : el.getAsJsonObject().getAsJsonArray(k)) {
                        assertTrue(v.getAsDouble() >= -16 && v.getAsDouble() <= 32, e.getKey() + " " + k);
                    }
                }
            }
        }
        assertTrue(models > 100, "only " + models + " derby models");
    }

    /** The mod's German texts fit their rooms: hints in two lines, buttons, the help panel. */
    @Test
    void textsFit() {
        assertTrue(DerbyGui.wrap(text, DerbyGui.lang(lang, "several"), DerbyGui.HINT_ROOM).size() <= 2);
        assertTrue(DerbyGui.wrap(text, DerbyGui.lang(lang, "pick"), DerbyGui.HINT_ROOM).size() <= 2);
        assertTrue(DerbyGui.wrap(text, "Deine Ente: SCHWARZ", DerbyGui.HINT_ROOM).size() <= 2);
        for (String key : List.of("solo", "group", "confirm", "withdraw", "claim")) {
            assertTrue(text.width(DerbyGui.lang(lang, key)) <= DerbyGui.BUTTON_W - 8, key);
        }
        BufferedImage help = DerbyGui.helpPanel(text, lang);
        assertEquals(DerbyGui.HELP_PANEL_W, help.getWidth());
        // the panel stays above the inventory (vanilla's label at 128) and inside the 427 px screen
        assertTrue(DerbyGui.HELP_PANEL_Y + DerbyGui.HELP_PANEL_H <= 125);
        assertTrue(DerbyGui.HELP_PANEL_X + DerbyGui.HELP_PANEL_W <= 301);
        assertTrue(DerbyGui.HELP_PANEL_Y >= -9);
    }

    /** The layout's text rows: only the brand's (shared with the other headlines at -8). */
    @Test
    void onlyTheBrandIsTitleText() {
        assertEquals(1, DerbyGui.anchors().size());
        assertEquals(-8, DerbyGui.anchors().get(0).y());
    }
}
