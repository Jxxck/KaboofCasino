package de.tigges.kaboofcasino.wager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

/**
 * The 0.8.0 -> 0.9.0 ledger migration (PLAN-2 C2): a ledger exactly as 0.8.0 wrote it (open ore records, a PLAYING
 * blackjack hand left by a crash, owed ores) is read by 0.9.0 and settled in ores; chip records make the ledger
 * version 3; a ledger without chip values is still written in the old versions (what a rollback to 0.8.0 reads).
 */
class LedgerMigrationTest {

    private static final UUID PLAYER = UUID.fromString("10920508-d5d8-3eed-93d2-92f193afe7d7");
    private static final UUID SLOT_ROUND = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID HAND_ROUND = UUID.fromString("a0000000-0000-0000-0000-000000000002");
    private static final UUID MACHINE = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private long now = 2_000_000;

    private final WagerEngine engine = new WagerEngine(new WagerEngine.Limits() {
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
    }, RoundSink.NONE, () -> now);

    private static NamespacedKey key(String k) {
        return new NamespacedKey("kaboofcasino", k);
    }

    private static PersistentDataContainer amount(String item, long count) {
        PersistentDataContainer c = FakePdc.CONTEXT.newPersistentDataContainer();
        c.set(key("item"), PersistentDataType.STRING, item);
        c.set(key("count"), PersistentDataType.LONG, count);
        return c;
    }

    private static PersistentDataContainer round(UUID id, String game, String state, PersistentDataContainer stake,
                                                 List<PersistentDataContainer> payout, long created, long ends,
                                                 String detail) {
        PersistentDataContainer c = FakePdc.CONTEXT.newPersistentDataContainer();
        c.set(key("id"), PersistentDataType.STRING, id.toString());
        c.set(key("machine"), PersistentDataType.STRING, MACHINE.toString());
        c.set(key("game"), PersistentDataType.STRING, game);
        c.set(key("state"), PersistentDataType.STRING, state);
        c.set(key("stake"), PersistentDataType.TAG_CONTAINER, stake);
        c.set(key("payout"), PersistentDataType.LIST.dataContainers(), payout);
        c.set(key("created"), PersistentDataType.LONG, created);
        c.set(key("ends"), PersistentDataType.LONG, ends);
        c.set(key("detail"), PersistentDataType.STRING, detail);
        return c;
    }

    /** A player file's PDC as 0.8.0 left it after a crash (its own writer's layout, built key by key). */
    private static FakePdc file080() {
        PersistentDataContainer root = FakePdc.CONTEXT.newPersistentDataContainer();
        root.set(key("v"), PersistentDataType.INTEGER, 2);
        root.set(key("rounds"), PersistentDataType.LIST.dataContainers(), List.of(
                round(SLOT_ROUND, "slot_machine", "DECIDED", amount("minecraft:diamond", 5),
                        List.of(amount("minecraft:diamond", 50)), 1_000_000, 1_006_000, "stops=0,0,1 x10"),
                round(HAND_ROUND, "blackjack_table", "PLAYING", amount("minecraft:gold_ingot", 8),
                        List.of(amount("minecraft:gold_ingot", 16)), 1_100_000, 1_400_000, "hand, stand pays")));
        root.set(key("owed"), PersistentDataType.LIST.dataContainers(), List.of(amount("minecraft:emerald", 70),
                amount("minecraft:iron_block", 3)));
        FakePdc pdc = new FakePdc();
        pdc.set(key("ledger"), PersistentDataType.TAG_CONTAINER, root);
        return pdc;
    }

    /** An account whose ledger lives in a real (fake) PDC, so every write goes through the codec. */
    private static final class PdcAccount implements Account {
        final FakePdc pdc;
        final FakeAccount items = new FakeAccount();

        PdcAccount(FakePdc pdc) {
            this.pdc = pdc;
        }

        @Override
        public UUID id() {
            return PLAYER;
        }

        @Override
        public String name() {
            return "Alice";
        }

        @Override
        public Ledger ledger() {
            return LedgerCodec.read(pdc);
        }

        @Override
        public void ledger(Ledger ledger) {
            LedgerCodec.write(pdc, ledger);
        }

        @Override
        public long countPlain(String item) {
            return items.countPlain(item);
        }

        @Override
        public TakePlan planTake(List<ItemAmount> list) {
            return items.planTake(list);
        }

        @Override
        public void applyTake(TakePlan plan) {
            items.applyTake(plan);
        }

        @Override
        public List<ItemAmount> give(List<ItemAmount> list) {
            return items.give(list);
        }

        int version() {
            PersistentDataContainer root = pdc.get(key("ledger"), PersistentDataType.TAG_CONTAINER);
            return root == null ? 0 : root.get(key("v"), PersistentDataType.INTEGER);
        }
    }

    @Test
    void a080LedgerIsReadAndSettledInOres() {
        PdcAccount a = new PdcAccount(file080());
        Ledger l = a.ledger();
        assertEquals(2, l.rounds().size());
        assertEquals(new ItemAmount("minecraft:diamond", 5), l.round(SLOT_ROUND).orElseThrow().stake());
        assertEquals(RoundState.PLAYING, l.round(HAND_ROUND).orElseThrow().state());
        assertEquals(List.of(new ItemAmount("minecraft:emerald", 70), new ItemAmount("minecraft:iron_block", 3)),
                l.owed());
        // the join after the crash: both records settle (the hand pays its stored stand payout), owed comes too
        List<RoundRecord> settled = engine.settleAll(a, SettleReason.JOIN_RECOVERY);
        assertEquals(2, settled.size());
        assertEquals(50, a.items.count("minecraft:diamond"));
        assertEquals(16, a.items.count("minecraft:gold_ingot"));
        assertEquals(70, a.items.count("minecraft:emerald"));
        assertEquals(3, a.items.count("minecraft:iron_block"));
        assertTrue(a.ledger().isEmpty());
        assertEquals(0, a.version(), "an empty ledger leaves no key");
    }

