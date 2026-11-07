package de.tigges.kaboofcasino.music;

import de.tigges.kaboofcasino.machine.Facing;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * Everything music of one world (pure): its {@link MusicStation} (persisted, every switched-on speaker) and the
 * {@link SpeakerGrid} of the loaded, switched-on ones that playback and particles use. The speaker hooks map 1:1 onto
 * the mod ({@code SpeakerBlock.useWithoutItem}, {@code SpeakerBlockEntity.refreshClock}/{@code preRemoveSideEffects},
 * {@code CasinoStation.pruneLoaded}). Thread: MAIN.
 */
final class MusicWorld {

    /** {@code CasinoSoundMod.onLevelTick}: prune every 100 ticks; the speakers refresh their clock as often. */
    static final int HOUSEKEEPING_TICKS = 100;

    private final MusicStation station;
    private final SpeakerGrid grid = new SpeakerGrid();

    MusicWorld(MusicStation station) {
        this.station = station;
    }

    MusicStation station() {
        return station;
    }

    SpeakerGrid grid() {
        return grid;
    }

    /** Switched on (toggle, or placed switched on): the station starts a playlist if it is idle. */
    void enable(MusicStation.Cell pos, Facing facing, long gameTime, LongSupplier randomSeed) {
        station.enable(pos, gameTime, randomSeed);
        grid.put(pos, facing);
    }

    /** Switched off or removed: the last one out makes the station idle. */
    void disable(MusicStation.Cell pos) {
        station.disable(pos);
        grid.remove(pos);
    }

    /** Chunk loaded: like the mod's clock refresh on load, an "on" speaker re-registers, an "off" one leaves. */
    void loaded(MusicStation.Cell pos, Facing facing, boolean on, long gameTime, LongSupplier randomSeed) {
        if (on) {
            enable(pos, facing, gameTime, randomSeed);
        } else {
            disable(pos);
        }
    }

    /** Chunk unloaded: no playback from there; the station keeps the speaker (it is still switched on). */
    void unloaded(MusicStation.Cell pos) {
        grid.remove(pos);
    }

    /**
     * Every {@link #HOUSEKEEPING_TICKS}: switched-on loaded speakers refresh their registration, positions in loaded
     * chunks without a switched-on speaker are pruned (two passes in a row, see {@link MusicStation#prune}).
     *
     * @param chunkLoaded true if the position's chunk is loaded
     */
    void housekeeping(long gameTime, LongSupplier randomSeed, Predicate<MusicStation.Cell> chunkLoaded) {
        for (SpeakerGrid.Speaker s : grid.all()) {
            station.enable(s.pos(), gameTime, randomSeed);
        }
        station.prune(c -> chunkLoaded.test(c) && !grid.contains(c));
    }

    /** The station as playback sees it: null while idle. */
    Playback.Station playing() {
        return station.active() ? new Playback.Station(station.epoch(), station.seed()) : null;
    }
}
