package de.tigges.kaboofcasino.packtool;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The full-block deco (render.md 4.4, option B): the mod's full-cube blocks drawn on reserved states of the vanilla
 * huge-mushroom blocks ({@link DecoFullStates}).
 *
 * <ul>
 *   <li>{@code assets/minecraft/blockstates/{red,brown}_mushroom_block.json} and {@code mushroom_stem.json} are
 *       replaced by a multipart that keeps the twelve vanilla parts, each additionally conditioned on "not a reserved
 *       state" ({@code down=false} or one of the {@code down=true} states the table does not use, among them the
 *       all-true default and the world-generated ones), so natural and vanilla-placed blocks look as before;</li>
 *   <li>then the casino parts, translated from the mod's own blockstate files: a {@code variants} file gives one part
 *       per reserved state (the marble's four random models stay a weighted list, the stars keep their {@code y}
 *       rotation per facing); the pilaster's {@code multipart} gives each mod part once, conditioned on the reserved
 *       states whose mod properties satisfy the mod's condition;</li>
 *   <li>the mod's item definitions {@code casinodeco:<id>} (the item_model of the deco items), the block models
 *       with their textures, and the {@code block.casinodeco.<id>} texts of all twelve languages.</li>
 * </ul>
 * Deterministic: table order, mod part order, Gson insertion order.
 */
final class DecoFullPack {

    /** The exact lang keys of the 27 blocks (casinodeco lang, filtered by PackTool). */
    static final List<String> LANG_PREFIXES = DecoFullStates.decoBlocks().stream()
            .map(id -> "block.casinodeco." + id + "=").toList();

    /** The vanilla files this part replaces. */
    static final Set<String> BLOCKSTATE_FILES = DecoFullStates.BLOCKS.stream()
            .map(b -> "assets/minecraft/blockstates/" + b + ".json").collect(Collectors.toUnmodifiableSet());

    static final String INSIDE = "minecraft:block/mushroom_block_inside";

    private DecoFullPack() {
    }

    static void add(ModJar jar, PackTree tree, Path packSrc) {
        ModelCopier copier = new ModelCopier(jar, tree);
        for (String deco : DecoFullStates.decoBlocks()) {
            copier.itemDefinition("casinodeco:" + deco);
        }
        for (String block : DecoFullStates.BLOCKS) {
            JsonObject blockstate = blockstate(jar, block);
            for (JsonElement part : blockstate.getAsJsonArray("multipart")) {
                modelsOf(part.getAsJsonObject().get("apply")).forEach(copier::model);
            }
            tree.put("assets/minecraft/blockstates/" + block + ".json", Json.bytes(blockstate));
        }
    }

    /** The replacement blockstate of one mushroom block. */
    static JsonObject blockstate(ModJar jar, String block) {
        List<DecoFullStates.Entry> reserved = DecoFullStates.of(block);
        Set<Integer> reservedMasks = reserved.stream().map(DecoFullStates.Entry::mask).collect(Collectors.toSet());
        JsonArray guard = new JsonArray();
        guard.add(Json.object("down", new com.google.gson.JsonPrimitive("false")));
        for (int mask = 0; mask <= DecoFullStates.ALL; mask++) {
            if (!reservedMasks.contains(mask)) {
                guard.add(DecoFullConditions.state(DecoFullStates.Entry.vanillaProps(mask, true)));
            }
        }
        JsonObject notReserved = Json.object("OR", guard);

        JsonArray parts = new JsonArray();
        for (JsonElement p : vanillaParts(block)) {
            JsonObject part = p.getAsJsonObject().deepCopy();
            JsonArray and = new JsonArray();
            and.add(part.remove("when"));
            and.add(notReserved.deepCopy());
            part.add("when", Json.object("AND", and));
            parts.add(part);
        }

        // casino parts, deco block by deco block in table order
        List<String> decos = reserved.stream().map(DecoFullStates.Entry::deco).distinct().toList();
        for (String deco : decos) {
            List<DecoFullStates.Entry> states = reserved.stream().filter(e -> e.deco().equals(deco)).toList();
            JsonObject mod = Json.parse(jar.bytes("assets/casinodeco/blockstates/" + deco + ".json"));
            if (mod.has("variants")) {
                for (DecoFullStates.Entry e : states) {
                    JsonObject part = new JsonObject();
                    part.add("when", DecoFullConditions.state(e.vanillaProps()));
                    part.add("apply", variant(mod.getAsJsonObject("variants"), e.modProps()).deepCopy());
                    parts.add(part);
                }
            } else {
                for (JsonElement mp : mod.getAsJsonArray("multipart")) {
                    JsonObject modPart = mp.getAsJsonObject();
                    JsonArray or = new JsonArray();
                    for (DecoFullStates.Entry e : states) {
                        if (DecoFullConditions.matches(modPart.get("when"), e.modProps())) {
                            or.add(DecoFullConditions.state(e.vanillaProps()));
                        }
                    }
                    if (or.isEmpty()) {
                        continue;
                    }
                    JsonObject part = new JsonObject();
                    part.add("when", or.size() == 1 ? or.get(0) : Json.object("OR", or));
                    part.add("apply", modPart.get("apply").deepCopy());
                    parts.add(part);
                }
            }
        }
        return Json.object("multipart", parts);
    }

    /** The value of the mod variant whose key ({@code ""} or {@code facing=east}) matches the mod properties. */
    static JsonElement variant(JsonObject variants, Map<String, String> modProps) {
        for (Map.Entry<String, JsonElement> v : variants.entrySet()) {
            boolean all = true;
            if (!v.getKey().isEmpty()) {
                for (String kv : v.getKey().split(",")) {
                    String[] p = kv.split("=", 2);
                    all &= p[1].equals(modProps.get(p[0]));
                }
            }
            if (all) {
                return v.getValue();
            }
        }
        throw new IllegalStateException("no mod variant for " + modProps + " in " + variants.keySet());
    }

    /** The model ids of an {@code apply} (one variant or a weighted list). */
    static List<String> modelsOf(JsonElement apply) {
        List<String> out = new ArrayList<>();
        if (apply.isJsonArray()) {
            apply.getAsJsonArray().forEach(v -> out.add(v.getAsJsonObject().get("model").getAsString()));
        } else {
            out.add(apply.getAsJsonObject().get("model").getAsString());
        }
        return out;
    }

    /**
     * The vanilla 26.3 multipart of a huge-mushroom block ({@code assets/minecraft/blockstates/<block>.json} of the
     * client jar; DecoFullPackTest compares it with the jar when it is available): per face the cap/stem model when
     * the face is true (north without uvlock, the rest {@code uvlock: true}) and the inside model when it is false.
     */
    static JsonArray vanillaParts(String block) {
        String outside = "minecraft:block/" + block;
        String[][] faces = {{"north", null, null}, {"east", "y", "90"}, {"south", "y", "180"}, {"west", "y", "270"},
            {"up", "x", "270"}, {"down", "x", "90"}};
        JsonArray parts = new JsonArray();
        for (boolean outer : new boolean[] {true, false}) {
            for (String[] f : faces) {
                JsonObject apply = new JsonObject();
                apply.addProperty("model", outer ? outside : INSIDE);
                if (outer && f[1] != null) {
                    apply.addProperty("uvlock", true);
                }
                if (f[1] != null) {
                    apply.addProperty(f[1], Integer.parseInt(f[2]));
                }
                JsonObject part = new JsonObject();
                part.add("apply", apply);
                part.add("when", Json.object(f[0], new com.google.gson.JsonPrimitive(String.valueOf(outer))));
                parts.add(part);
            }
        }
        return parts;
    }
}
