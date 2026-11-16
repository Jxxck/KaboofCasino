package de.tigges.kaboofcasino.guide;

import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.machine.CasinoItem;
import de.tigges.kaboofcasino.machine.CasinoItems;
import de.tigges.kaboofcasino.machine.ItemCatalog;
import de.tigges.kaboofcasino.util.Log;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemType;

/**
 * The item stacks of the guide's item bodies and the names of vanilla items: a vanilla item from the item registry;
 * a casino item as the plugin hands it out ({@link CasinoItems} for the machine items, {@link ItemCatalog} for the
 * decoration, seats and speaker); a casino item the plugin does not have yet (TVs, the croupier counter, the
 * croupier's hand until their steps land) as paper showing the mod's item model with its translatable name. The
 * stacks only live in the dialog packet (a dialog has no slots). Thread: MAIN.
 */
final class GuideItems {

    private final GuideBook book;
    private final Supplier<Messages> messages;
    private final Set<String> warned = new HashSet<>();

    GuideItems(GuideBook book, Supplier<Messages> messages) {
        this.book = book;
        this.messages = messages;
    }

    ItemStack stack(String id, int count) {
        int amount = Math.clamp(count, 1, 99);
        int colon = id.indexOf(':');
        String ns = id.substring(0, colon);
        String path = id.substring(colon + 1);
        if (ns.equals(NamespacedKey.MINECRAFT)) {
            ItemType type = Registry.ITEM.get(NamespacedKey.minecraft(path));
            if (type != null) {
                return type.createItemStack(amount);
            }
            warnOnce(id);
            return display(id, Component.text(id), amount);
        }
        if (ns.equals("roulettecasino")) {
            Optional<CasinoItem> machine = CasinoItem.byId(path);
            if (machine.isPresent()) {
                return CasinoItems.create(machine.get(), messages.get(), amount);
            }
        }
        Optional<ItemStack> registered = ItemCatalog.createRegistered(id, amount);
        if (registered.isPresent()) {
            return registered.get();
        }
        String key = book.nameKey(id);
        String german = key == null ? null : book.text(key);
        return display(id, key == null ? Component.text(id)
                : german == null ? Component.translatable(key) : Component.translatable(key, german), amount);
    }

    /** The translatable name of a {@code minecraft:} item (the client's language). */
    Component vanillaName(String id) {
        NamespacedKey key = NamespacedKey.fromString(id);
        ItemType type = key == null ? null : Registry.ITEM.get(key);
        if (type == null) {
            warnOnce(id);
            return Component.text(id);
        }
        return Component.translatable(type.translationKey());
    }

    /** Paper with the item model {@code id} and a name: only for showing. */
    private static ItemStack display(String id, Component name, int amount) {
        ItemStack stack = ItemStack.of(Material.PAPER, amount);
        stack.setData(DataComponentTypes.ITEM_MODEL, Key.key(id));
        stack.setData(DataComponentTypes.ITEM_NAME, name);
        return stack;
    }

    private void warnOnce(String id) {
        if (warned.add(id)) {
            Log.get().warn("KaboofCasino guide: unknown vanilla item {}", id);
        }
    }
}
