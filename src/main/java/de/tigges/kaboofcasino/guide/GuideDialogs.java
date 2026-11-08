package de.tigges.kaboofcasino.guide;

import de.tigges.kaboofcasino.guide.GuideBook.Arrow;
import de.tigges.kaboofcasino.guide.GuideBook.Chips;
import de.tigges.kaboofcasino.guide.GuideBook.Entity;
import de.tigges.kaboofcasino.guide.GuideBook.Item;
import de.tigges.kaboofcasino.guide.GuideBook.Label;
import de.tigges.kaboofcasino.guide.GuideBook.Page;
import de.tigges.kaboofcasino.guide.GuideBook.Preview;
import de.tigges.kaboofcasino.guide.GuideBook.Recipe;
import de.tigges.kaboofcasino.guide.GuideBook.Rule;
import de.tigges.kaboofcasino.guide.GuideBook.Scene;
import de.tigges.kaboofcasino.guide.GuideBook.Tab;
import de.tigges.kaboofcasino.guide.GuideBook.Text;
import de.tigges.kaboofcasino.guide.GuideBook.Tile;
import de.tigges.kaboofcasino.guide.GuideBook.Title;
import de.tigges.kaboofcasino.guide.GuideBook.Widget;
import de.tigges.kaboofcasino.guide.GuideView.Body;
import de.tigges.kaboofcasino.guide.GuideView.Button;
import de.tigges.kaboofcasino.guide.GuideView.ItemBody;
import de.tigges.kaboofcasino.guide.GuideView.TextBody;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Turns a spread of the guide into a dialog ({@link GuideView}); PLAN decision 6, render.md 5.3. One dialog per
 * spread, left page then right page in the mod's widget order. Every mod text is a translatable component with the
 * mod's German text as fallback, so the pack's language files apply.
 *
 * <ul>
 *   <li>title: bold gold text; label and text: in the mod's colour (gold, cream, muted), labels bold where the mod
 *       makes them bold;</li>
 *   <li>item: an item body; labels or texts the mod draws right beside it become its description, otherwise its
 *       name (the mod shows it on hover only), as a link if the item has an {@code index} entry for another spread
 *       (the mod jumps there on a click);</li>
 *   <li>tile (a button to another spread): an item body with the tile's icon and its label (or the item's name) as a
 *       link;</li>
 *   <li>recipe: an item body with the result (its count on the icon) and as description "Rezept" or the mod's
 *       "Formloses Rezept" with the result's name, then for a shaped recipe the grid (one digit per ingredient, "-"
 *       empty, empty edge rows and columns trimmed: the shape may go anywhere in the crafting grid) and the legend,
 *       for a shapeless one the ingredients with their counts; ingredients with an index entry elsewhere are links;
 *       a tag shows its first member plus "(beliebige Sorte)";</li>
 *   <li>scene (3D): an item body for each main item ({@link GuideBook#sceneItems}); the croupier hiring scene and an
 *       entity become a villager spawn egg named "Dorfbewohner" or "Croupier";</li>
 *   <li>arrow: "↓"; rule: a struck-through line; chips and the hover preview: nothing (every variant is shown).</li>
 * </ul>
 * A navigation line on top ("« Vorherige Seite  12 / 34  Nächste Seite »", links), the licence line on the cover.
 * Buttons: the four bookmarks (the open chapter's in gold), then "« Vorherige Seite", "Inhalt" (back along the
 * links, only while there is a way back, as the mod) and "Nächste Seite »"; "Fertig" is the exit button (also ESC).
 * Pure.
 */
public final class GuideDialogs {

    static final int TEXT_WIDTH = 250;
    static final int NAV_WIDTH = 300;
    static final int DESCRIPTION_WIDTH = 230;
    static final int ICON = 16;
    static final int BUTTON_WIDTH = 100;
    static final int EXIT_WIDTH = 150;
    /** The mod's frame colour ({@code CasinoGuideScreen.FRAME}), used for the rule. */
    static final TextColor FRAME = TextColor.color(0xA77A37);
    static final String VILLAGER_ICON = "minecraft:villager_spawn_egg";
    static final String CROUPIER_KEY = "entity.roulettecasino.villager.croupier";
    static final String VILLAGER_KEY = "entity.minecraft.villager";

    private static final TextColor GOLD = TextColor.color(GuideBook.Color.GOLD.rgb());
    private static final TextColor CREAM = TextColor.color(GuideBook.Color.CREAM.rgb());
    private static final TextColor MUTED = TextColor.color(GuideBook.Color.MUTED.rgb());

    private final GuideBook book;

    public GuideDialogs(GuideBook book) {
        this.book = book;
    }

    public GuideBook book() {
        return book;
    }

    /**
     * The dialog of spread {@code number}.
     *
     * @param history true while "Inhalt" leads back somewhere
     */
    public GuideView view(int number, boolean history, GuideTexts texts) {
        Page page = book.page(number);
        List<Body> body = new ArrayList<>();
        body.add(new TextBody(navigation(number, texts), NAV_WIDTH));
        List<Widget> widgets = page.widgets();
        for (int i = 0; i < widgets.size(); i++) {
            Widget w = widgets.get(i);
            List<String> shown = new ArrayList<>(2);
            switch (w) {
                case Title t -> {
                    body.add(text(tr(t.key()).color(GOLD).decorate(TextDecoration.BOLD)));
                    shown.add(t.key());
                }
                case Label l -> {
                    body.add(text(styled(l)));
                    shown.add(l.key());
                }
                case Text t -> {
                    body.add(text(tr(t.key()).color(color(t.color()))));
                    shown.add(t.key());
                }
                case Item item -> {
                    List<Component> beside = new ArrayList<>();
                    while (i + 1 < widgets.size() && beside(item, widgets.get(i + 1))) {
                        Widget next = widgets.get(++i);
                        beside.add(next instanceof Label l ? styled(l)
                                : tr(((Text) next).key()).color(color(((Text) next).color())));
                        shown.add(next instanceof Label l ? l.key() : ((Text) next).key());
                    }
                    Component description = !beside.isEmpty() ? Component.join(JoinConfiguration.newlines(), beside)
                            : indexLink(item.ref(), number, texts);
                    body.add(item(item.ref(), item.count(),
                            description != null ? description : name(item.ref(), texts).color(CREAM)));
                }
                case Tile t -> body.add(item(t.ref(), 1, link(t.key() != null ? tr(t.key()) : name(t.ref(), texts),
                        GuideAction.link(t.page()), texts)));
                case Recipe r -> body.add(item(r.result(), r.count(), recipe(r, number, texts)));
                case Scene s -> {
                    if (s.hiring()) {
                        body.add(entity(true));
                    }
                    for (String id : s.items()) {
                        body.add(item(id, 1, null));
                    }
                }
                case Entity e -> body.add(entity(e.croupier()));
                case Arrow a -> body.add(text(Component.text("↓", GOLD)));
                case Rule r -> body.add(text(Component.text(" ".repeat(36), FRAME)
                        .decorate(TextDecoration.STRIKETHROUGH)));
                case Chips c -> {
                    // every variant is on the page; nothing to choose
                }
                case Preview p -> {
                    // no hover preview in a dialog
                }
            }
            for (String key : shown) {
                Component note = key == null ? null : texts.notes().get(key);
                if (note != null) {
                    body.add(text(note)); // the plugin's note right under the mod text it amends
                }
            }
        }
        if (number == 1) {
            body.add(new TextBody(texts.credits(), TEXT_WIDTH));
        }
        List<Button> buttons = new ArrayList<>();
        for (Tab tab : book.tabs()) {
            Component label = tr(tab.key());
            if (tab.label().equals(page.chapter())) {
                label = label.color(GOLD); // not bold: "Böden und Wände" must fit the 100 px button
            }
            buttons.add(new Button(label, null, BUTTON_WIDTH, GuideAction.tab(tab.page())));
        }
        if (number > 1) {
            buttons.add(new Button(previousLabel(), null, BUTTON_WIDTH, GuideAction.turn(number - 1)));
        }
        if (history) {
            buttons.add(new Button(tr("guide.roulettecasino.contents"), null, BUTTON_WIDTH, GuideAction.back()));
        }
        if (number < book.size()) {
            buttons.add(new Button(nextLabel(), null, BUTTON_WIDTH, GuideAction.turn(number + 1)));
        }
        Button exit = new Button(tr("guide.roulettecasino.close"), null, EXIT_WIDTH, GuideAction.close());
        return new GuideView(number, tr("item.roulettecasino.casino_guide"), body, buttons, exit);
    }

    // ---- pieces ----------------------------------------------------------------------------------------------------

    /** "« Vorherige Seite   12 / 34   Nächste Seite »" with the two ends as links. */
    private Component navigation(int number, GuideTexts texts) {
        List<Component> parts = new ArrayList<>();
        if (number > 1) {
            parts.add(link(previousLabel(), GuideAction.turn(number - 1), texts));
        }
        parts.add(Component.text(number + " / " + book.size(), MUTED));
        if (number < book.size()) {
            parts.add(link(nextLabel(), GuideAction.turn(number + 1), texts));
        }
        return Component.join(JoinConfiguration.separator(Component.text("   ")), parts);
    }

    private Component previousLabel() {
        return Component.text("« ").append(tr("guide.roulettecasino.previous"));
    }

    private Component nextLabel() {
        return tr("guide.roulettecasino.next").append(Component.text(" »"));
    }

    /** True if {@code w} is a label or text the mod draws right beside {@code item} (its description). */
    static boolean beside(Item item, Widget w) {
        if (!(w instanceof Label) && !(w instanceof Text) || w.right() != item.right()) {
            return false;
        }
        return w.x() >= item.x() + Math.min(item.size(), ICON) && w.y() >= item.y() - 4
                && w.y() <= item.y() + item.size() + 2;
    }

    private Component styled(Label l) {
        Component c = tr(l.key()).color(color(l.color()));
        return l.bold() ? c.decorate(TextDecoration.BOLD) : c;
    }

    private Component indexLink(String id, int page, GuideTexts texts) {
        OptionalInt target = book.indexPage(id);
        return target.isPresent() && target.getAsInt() != page
                ? link(name(id, texts), GuideAction.link(target.getAsInt()), texts) : null;
    }

    private Body entity(boolean croupier) {
        Component name = croupier ? tr(CROUPIER_KEY) : Component.translatable(VILLAGER_KEY);
        return item(VILLAGER_ICON, 1, name.color(CREAM));
    }

    /** The recipe description: label and result, then the grid and legend (shaped) or the ingredient counts. */
    Component recipe(Recipe r, int page, GuideTexts texts) {
        List<Component> lines = new ArrayList<>();
        Component label = r.shapeless() ? tr("guide.roulettecasino.expansion.shapeless") : texts.recipe();
        lines.add(Component.text().append(label.color(GOLD)).append(Component.text(" · ", MUTED))
                .append(name(r.result(), texts).color(CREAM)).build());
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String cell : r.cells()) {
            if (cell != null) {
                counts.merge(cell, 1, Integer::sum);
            }
        }
        List<String> ingredients = List.copyOf(counts.keySet());
        if (r.shapeless()) {
            for (String ingredient : ingredients) {
                lines.add(Component.text().append(Component.text(counts.get(ingredient) + "× ", GOLD))
                        .append(ingredient(ingredient, page, texts)).build());
            }
        } else {
            for (String row : grid(r.cells(), ingredients)) {
                lines.add(gridRow(row));
            }
            for (int i = 0; i < ingredients.size(); i++) {
                lines.add(Component.text().append(Component.text((i + 1) + " ", GOLD))
                        .append(ingredient(ingredients.get(i), page, texts)).build());
            }
        }
        return Component.join(JoinConfiguration.newlines(), lines);
    }

    /**
     * The shape as rows of digits (1 = first ingredient, row by row) and "-" for empty cells, without empty edge rows
     * and columns.
     */
    static List<String> grid(List<String> cells, List<String> ingredients) {
        int top = 3;
        int bottom = -1;
        int left = 3;
        int rightmost = -1;
        for (int i = 0; i < 9; i++) {
            if (cells.get(i) != null) {
                top = Math.min(top, i / 3);
                bottom = Math.max(bottom, i / 3);
                left = Math.min(left, i % 3);
                rightmost = Math.max(rightmost, i % 3);
            }
        }
        List<String> rows = new ArrayList<>();
        for (int row = top; row <= bottom; row++) {
            StringBuilder s = new StringBuilder();
            for (int col = left; col <= rightmost; col++) {
                String cell = cells.get(row * 3 + col);
                if (col > left) {
                    s.append(' ');
                }
                s.append(cell == null ? "-" : Integer.toString(ingredients.indexOf(cell) + 1));
            }
            rows.add(s.toString());
        }
        return rows;
    }

    /** Digits gold, "-" muted (digits and "-" have the same 6 px advance, so the rows line up). */
    private static Component gridRow(String row) {
        var out = Component.text();
        for (char c : row.toCharArray()) {
            out.append(Component.text(c, c == '-' ? MUTED : c == ' ' ? CREAM : GOLD));
        }
        return out.build();
    }

    private Component ingredient(String ref, int page, GuideTexts texts) {
        if (ref.startsWith("#")) {
            return name(GuideBook.shown(ref), texts).color(CREAM).append(texts.anyKind().color(MUTED));
        }
        Component link = indexLink(ref, page, texts);
        return link != null ? link : name(ref, texts).color(CREAM);
    }

    /** A clickable text: gold, underlined, hover "Seite n". */
    private Component link(Component text, GuideAction action, GuideTexts texts) {
        return text.color(GOLD).decorate(TextDecoration.UNDERLINED).clickEvent(action.click())
                .hoverEvent(HoverEvent.showText(texts.gotoPage().apply(action.page())));
    }

    /** The name of an item: the mod's key with German fallback, or the vanilla name. */
    Component name(String id, GuideTexts texts) {
        String key = book.nameKey(id);
        return key != null ? tr(key) : texts.vanillaName().apply(id);
    }

    /** A mod text: translatable with the mod's German text as fallback. */
    Component tr(String key) {
        String german = book.german(key);
        return german != null ? Component.translatable(key, german) : Component.translatable(key);
    }

    private static TextColor color(GuideBook.Color c) {
        return TextColor.color(c.rgb());
    }

    private static Body text(Component c) {
        return new TextBody(c, TEXT_WIDTH);
    }

    private static Body item(String ref, int count, Component description) {
        return new ItemBody(GuideBook.shown(ref), count, description, DESCRIPTION_WIDTH, ICON, ICON);
    }
}
