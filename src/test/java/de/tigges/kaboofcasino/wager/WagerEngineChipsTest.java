package de.tigges.kaboofcasino.wager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Chip stakes and payouts through the engine (PLAN-2 C2): the selected chips leave, the record holds a chip value,
 * payouts come in the largest denominations, what does not fit is owed as a chip value, a double down / call is paid
 * by value with change; old ore records still settle in ores.
 */
class WagerEngineChipsTest {

    static final String DIAMOND = "minecraft:diamond";
    private long now = 1_000_000;

    private final WagerEngine.Limits limits = new WagerEngine.Limits() {
        @Override
        public int maxStake() {
            return 2000;
        }

        @Override
        public int maxOpenRounds() {
            return 8;
        }

        @Override
        public Set<String> betItems() {
            return Set.of(Chips.VALUE);
        }
    };

    private final List<String> events = new ArrayList<>();
    private final RoundSink sink = new RoundSink() {
        @Override
        public void started(UUID player, String name, RoundRecord round) {
            events.add("start " + round.stake());
        }

        @Override
        public void settled(UUID player, RoundRecord round, SettleReason reason, long at) {
            events.add("settle " + reason + " " + round.payout());
        }
    };
    private final WagerEngine engine = new WagerEngine(limits, sink, () -> now);

    private static String c(int d) {
        return Chips.key(d);
    }

    private static ItemAmount value(long v) {
        return new ItemAmount(Chips.VALUE, v);
    }

    private WagerEngine.Request request(Chips.Selection sel, long payout, RoundState state) {
        return new WagerEngine.Request(UUID.randomUUID(), "fortune_wheel", sel.asValue(),
                payout == 0 ? List.of() : List.of(value(payout)), now + 5_000, "test", state).withTake(sel.amounts());
    }

    @Test
    void theSelectedChipsLeaveAndTheRecordHoldsTheirValue() {
        FakeAccount a = new FakeAccount().with(c(25), 4).with(c(5), 3).with(c(100), 1);
        Chips.Selection sel = Chips.Selection.EMPTY.plus(25, 2).plus(5, 1);
        WagerEngine.Committed done = assertInstanceOf(WagerEngine.Committed.class,
                engine.commit(a, request(sel, 110, RoundState.DECIDED)));
        assertEquals(2, a.count(c(25)), "exactly the selected chips");
        assertEquals(2, a.count(c(5)));
        assertEquals(1, a.count(c(100)), "the 100 stays although it would also pay 55");
        assertEquals(value(55), done.round().stake());
        assertEquals(3, LedgerCodec.version(a.ledger()));
        assertEquals(List.of("start 55xkaboofcasino:chips"), events);
        // settled: 110 in the largest chips (1 x 100, 2 x 5)
        engine.settle(a, done.round().id(), SettleReason.NORMAL);
        assertEquals(2, a.count(c(100)));
        assertEquals(4, a.count(c(5)));
        assertTrue(a.ledger().isEmpty());
    }

    @Test
    void theChecksUseChipValues() {
        FakeAccount a = new FakeAccount().with(c(1000), 3).with(c(25), 1);
        // over the maximum in value (3 000 > 2 000)
        Chips.Selection big = Chips.Selection.EMPTY.plus(1000, 3);
        assertEquals(WagerEngine.Refusal.MAX_STAKE, assertInstanceOf(WagerEngine.Refused.class,
                engine.commit(a, request(big, 0, RoundState.DECIDED))).refusal());
        // chips that do not add up to the stake are refused (a forged request), nothing taken
        WagerEngine.Request forged = new WagerEngine.Request(UUID.randomUUID(), "plinko", value(50), List.of(),
                now, "x").withTake(List.of(new ItemAmount(c(25), 1)));
        assertEquals(WagerEngine.Refusal.BAD_COUNT,
                assertInstanceOf(WagerEngine.Refused.class, engine.commit(a, forged)).refusal());
        WagerEngine.Request noChips = new WagerEngine.Request(UUID.randomUUID(), "plinko", value(25), List.of(),
                now, "x");
        assertEquals(WagerEngine.Refusal.BAD_COUNT,
                assertInstanceOf(WagerEngine.Refused.class, engine.commit(a, noChips)).refusal(), "no chips named");
        // ores are no stakes any more
        WagerEngine.Request ore = new WagerEngine.Request(UUID.randomUUID(), "plinko", new ItemAmount(DIAMOND, 1),
                List.of(), now, "x");
        assertEquals(WagerEngine.Refusal.NOT_ALLOWED,
                assertInstanceOf(WagerEngine.Refused.class, engine.commit(a.with(DIAMOND, 1), ore)).refusal());
        // two slots naming the same chips need them twice
        Chips.Selection one = Chips.Selection.EMPTY.plus(25, 1);
        WagerEngine.Commit two = engine.commitAll(a, List.of(request(one, 0, RoundState.DECIDED),
                request(one, 0, RoundState.DECIDED)));
        WagerEngine.Refused r = assertInstanceOf(WagerEngine.Refused.class, two);
        assertEquals(WagerEngine.Refusal.NOT_ENOUGH, r.refusal());
        assertEquals(c(25), r.item());
        assertEquals(3, a.count(c(1000)));
        assertEquals(1, a.count(c(25)));
        assertEquals(0, a.ledgerWrites);
    }

