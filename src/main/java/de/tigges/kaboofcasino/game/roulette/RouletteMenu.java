package de.tigges.kaboofcasino.game.roulette;

import de.tigges.kaboofcasino.CasinoContext;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.game.StakeMenu;
import de.tigges.kaboofcasino.gui.DisplayCopies;
import de.tigges.kaboofcasino.gui.TitleBuilder;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * The roulette screen as a 9x6 chest (render.md 5.2; mod {@code RouletteScreen}): the title is the mod's art and the
 * texts at the pack's anchors (the header is {@code gui.roulettecasino.header_top}, "ROULETTE" in both languages); the nine bet slots (slot i = colour column {@code i % 3}: RED, BLACK, GREEN; row
 * {@code i / 3}) lie on grid columns 1 / 4 / 7, rows 2..4 (the mod's slots at (48 + 56c, 70 + 20r)); the SPIN
 * button lies over (3..5, 5). One colour per spin: once a column holds a stake the other columns are veiled
 * ({@code RouletteMenu.mayPlace}) and the marker moves down the chosen column.
 */
final class RouletteMenu extends StakeMenu {

    static final int[] COLUMNS = {1, 4, 7};
    static final int FIRST_ROW = 2;
    static final int[] BUTTON = {48, 49, 50};
    static final int BUTTON_ART = 49;
    static final int BETS = 45;
    static final TextColor CREAM = TextColor.color(0xF3E7BF);
    static final TextColor GOLD = TextColor.color(0xFFD770);

    private final RouletteController controller;

    RouletteMenu(UUID viewer, Component title, RouletteController controller, CasinoContext ctx) {
        super(viewer, title, ctx, 9);
        this.controller = controller;
    }

    /** Grid index of bet slot i (column i % 3, row i / 3). */
    static int gridSlot(int i) {
        return (FIRST_ROW + i / 3) * 9 + COLUMNS[i % 3];
    }

    static Component title(CasinoContext ctx) {
        TitleBuilder t = new TitleBuilder(ctx.layout());
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                t.art("roulette_bg_" + col + "_" + row);
            }
        }
        var m = ctx.messages().get();
        t.text("roulette_headline", "ROULETTE", CREAM);
        t.text("roulette_payouts", m.raw(Msg.GUI_SLOT_PAYOUTS), CREAM);
        Msg[] names = {Msg.GUI_ROULETTE_RED, Msg.GUI_ROULETTE_BLACK, Msg.GUI_ROULETTE_GREEN};
        for (int c = 0; c < RouletteGame.COLOURS; c++) {
            t.text("roulette_colour_" + c, m.raw(names[c]), CREAM);
        }
        // the payout rows: GREEN, RED, BLACK (RouletteScreen.extractLabels): name in cream, odds in gold
        int[] rows = {RouletteGame.GREEN, RouletteGame.RED, RouletteGame.BLACK};
        for (int row = 0; row < rows.length; row++) {
            t.text("roulette_payout_label_" + row, m.raw(names[rows[row]]), CREAM);
            t.text("roulette_multiplier_" + row, "×" + RouletteGame.multiplier(rows[row]), GOLD);
        }
        return t.build();
    }

    /** -1 = nothing staked, else the colour column in use. */
    int stakedColour() {
        for (int i = 0; i < 9; i++) {
            if (stake(i) != null) {
                return i % 3;
            }
        }
        return -1;
    }

    @Override
    protected boolean selectable(int slot) {
        int colour = stakedColour();
        return colour < 0 || slot % 3 == colour;
    }

    @Override
    protected void refused(Player player, int slot) {
        ctx.messages().get().actionBar(player, Msg.ROULETTE_SEVERAL);
    }

    @Override
    protected ItemStack lockedIcon() {
        return DisplayCopies.model("kaboofcasino:gui/roulette_veil", ctx.messages().get().render(Msg.GUI_ROULETTE_VEIL),
                List.of());
    }

    /** The marker moves down the chosen colour column. */
    @Override
    protected int next(int filled) {
        for (int step = 1; step < 3; step++) {
            int candidate = (filled + 3 * step) % 9;
            if (stake(candidate) == null) {
                return candidate;
            }
        }
        return filled;
    }

    @Override
    protected int[] stakeSlots() {
        int[] out = new int[9];
        for (int i = 0; i < 9; i++) {
            out[i] = gridSlot(i);
        }
        return out;
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
        return enabled ? "kaboofcasino:gui/roulette_on" : "kaboofcasino:gui/roulette_off";
    }

    @Override
    protected Msg buttonName() {
        return Msg.GUI_ROULETTE_SPIN;
    }

    @Override
    protected void play(Player player) {
        controller.spin(player, this);
    }

    @Override
    public RouletteController controller() {
        return controller;
    }
}
