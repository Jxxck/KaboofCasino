package de.tigges.kaboofcasino.packtool;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The display decoration's pack part: the uv-locked stair bake (template UVs, every state's shape against vanilla's
 * stair rule), the merged sign and chandelier models (inside the model range, every part present), one display model
 * per blockstate model, the placing items and their names.
 */
class DecoDisplayPackTest {

    private static ModJar jar;
    private static PackTree tree;

    @BeforeAll
    static void build() {
        jar = ModJar.open(Path.of(System.getProperty("kaboofcasino.modJar")),
                System.getProperty("kaboofcasino.modJarSha1"));
        tree = new PackTree();
        DecoDisplayPack.add(jar, tree, Path.of(System.getProperty("kaboofcasino.packSrc")));
    }

    /** Every face of the three vanilla templates uses the default UVs of its bounds (what uvlock reproduces). */
    @Test
    void templatesUseDefaultUvs() {
        for (String parent : DecoDisplayBake.TEMPLATES.keySet()) {
            for (JsonElement e : DecoDisplayBake.template(parent)) {
                JsonObject el = e.getAsJsonObject();
                double[] from = DecoDisplayBake.doubles(el.getAsJsonArray("from"));
                double[] to = DecoDisplayBake.doubles(el.getAsJsonArray("to"));
                for (Map.Entry<String, JsonElement> f : el.getAsJsonObject("faces").entrySet()) {
                    assertArrayEquals(DecoDisplayBake.defaultUv(from, to, f.getKey()),
                            DecoDisplayBake.doubles(f.getValue().getAsJsonObject().getAsJsonArray("uv")), 1e-9,
                            parent + " " + f.getKey());
                }
            }
        }
    }

    @Test
    void turnMatchesBlockModelRotation() {
        // y 90: north (8, 8, 0) -> east (16, 8, 8); x 180: up -> down
        assertArrayEquals(new double[] {16, 8, 8}, DecoDisplayBake.turn(new double[] {8, 8, 0}, 0, 90), 1e-9);
        assertEquals("east", DecoDisplayBake.turnDirection("north", 0, 90));
        assertEquals("down", DecoDisplayBake.turnDirection("up", 180, 0));
        assertEquals("south", DecoDisplayBake.turnDirection("north", 180, 0));
        assertEquals("east", DecoDisplayBake.turnDirection("north", 180, 270));
    }

    /**
     * Every baked state occupies exactly the octants of vanilla's stair rule: the half's slab plus the step on the
     * facing side; outer = facing side and the left (counter-clockwise) or right (clockwise) side, inner = both
     * sides; the top half mirrored vertically.
     */
    @Test
    void everyBakedStairStateHasVanillasShape() {
        int checked = 0;
        for (String id : DecoDisplayPack.DECO_IDS) {
            if (!id.endsWith("_stairs")) {
                continue;
            }
            String family = id.substring(0, id.length() - 7);
            JsonObject variants = Json.parse(jar.bytes("assets/casinodeco/blockstates/" + id + ".json"))
                    .getAsJsonObject("variants");
            for (String key : variants.keySet()) {
                String state = DecoDisplayPack.stateKey(key);
                String[] p = state.split("_", 3);
                String child = "assets/kaboofcasino/models/deco/stairs/" + family + "_" + state + "_0.json";
                assertTrue(tree.has(child), child);
                JsonObject model = Json.parse(tree.get(child));
                String parent = model.get("parent").getAsString().substring("kaboofcasino:".length());
                JsonArray elements = Json.parse(tree.get("assets/kaboofcasino/models/" + parent + ".json"))
                        .getAsJsonArray("elements");
                assertEquals(expectedOctants(p[0], p[1], p[2]), octants(elements), id + " " + key);
                assertTrue(PackTreeHas.item(tree, "kaboofcasino:deco/stairs/" + family + "_" + state + "_0"));
                checked++;
            }
        }
        assertEquals(7 * 40, checked);
    }

    private static Set<String> octants(JsonArray elements) {
        Set<String> out = new TreeSet<>();
        for (int ox = 0; ox < 2; ox++) {
            for (int oy = 0; oy < 2; oy++) {
                for (int oz = 0; oz < 2; oz++) {
                    double cx = ox * 8 + 4;
                    double cy = oy * 8 + 4;
                    double cz = oz * 8 + 4;
                    for (JsonElement e : elements) {
                        double[] f = DecoDisplayBake.doubles(e.getAsJsonObject().getAsJsonArray("from"));
                        double[] t = DecoDisplayBake.doubles(e.getAsJsonObject().getAsJsonArray("to"));
                        if (cx > f[0] && cx < t[0] && cy > f[1] && cy < t[1] && cz > f[2] && cz < t[2]) {
                            out.add(ox + "" + oy + oz);
                            break;
                        }
                    }
                }
            }
        }
        return out;
    }

