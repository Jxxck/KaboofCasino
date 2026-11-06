package de.tigges.kaboofcasino.gui;

import de.tigges.kaboofcasino.util.Keys;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import java.util.List;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Display copies for menu slots: marked with {@code kaboofcasino:display} so the close safety net can find any copy
 * that ever reached a real inventory. Never {@code isSimilar} to a real stack.
 */
public final class DisplayCopies {

    private DisplayCopies() {
    }

    public static ItemStack of(Material material, int amount, Component name, List<Component> lore) {
        ItemStack stack = ItemStack.of(material, Math.max(1, Math.min(amount, 99)));
        if (amount > material.getMaxStackSize()) {
            stack.setData(DataComponentTypes.MAX_STACK_SIZE, Math.min(amount, 99));
        }
        if (name != null) {
            stack.setData(DataComponentTypes.CUSTOM_NAME, name);
        }
        if (!lore.isEmpty()) {
            stack.setData(DataComponentTypes.LORE, ItemLore.lore(lore));
        }
        mark(stack);
        return stack;
    }

    /** A paper display item with a pack model (buttons). */
    public static ItemStack model(String modelKey, Component name, List<Component> lore) {
        ItemStack stack = of(Material.PAPER, 1, name, lore);
        stack.setData(DataComponentTypes.ITEM_MODEL, Key.key(modelKey));
        return stack;
    }

    static void mark(ItemStack stack) {
        stack.editPersistentDataContainer(pdc -> pdc.set(Keys.DISPLAY, PersistentDataType.BOOLEAN, true));
    }

    public static boolean isDisplayCopy(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getPersistentDataContainer().has(Keys.DISPLAY);
    }
}
