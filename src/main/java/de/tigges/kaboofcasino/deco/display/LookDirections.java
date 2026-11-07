package de.tigges.kaboofcasino.deco.display;

import org.bukkit.block.BlockFace;

/**
 * Vanilla's {@code Direction.orderedByNearest(entity)} and {@code BlockPlaceContext.getNearestLookingDirections()}
 * (the order in which wall decor, the sconce and the speaker try their attachments). Floats and {@code Mth.sin/cos}
 * as {@code Math.sin/cos} in float: the order only changes exactly on the diagonals. Pure.
 */
public final class LookDirections {

    private LookDirections() {
    }

    /** {@code Direction.orderedByNearest}: yaw and pitch in degrees (Minecraft: yaw 0 = south, pitch +90 = down). */
    public static BlockFace[] orderedByNearest(float yawDeg, float pitchDeg) {
        float pitch = pitchDeg * (float) (Math.PI / 180.0);
        float yaw = -yawDeg * (float) (Math.PI / 180.0);
        float pitchSin = (float) Math.sin(pitch);
        float pitchCos = (float) Math.cos(pitch);
        float yawSin = (float) Math.sin(yaw);
        float yawCos = (float) Math.cos(yaw);
        boolean xPos = yawSin > 0.0F;
        boolean yPos = pitchSin < 0.0F;
        boolean zPos = yawCos > 0.0F;
        float xYaw = xPos ? yawSin : -yawSin;
        float yMag = yPos ? -pitchSin : pitchSin;
        float zYaw = zPos ? yawCos : -yawCos;
        float xMag = xYaw * pitchCos;
        float zMag = zYaw * pitchCos;
        BlockFace axisX = xPos ? BlockFace.EAST : BlockFace.WEST;
        BlockFace axisY = yPos ? BlockFace.UP : BlockFace.DOWN;
        BlockFace axisZ = zPos ? BlockFace.SOUTH : BlockFace.NORTH;
        if (xYaw > zYaw) {
            if (yMag > xMag) {
                return array(axisY, axisX, axisZ);
            }
            return zMag > yMag ? array(axisX, axisZ, axisY) : array(axisX, axisY, axisZ);
        }
        if (yMag > zMag) {
            return array(axisY, axisZ, axisX);
        }
        return xMag > yMag ? array(axisZ, axisX, axisY) : array(axisZ, axisY, axisX);
    }

    private static BlockFace[] array(BlockFace a, BlockFace b, BlockFace c) {
        return new BlockFace[] {a, b, c, c.getOppositeFace(), b.getOppositeFace(), a.getOppositeFace()};
    }

    /**
     * {@code BlockPlaceContext.getNearestLookingDirections}: the look order, but when the clicked block is not replaced
     * the direction into the clicked block ({@code clickedFace.getOpposite()}) comes first.
     */
    public static BlockFace[] placing(float yawDeg, float pitchDeg, BlockFace clickedFace, boolean replaceClicked) {
        BlockFace[] directions = orderedByNearest(yawDeg, pitchDeg);
        if (replaceClicked) {
            return directions;
        }
        BlockFace into = clickedFace.getOppositeFace();
        int i = 0;
        while (i < directions.length && directions[i] != into) {
            i++;
        }
        if (i > 0 && i < directions.length) {
            System.arraycopy(directions, 0, directions, 1, i);
            directions[0] = into;
        }
        return directions;
    }
}
