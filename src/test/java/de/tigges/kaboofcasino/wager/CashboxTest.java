package de.tigges.kaboofcasino.wager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.cashier.Cashbox;
import de.tigges.kaboofcasino.cashier.CashierSettings;
import de.tigges.kaboofcasino.cashier.DailyLimits;
import de.tigges.kaboofcasino.cashier.ShopEntry;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Random;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * The cashier's three transactions (PLAN-2 C3) against the in-memory player: exchange, change and purchase are all or
 * nothing, chips come in largest first, what does not fit is owed (exchange only) in one ledger write, and every
 * refusal leaves inventory, ledger and the day's counts exactly as they were. A crash (the player file restored from
 * before or after the save) never shows half a transaction because both stores roll back together.
 */
class CashboxTest {

    static final String COAL = "minecraft:coal";
    static final String DIAMOND = "minecraft:diamond";
    static final String IRON_BLOCK = "minecraft:iron_block";
    static final Function<String, OptionalLong> RATES = CashierSettings.DEFAULTS::rate;
    static final LocalDate DAY = LocalDate.of(2026, 11, 8);

    static String chip(int d) {
        return Chips.key(d);
    }

    /** The day's counts in a field (the player's PDC). */
    static final class Store implements Cashbox.LimitStore {
        String value;
        int writes;

        @Override
        public String read() {
            return value;
        }

        @Override
        public void write(String v) {
            value = v;
            writes++;
        }
    }

    /** A prize of {@code slots} empty slots that counts its deliveries (the fake inventory has no prize items). */
    static final class Prize implements Cashbox.Goods {
        final int slots;
        int given;

        Prize(int slots) {
            this.slots = slots;
        }

        @Override
        public int slotsNeeded() {
            return slots;
        }

        @Override
        public int give() {
            given++;
            return 0;
        }
    }

    private static void unchanged(FakeAccount.Saved before, FakeAccount a) {
        assertEquals(before.inventory(), a.save().inventory());
        assertEquals(before.ledger(), a.save().ledger());
    }

    // ---- exchange --------------------------------------------------------------------------------------------------

    @Test
    void anExchangeTakesTheOresAndPaysTheirValueLargestChipsFirst() {
        FakeAccount a = new FakeAccount().with(DIAMOND, 40).with(COAL, 7);
        Cashbox.Outcome o = Cashbox.exchange(a, List.of(new ItemAmount(DIAMOND, 40), new ItemAmount(COAL, 7)),
                RATES);
        Cashbox.Exchanged done = assertInstanceOf(Cashbox.Exchanged.class, o);
        assertEquals(40 * 32 + 7, done.value());
        assertEquals(0, done.owed());
        assertEquals(0, a.count(DIAMOND));
        assertEquals(0, a.count(COAL));
        // 1287 = 1 x 1000 + 2 x 100 + 3 x 25 + 2 x 5 + 2 x 1
        assertEquals(1, a.count(chip(1000)));
        assertEquals(0, a.count(chip(500)));
        assertEquals(2, a.count(chip(100)));
        assertEquals(3, a.count(chip(25)));
        assertEquals(2, a.count(chip(5)));
        assertEquals(2, a.count(chip(1)));
        assertEquals(1287, a.countPlain(Chips.VALUE));
        assertEquals(0, a.ledgerWrites, "nothing owed: no ledger write");
    }

    @Test
    void whatDoesNotFitIsOwedInOneLedgerWrite() {
        // 35 stacks of dirt + 64 iron blocks: after the take one slot is free for 64 x 36 = 2304 = 2 x 1000 + 3 x 100
        // + 4 x 1: three kinds, two of them owed
        FakeAccount a = new FakeAccount().with("minecraft:dirt", 35 * 64).with(IRON_BLOCK, 64);
        Cashbox.Exchanged done = assertInstanceOf(Cashbox.Exchanged.class, Cashbox.exchange(a,
                List.of(new ItemAmount(IRON_BLOCK, 64)), RATES));
        assertEquals(2304, done.value());
        assertEquals(1, a.ledgerWrites);
        long inInventory = a.countPlain(Chips.VALUE);
        assertEquals(2304, inInventory + done.owed());
        assertTrue(done.owed() > 0);
        assertEquals(List.of(new ItemAmount(Chips.VALUE, done.owed())), a.ledger().owed());
        assertEquals(0, a.count(IRON_BLOCK));
    }

