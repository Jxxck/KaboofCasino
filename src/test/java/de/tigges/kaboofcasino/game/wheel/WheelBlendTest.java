package de.tigges.kaboofcasino.game.wheel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Fidelity 3: the plate's blur state follows FortuneWheelBlur in quarter steps (the pack's baked cross-fades). */
class WheelBlendTest {

    @Test
    void blendStepsFollowTheMod() {
        assertEquals(0, WheelRig.blendStep(0.0));
        assertEquals(0, WheelRig.blendStep(0.6), "sharp up to 0.6 segments per tick");
        assertEquals(8, WheelRig.blendStep(1.05), "full blur from 1.05");
        assertEquals(8, WheelRig.blendStep(2.0));
        // half-way through each transition (smoothstep 0.5 at the middle)
        assertEquals(2, WheelRig.blendStep((0.6 + 0.85) / 2));
        assertEquals(4, WheelRig.blendStep(0.85));
        assertEquals(6, WheelRig.blendStep((0.85 + 1.05) / 2));
        int last = 0;
        for (double v = 0; v <= 1.2; v += 0.001) {
            int step = WheelRig.blendStep(v);
            assertEquals(true, step >= last && step - last <= 1, "monotone in single steps at " + v);
            last = step;
        }
    }
}
