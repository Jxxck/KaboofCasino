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
        List<Recipes.Def> all = new ArrayList<>(Recipes.parse(ChipShopCatalogTest.class.getClassLoader().getResourceAsStream("pack/recipes.json")));
        all.addAll(de.tigges.kaboofcasino.outfit.Outfits.recipes());
        return all;
    }

    /** The ids the item catalog holds at runtime (machines first, then the registered deco and chips). */
    static List<String> ids() {
        List<String> out = new ArrayList<>();
        out.addAll(de.tigges.kaboofcasino.outfit.Outfits.ids());
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
        ChipShopCatalog.Catalog withTables = catalog(new ChipShopSettings(false, ChipShopSettings.DEFAULT_CATEGORIES,
                true, ChipShopSettings.DEFAULT_VALUES, List.of()));
        assertTrue(withTables.entry("roulettecasino:blackjack_table").isPresent());
        assertTrue(withTables.entry("roulettecasino:poker_table").isPresent());
        for (ChipShopCategory cat : ChipShopCategory.values()) {
            assertFalse(c.of(cat).isEmpty(), cat.name());
        }
        // the price list for Jack
        StringBuilder b = new StringBuilder("Chip-Shop price list (per piece, stack of 64; ores at the cashier rates)\n");
        for (ChipShopCategory cat : ChipShopCategory.values()) {
            b.append("\n").append(cat.name()).append('\n');
            for (ChipShopCatalog.Entry e : c.of(cat)) {
                b.append(String.format(Locale.ROOT, "  %-55s %8d %9d%n", e.id(), e.total(1), e.total(64)));
            }
        }
        Path out = Path.of("build/reports/chipshop-prices.txt");
        Files.createDirectories(out.getParent());
        Files.writeString(out, b.toString(), StandardCharsets.UTF_8);
    }

    @Test
    void jacksCategoryPrices() {
        ChipShopCatalog.Catalog c = catalog(ChipShopSettings.DEFAULTS);
        // floors and walls: at least 5 a block, 4 a stair, 3 a slab
        assertTrue(c.entry("casinodeco:ruby_velvet").orElseThrow().total(1) >= 5);
        assertEquals(4, c.entry("casinodeco:ivory_marble_stairs").orElseThrow().total(1));
        assertEquals(3, c.entry("casinodeco:ivory_marble_slab").orElseThrow().total(1));
        assertEquals(64 * 3, c.entry("casinodeco:ivory_marble_slab").orElseThrow().total(64));
        // furniture to technology: markup 3.75, at least 5
        for (ChipShopCategory cat : List.of(ChipShopCategory.FURNITURE, ChipShopCategory.BAR, ChipShopCategory.LIGHTS,
                ChipShopCategory.TECH)) {
            for (ChipShopCatalog.Entry e : c.of(cat)) {
                assertTrue(e.total(1) >= 5, e.id());
                assertTrue(e.total(1) >= e.perPiece() - 1e-9, "never below the value: " + e.id());
            }
        }
        // machines unchanged at 1.25, the guide first, the dealer station with them
        assertEquals("roulettecasino:casino_guide", c.of(ChipShopCategory.MACHINES).getFirst().id());
        assertTrue(c.entry("roulettecasino:dealer_station").orElseThrow().category() == ChipShopCategory.MACHINES);
        assertEquals(4, c.of(ChipShopCategory.TECH).size(), "speaker and three TVs");
        for (List<ChipShopCatalog.Entry> l : List.of(c.of(ChipShopCategory.FLOORS), c.of(ChipShopCategory.MACHINES))) {
            for (ChipShopCatalog.Entry e : l) {
                assertTrue(e.total(64) >= e.perPiece() * 64 - 1e-6, e.id());
                assertTrue(e.total(64) <= e.total(1) * 64, "a stack never costs more than 64 single pieces");
            }
        }
    }

    @Test
    void overridesHideFixAndLimit() {
        ChipShopSettings s = new ChipShopSettings(false, ChipShopSettings.DEFAULT_CATEGORIES, false,
                ChipShopSettings.DEFAULT_VALUES, List.of(
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
        // rounded UP to the 1/5/25 grid: nothing sells below its ingredients
        assertEquals(1, ChipShopPrices.roundUp(0.1));
        assertEquals(13, ChipShopPrices.roundUp(12.5));
        assertEquals(25, ChipShopPrices.roundUp(25));
        assertEquals(30, ChipShopPrices.roundUp(25.1));
        assertEquals(130, ChipShopPrices.roundUp(127.5));
        assertEquals(275, ChipShopPrices.roundUp(250.01));
        assertEquals(1275, ChipShopPrices.roundUp(1272.5));
        assertEquals(13, ChipShopPrices.price(10, 1.25, 0));
        assertEquals(5, ChipShopPrices.price(0.2, 1.25, 5), "the tab minimum per piece");
        // a stack as a whole: never below min x pieces, else the whole value rounded up once
        assertEquals(320, ChipShopPrices.total(0.3, 5, 64));
        assertEquals(135, ChipShopPrices.total(2.1, 0, 64), "64 x 2.1 = 134.4 -> 135, not 64 x 3");
        ChipShopCatalog.Entry fixed = new ChipShopCatalog.Entry("x:y", ChipShopCategory.BAR, 7, 0, true, 0, 0);
        assertEquals(448, fixed.total(64), "a fixed price is per piece");
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
        assertEquals(ChipShopCategory.MACHINES, ChipShopCategory.of("roulettecasino:casino_guide"));
        assertEquals(ChipShopCategory.MACHINES, ChipShopCategory.of("roulettecasino:dealer_station"));
        assertEquals(ChipShopCategory.TECH, ChipShopCategory.of("roulettecasino:casino_tv"));
        assertEquals(ChipShopCategory.MACHINES, ChipShopCategory.of("roulettecasino:derby_duck_red"));
        assertEquals(ChipShopCategory.FURNITURE, ChipShopCategory.of("casinodeco:casino_sofa"));
    }
}
