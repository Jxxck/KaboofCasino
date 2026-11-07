package de.tigges.kaboofcasino.music;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Volume curve and the start/next/stop rules of one listener (games.md 15, render.md 6). */
class PlaybackTest {

    private static final long EPOCH = 33_000_000L;
    private static final long SEED = 42L;
    private static final Playback.Station STATION = new Playback.Station(EPOCH, SEED);
    private static final long SECOND = 1_000_000_000L;

    /** Records the calls as text. */
    private static final class Log implements Playback.Out {
        final List<String> events = new ArrayList<>();

        @Override
        public void play(int track, float volume) {
            events.add("play " + track + " " + Math.round(volume * 1000));
        }

        @Override
        public void stop(int track) {
            events.add("stop " + track);
        }

        @Override
        public void stopVanillaMusic() {
            events.add("vanilla");
        }

        List<String> take() {
            List<String> out = List.copyOf(events);
            events.clear();
            return out;
        }
    }

    private static long gameTime(long tick) {
        return EPOCH + 5000 + tick;
    }

    private static int current(long tick) {
        return MusicTimeline.at((gameTime(tick) - EPOCH) * 50L, SEED).track();
    }

    private static String play(int track, double distance) {
        return "play " + track + " " + Math.round(Playback.volume(distance) * 1000);
    }

    private static void check(Playback p, long tick, double distance, Log log) {
        p.check(tick, tick * 50_000_000L, gameTime(tick), distance * distance, STATION, log);
    }

    @Test
    void volumeCurveIsTheMods() {
        assertEquals(0.7f, Playback.volume(0), 1e-6);
        assertEquals(0.7f, Playback.volume(12), 1e-6);
        assertEquals(0.35f, Playback.volume(30), 1e-6);
        assertEquals(0.590625f, Playback.volume(21), 1e-6); // t = 0.25: 0.7 * (1 - 0.0625 * 2.5)
        assertEquals(0.0f, Playback.volume(48), 1e-6);
        assertEquals(0.0f, Playback.volume(100), 1e-6);
        float last = 1;
        for (double d = 0; d <= 60; d += 0.05) {
            float v = Playback.volume(d);
            assertTrue(v <= last + 1e-7, "never louder further away: " + d);
            assertTrue(v >= 0 && v <= 0.7f + 1e-6);
            last = v;
        }
        // where an audible start begins (MIN_VOLUME): about 45.4 blocks
        assertTrue(Playback.volume(45.4) >= Playback.MIN_VOLUME);
        assertTrue(Playback.volume(45.5) < Playback.MIN_VOLUME);
    }

    @Test
    void rangeIsStrictlyBelow48() {
        Playback p = new Playback();
        Log log = new Log();
        p.check(0, 0, gameTime(0), 48.0 * 48.0, STATION, log);
        assertEquals(List.of(), log.take());
        assertEquals(Playback.State.IDLE, p.state());
        p.check(10, 0, gameTime(10), 47.99 * 47.99, STATION, log);
        assertEquals(List.of("vanilla"), log.take(), "covered: vanilla music stops, too quiet to start a track");
        assertEquals(Playback.State.PENDING, p.state());
    }

    @Test
    void insideTwelveBlocksTheCurrentTrackStartsAtOnceAtFullVolume() {
        Playback p = new Playback();
        Log log = new Log();
        check(p, 0, 5, log);
        assertEquals(List.of("vanilla", "play " + current(0) + " 700"), log.take(), "one vanilla stop per tick");
        assertEquals(Playback.State.PLAYING, p.state());
        assertEquals(current(0), p.track());
    }

    @Test
    void aStandingPlayerStartsAtTheNextCheckWithTheVolumeOfHisDistance() {
        Playback p = new Playback();
        Log log = new Log();
        check(p, 0, 20, log);
        assertEquals(List.of("vanilla"), log.take());
        check(p, 10, 20, log);
        assertEquals(List.of(play(current(10), 20), "vanilla"), log.take());
    }

