package de.tigges.kaboofcasino.game.derby;

import java.util.function.LongPredicate;

/**
 * When the derby rig sends its moving parts (ducks and gates) and how far ahead. Three tiers by the nearest player:
 * <ul>
 *   <li>{@link Tier#NEAR}: eyes within {@value #NEAR_RANGE} blocks of the anchor block's centre (the renderer's view
 *       distance, measured from the camera): every tick, the pose of {@code now + 1}, interpolation 1;</li>
 *   <li>{@link Tier#FAR}: nobody that near, but a player inside the display tracking range (horizontal distance to
 *       the rig entity, the server tracker's rule): a client with a higher entity distance draws the ducks there
 *       (view range 1.0 = 64 blocks x its entity-distance scaling), so they keep moving at every
 *       {@value #FAR_EVERY}th tick with the pose of {@code now + 4} and interpolation 4 (exact at the samples, linear
 *       between), each send granted by the shared idle budget ({@code lights.max-updates-per-tick}; asked only when
 *       due, retried every tick until granted, so a busy casino slows these far updates down, never the near
 *       ones);</li>
 *   <li>{@link Tier#NONE}: nobody tracks the displays: nothing is sent.</li>
 * </ul>
 * Pure; one instance per rig. Thread: MAIN.
 */
final class DerbyRigCadence {

    enum Tier { NONE, NEAR, FAR }

    /** {@code DuckDerbyRenderer.getViewDistance}. */
    static final int NEAR_RANGE = 64;
    /**
     * {@code spigot.yml} {@code entity-tracking-range.display} (128 on live). Paper's getters for it
     * ({@code Server.Spigot#getConfig}/{@code getSpigotConfig}) are deprecated, so it is this constant, capped like the
     * tracker by the world's view distance x 16 ({@link #trackingRange}).
     */
    static final int TRACKING_RANGE = 128;
    static final int NEAR_EVERY = 1;
    static final int FAR_EVERY = 4;

    private long last = Long.MIN_VALUE / 2;

    /** The display tracking range of a world with this view distance (chunks): {@code min(128, 16 x view)}. */
    static int trackingRange(int viewDistance) {
        return Math.min(TRACKING_RANGE, Math.max(1, viewDistance) * 16);
    }

    /**
     * @param eyeSq        squared distance from the anchor block's centre to the nearest player's eyes
     * @param horizontalSq squared horizontal distance from the rig entity to the nearest player
     */
    static Tier tier(double eyeSq, double horizontalSq, int trackingRange) {
        if (eyeSq <= (double) NEAR_RANGE * NEAR_RANGE) {
            return Tier.NEAR;
        }
        return horizontalSq <= (double) trackingRange * trackingRange ? Tier.FAR : Tier.NONE;
    }

    /**
     * The send of tick {@code now}: the number of ticks ahead (= the interpolation duration) to send, or 0 for none.
     * {@code grant} (the shared idle budget) is asked only when a far send is due.
     */
    int step(long now, Tier tier, LongPredicate grant) {
        switch (tier) {
            case NEAR -> {
                last = now;
                return NEAR_EVERY;
            }
            case FAR -> {
                if ((now - last < FAR_EVERY && now >= last) || !grant.test(now)) {
                    return 0;
                }
                last = now;
                return FAR_EVERY;
            }
            default -> {
                return 0;
            }
        }
    }

    /** Forget the last send (the rig was respawned). */
    void reset() {
        last = Long.MIN_VALUE / 2;
    }
}
