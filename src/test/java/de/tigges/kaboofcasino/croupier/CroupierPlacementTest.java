package de.tigges.kaboofcasino.croupier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.croupier.CroupierPlacement.Cell;
import de.tigges.kaboofcasino.croupier.CroupierPlacement.Stand;
import de.tigges.kaboofcasino.machine.Facing;
import de.tigges.kaboofcasino.util.Pos;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** Where the croupier stands: opposite the player, on a free two-high spot of the counter's chunk, else on its top. */
class CroupierPlacementTest {

    private static final String W = "minecraft:overworld";
    /** A counter in the middle of its chunk (chunk 0, 0). */
    private static final Pos COUNTER = new Pos(W, 8, 64, 8);

    /** A flat floor at y 63 (solid), air above, plus the given exceptions. */
    private static Function<Pos, Cell> floor(Map<Pos, Cell> except) {
        return p -> {
            Cell c = except.get(p);
            if (c != null) {
                return c;
            }
            if (p.equals(COUNTER)) {
                return Cell.BLOCKED; // the counter's own barrier is a casino cell
            }
            return p.y() <= 63 ? Cell.SOLID : Cell.AIR;
        };
    }

    @Test
    void sidesStartOppositeThePlayer() {
        // player south (+z): the croupier goes north and looks south across the counter
        assertEquals(Facing.NORTH, CroupierPlacement.sides(0.2, 3.0).get(0));
        assertEquals(Facing.SOUTH, CroupierPlacement.sides(0.2, 3.0).get(3));
        assertEquals(Facing.WEST, CroupierPlacement.sides(4.0, -1.0).get(0));
        assertEquals(Facing.EAST, CroupierPlacement.sides(-4.0, 1.0).get(0));
        assertEquals(Facing.SOUTH, CroupierPlacement.sides(1.0, -4.0).get(0));
        // the perpendicular sides next, the one turning less towards the player first
        assertEquals(List.of(Facing.NORTH, Facing.WEST, Facing.EAST, Facing.SOUTH), CroupierPlacement.sides(0.5, 3.0));
        // no player: Facing order
        assertEquals(List.of(Facing.values()), CroupierPlacement.sides(0, 0));
    }

    @Test
    void theCroupierStandsBehindTheCounterFacingIt() {
        Stand s = CroupierPlacement.choose(COUNTER, 0.0, 3.0, floor(Map.of()));
        assertEquals(new Pos(W, 8, 64, 7), s.feet());
        assertEquals(Facing.SOUTH, s.facing());
        assertEquals(0.0, s.lift(), 1e-12);
        Stand east = CroupierPlacement.choose(COUNTER, -2.0, 0.5, floor(Map.of()));
        assertEquals(new Pos(W, 9, 64, 8), east.feet());
        assertEquals(Facing.WEST, east.facing());
    }

    @Test
    void aBlockedSideIsSkipped() {
        Pos north = new Pos(W, 8, 64, 7);
        Map<Pos, Cell> wall = new HashMap<>();
        wall.put(north, Cell.SOLID);
        Stand s = CroupierPlacement.choose(COUNTER, 0.5, 3.0, floor(wall));
        assertEquals(new Pos(W, 7, 64, 8), s.feet()); // west: turns less towards the player than east
        assertEquals(Facing.EAST, s.facing());
        // head room: a block at head height also blocks, as does a casino cell, a liquid or another croupier
        for (Map.Entry<Pos, Cell> blocker : List.of(Map.entry(north.offset(0, 1, 0), Cell.SOLID),
                Map.entry(north, Cell.BLOCKED), Map.entry(north.offset(0, 1, 0), Cell.BLOCKED),
                Map.entry(north, new Cell(false, true, true, 0.0)),
                Map.entry(north.offset(0, 1, 0), new Cell(false, true, true, 0.0)),
                Map.entry(north.offset(0, -1, 0), Cell.AIR), Map.entry(north.offset(0, -1, 0), Cell.BLOCKED),
                Map.entry(north.offset(0, -1, 0), new Cell(false, false, false, 0.5)),
                Map.entry(north.offset(0, -1, 0), new Cell(false, true, false, 1.0)),
                Map.entry(north, new Cell(false, false, false, 0.5)))) {
            Stand t = CroupierPlacement.choose(COUNTER, 0.0, 3.0, floor(Map.of(blocker.getKey(), blocker.getValue())));
            assertEquals(new Pos(W, 7, 64, 8), t.feet(), blocker.toString());
        }
    }

