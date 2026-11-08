package de.tigges.kaboofcasino.machine;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.util.Keys;
import de.tigges.kaboofcasino.util.Log;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

/**
 * The mod's crafting recipes (games.md 13) for everything built so far: the machines, the seats, every deco block
 * and the speaker, read from {@code pack/recipes.json}, which the pack tool extracts from the mod's recipe and
 * recipe-advancement JSON. Bukkit matches casino ingredients by their material only ({@code MaterialChoice}), so
 * {@link ItemVetoListener} checks every crafted grid once more slot by slot ({@link #matches}, {@link RecipeMatch}):
 * casino ingredients by their item id, vanilla ingredients never with a casino item; stale item components therefore
 * never break crafting. Recipes unlock in the recipe book on first possession of the advancement's ingredient
 * ({@link #unlockedBy}).
 */
public final class Recipes {

    /**
     * One mod recipe. {@code id} is the Bukkit key path under {@code kaboofcasino:} ({@code slot_machine},
     * {@code casinodeco/ivory_marble}); ingredients are {@code minecraft:x}, {@code #minecraft:tag} or a casino id
     * ({@code roulettecasino:x}, {@code casinodeco:x}, {@code casinosound:x}); shapeless recipes have no pattern.
     */
    public record Def(String id, boolean shapeless, List<String> pattern, Map<Character, String> key,
                      List<String> ingredients, String result, int count, List<String> unlocks) {
        public Def {
            pattern = List.copyOf(pattern);
            key = Map.copyOf(key);
            ingredients = List.copyOf(ingredients);
            unlocks = List.copyOf(unlocks);
        }

        public NamespacedKey namespaced() {
            return Keys.key(id);
        }

        /** Every ingredient id the recipe uses. */
        public List<String> allIngredients() {
            return shapeless ? ingredients : List.copyOf(key.values());
        }
    }

    private static volatile List<Def> defs = List.of();
    /**
     * The plugin's own recipes (not the mod's) by source: the cashier's while {@code cashier.craftable} is on (step 9),
     * the leaderboards' while {@code leaderboard.craftable} is on (step 10).
     */
    private static final Map<String, List<Def>> OWN = new java.util.concurrent.ConcurrentSkipListMap<>();
    /** The registered recipes by key (only those whose items all exist). */
    private static volatile Map<NamespacedKey, Def> registered = Map.of();
    /** The result stack of every registered recipe. */
    private static volatile Map<NamespacedKey, ItemStack> results = Map.of();
    /** Tag ingredients resolved at registration. */
    private static final Map<String, Tag<Material>> TAGS = new HashMap<>();

    private Recipes() {
    }

    /** True for an id of one of the mod's three namespaces. */
    public static boolean casinoId(String id) {
        return id.startsWith("roulettecasino:") || id.startsWith("casinodeco:") || id.startsWith("casinosound:");
    }

