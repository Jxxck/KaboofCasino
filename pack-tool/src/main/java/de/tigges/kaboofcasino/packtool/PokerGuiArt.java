package de.tigges.kaboofcasino.packtool;

import java.awt.image.BufferedImage;

/**
 * The two backgrounds of the poker screens.
 *
 * <p><b>Bet view</b> ({@code gui/poker.png}, 366x266) through {@link MachineGuiArt} like the other 3x3 screens: the
 * three bet frames move by (-5 / 0 / +5, +3) px onto grid slots (2, 3) / (4, 3) / (6, 3) (the mod's slots at mod
 * (72 / 103 / 134, 89)), the laurels move with the outer frames. The payout panel keeps its three boxes whole: the
 * bold "DER EINSATZ ZAHLT" (113 px, the mod scales it into 108) needs the paytable box's full 115 px inside, and the
 * boxes' left and right parts hold the icons and the notes. The 15 columns the screen loses beyond the common cuts
 * are the only plain ones right of the inventory that are left: the felt between the payout frame and the boxes
 * (236..238, 356..361), the left panel's dark outline where it meets the payout panel's (223; one dark seam stays)
 * and the five felt columns between the left panel's inner frame and its outer frame (215..219). The bottom corner
 * ornaments of both panels are lifted out first and pasted back at the same distance from their frame.
 *
 * <p><b>Round view</b>: the mod's side panel ({@code gui/poker_panel.png}, 160x196) re-built 304 px wide around the
 * chest (window x -64..239, y -14..230). The mod stacks the dealer and player boxes over the board; above the chest
 * there is room for one row of cards only, so the three pieces stand side by side, each the mod's art pixel for
 * pixel: the dealer box (window -55..14), the board band (17..158), the player box (161..230), tops at window y 13
 * (the labels CROUPIER / DU above them like in the mod), then the stake band across all three (71..89), the timer bar
 * slot (19..156 x 101..105) and the bet screen's inventory slot art under the vanilla inventory.
 */
final class PokerGuiArt {

    static final MachineGuiArt ART = new MachineGuiArt(new int[][] {{215, 5}, {223, 1}, {236, 3}, {356, 6}});
    static final int[] FRAME_DX = {-5, 0, 5};
    static final int FRAME_DY = 3;
    /** The mod's bet frames: 22x22 at (69 + 31 i, 86). */
    static final int FRAME_X = 69;
    static final int FRAME_Y = 86;
    static final int FRAME_STEP = 31;
    static final int FRAME_SIZE = 22;
    /** First mod row of plain felt under the payout boxes and the left panel's inner frame (the erase source). */
    static final int ORNAMENT_FELT_Y = 251;

    // ---- round panel (window coordinates) ---------------------------------------------------------------------------
    static final int ROUND_X = -64;
    static final int ROUND_Y = -14;
    static final int ROUND_W = 304;
    static final int ROUND_H = 245;
    /** The hand boxes (mod 9..78 / 81..150 x 37..92, 70x56) and the board band (mod 9..150 x 94..129, 142x36). */
    static final int DEALER_X = -55;
    static final int BOARD_X = 17;
    static final int PLAYER_X = 161;
    static final int BOX_Y = 13;
    static final int HAND_W = 70;
    static final int HAND_H = 56;
    static final int BOARD_W = 142;
    static final int BOARD_H = 36;
    /** The stake band (mod 9..150 x 131..149) stretched over all three: window x STAKE_X0..STAKE_X1 - 1, 19 rows. */
    static final int STAKE_X0 = DEALER_X;
    static final int STAKE_X1 = PLAYER_X + HAND_W;
    static final int STAKE_Y = 71;
    static final int STAKE_H = 19;
    /** The timer bar's dark slot (the mod's rows 165..169, x 11..148): window x 19..156, y 101..105. */
    static final int BAR_SLOT_X = 19;
    static final int BAR_SLOT_Y = 101;

    private PokerGuiArt() {
    }

    /** Overwrites a rectangle {x, y, w, h} with plain felt rows taken from {@code feltY} downwards. */
    private static void erase(BufferedImage mod, int[] r, int feltY) {
        for (int y = 0; y < r[3]; y++) {
            Images.copy(mod, r[0], feltY + y, r[2], 1, mod, r[0], r[1] + y);
        }
    }

