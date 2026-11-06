package de.tigges.kaboofcasino.game.slot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.tigges.kaboofcasino.wager.ItemAmount;
import java.util.List;
import org.junit.jupiter.api.Test;

/** C4 (a loss settles at the result) and C7 (range to the block's box, as the mod's SlotMachineMenu). */
class SlotControllerRulesTest {

    @Test
    void aLossSettlesAtTheResultAWinWhenTheModStartsEjecting() {
        SlotSpin.Plan plan = SlotSpin.plan(new int[3], new int[] {0, 1, 3}, 5);
        assertEquals(plan.resultTick(), SlotController.settleTick(plan, List.of()));
        assertEquals(plan.resultTick() + 12,
                SlotController.settleTick(plan, List.of(new ItemAmount("minecraft:diamond", 2))));
    }

    @Test
    void distanceIsToTheNearestPointOfTheBlock() {
        assertEquals(0, SlotController.distanceSquaredToBlock(10.5, 64.2, -3.5, 10, 64, -4), 1e-9);
        // 2 blocks in front of the south face, at eye height 1.62 above the block's top
        assertEquals(4 + 0.62 * 0.62, SlotController.distanceSquaredToBlock(10.5, 65.62, -1, 10, 64, -4), 1e-9);
        // diagonal: 1 off in x and z
        assertEquals(2, SlotController.distanceSquaredToBlock(12, 64.5, -5, 10, 64, -4), 1e-9);
    }
}