    @Test
    void carpetsAndPathsSetTheFeetHeight() {
        Pos north = new Pos(W, 8, 64, 7);
        Stand carpet = CroupierPlacement.choose(COUNTER, 0.0, 3.0,
                floor(Map.of(north, new Cell(false, false, false, 1.0 / 16))));
        assertEquals(north, carpet.feet());
        assertEquals(1.0 / 16, carpet.lift(), 1e-12);
        Stand path = CroupierPlacement.choose(COUNTER, 0.0, 3.0,
                floor(Map.of(north.offset(0, -1, 0), new Cell(false, false, false, 15.0 / 16))));
        assertEquals(north, path.feet());
        assertEquals(-1.0 / 16, path.lift(), 1e-12);
        // a fence below (1.5 high) is a floor at the block's top
        Stand fence = CroupierPlacement.choose(COUNTER, 0.0, 3.0,
                floor(Map.of(north.offset(0, -1, 0), new Cell(false, false, false, 1.5))));
        assertEquals(0.0, fence.lift(), 1e-12);
    }

    @Test
    void theSpotStaysInTheCountersChunk() {
        // counter on the chunk's north edge (z 0): the north side (z -1) lies in the next chunk
        Pos edge = new Pos(W, 3, 64, 0);
        Function<Pos, Cell> cells = p -> p.equals(edge) ? Cell.BLOCKED : p.y() <= 63 ? Cell.SOLID : Cell.AIR;
        Stand s = CroupierPlacement.choose(edge, 0.0, 3.0, cells);
        assertEquals(new Pos(W, 2, 64, 0), s.feet()); // west instead of north
        assertEquals(edge.chunkX(), s.feet().chunkX());
        assertEquals(edge.chunkZ(), s.feet().chunkZ());
        // negative coordinates: x -16 is the west edge of chunk -1
        Pos negative = new Pos(W, -16, 64, -5);
        Function<Pos, Cell> cellsNeg = p -> p.equals(negative) ? Cell.BLOCKED : p.y() <= 63 ? Cell.SOLID : Cell.AIR;
        Stand n = CroupierPlacement.choose(negative, 3.0, 0.0, cellsNeg);
        // the west side (x -17) is in chunk -2; next come south and north (equal turn, Facing order)
        assertEquals(new Pos(W, -16, 64, -4), n.feet());
        assertEquals(Facing.NORTH, n.facing());
    }

    @Test
    void withNoFreeSideItStandsOnTheCounterLookingAtThePlayer() {
        Function<Pos, Cell> walled = p -> p.equals(COUNTER) ? Cell.BLOCKED : p.y() <= 65 ? Cell.SOLID : Cell.AIR;
        Stand s = CroupierPlacement.choose(COUNTER, -0.4, 2.0, walled);
        assertEquals(COUNTER, s.feet());
        assertEquals(CroupierPlacement.COUNTER_TOP, s.lift(), 1e-12);
        assertEquals(11.7 / 16, s.lift(), 1e-12);
        assertEquals(Facing.SOUTH, s.facing());
        assertEquals(Facing.WEST, CroupierPlacement.choose(COUNTER, -3.0, 1.0, walled).facing());
        assertEquals(Facing.SOUTH, CroupierPlacement.choose(COUNTER, 0.0, 0.0, walled).facing());
    }

    private static CroupierRecord croupier(Pos stand, double lift, Facing facing, boolean counter) {
        return new CroupierRecord(UUID.randomUUID(), stand, lift, facing, counter ? UUID.randomUUID() : null,
                counter ? COUNTER : null, null, null, null, List.of(), 0, 4, 0, 0, null);
    }

