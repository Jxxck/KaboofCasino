package de.tigges.kaboofcasino.music;

/**
 * The music of one player who loaded the music pack (pure state machine; {@link MusicService} feeds it the distance to
 * the nearest loaded, switched-on speaker of his world and the world's station). Thread: MAIN.
 *
 * <p>The mod ({@code CasinoSoundClient}) seeks every client into the shared timeline and fades the volume every tick.
 * A vanilla client can do neither (render.md 6), so:
 * <ul>
 *   <li><b>Start</b>: entering the 48-block range, a player gets the timeline's current track from its beginning,
 *       at the mod's volume for his distance at that moment (RECORDS, non-positional: the files are stereo). Because
 *       the volume of a playing sound cannot change, the start waits while he keeps walking towards the speaker
 *       (at least 1 block per {@link #CHECK_TICKS}, at most {@link #SETTLE_MAX_TICKS}), so someone walking into the
 *       casino hears it at the level of where he stops, not at the near-silence of the range's edge. Inside the
 *       full-volume radius (12 blocks) it starts at once. Below {@link #MIN_VOLUME} (beyond ~45.4 blocks) nothing
 *       starts; that also gives the 48-block edge a hysteresis.</li>
 *   <li><b>Too quiet</b>: if a playing track is far below the level of where the player now is (target at least
 *       {@link #QUIET_TARGET} and more than {@link #QUIET_RATIO} times the playing volume), it is stopped and started
 *       again like on entering. A track that is too loud (walking away) keeps playing until the range ends.</li>
 *   <li><b>Sequence</b>: when a track ends (real time, the client plays in real time), the next track of the shared
 *       shuffled order follows at the volume for the current distance; after the sixth the order repeats (one
 *       cycle). Players who entered at different times are therefore not in sync; players in range when the
 *       station starts are.</li>
 *   <li><b>Stop</b> (stopSound of the track): leaving 48 blocks, the station going idle or restarting (new epoch or
 *       seed), world change, death, quit, music switched off.</li>
 *   <li><b>Vanilla music</b>: a category-only stopSound(MUSIC) at every check while in range, i.e. every
 *       {@link #CHECK_TICKS} (at most once per tick). The mod stops it every tick and cancels the music manager; a
 *       song that a client starts in between (e.g. with the "constant" music frequency) plays at most half a
 *       second.</li>
 * </ul>
 */
final class Playback {

    /** {@code CasinoSoundClient.RANGE}. */
    static final double RANGE = 48.0;
    static final double RANGE_SQ = RANGE * RANGE;
    /** {@code CasinoSoundClient.FULL_VOLUME_RANGE}. */
    static final double FULL_VOLUME_RANGE = 12.0;
    /** The mod's volume inside the full-volume radius. */
    static final float MAX_VOLUME = 0.7f;
    /** No track is started below this volume (inaudible anyway). */
    static final float MIN_VOLUME = 0.01f;
    /** A player is checked this often (and after every speaker change in his world). */
    static final int CHECK_TICKS = 10;
    /** Getting closer by at least this much per check means "still walking in": the start waits. */
    static final double APPROACH_BLOCKS = 1.0;
    /** The start waits at most this long for a player who keeps approaching. */
    static final int SETTLE_MAX_TICKS = 200;
    static final float QUIET_TARGET = 0.3f;
    static final float QUIET_RATIO = 3.0f;

    enum State {
        /** Out of range (or no station). */
        IDLE,
        /** In range, no track playing: waiting to settle or too far for an audible start. */
        PENDING,
        /** A track plays. */
        PLAYING
    }

    /** What a check does to the player. */
    interface Out {
        void play(int track, float volume);

        void stop(int track);

        void stopVanillaMusic();
    }

    /** The world's station while it plays (epoch >= 0 and a speaker). */
    record Station(long epoch, long seed) {
    }

    private State state = State.IDLE;
    private long epoch;
    private long seed;
    private int[] order;
    private int orderIndex;
    private int track = -1;
    private float volume;
    private long endNanos;
    private long pendingSince;
    private double lastDistance;
    private long lastDistanceTick;
    private long lastVanillaStop = Long.MIN_VALUE;

    /** {@code CasinoSoundClient}: {@code t = clamp((d - 12) / 36, 0, 1)}, {@code 0.7 * (1 - smoothstep(t))}. */
    static float volume(double distance) {
        double t = Math.clamp((distance - 12.0) / 36.0, 0.0, 1.0);
        return (float) (0.7 * (1.0 - t * t * (3.0 - 2.0 * t)));
    }

