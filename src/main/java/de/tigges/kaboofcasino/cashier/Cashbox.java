package de.tigges.kaboofcasino.cashier;

import de.tigges.kaboofcasino.util.Threads;
import de.tigges.kaboofcasino.wager.Account;
import de.tigges.kaboofcasino.wager.Chips;
import de.tigges.kaboofcasino.wager.ItemAmount;
import de.tigges.kaboofcasino.wager.Ledger;
import java.time.LocalDate;
import java.util.List;
import java.util.OptionalLong;
import java.util.function.Function;

/**
 * The cashier's three transactions (PLAN-2 C3) on a player's {@link Account}, each in one tick and all or nothing
 * (server.md 1.2): every check and every plan runs <b>before</b> the first change; after it nothing can refuse any
 * more, and the remaining steps are plain inventory/PDC writes on the main thread, which no event, autosave or other
 * player can interleave. Inventory, ledger and the daily counts all live in the player's file, so a crash before the
 * next save rolls a whole transaction back, never a part of it.
 * <ul>
 *   <li><b>Exchange</b> (Wechseln): the ores leave ({@code planTake}/{@code applyTake}), the chips come in
 *       ({@code give} of the value: largest denominations first), what does not fit goes onto the owed list as chips
 *       in <b>one</b> ledger write (only if something is left over). No way back from chips to ores.</li>
 *   <li><b>Change</b> (Kleingeld): value-neutral; the result must fit completely ({@link Account#spareSlots}), else
 *       nothing changes. No owed delivery: the owed list is only a safety net that the check makes unreachable.</li>
 *   <li><b>Buy</b> (Preise): the price is paid in chips ({@code planPay}: exact chips, or one larger chip and the
 *       change back), the prize comes in, the day's count goes up; room for the prize <b>and</b> the change is checked
 *       first. No owed delivery of prizes.</li>
 * </ul>
 * Thread: MAIN. An unreadable ledger throws {@code CorruptLedgerException} before anything changed.
 */
public final class Cashbox {

    /** Why a transaction was refused (nothing changed). */
    public enum Refusal {
        /** Nothing selected. */
        EMPTY,
        /** An item the cashier does not take (no rate; chips are never exchanged back). */
        NOT_ACCEPTED,
        /** The selection (or the price) is not in the storage slots any more. */
        NOT_ENOUGH,
        /** The result does not fit into the storage slots. */
        NO_ROOM,
        /** The change would hand back exactly the selected chips. */
        NOTHING_TO_CHANGE,
        /** The prize's purchases for today are used up. */
        LIMIT
    }

    /** What a transaction did. */
    public sealed interface Outcome permits Exchanged, Changed, Bought, Refused {
    }

    /** @param item the item the refusal is about, or null */
    public record Refused(Refusal refusal, String item) implements Outcome {
    }

    /**
     * @param taken the ores that left the inventory
     * @param value their chip value (what the player got)
     * @param owed  the part of it that did not fit and is now owed (chip value)
     */
    public record Exchanged(List<ItemAmount> taken, long value, long owed) implements Outcome {
        public Exchanged {
            taken = List.copyOf(taken);
        }
    }

    /**
     * @param taken the chips that left the inventory (denomination keys)
     * @param given the chips that came in (denomination keys)
     * @param value both sides' chip value
     * @param owed  0 (a value above 0 means the room check was wrong: kept owed, never lost)
     */
    public record Changed(List<ItemAmount> taken, List<ItemAmount> given, long value, long owed) implements Outcome {
        public Changed {
            taken = List.copyOf(taken);
            given = List.copyOf(given);
        }
    }

    /**
     * @param entry     the prize
     * @param paid      the chips that left the inventory (denomination keys)
     * @param change    the change given back (chip value)
     * @param owed      0 (change that did not fit despite the check; kept owed, never lost)
     * @param goodsLeft 0 (prize items that did not fit despite the check)
     * @param limits    today's counts after the purchase
     */
    public record Bought(ShopEntry entry, List<ItemAmount> paid, long change, long owed, int goodsLeft,
                         DailyLimits limits) implements Outcome {
        public Bought {
            paid = List.copyOf(paid);
        }
    }

    /** The prize side of a purchase (the Bukkit layer builds a fresh stack of the entry's item). */
    public interface Goods {
        /** Empty storage slots the prize needs once its similar partial stacks are topped up; changes nothing. */
        int slotsNeeded();

        /** Puts the prize into the storage slots; returns how many items did not fit (0 after the room check). */
        int give();
    }

