package de.tigges.kaboofcasino.packtool;

import java.awt.image.BufferedImage;

/**
 * Text baked into the blackjack GUI pictures exactly as the 26.3 client draws it ({@code Font} /
 * {@code BakedSheetGlyph}): per glyph the shadow ({@code colour x 0.25}, offset 1, 1), its bold copy (+1), the glyph
 * and its bold copy (+1); a bold glyph advances one pixel more.
 */
final class BlackjackGuiText {

    private BlackjackGuiText() {
    }

    /** Width the client advances for {@code text} (bold: +1 per character). */
    static int width(TextGlyphs glyphs, String text, boolean bold) {
        return glyphs.width(text) + (bold ? text.length() : 0);
    }

    /** The client's shadow colour of an opaque text colour ({@code ARGB.scaleRGB(colour, 0.25F)}). */
    static int shadow(int argb) {
        return argb & 0xFF000000 | (argb & 0xFCFCFC) >> 2;
    }

    /**
     * Draws {@code text} with its cap tops at {@code top}, starting at {@code x}.
     *
     * @return the x after the text (the client's pen)
     */
    static int draw(BufferedImage target, TextGlyphs glyphs, String text, int x, int top, int argb, boolean bold,
                    boolean shadow) {
        int pen = x;
        for (int i = 0; i < text.length(); i++) {
            String c = text.substring(i, i + 1);
            if (shadow) {
                glyphs.draw(target, c, pen + 1, top + 1, shadow(argb), false);
                if (bold) {
                    glyphs.draw(target, c, pen + 2, top + 1, shadow(argb), false);
                }
            }
            glyphs.draw(target, c, pen, top, argb, false);
            if (bold) {
                glyphs.draw(target, c, pen + 1, top, argb, false);
            }
            pen += glyphs.width(c) + (bold ? 1 : 0);
        }
        return pen;
    }

    /** Draws {@code text} centred on {@code centreX} like {@code CasinoText.fitted(..., CENTER)} (left = x - w / 2). */
    static void centred(BufferedImage target, TextGlyphs glyphs, String text, int centreX, int top, int argb,
                        boolean bold, boolean shadow) {
        draw(target, glyphs, text, centreX - width(glyphs, text, bold) / 2, top, argb, bold, shadow);
    }
}
