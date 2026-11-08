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
    void recipeGrids() {
        List<String> dealer = Arrays.asList("minecraft:green_wool", "minecraft:green_wool", "minecraft:green_wool",
                "minecraft:gold_ingot", "minecraft:red_wool", "minecraft:gold_ingot", "#minecraft:planks",
                "#minecraft:planks", "#minecraft:planks");
        assertEquals(List.of("1 1 1", "2 3 2", "4 4 4"), GuideDialogs.grid(dealer,
                List.of("minecraft:green_wool", "minecraft:gold_ingot", "minecraft:red_wool", "#minecraft:planks")));
        List<String> slab = Arrays.asList(null, null, null, null, null, null, "a", "a", "a");
        assertEquals(List.of("1 1 1"), GuideDialogs.grid(slab, List.of("a")));
        List<String> duck = Arrays.asList(null, "d", null, "n", "x", "i", null, "p", null);
        assertEquals(List.of("- 1 -", "2 3 4", "- 5 -"), GuideDialogs.grid(duck, List.of("d", "n", "x", "i", "p")));
        List<String> column = Arrays.asList(null, "a", null, null, "b", null, null, null, null);
        assertEquals(List.of("1", "2"), GuideDialogs.grid(column, List.of("a", "b")));
    }

    @Test
    void recipesItemsAndLabels() {
        // the guide's own recipe (spread 22): shapeless, counts, a big item, the result's name
        String spread22 = GuideFixtures.describe(GuideFixtures.view(22, false));
        assertTrue(spread22.contains("item roulettecasino:casino_guide | {guide.roulettecasino.expansion.shapeless} · "
                + "{item.roulettecasino.casino_guide} / 1× {minecraft:leather} / 1× {minecraft:paper} / "
                + "1× {minecraft:green_wool}"), spread22);
        // spread 33: labels beside an item are its description; plinko links to its spread
        String spread33 = GuideFixtures.describe(GuideFixtures.view(33, false));
        assertTrue(spread33.contains("item roulettecasino:croupier_hand | {guide.roulettecasino.text.248} / "
                + "{guide.roulettecasino.text.249}"), spread33);
        assertTrue(spread33.contains("item minecraft:emerald | {guide.roulettecasino.text.213}\ntext(250) NOTE-213"),
                spread33); // the plugin's note right under it: bet lists and payouts are not in the dialog here
        // spread 9: the roulette mechanism is a component here, its own spread is 4 (a link in the legend)
        String spread9 = GuideFixtures.describe(GuideFixtures.view(9, false));
        assertTrue(spread9.contains("[{item.roulettecasino.roulette_mechanism}](kaboofcasino:guide "
                + "{do:\"link\",to:\"4\"})"), spread9);
        // results with counts, tags with their first member
        String spread12 = GuideFixtures.describe(GuideFixtures.view(12, false));
        assertTrue(spread12.contains("item roulettecasino:casino_chair x2 | Rezept · "
                + "{block.roulettecasino.casino_chair}"), spread12);
        assertTrue(spread12.contains("{minecraft:oak_planks} (beliebige Sorte)"), spread12);
    }

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
                33, GuideService.NOTE_BETS, 33, GuideService.NOTE_NAMES, 34, GuideService.NOTE_NUMBER, 34);
        where.forEach((key, spread) -> assertTrue(GuideFixtures.describe(GuideFixtures.view(spread, false))
                .contains("{" + key + "}"), key + " on spread " + spread));
    }

    @Test
    void snapshotOfTheCroupierSpread() {
        // spread 3 with a way back: villager -> croupier, the plugin's note on how it works here, the counter's
        // recipe, links to blackjack and poker; right
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
                item roulettecasino:dealer_station | Rezept · {block.roulettecasino.dealer_station} / 1 1 1 / 2 3 2 / 4 4 4 / 1 {minecraft:green_wool} / 2 {minecraft:gold_ingot} / 3 {minecraft:red_wool} / 4 {minecraft:oak_planks} (beliebige Sorte)
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
