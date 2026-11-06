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
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        List<PersistentDataContainer> list = pdc.getOrDefault(Keys.MACHINES, PersistentDataType.LIST.dataContainers(),
                List.of());
        String world = chunk.getWorld().getKey().asString();
        List<MachineRecord> out = new ArrayList<>();
        for (PersistentDataContainer c : list) {
            try {
                MachineType type = MachineType.byId(c.get(TYPE, PersistentDataType.STRING)).orElseThrow();
                out.add(new MachineRecord(UUID.fromString(c.get(ID, PersistentDataType.STRING)), type,
                        new Pos(world, c.get(X, PersistentDataType.INTEGER), c.get(Y, PersistentDataType.INTEGER),
                                c.get(Z, PersistentDataType.INTEGER)),
                        Facing.valueOf(c.get(FACING, PersistentDataType.STRING)),
                        UUID.fromString(c.get(PLACER, PersistentDataType.STRING)),
                        c.get(PLACED, PersistentDataType.LONG),
                        c.getOrDefault(NUMBER, PersistentDataType.INTEGER, 0),
                        c.getOrDefault(DATA, PersistentDataType.STRING, "")));
            } catch (RuntimeException e) {
                Log.critical("MACHINE_RECORD_UNREADABLE", "chunk {} {},{} holds an unreadable machine record "
                        + "(skipped, machine inactive): {}", world, chunk.getX(), chunk.getZ(), e.toString());
            }
        }
        return out;
    }

    public static void write(Chunk chunk, List<MachineRecord> records) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        if (records.isEmpty()) {
            pdc.remove(Keys.MACHINES);
            return;
        }
        PersistentDataAdapterContext ctx = pdc.getAdapterContext();
        List<PersistentDataContainer> list = new ArrayList<>();
        for (MachineRecord r : records) {
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
            list.add(c);
        }
        pdc.set(Keys.MACHINES, PersistentDataType.LIST.dataContainers(), list);
    }

    /** Adds or replaces one record (by id) in its anchor chunk. */
    public static void put(Chunk chunk, MachineRecord record) {
        List<MachineRecord> records = new ArrayList<>(read(chunk));
        records.removeIf(r -> r.id().equals(record.id()));
        records.add(record);
        write(chunk, records);
    }

    public static void remove(Chunk chunk, UUID id) {
        List<MachineRecord> records = new ArrayList<>(read(chunk));
        if (records.removeIf(r -> r.id().equals(id))) {
            write(chunk, records);
        }
    }
}
