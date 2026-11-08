package de.tigges.kaboofcasino.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.tigges.kaboofcasino.guide.GuideBook.Recipe;
import de.tigges.kaboofcasino.guide.GuideBook.Scene;
import de.tigges.kaboofcasino.guide.GuideBook.Tab;
import de.tigges.kaboofcasino.guide.GuideBook.Widget;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** The guide data the plugin ships is the mod's guide, complete, with the mod's German texts. */
class GuideBookTest {

    @Test
    void theBookIsTheModsGuide() {
        GuideBook book = GuideFixtures.book();
        JsonObject mod = GuideFixtures.modGuide();
        assertEquals(34, book.size());
        assertEquals(mod.getAsJsonArray("pages").size(), book.size());
        for (int i = 0; i < book.size(); i++) {
            JsonObject page = mod.getAsJsonArray("pages").get(i).getAsJsonObject();
            assertEquals(page.get("chapter").getAsString(), book.page(i + 1).chapter(), "spread " + (i + 1));
            assertEquals(page.getAsJsonArray("widgets").size(), book.page(i + 1).widgets().size(), "spread " + (i + 1));
            assertEquals(i + 1, book.page(i + 1).number());
        }
        assertEquals(List.of(new Tab("machines", 2, "roulettecasino:slot_machine"),
                new Tab("decor", 11, "casinodeco:casino_sofa"), new Tab("floors", 23, "casinodeco:ruby_stars"),
                new Tab("config", 33, "roulettecasino:croupier_hand")), book.tabs());
        assertEquals("guide.roulettecasino.decor", book.tabs().get(1).key());
    }

    @Test
    void widgetKindsMatchTheModsCounts() {
        Map<String, Integer> expected = new TreeMap<>(Map.ofEntries(Map.entry("Title", 89), Map.entry("Recipe", 89),
                Map.entry("Scene", 80), Map.entry("Tile", 73), Map.entry("Label", 24), Map.entry("Chips", 23),
                Map.entry("Item", 13), Map.entry("Text", 9), Map.entry("Rule", 9), Map.entry("Entity", 4),
                Map.entry("Preview", 3), Map.entry("Arrow", 3)));
        Map<String, Integer> counted = new TreeMap<>();
        for (GuideBook.Page page : GuideFixtures.book().pages()) {
            for (Widget w : page.widgets()) {
                counted.merge(w.getClass().getSimpleName(), 1, Integer::sum);
            }
        }
        assertEquals(expected, counted);
    }

    @Test
    void germanTextsAreExactlyTheModsDeDe() {
        GuideBook book = GuideFixtures.book();
        Map<String, String> mod = GuideFixtures.modGerman();
        assertEquals(134, book.germanKeys().size(), "the guide's keys, the screen's, the croupier and 92 names");
        for (String key : book.germanKeys()) {
            assertEquals(mod.get(key), book.german(key), key);
        }
        assertEquals("CASINO-HANDBUCH", book.german("guide.roulettecasino.text.006"));
        assertEquals("Vorherige Seite", book.german("guide.roulettecasino.previous"));
        assertEquals("Nächste Seite", book.german("guide.roulettecasino.next"));
        assertEquals("Inhalt", book.german("guide.roulettecasino.contents"));
        assertEquals("Fertig", book.german("guide.roulettecasino.close"));
        assertEquals("Formloses Rezept", book.german("guide.roulettecasino.expansion.shapeless"));
        assertEquals("„Kopieren“ im Fenster, dann Schleichen + Rechtsklick auf die andere Maschine.",
                book.german("guide.roulettecasino.text.264"));
    }

    @Test
    void everyKeyOfEveryWidgetAndEveryCasinoNameHasGermanText() {
        GuideBook book = GuideFixtures.book();
        int keys = 0;
        for (JsonElement p : GuideFixtures.modGuide().getAsJsonArray("pages")) {
            for (JsonElement w : p.getAsJsonObject().getAsJsonArray("widgets")) {
                JsonObject widget = w.getAsJsonObject();
                if (widget.has("key")) {
                    keys++;
                    assertNotNull(book.german(widget.get("key").getAsString()), widget.toString());
                }
            }
        }
        assertEquals(89 + 24 + 9 + 13, keys, "titles, labels, texts and labelled tiles");
        for (Tab tab : book.tabs()) {
            assertNotNull(book.german(tab.key()), tab.key());
        }
        for (String id : casinoIds(book)) {
            String key = book.nameKey(id);
            assertNotNull(key, id);
            assertNotNull(book.german(key), key);
            String path = id.substring(id.indexOf(':') + 1);
            String ns = id.substring(0, id.indexOf(':'));
            assertTrue(key.equals("block." + ns + "." + path) || key.equals("item." + ns + "." + path), key);
        }
        assertEquals("Croupier-Theke", book.german(book.nameKey("roulettecasino:dealer_station")));
        assertEquals("Kugellagermechanismus", book.german(book.nameKey("roulettecasino:roulette_mechanism")));
    }

