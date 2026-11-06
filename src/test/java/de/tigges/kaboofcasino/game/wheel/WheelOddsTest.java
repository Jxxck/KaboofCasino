package de.tigges.kaboofcasino.game.wheel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.tigges.kaboofcasino.game.CasinoRandom;
import de.tigges.kaboofcasino.game.OddsTestSupport;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Fortune wheel odds (games.md 8 "RTP"): exact enumeration and 10^7-round simulations of the shipped code path. */
class WheelOddsTest {

    @Test
    void naturalReturnIs250Of256ForEveryStakeSize() {
        long total = 0;
        int weights = 0;
        for (int s = 0; s < 16; s++) {
            total += (long) FortuneWheelGame.quarters(s) * FortuneWheelGame.weight(s);
            weights += FortuneWheelGame.weight(s);
        }
        assertEquals(64, weights);
        assertEquals(250, total, "sum of quarters x weight: RTP = 250 x 4 / (4 x 64 x 4) = 250/256");
        // fair rounding: over the 4 equally likely rolls every stake gets exactly stake x quarters / 4
        for (int stake = 1; stake <= 64; stake++) {
            long paid = 0;
            for (int s = 0; s < 16; s++) {
                for (int roll = 0; roll < 4; roll++) {
                    paid += (long) FortuneWheelGame.payout(stake, FortuneWheelGame.quarters(s), roll)
                            * FortuneWheelGame.weight(s);
                }
            }
            assertEquals(stake * 250L, paid, "stake " + stake);
        }
    }

    @Test
    void naturalDrawFollowsTheWeights() {
        int[] seen = new int[16];
        for (int roll = 0; roll < 64; roll++) {
            int r = roll;
            seen[WheelController.draw(-1, bound -> r)]++;
        }
        for (int s = 0; s < 16; s++) {
            assertEquals(FortuneWheelGame.weight(s), seen[s], "segment " + s);
        }
    }

    @Test
    void forcedPoolsAndTheirReturn() {
        assertEquals(28, WheelController.POOL.wins().length);
        assertEquals(36, WheelController.POOL.losses().length);
        assertEquals(53.0 / 28.0, WheelController.POOL.winningReturn(), 1e-12);
        assertEquals(19.0 / 72.0, WheelController.POOL.losingReturn(), 1e-12);
        for (int p : List.of(0, 25, 40, 50, 100)) {
            double rtp = OddsTestSupport.forcedMean(WheelController.POOL, p,
                    s -> FortuneWheelGame.quarters(s) / 4.0);
            assertEquals(p / 100.0 * 53.0 / 28.0 + (1 - p / 100.0) * 19.0 / 72.0, rtp, 1e-12, "chance " + p);
        }
    }

    /** The shipped path: draw, seed, fair payout per slot; random stakes 1..64 in slots 0..2. */
    private static double simulate(int winChance, long seed) {
        CasinoRandom random = new CasinoRandom(seed);
        long staked = 0;
        long paid = 0;
        for (int i = 0; i < 10_000_000; i++) {
            int segment = WheelController.draw(winChance, random::nextInt);
            long roundSeed = random.nextLong();
            int stake = 1 + random.nextInt(64);
            int slot = random.nextInt(3);
            staked += stake;
            paid += WheelController.payout(roundSeed, slot, stake, FortuneWheelGame.quarters(segment));
        }
        return (double) paid / staked;
    }

    @Test
    void simulationMatchesTheExactReturn() {
        assertEquals(250.0 / 256.0, simulate(-1, 20261106L), 0.002);
        assertEquals(0.5 * 53.0 / 28.0 + 0.5 * 19.0 / 72.0, simulate(50, 7L), 0.003);
    }

    @Test
    void aPartialThatPaysNothingShowsAsNoWin() {
        assertEquals(FortuneWheelGame.Kind.NONE, WheelController.resultKind(1, List.of(0, 0)));
        assertEquals(FortuneWheelGame.Kind.PARTIAL, WheelController.resultKind(1, List.of(0, 1)));
        assertEquals(FortuneWheelGame.Kind.PUSH, WheelController.resultKind(4, List.of(3)));
        assertEquals(FortuneWheelGame.Kind.JACKPOT, WheelController.resultKind(20, List.of(5)));
    }

    @Test
    void settlementTimingFollowsTheMod() {
        FortuneWheelSpin.Plan plan = FortuneWheelSpin.plan(0, 9, 42L);
        assertEquals(plan.resultTick() + 12, WheelController.settleTick(plan, true));
        assertEquals(plan.resultTick(), WheelController.settleTick(plan, false));
        // a loss unlocks at the result (readyTick = resultTick for NONE/PARTIAL/PUSH)
        FortuneWheelSpin.Plan lose = FortuneWheelSpin.plan(0, 1, 42L);
        assertEquals(lose.resultTick(), lose.readyTick());
    }
}
