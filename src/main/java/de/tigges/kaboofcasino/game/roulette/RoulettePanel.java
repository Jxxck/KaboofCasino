package de.tigges.kaboofcasino.game.roulette;

import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.game.PanelText;
import de.tigges.kaboofcasino.gui.GuiLayout;
import de.tigges.kaboofcasino.wager.ItemAmount;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

/**
 * The floating bet panel above the table ({@code RouletteBetRenderer}): the player's head and name, the chosen
 * colour's header (a bar glyph of the pack's gui font tinted in the header colour, the label centred on it in cream,
 * like {@code RouletteBetRenderer} 31 / 128-140), and the stakes on it (item sprite and "xN").
 */
final class RoulettePanel {

    /** {@code RouletteBetRenderer.HEADER}: RED, BLACK, GREEN. */
    static final int[] HEADER = {0x9B2631, 0x21252B, 0x1B9656};
    /** The label colour (-398143). */
    static final TextColor LABEL = TextColor.color(0xF9ECC1);

    private RoulettePanel() {
    }

    static Component render(Messages m, GuiLayout layout, UUID player, String name, int colour,
                            List<ItemAmount> stakes) {
        Msg[] names = {Msg.GUI_ROULETTE_RED, Msg.GUI_ROULETTE_BLACK, Msg.GUI_ROULETTE_GREEN};
        Component nl = Component.newline();
        return Component.text().append(PanelText.header(player, name)).append(nl)
                .append(header(layout, m.raw(names[colour]), colour)).append(nl)
                .append(PanelText.stakes(stakes)).build();
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
