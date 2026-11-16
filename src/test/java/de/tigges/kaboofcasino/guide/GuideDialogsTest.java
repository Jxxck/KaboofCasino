package de.tigges.kaboofcasino.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.deco.display.DecoType;
import de.tigges.kaboofcasino.deco.full.DecoBlock;
import de.tigges.kaboofcasino.guide.GuideView.Body;
import de.tigges.kaboofcasino.guide.GuideView.Button;
import de.tigges.kaboofcasino.guide.GuideView.ItemBody;
import de.tigges.kaboofcasino.guide.GuideView.TextBody;
import de.tigges.kaboofcasino.machine.CasinoItem;
import de.tigges.kaboofcasino.pack.PackFiles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Every spread converts into a dialog with the mod's texts, resolvable items and valid navigation. */
class GuideDialogsTest {

    private static final Pattern ENTRY = Pattern.compile("(\\w+):\"([^\"]*)\"");

    private static List<GuideView> all() {
        List<GuideView> out = new ArrayList<>();
        for (int page = 1; page <= GuideFixtures.book().size(); page++) {
            out.add(GuideFixtures.view(page, false));
            out.add(GuideFixtures.view(page, true));
        }
        return out;
    }

    /** Every component of a view: title, body texts, descriptions, button labels and tooltips. */
    private static List<Component> components(GuideView v) {
        List<Component> out = new ArrayList<>();
        out.add(v.title());
        for (Body b : v.body()) {
            switch (b) {
                case TextBody t -> out.add(t.text());
                case ItemBody i -> {
                    if (i.description() != null) {
                        out.add(i.description());
                    }
                }
            }
        }
        for (Button b : v.buttons()) {
            out.add(b.label());
            if (b.tooltip() != null) {
                out.add(b.tooltip());
            }
        }
        out.add(v.exit().label());
        return out;
    }

    private static GuideAction action(String snbt) {
        Map<String, String> values = new HashMap<>();
        Matcher m = ENTRY.matcher(snbt);
        while (m.find()) {
            values.put(m.group(1), m.group(2));
        }
        Optional<GuideAction> a = GuideAction.parse(values::get, 34);
        assertTrue(a.isPresent(), snbt);
        return a.get();
    }

    @Test
    void everySpreadConverts() {
        int views = 0;
        for (GuideView v : all()) {
            views++;
            assertTrue(v.body().size() >= 3, "spread " + v.page());
            assertTrue(v.body().getFirst() instanceof TextBody nav && nav.width() == GuideDialogs.NAV_WIDTH);
            assertTrue(GuideFixtures.plain(((TextBody) v.body().getFirst()).text()).contains(v.page() + " / 34"));
            assertEquals(4, v.buttons().stream().filter(b -> b.action().kind() == GuideAction.Kind.TAB).count());
            assertEquals(GuideAction.close(), v.exit().action());
            assertEquals("{guide.roulettecasino.close}", GuideFixtures.plain(v.exit().label()));
            assertEquals("{item.roulettecasino.casino_guide}", GuideFixtures.plain(v.title()));
            for (Body b : v.body()) {
                if (b instanceof ItemBody i) {
                    assertFalse(i.item().startsWith("#"));
                    assertTrue(i.count() >= 1 && i.count() <= 64);
                    assertTrue(i.width() >= 1 && i.width() <= 256 && i.height() >= 1 && i.height() <= 256);
                } else if (b instanceof TextBody t) {
                    assertTrue(t.width() >= 1 && t.width() <= 1024);
                }
            }
            for (Button b : v.buttons()) {
                assertTrue(b.width() >= 1 && b.width() <= 1024);
            }
        }
        assertEquals(68, views);
        assertTrue(GuideFixtures.plain(((TextBody) GuideFixtures.view(1, false).body().getLast()).text())
                .contains("CREDITS"), "the licence line is on the cover");
    }

