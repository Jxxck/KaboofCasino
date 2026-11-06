package de.tigges.kaboofcasino.machine;

import de.tigges.kaboofcasino.util.Pos;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The machine kinds. Each defines its carrier footprint relative to the anchor (offsets (x, y, z) in the SOUTH
 * frame, x = the viewer's right, rotated with the facing), how far the anchor lies above the placed cell, and the
 * item that places it (render.md 2.3).
 */
public enum MachineType {
    /** 1x1x3 column: lower (anchor = the placed cell), upper, top (games.md 6). */
    SLOT_MACHINE("slot_machine", new int[][] {{0, 0, 0}, {0, 1, 0}, {0, 2, 0}}, 0, false, true),
    /** 3 wide x 3 tall x 1 deep; placed cell = part 1 (bottom middle), anchor = part 4 (centre) (games.md 8). */
    FORTUNE_WHEEL("fortune_wheel", grid3x3(), 1, false, true),
    /** Same footprint as the wheel (games.md 9). */
    PLINKO("plinko", grid3x3(), 1, false, true),
    /**
     * 2 x 2 x 1 table without facing (games.md 5): the clicked cell is part 0, parts 1..3 at +X, +Z, +X+Z whichever
     * way the player looks; the mod needs no support below.
     */
    ROULETTE_TABLE("roulette_table", new int[][] {{0, 0, 0}, {1, 0, 0}, {0, 0, 1}, {1, 0, 1}}, 0, true, false);

    private final String id;
    private final int[][] footprint;
    private final int rise;
    private final boolean fixedFacing;
    private final boolean support;

    MachineType(String id, int[][] footprint, int rise, boolean fixedFacing, boolean support) {
        this.id = id;
        this.footprint = footprint;
        this.rise = rise;
        this.fixedFacing = fixedFacing;
        this.support = support;
    }

    /** True if the machine has no facing (always the SOUTH frame, footprint not rotated). */
    public boolean fixedFacing() {
        return fixedFacing;
    }

    /** True if the bottom row needs centre support below (the mod's {@code canSurvive}). */
    public boolean needsSupport() {
        return support;
    }

    /** Parts 0..8 of the mod's 3x3 machines: column {@code part % 3} (viewer's left to right), row {@code part / 3}. */
    private static int[][] grid3x3() {
        int[][] out = new int[9][];
        for (int part = 0; part < 9; part++) {
            out[part] = new int[] {part % 3 - 1, part / 3 - 1, 0};
        }
        return out;
    }

    /** Blocks from the placed (clicked) cell up to the anchor. */
    public int rise() {
        return rise;
    }

    public String id() {
        return id;
    }

    /** Carrier positions for an anchor and facing; offsets (x, y, z) are rotated with the facing. */
    public List<Pos> carriers(Pos anchor, Facing facing) {
        List<Pos> out = new ArrayList<>(footprint.length);
        for (int[] o : footprint) {
            int x = o[0];
            int z = o[2];
            for (int i = 0; i < facing.turns(); i++) {
                int nx = -z;
                z = x;
                x = nx;
            }
            out.add(anchor.offset(x, o[1], z));
        }
        return out;
    }

    /** The viewer's right (the footprint's +x) as a block step (dx, dz) for {@code facing}. */
    public static int[] right(Facing facing) {
        int x = 1;
        int z = 0;
        for (int i = 0; i < facing.turns(); i++) {
            int nx = -z;
            z = x;
            x = nx;
        }
        return new int[] {x, z};
    }

    /**
     * Whether the footprint fits into one chunk: null if it does, otherwise the world shift {dx, dz} of one block
     * that makes it fit ({0, 0} if none does). Machines with a facing move along their width only (left/right for
     * the player in front of them); a table without facing may also move along z or diagonally.
     */
    public int[] chunkFit(Pos anchor, Facing facing) {
        if (oneChunk(carriers(anchor, facing))) {
            return null;
        }
        List<int[]> shifts = new ArrayList<>();
        if (fixedFacing) {
            for (int[] d : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
                shifts.add(d);
            }
        } else {
            int[] r = right(facing);
            shifts.add(new int[] {-r[0], -r[1]});
            shifts.add(r);
        }
        for (int[] d : shifts) {
            if (oneChunk(carriers(anchor.offset(d[0], 0, d[1]), facing))) {
                return d;
            }
        }
        return new int[] {0, 0};
    }

    private static boolean oneChunk(List<Pos> carriers) {
        Pos first = carriers.getFirst();
        for (Pos p : carriers) {
            if (p.chunkX() != first.chunkX() || p.chunkZ() != first.chunkZ()) {
                return false;
            }
        }
        return true;
    }

    /** Lowest carrier row relative to the anchor (needs centre support below). */
    public int bottom() {
        int min = Integer.MAX_VALUE;
        for (int[] o : footprint) {
            min = Math.min(min, o[1]);
        }
        return min;
    }

    public static Optional<MachineType> byId(String id) {
        for (MachineType t : values()) {
            if (t.id.equals(id)) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }
}
