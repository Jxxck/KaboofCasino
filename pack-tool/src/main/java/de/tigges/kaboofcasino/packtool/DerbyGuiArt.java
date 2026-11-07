package de.tigges.kaboofcasino.packtool;

import java.awt.image.BufferedImage;

/**
 * The duck derby screen's drawing (mod {@code DerbyUiArt} and {@code DuckDerbyScreen.extractBackground}) and its
 * re-composition around a vanilla 9x6 chest.
 *
 * <p>The mod draws almost everything with code on a plain felt texture ({@code gui/duck_derby_felt.png}, 366x266: the
 * left panel 0..223 with a gold box 5..218 x 19..161, the right panel 232..365). The chest version (window
 * coordinates; see {@code DerbyGui} for what lies where):
 * <ul>
 *   <li>left panel at window x -14..189 (the mod's columns 102..121 removed), right panel at 196..300 (columns
 *       276..304 removed), both from window y -20 to 229: rows 4..14 and 100..102 of the box interior and 200..201
 *       below it removed, so the gold box runs from -12 to 127 around cards, bets, buttons and the lobby strip, and
 *       its bottom border ends just above vanilla's "Inventar" label;</li>
 *   <li>the four duck cards (42x37, the mod's {@code CARD_W}/{@code CARD_H}) at window y -4, x -1 / 45 / 89 / 135: the
 *       bet columns are grid columns 0 / 3 / 5 / 8, so the cards keep 2 px between the middle pair and 4 px to the
 *       outer ones, each 4 px off its bet column (the mod: 10 px apart, centred);</li>
 *   <li>bet slot frames on grid rows 1..3 of those columns, the inventory slot frames under the vanilla slots.</li>
 * </ul>
 * The cards are drawn at twice the resolution (font glyphs with {@code height} = half the texels): the mod scales the
 * ducks by 1.5 and shrinks "SCHWARZ" (42 px) into its 40 px room, which half pixels show at GUI scale 2 and 4.
 */
final class DerbyGuiArt {

    // ---- the mod's colours (DerbyUiArt, DuckDerbyScreen) ------------------------------------------------------------
    static final int FELT = -14660805;
    static final int INK = -14271439;
    static final int CREAM = -792641;
    static final int GREY = -8153987;
    static final int VEIL = -1271841994;
    static final int GOLD_LINE = -1654161;
    static final int CARD_OUTER = -15717856;
    static final int CARD_INNER = -13609408;
    static final int DIVIDER = -8230341;
    static final int BRAND = -1389181;
    static final int SEVERAL = -30350;
    static final int WAITING = -4864602;
    static final int MEMBER_OUTER = -15585500;
    static final int MEMBER_INNER = -14135237;
    static final int[] BANNER = {-1918110, -7979973, -2895947, -13352385};
    static final int[] NAME_COLOR = {2825480, 16379073, 2827544, 15980682};
    static final boolean[] NAME_SHADOW = {false, true, false, true};
    static final int[] HINT_COLOR = {16765000, 16740464, 16773846, 11187916};
    static final int[] DUCK_LIGHT = {-8600, -2267552, -791078, -10063757};
    static final int[] DUCK_BODY = {-1460161, -5489091, -2896198, -13023670};
    static final int[] DUCK_SHADE = {-4751574, -8705233, -5593713, -14470602};

    // ---- the chest layout (window coordinates) ----------------------------------------------------------------------
    /** Top-left of the background art and its size. */
    static final int ART_X = -14;
    static final int ART_Y = -20;
    static final int ART_W = 315;
    static final int ART_H = 250;
    /** The left panel's window columns (mod 0..223 minus 102..121) and the right panel's (mod 232..365 minus 276..304). */
    static final int LEFT_X = -14;
    static final int LEFT_W = 204;
    static final int RIGHT_X = 196;
    static final int RIGHT_W = 105;
    static final int[] LEFT_CUT = {102, 20};
    static final int[] RIGHT_CUT = {276, 29};
    /** Removed mod rows {from, count} (both panels). */
    static final int[][] ROW_CUTS = {{4, 11}, {100, 3}, {200, 2}};
    /** Grid columns of the four bet columns, the first bet row, the cards. */
    static final int[] LANE_COLUMNS = {0, 3, 5, 8};
    static final int FIRST_ROW = 1;
    static final int[] CARD_X = {-1, 45, 89, 135};
    static final int CARD_Y = -4;
    static final int CARD_W = 42;
    static final int CARD_H = 37;
    /** The right panel's centre line and its divider (the mod's 240..358 at y 51, 8 px inside the panel). */
    static final int RIGHT_CENTRE = RIGHT_X + RIGHT_W / 2;
    static final int DIVIDER_Y = 27;

