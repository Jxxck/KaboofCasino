package de.tigges.kaboofcasino.music;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * The shared clock of one world ({@code casinosound/CasinoStation}, SavedData {@code casinosound:station} per
 * dimension): {@code epoch} (game tick, -1 = idle), {@code seed} and the set of switched-on speakers, also those in
 * unloaded chunks. Pure; persisted in the world's PDC by {@link StationStore}. Thread: MAIN.
 *
 * <p>Same rules as the mod: the first speaker (or the first after an idle phase) starts a new playlist with a new
 * seed at the current game time; removing the last one makes the station idle. {@link #prune} is the mod's
 * {@code pruneLoaded} with a two-pass guard (see there).
 */
final class MusicStation {

    /** A speaker position inside the station's world (plain ints, never a packed block key). */
    record Cell(int x, int y, int z) {
    }

    private final Set<Cell> speakers = new LinkedHashSet<>();
    /** Positions that looked stale at the previous prune pass. */
    private Set<Cell> suspects = Set.of();
    private long epoch = -1L;
    private long seed;
    private boolean dirty;

    MusicStation() {
    }

    MusicStation(long epoch, long seed, Iterable<Cell> positions) {
        this.epoch = epoch;
        this.seed = seed;
        for (Cell c : positions) {
            speakers.add(c);
        }
    }

    long epoch() {
        return epoch;
    }

    long seed() {
        return seed;
    }

    /** True while a playlist runs (at least one speaker, epoch set). */
    boolean active() {
        return epoch >= 0L && !speakers.isEmpty();
    }

    Set<Cell> speakers() {
        return Collections.unmodifiableSet(speakers);
    }

    /** {@code CasinoStation.enable}: new epoch + seed if the station is empty or idle, then add the position. */
    void enable(Cell pos, long gameTime, LongSupplier randomSeed) {
        if (speakers.isEmpty() || epoch < 0L) {
            epoch = gameTime;
            seed = randomSeed.getAsLong();
            dirty = true;
        }
        if (speakers.add(pos)) {
            dirty = true;
        }
    }

    /** {@code CasinoStation.disable}: remove; the last one out makes the station idle. */
    void disable(Cell pos) {
        if (speakers.remove(pos)) {
            if (speakers.isEmpty()) {
                epoch = -1L;
            }
            dirty = true;
        }
    }

    /**
     * {@code CasinoStation.pruneLoaded} (every 100 ticks): removes positions whose chunk is loaded but which are no
     * switched-on speaker. The speaker block reports its state through {@link SpeakerHooks}; to never drop a speaker
     * whose chunk has just loaded and is not reported yet, a position is removed only when it is stale at two
     * passes in a row.
     *
     * @param stale true if the position's chunk is loaded and no switched-on speaker is known there
     * @return true if something was removed
     */
    boolean prune(Predicate<Cell> stale) {
        Set<Cell> now = new HashSet<>();
        for (Cell c : speakers) {
            if (stale.test(c)) {
                now.add(c);
            }
        }
        boolean changed = false;
        for (Cell c : now) {
            if (suspects.contains(c)) {
                speakers.remove(c);
                changed = true;
            }
        }
        now.removeIf(c -> !speakers.contains(c));
        suspects = now;
        if (changed) {
            if (speakers.isEmpty()) {
                epoch = -1L;
            }
            dirty = true;
        }
        return changed;
    }

    /** True once after a change (the store writes the PDC). */
    boolean takeDirty() {
        boolean d = dirty;
        dirty = false;
        return d;
    }

    /** x, y, z of every speaker in order, for the PDC. */
    int[] packedSpeakers() {
        int[] out = new int[speakers.size() * 3];
        int i = 0;
        for (Cell c : speakers) {
            out[i++] = c.x();
            out[i++] = c.y();
            out[i++] = c.z();
        }
        return out;
    }

    /** The inverse of {@link #packedSpeakers}; a trailing incomplete triple is ignored. */
    static Set<Cell> unpack(int[] packed) {
        Set<Cell> out = new LinkedHashSet<>();
        if (packed == null) {
            return out;
        }
        for (int i = 0; i + 2 < packed.length; i += 3) {
            out.add(new Cell(packed[i], packed[i + 1], packed[i + 2]));
        }
        return out;
    }

    @Override
    public String toString() {
        return "MusicStation[epoch=" + epoch + ", seed=" + seed + ", speakers=" + Arrays.toString(packedSpeakers())
                + "]";
    }
}
