package de.tigges.kaboofcasino.deco.display;

import de.tigges.kaboofcasino.machine.Facing;
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
 * Chunk PDC {@code kaboofcasino:deco} <-> {@link DecoRecord}s (a list of containers, like the machines'
 * {@code ChunkRecords}): saved with the chunk's blocks, so a carrier and its record roll back together after a crash.
 * Positions are three ints. Every entry other than the one written, also an unreadable one, is kept verbatim. Only on
 * loaded chunks. Thread: MAIN.
 */
public final class DecoRecords {

    public static final NamespacedKey KEY = Keys.key("deco");

    private static final NamespacedKey V = Keys.key("v");
    private static final NamespacedKey ID = Keys.key("id");
    private static final NamespacedKey TYPE = Keys.key("type");
    private static final NamespacedKey X = Keys.key("x");
    private static final NamespacedKey Y = Keys.key("y");
    private static final NamespacedKey Z = Keys.key("z");
    private static final NamespacedKey FACING = Keys.key("facing");
    private static final NamespacedKey STATE = Keys.key("state");
    private static final NamespacedKey PLACER = Keys.key("placer");
    private static final NamespacedKey PLACED = Keys.key("placed");

    private DecoRecords() {
    }

    public static boolean has(Chunk chunk) {
        return chunk.getPersistentDataContainer().has(KEY);
    }

    public static List<DecoRecord> read(Chunk chunk) {
        String world = chunk.getWorld().getKey().asString();
        List<DecoRecord> out = new ArrayList<>();
        for (PersistentDataContainer c : raw(chunk)) {
            try {
                out.add(decode(world, c));
            } catch (RuntimeException e) {
                Log.critical("DECO_RECORD_UNREADABLE", "chunk {} {},{} holds an unreadable decoration record (kept "
                        + "unchanged, block inactive): {}", world, chunk.getX(), chunk.getZ(), e.toString());
            }
        }
        return out;
    }

    private static List<PersistentDataContainer> raw(Chunk chunk) {
        return chunk.getPersistentDataContainer().getOrDefault(KEY, PersistentDataType.LIST.dataContainers(),
                List.of());
    }

    private static DecoRecord decode(String world, PersistentDataContainer c) {
        DecoType type = DecoType.byId(c.get(TYPE, PersistentDataType.STRING)).orElseThrow();
        String placer = c.get(PLACER, PersistentDataType.STRING);
        return new DecoRecord(UUID.fromString(c.get(ID, PersistentDataType.STRING)), type,
                new Pos(world, c.get(X, PersistentDataType.INTEGER), c.get(Y, PersistentDataType.INTEGER),
                        c.get(Z, PersistentDataType.INTEGER)),
                Facing.valueOf(c.get(FACING, PersistentDataType.STRING)),
                c.getOrDefault(STATE, PersistentDataType.INTEGER, 0),
                placer == null ? new UUID(0, 0) : UUID.fromString(placer),
                c.getOrDefault(PLACED, PersistentDataType.LONG, 0L));
    }

    private static String idOf(PersistentDataContainer c) {
        try {
            return c.get(ID, PersistentDataType.STRING);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static PersistentDataContainer encode(PersistentDataAdapterContext ctx, DecoRecord r) {
        PersistentDataContainer c = ctx.newPersistentDataContainer();
        c.set(V, PersistentDataType.INTEGER, DecoRecord.VERSION);
        c.set(ID, PersistentDataType.STRING, r.id().toString());
        c.set(TYPE, PersistentDataType.STRING, r.type().id());
        c.set(X, PersistentDataType.INTEGER, r.anchor().x());
        c.set(Y, PersistentDataType.INTEGER, r.anchor().y());
        c.set(Z, PersistentDataType.INTEGER, r.anchor().z());
        c.set(FACING, PersistentDataType.STRING, r.facing().name());
        c.set(STATE, PersistentDataType.INTEGER, r.state());
        c.set(PLACER, PersistentDataType.STRING, r.placer().toString());
        c.set(PLACED, PersistentDataType.LONG, r.placedAt());
        return c;
    }

    private static void writeRaw(Chunk chunk, List<PersistentDataContainer> list) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        if (list.isEmpty()) {
            pdc.remove(KEY);
        } else {
            pdc.set(KEY, PersistentDataType.LIST.dataContainers(), list);
        }
    }

    /** Adds or replaces one record (by id); every other entry is written back verbatim. */
    public static void put(Chunk chunk, DecoRecord record) {
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