    /** Where the daily counts are kept (the player's PDC). */
    public interface LimitStore {
        String read();

        void write(String value);
    }

    private Cashbox() {
    }

    private static Refused refused(Refusal r, String item) {
        return new Refused(r, item);
    }

    /** Wechseln: {@code selection} (ore counts) for chips at {@code rate}. */
    public static Outcome exchange(Account account, List<ItemAmount> selection,
                                   Function<String, OptionalLong> rate) {
        Threads.requireMain();
        List<ItemAmount> ores = ItemAmount.merge(selection);
        if (ores.isEmpty()) {
            return refused(Refusal.EMPTY, null);
        }
        for (ItemAmount a : ores) {
            if (Chips.isChips(a.item()) || rate.apply(a.item()).isEmpty()) {
                return refused(Refusal.NOT_ACCEPTED, a.item());
            }
        }
        long value = CashierMath.value(ores, rate);
        Ledger ledger = account.ledger();
        for (ItemAmount a : ores) {
            if (account.countPlain(a.item()) < a.count()) {
                return refused(Refusal.NOT_ENOUGH, a.item());
            }
        }
        Account.TakePlan take = account.planTake(ores);
        // ---- nothing refuses from here on ----
        account.applyTake(take);
        long owed = owed(account.give(List.of(new ItemAmount(Chips.VALUE, value))));
        if (owed > 0) {
            account.ledger(ledger.plusOwed(List.of(new ItemAmount(Chips.VALUE, owed))));
        }
        return new Exchanged(ores, value, owed);
    }

    /** Kleingeld: the selected chips into {@code target} (the rest in the next smaller denominations). */
    public static Outcome change(Account account, Chips.Selection selection, int target) {
        Threads.requireMain();
        Chips.index(target);
        if (selection.isEmpty()) {
            return refused(Refusal.EMPTY, null);
        }
        long value = selection.value();
        List<ItemAmount> result = CashierMath.change(value, target);
        if (CashierMath.same(selection, result)) {
            return refused(Refusal.NOTHING_TO_CHANGE, null);
        }
        Ledger ledger = account.ledger();
        List<ItemAmount> taken = selection.amounts();
        for (ItemAmount a : taken) {
            if (account.countPlain(a.item()) < a.count()) {
                return refused(Refusal.NOT_ENOUGH, a.item());
            }
        }
        Account.TakePlan take = account.planTake(taken);
        if (account.spareSlots(take, result) < 0) {
            return refused(Refusal.NO_ROOM, null);
        }
        // ---- nothing refuses from here on ----
        account.applyTake(take);
        long owed = owed(account.give(result));
        if (owed > 0) {
            account.ledger(ledger.plusOwed(List.of(new ItemAmount(Chips.VALUE, owed))));
        }
        return new Changed(taken, result, value, owed);
    }

    /** Preise: one purchase of {@code entry} on {@code today} (Berlin). */
    public static Outcome buy(Account account, ShopEntry entry, Goods goods, LimitStore store, LocalDate today) {
        Threads.requireMain();
        DailyLimits limits = DailyLimits.read(store.read(), today);
        if (limits.left(entry) <= 0) {
            return refused(Refusal.LIMIT, null);
        }
        Ledger ledger = account.ledger();
        Account.PayPlan pay = account.planPay(entry.price());
        if (pay == null) {
            return refused(Refusal.NOT_ENOUGH, Chips.VALUE);
        }
        long change = pay.payment().change();
        List<ItemAmount> changeChips = change > 0 ? List.of(new ItemAmount(Chips.VALUE, change)) : List.of();
        if (account.spareSlots(pay.take(), changeChips) < goods.slotsNeeded()) {
            return refused(Refusal.NO_ROOM, null);
        }
        // ---- nothing refuses from here on ----
        long owed = owed(account.applyPay(pay));
        int goodsLeft = goods.give();
        DailyLimits next = limits;
        if (entry.limit() > 0) {
            next = limits.plus(entry.key());
            store.write(next.encode());
        }
        if (owed > 0) {
            account.ledger(ledger.plusOwed(List.of(new ItemAmount(Chips.VALUE, owed))));
        }
        return new Bought(entry, pay.payment().take().amounts(), change, owed, goodsLeft, next);
    }

    /** The chip value of what {@code give} returned as not fitting. */
    private static long owed(List<ItemAmount> left) {
        return Chips.valueOf(Chips.normalize(left));
    }
}
