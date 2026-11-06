package de.tigges.kaboofcasino.packtool;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;

/**
 * JSON helpers. Output is deterministic: Gson keeps insertion order, numbers parsed from the mod keep their original
 * text, new doubles are written with {@link Double#toString}. Compact (no pretty printing) to keep the pack small.
 */
final class Json {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private Json() {
    }

    static JsonObject parse(byte[] utf8) {
        return JsonParser.parseString(new String(utf8, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    static byte[] bytes(JsonElement element) {
        return GSON.toJson(element).getBytes(StandardCharsets.UTF_8);
    }

    static JsonArray array(double... values) {
        JsonArray array = new JsonArray();
        for (double v : values) {
            array.add(round(v));
        }
        return array;
    }

    /** Rounds to 5 decimals so generated geometry has short, stable text (well below a texel and a model pixel). */
    static double round(double v) {
        double r = Math.round(v * 100_000.0) / 100_000.0;
        return r == 0.0 ? 0.0 : r; // no "-0.0"
    }

    static JsonObject object(String key, JsonElement value) {
        JsonObject o = new JsonObject();
        o.add(key, value);
        return o;
    }

    /** {@code {"model":{"type":"minecraft:model","model":<id>}}} - a plain item definition for one model. */
    static JsonObject itemDefinition(String modelId) {
        JsonObject model = new JsonObject();
        model.addProperty("type", "minecraft:model");
        model.addProperty("model", modelId);
        return object("model", model);
    }
}
