package de.tigges.kaboofcasino.machine;

import de.tigges.kaboofcasino.util.Keys;
import de.tigges.kaboofcasino.util.Log;
import de.tigges.kaboofcasino.util.Pos;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Chunk PDC <-> machine records ({@code kaboofcasino:machines}, a list of containers). Only on loaded chunks (F13).
 * Positions are three ints. Thread: MAIN.
 */
public final class ChunkRecords {

    private static final NamespacedKey V = Keys.key("v");
    private static final NamespacedKey ID = Keys.key("id");
    private static final NamespacedKey TYPE = Keys.key("type");
    private static final NamespacedKey X = Keys.key("x");
    private static final NamespacedKey Y = Keys.key("y");
    private static final NamespacedKey Z = Keys.key("z");
    private static final NamespacedKey FACING = Keys.key("facing");
    private static final NamespacedKey PLACER = Keys.key("placer");
    private static final NamespacedKey PLACED = Keys.key("placed");
    private static final NamespacedKey NUMBER = Keys.key("number");
    private static final NamespacedKey DATA = Keys.key("data");

    private ChunkRecords() {
    }

    public static boolean has(Chunk chunk) {
        return chunk.getPersistentDataContainer().has(Keys.MACHINES);
    }

    public static List<MachineRecord> read(Chunk chunk) {
        String world = chunk.getWorld().getKey().asString();
        List<MachineRecord> out = new ArrayList<>();
        for (PersistentDataContainer c : raw(chunk)) {
            try {
                out.add(decode(world, c));
            } catch (RuntimeException e) {
                Log.critical("MACHINE_RECORD_UNREADABLE", "chunk {} {},{} holds an unreadable machine record (kept "
                        + "unchanged in the chunk, machine inactive): {}", world, chunk.getX(), chunk.getZ(),
                        e.toString());
            }
        }
        return out;
    }

    private static List<PersistentDataContainer> raw(Chunk chunk) {
        return chunk.getPersistentDataContainer().getOrDefault(Keys.MACHINES,
                PersistentDataType.LIST.dataContainers(), List.of());
    }

    private static MachineRecord decode(String world, PersistentDataContainer c) {
        MachineType type = MachineType.byId(c.get(TYPE, PersistentDataType.STRING)).orElseThrow();
        return new MachineRecord(UUID.fromString(c.get(ID, PersistentDataType.STRING)), type,
                new Pos(world, c.get(X, PersistentDataType.INTEGER), c.get(Y, PersistentDataType.INTEGER),
                        c.get(Z, PersistentDataType.INTEGER)),
                Facing.valueOf(c.get(FACING, PersistentDataType.STRING)),
                UUID.fromString(c.get(PLACER, PersistentDataType.STRING)),
                c.get(PLACED, PersistentDataType.LONG),
                c.getOrDefault(NUMBER, PersistentDataType.INTEGER, 0),
                c.getOrDefault(DATA, PersistentDataType.STRING, ""));
    }

    /** The id string of a raw record, or null if it has none (such entries are kept as they are). */
    private static String idOf(PersistentDataContainer c) {
        try {
            return c.get(ID, PersistentDataType.STRING);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static PersistentDataContainer encode(PersistentDataAdapterContext ctx, MachineRecord r) {
        PersistentDataContainer c = ctx.newPersistentDataContainer();
        c.set(V, PersistentDataType.INTEGER, MachineRecord.VERSION);
        c.set(ID, PersistentDataType.STRING, r.id().toString());
        c.set(TYPE, PersistentDataType.STRING, r.type().id());
        c.set(X, PersistentDataType.INTEGER, r.anchor().x());
        c.set(Y, PersistentDataType.INTEGER, r.anchor().y());
        c.set(Z, PersistentDataType.INTEGER, r.anchor().z());
        c.set(FACING, PersistentDataType.STRING, r.facing().name());
        c.set(PLACER, PersistentDataType.STRING, r.placer().toString());
        c.set(PLACED, PersistentDataType.LONG, r.placedAt());
        c.set(NUMBER, PersistentDataType.INTEGER, r.number());
        c.set(DATA, PersistentDataType.STRING, r.data());
        return c;
    }

    /** Writes the raw list back (removes the key when empty). */
    private static void writeRaw(Chunk chunk, List<PersistentDataContainer> list) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        if (list.isEmpty()) {
            pdc.remove(Keys.MACHINES);
        } else {
            pdc.set(Keys.MACHINES, PersistentDataType.LIST.dataContainers(), list);
        }
    }

    /**
     * Adds or replaces one record (by id) in its anchor chunk. Every other entry, also an unreadable one, is written
     * back verbatim (never dropped silently).
     */
    public static void put(Chunk chunk, MachineRecord record) {
        String id = record.id().toString();
        List<PersistentDataContainer> list = new ArrayList<>();
        for (PersistentDataContainer c : raw(chunk)) {
            if (!id.equals(idOf(c))) {
                list.add(c);
            }
        }
        list.add(encode(chunk.getPersistentDataContainer().getAdapterContext(), record));
        writeRaw(chunk, list);
    }

    /** Removes one record by id; other entries stay verbatim. */
    public static void remove(Chunk chunk, UUID id) {
        String key = id.toString();
        List<PersistentDataContainer> before = raw(chunk);
        List<PersistentDataContainer> list = new ArrayList<>();
        for (PersistentDataContainer c : before) {
            if (!key.equals(idOf(c))) {
                list.add(c);
            }
        }
        if (list.size() != before.size()) {
            writeRaw(chunk, list);
        }
    }
}
