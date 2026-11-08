package de.tigges.kaboofcasino.croupier;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.tigges.kaboofcasino.croupier.CroupierEligibility.Facts;
import de.tigges.kaboofcasino.croupier.CroupierEligibility.Verdict;
import de.tigges.kaboofcasino.util.Pos;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Option A's safety rules: a fitting villager is taken, and every single rule on its own refuses one. */
class CroupierEligibilityTest {

    private static final Pos COUNTER = new Pos("minecraft:overworld", 10, 64, -7);

    /** An unemployed adult standing on the counter's top (feet exactly on y + 1). */
    private static Facts fitting() {
        return new Facts(true, 10.3, 65.0, -6.6, true, true, false, 1, 0, 0, true, false, false, false, false, false,
                false);
    }

    private static Verdict check(Facts f) {
        return CroupierEligibility.check(f, COUNTER, false, true);
    }

    @Test
    void aFittingVillagerIsTaken() {
        assertEquals(Verdict.OK, check(fitting()));
        // anywhere within the block above the counter, also at its edges and negative coordinates
        Facts f = fitting();
        assertEquals(Verdict.OK, check(new Facts(true, 10.0, 65.0, -7.0, true, true, false, 1, 0, 0, true, false,
                false, false, false, false, false)));
        assertEquals(Verdict.OK, check(new Facts(true, 10.999, 65.0005, -6.001, true, true, false, 1, 0, 0, true,
                false, false, false, false, false, false)));
        assertEquals(Verdict.OK, CroupierEligibility.check(f, COUNTER, false, true));
    }

    @Test
    void theVillagerMustStandOnTheCounter() {
        // beside it (also diagonally), on the floor next to it, a block too high
        for (double[] at : new double[][] {{9.9, 65.0, -6.5}, {11.0, 65.0, -6.5}, {10.5, 65.0, -7.01},
            {10.5, 65.0, -6.0}, {11.2, 65.0, -5.8}, {10.5, 64.0, -6.5}, {10.5, 66.0, -6.5}, {10.5, 63.0, -6.5}}) {
            assertEquals(Verdict.NOT_ABOVE_COUNTER, check(new Facts(true, at[0], at[1], at[2], true, true, false, 1, 0,
                    0, true, false, false, false, false, false, false)), java.util.Arrays.toString(at));
        }
        // in the block above but jumping, falling or not on the ground
        assertEquals(Verdict.NOT_ON_TOP, check(new Facts(true, 10.5, 65.3, -6.5, false, true, false, 1, 0, 0, true,
                false, false, false, false, false, false)));
        assertEquals(Verdict.NOT_ON_TOP, check(new Facts(true, 10.5, 65.0, -6.5, false, true, false, 1, 0, 0, true,
                false, false, false, false, false, false)));
        assertEquals(Verdict.NOT_ON_TOP, check(new Facts(true, 10.5, 65.2, -6.5, true, true, false, 1, 0, 0, true,
                false, false, false, false, false, false)));
    }

    @Test
    void everyRuleRefusesOnItsOwn() {
        Set<Verdict> seen = EnumSet.noneOf(Verdict.class);
        Facts f = fitting();
        expect(seen, Verdict.GONE, new Facts(false, f.x(), f.y(), f.z(), true, true, false, 1, 0, 0, true, false,
                false, false, false, false, false));
        expect(seen, Verdict.NITWIT, new Facts(true, f.x(), f.y(), f.z(), true, false, true, 1, 0, 0, true, false,
                false, false, false, false, false));
        // a nitwit's profession is not "none" either; the nitwit rule names it
        expect(seen, Verdict.EMPLOYED, new Facts(true, f.x(), f.y(), f.z(), true, false, false, 1, 0, 0, true, false,
                false, false, false, false, false));
        expect(seen, Verdict.LEVEL, new Facts(true, f.x(), f.y(), f.z(), true, true, false, 2, 0, 0, true, false,
                false, false, false, false, false));
        expect(seen, Verdict.EXPERIENCE, new Facts(true, f.x(), f.y(), f.z(), true, true, false, 1, 3, 0, true, false,
                false, false, false, false, false));
        expect(seen, Verdict.TRADES, new Facts(true, f.x(), f.y(), f.z(), true, true, false, 1, 0, 2, true, false,
                false, false, false, false, false));
        expect(seen, Verdict.BABY, new Facts(true, f.x(), f.y(), f.z(), true, true, false, 1, 0, 0, false, false,
                false, false, false, false, false));
        expect(seen, Verdict.LEASHED, new Facts(true, f.x(), f.y(), f.z(), true, true, false, 1, 0, 0, true, true,
                false, false, false, false, false));
        expect(seen, Verdict.NAMED, new Facts(true, f.x(), f.y(), f.z(), true, true, false, 1, 0, 0, true, false,
                true, false, false, false, false));
        expect(seen, Verdict.RIDING, new Facts(true, f.x(), f.y(), f.z(), true, true, false, 1, 0, 0, true, false,
                false, true, false, false, false));
        expect(seen, Verdict.PASSENGERS, new Facts(true, f.x(), f.y(), f.z(), true, true, false, 1, 0, 0, true, false,
                false, false, true, false, false));
        expect(seen, Verdict.TRADING, new Facts(true, f.x(), f.y(), f.z(), true, true, false, 1, 0, 0, true, false,
                false, false, false, true, false));
        expect(seen, Verdict.SLEEPING, new Facts(true, f.x(), f.y(), f.z(), true, true, false, 1, 0, 0, true, false,
                false, false, false, false, true));
        assertEquals(Verdict.COUNTER_TAKEN, CroupierEligibility.check(f, COUNTER, true, true));
        seen.add(Verdict.COUNTER_TAKEN);
        assertEquals(Verdict.OUT_OF_REACH, CroupierEligibility.check(f, COUNTER, false, false));
        seen.add(Verdict.OUT_OF_REACH);
        // with the positional rules of theVillagerMustStandOnTheCounter: every refusal is covered
        seen.add(Verdict.NOT_ABOVE_COUNTER);
        seen.add(Verdict.NOT_ON_TOP);
        seen.add(Verdict.OK);
        // NOBODY is the click's answer when no villager is there, never check's
        assertEquals(EnumSet.complementOf(EnumSet.of(Verdict.NOBODY)), seen);
    }

    private static void expect(Set<Verdict> seen, Verdict want, Facts f) {
        assertEquals(want, check(f));
        seen.add(want);
    }

    @Test
    void aTakenCounterRefusesBeforeTheVillagerIsLookedAt() {
        // a croupier's counter never takes a second villager, whatever the villager is
        Facts baby = new Facts(true, 10.5, 65.0, -6.5, true, true, false, 1, 0, 0, false, true, true, false, false,
                false, false);
        assertEquals(Verdict.COUNTER_TAKEN, CroupierEligibility.check(baby, COUNTER, true, true));
    }
}
