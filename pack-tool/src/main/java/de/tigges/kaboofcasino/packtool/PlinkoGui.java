package de.tigges.kaboofcasino.packtool;

import java.awt.image.BufferedImage;

/**
 * Plinko's chest GUI (render.md 5.2; mod {@code PlinkoScreen}): the mod's art re-composed by {@link MachineGuiArt}
 * (payout panel minus columns 261..266 and 312..320, keeping the pocket dots clear of the multipliers; "PLINKO"
 * re-centred). The three bet frames move by (+2, +7 / +3 / -1) px onto grid slots (2, 1) / (2, 2) / (2, 3) (the
 * mod's pitch is 22 px, the grid's 18: neighbouring frames share their border); the bullet dots move 6 px left and
 * the "1 Kugel" labels 8 px left, because the mod shrinks the German label into 31 px and a font glyph cannot.
 */
final class PlinkoGui {

    static final MachineGuiArt ART = new MachineGuiArt(new int[][] {{261, 6}, {312, 9}});
    static final int[] ROW_DY = {7, 3, -1};
    static final int ROW_DX = 2;
    static final int DOT_DX = -6;
    static final int LABEL_DX = -8;
    /** The mod's payout rows: quarters 32, 12, 8, 2, 1. */
    static final int ROWS = 5;

    private PlinkoGui() {
    }

    static java.util.List<GuiFont.Anchor> anchors() {
        java.util.List<GuiFont.Anchor> out = new java.util.ArrayList<>();
        out.add(GuiFont.Anchor.text("plinko_headline", ART.windowX(112), ART.windowY(12), "center"));
        out.add(GuiFont.Anchor.text("plinko_payouts", (ART.panelFrom() + ART.panelTo()) / 2 + MachineGuiArt.ART_X,
                ART.panelWindowY(14), "center"));
        out.add(GuiFont.Anchor.text("plinko_bets", ART.windowX(52), ART.windowY(32), "center"));
        for (int i = 0; i < 3; i++) {
            out.add(GuiFont.Anchor.text("plinko_ball_" + i, ART.windowX(31) + LABEL_DX, ART.windowY(53 + 22 * i)
                    + ROW_DY[i], "left"));
        }
        for (int row = 0; row < ROWS; row++) {
            out.add(GuiFont.Anchor.text("plinko_multiplier_" + row, ART.windowX(351), ART.panelWindowY(52 + 34 * row),
                    "right"));
        }
        return out;
    }

    static void add(ModJar jar, PackTree tree, GuiFont font, TextGlyphs text) {
        BufferedImage art = compose(Images.read(jar.bytes("assets/roulettecasino/textures/gui/plinko.png")));
        MachineGuiArt.tiles(font, "plinko", art);
        for (GuiFont.Anchor a : anchors()) {
            font.anchor(a);
        }
        SlotMachineGui.button(tree, text, "plinko_on", PackLang.text("DROP", "LOSLASSEN"), true, 14, 4.5, 11.5);
        SlotMachineGui.button(tree, text, "plinko_off", PackLang.text("DROP", "LOSLASSEN"), false, 14, 4.5, 11.5);
    }

    static BufferedImage compose(BufferedImage source) {
        BufferedImage mod = Images.argb(source);
        BufferedImage original = Images.crop(mod, 0, 0, mod.getWidth(), mod.getHeight());
        MachineGuiArt.felt(mod, 14, 46, 76, 67, 11, 12);
        for (int i = 0; i < 3; i++) {
            Images.copy(original, 20, 47 + 22 * i, 10, 20, mod, 20 + DOT_DX, 47 + 22 * i + ROW_DY[i]);
            Images.copy(original, 60, 47 + 22 * i, 26, 20, mod, 60 + ROW_DX, 47 + 22 * i + ROW_DY[i]);
        }
        return ART.compose(mod, Lettering.find(mod));
    }
}
