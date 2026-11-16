package de.tigges.kaboofcasino.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.chipshop.ChipShopCatalog;
import de.tigges.kaboofcasino.chipshop.ChipShopCategory;
import de.tigges.kaboofcasino.chipshop.ChipShopSettings;
import de.tigges.kaboofcasino.config.Messages;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The croupier's hand's Chip-Shop price dialog: pages, inputs, parsing and how the overrides list changes. */
class ChipShopDialogsTest {

    private static Messages messages;

    @BeforeAll
    static void load(@TempDir Path dir) throws IOException {
        Path file = dir.resolve(de.tigges.kaboofcasino.config.Language.current().messagesFile());
        try (InputStream in = ChipShopDialogsTest.class.getClassLoader().getResourceAsStream(de.tigges.kaboofcasino.config.Language.current().messagesFile())) {
            Files.write(file, in.readAllBytes());
        }
        messages = Messages.load(file, () -> ChipShopDialogsTest.class.getClassLoader()
                .getResourceAsStream(de.tigges.kaboofcasino.config.Language.current().messagesFile())).messages();
    }

    static List<ChipShopCatalog.Row> rows(int n) {
        List<ChipShopCatalog.Row> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(new ChipShopCatalog.Row("casinodeco:item_" + i, ChipShopCategory.FLOORS, OptionalLong.of(10 + i),
                    new ChipShopSettings.Item("casinodeco:item_" + i, i == 1 ? 99 : 0, i == 2 ? 3 : 0, i == 3)));
        }
        return out;
    }

    static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    @Tag("german")
    @Test
    void aPageShowsSixItemsWithTheirInputs() {
        ChipShopDialogs.View v = ChipShopDialogs.view(rows(8), 0, id -> Component.text(id), r -> Component.text("Böden"),
                messages);
        assertEquals("Chip-Shop – Preise", plain(v.title()));
        assertEquals(6, v.body().stream().filter(l -> l.item() != null).count());
        assertEquals(18, v.inputs().size());
        assertEquals("", ((AdminView.TextInput) v.inputs().get(0)).initial(), "automatic: empty");
        assertEquals("99", ((AdminView.TextInput) v.inputs().get(3)).initial());
        assertEquals("3", ((AdminView.TextInput) v.inputs().get(7)).initial());
        assertTrue(((AdminView.BoolInput) v.inputs().get(11)).initial());
        assertTrue(v.body().stream().anyMatch(l -> plain(l.text()).contains("casinodeco:item_0 – 10 Chips (automatisch)")),
                () -> v.body().stream().map(l -> plain(l.text())).toList().toString());
        assertEquals(3, v.buttons().size(), "save and two page buttons");
        assertEquals(2, ChipShopDialogs.view(rows(8), 1, id -> Component.text(id), r -> Component.text("x"), messages)
                .body().stream().filter(l -> l.item() != null).count());
    }

    @Test
    void inputsBecomeOverridesAndTheListKeepsTheRest() {
        List<ChipShopCatalog.Row> shown = ChipShopDialogs.shown(rows(8), 0);
        Map<String, String> text = new HashMap<>();
        Map<String, Boolean> bool = new HashMap<>();
        text.put("price_0", "250");
        text.put("limit_0", "2");
        text.put("price_1", "");     // back to automatic
        bool.put("hide_3", false);    // shown again
        var edited = ChipShopDialogs.parse(shown, text::get, bool::get).orElseThrow();
        assertEquals(new ChipShopSettings.Item("casinodeco:item_0", 250, 2, false), edited.get(0));
        assertEquals(new ChipShopSettings.Item("casinodeco:item_1", 0, 0, false), edited.get(1));
        assertEquals(new ChipShopSettings.Item("casinodeco:item_2", 0, 3, false), edited.get(2), "untouched");
        assertEquals(3, ChipShopDialogs.changes(shown, edited).size());
        List<ChipShopSettings.Item> before = List.of(new ChipShopSettings.Item("casinodeco:item_1", 99, 0, false),
                new ChipShopSettings.Item("casinodeco:item_2", 0, 3, false),
                new ChipShopSettings.Item("casinodeco:item_3", 0, 0, true),
                new ChipShopSettings.Item("casinodeco:other", 5, 0, false));
        assertEquals(List.of(new ChipShopSettings.Item("casinodeco:item_2", 0, 3, false),
                new ChipShopSettings.Item("casinodeco:other", 5, 0, false),
                new ChipShopSettings.Item("casinodeco:item_0", 250, 2, false)), ChipShopDialogs.merged(before, edited));
        // invalid inputs refuse the whole click
        text.put("price_2", "abc");
        assertTrue(ChipShopDialogs.parse(shown, text::get, bool::get).isEmpty());
        text.put("price_2", "100000001");
        assertTrue(ChipShopDialogs.parse(shown, text::get, bool::get).isEmpty());
        text.put("price_2", "");
        text.put("limit_2", "-1");
        assertTrue(ChipShopDialogs.parse(shown, text::get, bool::get).isEmpty());
    }
}
