package de.tigges.kaboofcasino.game.slot;

import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.gui.GuiLayout;
import de.tigges.kaboofcasino.wager.ItemAmount;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.object.ObjectContents;

/**
 * Text of the floating panel above the machine ({@code SlotMachineRenderer.panel}): the player's head and name, the
 * stake ("EINSATZ", icon x count) and, from the result on, the three symbols and the result line ("KEIN GEWINN",
 * "GEWINN xN", "JACKPOT xN") in the kind's colour. Icons are object components (atlas sprite, player head) and the
 * symbol glyphs of the pack's gui font.
 */
final class SlotPanel {

    private static final TextColor NAME = TextColor.color(-792629 & 0xffffff);
    private static final TextColor TITLE = TextColor.color(-398143 & 0xffffff);
    private static final TextColor WHITE = TextColor.color(0xffffff);
    /** {@code SlotMachineRenderer.RESULT_COLOR} / {@code RESULT_NUMBER} by kind. */
    private static final int[] RESULT_COLOR = {-1599358, -865166, -7811194, -5906485, -22016};
    private static final int[] RESULT_NUMBER = {0xFFFFFF, 0xFFFFFF, 0xFFFF55, 0xFFFF55, 0xFFFFFF};

    private SlotPanel() {
    }

    static Component render(Messages messages, GuiLayout layout, UUID player, String name, ItemAmount stake,
                            SlotGame.Result result, int[] stops) {
        Component head = Component.object(ObjectContents.playerHead(player));
        Component nl = Component.newline();
        Component text = Component.text()
                .append(head).append(Component.text(" " + name, NAME)).append(nl)
                .append(messages.render(Msg.PANEL_BET).color(TITLE)).append(nl)
                .append(sprite(stake.item())).append(Component.text(" ×" + stake.count(), WHITE))
                .build();
        if (result == null) {
            return text;
        }
        int[] symbols = SlotGame.symbols(stops);
        StringBuilder icons = new StringBuilder();
        for (int k = 0; k < 3; k++) {
            if (k > 0) {
                icons.append(layout.spaces(4));
            }
            icons.appendCodePoint(layout.glyph("symbol_" + SlotGame.NAMES[symbols[k]]).codePoint());
        }
        Component line = resultLine(messages, result);
        return text.append(nl).append(Component.text(icons.toString(), WHITE).font(layout.guiFont())).append(nl)
                .append(line);
    }

    static Component resultLine(Messages messages, SlotGame.Result result) {
        int kind = result.kind().ordinal();
        Component number = Component.text("×" + result.multiplier(), TextColor.color(RESULT_NUMBER[kind]));
        Component line = switch (result.kind()) {
            case NONE -> messages.render(Msg.PANEL_LOSE);
            case PUSH -> messages.render(Msg.PANEL_PUSH);
            case WIN, BIG -> messages.render(Msg.PANEL_WIN, Messages.component("mult", number));
            case JACKPOT -> messages.render(Msg.PANEL_JACKPOT, Messages.component("mult", number));
        };
        return line.colorIfAbsent(TextColor.color(RESULT_COLOR[kind] & 0xffffff))
                .decoration(TextDecoration.BOLD, true);
    }

    /** The bet item as an 8x8 atlas sprite: items from the items atlas, blocks from the blocks atlas. */
    static Component sprite(String itemKey) {
        String path = itemKey.substring(itemKey.indexOf(':') + 1);
        boolean block = path.endsWith("_block");
        Key atlas = Key.key(block ? "minecraft:blocks" : "minecraft:items");
        Key sprite = Key.key("minecraft", (block ? "block/" : "item/") + path);
        return Component.object(ObjectContents.sprite(atlas, sprite));
    }
}
