package de.tigges.kaboofcasino.wager;

import de.tigges.kaboofcasino.util.Keys;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.OptionalInt;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Chip items (PLAN-2 C1): {@code minecraft:paper} with {@code item_model kaboofcasino:chip_<n>}, a fixed translatable
 * name ({@code item.kaboofcasino.chip_<n>}, German fallback) and the PDC id {@code kaboofcasino:item =
 * kaboofcasino:chip_<n>}; identity only by that tag on paper, so a plain paper is never a chip. The name never comes
 * from {@code messages_de.yml}: chips made before and after a text change still stack. Thread: any (no world access).
 */
public final class ChipItems {

    private ChipItems() {
    }

    /** Registers the six chips for {@code /casino give} ({@code kaboofcasino:chip_25}) and the recipe vetoes. */
    public static void register() {
        for (int d : Chips.DENOMINATIONS) {
            de.tigges.kaboofcasino.machine.ItemCatalog.register(Chips.key(d), amount -> create(d, amount));
        }
    }

    /** {@code amount} fresh chips of {@code denomination} (amount 1..64). */
    public static ItemStack create(int denomination, int amount) {
        String key = Chips.key(denomination);
        ItemStack stack = ItemStack.of(Material.PAPER, amount);
        stack.setData(DataComponentTypes.ITEM_MODEL, Key.key(key));
        stack.setData(DataComponentTypes.ITEM_NAME, name(denomination));
        stack.editPersistentDataContainer(pdc -> pdc.set(Keys.ITEM, PersistentDataType.STRING, key));
        return stack;
    }

    /** {@code item.kaboofcasino.chip_<n>} with the German name as fallback ("25er-Chip"). */
    public static Component name(int denomination) {
        return Component.translatable("item.kaboofcasino.chip_" + denomination, denomination + "er-Chip");
    }

    /** The denomination of a chip stack (only by its PDC tag on paper), empty for everything else. */
    public static OptionalInt denomination(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getType() != Material.PAPER) {
            return OptionalInt.empty();
        }
        return Chips.denomination(stack.getPersistentDataContainer().get(Keys.ITEM, PersistentDataType.STRING));
    }

    public static boolean isChip(ItemStack stack) {
        return denomination(stack).isPresent();
    }

    /** A chip exactly as the casino makes it (it stacks with fresh chips; not renamed or otherwise changed). */
    public static boolean pristine(ItemStack stack) {
        OptionalInt d = denomination(stack);
        return d.isPresent() && stack.isSimilar(create(d.getAsInt(), 1));
    }

    /** A display or payout stack of an item key: chips by denomination key, otherwise the plain material. */
    public static ItemStack stackOf(String itemKey, int amount) {
        OptionalInt d = Chips.denomination(itemKey);
        if (d.isPresent()) {
            return create(d.getAsInt(), amount);
        }
        if (Chips.isValue(itemKey)) {
            throw new IllegalArgumentException("a chip value is not a stack: expand it first");
        }
        return ItemStack.of(PlayerAccount.material(itemKey), amount);
    }

    /**
     * What a display shows for an item key (a payout coin, a stake cell): a chip's look (paper with its model and
     * name, no casino tag: never a chip) or a plain stack of the material.
     */
    public static ItemStack lookOf(String itemKey) {
        OptionalInt d = Chips.denomination(itemKey);
        if (d.isEmpty()) {
            return ItemStack.of(PlayerAccount.material(itemKey));
        }
        ItemStack stack = ItemStack.of(Material.PAPER);
        stack.setData(DataComponentTypes.ITEM_MODEL, Key.key(itemKey));
        stack.setData(DataComponentTypes.ITEM_NAME, name(d.getAsInt()));
        return stack;
    }

    /** The item key a stack counts as: {@code kaboofcasino:chip_<n>} for chips, else the material key. */
    public static String keyOf(ItemStack stack) {
        OptionalInt d = denomination(stack);
        return d.isPresent() ? Chips.key(d.getAsInt()) : stack.getType().getKey().asString();
    }
}
