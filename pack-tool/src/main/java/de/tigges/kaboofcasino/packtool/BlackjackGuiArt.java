package de.tigges.kaboofcasino.packtool;

import java.awt.image.BufferedImage;

/**
 * The two backgrounds of the blackjack screens.
 *
 * <p><b>Bet view</b> ({@code gui/blackjack.png}, 366x266) through {@link MachineGuiArt} like the other 3x3 screens:
 * the three bet frames move by (-5 / 0 / +5, -4) px onto grid slots (2, 3) / (4, 3) / (6, 3) (the mod's slots at
 * mod (72 / 103 / 134, 96)), the laurels move with the outer frames. The payout panel loses 15 plain columns, all
 * outside its text: 4 felt columns between frame and boxes on each side (236..239, 357..360), 2 of the boxes' left
 * margin (242..243), 3 between the icons and the texts (276..278) and 2 right of the texts (351..352); the header line
 * and the boxes stay centred, "Mehr oder 21" (68 px) keeps its full room. The panel's bottom corner ornaments and its
 * "BLACKJACK" lettering (with its dark outline) are lifted out first and pasted back at the same distance from the
 * frame / centred.
 *
 * <p><b>Round view</b>: the mod's side panel ({@code gui/blackjack_panel.png}, 160x196) re-built 288 px wide around
 * the chest (window x -56..231, y -14..230): frame and corner ornaments, the dealer band (window y 0..35), the player
 * band (36..70) and the stake band (71..89) of the mod's band art, the timer bar slot (window 19..156 x 101..105) and
 * the mod's inventory slot art (window 7..168 x 139..214) under the vanilla inventory. The labels sit in the bands'
 * left part, the totals in their right part, so the 32 px card rows fit the 9 px above the chest at 427x240.
 *
 * <p>Both get the {@link InventoryLabelPlate} under vanilla's "Inventar" label (the bet view through
 * {@link MachineGuiArt#compose}).
 */
final class BlackjackGuiArt {

    static final MachineGuiArt ART = new MachineGuiArt(new int[][] {{236, 4}, {242, 2}, {276, 3}, {351, 2},
            {357, 4}});
    static final int[] FRAME_DX = {-5, 0, 5};
    static final int FRAME_DY = -4;
    /** The lettering moves this far right before the cuts so the felt source columns of the re-centring are plain. */
    static final int LETTERING_SHIFT = 3;

    // ---- round panel (window coordinates) ---------------------------------------------------------------------------
    static final int ROUND_X = -56;
    static final int ROUND_Y = -14;
    static final int ROUND_W = 288;
    static final int ROUND_H = 245;
    /** Bands: window top, height (dealer, player, stake). */
    static final int[][] BANDS = {{0, 36}, {36, 35}, {71, 19}};
    /** Bands run from window x BAND_X0 (inclusive) to BAND_X1 (exclusive), 9 px inside the panel like the mod's. */
    static final int BAND_X0 = ROUND_X + 9;
    static final int BAND_X1 = ROUND_X + ROUND_W - 9;
    /** The timer bar's dark slot (the mod's rows 165..169, x 11..148): window x 19..156, y 101..105. */
    static final int BAR_SLOT_X = 19;
    static final int BAR_SLOT_Y = 101;

    private BlackjackGuiArt() {
    }

    private static boolean felt(int argb) {
        return Lettering.felt(argb) && argb != 0xFF14291F;
    }

