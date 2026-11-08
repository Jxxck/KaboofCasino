package de.tigges.kaboofcasino.croupier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import de.tigges.kaboofcasino.croupier.CroupierHiring.Hand;
import de.tigges.kaboofcasino.croupier.CroupierHiring.Outcome;
import org.junit.jupiter.api.Test;

/** The mod's {@code CroupierHiring}: the card's hand, the card and the badge, the stream's path. */
class CroupierHiringTest {

    @Test
    void theCardHand() {
        // the clicked hand holds the card
        assertEquals(Hand.MAIN, CroupierHiring.cardHand(Hand.MAIN, true, false, false));
        assertEquals(Hand.MAIN, CroupierHiring.cardHand(Hand.MAIN, true, false, true));
        assertEquals(Hand.OFF, CroupierHiring.cardHand(Hand.OFF, false, false, true));
        assertEquals(Hand.OFF, CroupierHiring.cardHand(Hand.OFF, true, false, true));
        // main-hand click, main hand empty, card in the off hand
        assertEquals(Hand.OFF, CroupierHiring.cardHand(Hand.MAIN, false, true, true));
        // main-hand click with something else in the main hand: no hiring (the click trades)
        assertNull(CroupierHiring.cardHand(Hand.MAIN, false, false, true));
        // no card anywhere
        assertNull(CroupierHiring.cardHand(Hand.MAIN, false, true, false));
        assertNull(CroupierHiring.cardHand(Hand.MAIN, false, false, false));
        // an off-hand click only counts the off hand
        assertNull(CroupierHiring.cardHand(Hand.OFF, true, false, false));
        assertNull(CroupierHiring.cardHand(Hand.OFF, false, true, false));
    }

    @Test
    void theCardAndTheBadge() {
        // survival: one card used; the last card's hand takes the badge, else the inventory does
        assertEquals(new Outcome(true, true), CroupierHiring.outcome(false, 1));
        assertEquals(new Outcome(true, false), CroupierHiring.outcome(false, 2));
        assertEquals(new Outcome(true, false), CroupierHiring.outcome(false, 64));
        // creative: the card stays, the badge goes to the inventory
        assertEquals(new Outcome(false, false), CroupierHiring.outcome(true, 1));
        assertEquals(new Outcome(false, false), CroupierHiring.outcome(true, 16));
    }

    /** The mod's arithmetic, written out independently (Mth.lerp, Vec3.lerp, the swirl). */
    private static double[] mod(double[] from, double[] hands, int age, int k) {
        double t0 = age / 14.0;
        double t1 = (age + 1) / 14.0;
        double t = t0 + (t1 - t0) * (k / 4.0);
        double eased = t * t * (3.0 - 2.0 * t);
        double[] pos = new double[3];
        for (int i = 0; i < 3; i++) {
            pos[i] = from[i] + (hands[i] - from[i]) * eased;
        }
        pos[1] += Math.sin(t * Math.PI) * 0.9;
        double swirl = t * 9.0 + k * 1.57;
        pos[0] += Math.cos(swirl) * 0.22 * (1.0 - t);
        pos[2] += Math.sin(swirl) * 0.22 * (1.0 - t);
        return pos;
    }

    @Test
    void theStreamFlowsFromTheCroupierToTheHands() {
        double[] from = {10.5, 65.975, -3.5};
        double[] hands = {13.2, 66.1, -1.0};
        // starts at the croupier (swirled by 0.22), ends at the hands
        double[] start = CroupierHiring.streamPoint(from, hands, 0, 0);
        assertEquals(from[0] + 0.22, start[0], 1e-12);
        assertEquals(from[1], start[1], 1e-12);
        assertEquals(from[2], start[2], 1e-12);
        double[] end = CroupierHiring.streamPoint(from, hands, CroupierHiring.STREAM_TICKS, 0);
        for (int i = 0; i < 3; i++) {
            assertEquals(hands[i], end[i], 1e-9);
        }
        // half way it arcs 0.9 above the line
        double[] mid = CroupierHiring.streamPoint(from, hands, 7, 0);
        assertEquals((from[1] + hands[1]) / 2 + 0.9, mid[1], 1e-9);
        // every point of all 15 ticks as the mod computes it
        for (int age = 0; age <= CroupierHiring.STREAM_TICKS; age++) {
            for (int k = 0; k < 4; k++) {
                double[] want = mod(from, hands, age, k);
                double[] got = CroupierHiring.streamPoint(from, hands, age, k);
                for (int i = 0; i < 3; i++) {
                    assertEquals(want[i], got[i], 1e-12, age + "/" + k);
                }
            }
        }
    }

    @Test
    void theModsColours() {
        assertEquals(0xDF3B4B, CroupierHiring.RED_CONFETTI.getColor().asRGB());
        assertEquals(0xFFD168, CroupierHiring.GOLD_CONFETTI.getColor().asRGB());
        assertEquals(0x36D886, CroupierHiring.GREEN_CONFETTI.getColor().asRGB());
        assertEquals(1.15F, CroupierHiring.RED_CONFETTI.getSize());
        assertEquals(1.25F, CroupierHiring.GOLD_CONFETTI.getSize());
        assertEquals(1.15F, CroupierHiring.GREEN_CONFETTI.getSize());
    }
}
