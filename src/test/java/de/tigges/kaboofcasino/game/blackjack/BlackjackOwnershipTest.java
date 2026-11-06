package de.tigges.kaboofcasino.game.blackjack;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.machine.Heartbeat;
import org.junit.jupiter.api.Test;

/**
 * When a hand hands its PLAYING records to the ledger watchdog: the records' end is the last revision + 60 s (deal,
 * hit, double, result), the grace another 60 s; the heartbeat only beats after the hand logic ran.
 */
class BlackjackOwnershipTest {

    private static final long T0 = 1_000_000_000L;

    @Test
    void aRunningHandOwnsItsRecords() {
        Heartbeat hb = new Heartbeat();
        long ends = T0 + BlackjackController.RECORD_ENDS_MILLIS;
        hb.beat(T0 + 30_000);
        assertTrue(BlackjackController.owns(hb, T0 + 30_000, ends, false));
        // a 30 s turn plus the dealer: still well inside
        hb.beat(T0 + 55_000);
        assertTrue(BlackjackController.owns(hb, T0 + 55_000, ends, false));
    }

    @Test
    void aHandWithoutRevisionsForTooLongIsNotOwnedEvenWhileBeating() {
        Heartbeat hb = new Heartbeat();
        long ends = T0 + BlackjackController.RECORD_ENDS_MILLIS;
        long late = ends + Heartbeat.GRACE_MILLIS;
        hb.beat(late);
        assertFalse(BlackjackController.owns(hb, late, ends, false), "stuck hand: no revision for 120 s");
        hb.beat(late - 1);
        assertTrue(BlackjackController.owns(hb, late - 1, ends, false));
    }

    @Test
    void aSilentControllerIsNotOwned() {
        Heartbeat hb = new Heartbeat();
        long ends = T0 + BlackjackController.RECORD_ENDS_MILLIS;
        hb.beat(T0);
        assertFalse(BlackjackController.owns(hb, T0 + Heartbeat.MAX_SILENCE_MILLIS + 1, ends, false),
                "exception loop or no rig: the heartbeat stops");
        assertFalse(new Heartbeat().owns(T0, ends), "never ran");
    }

    @Test
    void aSettledHandOwnsNothing() {
        Heartbeat hb = new Heartbeat();
        hb.beat(T0);
        assertFalse(BlackjackController.owns(hb, T0, T0 + BlackjackController.RECORD_ENDS_MILLIS, true));
    }
}
