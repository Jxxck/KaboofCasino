package de.tigges.kaboofcasino.packtool;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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

/**
 * The blackjack models: every dealer face, re-baked with an own port of the 26.3 {@code FaceBakery} rules
 * ({@code FaceInfo} vertex order, {@code CuboidFace.UVs} corners, {@code Quadrant} shift), carries exactly the texels
 * the mod's {@code ModelPart.Cube} gives that corner; the composite texture; the 53 cards.
 */
class BlackjackModelsTest {

    private static PackTree tree;
    private static ModJar jar;

    @BeforeAll
    static void build() {
        jar = ModJar.open(Path.of(System.getProperty("kaboofcasino.modJar")),
                System.getProperty("kaboofcasino.modJarSha1"));
        tree = new PackTree();
        BlackjackPack.add(jar, tree, Path.of(System.getProperty("kaboofcasino.packSrc")));
    }

    /** {@code FaceInfo} (26.3, lines 11-45): per direction the 4 vertices as (x, y, z) picks, 0 = min, 1 = max. */
    private static final Map<String, int[][]> FACE_INFO = Map.of(
            "down", new int[][] {{0, 0, 1}, {0, 0, 0}, {1, 0, 0}, {1, 0, 1}},
            "up", new int[][] {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}},
            "north", new int[][] {{1, 1, 0}, {1, 0, 0}, {0, 0, 0}, {0, 1, 0}},
            "south", new int[][] {{0, 1, 1}, {0, 0, 1}, {1, 0, 1}, {1, 1, 1}},
            "west", new int[][] {{0, 1, 0}, {0, 0, 0}, {0, 0, 1}, {0, 1, 1}},
            "east", new int[][] {{1, 1, 1}, {1, 0, 1}, {1, 0, 0}, {1, 1, 0}});

    /** Baked corners {x, y, z, u, v} of a JSON face ({@code CuboidFace.getU/getV} with the quadrant shift). */
    private static double[][] bake(String dir, double[] from, double[] to, JsonObject face) {
        JsonArray uv = face.getAsJsonArray("uv");
        double minU = uv.get(0).getAsDouble();
        double minV = uv.get(1).getAsDouble();
        double maxU = uv.get(2).getAsDouble();
        double maxV = uv.get(3).getAsDouble();
        int shift = face.has("rotation") ? face.get("rotation").getAsInt() / 90 : 0;
        double[][] out = new double[4][];
        for (int i = 0; i < 4; i++) {
            int[] pick = FACE_INFO.get(dir)[i];
            int index = (i + shift) % 4;
            out[i] = new double[] {pick[0] == 1 ? to[0] : from[0], pick[1] == 1 ? to[1] : from[1],
                pick[2] == 1 ? to[2] : from[2], index != 0 && index != 1 ? maxU : minU,
                index != 0 && index != 3 ? maxV : minV};
        }
        return out;
    }

    private static double[] vec(JsonArray a) {
        return new double[] {a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()};
    }

    private static JsonArray elements(String path) {
        return Json.parse(tree.get("assets/kaboofcasino/models/" + path + ".json")).getAsJsonArray("elements");
    }

    @Test
    void everyDealerCornerCarriesTheModelPartTexel() {
        int checked = 0;
        for (BlackjackDealerModel.Part part : BlackjackDealerModel.PARTS) {
            JsonArray elements = elements(BlackjackDealerModel.path(part));
            assertEquals(part.cubes().size(), elements.size(), part.name());
            for (int c = 0; c < part.cubes().size(); c++) {
                JsonObject element = elements.get(c).getAsJsonObject();
                double[] from = vec(element.getAsJsonArray("from"));
                double[] to = vec(element.getAsJsonArray("to"));
                JsonObject faces = element.getAsJsonObject("faces");
                assertEquals(6, faces.size(), part.name());
                for (BlackjackDealerModel.Polygon p : BlackjackDealerModel.polygons(part.cubes().get(c))) {
                    // the polygon's 4 corners in model units: (8 + x, 8 - y, 8 - z); UV texels / 4
                    boolean matched = false;
                    for (Map.Entry<String, JsonElement> f : faces.entrySet()) {
                        double[][] baked = bake(f.getKey(), from, to, f.getValue().getAsJsonObject());
                        int hits = 0;
                        for (int i = 0; i < 4; i++) {
                            double[] m = {8 + p.pos()[i][0], 8 - p.pos()[i][1], 8 - p.pos()[i][2]};
                            for (double[] b : baked) {
                                if (Math.abs(b[0] - m[0]) < 1e-4 && Math.abs(b[1] - m[1]) < 1e-4
                                        && Math.abs(b[2] - m[2]) < 1e-4
                                        && Math.abs(b[3] - p.uv()[i][0] / 4.0) < 1e-4
                                        && Math.abs(b[4] - p.uv()[i][1] / 4.0) < 1e-4) {
                                    hits++;
                                }
                            }
                        }
                        matched |= hits == 4;
                    }
                    assertTrue(matched, part.name() + " cube " + c);
                    checked++;
                }
            }
        }
        assertEquals(6 * 10, checked);
    }

    @Test
    void dealerSpotChecks() {
        // the face (ModelPart north, texels u 8..16, v 8..18) looks south, unmirrored
        JsonObject head = elements("blackjack/dealer_head").get(0).getAsJsonObject().getAsJsonObject("faces");
        assertEquals("[2.0,2.0,4.0,4.5]", head.getAsJsonObject("south").get("uv").toString());
        // the mirrored left arm: its outer (+x, east) face shows the west strip u 28..32 mirrored
        JsonObject left = elements("blackjack/dealer_left_arm").get(0).getAsJsonObject().getAsJsonObject("faces");
        assertEquals("[8.0,12.5,7.0,14.0]", left.getAsJsonObject("east").get("uv").toString());
        // the jacket is inflated by 0.5 px, the forearms shrunk by 0.04 px in x
        JsonObject jacket = elements("blackjack/dealer_torso").get(1).getAsJsonObject();
        assertEquals("[3.5,-0.5,4.5]", jacket.get("from").toString());
        assertEquals("[12.5,20.5,11.5]", jacket.get("to").toString());
        JsonObject forearm = elements("blackjack/dealer_right_forearm").get(0).getAsJsonObject();
        assertEquals("[6.04,2.0,6.0]", forearm.get("from").toString());
    }

    @Test
    void everyModelStaysInTheItemModelRange() {
        for (Map.Entry<String, byte[]> e : tree.files().entrySet()) {
            if (!e.getKey().startsWith("assets/kaboofcasino/models/blackjack/")) {
                continue;
            }
            for (JsonElement el : Json.parse(e.getValue()).getAsJsonArray("elements")) {
                for (String corner : new String[] {"from", "to"}) {
                    for (double v : vec(el.getAsJsonObject().getAsJsonArray(corner))) {
                        assertTrue(v >= -16 && v <= 32, e.getKey());
                    }
                }
            }
        }
    }

    @Test
    void theDealerTextureIsTheCutoutCompositeInDrawOrder() {
        BufferedImage composite = Images.read(tree.get(BlackjackDealerModel.TEXTURE_PATH));
        BufferedImage[] layers = new BufferedImage[5];
        for (int i = 0; i < 5; i++) {
            layers[i] = Images.read(jar.bytes("assets/roulettecasino/textures/entity/"
                    + BlackjackDealerModel.LAYERS.get(i) + ".png"));
        }
        int opaque = 0;
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                int expected = 0;
                for (int i = 0; i < 5; i++) {
                    int argb = layers[i].getRGB(x, y);
                    if (argb >>> 24 >= 26) {
                        expected = 0xFF000000 | argb & 0xFFFFFF;
                    }
                }
                // the arms layer (drawn last, not inflated) never meets the inflated outfit layers 1..3, so the
                // draw order is also the depth order
                if (layers[4].getRGB(x, y) >>> 24 >= 26) {
                    for (int i = 1; i <= 3; i++) {
                        assertTrue(layers[i].getRGB(x, y) >>> 24 < 26, "arms over layer " + i + " at " + x + "," + y);
                    }
                }
                assertEquals(expected, composite.getRGB(x, y), x + "," + y);
                opaque += expected != 0 ? 1 : 0;
            }
        }
        assertEquals(64 * 64 - 1572, opaque);
    }

    @Test
    void fiftyThreeTwoSidedCards() {
        int cards = 0;
        for (String path : tree.files().keySet()) {
            if (path.matches("assets/kaboofcasino/models/blackjack/card_.*\\.json")) {
                cards++;
                assertTrue(tree.has(path.replace("/models/", "/items/")), path);
            }
        }
        assertEquals(53, cards);
        // the king of suit 3: cell column 12, row 3; the face up, the back (column 13, row 0) down
        JsonObject faces = elements("blackjack/card_51").get(0).getAsJsonObject().getAsJsonObject("faces");
        double[][] up = bake("up", new double[] {5.5, 7.97, 4.8}, new double[] {10.5, 8.03, 11.2},
                faces.getAsJsonObject("up"));
        // the top-left texel of the cell lies at (-x, -z), i.e. corner (5.5, 4.8)
        assertArrayEquals(new double[] {5.5, 8.03, 4.8, (12 * 25 + 0.02) / 350.0 * 16, (3 * 32 + 0.02) / 128.0 * 16},
                up[0], 1e-4);
        double[][] down = bake("down", new double[] {5.5, 7.97, 4.8}, new double[] {10.5, 8.03, 11.2},
                faces.getAsJsonObject("down"));
        // down vertex 1 is (min x, min z): the back cell's top-left
        assertArrayEquals(new double[] {5.5, 7.97, 4.8, (13 * 25 + 0.02) / 350.0 * 16, 0.02 / 128.0 * 16},
                down[1], 1e-4);
        assertTrue(tree.has(BlackjackCardModels.SHEET_PATH));
    }

    @Test
    void tableModelsHaveItemDefinitions() {
        for (int part = 0; part < 8; part++) {
            assertTrue(tree.has("assets/roulettecasino/items/block/blackjack_" + part + ".json"));
            assertTrue(tree.has("assets/roulettecasino/models/block/blackjack_" + part + ".json"));
        }
        for (String name : List.of("torso", "head", "right_arm", "right_forearm", "left_arm", "left_forearm",
                "right_leg", "left_leg")) {
            assertTrue(tree.has("assets/kaboofcasino/items/blackjack/dealer_" + name + ".json"), name);
        }
    }
}