    /** Every casino id the widgets name (refs, results, cells, chips, scene items). */
    static List<String> casinoIds(GuideBook book) {
        List<String> ids = new ArrayList<>();
        for (GuideBook.Page page : book.pages()) {
            for (Widget w : page.widgets()) {
                switch (w) {
                    case GuideBook.Tile t -> ids.add(t.ref());
                    case GuideBook.Item i -> ids.add(i.ref());
                    case Recipe r -> {
                        ids.add(r.result());
                        r.cells().stream().filter(c -> c != null && !c.startsWith("#")).forEach(ids::add);
                    }
                    case Scene s -> ids.addAll(s.items());
                    case GuideBook.Chips c -> ids.addAll(c.refs());
                    default -> {
                    }
                }
            }
        }
        return ids.stream().filter(id -> !id.startsWith("minecraft:")).distinct().sorted().toList();
    }

    @Test
    void scenesBecomeTheirMainItems() {
        GuideBook book = GuideFixtures.book();
        int scenes = 0;
        for (GuideBook.Page page : book.pages()) {
            for (Widget w : page.widgets()) {
                if (w instanceof Scene s) {
                    scenes++;
                    assertTrue(s.hiring() != !s.items().isEmpty(), "spread " + page.number() + ": " + s);
                    s.items().forEach(id -> assertFalse(id.startsWith("minecraft:"), page.number() + " " + id));
                }
            }
        }
        assertEquals(80, scenes);
        assertEquals(List.of("roulettecasino:roulette_table", "roulettecasino:slot_machine"), scene(1, 0).items());
        assertTrue(scene(3, 0).hiring());
        assertEquals(List.of("roulettecasino:blackjack_table"), scene(5, 0).items());
        assertEquals(List.of("casinodeco:casino_sofa"), scene(13, 0).items());
        assertEquals(List.of("roulettecasino:casino_tv_3x3"), scene(13, 3).items());
        assertEquals(List.of("casinodeco:cash_register"), scene(18, 2).items());
        // a wall piece in front of planks or paneling: the piece, not the wall behind it
        assertEquals(List.of("casinodeco:classic_gilded_dark_oak_paneling"), scene(29, 1).items());
        assertEquals(List.of("casinodeco:dark_oak_baseboard"), scene(30, 1).items());
        assertEquals(List.of("casinodeco:emerald_velvet_slab"), scene(24, 2).items());
    }

    private static Scene scene(int page, int n) {
        return GuideFixtures.book().page(page).widgets().stream().filter(Scene.class::isInstance)
                .map(Scene.class::cast).toList().get(n);
    }

    @Test
    void indexTagsAndPages() {
        GuideBook book = GuideFixtures.book();
        assertEquals(4, book.indexPage("roulettecasino:roulette_mechanism").getAsInt());
        assertEquals(13, book.indexPage("roulettecasino:casino_tv_large").getAsInt());
        assertTrue(book.indexPage("minecraft:emerald").isEmpty());
        for (JsonElement p : GuideFixtures.modGuide().getAsJsonArray("pages")) {
            for (JsonElement w : p.getAsJsonObject().getAsJsonArray("widgets")) {
                JsonObject widget = w.getAsJsonObject();
                if (widget.has("cells")) {
                    for (JsonElement c : widget.getAsJsonArray("cells")) {
                        if (!c.isJsonNull()) {
                            assertFalse(GuideBook.shown(c.getAsString()).startsWith("#"), c.getAsString());
                        }
                    }
                }
            }
        }
        assertEquals("minecraft:oak_planks", GuideBook.shown("#minecraft:planks"));
        assertEquals("minecraft:emerald", GuideBook.shown("minecraft:emerald"));
        assertThrows(IllegalArgumentException.class, () -> GuideBook.shown("#minecraft:logs"));
        assertThrows(IllegalArgumentException.class, () -> book.page(0));
        assertThrows(IllegalArgumentException.class, () -> book.page(35));
        assertTrue(book.valid(1) && book.valid(34) && !book.valid(0) && !book.valid(35));
    }
}
