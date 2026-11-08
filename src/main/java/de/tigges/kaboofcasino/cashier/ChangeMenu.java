package de.tigges.kaboofcasino.cashier;

import de.tigges.kaboofcasino.CasinoContext;
import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.gui.Button;
import de.tigges.kaboofcasino.wager.ChipItems;
import de.tigges.kaboofcasino.wager.Chips;
import de.tigges.kaboofcasino.wager.ItemAmount;
import de.tigges.kaboofcasino.wager.PlayerAccount;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/**
 * Kleingeld (PLAN-2 C3): chips into other denominations, value-neutral. Clicks on the own chips select them (left: that
 * stack added, right: one more, shift-left: all of that denomination), row 1 shows the selection (a click takes it
 * out, right: one less), row 2 the target denomination (the frame marks it; until one is clicked: one step smaller
 * than the largest selected chip, or 5 for ones), row 3 what the change gives (the target as far as it goes, the rest
 * smaller). WECHSELN changes in one tick if everything fits ({@link Cashbox#change}).
 */
final class ChangeMenu extends CashierMenu {

    /** Selected chips per denomination, {@link CashierView#ROW_DENOMINATIONS} order. */
    private final long[] selected = new long[CashierView.ROW_DENOMINATIONS.length];
    /** The clicked target denomination, 0 = automatic. */
    private int target;

    ChangeMenu(UUID viewer, CashierController controller, CasinoContext ctx) {
        super(CashierView.Tab.CHANGE, viewer, controller, ctx);
    }

    Chips.Selection selection() {
        long[] counts = new long[Chips.DENOMINATIONS.length];
        for (int k = 0; k < selected.length; k++) {
            counts[Chips.index(CashierView.ROW_DENOMINATIONS[k])] = selected[k];
        }
        return new Chips.Selection(counts);
    }

    /** The target: the clicked one, else one step below the largest selected chip (5 if only ones), else none. */
    static int effectiveTarget(int clicked, Chips.Selection selection) {
        if (clicked > 0) {
            return clicked;
        }
        int largest = selection.largest();
        if (largest == 0) {
            return 0;
        }
        int i = Chips.index(largest);
        return i > 0 ? Chips.DENOMINATIONS[i - 1] : Chips.DENOMINATIONS[1];
    }

    /** Caps the selection at the chips the player still has. */
    private void prune(Player player) {
        PlayerAccount account = new PlayerAccount(player);
        for (int k = 0; k < selected.length; k++) {
            if (selected[k] > 0) {
                selected[k] = Math.min(selected[k], account.countPlain(Chips.key(CashierView.ROW_DENOMINATIONS[k])));
            }
        }
    }

    @Override
    protected void renderTab(Player viewer) {
        prune(viewer);
        Messages m = messages();
        Chips.Selection selection = selection();
        long value = selection.value();
        int t = effectiveTarget(target, selection);
        for (int k = 0; k < selected.length; k++) {
            int d = CashierView.ROW_DENOMINATIONS[k];
            if (selected[k] > 0) {
                final int row = k;
                set(CashierView.SELECTED_FIRST + k, Button.of(chipIcon(d, selected[k], null, m.lines(
                        Msg.CASHIER_SELECTED_LORE, Messages.text("count", Long.toString(selected[k])))),
                        (p, click) -> selectedClicked(p, row, click)));
            }
            ItemStack icon = chipIcon(d, 1, m.render(Msg.CASHIER_TARGET, Messages.text("denomination",
                    Integer.toString(d))), m.lines(d == t ? Msg.CASHIER_TARGET_CHOSEN : Msg.CASHIER_TARGET_LORE));
            if (d == t) {
                icon.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
            }
            set(CashierView.TARGET_FIRST + k, Button.of(icon, (p, click) -> {
                touch();
                target = d;
                clicked(p);
                render();
            }));
        }
        if (t > 0) {
            int index = 0;
            while (CashierView.ROW_DENOMINATIONS[index] != t) {
                index++;
            }
            set(CashierView.MARKER, Button.display(picture("kaboofcasino:gui/cashier/marker",
                    List.of(Integer.toString(index)))));
        }
        List<ItemAmount> result = value > 0 && t > 0 ? CashierMath.change(value, t) : List.of();
        boolean changes = !result.isEmpty() && !CashierMath.same(selection, result);
        long[] row = CashierView.row(result);
        for (int k = 0; k < row.length; k++) {
            if (row[k] > 0) {
                set(CashierView.RESULT_FIRST + k, Button.display(chipIcon(CashierView.ROW_DENOMINATIONS[k], row[k],
                        null, m.lines(Msg.CASHIER_CHIPS_LORE, Messages.text("count", Long.toString(row[k]))))));
            }
        }
        clearButton(() -> java.util.Arrays.fill(selected, 0));
        commit("exchange", changes, m.render(Msg.CASHIER_EXCHANGE_BUTTON), changes ? m.lines(Msg.CASHIER_CHANGE_LORE,
                Messages.text("value", CashierMath.format(value)), Messages.text("denomination", Integer.toString(t)))
                : m.lines(Msg.CASHIER_NOTHING_LORE), (p, click) -> change(p));
        number(true, value);
    }

    private void selectedClicked(Player player, int row, ClickType click) {
        touch();
        if (click == ClickType.RIGHT && selected[row] > 1) {
            selected[row]--;
        } else {
            selected[row] = 0;
        }
        clicked(player);
        render();
    }

    @Override
    public void onBottomClick(Player player, int index, ClickType click, ItemStack stack) {
        touch();
        if (stack == null || stack.isEmpty()) {
            return;
        }
        OptionalInt d = ChipItems.denomination(stack);
        if (d.isEmpty()) {
            messages().actionBar(player, Msg.CASHIER_NOT_CHIP);
            refused(player);
            return;
        }
        int k = 0;
        while (CashierView.ROW_DENOMINATIONS[k] != d.getAsInt()) {
            k++;
        }
        long owned = new PlayerAccount(player).countPlain(Chips.key(d.getAsInt()));
        long next = CashierView.select(selectClick(click), selected[k], stack.getAmount(), owned);
        if (next == selected[k] || next <= 0) {
            return;
        }
        selected[k] = next;
        clicked(player);
        render();
    }

    private void change(Player player) {
        if (!controller.mayTransact(player)) {
            return;
        }
        prune(player);
        Chips.Selection selection = selection();
        int t = effectiveTarget(target, selection);
        if (selection.isEmpty() || t == 0) {
            messages().actionBar(player, Msg.CASHIER_EMPTY);
            refused(player);
            return;
        }
        Cashbox.Outcome outcome = controller.service().change(player, controller.machine(), selection, t);
        if (outcome instanceof Cashbox.Changed) {
            java.util.Arrays.fill(selected, 0);
        }
        if (isOpenFor(player)) {
            render();
        }
    }
}
