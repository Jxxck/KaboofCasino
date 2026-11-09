package de.tigges.kaboofcasino.packtool;

import java.awt.image.BufferedImage;

/**
 * The trim of the gilded dark oak blocks on stairs and slabs (not in MineVegas). The blocks' side texture is one gold
 * frame on a whole 16 x 16 face, so a half-height face cut from it shows an open piece of the frame. These two textures
 * are stitched from the original's own rows and columns (frame and a little of the panel, nothing painted): every 16 x 8
 * cell of {@link #half} and every 8 x 8 cell of {@link #quarter} holds a closed frame, at the place a face of that size
 * takes by its default UVs. Columns of {@link #half} stay the original's, so its frame lines up with a full block.
 */
final class DecoTrim {

    /** The rows (and columns) an 8-pixel frame keeps: shadow, gold, shadow, two of the panel, shadow, gold, shadow. */
    static final int[] ROWS = {1, 2, 3, 5, 6, 12, 13, 14};
    static final int[] COLUMNS = {2, 3, 4, 6, 7, 11, 12, 13};

    private DecoTrim() {
    }

    /** Two closed 16 x 8 frames, one above the other. */
    static BufferedImage half(BufferedImage side) {
        BufferedImage out = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                out.setRGB(x, y, side.getRGB(x, ROWS[y % 8]));
            }
        }
        return out;
    }

    /** Four closed 8 x 8 frames. */
    static BufferedImage quarter(BufferedImage side) {
        BufferedImage out = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                out.setRGB(x, y, side.getRGB(COLUMNS[x % 8], ROWS[y % 8]));
            }
        }
        return out;
    }
}
