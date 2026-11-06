package de.tigges.kaboofcasino.packtool;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Re-composition of the mod's 366x266 screens of the 3x3 machines (fortune wheel, Plinko) around a vanilla 9x6 chest
 * (render.md 5.2), so they fit the common scaled screens 480x270, 456x256 and 427x240 (125 px beside the chest, 9 px
 * above it at 240):
 * <ul>
 *   <li>rows of the left panel: everything down to mod y 142 is drawn 20 px higher than the mod's inventory
 *       alignment (window = mod + (x, -20): the headline at window -8 stays on screen, the bet slots land on grid
 *       rows); the plain felt rows 143..155 under the play button and 160..162 above the inventory label are
 *       removed, so the upper panel's bottom border (156..159) ends just above vanilla's "Inventar" label and the
 *       inventory art keeps window = mod - 36. The payout panel keeps all its rows at window = mod - 20 (only its
 *       outer frame is cut at 240 px height);</li>
 *   <li>columns: the left panel loses its 19 plain columns 192..210, the transparent gap 224..231 goes, and the
 *       payout panel loses the plain columns the game names (its bottom lettering is pasted back centred).</li>
 * </ul>
 * The play button (drawn by the mod's code, not part of the texture) becomes a 72x14 item over grid slot (4, 5).
 */
final class MachineGuiArt {

    static final int ART_X = -23;
    static final int ART_Y = -20;
    static final int MOD_W = 366;
    static final int MOD_H = 266;
    /** Last row kept at the upper offset; rows removed below it. */
    static final int[][] ROW_CUTS = {{143, 13}, {160, 3}};
    static final int[][] LEFT_CUTS = {{192, 19}, {224, 8}};

    private final int[] keptCols;
    private final int[] keptRows;
    private final int panelFrom;

    /** @param panelCuts {from, count} column ranges of the payout panel (mod x) to remove */
    MachineGuiArt(int[][] panelCuts) {
        List<int[]> cuts = new ArrayList<>(List.of(LEFT_CUTS));
        cuts.addAll(List.of(panelCuts));
        this.keptCols = kept(MOD_W, cuts);
        this.keptRows = kept(MOD_H, List.of(ROW_CUTS));
        this.panelFrom = x(232);
    }

    private static int[] kept(int size, List<int[]> cuts) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            boolean cut = false;
            for (int[] c : cuts) {
                cut |= i >= c[0] && i < c[0] + c[1];
            }
            if (!cut) {
                out.add(i);
            }
        }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    int width() {
        return keptCols.length;
    }

    int height() {
        return MOD_H;
    }

    /** Window y of a mod row of the payout panel (no rows removed there). */
    int panelWindowY(int my) {
        return my + ART_Y;
    }

    /** Composite x of mod column {@code mx} (the next kept column if it was cut). */
    int x(int mx) {
        int i = 0;
        while (i < keptCols.length && keptCols[i] < mx) {
            i++;
        }
        return i;
    }

    int y(int my) {
        int i = 0;
        while (i < keptRows.length && keptRows[i] < my) {
            i++;
        }
        return i;
    }

    /** Window coordinates (chest frame) of a mod position. */
    int windowX(int mx) {
        return x(mx) + ART_X;
    }

    int windowY(int my) {
        return y(my) + ART_Y;
    }

    /** First and one-past-last composite column of the payout panel. */
    int panelFrom() {
        return panelFrom;
    }

    int panelTo() {
        return keptCols.length;
    }

    /**
     * Applies the cuts to {@code mod} (already prepared by the game's own moves) and pastes the payout panel's
     * lettering ({@code text} = {x, y, w, h} in mod pixels) back centred in the narrower panel.
     */
    BufferedImage compose(BufferedImage mod, int[] text) {
        if (mod.getWidth() != MOD_W || mod.getHeight() != MOD_H) {
            throw new IllegalStateException("unexpected GUI size " + mod.getWidth() + "x" + mod.getHeight());
        }
        for (int x = 224; x < 232; x++) {
            for (int y = 0; y < MOD_H; y++) {
                if ((mod.getRGB(x, y) >>> 24) != 0) {
                    throw new IllegalStateException("column " + x + " of the GUI is not transparent");
                }
            }
        }
        BufferedImage out = Images.blank(width(), height());
        for (int cx = 0; cx < keptCols.length; cx++) {
            boolean left = keptCols[cx] < 224;
            int rows = left ? keptRows.length : MOD_H;
            for (int cy = 0; cy < rows; cy++) {
                out.setRGB(cx, cy, mod.getRGB(keptCols[cx], left ? keptRows[cy] : cy));
            }
        }
        // the lettering may have lost columns: wipe its band with the panel's own felt (16 plain columns at the
        // panel's left inside), then paste the original block centred in the narrower panel
        int panelW = panelTo() - panelFrom();
        int ty = text[1];
        int feltX = panelFrom() + 6;
        for (int x = panelFrom() + 6; x < panelTo() - 6; x++) {
            Images.copy(out, feltX + (x - feltX) % 16, ty, 1, text[3], out, x, ty);
        }
        int tx = panelFrom() + (panelW - text[2]) / 2;
        Images.copy(mod, text[0], text[1], text[2], text[3], out, tx, ty);
        return out;
    }

    /** Fills a rectangle with plain felt copied row by row from the felt band starting at {@code feltY}. */
    static void felt(BufferedImage img, int x, int y, int w, int h, int feltY, int feltRows) {
        BufferedImage band = Images.crop(img, x, feltY, w, feltRows);
        for (int r = 0; r < h; r++) {
            Images.copy(band, 0, r % feltRows, w, 1, img, x, y + r);
        }
    }

    /** Cuts the composite into 2x2 glyph tiles (each at most 256 px) at window (ART_X, ART_Y). */
    static void tiles(GuiFont font, String prefix, BufferedImage art) {
        int tw = (art.getWidth() + 1) / 2;
        int th = (art.getHeight() + 1) / 2;
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                int w = Math.min(tw, art.getWidth() - col * tw);
                int h = Math.min(th, art.getHeight() - row * th);
                BufferedImage tile = Images.crop(art, col * tw, row * th, w, h);
                font.art(prefix + "_bg_" + col + "_" + row, tile,
                        "font/" + prefix + "/bg_" + col + "_" + row + ".png", ART_X + col * tw, ART_Y + row * th);
            }
        }
    }
}
