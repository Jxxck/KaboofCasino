package de.tigges.kaboofcasino.packtool;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The chips' pack part: an item definition, an {@code item/generated} model and a 16x16 icon per denomination (the
 * icon also an items-atlas sprite for the plugin's text sprites), the value badge only in the GUI case of the item
 * definition and in front of the icon, every denomination its own colours and its own insert shape, the mod's colours
 * really the mod's, the names in all 12 languages, byte-identical output, and all of it in the built pack.
 */
class ChipPackTest {

    private static ModJar jar;
    private static PackTree tree;

    @BeforeAll
    static void build() {
        jar = ModJar.open(Path.of(System.getProperty("kaboofcasino.modJar")),
                System.getProperty("kaboofcasino.modJarSha1"));
        tree = add();
    }

    private static PackTree add() {
        PackTree t = new PackTree();
        ChipPack.add(jar, t, Path.of(System.getProperty("kaboofcasino.packSrc")));
        return t;
    }

    private static String texture(int d) {
        return "assets/kaboofcasino/textures/item/chip_" + d + ".png";
    }

    @Test
    void sixDenominationsLikeThePlugin() {
        assertArrayEquals(new int[] {1, 5, 25, 100, 500, 1000}, ChipPack.DENOMINATIONS);
        assertTrue(ChipPack.SOUND_EVENTS.isEmpty());
        assertTrue(ChipPack.LANG_PREFIXES.isEmpty());
    }

    @Test
    void everyChipHasAnItemDefinitionAModelAndAnIcon() {
        for (int d : ChipPack.DENOMINATIONS) {
            JsonObject definition = Json.parse(tree.get("assets/kaboofcasino/items/chip_" + d + ".json"));
            JsonObject fallback = definition.getAsJsonObject("model").getAsJsonObject("fallback");
            assertEquals("minecraft:model", fallback.get("type").getAsString());
            assertEquals("kaboofcasino:chip_" + d, fallback.get("model").getAsString());
            JsonObject json = Json.parse(tree.get("assets/kaboofcasino/models/chip_" + d + ".json"));
            assertEquals("minecraft:item/generated", json.get("parent").getAsString());
            assertEquals("kaboofcasino:item/chip_" + d,
                    json.getAsJsonObject("textures").get("layer0").getAsString());
            assertTrue(tree.has(texture(d)), texture(d));
            // the id the plugin's text sprite names in the minecraft:items atlas
            assertEquals("kaboofcasino:item/chip_" + d, TexturePadding.atlasTextureId(texture(d)));
        }
        // per chip: icon, model, badge texture, badge model, item definition; 12 language files
        assertEquals(6 * 5 + 12, tree.size(), tree.files().keySet().toString());
    }

