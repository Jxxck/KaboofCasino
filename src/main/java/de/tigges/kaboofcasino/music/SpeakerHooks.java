package de.tigges.kaboofcasino.music;

import de.tigges.kaboofcasino.machine.Facing;
import de.tigges.kaboofcasino.util.Pos;

/**
 * What the speaker block tells the music service ({@code casinosound}: {@code SpeakerBlock}, {@code CasinoStation}).
 * The station of a world knows every switched-on speaker, also in unloaded chunks ({@link #enable}/{@link #disable},
 * as the mod's SavedData); the playback only uses loaded ones ({@link #loaded}/{@link #unloaded}). Thread: MAIN.
 */
public interface SpeakerHooks {

    /** A speaker was switched on (the station starts a new playlist if it had none). */
    void enable(Pos pos, Facing facing);

    /** A speaker was switched off or removed (the station resets when the last one goes). */
    void disable(Pos pos);

    /** A speaker's chunk loaded (or it was placed): {@code on} = switched on. */
    void loaded(Pos pos, Facing facing, boolean on);

    /** A speaker's chunk unloaded. */
    void unloaded(Pos pos);

    /** No music (tests, music module off). */
    SpeakerHooks NONE = new SpeakerHooks() {
        @Override
        public void enable(Pos pos, Facing facing) {
        }

        @Override
        public void disable(Pos pos) {
        }

        @Override
        public void loaded(Pos pos, Facing facing, boolean on) {
        }

        @Override
        public void unloaded(Pos pos) {
        }
    };
}
