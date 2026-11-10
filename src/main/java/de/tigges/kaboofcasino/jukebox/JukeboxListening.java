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
    }

    private UUID jukebox;
    private long serial;
    private Key playing;
    private boolean pending;
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
        out.stopVanillaMusic();
        if (!source.jukebox().equals(jukebox) || source.serial() != serial) {
            if (playing != null) {
                out.stop(playing);
                playing = null;
            }
            jukebox = source.jukebox();
            serial = source.serial();
            pending = true;
            pendingSince = tick;
            lastDistance = source.distance();
            lastDistanceTick = tick;
        }
        if (playing != null || !pending || !source.audible()) {
            return;
        }
        long remaining = source.lengthMillis() - source.elapsedMillis();
        if (source.elapsedMillis() >= FRESH_MILLIS && remaining < MIN_REMAINING_MILLIS) {
            return; // too late for this track: the next one (a new serial) starts for him
        }
        float target = volume(source.distance(), source.range());
        if (target < MIN_VOLUME) {
            return;
        }
        boolean inside = source.distance() <= source.range() * FULL_SHARE;
        if (!inside && tick - lastDistanceTick >= CHECK_TICKS) {
            boolean approaching = lastDistance - source.distance() >= APPROACH_BLOCKS;
            lastDistance = source.distance();
            lastDistanceTick = tick;
            if (approaching && tick - pendingSince < SETTLE_TICKS) {
                return;
            }
        } else if (!inside) {
            return;
        }
        playing = source.sound();
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
    }
}
