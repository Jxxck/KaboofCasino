package de.tigges.kaboofcasino.game.wheel;

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
 * The fortune wheel screen as a 9x6 chest (render.md 5.2; mod {@code FortuneWheelScreen}): the title is the mod's
 * art, the disc of the last stop and the texts at the pack's anchors; the three bet slots are grid slots (2, 4),
 * (4, 4), (6, 4) (the mod's slots at mod (72 / 103 / 134, 110)), the SPIN button lies over (3..5, 5).
 */
final class WheelMenu extends StakeMenu {

    static final int[] STAKES = {38, 40, 42};
    static final int[] BUTTON = {48, 49, 50};
    static final int BUTTON_ART = 49;
    static final int BETS = 36;
    static final TextColor CREAM = TextColor.color(0xF3E7BF);
    static final TextColor GOLD = TextColor.color(0xFFD770);

    private final WheelController controller;

    WheelMenu(UUID viewer, Component title, WheelController controller, CasinoContext ctx) {
        super(viewer, title, ctx);
        this.controller = controller;
    }

    static Component title(CasinoContext ctx, int stop) {
        TitleBuilder t = new TitleBuilder(ctx.layout());
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                t.art("wheel_bg_" + col + "_" + row);
            }
        }
        t.artAt("wheel_disc_" + Math.floorMod(stop, FortuneWheelGame.SEGMENTS), "wheel_disc");
        var m = ctx.messages().get();
        t.text("wheel_headline", m.raw(Msg.GUI_WHEEL_TITLE), CREAM);
        t.text("wheel_payouts", m.raw(Msg.GUI_SLOT_PAYOUTS), CREAM);
        t.text("wheel_bet", m.raw(Msg.GUI_WHEEL_BET), CREAM);
        for (int row = 0; row < FortuneWheelGame.ROW_QUARTERS.length; row++) {
            t.text("wheel_multiplier_" + row, PanelText.multiplier(FortuneWheelGame.ROW_QUARTERS[row]),
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
        return enabled ? "kaboofcasino:gui/wheel_on" : "kaboofcasino:gui/wheel_off";
    }

    @Override
    protected Msg buttonName() {
        return Msg.GUI_WHEEL_SPIN;
    }

    @Override
    protected void play(Player player) {
        controller.spin(player, this);
    }

    @Override
    public WheelController controller() {
        return controller;
    }
}
