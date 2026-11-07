package de.tigges.kaboofcasino.deco.full;

import java.util.Objects;

/**
 * One block state of a huge-mushroom block: the six face properties. The reserved range is {@code down=true}; there
 * the remaining five faces form a mask (up 16, north 8, east 4, south 2, west 1), 31 being the all-true default that
 * vanilla placement produces.
 */
public record MushroomState(MushroomBlock block, boolean down, boolean up, boolean north, boolean east, boolean south,
                            boolean west) {

    public static final int UP = 16;
    public static final int NORTH = 8;
    public static final int EAST = 4;
    public static final int SOUTH = 2;
    public static final int WEST = 1;
    /** The all-true default (vanilla placement with mushroom updates disabled). */
    public static final int ALL = 31;

    public MushroomState {
        Objects.requireNonNull(block, "block");
    }

    /** The {@code down=true} state with the given mask of the other five faces. */
    public static MushroomState downTrue(MushroomBlock block, int mask) {
        if (mask < 0 || mask > ALL) {
            throw new IllegalArgumentException("mask " + mask);
        }
        return new MushroomState(block, true, (mask & UP) != 0, (mask & NORTH) != 0, (mask & EAST) != 0,
                (mask & SOUTH) != 0, (mask & WEST) != 0);
    }

    /** The mask of up/north/east/south/west (the {@code down} face is not part of it). */
    public int mask() {
        return (up ? UP : 0) | (north ? NORTH : 0) | (east ? EAST : 0) | (south ? SOUTH : 0) | (west ? WEST : 0);
    }

    public boolean allTrue() {
        return down && mask() == ALL;
    }

    /** Minecraft's state string with the properties in alphabetical order. */
    public String key() {
        return block.id() + "[" + properties() + "]";
    }

    /** {@code down=true,east=false,north=...,south=...,up=...,west=...} */
    public String properties() {
        return "down=" + down + ",east=" + east + ",north=" + north + ",south=" + south + ",up=" + up + ",west=" + west;
    }

    @Override
    public String toString() {
        return key();
    }
}
