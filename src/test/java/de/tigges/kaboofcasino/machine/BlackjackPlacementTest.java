package de.tigges.kaboofcasino.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.util.Pos;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The 4 x 2 blackjack footprint ({@code BlackjackTableBlock.partPos}) and its chunk fit. */
class BlackjackPlacementTest {

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
        Pos anchor = new Pos("minecraft:overworld", 100, 64, -200);
        for (Facing f : Facing.values()) {
            List<Pos> carriers = MachineType.BLACKJACK_TABLE.carriers(anchor, f);
            assertEquals(8, carriers.size());
            for (int part = 0; part < 8; part++) {
                int[] o = modPart(f.turns(), part);
                assertEquals(anchor.offset(o[0], 0, o[1]), carriers.get(part), f + " part " + part);
            }
            assertEquals(anchor, carriers.get(5), "anchor = part 5");
        }
        assertEquals(0, MachineType.BLACKJACK_TABLE.rise());
        assertTrue(!MachineType.BLACKJACK_TABLE.fixedFacing() && !MachineType.BLACKJACK_TABLE.needsSupport());
    }

    @Test
    void everyAnchorGetsTheSmallestShiftThatFits() {
        Set<String> seen = new HashSet<>();
        for (Facing f : Facing.values()) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    Pos anchor = new Pos("minecraft:overworld", 32 + x, 64, -48 + z);
                    int[] fit = MachineType.BLACKJACK_TABLE.chunkFit(anchor, f);
                    if (fit == null) {
                        continue;
                    }
                    assertTrue(fit[0] != 0 || fit[1] != 0, "no fit at " + anchor + " " + f);
                    assertNull(MachineType.BLACKJACK_TABLE.chunkFit(anchor.offset(fit[0], 0, fit[1]), f));
                    int along = Math.abs(fit[0]) + Math.abs(fit[1]);
                    assertTrue(along <= 3, "shift " + fit[0] + "," + fit[1]);
                    seen.add(Math.abs(fit[0]) + "," + Math.abs(fit[1]));
                }
            }
        }
        // one or two blocks to the side, one block forward/back, or both
        assertTrue(seen.contains("1,0") || seen.contains("0,1"), seen.toString());
        assertTrue(seen.contains("2,0") || seen.contains("0,2"), seen.toString());
    }

    @Test
    void threeWideMachinesStillMoveOneBlockSideways() {
        for (Facing f : Facing.values()) {
            for (int x = 0; x < 16; x++) {
                Pos anchor = new Pos("minecraft:overworld", 16 + x, 64, 16 + x);
                int[] fit = MachineType.FORTUNE_WHEEL.chunkFit(anchor, f);
                if (fit != null) {
                    assertNotNull(fit);
                    assertEquals(1, Math.abs(fit[0]) + Math.abs(fit[1]));
                    int[] r = MachineType.right(f);
                    assertEquals(0, fit[0] * r[1] - fit[1] * r[0], "sideways only");
                }
            }
        }
    }
}
