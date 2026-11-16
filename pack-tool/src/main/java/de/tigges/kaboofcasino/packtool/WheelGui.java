package de.tigges.kaboofcasino.packtool;

import java.awt.image.BufferedImage;

/**
 * The fortune wheel's chest GUI (render.md 5.2; mod {@code FortuneWheelScreen}): the mod's art re-composed by
 * {@link MachineGuiArt} (payout panel minus columns 304..318, "FORTUNE" re-centred). The three bet frames move by
 * -5 / 0 / +5 px onto grid slots (2, 4) / (4, 4) / (6, 4); the mod draws its slots at mod (72 / 103 / 134, 110).
 * The disc (the wheel with the last result under the pennant) is one of 16 glyphs from
 * {@code gui/fortune_wheel_discs.png} at mod (84, 28). Texts and the disc are anchors in the manifest.
 */
final class WheelGui {

    static final MachineGuiArt ART = new MachineGuiArt(new int[][] {{304, 15}});
    static final int[] FRAME_DX = {-5, 0, 5};
    /** The mod's payout rows: quarters 20, 8, 6, 4, 3, 2, 1, 0. */
    static final int ROWS = 8;

    private WheelGui() {
    }

    static java.util.List<GuiFont.Anchor> anchors() {
        java.util.List<GuiFont.Anchor> out = new java.util.ArrayList<>();
        out.add(GuiFont.Anchor.text("wheel_headline", ART.windowX(112), ART.windowY(12), "center"));
        out.add(GuiFont.Anchor.text("wheel_payouts", (ART.panelFrom() + ART.panelTo()) / 2 + MachineGuiArt.ART_X,
                ART.panelWindowY(14), "center"));
        out.add(GuiFont.Anchor.text("wheel_bet", ART.windowX(112), ART.windowY(94), "center"));
        for (int row = 0; row < ROWS; row++) {
            out.add(GuiFont.Anchor.text("wheel_multiplier_" + row, ART.windowX(351), ART.panelWindowY(46 + 21 * row),
                    "right"));
        }
        out.add(GuiFont.Anchor.glyph("wheel_disc", ART.windowX(84), ART.windowY(28)));
        return out;
    }

    static void add(ModJar jar, PackTree tree, GuiFont font, TextGlyphs text) {
        BufferedImage art = compose(Images.read(jar.bytes("assets/roulettecasino/textures/gui/fortune_wheel.png")));
        MachineGuiArt.tiles(font, "wheel", art);
        BufferedImage discs = Images.read(jar.bytes("assets/roulettecasino/textures/gui/fortune_wheel_discs.png"));
        String[] names = new String[16];
        for (int s = 0; s < 16; s++) {
            names[s] = "wheel_disc_" + s;
        }
        int dx = ART.windowX(84);
        int dy = ART.windowY(28);
        font.grid(names, discs, "font/wheel/discs.png", 4, 4, 64, 13 - dy, dx, dy);
        for (GuiFont.Anchor a : anchors()) {
            font.anchor(a);
        }
        SlotMachineGui.button(tree, text, "wheel_on", SlotMachineGui.spin(), true, 14, 4.5, 11.5);
        SlotMachineGui.button(tree, text, "wheel_off", SlotMachineGui.spin(), false, 14, 4.5, 11.5);
    }

    static BufferedImage compose(BufferedImage source) {
        BufferedImage mod = Images.argb(source);
        BufferedImage[] frames = new BufferedImage[3];
        for (int i = 0; i < 3; i++) {
            frames[i] = Images.crop(mod, 69 + 31 * i, 107, 22, 22);
        }
        MachineGuiArt.felt(mod, 64, 107, 94, 22, 129, 20);
        for (int i = 0; i < 3; i++) {
            Images.copy(frames[i], 0, 0, 22, 22, mod, 69 + 31 * i + FRAME_DX[i], 107);
        }
        return ART.compose(mod, Lettering.find(mod));
    }
}
