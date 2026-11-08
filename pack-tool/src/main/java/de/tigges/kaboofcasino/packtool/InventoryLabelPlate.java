package de.tigges.kaboofcasino.packtool;

import java.awt.image.BufferedImage;

/**
 * A light plate behind vanilla's player-inventory label in every felt menu title.
 *
 * <p>The 26.3 client draws the label ({@code container.inventory}, "Inventar") over the title art in #404040, without
 * shadow, at window ({@code inventoryLabelX}, {@code inventoryLabelY}) = (8, imageHeight - 94) = (8, 128) of a 9x6
 * chest (222 px): cap tops at y 128, x-height rows 130..134, descenders on 135. On the mod's dark felt that label is
 * unreadable, so every title art gets this plate under it, in the style of the mod's buttons (a gold
 * {@code DerbyUiArt.button} border, a dark shadow line under it) with a cream fill (the mod's text cream and its
 * roulette chips' cream insert as the lower shade):
 * <pre>
 *   x  5 ............................................ 59
 *   y 126  gold border (where the gold box ends above it, the plate hangs from it)
 *     127  cream (1 px above the cap tops)
 *     128..134  cream, the label's rows
 *     135  insert cream (descenders: the "y" of "Inventory")
 *     136  gold border
 *     137  dark shadow
 * </pre>
 * The inner width 6..58 holds "Inventar" (8..50) and also "Inventory", "Inventaire" or "Inventario" (up to 58).
 * Rows 138 and below stay felt: the inventory slot frames start at 139. The plate is the same in every menu, so the
 * label looks the same in each.
 */
final class InventoryLabelPlate {

    /** The vanilla label's position and colour (window coordinates of a 9x6 chest). */
    static final int LABEL_X = 8;
    static final int LABEL_Y = 222 - 94;
    static final int LABEL_COLOUR = 0xFF404040;
    /** The plate, window coordinates, inclusive. */
    static final int X0 = 5;
    static final int X1 = 59;
    static final int Y0 = 126;
    static final int Y1 = 137;
    /** The mod's colours: button border, button outline, text cream, roulette chip insert. */
    static final int BORDER = 0xFF000000 | -4878005;
    static final int SHADOW = 0xFF000000 | -15719140;
    static final int FILL = 0xFF000000 | DerbyGuiArt.CREAM;
    static final int SHADE = ChipPack.INSERT;

    private InventoryLabelPlate() {
    }

    /** The plate's colour at window (x, y), or 0 outside it. */
    static int colour(int x, int y) {
        if (x < X0 || x > X1 || y < Y0 || y > Y1) {
            return 0;
        }
        if (y == Y1) {
            return SHADOW;
        }
        if (y == Y0 || y == Y1 - 1 || x == X0 || x == X1) {
            return BORDER;
        }
        return y == Y1 - 2 ? SHADE : FILL;
    }

    /** Draws the plate onto {@code art} whose pixel (0, 0) lies at window ({@code artX}, {@code artY}). */
    static void draw(BufferedImage art, int artX, int artY) {
        for (int y = Y0; y <= Y1; y++) {
            for (int x = X0; x <= X1; x++) {
                int px = x - artX;
                int py = y - artY;
                if (px < 0 || py < 0 || px >= art.getWidth() || py >= art.getHeight()) {
                    throw new IllegalStateException("the art at " + artX + "," + artY + " does not cover the label "
                            + "plate at " + x + "," + y);
                }
                art.setRGB(px, py, colour(x, y));
            }
        }
    }
}
