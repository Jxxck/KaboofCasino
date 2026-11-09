package de.tigges.kaboofcasino.outfit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.chipshop.ChipShopCategory;
import de.tigges.kaboofcasino.machine.Recipes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The outfits' ids and recipes: unique, sold in their own tab, from vanilla ingredients. */
class OutfitsTest {

    @Test
    void everyPieceIsInTheOutfitTab() {
        assertEquals(Outfits.Style.values().length * Outfits.Piece.values().length, Outfits.ids().size());
        for (String id : Outfits.ids()) {
            assertEquals(ChipShopCategory.OUTFITS, ChipShopCategory.of(id), id);
        }
    }

    @Test
    void noTwoOutfitsShareARecipe() {
        Set<String> seen = new HashSet<>();
        for (Outfits.Style s : Outfits.Style.values()) {
            assertTrue(seen.add(s.wool() + "+" + s.accent()), s.name());
        }
    }

    @Test
    void theRecipesNeedOnlyVanillaIngredients() {
        List<Recipes.Def> recipes = new ArrayList<>(Outfits.recipes());
        assertEquals(Outfits.ids().size(), recipes.size());
        for (Recipes.Def d : recipes) {
            for (String ingredient : d.allIngredients()) {
                assertFalse(Recipes.casinoId(ingredient), d.id() + ": " + ingredient);
            }
            assertTrue(Recipes.casinoId(d.result()), d.result());
        }
    }
}
