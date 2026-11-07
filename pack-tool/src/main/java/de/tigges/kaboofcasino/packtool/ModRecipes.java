package de.tigges.kaboofcasino.packtool;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The mod's crafting recipes and their recipe-book unlocks for everything built so far, read from the mod jar's
 * {@code data/<namespace>/recipe/*.json} and {@code advancement/recipes/*.json} (the inventory_changed criterion) into
 * {@code recipes.json}, which the plugin registers as Bukkit shaped or shapeless recipes. Ingredients are single ids
 * or tags; anything else fails the build. A recipe's {@code id} is its Bukkit key path under {@code kaboofcasino:}:
 * the plain name for roulettecasino (unchanged since step 1), {@code <namespace>/<name>} for the other two.
 */
final class ModRecipes {

    static final String FILE = "recipes.json";
    /** The machine recipes built so far, in the mod's unlock order, then the seats. */
    static final List<String> SHIPPED = List.of("slot_machine_reel", "slot_machine_mechanism", "slot_machine",
            "roulette_mechanism", "fortune_wheel", "plinko", "roulette_table", "blackjack_table", "casino_chair",
            "casino_stool");
    /** Namespaces whose recipes all ship (step 4: every deco block and the speaker). */
    static final List<String> ALL_OF = List.of("casinodeco", "casinosound");

    private ModRecipes() {
    }

    static JsonObject build(ModJar jar) {
        JsonArray out = new JsonArray();
        for (String id : SHIPPED) {
            out.add(recipe(jar, "roulettecasino", id, id));
        }
        for (String ns : ALL_OF) {
            String dir = "data/" + ns + "/recipe/";
            for (String path : jar.list(dir)) {
                String name = path.substring(dir.length(), path.length() - ".json".length());
                out.add(recipe(jar, ns, name, ns + "/" + name));
            }
        }
        return Json.object("recipes", out);
    }

    private static JsonObject recipe(ModJar jar, String ns, String name, String key) {
        JsonObject recipe = Json.parse(jar.bytes("data/" + ns + "/recipe/" + name + ".json"));
        String type = recipe.get("type").getAsString();
        JsonObject r = new JsonObject();
        r.addProperty("id", key);
        if ("minecraft:crafting_shaped".equals(type)) {
            r.addProperty("type", "shaped");
            r.add("pattern", recipe.getAsJsonArray("pattern").deepCopy());
            Map<String, String> keys = new TreeMap<>();
            for (Map.Entry<String, JsonElement> e : recipe.getAsJsonObject("key").entrySet()) {
                keys.put(e.getKey(), single(ns + ":" + name, e.getValue()));
            }
            JsonObject keyObject = new JsonObject();
            keys.forEach(keyObject::addProperty);
            r.add("key", keyObject);
        } else if ("minecraft:crafting_shapeless".equals(type)) {
            r.addProperty("type", "shapeless");
            JsonArray ingredients = new JsonArray();
            for (JsonElement e : recipe.getAsJsonArray("ingredients")) {
                ingredients.add(single(ns + ":" + name, e));
            }
            r.add("ingredients", ingredients);
        } else {
            throw new IllegalStateException("recipe " + ns + ":" + name + " has type " + type);
        }
        JsonObject result = recipe.getAsJsonObject("result");
        r.addProperty("result", result.get("id").getAsString());
        r.addProperty("count", result.has("count") ? result.get("count").getAsInt() : 1);
        r.add("unlock", unlock(jar, ns, name));
        return r;
    }

    private static String single(String recipe, JsonElement ingredient) {
        if (!ingredient.isJsonPrimitive()) {
            throw new IllegalStateException("recipe " + recipe + ": ingredient " + ingredient + " is not one id");
        }
        return ingredient.getAsString();
    }

    /**
     * The items of the advancement's one inventory_changed criterion (named has_ingredient or has_<item>): one id, or
     * several of which any unlocks (glass display shelf, serving tray).
     */
    private static JsonArray unlock(ModJar jar, String ns, String name) {
        JsonObject advancement = Json.parse(jar.bytes("data/" + ns + "/advancement/recipes/" + name + ".json"));
        JsonArray found = null;
        for (Map.Entry<String, JsonElement> c : advancement.getAsJsonObject("criteria").entrySet()) {
            JsonObject criterion = c.getValue().getAsJsonObject();
            if (!"minecraft:inventory_changed".equals(criterion.get("trigger").getAsString())) {
                continue;
            }
            JsonArray items = criterion.getAsJsonObject("conditions").getAsJsonArray("items");
            if (items.size() != 1 || found != null) {
                throw new IllegalStateException("recipe " + ns + ":" + name + ": unlock is not one item");
            }
            JsonElement ids = items.get(0).getAsJsonObject().get("items");
            found = new JsonArray();
            if (ids.isJsonArray()) {
                for (JsonElement id : ids.getAsJsonArray()) {
                    found.add(id.getAsString());
                }
            } else {
                found.add(ids.getAsString());
            }
        }
        if (found == null) {
            throw new IllegalStateException("recipe " + ns + ":" + name + " has no inventory_changed unlock");
        }
        return found;
    }
}
