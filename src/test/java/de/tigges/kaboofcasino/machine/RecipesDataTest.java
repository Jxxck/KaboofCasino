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
        assertEquals(Set6.IDS, d.keySet());
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
    }

    @Test
    void unlocksFollowTheModsAdvancementChain() {
        Map<String, Recipes.Def> d = defs();
        assertEquals("minecraft:diamond", d.get("slot_machine_reel").unlock());
        assertEquals("roulettecasino:slot_machine_reel", d.get("slot_machine_mechanism").unlock());
        assertEquals("roulettecasino:slot_machine_mechanism", d.get("slot_machine").unlock());
        assertEquals("minecraft:gold_ingot", d.get("roulette_mechanism").unlock());
        assertEquals("roulettecasino:roulette_mechanism", d.get("fortune_wheel").unlock());
        assertEquals("minecraft:dropper", d.get("plinko").unlock());
        assertEquals("roulettecasino:roulette_mechanism", d.get("roulette_table").unlock());
        assertEquals(List.of("RVN", "GMG", "PSP"), d.get("roulette_table").pattern());
    }

    @Test
    void paperRulesAndCasinoItemsResolve() {
        Map<String, Recipes.Def> d = defs();
        assertEquals(Optional.of(new Recipes.PaperRule(null)), d.get("slot_machine_reel").paperRule());
        assertEquals(Optional.of(new Recipes.PaperRule(CasinoItem.SLOT_MACHINE_REEL)),
                d.get("slot_machine_mechanism").paperRule());
        assertEquals(Optional.of(new Recipes.PaperRule(CasinoItem.ROULETTE_MECHANISM)),
                d.get("fortune_wheel").paperRule());
        assertEquals(Optional.empty(), d.get("plinko").paperRule());
        assertEquals(Optional.empty(), d.get("roulette_mechanism").paperRule());
        for (Recipes.Def def : d.values()) {
            if (def.result().startsWith("roulettecasino:")) {
                assertTrue(CasinoItem.byId(def.result().substring(15)).isPresent(), def.result());
            }
            for (String ingredient : def.key().values()) {
                if (ingredient.startsWith("roulettecasino:")) {
                    assertTrue(CasinoItem.byId(ingredient.substring(15)).isPresent(), ingredient);
                }
            }
        }
        assertEquals(MachineType.FORTUNE_WHEEL, CasinoItem.FORTUNE_WHEEL.machine().orElseThrow());
        assertEquals(MachineType.PLINKO, CasinoItem.PLINKO.machine().orElseThrow());
    }

    /** The shipped set. */
    private static final class Set6 {
        static final java.util.Set<String> IDS = java.util.Set.of("slot_machine_reel", "slot_machine_mechanism",
                "slot_machine", "roulette_mechanism", "fortune_wheel", "plinko", "roulette_table");
    }
}