    private DerbyGuiArt() {
    }

    /** Window x of the item area of top slot {@code slot}. */
    static int slotX(int slot) {
        return 8 + 18 * (slot % 9);
    }

    static int slotY(int slot) {
        return 18 + 18 * (slot / 9);
    }

    /** Grid slot of bet slot {@code i} (lane {@code i % 4}, row {@code i / 4}). */
    static int betSlot(int i) {
        return (FIRST_ROW + i / 4) * 9 + LANE_COLUMNS[i % 4];
    }

    // ---- drawing (GuiGraphics.fill on a transformed pose: pixel centres decide, like the rasteriser) -----------------

    /** Draws onto {@code target} whose pixel (0, 0) is window ({@code ox}, {@code oy}) at {@code res} texels per px. */
    record Canvas(BufferedImage target, double ox, double oy, int res) {

        /** {@code g.fill(x0, y0, x1, y1)} under a pose translated to (tx, ty) and scaled by s (window units). */
        void fill(double tx, double ty, double s, double x0, double y0, double x1, double y1, int argb) {
            double ax0 = (tx + x0 * s - ox) * res;
            double ay0 = (ty + y0 * s - oy) * res;
            double ax1 = (tx + x1 * s - ox) * res;
            double ay1 = (ty + y1 * s - oy) * res;
            for (int py = (int) Math.floor(ay0); py < Math.ceil(ay1); py++) {
                for (int px = (int) Math.floor(ax0); px < Math.ceil(ax1); px++) {
                    if (px + 0.5 >= ax0 && px + 0.5 < ax1 && py + 0.5 >= ay0 && py + 0.5 < ay1 && px >= 0 && py >= 0
                            && px < target.getWidth() && py < target.getHeight()) {
                        Images.blend(target, px, py, argb);
                    }
                }
            }
        }

        void fill(int x0, int y0, int x1, int y1, int argb) {
            fill(0, 0, 1, x0, y0, x1, y1, argb);
        }
    }

