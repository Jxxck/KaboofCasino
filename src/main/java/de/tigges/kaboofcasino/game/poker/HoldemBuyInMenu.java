package de.tigges.kaboofcasino.game.poker;

import de.tigges.kaboofcasino.CasinoContext;
import de.tigges.kaboofcasino.config.CasinoConfig;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.game.StakeMenu;
import de.tigges.kaboofcasino.gui.GuiLayout;
import de.tigges.kaboofcasino.gui.TitleBuilder;
import de.tigges.kaboofcasino.util.ChipText;
import java.util.UUID;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Sitting down at a Texas Hold'em table: the poker bet view (its three chip slots hold the buy-in) with the table's
 * terms where the paytable was (blinds, the buy-in range, the time to act, the seats taken) and HINSETZEN instead of
 * AUSTEILEN. HINSETZEN is the table's {@link HoldemTable#sit}: one record for all three slots, then the Hold'em view.
 */
public final class HoldemBuyInMenu extends StakeMenu {

    private final PokerController controller;

    public HoldemBuyInMenu(UUID viewer, PokerController controller, CasinoContext ctx, int seatsTaken) {
        super(viewer, title(ctx.layout(), ctx.messages().get()::raw, ctx.config().get().poker().holdem(),
                seatsTaken), ctx);
        this.controller = controller;
    }

    /** The bet view's title with the Hold'em texts. */
    public static Component title(GuiLayout layout, Function<Msg, String> raw, CasinoConfig.Holdem h,
                                  int seatsTaken) {
        TitleBuilder t = new TitleBuilder(layout);
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                t.art("pk_bet_bg_" + col + "_" + row);
            }
        }
        t.text("pk_headline", raw.apply(Msg.GUI_HOLDEM_TITLE), PokerBetMenu.CREAM);
        t.text("pk_bet", raw.apply(Msg.GUI_HOLDEM_BUY_IN), PokerBetMenu.CREAM);
        t.text("pk_payouts", raw.apply(Msg.GUI_HOLDEM_TERMS), PokerBetMenu.CREAM);
        t.text("pk_ante_pays", raw.apply(Msg.GUI_HOLDEM_VERSUS), PokerBetMenu.GOLD, true, true);
        Msg[] names = {Msg.GUI_HOLDEM_ROW_SB, Msg.GUI_HOLDEM_ROW_BB, Msg.GUI_HOLDEM_ROW_MIN, Msg.GUI_HOLDEM_ROW_MAX,
                Msg.GUI_HOLDEM_ROW_TIME, Msg.GUI_HOLDEM_ROW_SEATS};
        String[] values = {ChipText.number(h.smallBlind()), ChipText.number(h.bigBlind()),
                ChipText.number(h.minBuyIn()), ChipText.number(h.maxBuyIn()), h.turnSeconds() + " s",
                seatsTaken + "/" + PokerLayout.SEATS};
        for (int row = 0; row < names.length; row++) {
            t.text("pk_pay_name_" + row, raw.apply(names[row]), PokerBetMenu.WHITE, false, true);
            t.text("pk_pay_odds_" + row, values[row], PokerBetMenu.GOLD, false, true);
        }
        t.text("pk_call_label", raw.apply(Msg.GUI_HOLDEM_BOX1_LABEL), PokerBetMenu.WHITE, true, true);
        t.text("pk_call_note", raw.apply(Msg.GUI_HOLDEM_BOX1_NOTE), PokerBetMenu.NOTE, false, true);
        t.text("pk_call_last", raw.apply(Msg.GUI_HOLDEM_BOX1_LAST), PokerBetMenu.NOTE, false, true);
        t.text("pk_dealer_label", raw.apply(Msg.GUI_HOLDEM_BOX2_LABEL), PokerBetMenu.WHITE, true, true);
        t.text("pk_dealer_note", raw.apply(Msg.GUI_HOLDEM_BOX2_NOTE), PokerBetMenu.NOTE, false, true);
        t.text("pk_dealer_last", raw.apply(Msg.GUI_HOLDEM_BOX2_LAST), PokerBetMenu.NOTE, false, true);
        return t.build();
    }

    @Override
    protected int[] stakeSlots() {
        return PokerBetMenu.STAKES;
    }

    @Override
    protected int[] buttonSlots() {
        return PokerBetMenu.BUTTON;
    }

    @Override
    protected int buttonArtSlot() {
        return PokerBetMenu.BUTTON_ART;
    }

    @Override
    protected int betsSlot() {
        return PokerBetMenu.BETS;
    }

    @Override
    protected String buttonModel(boolean enabled) {
        return enabled ? "kaboofcasino:gui/holdem/sit_on" : "kaboofcasino:gui/holdem/sit_off";
    }

    @Override
    protected Msg buttonName() {
        return Msg.GUI_HOLDEM_SIT;
    }

    @Override
    protected void play(Player player) {
        controller.holdemSit(player, this);
    }

    @Override
    public PokerController controller() {
        return controller;
    }

    @Override
    public boolean announcesIdleClose() {
        return false;
    }
}
