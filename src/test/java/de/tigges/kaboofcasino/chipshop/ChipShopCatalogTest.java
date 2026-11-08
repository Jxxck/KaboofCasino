package de.tigges.kaboofcasino.chipshop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.cashier.CashierSettings;
import de.tigges.kaboofcasino.deco.display.DecoType;
import de.tigges.kaboofcasino.deco.full.DecoBlock;
import de.tigges.kaboofcasino.machine.CasinoItem;
import de.tigges.kaboofcasino.machine.Recipes;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The Chip-Shop's catalog from the real recipes (pack/recipes.json) and the real item ids: everything sellable has a
 * price, the excluded items are out, the tabs, the rounding; writes the price list to
 * {@code build/reports/chipshop-prices.txt} for Jack.
 */
class ChipShopCatalogTest {

    static List<Recipes.Def> recipes() {
        return Recipes.parse(ChipShopCatalogTest.class.getClassLoader().getResourceAsStream("pack/recipes.json"));
    }

    /** The ids the item catalog holds at runtime (machines first, then the registered deco and chips). */
    static List<String> ids() {
        List<String> out = new ArrayList<>();
        for (CasinoItem i : CasinoItem.values()) {
            out.add("roulettecasino:" + i.id());
        }
        for (DecoBlock b : DecoBlock.values()) {
            out.add(b.modId());
        }
        for (DecoType t : DecoType.values()) {
            out.add(t.id());
        }
        out.add("kaboofcasino:chip_25");
        return out;
    }

    static ChipShopCatalog.Catalog catalog(ChipShopSettings s) {
        return ChipShopCatalog.build(ids(), recipes(), s, CashierSettings.DEFAULTS);
    }

    @Test
    void everythingSellableHasAPriceAndTheRestIsOut() throws IOException {
        ChipShopCatalog.Catalog c = catalog(ChipShopSettings.DEFAULTS);
        assertEquals(List.of(), c.unpriced(), "every sellable item has a recipe price");
        assertTrue(c.entry("roulettecasino:slot_machine").isPresent());
        assertTrue(c.entry("roulettecasino:duck_derby").isPresent());
        assertTrue(c.entry("roulettecasino:derby_duck_red").isPresent());
        assertTrue(c.entry("roulettecasino:casino_guide").isPresent());
        assertTrue(c.entry("casinosound:casino_speaker").isPresent());
        assertTrue(c.entry("roulettecasino:dealer_station").isPresent());
        for (String never : List.of("roulettecasino:blackjack_table", "roulettecasino:poker_table",
                "roulettecasino:croupier_badge", "roulettecasino:croupier_hand", "roulettecasino:cashier",
                "roulettecasino:croupier_hiring_card", "kaboofcasino:chip_25", "roulettecasino:leaderboard_wall")) {
            assertFalse(c.entry(never).isPresent(), never);
        }
        int deco = DecoBlock.values().length + DecoType.values().length;
        assertTrue(c.size() >= deco, "all deco plus the machines: " + c.size());
        // the tables with chipshop.tables
        ChipShopCatalog.Catalog withTables = catalog(new ChipShopSettings(false, 1.25, true,
                ChipShopSettings.DEFAULT_VALUES, List.of()));
        assertTrue(withTables.entry("roulettecasino:blackjack_table").isPresent());
        assertTrue(withTables.entry("roulettecasino:poker_table").isPresent());
        for (ChipShopCategory cat : ChipShopCategory.values()) {
            assertFalse(c.of(cat).isEmpty(), cat.name());
        }
        // the price list for Jack
        StringBuilder b = new StringBuilder("Chip-Shop price list (markup 1.25, ores at the cashier rates)\n");
        for (ChipShopCategory cat : ChipShopCategory.values()) {
            b.append("\n").append(cat.name()).append('\n');
            for (ChipShopCatalog.Entry e : c.of(cat)) {
                b.append(String.format(Locale.ROOT, "  %-55s %8d%n", e.id(), e.price()));
            }
        }
        Path out = Path.of("build/reports/chipshop-prices.txt");
        Files.createDirectories(out.getParent());
        Files.writeString(out, b.toString(), StandardCharsets.UTF_8);
    }

    @Test
    void overridesHideFixAndLimit() {
        ChipShopSettings s = new ChipShopSettings(false, 1.25, false, ChipShopSettings.DEFAULT_VALUES, List.of(
                new ChipShopSettings.Item("roulettecasino:slot_machine", 999, 2, false),
                new ChipShopSettings.Item("casinodeco:ruby_velvet", 0, 0, true)));
        ChipShopCatalog.Catalog c = catalog(s);
        ChipShopCatalog.Entry slot = c.entry("roulettecasino:slot_machine").orElseThrow();
        assertEquals(999, slot.price());
        assertEquals(2, slot.limit());
        assertTrue(slot.fixed());
        assertFalse(c.entry("casinodeco:ruby_velvet").isPresent());
    }

    @Test
    void roundingAndCosts() {
        assertEquals(1, ChipShopPrices.price(0.1, 1.25));
        assertEquals(13, ChipShopPrices.price(10, 1.25));
        assertEquals(25, ChipShopPrices.price(20, 1.25));
        assertEquals(125, ChipShopPrices.price(100, 1.25));
        assertEquals(130, ChipShopPrices.price(102, 1.25)); // 127.5 -> 130
        assertEquals(1250, ChipShopPrices.price(1000, 1.25));
        assertEquals(1275, ChipShopPrices.price(1018, 1.25)); // 1272.5 -> 1275
        // a recipe cycle (3 blocks -> 6 slabs, 2 slabs -> 1 block) never loops and keeps the block's own cost
        List<Recipes.Def> cycle = List.of(
                new Recipes.Def("a", true, List.of(), Map.of(), List.of("minecraft:paper", "minecraft:paper"), "x:a", 1,
                        List.of()),
                new Recipes.Def("s", true, List.of(), Map.of(), List.of("x:a", "x:a", "x:a"), "x:s", 6, List.of()),
                new Recipes.Def("b", true, List.of(), Map.of(), List.of("x:s", "x:s"), "x:a", 1, List.of()));
        Map<String, Double> costs = ChipShopPrices.costs(cycle, Map.of("minecraft:paper", 0.5));
        assertEquals(1.0, costs.get("x:a"), 1e-9);
        assertEquals(0.5, costs.get("x:s"), 1e-9);
        // categories
        assertEquals(ChipShopCategory.BAR, ChipShopCategory.of("casinodeco:black_marble_bar"));
        assertEquals(ChipShopCategory.BAR, ChipShopCategory.of("casinodeco:glass_coffee_table"));
        assertEquals(ChipShopCategory.FLOORS, ChipShopCategory.of("casinodeco:ruby_velvet_stairs"));
        assertEquals(ChipShopCategory.FLOORS, ChipShopCategory.of("casinodeco:dark_oak_pilaster"));
        assertEquals(ChipShopCategory.LIGHTS, ChipShopCategory.of("casinodeco:casino_chandelier"));
        assertEquals(ChipShopCategory.TECH, ChipShopCategory.of("roulettecasino:casino_guide"));
        assertEquals(ChipShopCategory.MACHINES, ChipShopCategory.of("roulettecasino:derby_duck_red"));
        assertEquals(ChipShopCategory.FURNITURE, ChipShopCategory.of("casinodeco:casino_sofa"));
    }
}
