package de.tigges.kaboofcasino.music;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.machine.Facing;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;

/** The station semantics of {@code CasinoStation} and the speaker hooks ({@code SpeakerBlock}, {@code SpeakerBlockEntity}). */
class MusicWorldTest {

    private static final MusicStation.Cell A = new MusicStation.Cell(10, 64, 10);
    private static final MusicStation.Cell B = new MusicStation.Cell(20, 64, 10);
    private static final MusicStation.Cell C = new MusicStation.Cell(-500, 70, 900);

    private final AtomicLong seeds = new AtomicLong(1000);
    private final LongSupplier random = seeds::incrementAndGet;

    @Test
    void theFirstSpeakerStartsAPlaylistTheOthersJoinIt() {
        MusicWorld w = new MusicWorld(new MusicStation());
        assertNull(w.playing(), "idle until a speaker is on");
        w.enable(A, Facing.NORTH, 5000, random);
        assertEquals(new Playback.Station(5000, 1001), w.playing());
        w.enable(B, Facing.SOUTH, 6000, random);
        assertEquals(new Playback.Station(5000, 1001), w.playing(), "same epoch and seed for the second");
        w.enable(A, Facing.NORTH, 7000, random);
        assertEquals(new Playback.Station(5000, 1001), w.playing(), "re-enabling changes nothing");
        assertEquals(2, w.grid().size());
    }

    @Test
    void theLastOneOutMakesTheStationIdleAndTheNextStartIsNew() {
        MusicWorld w = new MusicWorld(new MusicStation());
        w.enable(A, Facing.NORTH, 5000, random);
        w.enable(B, Facing.NORTH, 5100, random);
        w.disable(A);
        assertEquals(new Playback.Station(5000, 1001), w.playing(), "one is still on");
        w.disable(B);
        assertNull(w.playing());
        assertEquals(-1, w.station().epoch());
        w.disable(B);
        assertEquals(-1, w.station().epoch(), "disabling an unknown speaker is a no-op");
        w.enable(B, Facing.NORTH, 9000, random);
        assertEquals(new Playback.Station(9000, 1002), w.playing(), "new epoch and new seed");
    }

    @Test
    void unloadedSpeakersStayInTheStationButNotInTheGrid() {
        MusicWorld w = new MusicWorld(new MusicStation());
        w.enable(A, Facing.NORTH, 5000, random);
        w.enable(C, Facing.EAST, 5000, random);
        w.unloaded(C);
        assertEquals(Set.of(A, C), w.station().speakers());
        assertFalse(w.grid().contains(C));
        w.disable(A);
        assertEquals(new Playback.Station(5000, 1001), w.playing(), "C in an unloaded chunk keeps the playlist");
        w.loaded(C, Facing.EAST, true, 8000, random);
        assertTrue(w.grid().contains(C));
        assertEquals(new Playback.Station(5000, 1001), w.playing(), "loading an on speaker keeps the playlist");
    }

    @Test
    void loadingAnOffSpeakerUnregistersIt() {
        MusicWorld w = new MusicWorld(new MusicStation());
        w.enable(A, Facing.NORTH, 5000, random);
        w.unloaded(A);
        w.loaded(A, Facing.NORTH, false, 6000, random); // switched off while we did not see it (refreshClock)
        assertNull(w.playing());
        assertTrue(w.station().speakers().isEmpty());
    }

    @Test
    void loadingAnOnSpeakerIntoAnIdleStationStartsAPlaylist() {
        MusicWorld w = new MusicWorld(new MusicStation());
        w.loaded(A, Facing.WEST, true, 7000, random);
        assertEquals(new Playback.Station(7000, 1001), w.playing());
    }

    @Test
    void pruneNeedsTwoPassesAndOnlyTouchesLoadedChunks() {
        MusicWorld w = new MusicWorld(new MusicStation(5000, 77, List.of(A, B, C)));
        w.loaded(A, Facing.NORTH, true, 6000, random);
        // B: chunk loaded, no speaker reported; C: chunk not loaded
        w.housekeeping(6000, random, c -> !c.equals(C));
        assertEquals(Set.of(A, B, C), w.station().speakers(), "first pass only suspects B");
        w.housekeeping(6100, random, c -> !c.equals(C));
        assertEquals(Set.of(A, C), w.station().speakers(), "second pass removes B");
        assertEquals(new Playback.Station(5000, 77), w.playing());
        w.disable(A);
        w.housekeeping(6200, random, c -> true);
        w.housekeeping(6300, random, c -> true);
        assertNull(w.playing(), "C pruned once its chunk is loaded without a speaker: idle");
    }

    @Test
    void aSpeakerReportedBetweenTwoPassesIsKept() {
        MusicWorld w = new MusicWorld(new MusicStation(5000, 77, List.of(A, B)));
        w.loaded(A, Facing.NORTH, true, 6000, random);
        w.housekeeping(6000, random, c -> true);
        w.loaded(B, Facing.NORTH, true, 6050, random); // its chunk's hook came late
        w.housekeeping(6100, random, c -> true);
        assertEquals(Set.of(A, B), w.station().speakers());
    }

    @Test
    void stationCodecRoundTrip() {
        MusicStation s = new MusicStation();
        s.enable(A, 5000, () -> -3L);
        s.enable(C, 5001, () -> 9L);
        assertTrue(s.takeDirty());
        assertFalse(s.takeDirty());
        int[] packed = s.packedSpeakers();
        assertArrayEquals(new int[] {10, 64, 10, -500, 70, 900}, packed);
        MusicStation back = new MusicStation(s.epoch(), s.seed(), MusicStation.unpack(packed));
        assertEquals(5000, back.epoch());
        assertEquals(-3L, back.seed());
        assertEquals(s.speakers(), back.speakers());
        assertTrue(MusicStation.unpack(new int[] {1, 2}).isEmpty(), "incomplete triple ignored");
        assertTrue(MusicStation.unpack(null).isEmpty());
        assertFalse(new MusicStation(-1, 0, List.of(A)).active(), "an idle epoch is not active");
    }
}
