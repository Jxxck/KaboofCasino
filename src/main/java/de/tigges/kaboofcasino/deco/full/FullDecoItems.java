package de.tigges.kaboofcasino.deco.full;

import de.tigges.kaboofcasino.machine.ItemCatalog;
import de.tigges.kaboofcasino.util.Keys;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockDataMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * The deco items: the vanilla mushroom-block item with {@code block_state} = the block's reserved state (vanilla
 * places it as is), {@code item_model} = {@code casinodeco:<id>} (the mod's item definition in the pack), a
 * translatable {@code item_name} ({@code block.casinodeco.<id>}, the client's language from the pack; German
 * fallback) and the PDC tag {@code kaboofcasino:item} = the full mod id. Thread: MAIN.
 */
final class FullDecoItems {

    private static final Map<String, DecoBlock> BY_ID = new HashMap<>();

    static {
        for (DecoBlock b : DecoBlock.values()) {
            BY_ID.put(b.modId(), b);
        }
    }

    private FullDecoItems() {
    }

    static ItemStack create(DecoBlock block, int amount) {
        FullDecoStates.Entry state = FullDecoStates.itemState(block);
        ItemStack stack = ItemStack.of(DecoWorld.material(state.state().block()), amount);
        BlockData placed = Bukkit.createBlockData(state.state().key()); // all six faces, so all six are stored
        if (!stack.editMeta(BlockDataMeta.class, meta -> meta.setBlockData(placed))) {
            throw new IllegalStateException("no block_state on " + stack.getType()); // would place the default
        }
        stack.setData(DataComponentTypes.ITEM_MODEL, Key.key(block.modId()));
        stack.setData(DataComponentTypes.ITEM_NAME, Component.translatable(block.translationKey(),
                block.displayName()));
        stack.editPersistentDataContainer(pdc -> pdc.set(Keys.ITEM, PersistentDataType.STRING, block.modId()));
        return stack;
    }

    /** Registers all 27 items in the catalog ({@code /casino give}, recipes). */
    static void register() {
        for (DecoBlock block : DecoBlock.values()) {
            ItemCatalog.register(block.modId(), amount -> create(block, amount));
        }
    }

    /** The deco block of a stack, by its PDC tag only. */
    static Optional<DecoBlock> of(ItemStack stack) {
        return ItemCatalog.idOf(stack).map(BY_ID::get);
    }

    static Optional<DecoBlock> byId(String modId) {
        return Optional.ofNullable(BY_ID.get(modId));
    }
}
