package de.tigges.kaboofcasino.game;

import de.tigges.kaboofcasino.util.ItemNames;
import de.tigges.kaboofcasino.wager.Chips;
import de.tigges.kaboofcasino.wager.ItemAmount;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.text.format.TextColor;

/**
 * Building blocks of the floating panels ({@code CasinoFloatingPanels}): the player's head and name, a title, stake
 * cells (item sprite and "xN"), and the multiplier texts of the 3x3 machines in the German form of the mod's
 * {@code gui.roulettecasino.<game>.mult.<quarters>} keys ("×1,5", "×0,25").
 */
public final class PanelText {

    public static final TextColor NAME = TextColor.color(0xF3E7CB);
    public static final TextColor TITLE = TextColor.color(0xF9ECC1);
    public static final TextColor WHITE = TextColor.color(0xFFFFFF);

    private PanelText() {
    }

    public static Component header(UUID player, String name) {
        return Component.text().append(Component.object(ObjectContents.playerHead(player)))
                .append(Component.text(" " + name, NAME)).build();
    }

    /**
     * One line of stake cells, separated by spaces: per stake the sprite and "×count" (ores of an older round), or
     * for chips the sprite of the largest chip and the value ("1.250").
     */
    public static Component stakes(List<ItemAmount> stakes) {
        var line = Component.text();
        for (int i = 0; i < stakes.size(); i++) {
            if (i > 0) {
                line.append(Component.text("   "));
            }
            ItemAmount a = stakes.get(i);
            String count = Chips.isValue(a.item()) ? " " + ItemNames.value(a.count()) : " ×" + a.count();
            line.append(sprite(ChipStakes.cellItem(a))).append(Component.text(count, WHITE));
        }
        return line.build();
    }

    /**
     * The bet item as an 8x8 atlas sprite: items from the items atlas, blocks from the blocks atlas, a chip
     * (denomination key) as the pack's {@code kaboofcasino:item/chip_<n>} from the items atlas.
     */
    public static Component sprite(String itemKey) {
        if (Chips.denomination(itemKey).isPresent()) {
            return Component.object(ObjectContents.sprite(Key.key("minecraft:items"),
                    Key.key("kaboofcasino", "item/" + itemKey.substring(itemKey.indexOf(':') + 1))));
        }
        String path = itemKey.substring(itemKey.indexOf(':') + 1);
        boolean block = path.endsWith("_block");
        Key atlas = Key.key(block ? "minecraft:blocks" : "minecraft:items");
        Key sprite = Key.key("minecraft", (block ? "block/" : "item/") + path);
        return Component.object(ObjectContents.sprite(atlas, sprite));
    }

    /** {@code quarters / 4} as the mod's multiplier text: "×5", "×1.5", "×0.25" (German: "×1,5", "×0,25"). */
    public static String multiplier(int quarters) {
        String n = BigDecimal.valueOf(quarters).divide(BigDecimal.valueOf(4)).stripTrailingZeros().toPlainString();
        return "×" + de.tigges.kaboofcasino.util.ChipText.decimal(n);
    }
}
