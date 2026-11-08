package de.tigges.kaboofcasino.admin;

import de.tigges.kaboofcasino.chipshop.ChipShopCatalog;
import de.tigges.kaboofcasino.chipshop.ChipShopSettings;
import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.util.ChipText;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.kyori.adventure.text.Component;

/**
 * The Chip-Shop's price dialog (the croupier's hand on a Chip-Shop, step 11), server-free: per page
 * {@value #PAGE_SIZE} items (hidden ones too) with their icon and current price, a price input (empty = automatic from
 * the recipe), a daily limit and "ausblenden"; buttons Speichern, the pages, Fertig (ESC applies valid inputs too).
 * The answer ({@code kaboofcasino:chipshop_prices}, {@code {do:"save|prev|next|done"}} plus the inputs) changes the
 * {@code chipshop.items} overrides of the shown items only. Pure.
 */
final class ChipShopDialogs {

    static final int PAGE_SIZE = 6;
    static final int WIDTH = 300;
    static final int INPUT_WIDTH = 140;
    static final String DO = "do";

    enum Action {
        SAVE, PREV, NEXT, DONE;

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        static Optional<Action> of(String id) {
            for (Action a : values()) {
                if (a.id().equals(id)) {
                    return Optional.of(a);
                }
            }
            return Optional.empty();
        }
    }

    /** A body line: a plain text, or an item row (its icon with the text beside it). */
    record Line(String item, Component text) {
    }

    record Button(Component label, int width, Action action) {
    }

    record View(Component title, List<Line> body, List<AdminView.Input> inputs, List<Button> buttons, Button exit) {
        View {
            body = List.copyOf(body);
            inputs = List.copyOf(inputs);
            buttons = List.copyOf(buttons);
        }
    }

    private ChipShopDialogs() {
    }