    @Test
    void refusedExchangesChangeNothing() {
        FakeAccount a = new FakeAccount().with(DIAMOND, 5).with(chip(25), 4).with("minecraft:dirt", 3);
        FakeAccount.Saved before = a.save();
        assertEquals(Cashbox.Refusal.EMPTY, ((Cashbox.Refused) Cashbox.exchange(a, List.of(), RATES)).refusal());
        assertEquals(Cashbox.Refusal.NOT_ACCEPTED, ((Cashbox.Refused) Cashbox.exchange(a,
                List.of(new ItemAmount("minecraft:dirt", 3)), RATES)).refusal());
        Cashbox.Refused chips = (Cashbox.Refused) Cashbox.exchange(a, List.of(new ItemAmount(chip(25), 4)), RATES);
        assertEquals(Cashbox.Refusal.NOT_ACCEPTED, chips.refusal(), "no way back from chips");
        Cashbox.Refused short_ = (Cashbox.Refused) Cashbox.exchange(a, List.of(new ItemAmount(DIAMOND, 4),
                new ItemAmount(DIAMOND, 2)), RATES);
        assertEquals(Cashbox.Refusal.NOT_ENOUGH, short_.refusal());
        assertEquals(DIAMOND, short_.item());
        unchanged(before, a);
        assertEquals(0, a.ledgerWrites);
        // a planning failure throws before the first change
        a.failPlanOn = DIAMOND;
        assertThrows(IllegalStateException.class, () -> Cashbox.exchange(a, List.of(new ItemAmount(DIAMOND, 5)),
                RATES));
        unchanged(before, a);
    }

    @Test
    void anUnreadableLedgerRefusesBeforeAnythingMoves() {
        FakeAccount a = new FakeAccount().with(DIAMOND, 5).with(chip(100), 1);
        FakeAccount.Saved before = a.save();
        Account broken = new BrokenLedger(a);
        assertThrows(LedgerCodec.CorruptLedgerException.class, () -> Cashbox.exchange(broken,
                List.of(new ItemAmount(DIAMOND, 5)), RATES));
        assertThrows(LedgerCodec.CorruptLedgerException.class, () -> Cashbox.change(broken,
                Chips.Selection.of(Map.of(100, 1L)), 25));
        assertThrows(LedgerCodec.CorruptLedgerException.class, () -> Cashbox.buy(broken,
                new ShopEntry("minecraft:cake", 1, 50, 0), new Prize(1), new Store(), DAY));
        unchanged(before, a);
    }

    /** An account whose ledger cannot be read (every other call goes to the fake). */
    record BrokenLedger(FakeAccount a) implements Account {
        public UUID id() {
            return a.id();
        }

        public String name() {
            return a.name();
        }

        public Ledger ledger() {
            throw new LedgerCodec.CorruptLedgerException("broken", null);
        }

        public void ledger(Ledger ledger) {
            throw new AssertionError("never written");
        }

        public long countPlain(String item) {
            return a.countPlain(item);
        }

        public TakePlan planTake(List<ItemAmount> items) {
            return a.planTake(items);
        }

        public void applyTake(TakePlan plan) {
            throw new AssertionError("nothing may move");
        }

        public List<ItemAmount> give(List<ItemAmount> items) {
            throw new AssertionError("nothing may move");
        }

        public int spareSlots(TakePlan take, List<ItemAmount> items) {
            return a.spareSlots(take, items);
        }
    }

    // ---- change ----------------------------------------------------------------------------------------------------

    @Test
    void changeIsValueNeutralWithTheRestInSmallerChips() {
        FakeAccount a = new FakeAccount().with(chip(100), 1);
        Cashbox.Changed done = assertInstanceOf(Cashbox.Changed.class, Cashbox.change(a,
                Chips.Selection.of(Map.of(100, 1L)), 25));
        assertEquals(List.of(new ItemAmount(chip(25), 4)), done.given());
        assertEquals(0, a.count(chip(100)));
        assertEquals(4, a.count(chip(25)));
        // 3 x 100 + 2 x 5 into 25s: 12 x 25 + 2 x 5
        FakeAccount b = new FakeAccount().with(chip(100), 3).with(chip(5), 2);
        Cashbox.Changed mixed = assertInstanceOf(Cashbox.Changed.class, Cashbox.change(b,
                Chips.Selection.of(Map.of(100, 3L, 5, 2L)), 25));
        assertEquals(310, mixed.value());
        assertEquals(12, b.count(chip(25)));
        assertEquals(2, b.count(chip(5)));
        assertEquals(310, b.countPlain(Chips.VALUE));
        // combining: 40 x 25 into one 1000
        FakeAccount c = new FakeAccount().with(chip(25), 40);
        assertInstanceOf(Cashbox.Changed.class, Cashbox.change(c, Chips.Selection.of(Map.of(25, 40L)), 1000));
        assertEquals(1, c.count(chip(1000)));
        assertEquals(0, c.count(chip(25)));
        assertEquals(0, a.ledgerWrites + b.ledgerWrites + c.ledgerWrites);
    }