    @Tag("german")
    @Test
    void everyLangKeyExistsInDeDeWithItsGermanText() {
        Map<String, String> german = GuideFixtures.modGerman();
        Set<String> used = new TreeSet<>();
        for (GuideView v : all()) {
            for (Component c : components(v)) {
                for (TranslatableComponent t : GuideFixtures.translatables(c)) {
                    if (t.key().startsWith("minecraft:") || t.key().equals(GuideDialogs.VILLAGER_KEY)) {
                        continue; // vanilla names: the client has them (the fixture's stand-in key is the id)
                    }
                    used.add(t.key());
                    assertTrue(german.containsKey(t.key()), "not in de_de: " + t.key());
                    assertEquals(german.get(t.key()), t.fallback(), t.key());
                }
            }
        }
        // every German text the plugin ships is used by some dialog
        assertEquals(GuideFixtures.book().germanKeys(), used);
        assertTrue(used.containsAll(List.of("guide.roulettecasino.previous", "guide.roulettecasino.next",
                "guide.roulettecasino.contents", "guide.roulettecasino.close", "guide.roulettecasino.machines",
                "guide.roulettecasino.expansion.shapeless", "guide.roulettecasino.text.265",
                "entity.roulettecasino.villager.croupier", "block.roulettecasino.dealer_station")), used.toString());
    }

    @Test
    void everyItemIdResolves() {
        Set<String> casino = new TreeSet<>();
        for (GuideView v : all()) {
            for (Body b : v.body()) {
                if (b instanceof ItemBody i) {
                    if (i.item().startsWith("minecraft:")) {
                        // the registry (ItemType) needs a server; the Material enum names every vanilla item
                        assertNotNull(Material.matchMaterial(i.item()), i.item());
                    } else {
                        casino.add(i.item());
                    }
                }
            }
        }
        // the names in the recipe legends too
        for (String id : GuideBookTest.casinoIds(GuideFixtures.book())) {
            casino.add(id);
        }
        Set<String> ofThePlugin = new TreeSet<>();
        Set<String> displayOnly = new TreeSet<>();
        for (String id : casino) {
            String path = id.substring(id.indexOf(':') + 1);
            boolean known = id.startsWith("roulettecasino:") && CasinoItem.byId(path).isPresent()
                    || DecoType.byId(id).isPresent()
                    || Arrays.stream(DecoBlock.values()).anyMatch(d -> d.modId().equals(id));
            (known ? ofThePlugin : displayOnly).add(id);
            assertNotNull(GuideFixtures.book().nameKey(id), id);
        }
        System.out.println("guide items: " + casino.size() + " casino ids, " + ofThePlugin.size()
                + " made by the plugin's item code, shown as the mod's item model only: " + displayOnly);
        // the rest is shown as paper with the mod's item model: it must be in the pack once its step ships it
        Set<String> missingModel = new TreeSet<>();
        for (String id : casino) {
            if (!PackFiles.hasItemModel(id)) {
                missingModel.add(id);
            }
        }
        System.out.println("guide items without an item definition in the pack: " + missingModel);
        assertTrue(Set.of("roulettecasino:casino_tv", "roulettecasino:casino_tv_large",
                "roulettecasino:casino_tv_3x3", "roulettecasino:dealer_station", "roulettecasino:croupier_hand")
                .containsAll(displayOnly), "unexpected display-only ids " + displayOnly);
        // the TVs and the croupier counter come with their own step (their item definitions with them)
        assertTrue(Set.of("roulettecasino:casino_tv", "roulettecasino:casino_tv_large",
                "roulettecasino:casino_tv_3x3", "roulettecasino:dealer_station").containsAll(missingModel),
                "items without a pack model " + missingModel);
        assertTrue(PackFiles.hasItemModel("roulettecasino:croupier_hand"));
        assertTrue(PackFiles.hasItemModel("roulettecasino:casino_guide"));
    }

