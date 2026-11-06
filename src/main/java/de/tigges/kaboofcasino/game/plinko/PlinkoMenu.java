package de.tigges.kaboofcasino.game.plinko;

import de.tigges.kaboofcasino.CasinoContext;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.game.PanelText;
import de.tigges.kaboofcasino.game.StakeMenu;
import de.tigges.kaboofcasino.gui.TitleBuilder;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

/**
 * The Plinko screen as a 9x6 chest (render.md 5.2; mod {@code PlinkoScreen}): the title is the mod's art and the
 * texts ("EINSÄTZE", three times "1 Kugel", the payout multipliers) at the pack's anchors; the three bet slots (one
 * ball each) are grid slots (2, 1), (2, 2), (2, 3) (the mod's slots at mod (65, 49 / 71 / 93)), the DROP button lies
 * over (3..5, 5).
 */
final class PlinkoMenu extends StakeMenu {

    static final int[] STAKES = {11, 20, 29};
    static final int[] BUTTON = {48, 49, 50};
    static final int BUTTON_ART = 49;
    static final int BETS = 36;
    static final TextColor CREAM = TextColor.color(0xF3E7BF);
    static final TextColor GOLD = TextColor.color(0xFFD770);

    private final PlinkoController controller;

    PlinkoMenu(UUID viewer, Component title, PlinkoController controller, CasinoContext ctx) {
        super(viewer, title, ctx);
        this.controller = controller;
    }

    static Component title(CasinoContext ctx) {
        TitleBuilder t = new TitleBuilder(ctx.layout());
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                t.art("plinko_bg_" + col + "_" + row);
            }
        }
        var m = ctx.messages().get();
        t.text("plinko_headline", m.raw(Msg.GUI_PLINKO_TITLE), CREAM);
        t.text("plinko_payouts", m.raw(Msg.GUI_SLOT_PAYOUTS), CREAM);
        t.text("plinko_bets", m.raw(Msg.GUI_PLINKO_BETS), CREAM);
        for (int i = 0; i < 3; i++) {
            t.text("plinko_ball_" + i, m.raw(Msg.GUI_PLINKO_BALL), CREAM);
        }
        for (int row = 0; row < PlinkoGame.ROW_QUARTERS.length; row++) {
            t.text("plinko_multiplier_" + row, PanelText.multiplier(PlinkoGame.ROW_QUARTERS[row]),
                    row == 0 ? GOLD : CREAM);
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
        return enabled ? "kaboofcasino:gui/plinko_on" : "kaboofcasino:gui/plinko_off";
    }

    @Override
    protected Msg buttonName() {
        return Msg.GUI_PLINKO_DROP;
    }

    @Override
    protected void play(Player player) {
        controller.drop(player, this);
    }

    @Override
    public PlinkoController controller() {
        return controller;
    }
}
