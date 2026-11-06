package de.tigges.kaboofcasino.gui;

import de.tigges.kaboofcasino.util.Log;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * The close safety net (PostMarkt {@code DisplayPurge}): every casino view cancels every click, so a display copy can
 * never reach a player inventory. If one does anyway it is removed and logged CRITICAL. Thread: MAIN.
 */
final class DisplayPurge {

    private DisplayPurge() {
    }

    static void purge(Player player, InventoryView view) {
        List<String> hits = new ArrayList<>();
        PlayerInventory inv = player.getInventory();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack stack = inv.getItem(slot);
            if (DisplayCopies.isDisplayCopy(stack)) {
                hits.add("slot " + slot + ": " + stack.getAmount() + "x" + stack.getType().getKey());
                inv.setItem(slot, null);
            }
        }
        ItemStack cursor = view.getCursor();
        if (DisplayCopies.isDisplayCopy(cursor)) {
            hits.add("cursor: " + cursor.getAmount() + "x" + cursor.getType().getKey());
            view.setCursor(null);
        }
        if (!hits.isEmpty()) {
            Log.critical("DISPLAY_COPY_PURGED", "removed casino display copies from {} ({}): {}", player.getName(),
                    player.getUniqueId(), hits);
        }
    }
}
