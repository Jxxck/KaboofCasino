package de.tigges.kaboofcasino.cashier;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One prize of the cashier's shop ({@code shop.items}, PLAN-2 C3): what a purchase hands out, its price in chips and
 * the optional limit per player and day. Pure.
 *
 * @param item  a casino item id ({@code roulettecasino:casino_chair}, built fresh so its name follows the texts) or a
 *              vanilla item with components as the {@code /give} syntax writes it
 *              ({@code minecraft:diamond_sword[enchantments={sharpness:5}]}); never a casino chip
 * @param count items per purchase (1..{@value #MAX_COUNT})
 * @param price chips per purchase (a chip value, 1..{@value #MAX_PRICE})
 * @param limit purchases per player and day (Europe/Berlin date), 0 = no limit (..{@value #MAX_LIMIT})
 */
public record ShopEntry(String item, int count, long price, int limit) {

    public static final int MAX_COUNT = 64;
    public static final long MAX_PRICE = 100_000_000L;
    public static final int MAX_LIMIT = 1000;
    /** Longest item text (a heavily enchanted book is a few hundred characters). */
    public static final int MAX_ITEM = 4096;
    /** {@code namespace:path} with an optional {@code [components]} part. */
    private static final Pattern ITEM = Pattern.compile("[a-z0-9_.\\-]+:[a-z0-9_./\\-]+(\\[.*])?", Pattern.DOTALL);
    private static final java.util.Set<String> CASINO_NAMESPACES = java.util.Set.of("roulettecasino", "casinodeco",
            "casinosound");

    public ShopEntry {
        Objects.requireNonNull(item, "item");
        String why = invalid(item);
        if (why != null) {
            throw new IllegalArgumentException(why);
        }
        if (count < 1 || count > MAX_COUNT) {
            throw new IllegalArgumentException("count outside 1.." + MAX_COUNT + ": " + count);
        }
        if (price < 1 || price > MAX_PRICE) {
            throw new IllegalArgumentException("price outside 1.." + MAX_PRICE + ": " + price);
        }
        if (limit < 0 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit outside 0.." + MAX_LIMIT + ": " + limit);
        }
    }

    /** Why {@code item} cannot be a prize, or null if it can (syntax only; whether it exists is checked live). */
    public static String invalid(String item) {
        if (item == null || item.isBlank()) {
            return "no item";
        }
        if (item.length() > MAX_ITEM) {
            return "item text longer than " + MAX_ITEM + " characters";
        }
        if (!ITEM.matcher(item).matches()) {
            return "not an item id: " + item;
        }
        if (namespace(item).equals("kaboofcasino")) {
            return "casino chips are no prize: " + item;
        }
        return null;
    }

    private static String namespace(String item) {
        return item.substring(0, item.indexOf(':'));
    }

    /** The item id without components ({@code minecraft:diamond_sword}). */
    public String id() {
        int bracket = item.indexOf('[');
        return bracket < 0 ? item : item.substring(0, bracket);
    }

    /** True for an item of the casino (machine, deco, seat, speaker): built by the casino, never parsed. */
    public boolean casino() {
        return CASINO_NAMESPACES.contains(namespace(item)) && item.indexOf('[') < 0;
    }

    /**
     * The stable identity of the prize for the daily limits: the first 16 hex digits of the SHA-256 of
     * {@code item|count}. A new price or limit keeps the counts of the day; another item or count starts afresh.
     */
    public String key() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest((item + "|" + count)
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public ShopEntry withPrice(long newPrice) {
        return new ShopEntry(item, count, newPrice, limit);
    }

    public ShopEntry withLimit(int newLimit) {
        return new ShopEntry(item, count, price, newLimit);
    }

    /** The entry as config.yml writes it (a map in the {@code shop.items} list; the limit only when set). */
    public Map<String, Object> toYaml() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("item", item);
        out.put("count", count);
        out.put("price", price <= Integer.MAX_VALUE ? (Object) (int) price : (Object) price);
        out.put("limit", limit);
        return out;
    }

    /** The whole list for {@code shop.items}. */
    public static List<Map<String, Object>> toYaml(List<ShopEntry> entries) {
        return entries.stream().map(ShopEntry::toYaml).toList();
    }

    /**
     * One element of {@code shop.items} (a map with {@code item}, {@code price} and optional {@code count} and
     * {@code limit}); null with a warning if it is invalid.
     */
    public static ShopEntry fromYaml(Object element, int index, List<String> warnings) {
        String where = "config.yml: shop.items entry " + (index + 1);
        if (!(element instanceof Map<?, ?> map)) {
            warnings.add(where + " is not a map with item/price; dropped");
            return null;
        }
        Object item = map.get("item");
        String id = item instanceof String s ? normalized(s) : null;
        if (id == null || invalid(id) != null) {
            warnings.add(where + ": " + (id == null ? "item is missing" : invalid(id)) + "; dropped");
            return null;
        }
        Long count = whole(map.get("count"), 1, 1, MAX_COUNT);
        Long price = whole(map.get("price"), -1, 1, MAX_PRICE);
        Long limit = whole(map.get("limit"), 0, 0, MAX_LIMIT);
        if (count == null || price == null || limit == null) {
            warnings.add(where + " (" + id + "): count must be 1.." + MAX_COUNT + ", price 1.." + MAX_PRICE
                    + ", limit 0.." + MAX_LIMIT + "; dropped");
            return null;
        }
        return new ShopEntry(id, count.intValue(), price, limit.intValue());
    }

    /** Stripped, the id lower case (components keep their case). */
    static String normalized(String raw) {
        String s = raw.strip();
        int bracket = s.indexOf('[');
        return bracket < 0 ? s.toLowerCase(Locale.ROOT) : s.substring(0, bracket).toLowerCase(Locale.ROOT)
                + s.substring(bracket);
    }

    /** A whole number in range, {@code def} if absent (def -1 = required); null if invalid. */
    private static Long whole(Object v, long def, long min, long max) {
        if (v == null) {
            return def < 0 ? null : def;
        }
        if (v instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue()) && n.doubleValue() >= min
                && n.doubleValue() <= max) {
            return n.longValue();
        }
        return null;
    }
}
