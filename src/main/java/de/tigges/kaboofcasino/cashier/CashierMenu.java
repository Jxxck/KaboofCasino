package de.tigges.kaboofcasino.cashier;

import de.tigges.kaboofcasino.CasinoContext;
import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.game.GameMenu;
import de.tigges.kaboofcasino.game.MachineController;
import de.tigges.kaboofcasino.gui.Button;
import de.tigges.kaboofcasino.gui.ClickAction;
import de.tigges.kaboofcasino.gui.DisplayCopies;
import de.tigges.kaboofcasino.gui.GuiLayout;
import de.tigges.kaboofcasino.gui.TitleBuilder;
import de.tigges.kaboofcasino.wager.ChipItems;
import de.tigges.kaboofcasino.wager.Chips;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/**
 * One tab of the cashier menu (9x6 chest, {@link CashierView}): the title is the art (the derby's felt panels, this
 * tab's overlay with the tab bar, frames and labels, the receipt's texts from {@code messages_de.yml}); the slots hold
 * display copies only. The selection model of the bet slots: clicks on the own inventory select, nothing moves until
 * the commit button (WECHSELN / KAUFEN), which runs one atomic transaction ({@link Cashbox}). Thread: MAIN.
 */
abstract class CashierMenu extends GameMenu {

    static final TextColor BRAND = TextColor.color(0xEACD83);
    static final TextColor CREAM = TextColor.color(0xF3E7BF);
    static final TextColor MUTED = TextColor.color(0xB5C5A6);
    static final String BLANK = "kaboofcasino:gui/blank";

    protected final CashierController controller;
    protected final CasinoContext ctx;
    private final CashierView.Tab tab;
    private long lastAction;

    protected CashierMenu(CashierView.Tab tab, UUID viewer, CashierController controller, CasinoContext ctx) {
        super(viewer, title(ctx.layout(), ctx.messages().get()::raw, tab));
        this.tab = tab;
        this.controller = controller;
        this.ctx = ctx;
        this.lastAction = Bukkit.getCurrentTick();
    }

    static CashierMenu of(CashierView.Tab tab, UUID viewer, CashierController controller, CasinoContext ctx) {
        return switch (tab) {
            case EXCHANGE -> new ExchangeMenu(viewer, controller, ctx);
            case CHANGE -> new ChangeMenu(viewer, controller, ctx);
            case SHOP -> new ShopMenu(viewer, controller, ctx);
        };
    }

    /** The title: background, this tab's overlay, the brand, the tab's head, help lines and value labels. */
    static Component title(GuiLayout layout, Function<Msg, String> raw, CashierView.Tab tab) {
        TitleBuilder t = new TitleBuilder(layout);
        t.art("cashier_bg_0");
        t.art("cashier_bg_1");
        t.art("cashier_overlay_" + tab.pack());
        t.text("cashier_brand", raw.apply(Msg.CASHIER_BRAND), BRAND, true, true);
        t.text("cashier_head", raw.apply(texts(tab)[0]), CREAM, false, true);
        String[] help = raw.apply(texts(tab)[1]).split("\n", -1);
        for (int i = 0; i < Math.min(help.length, 5); i++) {
            if (!help[i].isBlank()) {
                t.text("cashier_help_" + i, help[i].strip(), MUTED, false, false);
            }
        }
        t.text("cashier_label_a", raw.apply(texts(tab)[2]), CREAM, false, true);
        if (tab == CashierView.Tab.SHOP) {
            t.text("cashier_label_b", raw.apply(Msg.CASHIER_LABEL_PRICE), CREAM, false, true);
        }
        return t.build();
    }

    /** Head, help and label A of a tab. */
    static Msg[] texts(CashierView.Tab tab) {
        return switch (tab) {
            case EXCHANGE -> new Msg[] {Msg.CASHIER_HEAD_EXCHANGE, Msg.CASHIER_HELP_EXCHANGE, Msg.CASHIER_LABEL_VALUE};
            case CHANGE -> new Msg[] {Msg.CASHIER_HEAD_CHANGE, Msg.CASHIER_HELP_CHANGE, Msg.CASHIER_LABEL_VALUE};
            case SHOP -> new Msg[] {Msg.CASHIER_HEAD_SHOP, Msg.CASHIER_HELP_SHOP, Msg.CASHIER_LABEL_BALANCE};
        };
    }

    @Override
    public MachineController controller() {
        return controller;
    }

    CashierView.Tab tab() {
        return tab;
    }

    /** Server tick of the last click (the cashier's own idle close). */
    long lastAction() {
        return lastAction;
    }

    protected void touch() {
        lastAction = Bukkit.getCurrentTick();
    }

