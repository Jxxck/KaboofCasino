package de.tigges.kaboofcasino.cashier;

import de.tigges.kaboofcasino.CasinoContext;
import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.gui.Button;
import de.tigges.kaboofcasino.gui.DisplayCopies;
import de.tigges.kaboofcasino.wager.ChipItems;
import de.tigges.kaboofcasino.wager.ItemAmount;
import de.tigges.kaboofcasino.wager.PlayerAccount;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/**
 * Wechseln (PLAN-2 C3): ores from the own inventory into chips. Clicks on the inventory select (left: that stack added,
 * right: one more, shift-left: all of that item; only plain stacks of items with a rate); the tray (slots 9-26) shows
 * the selection, a click there takes it out (right: one less). Slots 30-35 show the chips the exchange gives (largest
 * first), the receipt its value; WECHSELN exchanges in one tick ({@link Cashbox#exchange}).
 */
final class ExchangeMenu extends CashierMenu {

    private final Map<String, Long> selection = new LinkedHashMap<>();

    ExchangeMenu(UUID viewer, CashierController controller, CasinoContext ctx) {
        super(CashierView.Tab.EXCHANGE, viewer, controller, ctx);
    }

    private List<ItemAmount> amounts() {
        List<ItemAmount> out = new ArrayList<>();
        selection.forEach((item, n) -> out.add(new ItemAmount(item, n)));
        return out;
    }

    /** Drops what lost its rate (a reload) or is no longer owned; caps at what the player still has. */
    private void prune(Player player) {
        PlayerAccount account = new PlayerAccount(player);
        for (Iterator<Map.Entry<String, Long>> it = selection.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Long> e = it.next();
            long owned = settings().rate(e.getKey()).isPresent() ? account.countPlain(e.getKey()) : 0;
            if (owned <= 0) {
                it.remove();
            } else if (e.getValue() > owned) {
                e.setValue(owned);
            }
        }
    }

    @Override
    protected void renderTab(Player viewer) {
        prune(viewer);
        Messages m = messages();
        CashierSettings s = settings();
        long value = Math.max(0, CashierMath.value(amounts(), s::rate));
        int slot = CashierView.TRAY_FIRST;
        for (Map.Entry<String, Long> e : selection.entrySet()) {
            String item = e.getKey();
            long count = e.getValue();
            Material material = Material.matchMaterial(item);
            if (material == null) {
                continue;
            }
            ItemStack icon = DisplayCopies.of(material, (int) Math.min(99, count), null, m.lines(
                    Msg.CASHIER_ORE_LORE, Messages.text("count", Long.toString(count)), Messages.text("value",
                            CashierMath.format(count * s.rate(item).orElse(0)))));
            set(slot++, Button.of(icon, (p, click) -> trayClicked(p, item, click)));
        }
        long[] row = CashierView.breakdownRow(value);
        for (int k = 0; k < row.length; k++) {
            if (row[k] > 0) {
                set(CashierView.PREVIEW_FIRST + k, Button.display(chipIcon(CashierView.ROW_DENOMINATIONS[k], row[k],
                        null, m.lines(Msg.CASHIER_CHIPS_LORE, Messages.text("count", Long.toString(row[k]))))));
            }
        }
        set(CashierView.RATES_BOOK, Button.display(ratesBook()));
        clearButton(selection::clear);
        commit("exchange", value > 0, m.render(Msg.CASHIER_EXCHANGE_BUTTON), value > 0
                ? m.lines(Msg.CASHIER_EXCHANGE_LORE, Messages.text("value", CashierMath.format(value)))
                : m.lines(Msg.CASHIER_NOTHING_LORE), (p, click) -> exchange(p));
        number(true, value);
    }

    /** The rates as a book tooltip (the bets book's look): one line per item, then the rules. */
    private ItemStack ratesBook() {
        Messages m = messages();
        List<Component> lore = new ArrayList<>();
        for (CashierSettings.Rate r : settings().rates()) {
            lore.add(m.render(Msg.CASHIER_RATE_LINE, Messages.component("item", CashierService.name(r.item())),
                    Messages.text("value", CashierMath.format(r.value()))));
        }
        lore.addAll(m.lines(Msg.CASHIER_RATES_LORE));
        return DisplayCopies.of(Material.BOOK, 1, m.render(Msg.CASHIER_RATES), lore);
    }

    private void trayClicked(Player player, String item, ClickType click) {
        touch();
        Long n = selection.get(item);
        if (n == null) {
            return;
        }
        if (click == ClickType.RIGHT && n > 1) {
            selection.put(item, n - 1);
        } else {
            selection.remove(item);
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
        Messages m = messages();
        String key = stack.getType().getKey().asString();
        OptionalLong rate = settings().rate(key);
        if (ChipItems.isChip(stack) || rate.isEmpty()) {
            m.actionBar(player, Msg.CASHIER_NOT_ACCEPTED, Messages.component("item", stack.effectiveName()));
            refused(player);
            return;
        }
        if (!PlayerAccount.isPlain(stack)) {
            m.actionBar(player, Msg.CASHIER_NOT_PLAIN);
            refused(player);
            return;
        }
        if (!selection.containsKey(key) && selection.size() >= CashierView.TRAY_SIZE) {
            m.actionBar(player, Msg.CASHIER_TRAY_FULL);
            refused(player);
            return;
        }
        long owned = new PlayerAccount(player).countPlain(key);
        long current = selection.getOrDefault(key, 0L);
        long next = CashierView.select(selectClick(click), current, stack.getAmount(), owned);
        if (next == current || next <= 0) {
            return;
        }
        selection.put(key, next);
        clicked(player);
        render();
    }

    private void exchange(Player player) {
        if (!controller.mayTransact(player)) {
            return;
        }
        prune(player);
        Cashbox.Outcome outcome = controller.service().exchange(player, controller.machine(), amounts());
        if (outcome instanceof Cashbox.Exchanged) {
            selection.clear();
        }
        if (isOpenFor(player)) {
            render();
        }
    }
}
