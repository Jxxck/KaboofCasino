package de.tigges.kaboofcasino.packtool;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The display decoration's pack part: the uv-locked stair bake (template UVs, every state's shape against vanilla's
 * stair rule), the merged sign and chandelier models (inside the model range, every part present), one display model
 * per blockstate model (the TVs' per cell and channel), the placing items and their names.
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

    /** A cover's faces keep their exact size and UVs and lie exactly {@code COVER_OUT} outside the carrier's. */
    @Test
    void coverFacesAreExactAndJustOutside() {
        JsonArray exact = DecoDisplayBake.bakeUvLocked(DecoDisplayBake.template("minecraft:block/stairs"), 0, 90);
        JsonArray cover = DecoDisplayBake.cover(exact);
        int index = 0;
        for (JsonElement e : exact) {
            JsonObject el = e.getAsJsonObject();
            double[] from = DecoDisplayBake.doubles(el.getAsJsonArray("from"));
            double[] to = DecoDisplayBake.doubles(el.getAsJsonArray("to"));
            index++; // the box underneath
            for (Map.Entry<String, JsonElement> f : el.getAsJsonObject("faces").entrySet()) {
                JsonObject plane = cover.get(index++).getAsJsonObject();
                double[] a = DecoDisplayBake.doubles(plane.getAsJsonArray("from"));
                double[] b = DecoDisplayBake.doubles(plane.getAsJsonArray("to"));
                int moved = 0;
                for (int axis = 0; axis < 3; axis++) {
                    if (a[axis] == b[axis]) {
                        moved++;
                        double out = f.getKey().equals("east") || f.getKey().equals("up") || f.getKey().equals("south")
                                ? b[axis] - to[axis] : from[axis] - a[axis];
                        assertEquals(DecoDisplayBake.COVER_OUT, out, 1e-9, f.getKey());
                    } else {
                        assertEquals(from[axis], a[axis], 1e-9, f.getKey());
                        assertEquals(to[axis], b[axis], 1e-9, f.getKey());
                    }
                }
                assertEquals(1, moved, f.getKey());
                assertEquals(f.getValue(), plane.getAsJsonObject("faces").get(f.getKey()), f.getKey());
            }
        }
        assertEquals(cover.size(), index);
    }

    /** The stairs and slabs the mod does not have: items, all 40 stair states, the three slab types. */
    @Test
    void ownStairsAndSlabsAreComplete() {
        for (String family : DecoDisplayPack.OWN_FAMILIES) {
            assertTrue(tree.has("assets/casinodeco/items/" + family + "_stairs.json"), family);
            assertTrue(tree.has("assets/casinodeco/models/item/" + family + "_slab.json"), family);
            int states = 0;
            for (String facing : List.of("north", "east", "south", "west")) {
                for (String half : List.of("bottom", "top")) {
                    for (String shape : List.of("straight", "inner_left", "inner_right", "outer_left", "outer_right")) {
                        assertTrue(PackTreeHas.item(tree, "kaboofcasino:deco/stairs/" + family + "_" + facing + "_"
                                + half + "_" + shape + "_0"), shape);
                        states++;
                    }
                }
            }
            assertEquals(40, states);
            for (String slab : List.of(family + "_slab", family + "_slab_top", family)) {
                assertTrue(PackTreeHas.item(tree, "kaboofcasino:deco/" + slab), slab);
            }
        }
        JsonObject side = Json.parse(tree.get("assets/kaboofcasino/models/deco/slab/classic_gilded_dark_oak_paneling_slab.json"))
                .getAsJsonObject("textures");
        assertEquals("minecraft:block/dark_oak_planks", side.get("top").getAsString());
        assertEquals("casinodeco:block/classic_gilded_dark_oak_paneling", side.get("side").getAsString());
    }

    private static String faceTexture(JsonArray elements, double[] from, String dir) {
        for (JsonElement e : elements) {
            JsonObject el = e.getAsJsonObject();
            if (java.util.Arrays.equals(from, DecoDisplayBake.doubles(el.getAsJsonArray("from")))) {
                return el.getAsJsonObject("faces").getAsJsonObject(dir).get("texture").getAsString();
            }
        }
        throw new IllegalStateException("no element at " + java.util.Arrays.toString(from));
    }

    /** Whole faces keep the side texture, half-height ones a 16 x 8 frame, small and half hidden ones an 8 x 8 frame. */
    @Test
    void facesPickTheFrameOfTheirShape() {
        JsonArray straight = DecoDisplayBake.faceRoles(DecoDisplayBake.bakeUvLocked(
                DecoDisplayBake.template("minecraft:block/stairs"), 0, 0));
        double[] low = {0, 0, 0};
        double[] step = {8, 8, 0};
        assertEquals("#side", faceTexture(straight, low, "east"), "the back");
        assertEquals("#side", faceTexture(straight, step, "east"), "the back");
        assertEquals("#half", faceTexture(straight, low, "west"), "lower riser");
        assertEquals("#half", faceTexture(straight, step, "west"), "upper riser");
        assertEquals("#half", faceTexture(straight, low, "north"), "side below");
        assertEquals("#quarter", faceTexture(straight, step, "north"), "side above");
        JsonArray inner = DecoDisplayBake.faceRoles(DecoDisplayBake.bakeUvLocked(
                DecoDisplayBake.template("minecraft:block/inner_stairs"), 0, 0));
        assertEquals("#quarter", faceTexture(inner, step, "west"), "riser half behind the other arm");
        assertEquals("#side", faceTexture(inner, step, "south"), "whole back in two parts");
        assertEquals("#half", faceTexture(DecoDisplayBake.slab(0, 8), low, "north"));
        assertEquals("#side", faceTexture(DecoDisplayBake.slab(0, 16), low, "north"));
    }

    /** Every cell of the trim textures holds a closed gold frame. */
    @Test
    void trimCellsAreClosedFrames() {
        BufferedImage side = Images.read(jar.bytes("assets/casinodeco/textures/block/gilded_dark_oak_paneling.png"));
        BufferedImage half = DecoTrim.half(side, DecoTrim.Pattern.FRAME);
        BufferedImage quarter = DecoTrim.quarter(side, DecoTrim.Pattern.FRAME);
        for (int cell : new int[] {0, 8}) {
            assertEquals(side.getRGB(8, 2), half.getRGB(8, cell + 1), "top bar");
            assertEquals(side.getRGB(8, 13), half.getRGB(8, cell + 6), "bottom bar");
            assertEquals(side.getRGB(3, 2), quarter.getRGB(cell + 1, 1), "corner");
            assertEquals(side.getRGB(12, 13), quarter.getRGB(cell + 6, 6), "corner");
        }
        assertTrue(tree.has("assets/kaboofcasino/textures/block/trim/classic_gilded_dark_oak_paneling_quarter.png"));
    }

    /** Every wall pattern keeps its gold on the small faces, and the patterns are told apart by the block's name. */
    @Test
    void everyWallPatternSurvivesOnSmallFaces() {
        assertEquals(DecoTrim.Pattern.BASEBOARD, DecoTrim.Pattern.of("classic_dark_oak_baseboard"));
        assertEquals(DecoTrim.Pattern.DADO, DecoTrim.Pattern.of("dark_oak_dado_rail"));
        assertEquals(DecoTrim.Pattern.CORNICE, DecoTrim.Pattern.of("dark_oak_cornice"));
        assertEquals(DecoTrim.Pattern.PILASTER, DecoTrim.Pattern.of("classic_dark_oak_pilaster"));
        assertEquals(DecoTrim.Pattern.DIAMOND, DecoTrim.Pattern.of("dark_oak_diamond_panel"));
        assertEquals(DecoTrim.Pattern.PLAIN, DecoTrim.Pattern.of("dark_oak_paneling"));
        assertEquals(DecoTrim.Pattern.FRAME, DecoTrim.Pattern.of("classic_gilded_dark_oak_paneling"));
        for (String family : DecoDisplayPack.OWN_FAMILIES) {
            if (family.equals("dark_oak_paneling")) {
                continue; // the one pattern without gold
            }
            BufferedImage side = Images.read(jar.bytes("assets/casinodeco/textures/block/" + family + ".png"));
            assertTrue(gold(DecoTrim.half(side, DecoTrim.Pattern.of(family))) > 8, family + " half");
            assertTrue(gold(DecoTrim.quarter(side, DecoTrim.Pattern.of(family))) > 8, family + " quarter");
        }
    }

    private static int gold(BufferedImage image) {
        int n = 0;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int p = image.getRGB(x, y);
                n += ((p >> 16) & 255) > 150 && ((p >> 8) & 255) > 110 && (p & 255) < 110 ? 1 : 0;
            }
        }
        return n;
    }

    /** The three slab types show the exact covers, with the mod's textures; a double slab its one texture. */
    @Test
    void slabsAreExactCovers() {
        JsonObject def = Json.parse(tree.get("assets/kaboofcasino/items/deco/ivory_marble_slab_v1.json"));
        assertEquals("kaboofcasino:deco/slab/ivory_marble_slab_v1", def.getAsJsonObject("model").get("model").getAsString());
        JsonObject child = Json.parse(tree.get("assets/kaboofcasino/models/deco/slab/ivory_marble_slab_v1.json"));
        assertEquals("kaboofcasino:deco/slab/template/bottom", child.get("parent").getAsString());
        assertEquals("casinodeco:block/ivory_marble_v1", child.getAsJsonObject("textures").get("top").getAsString());
        JsonArray bottom = Json.parse(tree.get("assets/kaboofcasino/models/deco/slab/template/bottom.json"))
                .getAsJsonArray("elements");
        assertEquals(7, bottom.size());
        JsonObject up = bottom.get(2).getAsJsonObject();
        assertArrayEquals(new double[] {0, 8 + DecoDisplayBake.COVER_OUT, 0},
                DecoDisplayBake.doubles(up.getAsJsonArray("from")), 1e-9);
        assertArrayEquals(new double[] {16, 8 + DecoDisplayBake.COVER_OUT, 16},
                DecoDisplayBake.doubles(up.getAsJsonArray("to")), 1e-9);
        assertArrayEquals(new double[] {0, 0, 16, 16}, DecoDisplayBake.doubles(up.getAsJsonObject("faces")
                .getAsJsonObject("up").getAsJsonArray("uv")), 1e-9);
        JsonObject full = Json.parse(tree.get("assets/kaboofcasino/models/deco/slab/ivory_marble.json"));
        assertEquals("kaboofcasino:deco/slab/template/double", full.get("parent").getAsString());
        assertEquals("casinodeco:block/ivory_marble", full.getAsJsonObject("textures").get("side").getAsString());
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

    /** One display model per block model of the blockstates, the 45 placing items, their names in every language. */
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
        for (String id : java.util.List.of("casino_tv", "casino_tv_large", "casino_tv_3x3", "dealer_station")) {
            assertTrue(tree.has("assets/roulettecasino/items/" + id + ".json"), id);
            assertTrue(DecoDisplayPack.LANG_PREFIXES.contains("block.roulettecasino." + id + "="), id);
        }
        assertTrue(PackTreeHas.item(tree, "kaboofcasino:deco/dealer_station"));
        assertEquals(45, DecoDisplayPack.LANG_PREFIXES.size());
        // the glass models' sprite objects became plain sprite ids
        JsonObject sconce = Json.parse(tree.get("assets/casinodeco/models/block/chandelier_wall_sconce.json"));
        for (Map.Entry<String, JsonElement> t : sconce.getAsJsonObject("textures").entrySet()) {
            assertTrue(t.getValue().isJsonPrimitive(), t.getKey());
        }
    }

    /**
     * The TVs: every model the blockstates pick for a cell the mod places (all parts of the large and 3 x 3 TV, part 0
     * of the small one) is shipped verbatim with a display definition; the small TV's unused parts 1..3 are not. The
     * screen pictures are atlas sprites whose sides are multiples of 16 (no padding, mip level kept).
     */
    @Test
    void televisionCellModels() {
        int shipped = 0;
        for (Map.Entry<String, Integer> tv : DecoDisplayPack.TV_COLUMNS.entrySet()) {
            int cells = tv.getValue() * tv.getValue();
            Set<String> used = new TreeSet<>();
            JsonObject variants = Json.parse(jar.bytes("assets/roulettecasino/blockstates/" + tv.getKey() + ".json"))
                    .getAsJsonObject("variants");
            for (Map.Entry<String, JsonElement> v : variants.entrySet()) {
                int part = Integer.parseInt(v.getKey().replaceAll(".*part=(\\d+).*", "$1"));
                if (part < cells) {
                    used.add(v.getValue().getAsJsonObject().get("model").getAsString());
                }
            }
            assertEquals(DecoDisplayPack.TV_CHANNELS * cells, used.size(), tv.getKey());
            for (String model : used) {
                String name = model.substring("roulettecasino:block/".length());
                String path = "assets/roulettecasino/models/block/" + name + ".json";
                assertArrayEquals(jar.bytes(path), tree.get(path), path);
                assertTrue(PackTreeHas.item(tree, "kaboofcasino:deco/" + name), name);
                shipped++;
            }
            for (int part = cells; part < 4 && tv.getValue() == 1; part++) {
                assertTrue(!PackTreeHas.item(tree, "kaboofcasino:deco/casino_tv_screen1_" + part), "unused part");
            }
        }
        assertEquals(9 * (1 + 4 + 9), shipped);
        for (String screen : java.util.List.of("off", "info", "roulette", "blackjack", "slot_machine",
                "fortune_wheel", "duck_derby", "plinko", "poker")) {
            String path = "assets/roulettecasino/textures/block/casino_tv_" + screen + ".png";
            assertTrue(tree.has(path), path);
            java.awt.image.BufferedImage image = Images.read(tree.get(path));
            assertEquals(0, image.getWidth() % 16, path);
            assertEquals(0, image.getHeight() % 16, path);
        }
        assertTrue(!tree.has("assets/roulettecasino/textures/block/casino_tv_on.png"), "no channel uses it");
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
