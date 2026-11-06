package de.tigges.kaboofcasino.wager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The bet engine's failure cases (server.md 1.3): quit, crash, death, full inventory, owed delivery. */
class WagerEngineTest {

    static final String DIAMOND = "minecraft:diamond";
    static final String IRON_BLOCK = "minecraft:iron_block";
    private long now = 1_000_000;
    private final List<String> events = new ArrayList<>();

    private final WagerEngine.Limits limits = new WagerEngine.Limits() {
        @Override
        public int maxStake() {
            return 64;
        }

        @Override
        public int maxOpenRounds() {
            return 4;
        }

        @Override
        public Set<String> betItems() {
            return Set.of(DIAMOND, IRON_BLOCK);
        }
    };

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

    private WagerEngine.Request request(String item, long stake, long payout) {
        return new WagerEngine.Request(UUID.randomUUID(), "slot_machine", new ItemAmount(item, stake),
                payout == 0 ? List.of() : List.of(new ItemAmount(item, payout)), now + 5_000, "test");
    }

    private RoundRecord commit(FakeAccount a, String item, long stake, long payout) {
        WagerEngine.Commit c = engine.commit(a, request(item, stake, payout));
        return assertInstanceOf(WagerEngine.Committed.class, c).round();
    }

    @Test
    void commitTakesTheStakeAndWritesTheRecordInOneStep() {
        FakeAccount a = new FakeAccount().with(DIAMOND, 10);
        RoundRecord r = commit(a, DIAMOND, 4, 8);
        assertEquals(6, a.count(DIAMOND));
        assertEquals(List.of(r), a.ledger().rounds());
        assertEquals(1, a.ledgerWrites);
        assertEquals(List.of("start 4xminecraft:diamond"), events);
    }

    @Test
    void settlePaysOnceAndIsIdempotent() {
        FakeAccount a = new FakeAccount().with(DIAMOND, 10);
        RoundRecord r = commit(a, DIAMOND, 4, 8);
        assertEquals(r, engine.settle(a, r.id(), SettleReason.NORMAL));
        assertEquals(14, a.count(DIAMOND));
        assertTrue(a.ledger().isEmpty());
        assertNull(engine.settle(a, r.id(), SettleReason.NORMAL));
        assertEquals(14, a.count(DIAMOND));
    }

    @Test
    void aLossPaysNothing() {
        FakeAccount a = new FakeAccount().with(DIAMOND, 10);
        RoundRecord r = commit(a, DIAMOND, 10, 0);
        engine.settle(a, r.id(), SettleReason.NORMAL);
        assertEquals(0, a.count(DIAMOND));
        assertTrue(a.ledger().isEmpty());
    }

    @Test
    void refusalsChangeNothing() {
        FakeAccount a = new FakeAccount().with(DIAMOND, 3).with("minecraft:dirt", 5);
        assertEquals(new WagerEngine.Refused(WagerEngine.Refusal.NOT_ENOUGH, DIAMOND),
                engine.commit(a, request(DIAMOND, 4, 0)));
        assertEquals(new WagerEngine.Refused(WagerEngine.Refusal.NOT_ALLOWED, "minecraft:dirt"),
                engine.commit(a, request("minecraft:dirt", 1, 0)));
        assertEquals(new WagerEngine.Refused(WagerEngine.Refusal.MAX_STAKE), engine.commit(a, request(DIAMOND, 65, 0)));
        assertEquals(3, a.count(DIAMOND));
        assertTrue(a.ledger().isEmpty());
        assertEquals(0, a.ledgerWrites);
    }

    @Test
    void severalStakesCommitTogetherOrNotAtAll() {
        FakeAccount a = new FakeAccount().with(DIAMOND, 10).with(IRON_BLOCK, 5);
        List<WagerEngine.Request> three = List.of(request(DIAMOND, 4, 8), request(IRON_BLOCK, 5, 0),
                request(DIAMOND, 6, 3));
        WagerEngine.Committed c = assertInstanceOf(WagerEngine.Committed.class, engine.commitAll(a, three));
        assertEquals(3, c.rounds().size());
        assertEquals(0, a.count(DIAMOND));
        assertEquals(0, a.count(IRON_BLOCK));
        assertEquals(c.rounds(), a.ledger().rounds());
        assertEquals(1, a.ledgerWrites, "one ledger write for all stakes");

        // stakes of the same item add up: 4 + 4 diamonds with 6 owned is refused as a whole
        FakeAccount b = new FakeAccount().with(DIAMOND, 6).with(IRON_BLOCK, 1);
        assertEquals(new WagerEngine.Refused(WagerEngine.Refusal.NOT_ENOUGH, DIAMOND),
                engine.commitAll(b, List.of(request(IRON_BLOCK, 1, 0), request(DIAMOND, 4, 0),
                        request(DIAMOND, 4, 0))));
        assertEquals(6, b.count(DIAMOND));
        assertEquals(1, b.count(IRON_BLOCK));
        assertEquals(0, b.ledgerWrites);
    }

