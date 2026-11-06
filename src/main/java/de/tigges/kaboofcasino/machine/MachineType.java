package de.tigges.kaboofcasino.machine;

import de.tigges.kaboofcasino.util.Pos;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The machine kinds. Each defines its carrier footprint relative to the anchor (in the SOUTH frame: x right of the
 * player who faces the machine is -x... kept simple: offsets are rotated by the facing) and the item that places it.
 * Step 1 has the slot machine only; the other six games add their constants here (render.md 2.3).
 */
public enum MachineType {
    /** 1x1x3 column: lower (anchor), upper, top (games.md 6). */
    SLOT_MACHINE("slot_machine", new int[][] {{0, 0, 0}, {0, 1, 0}, {0, 2, 0}});

    private final String id;
    private final int[][] footprint;

    MachineType(String id, int[][] footprint) {
        this.id = id;
        this.footprint = footprint;
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

    public int height() {
        int max = 0;
        for (int[] o : footprint) {
            max = Math.max(max, o[1]);
        }
        return max + 1;
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
