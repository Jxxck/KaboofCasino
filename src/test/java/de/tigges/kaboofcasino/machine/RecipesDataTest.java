package de.tigges.kaboofcasino.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.deco.display.DecoType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** The recipes the pack tool extracted from the mod's JSON (pack/recipes.json) and how the plugin reads them. */
class RecipesDataTest {

    private static Map<String, Recipes.Def> defs() {
        return Recipes.parse(RecipesDataTest.class.getClassLoader().getResourceAsStream("pack/recipes.json"))
                .stream().collect(Collectors.toMap(Recipes.Def::id, d -> d));
    }

    @Test
    void theShippedRecipesAreTheMods() {
        Map<String, Recipes.Def> d = defs();
        assertEquals(Set6.MACHINES, d.keySet().stream().filter(k -> !k.contains("/"))
                .collect(Collectors.toSet()));
        Recipes.Def wheel = d.get("fortune_wheel");
        assertEquals(List.of("RVW", "GMG", "PSP"), wheel.pattern());
        assertEquals("roulettecasino:roulette_mechanism", wheel.key().get('M'));
        assertEquals("#minecraft:planks", wheel.key().get('P'));
        assertEquals("roulettecasino:fortune_wheel", wheel.result());
        Recipes.Def plinko = d.get("plinko");
        assertEquals(List.of("RDV", "GHG", "PSP"), plinko.pattern());
        assertEquals("minecraft:dropper", plinko.key().get('H'));
        Recipes.Def mechanism = d.get("roulette_mechanism");
        assertEquals(List.of(" G ", "GDG", " R "), mechanism.pattern());
        assertEquals("roulettecasino:roulette_mechanism", mechanism.result());
        Recipes.Def blackjack = d.get("blackjack_table");
        assertEquals(List.of("WWW", "GBG", "PRP"), blackjack.pattern());
        assertEquals("minecraft:green_wool", blackjack.key().get('W'));
        assertEquals("minecraft:gold_block", blackjack.key().get('G'));
        assertEquals("roulettecasino:croupier_badge", blackjack.key().get('B'));
        assertEquals("#minecraft:planks", blackjack.key().get('P'));
        assertEquals("minecraft:redstone", blackjack.key().get('R'));
        assertEquals("roulettecasino:blackjack_table", blackjack.result());
        Recipes.Def derby = d.get("duck_derby");
        assertEquals(List.of("WWW", "GMG", "PRP"), derby.pattern());
        assertEquals("roulettecasino:duck_derby_mechanism", derby.key().get('M'));
        // the 23 other duck orders of the mechanism have no recipe-book unlock in the mod
        assertEquals(List.of(), d.get("duck_derby_mechanism_order_07").unlocks());
        // the mechanism unlocks with any racing duck (the mod's item tag #roulettecasino:derby_ducks, expanded)
        assertEquals(List.of("roulettecasino:derby_duck_red", "roulettecasino:derby_duck_yellow",
                "roulettecasino:derby_duck_black", "roulettecasino:derby_duck_white"),
                d.get("duck_derby_mechanism").unlocks());
        assertEquals(List.of("roulettecasino:duck_derby_mechanism"), d.get("duck_derby").unlocks());
        // step 8: the badge unlocks both card tables; the guide, counter and TVs unlock like the mod
        assertEquals(List.of("roulettecasino:croupier_badge"), d.get("blackjack_table").unlocks());
        assertEquals(List.of("roulettecasino:croupier_badge"), d.get("poker_table").unlocks());
        assertEquals(List.of("minecraft:leather"), d.get("casino_guide").unlocks());
        assertEquals(List.of("minecraft:green_wool"), d.get("dealer_station").unlocks());
        assertEquals(List.of("minecraft:tinted_glass"), d.get("casino_tv").unlocks());
        assertEquals(List.of("minecraft:tinted_glass"), d.get("casino_tv_large").unlocks());
        assertEquals(List.of("roulettecasino:casino_tv_large"), d.get("casino_tv_3x3").unlocks());
        assertEquals("roulettecasino:duck_derby_mechanism", d.get("duck_derby_mechanism_order_07").result());
    }

    @Test
    void unlocksFollowTheModsAdvancementChain() {
        Map<String, Recipes.Def> d = defs();
        assertEquals(List.of("minecraft:diamond"), d.get("slot_machine_reel").unlocks());
        assertEquals(List.of("roulettecasino:slot_machine_reel"), d.get("slot_machine_mechanism").unlocks());
        assertEquals(List.of("roulettecasino:slot_machine_mechanism"), d.get("slot_machine").unlocks());
        assertEquals(List.of("minecraft:gold_ingot"), d.get("roulette_mechanism").unlocks());
        assertEquals(List.of("roulettecasino:roulette_mechanism"), d.get("fortune_wheel").unlocks());
        assertEquals(List.of("minecraft:dropper"), d.get("plinko").unlocks());
        assertEquals(List.of("roulettecasino:roulette_mechanism"), d.get("roulette_table").unlocks());
        // the croupier's badge (the mod hires a croupier for it; here /casino give until the croupier exists)
        assertEquals(List.of("roulettecasino:croupier_badge"), d.get("blackjack_table").unlocks());
        assertEquals(List.of("RVN", "GMG", "PSP"), d.get("roulette_table").pattern());
    }

