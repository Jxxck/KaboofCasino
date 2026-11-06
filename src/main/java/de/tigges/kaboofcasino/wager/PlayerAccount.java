package de.tigges.kaboofcasino.wager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * {@link Account} of an online player: ledger in his PDC, items in his storage slots 0-35 (never armour or offhand).
 * Live inventory mirrors are never mutated: clone, change, {@code setItem} (PostMarkt pitfall "Mirrors"). Thread:
 * MAIN.
 */
public final class PlayerAccount implements Account {

    private final Player player;

    public PlayerAccount(Player player) {
        this.player = player;
    }

    public Player player() {
        return player;
    }

    @Override
    public UUID id() {
        return player.getUniqueId();
    }

    @Override
    public String name() {
        return player.getName();
    }

    @Override
    public Ledger ledger() {
        return LedgerCodec.read(player.getPersistentDataContainer());
    }

    @Override
    public void ledger(Ledger ledger) {
        LedgerCodec.write(player.getPersistentDataContainer(), ledger);
    }

    /** A plain stack: nothing but the material's default components (no name, enchantment, damage, data ...). */
    public static boolean isPlain(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.isSimilar(ItemStack.of(stack.getType()));
    }

    public static Material material(String key) {
        Material m = Material.matchMaterial(key);
        if (m == null || !m.isItem()) {
            throw new IllegalArgumentException("not an item: " + key);
        }
        return m;
    }

    @Override
    public long countPlain(String item) {
        Material m = material(item);
        PlayerInventory inv = player.getInventory();
        long sum = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack s = inv.getItem(slot);
            if (s != null && s.getType() == m && isPlain(s)) {
                sum += s.getAmount();
            }
        }
        return sum;
    }

    @Override
    public void take(String item, long count) {
        Material m = material(item);
        PlayerInventory inv = player.getInventory();
        long left = count;
        // plan first (one take per slot), apply afterwards
        List<int[]> plan = new ArrayList<>();
        for (int slot = 0; slot < 36 && left > 0; slot++) {
            ItemStack s = inv.getItem(slot);
            if (s != null && s.getType() == m && isPlain(s)) {
                int n = (int) Math.min(left, s.getAmount());
                plan.add(new int[] {slot, s.getAmount() - n});
                left -= n;
            }
        }
        if (left > 0) {
            throw new IllegalStateException("cannot take " + count + "x" + item + " from " + player.getName());
        }
        for (int[] step : plan) {
            if (step[1] == 0) {
                inv.setItem(step[0], null);
            } else {
                ItemStack copy = inv.getItem(step[0]).clone();
                copy.setAmount(step[1]);
                inv.setItem(step[0], copy);
            }
        }
    }

    @Override
    public List<ItemAmount> give(List<ItemAmount> items) {
        List<ItemAmount> left = new ArrayList<>();
        PlayerInventory inv = player.getInventory();
        for (ItemAmount a : items) {
            Material m = material(a.item());
            int max = m.getMaxStackSize();
            long rest = a.count();
            List<ItemStack> stacks = new ArrayList<>();
            while (rest > 0) {
                int n = (int) Math.min(rest, max);
                stacks.add(ItemStack.of(m, n));
                rest -= n;
            }
            Map<Integer, ItemStack> leftovers = inv.addItem(stacks.toArray(ItemStack[]::new));
            long notFitted = leftovers.values().stream().mapToLong(ItemStack::getAmount).sum();
            if (notFitted > 0) {
                left.add(new ItemAmount(a.item(), notFitted));
            }
        }
        return left;
    }
}
