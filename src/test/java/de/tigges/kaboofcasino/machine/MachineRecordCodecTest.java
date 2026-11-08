package de.tigges.kaboofcasino.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import de.tigges.kaboofcasino.util.Keys;
import de.tigges.kaboofcasino.util.Pos;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

/**
 * The chunk-PDC codec of machine records with the croupier's-hand settings: a record written by 0.7.0 (no own keys)
 * reads as "automatic name, follows its type"; a record without own settings is written with exactly 0.7.0's keys
 * (and values); own settings round-trip as three optional keys that 0.7.0's decoder never reads; invalid stored values
 * read as "inherit" instead of making the machine unreadable.
 */
class MachineRecordCodecTest {

    private static final String WORLD = "minecraft:overworld";
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID PLACER = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    /** The record as 0.7.0's {@code ChunkRecords.encode} wrote it. */
    private static FakePdc oldRecord() {
        FakePdc c = new FakePdc();
        c.set(Keys.key("v"), PersistentDataType.INTEGER, 1);
        c.set(Keys.key("id"), PersistentDataType.STRING, ID.toString());
        c.set(Keys.key("type"), PersistentDataType.STRING, "slot_machine");
        c.set(Keys.key("x"), PersistentDataType.INTEGER, 10);
        c.set(Keys.key("y"), PersistentDataType.INTEGER, 1000);
        c.set(Keys.key("z"), PersistentDataType.INTEGER, -3);
        c.set(Keys.key("facing"), PersistentDataType.STRING, "NORTH");
        c.set(Keys.key("placer"), PersistentDataType.STRING, PLACER.toString());
        c.set(Keys.key("placed"), PersistentDataType.LONG, 1_700_000_000_000L);
        c.set(Keys.key("number"), PersistentDataType.INTEGER, 3);
        c.set(Keys.key("data"), PersistentDataType.STRING, "1,2,3");
        return c;
    }

    private static MachineRecord record(MachineSettings.Own own) {
        return new MachineRecord(ID, MachineType.SLOT_MACHINE, new Pos(WORLD, 10, 1000, -3), Facing.NORTH, PLACER,
                1_700_000_000_000L, 3, "1,2,3", own);
    }

    private static Set<String> keys(FakePdc c) {
        return c.getKeys().stream().map(NamespacedKey::asString).collect(Collectors.toCollection(TreeSet::new));
    }

    @Test
    void anOldRecordReadsAsFollowingItsType() {
        MachineRecord r = ChunkRecords.decode(WORLD, oldRecord());
        assertEquals(MachineSettings.Own.NONE, r.own());
        assertEquals(record(MachineSettings.Own.NONE), r);
    }

    @Test
    void aRecordWithoutOwnSettingsIsWrittenExactlyAsBefore() {
        FakePdc written = (FakePdc) ChunkRecords.encode(FakePdc.CONTEXT, record(MachineSettings.Own.NONE));
        assertEquals(keys(oldRecord()), keys(written));
        assertEquals(oldRecord().raw(), written.raw());
    }

    @Test
    void ownSettingsRoundTripAsThreeOptionalKeys() {
        for (MachineSettings.Own own : new MachineSettings.Own[] {
                new MachineSettings.Own("Glückspilz | 7", 30, MachineSettings.INHERIT_BIAS),
                new MachineSettings.Own("", 0, MachineSettings.INHERIT_BIAS),
                new MachineSettings.Own("x", MachineSettings.INHERIT_CHANCE, MachineSettings.INHERIT_BIAS),
                new MachineSettings.Own("", MachineSettings.INHERIT_CHANCE, -100),
                new MachineSettings.Own("Tisch 1", 100, 100)}) {
            FakePdc written = (FakePdc) ChunkRecords.encode(FakePdc.CONTEXT, record(own));
            assertEquals(record(own), ChunkRecords.decode(WORLD, written), own.toString());
            Set<String> extra = keys(written);
            extra.removeAll(keys(oldRecord()));
            Set<String> expected = new TreeSet<>();
            if (!own.name().isEmpty()) {
                expected.add("kaboofcasino:name");
            }
            if (own.ownChance()) {
                expected.add("kaboofcasino:chance");
            }
            if (own.ownBias()) {
                expected.add("kaboofcasino:bias");
            }
            assertEquals(expected, extra, own.toString());
            assertEquals(1, written.get(Keys.key("v"), PersistentDataType.INTEGER), "format version stays 1");
        }
    }

    /**
     * 0.7.0's decoder reads only its own keys by name ({@code c.get(KEY, type)}), never checks the version and never
     * lists the keys: a record with own settings decodes there as the same machine. (0.7.0 rewrites a record without
     * the extra keys when it saves game data, e.g. after a slot spin: a downgrade keeps the machines but drops their
     * own settings.)
     */
    @Test
    void theOldDecoderStillReadsANewRecord() {
        FakePdc written = (FakePdc) ChunkRecords.encode(FakePdc.CONTEXT,
                record(new MachineSettings.Own("Name", 55, MachineSettings.INHERIT_BIAS)));
        FakePdc old = oldRecord();
        for (NamespacedKey key : old.getKeys()) {
            assertEquals(old.raw().get(key), written.raw().get(key), key.asString());
        }
    }

    @Test
    void invalidStoredValuesReadAsInherit() {
        FakePdc c = oldRecord();
        c.set(Keys.key("name"), PersistentDataType.STRING, "a\u0007b");
        c.set(Keys.key("chance"), PersistentDataType.INTEGER, 101);
        c.set(Keys.key("bias"), PersistentDataType.INTEGER, -101);
        assertEquals(MachineSettings.Own.NONE, ChunkRecords.decode(WORLD, c).own());
        FakePdc wrongTypes = oldRecord();
        wrongTypes.set(Keys.key("chance"), PersistentDataType.STRING, "30");
        wrongTypes.set(Keys.key("bias"), PersistentDataType.LONG, 5L);
        assertEquals(MachineSettings.Own.NONE, ChunkRecords.decode(WORLD, wrongTypes).own());
        FakePdc spaced = oldRecord();
        spaced.set(Keys.key("name"), PersistentDataType.STRING, "  Lucky  ");
        assertEquals("Lucky", ChunkRecords.decode(WORLD, spaced).own().name());
    }

    @Test
    void withDataKeepsTheOwnSettings() {
        MachineSettings.Own own = new MachineSettings.Own("N", 12, MachineSettings.INHERIT_BIAS);
        MachineRecord r = record(own).withData("4,5,6");
        assertEquals(own, r.own());
        assertEquals("4,5,6", r.data());
        assertEquals(MachineSettings.Own.NONE, record(own).withOwn(MachineSettings.Own.NONE).own());
        assertNotNull(new MachineRecord(ID, MachineType.PLINKO, new Pos(WORLD, 0, 0, 0), Facing.SOUTH, PLACER, 0, 1,
                "").own());
        assertFalse(new MachineRecord(ID, MachineType.PLINKO, new Pos(WORLD, 0, 0, 0), Facing.SOUTH, PLACER, 0, 1,
                "").own().ownChance());
    }
}
