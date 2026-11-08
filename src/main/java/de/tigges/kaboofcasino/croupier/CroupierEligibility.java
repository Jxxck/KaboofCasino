package de.tigges.kaboofcasino.croupier;

import de.tigges.kaboofcasino.util.Pos;

/**
 * Option A's safety rules: may this villager, standing on a counter a player right-clicked, become the counter's
 * croupier? In the mod an unemployed adult (non-nitwit) villager claims a free dealer station as its job site (games.md
 * 10.1); here a player brings it onto the counter and clicks the counter, which consumes it. Nothing ever converts
 * without that click. Every rule must hold; the first one that fails is the verdict. Pure.
 */
public final class CroupierEligibility {

    /** Feet within this of the counter's top count as standing on it (the barrier's top is exactly y + 1). */
    static final double ON_TOP = 1.0E-3;
    /** Why a villager is not taken (or {@link #OK}). */
    public enum Verdict {
        OK,
        /** Dead or removed. */
        GONE,
        /** Its feet are not in the block directly above the counter. */
        NOT_ABOVE_COUNTER,
        /** In that block but not standing on the counter's top (jumping, falling, swimming). */
        NOT_ON_TOP,
        /** The counter has a croupier already. */
        COUNTER_TAKEN,
        NITWIT,
        /** Has a profession. */
        EMPLOYED,
        /** Above level 1. */
        LEVEL,
        /** Has villager experience. */
        EXPERIENCE,
        /** Has trades (recipes). */
        TRADES,
        BABY,
        LEASHED,
        /** Has a custom name (somebody's named villager). */
        NAMED,
        /** Rides something (boat, minecart). */
        RIDING,
        /** Carries passengers. */
        PASSENGERS,
        /** A player has its trade screen open. */
        TRADING,
        SLEEPING,
        /** The clicking player is not within reach of the counter (vanilla's block interaction range). */
        OUT_OF_REACH,
        /** No villager stands in the block above the counter (only a click's answer, never {@link #check}'s). */
        NOBODY
    }

    /**
     * What the check needs to know about a villager, read from the entity on the main thread: alive, its exact feet
     * position, vanilla's on-ground flag, profession none / nitwit, level, villager experience, number of trades,
     * adult, leashed, custom name, riding, carrying passengers, trading, sleeping.
     */
    public record Facts(boolean alive, double x, double y, double z, boolean onGround, boolean unemployed,
                        boolean nitwit, int level, int experience, int recipes, boolean adult, boolean leashed,
                        boolean customName, boolean riding, boolean passengers, boolean trading, boolean sleeping) {
    }

    private CroupierEligibility() {
    }

    /**
     * @param counter     the dealer station's block
     * @param taken       the counter has a croupier already
     * @param inReach     the clicking player is within reach of the counter
     */
    public static Verdict check(Facts v, Pos counter, boolean taken, boolean inReach) {
        if (!v.alive()) {
            return Verdict.GONE;
        }
        if ((int) Math.floor(v.x()) != counter.x() || (int) Math.floor(v.z()) != counter.z()
                || (int) Math.floor(v.y()) != counter.y() + 1) {
            return Verdict.NOT_ABOVE_COUNTER;
        }
        if (!v.onGround() || Math.abs(v.y() - (counter.y() + 1)) > ON_TOP) {
            return Verdict.NOT_ON_TOP;
        }
        if (taken) {
            return Verdict.COUNTER_TAKEN;
        }
        if (v.nitwit()) {
            return Verdict.NITWIT;
        }
        if (!v.unemployed()) {
            return Verdict.EMPLOYED;
        }
        if (v.level() != 1) {
            return Verdict.LEVEL;
        }
        if (v.experience() != 0) {
            return Verdict.EXPERIENCE;
        }
        if (v.recipes() != 0) {
            return Verdict.TRADES;
        }
        if (!v.adult()) {
            return Verdict.BABY;
        }
        if (v.leashed()) {
            return Verdict.LEASHED;
        }
        if (v.customName()) {
            return Verdict.NAMED;
        }
        if (v.riding()) {
            return Verdict.RIDING;
        }
        if (v.passengers()) {
            return Verdict.PASSENGERS;
        }
        if (v.trading()) {
            return Verdict.TRADING;
        }
        if (v.sleeping()) {
            return Verdict.SLEEPING;
        }
        if (!inReach) {
            return Verdict.OUT_OF_REACH;
        }
        return Verdict.OK;
    }
}
