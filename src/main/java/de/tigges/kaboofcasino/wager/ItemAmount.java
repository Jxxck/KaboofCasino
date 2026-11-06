package de.tigges.kaboofcasino.wager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A count of one plain item, by material key ({@code minecraft:diamond}). Stakes and payouts are always plain items
 * (server.md 1.4): a payout is created fresh as {@code ItemStack.of(material, n)}, so no component is ever stored.
 */
public record ItemAmount(String item, long count) {

    public ItemAmount {
        Objects.requireNonNull(item, "item");
        if (item.isBlank() || count <= 0) {
            throw new IllegalArgumentException("bad item amount " + count + "x" + item);
        }
    }

    /** Adds up equal items, keeping the order of first appearance. */
    public static List<ItemAmount> merge(List<ItemAmount> amounts) {
        Map<String, Long> sum = new LinkedHashMap<>();
        for (ItemAmount a : amounts) {
            sum.merge(a.item(), a.count(), Math::addExact);
        }
        List<ItemAmount> out = new ArrayList<>();
        sum.forEach((item, count) -> out.add(new ItemAmount(item, count)));
        return List.copyOf(out);
    }

    public static long total(List<ItemAmount> amounts, String item) {
        return amounts.stream().filter(a -> a.item().equals(item)).mapToLong(ItemAmount::count).sum();
    }

    @Override
    public String toString() {
        return count + "x" + item;
    }
}
