package de.tigges.kaboofcasino.jukebox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;

/** The jukebox's catalog, its stored setting and what a listener hears. */
class JukeboxTest {

    static JukeboxCatalog catalog() throws IOException {
        try (InputStream in = Files.newInputStream(Path.of("pack-src/jukebox/tracks.json"))) {
            return JukeboxCatalog.parse(in);
        }
    }

    @Test
    void theCatalogHasNineGenresAndEveryVanillaTrackOnce() throws IOException {
        JukeboxCatalog c = catalog();
        assertEquals(9, c.genres().size());
        assertEquals(98, c.tracks().size());
        Set<String> ids = new HashSet<>();
        for (JukeboxCatalog.Track t : c.tracks()) {
            assertTrue(ids.add(t.id()), t.id());
            assertTrue(t.millis() > 60_000 || t.millis() > 30_000 && t.id().matches("key|oxygene|dry_hands|wet_hands|disc_11"),
                    t.id() + " " + t.millis());
            assertFalse(t.title().isBlank());
            assertTrue(t.icon().startsWith("minecraft:music_disc_"), t.icon());
            // every track has its own sound event (the jukebox's, not the lounge speakers' shared ones)
            assertEquals(Key.key("kaboofcasino", "jukebox." + t.id()), t.sound());
            assertEquals(t.genre().equals("lounge"), t.needsMusicPack(), t.id());
        }
        for (JukeboxCatalog.Genre g : c.genres()) {
            assertFalse(c.of(g.id()).isEmpty(), g.id());
            assertTrue(c.of(g.id()).size() <= JukeboxMenu.PAGE_SIZE, g.id());
        }
        assertEquals(22, c.of("discs").size());
        assertEquals(148_240, c.track("disc_pigstep").orElseThrow().millis());
    }

    @Test
    void theSettingIsStoredAndUnknownValuesFallBack() throws IOException {
        JukeboxCatalog c = catalog();
        JukeboxState s = new JukeboxState(true, "chrysopoeia", JukeboxState.Mode.SHUFFLE, 48);
        assertEquals(s, JukeboxState.decode(s.encode(), c));
        JukeboxState d = JukeboxState.decode("v=1;on=1;t=no_such_track;m=???;r=7", c);
        assertTrue(d.playing());
        assertEquals(JukeboxState.fresh(c).track(), d.track());
        assertFalse(c.track(d.track()).orElseThrow().needsMusicPack(), "a new jukebox starts with a track all hear");
        assertEquals(JukeboxState.Mode.ORDER, d.mode());
        assertEquals(JukeboxState.DEFAULT_RANGE, d.range());
        assertEquals(JukeboxState.fresh(c), JukeboxState.decode("", c));
        assertFalse(JukeboxState.fresh(c).playing());
        assertEquals(48, s.withNextRange().withNextRange().withNextRange().range(), "the ranges cycle");
        assertEquals(JukeboxState.Mode.REPEAT, s.withNextMode().mode());
    }

    @Test
    void theNextTrackFollowsTheModeWithinTheGenre() throws IOException {
        JukeboxCatalog c = catalog();
        List<JukeboxCatalog.Track> water = c.of("water");
        JukeboxState s = new JukeboxState(true, water.getLast().id(), JukeboxState.Mode.ORDER, 32);
        assertEquals(water.getFirst().id(), s.next(c, n -> 0), "after the last the first");
        JukeboxState r = new JukeboxState(true, water.get(1).id(), JukeboxState.Mode.REPEAT, 32);
        assertEquals(water.get(1).id(), r.next(c, n -> 0));
        JukeboxState sh = new JukeboxState(true, water.get(1).id(), JukeboxState.Mode.SHUFFLE, 32);
        for (int pick = 0; pick < water.size() - 1; pick++) {
            int p = pick;
            String next = sh.next(c, n -> p);
            assertNotEquals(water.get(1).id(), next, "shuffle never repeats the same track");
            assertEquals("water", c.track(next).orElseThrow().genre());
        }
        assertEquals(water.get(2).id(), r.skipped(c, n -> 0), "a skip on repeat goes on to the next track");
        assertEquals(water.getFirst().id(), s.skipped(c, n -> 0));
    }

    // ---- listening ------------------------------------------------------------------------------------------------

    static final class Out implements JukeboxListening.Out {
        final List<String> log = new ArrayList<>();

        @Override
        public void play(Key sound, float volume, String title) {
            log.add("play " + sound.value());
        }

