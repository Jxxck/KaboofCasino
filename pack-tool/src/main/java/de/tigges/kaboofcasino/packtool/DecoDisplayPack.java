package de.tigges.kaboofcasino.packtool;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Models of the display decoration (step 4; the TVs and the dealer station of step 8): every non-full-cube block of
 * {@code casinodeco}, the casino chair and stool, the three casino TVs and the dealer station ({@code roulettecasino})
 * and the speaker ({@code casinosound}).
 * <ul>
 *   <li>the mod's item definitions of the 45 placing items (with their item and icon models and textures);</li>
 *   <li>one item definition {@code kaboofcasino:deco/<model>} per block model the mod's blockstates of these blocks
 *       use, for item displays (render.md 2.2; blockstate {@code y} turns are display rotations);</li>
 *   <li>the stairs baked per state ({@code kaboofcasino:deco/stairs/<family>_<facing>_<half>_<shape>_<variant>}):
 *       the 40 turned, uv-locked vanilla templates once ({@code kaboofcasino:deco/stairs/template/...}), and a child
 *       per family and random variant that only sets the textures ({@link DecoDisplayBake});</li>
 *   <li>the 3 x 2 sign merged into one model around its middle column ({@code kaboofcasino:deco/casino_sign}) and the
 *       chandelier's nine body parts merged around the anchor ({@code kaboofcasino:deco/chandelier_body});</li>
 *   <li>the TVs' cell models as the mod has them, one per channel and cell
 *       ({@code kaboofcasino:deco/<tv>_screen<0..8>_<part>}, {@link #televisions}).</li>
 * </ul>
 * The blocks only use vanilla sounds (cushion, chest, lever click, candles), so no sound events are added.
 */
final class DecoDisplayPack {

    /** Sound events of roulettecasino/casinosound used by deco (plain effects; the music goes into the music pack). */
    static final List<String> SOUND_EVENTS = List.of();

    /** The display decoration's ids per namespace (casinodeco ids, then the two others). */
    static final List<String> DECO_IDS = List.of("casino_sofa", "casino_sign", "small_casino_sign",
            "wood_coffee_table", "glass_coffee_table", "wood_table", "glass_table", "casino_chandelier",
            "velvet_rope_post", "ruby_velvet_stairs", "ruby_velvet_slab", "emerald_velvet_stairs",
            "emerald_velvet_slab", "sapphire_velvet_stairs", "sapphire_velvet_slab", "ivory_black_checkers_stairs",
            "ivory_black_checkers_slab", "ivory_marble_stairs", "ivory_marble_slab", "obsidian_marble_stairs",
            "obsidian_marble_slab", "gold_veined_black_marble_stairs", "gold_veined_black_marble_slab", "walnut_bar",
            "ivory_marble_bar", "black_marble_bar", "chandelier_wall_sconce", "casino_bottles", "champagne_coupes",
            "shot_glasses", "casino_chip_pile", "chip_case", "reception_plaque", "cash_register", "serving_tray",
            "vip_only_plaque", "bottle_display_shelf", "glass_display_shelf");
    static final List<String> OTHER_IDS = List.of("roulettecasino:casino_chair", "roulettecasino:casino_stool",
            "casinosound:casino_speaker", "roulettecasino:casino_tv", "roulettecasino:casino_tv_large",
            "roulettecasino:casino_tv_3x3", "roulettecasino:dealer_station");
    /** The three TVs ({@code roulettecasino}) and their columns (= rows). */
    static final Map<String, Integer> TV_COLUMNS = Map.of("casino_tv", 1, "casino_tv_large", 2, "casino_tv_3x3", 3);
    /** The TVs' channels 0..8 ({@code screen}; 0 = off). */
    static final int TV_CHANNELS = 9;

    /** The wall blocks that get stairs and slabs the mod does not have (Jack 2026-11-09, {@link #ownStairsAndSlabs}). */
    static final List<String> OWN_FAMILIES = List.of("classic_dark_oak_baseboard", "classic_dark_oak_cornice",
            "classic_dark_oak_dado_rail", "classic_dark_oak_diamond_panel", "classic_dark_oak_pilaster",
            "classic_gilded_dark_oak_paneling", "dark_oak_baseboard", "dark_oak_cornice", "dark_oak_dado_rail",
            "dark_oak_diamond_panel", "dark_oak_paneling", "dark_oak_pilaster", "gilded_dark_oak_paneling");

    /** Exact keys ("key="): the names of the placing items in all 12 languages. */
    static final List<String> LANG_PREFIXES = langKeys();

    static final String PREFIX = "kaboofcasino:deco/";

    private DecoDisplayPack() {
    }

    private static List<String> langKeys() {
        List<String> out = new ArrayList<>();
        for (String id : DECO_IDS) {
            out.add("block.casinodeco." + id + "=");
        }
        for (String id : OTHER_IDS) {
            out.add("block." + id.replace(':', '.') + "=");
        }
        return List.copyOf(out);
    }

    static void add(ModJar jar, PackTree tree, Path packSrc) {
        ModelCopier copier = new ModelCopier(jar, tree);
        flattenSprites(jar, tree, copier);
        for (String id : DECO_IDS) {
            copier.itemDefinition("casinodeco:" + id);
        }
        for (String id : OTHER_IDS) {
            copier.itemDefinition(id);
        }
        // block models the blockstates use, except those baked or merged below
        TreeSet<String> models = new TreeSet<>();
        for (String id : blockIds()) {
            String ns = id.substring(0, id.indexOf(':'));
            String path = id.substring(id.indexOf(':') + 1);
            JsonObject state = Json.parse(jar.bytes("assets/" + ns + "/blockstates/" + path + ".json"));
            if (path.endsWith("_stairs") || path.equals("casino_sign") || path.equals("casino_chandelier")
                    || TV_COLUMNS.containsKey(path)) {
                continue;
            }
            collect(state, models);
        }
        models.add("casinodeco:block/chandelier_9");
        models.add("casinodeco:block/chandelier_10");
        Map<String, String> slabs = slabModels(jar);
        for (String model : models) {
            copier.model(model);
            String name = model.substring(model.indexOf("block/") + "block/".length());
            if (slabs.containsKey(model)) {
                ModelCopier.Id ref = ModelCopier.Id.parse(model);
                JsonObject textures = Json.parse(jar.bytes("assets/" + ref.namespace() + "/models/" + ref.path()
                        + ".json")).getAsJsonObject("textures").deepCopy();
                if (textures.has("all")) {
                    // a double slab's model is a cube_all: its one texture on all sides
                    JsonElement all = textures.remove("all");
                    textures.add("bottom", all);
                    textures.add("top", all);
                    textures.add("side", all);
                }
                slabCover(tree, name, slabs.get(model), textures);
            } else {
                displayDefinition(tree, name, model);
            }
        }
        merged(jar, copier, tree, "casino_sign", "casinodeco:block/sign_", 6, 0);
        merged(jar, copier, tree, "chandelier_body", "casinodeco:block/chandelier_", 9, 1);
        stairs(jar, copier, tree);
        ownStairsAndSlabs(jar, copier, tree);
        CasinoSignPack.add(copier, tree);
        televisions(copier, tree);
    }

    /**
     * One display model per TV cell and channel: the mod's {@code roulettecasino:block/<tv>_screen<channel>_<part>}
     * (the screen picture's slice for that cell, the off picture for channel 0), as the blockstates pick them. The
     * small TV's blockstate also names parts 1..3, which the mod never places: those are not shipped.
     */
    static void televisions(ModelCopier copier, PackTree tree) {
        for (String id : new TreeSet<>(TV_COLUMNS.keySet())) {
            int cells = TV_COLUMNS.get(id) * TV_COLUMNS.get(id);
            for (int channel = 0; channel < TV_CHANNELS; channel++) {
                for (int part = 0; part < cells; part++) {
                    String name = id + "_screen" + channel + "_" + part;
                    String model = "roulettecasino:block/" + name;
                    copier.model(model);
                    displayDefinition(tree, name, model);
                }
            }
        }
    }

    /**
     * Some glass models name textures as {@code {"sprite": ..., "force_translucent": ...}} (26.x). Every such texture
     * of the mod is either fully translucent (every pixel 0 < alpha < 255: {@code casino_glass_clear},
     * {@code casino_glass_rim}, {@code decor_crystal}, all with {@code force_translucent: true}) or fully opaque (the
     * {@code false} ones), so the client's own transparency test gives the same render type; the models are copied
     * with the plain sprite id, which the rest of the pack tool (ModelCopier, TexturePadding) reads.
     */
    static void flattenSprites(ModJar jar, PackTree tree, ModelCopier copier) {
        for (String path : jar.list("assets/casinodeco/models/")) {
            if (!path.endsWith(".json") || tree.has(path)) {
                continue;
            }
            JsonObject model = Json.parse(jar.bytes(path));
            if (!model.has("textures")) {
                continue;
            }
            JsonObject textures = model.getAsJsonObject("textures");
            boolean changed = false;
            for (Map.Entry<String, JsonElement> t : textures.entrySet()) {
                if (t.getValue().isJsonObject()) {
                    t.setValue(t.getValue().getAsJsonObject().get("sprite"));
                    changed = true;
                }
            }
            if (!changed) {
                continue;
            }
            tree.put(path, Json.bytes(model));
            for (Map.Entry<String, JsonElement> t : textures.entrySet()) {
                if (!t.getValue().getAsString().startsWith("#")) {
                    copier.texture(t.getValue().getAsString());
                }
            }
            if (model.has("parent")) {
                copier.model(model.get("parent").getAsString());
            }
        }
    }

    /** {@code <ns>:<id>} of every block with a blockstate here. */
    static List<String> blockIds() {
        List<String> out = new ArrayList<>();
        for (String id : DECO_IDS) {
            out.add("casinodeco:" + id);
        }
        out.addAll(OTHER_IDS);
        return out;
    }

    /** Every {@code "model"} string of a blockstate (variants and multipart, single entries and random lists). */
    static void collect(JsonElement e, java.util.Set<String> out) {
        if (e.isJsonObject()) {
            for (Map.Entry<String, JsonElement> c : e.getAsJsonObject().entrySet()) {
                if (c.getKey().equals("model") && c.getValue().isJsonPrimitive()) {
                    out.add(c.getValue().getAsString());
                } else {
                    collect(c.getValue(), out);
                }
            }
        } else if (e.isJsonArray()) {
            e.getAsJsonArray().forEach(c -> collect(c, out));
        }
    }

    /** {@code assets/kaboofcasino/items/deco/<name>.json} -> {@code model}. */
    static void displayDefinition(PackTree tree, String name, String model) {
        tree.put("assets/kaboofcasino/items/deco/" + name + ".json", Json.bytes(Json.itemDefinition(model)));
    }

    /**
     * Merges the part models {@code <base>0..<count-1>} into one model: the sign's parts sit in 3 columns (to +x in
     * the south frame, part % 3) and 2 rows (part / 3) around the middle column ({@code layout 0}); the chandelier's
     * body parts in a 3 x 3 grid around part 4 ({@code layout 1}: x = part % 3 - 1, z = part / 3 - 1). Model
     * coordinates must stay within [-16, 32]; the chandelier's corner candles reach 0.3 px further, so its merged
     * model is stored at half size about the centre (8, 8, 8) and the display scales it by 2 (render.md 2.2, "fewer
     * entities"): {@code 8 + (c - 8) / 2}, angles and UVs unchanged.
     */
    static void merged(ModJar jar, ModelCopier copier, PackTree tree, String name, String base, int count,
                       int layout) {
        JsonObject textures = null;
        JsonArray elements = new JsonArray();
        for (int part = 0; part < count; part++) {
            String id = base + part;
            copier.model(id);
            ModelCopier.Id ref = ModelCopier.Id.parse(id);
            JsonObject model = Json.parse(jar.bytes("assets/" + ref.namespace() + "/models/" + ref.path() + ".json"));
            JsonObject t = model.getAsJsonObject("textures");
            if (textures == null) {
                textures = t;
            } else if (!textures.equals(t)) {
                throw new IllegalStateException(id + " has other textures than " + base + "0");
            }
            double dx = layout == 0 ? (part % 3 - 1) * 16.0 : (part % 3 - 1) * 16.0;
            double dy = layout == 0 ? (part / 3) * 16.0 : 0.0;
            double dz = layout == 0 ? 0.0 : (part / 3 - 1) * 16.0;
            elements.addAll(Models.placed(model.getAsJsonArray("elements"), dx, dy, dz, -1, 0));
        }
        if (layout == 1) {
            elements = halved(elements);
        }
        JsonObject out = new JsonObject();
        out.add("textures", textures);
        out.add("elements", elements);
        tree.put("assets/kaboofcasino/models/deco/" + name + ".json", Json.bytes(out));
        displayDefinition(tree, name, PREFIX + name);
    }

    /** Every coordinate {@code c} (from, to, rotation origin) as {@code 8 + (c - 8) / 2}. */
    static JsonArray halved(JsonArray elements) {
        JsonArray out = new JsonArray();
        for (JsonElement e : elements) {
            JsonObject el = e.getAsJsonObject().deepCopy();
            el.add("from", half(el.getAsJsonArray("from")));
            el.add("to", half(el.getAsJsonArray("to")));
            if (el.has("rotation")) {
                JsonObject rotation = el.getAsJsonObject("rotation");
                rotation.add("origin", half(rotation.getAsJsonArray("origin")));
            }
            out.add(el);
        }
        return out;
    }

    private static JsonArray half(JsonArray v) {
        double[] d = DecoDisplayBake.doubles(v);
        return Json.array(8 + (d[0] - 8) / 2, 8 + (d[1] - 8) / 2, 8 + (d[2] - 8) / 2);
    }

    /** Every model of the slab blockstates and its type ({@code bottom}, {@code top}, {@code double}). */
    static Map<String, String> slabModels(ModJar jar) {
        Map<String, String> out = new java.util.TreeMap<>();
        for (String id : DECO_IDS) {
            if (id.endsWith("_slab")) {
                JsonObject variants = Json.parse(jar.bytes("assets/casinodeco/blockstates/" + id + ".json"))
                        .getAsJsonObject("variants");
                for (Map.Entry<String, JsonElement> v : variants.entrySet()) {
                    String type = v.getKey().substring("type=".length());
                    java.util.Set<String> found = new TreeSet<>();
                    collect(v.getValue(), found);
                    found.forEach(m -> out.put(m, type));
                }
            }
        }
        return out;
    }

    /** A slab's display: the exact cover of its type ({@link DecoDisplayBake#cover}) with these textures. */
    static void slabCover(PackTree tree, String name, String type, JsonObject slabTextures) {
        String template = "assets/kaboofcasino/models/deco/slab/template/" + type + ".json";
        if (!tree.has(template)) {
            JsonObject t = new JsonObject();
            t.add("elements", DecoDisplayBake.cover(switch (type) {
                case "bottom" -> DecoDisplayBake.slab(0, 8);
                case "top" -> DecoDisplayBake.slab(8, 16);
                case "double" -> DecoDisplayBake.slab(0, 16);
                default -> throw new IllegalStateException("slab type " + type);
            }));
            tree.put(template, Json.bytes(t));
        }
        JsonObject textures = coverTextures(slabTextures);
        JsonObject child = new JsonObject();
        child.addProperty("parent", PREFIX + "slab/template/" + type);
        child.add("textures", textures);
        tree.put("assets/kaboofcasino/models/deco/slab/" + name + ".json", Json.bytes(child));
        displayDefinition(tree, name, PREFIX + "slab/" + name);
    }

    /** A cover's textures: {@code half} and {@code quarter} are the side texture unless the family has its own trim. */
    private static JsonObject coverTextures(JsonObject given) {
        JsonObject textures = given.deepCopy();
        textures.add("particle", textures.get("side"));
        for (String role : List.of("half", "quarter")) {
            if (!textures.has(role)) {
                textures.add(role, textures.get("side"));
            }
        }
        return textures;
    }

    /** The baked stairs of every family (see the class comment). */
    static void stairs(ModJar jar, ModelCopier copier, PackTree tree) {
        for (String id : DECO_IDS) {
            if (!id.endsWith("_stairs")) {
                continue;
            }
            String family = id.substring(0, id.length() - "_stairs".length());
            JsonObject variants = Json.parse(jar.bytes("assets/casinodeco/blockstates/" + id + ".json"))
                    .getAsJsonObject("variants");
            for (Map.Entry<String, JsonElement> v : variants.entrySet()) {
                String key = stateKey(v.getKey());
                List<JsonObject> list = new ArrayList<>();
                if (v.getValue().isJsonArray()) {
                    v.getValue().getAsJsonArray().forEach(x -> list.add(x.getAsJsonObject()));
                } else {
                    list.add(v.getValue().getAsJsonObject());
                }
                for (int i = 0; i < list.size(); i++) {
                    JsonObject entry = list.get(i);
                    int x = entry.has("x") ? entry.get("x").getAsInt() : 0;
                    int y = entry.has("y") ? entry.get("y").getAsInt() : 0;
                    String modelId = entry.get("model").getAsString();
                    ModelCopier.Id ref = ModelCopier.Id.parse(modelId);
                    JsonObject model = Json.parse(jar.bytes("assets/" + ref.namespace() + "/models/" + ref.path()
                            + ".json"));
                    JsonObject textures = model.getAsJsonObject("textures").deepCopy();
                    for (Map.Entry<String, JsonElement> tex : textures.entrySet()) {
                        copier.texture(tex.getValue().getAsString());
                    }
                    stairDisplay(tree, "stairs/" + family + "_" + key + "_" + i, model.get("parent").getAsString(), x,
                            y, textures);
                }
            }
        }
    }

    /** One stair state's display: the turned exact cover (shared by every family) with these textures. */
    static void stairDisplay(PackTree tree, String name, String parent, int x, int y, JsonObject stairTextures) {
        String template = "template/" + parent.substring(parent.indexOf("block/") + 6) + "_x" + x + "_y" + y;
        String path = "assets/kaboofcasino/models/deco/stairs/" + template + ".json";
        if (!tree.has(path)) {
            JsonObject t = new JsonObject();
            t.add("elements", DecoDisplayBake.cover(DecoDisplayBake.faceRoles(DecoDisplayBake.bakeUvLocked(
                    DecoDisplayBake.template(parent), x, y))));
            tree.put(path, Json.bytes(t));
        }
        JsonObject textures = coverTextures(stairTextures);
        JsonObject child = new JsonObject();
        child.addProperty("parent", PREFIX + "stairs/" + template);
        child.add("textures", textures);
        tree.put("assets/kaboofcasino/models/deco/" + name + ".json", Json.bytes(child));
        displayDefinition(tree, name, PREFIX + name);
    }

    /**
     * The stairs and slabs of {@link #OWN_FAMILIES}, which the mod has no files for: the placing items (3D like the
     * mod's), every stair state by the turns of the mod's velvet stairs, and the three slab types; top and bottom show
     * the block's end texture, whole faces its side texture, smaller faces the closed frames of {@link DecoTrim}.
     */
    static void ownStairsAndSlabs(ModJar jar, ModelCopier copier, PackTree tree) {
        JsonObject turns = Json.parse(jar.bytes("assets/casinodeco/blockstates/ruby_velvet_stairs.json"))
                .getAsJsonObject("variants");
        for (String name : OWN_FAMILIES) {
            JsonObject block = Json.parse(jar.bytes("assets/casinodeco/models/block/" + name + ".json"))
                    .getAsJsonObject("textures");
            String end = block.get("end").getAsString();
            copier.texture(block.get("side").getAsString());
            copier.texture(end);
            JsonObject textures = new JsonObject();
            textures.addProperty("bottom", end);
            textures.addProperty("top", end);
            textures.add("side", block.get("side"));
            BufferedImage side = Images.read(jar.bytes("assets/casinodeco/textures/block/" + name + ".png"));
            DecoTrim.Pattern pattern = DecoTrim.Pattern.of(name);
            tree.put("assets/kaboofcasino/textures/block/trim/" + name + "_half.png",
                    Images.write(DecoTrim.half(side, pattern)));
            tree.put("assets/kaboofcasino/textures/block/trim/" + name + "_quarter.png",
                    Images.write(DecoTrim.quarter(side, pattern)));
            textures.addProperty("half", "kaboofcasino:block/trim/" + name + "_half");
            textures.addProperty("quarter", "kaboofcasino:block/trim/" + name + "_quarter");
            stairsItem(tree, name + "_stairs", textures);
            slabItem(tree, name + "_slab", textures);
            for (Map.Entry<String, JsonElement> v : turns.entrySet()) {
                JsonObject entry = v.getValue().isJsonArray() ? v.getValue().getAsJsonArray().get(0).getAsJsonObject()
                        : v.getValue().getAsJsonObject();
                ModelCopier.Id ref = ModelCopier.Id.parse(entry.get("model").getAsString());
                String parent = Json.parse(jar.bytes("assets/" + ref.namespace() + "/models/" + ref.path() + ".json"))
                        .get("parent").getAsString();
                stairDisplay(tree, "stairs/" + name + "_" + stateKey(v.getKey()) + "_0", parent,
                        entry.has("x") ? entry.get("x").getAsInt() : 0, entry.has("y") ? entry.get("y").getAsInt() : 0,
                        textures);
            }
            slabCover(tree, name + "_slab", "bottom", textures);
            slabCover(tree, name + "_slab_top", "top", textures);
            slabCover(tree, name, "double", textures);
        }
    }

    /** A slab's placing item: the vanilla slab with a closed frame on its sides. */
    private static void slabItem(PackTree tree, String id, JsonObject textures) {
        JsonObject slab = new JsonObject();
        slab.addProperty("bottom", textures.get("bottom").getAsString());
        slab.addProperty("top", textures.get("top").getAsString());
        slab.addProperty("side", textures.get("half").getAsString());
        JsonObject block = new JsonObject();
        block.addProperty("parent", "minecraft:block/slab");
        block.add("textures", slab);
        tree.put("assets/casinodeco/models/block/" + id + ".json", Json.bytes(block));
        JsonObject item = new JsonObject();
        item.addProperty("parent", "casinodeco:block/" + id);
        tree.put("assets/casinodeco/models/item/" + id + ".json", Json.bytes(item));
        tree.put("assets/casinodeco/items/" + id + ".json", Json.bytes(Json.itemDefinition("casinodeco:item/" + id)));
    }

    /** A stair's placing item: the straight stair cover (with its frames) held and shown like a vanilla stair. */
    private static void stairsItem(PackTree tree, String id, JsonObject textures) {
        JsonObject item = new JsonObject();
        item.addProperty("parent", PREFIX + "stairs/template/stairs_x0_y0");
        item.add("textures", coverTextures(textures));
        JsonObject display = new JsonObject();
        display.add("gui", transform(30, 135, 0, 0, 0, 0.625));
        display.add("ground", transform(0, 0, 0, 3, 0, 0.25));
        display.add("fixed", transform(0, 0, 0, 0, 0, 0.5));
        display.add("head", transform(0, -90, 0, 0, 0, 1));
        display.add("thirdperson_righthand", transform(75, 45, 0, 2.5, 0, 0.375));
        display.add("thirdperson_lefthand", transform(75, -135, 0, 2.5, 0, 0.375));
        display.add("firstperson_righthand", transform(0, 45, 0, 0, 0, 0.4));
        display.add("firstperson_lefthand", transform(0, 225, 0, 0, 0, 0.4));
        item.add("display", display);
        tree.put("assets/casinodeco/models/item/" + id + ".json", Json.bytes(item));
        tree.put("assets/casinodeco/items/" + id + ".json", Json.bytes(Json.itemDefinition("casinodeco:item/" + id)));
    }

    private static JsonObject transform(double rx, double ry, double rz, double ty, double tz, double scale) {
        JsonObject t = new JsonObject();
        t.add("rotation", Json.array(rx, ry, rz));
        t.add("translation", Json.array(0, ty, tz));
        t.add("scale", Json.array(scale, scale, scale));
        return t;
    }

    /** {@code facing=east,half=bottom,shape=inner_left} -> {@code east_bottom_inner_left}. */
    static String stateKey(String variant) {
        String facing = null;
        String half = null;
        String shape = null;
        for (String kv : variant.split(",")) {
            String[] p = kv.split("=");
            switch (p[0]) {
                case "facing" -> facing = p[1];
                case "half" -> half = p[1];
                case "shape" -> shape = p[1];
                default -> throw new IllegalStateException("unexpected stair property " + kv);
            }
        }
        return facing + "_" + half + "_" + shape;
    }
}