    private static Set<String> expectedOctants(String facing, String half, String shape) {
        int slab = half.equals("bottom") ? 0 : 1;
        int step = 1 - slab;
        Set<String> out = new TreeSet<>();
        for (int ox = 0; ox < 2; ox++) {
            for (int oz = 0; oz < 2; oz++) {
                out.add(ox + "" + slab + oz);
                boolean front = side(facing, ox, oz);
                boolean left = side(ccw(facing), ox, oz);
                boolean right = side(cw(facing), ox, oz);
                boolean in = switch (shape) {
                    case "straight" -> front;
                    case "outer_left" -> front && left;
                    case "outer_right" -> front && right;
                    case "inner_left" -> front || left;
                    case "inner_right" -> front || right;
                    default -> throw new IllegalStateException(shape);
                };
                if (in) {
                    out.add(ox + "" + step + oz);
                }
            }
        }
        return out;
    }

    private static boolean side(String dir, int ox, int oz) {
        return switch (dir) {
            case "east" -> ox == 1;
            case "west" -> ox == 0;
            case "south" -> oz == 1;
            case "north" -> oz == 0;
            default -> throw new IllegalStateException(dir);
        };
    }

    private static String cw(String d) {
        return switch (d) {
            case "north" -> "east";
            case "east" -> "south";
            case "south" -> "west";
            default -> "north";
        };
    }

    private static String ccw(String d) {
        return cw(cw(cw(d)));
    }

    /** The marble stairs have four random variants per state, the rest one; textures follow the mod's models. */
    @Test
    void stairVariantsAndTextures() {
        JsonObject v3 = Json.parse(tree.get("assets/kaboofcasino/models/deco/stairs/ivory_marble_east_top_"
                + "inner_left_3.json"));
        assertEquals("casinodeco:block/ivory_marble_v3", v3.getAsJsonObject("textures").get("top").getAsString());
        assertTrue(!tree.has("assets/kaboofcasino/models/deco/stairs/ruby_velvet_east_top_inner_left_1.json"));
        assertTrue(tree.has("assets/casinodeco/textures/block/ivory_marble_v3.png"));
    }

    /** Merged models stay inside the model coordinate range [-16, 32] and contain every part. */
    @Test
    void mergedModels() {
        for (String name : new String[] {"casino_sign", "chandelier_body"}) {
            JsonObject model = Json.parse(tree.get("assets/kaboofcasino/models/deco/" + name + ".json"));
            JsonArray elements = model.getAsJsonArray("elements");
            int parts = name.equals("casino_sign") ? 6 : 9;
            int expected = 0;
            for (int p = 0; p < parts; p++) {
                expected += Json.parse(jar.bytes("assets/casinodeco/models/block/"
                        + (name.equals("casino_sign") ? "sign_" : "chandelier_") + p + ".json"))
                        .getAsJsonArray("elements").size();
            }
            assertEquals(expected, elements.size(), name);
            double min = Double.MAX_VALUE;
            double max = -Double.MAX_VALUE;
            for (JsonElement e : elements) {
                JsonObject el = e.getAsJsonObject();
                for (String k : new String[] {"from", "to"}) {
                    for (double v : DecoDisplayBake.doubles(el.getAsJsonArray(k))) {
                        assertTrue(v >= -16 && v <= 32, name + " " + v);
                        min = Math.min(min, v);
                        max = Math.max(max, v);
                    }
                }
            }
            assertTrue(PackTreeHas.item(tree, "kaboofcasino:deco/" + name));
            if (name.equals("chandelier_body")) {
                // half size about the centre: the whole 3 x 3 body (-16.3 .. 32.3 px) in 8 +- 12.2
                assertTrue(min > -4.2 && max < 20.2, min + " " + max);
            }
        }
    }

    /** One display model per block model of the blockstates, the 41 placing items, their names in every language. */
    @Test
    void displayModelsItemsAndNames() {
        assertTrue(PackTreeHas.item(tree, "kaboofcasino:deco/sofa_a0"));
        assertTrue(PackTreeHas.item(tree, "kaboofcasino:deco/speaker_ceiling_on"));
        assertTrue(PackTreeHas.item(tree, "kaboofcasino:deco/black_marble_bar_top_v2"));
        assertTrue(PackTreeHas.item(tree, "kaboofcasino:deco/gold_veined_black_marble_slab_top_v1"));
        assertTrue(PackTreeHas.item(tree, "kaboofcasino:deco/casino_chair"));
        assertTrue(!PackTreeHas.item(tree, "kaboofcasino:deco/sign_0"), "merged parts are not shipped alone");
        for (String id : DecoDisplayPack.DECO_IDS) {
            assertTrue(tree.has("assets/casinodeco/items/" + id + ".json"), id);
        }
        assertTrue(tree.has("assets/roulettecasino/items/casino_chair.json"));
        assertTrue(tree.has("assets/casinosound/items/casino_speaker.json"));
        assertEquals(41, DecoDisplayPack.LANG_PREFIXES.size());
        // the glass models' sprite objects became plain sprite ids
        JsonObject sconce = Json.parse(tree.get("assets/casinodeco/models/block/chandelier_wall_sconce.json"));
        for (Map.Entry<String, JsonElement> t : sconce.getAsJsonObject("textures").entrySet()) {
            assertTrue(t.getValue().isJsonPrimitive(), t.getKey());
        }
    }

    /** Small helper: an item definition exists in the tree. */
    static final class PackTreeHas {
        private PackTreeHas() {
        }

        static boolean item(PackTree tree, String key) {
            int colon = key.indexOf(':');
            return tree.has("assets/" + key.substring(0, colon) + "/items/" + key.substring(colon + 1) + ".json");
        }
    }
}
