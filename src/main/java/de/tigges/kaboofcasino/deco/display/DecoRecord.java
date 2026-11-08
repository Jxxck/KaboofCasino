package de.tigges.kaboofcasino.deco.display;

import de.tigges.kaboofcasino.machine.Facing;
import de.tigges.kaboofcasino.util.Pos;
import java.util.UUID;

/**
 * One placed decoration block (chunk PDC {@code kaboofcasino:deco} of the anchor's chunk). Multi-block types (the 3 x 2
 * sign, the chandelier) have one record for all their cells, which lie in that one chunk. {@code state} holds what the
 * mod keeps in its block state and the carrier cannot show:
 * <ul>
 *   <li>tables, bars, rope post: the connection mask (bit 0 north, 1 east, 2 south, 3 west; {@code Connections});</li>
 *   <li>sofa: {@code joins} (bits 0-3) and the {@code back} mask (bits 4-7);</li>
 *   <li>stacked decor: the count 1..4;</li>
 *   <li>chip case: bit 0 = open;</li>
 *   <li>speaker: bit 0 = on, bits 1-2 = face (0 floor, 1 wall, 2 ceiling);</li>
 *   <li>chandelier: the chain length 1..2;</li>
 *   <li>TVs: the channel 0..8 on all cells (the mod's {@code screen}; 0 = off, {@code on = screen != 0});</li>
 *   <li>slabs: the number of deco slabs in the cell, 1 or 2 (what breaking drops; never more than the carrier holds;
 *       0 = a 0.5.0 record, read from the carrier once and then stored);</li>
 *   <li>stairs and the rest: 0 (the vanilla carrier holds facing, half and shape).</li>
 * </ul>
 */
public record DecoRecord(UUID id, DecoType type, Pos anchor, Facing facing, int state, UUID placer, long placedAt) {

    public static final int VERSION = 1;

    public static final int FACE_FLOOR = 0;
    public static final int FACE_WALL = 1;
    public static final int FACE_CEILING = 2;

    /** The highest TV channel ({@code CasinoTelevisionChannels.LAST}). */
    public static final int LAST_CHANNEL = 8;

    public DecoRecord withState(int newState) {
        return new DecoRecord(id, type, anchor, facing, newState, placer, placedAt);
    }

    // ---- state accessors (pure) ------------------------------------------------------------------------------------

    public int mask() {
        return state & 15;
    }

    public int sofaJoins() {
        return state & 15;
    }

    public int sofaBack() {
        return (state >> 4) & 15;
    }

    public static int sofaState(int joins, int back) {
        return (joins & 15) | (back & 15) << 4;
    }

    public int count() {
        return Math.max(1, Math.min(4, state));
    }

    public boolean open() {
        return (state & 1) != 0;
    }

    public boolean on() {
        return (state & 1) != 0;
    }

    public int face() {
        return (state >> 1) & 3;
    }

    public static int speakerState(boolean on, int face) {
        return (on ? 1 : 0) | (face & 3) << 1;
    }

    public int chain() {
        return Math.max(1, Math.min(2, state));
    }

    /** The TV's channel 0..8 (0 = off). */
    public int channel() {
        return Math.max(0, Math.min(LAST_CHANNEL, state));
    }

    /** The TV is on ({@code on = screen != 0}): it shows a picture and emits its light. */
    public boolean tvOn() {
        return channel() != 0;
    }

    /**
     * {@code CasinoTelevisionChannels.next}: off (0) -> 3 (the info screen) -> 1 -> 2 -> 4 -> 5 -> 6 -> 7 -> 8 ->
     * off.
     */
    public static int nextChannel(int channel) {
        return switch (channel) {
            case 0 -> 3;
            case 1 -> 2;
            case 2 -> 4;
            case 3 -> 1;
            case 4 -> 5;
            case 5 -> 6;
            case 6 -> 7;
            case 7 -> 8;
            default -> 0;
        };
    }
}
