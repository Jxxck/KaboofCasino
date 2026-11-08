package de.tigges.kaboofcasino.wager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * {@link Account} of an online player: ledger in his PDC, items in his storage slots 0-35 (never armour or offhand).
 * Chips ({@link ChipItems}) are recognised by their PDC tag only; a chip value is given in the largest denominations
 * first. Live inventory mirrors are never mutated: clone, change, {@code setItem} (PostMarkt pitfall "Mirrors").
 * Thread: MAIN.
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
        PlayerInventory inv = player.getInventory();
        long sum = 0;
        if (Chips.isValue(item)) {
            for (int slot = 0; slot < 36; slot++) {
                ItemStack s = inv.getItem(slot);
                OptionalInt d = ChipItems.denomination(s);
                if (d.isPresent()) {
                    sum += (long) s.getAmount() * d.getAsInt();
                }
            }
            return sum;
        }
        for (int slot = 0; slot < 36; slot++) {
            if (matches(inv.getItem(slot), item)) {
                sum += inv.getItem(slot).getAmount();
            }
        }
        return sum;
    }

    /**
     * Whether {@code s} counts as {@code item}: a chip of that denomination (any, by its PDC tag), or a plain stack
     * of that material.
     */
    static boolean matches(ItemStack s, String item) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        OptionalInt d = Chips.denomination(item);
        if (d.isPresent()) {
            OptionalInt have = ChipItems.denomination(s);
            return have.isPresent() && have.getAsInt() == d.getAsInt();
        }
        return s.getType() == material(item) && isPlain(s);
    }

    @Override
    public TakePlan planTake(List<ItemAmount> items) {
        PlayerInventory inv = player.getInventory();
        List<int[]> steps = new ArrayList<>();
        for (ItemAmount need : ItemAmount.merge(items)) {
            if (Chips.isValue(need.item())) {
                throw new IllegalArgumentException("a chip value is paid with planPay, not taken: " + need);
            }
            long left = need.count();
            // chips: pristine stacks first (a renamed chip is still a chip, but goes last)
            for (int pass = 0; pass < 2 && left > 0; pass++) {
                for (int slot = 0; slot < 36 && left > 0; slot++) {
                    ItemStack s = inv.getItem(slot);
                    if (!matches(s, need.item()) || taken(steps, slot)) {
                        continue;
                    }
                    boolean pristine = !ChipItems.isChip(s) || ChipItems.pristine(s);
                    if (pristine != (pass == 0)) {
                        continue;
                    }
                    int n = (int) Math.min(left, s.getAmount());
                    steps.add(new int[] {slot, s.getAmount() - n});
                    left -= n;
                }
            }
            if (left > 0) {
                throw new IllegalStateException("cannot take " + need + " from " + player.getName());
            }
        }
        return new Steps(List.copyOf(steps));
    }

    private static boolean taken(List<int[]> steps, int slot) {
        for (int[] step : steps) {
            if (step[0] == slot) {
                return true;
            }
        }
        return false;
    }

    /** Slot -> new amount (0 = empty), one entry per slot. */
    private record Steps(List<int[]> steps) implements TakePlan {
    }

    @Override
    public void applyTake(TakePlan plan) {
        PlayerInventory inv = player.getInventory();
        for (int[] step : ((Steps) plan).steps()) {
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
        for (ItemAmount a : Chips.expand(items)) {
            ItemStack one = ChipItems.stackOf(a.item(), 1);
            int max = one.getMaxStackSize();
            long rest = a.count();
            List<ItemStack> stacks = new ArrayList<>();
            while (rest > 0) {
                int n = (int) Math.min(rest, max);
                ItemStack stack = one.clone();
                stack.setAmount(n);
                stacks.add(stack);
                rest -= n;
            }
            Map<Integer, ItemStack> leftovers = inv.addItem(stacks.toArray(ItemStack[]::new));
            long notFitted = leftovers.values().stream().mapToLong(ItemStack::getAmount).sum();
            if (notFitted > 0) {
                left.add(new ItemAmount(a.item(), notFitted));
            }
        }
        return Chips.normalize(left);
    }

    /**
     * Simulates {@code take} and then {@link #give} on a copy of the storage slots: per kind the room in its
     * similar partial stacks, the rest in fresh stacks of its maximum size, each taking an empty slot
     * ({@code addItem} fills partial stacks first, then empty slots).
     */
    @Override
    public int spareSlots(TakePlan take, List<ItemAmount> items) {
        PlayerInventory inv = player.getInventory();
        int[] amounts = new int[36];
        ItemStack[] stacks = new ItemStack[36];
        for (int slot = 0; slot < 36; slot++) {
            ItemStack s = inv.getItem(slot);
            if (s != null && !s.isEmpty()) {
                stacks[slot] = s;
                amounts[slot] = s.getAmount();
            }
        }
        if (take != null) {
            for (int[] step : ((Steps) take).steps()) {
                amounts[step[0]] = step[1];
            }
        }
        long free = 0;
        for (int slot = 0; slot < 36; slot++) {
            if (amounts[slot] <= 0) {
                free++;
            }
        }
        for (ItemAmount a : Chips.expand(items)) {
            ItemStack one = ChipItems.stackOf(a.item(), 1);
            int max = one.getMaxStackSize();
            long room = 0;
            for (int slot = 0; slot < 36; slot++) {
                if (amounts[slot] > 0 && stacks[slot].isSimilar(one)) {
                    room += Math.max(0, stacks[slot].getMaxStackSize() - amounts[slot]);
                }
            }
            long rest = Math.max(0, a.count() - room);
            free -= (rest + max - 1) / max;
        }
        return (int) Math.max(Integer.MIN_VALUE, free);
    }
}
