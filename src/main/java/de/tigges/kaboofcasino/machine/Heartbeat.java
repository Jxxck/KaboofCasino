package de.tigges.kaboofcasino.machine;

/**
 * Liveness of a machine that runs a round (pure, unit-tested). The ledger watchdog leaves a round to its machine only
 * while the machine ticked within {@link #MAX_SILENCE_MILLIS} (an unloaded chunk or a stuck controller stops beating)
 * and the round's scheduled end is less than {@link #GRACE_MILLIS} in the past.
 */
public final class Heartbeat {

    public static final long MAX_SILENCE_MILLIS = 2_000;
    public static final long GRACE_MILLIS = 60_000;

    private long last = -1;

    public void beat(long now) {
        last = now;
    }

    public boolean owns(long now, long ends) {
        return last >= 0 && now - last <= MAX_SILENCE_MILLIS && now < ends + GRACE_MILLIS;
    }
}
