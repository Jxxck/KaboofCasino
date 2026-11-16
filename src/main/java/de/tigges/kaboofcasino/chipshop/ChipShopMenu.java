package de.tigges.kaboofcasino.chipshop;

import de.tigges.kaboofcasino.CasinoContext;
import de.tigges.kaboofcasino.cashier.Cashbox;
import de.tigges.kaboofcasino.cashier.CashierView;
import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.game.GameMenu;
import de.tigges.kaboofcasino.game.MachineController;
import de.tigges.kaboofcasino.gui.Button;
import de.tigges.kaboofcasino.gui.DisplayCopies;
import de.tigges.kaboofcasino.gui.GuiLayout;
import de.tigges.kaboofcasino.gui.TitleBuilder;
import de.tigges.kaboofcasino.util.ChipText;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/**
 * One tab of the Chip-Shop (9x6 chest, the cashier's layout; the title is the art): the seven category tabs in slots 0-6,
 * the tab's articles in 9-44 (36 a page), pages 45/46, the amount toggle 47 (one piece or a stack), KAUFEN 48-51, the
 * price of the choice (52) and the player's chips (53) in the side panel. A click on an article chooses it; KAUFEN
 * buys the chosen amount, Umschalt-click on KAUFEN always a stack; one purchase per 10 ticks (double clicks). Thread:
 * MAIN.
 */
final class ChipShopMenu extends GameMenu {

    static final TextColor BRAND = TextColor.color(0xEACD83);
    static final TextColor CREAM = TextColor.color(0xF3E7BF);
    static final TextColor MUTED = TextColor.color(0xB5C5A6);
    static final String BLANK = "kaboofcasino:gui/blank";
    static final int QTY = 47;
    static final int BUY_GAP_TICKS = 10;

    private final ChipShopController controller;
    private final CasinoContext ctx;
    private final ChipShopCategory category;
    private long lastAction;
    private long lastBuy = Long.MIN_VALUE / 2;
    private int page;
    private boolean stack;
    private ChipShopCatalog.Entry chosen;
    /** A purchase above {@link #CONFIRM_ABOVE} waiting for its second click: what, how many, the total, since when. */
    private Pending pending;

    /** Purchases above this total (chips) need a second click within {@link #CONFIRM_TICKS}. */
    static final long CONFIRM_ABOVE = 1000;
    static final int CONFIRM_TICKS = 60;

    record Pending(String id, int amount, long total, long tick) {
    }

    ChipShopMenu(ChipShopCategory category, UUID viewer, ChipShopController controller, CasinoContext ctx) {
        super(viewer, title(ctx.layout(), ctx.messages().get()::raw, category));
        this.category = category;
        this.controller = controller;
        this.ctx = ctx;
        this.lastAction = Bukkit.getCurrentTick();
    }

    /** The title: background, the active tab's overlay, brand, the tab's name, help lines and the two labels. */
    static Component title(GuiLayout layout, Function<Msg, String> raw, ChipShopCategory category) {
        TitleBuilder t = new TitleBuilder(layout);
        t.art("chipshop_bg_0");
        t.art("chipshop_bg_1");
        t.art("chipshop_overlay_" + category.ordinal());
        t.text("chipshop_brand", raw.apply(Msg.CHIPSHOP_BRAND), BRAND, true, true);
        t.text("chipshop_head", raw.apply(categoryMsg(category)), CREAM, false, true);
        String[] help = raw.apply(Msg.CHIPSHOP_HELP).split("\n", -1);
        for (int i = 0; i < Math.min(help.length, 5); i++) {
            if (!help[i].isBlank()) {
                t.text("chipshop_help_" + i, help[i].strip(), MUTED, false, false);
            }
        }
        t.text("chipshop_label_a", raw.apply(Msg.CHIPSHOP_LABEL_BALANCE), CREAM, false, true);
        t.text("chipshop_label_b", raw.apply(Msg.CHIPSHOP_LABEL_PRICE), CREAM, false, true);
        return t.build();
    }

