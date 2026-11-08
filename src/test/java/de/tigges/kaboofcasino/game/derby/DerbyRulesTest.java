package de.tigges.kaboofcasino.game.derby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.game.Odds;
import de.tigges.kaboofcasino.wager.ItemAmount;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;

/**
 * The derby's money rules (games.md 7, DuckDerbyBlockEntity.launchPersonal/runnerUp, CasinoOdds.derby): the winner
 * draw, the runner-up, the payout per stake and when group races are allowed. The exact RTP (2c + 25) / 75 per item
 * staked follows from the enumeration below plus the 2nd place being uniform among the non-winners
 * (tools/rtp/DerbyRtp.java; DerbyRtpSimulationTest measures it through the shipped path).
 */
class DerbyRulesTest {

    /** Hands out the scripted numbers in order and records every bound asked for. */
    private static final class Script implements IntUnaryOperator {
        private final int[] values;
        private int next;
        final List<Integer> bounds = new ArrayList<>();

        Script(int... values) {
            this.values = values;
        }

        @Override
        public int applyAsInt(int bound) {
            bounds.add(bound);
            return values[next++];
        }
    }

    /** Hands out seeds in order and counts them. */
    private static final class Seeds implements LongSupplier {
        private final long[] values;
        int used;

        Seeds(long... values) {
            this.values = values;
        }

        @Override
        public long getAsLong() {
            return values[used++];
        }
    }

    /** The mod's CasinoOdds.derby, written out: nextInt(100) < percent -> lane, else nextInt(3) skipping the lane. */
    private static int modDerby(int lane, int percent, int first, int second) {
        if (first < percent) {
            return lane;
        }
        return second < lane ? second : second + 1;
    }

    @Test
    void naturalOddsDrawNextIntOfFourThenOneSeed() {
        for (int roll = 0; roll < 4; roll++) {
            for (int soloLane = 0; soloLane < 4; soloLane++) {
                Script ints = new Script(roll);
                Seeds seeds = new Seeds(1000L + roll);
                DerbyRules.Race race = DerbyRules.draw(false, soloLane, -1, ints, seeds);
                assertEquals(roll, race.winner(), "natural odds: the winner is nextInt(4) whatever the player's lane");
                assertEquals(List.of(4), ints.bounds);
                assertEquals(1, seeds.used, "one seed when the plan exists");
                assertEquals(1000L + roll, race.seed());
                assertNotNull(race.plan());
                assertEquals(roll, race.plan().winner());
                assertEquals(race.seed(), race.plan().seed());
                assertEquals(DerbyRules.runnerUp(race.plan()), race.second());
            }
        }
    }

    @Test
    void groupRacesIgnoreTheChance() {
        for (int chance : new int[] {-1, 0, 10, 25, 50, 100}) {
            for (int roll = 0; roll < 4; roll++) {
                Script ints = new Script(roll);
                DerbyRules.Race race = DerbyRules.draw(true, 2, chance, ints, new Seeds(77L));
                assertEquals(roll, race.winner(), "group, chance " + chance);
                assertEquals(List.of(4), ints.bounds, "group races always draw nextInt(4)");
            }
        }
    }

    @Test
    void forcedChanceIsCasinoOddsDerby() {
        // Odds.derby over every pair of numbers it can consume, every lane and every chance
        for (int chance = 0; chance <= 100; chance++) {
            for (int lane = 0; lane < 4; lane++) {
                long wins = 0;
                long[] others = new long[4];
                for (int a = 0; a < 100; a++) {
                    for (int b = 0; b < 3; b++) {
                        Script ints = new Script(a, b);
                        int w = Odds.derby(lane, chance, ints);
                        assertEquals(modDerby(lane, chance, a, b), w);
                        assertEquals(a < chance ? List.of(100) : List.of(100, 3), ints.bounds);
                        if (w == lane) {
                            wins++;
                        } else {
                            others[w]++;
                        }
                    }
                }
                assertEquals(3L * chance, wins, "P(win) = chance / 100 exactly");
                for (int o = 0; o < 4; o++) {
                    if (o != lane) {
                        assertEquals(100L - chance, others[o], "the other lanes share the rest equally");
                    }
                }
                // RTP over 900: 3 per win (x 3 for the b values) and 1/3 of the losing draws push (2nd place)
                long rtp900 = 9 * wins + (300 - wins);
                assertEquals(12L * (2 * chance + 25), rtp900, "RTP (2c + 25) / 75, chance " + chance);
                double p = chance / 100.0;
                assertEquals(3 * p + (1 - p) / 3, rtp900 / 900.0, 1e-12, "CasinoOdds.expectedReturn(derby)");
            }
        }
        // and DerbyRules.draw routes a solo race with a chance through it (a sample, each draw plans a race)
        for (int chance : new int[] {0, 1, 25, 60, 100}) {
            for (int lane = 0; lane < 4; lane++) {
                for (int a : new int[] {0, Math.max(0, chance - 1), Math.min(99, chance), 99}) {
                    for (int b = 0; b < 3; b++) {
                        Script ints = new Script(a, b);
                        Seeds seeds = new Seeds(31L * a + b);
                        DerbyRules.Race race = DerbyRules.draw(false, lane, chance, ints, seeds);
                        assertEquals(modDerby(lane, chance, a, b), race.winner(),
                                "chance " + chance + " lane " + lane + " a " + a + " b " + b);
                        assertEquals(a < chance ? List.of(100) : List.of(100, 3), ints.bounds);
                        assertEquals(1, seeds.used);
                        if (a < chance) {
                            break; // b is not consumed
                        }
                    }
                }
            }
        }
    }