        @Override
        public void stop(Key sound) {
            log.add("stop " + sound.value());
        }

        @Override
        public void stopVanillaMusic() {
            log.add("vanilla off");
        }

        @Override
        public void inaudible(String title) {
            log.add("hint " + title);
        }

        List<String> sounds() {
            return log.stream().filter(s -> !s.equals("vanilla off")).toList();
        }
    }

    static final UUID J = UUID.randomUUID();
    static final Key A = Key.key("kaboofcasino", "jukebox.a");
    static final Key B = Key.key("kaboofcasino", "jukebox.b");

    static JukeboxListening.Source src(long serial, Key sound, long elapsed, double distance) {
        return new JukeboxListening.Source(J, serial, sound, "T", elapsed, 200_000, distance, 32, true);
    }

    @Test
    void aListenerFollowsTheJukeboxAndJoinsOnlyWhenEnoughOfTheTrackIsLeft() {
        Out out = new Out();
        JukeboxListening l = new JukeboxListening();
        l.check(0, src(1, A, 0, 5), out);
        assertEquals(List.of("play jukebox.a"), out.sounds(), "close by: at once");
        l.check(10, src(1, A, 500, 5), out);
        assertEquals(1, out.sounds().size(), "the same track keeps playing");
        l.check(20, src(2, B, 0, 5), out);
        assertEquals(List.of("play jukebox.a", "stop jukebox.a", "play jukebox.b"), out.sounds(), "a new track for all");
        l.check(30, null, out);
        assertEquals("stop jukebox.b", out.sounds().getLast(), "out of range");
        assertNull(l.playing());
        Out late = new Out();
        JukeboxListening l2 = new JukeboxListening();
        l2.check(0, src(3, A, 190_000, 5), late);
        assertTrue(late.sounds().isEmpty(), "10 s left: wait for the next track");
        l2.check(10, src(4, B, 0, 5), late);
        assertEquals(List.of("play jukebox.b"), late.sounds());
    }

    @Test
    void theStartWaitsWhileThePlayerWalksIn() {
        Out out = new Out();
        JukeboxListening l = new JukeboxListening();
        l.check(0, src(1, A, 0, 30), out);
        l.check(10, src(1, A, 500, 27), out);
        assertTrue(out.sounds().isEmpty(), "still approaching");
        l.check(20, src(1, A, 1000, 26.8), out);
        assertEquals(List.of("play jukebox.a"), out.sounds(), "stopped walking: starts at this distance");
        l.check(30, src(2, B, 0, 25), out);
        assertEquals("play jukebox.b", out.sounds().getLast(), "he is settled: the next track starts at once");
        Out far = new Out();
        JukeboxListening edge = new JukeboxListening();
        edge.check(0, src(1, A, 0, 31.99), far);
        edge.check(10, src(1, A, 0, 31.99), far);
        assertTrue(far.sounds().isEmpty(), "inaudible at the edge");
        assertTrue(JukeboxListening.volume(5, 32) == JukeboxListening.MAX_VOLUME);
        assertTrue(JukeboxListening.volume(31.9, 32) < JukeboxListening.MIN_VOLUME);
    }

    @Test
    void aTrackThePlayerCannotHearIsNeverStarted() {
        Out out = new Out();
        JukeboxListening l = new JukeboxListening();
        JukeboxListening.Source silent = new JukeboxListening.Source(J, 1, A, "T", 0, 200_000, 3, 32, false);
        l.check(0, silent, out);
        l.check(10, silent, out);
        assertEquals(List.of("hint T"), out.log, "a lounge track without the music pack: told once, his own music on");
        l.check(20, new JukeboxListening.Source(J, 2, B, "U", 0, 200_000, 3, 32, false), out);
        assertEquals("hint U", out.log.getLast(), "told again for the next track");
    }

    @Test
    void aTrackStartedFarAwayStartsAgainWhenHeComesMuchCloser() {
        Out out = new Out();
        JukeboxListening l = new JukeboxListening();
        l.check(0, src(1, A, 0, 25), out);
        l.check(10, src(1, A, 500, 25), out);
        assertEquals(List.of("play jukebox.a"), out.sounds(), "standing at 25 blocks: quietly");
        l.check(20, src(1, A, 1000, 5), out);
        assertEquals(List.of("play jukebox.a", "stop jukebox.a", "play jukebox.a"), out.sounds(),
                "at the jukebox: again at the right level");
    }
}
