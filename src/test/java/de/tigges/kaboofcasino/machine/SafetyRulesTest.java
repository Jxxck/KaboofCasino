package de.tigges.kaboofcasino.machine;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.util.Pos;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Safety rules of the fix round after step 3: rescue, logout spots, roulette support, break delivery, fluids. */
class SafetyRulesTest {

    /** 1a: a player inside carriers goes to the nearest free standing spot, the machine's front first. */
    @Test
    void rescueFindsTheNearestStandableSpotInFront() {
        // flat ground at y 63, a wheel's carriers x 9..11, y 64..66, z 10, facing north (front = -z)
        Set<String> carriers = new HashSet<>();
        for (int x = 9; x <= 11; x++) {
            for (int y = 64; y <= 66; y++) {
                carriers.add(x + "," + y + ",10");
            }
        }
        Rescue.Cells cells = (x, y, z) -> y == 64 && !carriers.contains(x + "," + y + "," + z)
                && !carriers.contains(x + "," + (y + 1) + "," + z);
        int[] spot = Rescue.nearest(10, 64, 10, new int[] {0, -1}, cells);
        assertArrayEquals(new int[] {10, 64, 9}, spot, "one block in front of the machine");
        // behind the machine only if the front is blocked
        Rescue.Cells onlyBehind = (x, y, z) -> y == 64 && z == 11;
        assertArrayEquals(new int[] {10, 64, 11}, Rescue.nearest(10, 64, 10, new int[] {0, -1}, onlyBehind));
        // nothing standable within the radius: the caller falls back to the highest block
        assertNull(Rescue.nearest(10, 64, 10, new int[] {0, -1}, (x, y, z) -> false));
        // a spot a few blocks up counts too (a machine in a pit)
        assertArrayEquals(new int[] {10, 67, 10}, Rescue.nearest(10, 64, 10, new int[] {0, -1},
                (x, y, z) -> y == 67 && x == 10 && z == 10));
    }

    /** 1b: logout boxes (feet and head) block carriers within one block; bots are not recorded (listener). */
    @Test
    void logoutSpotsBlockCarriersWithinOneBlock() {
        LogoutSpots.Box box = LogoutSpots.Box.of("w", 10.2, 64.0, 5.2, 10.8, 65.8, 5.8);
        assertEquals(new LogoutSpots.Box("w", 10, 64, 5, 10, 65, 5), box, "feet and head cells");
        assertTrue(box.near(new Pos("w", 10, 64, 5), 1));
        assertTrue(box.near(new Pos("w", 11, 66, 6), 1), "diagonal neighbour of the head");
        assertFalse(box.near(new Pos("w", 12, 64, 5), 1));
        assertFalse(box.near(new Pos("w", 10, 67, 5), 1));
        assertFalse(box.near(new Pos("other", 10, 64, 5), 1), "another world");
        LogoutSpots spots = new LogoutSpots(null);
        UUID alice = UUID.randomUUID();
        spots.record(alice, box, 1L);
        assertTrue(spots.blocks(new Pos("w", 9, 63, 4)));
        // the slot machine column one block away from the head would bury her: refused
        for (Pos p : MachineType.SLOT_MACHINE.carriers(new Pos("w", 11, 64, 5), Facing.SOUTH)) {
            assertTrue(spots.blocks(p) || p.y() > 66);
        }
        spots.forget(alice);
        assertFalse(spots.blocks(new Pos("w", 10, 64, 5)), "her next join frees the spot");
    }

    /** 2: the roulette table needs one supported cell unless roulette.needs-support is false (the mod). */
    @Test
    void rouletteSupportRule() {
        assertFalse(MachineService.supportOk(MachineType.ROULETTE_TABLE, 0, 4, true), "over air");
        assertTrue(MachineService.supportOk(MachineType.ROULETTE_TABLE, 1, 4, true), "one supported cell");
        assertTrue(MachineService.supportOk(MachineType.ROULETTE_TABLE, 0, 4, false), "the mod's behaviour");
        assertFalse(MachineService.supportOk(MachineType.FORTUNE_WHEEL, 2, 3, true), "3x3: every bottom cell");
        assertTrue(MachineService.supportOk(MachineType.FORTUNE_WHEEL, 3, 3, false));
        assertTrue(MachineService.supportOk(MachineType.SLOT_MACHINE, 1, 1, true));
    }

    /** 3: the broken machine's item goes into the breaker's inventory, only the rest drops at his feet. */
    @Test
    void breakDeliveryGivesFirstAndDropsOnlyTheRest() {
        List<String> dropped = new ArrayList<>();
        MachineService.giveOrDrop("wheel", item -> Map.of(), dropped::add);
        assertTrue(dropped.isEmpty(), "fits: nothing dropped");
        Map<Integer, String> full = new HashMap<>();
        full.put(0, "wheel");
        MachineService.giveOrDrop("wheel", item -> full, dropped::add);
        assertEquals(List.of("wheel"), dropped, "full inventory: at the breaker's feet");
    }

    /** 4: cells with any fluid state are refused (water, lava, waterlogged, seagrass/kelp/bubble columns). */
    @Test
    void anyFluidStateIsWet() {
        assertTrue(MachineService.wet(true, false, true));
        assertTrue(MachineService.wet(false, true, true));
        assertTrue(MachineService.wet(false, false, true), "seagrass: not liquid, not Waterlogged, but water");
        assertFalse(MachineService.wet(false, false, false));
    }
}