    @Test
    void afterTheCounterIsLostOneOnItsTopStepsDown() {
        Stand beside = CroupierPlacement.afterLoss(croupier(new Pos(W, 8, 64, 7), 0.0, Facing.SOUTH, true),
                floor(Map.of()));
        assertEquals(new Stand(new Pos(W, 8, 64, 7), 0.0, Facing.SOUTH), beside);
        Function<Pos, Cell> gone = p -> p.y() <= 63 ? Cell.SOLID : Cell.AIR; // the barrier is air now
        Stand top = CroupierPlacement.afterLoss(croupier(COUNTER, CroupierPlacement.COUNTER_TOP, Facing.EAST, true),
                gone);
        assertEquals(new Stand(COUNTER, 0.0, Facing.EAST), top);
        // nothing below the counter (it was hanging in the air): it stays where it was
        Function<Pos, Cell> air = p -> Cell.AIR;
        Stand floating = CroupierPlacement.afterLoss(croupier(COUNTER, CroupierPlacement.COUNTER_TOP, Facing.EAST,
                true), air);
        assertEquals(CroupierPlacement.COUNTER_TOP, floating.lift(), 1e-12);
    }

    @Test
    void aCounterlessCroupierTakesACounterRightNextToIt() {
        Stand at = new Stand(new Pos(W, 8, 64, 7), 0.0, Facing.NORTH);
        // counter placed east of its feet: it turns east
        Optional<Stand> east = CroupierPlacement.attach(at, new Pos(W, 9, 64, 7));
        assertEquals(Optional.of(new Stand(new Pos(W, 8, 64, 7), 0.0, Facing.EAST)), east);
        assertEquals(Facing.SOUTH, CroupierPlacement.attach(at, new Pos(W, 8, 64, 8)).orElseThrow().facing());
        // into its feet block: it stands on the counter's top
        Optional<Stand> onTop = CroupierPlacement.attach(at, new Pos(W, 8, 64, 7));
        assertEquals(Optional.of(new Stand(new Pos(W, 8, 64, 7), CroupierPlacement.COUNTER_TOP, Facing.NORTH)),
                onTop);
        // diagonal, two blocks away, another height, another world, another chunk: no
        for (Pos p : List.of(new Pos(W, 9, 64, 8), new Pos(W, 10, 64, 7), new Pos(W, 9, 65, 7), new Pos(W, 9, 63, 7),
                new Pos("minecraft:the_nether", 9, 64, 7))) {
            assertTrue(CroupierPlacement.attach(at, p).isEmpty(), p.toString());
        }
        Stand border = new Stand(new Pos(W, 15, 64, 3), 0.0, Facing.NORTH);
        assertTrue(CroupierPlacement.attach(border, new Pos(W, 16, 64, 3)).isEmpty());
        assertTrue(CroupierPlacement.attach(border, new Pos(W, 14, 64, 3)).isPresent());
    }

    @Test
    void facingHelpers() {
        assertEquals(Facing.NORTH, CroupierPlacement.opposite(Facing.SOUTH));
        assertEquals(Facing.EAST, CroupierPlacement.opposite(Facing.WEST));
        assertEquals(Facing.SOUTH, CroupierPlacement.opposite(Facing.NORTH));
        assertEquals(Facing.WEST, CroupierPlacement.opposite(Facing.EAST));
        assertEquals(Facing.EAST, CroupierPlacement.towards(2, 1));
        assertEquals(Facing.NORTH, CroupierPlacement.towards(1, -2));
        // the side's step points from the counter to the spot; facing the counter is its opposite
        for (Facing side : Facing.values()) {
            Stand s = CroupierPlacement.beside(COUNTER, side, floor(Map.of())).orElseThrow();
            int[] step = side.step();
            assertEquals(COUNTER.offset(step[0], 0, step[1]), s.feet());
            int[] look = s.facing().step();
            assertEquals(COUNTER, s.feet().offset(look[0], 0, look[1]));
        }
    }
}