    @Test
    void whatDoesNotFitIsOwedAsAChipValue() {
        FakeAccount a = new FakeAccount().with(c(1000), 1);
        a.capacityStacks = 2;
        RoundRecord r = assertInstanceOf(WagerEngine.Committed.class, engine.commit(a,
                request(Chips.Selection.EMPTY.plus(1000, 1), 2_834, RoundState.DECIDED))).round();
        // 2 834 = 2 x 1000, 500, 3 x 100, 25, 5, 4 x 1: six stacks, two slots
        engine.settle(a, r.id(), SettleReason.NORMAL);
        assertEquals(2, a.count(c(1000)));
        assertEquals(1, a.count(c(500)));
        assertEquals(List.of(value(334)), a.ledger().owed());
        assertEquals(3, LedgerCodec.version(a.ledger()));
        a.capacityStacks = 36;
        assertEquals(List.of(value(334)), engine.deliverOwed(a));
        assertEquals(3, a.count(c(100)));
        assertEquals(4, a.count(c(1)));
        assertTrue(a.ledger().isEmpty());
    }

    @Test
    void aDoubleDownIsPaidByValueWithChange() {
        FakeAccount a = new FakeAccount().with(c(25), 1).with(c(100), 1);
        RoundRecord r = assertInstanceOf(WagerEngine.Committed.class, engine.commit(a,
                request(Chips.Selection.EMPTY.plus(25, 1), 25, RoundState.PLAYING))).round();
        int writes = a.ledgerWrites;
        RoundRecord doubled = new RoundRecord(r.id(), r.machine(), r.game(), RoundState.PLAYING, value(50),
                List.of(value(100)), r.created(), r.ends(), "double");
        assertEquals(WagerEngine.Revision.DONE, engine.revise(a, List.of(doubled), List.of(value(25))));
        assertEquals(0, a.count(c(100)), "the 100 paid the 25");
        assertEquals(3, a.count(c(25)), "75 change as three 25s");
        assertEquals(writes + 1, a.ledgerWrites, "one ledger write");
        assertEquals(doubled, a.ledger().round(r.id()).orElseThrow());
        // not enough chips: nothing changes
        assertEquals(WagerEngine.Revision.NOT_ENOUGH, engine.revise(a, List.of(doubled), List.of(value(100))));
        assertEquals(3, a.count(c(25)));
    }

