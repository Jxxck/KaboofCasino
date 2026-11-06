package de.tigges.kaboofcasino.wager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * An in-memory player: inventory as item counts with a capacity in stacks of 64 (36 storage slots by default), and
 * the ledger. {@link #save()} / {@link #restore(Saved)} model the player file: inventory and PDC are always saved and
 * rolled back together (server.md F5).
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
        return count(item);
    }

    @Override
    public void take(String item, long count) {
        long have = count(item);
        if (have < count) {
            throw new IllegalStateException("take more than present");
        }
        if (have == count) {
            inventory.remove(item);
        } else {
            inventory.put(item, have - count);
        }
    }

    @Override
    public List<ItemAmount> give(List<ItemAmount> items) {
        List<ItemAmount> left = new ArrayList<>();
        for (ItemAmount a : items) {
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
        return left;
    }
}
