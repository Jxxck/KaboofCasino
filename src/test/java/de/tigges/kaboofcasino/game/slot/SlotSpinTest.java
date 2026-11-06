package de.tigges.kaboofcasino.game.slot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.game.CasinoRandom;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The spin plan against games.md 6 "Timing & animation phases". */
class SlotSpinTest {

    @Test
    void reelsLandOnTheirStopsInOrderWithGaps() {
        CasinoRandom random = new CasinoRandom(1);
        int minResult = Integer.MAX_VALUE;
        int maxResult = 0;
        int minTease = Integer.MAX_VALUE;
        int maxTease = 0;
        for (int i = 0; i < 20_000; i++) {
            int[] from = {random.nextInt(8), random.nextInt(8), random.nextInt(8)};
            int[] stops = {random.nextInt(8), random.nextInt(8), random.nextInt(8)};
            SlotSpin.Plan plan = SlotSpin.plan(from, stops, random.nextLong());
            for (int k = 0; k < 3; k++) {
                double end = plan.turn(k, plan.land(k) + 12);
                assertEquals(stops[k], Math.floorMod(Math.round(end), 8), "reel " + k + " stops on its face");
                assertEquals(0.0, plan.speed(k, plan.land(k) + 1), 0.0);
                if (k > 0) {
                    assertTrue(plan.land(k) >= plan.land(k - 1) + 8 - 1e-9, "8 ticks between landings");
                }
            }
            assertEquals((int) Math.ceil(plan.land(2)) + 10, plan.resultTick());
            assertEquals(plan.resultTick() + 12, plan.payoutTick());
            if (plan.tease()) {
                minTease = Math.min(minTease, plan.resultTick());
                maxTease = Math.max(maxTease, plan.resultTick());
            } else {
                minResult = Math.min(minResult, plan.resultTick());
                maxResult = Math.max(maxResult, plan.resultTick());
            }
            List<SlotSpin.Cue> cues = plan.cues();
            assertEquals(SlotSpin.Sound.LEVER, cues.getFirst().sound());
            for (int c = 1; c < cues.size(); c++) {
                assertTrue(cues.get(c - 1).tick() <= cues.get(c).tick());
            }
            assertEquals(3, cues.stream().filter(c -> c.sound() == SlotSpin.Sound.STOP).count());
        }
        // games.md: resultTick 80-107 normally, 102-106 with a tease
        assertTrue(minResult >= 80 && maxResult <= 107, minResult + ".." + maxResult);
        assertTrue(minTease >= 102 && maxTease <= 106, minTease + ".." + maxTease);
    }

    @Test
    void teaseWhenTheFirstTwoSymbolsMatch() {
        assertTrue(SlotSpin.plan(new int[3], new int[] {0, 0, 3}, 5).tease());   // seven, seven
        assertTrue(!SlotSpin.plan(new int[3], new int[] {0, 1, 3}, 5).tease());  // seven, lemon
    }

    @Test
    void leverAndCelebrationTiming() {
        SlotSpin.Plan plan = SlotSpin.plan(new int[3], new int[] {0, 0, 0}, 99);
        assertEquals(-14.0, plan.lever(0), 1e-9);
        assertEquals(95.0, plan.lever(9), 1e-9);
        assertEquals(-14.0, plan.lever(26), 1e-9);
        assertEquals(SlotGame.Kind.JACKPOT, plan.result().kind());
        assertEquals(plan.resultTick() + 96, plan.endTick());
        assertEquals(plan.resultTick() + 40, plan.readyTick());
        SlotSpin.Plan lose = SlotSpin.plan(new int[3], new int[] {0, 1, 1}, 99);
        assertEquals(lose.resultTick(), lose.readyTick());
        assertEquals(1.0, lose.flash(lose.resultTick() + 1), 0);
    }

    @Test
    void sameSeedSamePlan() {
        SlotSpin.Plan a = SlotSpin.plan(new int[] {1, 2, 3}, new int[] {4, 5, 6}, 1234);
        SlotSpin.Plan b = SlotSpin.plan(new int[] {1, 2, 3}, new int[] {4, 5, 6}, 1234);
        assertEquals(a.cues(), b.cues());
        assertEquals(a.resultTick(), b.resultTick());
    }
}
