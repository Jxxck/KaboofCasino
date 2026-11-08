package de.tigges.kaboofcasino.cashier;

import de.tigges.kaboofcasino.wager.Chips;
import java.util.ArrayList;
import java.util.List;

/**
 * Where the cashier menu shows what (the pack's {@code CashierGui} draws its frames and pictures for exactly these
 * slots), and the menu's small pure rules. 9x6 chest:
 * <pre>
 *  row 0     0-2 Wechseln, 3-5 Kleingeld, 6-8 Preise (tab bar in the title)
 *  Wechseln  9-26 the selected ores, 30-35 the chips the exchange gives (1000 ... 1), 45 rates book, 46 clear,
 *            48-51 WECHSELN (art in 49), 53 the value (right panel, row A)
 *  Kleingeld 12-17 the selected chips (1000 ... 1), 20 the target's frame, 21-26 the targets (1000 ... 1),
 *            30-35 the result (1000 ... 1), 46 clear, 48-51 WECHSELN, 53 the value
 *  Preise    9-44 the prizes of a page, 45/46 previous/next page, 48-51 KAUFEN, 53 the player's chips (row A),
 *            52 the selected prize's price (row B)
 * </pre>
 */
public final class CashierView {

    /** The three tabs: their slots in row 0 and their pack names. */
    public enum Tab {
        EXCHANGE("exchange"), CHANGE("change"), SHOP("shop");

        private final String pack;

        Tab(String pack) {
            this.pack = pack;
        }

        public String pack() {
            return pack;
        }

        /** The three row-0 slots of this tab's button. */
        public int[] slots() {
            int first = ordinal() * 3;
            return new int[] {first, first + 1, first + 2};
        }
    }

    public static final int TRAY_FIRST = 9;
    public static final int TRAY_SIZE = 18;
    /** Denominations largest first, as the chip rows show them. */
    public static final int[] ROW_DENOMINATIONS = {1000, 500, 100, 25, 5, 1};
    public static final int PREVIEW_FIRST = 30;
    public static final int SELECTED_FIRST = 12;
    public static final int MARKER = 20;
    public static final int TARGET_FIRST = 21;
    public static final int RESULT_FIRST = 30;
    public static final int RATES_BOOK = 45;
    public static final int CLEAR = 46;
    public static final int PREV = 45;
    public static final int NEXT = 46;
    public static final int[] COMMIT = {48, 49, 50, 51};
    public static final int COMMIT_ART = 49;
    public static final int NUM_A = 53;
    public static final int NUM_B = 52;
    public static final int PRIZES_FIRST = 9;
    public static final int PAGE_SIZE = 36;
    /** Digit cells of a number display. */
    public static final int CELLS = 10;

    private CashierView() {
    }

    /** The slot of denomination {@code d} in a chip row starting at {@code first} (1000 leftmost). */
    public static int chipSlot(int first, int denomination) {
        for (int i = 0; i < ROW_DENOMINATIONS.length; i++) {
            if (ROW_DENOMINATIONS[i] == denomination) {
                return first + i;
            }
        }
        throw new IllegalArgumentException("no chip denomination: " + denomination);
    }

    /** The denomination shown in slot {@code slot} of a chip row starting at {@code first}, or 0. */
    public static int denominationAt(int first, int slot) {
        int i = slot - first;
        return i >= 0 && i < ROW_DENOMINATIONS.length ? ROW_DENOMINATIONS[i] : 0;
    }

    /**
     * The custom model data strings of a number display: string {@code k} is the {@code k}-th digit from the right
     * ("0".."9"), "" for an empty cell. A value with more than {@link #CELLS} digits shows its last ten (never in
     * practice: the shop's prices end at 10^8).
     */
    public static List<String> digits(long value) {
        String s = Long.toString(Math.max(0, value));
        List<String> out = new ArrayList<>();
        for (int k = 0; k < CELLS; k++) {
            out.add(k < s.length() ? String.valueOf(s.charAt(s.length() - 1 - k)) : "");
        }
        return out;
    }

    /** Pages of a shop with {@code entries} prizes (at least one). */
    public static int pages(int entries) {
        return Math.max(1, (entries + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    /** The chips {@code value} gives, per denomination largest first ({@link #ROW_DENOMINATIONS} order). */
    public static long[] breakdownRow(long value) {
        long[] out = new long[ROW_DENOMINATIONS.length];
        for (var a : Chips.breakdown(value)) {
            out[indexOf(Chips.denomination(a.item()).orElseThrow())] += a.count();
        }
        return out;
    }

    /** The counts of a chip list per denomination in {@link #ROW_DENOMINATIONS} order. */
    public static long[] row(List<de.tigges.kaboofcasino.wager.ItemAmount> chips) {
        long[] out = new long[ROW_DENOMINATIONS.length];
        for (var a : chips) {
            out[indexOf(Chips.denomination(a.item()).orElseThrow())] += a.count();
        }
        return out;
    }

    private static int indexOf(int denomination) {
        for (int i = 0; i < ROW_DENOMINATIONS.length; i++) {
            if (ROW_DENOMINATIONS[i] == denomination) {
                return i;
            }
        }
        throw new IllegalArgumentException("no chip denomination: " + denomination);
    }

    /**
     * The count a click on an inventory stack selects (left: that stack added, right: one more, shift-left: all the
     * player has), never above what he has.
     *
     * @param selected already selected of this item
     * @param stack    the clicked stack's size
     * @param owned    how many he has (plain stacks / chips of that denomination)
     */
    public static long select(SelectClick click, long selected, int stack, long owned) {
        long want = switch (click) {
            case RIGHT -> selected + 1;
            case SHIFT -> owned;
            case LEFT -> selected + stack;
        };
        return Math.max(0, Math.min(want, owned));
    }

    /** The bottom clicks that select (the bet slots' rule). */
    public enum SelectClick {
        LEFT, RIGHT, SHIFT
    }
}
