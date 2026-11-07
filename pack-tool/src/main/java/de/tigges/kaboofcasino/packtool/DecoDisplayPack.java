package de.tigges.kaboofcasino.packtool;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Models of the display decoration (step 4): every non-full-cube block of {@code casinodeco}, the casino chair and
 * stool ({@code roulettecasino}) and the speaker ({@code casinosound}).
 * <ul>
 *   <li>the mod's item definitions of the 41 placing items (with their item and icon models and textures);</li>
 *   <li>one item definition {@code kaboofcasino:deco/<model>} per block model the mod's blockstates of these blocks
 *       use, for item displays (render.md 2.2; blockstate {@code y} turns are display rotations);</li>
 *   <li>the stairs baked per state ({@code kaboofcasino:deco/stairs/<family>_<facing>_<half>_<shape>_<variant>}):
 *       the 40 turned, uv-locked vanilla templates once ({@code kaboofcasino:deco/stairs/template/...}), and a child
 *       per family and random variant that only sets the textures ({@link DecoDisplayBake});</li>
 *   <li>the 3 x 2 sign merged into one model around its middle column ({@code kaboofcasino:deco/casino_sign}) and the
 *       chandelier's nine body parts merged around the anchor ({@code kaboofcasino:deco/chandelier_body}).</li>
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
            "casinosound:casino_speaker");

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
            if (path.endsWith("_stairs") || path.equals("casino_sign") || path.equals("casino_chandelier")) {
                continue;
            }
            collect(state, models);
        }
        models.add("casinodeco:block/chandelier_9");
        models.add("casinodeco:block/chandelier_10");
        for (String model : models) {
            copier.model(model);
            String name = model.substring(model.indexOf("block/") + "block/".length());
            displayDefinition(tree, name, model);
        }
        merged(jar, copier, tree, "casino_sign", "casinodeco:block/sign_", 6, 0);
        merged(jar, copier, tree, "chandelier_body", "casinodeco:block/chandelier_", 9, 1);
        stairs(jar, copier, tree);
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

    /** The baked stairs of every family (see the class comment). */
    static void stairs(ModJar jar, ModelCopier copier, PackTree tree) {
        TreeSet<String> templates = new TreeSet<>();
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
                    String parent = model.get("parent").getAsString();
                    String template = "template/" + parent.substring(parent.indexOf("block/") + 6) + "_x" + x + "_y"
                            + y;
                    if (templates.add(template)) {
                        JsonObject t = new JsonObject();
                        t.add("elements", DecoDisplayBake.bakeUvLocked(DecoDisplayBake.template(parent), x, y));
                        tree.put("assets/kaboofcasino/models/deco/stairs/" + template + ".json", Json.bytes(t));
                    }
                    JsonObject textures = model.getAsJsonObject("textures").deepCopy();
                    for (Map.Entry<String, JsonElement> tex : textures.entrySet()) {
                        copier.texture(tex.getValue().getAsString());
                    }
                    textures.add("particle", textures.get("side"));
                    JsonObject child = new JsonObject();
                    child.addProperty("parent", PREFIX + "stairs/" + template);
                    child.add("textures", textures);
                    String name = "stairs/" + family + "_" + key + "_" + i;
                    tree.put("assets/kaboofcasino/models/deco/" + name + ".json", Json.bytes(child));
                    displayDefinition(tree, name, PREFIX + name);
                }
            }
        }
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
