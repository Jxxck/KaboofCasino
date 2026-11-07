package de.tigges.kaboofcasino.music;

import de.tigges.kaboofcasino.machine.Facing;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The loaded, switched-on speakers of one world, bucketed in 64 x 64 columns (x/z), so "the nearest speaker within 48
 * blocks" looks at no more than 3 x 3 buckets instead of every speaker (the mod's client scans all loaded speaker
 * block entities every tick). Pure. Thread: MAIN.
 */
final class SpeakerGrid {

    static final int CELL = 64;

    /** A loaded, switched-on speaker. */
    record Speaker(MusicStation.Cell pos, Facing facing) {

        /** {@code Vec3.atCenterOf(pos).distanceToSqr(x, y, z)}. */
        double distanceSq(double x, double y, double z) {
            double dx = pos.x() + 0.5 - x;
            double dy = pos.y() + 0.5 - y;
            double dz = pos.z() + 0.5 - z;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    /** The nearest speaker and its squared distance. */
    record Nearest(Speaker speaker, double distanceSq) {
    }

    private final Map<MusicStation.Cell, Speaker> all = new LinkedHashMap<>();
    private final Map<Long, List<Speaker>> buckets = new HashMap<>();

    private static long bucket(int bx, int bz) {
        return ((long) bx & 0xffffffffL) | (((long) bz & 0xffffffffL) << 32);
    }

    private static long bucketOf(MusicStation.Cell c) {
        return bucket(Math.floorDiv(c.x(), CELL), Math.floorDiv(c.z(), CELL));
    }

    /** Adds or replaces (new facing) a speaker. */
    void put(MusicStation.Cell pos, Facing facing) {
        remove(pos);
        Speaker s = new Speaker(pos, facing);
        all.put(pos, s);
        buckets.computeIfAbsent(bucketOf(pos), k -> new ArrayList<>(2)).add(s);
    }

    /** True if it was there. */
    boolean remove(MusicStation.Cell pos) {
        Speaker old = all.remove(pos);
        if (old == null) {
            return false;
        }
        long key = bucketOf(pos);
        List<Speaker> list = buckets.get(key);
        if (list != null) {
            list.remove(old);
            if (list.isEmpty()) {
                buckets.remove(key);
            }
        }
        return true;
    }

    boolean contains(MusicStation.Cell pos) {
        return all.containsKey(pos);
    }

    boolean isEmpty() {
        return all.isEmpty();
    }

    int size() {
        return all.size();
    }

    Collection<Speaker> all() {
        return Collections.unmodifiableCollection(all.values());
    }

    /**
     * The nearest speaker strictly closer than {@code range} to the point (block centres, like the mod's
     * {@code d < distance} search with {@code d < RANGE²}), or null.
     */
    Nearest nearest(double x, double y, double z, double range) {
        double limit = range * range;
        Speaker best = null;
        double bestSq = limit;
        int bx0 = Math.floorDiv((int) Math.floor(x - range), CELL);
        int bx1 = Math.floorDiv((int) Math.floor(x + range), CELL);
        int bz0 = Math.floorDiv((int) Math.floor(z - range), CELL);
        int bz1 = Math.floorDiv((int) Math.floor(z + range), CELL);
        for (int bx = bx0; bx <= bx1; bx++) {
            for (int bz = bz0; bz <= bz1; bz++) {
                List<Speaker> list = buckets.get(bucket(bx, bz));
                if (list == null) {
                    continue;
                }
                for (Speaker s : list) {
                    double d = s.distanceSq(x, y, z);
                    if (d < bestSq) {
                        bestSq = d;
                        best = s;
                    }
                }
            }
        }
        return best == null ? null : new Nearest(best, bestSq);
    }
}
