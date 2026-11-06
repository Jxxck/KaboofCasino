package de.tigges.kaboofcasino.packtool;

import java.awt.image.BufferedImage;

/** Finds the gold lettering ("FORTUNE", "PLINKO", "JACKPOT") at the bottom of a payout panel (mod px). */
final class Lettering {

    private Lettering() {
    }

    /** True for the dark green felt of the mod's GUIs. */
    static boolean felt(int argb) {
        int r = argb >> 16 & 255;
        int g = argb >> 8 & 255;
        int b = argb & 255;
        return g >= r && g >= b && r < 80 && g < 110;
    }

    /** Bounding box {x, y, w, h} of non-felt pixels in the panel's interior rows 222..252 (x 236..360). */
    static int[] find(BufferedImage mod) {
        int x0 = Integer.MAX_VALUE;
        int y0 = Integer.MAX_VALUE;
        int x1 = -1;
        int y1 = -1;
        for (int y = 222; y < 253; y++) {
            for (int x = 236; x < 361; x++) {
                int p = mod.getRGB(x, y);
                if ((p >>> 24) != 0 && !felt(p)) {
                    x0 = Math.min(x0, x);
                    y0 = Math.min(y0, y);
                    x1 = Math.max(x1, x);
                    y1 = Math.max(y1, y);
                }
            }
        }
        if (x1 < 0) {
            throw new IllegalStateException("no lettering found in the payout panel");
        }
        return new int[] {x0, y0, x1 - x0 + 1, y1 - y0 + 1};
    }
}