    @Test
    void navigationTargetsAreInRange() {
        GuideBook book = GuideFixtures.book();
        int links = 0;
        for (GuideView v : all()) {
            boolean history = v.buttons().stream().anyMatch(b -> b.action().kind() == GuideAction.Kind.BACK);
            for (Button b : v.buttons()) {
                GuideAction a = b.action();
                assertEquals(a, action(a.snbt()));
                switch (a.kind()) {
                    case TURN -> assertTrue(a.page() == v.page() - 1 || a.page() == v.page() + 1,
                            a + " on " + v.page());
                    case TAB -> assertTrue(Set.of(2, 11, 23, 33).contains(a.page()), a.toString());
                    case LINK, CLOSE -> throw new AssertionError("no " + a.kind() + " button");
                    case BACK -> assertTrue(history);
                }
            }
            assertEquals(v.page() > 1, v.buttons().stream().anyMatch(b -> b.action().kind() == GuideAction.Kind.TURN
                    && b.action().page() == v.page() - 1));
            assertEquals(v.page() < 34, v.buttons().stream().anyMatch(b -> b.action().kind() == GuideAction.Kind.TURN
                    && b.action().page() == v.page() + 1));
            for (Component c : components(v)) {
                for (String snbt : GuideFixtures.clicks(c)) {
                    GuideAction a = action(snbt);
                    assertTrue(book.valid(a.page()), snbt);
                    assertTrue(a.kind() == GuideAction.Kind.LINK || a.kind() == GuideAction.Kind.TURN, snbt);
                    links++;
                }
            }
        }
        assertTrue(links > 300, "links " + links);
        // the tiles keep their targets
        List<Integer> tiles = new ArrayList<>();
        for (Body b : GuideFixtures.view(2, false).body()) {
            if (b instanceof ItemBody i && i.description() != null) {
                tiles.add(action(GuideFixtures.clicks(i.description()).getFirst()).page());
            }
        }
        assertEquals(List.of(4, 5, 6, 7, 8, 9, 10), tiles);
    }

    @Test
    void historyShowsInhalt() {
        assertFalse(GuideFixtures.view(5, false).buttons().stream()
                .anyMatch(b -> b.action().kind() == GuideAction.Kind.BACK));
        assertTrue(GuideFixtures.view(5, true).buttons().stream()
                .anyMatch(b -> b.action().equals(GuideAction.back())
                        && GuideFixtures.plain(b.label()).equals("{guide.roulettecasino.contents}")));
        // the open chapter's bookmark stands out
        GuideView decor = GuideFixtures.view(14, false);
        assertEquals("{guide.roulettecasino.decor}", GuideFixtures.plain(decor.buttons().get(1).label()));
        assertEquals(0xF2C65A, decor.buttons().get(1).label().color().value());
        assertEquals(null, decor.buttons().get(0).label().color());
    }

    @Test
    void recipesArePictures() {
        // the guide's own recipe (spread 22): shapeless, its sign with the mod's tooltip, the ingredients from the
        // top left (the picture draws the known icons first, then the vanilla ones line by line)
        String spread22 = GuideFixtures.describe(GuideFixtures.view(22, false));
        assertTrue(spread22.contains("recipe(250) Formlos · "
                + "{item.roulettecasino.casino_guide} | <{guide.roulettecasino.expansion.shapeless}> "
                + "<{minecraft:green_wool}> <{item.roulettecasino.casino_guide}> <{minecraft:leather}> "
                + "<{minecraft:paper}>"), spread22);
        // spread 33: labels beside an item are its description; plinko links to its spread
        String spread33 = GuideFixtures.describe(GuideFixtures.view(33, false));
        assertTrue(spread33.contains("item roulettecasino:croupier_hand | {guide.roulettecasino.text.248} / "
                + "{guide.roulettecasino.text.249}"), spread33);
        assertTrue(spread33.contains("item minecraft:emerald | {guide.roulettecasino.text.213}\ntext(250) NOTE-213"),
                spread33); // the plugin's note right under it: bet lists and payouts are not in the dialog here
        // spread 9: the roulette mechanism is a component here, its own spread is 4 (its icon links there, as the
        // result of its recipe on this spread)
        String spread9 = GuideFixtures.describe(GuideFixtures.view(9, false));
        assertTrue(spread9.contains("[<{item.roulettecasino.roulette_mechanism}>](kaboofcasino:guide "
                + "{do:\"link\",to:\"4\"}) <{minecraft:gold_block}>"), spread9);
        assertTrue(spread9.contains("recipe(250) {item.roulettecasino.roulette_mechanism} | "
                + "<{minecraft:diamond_block}> [<{item.roulettecasino.roulette_mechanism}>](kaboofcasino:guide "
                + "{do:\"link\",to:\"4\"})"), spread9);
        // results with counts, tags with their first member and "(beliebige Sorte)" as the tooltip
        String spread12 = GuideFixtures.describe(GuideFixtures.view(12, false));
        assertTrue(spread12.contains("recipe(250) {block.roulettecasino.casino_chair} | "
                + "<{minecraft:oak_planks} (beliebige Sorte)> <{minecraft:red_wool}>"), spread12);
        assertTrue(spread12.contains("<{block.roulettecasino.casino_chair}> x2 <{minecraft:gold_nugget}> "
                + "<{minecraft:gold_nugget}>"), spread12);
        // the decoration's counts up to 8
        assertTrue(GuideFixtures.describe(GuideFixtures.view(24, false))
                .contains("<{block.casinodeco.emerald_velvet}> x8"));
    }

