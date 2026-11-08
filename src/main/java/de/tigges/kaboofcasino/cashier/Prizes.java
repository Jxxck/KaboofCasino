package de.tigges.kaboofcasino.cashier;

import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.machine.ItemCatalog;
import de.tigges.kaboofcasino.machine.Recipes;
import de.tigges.kaboofcasino.util.Log;
import de.tigges.kaboofcasino.wager.ChipItems;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The prizes as items (the Bukkit side of {@link ShopEntry}): a casino id is built fresh by the casino (its name
 * follows {@code messages_de.yml}), a vanilla item from its {@code /give} text with Bukkit's item factory; the
 * reverse ({@link #spec}) for the croupier's hand. Thread: MAIN.
 */
public final class Prizes {

    /** Prizes that could not be built, logged once per text until the plugin restarts. */
    private static final Set<String> REPORTED = new HashSet<>();

    private Prizes() {
    }

    /** One item of the prize (amount 1), or empty if it cannot be built (unknown id, invalid components, a chip). */
    public static Optional<ItemStack> one(ShopEntry entry, Messages messages) {
        try {
            ItemStack stack;
            if (entry.casino()) {
                stack = Recipes.casinoStack(entry.item(), messages, 1).orElse(null);
            } else {
                stack = Bukkit.getItemFactory().createItemStack(entry.item());
            }
            if (stack == null || stack.isEmpty() || ChipItems.isChip(stack)) {
                report(entry, "unknown or not a prize");
                return Optional.empty();
            }
            return Optional.of(stack.asOne());
        } catch (IllegalArgumentException e) {
            report(entry, e.getMessage());
            return Optional.empty();
        }
    }

    private static void report(ShopEntry entry, String why) {
        if (REPORTED.add(entry.item())) {
            Log.get().warn("KaboofCasino shop: prize '{}' cannot be built ({}); it is shown as unavailable",
                    entry.item(), why);
        }
    }

    /**
     * The shop text of a stack (the croupier's hand adds the item in the off hand): a casino item by its id
     * (ItemCatalog), anything else as {@code <id>[<components>]}; empty for a chip or air.
     */
    public static Optional<String> spec(ItemStack stack) {
        if (stack == null || stack.isEmpty() || ChipItems.isChip(stack)) {
            return Optional.empty();
        }
        Optional<String> casino = ItemCatalog.idOf(stack);
        if (casino.isPresent()) {
            return ShopEntry.invalid(casino.get()) == null ? casino : Optional.empty();
        }
        String id = stack.getType().getKey().asString();
        ItemMeta meta = stack.getItemMeta();
        String components = meta == null ? "" : meta.getAsComponentString();
        String spec = components == null || components.isEmpty() || components.equals("[]") ? id : id + components;
        return ShopEntry.invalid(spec) == null ? Optional.of(spec) : Optional.empty();
    }

    /** {@code count} of the prize as stacks of at most its maximum size. */
    static List<ItemStack> stacks(ItemStack one, int count) {
        List<ItemStack> out = new ArrayList<>();
        int max = Math.max(1, one.getMaxStackSize());
        for (int left = count; left > 0; left -= max) {
            out.add(one.asQuantity(Math.min(left, max)));
        }
        return out;
    }

    /** The purchase's prize side for {@link Cashbox#buy}: room check and delivery into the storage slots. */
    static Cashbox.Goods goods(Player player, ItemStack one, int count) {
        return new Cashbox.Goods() {
            @Override
            public int slotsNeeded() {
                PlayerInventory inv = player.getInventory();
                int max = Math.max(1, one.getMaxStackSize());
                long room = 0;
                for (int slot = 0; slot < 36; slot++) {
                    ItemStack s = inv.getItem(slot);
                    if (s != null && !s.isEmpty() && s.isSimilar(one)) {
                        room += Math.max(0, s.getMaxStackSize() - s.getAmount());
                    }
                }
                long rest = Math.max(0, count - room);
                return (int) ((rest + max - 1) / max);
            }

            @Override
            public int give() {
                int left = 0;
                for (ItemStack rest : player.getInventory().addItem(stacks(one, count).toArray(ItemStack[]::new))
                        .values()) {
                    left += rest.getAmount();
                }
                return left;
            }
        };
    }
}
