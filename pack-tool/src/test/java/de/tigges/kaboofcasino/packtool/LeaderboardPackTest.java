package de.tigges.kaboofcasino.packtool;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The leaderboards' pack part (step 10): the three display models and the two items exist, every element stays in the
 * model space and in its cells, the text areas the plugin draws on are blank felt with nothing in front of it, the
 * LEDs spell the header words, every texture resolves; deterministic.
 */
class LeaderboardPackTest {

    private static ModJar jar;
    private static PackTree tree;

    private static PackTree build() {
        PackTree t = new PackTree();
        LeaderboardPack.add(jar, t, Path.of(System.getProperty("kaboofcasino.packSrc")));
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

    private static JsonObject model(String id) {
        return json("assets/kaboofcasino/models/" + id.substring("kaboofcasino:".length()) + ".json");
    }

    private static double[] from(JsonElement e) {
        return DecoDisplayBake.doubles(e.getAsJsonObject().getAsJsonArray("from"));
    }

    private static double[] to(JsonElement e) {
        return DecoDisplayBake.doubles(e.getAsJsonObject().getAsJsonArray("to"));
    }

    private static String texture(JsonElement e) {
        return e.getAsJsonObject().getAsJsonObject("faces").entrySet().iterator().next().getValue().getAsJsonObject()
                .get("texture").getAsString();
    }

    /** Every element inside the box {x0, y0, z0, x1, y1, z1} (and the client's -16..32). */
    private static void inside(JsonObject model, double... box) {
        for (JsonElement e : model.getAsJsonArray("elements")) {
            double[] f = from(e);
            double[] t = to(e);
            for (int i = 0; i < 3; i++) {
                assertTrue(f[i] < t[i], e.toString());
                assertTrue(f[i] >= -16 && t[i] <= 32, e.toString());
                assertTrue(f[i] >= box[i] - 1e-9 && t[i] <= box[i + 3] + 1e-9, i + ": " + e);
            }
        }
    }

    @Test
    void deterministic() {
        PackTree again = build();
        assertEquals(tree.files().keySet(), again.files().keySet());
        for (Map.Entry<String, byte[]> e : tree.files().entrySet()) {
            assertArrayEquals(e.getValue(), again.get(e.getKey()), e.getKey());
        }
    }

    @Test
    void theDisplayModelsAndTheirItemDefinitionsExist() {
        for (String id : List.of(LeaderboardPack.WALL, LeaderboardPack.COLUMN_POST, LeaderboardPack.COLUMN_BOARD)) {
            String path = id.substring("kaboofcasino:".length());
            assertEquals(id, json("assets/kaboofcasino/items/" + path + ".json").getAsJsonObject("model")
                    .get("model").getAsString());
            assertTrue(model(id).getAsJsonArray("elements").size() > 4, id);
        }
        assertEquals("kaboofcasino:leaderboard/wall", LeaderboardPack.WALL);
        assertEquals("kaboofcasino:leaderboard/column_post", LeaderboardPack.COLUMN_POST);
        assertEquals("kaboofcasino:leaderboard/column_board", LeaderboardPack.COLUMN_BOARD);
    }

    @Test
    void theItemsShowTheirIconEverywhere() {
        for (String id : List.of(LeaderboardPack.WALL_ITEM, LeaderboardPack.COLUMN_ITEM)) {
            String name = id.substring("kaboofcasino:".length());
            JsonObject definition = json("assets/kaboofcasino/items/" + name + ".json").getAsJsonObject("model");
            assertEquals("minecraft:model", definition.get("type").getAsString());
            assertEquals("kaboofcasino:item/" + name, definition.get("model").getAsString());
            JsonObject model = json("assets/kaboofcasino/models/item/" + name + ".json");
            assertEquals("minecraft:item/generated", model.get("parent").getAsString());
            assertEquals("kaboofcasino:item/" + name, model.getAsJsonObject("textures").get("layer0").getAsString());
            BufferedImage icon = Images.read(tree.get("assets/kaboofcasino/textures/item/" + name + ".png"));
            assertEquals(32, icon.getWidth());
            assertEquals(32, icon.getHeight());
            int opaque = 0;
            for (int y = 0; y < 32; y++) {
                for (int x = 0; x < 32; x++) {
                    int a = icon.getRGB(x, y) >>> 24;
                    assertTrue(a == 0 || a == 255, name + " " + x + "," + y);
                    opaque += a == 255 ? 1 : 0;
                }
            }
            assertTrue(opaque > 200, name + ": " + opaque);
        }
    }

    @Test
    void everythingStaysInItsCells() {
        // wall: 3 x 2 cells from the bottom middle cell, the back on the wall, at most 3 deep
        inside(model(LeaderboardPack.WALL), -16, 0, 0, 32, 32, 3);
        // the column's post: its own cell
        inside(model(LeaderboardPack.COLUMN_POST), 0, 0, 0, 16, 16, 16);
        // the column's board: 1.5 wide, 2 high, its body z 6..10, the frame's relief less than a pixel in front
        inside(model(LeaderboardPack.COLUMN_BOARD), -4, -8, 6, 20, 24, 11);
        // the post's pole meets the board's middle (x 8, z 8)
        JsonElement pole = model(LeaderboardPack.COLUMN_POST).getAsJsonArray("elements").get(3);
        assertArrayEquals(new double[] {7, 3.5, 7}, from(pole), 1e-9);
        assertArrayEquals(new double[] {9, 15, 9}, to(pole), 1e-9);
    }

    @Test
    void theTextAreasAreTheContractsAndBlankFelt() {
        assertArrayEquals(new double[] {-14, 2, 30, 25}, LeaderboardPack.WALL_BOARD.textArea(), 1e-9);
        assertEquals(2, LeaderboardPack.WALL_BOARD.felt());
        assertArrayEquals(new double[] {-2, -6, 18, 17}, LeaderboardPack.COLUMN.textArea(), 1e-9);
        assertEquals(10, LeaderboardPack.COLUMN.felt());
        for (LeaderboardPack.Board board : List.of(LeaderboardPack.WALL_BOARD, LeaderboardPack.COLUMN)) {
            JsonObject model = model("kaboofcasino:" + board.path());
            double[] area = board.textArea();
            boolean covered = false;
            for (JsonElement e : model.getAsJsonArray("elements")) {
                double[] f = from(e);
                double[] t = to(e);
                boolean overlaps = f[0] < area[2] && t[0] > area[0] && f[1] < area[3] && t[1] > area[1];
                if (!overlaps) {
                    continue;
                }
                // the contract: nothing in front of the felt face + 0.5; in fact nothing in front of the felt at all
                assertTrue(t[2] <= board.felt() + 0.5, board.path() + " " + e);
                assertTrue(t[2] <= board.felt(), board.path() + " " + e);
                if (texture(e).equals("#panel")) {
                    covered = f[0] <= area[0] && t[0] >= area[2] && f[1] <= area[1] && t[1] >= area[3]
                            && t[2] == board.felt();
                    assertTrue(e.getAsJsonObject().getAsJsonObject("faces").has("south"));
                }
            }
            assertTrue(covered, board.path() + ": the felt face covers the text area");
            // the felt texture is plain felt inside the text area (the LED backing is only in the header)
            String id = board.panelTexture();
            BufferedImage panel = Images.read(tree.get("assets/kaboofcasino/textures/"
                    + id.substring(id.indexOf(':') + 1) + ".png"));
            double[] felt = board.feltRect();
            assertEquals((felt[2] - felt[0]) * LeaderboardPack.TEXELS, panel.getWidth(), 1e-9);
            assertEquals((felt[3] - felt[1]) * LeaderboardPack.TEXELS, panel.getHeight(), 1e-9);
            assertEquals(0, panel.getWidth() % 16, "no padding needed");
            assertEquals(0, panel.getHeight() % 16, "no padding needed");
            int headerRows = (int) Math.round((felt[3] - area[3]) * LeaderboardPack.TEXELS);
            int backing = 0;
            for (int y = 0; y < panel.getHeight(); y++) {
                for (int x = 0; x < panel.getWidth(); x++) {
                    int p = panel.getRGB(x, y);
                    if (y >= headerRows) {
                        assertEquals(LeaderboardPack.FELT, p, board.path() + " " + x + "," + y);
                    } else {
                        assertTrue(p == LeaderboardPack.FELT || p == LeaderboardPack.BACKING);
                        backing += p == LeaderboardPack.BACKING ? 1 : 0;
                    }
                }
            }
            assertTrue(backing > 100, board.path() + " backing " + backing);
        }
    }

    @Test
    void theLedsSpellTheHeaderWordsAndGlow() {
        assertEquals("BESTENLISTE", LeaderboardPack.WALL_WORD);
        assertEquals("TOP 10", LeaderboardPack.COLUMN_WORD);
        // the letters the cashier's KASSE shares stay the same
        for (char c : new char[] {'S', 'E'}) {
            assertArrayEquals(CashierPack.LETTERS.get(c), LeaderboardPack.LETTERS.get(c), String.valueOf(c));
        }
        boolean[][] wall = LeaderboardPack.leds(LeaderboardPack.WALL_WORD);
        assertEquals(7, wall.length);
        assertEquals(11 * 6 - 1, wall[0].length);
        boolean[][] column = LeaderboardPack.leds(LeaderboardPack.COLUMN_WORD);
        assertEquals(5 * 6 + 3 + 1 - 1, column[0].length);
        StringBuilder top = new StringBuilder();
        for (boolean b : column[0]) {
            top.append(b ? '#' : '.');
        }
        // T O P, the 3-column space, 1 0
        assertEquals("#####..###..####........#....###.", top.toString());
        for (LeaderboardPack.Board board : List.of(LeaderboardPack.WALL_BOARD, LeaderboardPack.COLUMN)) {
            boolean[][] grid = LeaderboardPack.leds(board.word());
            int lit = 0;
            for (boolean[] row : grid) {
                for (boolean b : row) {
                    lit += b ? 1 : 0;
                }
            }
            int leds = 0;
            int bulbs = 0;
            double[] felt = board.feltRect();
            double[] area = board.textArea();
            for (JsonElement e : model("kaboofcasino:" + board.path()).getAsJsonArray("elements")) {
                String t = texture(e);
                double[] f = from(e);
                double[] to = to(e);
                if (t.equals("#led")) {
                    leds++;
                    // in the header's felt, above the divider, standing on the felt
                    assertTrue(f[0] >= felt[0] && to[0] <= felt[2], e.toString());
                    assertTrue(f[1] >= area[3] + LeaderboardPack.DIVIDER && to[1] <= felt[3], e.toString());
                    assertEquals(board.felt(), f[2], 1e-9);
                }
                if (t.equals("#bulb")) {
                    bulbs++;
                    // on the brass band, outside the felt
                    boolean onBand = to[0] <= felt[0] || f[0] >= felt[2] || to[1] <= felt[1] || f[1] >= felt[3];
                    assertTrue(onBand, e.toString());
                    assertTrue(f[0] >= board.x0() + LeaderboardPack.RIM && to[0] <= board.x1() - LeaderboardPack.RIM
                            && f[1] >= board.y0() + LeaderboardPack.RIM && to[1] <= board.y1() - LeaderboardPack.RIM,
                            e.toString());
                }
                if (t.equals("#led") || t.equals("#bulb")) {
                    assertEquals(15, e.getAsJsonObject().get("light_emission").getAsInt(), e.toString());
                } else {
                    assertTrue(!e.getAsJsonObject().has("light_emission"), e.toString());
                }
            }
            assertEquals(lit, leds, board.path());
            assertEquals(LeaderboardPack.bulbs(board).size(), bulbs, board.path());
            assertTrue(bulbs >= 24, board.path() + " bulbs " + bulbs);
        }
    }

    @Test
    void everyTextureResolves() {
        for (String id : List.of(LeaderboardPack.WALL, LeaderboardPack.COLUMN_POST, LeaderboardPack.COLUMN_BOARD)) {
            JsonObject model = model(id);
            JsonObject textures = model.getAsJsonObject("textures");
            for (Map.Entry<String, JsonElement> t : textures.entrySet()) {
                String tex = t.getValue().getAsString();
                String path = "assets/" + tex.substring(0, tex.indexOf(':')) + "/textures/"
                        + tex.substring(tex.indexOf(':') + 1) + ".png";
                assertTrue(tree.has(path), id + " " + path);
            }
            for (JsonElement e : model.getAsJsonArray("elements")) {
                for (Map.Entry<String, JsonElement> face : e.getAsJsonObject().getAsJsonObject("faces").entrySet()) {
                    String ref = face.getValue().getAsJsonObject().get("texture").getAsString();
                    assertTrue(textures.has(ref.substring(1)), id + " " + ref);
                }
            }
        }
    }
}
