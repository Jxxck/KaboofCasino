package de.tigges.kaboofcasino.game.poker;

import de.tigges.kaboofcasino.CasinoContext;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.game.StakeMenu;
import de.tigges.kaboofcasino.gui.GuiLayout;
import de.tigges.kaboofcasino.gui.TitleBuilder;
import java.util.UUID;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

/**
 * The poker ante view as a 9x6 chest (render.md 5.2; mod {@code PokerScreen} while betting): the title is the mod's
 * 366x266 window (headline "POKER", "EINSATZ" over the slots, the payout panel with "DER EINSATZ ZAHLT", the six
 * paytable rows, the call box MITGEHEN / 2 × Einsatz / 1:1 and the dealer box CROUPIER / Ab Paar Vieren / sonst
 * bezahlt, in the mod's colours and weights); the three ante slots are grid slots (2, 3), (4, 3), (6, 3) (the mod's
 * slots at mod (72 / 103 / 134, 89)), AUSTEILEN lies at the mod's place over (3..5, 5), the bettable items are the
 * book in (0, 5). DEAL is the controller's: it commits, clears this menu and opens {@link PokerRoundMenu}.
 */
public final class PokerBetMenu extends StakeMenu {

    static final int[] STAKES = {29, 31, 33};
    static final int[] BUTTON = {48, 49, 50};
    static final int BUTTON_ART = 49;
    static final int BETS = 45;
    /** {@code PokerScreen.extractBetLabels}: headline, EINSATZ and Gewinne -792641 without shadow. */
    static final TextColor CREAM = TextColor.color(0xF3E7BF);
    /** "DER EINSATZ ZAHLT", the odds and "1:1" -22016; names, MITGEHEN and CROUPIER white; notes -5583431. */
    static final TextColor GOLD = TextColor.color(0xFFAA00);
    static final TextColor WHITE = TextColor.color(0xFFFFFF);
    static final TextColor NOTE = TextColor.color(0xAACDB9);
    /** The paytable ({@code PAY_CATEGORIES} 9..4, the last row "straight or less"). */
    static final Msg[] PAY_NAMES = {Msg.GUI_POKER_PAY_ROYAL_FLUSH, Msg.GUI_POKER_PAY_STRAIGHT_FLUSH,
            Msg.GUI_POKER_PAY_FOUR_OF_A_KIND, Msg.GUI_POKER_PAY_FULL_HOUSE, Msg.GUI_POKER_PAY_FLUSH,
            Msg.GUI_POKER_PAY_STRAIGHT_LESS};
    static final int[] PAY_CATEGORIES = {9, 8, 7, 6, 5, 4};

    private final PokerController controller;

    public PokerBetMenu(UUID viewer, PokerController controller, CasinoContext ctx) {
        super(viewer, title(ctx), ctx);
        this.controller = controller;
    }

    static Component title(CasinoContext ctx) {
        return title(ctx.layout(), ctx.messages().get()::raw);
    }

    /** "100:1" .. "1:1": {@code PokerHands.antePays(category << 20) + ":1"}. */
    static String odds(int category) {
        return PokerHands.antePays(category << 20) + ":1";
    }

    /** The ante view's title from the pack's layout and the message texts ({@code raw}). */
    public static Component title(GuiLayout layout, Function<Msg, String> raw) {
        TitleBuilder t = new TitleBuilder(layout);
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                t.art("pk_bet_bg_" + col + "_" + row);
            }
        }
        t.text("pk_headline", raw.apply(Msg.GUI_POKER_TITLE), CREAM);
        t.text("pk_bet", raw.apply(Msg.GUI_BLACKJACK_STAKE), CREAM);
        t.text("pk_payouts", raw.apply(Msg.GUI_SLOT_PAYOUTS), CREAM);
        t.text("pk_ante_pays", raw.apply(Msg.GUI_POKER_ANTE_PAYS), GOLD, true, true);
        for (int row = 0; row < PAY_NAMES.length; row++) {
            t.text("pk_pay_name_" + row, raw.apply(PAY_NAMES[row]), WHITE, false, true);
            t.text("pk_pay_odds_" + row, odds(PAY_CATEGORIES[row]), GOLD, false, true);
        }
        t.text("pk_call_label", raw.apply(Msg.GUI_POKER_CALL_LABEL), WHITE, true, true);
        t.text("pk_call_note", raw.apply(Msg.GUI_POKER_CALL_NOTE), NOTE, false, true);
        t.text("pk_call_last", "1:1", GOLD, true, true);
        t.text("pk_dealer_label", raw.apply(Msg.GUI_BLACKJACK_DEALER), WHITE, true, true);
        t.text("pk_dealer_note", raw.apply(Msg.GUI_POKER_QUALIFY_NOTE), NOTE, false, true);
        t.text("pk_dealer_last", raw.apply(Msg.GUI_POKER_QUALIFY_ELSE), NOTE, false, true);
        return t.build();
    }

    @Override
    protected int[] stakeSlots() {
        return STAKES;
    }

    @Override
    protected int[] buttonSlots() {
        return BUTTON;
    }

    @Override
    protected int buttonArtSlot() {
        return BUTTON_ART;
    }

    @Override
    protected int betsSlot() {
        return BETS;
    }

    @Override
    protected String buttonModel(boolean enabled) {
        return enabled ? "kaboofcasino:gui/poker/deal_on" : "kaboofcasino:gui/poker/deal_off";
    }

    @Override
    protected Msg buttonName() {
        return Msg.GUI_POKER_DEAL;
    }

    @Override
    protected void play(Player player) {
        controller.deal(player, this);
    }

    @Override
    public PokerController controller() {
        return controller;
    }

    /** {@code PokerTableBlockEntity.serverTick}: the bet menu closes after 2400 idle ticks without a word. */
    @Override
    public boolean announcesIdleClose() {
        return false;
    }
}
