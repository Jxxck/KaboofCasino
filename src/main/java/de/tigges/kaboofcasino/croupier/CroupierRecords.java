package de.tigges.kaboofcasino.croupier;

import de.tigges.kaboofcasino.util.Keys;
import de.tigges.kaboofcasino.util.Log;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Chunk PDC {@code kaboofcasino:croupiers}: a list of JSON strings ({@link CroupierCodec}), saved with the chunk's
 * blocks, so a croupier and its counter (same chunk) roll back together after a crash. Every entry other than the one
 * written, also an unreadable or newer one, is kept verbatim. Only on loaded chunks. Thread: MAIN.
 */
public final class CroupierRecords {

    public static final NamespacedKey KEY = Keys.key("croupiers");

    private CroupierRecords() {
    }

    public static boolean has(Chunk chunk) {
        return chunk.getPersistentDataContainer().has(KEY);
    }

    /** The readable records of the chunk; unreadable and newer ones are logged and skipped (and stay stored). */
    public static List<CroupierRecord> read(Chunk chunk) {
        String world = chunk.getWorld().getKey().asString();
        List<CroupierRecord> out = new ArrayList<>();
        for (String raw : raw(chunk)) {
            try {
                CroupierRecord r = CroupierCodec.decode(raw);
                if (!r.stand().world().equals(world) || r.stand().chunkX() != chunk.getX()
                        || r.stand().chunkZ() != chunk.getZ()) {
                    Log.critical("CROUPIER_RECORD_ELSEWHERE", "chunk {} {},{} holds croupier {} standing at {} (kept "
                            + "unchanged, croupier inactive)", world, chunk.getX(), chunk.getZ(), r.id(), r.stand());
                    continue;
                }
                out.add(r);
            } catch (CroupierCodec.NewerVersion e) {
                Log.critical("CROUPIER_RECORD_NEWER", "chunk {} {},{}: {} (kept unchanged, croupier inactive)", world,
                        chunk.getX(), chunk.getZ(), e.getMessage());
            } catch (RuntimeException e) {
                Log.critical("CROUPIER_RECORD_UNREADABLE", "chunk {} {},{} holds an unreadable croupier record (kept "
                        + "unchanged, croupier inactive): {}", world, chunk.getX(), chunk.getZ(), e.toString());
            }
        }
        return out;
    }

    private static List<String> raw(Chunk chunk) {
        return chunk.getPersistentDataContainer().getOrDefault(KEY, PersistentDataType.LIST.strings(), List.of());
    }

    private static void writeRaw(Chunk chunk, List<String> list) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        if (list.isEmpty()) {
            pdc.remove(KEY);
        } else {
            pdc.set(KEY, PersistentDataType.LIST.strings(), list);
        }
    }

    /** Adds or replaces one record (by id); every other entry is written back verbatim. */
    public static void put(Chunk chunk, CroupierRecord record) {
        writeRaw(chunk, replaced(raw(chunk), record.id(), CroupierCodec.encode(record)));
    }

    /** Removes one record by id; other entries stay verbatim. */
    public static void remove(Chunk chunk, UUID id) {
        List<String> before = raw(chunk);
        List<String> after = replaced(before, id, null);
        if (after.size() != before.size()) {
            writeRaw(chunk, after);
        }
    }

    /** {@code list} without the entries of {@code id}, plus {@code encoded} at the end if not null. Pure. */
    static List<String> replaced(List<String> list, UUID id, String encoded) {
        String key = id.toString();
        List<String> out = new ArrayList<>(list.size() + 1);
        for (String raw : list) {
            if (!key.equals(CroupierCodec.idOf(raw))) {
                out.add(raw);
            }
        }
        if (encoded != null) {
            out.add(encoded);
        }
        return out;
    }
}
