package de.tigges.kaboofcasino.deco.full;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.deco.full.FullDecoStates.Pilaster;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Pilaster connections against the mod's CasinoPilasterBlock / dark_oak_pilaster.json. */
class PilasterChoiceTest {

    private static final List<Pilaster> AVAILABLE = FullDecoStates.PILASTERS;
    private static final Set<Side> ALL = EnumSet.allOf(Side.class);

    /** The mod's read(): every horizontal side takes CasinoFloorsAndWalls.band(neighbour), vertical ones nothing. */
    private static Pilaster modRead(Map<Side, DecoBlock> neighbours) {
        return new Pilaster(band(neighbours.get(Side.NORTH)), band(neighbours.get(Side.EAST)),
                band(neighbours.get(Side.SOUTH)), band(neighbours.get(Side.WEST)));
    }

    private static int band(DecoBlock b) {
        if (b == null) {
            return 0;
        }
        return switch (b) {
            case DARK_OAK_BASEBOARD, CLASSIC_DARK_OAK_BASEBOARD -> 1;
            case DARK_OAK_CORNICE, CLASSIC_DARK_OAK_CORNICE -> 2;
            case DARK_OAK_DADO_RAIL, CLASSIC_DARK_OAK_DADO_RAIL -> 3;
            default -> 0;
        };
    }

    /** The mod model: north face = east/west values, south = west/east, east = south/north, west = north/south. */
    private static String face(Pilaster p, Side face) {
        return switch (face) {
            case NORTH -> "north_" + p.east() + "_" + p.west();
            case SOUTH -> "south_" + p.west() + "_" + p.east();
            case EAST -> "east_" + p.south() + "_" + p.north();
            case WEST -> "west_" + p.north() + "_" + p.south();
        };
    }

    @Test
    void targetIsTheModsRead() {
        DecoBlock[] all = DecoBlock.values();
        for (int i = 0; i < all.length; i++) {
            Map<Side, DecoBlock> n = new EnumMap<>(Side.class);
            n.put(Side.NORTH, all[i]);
            n.put(Side.EAST, all[(i + 5) % all.length]);
            n.put(Side.SOUTH, all[(i + 11) % all.length]);
            n.put(Side.WEST, all[(i + 17) % all.length]);
            Pilaster target = PilasterChoice.target(side -> n.get(side).band());
            assertEquals(modRead(n), target);
        }
        assertEquals(new Pilaster(0, 1, 0, 3), PilasterChoice.target(side -> switch (side) {
            case EAST -> DecoBlock.CLASSIC_DARK_OAK_BASEBOARD.band();
            case WEST -> DecoBlock.DARK_OAK_DADO_RAIL.band();
            default -> DecoBlock.IVORY_MARBLE.band();
        }));
    }

    /** A reserved combination is used unchanged, whatever is visible. */
    @Test
    void reservedTargetsAreExact() {
        for (Pilaster p : AVAILABLE) {
            for (int vis = 0; vis < 16; vis++) {
                assertEquals(p, PilasterChoice.choose(p, visible(vis), AVAILABLE));
            }
        }
    }

    /**
     * Every one of the 256 mod states under every visibility: the choice is reserved, and if some reserved state
     * shows all visible faces exactly like the mod, the choice does too (hidden faces are don't-care).
     */
    @Test
    void visibleFacesAreRightWheneverPossible() {
        int exact = 0;
        for (int code = 0; code < 256; code++) {
            Pilaster target = new Pilaster(code & 3, (code >> 2) & 3, (code >> 4) & 3, (code >> 6) & 3);
            for (int vis = 0; vis < 16; vis++) {
                Set<Side> visible = visible(vis);
                Pilaster chosen = PilasterChoice.choose(target, visible, AVAILABLE);
                assertTrue(AVAILABLE.contains(chosen));
                boolean possible = AVAILABLE.stream().anyMatch(c -> sameVisible(target, c, visible));
                if (possible) {
                    assertTrue(sameVisible(target, chosen, visible), target + " " + visible + " -> " + chosen);
                    exact++;
                }
                int best = AVAILABLE.stream().mapToInt(c -> matchingFaces(target, c, visible)).max().orElseThrow();
                assertEquals(best, matchingFaces(target, chosen, visible), target + " " + visible);
            }
        }
        assertTrue(exact > 1000, "only " + exact);
    }

    @Test
    void wallsTeesEndsAndCorners() {
        // straight wall along x at baseboard height, room to the north, wall blocks east and west (hidden faces)
        Set<Side> northOpen = EnumSet.of(Side.NORTH);
        assertEquals(new Pilaster(0, 1, 0, 1), PilasterChoice.choose(new Pilaster(0, 1, 0, 1), northOpen, AVAILABLE));
        // T junction: a dado-rail wall also runs south; the east/west faces are hidden, the north one shows 3|3
        assertEquals(new Pilaster(0, 3, 0, 3), PilasterChoice.choose(new Pilaster(0, 3, 3, 3), northOpen, AVAILABLE));
        // wall end: cornice only on the east, west open
        assertEquals(new Pilaster(0, 2, 0, 0), PilasterChoice.choose(new Pilaster(0, 2, 0, 0),
                EnumSet.of(Side.NORTH, Side.WEST, Side.SOUTH), AVAILABLE));
        // outer corners at baseboard height are exact (all four orientations)
        for (Pilaster c : FullDecoStates.CORNERS) {
            assertEquals(c, PilasterChoice.choose(c, ALL, AVAILABLE));
        }
        // a cornice corner (walls east and south) is not reserved: only one of the two visible faces is exact
        Pilaster corner = PilasterChoice.choose(new Pilaster(0, 2, 2, 0), EnumSet.of(Side.NORTH, Side.WEST),
                AVAILABLE);
        assertEquals(new Pilaster(0, 2, 0, 0), corner);
        assertEquals(face(new Pilaster(0, 2, 2, 0), Side.NORTH), face(corner, Side.NORTH));
        // free-standing, nothing around
        assertEquals(new Pilaster(0, 0, 0, 0), PilasterChoice.choose(new Pilaster(0, 0, 0, 0), ALL, AVAILABLE));
    }

    private static Set<Side> visible(int bits) {
        Set<Side> s = EnumSet.noneOf(Side.class);
        for (Side side : Side.values()) {
            if ((bits & (1 << side.ordinal())) != 0) {
                s.add(side);
            }
        }
        return s;
    }

    private static boolean sameVisible(Pilaster a, Pilaster b, Set<Side> visible) {
        return visible.stream().allMatch(f -> face(a, f).equals(face(b, f)));
    }

    private static int matchingFaces(Pilaster a, Pilaster b, Set<Side> visible) {
        return (int) visible.stream().filter(f -> face(a, f).equals(face(b, f))).count();
    }
}