    @Test
    void theStakeCapCoversTheWholeRoundAndEveryStakeNeedsARecordSlot() {
        FakeAccount a = new FakeAccount().with(DIAMOND, 64).with(IRON_BLOCK, 64);
        assertEquals(new WagerEngine.Refused(WagerEngine.Refusal.MAX_STAKE),
                engine.commitAll(a, List.of(request(DIAMOND, 40, 0), request(IRON_BLOCK, 25, 0))));
        assertInstanceOf(WagerEngine.Committed.class,
                engine.commitAll(a, List.of(request(DIAMOND, 40, 0), request(IRON_BLOCK, 24, 0))));
        // 2 of 4 record slots used: three more stakes do not fit, two do
        assertEquals(new WagerEngine.Refused(WagerEngine.Refusal.ROUNDS_FULL), engine.commitAll(a,
                List.of(request(DIAMOND, 1, 0), request(DIAMOND, 1, 0), request(DIAMOND, 1, 0))));
        assertInstanceOf(WagerEngine.Committed.class,
                engine.commitAll(a, List.of(request(DIAMOND, 1, 0), request(DIAMOND, 1, 0))));
        assertEquals(new WagerEngine.Refused(WagerEngine.Refusal.BAD_COUNT), engine.commitAll(a, List.of()));
    }

    @Test
    void openRoundsAreCapped() {
        FakeAccount a = new FakeAccount().with(DIAMOND, 64);
        for (int i = 0; i < 4; i++) {
            commit(a, DIAMOND, 1, 0);
        }
        assertEquals(new WagerEngine.Refused(WagerEngine.Refusal.ROUNDS_FULL), engine.commit(a, request(DIAMOND, 1, 0)));
    }

    @Test
    void quitSettlesEveryOpenRoundBeforeTheSave() {
        FakeAccount a = new FakeAccount().with(DIAMOND, 20);
        commit(a, DIAMOND, 5, 10);
        commit(a, DIAMOND, 5, 0);
        List<RoundRecord> settled = engine.settleAll(a, SettleReason.QUIT);
        assertEquals(2, settled.size());
        assertEquals(20, a.count(DIAMOND));
        FakeAccount.Saved quitSave = a.save();
        assertTrue(quitSave.ledger().isEmpty());
        // rejoin: nothing to recover, nothing paid twice
        a.restore(quitSave);
        assertTrue(engine.settleAll(a, SettleReason.JOIN_RECOVERY).isEmpty());
        assertEquals(20, a.count(DIAMOND));
    }

    @Test
    void crashBeforeTheNextSaveRollsBackStakeAndRecordTogether() {
        FakeAccount a = new FakeAccount().with(DIAMOND, 10);
        FakeAccount.Saved before = a.save();
        commit(a, DIAMOND, 10, 20);
        // kill -9: the file still holds the state before the commit
        a.restore(before);
        assertEquals(10, a.count(DIAMOND));
        assertTrue(engine.settleAll(a, SettleReason.JOIN_RECOVERY).isEmpty());
        assertEquals(10, a.count(DIAMOND));
    }

    @Test
    void crashAfterASaveWithTheRecordPaysItOnceAtTheNextJoin() {
        FakeAccount a = new FakeAccount().with(DIAMOND, 10);
        RoundRecord r = commit(a, DIAMOND, 10, 20);
        FakeAccount.Saved autosave = a.save();          // autosave/KaboofStats save with the record
        engine.settle(a, r.id(), SettleReason.NORMAL);  // paid in memory ...
        a.restore(autosave);                            // ... crash before the next save
        assertEquals(0, a.count(DIAMOND));
        List<RoundRecord> recovered = engine.settleAll(a, SettleReason.JOIN_RECOVERY);
        assertEquals(List.of(r), recovered);
        assertEquals(20, a.count(DIAMOND));
        assertTrue(engine.settleAll(a, SettleReason.JOIN_RECOVERY).isEmpty());
        assertEquals(20, a.count(DIAMOND));
    }