    @Test
    void changeThatDoesNotFitIsOwed() {
        FakeAccount a = new FakeAccount().with(c(1000), 1).with(c(1), 64);
        RoundRecord r = assertInstanceOf(WagerEngine.Committed.class, engine.commit(a,
                request(Chips.Selection.EMPTY.plus(1, 1), 0, RoundState.PLAYING))).round();
        a.capacityStacks = 2; // the 1000 and the 63 ones: nothing else fits
        RoundRecord called = new RoundRecord(r.id(), r.machine(), r.game(), RoundState.PLAYING, value(3),
                List.of(), r.created(), r.ends(), "call");
        // a call of 2 is paid with the ones (exact): no change
        assertEquals(WagerEngine.Revision.DONE, engine.revise(a, List.of(called), List.of(value(2))));
        assertEquals(61, a.count(c(1)));
        // 999 more: the ones (61) are not enough, so the 1000 pays, 1 change fits onto the ones' stack
        assertEquals(WagerEngine.Revision.DONE, engine.revise(a, List.of(called), List.of(value(999))));
        assertEquals(0, a.count(c(1000)));
        assertEquals(62, a.count(c(1)));
        assertTrue(a.ledger().owed().isEmpty());
        // paying 7 from 62 ones is exact again; paying 500 with change that cannot fit is owed
        FakeAccount b = new FakeAccount().with(c(1000), 1).with(c(5), 64).with(c(1), 63);
        RoundRecord rb = assertInstanceOf(WagerEngine.Committed.class, engine.commit(b,
                request(Chips.Selection.EMPTY.plus(1, 1), 0, RoundState.PLAYING))).round();
        b.capacityStacks = 3;
        RoundRecord calledB = new RoundRecord(rb.id(), rb.machine(), rb.game(), RoundState.PLAYING, value(3),
                List.of(), rb.created(), rb.ends(), "call");
        assertEquals(WagerEngine.Revision.DONE, engine.revise(b, List.of(calledB), List.of(value(575))));
        // 575 > 320 + 62 of the small chips: the 1000 pays, 425 change = 4 x 100 (into the freed slot) + 25 (no
        // room): the 25 is owed in the same ledger write
        assertEquals(0, b.count(c(1000)));
        assertEquals(4, b.count(c(100)));
        assertEquals(List.of(value(25)), b.ledger().owed());
    }

    @Test
    void oreRecordsOf080SettleInOresNextToChips() {
        FakeAccount a = new FakeAccount().with(c(5), 2);
        UUID machine = UUID.randomUUID();
        RoundRecord ore = new RoundRecord(UUID.randomUUID(), machine, "blackjack_table", RoundState.PLAYING,
                new ItemAmount(DIAMOND, 4), List.of(new ItemAmount(DIAMOND, 10)), now - 9000, now - 1000, "crash");
        a.ledger(new Ledger(List.of(ore), List.of()));
        assertEquals(2, LedgerCodec.version(a.ledger()), "an 0.8.0 ledger stays at its version");
        RoundRecord chip = assertInstanceOf(WagerEngine.Committed.class, engine.commitAll(a,
                List.of(request(Chips.Selection.EMPTY.plus(5, 2), 25, RoundState.DECIDED)))).round();
        assertEquals(3, LedgerCodec.version(a.ledger()));
        engine.settleAll(a, SettleReason.JOIN_RECOVERY);
        assertEquals(10, a.count(DIAMOND), "the ore record pays ores");
        assertEquals(1, a.count(c(25)), "the chip record pays chips");
        assertTrue(a.ledger().isEmpty());
        assertTrue(events.contains("settle JOIN_RECOVERY [10xminecraft:diamond]"));
        assertTrue(events.contains("settle JOIN_RECOVERY [25xkaboofcasino:chips]"), events.toString());
        assertEquals(chip.stake(), value(10));
    }

    @Test
    void deathPutsChipsIntoTheGraveAndKeepsTheRestAsAValue() {
        FakeAccount a = new FakeAccount().with(c(100), 1);
        RoundRecord r = assertInstanceOf(WagerEngine.Committed.class, engine.commit(a,
                request(Chips.Selection.EMPTY.plus(100, 1), 1_250, RoundState.DECIDED))).round();
        List<List<ItemAmount>> offered = new ArrayList<>();
        // a grave with room for one stack: the 1000s go in, the rest stays owed as a value
        List<ItemAmount> dropped = engine.drainForDeath(a, items -> {
            offered.add(items);
            List<ItemAmount> expanded = Chips.expand(items);
            return Chips.normalize(expanded.subList(1, expanded.size()));
        });
        assertEquals(List.of(List.of(value(1_250))), offered);
        assertEquals(List.of(value(1_000)), dropped);
        assertEquals(List.of(value(250)), a.ledger().owed());
        assertTrue(a.ledger().round(r.id()).isEmpty());
    }
}
