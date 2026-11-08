package de.tigges.kaboofcasino.machine;

import java.util.Optional;
import org.bukkit.Keyed;
import org.bukkit.block.Crafter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.Recipe;
import io.papermc.paper.event.player.CartographyItemEvent;
import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent;
import io.papermc.paper.event.player.PlayerPurchaseEvent;

/**
 * Casino items are paper underneath (server.md 3.1); none of paper's vanilla uses may work with them:
 * <ul>
 *   <li>crafting (grid and crafter): a casino item in a non-casino recipe -> no result; a grid of a casino recipe
 *       or with casino items gives the result of the casino recipe it holds slot by slot ({@link Recipes#craft}:
 *       casino ingredients by item id, vanilla ones never a casino item), whichever same-material recipe Bukkit
 *       picked;</li>
 *   <li>villager trades (librarians and cartographers buy paper): a casino item in the merchant slots cancels the
 *       purchase;</li>
 *   <li>the cartography table (map extension with paper): taking the result is cancelled;</li>
 *   <li>anvil, smithing table, grindstone: no result with a chip in them (no renamed chips).</li>
 * </ul>
 * Also unlocks the recipes in the recipe book along the mod's chain on first possession (at join and on every slot
 * change). Thread: MAIN.
 */
public final class ItemVetoListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        CraftingInventory inv = event.getInventory();
        Optional<ItemStack> result = result(event.getRecipe(), inv.getMatrix());
        if (result != null) {
            inv.setResult(result.orElse(null));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCrafter(CrafterCraftEvent event) {
        if (event.getBlock().getState(false) instanceof Crafter crafter) {
            Optional<ItemStack> result = result(event.getRecipe(), crafter.getInventory().getContents());
            if (result != null) {
                if (result.isEmpty()) {
                    event.setCancelled(true);
                } else {
                    event.setResult(result.get());
                }
            }
        }
    }

    /**
     * The result a grid must give: {@code null} = leave vanilla's (no casino recipe, no casino item); otherwise the
     * casino recipe the grid holds slot by slot ({@link Recipes#craft}), or empty = nothing (a casino item in a
     * vanilla recipe, or casino items that fit no casino recipe).
     */
    static Optional<ItemStack> result(Recipe recipe, ItemStack[] matrix) {
        boolean ours = recipe instanceof Keyed keyed && Recipes.ours(keyed.getKey());
        boolean casino = false;
        for (ItemStack stack : matrix) {
            casino |= CasinoItems.isCasinoItem(stack);
        }
        if (!ours && !casino) {
            return null;
        }
        return Recipes.craft(matrix);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPurchase(PlayerPurchaseEvent event) {
        Player player = event.getPlayer();
        Inventory top = player.getOpenInventory().getTopInventory();
        if (top instanceof MerchantInventory merchant
                && (CasinoItems.isCasinoItem(merchant.getItem(0)) || CasinoItems.isCasinoItem(merchant.getItem(1)))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCartography(CartographyItemEvent event) {
        for (ItemStack stack : event.getInventory().getContents()) {
            if (CasinoItems.isCasinoItem(stack)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /**
     * Chips are never an anvil, smithing or grindstone ingredient (PLAN-2 C1): no renamed "1000er-Chip" made of a
     * 1. Paper has no use there anyway; this covers every result screen sharing the event (anvil, smithing,
     * grindstone).
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareResult(com.destroystokyo.paper.event.inventory.PrepareResultEvent event) {
        for (ItemStack stack : event.getInventory().getContents()) {
            if (de.tigges.kaboofcasino.wager.ChipItems.isChip(stack)) {
                event.setResult(null);
                return;
            }
        }
    }

    /** The mod's recipe advancements: diamond -> reel, reel -> mechanism, mechanism -> slot machine. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        for (ItemStack stack : player.getInventory().getContents()) {
            unlock(player, stack);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSlotChange(PlayerInventorySlotChangeEvent event) {
        unlock(event.getPlayer(), event.getNewItemStack());
    }

    public static void unlock(Player player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        for (org.bukkit.NamespacedKey recipe : Recipes.unlockedBy(stack)) {
            if (!player.hasDiscoveredRecipe(recipe)) {
                player.discoverRecipe(recipe);
            }
        }
    }
}