    @Test
    void casinoIdsResolveToMachineItemsOrTheModsDecoNamespaces() {
        Map<String, Recipes.Def> d = defs();
        for (Recipes.Def def : d.values()) {
            for (String id : java.util.stream.Stream.concat(java.util.stream.Stream.of(def.result()),
                    def.allIngredients().stream()).toList()) {
                if (id.startsWith("roulettecasino:")) {
                    // machine items, or display deco (chair, stool, the TVs, the dealer station)
                    assertTrue(CasinoItem.byId(id.substring(15)).isPresent() || DecoType.byId(id).isPresent(), id);
                } else if (!id.startsWith("minecraft:") && !id.startsWith("#")) {
                    assertTrue(id.startsWith("casinodeco:") || id.startsWith("casinosound:"), id);
                }
            }
        }
        assertEquals(MachineType.FORTUNE_WHEEL, CasinoItem.FORTUNE_WHEEL.machine().orElseThrow());
        assertEquals(MachineType.PLINKO, CasinoItem.PLINKO.machine().orElseThrow());
    }

    @Test
    void decoSeatAndSpeakerRecipesAreAllTheMods() {
        Map<String, Recipes.Def> d = defs();
        assertEquals(65, d.keySet().stream().filter(k -> k.startsWith("casinodeco/")).count());
        assertTrue(d.containsKey("casinosound/casino_speaker"));
        assertTrue(d.containsKey("casino_chair") && d.containsKey("casino_stool"));
        Recipes.Def checkers = d.get("casinodeco/ivory_black_checkers");
        assertEquals(List.of("IBI", "BNB", "IBI"), checkers.pattern());
        assertEquals("casinodeco:ivory_marble", checkers.key().get('I'));
        assertEquals(List.of("casinodeco:obsidian_marble"), checkers.unlocks());
        Recipes.Def bottles = d.get("casinodeco/casino_bottles");
        assertTrue(bottles.shapeless());
        assertEquals(List.of("minecraft:glass_bottle", "minecraft:paper"), bottles.ingredients());
        // stairs and slabs unlock with their velvet/marble (criterion named has_polished_andesite in the mod)
        assertEquals(List.of("casinodeco:ruby_velvet"), d.get("casinodeco/ruby_velvet_stairs").unlocks());
        assertEquals("casinodeco:casino_sofa", d.get("casinodeco/casino_sofa").result());
        assertEquals(List.of("casinodeco:champagne_coupes", "casinodeco:shot_glasses"),
                d.get("casinodeco/serving_tray").unlocks());
    }

    /** The TVs and the dealer station (step 8): the mod's recipes, results are display deco. */
    @Test
    void televisionAndDealerStationRecipes() {
        Map<String, Recipes.Def> d = defs();
        Recipes.Def tv = d.get("casino_tv");
        assertEquals(List.of("nnn", "nTn", "nRn"), tv.pattern());
        assertEquals("minecraft:tinted_glass", tv.key().get('T'));
        assertEquals(List.of("III", "TRT", "III"), d.get("casino_tv_large").pattern());
        Recipes.Def xl = d.get("casino_tv_3x3");
        assertTrue(xl.shapeless());
        assertEquals(List.of("roulettecasino:casino_tv_large", "minecraft:redstone_block"), xl.ingredients());
        Recipes.Def station = d.get("dealer_station");
        assertEquals(List.of("FFF", "GRG", "PPP"), station.pattern());
        assertEquals("#minecraft:planks", station.key().get('P'));
        for (String id : List.of("casino_tv", "casino_tv_large", "casino_tv_3x3", "dealer_station")) {
            assertEquals(DecoType.byId("roulettecasino:" + id).orElseThrow().id(), d.get(id).result());
        }
    }

    /** The shipped set. */
    private static final class Set6 {
        static final String[] ORDER_IDS = java.util.stream.IntStream.rangeClosed(1, 23)
                .mapToObj(i -> String.format(java.util.Locale.ROOT, "duck_derby_mechanism_order_%02d", i))
                .toArray(String[]::new);
        static final java.util.Set<String> MACHINES = java.util.stream.Stream.concat(java.util.stream.Stream.of(
                "slot_machine_reel", "slot_machine_mechanism", "slot_machine", "roulette_mechanism", "fortune_wheel",
                "plinko", "roulette_table", "blackjack_table", "casino_chair", "casino_stool", "poker_table",
                "derby_duck_yellow", "derby_duck_red", "derby_duck_white", "derby_duck_black", "duck_derby_mechanism",
                "duck_derby", "casino_guide", "dealer_station", "casino_tv", "casino_tv_large", "casino_tv_3x3"),
                java.util.Arrays.stream(ORDER_IDS)).collect(java.util.stream.Collectors.toSet());
    }
}
