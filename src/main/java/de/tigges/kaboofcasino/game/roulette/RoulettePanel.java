package de.tigges.kaboofcasino.game.roulette;

import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.game.PanelText;
import de.tigges.kaboofcasino.gui.GuiLayout;
import de.tigges.kaboofcasino.wager.Chips;
import de.tigges.kaboofcasino.wager.ItemAmount;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;

/**
 * The floating bet panel above the table ({@code RouletteBetRenderer}): the player's head and name, the chosen
 * colour's header (a bar glyph of the pack's gui font tinted in the header colour, the label centred on it in cream,
 * like {@code RouletteBetRenderer} 31 / 128-140), and the stakes on it (item sprite and "xN"). With several players
 * (not in the mod) one line each: head, name, colour and chips, under the time left to bet.
 */
final class RoulettePanel {

    /** {@code RouletteBetRenderer.HEADER}: RED, BLACK, GREEN. */
    static final int[] HEADER = {0x9B2631, 0x21252B, 0x1B9656};
    /** The label colour (-398143). */
    static final TextColor LABEL = TextColor.color(0xF9ECC1);
    /** The colour names on a line of the table panel, light enough for the panel's dark background. */
    static final int[] WORD = {0xE0525F, 0xB8BEC8, 0x4BD18B};

    /** One player's line of the table panel. */
    record Line(UUID player, String name, int colour, long chips) {
    }

    private RoulettePanel() {
    }

    private static final Msg[] NAMES = {Msg.GUI_ROULETTE_RED, Msg.GUI_ROULETTE_BLACK, Msg.GUI_ROULETTE_GREEN};

    static Component render(Messages m, GuiLayout layout, UUID player, String name, int colour,
                            List<ItemAmount> stakes) {
        Component nl = Component.newline();
        return Component.text().append(PanelText.header(player, name)).append(nl)
                .append(header(layout, m.raw(NAMES[colour]), colour)).append(nl)
                .append(PanelText.stakes(stakes)).build();
    }

    /** Everybody at the table, one line each; {@code seconds} above them while bets are open (else negative). */
    static Component table(Messages m, List<Line> lines, long seconds) {
        TextComponent.Builder out = Component.text();
        if (seconds >= 0) {
            out.append(m.render(Msg.ROULETTE_COUNTDOWN, Messages.text("seconds", Long.toString(seconds))));
            out.append(Component.newline());
        }
        for (int i = 0; i < lines.size(); i++) {
            Line l = lines.get(i);
            if (i > 0) {
                out.append(Component.newline());
            }
            out.append(PanelText.header(l.player(), l.name()))
                    .append(Component.text("  " + m.raw(NAMES[l.colour()]) + "  ", TextColor.color(WORD[l.colour()])))
                    .append(PanelText.stakes(List.of(new ItemAmount(Chips.VALUE, l.chips()))));
        }
        return out.build();
    }

    /** The bar, back by its advance, the centred label, and space up to the bar's width (the line stays centred). */
    static Component header(GuiLayout layout, String label, int colour) {
        GuiLayout.Glyph bar = layout.glyph("panel_bar");
        int width = layout.width(label);
        int before = Math.max(0, (bar.advance() - 1 - width) / 2);
        int after = Math.max(0, bar.advance() - before - width);
        return Component.text()
                .append(Component.text(Character.toString(bar.codePoint()) + layout.spaces(-bar.advance())
                        + layout.spaces(before), TextColor.color(HEADER[colour])).font(layout.guiFont()))
                .append(Component.text(label, LABEL))
                .append(Component.text(layout.spaces(after)).font(layout.guiFont()))
                .build();
    }
}
