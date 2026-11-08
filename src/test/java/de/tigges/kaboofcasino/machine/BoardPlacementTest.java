package de.tigges.kaboofcasino.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.util.Pos;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

/**
 * The two leaderboards as machines (step 10): the wall board 3 wide x 2 tall centred on the placed cell, hung on a
 * wall (no support below, a horizontal face only); the column 1x1x3 on support; no game, own items.
 */
class BoardPlacementTest {

    @Test
    void theWallBoardIsThreeWideTwoTallAroundThePlacedCell() {
        MachineType t = MachineType.LEADERBOARD_WALL;
        assertFalse(t.isGame());
        assertTrue(t.isBoard());
        assertTrue(t.onWall());
        assertFalse(t.needsSupport());
        assertEquals(0, t.rise());
        for (Facing f : Facing.values()) {
            Pos anchor = new Pos("w", 8, 64, 8);
            int[] r = MachineType.right(f);
            Set<Pos> expected = new HashSet<>();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    expected.add(anchor.offset(dx * r[0], dy, dx * r[1]));
                }
            }
            assertEquals(expected, Set.copyOf(t.carriers(anchor, f)), f.name());
        }
        assertEquals(CasinoItem.LEADERBOARD_WALL, CasinoItem.forMachine(t));
        assertEquals(t, MachineType.byId("leaderboard_wall").orElseThrow());
        assertEquals("kaboofcasino:leaderboard_wall", CasinoItem.LEADERBOARD_WALL.model());
        assertTrue(MachineService.isHorizontal(BlockFace.NORTH));
        assertFalse(MachineService.isHorizontal(BlockFace.UP));
        assertFalse(MachineService.isHorizontal(BlockFace.DOWN));
    }

    @Test
    void theColumnStandsLikeASlotMachine() {
        MachineType t = MachineType.LEADERBOARD_COLUMN;
        assertFalse(t.isGame());
        assertTrue(t.isBoard());
        assertFalse(t.onWall());
        assertTrue(t.needsSupport());
        Pos anchor = new Pos("w", 8, 64, 8);
        assertEquals(List.of(anchor, anchor.offset(0, 1, 0), anchor.offset(0, 2, 0)), t.carriers(anchor,
                Facing.EAST));
        assertEquals(CasinoItem.LEADERBOARD_COLUMN, CasinoItem.forMachine(t));
        assertFalse(MachineSettings.MOD_ORDER.contains(t));
        assertFalse(MachineSettings.usesBias(t));
        assertTrue(MachineType.CASHIER.isGame() == false && MachineType.SLOT_MACHINE.isGame());
    }
}
