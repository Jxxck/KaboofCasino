package de.tigges.kaboofcasino.machine;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.util.Keys;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;

/**
 * The mod's crafting recipes for the shipped machines (games.md 13), read from {@code pack/recipes.json}, which the
 * pack tool extracts from the mod's recipe and recipe-advancement JSON. Casino ingredients are paper, matched as
 * {@code MaterialChoice(PAPER)}; {@link ItemVetoListener} checks the PDC tag of every paper in the grid against
 * {@link #paperRule}, so stale item components never break crafting. Recipes unlock in the recipe book on first
 * possession of the advancement's ingredient ({@link #unlockedBy}).
 */
public final class Recipes {

    /** What the paper slots of a recipe must hold: a casino item, or {@code null} for plain paper. */
    public record PaperRule(CasinoItem expected) {
    }

    /** One mod recipe: pattern rows, ingredient ids per key ({@code minecraft:x}, {@code #minecraft:tag},
     * {@code roulettecasino:x}), result, and the id whose possession unlocks it. */
    public record Def(String id, List<String> pattern, Map<Character, String> key, String result, int count,
                      String unlock) {
        public Def {
            pattern = List.copyOf(pattern);
            key = Map.copyOf(key);
        }

        public NamespacedKey namespaced() {
            return Keys.key(id);
        }

        /** The paper rule: the one casino ingredient, plain paper, or none. */
        public Optional<PaperRule> paperRule() {
            CasinoItem expected = null;
            boolean paper = false;
            for (String ingredient : key.values()) {
                if (ingredient.equals("minecraft:paper")) {
                    paper = true;
                } else if (ingredient.startsWith("roulettecasino:")) {
                    CasinoItem item = casinoItem(ingredient);
                    if (expected != null && expected != item) {
                        throw new IllegalStateException("recipe " + id + " needs two different casino items");
                    }
                    expected = item;
                }
            }
            if (paper && expected != null) {
                throw new IllegalStateException("recipe " + id + " mixes plain paper and casino items");
            }
            return paper || expected != null ? Optional.of(new PaperRule(expected)) : Optional.empty();
        }
    }

    private static volatile List<Def> defs = List.of();

    private Recipes() {
    }

    /** Parses {@code recipes.json} (pure; checked by unit tests against the built pack). */
    public static List<Def> parse(InputStream in) {
        try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            List<Def> out = new ArrayList<>();
            for (JsonElement e : root.getAsJsonArray("recipes")) {
                JsonObject r = e.getAsJsonObject();
                List<String> pattern = new ArrayList<>();
                r.getAsJsonArray("pattern").forEach(p -> pattern.add(p.getAsString()));
                Map<Character, String> key = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> k : r.getAsJsonObject("key").entrySet()) {
                    key.put(k.getKey().charAt(0), k.getValue().getAsString());
                }
                out.add(new Def(r.get("id").getAsString(), pattern, key, r.get("result").getAsString(),
                        r.get("count").getAsInt(), r.get("unlock").getAsString()));
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

    static CasinoItem casinoItem(String id) {
        return CasinoItem.byId(id.substring("roulettecasino:".length()))
                .orElseThrow(() -> new IllegalStateException("unknown casino item " + id));
    }

    /** The recipes a held item unlocks (the mod's {@code advancement/recipes/*}: inventory_changed triggers). */
    public static List<NamespacedKey> unlockedBy(ItemStack stack) {
        Optional<CasinoItem> casino = CasinoItems.of(stack);
        String id = casino.map(c -> "roulettecasino:" + c.id()).orElse(
                stack.getType() == Material.PAPER && CasinoItems.isCasinoItem(stack) ? null
                        : stack.getType().getKey().asString());
        if (id == null) {
            return List.of();
        }
        List<NamespacedKey> out = new ArrayList<>();
        for (Def d : defs) {
            if (d.unlock().equals(id)) {
                out.add(d.namespaced());
            }
        }
        return out;
    }

    public static Optional<PaperRule> paperRule(NamespacedKey key) {
        for (Def d : defs) {
            if (d.namespaced().equals(key)) {
                return d.paperRule();
            }
        }
        return Optional.empty();
    }

    /** Registers (or re-registers after /reload) every recipe. */
    public static void register(Messages messages) {
        unregister();
        for (Def d : defs) {
            ItemStack result = d.result().startsWith("roulettecasino:")
                    ? CasinoItems.create(casinoItem(d.result()), messages, d.count())
                    : ItemStack.of(material(d.result()), d.count());
            ShapedRecipe recipe = new ShapedRecipe(d.namespaced(), result);
            recipe.shape(d.pattern().toArray(String[]::new));
            for (Map.Entry<Character, String> k : d.key().entrySet()) {
                recipe.setIngredient(k.getKey(), choice(k.getValue()));
            }
            Bukkit.addRecipe(recipe);
        }
    }

    private static RecipeChoice choice(String ingredient) {
        if (ingredient.startsWith("#")) {
            NamespacedKey tag = NamespacedKey.fromString(ingredient.substring(1));
            Tag<Material> items = tag == null ? null : Bukkit.getTag(Tag.REGISTRY_ITEMS, tag, Material.class);
            if (items == null) {
                throw new IllegalStateException("unknown item tag " + ingredient);
            }
            return new RecipeChoice.MaterialChoice(items);
        }
        if (ingredient.startsWith("roulettecasino:") || ingredient.equals("minecraft:paper")) {
            return new RecipeChoice.MaterialChoice(Material.PAPER);
        }
        return new RecipeChoice.MaterialChoice(material(ingredient));
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
    }

    /** True if every paper in {@code matrix} satisfies the recipe's paper rule. */
    public static boolean papersValid(PaperRule rule, ItemStack[] matrix) {
        for (ItemStack stack : matrix) {
            if (stack == null || stack.getType() != Material.PAPER) {
                continue;
            }
            Optional<CasinoItem> item = CasinoItems.of(stack);
            if (rule.expected() == null ? CasinoItems.isCasinoItem(stack) : item.orElse(null) != rule.expected()) {
                return false;
            }
        }
        return true;
    }
}
