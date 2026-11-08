package de.tigges.kaboofcasino.croupier;

import de.tigges.kaboofcasino.machine.Facing;
import de.tigges.kaboofcasino.util.Pos;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Where a croupier stands: on the ground beside its counter, on the side opposite the nearest player (so it faces him
 * across the counter), on a free two-block-high spot of the counter's chunk; if no side is free, on the counter's top.
 * Pure.
 */
public final class CroupierPlacement {

    /** {@code DealerStationBlock}'s top: 11.7 px (the plugin's barrier is a full block; the croupier is a display). */
    public static final double COUNTER_TOP = 11.7 / 16.0;
    /** A feet block with a collision box up to this height (carpet, a thin snow layer) is stood on. */
    static final double LOW = 0.1875;
    /** A floor's collision top must reach this (full blocks, top slabs, stairs, paths, farmland, soul sand). */
    static final double FLOOR = 0.8;

    /** Where a croupier stands: the block of its feet, the feet's height in it, and where it looks. */
    public record Stand(Pos feet, double lift, Facing facing) {
    }

    /**
     * What placement needs to know about one block.
     *
     * @param blocked  a casino block (machine or decoration cell), another croupier's spot, or outside the world
     * @param liquid   water or lava
     * @param passable no collision at all ({@code Block#isPassable})
     * @param top      the top of its collision boxes in the block (0 if none)
     */
    public record Cell(boolean blocked, boolean liquid, boolean passable, double top) {

        public static final Cell AIR = new Cell(false, false, true, 0.0);
        public static final Cell SOLID = new Cell(false, false, false, 1.0);
        public static final Cell BLOCKED = new Cell(true, false, false, 1.0);
    }

    private CroupierPlacement() {
    }

    /**
     * The four sides of a counter (the direction from the counter to the croupier's spot), the one opposite the
     * player first, then by how far they turn away from him; ties in {@link Facing} order.
     *
     * @param towardsX,towardsZ the direction from the counter to the nearest player (0, 0: none)
     */
    static List<Facing> sides(double towardsX, double towardsZ) {
        List<Facing> out = new ArrayList<>(List.of(Facing.values()));
        out.sort(Comparator.comparingDouble(f -> f.step()[0] * towardsX + f.step()[1] * towardsZ));
        return out;
    }

    /** The croupier's spot for a new croupier at {@code counter}. */
    public static Stand choose(Pos counter, double towardsX, double towardsZ, Function<Pos, Cell> cells) {
        for (Facing side : sides(towardsX, towardsZ)) {
            Optional<Stand> s = beside(counter, side, cells);
            if (s.isPresent()) {
                return s.get();
            }
        }
        return new Stand(counter, COUNTER_TOP, towards(towardsX, towardsZ));
    }

    /** The spot on {@code side} of the counter, facing it, if free and in the counter's chunk. */
    static Optional<Stand> beside(Pos counter, Facing side, Function<Pos, Cell> cells) {
        int[] s = side.step();
        Pos feet = counter.offset(s[0], 0, s[1]);
        if (feet.chunkX() != counter.chunkX() || feet.chunkZ() != counter.chunkZ()) {
            return Optional.empty();
        }
        Optional<Double> lift = standable(feet, cells);
        return lift.map(l -> new Stand(feet, l, opposite(side)));
    }

    /**
     * The feet height if a croupier can stand in {@code feet}: the block and the one above are free (no casino block,
     * no liquid; the head block passable, the feet block passable or as low as a carpet) and something carries it.
     */
    static Optional<Double> standable(Pos feet, Function<Pos, Cell> cells) {
        Cell f = cells.apply(feet);
        Cell head = cells.apply(feet.offset(0, 1, 0));
        if (f.blocked() || f.liquid() || head.blocked() || head.liquid() || !head.passable()) {
            return Optional.empty();
        }
        if (!f.passable()) {
            return f.top() <= LOW ? Optional.of(f.top()) : Optional.empty();
        }
        Cell below = cells.apply(feet.offset(0, -1, 0));
        if (below.blocked() || below.liquid() || below.top() < FLOOR) {
            return Optional.empty();
        }
        return Optional.of(Math.min(1.0, below.top()) - 1.0);
    }

    /**
     * Where a croupier stands once its counter is gone: one that stood on the counter's top steps down into the
     * counter's block if something carries it there; one beside it stays.
     */
    static Stand afterLoss(CroupierRecord r, Function<Pos, Cell> cells) {
        if (r.counterPos() == null || !r.stand().equals(r.counterPos())) {
            return r.standing();
        }
        Cell below = cells.apply(r.stand().offset(0, -1, 0));
        if (below.blocked() || below.liquid() || below.top() < FLOOR) {
            return r.standing();
        }
        return new Stand(r.stand(), Math.min(1.0, below.top()) - 1.0, r.facing());
    }

    /**
     * How a counterless croupier attaches to a counter placed at {@code counter}: beside it (horizontally next to its
     * feet, same height, same chunk) it turns towards it; in its feet block it steps onto its top. Empty: too far.
     */
    static Optional<Stand> attach(Stand at, Pos counter) {
        Pos feet = at.feet();
        if (!feet.world().equals(counter.world()) || feet.chunkX() != counter.chunkX()
                || feet.chunkZ() != counter.chunkZ() || feet.y() != counter.y()) {
            return Optional.empty();
        }
        int dx = counter.x() - feet.x();
        int dz = counter.z() - feet.z();
        if (dx == 0 && dz == 0) {
            return Optional.of(new Stand(counter, COUNTER_TOP, at.facing()));
        }
        if (Math.abs(dx) + Math.abs(dz) != 1) {
            return Optional.empty();
        }
        return Optional.of(new Stand(feet, at.lift(), towards(dx, dz)));
    }

    /** The facing nearest to the direction (x, z); SOUTH for none. */
    static Facing towards(double x, double z) {
        if (x == 0 && z == 0) {
            return Facing.SOUTH;
        }
        if (Math.abs(x) > Math.abs(z)) {
            return x > 0 ? Facing.EAST : Facing.WEST;
        }
        return z > 0 ? Facing.SOUTH : Facing.NORTH;
    }

    static Facing opposite(Facing f) {
        return Facing.values()[(f.ordinal() + 2) % 4];
    }
}
