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
import io.papermc.paper.event.player.PlayerPurchaseEvent;

/**
 * Casino items are paper underneath (server.md 3.1); none of paper's vanilla uses may work with them:
 * <ul>
 *   <li>crafting (grid and crafter): a casino item in a non-casino recipe -> no result; casino recipes check every
 *       paper's tag ({@link Recipes#papersValid});</li>
 *   <li>villager trades (librarians and cartographers buy paper): a casino item in the merchant slots cancels the
 *       purchase;</li>
 *   <li>the cartography table (map extension with paper): taking the result is cancelled.</li>
 * </ul>
 * Also unlocks the casino recipes in the recipe book at join. Thread: MAIN.
 */
public final class ItemVetoListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        CraftingInventory inv = event.getInventory();
        if (!allowed(event.getRecipe(), inv.getMatrix())) {
            inv.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCrafter(CrafterCraftEvent event) {
        if (event.getBlock().getState(false) instanceof Crafter crafter
                && !allowed(event.getRecipe(), crafter.getInventory().getContents())) {
            event.setCancelled(true);
        }
    }

    static boolean allowed(Recipe recipe, ItemStack[] matrix) {
        Optional<Recipes.PaperRule> rule = recipe instanceof Keyed keyed ? Recipes.paperRule(keyed.getKey())
                : Optional.empty();
        if (rule.isPresent()) {
            return Recipes.papersValid(rule.get(), matrix);
        }
        for (ItemStack stack : matrix) {
            if (CasinoItems.isCasinoItem(stack)) {
                return false;
            }
        }
        return true;
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

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.getPlayer().discoverRecipes(Recipes.ALL);
    }
}
