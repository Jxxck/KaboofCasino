package de.tigges.kaboofcasino.chipshop;

import de.tigges.kaboofcasino.cashier.CashierSettings;
import de.tigges.kaboofcasino.machine.CasinoItem;
import de.tigges.kaboofcasino.machine.Recipes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;

/**
 * What the Chip-Shop sells (PLAN-2 C6), built from the casino's item ids (the item catalog): every deco block, seat,
 * the speaker, the TVs, the dealer station, the guide, the machines without a croupier's badge with their parts, the
 * cashier, the leaderboards and the shop itself (Jack 2026-11-10: everything except what needs a croupier); not the
 * chips and the croupier's badge, hiring card and hand; the blackjack and poker tables only with
 * {@code chipshop.tables}. Each entry has its tab, its unit price (a fixed price
 * from the config, else from the recipe cost) and its daily limit; hidden items and items without a price are left
 * out (the latter are reported). Pure.
 */
public final class ChipShopCatalog {

    /**
     * One article: the casino id, its tab, the price of one, the daily limit (0 = none), automatic or fixed; for an
     * automatic price the value per piece (cost x markup) and the tab's minimum per piece, so a stack is priced as a
     * whole ({@link #total}).
     */
    public record Entry(String id, ChipShopCategory category, long price, int limit, boolean fixed, double perPiece,
                        long min) {
        /** The price of {@code pieces} at once: fixed prices per piece, automatic ones rounded as a whole. */
        public long total(int pieces) {
            return fixed ? price * pieces : ChipShopPrices.total(perPiece, min, pieces);
        }
    }

    /** The catalog: entries by tab (catalog order), and the ids that could not be priced. */
    public record Catalog(Map<ChipShopCategory, List<Entry>> byCategory, List<String> unpriced) {
        public Catalog {
            byCategory = Map.copyOf(byCategory);
            unpriced = List.copyOf(unpriced);
        }

        public List<Entry> of(ChipShopCategory c) {
            return byCategory.getOrDefault(c, List.of());
        }

        public java.util.Optional<Entry> entry(String id) {
            for (List<Entry> l : byCategory.values()) {
                for (Entry e : l) {
                    if (e.id().equals(id)) {
                        return java.util.Optional.of(e);
                    }
                }
            }
            return java.util.Optional.empty();
        }

        public int size() {
            return byCategory.values().stream().mapToInt(List::size).sum();
        }
    }

    /** The croupier's things: never sold (the tables need the badge too, see {@link #TABLES}). */
    static final Set<String> NEVER = ids(CasinoItem.CROUPIER_BADGE, CasinoItem.CROUPIER_HIRING_CARD,
            CasinoItem.CROUPIER_HAND);
    static final Set<String> TABLES = ids(CasinoItem.BLACKJACK_TABLE, CasinoItem.POKER_TABLE);

    /** The catalog ids ({@code roulettecasino:<id>}) of machine items. */
    private static Set<String> ids(CasinoItem... items) {
        Set<String> out = new java.util.HashSet<>();
        for (CasinoItem i : items) {
            out.add("roulettecasino:" + i.id());
        }
        return Set.copyOf(out);
    }

    private ChipShopCatalog() {
    }

    /** True if {@code id} can be sold at all (before hiding and pricing). */
    public static boolean sellable(String id, boolean tables) {
        return !id.startsWith("kaboofcasino:") && !NEVER.contains(id) && (tables || !TABLES.contains(id));
    }

    /** The values the recipe costs start from: the shop's table plus the ores at the cashier's rates. */
    public static Map<String, Double> values(ChipShopSettings shop, CashierSettings cashier) {
        Map<String, Double> v = new HashMap<>(shop.values());
        for (CashierSettings.Rate r : cashier.rates()) {
            v.put(r.item(), (double) r.value());
        }
        nugget(v, "minecraft:gold_nugget", "minecraft:gold_ingot");
        nugget(v, "minecraft:iron_nugget", "minecraft:iron_ingot");
        // the cashier's and the shop's recipes take glass panes: 6 glass make 16 (also with an older values list)
        derive(v, "minecraft:glass_pane", "minecraft:glass", 6.0 / 16.0);
        return v;
    }

    private static void nugget(Map<String, Double> v, String nugget, String ingot) {
        derive(v, nugget, ingot, 1.0 / 9.0);
    }

    /** {@code part} at {@code factor} times {@code whole}, unless the values list prices it itself. */
    private static void derive(Map<String, Double> v, String part, String whole, double factor) {
        if (!v.containsKey(part) && v.containsKey(whole)) {
            v.put(part, v.get(whole) * factor);
        }
    }

    /** One line of the croupier's-hand dialog: also hidden and unpriced items, with the automatic price if any. */
    public record Row(String id, ChipShopCategory category, OptionalLong auto, ChipShopSettings.Item override) {
    }

    /** Every sellable item, tab by tab (catalog order within a tab), for the price dialog. */
    public static List<Row> rows(List<String> ids, List<Recipes.Def> recipes, ChipShopSettings shop,
                                 CashierSettings cashier) {
        Map<String, Double> costs = ChipShopPrices.costs(recipes, values(shop, cashier));
        List<Row> out = new ArrayList<>();
        for (ChipShopCategory c : ChipShopCategory.values()) {
            for (String id : ids) {
                if (sellable(id, shop.tables()) && ChipShopCategory.of(id) == c) {
                    Double cost = costs.get(id);
                    ChipShopSettings.Pricing pr = shop.pricing(c);
                    out.add(new Row(id, c, cost == null ? OptionalLong.empty()
                            : OptionalLong.of(ChipShopPrices.price(cost, pr.markup(), pr.min(id))), shop.item(id)));
                }
            }
        }
        return List.copyOf(out);
    }

    public static Catalog build(List<String> ids, List<Recipes.Def> recipes, ChipShopSettings shop,
                                CashierSettings cashier) {
        Map<String, Double> costs = ChipShopPrices.costs(recipes, values(shop, cashier));
        Map<ChipShopCategory, List<Entry>> by = new HashMap<>();
        List<String> unpriced = new ArrayList<>();
        for (String id : ids) {
            if (!sellable(id, shop.tables())) {
                continue;
            }
            ChipShopSettings.Item o = shop.item(id);
            if (o.hidden()) {
                continue;
            }
            ChipShopCategory c = ChipShopCategory.of(id);
            ChipShopSettings.Pricing pr = shop.pricing(c);
            Double cost = costs.get(id);
            if (o.price() <= 0 && cost == null) {
                unpriced.add(id);
                continue;
            }
            double perPiece = cost == null ? 0 : cost * pr.markup();
            long min = pr.min(id);
            long price = o.price() > 0 ? o.price() : ChipShopPrices.total(perPiece, min, 1);
            by.computeIfAbsent(c, k -> new ArrayList<>()).add(new Entry(id, c, price, o.limit(), o.price() > 0,
                    perPiece, min));
        }
        by.computeIfPresent(ChipShopCategory.MACHINES, (k, l) -> {
            l.sort(java.util.Comparator.comparing(e -> !e.id().equals(ChipShopCategory.GUIDE)));
            return l;
        });
        // the speaker and the TVs first, the leaderboards after them (stable: smallest to largest as catalogued)
        by.computeIfPresent(ChipShopCategory.TECH, (k, l) -> {
            l.sort(java.util.Comparator.comparing(e -> e.id().startsWith("roulettecasino:leaderboard_")));
            return l;
        });
        return new Catalog(by, unpriced);
    }
}
