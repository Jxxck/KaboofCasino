package de.tigges.kaboofcasino.game.derby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.wager.ItemAmount;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * RTP of the duck derby through the shipped path: DerbyRules.draw (winner, seed, plan) with a seeded
 * java.util.Random as nextInt/nextLong, the runner-up of the plan and DerbyRules.payout per stake stack. The exact
 * RTP per item staked is 3p + (1-p)/3 = (2c + 25) / 75 (p = 1/4 for natural odds; tools/rtp/DerbyRtp.java). The
 * planner is the slow part (~100 plans/s on one core, 8-10 ms per plan), so each scenario runs
 * {@value #RACES_DEFAULT} races, ~15 s (environment KABOOF_DERBY_RACES overrides) and asserts within 4 standard
 * errors: the RTP (4 SE ~0.14 for natural odds at the default), the win rate p and the push rate (1-p)/3, which is
 * the planner's share (2nd place uniform among the three non-winners; DerbyRtp mc measures it to +-0.002).
 */
class DerbyRtpSimulationTest {

    static final int RACES_DEFAULT = 1500;
    private static final int RACES = scaled("KABOOF_DERBY_RACES", RACES_DEFAULT);
    private static final List<String> ITEMS = List.of("minecraft:diamond", "minecraft:iron_ingot",
            "minecraft:emerald_block");

    static int scaled(String env, int fallback) {
        String v = System.getenv(env);
        return v == null || v.isBlank() ? fallback : Integer.parseInt(v.trim());
    }

    private static void simulate(int chance, long seed) {
        Random rules = new Random(seed); // the draw's generator, as the controller hands it to DerbyRules.draw
        Random players = new Random(~seed); // the player's lane and stakes
        long staked = 0;
        long returned = 0;
        double squares = 0;
        long wins = 0;
        long pushes = 0;
        long t0 = System.nanoTime();
        for (int i = 0; i < RACES; i++) {
            int lane = players.nextInt(4);
            List<ItemAmount> stakes = new ArrayList<>();
            for (int s = 1 + players.nextInt(3); s > 0; s--) {
                stakes.add(new ItemAmount(ITEMS.get(players.nextInt(ITEMS.size())), 1 + players.nextInt(64)));
            }
            DerbyRules.Race race = DerbyRules.draw(false, lane, chance, rules::nextInt, rules::nextLong);
            int second = race.second();
            assertTrue(second >= 0 && second < 4);
            assertNotEquals(race.winner(), second);
            long in = 0;
            long out = 0;
            for (ItemAmount stake : stakes) {
                in += stake.count();
                for (ItemAmount paid : DerbyRules.payout(stake, lane, race.winner(), second)) {
                    assertEquals(stake.item(), paid.item(), "paid in the staked item");
                    out += paid.count();
                }
            }
            staked += in;
            returned += out;
            squares += (double) in * in;
            if (lane == race.winner()) {
                wins++;
                assertEquals(3 * in, out);
            } else if (lane == second) {
                pushes++;
                assertEquals(in, out);
            } else {
                assertEquals(0, out);
            }
        }
        double secs = (System.nanoTime() - t0) / 1e9;
        double p = chance < 0 ? 0.25 : chance / 100.0;
        double exact = 3 * p + (1 - p) / 3;
        double secondMoment = 9 * p + (1 - p) / 3;
        // ratio estimator sum(m x) / sum(x) with the per-race multiplier m independent of the stake x
        double sigma = Math.sqrt(secondMoment - exact * exact) * Math.sqrt(squares) / staked;
        double rtp = (double) returned / staked;
        double winRate = (double) wins / RACES;
        double pushRate = (double) pushes / RACES;
        double pushExact = (1 - p) / 3;
        System.out.printf(Locale.ROOT, "derby chance %d: %,d races in %.1f s (%.0f plans/s): RTP %.4f (exact %.4f,"
                + " 4 SE %.4f), win %.4f (%.4f), 2nd %.4f (%.4f)%n", chance, RACES, secs, RACES / secs, rtp, exact,
                4 * sigma, winRate, p, pushRate, pushExact);
        assertTrue(Math.abs(rtp - exact) < 4 * sigma, "RTP " + rtp + " vs " + exact + " (4 SE " + 4 * sigma + ")");
        assertEquals(p, winRate, 4 * Math.sqrt(p * (1 - p) / RACES) + 1e-12, "win rate");
        assertEquals(pushExact, pushRate, 4 * Math.sqrt(pushExact * (1 - pushExact) / RACES) + 1e-12,
                "2nd-place rate");
    }

    @Test
    void naturalOddsReturnEverything() {
        simulate(-1, 20261107L);
    }

    @Test
    void forcedChance10Returns60Percent() {
        simulate(10, 42L);
    }

    @Test
    void forcedChance50Returns167Percent() {
        simulate(50, 7L);
    }
}