    @Test
    void refusedChangesChangeNothing() {
        FakeAccount a = new FakeAccount().with(chip(1), 3).with(chip(100), 2);
        FakeAccount.Saved before = a.save();
        assertEquals(Cashbox.Refusal.EMPTY, ((Cashbox.Refused) Cashbox.change(a, Chips.Selection.EMPTY, 5))
                .refusal());
        assertEquals(Cashbox.Refusal.NOTHING_TO_CHANGE, ((Cashbox.Refused) Cashbox.change(a,
                Chips.Selection.of(Map.of(1, 3L)), 5)).refusal(), "3 ones cannot become a five");
        assertEquals(Cashbox.Refusal.NOT_ENOUGH, ((Cashbox.Refused) Cashbox.change(a,
                Chips.Selection.of(Map.of(100, 3L)), 25)).refusal());
        unchanged(before, a);
        // no room: 35 full stacks of something else, the one free slot holds 64 chips, 1 x 100 into ones needs two
        FakeAccount full = new FakeAccount().with("minecraft:dirt", 35 * 64).with(chip(100), 1);
        FakeAccount.Saved fullBefore = full.save();
        assertEquals(Cashbox.Refusal.NO_ROOM, ((Cashbox.Refused) Cashbox.change(full,
                Chips.Selection.of(Map.of(100, 1L)), 1)).refusal());
        unchanged(fullBefore, full);
        assertEquals(0, a.ledgerWrites + full.ledgerWrites);
        // the slot the taken chip frees counts: 1 x 100 into 25s fits into it
        assertInstanceOf(Cashbox.Changed.class, Cashbox.change(full, Chips.Selection.of(Map.of(100, 1L)), 25));
        assertEquals(4, full.count(chip(25)));
    }

    @Test
    void randomChangesConserveTheChipValue() {
        Random r = new Random(9);
        for (int n = 0; n < 2000; n++) {
            FakeAccount a = new FakeAccount();
            long[] counts = new long[Chips.DENOMINATIONS.length];
            for (int i = 0; i < counts.length; i++) {
                long have = r.nextInt(4) == 0 ? r.nextInt(200) : 0;
                if (have > 0) {
                    a.with(chip(Chips.DENOMINATIONS[i]), have);
                    counts[i] = r.nextInt((int) have + 1);
                }
            }
            long before = a.countPlain(Chips.VALUE);
            int target = Chips.DENOMINATIONS[r.nextInt(Chips.DENOMINATIONS.length)];
            Cashbox.Outcome o = Cashbox.change(a, new Chips.Selection(counts), target);
            assertEquals(before, a.countPlain(Chips.VALUE), "value-neutral: " + o);
            if (o instanceof Cashbox.Changed done) {
                assertEquals(done.value(), Chips.valueOf(done.given()));
                assertEquals(done.value(), Chips.valueOf(done.taken()));
                assertEquals(0, done.owed());
            }
        }
    }

    // ---- buy -------------------------------------------------------------------------------------------------------

    @Test
    void aPurchasePaysExactlyOrWithChange() {
        ShopEntry chair = new ShopEntry("roulettecasino:casino_chair", 1, 64, 0);
        FakeAccount a = new FakeAccount().with(chip(25), 2).with(chip(5), 2).with(chip(1), 4);
        Prize prize = new Prize(1);
        Store store = new Store();
        Cashbox.Bought done = assertInstanceOf(Cashbox.Bought.class, Cashbox.buy(a, chair, prize, store, DAY));
        assertEquals(0, done.change());
        assertEquals(0, a.countPlain(Chips.VALUE));
        assertEquals(1, prize.given);
        assertEquals(0, store.writes, "no limit: no count kept");
        // one larger chip and the change back
        FakeAccount b = new FakeAccount().with(chip(100), 1);
        Cashbox.Bought withChange = assertInstanceOf(Cashbox.Bought.class, Cashbox.buy(b, chair, new Prize(1),
                new Store(), DAY));
        assertEquals(36, withChange.change());
        assertEquals(36, b.countPlain(Chips.VALUE));
        assertEquals(1, b.count(chip(25)));
        assertEquals(2, b.count(chip(5)));
        assertEquals(1, b.count(chip(1)));
        assertEquals(0, a.ledgerWrites + b.ledgerWrites);
    }

