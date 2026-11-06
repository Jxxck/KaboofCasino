package de.tigges.kaboofcasino.packtool;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Map;
import java.util.Set;

/**
 * Copies mod models, item definitions and everything they reference (parents in a mod namespace, textures and their
 * {@code .mcmeta}) from the mod jar into the pack. References into {@code minecraft:} are left alone: the client has
 * them.
 */
final class ModelCopier {

    static final Set<String> MOD_NAMESPACES = Set.of("roulettecasino", "casinodeco", "casinosound");

    private final ModJar jar;
    private final PackTree tree;

    ModelCopier(ModJar jar, PackTree tree) {
        this.jar = jar;
        this.tree = tree;
    }

    /** Copies {@code assets/<ns>/items/<path>.json} and the models it names. */
    void itemDefinition(String id) {
        Id ref = Id.parse(id);
        String path = "assets/" + ref.namespace() + "/items/" + ref.path() + ".json";
        byte[] data = jar.bytes(path);
        tree.put(path, data);
        modelsIn(Json.parse(data));
    }

    /** Copies {@code assets/<ns>/models/<path>.json} with its parents and textures. */
    void model(String id) {
        Id ref = Id.parse(id);
        if (!MOD_NAMESPACES.contains(ref.namespace())) {
            return;
        }
        String path = "assets/" + ref.namespace() + "/models/" + ref.path() + ".json";
        if (tree.has(path)) {
            return;
        }
        byte[] data = jar.bytes(path);
        tree.put(path, data);
        JsonObject model = Json.parse(data);
        if (model.has("parent")) {
            model(model.get("parent").getAsString());
        }
        if (model.has("textures")) {
            for (Map.Entry<String, JsonElement> e : model.getAsJsonObject("textures").entrySet()) {
                String value = e.getValue().getAsString();
                if (!value.startsWith("#")) {
                    texture(value);
                }
            }
        }
    }

    /** Copies {@code assets/<ns>/textures/<path>.png} (+ .mcmeta) if it is a mod texture. */
    void texture(String id) {
        Id ref = Id.parse(id);
        if (!MOD_NAMESPACES.contains(ref.namespace())) {
            return;
        }
        String path = "assets/" + ref.namespace() + "/textures/" + ref.path() + ".png";
        tree.put(path, jar.bytes(path));
        jar.optional(path + ".mcmeta").ifPresent(meta -> tree.put(path + ".mcmeta", meta));
    }

    /** Copies a mod file under another pack path (e.g. an entity sheet into the blocks atlas). */
    void copyAs(String modPath, String packPath) {
        tree.put(packPath, jar.bytes(modPath));
    }

    /** Walks an item definition and copies every {@code "model": "<id>"} string it finds. */
    private void modelsIn(JsonElement element) {
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : element.getAsJsonObject().entrySet()) {
                JsonElement v = e.getValue();
                if (e.getKey().equals("model") && v.isJsonPrimitive()) {
                    model(v.getAsString());
                } else {
                    modelsIn(v);
                }
            }
        } else if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(this::modelsIn);
        }
    }

    /** A namespaced id; a missing namespace means {@code minecraft}. */
    record Id(String namespace, String path) {
        static Id parse(String id) {
            int colon = id.indexOf(':');
            return colon < 0 ? new Id("minecraft", id) : new Id(id.substring(0, colon), id.substring(colon + 1));
        }

        @Override
        public String toString() {
            return namespace + ":" + path;
        }
    }
}
