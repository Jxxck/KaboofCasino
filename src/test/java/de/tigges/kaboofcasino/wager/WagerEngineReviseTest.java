package de.tigges.kaboofcasino.wager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** PLAYING records and their revision (blackjack: a new stand payout after a hit, the double down's second stake). */
class WagerEngineReviseTest {

    static final String DIAMOND = "minecraft:diamond";
    static final String IRON = "minecraft:iron_block";
    private final long now = 1_000_000;

    private final WagerEngine engine = new WagerEngine(new WagerEngine.Limits() {
        @Override
        public int maxStake() {
            return 64;
        }

        @Override
        public int maxOpenRounds() {
            return 8;
        }

        @Override
        public Set<String> betItems() {
            return Set.of(DIAMOND, IRON);
        }
    }, RoundSink.NONE, () -> now);

    private List<RoundRecord> deal(FakeAccount a, long diamonds, long iron) {
        WagerEngine.Commit c = engine.commitAll(a, List.of(
                new WagerEngine.Request(UUID.randomUUID(), "blackjack", new ItemAmount(DIAMOND, diamonds),
                        List.of(new ItemAmount(DIAMOND, diamonds * 2)), now + 60_000, "slot=0", RoundState.PLAYING),
                new WagerEngine.Request(UUID.randomUUID(), "blackjack", new ItemAmount(IRON, iron),
                        List.of(new ItemAmount(IRON, iron * 2)), now + 60_000, "slot=1", RoundState.PLAYING)));
        return ((WagerEngine.Committed) c).rounds();
    }

    private static RoundRecord with(RoundRecord r, long stake, List<ItemAmount> payout) {
        return new RoundRecord(r.id(), r.machine(), r.game(), RoundState.PLAYING,
                new ItemAmount(r.stake().item(), stake), payout, r.created(), r.ends(), r.detail());
    }

    @Test
    void playingRecordsAreWrittenAsPlaying() {
        FakeAccount a = new FakeAccount().with(DIAMOND, 10).with(IRON, 10);
        List<RoundRecord> rounds = deal(a, 3, 5);
        assertEquals(RoundState.PLAYING, a.ledger().round(rounds.get(0).id()).orElseThrow().state());
        assertEquals(7, a.count(DIAMOND));
        assertEquals(5, a.count(IRON));
    }

    @Test
    void aHitRewritesThePayoutInOneWrite() {
        FakeAccount a = new FakeAccount().with(DIAMOND, 10).with(IRON, 10);
        List<RoundRecord> rounds = deal(a, 3, 5);
        int writes = a.ledgerWrites;
        WagerEngine.Revision r = engine.revise(a, List.of(with(rounds.get(0), 3, List.of()),
                with(rounds.get(1), 5, List.of())), List.of());
        assertEquals(WagerEngine.Revision.DONE, r);
        assertEquals(writes + 1, a.ledgerWrites);
        assertEquals(List.of(), a.ledger().round(rounds.get(0).id()).orElseThrow().payout());
        assertEquals(7, a.count(DIAMOND));
    }

    @Test
    void aDoubleTakesTheSecondStakeAndWritesOnce() {
        FakeAccount a = new FakeAccount().with(DIAMOND, 10).with(IRON, 10);
        List<RoundRecord> rounds = deal(a, 3, 5);
        int writes = a.ledgerWrites;
        WagerEngine.Revision r = engine.revise(a, List.of(with(rounds.get(0), 6, List.of(new ItemAmount(DIAMOND, 12))),
                with(rounds.get(1), 10, List.of(new ItemAmount(IRON, 20)))),
                List.of(new ItemAmount(DIAMOND, 3), new ItemAmount(IRON, 5)));
        assertEquals(WagerEngine.Revision.DONE, r);
        assertEquals(writes + 1, a.ledgerWrites);
        assertEquals(4, a.count(DIAMOND));
        assertEquals(0, a.count(IRON));
        assertEquals(6, a.ledger().round(rounds.get(0).id()).orElseThrow().stake().count());
        // settling now (quit, death, crash) pays the doubled stand payout
        engine.settleAll(a, SettleReason.QUIT);
        assertEquals(16, a.count(DIAMOND));
        assertEquals(20, a.count(IRON));
        assertEquals(Ledger.EMPTY, a.ledger());
    }

    @Test
    void aDoubleWithoutTheItemsChangesNothing() {
        FakeAccount a = new FakeAccount().with(DIAMOND, 10).with(IRON, 7);
        List<RoundRecord> rounds = deal(a, 3, 5);
        FakeAccount.Saved before = a.save();
        int writes = a.ledgerWrites;
        WagerEngine.Revision r = engine.revise(a, List.of(with(rounds.get(0), 6, List.of()),
                with(rounds.get(1), 10, List.of())), List.of(new ItemAmount(DIAMOND, 3), new ItemAmount(IRON, 5)));
        assertEquals(WagerEngine.Revision.NOT_ENOUGH, r);
        assertEquals(writes, a.ledgerWrites);
        assertEquals(before, a.save());
    }

    @Test
    void aSettledHandIsNotRevived() {
        FakeAccount a = new FakeAccount().with(DIAMOND, 10).with(IRON, 10);
        List<RoundRecord> rounds = deal(a, 3, 5);
        engine.settleAll(a, SettleReason.QUIT);
        FakeAccount.Saved before = a.save();
        WagerEngine.Revision r = engine.revise(a, List.of(with(rounds.get(0), 6, List.of())),
                List.of(new ItemAmount(DIAMOND, 3)));
        assertEquals(WagerEngine.Revision.GONE, r);
        assertEquals(before, a.save());
    }

    @Test
    void aFailingTakePlanChangesNothing() {
        FakeAccount a = new FakeAccount().with(DIAMOND, 10).with(IRON, 10);
        List<RoundRecord> rounds = deal(a, 3, 5);
        FakeAccount.Saved before = a.save();
        a.failPlanOn = IRON;
        assertThrows(RuntimeException.class, () -> engine.revise(a, List.of(with(rounds.get(1), 10, List.of())),
                List.of(new ItemAmount(IRON, 5))));
        assertEquals(before, a.save());
    }

    @Test
    void theLedgerIsVersionTwoOnlyWhileAHandIsOpen() {
        FakeAccount a = new FakeAccount().with(DIAMOND, 10).with(IRON, 10);
        assertEquals(1, LedgerCodec.version(a.ledger()));
        deal(a, 3, 5);
        assertEquals(2, LedgerCodec.version(a.ledger()));
        assertEquals(LedgerCodec.VERSION, LedgerCodec.version(a.ledger()));
        engine.settleAll(a, SettleReason.QUIT);
        assertEquals(1, LedgerCodec.version(new Ledger(List.of(), List.of(new ItemAmount(DIAMOND, 1)))));
    }
}