    @Test
    void owedOresWaitForRoomAndStayAnOldVersionLedger() {
        PdcAccount a = new PdcAccount(file080());
        a.items.capacityStacks = 1;
        engine.settleAll(a, SettleReason.JOIN_RECOVERY);
        // one stack of room: 64 of the owed emeralds came first, the rest stays owed as ores in a version 1 ledger
        assertEquals(64, a.items.count("minecraft:emerald"));
        assertEquals(List.of(new ItemAmount("minecraft:emerald", 6), new ItemAmount("minecraft:iron_block", 3),
                new ItemAmount("minecraft:diamond", 50), new ItemAmount("minecraft:gold_ingot", 16)),
                a.ledger().owed());
        assertEquals(1, a.version(), "0.8.0 (and 0.3.x) can still read it after a rollback");
        // a chip round is refused while ores are owed (as before), so no chip value joins an old-version ledger yet
        a.items.with(Chips.key(25), 2);
        WagerEngine.Request bet = new WagerEngine.Request(MACHINE, "plinko", new ItemAmount(Chips.VALUE, 50),
                List.of(), now, "x").withTake(List.of(new ItemAmount(Chips.key(25), 2)));
        assertEquals(WagerEngine.Refusal.OWED_PENDING,
                assertInstanceOf(WagerEngine.Refused.class, engine.commit(a, bet)).refusal());
        a.items.capacityStacks = 36;
        engine.deliverOwed(a);
        assertTrue(a.ledger().isEmpty());
        assertInstanceOf(WagerEngine.Committed.class, engine.commit(a, bet));
        assertEquals(3, a.version(), "a chip record makes it version 3");
    }

    @Test
    void theWriterKeepsTheOldLayoutForOreLedgers() {
        // what 0.9.0 writes for an ore-only ledger is key by key what 0.8.0 wrote: a rollback reads it unchanged
        FakePdc original = file080();
        Ledger l = LedgerCodec.read(original);
        FakePdc rewritten = new FakePdc();
        LedgerCodec.write(rewritten, l);
        assertEquals(LedgerCodec.read(rewritten), l);
        PersistentDataContainer root = rewritten.get(key("ledger"), PersistentDataType.TAG_CONTAINER);
        assertEquals(2, root.get(key("v"), PersistentDataType.INTEGER));
        assertEquals(Set.of(key("v"), key("rounds"), key("owed")), root.getKeys());
        // a chip value anywhere: version 3 (0.8.0 refuses such a ledger and leaves it untouched)
        Ledger chips = l.plusOwed(List.of(new ItemAmount(Chips.VALUE, 125)));
        LedgerCodec.write(rewritten, chips);
        root = rewritten.get(key("ledger"), PersistentDataType.TAG_CONTAINER);
        assertEquals(3, root.get(key("v"), PersistentDataType.INTEGER));
        assertEquals(chips, LedgerCodec.read(rewritten));
    }

    @Test
    void a080ReaderRefusesVersion3AndTouchesNothing() {
        // 0.8.0's codec: VERSION = 2, "v < 1 || v > VERSION" -> CorruptLedgerException; its listeners then log
        // CRITICAL and refuse every casino action for that player, never writing the ledger. Simulated with the
        // same check on the stored version.
        FakePdc pdc = new FakePdc();
        LedgerCodec.write(pdc, new Ledger(List.of(), List.of(new ItemAmount(Chips.VALUE, 40))));
        Map<NamespacedKey, Object> before = Map.copyOf(pdc.raw());
        int v = pdc.get(key("ledger"), PersistentDataType.TAG_CONTAINER).get(key("v"), PersistentDataType.INTEGER);
        assertTrue(v > 2, "0.8.0 knows 1..2");
        assertEquals(before, pdc.raw());
        // and this version refuses a newer one the same way
        PersistentDataContainer root = pdc.get(key("ledger"), PersistentDataType.TAG_CONTAINER);
        root.set(key("v"), PersistentDataType.INTEGER, LedgerCodec.VERSION + 1);
        pdc.set(key("ledger"), PersistentDataType.TAG_CONTAINER, root);
        assertThrows(LedgerCodec.CorruptLedgerException.class, () -> LedgerCodec.read(pdc));
    }

    @Test
    void aCleanStopLeavesOnlyOwedItems() {
        // after a clean stop (server.md: STOP settles everything) the ledger holds at most owed items: ores in an
        // old version, chips as one value in version 3
        PdcAccount a = new PdcAccount(file080());
        a.items.capacityStacks = 0;
        List<RoundRecord> settled = engine.settleAll(a, SettleReason.STOP);
        assertEquals(2, settled.size());
        assertTrue(a.ledger().rounds().isEmpty());
        List<ItemAmount> owed = new ArrayList<>(a.ledger().owed());
        assertEquals(4, owed.size());
        assertEquals(1, a.version());
    }
}
