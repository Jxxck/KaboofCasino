package de.tigges.kaboofcasino.machine;

/**
 * Mining speed of a machine (games.md 21.1: the machines copy oak planks with strength 3.0, no tool needed): progress
 * per tick = speed / hardness / 30, with the vanilla factors for haste, mining fatigue, the block-break-speed
 * attribute, being under water without aqua affinity and not standing on the ground. Pure.
 */
public final class BreakProgress {

    public static final double HARDNESS = 3.0;

    private BreakProgress() {
    }

    /**
     * @param toolSpeed      the tool's speed on oak planks (1 by hand, efficiency included)
     * @param haste          haste amplifier + 1, 0 if none
     * @param fatigue        mining fatigue amplifier + 1, 0 if none
     * @param breakSpeedAttr {@code block_break_speed} attribute value (1 by default)
     * @param submergedAttr  {@code submerged_mining_speed} if under water, else 1
     * @param onGround       standing on the ground
     */
    public static double perTick(double toolSpeed, int haste, int fatigue, double breakSpeedAttr, double submergedAttr,
                                 boolean onGround) {
        double speed = toolSpeed;
        if (haste > 0) {
            speed *= 1.0 + 0.2 * haste;
        }
        if (fatigue > 0) {
            speed *= switch (fatigue) {
                case 1 -> 0.3;
                case 2 -> 0.09;
                case 3 -> 0.0027;
                default -> 8.1e-4;
            };
        }
        speed *= breakSpeedAttr;
        speed *= submergedAttr;
        if (!onGround) {
            speed /= 5.0;
        }
        return speed / HARDNESS / 30.0;
    }
}
