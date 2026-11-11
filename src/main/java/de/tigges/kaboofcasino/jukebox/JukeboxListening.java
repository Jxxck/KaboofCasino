package de.tigges.kaboofcasino.jukebox;

import java.util.UUID;
import net.kyori.adventure.key.Key;

/**
 * What one player hears of the jukeboxes (pure state machine; {@link JukeboxService} feeds it the nearest playing
 * jukebox in range every {@link #CHECK_TICKS} and right after a jukebox changed its track). Thread: MAIN.
 * <ul>
 *   <li>The jukebox is the clock: it changes the track (a click, or the track's end), and every listener switches to
 *       the new track at once ({@link Source#serial}). A vanilla client cannot seek, so a listener who comes into range
 *       hears the current track from its beginning, but only while it has just started ({@link #FRESH_MILLIS}) or
 *       still has {@link #MIN_REMAINING_MILLIS} to go; otherwise the music starts with the next track.</li>
 *   <li>The volume of a playing sound cannot change, so the start waits while the player keeps walking towards the
 *       jukebox (at least {@link #APPROACH_BLOCKS} per check, at most {@link #SETTLE_TICKS}); inside the full-volume
 *       radius it starts at once. Below {@link #MIN_VOLUME} nothing starts.</li>
 *   <li>Out of every range, the jukebox stopping or going away, death, world change, quit: the sound stops. While a
 *       jukebox covers the player, vanilla background music is stopped at every check.</li>
 * </ul>
 */
final class JukeboxListening {

    static final int CHECK_TICKS = 10;
    static final long FRESH_MILLIS = 3_000;
    static final long MIN_REMAINING_MILLIS = 20_000;
    static final double APPROACH_BLOCKS = 1.0;
    static final int SETTLE_TICKS = 200;
    static final float MAX_VOLUME = 0.8f;
    static final float MIN_VOLUME = 0.02f;
    /** The share of the range at full volume; beyond it the volume falls smoothly to 0 at the range. */
    static final double FULL_SHARE = 0.4;

    /** The nearest playing jukebox in range: who, which track, how far into it, can this player hear it. */
    record Source(UUID jukebox, long serial, Key sound, String title, long elapsedMillis, long lengthMillis,
                  double distance, int range, boolean audible) {
    }

    interface Out {
        void play(Key sound, float volume, String title);

        void stop(Key sound);

        void stopVanillaMusic();

        /** The jukebox plays a track this player cannot hear (a lounge track without the music pack). */
        void inaudible(String title);
    }

    /** A playing track far below the level of where the player now is starts again ({@code Playback}'s rule). */
    static final float QUIET_TARGET = 0.3f;
    static final float QUIET_RATIO = 3.0f;

    private UUID jukebox;
    private long serial;
    private Key playing;
    private float playingVolume;
    private boolean pending;
    /** He has started a track of this jukebox: later tracks start at once (the approach wait is for coming in). */
    private boolean settled;
    private boolean hinted;
    private long pendingSince;
    private double lastDistance;
    private long lastDistanceTick;

    /** {@code MAX_VOLUME} inside 40 % of the range, then down to 0 at the range (smoothstep). */
    static float volume(double distance, int range) {
        double full = range * FULL_SHARE;
        double t = Math.clamp((distance - full) / (range - full), 0.0, 1.0);
        return (float) (MAX_VOLUME * (1.0 - t * t * (3.0 - 2.0 * t)));
    }

    Key playing() {
        return playing;
    }

    UUID jukebox() {
        return jukebox;
    }

    /** One check; {@code source} null = no playing jukebox covers the player (or he may not hear any). */
    void check(long tick, Source source, Out out) {
        if (source == null) {
            stop(out);
            return;
        }
        boolean newJukebox = !source.jukebox().equals(jukebox);
        if (newJukebox || source.serial() != serial) {
            if (playing != null) {
                out.stop(playing);
                playing = null;
            }
            if (newJukebox) {
                settled = false;
                pendingSince = tick;
                lastDistance = source.distance();
                lastDistanceTick = tick;
            }
            jukebox = source.jukebox();
            serial = source.serial();
            pending = true;
            hinted = false;
        }
        if (!source.audible()) {
            // he keeps his own background music; tell him once per track why the jukebox is silent for him
            if (!hinted) {
                out.inaudible(source.title());
                hinted = true;
            }
            return;
        }
        out.stopVanillaMusic();
        long remaining = source.lengthMillis() - source.elapsedMillis();
        boolean joinable = source.elapsedMillis() < FRESH_MILLIS || remaining >= MIN_REMAINING_MILLIS;
        float target = volume(source.distance(), source.range());
        if (playing != null) {
            if (target < QUIET_TARGET || playingVolume * QUIET_RATIO >= target || !joinable) {
                return;
            }
            // started far away, now much closer: the track again from its beginning at the right level
            out.stop(playing);
            playing = null;
            pending = true;
        }
        if (!pending || !joinable || target < MIN_VOLUME) {
            return; // too late for this track: the next one (a new serial) starts for him
        }
        boolean inside = source.distance() <= source.range() * FULL_SHARE;
        if (!settled && !inside) {
            if (tick - lastDistanceTick < CHECK_TICKS) {
                return;
            }
            boolean approaching = lastDistance - source.distance() >= APPROACH_BLOCKS;
            lastDistance = source.distance();
            lastDistanceTick = tick;
            if (approaching && tick - pendingSince < SETTLE_TICKS) {
                return;
            }
        }
        settled = true;
        playing = source.sound();
        playingVolume = target;
        pending = false;
        out.play(source.sound(), target, source.title());
    }

    /** Stops what plays and forgets the jukebox. */
    void stop(Out out) {
        if (playing != null) {
            out.stop(playing);
        }
        playing = null;
        jukebox = null;
        pending = false;
        settled = false;
        hinted = false;
    }
}
