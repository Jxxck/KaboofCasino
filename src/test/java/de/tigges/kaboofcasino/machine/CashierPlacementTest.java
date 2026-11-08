package de.tigges.kaboofcasino.machine;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.util.Pos;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * The cashier as a machine (step 9): 2 wide x 1 deep x 2 tall on the placed cell and the viewer's right, turned with
 * the facing, both bottom cells on support, inside one chunk (one block sideways otherwise), no game and no settings,
 * its own placing item.
 */
class CashierPlacementTest {

    @Test
    void theFootprintIsTwoWideTwoTallOnTheViewersRight() {
        MachineType t = MachineType.CASHIER;
        assertFalse(t.isGame());
        assertTrue(t.needsSupport());
        assertFalse(t.fixedFacing());
        assertEquals(0, t.rise());
        assertEquals(0, t.bottom());
        for (Facing f : Facing.values()) {
            Pos anchor = new Pos("w", 8, 64, 8);
            List<Pos> cells = t.carriers(anchor, f);
            int[] r = MachineType.right(f);
            Set<Pos> expected = Set.of(anchor, anchor.offset(r[0], 0, r[1]), anchor.offset(0, 1, 0),
                    anchor.offset(r[0], 1, r[1]));
            assertEquals(expected, Set.copyOf(cells), f.name());
            assertEquals(2, cells.stream().filter(p -> p.y() == 64).count(), "two cells need support");
        }
        assertEquals(CasinoItem.CASHIER, CasinoItem.forMachine(MachineType.CASHIER));
        assertEquals(MachineType.CASHIER, MachineType.byId("cashier").orElseThrow());
        assertEquals("kaboofcasino:cashier", CasinoItem.CASHIER.model());
    }

    @Test
    void itMovesOneBlockSidewaysAtAChunkBorder() {
        MachineType t = MachineType.CASHIER;
        for (Facing f : Facing.values()) {
            int[] r = MachineType.right(f);
            assertNull(t.chunkFit(new Pos("w", 8, 64, 8), f));
            // the right cell would stick into the next chunk: one block to the left fits
            Pos last = new Pos("w", r[0] > 0 ? 15 : r[0] < 0 ? 16 : 8, 64, r[1] > 0 ? 15 : r[1] < 0 ? 16 : 8);
            assertArrayEquals(new int[] {-r[0], -r[1]}, t.chunkFit(last, f), f.name());
            assertNull(t.chunkFit(last.offset(-r[0], 0, -r[1]), f));
        }
        // all four cells always in the anchor's chunk once placed
        Set<String> chunks = t.carriers(new Pos("w", 14, 64, 3), Facing.NORTH).stream()
                .map(p -> p.chunkX() + "," + p.chunkZ()).collect(Collectors.toSet());
        assertEquals(1, chunks.size());
    }

    @Test
    void theCashierHasNoWinChanceOrBiasAndNoMachineDialog() {
        assertFalse(MachineSettings.MOD_ORDER.contains(MachineType.CASHIER));
        assertFalse(MachineSettings.usesBias(MachineType.CASHIER));
        for (Runnable r : List.<Runnable>of(() -> MachineSettings.modType(MachineType.CASHIER),
                () -> MachineSettings.configPath(MachineType.CASHIER),
                () -> MachineSettings.typeValue(de.tigges.kaboofcasino.config.CasinoConfig.DEFAULTS,
                        MachineType.CASHIER))) {
            boolean refused = false;
            try {
                r.run();
            } catch (IllegalArgumentException e) {
                refused = true;
            }
            assertTrue(refused);
        }
    }
}