    @Test
    void deathDrainsRoundsAndOwedIntoTheDrops() {
        FakeAccount a = new FakeAccount().with(DIAMOND, 10);
        a.capacityStacks = 1;
        RoundRecord first = commit(a, DIAMOND, 10, 200);
        engine.settle(a, first.id(), SettleReason.NORMAL); // 64 fit, 136 owed
        assertEquals(64, a.count(DIAMOND));
        assertEquals(List.of(new ItemAmount(DIAMOND, 136)), a.ledger().owed());
        // a round cannot start while something is owed, so the open round below is from before (crash recovery path)
        a.ledger(a.ledger().withRound(new RoundRecord(UUID.randomUUID(), UUID.randomUUID(), "slot_machine",
                RoundState.DECIDED, new ItemAmount(IRON_BLOCK, 2), List.of(new ItemAmount(IRON_BLOCK, 4)), now, now,
                "x")));
        List<ItemAmount> into = new ArrayList<>();
        List<ItemAmount> drops = engine.drainForDeath(a, items -> {
            into.addAll(items);
            return List.of();
        });
        assertEquals(List.of(new ItemAmount(DIAMOND, 136), new ItemAmount(IRON_BLOCK, 4)), drops);
        assertEquals(drops, into);
        assertTrue(a.ledger().isEmpty());
    }

    @Test
    void whatTheGraveCannotHoldStaysOwed() {
        FakeAccount a = new FakeAccount().with(DIAMOND, 64);
        commit(a, DIAMOND, 64, 6400);
        // grave room for 10 stacks: 640 into the drops, 5760 stay owed for after a revive
        List<ItemAmount> dropped = engine.drainForDeath(a, items -> {
            List<ItemAmount> rest = new ArrayList<>();
            for (ItemAmount i : items) {
                if (i.count() > 640) {
                    rest.add(new ItemAmount(i.item(), i.count() - 640));
                }
            }
            return rest;
        });
        assertEquals(List.of(new ItemAmount(DIAMOND, 640)), dropped);
        assertTrue(a.ledger().rounds().isEmpty());
        assertEquals(List.of(new ItemAmount(DIAMOND, 5760)), a.ledger().owed());
        assertTrue(events.contains("settle DEATH [6400xminecraft:diamond]"));
    }

    @Test
    void aFailingDropTargetChangesNothing() {
        FakeAccount a = new FakeAccount().with(DIAMOND, 10);
        RoundRecord r = commit(a, DIAMOND, 10, 20);
        Ledger before = a.ledger();
        try {
            engine.drainForDeath(a, items -> {
                throw new IllegalStateException("unknown material");
            });
        } catch (IllegalStateException expected) {
            // the ledger is written last
        }
        assertEquals(before, a.ledger());
        assertEquals(List.of(r), a.ledger().rounds());
    }

    @Test
    void owedDeliveryWritesPerItemSoAFailureCannotDupe() {
        FakeAccount a = new FakeAccount();
        a.failOn = IRON_BLOCK;
        a.ledger(Ledger.EMPTY.plusOwed(List.of(new ItemAmount(DIAMOND, 70), new ItemAmount(IRON_BLOCK, 5),
                new ItemAmount("minecraft:emerald", 3))));
        try {
            engine.deliverOwed(a);
        } catch (IllegalStateException expected) {
            // the second item fails before it changes the inventory
        }
        assertEquals(70, a.count(DIAMOND));
        assertEquals(List.of(new ItemAmount(IRON_BLOCK, 5), new ItemAmount("minecraft:emerald", 3)),
                a.ledger().owed(), "the delivered item left the ledger at once, the rest is still owed");
        a.failOn = null;
        engine.deliverOwed(a);
        assertTrue(a.ledger().isEmpty());
        assertEquals(5, a.count(IRON_BLOCK));
    }

    @Test
    void aCancelledDeathPutsTheItemsBack() {
        FakeAccount a = new FakeAccount().with(DIAMOND, 10);
        commit(a, DIAMOND, 10, 30);
        List<ItemAmount> drops = engine.drainForDeath(a, items -> List.of());
        engine.restoreAfterCancelledDeath(a, drops);
        assertEquals(30, a.count(DIAMOND));
        assertTrue(a.ledger().isEmpty());
    }

    @Test
    void fullInventoryGoesToOwedNeverToTheGroundAndBlocksNewRounds() {
        FakeAccount a = new FakeAccount().with(DIAMOND, 64).with("minecraft:dirt", 35 * 64);
        RoundRecord r = commit(a, DIAMOND, 64, 6400); // jackpot x100 with a full inventory
        engine.settle(a, r.id(), SettleReason.NORMAL);
        assertEquals(64, a.count(DIAMOND));
        assertEquals(List.of(new ItemAmount(DIAMOND, 6336)), a.ledger().owed());
        assertEquals(new WagerEngine.Refused(WagerEngine.Refusal.OWED_PENDING), engine.commit(a, request(DIAMOND, 1, 0)));
        // the player makes room: delivery gives what fits, the rest stays owed
        a.inventory.remove("minecraft:dirt");
        List<ItemAmount> delivered = engine.deliverOwed(a);
        assertEquals(List.of(new ItemAmount(DIAMOND, 35 * 64)), delivered);
        assertEquals(36 * 64, a.count(DIAMOND));
        assertEquals(List.of(new ItemAmount(DIAMOND, 6336 - 35 * 64)), a.ledger().owed());
        // conservation: everything paid is either in the inventory or owed
        assertEquals(6400, a.count(DIAMOND) + ItemAmount.total(a.ledger().owed(), DIAMOND));
    }

