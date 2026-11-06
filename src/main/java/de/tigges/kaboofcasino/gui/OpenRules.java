package de.tigges.kaboofcasino.gui;

/**
 * The rules of {@link MenuService#open}, pure: interaction tick (FEAS-02), usable player, safe moment. Checked in
 * this order; the first failing one is the verdict.
 */
final class OpenRules {

    enum Verdict {
        OPEN, NOT_INTERACTION_TICK, UNUSABLE, UNSAFE
    }

    private OpenRules() {
    }

    static Verdict check(long interactionTick, long currentTick, boolean usable, boolean safe) {
        if (interactionTick < 0 || interactionTick != currentTick) {
            return Verdict.NOT_INTERACTION_TICK;
        }
        if (!usable) {
            return Verdict.UNUSABLE;
        }
        return safe ? Verdict.OPEN : Verdict.UNSAFE;
    }
}