    @Test
    void walkingInStartsWhereThePlayerStops() {
        Playback p = new Playback();
        Log log = new Log();
        double[] path = {46, 42, 38, 34, 30, 26, 22, 18, 15};
        long tick = 0;
        for (double d : path) {
            check(p, tick, d, log);
            tick += 10;
        }
        assertEquals(List.of("vanilla"), log.take(), "nothing plays while he walks in");
        check(p, tick, 15, log);
        assertEquals(List.of(play(current(tick), 15), "vanilla"), log.take());
    }

    @Test
    void walkingInToTwelveBlocksStartsThere() {
        Playback p = new Playback();
        Log log = new Log();
        check(p, 0, 40, log);
        check(p, 10, 30, log);
        check(p, 20, 20, log);
        log.take();
        check(p, 30, 11.5, log);
        assertEquals(List.of("play " + current(30) + " 700", "vanilla"), log.take());
    }

    @Test
    void theStartWaitsAtMostTenSeconds() {
        Playback p = new Playback();
        Log log = new Log();
        double d = 40;
        long tick = 0;
        while (tick < Playback.SETTLE_MAX_TICKS) {
            check(p, tick, d, log);
            d -= 1.05; // slow but steady approach that does not reach 12 blocks within 10 s (40 -> 19)
            tick += 10;
        }
        assertTrue(log.take().stream().noneMatch(e -> e.startsWith("play")));
        check(p, tick, d, log);
        assertTrue(log.take().stream().anyMatch(e -> e.startsWith("play ")), "started after 10 s of approach");
    }

    @Test
    void anExtraCheckRightAfterEnteringSaysNothingAboutSpeed() {
        Playback p = new Playback();
        Log log = new Log();
        check(p, 0, 30, log);
        check(p, 1, 29.8, log); // a speaker changed in his world: off-cadence check one tick later
        assertEquals(List.of("vanilla"), log.take());
        check(p, 10, 28, log); // still walking in
        assertEquals(List.of(), log.take());
        check(p, 20, 28, log);
        assertEquals(List.of(play(current(20), 28), "vanilla"), log.take());
    }

    @Test
    void leavingTheRangeStopsTheTrack() {
        Playback p = new Playback();
        Log log = new Log();
        check(p, 0, 5, log);
        int track = p.track();
        log.take();
        check(p, 10, 30, log);
        assertEquals(List.of(), log.take(), "too loud now, but it keeps playing");
        check(p, 20, 48.5, log);
        assertEquals(List.of("stop " + track), log.take());
        assertEquals(Playback.State.IDLE, p.state());
        check(p, 30, 47, log);
        assertEquals(List.of("vanilla"), log.take(), "re-entering stops vanilla music at once again");
    }

    @Test
    void theNextTrackFollowsTheSharedOrderWhenTheTrackEnds() {
        Playback p = new Playback();
        Log log = new Log();
        long nanos = 0;
        p.check(0, nanos, gameTime(0), 25.0, STATION, log); // 5 blocks
        int[] order = MusicTimeline.order(SEED);
        int first = p.track();
        int index = indexOf(order, first);
        log.take();
        long end = MusicTimeline.LENGTH_MS[first] * 1_000_000L;
        assertTrue(!p.due(end - 1));
        assertTrue(p.due(end));
        p.check(10, end, gameTime(10), 20.0 * 20.0, STATION, log);
        int second = order[(index + 1) % 6];
        assertEquals(List.of("stop " + first, play(second, 20)), log.take(), "volume of the current distance");
        // walk the whole cycle: after the sixth the order repeats
        long t = end;
        int expected = index + 1;
        for (int i = 0; i < 6; i++) {
            t += MusicTimeline.LENGTH_MS[order[expected % 6]] * 1_000_000L;
            expected++;
            p.check(20 + i, t, gameTime(20 + i), 25.0, STATION, log);
            assertEquals(order[expected % 6], p.track());
        }
    }

