package de.tigges.kaboofcasino.game.slot;

import de.tigges.kaboofcasino.CasinoContext;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.gui.Button;
import de.tigges.kaboofcasino.gui.DisplayCopies;
import de.tigges.kaboofcasino.game.ChipStakes;
import de.tigges.kaboofcasino.game.GameMenu;
import de.tigges.kaboofcasino.gui.TitleBuilder;
import de.tigges.kaboofcasino.wager.ChipItems;
import de.tigges.kaboofcasino.wager.Chips;
import de.tigges.kaboofcasino.wager.ItemAmount;
import de.tigges.kaboofcasino.wager.Ledger;
import de.tigges.kaboofcasino.wager.PlayerAccount;
import de.tigges.kaboofcasino.util.ItemNames;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/**
 * The slot machine screen as a 9x6 chest (render.md 5.2). The title is the mod's art (font tiles), the current reel
 * faces and the texts; the stake, the bettable-items list and the SPIN button are display items in grid slots.
 * Selection model: clicking an item in the own inventory selects the stake (left: that stack, right: +1, shift-left:
 * all of that item, capped at {@code bets.max-stake}); nothing moves until SPIN commits the round.
 */
final class SlotMenu extends GameMenu {

    /** Grid slot (4, 3): the mod's bet slot at mod (104, 96). */
    static final int STAKE_SLOT = 31;
    /** Grid slots (3..5, 4): the SPIN button (art in 40, click areas left and right of it). */
    static final int[] BUTTON_SLOTS = {39, 40, 41};
    static final int BUTTON_ART_SLOT = 40;
    /** Grid slot (0, 4): the bettable-items list (the mod draws it left of its window). */
    static final int BETS_SLOT = 36;
    private static final TextColor CREAM = TextColor.color(-792641 & 0xffffff);
    private static final TextColor GOLD = TextColor.color(-10384 & 0xffffff);

    private final SlotController controller;
    private final CasinoContext ctx;
    private Chips.Selection selection;

    SlotMenu(UUID viewer, Component title, SlotController controller, CasinoContext ctx) {
        super(viewer, title);
        this.controller = controller;
        this.ctx = ctx;
    }

    /**
     * The title: background tiles, the reel faces of the last stops, headline, labels and multipliers, all at the
     * positions the pack declares ({@code gui-layout.json} anchors).
     */
    static Component title(CasinoContext ctx, int[] stops) {
        TitleBuilder t = new TitleBuilder(ctx.layout());
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                t.art("slot_bg_" + col + "_" + row);
            }
        }
        for (int k = 0; k < 3; k++) {
            t.artAt("slot_reel_" + k + "_" + Math.floorMod(stops[k], 8), "slot_reel_" + k);
        }
        var m = ctx.messages().get();
        t.text("slot_headline", m.raw(Msg.GUI_SLOT_TITLE), CREAM);
        t.text("slot_payouts", m.raw(Msg.GUI_SLOT_PAYOUTS), CREAM);
        t.text("slot_bet", m.raw(Msg.GUI_SLOT_BET), CREAM);
        for (int row = 0; row < SlotGame.PAYTABLE.length; row++) {
            t.text("slot_multiplier_" + row, "×" + SlotGame.PAYTABLE[row].multiplier(), row == 0 ? GOLD : CREAM);
        }
        return t.build();
    }

    /** The chips on the bet slot (null = none). */
    Chips.Selection selection() {
        return selection;
    }

    void clearSelection() {
        selection = null;
    }

    @Override
    public void render() {
        clear();
        var m = ctx.messages().get();
        if (selection != null) {
            set(STAKE_SLOT, Button.of(ChipStakes.icon(ctx, selection, m.lines(Msg.CHIPS_SLOT_STAKE_LORE), false),
                    (p, click) -> {
                        Chips.Selection less = selection.minusSmallest();
                        selection = click == ClickType.RIGHT && !less.isEmpty() ? less : null;
                        render();
                    }));
        }
        boolean owed = !owedNow().isEmpty();
        ItemStack button;
        if (owed) {
            button = DisplayCopies.model("kaboofcasino:gui/collect_on", m.render(Msg.GUI_SLOT_COLLECT),
                    m.lines(Msg.GUI_SLOT_COLLECT_LORE));
        } else {
            button = DisplayCopies.model(selection == null ? "kaboofcasino:gui/spin_off" : "kaboofcasino:gui/spin_on",
                    m.render(Msg.GUI_SLOT_SPIN), m.lines(Msg.CHIPS_SLOT_SPIN_LORE,
                            de.tigges.kaboofcasino.config.Messages.text("max",
                                    de.tigges.kaboofcasino.util.ChipText.chips(ctx.config().get().bets().maxStake()))));
        }
        ItemStack blank = DisplayCopies.model("kaboofcasino:gui/blank", m.render(owed ? Msg.GUI_SLOT_COLLECT
                : Msg.GUI_SLOT_SPIN), List.of());
        for (int slot : BUTTON_SLOTS) {
            ItemStack icon = slot == BUTTON_ART_SLOT ? button : blank;
            set(slot, owed ? Button.of(icon, (p, click) -> {
                ctx.ledger().collect(p);
                render();
            }) : Button.commit(icon, (p, click) -> controller.spin(p, this)));
        }
        set(BETS_SLOT, Button.display(ChipStakes.book(ctx)));
    }

    private List<ItemAmount> owedNow() {
        Player p = org.bukkit.Bukkit.getPlayer(viewer());
        if (p == null) {
            return List.of();
        }
        try {
            Ledger l = new PlayerAccount(p).ledger();
            return l.owed();
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    /** A click on chips in the own inventory adds them to the bet slot ({@link ChipStakes#add}). */
    @Override
    public void onBottomClick(Player player, int index, ClickType click, ItemStack clicked) {
        if (clicked == null || clicked.isEmpty()) {
            return;
        }
        var m = ctx.messages().get();
        OptionalInt chip = ChipItems.denomination(clicked);
        if (chip.isEmpty()) {
            m.actionBar(player, Msg.CHIPS_ONLY);
            return;
        }
        int d = chip.getAsInt();
        long max = ctx.config().get().bets().maxStake();
        long current = selection == null ? 0 : selection.value();
        long ownedFree = new PlayerAccount(player).countPlain(Chips.key(d))
                - (selection == null ? 0 : selection.count(d));
        long add = ChipStakes.add(click, d, clicked.getAmount(), ownedFree, current, 0, max);
        if (ChipStakes.capped(click, d, clicked.getAmount(), ownedFree, current, 0, max)) {
            ChipStakes.tellMax(ctx, player);
        }
        if (add >= 1) {
            selection = (selection == null ? Chips.Selection.EMPTY : selection).plus(d, add);
            player.playSound(player, org.bukkit.Sound.UI_BUTTON_CLICK, 0.4f, 1.2f);
        }
        render();
    }

    @Override
    public SlotController controller() {
        return controller;
    }
}
