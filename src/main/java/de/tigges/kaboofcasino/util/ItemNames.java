package de.tigges.kaboofcasino.util;

import de.tigges.kaboofcasino.wager.ItemAmount;
import de.tigges.kaboofcasino.wager.PlayerAccount;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.Material;

/** Item amounts as text: "64× Diamant" with the client's own item names (translatable). */
public final class ItemNames {

    private ItemNames() {
    }

    public static Component name(String itemKey) {
        Material m = PlayerAccount.material(itemKey);
        return Component.translatable(m);
    }

    public static Component amount(ItemAmount a) {
        return Component.text(a.count() + "× ").append(name(a.item()));
    }

    public static Component list(List<ItemAmount> items) {
        return Component.join(JoinConfiguration.commas(true), items.stream().map(ItemNames::amount).toList());
    }
}
