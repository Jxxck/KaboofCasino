package de.tigges.kaboofcasino.cashier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.gui.GuiLayout;
import de.tigges.kaboofcasino.pack.PackFiles;
import de.tigges.kaboofcasino.wager.Chips;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The cashier menu against this build's pack: every glyph, anchor and model it names exists, the number displays and
 * the marker have a case for every string the menu sends, the receipt's texts fit the right panel, the rig's models are
 * in the pack, and the menu's pure rules (digits, selection clicks, chip rows, pages).
 */
class CashierGuiTest {

    /** The right panel's text room (window x), from the derby's panel: 196..300 with 4 px margins. */
    static final int PANEL_LEFT = 200;
    static final int PANEL_RIGHT = 297;

    private static GuiLayout layout;
    private static Function<Msg, String> raw;

    @BeforeAll
    static void load() {
        layout = GuiLayout.load(CashierGuiTest.class.getClassLoader().getResourceAsStream("pack/gui-layout.json"));
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(
                CashierGuiTest.class.getClassLoader().getResourceAsStream("messages_de.yml"), StandardCharsets.UTF_8));
        raw = m -> yaml.getString(m.key());
    }

    private static JsonObject json(String path) {
        byte[] bytes = PackFiles.all().get(path);
        assertNotNull(bytes, path);
        return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void everyTitleGlyphAndAnchorExistsAndTheReceiptFits() {
        for (CashierView.Tab tab : CashierView.Tab.values()) {
            Component title = CashierMenu.title(layout, raw, tab);
            assertTrue(title.children().size() > 5, tab.name());
            layout.glyph("cashier_overlay_" + tab.pack());
            Msg[] texts = CashierMenu.texts(tab);
            List<String[]> lines = new ArrayList<>();
            lines.add(new String[] {"cashier_head", raw.apply(texts[0])});
            String[] help = raw.apply(texts[1]).split("\n");
            assertTrue(help.length <= 5, tab + ": at most five help lines");
            for (int i = 0; i < help.length; i++) {
                lines.add(new String[] {"cashier_help_" + i, help[i].strip()});
            }
            lines.add(new String[] {"cashier_label_a", raw.apply(texts[2])});
            lines.add(new String[] {"cashier_label_b", raw.apply(Msg.CASHIER_LABEL_PRICE)});
            lines.add(new String[] {"cashier_brand", raw.apply(Msg.CASHIER_BRAND)});
            for (String[] line : lines) {
                GuiLayout.Anchor a = layout.anchor(line[0]);
                int w = layout.width(line[1]) + (line[0].equals("cashier_brand") ? line[1].length() : 0);
                int left = switch (a.align()) {
                    case LEFT -> a.x();
                    case CENTER -> a.x() - w / 2;
                    case RIGHT -> a.x() - w;
                };
                assertTrue(left >= PANEL_LEFT && left + w <= PANEL_RIGHT, tab + " " + line[0] + " '" + line[1]
                        + "' spans " + left + ".." + (left + w));
                for (int i = 0; i < line[1].length(); i++) {
                    assertTrue(line[1].charAt(i) == ' ' || layout.hasGlyph(line[1].charAt(i)), "no glyph for '"
                            + line[1].charAt(i) + "' in " + line[1]);
                }
            }
            // the title's text runs use the anchors' row fonts
            for (Component c : title.children()) {
                Key font = ((TextComponent) c).font();
                assertNotNull(font);
            }
        }
    }

    @Test
    void everyModelTheMenuAndTheRigNameIsInThePack() {
        Set<String> keys = new TreeSet<>(CashierRig.modelKeys());
        keys.add("kaboofcasino:cashier");
        keys.add("kaboofcasino:gui/blank");
        for (String name : List.of("num_a", "num_b", "marker", "exchange_on", "exchange_off", "buy_on", "buy_off",
                "clear", "prev", "next")) {
            keys.add("kaboofcasino:gui/cashier/" + name);
        }
        for (String key : keys) {
            assertTrue(PackFiles.hasItemModel(key), key);
        }
        // the chips themselves come from the chip art (step 9): their keys are the denomination keys
        for (int d : Chips.DENOMINATIONS) {
            assertEquals("kaboofcasino:chip_" + d, Chips.key(d));
        }
    }

    /** The {@code when} values of a select's cases. */
    private static Set<String> whens(JsonObject select) {
        Set<String> out = new TreeSet<>();
        for (JsonElement c : select.getAsJsonArray("cases")) {
            JsonElement when = c.getAsJsonObject().get("when");
            if (when.isJsonArray()) {
                when.getAsJsonArray().forEach(w -> out.add(w.getAsString()));
            } else {
                out.add(when.getAsString());
            }
        }
        return out;
    }

    @Test
    void theNumberDisplaysHaveACasePerDigitAndCell() {
        Set<String> digits = new TreeSet<>(List.of("0", "1", "2", "3", "4", "5", "6", "7", "8", "9"));
        for (String display : List.of("num_a", "num_b")) {
            JsonObject def = json("assets/kaboofcasino/items/gui/cashier/" + display + ".json");
            assertTrue(def.get("oversized_in_gui").getAsBoolean());
            JsonArray cells = def.getAsJsonObject("model").getAsJsonArray("models");
            // ten digit cells, then the three thousands gaps (strings 10..12, "." draws a dot)
            assertEquals(CashierView.CELLS + CashierView.GAPS, cells.size());
            for (int k = 0; k < cells.size(); k++) {
                JsonObject select = cells.get(k).getAsJsonObject();
                assertEquals(k, select.get("index").getAsInt());
                assertEquals(k < CashierView.CELLS ? digits : Set.of("."), whens(select));
            }
        }
        JsonObject marker = json("assets/kaboofcasino/items/gui/cashier/marker.json").getAsJsonObject("model");
        assertEquals(Set.of("0", "1", "2", "3", "4", "5"), whens(marker));
        // what the menu sends
        // ten digit cells from the right, then the three thousands gaps: "1.234" has a dot in the first gap only
        assertEquals(List.of("4", "3", "2", "1", "", "", "", "", "", "", ".", "", ""), CashierView.digits(1234));
        assertEquals(List.of("0", "", "", "", "", "", "", "", "", "", "", "", ""), CashierView.digits(0));
        assertEquals(List.of(".", ".", ""), CashierView.digits(1_234_567).subList(10, 13));
        assertEquals(List.of("", "", ""), CashierView.digits(999).subList(10, 13));
        assertEquals(CashierView.CELLS + CashierView.GAPS, CashierView.digits(9_999_999_999L).size());
        List<String> sent = CashierView.digits(ShopEntry.MAX_PRICE);
        for (String s : sent.subList(0, CashierView.CELLS)) {
            assertTrue(s.isEmpty() || digits.contains(s));
        }
        for (String s : sent.subList(CashierView.CELLS, sent.size())) {
            assertTrue(s.isEmpty() || s.equals("."), "a gap is a dot or nothing");
        }
    }

    @Test
    void theRigsCustomModelStaysInsideTheFootprint() {
        JsonObject screen = json("assets/kaboofcasino/models/cashier/screen.json");
        for (JsonElement e : screen.getAsJsonArray("elements")) {
            JsonArray from = e.getAsJsonObject().getAsJsonArray("from");
            JsonArray to = e.getAsJsonObject().getAsJsonArray("to");
            // anchor cell frame: x 0..32 (both cells), y 16..32 (the upper row), z 0..16
            assertTrue(from.get(0).getAsDouble() >= 0 && to.get(0).getAsDouble() <= 32, e.toString());
            assertTrue(from.get(1).getAsDouble() >= 16 && to.get(1).getAsDouble() <= 32, e.toString());
            assertTrue(from.get(2).getAsDouble() >= 0 && to.get(2).getAsDouble() <= 16, e.toString());
        }
        // and lands there for every facing (the rig's transformation of its pivot)
        for (de.tigges.kaboofcasino.machine.Facing f : de.tigges.kaboofcasino.machine.Facing.values()) {
            for (CashierRig.Part p : CashierRig.PARTS) {
                var t = CashierRig.transformation(f, p);
                var centre = de.tigges.kaboofcasino.visual.RigMath.apply(t, 8, 8, 8);
                var cells = de.tigges.kaboofcasino.machine.MachineType.CASHIER.carriers(
                        new de.tigges.kaboofcasino.util.Pos("w", 0, 0, 0), f);
                boolean inside = cells.stream().anyMatch(c -> centre.x() + 0.5 >= c.x() - 1e-4
                        && centre.x() + 0.5 <= c.x() + 1 + 1e-4 && centre.z() + 0.5 >= c.z() - 1e-4
                        && centre.z() + 0.5 <= c.z() + 1 + 1e-4);
                assertTrue(inside || p.model().endsWith("screen"), f + " " + p + " centre " + centre);
            }
        }
    }

    @Test
    void selectionClicksFollowTheBetSlotsRule() {
        // left: the clicked stack added, right: one more, shift: everything; never more than owned
        assertEquals(64, CashierView.select(CashierView.SelectClick.LEFT, 0, 64, 100));
        assertEquals(100, CashierView.select(CashierView.SelectClick.LEFT, 64, 64, 100));
        assertEquals(65, CashierView.select(CashierView.SelectClick.RIGHT, 64, 64, 100));
        assertEquals(100, CashierView.select(CashierView.SelectClick.SHIFT, 3, 64, 100));
        assertEquals(0, CashierView.select(CashierView.SelectClick.RIGHT, 0, 1, 0));
        assertEquals(12, CashierView.chipSlot(CashierView.SELECTED_FIRST, 1000));
        assertEquals(17, CashierView.chipSlot(CashierView.SELECTED_FIRST, 1));
        assertEquals(25, CashierView.denominationAt(CashierView.TARGET_FIRST, 24));
        assertEquals(0, CashierView.denominationAt(CashierView.TARGET_FIRST, 27));
        assertEquals(1, CashierView.pages(0));
        assertEquals(1, CashierView.pages(36));
        assertEquals(2, CashierView.pages(37));
        // 1287 chips: 1 x 1000, 2 x 100, 3 x 25, 2 x 5, 2 x 1 in the preview row
        assertEquals(List.of(1L, 0L, 2L, 3L, 2L, 2L), java.util.Arrays.stream(CashierView.breakdownRow(1287)).boxed()
                .toList());
        // the change tab's automatic target: one step below the largest chip, 5 for ones
        assertEquals(25, ChangeMenu.effectiveTarget(0, Chips.Selection.of(java.util.Map.of(100, 1L, 1, 3L))));
        assertEquals(5, ChangeMenu.effectiveTarget(0, Chips.Selection.of(java.util.Map.of(1, 30L))));
        assertEquals(500, ChangeMenu.effectiveTarget(0, Chips.Selection.of(java.util.Map.of(1000, 1L))));
        assertEquals(1000, ChangeMenu.effectiveTarget(1000, Chips.Selection.of(java.util.Map.of(5, 1L))));
        assertEquals(0, ChangeMenu.effectiveTarget(0, Chips.Selection.EMPTY));
        // tabs: three slots each across row 0
        assertEquals(List.of(3, 4, 5), java.util.Arrays.stream(CashierView.Tab.CHANGE.slots()).boxed().toList());
    }
}
