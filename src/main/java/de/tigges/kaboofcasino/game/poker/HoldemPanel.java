package de.tigges.kaboofcasino.game.poker;

import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.game.PanelText;
import de.tigges.kaboofcasino.util.ChipText;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * The floating panel above a Texas Hold'em table: the blinds and the pot (or the seconds to the next hand, or that
 * the table waits for players), then one line per seat: whose turn it is, head and name, the chips in front of the
 * player, the dealer button, the last action, at the showdown the hand, a winner in green. Pure.
 */
final class HoldemPanel {

    static final TextColor GOLD = TextColor.color(0xF2CC72);
    static final TextColor GREY = TextColor.color(0xA8B5A8);
    static final TextColor RED = TextColor.color(0xE79882);
    static final TextColor GREEN = TextColor.color(0x88CF86);

    /** One seat's line. */
    record Line(UUID player, String name, int stack, String action, boolean acting, boolean folded, boolean dealer,
                int category, boolean winner) {
    }

    /** {@code pot} -1 = no hand; {@code seconds} -1 = no countdown; {@code lobby}: fewer than two players seated. */
    record Content(int smallBlind, int bigBlind, int pot, long seconds, boolean lobby, List<Line> lines) {
        Content {
            lines = List.copyOf(lines);
        }
    }

    private HoldemPanel() {
    }

    static Component render(Messages m, Content c) {
        TextComponent.Builder text = Component.text();
        text.append(m.render(Msg.POKER_HOLDEM_PANEL_TITLE, Messages.text("sb", ChipText.number(c.smallBlind())),
                Messages.text("bb", ChipText.number(c.bigBlind()))));
        Component second;
        if (c.pot() >= 0) {
            second = m.render(Msg.POKER_HOLDEM_PANEL_POT, Messages.text("chips", ChipText.chips(c.pot())));
        } else if (c.lobby()) {
            second = m.render(Msg.POKER_HOLDEM_PANEL_LOBBY);
        } else if (c.seconds() >= 0) {
            second = m.render(Msg.POKER_HOLDEM_PANEL_NEXT, Messages.text("seconds", Long.toString(c.seconds())));
        } else {
            second = null;
        }
        if (second != null) {
            text.append(Component.newline()).append(second);
        }
        for (Line l : c.lines()) {
            text.append(Component.newline());
            if (l.acting()) {
                text.append(Component.text("» ", GOLD));
            }
            text.append(PanelText.header(l.player(), l.name())).append(Component.text("  "))
                    .append(Component.text(ChipText.chips(l.stack()), l.folded() ? GREY : GOLD));
            if (l.dealer()) {
                text.append(Component.text(" [D]", NamedTextColor.WHITE));
            }
            if (!l.action().isEmpty()) {
                TextColor colour = l.winner() ? GREEN : l.folded() ? RED : GREY;
                Component action = Component.text("  " + l.action(), colour);
                text.append(l.winner() ? action.decorate(TextDecoration.BOLD) : action);
            }
            if (l.category() >= 0) {
                text.append(Component.text("  ")).append(PokerPanel.handName(l.category())
                        .color(l.winner() ? GREEN : PanelText.WHITE));
            }
        }
        return text.build();
    }
}