    @Test
    void iconsAre16x16RgbaOpaqueOrClear() throws IOException {
        for (int d : ChipPack.DENOMINATIONS) {
            BufferedImage png = ImageIO.read(new ByteArrayInputStream(tree.get(texture(d))));
            assertEquals(16, png.getWidth(), texture(d));
            assertEquals(16, png.getHeight(), texture(d));
            assertTrue(png.getColorModel().hasAlpha(), texture(d));
            assertEquals(4, png.getColorModel().getNumComponents(), texture(d));
            int opaque = 0;
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int a = png.getRGB(x, y) >>> 24;
                    assertTrue(a == 0 || a == 255, texture(d) + " alpha " + a + " at " + x + "," + y);
                    opaque += a == 255 ? 1 : 0;
                }
            }
            assertEquals(0, png.getRGB(0, 0) >>> 24, "corner clear");
            assertEquals(0, png.getRGB(15, 15) >>> 24, "corner clear");
            assertTrue(opaque > 150 && opaque < 200, texture(d) + ": " + opaque + " opaque pixels");
            assertArrayEquals(Images.write(ChipPack.icon(d)), tree.get(texture(d)));
        }
    }

    @Test
    void theFaceIsASymmetricDisc() {
        for (int d : ChipPack.DENOMINATIONS) {
            String[] face = ChipPack.face(d);
            assertEquals(16, face.length);
            for (int y = 0; y < 16; y++) {
                assertEquals(16, face[y].length(), "row " + y);
                assertEquals(16, ChipPack.INSERTS.get(d)[y].length(), d + " insert row " + y);
                for (int x = 0; x < 16; x++) {
                    char c = face[y].charAt(x);
                    assertTrue(".LODHBKIR".indexOf(c) >= 0, "role " + c);
                    assertTrue(ChipPack.BASE[y].charAt(x) != 'I', "the base has no inserts");
                    // the disc spans 1..15 around (8, 8): shape, inserts and ring are mirror-symmetric
                    if (x >= 1 && y >= 1) {
                        char mirrorX = face[y].charAt(16 - x);
                        char mirrorY = face[16 - y].charAt(x);
                        assertEquals(c == '.', mirrorX == '.', d + ": " + x + "," + y);
                        assertEquals(c == '.', mirrorY == '.', d + ": " + x + "," + y);
                        if (c == 'I' || c == 'R') {
                            assertEquals(c, mirrorX, d + ": " + x + "," + y);
                            assertEquals(c, mirrorY, d + ": " + x + "," + y);
                        }
                    } else {
                        assertEquals('.', c, "row 0 and column 0 stay clear");
                    }
                    // inserts only in the body band between rim and inlay ring
                    if (c == 'I') {
                        assertTrue("HBK".indexOf(ChipPack.BASE[y].charAt(x)) >= 0, d + ": " + x + "," + y);
                        double r = Math.hypot(x - 8, y - 8);
                        assertTrue(r > 3.5 && r < 6.5, d + ": insert at radius " + r);
                    }
                }
            }
            assertTrue(String.join("", face).indexOf('R') >= 0, "an inlay ring");
            assertTrue(String.join("", face).indexOf('I') >= 0, "inserts");
        }
    }

    /** Insert pixels of a denomination as a 16x16 mask. */
    private static boolean[][] inserts(int d) {
        boolean[][] mask = new boolean[16][16];
        String[] face = ChipPack.face(d);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                mask[y][x] = face[y].charAt(x) == 'I';
            }
        }
        return mask;
    }

    /** The sizes of the 8-connected insert pieces, sorted: the shape of the inserts regardless of colour. */
    private static List<Integer> pieces(boolean[][] mask) {
        boolean[][] seen = new boolean[16][16];
        List<Integer> out = new ArrayList<>();
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                if (!mask[y][x] || seen[y][x]) {
                    continue;
                }
                int size = 0;
                ArrayDeque<int[]> todo = new ArrayDeque<>();
                todo.add(new int[] {x, y});
                seen[y][x] = true;
                while (!todo.isEmpty()) {
                    int[] p = todo.poll();
                    size++;
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            int nx = p[0] + dx;
                            int ny = p[1] + dy;
                            if (nx >= 0 && ny >= 0 && nx < 16 && ny < 16 && mask[ny][nx] && !seen[ny][nx]) {
                                seen[ny][nx] = true;
                                todo.add(new int[] {nx, ny});
                            }
                        }
                    }
                }
                out.add(size);
            }
        }
        out.sort(null);
        return out;
    }

    /**
     * Colour-blind players and the hand / ground (no badge there) tell the chips apart by their inserts: every
     * denomination's insert mask differs from every other's in at least 10 pixels, and the pieces differ in number or
     * size (the shapes, not just their places).
     */
    @Test
    void everyDenominationHasItsOwnInsertShape() {
        Map<Integer, List<Integer>> shapes = new HashMap<>();
        for (int d : ChipPack.DENOMINATIONS) {
            shapes.put(d, pieces(inserts(d)));
        }
        // 1: four small ticks; 5: four long caps; 25: eight short; 100: two wide stripes; 500: a dotted ring of 14;
        // 1000: one full ring
        assertEquals(List.of(2, 2, 2, 2), shapes.get(1));
        assertEquals(List.of(10, 10, 10, 10), shapes.get(5));
        assertEquals(List.of(2, 2, 2, 2, 3, 3, 3, 3), shapes.get(25));
        assertEquals(List.of(19, 19), shapes.get(100));
        assertEquals(14, shapes.get(500).size());
        assertTrue(shapes.get(500).stream().allMatch(n -> n == 1));
        assertEquals(List.of(28), shapes.get(1000));
        for (int i = 0; i < ChipPack.DENOMINATIONS.length; i++) {
            for (int j = i + 1; j < ChipPack.DENOMINATIONS.length; j++) {
                int a = ChipPack.DENOMINATIONS[i];
                int b = ChipPack.DENOMINATIONS[j];
                assertFalse(shapes.get(a).equals(shapes.get(b)), a + " and " + b + " have the same insert shape");
                boolean[][] ma = inserts(a);
                boolean[][] mb = inserts(b);
                int differ = 0;
                for (int y = 0; y < 16; y++) {
                    for (int x = 0; x < 16; x++) {
                        differ += ma[y][x] != mb[y][x] ? 1 : 0;
                    }
                }
                assertTrue(differ >= 10, a + " vs " + b + ": " + differ + " pixels differ");
            }
        }
    }

    /** The 26.3 client's {@code ItemDisplayContext} names (net.minecraft.world.item.ItemDisplayContext). */
    private static final Set<String> DISPLAY_CONTEXTS = Set.of("none", "thirdperson_lefthand",
            "thirdperson_righthand", "firstperson_lefthand", "firstperson_righthand", "head", "gui", "ground", "fixed",
            "on_shelf");

    private static List<String> whens(JsonObject select) {
        List<String> out = new ArrayList<>();
        for (JsonElement c : select.getAsJsonArray("cases")) {
            JsonElement when = c.getAsJsonObject().get("when");
            if (when.isJsonArray()) {
                when.getAsJsonArray().forEach(w -> out.add(w.getAsString()));
            } else {
                out.add(when.getAsString());
            }
        }
        return out;
    }

    /** Model ids an item model tree can draw. */
    private static void modelIds(JsonElement node, List<String> out) {
        if (node.isJsonObject()) {
            JsonObject o = node.getAsJsonObject();
            if (o.has("type") && o.get("type").getAsString().equals("minecraft:model")) {
                out.add(o.get("model").getAsString());
            }
            o.entrySet().forEach(e -> modelIds(e.getValue(), out));
        } else if (node.isJsonArray()) {
            node.getAsJsonArray().forEach(e -> modelIds(e, out));
        }
    }

    @Test
    void theBadgeIsOnlyInTheGuiCase() {
        for (int d : ChipPack.DENOMINATIONS) {
            String chip = "kaboofcasino:chip_" + d;
            JsonObject select = Json.parse(tree.get("assets/kaboofcasino/items/chip_" + d + ".json"))
                    .getAsJsonObject("model");
            assertEquals("minecraft:select", select.get("type").getAsString());
            assertEquals("minecraft:display_context", select.get("property").getAsString());
            assertTrue(DISPLAY_CONTEXTS.containsAll(whens(select)), whens(select).toString());
            assertEquals(List.of("gui"), whens(select));
            // the GUI: the chip, then the badge over it
            JsonObject composite = select.getAsJsonArray("cases").get(0).getAsJsonObject().getAsJsonObject("model");
            assertEquals("minecraft:composite", composite.get("type").getAsString());
            List<String> gui = new ArrayList<>();
            modelIds(composite, gui);
            assertEquals(List.of(chip, chip + "_badge"), gui);
            // every other context (hands, ground, item frame, shelf, head): the chip alone
            List<String> elsewhere = new ArrayList<>();
            modelIds(select.getAsJsonObject("fallback"), elsewhere);
            assertEquals(List.of(chip), elsewhere);
            assertTrue(tree.has("assets/kaboofcasino/models/chip_" + d + "_badge.json"));
        }
    }

    /**
     * The badge model is one flat quad in front of the icon's front face ({@code item/generated} layers span z
     * 7.5..8.5), facing the viewer (south), its texels 1:1 over the icon's top-left corner, lit like the icon.
     */
    @Test
    void theBadgeDrawsInFrontOfTheIcon() {
        for (int d : ChipPack.DENOMINATIONS) {
            JsonObject model = Json.parse(tree.get("assets/kaboofcasino/models/chip_" + d + "_badge.json"));
            assertFalse(model.has("parent"));
            assertFalse(model.has("display"), "no display transform, like item/generated in the GUI");
            assertEquals("front", model.get("gui_light").getAsString());
            JsonArray elements = model.getAsJsonArray("elements");
            assertEquals(1, elements.size());
            JsonObject element = elements.get(0).getAsJsonObject();
            assertEquals(Set.of("south"), element.getAsJsonObject("faces").keySet());
            double[] from = DecoDisplayBake.doubles(element.getAsJsonArray("from"));
            double[] to = DecoDisplayBake.doubles(element.getAsJsonArray("to"));
            assertEquals(from[2], to[2]);
            assertTrue(from[2] > 8.5 && from[2] < 9.5, "z " + from[2]);
            assertEquals(0, from[0]);
            assertEquals(16, to[1]);
            assertEquals(ChipPack.badgeWidth(d), to[0]);
            assertEquals(16 - ChipPack.BADGE_H, from[1]);
            double[] uv = DecoDisplayBake.doubles(element.getAsJsonObject("faces").getAsJsonObject("south")
                    .getAsJsonArray("uv"));
            assertArrayEquals(new double[] {0, 0, to[0] - from[0], to[1] - from[1]}, uv);
            assertEquals("kaboofcasino:item/chip_" + d + "_badge", model.getAsJsonObject("textures").get("badge")
                    .getAsString());
            assertEquals("kaboofcasino:item/chip_" + d, model.getAsJsonObject("textures").get("particle")
                    .getAsString());
        }
    }

    /** Reads a badge back: cream numerals 1 px apart, a one-pixel outline, nothing else. */
    @Test
    void theBadgeShowsTheValueInOutlined3x5Numerals() throws IOException {
        Map<Integer, String> expected = Map.of(1, "1", 5, "5", 25, "25", 100, "100", 500, "500", 1000, "1K");
        for (int d : ChipPack.DENOMINATIONS) {
            BufferedImage png = ImageIO.read(new ByteArrayInputStream(tree.get(
                    "assets/kaboofcasino/textures/item/chip_" + d + "_badge.png")));
            assertEquals(16, png.getWidth());
            assertEquals(16, png.getHeight());
            int w = ChipPack.badgeWidth(d);
            assertTrue(w <= 16);
            StringBuilder read = new StringBuilder();
            for (int i = 0; 1 + 4 * i + 3 <= w - 1; i++) {
                String[] cell = new String[5];
                for (int y = 0; y < 5; y++) {
                    StringBuilder row = new StringBuilder();
                    for (int x = 0; x < 3; x++) {
                        row.append(png.getRGB(1 + 4 * i + x, 1 + y) == ChipPack.BADGE_CREAM ? '#' : '.');
                    }
                    cell[y] = row.toString();
                }
                for (Map.Entry<Character, String[]> n : ChipPack.NUMERALS.entrySet()) {
                    if (Arrays.equals(n.getValue(), cell)) {
                        read.append(n.getKey());
                    }
                }
            }
            assertEquals(expected.get(d), read.toString());
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int p = png.getRGB(x, y);
                    boolean nearCream = false;
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            int nx = x + dx;
                            int ny = y + dy;
                            nearCream |= nx >= 0 && ny >= 0 && nx < 16 && ny < 16 && (dx != 0 || dy != 0)
                                    && png.getRGB(nx, ny) == ChipPack.BADGE_CREAM;
                        }
                    }
                    boolean inBadge = x < w && y < ChipPack.BADGE_H;
                    if (p == ChipPack.BADGE_CREAM) {
                        assertTrue(inBadge && x > 0 && y > 0 && x < w - 1 && y < ChipPack.BADGE_H - 1);
                    } else if (nearCream) {
                        assertEquals(ChipPack.BADGE_OUTLINE, p, d + ": outline at " + x + "," + y);
                    } else {
                        assertEquals(0, p >>> 24, d + ": clear at " + x + "," + y);
                    }
                }
            }
            // the top-left corner is the badge's
            assertEquals(ChipPack.BADGE_OUTLINE, png.getRGB(1, 0), d + "");
        }
        assertEquals(0xFFF3E7BF, ChipPack.BADGE_CREAM);
        for (String[] n : ChipPack.NUMERALS.values()) {
            assertEquals(5, n.length);
            Arrays.stream(n).forEach(row -> assertEquals(3, row.length()));
        }
        assertTrue(colours("assets/roulettecasino/textures/gui/roulette.png").contains(ChipPack.BADGE_OUTLINE),
                "the outline is the mod's chip black");
    }

    @Test
    void everyDenominationHasItsOwnColours() {
        List<int[]> bodies = new ArrayList<>();
        Set<String> pngs = new HashSet<>();
        for (int d : ChipPack.DENOMINATIONS) {
            BufferedImage icon = ChipPack.icon(d);
            int body = icon.getRGB(8, 8);
            assertEquals(ChipPack.palette(d).body(), body);
            bodies.add(new int[] {body >> 16 & 255, body >> 8 & 255, body & 255});
            pngs.add(Arrays.toString(tree.get(texture(d))));
        }
        assertEquals(6, pngs.size());
        for (int i = 0; i < bodies.size(); i++) {
            for (int j = i + 1; j < bodies.size(); j++) {
                int[] a = bodies.get(i);
                int[] b = bodies.get(j);
                double distance = Math.sqrt(Math.pow(a[0] - b[0], 2) + Math.pow(a[1] - b[1], 2)
                        + Math.pow(a[2] - b[2], 2));
                assertTrue(distance > 80, ChipPack.DENOMINATIONS[i] + " vs " + ChipPack.DENOMINATIONS[j] + ": "
                        + distance);
            }
        }
        // white, red, green, black, purple, gold
        double[] white = ChipPack.hsl(ChipPack.palette(1).body());
        double[] red = ChipPack.hsl(ChipPack.palette(5).body());
        double[] green = ChipPack.hsl(ChipPack.palette(25).body());
        double[] black = ChipPack.hsl(ChipPack.palette(100).body());
        double[] purple = ChipPack.hsl(ChipPack.palette(500).body());
        double[] gold = ChipPack.hsl(ChipPack.palette(1000).body());
        assertTrue(white[2] > 0.9, "white");
        assertTrue(black[2] < 0.2, "black");
        assertTrue(red[0] > 0.95 || red[0] < 0.03, "red hue " + red[0]);
        assertTrue(green[0] > 0.35 && green[0] < 0.45, "green hue " + green[0]);
        assertTrue(purple[0] > 0.75 && purple[0] < 0.8 && purple[1] > 0.4, "purple " + Arrays.toString(purple));
        assertTrue(gold[0] > 0.1 && gold[0] < 0.13 && gold[1] > 0.5, "gold " + Arrays.toString(gold));
        // the gold chip's rim, ring and inserts stand out of its gold body
        ChipPack.Palette g = ChipPack.palette(1000);
        assertTrue(ChipPack.hsl(g.rim())[2] < gold[2] - 0.2 && ChipPack.hsl(g.insert())[2] > gold[2] + 0.3);
    }

    @Test
    void recolourKeepsTheShadingAndShadeKeepsTheHue() {
        // red's tones turned to purple keep their lightness order and steps
        int[] purple = new int[3];
        for (int i = 0; i < 3; i++) {
            purple[i] = ChipPack.recolour(ChipPack.RED[i], 280, 0.9, 0);
            assertEquals(ChipPack.hsl(ChipPack.RED[i])[2], ChipPack.hsl(purple[i])[2], 0.01);
            assertEquals(280 / 360.0, ChipPack.hsl(purple[i])[0], 0.01);
        }
        assertEquals(0xFF77299E, purple[1]);
        double[] rim = ChipPack.hsl(ChipPack.RIM);
        double[] bronze = ChipPack.hsl(ChipPack.shade(ChipPack.RIM, -0.26, 1.3));
        assertEquals(rim[0], bronze[0], 0.01);
        assertEquals(rim[2] - 0.26, bronze[2], 0.01);
        assertEquals(0xFFBD9954, ChipPack.shade(ChipPack.RIM, 0, 1)); // the round trip is exact
    }

    @Test
    void theModsColoursAreInTheModsTextures() {
        Set<Integer> roulette = colours("assets/roulettecasino/textures/gui/roulette.png");
        for (int c : new int[] {ChipPack.RIM, ChipPack.RIM_LIGHT, ChipPack.RIM_DARK, ChipPack.INSERT,
                ChipPack.RED[1], ChipPack.GREEN[1], ChipPack.BLACK[1]}) {
            assertTrue(roulette.contains(c), Integer.toHexString(c));
        }
        assertTrue(colours("assets/casinodeco/textures/block/casino_chip_pile.png").contains(ChipPack.RING));
        Set<Integer> atlas = colours("assets/casinodeco/textures/block/casino_decor_atlas.png");
        List<Integer> used = new ArrayList<>(List.of(ChipPack.RING, ChipPack.WHITE_INSERT));
        for (int[] tones : List.of(ChipPack.WHITE, ChipPack.RED, ChipPack.GREEN, ChipPack.BLACK)) {
            Arrays.stream(tones).forEach(used::add);
        }
        for (int c : used) {
            assertTrue(atlas.contains(c), Integer.toHexString(c));
        }
    }

    private static Set<Integer> colours(String path) {
        BufferedImage img = Images.read(jar.bytes(path));
        Set<Integer> out = new HashSet<>();
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                out.add(img.getRGB(x, y));
            }
        }
        return out;
    }

    @Test
    void namesInGermanEnglishAndTheOtherTenLanguages() {
        List<String> codes = new ArrayList<>();
        for (String p : jar.list("assets/roulettecasino/lang/")) {
            codes.add(p.substring(p.lastIndexOf('/') + 1, p.length() - 5));
        }
        assertEquals(12, codes.size(), codes.toString());
        for (String code : codes) {
            JsonObject lang = Json.parse(tree.get("assets/kaboofcasino/lang/" + code + ".json"));
            assertEquals(6, lang.size(), code);
            for (int d : ChipPack.DENOMINATIONS) {
                String expected = code.equals("de_de") ? d + "er-Chip" : d + " Chip";
                assertEquals(expected, lang.get("item.kaboofcasino.chip_" + d).getAsString(), code);
            }
        }
        JsonObject de = Json.parse(tree.get("assets/kaboofcasino/lang/de_de.json"));
        assertEquals("25er-Chip", de.get("item.kaboofcasino.chip_25").getAsString());
        assertEquals("1000er-Chip", de.get("item.kaboofcasino.chip_1000").getAsString());
        JsonObject en = Json.parse(tree.get("assets/kaboofcasino/lang/en_us.json"));
        assertEquals("1 Chip", en.get("item.kaboofcasino.chip_1").getAsString());
        assertEquals("1000 Chip", en.get("item.kaboofcasino.chip_1000").getAsString());
    }

    @Test
    void twoBuildsAreByteIdentical() {
        PackTree second = add();
        assertEquals(tree.files().keySet(), second.files().keySet());
        for (Map.Entry<String, byte[]> e : tree.files().entrySet()) {
            assertArrayEquals(e.getValue(), second.files().get(e.getKey()), e.getKey());
        }
    }

    @Test
    void theChipsAreInTheBuiltPack() throws IOException {
        PackTool.Result result = PackTool.build(Path.of(System.getProperty("kaboofcasino.modJar")),
                System.getProperty("kaboofcasino.modJarSha1"), Path.of(System.getProperty("kaboofcasino.packSrc")));
        Map<String, byte[]> zip = new HashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(result.zip()))) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                zip.put(e.getName(), in.readAllBytes());
            }
        }
        for (Map.Entry<String, byte[]> e : tree.files().entrySet()) {
            assertArrayEquals(e.getValue(), zip.get(e.getKey()), e.getKey());
        }
        assertFalse(result.padded().stream().anyMatch(p -> p.id().contains("chip_")), "16x16 needs no padding");
    }
}