    static BufferedImage bet(BufferedImage source) {
        BufferedImage original = Images.crop(Images.argb(source), 0, 0, source.getWidth(), source.getHeight());
        BufferedImage mod = Images.crop(original, 0, 0, original.getWidth(), original.getHeight());
        // bet frames and laurels: felt over the old place (rows 84..109; rows 112.. are plain felt down to the
        // panel border, the mod draws its DEAL button there), then the moved copies
        int[] leftLaurel = BlackjackGuiArt.box(original, 40, 84, FRAME_X, 110);
        int[] rightLaurel = BlackjackGuiArt.box(original, FRAME_X + 2 * FRAME_STEP + FRAME_SIZE, 84, 186, 110);
        MachineGuiArt.felt(mod, 40, 84, 146, 26, 112, 26);
        Images.copy(original, leftLaurel[0], leftLaurel[1], leftLaurel[2], leftLaurel[3], mod,
                leftLaurel[0] + FRAME_DX[0], leftLaurel[1] + FRAME_DY);
        Images.copy(original, rightLaurel[0], rightLaurel[1], rightLaurel[2], rightLaurel[3], mod,
                rightLaurel[0] + FRAME_DX[2], rightLaurel[1] + FRAME_DY);
        for (int i = 0; i < 3; i++) {
            int x = FRAME_X + FRAME_STEP * i;
            Images.copy(original, x, FRAME_Y, FRAME_SIZE, FRAME_SIZE, mod, x + FRAME_DX[i], FRAME_Y + FRAME_DY);
        }
        // bottom corner ornaments out (pasted back after the cuts)
        int[] inner = BlackjackGuiArt.box(mod, 205, 254, 221, 263);
        int[] left = BlackjackGuiArt.box(mod, 236, 254, 252, 263);
        int[] right = BlackjackGuiArt.box(mod, 346, 254, 362, 263);
        BufferedImage innerOrnament = Images.crop(mod, inner[0], inner[1], inner[2], inner[3]);
        BufferedImage leftOrnament = Images.crop(mod, left[0], left[1], left[2], left[3]);
        BufferedImage rightOrnament = Images.crop(mod, right[0], right[1], right[2], right[3]);
        erase(mod, inner, ORNAMENT_FELT_Y);
        erase(mod, left, ORNAMENT_FELT_Y);
        erase(mod, right, ORNAMENT_FELT_Y);
        // no lettering in the poker payout panel: an empty block
        BufferedImage out = ART.compose(mod, new int[4]);
        Images.copy(innerOrnament, 0, 0, inner[2], inner[3], out, ART.x(221) - (221 - inner[0]), ART.y(inner[1]));
        Images.copy(leftOrnament, 0, 0, left[2], left[3], out, ART.panelFrom() + (left[0] - 232), left[1]);
        Images.copy(rightOrnament, 0, 0, right[2], right[3], out, ART.panelTo() - (366 - right[0]), right[1]);
        return out;
    }

    /** The round view's background, {@link #ROUND_W} x {@link #ROUND_H}, top-left at window (ROUND_X, ROUND_Y). */
    static BufferedImage round(BufferedImage panelSource, BufferedImage betSource) {
        BufferedImage panel = Images.argb(panelSource);
        BufferedImage bet = Images.argb(betSource);
        if (panel.getWidth() != 160 || panel.getHeight() != 196) {
            throw new IllegalStateException("unexpected panel size " + panel.getWidth() + "x" + panel.getHeight());
        }
        BufferedImage out = Images.blank(ROUND_W, ROUND_H);
        // felt: the panel's plain rows 150..164 (x 5..154) tiled
        BufferedImage felt = Images.crop(panel, 5, 150, 150, 15);
        for (int y = 0; y < ROUND_H; y++) {
            for (int x = 0; x < ROUND_W; x++) {
                out.setRGB(x, y, felt.getRGB(x % 150, (y + 7 * (x / 150)) % 15));
            }
        }
        // frame: corners (16 px with the ornaments at the top, 8 px at the bottom), straight parts tiled
        int w = ROUND_W;
        int h = ROUND_H;
        for (int x = 8; x < w - 8; x++) {
            Images.copy(panel, 16 + (x - 8) % 128, 0, 1, 5, out, x, 0);
            Images.copy(panel, 16 + (x - 8) % 128, 191, 1, 5, out, x, h - 5);
        }
        for (int y = 16; y < h - 8; y++) {
            Images.copy(panel, 0, 16 + (y - 16) % 160, 4, 1, out, 0, y);
            Images.copy(panel, 156, 16 + (y - 16) % 160, 4, 1, out, w - 4, y);
        }
        Images.copy(panel, 0, 0, 16, 16, out, 0, 0);
        Images.copy(panel, 144, 0, 16, 16, out, w - 16, 0);
        Images.copy(panel, 0, 188, 8, 8, out, 0, h - 8);
        Images.copy(panel, 152, 188, 8, 8, out, w - 8, h - 8);
        // the two hand boxes and the board band, unchanged
        Images.copy(panel, 9, 37, HAND_W, HAND_H, out, DEALER_X - ROUND_X, BOX_Y - ROUND_Y);
        Images.copy(panel, 81, 37, HAND_W, HAND_H, out, PLAYER_X - ROUND_X, BOX_Y - ROUND_Y);
        Images.copy(panel, 9, 94, BOARD_W, BOARD_H, out, BOARD_X - ROUND_X, BOX_Y - ROUND_Y);
        // the stake band: its 4 edge columns on each side kept, the inside (mod x 13..146) tiled across
        int bandW = STAKE_X1 - STAKE_X0;
        for (int r = 0; r < STAKE_H; r++) {
            for (int x = 0; x < bandW; x++) {
                int sx = x < 4 ? 9 + x : x >= bandW - 4 ? 150 - (bandW - 1 - x) : 13 + (x - 4) % 134;
                out.setRGB(STAKE_X0 - ROUND_X + x, STAKE_Y - ROUND_Y + r, panel.getRGB(sx, 131 + r));
            }
        }
        // the timer bar's slot (mod rows 165..169, x 11..148)
        Images.copy(panel, 11, 165, 138, 5, out, BAR_SLOT_X - ROUND_X, BAR_SLOT_Y - ROUND_Y);
        // the inventory slot art of the bet screen (mod (30, 175) 162x54 and the hotbar (30, 233) 162x18)
        Images.copy(bet, 30, 175, 162, 54, out, 7 - ROUND_X, 139 - ROUND_Y);
        Images.copy(bet, 30, 233, 162, 18, out, 7 - ROUND_X, 197 - ROUND_Y);
        return out;
    }
}