    /** The cashier closes its menu itself after the last click, not after the opening ({@link CashierController}). */
    @Override
    public boolean closesWhenIdle() {
        return false;
    }

    protected CashierSettings settings() {
        return controller.service().settings();
    }

    protected Messages messages() {
        return ctx.messages().get();
    }

    @Override
    public final void render() {
        clear();
        Messages m = messages();
        for (CashierView.Tab t : CashierView.Tab.values()) {
            Msg[] name = switch (t) {
                case EXCHANGE -> new Msg[] {Msg.CASHIER_TAB_EXCHANGE, Msg.CASHIER_TAB_EXCHANGE_LORE};
                case CHANGE -> new Msg[] {Msg.CASHIER_TAB_CHANGE, Msg.CASHIER_TAB_CHANGE_LORE};
                case SHOP -> new Msg[] {Msg.CASHIER_TAB_SHOP, Msg.CASHIER_TAB_SHOP_LORE};
            };
            ItemStack icon = DisplayCopies.model(BLANK, m.render(name[0]), m.lines(name[1]));
            for (int slot : t.slots()) {
                set(slot, t == tab ? Button.display(icon) : Button.of(icon, (p, click) -> {
                    touch();
                    p.playSound(p, Sound.UI_BUTTON_CLICK, 0.4f, 1.2f);
                    controller.show(p, t);
                }));
            }
        }
        Player viewer = Bukkit.getPlayer(viewer());
        if (viewer != null) {
            renderTab(viewer);
        }
    }

    /** The tab's slots (the tab bar is drawn). */
    protected abstract void renderTab(Player viewer);

    // ---- shared pieces ----------------------------------------------------------------------------------------------

    /** A chip as a display (the chip's model and name, no casino item tag), {@code count} shown up to 99. */
    protected static ItemStack chipIcon(int denomination, long count, Component name, List<Component> lore) {
        ItemStack stack = DisplayCopies.model(Chips.key(denomination), (name == null ? ChipItems.name(denomination)
                : name).decoration(TextDecoration.ITALIC, false), lore);
        int shown = (int) Math.max(1, Math.min(99, count));
        if (shown > 1) {
            stack.setData(DataComponentTypes.MAX_STACK_SIZE, 99);
            stack.setAmount(shown);
        }
        return stack;
    }

    /** A pack picture driven by custom model data strings, without a tooltip (number displays, the marker). */
    protected static ItemStack picture(String model, List<String> strings) {
        ItemStack stack = DisplayCopies.model(model, null, List.of());
        CustomModelData.Builder data = CustomModelData.customModelData();
        strings.forEach(data::addString);
        stack.setData(DataComponentTypes.CUSTOM_MODEL_DATA, data.build());
        stack.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().hideTooltip(true).build());
        return stack;
    }

    /** A number in the receipt: row A (slot 53) or row B (slot 52). */
    protected void number(boolean rowA, long value) {
        set(rowA ? CashierView.NUM_A : CashierView.NUM_B, Button.display(picture(rowA
                ? "kaboofcasino:gui/cashier/num_a" : "kaboofcasino:gui/cashier/num_b", CashierView.digits(value))));
    }

    /** The commit button over slots 48-51 (art in 49): {@code model} on/off, its name and lore, the action. */
    protected void commit(String model, boolean enabled, Component name, List<Component> lore, ClickAction action) {
        for (int slot : CashierView.COMMIT) {
            ItemStack icon = DisplayCopies.model(slot == CashierView.COMMIT_ART
                    ? "kaboofcasino:gui/cashier/" + model + (enabled ? "_on" : "_off") : BLANK, name, lore);
            set(slot, Button.commit(icon, (p, click) -> {
                touch();
                action.click(p, click);
            }));
        }
    }

    /** The clear button (slot 46). */
    protected void clearButton(Runnable clear) {
        Messages m = messages();
        set(CashierView.CLEAR, Button.of(DisplayCopies.model("kaboofcasino:gui/cashier/clear", m.render(
                Msg.CASHIER_CLEAR), m.lines(Msg.CASHIER_CLEAR_LORE)), (p, click) -> {
                    touch();
                    clear.run();
                    p.playSound(p, Sound.UI_BUTTON_CLICK, 0.4f, 1.0f);
                    render();
                }));
    }

    protected static CashierView.SelectClick selectClick(ClickType click) {
        return switch (click) {
            case RIGHT -> CashierView.SelectClick.RIGHT;
            case SHIFT_LEFT -> CashierView.SelectClick.SHIFT;
            default -> CashierView.SelectClick.LEFT;
        };
    }

    protected static void clicked(Player player) {
        player.playSound(player, Sound.UI_BUTTON_CLICK, 0.4f, 1.2f);
    }

    protected static void refused(Player player) {
        player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
    }
}
