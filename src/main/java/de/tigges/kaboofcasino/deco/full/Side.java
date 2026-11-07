package de.tigges.kaboofcasino.deco.full;

/** A horizontal side, in the mod's order (north, east, south, west = {@code Direction.Plane.HORIZONTAL}). */
public enum Side {
    NORTH(0, -1),
    EAST(1, 0),
    SOUTH(0, 1),
    WEST(-1, 0);

    private final int dx;
    private final int dz;

    Side(int dx, int dz) {
        this.dx = dx;
        this.dz = dz;
    }

    public int dx() {
        return dx;
    }

    public int dz() {
        return dz;
    }

    /** Property name and value ({@code north} ...). */
    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
