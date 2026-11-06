package de.tigges.kaboofcasino.game.slot;

/**
 * Model points of the slot machine in px, south frame, lower-block coordinates (games.md 6 "Sounds & particles",
 * {@code SlotMachineRenderGeometry}, {@code SlotMachineBlockEntity}).
 */
public final class SlotGeometry {

    /** Left x of each drum; drums are 5 px wide, so the centre is x0 + 2.5. */
    public static final double[] DRUM_X0 = {-0.5, 5.5, 11.5};
    public static final double AXIS_Y = 25.75;
    public static final double AXIS_Z = 4.497182503956605;
    public static final double[] LEVER_PIVOT = {20.6, 9.5, 8.0};
    /** Sound points. */
    public static final double[] LEVER_SOUND = {20.6, 10.0, 12.0};
    public static final double[] WINDOW_SOUND = {8.0, 25.75, 14.5};
    public static final double[] MOUTH_SOUND = {8.0, 6.5, 16.5};
    /** Particle points. */
    public static final double[] WINDOW_FRONT = {8.0, 25.75, 15.5};
    public static final double[] BURST_MOUTH = {8.0, 8.0, 17.5};
    public static final double[] BURST_WINDOW = {8.0, 28.75, 16.0};
    public static final double[] BURST_LAMP = {8.0, 40.0, 7.5};
    public static final double[] LOSE_LAMP = {8.0, 39.5, 7.5};
    public static final double[] PUSH_POINT = {8.0, 7.5, 17.0};
    /** Where payouts leave the machine ({@code eject}: model 8, 7, 17). */
    public static final double[] COIN_MOUTH = {8.0, 7.0, 17.0};
    /** The floating panel: above the anchor block centre (not turned with the facing). */
    public static final double PANEL_Y = 3.3;

    private SlotGeometry() {
    }

    public static double drumCentreX(int reel) {
        return DRUM_X0[reel] + 2.5;
    }
}
