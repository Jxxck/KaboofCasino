package de.tigges.kaboofcasino.cashier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;

/**
 * The cashier's part of {@code config.yml} (PLAN-2 C3): {@code cashier.rates} (chip value per item), {@code
 * cashier.craftable} and {@code shop.items} (the prizes). Rates and prizes are YAML <b>lists</b>, not maps: the
 * start-up merge adds only keys the live file lacks ({@code YamlFiles}), so a list is taken over whole and entries
 * Jack deleted never come back. Pure.
 *
 * @param rates     what the cashier takes and its chip value per item (plain stacks only)
 * @param craftable true: the cashier has a recipe ({@link #RECIPE}); false (default): only {@code /casino give}
 * @param shop      the prizes in shop order
 */
public record CashierSettings(List<Rate> rates, boolean craftable, List<ShopEntry> shop) {

    /** Highest rate (chips per item). */
    public static final long MAX_RATE = 1_000_000L;
    /** Most prizes in the shop (four menu pages of 36). */
    public static final int MAX_SHOP = 144;
    private static final Pattern KEY = Pattern.compile("[a-z0-9_.\\-]+:[a-z0-9_./\\-]+");

    /**
     * The optional recipe (in the plugin's own namespace, {@code kaboofcasino:cashier}), built from what the cashier
     * is made of: glass pane, small casino sign, glass pane over black marble bar, cash register, black marble bar.
     */
    public static final List<String> RECIPE = List.of("GSG", "BRB");
    public static final Map<Character, String> RECIPE_KEY = Map.of('G', "minecraft:glass_pane",
            'S', "casinodeco:small_casino_sign", 'B', "casinodeco:black_marble_bar", 'R', "casinodeco:cash_register");
    /** The recipe unlocks when the player holds a cash register. */
    public static final String RECIPE_UNLOCK = "casinodeco:cash_register";

    /** One rate: {@code value} chips per {@code item} (a material key). */
    public record Rate(String item, long value) {
        public Rate {
            if (item == null || !KEY.matcher(item).matches() || item.startsWith("kaboofcasino:")) {
                throw new IllegalArgumentException("not an item key for a rate: " + item);
            }
            if (value < 1 || value > MAX_RATE) {
                throw new IllegalArgumentException("rate outside 1.." + MAX_RATE + ": " + value);
            }
        }

        public Map<String, Object> toYaml() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("item", item);
            out.put("value", (int) value);
            return out;
        }
    }

    public CashierSettings {
        rates = List.copyOf(rates);
        shop = List.copyOf(shop);
    }

    /** PLAN-2 C3: ingots and gems, and each block at 9 times. */
    public static final List<Rate> DEFAULT_RATES = List.of(
            new Rate("minecraft:coal", 1), new Rate("minecraft:coal_block", 9),
            new Rate("minecraft:copper_ingot", 1), new Rate("minecraft:copper_block", 9),
            new Rate("minecraft:redstone", 1), new Rate("minecraft:redstone_block", 9),
            new Rate("minecraft:lapis_lazuli", 2), new Rate("minecraft:lapis_block", 18),
            new Rate("minecraft:iron_ingot", 4), new Rate("minecraft:iron_block", 36),
            new Rate("minecraft:gold_ingot", 8), new Rate("minecraft:gold_block", 72),
            new Rate("minecraft:emerald", 16), new Rate("minecraft:emerald_block", 144),
            new Rate("minecraft:diamond", 32), new Rate("minecraft:diamond_block", 288),
            new Rate("minecraft:netherite_ingot", 256), new Rate("minecraft:netherite_block", 2304));

    /**
     * No prizes by default (step 11 review): the deco is sold in the Chip-Shop; the cashier's Prämien are the admin's
     * special prizes. (0.9.0-0.11.0 shipped six examples, see the step 11 ship notes.)
     */
    public static final List<ShopEntry> DEFAULT_SHOP = List.of();

    public static final CashierSettings DEFAULTS = new CashierSettings(DEFAULT_RATES, false, DEFAULT_SHOP);

    /** The chip value of one {@code item}, empty if the cashier does not take it. */
    public OptionalLong rate(String item) {
        for (Rate r : rates) {
            if (r.item().equals(item)) {
                return OptionalLong.of(r.value());
            }
        }
        return OptionalLong.empty();
    }

    public CashierSettings withShop(List<ShopEntry> newShop) {
        return new CashierSettings(rates, craftable, newShop);
    }

    /**
     * Reads {@code cashier.*} and {@code shop.items} of the (merged) config; a bad value gives an English warning
     * (like {@code ConfigLoader}) and is dropped or replaced by its default.
     */
    public static CashierSettings read(ConfigurationSection y, List<String> warnings) {
        boolean craftable = DEFAULTS.craftable();
        Object c = y.get("cashier.craftable");
        if (c instanceof Boolean b) {
            craftable = b;
        } else {
            warnings.add("config.yml: cashier.craftable must be true or false (got " + c + "); using " + craftable);
        }
        return new CashierSettings(rates(y.get("cashier.rates"), warnings), craftable,
                shop(y.get("shop.items"), warnings));
    }

    static List<Rate> rates(Object v, List<String> warnings) {
        if (!(v instanceof List<?> list)) {
            warnings.add("config.yml: cashier.rates must be a list of {item, value}; using the defaults");
            return DEFAULT_RATES;
        }
        List<Rate> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < list.size(); i++) {
            String where = "config.yml: cashier.rates entry " + (i + 1);
            if (!(list.get(i) instanceof Map<?, ?> map)) {
                warnings.add(where + " is not a map with item/value; dropped");
                continue;
            }
            Object item = map.get("item");
            Object value = map.get("value");
            String key = item instanceof String s ? s.strip().toLowerCase(Locale.ROOT) : null;
            if (key != null && !key.contains(":")) {
                key = "minecraft:" + key;
            }
            if (key == null || !KEY.matcher(key).matches() || key.startsWith("kaboofcasino:")) {
                warnings.add(where + ": '" + item + "' is not an item key; dropped");
                continue;
            }
            if (!(value instanceof Number n) || n.doubleValue() != Math.rint(n.doubleValue()) || n.doubleValue() < 1
                    || n.doubleValue() > MAX_RATE) {
                warnings.add(where + " (" + key + "): value must be a whole number 1.." + MAX_RATE + " (got " + value
                        + "); dropped");
                continue;
            }
            if (!seen.add(key)) {
                warnings.add(where + ": " + key + " is listed twice; the first rate counts");
                continue;
            }
            out.add(new Rate(key, n.longValue()));
        }
        return List.copyOf(out);
    }

    static List<ShopEntry> shop(Object v, List<String> warnings) {
        if (v == null) {
            warnings.add("config.yml: shop.items is missing; using the default prizes");
            return DEFAULT_SHOP;
        }
        if (!(v instanceof List<?> list)) {
            warnings.add("config.yml: shop.items must be a list of {item, count, price, limit}; the shop is empty");
            return List.of();
        }
        List<ShopEntry> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            ShopEntry e = ShopEntry.fromYaml(list.get(i), i, warnings);
            if (e == null) {
                continue;
            }
            if (out.size() >= MAX_SHOP) {
                warnings.add("config.yml: shop.items has more than " + MAX_SHOP + " prizes; the rest is ignored");
                break;
            }
            out.add(e);
        }
        return List.copyOf(out);
    }
}
