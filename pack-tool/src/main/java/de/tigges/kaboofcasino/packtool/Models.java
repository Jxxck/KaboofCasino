package de.tigges.kaboofcasino.packtool;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Map;

/** Writing generated models and editing converted mesh elements (pure JSON work). */
final class Models {

    private Models() {
    }

    /** Writes {@code assets/kaboofcasino/models/<path>.json} with one texture ({@code #sheet}) and optionally a plain
     * item definition {@code kaboofcasino:<path>}. */
    static void write(PackTree tree, String path, String sheet, JsonArray elements, boolean itemDefinition) {
        JsonObject textures = new JsonObject();
        textures.addProperty("sheet", sheet);
        textures.addProperty("particle", sheet);
        JsonObject model = new JsonObject();
        model.add("textures", textures);
        model.add("elements", elements);
        tree.put("assets/kaboofcasino/models/" + path + ".json", Json.bytes(model));
        if (itemDefinition) {
            tree.put("assets/kaboofcasino/items/" + path + ".json",
                    Json.bytes(Json.itemDefinition("kaboofcasino:" + path)));
        }
    }

    /** An item definition whose model takes {@code count} tints from {@code custom_model_data} colours. */
    static void tintedDefinition(PackTree tree, String path, int count) {
        JsonArray tints = new JsonArray();
        for (int i = 0; i < count; i++) {
            JsonObject tint = new JsonObject();
            tint.addProperty("type", "minecraft:custom_model_data");
            tint.addProperty("index", i);
            tint.addProperty("default", 0xFFFFFF);
            tints.add(tint);
        }
        JsonObject definition = Json.itemDefinition("kaboofcasino:" + path);
        definition.getAsJsonObject("model").add("tints", tints);
        tree.put("assets/kaboofcasino/items/" + path + ".json", Json.bytes(definition));
    }

    /** A deep copy of {@code elements} moved by (dx, dy, dz) model units, with every face's tint and emission set. */
    static JsonArray placed(JsonArray elements, double dx, double dy, double dz, int tint, int lightEmission) {
        JsonArray out = new JsonArray();
        for (JsonElement e : elements) {
            JsonObject el = e.getAsJsonObject().deepCopy();
            el.add("from", shift(el.getAsJsonArray("from"), dx, dy, dz));
            el.add("to", shift(el.getAsJsonArray("to"), dx, dy, dz));
            if (el.has("rotation")) {
                JsonObject rotation = el.getAsJsonObject("rotation");
                rotation.add("origin", shift(rotation.getAsJsonArray("origin"), dx, dy, dz));
            }
            if (tint >= 0) {
                for (Map.Entry<String, JsonElement> face : el.getAsJsonObject("faces").entrySet()) {
                    face.getValue().getAsJsonObject().addProperty("tintindex", tint);
                }
            }
            if (lightEmission > 0) {
                el.addProperty("light_emission", lightEmission);
            }
            out.add(el);
        }
        return out;
    }

    private static JsonArray shift(JsonArray v, double dx, double dy, double dz) {
        return Json.array(v.get(0).getAsDouble() + dx, v.get(1).getAsDouble() + dy, v.get(2).getAsDouble() + dz);
    }

    /** One flat south-facing quad (from x0,y0 to x1,y1 at z) with a uv rectangle in model units. */
    static JsonObject quad(double x0, double y0, double x1, double y1, double z, double[] uv, int tint,
                           int lightEmission) {
        JsonObject face = new JsonObject();
        face.add("uv", Json.array(uv));
        face.addProperty("texture", "#sheet");
        if (tint >= 0) {
            face.addProperty("tintindex", tint);
        }
        JsonObject element = new JsonObject();
        element.add("from", Json.array(x0, y0, z));
        element.add("to", Json.array(x1, y1, z));
        element.add("faces", Json.object("south", face));
        if (lightEmission > 0) {
            element.addProperty("light_emission", lightEmission);
        }
        return element;
    }
}
