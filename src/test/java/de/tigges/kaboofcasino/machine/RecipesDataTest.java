package de.tigges.kaboofcasino.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                    assertTrue(CasinoItem.byId(id.substring(15)).isPresent()
                            || id.equals("roulettecasino:casino_chair") || id.equals("roulettecasino:casino_stool"),
                            id);
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

    /** The shipped set. */
    private static final class Set6 {
        static final java.util.Set<String> MACHINES = java.util.Set.of("slot_machine_reel", "slot_machine_mechanism",
                "slot_machine", "roulette_mechanism", "fortune_wheel", "plinko", "roulette_table", "blackjack_table",
                "casino_chair", "casino_stool", "poker_table");
    }
}
