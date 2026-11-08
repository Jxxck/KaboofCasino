package de.tigges.kaboofcasino.cashier;

import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.machine.CasinoItems;
import de.tigges.kaboofcasino.machine.ItemCatalog;
import de.tigges.kaboofcasino.machine.Recipes;
import de.tigges.kaboofcasino.util.Log;
import de.tigges.kaboofcasino.wager.ChipItems;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.BundleContents;
import io.papermc.paper.datacomponent.item.ItemContainerContents;
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
            if (smuggles(stack)) {
                skipped(entry);
                return Optional.empty();
            }
            return Optional.of(stack.asOne());
        } catch (IllegalArgumentException e) {
            report(entry, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * True if the entry's prize holds chips or casino items ({@link #smuggles}): such an entry is left out of the
     * shop players see (logged WARN once); the croupier's hand still lists it so it can be removed.
     */
    public static boolean refused(ShopEntry entry, Messages messages) {
        if (entry.casino()) {
            return false; // built fresh by the casino: never filled
        }
        try {
            ItemStack stack = Bukkit.getItemFactory().createItemStack(entry.item());
            if (smuggles(stack)) {
                skipped(entry);
                return true;
            }
            return false;
        } catch (IllegalArgumentException e) {
            return false; // unbuildable: shown as unavailable by one()
        }
    }

    /** A prize with chips or casino items inside: left out of the shop entirely (logged once per item text). */
    private static void skipped(ShopEntry entry) {
        if (REPORTED.add(entry.item())) {
            Log.get().warn("KaboofCasino shop: prize '{}' holds chips or casino items in a container or bundle; "
                    + "the entry is skipped", entry.item());
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
     * (ItemCatalog), anything else as {@code <id>[<components>]}; empty for a chip, air, or an item that
     * {@link #smuggles} chips or casino items.
     */
    public static Optional<String> spec(ItemStack stack) {
        if (stack == null || stack.isEmpty() || ChipItems.isChip(stack) || smuggles(stack)) {
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

    /**
     * True if {@code stack} holds a chip or any casino-tagged item ({@code kaboofcasino:item}) in its
     * {@code container} (shulker box, ...) or {@code bundle_contents}, at any depth: such a prize would hand out chips
     * or casino items past the cashier. Too deep a nesting counts as holding one.
     */
    public static boolean smuggles(ItemStack stack) {
        return smuggles(stack, 0);
    }

    private static boolean smuggles(ItemStack stack, int depth) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (depth > MAX_NESTING) {
            return true;
        }
        List<ItemStack> inside = new ArrayList<>();
        ItemContainerContents container = stack.getData(DataComponentTypes.CONTAINER);
        if (container != null) {
            inside.addAll(container.contents());
        }
        BundleContents bundle = stack.getData(DataComponentTypes.BUNDLE_CONTENTS);
        if (bundle != null) {
            inside.addAll(bundle.contents());
        }
        for (ItemStack in : inside) {
            if (in != null && !in.isEmpty() && (ChipItems.isChip(in) || CasinoItems.isCasinoItem(in)
                    || smuggles(in, depth + 1))) {
                return true;
            }
        }
        return false;
    }

    /** Deeper than this a prize's containers are not looked into: refused. */
    static final int MAX_NESTING = 8;

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

            /**
             * Puts the prize in. Safety net (the room check makes it unreachable): the owed list holds only plain
             * items and chip values, not a prize with components, so what did not fit is dropped at the player's
             * feet, only he can pick it up, it does not despawn and mobs leave it; logged WARN with the position.
             */
            @Override
            public int give() {
                int left = 0;
                for (ItemStack rest : player.getInventory().addItem(stacks(one, count).toArray(ItemStack[]::new))
                        .values()) {
                    left += rest.getAmount();
                    player.getWorld().dropItem(player.getLocation(), rest, item -> {
                        item.setOwner(player.getUniqueId());
                        item.setThrower(player.getUniqueId());
                        item.setCanMobPickup(false);
                        item.setWillAge(false);
                        item.setPickupDelay(0);
                    });
                    de.tigges.kaboofcasino.util.Log.get().warn("cashier prize for {}: {} x {} did not fit and was "
                            + "dropped at {} (only {} can pick it up)", player.getName(), rest.getAmount(),
                            rest.getType().getKey(), player.getLocation().toVector(), player.getName());
                }
                return left;
            }
        };
    }
}
