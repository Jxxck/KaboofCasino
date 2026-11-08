package de.tigges.kaboofcasino.admin;

import de.tigges.kaboofcasino.cashier.CashierMath;
import de.tigges.kaboofcasino.cashier.CashierSettings;
import de.tigges.kaboofcasino.cashier.ShopEntry;
import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.kyori.adventure.text.Component;

/**
 * The cashier's shop dialog (the croupier's hand on a cashier, step 9), server-free: per page
 * {@value #PAGE_SIZE} prizes with their item (icon and description), price and limit as text inputs and a "remove"
 * checkbox; below them the item in the admin's off hand (if any) with its own price and limit; buttons Save, Add
 * (only with an off-hand item), the page buttons (only with several pages) and "Fertig" (also ESC). {@link ShopPaper}
 * turns it into a Paper dialog, {@link ShopClick} reads the answer. Pure.
 */
final class ShopDialogs {

    static final int PAGE_SIZE = 6;
    static final int WIDTH = 300;
    static final int INPUT_WIDTH = 140;

    /** One line of the body: a text, or a prize (shown as its item with the description beside it). */
    sealed interface Line permits TextLine, PrizeLine {
        Component text();
    }

    record TextLine(Component text) implements Line {
    }

    record PrizeLine(ShopEntry entry, Component text) implements Line {
    }

    /** A button: label, optional tooltip, the action it sends. */
    record Button(Component label, Component tooltip, int width, ShopClick.Action action) {
    }

    /** The dialog: title, body, inputs (the croupier's-hand input types), buttons, exit button. */
    record View(Component title, List<Line> body, List<AdminView.Input> inputs, List<Button> buttons, Button exit) {
        View {
            body = List.copyOf(body);
            inputs = List.copyOf(inputs);
            buttons = List.copyOf(buttons);
        }
    }

    /**
     * What the dialog shows.
     *
     * @param shop     the whole prize list
     * @param page     the page (clamped)
     * @param newSpec  the off-hand item as a prize text, or null
     * @param newCount its amount (items per purchase)
     * @param names    a prize's display name ("1× Casino-Stuhl")
     */
    record Data(List<ShopEntry> shop, int page, String newSpec, int newCount, Function<ShopEntry, Component> names,
                Component newName) {
        Data {
            shop = List.copyOf(shop);
        }
    }

    private ShopDialogs() {
    }

