package de.tigges.kaboofcasino.croupier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.tigges.kaboofcasino.machine.Facing;
import de.tigges.kaboofcasino.util.Pos;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The record in the chunk: round trips, defaults, newer and broken entries (kept verbatim by the store). */
class CroupierCodecTest {

    private static final String W = "minecraft:overworld";
    private static final String SNBT = "{Brain:{memories:{}},Inventory:[{count:3,id:\"minecraft:bread\"}],"
            + "VillagerData:{level:1,profession:\"minecraft:none\",type:\"minecraft:plains\"},Xp:0,"
            + "CustomName:\"quote \\\" and unicode äöü\"}";

    private static CroupierRecord full() {
        return new CroupierRecord(UUID.fromString("0b6c3b8e-4f0e-4c4a-9b1e-2a7f4f3b9d10"), new Pos(W, -1201, 1022, 33),
                -0.0625, Facing.WEST, UUID.fromString("7a1d9a52-7d1c-4d3a-8f0e-0c6d2b3e4f50"),
                new Pos(W, -1202, 1022, 33), UUID.fromString("e2c8d1f4-1b9a-4f3e-9c2d-5a6b7c8d9e0f"), SNBT, "26.3",
                List.of("AAEC", "3q2+7w=="), 17, 34, 20_512L, 1_762_612_345_678L,
                UUID.fromString("11111111-2222-3333-4444-555555555555"));
    }

    @Test
    void roundTrip() {
        CroupierRecord r = full();
        String json = CroupierCodec.encode(r);
        assertEquals(r, CroupierCodec.decode(json));
        assertEquals(r.id().toString(), CroupierCodec.idOf(json));
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        assertEquals(CroupierCodec.VERSION, o.get("v").getAsInt());
        assertEquals(SNBT, o.get("snapshot").getAsString());
        // an attached croupier on its counter's top, the largest whole numbers
        CroupierRecord top = new CroupierRecord(UUID.randomUUID(), new Pos(W, 5, -64, 5),
                CroupierPlacement.COUNTER_TOP, Facing.SOUTH, UUID.randomUUID(), new Pos(W, 5, -64, 5), null, null,
                null, List.of(), CroupierTrade.MAX_USES, Integer.MAX_VALUE, Long.MAX_VALUE, Long.MIN_VALUE, null);
        assertEquals(top, CroupierCodec.decode(CroupierCodec.encode(top)));
    }

    @Test
    void aCroupierWithoutVillagerOrCounterOmitsTheirFields() {
        CroupierRecord r = new CroupierRecord(UUID.randomUUID(), new Pos(W, 0, 64, 0), 0.0, Facing.NORTH, null, null,
                null, null, null, List.of(), 0, 6, 3L, 9L, null);
        String json = CroupierCodec.encode(r);
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        for (String absent : List.of("counter", "cx", "villager", "snapshot", "mc", "items", "near")) {
            assertFalse(o.has(absent), absent);
        }
        CroupierRecord back = CroupierCodec.decode(json);
        assertEquals(r, back);
        assertFalse(back.hasCounter());
        assertNull(back.snapshot());
        assertTrue(back.items().isEmpty());
    }

    @Test
    void aMinimalVersionOneRecordReadsWithDefaults() {
        // what any version-1 writer must at least have written; everything else defaults to "none" / 0
        String minimal = "{\"v\":1,\"id\":\"0b6c3b8e-4f0e-4c4a-9b1e-2a7f4f3b9d10\",\"world\":\"minecraft:overworld\","
                + "\"x\":1,\"y\":2,\"z\":3,\"facing\":\"EAST\"}";
        CroupierRecord r = CroupierCodec.decode(minimal);
        assertEquals(new Pos(W, 1, 2, 3), r.stand());
        assertEquals(0.0, r.lift());
        assertEquals(Facing.EAST, r.facing());
        assertFalse(r.hasCounter());
        assertEquals(0, r.uses());
        assertEquals(0, r.xp());
        assertEquals(0L, r.restockDay());
        assertTrue(r.items().isEmpty());
        // unknown fields of a later minor change are ignored
        assertEquals(r, CroupierCodec.decode(minimal.replace("}", ",\"future\":[1,2]}")));
        // out-of-range numbers are clamped by the record
        CroupierRecord odd = CroupierCodec.decode(minimal.replace("}", ",\"uses\":999,\"xp\":-5}"));
        assertEquals(CroupierTrade.MAX_USES, odd.uses());
        assertEquals(0, odd.xp());
    }

