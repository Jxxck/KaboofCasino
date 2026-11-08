package de.tigges.kaboofcasino.cashier;

import de.tigges.kaboofcasino.wager.Chips;
import de.tigges.kaboofcasino.wager.ItemAmount;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.function.Function;

/**
 * The cashier's arithmetic (PLAN-2 C3), pure: the chip value of an ore selection at the configured rates, and the
 * change of a chip selection into a target denomination. Values are exact longs ({@code Math.*Exact}).
 */
public final class CashierMath {

    private CashierMath() {
    }

    /**
     * The chip value of {@code items} at {@code rate} (sum of count x rate), or -1 if an item has no rate or a count
     * is below 1.
     */
    public static long value(List<ItemAmount> items, Function<String, OptionalLong> rate) {
        long sum = 0;
        for (ItemAmount a : items) {
            OptionalLong r = rate.apply(a.item());
            if (r.isEmpty() || a.count() < 1) {
                return -1;
            }
            sum = Math.addExact(sum, Math.multiplyExact(a.count(), r.getAsLong()));
        }
        return sum;
    }

    /**
     * {@code value} in chips of {@code target} as far as it goes, the rest in the next smaller denominations, largest
     * first (denomination keys with chip counts). Value-neutral by construction: the result is worth {@code value}.
     */
    public static List<ItemAmount> change(long value, int target) {
        Chips.index(target);
        if (value < 0) {
            throw new IllegalArgumentException("negative value " + value);
        }
        List<ItemAmount> out = new ArrayList<>();
        long n = value / target;
        if (n > 0) {
            out.add(new ItemAmount(Chips.key(target), n));
        }
        out.addAll(Chips.breakdown(value % target));
        return List.copyOf(out);
    }

    /** True if {@code result} holds exactly the chips of {@code selection} (the change would move nothing). */
    public static boolean same(Chips.Selection selection, List<ItemAmount> result) {
        long[] counts = new long[Chips.DENOMINATIONS.length];
        for (ItemAmount a : result) {
            counts[Chips.index(Chips.denomination(a.item()).orElseThrow())] += a.count();
        }
        return new Chips.Selection(counts).equals(selection);
    }

    /** A chip value in German digits ({@link de.tigges.kaboofcasino.util.ChipText#number}): 1234567 -> 1.234.567. */
    public static String format(long value) {
        return de.tigges.kaboofcasino.util.ChipText.number(value);
    }
}