    static Msg categoryMsg(ChipShopCategory c) {
        return switch (c) {
            case FLOORS -> Msg.CHIPSHOP_CAT_FLOORS;
            case FURNITURE -> Msg.CHIPSHOP_CAT_FURNITURE;
            case BAR -> Msg.CHIPSHOP_CAT_BAR;
            case LIGHTS -> Msg.CHIPSHOP_CAT_LIGHTS;
            case TECH -> Msg.CHIPSHOP_CAT_TECH;
            case MACHINES -> Msg.CHIPSHOP_CAT_MACHINES;
            case OUTFITS -> Msg.CHIPSHOP_CAT_OUTFITS;
        };
    }

    @Override
    public MachineController controller() {
        return controller;
    }

    long lastAction() {
        return lastAction;
    }

    private void touch() {
        lastAction = Bukkit.getCurrentTick();
    }

    void stack(boolean stack) {
        this.stack = stack;
    }

    @Override
    public boolean closesWhenIdle() {
        return false;
    }

    private Messages messages() {
        return ctx.messages().get();
    }

    @Override
    public void render() {
        clear();
        Messages m = messages();
        ChipShopCatalog.Catalog catalog = controller.service().catalog();
        for (ChipShopCategory c : ChipShopCategory.values()) {
            ItemStack icon = controller.service().one(c.icon()).map(i -> DisplayCopies.copyOf(i, 1,
                    m.lines(Msg.CHIPSHOP_TAB_LORE))).orElse(DisplayCopies.model(BLANK, null, List.of()));
            icon.setData(DataComponentTypes.ITEM_NAME, m.render(categoryMsg(c)));
            if (c == category) {
                icon.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
                set(c.ordinal(), Button.display(icon));
            } else {
                set(c.ordinal(), Button.of(icon, (p, click) -> {
                    touch();
                    p.playSound(p, Sound.UI_BUTTON_CLICK, 0.4f, 1.2f);
                    controller.show(p, c, stack);
                }));
            }
        }
        Player viewer = Bukkit.getPlayer(viewer());
        if (viewer == null) {
            return;
        }
        List<ChipShopCatalog.Entry> items = catalog.of(category);
        int pages = CashierView.pages(items.size());
        page = Math.max(0, Math.min(page, pages - 1));
        if (chosen != null && catalog.entry(chosen.id()).filter(e -> e.equals(chosen)).isEmpty()) {
            chosen = null;
        }
        for (int k = 0; k < CashierView.PAGE_SIZE; k++) {
            int index = page * CashierView.PAGE_SIZE + k;
            if (index >= items.size()) {
                break;
            }
            ChipShopCatalog.Entry e = items.get(index);
            set(CashierView.PRIZES_FIRST + k, Button.of(icon(viewer, e, e.equals(chosen)), (p, click) -> {
                touch();
                chosen = e;
                pending = null;
                p.playSound(p, Sound.UI_BUTTON_CLICK, 0.4f, 1.2f);
                render();
            }));
        }
        if (items.isEmpty()) {
            set(CashierView.PRIZES_FIRST + 13, Button.display(DisplayCopies.of(Material.BARRIER, 1,
                    m.render(Msg.CHIPSHOP_EMPTY), List.of())));
        }
        if (pages > 1) {
            List<Component> lore = m.lines(Msg.CHIPSHOP_PAGE_LORE);
            Component name = m.render(Msg.CHIPSHOP_PAGE, Messages.text("page", Integer.toString(page + 1)),
                    Messages.text("pages", Integer.toString(pages)));
            set(CashierView.PREV, Button.of(DisplayCopies.model("kaboofcasino:gui/cashier/prev", name, lore),
                    (p, click) -> turn(p, -1, pages)));
            set(CashierView.NEXT, Button.of(DisplayCopies.model("kaboofcasino:gui/cashier/next", name, lore),
                    (p, click) -> turn(p, 1, pages)));
        }
        int stackAmount = chosen == null ? 0 : amount(chosen, true);
        boolean showStack = stack && stackAmount != 1; // a piece of clothing has no stack: the toggle says 1
        set(QTY, Button.of(DisplayCopies.model(showStack ? "kaboofcasino:gui/chipshop/qty_stack"
                : "kaboofcasino:gui/chipshop/qty_one", m.render(showStack ? Msg.CHIPSHOP_QTY_STACK
                : Msg.CHIPSHOP_QTY_ONE), m.lines(Msg.CHIPSHOP_QTY_LORE)), (p, click) -> {
                    touch();
                    stack = !stack;
                    pending = null;
                    p.playSound(p, Sound.UI_BUTTON_CLICK, 0.4f, 1.2f);
                    render();
                }));
        int amount = chosen == null ? 0 : amount(chosen, stack);
        Component name = chosen == null ? Component.empty() : ChipShopService.name(controller.service()
                .one(chosen.id()), chosen.id());
        List<Component> buyLore = new ArrayList<>();
        if (chosen == null) {
            buyLore.addAll(m.lines(Msg.CHIPSHOP_NONE_SELECTED));
        } else {
            buyLore.addAll(m.lines(Msg.CHIPSHOP_BUY_LORE, Messages.text("count", ChipText.number(amount)),
                    Messages.component("item", name), Messages.text("price", ChipText.chips(chosen.total(amount)))));
            if (stackAmount > 1) {
                buyLore.addAll(m.lines(Msg.CHIPSHOP_BUY_SHIFT_LORE, Messages.text("count",
                        ChipText.number(stackAmount)), Messages.text("price", ChipText.chips(chosen.total(stackAmount)))));
            }
        }
        Component buyName = pending != null && confirmable(Bukkit.getCurrentTick())
                ? m.render(Msg.CHIPSHOP_CONFIRM, Messages.text("price", ChipText.chips(pending.total())))
                : m.render(Msg.CHIPSHOP_BUY_BUTTON);
        for (int slot : CashierView.COMMIT) {
            String art = chosen != null ? "kaboofcasino:gui/cashier/buy_on" : "kaboofcasino:gui/cashier/buy_off";
            ItemStack icon = DisplayCopies.model(slot == CashierView.COMMIT_ART ? art : BLANK, buyName, buyLore);
            set(slot, Button.commitWithShift(icon, (p, click) -> {
                touch();
                buy(p, click.isShiftClick());
            }));
        }
        number(true, ChipShopService.balance(viewer));
        if (chosen != null) {
            number(false, chosen.total(amount));
        }
    }

