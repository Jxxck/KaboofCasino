package de.tigges.kaboofcasino.wager;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * Death winnings and DeathBan's grave (pure, unit-tested). DeathBan builds its grave from {@code getDrops()} at HIGH:
 * one chest, a second above 27 stacks, at most {@link #GRAVE_SLOTS} slots filled with {@code addItem}; whatever does
 * not fit stays in the drops and falls on the ground. So the casino puts winnings into the drops only as far as they
 * fit: first into partial plain stacks of the same item already in the drops, then into free slots (every non-empty
 * drop counts as one used slot, which is conservative because {@code addItem} merges). The rest stays owed in the
 * player PDC and is delivered after a revive by the normal owed timer. Nothing goes on the ground.
 */
public final class DeathDrops {

    public static final int GRAVE_SLOTS = 54;

    /** A stack already in the drop list, as the planner sees it. */
    public record Existing(String item, int amount, int maxStack, boolean plain) {
    }

    /**
     * @param topUps    index into the existing drops -> items added to that stack
     * @param newStacks stacks to append (each at most a full stack)
     * @param kept      what does not fit: stays owed
     */
    public record Plan(Map<Integer, Integer> topUps, List<ItemAmount> newStacks, List<ItemAmount> kept) {
        public Plan {
            topUps = Map.copyOf(topUps);
            newStacks = List.copyOf(newStacks);
            kept = ItemAmount.merge(kept);
        }
    }

    private DeathDrops() {
    }

    public static Plan plan(List<Existing> existing, List<ItemAmount> winnings, ToIntFunction<String> maxStack,
                            int slots) {
        Map<Integer, Integer> topUps = new java.util.HashMap<>();
        List<ItemAmount> stacks = new ArrayList<>();
        List<ItemAmount> kept = new ArrayList<>();
        int used = existing.size();
        int free = Math.max(0, slots - used);
        for (ItemAmount w : ItemAmount.merge(winnings)) {
            long rest = w.count();
            for (int i = 0; i < existing.size() && rest > 0; i++) {
                Existing e = existing.get(i);
                if (!e.plain() || !e.item().equals(w.item())) {
                    continue;
                }
                int room = e.maxStack() - e.amount() - topUps.getOrDefault(i, 0);
                if (room > 0) {
                    int add = (int) Math.min(room, rest);
                    topUps.merge(i, add, Integer::sum);
                    rest -= add;
                }
            }
            int max = maxStack.applyAsInt(w.item());
            while (rest > 0 && free > 0) {
                int n = (int) Math.min(rest, max);
                stacks.add(new ItemAmount(w.item(), n));
                rest -= n;
                free--;
            }
            if (rest > 0) {
                kept.add(new ItemAmount(w.item(), rest));
            }
        }
        return new Plan(topUps, stacks, kept);
    }

    /** Reads and changes stack amounts of the platform's stack type. */
    public interface StackOps<T> {
        String item(T stack);

        int amount(T stack);

        void amount(T stack, int amount);
    }

    /** What was put into the drops, by stack identity, so a cancelled death can take exactly that back out. */
    public static final class Applied<T> {
        private final List<T> added = new ArrayList<>();
        private final Map<T, Integer> toppedUp = new IdentityHashMap<>();

        public void added(T stack) {
            added.add(stack);
        }

        public void toppedUp(T stack, int amount) {
            toppedUp.merge(stack, amount, Integer::sum);
        }

        public boolean isEmpty() {
            return added.isEmpty() && toppedUp.isEmpty();
        }

        /**
         * Takes the winnings back out of {@code drops}: only stacks still found there (by identity) and only as much as
         * they still hold.
         *
         * @return restored (to give back) and missing (CRITICAL: someone took them out already)
         */
        public Restore restore(List<T> drops, StackOps<T> ops) {
            List<ItemAmount> restored = new ArrayList<>();
            List<ItemAmount> missing = new ArrayList<>();
            for (T stack : added) {
                int idx = indexOf(drops, stack);
                int amount = ops.amount(stack);
                if (idx >= 0 && amount > 0) {
                    drops.remove(idx);
                    restored.add(new ItemAmount(ops.item(stack), amount));
                } else if (amount > 0) {
                    missing.add(new ItemAmount(ops.item(stack), amount));
                }
            }
            for (Map.Entry<T, Integer> e : toppedUp.entrySet()) {
                T stack = e.getKey();
                int add = e.getValue();
                int idx = indexOf(drops, stack);
                int have = ops.amount(stack);
                int take = idx >= 0 ? Math.min(add, have) : 0;
                if (take > 0) {
                    if (take == have) {
                        drops.remove(idx);
                    } else {
                        ops.amount(stack, have - take);
                    }
                    restored.add(new ItemAmount(ops.item(stack), take));
                }
                if (take < add) {
                    missing.add(new ItemAmount(ops.item(stack), add - take));
                }
            }
            return new Restore(ItemAmount.merge(restored), ItemAmount.merge(missing));
        }

        private static <T> int indexOf(List<T> drops, T stack) {
            for (int i = 0; i < drops.size(); i++) {
                if (drops.get(i) == stack) {
                    return i;
                }
            }
            return -1;
        }
    }

    /** Result of {@link Applied#restore}. */
    public record Restore(List<ItemAmount> restored, List<ItemAmount> missing) {
    }
}
