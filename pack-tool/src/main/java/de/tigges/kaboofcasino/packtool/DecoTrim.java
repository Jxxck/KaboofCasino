package de.tigges.kaboofcasino.packtool;

import java.awt.image.BufferedImage;

/**
 * The patterns of the dark oak wall blocks on stairs and slabs (not in MineVegas). A block's side texture is drawn for a
 * whole 16 x 16 face, so a smaller face cut from it shows a piece of the pattern. These textures are stitched from the
 * original's own rows and columns (nothing painted): every 16 x 8 cell of {@link #half} and every 8 x 8 cell of
 * {@link #quarter} holds the whole pattern, at the place a face of that size takes by its default UVs. Columns of
 * {@link #half} stay the original's, so its vertical lines meet a full block's.
 */
final class DecoTrim {

    /** A small face keeps the two dark lines of the panelling at its edges. */
    private static final int[] LINES = {2, 3, 5, 6, 8, 9, 12, 13};

    /** Which 8 rows, and which 8 columns for the small faces, carry a pattern. */
    enum Pattern {
        /** Shadow, gold, shadow, two of the panel, shadow, gold, shadow. */
        FRAME(new int[] {1, 2, 3, 5, 6, 12, 13, 14}, new int[] {2, 3, 4, 6, 7, 11, 12, 13}),
        PLAIN(new int[] {8, 9, 10, 11, 12, 13, 14, 15}, LINES),
        /** The lower half: the gold band over the dark foot. */
        BASEBOARD(new int[] {8, 9, 10, 11, 12, 13, 14, 15}, LINES),
        /** The gold band in the middle. */
        DADO(new int[] {4, 5, 6, 7, 8, 9, 10, 11}, LINES),
        /** The upper half: the gold band under the dark top. */
        CORNICE(new int[] {0, 1, 2, 3, 4, 5, 6, 7}, LINES),
        /** Both gold bars, side by side on a small face. */
        PILASTER(new int[] {0, 1, 2, 3, 4, 5, 6, 7}, new int[] {2, 3, 4, 5, 9, 10, 11, 12}),
        /** The medallion, flatter on a half face, smaller on a small one. */
        DIAMOND(new int[] {3, 4, 6, 7, 8, 9, 11, 12}, new int[] {3, 4, 6, 7, 8, 9, 11, 12});

        final int[] rows;
        final int[] columns;

        Pattern(int[] rows, int[] columns) {
            this.rows = rows;
            this.columns = columns;
        }

        /** The pattern of a block by its name. */
        static Pattern of(String family) {
            if (family.contains("gilded")) {
                return FRAME;
            }
            if (family.endsWith("baseboard")) {
                return BASEBOARD;
            }
            if (family.endsWith("dado_rail")) {
                return DADO;
            }
            if (family.endsWith("cornice")) {
                return CORNICE;
            }
            if (family.endsWith("pilaster")) {
                return PILASTER;
            }
            return family.endsWith("diamond_panel") ? DIAMOND : PLAIN;
        }
    }

    private DecoTrim() {
    }

    /** Two whole 16 x 8 patterns, one above the other. */
    static BufferedImage half(BufferedImage side, Pattern p) {
        BufferedImage out = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                out.setRGB(x, y, side.getRGB(x, p.rows[y % 8]));
            }
        }
        return out;
    }

    /** Four whole 8 x 8 patterns. */
    static BufferedImage quarter(BufferedImage side, Pattern p) {
        BufferedImage out = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                out.setRGB(x, y, side.getRGB(p.columns[x % 8], p.rows[y % 8]));
            }
        }
        return out;
    }
}
