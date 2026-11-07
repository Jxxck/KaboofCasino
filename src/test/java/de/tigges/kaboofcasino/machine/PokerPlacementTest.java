package de.tigges.kaboofcasino.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.util.Pos;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The 4 x 3 poker footprint ({@code PokerTableBlock.partPos}) and its chunk fit. */
class PokerPlacementTest {

    /** The mod's {@code rotatedOffset(facing, part % 4 - 1, part / 4 - 1)}, turns = {@code get2DDataValue}. */
    private static int[] modPart(int turns, int part) {
        int x = part % 4 - 1;
        int z = part / 4 - 1;
        for (int i = 0; i < turns; i++) {
            int nx = -z;
            z = x;
            x = nx;
        }
        return new int[] {x, z};
    }

    @Test
    void carriersAreTheModsPartsInPartOrder() {
        Pos anchor = new Pos("minecraft:overworld", -40, 70, 300);
        for (Facing f : Facing.values()) {
            List<Pos> carriers = MachineType.POKER_TABLE.carriers(anchor, f);
            assertEquals(12, carriers.size());
            for (int part = 0; part < 12; part++) {
                int[] o = modPart(f.turns(), part);
                assertEquals(anchor.offset(o[0], 0, o[1]), carriers.get(part), f + " part " + part);
            }
            assertEquals(anchor, carriers.get(5), "anchor = part 5");
            assertEquals(12, new HashSet<>(carriers).size());
        }
        assertEquals(0, MachineType.POKER_TABLE.rise());
        assertTrue(!MachineType.POKER_TABLE.fixedFacing() && !MachineType.POKER_TABLE.needsSupport());
    }

    @Test
    void everyAnchorGetsAShiftThatFits() {
        Set<String> seen = new HashSet<>();
        for (Facing f : Facing.values()) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    Pos anchor = new Pos("minecraft:overworld", 32 + x, 64, -48 + z);
                    int[] fit = MachineType.POKER_TABLE.chunkFit(anchor, f);
                    if (fit == null) {
                        continue;
                    }
                    assertNull(MachineType.POKER_TABLE.chunkFit(anchor.offset(fit[0], 0, fit[1]), f));
                    assertTrue(Math.abs(fit[0]) + Math.abs(fit[1]) <= 4, "shift " + fit[0] + "," + fit[1]);
                    seen.add(fit[0] + "," + fit[1]);
                }
            }
        }
        assertTrue(!seen.isEmpty());
    }
}
