package de.tigges.kaboofcasino.game.plinko;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Plinko's balls stay on the board (board px x 0..48, y 0..48 = the 3x3 footprint) on every path and seed, and the
 * ball (radius 1 px at z 12) stays inside the block between the board (z 11) and the post tips (z 13).
 */
class PlinkoGeometryTest {

    @Test
    void ballsStayOnTheBoardOnEveryPath() {
        java.util.Random r = new java.util.Random(3);
        for (int path = 0; path < 256; path++) {
            for (int k = 0; k < 4; k++) {
                PlinkoDrop.Plan plan = PlinkoDrop.plan(new int[] {path, r.nextInt(256), r.nextInt(256)}, r.nextLong());
                for (int b = 0; b < plan.ballCount(); b++) {
                    PlinkoDrop.Ball ball = plan.ball(b);
                    for (double t = 0; t < ball.restTick() + 5; t += 0.25) {
                        double[] p = ball.position(t);
                        assertTrue(p[0] - 1 >= 2 && p[0] + 1 <= 46, "x " + p[0] + " path " + path);
                        assertTrue(p[1] - 1 >= 10 && p[1] + 1 <= 41, "y " + p[1] + " path " + path);
                    }
                    double x = ball.position(ball.restTick() + 5)[0];
                    assertTrue(Math.abs(x - PlinkoGame.pocketX(PlinkoGame.pocketOf(ball.path()))) < 1e-6);
                }
            }
        }
        assertTrue(PlinkoGame.BALL_Z - 1 >= 11 && PlinkoGame.BALL_Z + 1 <= 13);
    }
}