    /** Parses {@code recipes.json} (pure; checked by unit tests against the built pack). */
    public static List<Def> parse(InputStream in) {
        try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            List<Def> out = new ArrayList<>();
            for (JsonElement e : root.getAsJsonArray("recipes")) {
                JsonObject r = e.getAsJsonObject();
                boolean shapeless = "shapeless".equals(r.get("type").getAsString());
                List<String> pattern = new ArrayList<>();
                Map<Character, String> key = new LinkedHashMap<>();
                List<String> ingredients = new ArrayList<>();
                if (shapeless) {
                    r.getAsJsonArray("ingredients").forEach(i -> ingredients.add(i.getAsString()));
                } else {
                    r.getAsJsonArray("pattern").forEach(p -> pattern.add(p.getAsString()));
                    for (Map.Entry<String, JsonElement> k : r.getAsJsonObject("key").entrySet()) {
                        key.put(k.getKey().charAt(0), k.getValue().getAsString());
                    }
                }
                List<String> unlocks = new ArrayList<>();
                r.getAsJsonArray("unlock").forEach(u -> unlocks.add(u.getAsString()));
                out.add(new Def(r.get("id").getAsString(), shapeless, pattern, key, ingredients,
                        r.get("result").getAsString(), r.get("count").getAsInt(), unlocks));
            }
            return List.copyOf(out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Loads the recipes from the plugin jar ({@code pack/recipes.json}). */
    public static void load(ClassLoader loader) {
        try (InputStream in = loader.getResourceAsStream("pack/recipes.json")) {
            if (in == null) {
                throw new IllegalStateException("pack/recipes.json missing from the plugin jar");
            }
            defs = parse(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static List<Def> all() {
        return defs;
    }

    /**
     * Sets one source's own recipes ({@code "cashier"}, {@code "leaderboard"}); they are registered (or dropped) with
     * the mod's at the next {@link #register}.
     */
    public static void own(String source, List<Def> recipes) {
        OWN.put(source, List.copyOf(recipes));
    }

    /** A casino stack: a machine item ({@link CasinoItems}) or a registered deco/seat/speaker item. */
    public static Optional<ItemStack> casinoStack(String id, Messages messages, int amount) {
        if (id.startsWith("roulettecasino:")) {
            Optional<CasinoItem> item = CasinoItem.byId(id.substring("roulettecasino:".length()));
            if (item.isPresent()) {
                return Optional.of(CasinoItems.create(item.get(), messages, amount));
            }
        }
        return ItemCatalog.createRegistered(id, amount);
    }

    /** The recipes a held item unlocks (the mod's {@code advancement/recipes/*}: inventory_changed triggers). */
    public static List<NamespacedKey> unlockedBy(ItemStack stack) {
        String id = ItemCatalog.idOf(stack).orElse(stack.getType().getKey().asString());
        List<NamespacedKey> out = new ArrayList<>();
        for (Def d : registered.values()) {
            if (d.unlocks().contains(id)) {
                out.add(d.namespaced());
            }
        }
        return out;
    }

    /** Registers (or re-registers after /reload) every recipe whose items exist; the rest is logged and skipped. */
    public static void register(Messages messages) {
        unregister();
        Map<NamespacedKey, Def> done = new LinkedHashMap<>();
        Map<NamespacedKey, ItemStack> made = new LinkedHashMap<>();
        List<Def> every = new ArrayList<>(defs);
        OWN.values().forEach(every::addAll);
        for (Def d : every) {
            try {
                ItemStack result = casinoId(d.result()) ? casinoStack(d.result(), messages, d.count())
                        .orElseThrow(() -> new IllegalStateException("unknown casino item " + d.result()))
                        : ItemStack.of(material(d.result()), d.count());
                Recipe recipe;
                if (d.shapeless()) {
                    ShapelessRecipe r = new ShapelessRecipe(d.namespaced(), result);
                    for (String ingredient : d.ingredients()) {
                        r.addIngredient(choice(ingredient, messages));
                    }
                    recipe = r;
                } else {
                    ShapedRecipe r = new ShapedRecipe(d.namespaced(), result);
                    r.shape(d.pattern().toArray(String[]::new));
                    for (Map.Entry<Character, String> k : d.key().entrySet()) {
                        r.setIngredient(k.getKey(), choice(k.getValue(), messages));
                    }
                    recipe = r;
                }
                Bukkit.addRecipe(recipe);
                done.put(d.namespaced(), d);
                made.put(d.namespaced(), result.clone());
            } catch (RuntimeException e) {
                Log.get().warn("recipe {} not registered: {}", d.id(), e.getMessage());
            }
        }
        registered = Map.copyOf(done);
        results = Map.copyOf(made);
    }

    private static RecipeChoice choice(String ingredient, Messages messages) {
        if (ingredient.startsWith("#")) {
            return new RecipeChoice.MaterialChoice(tag(ingredient));
        }
        if (casinoId(ingredient)) {
            Material m = casinoStack(ingredient, messages, 1).map(ItemStack::getType)
                    .orElseThrow(() -> new IllegalStateException("unknown casino ingredient " + ingredient));
            return new RecipeChoice.MaterialChoice(m);
        }
        return new RecipeChoice.MaterialChoice(material(ingredient));
    }

    private static Tag<Material> tag(String ingredient) {
        Tag<Material> cached = TAGS.get(ingredient);
        if (cached != null) {
            return cached;
        }
        NamespacedKey key = NamespacedKey.fromString(ingredient.substring(1));
        Tag<Material> items = key == null ? null : Bukkit.getTag(Tag.REGISTRY_ITEMS, key, Material.class);
        if (items == null) {
            throw new IllegalStateException("unknown item tag " + ingredient);
        }
        TAGS.put(ingredient, items);
        return items;
    }

    private static Material material(String id) {
        Material m = Material.matchMaterial(id);
        if (m == null || !m.isItem()) {
            throw new IllegalStateException("unknown item " + id);
        }
        return m;
    }

    public static void unregister() {
        for (Def d : defs) {
            Bukkit.removeRecipe(d.namespaced());
        }
        for (NamespacedKey key : registered.keySet()) {
            Bukkit.removeRecipe(key); // the plugin's own ones too (dropped from own since)
        }
        registered = Map.of();
        results = Map.of();
    }

    /** True if {@code key} is one of the casino's recipes. */
    public static boolean ours(NamespacedKey key) {
        return registered.containsKey(key);
    }

    /**
     * The slot-exact check of a casino recipe over a crafting grid (3x3 or the inventory's 2x2); false for unknown
     * keys.
     */
    public static boolean matches(NamespacedKey key, ItemStack[] matrix) {
        Def d = registered.get(key);
        if (d == null) {
            return false;
        }
        RecipeMatch.Slot[] grid = new RecipeMatch.Slot[matrix.length];
        for (int i = 0; i < matrix.length; i++) {
            ItemStack s = matrix[i];
            grid[i] = s == null || s.isEmpty() ? null
                    : new RecipeMatch.Slot(s.getType().getKey().asString(), ItemCatalog.idOf(s).orElse(null));
        }
        int width = matrix.length == 4 ? 2 : 3;
        if (d.shapeless()) {
            return RecipeMatch.shapeless(d.ingredients().stream().map(Recipes::predicate).toList(), grid);
        }
        Map<Character, Predicate<RecipeMatch.Slot>> key2 = new HashMap<>();
        d.key().forEach((c, ingredient) -> key2.put(c, predicate(ingredient)));
        return RecipeMatch.shaped(d.pattern(), key2, grid, width);
    }

    /**
     * The casino recipe the grid really holds, slot by slot. Bukkit matches by material, and several casino recipes
     * share one material pattern (all velvet and marble stairs: six mushroom-stem items in the same shape), so the
     * recipe Bukkit picked may be the wrong one of them; the crafting listener therefore sets the result of this one.
     */
    public static Optional<ItemStack> craft(ItemStack[] matrix) {
        for (NamespacedKey key : registered.keySet()) {
            if (matches(key, matrix)) {
                return Optional.of(results.get(key).clone());
            }
        }
        return Optional.empty();
    }

    /** One ingredient: a casino id needs exactly that item; a vanilla id or tag a vanilla stack of that material. */
    static Predicate<RecipeMatch.Slot> predicate(String ingredient) {
        if (casinoId(ingredient)) {
            return s -> ingredient.equals(s.casinoId());
        }
        if (ingredient.startsWith("#")) {
            Tag<Material> t = tag(ingredient);
            return s -> s.casinoId() == null && t.isTagged(material(s.material()));
        }
        return s -> s.casinoId() == null && ingredient.equals(s.material());
    }
}