    @Test
    void aTrackThatEndsBeyondTheAudibleRadiusIsNotFollowedUntilHeComesBack() {
        Playback p = new Playback();
        Log log = new Log();
        p.check(0, 0, gameTime(0), 25.0, STATION, log);
        int first = p.track();
        log.take();
        long end = MusicTimeline.LENGTH_MS[first] * 1_000_000L;
        p.check(10, end, gameTime(10), 46.0 * 46.0, STATION, log);
        assertEquals(List.of("stop " + first), log.take());
        assertEquals(Playback.State.PENDING, p.state());
    }

    @Test
    void aTrackFarTooQuietForWhereHeIsNowStartsAgain() {
        Playback p = new Playback();
        Log log = new Log();
        check(p, 0, 45, log);
        check(p, 10, 45, log);
        int track = p.track();
        assertEquals(List.of("vanilla", play(current(10), 45), "vanilla"), log.take());
        check(p, 20, 30, log); // 0.35 vs 0.014 playing
        assertEquals(List.of("stop " + track), log.take());
        check(p, 30, 30, log);
        assertEquals(List.of(play(current(30), 30), "vanilla"), log.take());
    }

    @Test
    void notTooQuietKeepsPlaying() {
        Playback p = new Playback();
        Log log = new Log();
        check(p, 0, 30, log);
        check(p, 10, 30, log);
        log.take();
        check(p, 20, 5, log); // 0.7 < 3 x 0.35
        assertEquals(List.of(), log.take());
        assertEquals(0.35f, p.playingVolume(), 1e-6);
    }

    @Test
    void noAudibleStartBeyondTheMinimumVolume() {
        Playback p = new Playback();
        Log log = new Log();
        for (long tick = 0; tick <= 400; tick += 10) {
            check(p, tick, 46, log);
        }
        assertEquals(List.of("vanilla", "vanilla", "vanilla"), log.take(), "vanilla music every 10 s, no track");
    }

    @Test
    void vanillaMusicIsStoppedEveryTenSecondsInRange() {
        Playback p = new Playback();
        Log log = new Log();
        List<Long> stops = new ArrayList<>();
        for (long tick = 0; tick <= 600; tick += 10) {
            check(p, tick, 8, log);
            for (String e : log.take()) {
                if (e.equals("vanilla")) {
                    stops.add(tick);
                }
            }
        }
        assertEquals(List.of(0L, 200L, 400L, 600L), stops.stream().distinct().toList());
    }

    @Test
    void aRestartedStationStartsItsNewPlaylist() {
        Playback p = new Playback();
        Log log = new Log();
        check(p, 0, 5, log);
        int old = p.track();
        log.take();
        Playback.Station fresh = new Playback.Station(gameTime(10), 99L);
        p.check(10, 0, gameTime(10), 25.0, fresh, log);
        assertEquals(List.of("stop " + old, "play " + MusicTimeline.order(99L)[0] + " 700", "vanilla"), log.take(),
                "the new playlist from its first track");
        p.check(20, 0, gameTime(20), 25.0, null, log);
        assertEquals(List.of("stop " + MusicTimeline.order(99L)[0]), log.take(), "idle station: silence");
    }

    @Test
    void externalStopsSendOnlyWhatIsNeeded() {
        Playback p = new Playback();
        Log log = new Log();
        p.stop(log);
        assertEquals(List.of(), log.take());
        check(p, 0, 20, log);
        log.take();
        p.stop(log);
        assertEquals(List.of(), log.take(), "pending: nothing plays");
        check(p, 10, 5, log);
        int track = p.track();
        log.take();
        p.stop(log);
        assertEquals(List.of("stop " + track), log.take());
        assertEquals(Playback.State.IDLE, p.state());
    }

    private static int indexOf(int[] a, int v) {
        for (int i = 0; i < a.length; i++) {
            if (a[i] == v) {
                return i;
            }
        }
        throw new AssertionError(v);
    }
}
