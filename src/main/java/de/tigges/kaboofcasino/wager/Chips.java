package de.tigges.kaboofcasino.wager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Casino chips (PLAN-2 C1/C2), pure. A chip is a real item of one denomination ({@link #DENOMINATIONS}); stakes,
 * payouts and owed chips are a <b>chip value</b>. Both live in {@link ItemAmount}s:
 * <ul>
 *   <li>{@link #VALUE} ({@code kaboofcasino:chips}) with {@code count} = a chip value: what round records, payouts and
 *       the owed list hold (ledger version 3);</li>
 *   <li>{@link #key(int)} ({@code kaboofcasino:chip_25}) with {@code count} = a number of chips of that denomination:
 *       what is taken from or put into an inventory, never stored in the ledger.</li>
 * </ul>
 * A value is paid out in the largest denominations first ({@link #breakdown}). Ore stakes of older records
 * ({@code minecraft:diamond}) stay plain item amounts and are paid in ores.
 */
public final class Chips {

    /** The chip value pseudo item: {@code count} is a value in chips (never a material). */
    public static final String VALUE = "kaboofcasino:chips";
    /**
     * Prefix of the denomination keys, which are also the chips' casino item ids (PDC {@code kaboofcasino:item} =
     * {@code kaboofcasino:chip_25}, C1).
     */
    public static final String KEY_PREFIX = "kaboofcasino:chip_";
    /** The denominations, smallest first (C1: white, red, green, black, purple, gold). Each divides the next. */
    public static final int[] DENOMINATIONS = {1, 5, 25, 100, 500, 1000};
    /** The colour names of the denominations, same order (pack art, texts). */
    public static final List<String> COLOURS = List.of("white", "red", "green", "black", "purple", "gold");

    private Chips() {
    }

    /** {@code kaboofcasino:chip_<denomination>}. */
    public static String key(int denomination) {
        index(denomination);
        return KEY_PREFIX + denomination;
    }

    /** Index of a denomination in {@link #DENOMINATIONS}; throws for anything else. */
    public static int index(int denomination) {
        for (int i = 0; i < DENOMINATIONS.length; i++) {
            if (DENOMINATIONS[i] == denomination) {
                return i;
            }
        }
        throw new IllegalArgumentException("no chip denomination: " + denomination);
    }

    public static boolean isValue(String item) {
        return VALUE.equals(item);
    }

    /** The denomination of a denomination key ({@code kaboofcasino:chip_25} -> 25), empty for anything else. */
    public static OptionalInt denomination(String item) {
        if (item == null || !item.startsWith(KEY_PREFIX)) {
            return OptionalInt.empty();
        }
        return parse(item.substring(KEY_PREFIX.length()));
    }

    private static OptionalInt parse(String digits) {
        if (digits.isEmpty() || digits.length() > 4 || !digits.chars().allMatch(Character::isDigit)) {
            return OptionalInt.empty();
        }
        int d = Integer.parseInt(digits);
        for (int den : DENOMINATIONS) {
            if (den == d) {
                return OptionalInt.of(d);
            }
        }
        return OptionalInt.empty();
    }

    /** True for {@link #VALUE} and the denomination keys. */
    public static boolean isChips(String item) {
        return isValue(item) || denomination(item).isPresent();
    }

    /**
     * {@code value} in chips, largest denominations first (the payout rule, C2): one amount per denomination used,
     * counts may exceed a stack. Empty for 0.
     */
    public static List<ItemAmount> breakdown(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("negative chip value " + value);
        }
        List<ItemAmount> out = new ArrayList<>();
        long rest = value;
        for (int i = DENOMINATIONS.length - 1; i >= 0 && rest > 0; i--) {
            long n = rest / DENOMINATIONS[i];
            if (n > 0) {
                out.add(new ItemAmount(KEY_PREFIX + DENOMINATIONS[i], n));
                rest -= n * DENOMINATIONS[i];
            }
        }
        return List.copyOf(out);
    }

    /** The chip value of an amount: a value, chips of one denomination, or 0 for anything else (ores). */
    public static long valueOf(ItemAmount a) {
        if (isValue(a.item())) {
            return a.count();
        }
        OptionalInt d = denomination(a.item());
        return d.isPresent() ? Math.multiplyExact(a.count(), (long) d.getAsInt()) : 0L;
    }

    /** The chip value of a list (values and denominations; other items count 0). */
    public static long valueOf(List<ItemAmount> amounts) {
        long sum = 0;
        for (ItemAmount a : amounts) {
            sum = Math.addExact(sum, valueOf(a));
        }
        return sum;
    }

    /**
     * Folds denomination amounts into one {@link #VALUE} amount (where the ledger keeps chips), other items unchanged
     * and merged.
     */
    public static List<ItemAmount> normalize(List<ItemAmount> amounts) {
        List<ItemAmount> out = new ArrayList<>();
        long value = 0;
        for (ItemAmount a : amounts) {
            if (isChips(a.item())) {
                value = Math.addExact(value, valueOf(a));
            } else {
                out.add(a);
            }
        }
        if (value > 0) {
            out.add(new ItemAmount(VALUE, value));
        }
        return ItemAmount.merge(out);
    }

    /** Replaces every {@link #VALUE} amount by its {@link #breakdown} (other items unchanged). */
    public static List<ItemAmount> expand(List<ItemAmount> amounts) {
        List<ItemAmount> out = new ArrayList<>();
        for (ItemAmount a : amounts) {
            if (isValue(a.item())) {
                out.addAll(breakdown(a.count()));
            } else {
                out.add(a);
            }
        }
        return ItemAmount.merge(out);
    }

    /** A selection of chips: how many of each denomination ({@link #DENOMINATIONS} order). Immutable. */
    public record Selection(long[] counts) {

        public static final Selection EMPTY = new Selection(new long[DENOMINATIONS.length]);

        public Selection {
            if (counts.length != DENOMINATIONS.length) {
                throw new IllegalArgumentException("one count per denomination");
            }
            counts = counts.clone();
            for (long c : counts) {
                if (c < 0) {
                    throw new IllegalArgumentException("negative chip count");
                }
            }
        }

        public static Selection of(Map<Integer, Long> byDenomination) {
            long[] c = new long[DENOMINATIONS.length];
            byDenomination.forEach((d, n) -> c[index(d)] += n);
            return new Selection(c);
        }

        public long count(int denomination) {
            return counts[index(denomination)];
        }

        public Selection plus(int denomination, long n) {
            long[] c = counts.clone();
            c[index(denomination)] = Math.addExact(c[index(denomination)], n);
            return new Selection(c);
        }

        /** One chip of the smallest denomination fewer (the bet slot's right click); unchanged if empty. */
        public Selection minusSmallest() {
            long[] c = counts.clone();
            for (int i = 0; i < c.length; i++) {
                if (c[i] > 0) {
                    c[i]--;
                    return new Selection(c);
                }
            }
            return this;
        }

        public long value() {
            long v = 0;
            for (int i = 0; i < counts.length; i++) {
                v = Math.addExact(v, Math.multiplyExact(counts[i], (long) DENOMINATIONS[i]));
            }
            return v;
        }

        public long chips() {
            long n = 0;
            for (long c : counts) {
                n += c;
            }
            return n;
        }

        public boolean isEmpty() {
            return chips() == 0;
        }

        /** The largest denomination in the selection, or 0. */
        public int largest() {
            for (int i = counts.length - 1; i >= 0; i--) {
                if (counts[i] > 0) {
                    return DENOMINATIONS[i];
                }
            }
            return 0;
        }

        /** As denomination amounts (what leaves the inventory), largest first. */
        public List<ItemAmount> amounts() {
            List<ItemAmount> out = new ArrayList<>();
            for (int i = counts.length - 1; i >= 0; i--) {
                if (counts[i] > 0) {
                    out.add(new ItemAmount(KEY_PREFIX + DENOMINATIONS[i], counts[i]));
                }
            }
            return List.copyOf(out);
        }

        /** As a chip value amount (what a round record holds); null if empty. */
        public ItemAmount asValue() {
            long v = value();
            return v > 0 ? new ItemAmount(VALUE, v) : null;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Selection s && java.util.Arrays.equals(counts, s.counts);
        }

        @Override
        public int hashCode() {
            return java.util.Arrays.hashCode(counts);
        }

        @Override
        public String toString() {
            StringBuilder b = new StringBuilder();
            for (ItemAmount a : amounts()) {
                if (!b.isEmpty()) {
                    b.append('+');
                }
                b.append(a.count()).append('x').append(a.item().substring(KEY_PREFIX.length()));
            }
            return b.isEmpty() ? "0" : b.toString();
        }
    }

    /**
     * Paying {@code value} out of {@code available} chips (a double down, a poker call, a purchase): which chips to
     * take and how much change comes back.
     *
     * @param take   chips to take
     * @param change value to give back (0 if the chips add up exactly)
     */
    public record Payment(Selection take, long change) {
    }

    /**
     * Picks chips worth exactly {@code value} if possible, else the chips worth the smallest total above it (the least
     * change; for a total, the largest chips first, so the fewest chips). With denominations that divide each other,
     * largest first finds an exact combination for a total whenever one exists; the smallest reachable total lies
     * below {@code value} + the largest denomination (all chips up to the value, plus one bigger chip). Null if the
     * chips are worth less than {@code value}.
     */
    public static Payment pay(Selection available, long value) {
        if (value < 0) {
            throw new IllegalArgumentException("negative value " + value);
        }
        if (available.value() < value) {
            return null;
        }
        long last = Math.min(available.value(), value + DENOMINATIONS[DENOMINATIONS.length - 1]);
        for (long total = value; total <= last; total++) {
            long[] take = exact(available, total);
            if (take != null) {
                return new Payment(new Selection(take), total - value);
            }
        }
        throw new IllegalStateException("no chip combination between " + value + " and " + last);
    }

    /** Chips worth exactly {@code total}, largest first, or null. */
    private static long[] exact(Selection available, long total) {
        long[] take = new long[DENOMINATIONS.length];
        long rest = total;
        for (int i = DENOMINATIONS.length - 1; i >= 0; i--) {
            long n = Math.min(available.counts()[i], rest / DENOMINATIONS[i]);
            take[i] = n;
            rest -= n * DENOMINATIONS[i];
        }
        return rest == 0 ? take : null;
    }
}
