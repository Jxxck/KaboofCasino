package de.tigges.kaboofcasino.packtool;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The mod's crafting recipes and their recipe-book unlocks for the shipped machines, read from the mod jar's
 * {@code data/roulettecasino/recipe/*.json} and {@code advancement/recipes/*.json} (the {@code has_ingredient}
 * inventory trigger) into {@code recipes.json}, which the plugin registers as Bukkit shaped recipes. Only shaped
 * recipes with single-id or tag ingredients exist among them; anything else fails the build.
 */
final class ModRecipes {

    static final String FILE = "recipes.json";
    /** The recipes of the machines built so far, in the mod's unlock order. */
    static final List<String> SHIPPED = List.of("slot_machine_reel", "slot_machine_mechanism", "slot_machine",
            "roulette_mechanism", "fortune_wheel", "plinko", "roulette_table");

    private ModRecipes() {
    }

    static JsonObject build(ModJar jar) {
        JsonArray out = new JsonArray();
        for (String id : SHIPPED) {
            JsonObject recipe = Json.parse(jar.bytes("data/roulettecasino/recipe/" + id + ".json"));
            if (!"minecraft:crafting_shaped".equals(recipe.get("type").getAsString())) {
                throw new IllegalStateException("recipe " + id + " is not shaped");
            }
            JsonObject r = new JsonObject();
            r.addProperty("id", id);
            r.add("pattern", recipe.getAsJsonArray("pattern").deepCopy());
            Map<String, String> keys = new TreeMap<>();
            for (Map.Entry<String, JsonElement> e : recipe.getAsJsonObject("key").entrySet()) {
                if (!e.getValue().isJsonPrimitive()) {
                    throw new IllegalStateException("recipe " + id + ": ingredient " + e.getKey() + " is not one id");
                }
                keys.put(e.getKey(), e.getValue().getAsString());
            }
            JsonObject key = new JsonObject();
            keys.forEach(key::addProperty);
            r.add("key", key);
            JsonObject result = recipe.getAsJsonObject("result");
            r.addProperty("result", result.get("id").getAsString());
            r.addProperty("count", result.has("count") ? result.get("count").getAsInt() : 1);
            JsonObject advancement = Json.parse(jar.bytes("data/roulettecasino/advancement/recipes/" + id + ".json"));
            JsonArray items = advancement.getAsJsonObject("criteria").getAsJsonObject("has_ingredient")
                    .getAsJsonObject("conditions").getAsJsonArray("items");
            if (items.size() != 1) {
                throw new IllegalStateException("recipe " + id + ": unlock by " + items.size() + " items");
            }
            r.addProperty("unlock", items.get(0).getAsJsonObject().get("items").getAsString());
            out.add(r);
        }
        return Json.object("recipes", out);
    }
}