    /** True while the pending purchase may still be confirmed. */
    private boolean confirmable(long now) {
        return pending != null && now - pending.tick() <= CONFIRM_TICKS;
    }

    /** Every tick from the controller: a confirmation that ran out turns the button back. */
    void tick(long now) {
        if (pending != null && !confirmable(now)) {
            pending = null;
            Player viewer = Bukkit.getPlayer(viewer());
            if (viewer != null && isOpenFor(viewer)) {
                render();
            }
        }
    }

    /** One piece, or a stack of the item (its stack size; 1 for items that do not stack). */
    private int amount(ChipShopCatalog.Entry e, boolean asStack) {
        if (!asStack) {
            return 1;
        }
        return controller.service().one(e.id()).map(ItemStack::getMaxStackSize).orElse(1);
    }

    private ItemStack icon(Player viewer, ChipShopCatalog.Entry e, boolean isChosen) {
        Messages m = messages();
        Optional<ItemStack> one = controller.service().one(e.id());
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(m.render(Msg.CHIPSHOP_PRICE_LORE, Messages.text("price", ChipText.chips(e.total(1)))));
        int max = one.map(ItemStack::getMaxStackSize).orElse(1);
        if (max > 1) {
            lore.add(m.render(Msg.CHIPSHOP_STACK_LORE, Messages.text("count", ChipText.number(max)),
                    Messages.text("price", ChipText.chips(e.total(max)))));
        }
        if (e.limit() > 0) {
            lore.add(m.render(Msg.CHIPSHOP_LIMIT_LORE, Messages.text("left", ChipText.number(
                    controller.service().left(viewer, e))), Messages.text("limit", ChipText.number(e.limit()))));
        }
        if (one.isEmpty()) {
            lore.add(m.render(Msg.CHIPSHOP_UNAVAILABLE));
            return DisplayCopies.of(Material.BARRIER, 1, Component.text(e.id()), lore);
        }
        lore.addAll(m.lines(isChosen ? Msg.CHIPSHOP_SELECTED_LORE : Msg.CHIPSHOP_SELECT_LORE));
        ItemStack icon = DisplayCopies.copyOf(one.get(), 1, lore);
        if (isChosen) {
            icon.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        return icon;
    }

    private void turn(Player player, int step, int pages) {
        touch();
        page = Math.floorMod(page + step, pages);
        player.playSound(player, Sound.UI_BUTTON_CLICK, 0.4f, 1.2f);
        render();
    }

    @Override
    public void onBottomClick(Player player, int index, ClickType click, ItemStack stack) {
        touch();
    }

    private void buy(Player player, boolean shift) {
        long now = Bukkit.getCurrentTick();
        if (now - lastBuy < BUY_GAP_TICKS) {
            return; // a double click
        }
        if (!controller.mayTransact(player)) {
            return;
        }
        if (chosen == null) {
            messages().actionBar(player, Msg.CHIPSHOP_NONE_SELECTED);
            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
            return;
        }
        Optional<ChipShopCatalog.Entry> current = controller.service().catalog().entry(chosen.id());
        if (current.isEmpty() || !current.get().equals(chosen)) {
            // a reload or the croupier's hand changed price, limit or offer since it was shown
            messages().actionBar(player, Msg.CHIPSHOP_CHANGED);
            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
            chosen = null;
            render();
            return;
        }
        int amount = amount(chosen, shift || stack);
        long total = chosen.total(amount);
        boolean armed = confirmable(now) && pending.id().equals(chosen.id()) && pending.amount() == amount;
        if (armed && now - pending.tick() < BUY_GAP_TICKS) {
            return; // the second half of a double click confirms nothing
        }
        if (total > CONFIRM_ABOVE && !armed) {
            // a big purchase: the first click only arms it, a second click within 3 s buys
            pending = new Pending(chosen.id(), amount, total, now);
            messages().actionBar(player, Msg.CHIPSHOP_CONFIRM_HINT, Messages.text("price", ChipText.chips(total)));
            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 1.4f);
            render();
            return;
        }
        pending = null;
        Cashbox.Outcome outcome = controller.service().buy(player, controller.machine(), chosen, amount);
        if (outcome instanceof Cashbox.Bought) {
            lastBuy = now;
        }
        if (isOpenFor(player)) {
            render();
        }
    }

    /** A number in the side panel: the player's chips (slot 53) or the price (slot 52), the cashier's digit art. */
    private void number(boolean balance, long value) {
        ItemStack stack = DisplayCopies.model(balance ? "kaboofcasino:gui/cashier/num_a"
                : "kaboofcasino:gui/cashier/num_b", null, List.of());
        CustomModelData.Builder data = CustomModelData.customModelData();
        CashierView.digits(value).forEach(data::addString);
        stack.setData(DataComponentTypes.CUSTOM_MODEL_DATA, data.build());
        stack.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().hideTooltip(true).build());
        set(balance ? CashierView.NUM_A : CashierView.NUM_B, Button.display(stack));
    }
}
