package de.tigges.kaboofcasino.machine;

import org.bukkit.block.BlockFace;

/**
 * The four machine facings with the mod's {@code get2DDataValue} turns (S=0, W=1, N=2, E=3). The machine's front
 * points in this direction (towards the player who placed it).
 */
public enum Facing {
    SOUTH(0, BlockFace.SOUTH), WEST(1, BlockFace.WEST), NORTH(2, BlockFace.NORTH), EAST(3, BlockFace.EAST);

    private final int turns;
    private final BlockFace face;

    Facing(int turns, BlockFace face) {
        this.turns = turns;
        this.face = face;
    }

    public int turns() {
        return turns;
    }

    public BlockFace blockFace() {
        return face;
    }

    /** Front direction as (dx, dz) ({@code SlotMachineLayout.facingStep}). */
    public int[] step() {
        return new int[] {face.getModX(), face.getModZ()};
    }

    /** The mod's placement: {@code context.getHorizontalDirection().getOpposite()}. */
    public static Facing towardsPlayer(BlockFace playerLooks) {
        return of(playerLooks.getOppositeFace());
    }

    public static Facing of(BlockFace face) {
        for (Facing f : values()) {
            if (f.face == face) {
                return f;
            }
        }
        throw new IllegalArgumentException("not a horizontal facing: " + face);
    }

    /** True if both lie on the same axis (N/S or E/W). */
    public boolean sameAxis(Facing other) {
        return (turns & 1) == (other.turns & 1);
    }

    /**
     * {@code SlotMachineLayout.toBlock}: a model point in px (south frame, lower-block coordinates) to block offsets
     * from the anchor's corner, rotated about the block centre by this facing.
     */
    public double[] toBlock(double x, double y, double z) {
        double px = x;
        double pz = z;
        for (int i = 0; i < turns; i++) {
            double nx = 16.0 - pz;
            pz = px;
            px = nx;
        }
        return new double[] {px / 16.0, y / 16.0, pz / 16.0};
    }
}
