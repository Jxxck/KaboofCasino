package de.tigges.kaboofcasino.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/** Slot-exact matching of casino recipes: same material, different casino items, mirroring, offsets, shapeless. */
class RecipeMatchTest {

    private static final String MUSH = "minecraft:red_mushroom_block";

    private static RecipeMatch.Slot casino(String id) {
        return new RecipeMatch.Slot(MUSH, id);
    }

    private static RecipeMatch.Slot vanilla(String material) {
        return new RecipeMatch.Slot(material, null);
    }

    private static Predicate<RecipeMatch.Slot> is(String casinoId) {
        return s -> casinoId.equals(s.casinoId());
    }

    private static Predicate<RecipeMatch.Slot> plain(String material) {
        return s -> s.casinoId() == null && material.equals(s.material());
    }

    @Test
    void twoCasinoItemsOfOneMaterialAreTold() {
        // ivory_black_checkers: IBI / BNB / IBI with two marbles that may share a mushroom material
        Map<Character, Predicate<RecipeMatch.Slot>> key = Map.of('I', is("casinodeco:ivory_marble"), 'B',
                is("casinodeco:obsidian_marble"), 'N', plain("minecraft:gold_nugget"));
        RecipeMatch.Slot i = casino("casinodeco:ivory_marble");
        RecipeMatch.Slot b = casino("casinodeco:obsidian_marble");
        RecipeMatch.Slot n = vanilla("minecraft:gold_nugget");
        List<String> pattern = List.of("IBI", "BNB", "IBI");
        assertTrue(RecipeMatch.shaped(pattern, key, new RecipeMatch.Slot[] {i, b, i, b, n, b, i, b, i}, 3));
        assertFalse(RecipeMatch.shaped(pattern, key, new RecipeMatch.Slot[] {b, i, b, i, n, i, b, i, b}, 3));
        // a plain mushroom block instead of a marble
        assertFalse(RecipeMatch.shaped(pattern, key, new RecipeMatch.Slot[] {i, b, i, b, n, b, i, b,
                vanilla(MUSH)}, 3));
        // a casino item where a vanilla ingredient belongs
        assertFalse(RecipeMatch.shaped(pattern, key, new RecipeMatch.Slot[] {i, b, i, b,
                new RecipeMatch.Slot("minecraft:gold_nugget", "casinodeco:x"), b, i, b, i}, 3));
    }

    @Test
    void stairsFitEveryOffsetAndTheMirror() {
        Map<Character, Predicate<RecipeMatch.Slot>> key = Map.of('B', is("casinodeco:ruby_velvet"));
        RecipeMatch.Slot v = casino("casinodeco:ruby_velvet");
        List<String> pattern = List.of("B  ", "BB ", "BBB");
        assertTrue(RecipeMatch.shaped(pattern, key, new RecipeMatch.Slot[] {v, null, null, v, v, null, v, v, v}, 3));
        assertTrue(RecipeMatch.shaped(pattern, key, new RecipeMatch.Slot[] {null, null, v, null, v, v, v, v, v}, 3),
                "mirrored");
        assertFalse(RecipeMatch.shaped(pattern, key, new RecipeMatch.Slot[] {v, null, null, v, v, null, v, v, null},
                3));
        // a 2-wide pattern at both offsets, and in the 2x2 inventory grid
        List<String> small = List.of("BB", "BB");
        assertTrue(RecipeMatch.shaped(small, key, new RecipeMatch.Slot[] {null, v, v, null, v, v, null, null, null},
                3));
        assertTrue(RecipeMatch.shaped(small, key, new RecipeMatch.Slot[] {v, v, v, v}, 2));
        assertFalse(RecipeMatch.shaped(small, key, new RecipeMatch.Slot[] {v, v, v, v, null, null, null, null, v},
                3), "extra stack outside the pattern");
    }

    @Test
    void patternsAreTrimmedLikeVanilla() {
        assertEquals(List.of("W", "N"), RecipeMatch.trim(List.of(" W ", " N ", "   ")));
        assertEquals(List.of("N N"), RecipeMatch.trim(List.of("   ", "N N")));
    }

    @Test
    void shapelessNeedsAOneToOneAssignment() {
        List<Predicate<RecipeMatch.Slot>> ingredients = List.of(plain("minecraft:glass_bottle"),
                plain("minecraft:paper"));
        assertTrue(RecipeMatch.shapeless(ingredients, new RecipeMatch.Slot[] {null, vanilla("minecraft:paper"),
                null, null, vanilla("minecraft:glass_bottle"), null, null, null, null}));
        assertFalse(RecipeMatch.shapeless(ingredients, new RecipeMatch.Slot[] {vanilla("minecraft:paper"),
                new RecipeMatch.Slot("minecraft:glass_bottle", "casinodeco:x")}), "casino item as a vanilla bottle");
        assertFalse(RecipeMatch.shapeless(ingredients, new RecipeMatch.Slot[] {vanilla("minecraft:paper"),
                vanilla("minecraft:glass_bottle"), vanilla("minecraft:paper")}));
    }
}
