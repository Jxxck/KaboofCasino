package de.tigges.kaboofcasino.deco.full;

import de.tigges.kaboofcasino.deco.full.FullDecoStates.Pilaster;
import java.util.List;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * Pilaster connections like the mod's {@code CasinoPilasterBlock}: each horizontal side takes the band of the
 * neighbour on that side ({@code read()} / {@code updateShape()}: 1 baseboard, 2 cornice, 3 dado rail, else 0;
 * vertical neighbours do not matter). The model ({@code dark_oak_pilaster.json}) shows the north and south faces by
 * the east/west values and the east and west faces by the north/south values.
 *
 * <p>Only {@link FullDecoStates#PILASTERS} exist as reserved states. If the mod's state is one of them, it is used
 * unchanged. Otherwise the state is chosen that looks the same on the faces a player can see: a face is hidden when
 * the neighbour on that side is a full opaque block (the client culls it). Ranking: visible faces that match
 * exactly, then matching edges of visible faces, then matching sides, then table order.
 */
public final class PilasterChoice {

    private PilasterChoice() {
    }

    /** The mod's connections from the neighbours' bands. */
    public static Pilaster target(ToIntFunction<Side> bandOf) {
        return new Pilaster(bandOf.applyAsInt(Side.NORTH), bandOf.applyAsInt(Side.EAST),
                bandOf.applyAsInt(Side.SOUTH), bandOf.applyAsInt(Side.WEST));
    }

    /**
     * @param target       the mod's connections
     * @param visibleFaces the faces whose neighbour does not hide them
     * @param available    the reserved combinations, in table order
     */
    public static Pilaster choose(Pilaster target, Set<Side> visibleFaces, List<Pilaster> available) {
        Pilaster best = null;
        long bestScore = Long.MIN_VALUE;
        for (Pilaster candidate : available) {
            long score = score(target, visibleFaces, candidate);
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        if (best == null) {
            throw new IllegalArgumentException("no pilaster states");
        }
        return best;
    }

    static long score(Pilaster target, Set<Side> visibleFaces, Pilaster c) {
        // faces showing the east/west values: north and south; faces showing north/south: east and west
        int ewFaces = (visibleFaces.contains(Side.NORTH) ? 1 : 0) + (visibleFaces.contains(Side.SOUTH) ? 1 : 0);
        int nsFaces = (visibleFaces.contains(Side.EAST) ? 1 : 0) + (visibleFaces.contains(Side.WEST) ? 1 : 0);
        int e = eq(target.east(), c.east());
        int w = eq(target.west(), c.west());
        int n = eq(target.north(), c.north());
        int s = eq(target.south(), c.south());
        int exactFaces = ewFaces * (e & w) + nsFaces * (n & s);
        int edges = ewFaces * (e + w) + nsFaces * (n + s);
        int sides = n + e + s + w;
        return exactFaces * 10_000L + edges * 100L + sides;
    }

    private static int eq(int a, int b) {
        return a == b ? 1 : 0;
    }
}
