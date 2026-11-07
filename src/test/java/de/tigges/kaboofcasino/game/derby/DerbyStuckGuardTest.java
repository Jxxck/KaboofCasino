package de.tigges.kaboofcasino.game.derby;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The derby's stuck guard: a race past its {@code endTick} + 600 ticks, or a countdown past its 200 ticks + 600, is
 * stuck; real races (endTick 243..276, games.md 7) and countdowns never are.
 */
class DerbyStuckGuardTest {

    private static final long T0 = 50_000_000L;

    @Test
    void aRaceIsStuckOnlyLongAfterItsEnd() {
        for (int end : new int[] {243, 257, 276}) {
            for (long t = 0; t <= end + DerbyController.STUCK_AFTER_TICKS; t++) {
                assertFalse(DerbyController.raceStuck(T0 + t, T0, end), "tick " + t + " of a race ending at " + end);
            }
            assertTrue(DerbyController.raceStuck(T0 + end + DerbyController.STUCK_AFTER_TICKS + 1, T0, end));
        }
    }

    @Test
    void everyRealPlanEndsLongBeforeTheGuard() {
        for (long seed = 0; seed < 40; seed++) {
            DuckDerbyRace.Plan plan = DuckDerbyRace.plan((int) (seed % 4), seed * 7919L);
            assertFalse(DerbyController.raceStuck(T0 + plan.endTick() + 1, T0, plan.endTick()));
        }
    }

    @Test
    void aCountdownIsStuckOnlyLongAfterItsEnd() {
        long until = T0 + DerbyController.ENROLL_TICKS;
        for (long t = T0; t <= until + DerbyController.STUCK_AFTER_TICKS; t++) {
            assertFalse(DerbyController.countdownStuck(t, until));
        }
        assertTrue(DerbyController.countdownStuck(until + DerbyController.STUCK_AFTER_TICKS + 1, until));
        assertFalse(DerbyController.countdownStuck(T0 + 10_000, -1), "no countdown");
    }

    @Test
    void thePayoutIsSpreadOverFiveTicksAndEndsByTheLast() {
        for (int n = 0; n <= 64; n++) {
            int prev = 0;
            for (int step = 0; step < DerbyController.PAYOUT_TICKS; step++) {
                int upTo = DerbyController.payUpTo(n, step);
                assertTrue(upTo >= prev && upTo <= n, n + " entrants, step " + step);
                assertTrue(upTo - prev <= (n + DerbyController.PAYOUT_TICKS - 1) / DerbyController.PAYOUT_TICKS + 1,
                        "even batches");
                prev = upTo;
            }
            org.junit.jupiter.api.Assertions.assertEquals(n, prev, "everybody paid by resultTick + 16");
            org.junit.jupiter.api.Assertions.assertEquals(n, DerbyController.payUpTo(n, 99));
        }
        org.junit.jupiter.api.Assertions.assertEquals(1, DerbyController.payUpTo(1, 0), "one entrant: at once");
        org.junit.jupiter.api.Assertions.assertEquals(13, DerbyController.payUpTo(64, 0));
    }
}
