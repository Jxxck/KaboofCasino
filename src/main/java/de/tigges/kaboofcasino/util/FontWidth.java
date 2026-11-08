package de.tigges.kaboofcasino.util;

/**
 * The default font's text width, close enough for names and numbers (glyph width + 1 per character; the client's exact
 * widths vary by a pixel here and there). Used to keep texts inside boxes: board columns, guide captions. Pure.
 */
public final class FontWidth {

    private FontWidth() {
    }

    /** The advance of a character in pixels at scale 1. */
    public static int advance(char ch) {
        return switch (ch) {
            case 'i', '!', '.', ',', ':', ';', '|', '\'' -> 2;
            case 'l', '`' -> 3;
            case 't', 'I', '[', ']', ' ' -> 4;
            case 'f', 'k', '<', '>', '(', ')', '{', '}', '"', '*' -> 5;
            case '@', '~', '…' -> 7;
            default -> 6;
        };
    }

    public static int width(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            w += advance(s.charAt(i));
        }
        return w;
    }

    /** {@code text} cut to {@code maxPx} pixels, with "…" when cut. */
    public static String fit(String text, int maxPx) {
        if (width(text) <= maxPx) {
            return text;
        }
        StringBuilder b = new StringBuilder();
        int used = width("…");
        for (int i = 0; i < text.length(); i++) {
            int w = advance(text.charAt(i));
            if (used + w > maxPx) {
                break;
            }
            b.append(text.charAt(i));
            used += w;
        }
        return b.toString().stripTrailing() + "…";
    }
}
