package de.tigges.kaboofcasino.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

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