    /** {@code DerbyUiArt.slot}: an 18x18 frame around the item area at (x, y). */
    static void slot(Canvas g, int x, int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, -15391202);
        g.fill(x, y, x + 16, y + 16, -11970227);
        g.fill(x, y + 16, x + 17, y + 17, -8285828);
        g.fill(x + 16, y, x + 17, y + 17, -8285828);
        g.fill(x, y, x + 16, y + 1, -13286601);
        g.fill(x, y, x + 1, y + 16, -13286601);
    }

    /** {@code DerbyUiArt.button}. */
    static void button(Canvas g, int x, int y, int w, int h, boolean enabled, boolean hover) {
        g.fill(x, y, x + w, y + h, -15719140);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, enabled ? -4878005 : -11773114);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, enabled ? (hover ? -12619437 : -13938371) : -14139856);
        g.fill(x + 2, y + 2, x + w - 2, y + 3, enabled ? (hover ? -9006233 : -11373737) : -12956100);
        g.fill(x + 2, y + h - 3, x + w - 2, y + h - 2, -15257821);
    }

    /** {@code DerbyUiArt.frame}: the help panel's wooden frame with gold studs. */
    static void frame(Canvas g, int x, int y, int w, int h, int fill) {
        g.fill(x, y, x + w, y + h, -15918058);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, -9021911);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, -3890595);
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, -15716829);
        g.fill(x + 4, y + 4, x + w - 4, y + h - 4, fill);
        g.fill(x + 4, y + 4, x + w - 4, y + 5, -12818864);
        g.fill(x + 4, y + h - 5, x + w - 4, y + h - 4, -15255509);
        for (int side = 0; side < 2; side++) {
            for (int end = 0; end < 2; end++) {
                int px = side == 0 ? x + 6 : x + w - 9;
                int py = end == 0 ? y + 6 : y + h - 9;
                for (int n = 0; n < 2; n++) {
                    stud(g, px + (side == 0 ? 5 * n : -5 * n), py);
                }
            }
        }
    }

    private static void stud(Canvas g, int x, int y) {
        g.fill(x, y + 1, x + 3, y + 3, -8956895);
        g.fill(x + 1, y, x + 3, y + 4, -8956895);
        g.fill(x, y + 1, x + 3, y + 2, -2839986);
        g.fill(x + 1, y, x + 2, y + 3, -1060222);
        g.fill(x + 1, y + 1, x + 2, y + 2, -5984);
    }

    /** {@code DerbyUiArt.outcome}: medal 1 / 2 or the red cross, 16x16 at (x, y). */
    static void outcome(Canvas g, TextGlyphs text, int x, int y, int rank) {
        if (rank < 2) {
            int rim = rank == 0 ? -7378645 : -11311260;
            int face = rank == 0 ? -1983903 : -4404803;
            int shine = rank == 0 ? -6496 : -1774886;
            g.fill(x + 4, y + 9, x + 7, y + 16, -7781830);
            g.fill(x + 9, y + 9, x + 12, y + 16, -7781830);
            g.fill(x + 4, y, x + 12, y + 12, rim);
            g.fill(x + 2, y + 2, x + 14, y + 10, rim);
            g.fill(x + 4, y + 1, x + 12, y + 11, face);
            g.fill(x + 3, y + 3, x + 13, y + 9, face);
            g.fill(x + 4, y + 1, x + 10, y + 2, shine);
            g.fill(x + 3, y + 3, x + 4, y + 8, shine);
            text(g, text, Integer.toString(rank + 1), x + 6, y + 2, INK, false, false);
        } else {
            for (int i = 0; i < 9; i++) {
                g.fill(x + 3 + i, y + 4 + i, x + 5 + i, y + 6 + i, -8896717);
                g.fill(x + 11 - i, y + 4 + i, x + 13 - i, y + 6 + i, -8896717);
            }
            for (int i = 0; i < 9; i++) {
                g.fill(x + 3 + i, y + 3 + i, x + 5 + i, y + 5 + i, -5481400);
                g.fill(x + 11 - i, y + 3 + i, x + 13 - i, y + 5 + i, -5481400);
            }
        }
    }

    /** {@code DerbyUiArt.duck} at (x, y), scale s. */
    static void duck(Canvas g, double x, double y, int lane, double s) {
        g.fill(x, y, s, 2, 8, 12, 13, DUCK_SHADE[lane]);
        g.fill(x, y, s, 1, 7, 4, 10, DUCK_BODY[lane]);
        g.fill(x, y, s, 4, 7, 13, 11, DUCK_BODY[lane]);
        g.fill(x, y, s, 8, 2, 13, 9, DUCK_BODY[lane]);
        g.fill(x, y, s, 9, 1, 12, 2, DUCK_BODY[lane]);
        g.fill(x, y, s, 9, 2, 12, 4, DUCK_LIGHT[lane]);
        g.fill(x, y, s, 4, 7, 8, 8, DUCK_LIGHT[lane]);
        g.fill(x, y, s, 5, 9, 9, 10, DUCK_SHADE[lane]);
        g.fill(x, y, s, 13, 5, 16, 7, -1989302);
        g.fill(x, y, s, 13, 7, 15, 8, -5936342);
        g.fill(x, y, s, 11, 4, 12, 5, -15456994);
        g.fill(x, y, s, 5, 13, 7, 14, -4749767);
        g.fill(x, y, s, 9, 13, 11, 14, -4749767);
    }

    /**
     * Text like {@code Font.drawInBatch} at window (x, top) on the canvas (shadow first, then the glyphs; bold adds a
     * copy one pixel right). At canvas resolution 2 every font pixel becomes 2x2 texels.
     */
    static void text(Canvas g, TextGlyphs glyphs, String s, int x, int top, int argb, boolean bold, boolean shadow) {
        int colour = 0xFF000000 | argb;
        if (g.res() == 1 && g.ox() == Math.floor(g.ox()) && g.oy() == Math.floor(g.oy())) {
            BlackjackGuiText.draw(g.target(), glyphs, s, x - (int) g.ox(), top - (int) g.oy(), colour, bold, shadow);
            return;
        }
        scaled(g, run(glyphs, s, colour, bold, shadow), x, top, 1.0);
    }

    /** Rows above the cap tops in a text run (accent dots of capitals). */
    static final int RUN_TOP = 3;

    /** A text image: cap tops at row {@link #RUN_TOP}, left edge at column 0. */
    static BufferedImage run(TextGlyphs glyphs, String s, int argb, boolean bold, boolean shadow) {
        BufferedImage run = Images.blank(BlackjackGuiText.width(glyphs, s, bold) + 2, RUN_TOP + 10);
        BlackjackGuiText.draw(run, glyphs, s, 0, RUN_TOP, 0xFF000000 | argb, bold, shadow);
        return run;
    }

    /**
     * Draws {@code run} (see {@link #run}) with its cap tops at window y {@code top} and its left edge at x, scaled by
     * {@code scale} about that point, sampling like the GPU (the nearest texel at each canvas pixel centre):
     * {@code CasinoText.fitted} when the text is wider than its room.
     */
    static void scaled(Canvas g, BufferedImage run, double x, double top, double scale) {
        double runTop = top - RUN_TOP * scale;
        int x0 = (int) Math.floor((x - g.ox()) * g.res());
        int y0 = (int) Math.floor((runTop - g.oy()) * g.res());
        int w = (int) Math.ceil(run.getWidth() * scale * g.res()) + 1;
        int h = (int) Math.ceil(run.getHeight() * scale * g.res()) + 1;
        for (int py = y0; py < y0 + h; py++) {
            for (int px = x0; px < x0 + w; px++) {
                double sx = ((px + 0.5) / g.res() + g.ox() - x) / scale;
                double sy = ((py + 0.5) / g.res() + g.oy() - runTop) / scale;
                if (sx < 0 || sy < 0 || sx >= run.getWidth() || sy >= run.getHeight() || px < 0 || py < 0
                        || px >= g.target().getWidth() || py >= g.target().getHeight()) {
                    continue;
                }
                int p = run.getRGB((int) sx, (int) sy);
                if ((p >>> 24) != 0) {
                    Images.blend(g.target(), px, py, p);
                }
            }
        }
    }

    /**
     * {@code CasinoText.fitted}: text centred / left / right at x (align -1, 0, 1), shrunk about its left edge to
     * {@code room} if wider ({@code y + (1 - scale) * 4}).
     */
    static void fitted(Canvas g, TextGlyphs glyphs, String s, int x, int y, int room, int argb, boolean shadow,
                       int align) {
        int width = glyphs.width(s);
        if (width <= room) {
            int left = align == 0 ? x - width / 2 : align < 0 ? x : x - width;
            text(g, glyphs, s, left, y, argb, false, shadow);
            return;
        }
        double scale = (double) room / width;
        double left = align == 0 ? x - room / 2.0 : align < 0 ? x : x - room;
        scaled(g, run(glyphs, s, argb, false, shadow), left, y + (1 - scale) * 4, scale);
    }

    /** {@code DuckDerbyScreen.veiled}: a colour blended 180/255 toward the veil colour (per channel, integer math). */
    static int veiled(int rgb) {
        int out = 0;
        for (int shift = 0; shift <= 16; shift += 8) {
            int under = rgb >> shift & 0xFF;
            int over = VEIL >> shift & 0xFF;
            out |= under + (over - under) * 180 / 255 << shift;
        }
        return out;
    }

    // ---- the cards ---------------------------------------------------------------------------------------------------

    /**
     * One duck card as {@code extractBackground} + {@code extractLabels} draw it (no gold lines: the chosen lane's lines
     * and the veil are items), at {@code res} texels per GUI pixel, its top-left at (0, 0).
     */
    static BufferedImage card(TextGlyphs text, int lane, int res) {
        BufferedImage img = Images.blank(CARD_W * res, CARD_H * res);
        Canvas g = new Canvas(img, 0, 0, res);
        g.fill(0, 0, CARD_W, CARD_H, CARD_OUTER);
        g.fill(1, 1, CARD_W - 1, CARD_H - 1, CARD_INNER);
        duck(g, 9, 2, lane, 1.5);
        g.fill(1, 25, CARD_W - 1, 36, BANNER[lane]);
        name(g, text, lane, 0, 0, false);
        return img;
    }

    /** The duck's name on its banner (centre x + 21, y + 26, room 40), bright or muted ({@code veiled}, no shadow). */
    static void name(Canvas g, TextGlyphs text, int lane, int x, int y, boolean muted) {
        int colour = muted ? veiled(NAME_COLOR[lane]) : NAME_COLOR[lane];
        fitted(g, text, DerbyGui.DUCKS[lane], x + 21, y + 26, 40, colour, NAME_SHADOW[lane] && !muted, 0);
    }

    // ---- the background ----------------------------------------------------------------------------------------------

    private static int[] kept(int from, int to, int[]... cuts) {
        java.util.List<Integer> out = new java.util.ArrayList<>();
        for (int i = from; i < to; i++) {
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

    /** Mod rows kept, in window order from {@link #ART_Y}. */
    static int[] keptRows() {
        return kept(0, 266, ROW_CUTS);
    }

    /** Window y of the kept row that came from mod row {@code my} (or the next kept one). */
    static int windowY(int my) {
        int[] rows = keptRows();
        int i = 0;
        while (i < rows.length && rows[i] < my) {
            i++;
        }
        return ART_Y + i;
    }

    /**
     * The whole background in window coordinates from ({@link #ART_X}, {@link #ART_Y}): both felt panels, the bet slot
     * frames, the inventory frames and the right panel's divider. The cards are separate glyphs; everything that
     * changes is an item.
     */
    static BufferedImage background(BufferedImage feltSource) {
        BufferedImage felt = Images.argb(feltSource);
        if (felt.getWidth() != 366 || felt.getHeight() != 266) {
            throw new IllegalStateException("unexpected felt size " + felt.getWidth() + "x" + felt.getHeight());
        }
        for (int x = 224; x < 232; x++) {
            for (int y = 0; y < 266; y++) {
                if ((felt.getRGB(x, y) >>> 24) != 0) {
                    throw new IllegalStateException("felt column " + x + " is not transparent");
                }
            }
        }
        int[] rows = keptRows();
        int[] left = kept(0, 224, LEFT_CUT);
        int[] right = kept(232, 366, RIGHT_CUT);
        if (rows.length != ART_H || left.length != LEFT_W || right.length != RIGHT_W) {
            throw new IllegalStateException("cuts do not give " + ART_H + " rows, " + LEFT_W + " + " + RIGHT_W
                    + " columns");
        }
        BufferedImage out = Images.blank(ART_W, ART_H);
        for (int r = 0; r < rows.length; r++) {
            for (int c = 0; c < left.length; c++) {
                out.setRGB(LEFT_X - ART_X + c, r, felt.getRGB(left[c], rows[r]));
            }
            for (int c = 0; c < right.length; c++) {
                out.setRGB(RIGHT_X - ART_X + c, r, felt.getRGB(right[c], rows[r]));
            }
        }
        Canvas g = new Canvas(out, ART_X, ART_Y, 1);
        for (int i = 0; i < 12; i++) {
            int slot = betSlot(i);
            slot(g, slotX(slot), slotY(slot));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                slot(g, 8 + col * 18, 140 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            slot(g, 8 + col * 18, 198);
        }
        g.fill(RIGHT_X + 8, DIVIDER_Y, RIGHT_X + RIGHT_W - 8, DIVIDER_Y + 1, DIVIDER);
        return out;
    }
}