    State state() {
        return state;
    }

    int track() {
        return track;
    }

    float playingVolume() {
        return volume;
    }

    /** True if the playing track has ended (the service then checks this player at once). */
    boolean due(long nanos) {
        return state == State.PLAYING && nanos - endNanos >= 0;
    }

    /**
     * One check.
     *
     * @param tick       server tick (cadences)
     * @param nanos      {@link System#nanoTime()} (track ends)
     * @param gameTime   the world's game time (the shared timeline)
     * @param distanceSq squared distance to the nearest loaded, switched-on speaker, or infinity
     * @param station    the world's station, or null when it is idle
     */
    void check(long tick, long nanos, long gameTime, double distanceSq, Station station, Out out) {
        boolean covered = station != null && distanceSq < RANGE_SQ;
        if (!covered) {
            stop(out);
            return;
        }
        if (lastVanillaStop != tick) {
            stopVanilla(tick, out);
        }
        double distance = Math.sqrt(distanceSq);
        float target = volume(distance);
        if (state == State.PLAYING && (station.epoch() != epoch || station.seed() != seed)) {
            // the station restarted (all speakers were off, then one came on): the new playlist from its start
            out.stop(track);
            pending(tick, distance);
        }
        switch (state) {
            case IDLE -> {
                pending(tick, distance);
                if (distance <= FULL_VOLUME_RANGE) {
                    start(tick, nanos, gameTime, station, target, out);
                }
            }
            case PENDING -> pendingCheck(tick, nanos, gameTime, distance, station, target, out);
            case PLAYING -> {
                if (nanos - endNanos >= 0) {
                    next(tick, nanos, distance, target, out);
                } else if (target >= QUIET_TARGET && volume * QUIET_RATIO < target) {
                    out.stop(track);
                    pending(tick, distance);
                }
            }
        }
    }

    private void pending(long tick, double distance) {
        state = State.PENDING;
        track = -1;
        pendingSince = tick;
        lastDistance = distance;
        lastDistanceTick = tick;
    }

    private void pendingCheck(long tick, long nanos, long gameTime, double distance, Station station, float target,
                              Out out) {
        if (target < MIN_VOLUME) {
            pending(tick, distance);
            return;
        }
        if (distance <= FULL_VOLUME_RANGE) {
            start(tick, nanos, gameTime, station, target, out);
            return;
        }
        if (tick - lastDistanceTick < CHECK_TICKS) {
            return; // an extra check right after the last one says nothing about walking speed
        }
        boolean approaching = lastDistance - distance >= APPROACH_BLOCKS;
        if (!approaching || tick - pendingSince >= SETTLE_MAX_TICKS) {
            start(tick, nanos, gameTime, station, target, out);
        } else {
            lastDistance = distance;
            lastDistanceTick = tick;
        }
    }

    /** The timeline's current track from its beginning. */
    private void start(long tick, long nanos, long gameTime, Station station, float target, Out out) {
        epoch = station.epoch();
        seed = station.seed();
        order = MusicTimeline.order(seed);
        int current = MusicTimeline.at((gameTime - epoch) * 50L, seed).track();
        orderIndex = 0;
        for (int i = 0; i < order.length; i++) {
            if (order[i] == current) {
                orderIndex = i;
            }
        }
        play(nanos, target, out);
        if (lastVanillaStop != tick) {
            stopVanilla(tick, out);
        }
    }

    private void stopVanilla(long tick, Out out) {
        out.stopVanillaMusic();
        lastVanillaStop = tick;
    }

    /** The playing track ended: the next one of the shared order at the volume for the current distance. */
    private void next(long tick, long nanos, double distance, float target, Out out) {
        out.stop(track); // normally over already; never let a late tail overlap the next track
        if (target < MIN_VOLUME) {
            pending(tick, distance);
            return;
        }
        orderIndex = (orderIndex + 1) % order.length;
        play(nanos, target, out);
    }

    private void play(long nanos, float target, Out out) {
        track = order[orderIndex];
        volume = target;
        endNanos = nanos + MusicTimeline.LENGTH_MS[track] * 1_000_000L;
        state = State.PLAYING;
        out.play(track, target);
    }

    /** Stops the playing track (leaving, death, world change, music off) and forgets the range. */
    void stop(Out out) {
        if (state == State.PLAYING) {
            out.stop(track);
        }
        state = State.IDLE;
        track = -1;
    }
}
