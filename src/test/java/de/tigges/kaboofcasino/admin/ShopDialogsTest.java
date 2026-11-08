package de.tigges.kaboofcasino.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.cashier.CashierSettings;
import de.tigges.kaboofcasino.cashier.ShopEntry;
import de.tigges.kaboofcasino.config.Messages;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The cashier's shop dialog of the croupier's hand: what it shows, that a forged or broken answer is never applied
 * (unknown actions, ADD without an off-hand item, bad prices and limits, missing inputs), and how a page's edits,
 * removals and the added off-hand item change the list.
 */
class ShopDialogsTest {

    private static Messages messages;

    @BeforeAll
    static void load(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("messages_de.yml");
        try (InputStream in = ShopDialogsTest.class.getClassLoader().getResourceAsStream("messages_de.yml")) {
            Files.write(file, in.readAllBytes());
        }
        messages = Messages.load(file, () -> ShopDialogsTest.class.getClassLoader()
                .getResourceAsStream("messages_de.yml")).messages();
    }

    static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    static List<ShopEntry> shop(int n) {
        List<ShopEntry> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(new ShopEntry("minecraft:cake", i % 64 + 1, 10 + i, i % 3));
        }
        return out;
    }

    static ShopDialogs.View view(List<ShopEntry> shop, int page, String newSpec) {
        return ShopDialogs.view(new ShopDialogs.Data(shop, page, newSpec, newSpec == null ? 0 : 5,
                e -> Component.text(e.count() + "× Kuchen"), Component.text("Diamantschwert")), messages);
    }

    /** The answer a client sends for {@code view} with its inputs untouched and the button {@code action}. */
    static Map<String, Object> echo(ShopDialogs.View view, ShopClick.Action action) {
        Map<String, Object> values = new HashMap<>();
        values.put(ShopClick.DO, action.id());
        for (AdminView.Input in : view.inputs()) {
            switch (in) {
                case AdminView.TextInput t -> values.put(t.key(), t.initial());
                case AdminView.BoolInput b -> values.put(b.key(), b.initial());
                case AdminView.RangeInput r -> values.put(r.key(), (float) r.initial());
            }
        }
        return values;
    }

    static Optional<ShopClick> parse(int shown, boolean withNew, Map<String, Object> values) {
        return ShopClick.parse(shown, withNew, k -> values.get(k) instanceof String s ? s : null,
                k -> values.get(k) instanceof Boolean b ? b : null);
    }

    @Test
    void theDialogShowsAPageOfPrizesAndTheOffHandItem() {
        ShopDialogs.View v = view(shop(8), 0, "minecraft:diamond_sword");
        assertEquals("Kasse – Prämien", plain(v.title()));
        long prizes = v.body().stream().filter(l -> l instanceof ShopDialogs.PrizeLine).count();
        assertEquals(ShopDialogs.PAGE_SIZE, prizes);
        assertEquals(3 * ShopDialogs.PAGE_SIZE + 2, v.inputs().size());
        assertEquals("1. Preis", plain(((AdminView.TextInput) v.inputs().get(0)).label()));
        assertEquals("10", ((AdminView.TextInput) v.inputs().get(0)).initial());
        assertTrue(v.body().stream().anyMatch(l -> plain(l.text()).startsWith("Neu: Diamantschwert (5×)")));
        assertEquals(List.of(ShopClick.Action.SAVE, ShopClick.Action.ADD, ShopClick.Action.PREV,
                ShopClick.Action.NEXT), v.buttons().stream().map(ShopDialogs.Button::action).toList());
        assertEquals(ShopClick.Action.DONE, v.exit().action());
        // the second page: prizes 7 and 8, numbered as in the whole list
        ShopDialogs.View second = view(shop(8), 1, null);
        assertEquals(2, second.body().stream().filter(l -> l instanceof ShopDialogs.PrizeLine).count());
        assertEquals("7. Preis", plain(((AdminView.TextInput) second.inputs().get(0)).label()));
        assertEquals(List.of(ShopClick.Action.SAVE, ShopClick.Action.PREV, ShopClick.Action.NEXT),
                second.buttons().stream().map(ShopDialogs.Button::action).toList(), "no ADD without an item");
        // a page beyond the end shows the last one; one page has no page buttons
        assertEquals(second.inputs().size(), view(shop(8), 5, null).inputs().size());
        assertEquals(List.of(ShopClick.Action.SAVE), view(shop(2), 0, null).buttons().stream()
                .map(ShopDialogs.Button::action).toList());
        assertTrue(view(List.of(), 0, null).inputs().isEmpty());
        // a full shop offers no ADD
        assertFalse(view(shop(CashierSettings.MAX_SHOP), 0, "minecraft:apple").buttons().stream()
                .anyMatch(b -> b.action() == ShopClick.Action.ADD));
    }

    @Test
    void anOffHandItemWithChipsSaysWhyItCannotBeAPrize() {
        ShopDialogs.View none = view(shop(2), 0, null);
        assertTrue(none.body().stream().anyMatch(l -> plain(l.text()).startsWith("Neue Prämie: Nimm ein Item")));
        ShopDialogs.View refused = ShopDialogs.view(new ShopDialogs.Data(shop(2), 0, null, 0,
                e -> Component.text(e.count() + "× Kuchen"), null, true), messages);
        assertTrue(refused.body().stream().anyMatch(l -> plain(l.text()).equals(
                "Chips und Casino-Items, auch in Kisten oder Bündeln, können keine Prämie sein.")));
        assertFalse(refused.buttons().stream().anyMatch(b -> b.action() == ShopClick.Action.ADD));
        assertEquals(none.inputs().size(), refused.inputs().size(), "no new-prize inputs");
        // prices and limits in the list are formatted like every other number
        ShopDialogs.View big = view(List.of(new ShopEntry("minecraft:cake", 1, 1_250_000, 1000)), 0, null);
        assertTrue(big.body().stream().anyMatch(l -> plain(l.text()).endsWith("– 1.250.000 Chips, Limit 1.000")),
                () -> big.body().stream().map(l -> plain(l.text())).toList().toString());
        ShopDialogs.View one = view(List.of(new ShopEntry("minecraft:cake", 1, 1, 0)), 0, null);
        assertTrue(one.body().stream().anyMatch(l -> plain(l.text()).endsWith("– 1 Chip, Limit 0")));
    }

    @Test
    void anUntouchedDialogChangesNothing() {
        List<ShopEntry> shop = shop(9);
        for (int page = 0; page < 2; page++) {
            ShopDialogs.View v = view(shop, page, null);
            ShopClick click = parse(ShopDialogs.shown(shop, page).size(), false, echo(v, ShopClick.Action.SAVE))
                    .orElseThrow();
            ShopDialogs.Changes c = ShopDialogs.apply(shop, page, click.edits().orElseThrow(), null);
            assertEquals(shop, c.after());
            assertEquals(List.of(), c.lines());
        }
    }

    @Test
    void forgedOrBrokenAnswersAreNeverApplied() {
        List<ShopEntry> shop = shop(3);
        ShopDialogs.View v = view(shop, 0, null);
        Map<String, Object> ok = echo(v, ShopClick.Action.SAVE);
        // unknown actions, ADD without an off-hand item
        for (String action : new String[] {null, "", "SAVE", "delete", "save ", "{}"}) {
            Map<String, Object> forged = new HashMap<>(ok);
            forged.put(ShopClick.DO, action);
            assertTrue(parse(3, false, forged).isEmpty(), "action " + action);
        }
        Map<String, Object> add = new HashMap<>(ok);
        add.put(ShopClick.DO, "add");
        assertTrue(parse(3, false, add).isEmpty());
        // bad values: the click is read, its edits are not
        for (String price : new String[] {"0", "-5", "1,5", "1.5.", "abc", "1e3", "100000001", "99999999999999", " ",
                "0x10", "١٢"}) {
            Map<String, Object> forged = new HashMap<>(ok);
            forged.put(ShopClick.price(1), price);
            assertTrue(parse(3, false, forged).orElseThrow().edits().isEmpty(), "price '" + price + "'");
        }
        for (String limit : new String[] {"-1", "1001", "x", "2.5"}) {
            Map<String, Object> forged = new HashMap<>(ok);
            forged.put(ShopClick.limit(0), limit);
            assertTrue(parse(3, false, forged).orElseThrow().edits().isEmpty(), "limit '" + limit + "'");
        }
        Map<String, Object> missing = new HashMap<>(ok);
        missing.remove(ShopClick.remove(2));
        assertTrue(parse(3, false, missing).orElseThrow().edits().isEmpty(), "a checkbox is missing");
        Map<String, Object> wrongType = new HashMap<>(ok);
        wrongType.put(ShopClick.price(0), 50f);
        assertTrue(parse(3, false, wrongType).orElseThrow().edits().isEmpty(), "a number instead of text");
        // separators and an empty limit are fine
        Map<String, Object> typed = new HashMap<>(ok);
        typed.put(ShopClick.price(0), " 1.250 ");
        typed.put(ShopClick.price(1), "100 000 000");
        typed.put(ShopClick.limit(2), "");
        Map<Integer, ShopClick.Edit> edits = parse(3, false, typed).orElseThrow().edits().orElseThrow();
        assertEquals(1250, edits.get(0).price());
        assertEquals(100_000_000L, edits.get(1).price());
        assertEquals(0, edits.get(2).limit());
        // inputs of prizes the dialog did not show are ignored
        Map<String, Object> extra = new HashMap<>(ok);
        extra.put(ShopClick.price(7), "1");
        assertEquals(3, parse(3, false, extra).orElseThrow().edits().orElseThrow().size());
        // the off-hand item's values
        ShopDialogs.View withNew = view(shop, 0, "minecraft:apple");
        Map<String, Object> adding = echo(withNew, ShopClick.Action.ADD);
        assertTrue(parse(3, true, adding).orElseThrow().added().isEmpty(), "no price typed yet");
        adding.put(ShopClick.NEW_PRICE, "75");
        adding.put(ShopClick.NEW_LIMIT, "2");
        assertEquals(new ShopClick.Edit(75, 2, false), parse(3, true, adding).orElseThrow().added().orElseThrow());
        assertNull(ShopClick.number("12", 13, 20, false));
    }

    @Test
    void editsRemovalsAndTheAddedItem() {
        List<ShopEntry> shop = shop(8);
        Map<Integer, ShopClick.Edit> edits = new HashMap<>();
        // page 1 shows prizes 6 and 7: raise the price of 6, remove 7
        edits.put(0, new ShopClick.Edit(500, 5, false));
        edits.put(1, new ShopClick.Edit(shop.get(7).price(), shop.get(7).limit(), true));
        ShopEntry added = new ShopEntry("minecraft:apple", 3, 9, 0);
        ShopDialogs.Changes c = ShopDialogs.apply(shop, 1, edits, added);
        assertEquals(8, c.after().size());
        assertEquals(shop.subList(0, 6), c.after().subList(0, 6));
        assertEquals(shop.get(6).withPrice(500).withLimit(5), c.after().get(6));
        assertEquals(added, c.after().get(7));
        assertEquals(3, c.lines().size());
        assertTrue(c.lines().get(0).contains("price 16 -> 500"), c.lines().toString());
        assertTrue(c.lines().get(1).startsWith("removed"));
        assertTrue(c.lines().get(2).startsWith("added 3x minecraft:apple"));
    }
}