    @Test
    void aNewerRecordIsRefusedButItsIdKnown() {
        String newer = CroupierCodec.encode(full()).replace("\"v\":1", "\"v\":2");
        assertThrows(CroupierCodec.NewerVersion.class, () -> CroupierCodec.decode(newer));
        assertEquals(full().id().toString(), CroupierCodec.idOf(newer));
    }

    @Test
    void brokenRecordsAreRefused() {
        String ok = CroupierCodec.encode(full());
        for (String broken : List.of("", "[]", "{}", "not json at all", ok.replace("\"v\":1", "\"v\":0"),
                ok.replace("\"facing\":\"WEST\"", "\"facing\":\"UP\""), ok.replace("\"x\":-1201", "\"x\":1.5"),
                ok.replace("\"x\":-1201", "\"x\":\"far\""), ok.replace(",\"cx\":-1202", ""),
                ok.replace("\"lift\":-0.0625", "\"lift\":7.0"), ok.replace("\"id\":\"0b6c", "\"id\":\"zz6c"),
                ok.replace("\"uses\":17", "\"uses\":1.5"))) {
            assertThrows(IllegalArgumentException.class, () -> CroupierCodec.decode(broken), broken);
            assertFalse(CroupierCodec.NewerVersion.class.isInstance(catchIt(broken)), broken);
        }
        assertNull(CroupierCodec.idOf("not json at all"));
        assertNull(CroupierCodec.idOf("{}"));
        assertThrows(IllegalArgumentException.class, () -> CroupierCodec.decode(null));
    }

    private static RuntimeException catchIt(String json) {
        try {
            CroupierCodec.decode(json);
            return null;
        } catch (RuntimeException e) {
            return e;
        }
    }

    @Test
    void theStoreReplacesOnlyItsOwnEntry() {
        CroupierRecord a = full();
        String other = CroupierCodec.encode(new CroupierRecord(UUID.randomUUID(), new Pos(W, 0, 64, 0), 0.0,
                Facing.NORTH, null, null, null, null, null, List.of(), 0, 0, 0L, 0L, null));
        String newer = other.replace("\"v\":1", "\"v\":9");
        String garbage = "%%% garbage %%%";
        List<String> list = List.of(garbage, CroupierCodec.encode(a), newer, other);
        CroupierRecord changed = a.withTrade(18, 36);
        List<String> after = CroupierRecords.replaced(list, a.id(), CroupierCodec.encode(changed));
        assertEquals(List.of(garbage, newer, other, CroupierCodec.encode(changed)), after);
        assertEquals(List.of(garbage, newer, other), CroupierRecords.replaced(after, a.id(), null));
        assertEquals(list, CroupierRecords.replaced(list, UUID.randomUUID(), null));
    }

    @Test
    void recordInvariants() {
        UUID id = UUID.randomUUID();
        Pos at = new Pos(W, 0, 64, 0);
        assertThrows(IllegalArgumentException.class, () -> new CroupierRecord(id, at, 0.0, Facing.NORTH,
                UUID.randomUUID(), null, null, null, null, List.of(), 0, 0, 0, 0, null));
        assertThrows(IllegalArgumentException.class, () -> new CroupierRecord(id, at, 0.0, Facing.NORTH,
                UUID.randomUUID(), new Pos("minecraft:the_end", 0, 64, 1), null, null, null, List.of(), 0, 0, 0, 0,
                null));
        assertThrows(IllegalArgumentException.class, () -> new CroupierRecord(id, at, Double.NaN, Facing.NORTH, null,
                null, null, null, null, List.of(), 0, 0, 0, 0, null));
        CroupierRecord r = new CroupierRecord(id, at, 0.0, Facing.NORTH, null, null, null, null, null, null, -3, -1, 0,
                0, null);
        assertEquals(0, r.uses());
        assertEquals(0, r.xp());
        assertTrue(r.items().isEmpty());
        assertEquals(1, r.level());
        assertEquals(3, r.withTrade(5, 70).level());
    }
}
