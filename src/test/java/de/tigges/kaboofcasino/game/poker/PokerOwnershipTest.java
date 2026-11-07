package de.tigges.kaboofcasino.game.poker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.machine.Heartbeat;
import org.junit.jupiter.api.Test;

/**
 * When a hand hands its PLAYING records to the ledger watchdog: the end is the last revision (deal, call, result) +
 * 60 s, the grace another 60 s. A whole hand without a call is DEAL + 10.5 s dealing + 30 s decision: inside.
 */
class PokerOwnershipTest {

    private static final long T0 = 1_000_000_000L;

    @Test
    void aHandThatTimesOutIsOwnedThroughout() {
        Heartbeat hb = new Heartbeat();
        long ends = T0 + PokerController.RECORD_ENDS_MILLIS;
        long timeout = T0 + (209 + 600) * 50L;
        hb.beat(timeout);
        assertTrue(PokerController.owns(hb, timeout, ends, false));
        long settle = timeout + (PokerController.PAYOUT_DELAY + 1) * 50L;
        hb.beat(settle);
        assertTrue(PokerController.owns(hb, settle, ends, false));
    }

    @Test
    void aStuckHandIsNotOwned() {
        Heartbeat hb = new Heartbeat();
        long ends = T0 + PokerController.RECORD_ENDS_MILLIS;
        long late = ends + Heartbeat.GRACE_MILLIS;
        hb.beat(late);
        assertFalse(PokerController.owns(hb, late, ends, false));
        hb.beat(late - 1);
        assertTrue(PokerController.owns(hb, late - 1, ends, false));
    }

    @Test
    void aSilentOrSettledControllerOwnsNothing() {
        Heartbeat hb = new Heartbeat();
        long ends = T0 + PokerController.RECORD_ENDS_MILLIS;
        hb.beat(T0);
        assertFalse(PokerController.owns(hb, T0 + Heartbeat.MAX_SILENCE_MILLIS + 1, ends, false));
        assertFalse(PokerController.owns(hb, T0, ends, true));
    }
}
