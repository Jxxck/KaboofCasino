package de.tigges.kaboofcasino.wager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * An in-memory player: inventory as item counts with a capacity in stacks of 64 (36 storage slots by default), and
 * the ledger. Chips are counted per denomination key ({@code kaboofcasino:chip_25}); a chip value is given in the
 * largest denominations first, what does not fit comes back as a value. {@link #save()} / {@link #restore(Saved)}
 * model the player file: inventory and PDC are always saved and rolled back together (server.md F5).
 */
final class FakeAccount implements Account {

    record Saved(Map<String, Long> inventory, Ledger ledger) {
    }

    private final UUID id = UUID.randomUUID();
    final Map<String, Long> inventory = new LinkedHashMap<>();
    private Ledger ledger = Ledger.EMPTY;
    int capacityStacks = 36;
    int ledgerWrites;
    /** give() throws for this item before changing anything (exception-safety tests). */
    String failOn;
    /** planTake throws for this item (nothing may change). */
    String failPlanOn;
    int takePlans;

    FakeAccount with(String item, long count) {
        inventory.merge(item, count, Long::sum);
        return this;
    }

    long count(String item) {
        return inventory.getOrDefault(item, 0L);
    }

    Saved save() {
        return new Saved(new LinkedHashMap<>(inventory), ledger);
    }

    void restore(Saved saved) {
        inventory.clear();
        inventory.putAll(saved.inventory());
        ledger = saved.ledger();
    }

    private long usedStacks() {
        return inventory.values().stream().mapToLong(c -> (c + 63) / 64).sum();
    }

    /** The cashier's room check: stacks of 64 per item key after the take and the give (as {@link #give} fills). */
    @Override
    public int spareSlots(TakePlan take, List<ItemAmount> items) {
        Map<String, Long> after = new LinkedHashMap<>(take == null ? inventory : ((Plan) take).after());
        for (ItemAmount a : Chips.expand(items)) {
            after.merge(a.item(), a.count(), Long::sum);
        }
        long used = after.values().stream().mapToLong(c -> (c + 63) / 64).sum();
        return (int) (capacityStacks - used);
    }

    @Override
    public UUID id() {
        return id;
    }

    @Override
    public String name() {
        return "Fake";
    }

    @Override
    public Ledger ledger() {
        return ledger;
    }

    @Override
    public void ledger(Ledger ledger) {
        this.ledger = ledger;
        ledgerWrites++;
    }

    @Override
    public long countPlain(String item) {
        if (Chips.isValue(item)) {
            long v = 0;
            for (int d : Chips.DENOMINATIONS) {
                v += count(Chips.key(d)) * d;
            }
            return v;
        }
        return count(item);
    }

    @Override
    public TakePlan planTake(List<ItemAmount> items) {
        Map<String, Long> after = new LinkedHashMap<>(inventory);
        for (ItemAmount need : ItemAmount.merge(items)) {
            if (Chips.isValue(need.item())) {
                throw new IllegalArgumentException("a chip value is paid, not taken");
            }
            if (need.item().equals(failPlanOn)) {
                throw new IllegalStateException("planning failure for " + need.item());
            }
            long have = after.getOrDefault(need.item(), 0L);
            if (have < need.count()) {
                throw new IllegalStateException("take more than present");
            }
            after.put(need.item(), have - need.count());
        }
        takePlans++;
        return new Plan(after);
    }

    private record Plan(Map<String, Long> after) implements TakePlan {
    }

    @Override
    public void applyTake(TakePlan plan) {
        inventory.clear();
        ((Plan) plan).after().forEach((item, n) -> {
            if (n > 0) {
                inventory.put(item, n);
            }
        });
    }

    @Override
    public List<ItemAmount> give(List<ItemAmount> items) {
        List<ItemAmount> left = new ArrayList<>();
        for (ItemAmount a : Chips.expand(items)) {
            if (a.item().equals(failOn)) {
                throw new IllegalStateException("give fails for " + failOn);
            }
            long rest = a.count();
            // first top up the partial stack of the same item, then free slots
            long have = count(a.item());
            long room = have % 64 == 0 ? 0 : 64 - have % 64;
            long put = Math.min(rest, room);
            rest -= put;
            long freeStacks = capacityStacks - usedStacks();
            long more = Math.min(rest, Math.max(0, freeStacks) * 64);
            put += more;
            rest -= more;
            if (put > 0) {
                inventory.merge(a.item(), put, Long::sum);
            }
            if (rest > 0) {
                left.add(new ItemAmount(a.item(), rest));
            }
        }
        return Chips.normalize(left);
    }
}