    /**
     * Every recipe widget is a picture: the pack's lines one to one, the caption once, each ingredient cell and the
     * result with its tooltip (and link), the shapeless sign exactly on shapeless recipes, the count above 1.
     */
    @Test
    void everyRecipeHasItsPicture() {
        GuideBook book = GuideFixtures.book();
        RecipePictures pictures = GuideFixtures.pictures();
        GuideDialogs dialogs = new GuideDialogs(book, pictures);
        GuideTexts texts = GuideFixtures.texts();
        int recipes = 0;
        for (GuideBook.Page page : book.pages()) {
            List<GuideBook.Widget> widgets = page.widgets();
            for (int i = 0; i < widgets.size(); i++) {
                if (!(widgets.get(i) instanceof GuideBook.Recipe r)) {
                    continue;
                }
                recipes++;
                String where = page.number() + ":" + i;
                // the caption (name, "Formlos · name") stays inside the 250 px text box
                String caption = GuideFixtures.plain(dialogs.caption(r, texts));
                String german = book.nameKey(r.result()) == null ? null : book.text(book.nameKey(r.result()));
                String shown = caption.startsWith("{") || caption.startsWith("Formlos · {")
                        ? (r.shapeless() ? "Formlos · " : "") + german : caption;
                assertTrue(german == null || de.tigges.kaboofcasino.util.FontWidth.width(shown)
                        <= GuideDialogs.CAPTION_PX, where + ": " + shown);
                RecipePictures.Picture picture = pictures.picture(page.number(), i).orElseThrow(() ->
                        new AssertionError("no picture for " + where));
                Component c = dialogs.recipe(r, page.number(), i, texts);
                List<Component> lines = c.children().stream().filter(l -> !l.equals(Component.newline())).toList();
                assertEquals(picture.lines().size(), lines.size(), where);
                Set<Integer> cells = new TreeSet<>();
                int captions = 0;
                int hints = 0;
                String count = "";
                boolean result = false;
                for (int l = 0; l < lines.size(); l++) {
                    List<RecipePictures.Segment> segments = picture.lines().get(l);
                    List<Component> runs = lines.get(l).children();
                    assertEquals(segments.size(), runs.size(), where + " line " + l);
                    for (int s = 0; s < segments.size(); s++) {
                        RecipePictures.Segment seg = segments.get(s);
                        Component run = runs.get(s);
                        switch (seg.kind()) {
                            case CAPTION -> {
                                captions++;
                                assertFalse(GuideFixtures.picture(run), where);
                                // the result's name; shapeless ones with the short "Formlos · " in front
                                assertEquals(GuideFixtures.plain(dialogs.caption(r, texts)), GuideFixtures.plain(run),
                                        where);
                                assertEquals(r.shapeless(), GuideFixtures.plain(run).startsWith("Formlos · "), where);
                            }
                            case CELL -> {
                                cells.add(seg.cell());
                                String ref = r.cells().get(seg.cell());
                                assertNotNull(ref, where + " cell " + seg.cell());
                                String hover = GuideFixtures.plain(run);
                                if (ref.startsWith("#")) {
                                    assertTrue(hover.endsWith(" (beliebige Sorte)>"), hover);
                                    assertEquals(null, run.clickEvent(), "a tag is no link " + where);
                                } else {
                                    assertTrue(hover.contains("<" + GuideFixtures.plain(GuideFixtures.itemName(ref))
                                            + ">"), hover);
                                    linkIfIndexedElsewhere(book, ref, page.number(), run);
                                }
                            }
                            case RESULT -> {
                                result = true;
                                assertTrue(GuideFixtures.plain(run).contains("<" + GuideFixtures.plain(
                                        GuideFixtures.itemName(r.result())) + ">"), where);
                                linkIfIndexedElsewhere(book, r.result(), page.number(), run);
                            }
                            case HINT -> {
                                hints++;
                                assertEquals("<{guide.roulettecasino.expansion.shapeless}>",
                                        GuideFixtures.plain(run));
                            }
                            case COUNT -> count += GuideFixtures.plain(run);
                            case PLAIN -> {
                                assertTrue(GuideFixtures.picture(run), where);
                                assertEquals(null, run.hoverEvent(), where);
                                assertEquals(null, run.clickEvent(), where);
                            }
                        }
                    }
                }
                assertEquals(1, captions, where);
                assertTrue(result, where);
                assertEquals(r.shapeless() ? 1 : 0, hints, where);
                assertEquals(r.count() > 1 ? "x" + r.count() : "", count, where);
                Set<Integer> filled = new TreeSet<>();
                for (int cell = 0; cell < 9; cell++) {
                    if (r.cells().get(cell) != null) {
                        filled.add(cell);
                    }
                }
                assertEquals(filled, cells, where);
            }
        }
        assertEquals(89, recipes);
        assertEquals(89, pictures.all().size());
        assertTrue(GuideDialogs.TEXT_WIDTH >= pictures.width() + 8, "the picture lines fit the text body");
    }

