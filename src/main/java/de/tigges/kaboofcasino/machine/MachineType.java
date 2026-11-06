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
    SLOT_MACHINE("slot_machine", new int[][] {{0, 0, 0}, {0, 1, 0}, {0, 2, 0}}, 0),
    /** 3 wide x 3 tall x 1 deep; placed cell = part 1 (bottom middle), anchor = part 4 (centre) (games.md 8). */
    FORTUNE_WHEEL("fortune_wheel", grid3x3(), 1),
    /** Same footprint as the wheel (games.md 9). */
    PLINKO("plinko", grid3x3(), 1);

    private final String id;
    private final int[][] footprint;
    private final int rise;

    MachineType(String id, int[][] footprint, int rise) {
        this.id = id;
        this.footprint = footprint;
        this.rise = rise;
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