    @Test
    void theDailyLimitCountsPerPrizeAndStartsAfreshTheNextDay() {
        ShopEntry cake = new ShopEntry("minecraft:cake", 1, 10, 2);
        ShopEntry other = new ShopEntry("minecraft:cake", 2, 10, 2);
        FakeAccount a = new FakeAccount().with(chip(5), 64);
        Store store = new Store();
        assertInstanceOf(Cashbox.Bought.class, Cashbox.buy(a, cake, new Prize(1), store, DAY));
        Cashbox.Bought second = assertInstanceOf(Cashbox.Bought.class, Cashbox.buy(a, cake, new Prize(1), store,
                DAY));
        assertEquals(2, second.limits().count(cake.key()));
        FakeAccount.Saved before = a.save();
        String stored = store.value;
        Cashbox.Refused third = assertInstanceOf(Cashbox.Refused.class, Cashbox.buy(a, cake, new Prize(1), store,
                DAY));
        assertEquals(Cashbox.Refusal.LIMIT, third.refusal());
        unchanged(before, a);
        assertEquals(stored, store.value);
        // another prize (other count) has its own count; a new price keeps the count (same key)
        assertInstanceOf(Cashbox.Bought.class, Cashbox.buy(a, other, new Prize(1), store, DAY));
        assertEquals(Cashbox.Refusal.LIMIT, ((Cashbox.Refused) Cashbox.buy(a, cake.withPrice(7), new Prize(1), store,
                DAY)).refusal());
        // the next Berlin day
        assertInstanceOf(Cashbox.Bought.class, Cashbox.buy(a, cake, new Prize(1), store, DAY.plusDays(1)));
        assertEquals(1, DailyLimits.read(store.value, DAY.plusDays(1)).count(cake.key()));
        assertEquals(0, DailyLimits.read(store.value, DAY.plusDays(2)).count(cake.key()));
    }

    @Test
    void refusedPurchasesChangeNothing() {
        ShopEntry sword = new ShopEntry("minecraft:diamond_sword[enchantments={sharpness:5}]", 3, 100, 0);
        // not enough chips
        FakeAccount poor = new FakeAccount().with(chip(25), 3);
        FakeAccount.Saved before = poor.save();
        Prize prize = new Prize(3);
        Store store = new Store();
        assertEquals(Cashbox.Refusal.NOT_ENOUGH, ((Cashbox.Refused) Cashbox.buy(poor, sword, prize, store, DAY))
                .refusal());
        unchanged(before, poor);
        // room for the prize but not for the change: the change of a 500 is 4 x 100
        FakeAccount full = new FakeAccount().with("minecraft:dirt", 33 * 64).with(chip(500), 1).with(chip(1), 64);
        FakeAccount.Saved fullBefore = full.save();
        assertEquals(1, full.spareSlots(null, List.of()));
        assertEquals(Cashbox.Refusal.NO_ROOM, ((Cashbox.Refused) Cashbox.buy(full, sword, new Prize(2), store, DAY))
                .refusal(), "the 500 slot frees, the change needs one, the swords two of the two left");
        unchanged(fullBefore, full);
        assertEquals(0, prize.given);
        assertNull(store.value);
        assertEquals(0, poor.ledgerWrites + full.ledgerWrites);
        // with one slot less needed it fits: the 500's slot holds the change, the free slot the prize
        assertInstanceOf(Cashbox.Bought.class, Cashbox.buy(full, sword, new Prize(1), store, DAY));
        assertEquals(4, full.count(chip(100)));
    }

    // ---- crashes ---------------------------------------------------------------------------------------------------

    @Test
    void aCrashRollsAWholeTransactionBackOrKeepsItWhole() {
        FakeAccount a = new FakeAccount().with(DIAMOND, 10).with(chip(100), 2);
        Store store = new Store();
        FakeAccount.Saved saved = a.save();
        String storedBefore = store.value;
        Cashbox.exchange(a, List.of(new ItemAmount(DIAMOND, 10)), RATES);
        Cashbox.change(a, Chips.Selection.of(Map.of(100, 2L)), 25);
        Cashbox.buy(a, new ShopEntry("minecraft:cake", 1, 30, 1), new Prize(1), store, DAY);
        FakeAccount.Saved after = a.save();
        // crash before the next save: the file from before; everything as it was
        a.restore(saved);
        store.value = storedBefore;
        assertEquals(10, a.count(DIAMOND));
        assertEquals(200, a.countPlain(Chips.VALUE));
        assertNull(store.value);
        // the save after: everything done; value: 320 for the diamonds + 200 - 30
        a.restore(after);
        assertEquals(0, a.count(DIAMOND));
        assertEquals(490, a.countPlain(Chips.VALUE));
    }
}