    private static void linkIfIndexedElsewhere(GuideBook book, String id, int page, Component run) {
        java.util.OptionalInt target = book.indexPage(id);
        if (target.isPresent() && target.getAsInt() != page) {
            assertEquals(List.of("{do:\"link\",to:\"" + target.getAsInt() + "\"}"), GuideFixtures.clicks(run), id);
        } else {
            assertEquals(null, run.clickEvent(), id);
        }
    }

    /** Every glyph of every picture is a char of the pack's recipe font (a bitmap glyph or a space). */
    @Test
    void everyPictureGlyphIsInThePackFont() {
        RecipePictures pictures = GuideFixtures.pictures();
        String path = "assets/" + pictures.font().namespace() + "/font/" + pictures.font().value() + ".json";
        byte[] font = PackFiles.all().get(path);
        assertNotNull(font, path);
        Set<Integer> defined = new TreeSet<>();
        for (var p : com.google.gson.JsonParser.parseString(new String(font, java.nio.charset.StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("providers")) {
            var provider = p.getAsJsonObject();
            if (provider.get("type").getAsString().equals("space")) {
                provider.getAsJsonObject("advances").keySet().forEach(k -> defined.add(k.codePointAt(0)));
            } else {
                for (var row : provider.getAsJsonArray("chars")) {
                    row.getAsString().codePoints().filter(cp -> cp != 0).forEach(defined::add);
                }
                String file = provider.get("file").getAsString();
                String ns = file.substring(0, file.indexOf(':'));
                assertTrue(ns.equals("minecraft") || PackFiles.all().containsKey("assets/" + ns + "/textures/"
                        + file.substring(file.indexOf(':') + 1)), file);
            }
        }
        int glyphs = 0;
        for (RecipePictures.Picture picture : pictures.all().values()) {
            for (RecipePictures.Segment s : RecipePictures.segments(picture)) {
                for (int cp : s.text().codePoints().toArray()) {
                    assertTrue(defined.contains(cp), "U+" + Integer.toHexString(cp) + " is not in " + path);
                    glyphs++;
                }
            }
        }
        assertTrue(glyphs > 1000, "glyphs " + glyphs);
    }

    @Tag("german")
    @Test
    void stylesFollowTheMod() {
        List<Body> cover = GuideFixtures.view(1, false).body();
        // title widget: bold, the mod's gold
        TranslatableComponent title = (TranslatableComponent) ((TextBody) cover.get(1)).text();
        assertEquals("guide.roulettecasino.text.006", title.key());
        assertEquals("CASINO-HANDBUCH", title.fallback());
        assertEquals(0xF2C65A, title.color().value());
        assertEquals(TextDecoration.State.TRUE, title.decoration(TextDecoration.BOLD));
        // the muted label below the scene's items
        Component label = cover.stream().filter(b -> b instanceof TextBody t
                && t.text() instanceof TranslatableComponent tc && tc.key().equals("guide.roulettecasino.text.004"))
                .map(b -> ((TextBody) b).text()).findFirst().orElseThrow();
        assertEquals(0xA8C6B0, label.color().value());
        // a section tile: icon, gold underlined link with a hover hint
        ItemBody machines = cover.stream().filter(b -> b instanceof ItemBody i
                && i.item().equals("roulettecasino:slot_machine") && i.description() != null)
                .map(ItemBody.class::cast).findFirst().orElseThrow();
        Component link = machines.description();
        assertEquals(0xF2C65A, link.color().value());
        assertEquals(TextDecoration.State.TRUE, link.decoration(TextDecoration.UNDERLINED));
        assertNotNull(link.hoverEvent());
        assertEquals(List.of("{do:\"link\",to:\"2\"}"), GuideFixtures.clicks(link));
        // the rule: a struck-through line in the mod's frame colour
        Component rule = GuideFixtures.view(5, false).body().stream()
                .filter(b -> b instanceof TextBody t && t.text().decoration(TextDecoration.STRIKETHROUGH)
                        == TextDecoration.State.TRUE)
                .map(b -> ((TextBody) b).text()).findFirst().orElseThrow();
        assertEquals(GuideDialogs.FRAME, rule.color());
    }

    @Test
    void pluginNotesHangOnModTextsOfTheirSpreads() {
        // every mod text a plugin note amends is on the spread the note is written for
        java.util.Map<String, Integer> where = java.util.Map.of(GuideService.NOTE_COUNTER, 3, GuideService.NOTE_HAND,
                33, GuideService.NOTE_BETS, 33, GuideService.NOTE_NAMES, 34, GuideService.NOTE_NUMBER, 34,
                GuideService.NOTE_CHIPS, 2);
        where.forEach((key, spread) -> assertTrue(GuideFixtures.describe(GuideFixtures.view(spread, false))
                .contains("{" + key + "}"), key + " on spread " + spread));
    }

    @Test
    void snapshotOfTheCroupierSpread() {
        // spread 3 with a way back: villager -> croupier, the plugin's note on how it works here, the counter's
        // recipe picture (caption, then the icons as drawn: the wool and planks with the card, then the gold ingots
        // that sit on lines of their own), links to blackjack and poker; right
        // page: 24 emeralds -> hiring card, the hiring scene as the croupier, the badge with its label
        String expected = """
                title {item.roulettecasino.casino_guide}
                text(300) [« {guide.roulettecasino.previous}](kaboofcasino:guide {do:"turn",to:"2"})   3 / 34   [{guide.roulettecasino.next} »](kaboofcasino:guide {do:"turn",to:"4"})
                text(250) *{guide.roulettecasino.text.041}*
                item minecraft:villager_spawn_egg | {entity.minecraft.villager}
                text(250) ↓
                item minecraft:villager_spawn_egg | {entity.roulettecasino.villager.croupier}
                text(250) {guide.roulettecasino.text.043}
                text(250) NOTE-043
                recipe(250) {block.roulettecasino.dealer_station} | <{minecraft:green_wool}> <{minecraft:green_wool}> <{minecraft:green_wool}> <{minecraft:red_wool}> <{minecraft:oak_planks} (beliebige Sorte)> <{minecraft:oak_planks} (beliebige Sorte)> <{minecraft:oak_planks} (beliebige Sorte)> <{block.roulettecasino.dealer_station}> <{minecraft:gold_ingot}> <{minecraft:gold_ingot}>
                item roulettecasino:blackjack_table | [{block.roulettecasino.blackjack_table}](kaboofcasino:guide {do:"link",to:"5"})
                item roulettecasino:poker_table | [{block.roulettecasino.poker_table}](kaboofcasino:guide {do:"link",to:"6"})
                text(250) *{guide.roulettecasino.text.046}*
                item minecraft:emerald x24 | {minecraft:emerald}
                text(250) ↓
                item roulettecasino:croupier_hiring_card | {item.roulettecasino.croupier_hiring_card}
                text(250) {guide.roulettecasino.text.050}
                item minecraft:villager_spawn_egg | {entity.roulettecasino.villager.croupier}
                item roulettecasino:croupier_badge | {guide.roulettecasino.text.051}
                button {guide.roulettecasino.machines} -> {do:"tab",to:"2"}
                button {guide.roulettecasino.decor} -> {do:"tab",to:"11"}
                button {guide.roulettecasino.floors} -> {do:"tab",to:"23"}
                button {guide.roulettecasino.config} -> {do:"tab",to:"33"}
                button « {guide.roulettecasino.previous} -> {do:"turn",to:"2"}
                button {guide.roulettecasino.contents} -> {do:"back"}
                button {guide.roulettecasino.next} » -> {do:"turn",to:"4"}
                exit {guide.roulettecasino.close} -> {do:"close"}""";
        assertEquals(expected, GuideFixtures.describe(GuideFixtures.view(3, true)));
    }
}
