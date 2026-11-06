package de.tigges.kaboofcasino.wager;

import java.util.List;
import java.util.UUID;

/**
 * One player's two money stores, which the server saves together in his {@code .dat} (server.md F5): the casino
 * ledger (PDC) and the inventory. All calls run on MAIN in the same tick; implementations never save the player.
 */
public interface Account {

    UUID id();

    String name();

    /** Reads the ledger. */
    Ledger ledger();

    /** Replaces the ledger (one PDC write; an empty ledger removes the key). */
    void ledger(Ledger ledger);

    /** Plain stacks of {@code item} in the storage slots 0-35. */
    long countPlain(String item);

    /** A planned take: the changes to make, computed without touching anything. */
    interface TakePlan {
    }

    /**
     * Plans removing all {@code items} (merged per item) as plain stacks from the storage slots 0-35, at most one
     * take per slot (PostMarkt RT-01). Changes nothing; throws if something is missing.
     */
    TakePlan planTake(List<ItemAmount> items);

    /** Applies a plan from {@link #planTake} (same tick, nothing in between). */
    void applyTake(TakePlan plan);

    /** Removes {@code count} plain {@code item}s (plan, then apply). */
    default void take(String item, long count) {
        applyTake(planTake(List.of(new ItemAmount(item, count))));
    }

    /** Puts fresh plain stacks into the storage slots; returns what did not fit (never dropped). */
    List<ItemAmount> give(List<ItemAmount> items);
}
