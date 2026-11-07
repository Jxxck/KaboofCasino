package de.tigges.kaboofcasino.music;

import de.tigges.kaboofcasino.util.Keys;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * The station of a world in the world's PDC (saved with the world like the mod's SavedData
 * {@code casinosound:station}): {@code kaboofcasino:music_station} = {epoch: long, seed: long, speakers: int[] of x, y,
 * z triples}. Positions are plain ints (never a packed block key). Thread: MAIN.
 */
final class StationStore {

    static final NamespacedKey STATION = Keys.key("music_station");
    private static final NamespacedKey EPOCH = Keys.key("epoch");
    private static final NamespacedKey SEED = Keys.key("seed");
    private static final NamespacedKey SPEAKERS = Keys.key("speakers");

    private StationStore() {
    }

    static MusicStation read(PersistentDataContainer world) {
        PersistentDataContainer c = world.get(STATION, PersistentDataType.TAG_CONTAINER);
        if (c == null) {
            return new MusicStation();
        }
        long epoch = c.getOrDefault(EPOCH, PersistentDataType.LONG, -1L);
        long seed = c.getOrDefault(SEED, PersistentDataType.LONG, 0L);
        int[] speakers = c.getOrDefault(SPEAKERS, PersistentDataType.INTEGER_ARRAY, new int[0]);
        return new MusicStation(epoch, seed, MusicStation.unpack(speakers));
    }

    static void write(PersistentDataContainer world, MusicStation station) {
        PersistentDataContainer c = world.getAdapterContext().newPersistentDataContainer();
        c.set(EPOCH, PersistentDataType.LONG, station.epoch());
        c.set(SEED, PersistentDataType.LONG, station.seed());
        c.set(SPEAKERS, PersistentDataType.INTEGER_ARRAY, station.packedSpeakers());
        world.set(STATION, PersistentDataType.TAG_CONTAINER, c);
    }
}
