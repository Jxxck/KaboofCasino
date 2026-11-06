package de.tigges.kaboofcasino.machine;

import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.util.Keys;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** Creates and recognises casino items. Thread: MAIN. */
public final class CasinoItems {

    private CasinoItems() {
    }

    public static ItemStack create(CasinoItem item, Messages messages, int amount) {
        ItemStack stack = ItemStack.of(Material.PAPER, amount);
        stack.setData(DataComponentTypes.ITEM_MODEL, Key.key(item.model()));
        stack.setData(DataComponentTypes.ITEM_NAME, messages.render(item.nameMsg()));
        if (item.machine().isPresent()) {
            stack.setData(DataComponentTypes.LORE, ItemLore.lore(List.of(messages.render(Msg.ITEM_SLOT_MACHINE_LORE))));
        }
        stack.editPersistentDataContainer(pdc -> pdc.set(Keys.ITEM, PersistentDataType.STRING, item.id()));
        return stack;
    }

    /** The casino item type of a stack (only by its PDC tag), empty for everything else. */
    public static Optional<CasinoItem> of(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getType() != Material.PAPER) {
            return Optional.empty();
        }
        String id = stack.getPersistentDataContainer().get(Keys.ITEM, PersistentDataType.STRING);
        return id == null ? Optional.empty() : CasinoItem.byId(id);
    }

    public static boolean isCasinoItem(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getPersistentDataContainer().has(Keys.ITEM);
    }
}
