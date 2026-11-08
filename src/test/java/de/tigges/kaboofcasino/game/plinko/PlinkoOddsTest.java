package de.tigges.kaboofcasino.game.plinko;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import de.tigges.kaboofcasino.game.CasinoRandom;
import de.tigges.kaboofcasino.game.FairPayout;
import de.tigges.kaboofcasino.game.OddsTestSupport;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Plinko odds (games.md 9 "RTP"): exact enumeration over the 256 paths and 10^7-ball simulations. */
class PlinkoOddsTest {

    @Test
    void naturalReturnIs998Of1024ForEveryStakeSize() {
        long total = 0;
        for (int path = 0; path < 256; path++) {
            total += PlinkoGame.quarters(PlinkoGame.pocketOf(path));
        }
        assertEquals(998, total / 1, "sum over 256 paths of quarters / 4 / 256 = 998/1024");
        for (int stake : de.tigges.kaboofcasino.game.wheel.WheelOddsTest.stakes()) {
            long paid = 0;
            for (int path = 0; path < 256; path++) {
                for (int roll = 0; roll < 4; roll++) {
                    paid += PlinkoGame.payout(stake, PlinkoGame.quarters(PlinkoGame.pocketOf(path)), roll);
                }
            }
            assertEquals(stake * 998L, paid, "stake " + stake);
        }
    }

    @Test
    void forcedPoolsAndTheirReturn() {
        assertEquals(74, PlinkoController.POOL.wins().length);
        assertEquals(182, PlinkoController.POOL.losses().length);
        assertEquals(88.0 / 37.0, PlinkoController.POOL.winningReturn(), 1e-12);
        assertEquals(21.0 / 52.0, PlinkoController.POOL.losingReturn(), 1e-12);
        for (int p : List.of(0, 25, 30, 50, 100)) {
            double rtp = OddsTestSupport.forcedMean(PlinkoController.POOL, p,
                    path -> PlinkoGame.quarters(PlinkoGame.pocketOf(path)) / 4.0);
            assertEquals(p / 100.0 * 88.0 / 37.0 + (1 - p / 100.0) * 21.0 / 52.0, rtp, 1e-12, "chance " + p);
        }
    }

    private static double simulate(int winChance, long seed) {
        CasinoRandom random = new CasinoRandom(seed);
        long staked = 0;
        long paid = 0;
        for (int i = 0; i < 10_000_000; i++) {
            long roundSeed = random.nextLong();
            int path = PlinkoController.path(winChance, random::nextInt);
            int stake = 1 + random.nextInt(64);
            int slot = random.nextInt(3);
            staked += stake;
            paid += PlinkoController.payout(roundSeed, slot, stake, PlinkoGame.quarters(PlinkoGame.pocketOf(path)));
        }
        return (double) paid / staked;
    }

    @Test
    void simulationMatchesTheExactReturn() {
        assertEquals(998.0 / 1024.0, simulate(-1, 20261106L), 0.002);
        assertEquals(0.3 * 88.0 / 37.0 + 0.7 * 21.0 / 52.0, simulate(30, 9L), 0.003);
    }

    @Test
    void theRoundingRollUsesTheSlotNotTheBall() {
        // games.md 9: balls are numbered over the filled slots, the fair-rounding roll uses the slot index
        long seed = 12345L;
        int bySlot = PlinkoController.payout(seed, 2, 3, 1);
        assertEquals(PlinkoGame.payout(3, 1, FairPayout.roll(seed, 2, 4)), bySlot);
        boolean differs = false;
        for (long s = 0; s < 64 && !differs; s++) {
            differs = FairPayout.roll(s, 0, 4) != FairPayout.roll(s, 2, 4);
        }
        assertNotEquals(false, differs);
    }
}