    @Test
    void owedDeliveryNeedsNoRound() {
        FakeAccount a = new FakeAccount();
        a.ledger(Ledger.EMPTY.plusOwed(List.of(new ItemAmount(DIAMOND, 70))));
        assertEquals(List.of(new ItemAmount(DIAMOND, 70)), engine.deliverOwed(a));
        assertTrue(a.ledger().isEmpty());
        assertEquals(List.of(), engine.deliverOwed(a));
    }

    @Test
    void theWatchdogSettlesOnlyLateRoundsNoMachineOwns() {
        FakeAccount a = new FakeAccount().with(DIAMOND, 10);
        RoundRecord owned = commit(a, DIAMOND, 2, 4);
        RoundRecord orphan = commit(a, DIAMOND, 2, 6);
        now += 5_000 + 9_999;
        assertTrue(engine.settleStale(a, 10_000, id -> false).isEmpty());
        now += 1;
        List<RoundRecord> settled = engine.settleStale(a, 10_000, id -> id.equals(owned.id()));
        assertEquals(List.of(orphan), settled);
        assertEquals(List.of(owned), a.ledger().rounds());
        assertTrue(events.contains("settle TIMEOUT [6xminecraft:diamond]"));
    }

    /**
     * Model check (server.md 1.4): every interleaving of commit, settle, save, crash, quit and join keeps
     * "items in the file + payouts still owed by open records" consistent and pays every round at most once.
     */
    @Test
    void everyInterleavingOfSaveCrashAndSettleIsConsistent() {
        String[] ops = {"commit", "save", "settle", "crash", "quit", "join"};
        int sequences = 0;
        int[] idx = new int[5];
        while (true) {
            sequences++;
            FakeAccount a = new FakeAccount().with(DIAMOND, 8);
            FakeAccount.Saved file = a.save();
            boolean online = true;
            int committed = 0;
            for (int i : idx) {
                switch (ops[i]) {
                    case "commit" -> {
                        if (online && a.count(DIAMOND) >= 2 && a.ledger().owed().isEmpty()
                                && engine.commit(a, request(DIAMOND, 2, 6)) instanceof WagerEngine.Committed) {
                            committed++;
                        }
                    }
                    case "save" -> file = online ? a.save() : file;
                    case "settle" -> {
                        if (online && !a.ledger().rounds().isEmpty()) {
                            engine.settle(a, a.ledger().rounds().getFirst().id(), SettleReason.NORMAL);
                        }
                    }
                    case "crash" -> {
                        a.restore(file);
                        online = false;
                    }
                    case "quit" -> {
                        if (online) {
                            engine.settleAll(a, SettleReason.QUIT);
                            file = a.save();
                            online = false;
                        }
                    }
                    case "join" -> {
                        if (!online) {
                            a.restore(file);
                            engine.settleAll(a, SettleReason.JOIN_RECOVERY);
                            online = true;
                        }
                    }
                    default -> throw new IllegalStateException();
                }
                // invariant at every point: each round's stake of 2 is either still in the inventory (round never
                // happened) or became 6 (paid) / is pending in an open record (worth 6 when settled)
                long value = a.count(DIAMOND) + ItemAmount.total(a.ledger().owed(), DIAMOND)
                        + a.ledger().rounds().stream().mapToLong(r -> ItemAmount.total(r.payout(), DIAMOND)).sum();
                long rounds = (value - 8) / 4;
                assertEquals(8 + 4 * rounds, value, "inconsistent after " + describe(ops, idx));
                assertTrue(rounds >= 0 && rounds <= committed, "paid a round twice after " + describe(ops, idx));
            }
            int k = idx.length - 1;
            while (k >= 0 && idx[k] == ops.length - 1) {
                idx[k--] = 0;
            }
            if (k < 0) {
                break;
            }
            idx[k]++;
        }
        assertEquals((int) Math.pow(ops.length, idx.length), sequences);
    }

    private static String describe(String[] ops, int[] idx) {
        StringBuilder b = new StringBuilder();
        for (int i : idx) {
            b.append(ops[i]).append(' ');
        }
        return b.toString().strip();
    }
}