    static int pages(int entries) {
        return Math.max(1, (entries + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    static int clamp(int page, int entries) {
        return Math.max(0, Math.min(page, pages(entries) - 1));
    }

    /** The prizes of {@code page} (global indices from {@code page * PAGE_SIZE}). */
    static List<ShopEntry> shown(List<ShopEntry> shop, int page) {
        int from = clamp(page, shop.size()) * PAGE_SIZE;
        return List.copyOf(shop.subList(Math.min(from, shop.size()), Math.min(from + PAGE_SIZE, shop.size())));
    }

    static View view(Data d, Messages m) {
        int page = clamp(d.page(), d.shop().size());
        int pages = pages(d.shop().size());
        List<Line> body = new ArrayList<>();
        body.add(new TextLine(m.render(Msg.SHOP_ADMIN_HELP)));
        List<AdminView.Input> inputs = new ArrayList<>();
        List<ShopEntry> shown = shown(d.shop(), page);
        for (int i = 0; i < shown.size(); i++) {
            ShopEntry e = shown.get(i);
            String n = Integer.toString(page * PAGE_SIZE + i + 1);
            body.add(new PrizeLine(e, m.render(Msg.SHOP_ADMIN_ENTRY, Messages.text("index", n),
                    Messages.component("item", d.names().apply(e)), Messages.text("price",
                            CashierMath.format(e.price())), Messages.text("limit", Integer.toString(e.limit())))));
            inputs.add(new AdminView.TextInput(ShopClick.price(i), m.render(Msg.SHOP_ADMIN_PRICE, Messages.text("index",
                    n)), Long.toString(e.price()), ShopClick.PRICE_LENGTH, INPUT_WIDTH));
            inputs.add(new AdminView.TextInput(ShopClick.limit(i), m.render(Msg.SHOP_ADMIN_LIMIT, Messages.text("index",
                    n)), Integer.toString(e.limit()), ShopClick.LIMIT_LENGTH, INPUT_WIDTH));
            inputs.add(new AdminView.BoolInput(ShopClick.remove(i), m.render(Msg.SHOP_ADMIN_REMOVE,
                    Messages.text("index", n)), false));
        }
        if (d.shop().isEmpty()) {
            body.add(new TextLine(m.render(Msg.SHOP_EMPTY)));
        }
        if (pages > 1) {
            body.add(new TextLine(m.render(Msg.SHOP_ADMIN_PAGE, Messages.text("page", Integer.toString(page + 1)),
                    Messages.text("pages", Integer.toString(pages)))));
        }
        boolean withNew = d.newSpec() != null;
        if (withNew) {
            body.add(new TextLine(m.render(Msg.SHOP_ADMIN_NEW, Messages.component("item", d.newName()),
                    Messages.text("count", Integer.toString(d.newCount())))));
            inputs.add(new AdminView.TextInput(ShopClick.NEW_PRICE, m.render(Msg.SHOP_ADMIN_NEW_PRICE), "",
                    ShopClick.PRICE_LENGTH, INPUT_WIDTH));
            inputs.add(new AdminView.TextInput(ShopClick.NEW_LIMIT, m.render(Msg.SHOP_ADMIN_NEW_LIMIT), "0",
                    ShopClick.LIMIT_LENGTH, INPUT_WIDTH));
        } else {
            body.add(new TextLine(m.render(Msg.SHOP_ADMIN_NEW_NONE)));
        }
        List<Button> buttons = new ArrayList<>();
        buttons.add(new Button(m.render(Msg.SHOP_ADMIN_SAVE), null, 150, ShopClick.Action.SAVE));
        if (withNew && d.shop().size() < CashierSettings.MAX_SHOP) {
            buttons.add(new Button(m.render(Msg.SHOP_ADMIN_ADD), null, 150, ShopClick.Action.ADD));
        }
        if (pages > 1) {
            buttons.add(new Button(m.render(Msg.SHOP_ADMIN_PREV), null, 150, ShopClick.Action.PREV));
            buttons.add(new Button(m.render(Msg.SHOP_ADMIN_NEXT), null, 150, ShopClick.Action.NEXT));
        }
        return new View(m.render(Msg.SHOP_ADMIN_TITLE), body, inputs, buttons,
                new Button(m.render(Msg.SHOP_ADMIN_DONE), null, 200, ShopClick.Action.DONE));
    }

    /** What a change did, for the log: one line per prize. */
    record Changes(List<ShopEntry> after, List<String> lines) {
        Changes {
            after = List.copyOf(after);
            lines = List.copyOf(lines);
        }
    }

    /**
     * The list after a click: the page's edits on the prizes it showed ({@code shown}, from {@code page * PAGE_SIZE}),
     * then the off-hand item appended ({@code added} null for none). The caller has checked that the list did not
     * change since the dialog was shown.
     */
    static Changes apply(List<ShopEntry> shop, int page, java.util.Map<Integer, ShopClick.Edit> edits,
                         ShopEntry added) {
        List<ShopEntry> out = new ArrayList<>(shop);
        List<String> lines = new ArrayList<>();
        int from = clamp(page, shop.size()) * PAGE_SIZE;
        List<Integer> removed = new ArrayList<>();
        for (java.util.Map.Entry<Integer, ShopClick.Edit> e : new java.util.TreeMap<>(edits).entrySet()) {
            int index = from + e.getKey();
            if (index >= shop.size()) {
                continue;
            }
            ShopEntry old = shop.get(index);
            ShopClick.Edit edit = e.getValue();
            if (edit.remove()) {
                removed.add(index);
                lines.add("removed " + old.count() + "x " + old.item() + " (price " + old.price() + ", limit "
                        + old.limit() + ")");
                continue;
            }
            ShopEntry next = old.withPrice(edit.price()).withLimit(edit.limit());
            if (!next.equals(old)) {
                out.set(index, next);
                lines.add(old.count() + "x " + old.item() + ": price " + old.price() + " -> " + next.price()
                        + ", limit " + old.limit() + " -> " + next.limit());
            }
        }
        for (int i = removed.size() - 1; i >= 0; i--) {
            out.remove((int) removed.get(i));
        }
        if (added != null) {
            out.add(added);
            lines.add("added " + added.count() + "x " + added.item() + " (price " + added.price() + ", limit "
                    + added.limit() + ")");
        }
        return new Changes(out, lines);
    }
}
