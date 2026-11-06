package de.tigges.kaboofcasino.machine;

/**
 * Where to put a player found inside a machine's carriers (safety 1a, pure): the nearest spot with two free cells
 * (feet and head) and safe ground below, searched outward up to {@link #RADIUS} blocks and a few blocks up and down,
 * spots in front of the machine first; null if none (the caller then uses the highest safe block above).
 */
public final class Rescue {

    public static final int RADIUS = 4;

    /** The world as the search sees it. */
    public interface Cells {
        /** Feet at (x, y, z): y and y + 1 free (no carrier, no solid, no liquid, no fire), y - 1 safe ground. */
        boolean standable(int x, int y, int z);
    }

    private Rescue() {
    }

    /**
     * @param x, y, z the player's feet block
     * @param front   the machine's front step (dx, dz); spots on that side of the player are preferred
     */
    public static int[] nearest(int x, int y, int z, int[] front, Cells cells) {
        int[] best = null;
        double bestScore = Double.MAX_VALUE;
        for (int dy = 0; dy <= 6; dy = dy <= 0 ? 1 - dy : -dy) {
            if (Math.abs(dy) > 3) {
                break;
            }
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    if (dx == 0 && dz == 0 && dy == 0) {
                        continue;
                    }
                    if (!cells.standable(x + dx, y + dy, z + dz)) {
                        continue;
                    }
                    double distance = Math.sqrt(dx * dx + dz * dz) + 0.75 * Math.abs(dy);
                    boolean inFront = dx * front[0] + dz * front[1] > 0;
                    double score = distance + (inFront ? 0 : 2.5);
                    if (score < bestScore) {
                        bestScore = score;
                        best = new int[] {x + dx, y + dy, z + dz};
                    }
                }
            }
        }
        return best;
    }
}