    /** Bounding box {x, y, w, h} of non-felt pixels (the lettering outline counts) in a mod rectangle. */
    static int[] box(BufferedImage mod, int x0, int y0, int x1, int y1) {
        int bx0 = Integer.MAX_VALUE;
        int by0 = Integer.MAX_VALUE;
        int bx1 = -1;
        int by1 = -1;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                if (!felt(mod.getRGB(x, y))) {
                    bx0 = Math.min(bx0, x);
                    by0 = Math.min(by0, y);
                    bx1 = Math.max(bx1, x);
                    by1 = Math.max(by1, y);
                }
            }
        }
        if (bx1 < 0) {
            throw new IllegalStateException("nothing found in " + x0 + "," + y0 + ".." + x1 + "," + y1);
        }
        return new int[] {bx0, by0, bx1 - bx0 + 1, by1 - by0 + 1};
    }

    /** Overwrites a rectangle with plain felt rows taken from {@code feltY} downwards in the same columns. */
    private static void erase(BufferedImage mod, int[] r, int feltY) {
        for (int y = 0; y < r[3]; y++) {
            Images.copy(mod, r[0], feltY + y, r[2], 1, mod, r[0], r[1] + y);
        }
    }

    static BufferedImage bet(BufferedImage source) {
        BufferedImage mod = Images.argb(source);
        BufferedImage original = Images.crop(mod, 0, 0, mod.getWidth(), mod.getHeight());
        // bet frames (22 px at mod 69 + 31 i, 93) and laurels: felt over the old place, then the moved copies
        MachineGuiArt.felt(mod, 40, 88, 145, 30, 120, 20);
        Images.copy(original, 46, 96, 23, 20, mod, 46 + FRAME_DX[0], 96 + FRAME_DY);
        Images.copy(original, 153, 96, 23, 20, mod, 153 + FRAME_DX[2], 96 + FRAME_DY);
        for (int i = 0; i < 3; i++) {
            Images.copy(original, 69 + 31 * i, 93, 22, 22, mod, 69 + 31 * i + FRAME_DX[i], 93 + FRAME_DY);
        }
        // payout panel: lettering and bottom corner ornaments out (pasted back after the cuts)
        int[] lettering = box(mod, 236, 222, 361, 253);
        BufferedImage letters = Images.crop(mod, lettering[0], lettering[1], lettering[2], lettering[3]);
        erase(mod, lettering, 244);
        int[] moved = {lettering[0] + LETTERING_SHIFT, lettering[1], lettering[2], lettering[3]};
        Images.copy(letters, 0, 0, moved[2], moved[3], mod, moved[0], moved[1]);
        int[] left = box(mod, 236, 254, 252, 263);
        int[] right = box(mod, 346, 254, 362, 263);
        BufferedImage leftOrnament = Images.crop(mod, left[0], left[1], left[2], left[3]);
        BufferedImage rightOrnament = Images.crop(mod, right[0], right[1], right[2], right[3]);
        erase(mod, left, 248);
        erase(mod, right, 248);
        BufferedImage out = ART.compose(mod, moved);
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
        // bands: top edge, interior rows, bottom edge of the mod's band art, tiled across
        int[][] source = {{40, 75}, {90, 125}, {130, 148}};
        for (int b = 0; b < BANDS.length; b++) {
            int top = BANDS[b][0] - ROUND_Y;
            int rows = BANDS[b][1];
            for (int r = 0; r < rows; r++) {
                int sy = r == 0 ? source[b][0] : r == rows - 1 ? source[b][1] : source[b][0] + 1
                        + (r - 1) % (source[b][1] - source[b][0] - 1);
                for (int x = BAND_X0 - ROUND_X; x < BAND_X1 - ROUND_X; x++) {
                    out.setRGB(x, top + r, panel.getRGB(9 + (x - (BAND_X0 - ROUND_X)) % 142, sy));
                }
            }
        }
        // the timer bar's slot (mod rows 165..169, x 11..148)
        Images.copy(panel, 11, 165, 138, 5, out, BAR_SLOT_X - ROUND_X, BAR_SLOT_Y - ROUND_Y);
        // the inventory slot art of the bet screen (mod (30, 175) 162x54 and the hotbar (30, 233) 162x18)
        Images.copy(bet, 30, 175, 162, 54, out, 7 - ROUND_X, 139 - ROUND_Y);
        Images.copy(bet, 30, 233, 162, 18, out, 7 - ROUND_X, 197 - ROUND_Y);
        InventoryLabelPlate.draw(out, ROUND_X, ROUND_Y);
        return out;
    }

    /** Cuts {@code art} into 2x2 glyph tiles (each at most 256 px) with its top-left at window (x0, y0). */
    static void tiles(GuiFont font, String prefix, BufferedImage art, int x0, int y0) {
        int tw = (art.getWidth() + 1) / 2;
        int th = (art.getHeight() + 1) / 2;
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                int w = Math.min(tw, art.getWidth() - col * tw);
                int h = Math.min(th, art.getHeight() - row * th);
                BufferedImage tile = Images.crop(art, col * tw, row * th, w, h);
                font.art(prefix + "_bg_" + col + "_" + row, tile, "font/" + prefix + "/bg_" + col + "_" + row + ".png",
                        x0 + col * tw, y0 + row * th);
            }
        }
    }
}
