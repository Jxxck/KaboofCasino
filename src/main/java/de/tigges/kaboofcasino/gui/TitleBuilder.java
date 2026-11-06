package de.tigges.kaboofcasino.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Composes a chest title out of art glyphs, spaces and positioned text (render.md 5.1). The client draws the title
 * at window x = 8 and advances the pen by each glyph's advance; {@link #moveTo} emits the space characters that bring
 * the pen to an absolute window x. Art is drawn white (the default title colour would darken it).
 */
public final class TitleBuilder {

    /** Horizontal alignment of a text. */
    public enum Align {
        LEFT, CENTER, RIGHT
    }

    private final GuiLayout layout;
    private final TextComponent.Builder out = Component.text();
    private int pen = 8;

    public TitleBuilder(GuiLayout layout) {
        this.layout = layout;
    }

    /** Draws an art glyph at its intended position. */
    public TitleBuilder art(String name) {
        GuiLayout.Glyph g = layout.glyph(name);
        return art(name, g.x());
    }

    /** Draws an art glyph with its top-left at window x (its y is fixed by the font's ascent). */
    public TitleBuilder art(String name, int x) {
        GuiLayout.Glyph g = layout.glyph(name);
        moveTo(x);
        out.append(Component.text(Character.toString(g.codePoint())).font(layout.guiFont())
                .color(NamedTextColor.WHITE));
        pen += g.advance();
        return this;
    }

    /** Draws {@code text} with its cap tops at window y, aligned at x. */
    public TitleBuilder text(String text, int x, int y, TextColor color, Align align) {
        int w = layout.width(text);
        int start = switch (align) {
            case LEFT -> x;
            case CENTER -> x - w / 2;
            case RIGHT -> x - w;
        };
        moveTo(start);
        out.append(Component.text(text).font(layout.textFont(y)).color(color));
        pen += w;
        return this;
    }

    /** Draws {@code text} at a named anchor of the pack's layout. */
    public TitleBuilder text(String anchor, String text, TextColor color) {
        GuiLayout.Anchor a = layout.anchor(anchor);
        return text(text, a.x(), a.y(), color, a.align());
    }

    /**
     * Draws {@code text} at a named anchor, optionally bold (the client advances one pixel more per character) and
     * with the client's text shadow (a style shadow colour is drawn even in a chest title, which itself has none:
     * {@code Font.getShadowColor}; colour x 0.25 like {@code drawShadow}).
     */
    public TitleBuilder text(String anchor, String text, TextColor color, boolean bold, boolean shadow) {
        GuiLayout.Anchor a = layout.anchor(anchor);
        int w = layout.width(text) + (bold ? text.codePointCount(0, text.length()) : 0);
        int start = switch (a.align()) {
            case LEFT -> a.x();
            case CENTER -> a.x() - w / 2;
            case RIGHT -> a.x() - w;
        };
        moveTo(start);
        Component c = Component.text(text).font(layout.textFont(a.y())).color(color);
        if (bold) {
            c = c.decorate(TextDecoration.BOLD);
        }
        if (shadow) {
            c = c.shadowColor(ShadowColor.shadowColor(0xFF000000 | (color.value() & 0xFCFCFC) >> 2));
        }
        out.append(c);
        pen += w;
        return this;
    }

    /** Draws an art glyph at the x of a named anchor. */
    public TitleBuilder artAt(String glyph, String anchor) {
        return art(glyph, layout.anchor(anchor).x());
    }

    public TitleBuilder moveTo(int x) {
        int delta = x - pen;
        if (delta != 0) {
            out.append(Component.text(layout.spaces(delta)).font(layout.guiFont()));
            pen = x;
        }
        return this;
    }

    public int pen() {
        return pen;
    }

    public Component build() {
        return out.build();
    }
}
