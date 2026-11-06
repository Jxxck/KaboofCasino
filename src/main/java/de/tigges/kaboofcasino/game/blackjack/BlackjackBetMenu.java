package de.tigges.kaboofcasino.game.blackjack;

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
 * The blackjack bet view as a 9x6 chest (render.md 5.2; mod {@code BlackjackScreen} while betting): the title is the
 * mod's 366x266 window (headline "BLACKJACK", "EINSATZ" over the slots, the payouts panel with BLACKJACK / SIEG /
 * REMIS, their notes and odds, bold and shadowed like the mod's); the three bet slots are grid slots (2, 3), (4, 3),
 * (6, 3) (the mod's slots at mod (72 / 103 / 134, 96)), AUSTEILEN lies over (3..5, 5), the bettable items are the book
 * in (0, 5). DEAL is the controller's: it commits, clears this menu and opens {@link BlackjackRoundMenu}.
 */
public final class BlackjackBetMenu extends StakeMenu {

    static final int[] STAKES = {29, 31, 33};
    static final int[] BUTTON = {48, 49, 50};
    static final int BUTTON_ART = 49;
    static final int BETS = 45;
    /** The mod's label colour -792641. */
    static final TextColor CREAM = TextColor.color(0xF3E7BF);
    /** {@code BlackjackScreen.extractBetLabels}: labels white bold, notes -5583431, odds -22016, all shadowed. */
    static final TextColor WHITE = TextColor.color(0xFFFFFF);
    static final TextColor NOTE = TextColor.color(0xAACDB9);
    static final TextColor GOLD = TextColor.color(0xFFAA00);
    static final Msg[][] PAY_ROWS = {
            {Msg.GUI_BLACKJACK_PAY_BLACKJACK, Msg.GUI_BLACKJACK_PAY_BLACKJACK_NOTE, Msg.GUI_BLACKJACK_ODDS_BLACKJACK},
            {Msg.GUI_BLACKJACK_PAY_WIN, Msg.GUI_BLACKJACK_PAY_WIN_NOTE, Msg.GUI_BLACKJACK_ODDS_WIN},
            {Msg.GUI_BLACKJACK_PAY_PUSH, Msg.GUI_BLACKJACK_PAY_PUSH_NOTE, Msg.GUI_BLACKJACK_ODDS_PUSH}};

    private final BlackjackController controller;

    public BlackjackBetMenu(UUID viewer, BlackjackController controller, CasinoContext ctx) {
        super(viewer, title(ctx), ctx);
        this.controller = controller;
    }

    static Component title(CasinoContext ctx) {
        return title(ctx.layout(), ctx.messages().get()::raw);
    }

    /** The bet view's title from the pack's layout and the message texts ({@code raw}). */
    public static Component title(GuiLayout layout, Function<Msg, String> raw) {
        TitleBuilder t = new TitleBuilder(layout);
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                t.art("bj_bet_bg_" + col + "_" + row);
            }
        }
        t.text("bj_headline", raw.apply(Msg.GUI_BLACKJACK_TITLE), CREAM);
        t.text("bj_payouts", raw.apply(Msg.GUI_SLOT_PAYOUTS), CREAM);
        t.text("bj_bet", raw.apply(Msg.GUI_BLACKJACK_STAKE), CREAM);
        for (int row = 0; row < PAY_ROWS.length; row++) {
            t.text("bj_pay_label_" + row, raw.apply(PAY_ROWS[row][0]), WHITE, true, true);
            t.text("bj_pay_note_" + row, raw.apply(PAY_ROWS[row][1]), NOTE, false, true);
            t.text("bj_pay_odds_" + row, raw.apply(PAY_ROWS[row][2]), GOLD, false, true);
        }
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
        return enabled ? "kaboofcasino:gui/blackjack/deal_on" : "kaboofcasino:gui/blackjack/deal_off";
    }

    @Override
    protected Msg buttonName() {
        return Msg.GUI_BLACKJACK_DEAL;
    }

    @Override
    protected void play(Player player) {
        controller.deal(player, this);
    }

    @Override
    public BlackjackController controller() {
        return controller;
    }
}
