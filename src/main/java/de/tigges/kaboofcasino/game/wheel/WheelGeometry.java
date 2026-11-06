package de.tigges.kaboofcasino.game.wheel;

/** Fortune wheel geometry in wheel-local px ({@code FortuneWheelRenderGeometry}; same values as the pack tool). */
final class WheelGeometry {

    /** Bulb centres (x, y), clockwise from the top, at z 13.06. */
    static final double[] BULBS = {11.5, 29, 18.5, 26.5, 23.5, 21.5, 26, 14.5, 26, 7.5, 23.5, 0.5, 18.5, -4.5,
        11.5, -7, 4.5, -7, -2.5, -4.5, -7.5, 0.5, -10, 7.5, -10, 14.5, -7.5, 21.5, -2.5, 26.5, 4.5, 29};
    static final double[] AXLE = {8, 11};
    static final double PLATE_HALF = 20;
    static final double PLATE_Z = 11;
    static final double[] HINGE = {8, 30};

    private WheelGeometry() {
    }
}
