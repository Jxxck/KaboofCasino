package de.tigges.kaboofcasino.chipshop;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;

/**
 * The Chip-Shop's settings ({@code chipshop:} in config.yml, PLAN-2 C6).
 *
 * @param craftable  true (default): the shop counter has a recipe like the machines; false: only {@code /casino give chip_shop}
 * @param categories per tab: price = recipe cost x markup, at least the minimum per piece
 *                   ({@code chipshop.categories.<tab>.markup / min-price / min-price-stairs / min-price-slab})
 * @param tables     true: the blackjack and poker tables are sold too (default false: they stay badge-bound)
 * @param values    chip values of the ingredients that are no ores (the ores count at {@code cashier.rates})
 * @param items     per item: a fixed price, a daily limit, hidden (a list, written by the croupier's hand)
 */
public record ChipShopSettings(boolean craftable, Map<ChipShopCategory, Pricing> categories, boolean tables,
                               Map<String, Double> values, List<Item> items) {

    /**
     * One tab's pricing: the markup on the recipe cost (1.0..20.0) and the minimum price per piece (0..1 000 000;
     * stairs and slabs may have their own, 0 = the general one).
     */
    public record Pricing(double markup, long minPrice, long minStairs, long minSlab) {
        /** The minimum price per piece of {@code id}. */
        public long min(String id) {
            if (id.endsWith("_stairs") && minStairs > 0) {
                return minStairs;
            }
            if (id.endsWith("_slab") && minSlab > 0) {
                return minSlab;
            }
            return minPrice;
        }
    }

    /**
     * Jack's prices (2026-11-09, "Deko teurer machen"): floors and walls at 1.25 but at least 5 a block, 4 a stair,
     * 3 a slab; furniture, bar and tables, lights and signs, technology at 3.75 and at least 5; machines at 1.25.
     */
    public static final Map<ChipShopCategory, Pricing> DEFAULT_CATEGORIES = defaultCategories();

    private static Map<ChipShopCategory, Pricing> defaultCategories() {
        Map<ChipShopCategory, Pricing> m = new java.util.EnumMap<>(ChipShopCategory.class);
        m.put(ChipShopCategory.FLOORS, new Pricing(1.25, 5, 4, 3));
        for (ChipShopCategory c : List.of(ChipShopCategory.FURNITURE, ChipShopCategory.BAR, ChipShopCategory.LIGHTS,
                ChipShopCategory.TECH)) {
            m.put(c, new Pricing(3.75, 5, 0, 0));
        }
        m.put(ChipShopCategory.MACHINES, new Pricing(1.25, 0, 0, 0));
        m.put(ChipShopCategory.OUTFITS, new Pricing(15, 50, 0, 0));
        return java.util.Collections.unmodifiableMap(m);
    }

    /** The pricing of {@code c} (the default if the config misses it). */
    public Pricing pricing(ChipShopCategory c) {
        return categories.getOrDefault(c, DEFAULT_CATEGORIES.get(c));
    }

    /** One item's overrides: {@code price} 0 = automatic, {@code limit} 0 = none. */
    public record Item(String item, long price, int limit, boolean hidden) {
        public boolean empty() {
            return price == 0 && limit == 0 && !hidden;
        }

        public Map<String, Object> toYaml() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("item", item);
            if (price > 0) {
                out.put("price", price);
            }
            if (limit > 0) {
                out.put("limit", limit);
            }
            if (hidden) {
                out.put("hidden", true);
            }
            return out;
        }
    }

    public static final long MAX_PRICE = 100_000_000L;
    public static final int MAX_LIMIT = 10_000;
    private static final Pattern KEY = Pattern.compile("#?[a-z0-9_.\\-]+:[a-z0-9_./\\-]+");

    /** The default values (chips per item) of the mod recipes' ingredients that are no ores. */
    public static final Map<String, Double> DEFAULT_VALUES = defaults();

    public static final ChipShopSettings DEFAULTS = new ChipShopSettings(true, DEFAULT_CATEGORIES, false,
            DEFAULT_VALUES, List.of());

    public ChipShopSettings {
        categories = Map.copyOf(categories);
        values = Map.copyOf(values);
        items = List.copyOf(items);
    }

    private static Map<String, Double> defaults() {
        Map<String, Double> v = new LinkedHashMap<>();
        v.put("#minecraft:planks", 0.25);
        v.put("minecraft:dark_oak_planks", 0.25);
        v.put("#minecraft:wooden_slabs", 0.125);
        v.put("#minecraft:signs", 0.54);
        v.put("minecraft:stick", 0.125);
        v.put("minecraft:torch", 0.28);
        for (String c : List.of("black", "blue", "green", "red", "white")) {
            v.put("minecraft:" + c + "_wool", 1.0);
        }
        for (String c : List.of("black", "green", "red", "white", "yellow")) {
            v.put("minecraft:" + c + "_dye", 0.5);
        }
        v.put("minecraft:leather", 1.0);
        v.put("minecraft:paper", 0.5);
        v.put("minecraft:clay_ball", 0.25);
        v.put("minecraft:glass", 0.5);
        v.put("minecraft:green_stained_glass", 0.75);
        v.put("minecraft:tinted_glass", 2.5);
        v.put("minecraft:glass_bottle", 0.5);
        v.put("minecraft:amethyst_shard", 1.0);
        v.put("minecraft:calcite", 0.5);
        v.put("minecraft:glowstone_dust", 1.0);
        v.put("minecraft:glowstone", 4.0);
        v.put("minecraft:iron_chain", 5.0);
        v.put("minecraft:dropper", 2.0);
        v.put("minecraft:piston", 6.0);
        // the croupier's badge has no recipe: what its hiring card costs (24 emeralds at 16)
        v.put("roulettecasino:croupier_badge", 384.0);
        return Map.copyOf(v);
    }

    /** The overrides of {@code id}, or none. */
    public Item item(String id) {
        for (Item i : items) {
            if (i.item().equals(id)) {
                return i;
            }
        }
        return new Item(id, 0, 0, false);
    }

    public static ChipShopSettings read(ConfigurationSection y, List<String> warnings) {
        boolean craftable = bool(y, "chipshop.craftable", DEFAULTS.craftable(), warnings);
        boolean tables = bool(y, "chipshop.tables", DEFAULTS.tables(), warnings);
        Map<ChipShopCategory, Pricing> categories = new java.util.EnumMap<>(ChipShopCategory.class);
        for (ChipShopCategory c : ChipShopCategory.values()) {
            Pricing d = DEFAULT_CATEGORIES.get(c);
            String base = "chipshop.categories." + c.id() + ".";
            categories.put(c, new Pricing(number(y, base + "markup", d.markup(), 1.0, 20.0, warnings),
                    (long) number(y, base + "min-price", d.minPrice(), 0, 1_000_000, warnings),
                    (long) number(y, base + "min-price-stairs", d.minStairs(), 0, 1_000_000, warnings),
                    (long) number(y, base + "min-price-slab", d.minSlab(), 0, 1_000_000, warnings)));
        }
        return new ChipShopSettings(craftable, categories, tables, values(y.get("chipshop.values"), warnings),
                items(y.get("chipshop.items"), warnings));
    }

    private static double number(ConfigurationSection y, String path, double def, double min, double max,
                                 List<String> warnings) {
        Object v = y.get(path);
        if (v == null) {
            return def;
        }
        if (v instanceof Number n && n.doubleValue() >= min && n.doubleValue() <= max) {
            return n.doubleValue();
        }
        warnings.add("config.yml: " + path + " must be a number from " + min + " to " + max + " (got " + v
                + "); using " + def);
        return def;
    }

    private static boolean bool(ConfigurationSection y, String path, boolean def, List<String> warnings) {
        Object v = y.get(path);
        if (v instanceof Boolean b) {
            return b;
        }
        if (v != null) {
            warnings.add("config.yml: " + path + " must be true or false (got " + v + "); using " + def);
        }
        return def;
    }

    static Map<String, Double> values(Object v, List<String> warnings) {
        if (v == null) {
            return DEFAULT_VALUES;
        }
        if (!(v instanceof List<?> list)) {
            warnings.add("config.yml: chipshop.values must be a list of {item, value}; using the defaults");
            return DEFAULT_VALUES;
        }
        Map<String, Double> out = new LinkedHashMap<>();
        for (int i = 0; i < list.size(); i++) {
            String where = "config.yml: chipshop.values entry " + (i + 1);
            if (!(list.get(i) instanceof Map<?, ?> map) || !(map.get("item") instanceof String s)
                    || !(map.get("value") instanceof Number n) || n.doubleValue() < 0 || n.doubleValue() > 1e6) {
                warnings.add(where + " needs item and a value from 0 to 1000000; dropped");
                continue;
            }
            String key = key(s);
            if (key == null) {
                warnings.add(where + ": '" + s + "' is no item id or #tag; dropped");
                continue;
            }
            out.put(key, n.doubleValue());
        }
        return out;
    }

    static List<Item> items(Object v, List<String> warnings) {
        if (v == null) {
            return List.of();
        }
        if (!(v instanceof List<?> list)) {
            warnings.add("config.yml: chipshop.items must be a list of {item, price, limit, hidden}; none used");
            return List.of();
        }
        List<Item> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < list.size(); i++) {
            String where = "config.yml: chipshop.items entry " + (i + 1);
            if (!(list.get(i) instanceof Map<?, ?> map) || !(map.get("item") instanceof String s)
                    || key(s) == null || s.startsWith("#")) {
                warnings.add(where + " needs an item id; dropped");
                continue;
            }
            String id = key(s);
            long price = map.get("price") instanceof Number n ? n.longValue() : 0;
            int limit = map.get("limit") instanceof Number n ? n.intValue() : 0;
            boolean hidden = Boolean.TRUE.equals(map.get("hidden"));
            if (price < 0 || price > MAX_PRICE || limit < 0 || limit > MAX_LIMIT) {
                warnings.add(where + " (" + id + "): price 0-" + MAX_PRICE + ", limit 0-" + MAX_LIMIT + "; dropped");
                continue;
            }
            if (!seen.add(id)) {
                warnings.add(where + ": " + id + " is listed twice; the first entry counts");
                continue;
            }
            out.add(new Item(id, price, limit, hidden));
        }
        return out;
    }

    static String key(String s) {
        String k = s.strip().toLowerCase(Locale.ROOT);
        if (!k.contains(":")) {
            k = (k.startsWith("#") ? "#minecraft:" + k.substring(1) : "minecraft:" + k);
        }
        return KEY.matcher(k).matches() ? k : null;
    }
}
