package de.tigges.kaboofcasino.chipshop;

import de.tigges.kaboofcasino.machine.Recipes;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

/**
 * Chip-Shop prices from the mod's recipes (PLAN-2 C6): an item's cost is the cheapest of its recipes, the sum of its
 * ingredients' values per crafted item; ingredients are valued from {@code values} (ores at the cashier's rates, the
 * rest from {@code chipshop.values}) or, for casino items, by their own cost (found by repeated relaxation, so recipe
 * cycles such as block and slab never loop). The price is cost x markup, rounded to round amounts (below 25 to whole
 * chips, below 250 to 5, else to 25), at least 1. Pure.
 */
public final class ChipShopPrices {

    private ChipShopPrices() {
    }

    /** Ingredients per crafted item of one recipe: id (or #tag) to amount. */
    static Map<String, Double> perItem(Recipes.Def r) {
        Map<String, Double> out = new LinkedHashMap<>();
        if (r.shapeless()) {
            for (String i : r.ingredients()) {
                out.merge(i, 1.0, Double::sum);
            }
        } else {
            for (String row : r.pattern()) {
                for (char c : row.toCharArray()) {
                    String i = r.key().get(c);
                    if (i != null) {
                        out.merge(i, 1.0, Double::sum);
                    }
                }
            }
        }
        out.replaceAll((k, v) -> v / Math.max(1, r.count()));
        return out;
    }

    /**
     * The raw cost (chips, before markup) of every recipe result that can be valued; items whose recipes use an
     * unvalued ingredient (and have no other recipe) are missing.
     */
    public static Map<String, Double> costs(List<Recipes.Def> recipes, Map<String, Double> values) {
        Map<String, Double> cost = new HashMap<>(values);
        boolean changed = true;
        for (int pass = 0; changed && pass < 64; pass++) {
            changed = false;
            for (Recipes.Def r : recipes) {
                double sum = 0;
                boolean known = true;
                for (Map.Entry<String, Double> e : perItem(r).entrySet()) {
                    Double v = cost.get(e.getKey());
                    if (v == null) {
                        known = false;
                        break;
                    }
                    sum += v * e.getValue();
                }
                if (!known) {
                    continue;
                }
                Double old = cost.get(r.result());
                if (old == null || sum < old - 1e-9) {
                    cost.put(r.result(), sum);
                    changed = true;
                }
            }
        }
        Map<String, Double> out = new HashMap<>();
        for (Recipes.Def r : recipes) {
            Double c = cost.get(r.result());
            if (c != null) {
                out.put(r.result(), c);
            }
        }
        return out;
    }

    /** A cost times the markup, rounded to the 1/5/25 grid, at least 1 chip. */
    public static long price(double cost, double markup) {
        double p = cost * markup;
        long step = p < 25 ? 1 : p < 250 ? 5 : 25;
        long rounded = Math.round(p / step) * step;
        return Math.max(1, rounded);
    }

    /** The automatic price of {@code id}, if its cost is known. */
    public static OptionalLong price(Map<String, Double> costs, String id, double markup) {
        Double c = costs.get(id);
        return c == null ? OptionalLong.empty() : OptionalLong.of(price(c, markup));
    }
}
