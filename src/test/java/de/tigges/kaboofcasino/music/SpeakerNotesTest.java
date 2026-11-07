package de.tigges.kaboofcasino.music;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.machine.Facing;
import org.junit.jupiter.api.Test;

/** The mod's note particles: stagger, position, colour; and the music's sound keys. */
class SpeakerNotesTest {

    @Test
    void blockPosPackingIsVanillas() {
        // BlockPos.asLong of 26.3: x << 38 | z << 12 | y (26/26/12 bits)
        assertEquals(0L, SpeakerNotes.asLong(0, 0, 0));
        assertEquals(1L << 38, SpeakerNotes.asLong(1, 0, 0));
        assertEquals(1L << 12, SpeakerNotes.asLong(0, 0, 1));
        assertEquals(1L, SpeakerNotes.asLong(0, 1, 0));
        assertEquals(-1L, SpeakerNotes.asLong(-1, -1, -1));
        assertEquals(0xFFFL, SpeakerNotes.asLong(0, -1, 0));
    }

    @Test
    void oneNoteEvery32TicksStaggeredByPosition() {
        MusicStation.Cell pos = new MusicStation.Cell(123, 70, -45);
        int count = 0;
        long first = -1;
        for (long t = 1_000_000; t < 1_000_000 + 320; t++) {
            if (SpeakerNotes.due(t, pos)) {
                count++;
                if (first < 0) {
                    first = t;
                }
            }
        }
        assertEquals(10, count);
        assertEquals(0, Math.floorMod(first + SpeakerNotes.asLong(123, 70, -45), 32));
        MusicStation.Cell next = new MusicStation.Cell(123, 71, -45);
        assertTrue(SpeakerNotes.due(first - 1, next), "the block above is one tick earlier");
    }

    @Test
    void positionIsCentrePlusFacingTimes034AndUp012() {
        MusicStation.Cell pos = new MusicStation.Cell(10, 64, -3);
        // block -3 has its centre at -2.5
        assertArrayEquals(new double[] {10.5, 64.62, -2.5 - 0.34}, SpeakerNotes.position(pos, Facing.NORTH), 1e-9);
        assertArrayEquals(new double[] {10.5, 64.62, -2.5 + 0.34}, SpeakerNotes.position(pos, Facing.SOUTH), 1e-9);
        assertArrayEquals(new double[] {10.5 + 0.34, 64.62, -2.5}, SpeakerNotes.position(pos, Facing.EAST), 1e-9);
        assertArrayEquals(new double[] {10.5 - 0.34, 64.62, -2.5}, SpeakerNotes.position(pos, Facing.WEST), 1e-9);
    }

    @Test
    void colourIsOneOf24() {
        assertEquals(0.0, SpeakerNotes.colour(0));
        assertEquals(23 / 24.0, SpeakerNotes.colour(23));
        assertEquals(32.0, SpeakerNotes.RANGE);
    }

    @Test
    void soundKeysAreTheModsEvents() {
        assertEquals(6, MusicService.TRACK_KEYS.length);
        assertEquals("casinosound:airport_lounge", MusicService.TRACK_KEYS[0].asString());
        assertEquals("casinosound:bossa_antigua", MusicService.TRACK_KEYS[5].asString());
    }
}
