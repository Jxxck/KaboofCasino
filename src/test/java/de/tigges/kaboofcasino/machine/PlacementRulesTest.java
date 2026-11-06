package de.tigges.kaboofcasino.machine;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.util.Pos;
import java.util.List;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

/** Safety 1 and 2: 3x3 machines stay inside one chunk; other players keep two blocks away while placing. */
class PlacementRulesTest {

    @Test
    void theShiftNamesTheSideThatFits() {
        for (MachineType type : List.of(MachineType.FORTUNE_WHEEL, MachineType.PLINKO)) {
            for (Facing f : Facing.values()) {
                int[] r = MachineType.right(f);
                assertNull(type.chunkFit(new Pos("w", 8, 64, 8), f), "inside a chunk");
                // anchor on the chunk's first column along the row: the left part sticks into the previous chunk
                Pos first = new Pos("w", r[0] != 0 ? (r[0] > 0 ? 16 : 15) : 8, 64, r[1] != 0 ? (r[1] > 0 ? 16 : 15)
                        : 8);
                assertArrayEquals(r, type.chunkFit(first, f), f + " " + first);
                assertNull(type.chunkFit(first.offset(r[0], 0, r[1]), f), "one block to the right fits");
                Pos last = first.offset(-r[0], 0, -r[1]);
                assertArrayEquals(new int[] {-r[0], -r[1]}, type.chunkFit(last, f), f + " " + last);
                // the player in front looks against the facing: "rechts" for a shift to the viewer's right
                org.bukkit.block.BlockFace looks = f.blockFace().getOppositeFace();
                assertEquals("rechts", MachineService.direction(looks, r));
                assertEquals("links", MachineService.direction(looks, new int[] {-r[0], -r[1]}));
            }
        }
        assertNull(MachineType.SLOT_MACHINE.chunkFit(new Pos("w", 15, 64, 15), Facing.SOUTH));
    }

    @Test
    void theRouletteTableMovesAlongXZOrDiagonally() {
        MachineType t = MachineType.ROULETTE_TABLE;
        assertNull(t.chunkFit(new Pos("w", 3, 64, 3), Facing.SOUTH));
        assertArrayEquals(new int[] {-1, 0}, t.chunkFit(new Pos("w", 15, 64, 3), Facing.SOUTH));
        assertArrayEquals(new int[] {0, -1}, t.chunkFit(new Pos("w", 3, 64, 31), Facing.SOUTH));
        assertArrayEquals(new int[] {-1, -1}, t.chunkFit(new Pos("w", -1, 64, 15), Facing.SOUTH));
        // a player looking north (-z): -x is his left, -z is away from him
        assertEquals("links", MachineService.direction(org.bukkit.block.BlockFace.NORTH, new int[] {-1, 0}));
        assertEquals("hinten", MachineService.direction(org.bukkit.block.BlockFace.NORTH, new int[] {0, -1}));
        assertEquals("links hinten", MachineService.direction(org.bukkit.block.BlockFace.NORTH, new int[] {-1, -1}));
        assertEquals("vorne", MachineService.direction(org.bukkit.block.BlockFace.EAST, new int[] {-1, 0}));
    }

    @Test
    void otherPlayersWithinTwoBlocksOfAnyCarrierBlockThePlacement() {
        List<Pos> carriers = MachineType.FORTUNE_WHEEL.carriers(new Pos("w", 10, 64, 10), Facing.NORTH);
        // a player box (0.6 wide, 1.8 tall) standing 2 blocks in front of the wheel (carrier face at z 10)
        BoundingBox twoAway = new BoundingBox(10.2, 63, 7.4, 10.8, 64.8, 8.0);
        assertTrue(MachineService.near(twoAway, carriers, 2.0));
        BoundingBox threeAway = new BoundingBox(10.2, 63, 6.4, 10.8, 64.8, 7.0);
        assertFalse(MachineService.near(threeAway, carriers, 2.0));
        // next to the outer column (x 11 is the last carrier column)
        assertTrue(MachineService.near(new BoundingBox(13.5, 64, 10.2, 14.1, 65.8, 10.8), carriers, 2.0));
        assertFalse(MachineService.near(new BoundingBox(14.5, 64, 10.2, 15.1, 65.8, 10.8), carriers, 2.0));
    }
}