    static int pages(int rows) {
        return Math.max(1, (rows + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    static int clamp(int page, int rows) {
        return Math.max(0, Math.min(page, pages(rows) - 1));
    }

    static List<ChipShopCatalog.Row> shown(List<ChipShopCatalog.Row> rows, int page) {
        int from = clamp(page, rows.size()) * PAGE_SIZE;
        return List.copyOf(rows.subList(Math.min(from, rows.size()), Math.min(from + PAGE_SIZE, rows.size())));
    }

    static String price(int i) {
        return "price_" + i;
    }

    static String limit(int i) {
        return "limit_" + i;
    }

    static String hide(int i) {
        return "hide_" + i;
    }

    /** The dialog for a page of {@code rows}; {@code names} gives an item's display name, {@code tab} a tab's. */
    static View view(List<ChipShopCatalog.Row> rows, int page, Function<String, Component> names,
                     Function<ChipShopCatalog.Row, Component> tab, Messages m) {
        int p = clamp(page, rows.size());
        int pages = pages(rows.size());
        List<Line> body = new ArrayList<>();
        body.add(new Line(null, m.render(Msg.CHIPSHOP_ADMIN_HELP)));
        List<AdminView.Input> inputs = new ArrayList<>();
        List<ChipShopCatalog.Row> shown = shown(rows, p);
        if (!shown.isEmpty()) {
            body.add(new Line(null, m.render(Msg.CHIPSHOP_ADMIN_PAGE, Messages.component("category",
                    tab.apply(shown.getFirst())), Messages.text("page", Integer.toString(p + 1)),
                    Messages.text("pages", Integer.toString(pages)))));
        }
        for (int i = 0; i < shown.size(); i++) {
            ChipShopCatalog.Row r = shown.get(i);
            ChipShopSettings.Item o = r.override();
            long price = o.price() > 0 ? o.price() : r.auto().orElse(0);
            body.add(new Line(r.id(), m.render(Msg.CHIPSHOP_ADMIN_ENTRY, Messages.component("item",
                    names.apply(r.id())), Messages.text("price", price > 0 ? ChipText.chips(price) : "–"),
                    Messages.component("auto", o.price() > 0 ? Component.empty() : m.render(Msg.CHIPSHOP_ADMIN_AUTO)),
                    Messages.text("limit", ChipText.number(o.limit())), Messages.component("hidden", o.hidden()
                            ? m.render(Msg.CHIPSHOP_ADMIN_HIDDEN) : Component.empty()))));
            inputs.add(new AdminView.TextInput(price(i), Component.text((i + 1) + ". ").append(m.render(
                    Msg.CHIPSHOP_ADMIN_PRICE)), o.price() > 0 ? Long.toString(o.price()) : "", 9, INPUT_WIDTH));
            inputs.add(new AdminView.TextInput(limit(i), Component.text((i + 1) + ". ").append(m.render(
                    Msg.CHIPSHOP_ADMIN_LIMIT)), Integer.toString(o.limit()), 5, INPUT_WIDTH));
            inputs.add(new AdminView.BoolInput(hide(i), Component.text((i + 1) + ". ").append(m.render(
                    Msg.CHIPSHOP_ADMIN_HIDE)), o.hidden()));
        }
        List<Button> buttons = new ArrayList<>();
        buttons.add(new Button(m.render(Msg.CHIPSHOP_ADMIN_SAVE), 150, Action.SAVE));
        if (pages > 1) {
            buttons.add(new Button(m.render(Msg.CHIPSHOP_ADMIN_PREV), 150, Action.PREV));
            buttons.add(new Button(m.render(Msg.CHIPSHOP_ADMIN_NEXT), 150, Action.NEXT));
        }
        return new View(m.render(Msg.CHIPSHOP_ADMIN_TITLE), body, inputs, buttons,
                new Button(m.render(Msg.CHIPSHOP_ADMIN_DONE), 200, Action.DONE));
    }

    /**
     * The overrides of the shown rows from the answer, or empty if any input is invalid (a price is empty or
     * 1..MAX_PRICE, a limit 0..MAX_LIMIT); missing inputs keep the row as it was.
     */
    static Optional<List<ChipShopSettings.Item>> parse(List<ChipShopCatalog.Row> shown,
                                                      Function<String, String> text, Function<String, Boolean> bool) {
        List<ChipShopSettings.Item> out = new ArrayList<>();
        for (int i = 0; i < shown.size(); i++) {
            ChipShopCatalog.Row r = shown.get(i);
            String p = text.apply(price(i));
            String l = text.apply(limit(i));
            Boolean h = bool.apply(hide(i));
            long price = r.override().price();
            int limit = r.override().limit();
            try {
                if (p != null) {
                    price = p.isBlank() ? 0 : Long.parseLong(p.strip());
                }
                if (l != null) {
                    limit = l.isBlank() ? 0 : Integer.parseInt(l.strip());
                }
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
            if (price < 0 || price > ChipShopSettings.MAX_PRICE || limit < 0 || limit > ChipShopSettings.MAX_LIMIT) {
                return Optional.empty();
            }
            out.add(new ChipShopSettings.Item(r.id(), price, limit, h != null ? h : r.override().hidden()));
        }
        return Optional.of(out);
    }

    /** The changes for the log: one line per changed item ("id: price 0 -> 500, limit 0 -> 1, hidden"). */
    static List<String> changes(List<ChipShopCatalog.Row> shown, List<ChipShopSettings.Item> after) {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < shown.size(); i++) {
            ChipShopSettings.Item a = shown.get(i).override();
            ChipShopSettings.Item b = after.get(i);
            if (a.price() != b.price() || a.limit() != b.limit() || a.hidden() != b.hidden()) {
                lines.add(b.item() + ": price " + a.price() + " -> " + b.price() + ", limit " + a.limit() + " -> "
                        + b.limit() + ", hidden " + a.hidden() + " -> " + b.hidden());
            }
        }
        return lines;
    }

    /** The whole {@code chipshop.items} list after the change (empty overrides dropped, order kept). */
    static List<ChipShopSettings.Item> merged(List<ChipShopSettings.Item> before, List<ChipShopSettings.Item> edited) {
        Map<String, ChipShopSettings.Item> all = new LinkedHashMap<>();
        before.forEach(i -> all.put(i.item(), i));
        edited.forEach(i -> all.put(i.item(), i));
        return all.values().stream().filter(i -> !i.empty()).toList();
    }
}
