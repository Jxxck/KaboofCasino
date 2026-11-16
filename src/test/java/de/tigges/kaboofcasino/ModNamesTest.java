package de.tigges.kaboofcasino;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.config.Language;
import de.tigges.kaboofcasino.deco.display.DecoType;
import de.tigges.kaboofcasino.deco.full.DecoBlock;
import de.tigges.kaboofcasino.outfit.Outfits;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The names the plugin gives its items when a client has no translation: the mod's own words in both languages
 * ({@code en_us.json}, {@code de_de.json}), and names of the same make for what the plugin adds.
 */
class ModNamesTest {

    private Language before;

    @BeforeEach
    void remember() {
        before = Language.current();
    }

    @AfterEach
    void restore() {
        Language.use(before);
    }

    @Test
    void decorationCarriesTheModsNamesInBothLanguages() {
        Map<String, String> en = TestModJar.lang("en_us");
        Map<String, String> de = TestModJar.lang("de_de");
        int own = 0;
        for (DecoType t : DecoType.values()) {
            if (en.containsKey(t.translationKey())) {
                assertEquals(en.get(t.translationKey()), t.english(), t.id());
                assertEquals(de.get(t.translationKey()), t.german(), t.id());
            } else {
                own++;
                assertTrue(t.own(), t.id() + " is neither the mod's nor marked as the plugin's own");
                assertFalse(t.english().isBlank() || t.german().isBlank(), t.id());
                if (t.id().endsWith("_stairs")) {
                    assertTrue(t.english().endsWith(" Stairs") && t.german().startsWith("Treppe"), t.id());
                } else if (t.id().endsWith("_slab")) {
                    assertTrue(t.english().endsWith(" Slab") && t.german().startsWith("Stufe"), t.id());
                }
            }
        }
        assertEquals(28, own);
        for (DecoBlock b : DecoBlock.values()) {
            assertEquals(en.get(b.translationKey()), b.englishName(), b.modId());
            assertEquals(de.get(b.translationKey()), b.germanName(), b.modId());
        }
        Language.use(Language.EN);
        assertEquals("Casino Speaker", DecoType.CASINO_SPEAKER.displayName());
        assertEquals("Gilded Dark Oak Paneling Stairs", DecoType.GILDED_DARK_OAK_STAIRS.displayName());
        assertEquals("Casino Sign 2×2", DecoType.CASINO_SIGN_2X2.displayName());
        assertEquals("Ruby Velvet Carpet", DecoBlock.RUBY_VELVET.displayName());
        Language.use(Language.DE);
        assertEquals("Casino-Lautsprecher", DecoType.CASINO_SPEAKER.displayName());
        assertEquals("Bordeauxroter Samtteppich", DecoBlock.RUBY_VELVET.displayName());
    }

    @Test
    void everyOutfitPieceHasItsOwnNameInBothLanguages() {
        Set<String> english = new HashSet<>();
        Set<String> german = new HashSet<>();
        for (Outfits.Style s : Outfits.Style.values()) {
            for (Outfits.Piece p : Outfits.Piece.values()) {
                assertTrue(english.add(s.englishName(p)), s.englishName(p) + " twice");
                assertTrue(german.add(s.germanName(p)), s.germanName(p) + " twice");
                assertFalse(s.englishName(p).matches(".*[äöüßÄÖÜ].*"), s.englishName(p));
            }
        }
        assertEquals(28, english.size());
        Language.use(Language.EN);
        assertEquals("Top Hat", Outfits.Style.SMOKING.name(Outfits.Piece.HAT));
        Language.use(Language.DE);
        assertEquals("Zylinder", Outfits.Style.SMOKING.name(Outfits.Piece.HAT));
    }
}
