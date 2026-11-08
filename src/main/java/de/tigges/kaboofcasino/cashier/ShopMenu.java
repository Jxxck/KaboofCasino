package de.tigges.kaboofcasino.cashier;

import de.tigges.kaboofcasino.CasinoContext;
import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.gui.Button;
import de.tigges.kaboofcasino.gui.DisplayCopies;
import de.tigges.kaboofcasino.wager.Chips;
import de.tigges.kaboofcasino.wager.PlayerAccount;
import io.papermc.paper.datacomponent.DataComponentTypes;
import de.tigges.kaboofcasino.util.ChipText;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/**
 * Preise (PLAN-2 C3): the prizes of {@code shop.items}, 36 per page (slots 9-44), each with its price and today's
 * remaining purchases; a click selects one (glint), KAUFEN buys it once in one tick ({@link Cashbox#buy}: the chips
 * leave, exact or one larger chip and the change back, the prize comes in; refused without room for both). The
 * receipt shows the player's chips and the selected price. A selection is bound to the entry it showed: if the list
 * changed meanwhile (reload, the croupier's hand), the purchase is refused and the page redrawn.
 */
final class ShopMenu extends CashierMenu {

    private int page;
    private int selected = -1;
    private ShopEntry selectedEntry;
    /** Server tick of the last purchase: a second one within {@link #BUY_GAP_TICKS} is refused (a double click). */
    private long lastBuy = Long.MIN_VALUE / 2;
    static final int BUY_GAP_TICKS = 10;

    ShopMenu(UUID viewer, CashierController controller, CasinoContext ctx) {
        super(CashierView.Tab.SHOP, viewer, controller, ctx);
    }

    @Override
    protected void renderTab(Player viewer) {
        Messages m = messages();
        List<ShopEntry> shop = controller.service().shop();
        int pages = CashierView.pages(shop.size());
        page = Math.max(0, Math.min(page, pages - 1));
        if (selected >= shop.size() || selected >= 0 && !shop.get(selected).equals(selectedEntry)) {
            selected = -1;
            selectedEntry = null;
        }
        DailyLimits limits = controller.service().limits(viewer);
        for (int k = 0; k < CashierView.PAGE_SIZE; k++) {
            int index = page * CashierView.PAGE_SIZE + k;
            if (index >= shop.size()) {
                break;
            }
            ShopEntry entry = shop.get(index);
            set(CashierView.PRIZES_FIRST + k, Button.of(prizeIcon(entry, limits, index == selected), (p, click) -> {
                touch();
                selected = index;
                selectedEntry = entry;
                clicked(p);
                render();
            }));
        }
        if (shop.isEmpty()) {
            set(CashierView.PRIZES_FIRST + 13, Button.display(DisplayCopies.of(Material.BARRIER, 1,
                    m.render(Msg.SHOP_EMPTY), List.of())));
        }
        if (pages > 1) {
            List<Component> lore = m.lines(Msg.SHOP_PAGE_LORE);
            Component name = m.render(Msg.SHOP_PAGE, Messages.text("page", Integer.toString(page + 1)),
                    Messages.text("pages", Integer.toString(pages)));
            set(CashierView.PREV, Button.of(DisplayCopies.model("kaboofcasino:gui/cashier/prev", name, lore),
                    (p, click) -> turn(p, -1, pages)));
            set(CashierView.NEXT, Button.of(DisplayCopies.model("kaboofcasino:gui/cashier/next", name, lore),
                    (p, click) -> turn(p, 1, pages)));
        }
        Component prizeName = selectedEntry == null ? Component.empty() : name(selectedEntry);
        commit("buy", selectedEntry != null, m.render(Msg.SHOP_BUY_BUTTON), selectedEntry == null
                ? m.lines(Msg.SHOP_NONE_SELECTED) : m.lines(Msg.SHOP_BUY_LORE, Messages.component("item", prizeName),
                Messages.text("price", ChipText.chips(selectedEntry.price()))), (p, click) -> buy(p));
        number(true, new PlayerAccount(viewer).countPlain(Chips.VALUE));
        if (selectedEntry != null) {
            number(false, selectedEntry.price());
        }
    }

    private Component name(ShopEntry entry) {
        Optional<ItemStack> one = Prizes.one(entry, messages());
        return Component.text(entry.count() + "× ").append(one.map(ItemStack::effectiveName)
                .orElse(Component.text(entry.item())));
    }

    private ItemStack prizeIcon(ShopEntry entry, DailyLimits limits, boolean chosen) {
        Messages m = messages();
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(m.render(Msg.SHOP_PRICE_LORE, Messages.text("price", ChipText.chips(entry.price()))));
        if (entry.count() > 1) {
            lore.add(m.render(Msg.SHOP_COUNT_LORE, Messages.text("count", ChipText.number(entry.count()))));
        }
        if (entry.limit() > 0) {
            lore.add(m.render(Msg.SHOP_LIMIT_LORE, Messages.text("left", ChipText.number(limits.left(entry))),
                    Messages.text("limit", ChipText.number(entry.limit()))));
        }
        Optional<ItemStack> one = Prizes.one(entry, m);
        if (one.isEmpty()) {
            lore.add(m.render(Msg.SHOP_UNAVAILABLE));
            return DisplayCopies.of(Material.BARRIER, 1, Component.text(entry.id(), NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false), lore);
        }
        lore.addAll(m.lines(chosen ? Msg.SHOP_SELECTED_LORE : Msg.SHOP_SELECT_LORE));
        ItemStack icon = DisplayCopies.copyOf(one.get(), entry.count(), lore);
        if (chosen) {
            icon.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        return icon;
    }

    /** The page buttons: one page back or on, wrapping around. */
    private void turn(Player player, int step, int pages) {
        touch();
        page = Math.floorMod(page + step, pages);
        clicked(player);
        render();
    }

    @Override
    public void onBottomClick(Player player, int index, ClickType click, ItemStack stack) {
        touch();
    }

    private void buy(Player player) {
        long now = org.bukkit.Bukkit.getCurrentTick();
        if (now - lastBuy < BUY_GAP_TICKS) {
            return; // a double click: the first one bought, the selection is gone
        }
        if (!controller.mayTransact(player)) {
            return;
        }
        if (selectedEntry == null) {
            messages().actionBar(player, Msg.SHOP_NONE_SELECTED);
            refused(player);
            return;
        }
        List<ShopEntry> shop = controller.service().shop();
        if (selected >= shop.size() || !shop.get(selected).equals(selectedEntry)) {
            messages().actionBar(player, Msg.SHOP_CHANGED);
            refused(player);
            render();
            return;
        }
        Cashbox.Outcome outcome = controller.service().buy(player, controller.machine(), selectedEntry);
        if (outcome instanceof Cashbox.Bought) {
            // one purchase per selection: the next one needs a new click on the prize
            lastBuy = now;
            selected = -1;
            selectedEntry = null;
        }
        if (isOpenFor(player)) {
            render();
        }
    }
}
