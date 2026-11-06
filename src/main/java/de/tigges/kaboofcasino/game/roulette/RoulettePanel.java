package de.tigges.kaboofcasino.game.roulette;

import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.game.PanelText;
import de.tigges.kaboofcasino.wager.ItemAmount;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * The floating bet panel above the table ({@code RouletteBetRenderer}): the player's head and name, the chosen
 * colour's header in its colour, and the stakes on it (item sprite and "xN").
 */
final class RoulettePanel {

    /** Header colours RED, BLACK, GREEN. */
    static final int[] COLOUR = {0xE04848, 0xC8C8C8, 0x36D886};

    private RoulettePanel() {
    }

    static Component render(Messages m, UUID player, String name, int colour, List<ItemAmount> stakes) {
        Msg[] names = {Msg.GUI_ROULETTE_RED, Msg.GUI_ROULETTE_BLACK, Msg.GUI_ROULETTE_GREEN};
        Component nl = Component.newline();
        return Component.text().append(PanelText.header(player, name)).append(nl)
                .append(m.render(names[colour]).color(TextColor.color(COLOUR[colour]))
                        .decoration(TextDecoration.BOLD, true)).append(nl)
                .append(PanelText.stakes(stakes)).build();
    }
}
