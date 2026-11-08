package de.tigges.kaboofcasino.packtool;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
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
 * icon also an items-atlas sprite for the plugin's text sprites), every denomination its own colours, the mod's
 * colours really the mod's, the names in all 12 languages, byte-identical output, and all of it in the built pack.
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
            JsonObject model = definition.getAsJsonObject("model");
            assertEquals("minecraft:model", model.get("type").getAsString());
            assertEquals("kaboofcasino:chip_" + d, model.get("model").getAsString());
            JsonObject json = Json.parse(tree.get("assets/kaboofcasino/models/chip_" + d + ".json"));
            assertEquals("minecraft:item/generated", json.get("parent").getAsString());
            assertEquals("kaboofcasino:item/chip_" + d,
                    json.getAsJsonObject("textures").get("layer0").getAsString());
            assertTrue(tree.has(texture(d)), texture(d));
            // the id the plugin's text sprite names in the minecraft:items atlas
            assertEquals("kaboofcasino:item/chip_" + d, TexturePadding.atlasTextureId(texture(d)));
        }
        assertEquals(6 * 3 + 12, tree.size(), tree.files().keySet().toString());
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
        assertEquals(16, ChipPack.FACE.length);
        for (int y = 0; y < 16; y++) {
            assertEquals(16, ChipPack.FACE[y].length(), "row " + y);
            for (int x = 0; x < 16; x++) {
                char c = ChipPack.FACE[y].charAt(x);
                assertTrue(".LODHBKIR".indexOf(c) >= 0, "role " + c);
                // the disc spans 1..15 around (8, 8): shape, inserts and ring are mirror-symmetric
                if (x >= 1 && y >= 1) {
                    char mirrorX = ChipPack.FACE[y].charAt(16 - x);
                    char mirrorY = ChipPack.FACE[16 - y].charAt(x);
                    assertEquals(c == '.', mirrorX == '.', x + "," + y);
                    assertEquals(c == '.', mirrorY == '.', x + "," + y);
                    if (c == 'I' || c == 'R') {
                        assertEquals(c, mirrorX, x + "," + y);
                        assertEquals(c, mirrorY, x + "," + y);
                    }
                } else {
                    assertEquals('.', c, "row 0 and column 0 stay clear");
                }
            }
        }
        // four inserts of 6 px each, an inlay ring
        long inserts = String.join("", ChipPack.FACE).chars().filter(c -> c == 'I').count();
        assertEquals(24, inserts);
        assertTrue(String.join("", ChipPack.FACE).indexOf('R') >= 0);
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
