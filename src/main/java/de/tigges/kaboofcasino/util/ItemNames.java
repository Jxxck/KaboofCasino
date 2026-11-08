package de.tigges.kaboofcasino.util;

import de.tigges.kaboofcasino.wager.ChipItems;
import de.tigges.kaboofcasino.wager.Chips;
import de.tigges.kaboofcasino.wager.ItemAmount;
import de.tigges.kaboofcasino.wager.PlayerAccount;
import java.util.List;
import java.util.OptionalInt;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.Material;

/**
 * Item amounts as text: "64× Diamant" with the client's own item names (translatable), chip values as "1.250 Chips",
 * chips of one denomination by the chip's name.
 */
public final class ItemNames {

    private ItemNames() {
    }

    public static Component name(String itemKey) {
        if (Chips.isValue(itemKey)) {
            return Component.text("Chips");
        }
        OptionalInt d = Chips.denomination(itemKey);
        if (d.isPresent()) {
            return ChipItems.name(d.getAsInt());
        }
        Material m = PlayerAccount.material(itemKey);
        return Component.translatable(m);
    }

    public static Component amount(ItemAmount a) {
        if (Chips.isValue(a.item())) {
            return Component.text(ChipText.chips(a.count()));
        }
        return Component.text(a.count() + "× ").append(name(a.item()));
    }

    public static Component list(List<ItemAmount> items) {
        return Component.join(JoinConfiguration.commas(true), items.stream().map(ItemNames::amount).toList());
    }

    /** A chip value with German digit grouping: 1250 -> "1.250". */
    public static String value(long value) {
        return ChipText.number(value);
    }
}
