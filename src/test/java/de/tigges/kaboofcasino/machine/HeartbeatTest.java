package de.tigges.kaboofcasino.machine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** B6: a machine owns its round only while it ticks and not long after the scheduled end. */
class HeartbeatTest {

    @Test
    void ownsOnlyWhileTickingAndBeforeTheGraceEnds() {
        Heartbeat h = new Heartbeat();
        long ends = 10_000;
        assertFalse(h.owns(1_000, ends), "never ticked");
        h.beat(1_000);
        assertTrue(h.owns(2_500, ends));
        assertTrue(h.owns(3_000, ends));
        assertFalse(h.owns(3_001, ends), "silent for more than 2 s: chunk unloaded or stuck");
        h.beat(ends + Heartbeat.GRACE_MILLIS - 1);
        assertTrue(h.owns(ends + Heartbeat.GRACE_MILLIS - 1, ends));
        h.beat(ends + Heartbeat.GRACE_MILLIS);
        assertFalse(h.owns(ends + Heartbeat.GRACE_MILLIS, ends), "ticking, but far past the end");
    }
}
