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

    /**
     * What the storage slots 0-35 hold of {@code item}: plain stacks of a material; chips of one denomination for a
     * {@link Chips#key denomination key} (any chip of it, by its PDC tag); the total chip value for
     * {@link Chips#VALUE}.
     */
    long countPlain(String item);

    /** The chips in the storage slots, per denomination. */
    default Chips.Selection chips() {
        long[] counts = new long[Chips.DENOMINATIONS.length];
        for (int i = 0; i < counts.length; i++) {
            counts[i] = countPlain(Chips.key(Chips.DENOMINATIONS[i]));
        }
        return new Chips.Selection(counts);
    }

    /** A planned take: the changes to make, computed without touching anything. */
    interface TakePlan {
    }

    /**
     * Plans removing all {@code items} (merged per item) from the storage slots 0-35: plain stacks of a material, or
     * chips of a denomination key; at most one take per slot (PostMarkt RT-01). Changes nothing; throws if something
     * is missing or an item is a {@link Chips#VALUE} (pay a value with {@link #planPay}).
     */
    TakePlan planTake(List<ItemAmount> items);

    /** Applies a plan from {@link #planTake} (same tick, nothing in between). */
    void applyTake(TakePlan plan);

    /** Removes {@code count} plain {@code item}s (plan, then apply). */
    default void take(String item, long count) {
        applyTake(planTake(List.of(new ItemAmount(item, count))));
    }

    /**
     * Puts fresh stacks into the storage slots: plain stacks of a material, chips of a denomination key, a
     * {@link Chips#VALUE} as chips in the largest denominations first. Returns what did not fit (never dropped), chips
     * always as a {@link Chips#VALUE}.
     */
    List<ItemAmount> give(List<ItemAmount> items);

    /** A planned payment of a chip value: the chips to take and the change. */
    record PayPlan(Chips.Payment payment, TakePlan take) {
    }

    /**
     * Plans paying {@code value} in chips out of the storage slots ({@link Chips#pay}: exact chips if they add up,
     * else one chip more and change). Changes nothing; null if the chips are worth less.
     */
    default PayPlan planPay(long value) {
        Chips.Payment payment = Chips.pay(chips(), value);
        if (payment == null) {
            return null;
        }
        return new PayPlan(payment, planTake(payment.take().amounts()));
    }

    /**
     * Applies a {@link #planPay} plan in the same tick: the chips leave, the change comes in as chips. Returns the
     * change that did not fit (a {@link Chips#VALUE}, for the owed list), empty if all fitted.
     */
    default List<ItemAmount> applyPay(PayPlan plan) {
        applyTake(plan.take());
        long change = plan.payment().change();
        return change > 0 ? Chips.normalize(give(List.of(new ItemAmount(Chips.VALUE, change)))) : List.of();
    }

    /**
     * The cashier's room check (PLAN-2 C3): how many storage slots stay empty if {@code take} (null: nothing) is
     * applied and then {@code items} are put in exactly as {@link #give} puts them (fresh stacks, partial stacks of
     * the same kind first, a {@link Chips#VALUE} as chips largest first); negative = that many slots are missing.
     * Changes nothing.
     */
    default int spareSlots(TakePlan take, List<ItemAmount> items) {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " cannot simulate its storage slots");
    }
}