    @Test
    void forcedChanceRejectsWhatTheModRejects() {
        assertThrows(IllegalArgumentException.class, () -> Odds.derby(-1, 25, new Script(0, 0)));
        assertThrows(IllegalArgumentException.class, () -> Odds.derby(4, 25, new Script(0, 0)));
        assertThrows(IllegalArgumentException.class, () -> Odds.derby(0, 101, new Script(0, 0)));
        assertThrows(IllegalArgumentException.class, () -> Odds.derby(0, -1, new Script(0, 0)));
        assertThrows(IllegalArgumentException.class, () -> Odds.derby(0, 25, new Script(100)));
        assertThrows(IllegalArgumentException.class, () -> Odds.derby(0, 25, new Script(-1)));
        assertThrows(IllegalArgumentException.class, () -> Odds.derby(0, 25, new Script(30, 3)));
        assertThrows(IllegalArgumentException.class,
                () -> DerbyRules.draw(false, 0, 25, new Script(100), new Seeds(1L)));
        assertThrows(IllegalArgumentException.class,
                () -> DerbyRules.draw(false, 0, -1, new Script(4), new Seeds(1L)), "plan(4, seed): lane 4");
    }

    @Test
    void runnerUpIsTheLaneWithRankOne() {
        assertEquals(-1, DerbyRules.runnerUp(null));
        java.util.Random random = new java.util.Random(7);
        for (int i = 0; i < 40; i++) {
            int winner = i % 4;
            DuckDerbyRace.Plan plan = DuckDerbyRace.plan(winner, random.nextLong());
            int second = DerbyRules.runnerUp(plan);
            assertTrue(second >= 0 && second < 4 && second != winner);
            assertEquals(1, plan.rank(second));
            // the second-earliest finish
            int best = Integer.MAX_VALUE;
            for (int lane = 0; lane < 4; lane++) {
                if (lane != winner) {
                    best = Math.min(best, plan.finishTick(lane));
                }
            }
            assertEquals(best, plan.finishTick(second));
        }
    }

    @Test
    void payoutIsThreeTimesForTheWinnerAndTheStakeForSecond() {
        for (String item : List.of("minecraft:diamond", "minecraft:iron_block",
                de.tigges.kaboofcasino.wager.Chips.VALUE)) {
            for (long count : de.tigges.kaboofcasino.game.wheel.WheelOddsTest.stakes()) {
                ItemAmount stake = new ItemAmount(item, count);
                for (int winner = 0; winner < 4; winner++) {
                    for (int second = 0; second < 4; second++) {
                        if (second == winner) {
                            continue;
                        }
                        for (int lane = 0; lane < 4; lane++) {
                            List<ItemAmount> paid = DerbyRules.payout(stake, lane, winner, second);
                            if (lane == winner) {
                                assertEquals(List.of(new ItemAmount(item, 3 * count)), paid);
                            } else if (lane == second) {
                                assertEquals(List.of(new ItemAmount(item, count)), paid);
                            } else {
                                assertEquals(List.of(), paid);
                            }
                        }
                    }
                }
            }
        }
        // no plan -> no runner-up: only the winner is paid
        ItemAmount one = new ItemAmount("minecraft:gold_ingot", 5);
        assertEquals(List.of(new ItemAmount("minecraft:gold_ingot", 15)), DerbyRules.payout(one, 1, 1, -1));
        assertEquals(List.of(), DerbyRules.payout(one, 0, 1, -1));
        assertEquals(3, DerbyRules.PAYOUT_MULTIPLIER);
        assertEquals(1, DerbyRules.SECOND_MULTIPLIER);
    }

    @Test
    void groupRacesOnlyWithNaturalOddsOrExactly25() {
        assertTrue(DerbyRules.groupAllowed(-1));
        assertTrue(DerbyRules.groupAllowed(25));
        for (int chance = 0; chance <= 100; chance++) {
            if (chance != 25) {
                assertFalse(DerbyRules.groupAllowed(chance), "chance " + chance);
            }
        }
    }
}
