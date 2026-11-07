package de.tigges.kaboofcasino.game.derby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The race planner ({@code DuckDerbyRace}, verbatim port of the mod's class, games.md 7 "Random source & race model").
 * Golden values come from an independent transcription of the mod's source (tools/rtp/DerbyRtp.java golden), which
 * also matched the plugin's port and the mod's own bytecode bit for bit on many seeds (DerbyRtp cross). Then
 * invariants over {@value #PLANS_DEFAULT} random plans, ~20 s (environment KABOOF_DERBY_PLANS overrides, e.g. 20000
 * for ~200 s; the planner runs at ~100 plans/s).
 */
class DuckDerbyRaceTest {

    static final int PLANS_DEFAULT = 2000;

    private static void pose(DuckDerbyRace.Plan plan, int lane, double elapsed, double z, double pitch, double lift) {
        DuckDerbyRace.Pose out = new DuckDerbyRace.Pose();
        plan.pose(lane, elapsed, out);
        String at = "pose(" + lane + ", " + elapsed + ")";
        assertEquals(z, out.z, at + " z");
        assertEquals(pitch, out.pitch, at + " pitch");
        assertEquals(lift, out.lift, at + " lift");
    }

    private static void ticks(DuckDerbyRace.Plan plan, int[] finishTicks, int[] ranks, int second, int returnStart) {
        for (int lane = 0; lane < 4; lane++) {
            assertEquals(finishTicks[lane], plan.finishTick(lane), "finishTick " + lane);
            assertEquals(ranks[lane], plan.rank(lane), "rank " + lane);
        }
        assertEquals(second, DerbyRules.runnerUp(plan));
        assertEquals(140, plan.winnerFinishTick());
        assertEquals(154, plan.resultTick());
        assertEquals(returnStart, plan.returnStartTick());
        assertEquals(returnStart + 36, plan.returnEndTick());
        assertEquals(returnStart + 46, plan.endTick());
    }

    private static void quacks(DuckDerbyRace.Plan plan, Object[][] expected) {
        List<DuckDerbyRace.Quack> q = plan.quacks();
        assertEquals(expected.length, q.size(), "quack count");
        for (int i = 0; i < expected.length; i++) {
            assertEquals(new DuckDerbyRace.Quack((int) expected[i][0], (int) expected[i][1], (float) expected[i][2],
                    (float) expected[i][3]), q.get(i), "quack " + i);
        }
    }

    @Test
    void goldenPhotoFinishYellowWins() {
        DuckDerbyRace.Plan plan = DuckDerbyRace.plan(0, 0L); // story photo finish, attempt 25 of 48 chosen
        ticks(plan, new int[] {140, 167, 147, 160}, new int[] {0, 3, 1, 2}, 2, 211);
        pose(plan, 1, 12.5, 56.5, 3.4174748241398955E-4, 5.858528269954106E-4); // gate wobble
        pose(plan, 2, 23.0, 56.5, -0.05069997365316059, 0.08691424054827528);
        pose(plan, 3, 100.0, 23.445467681228187, -0.2666201567869599, 0.0);
        pose(plan, 0, 140.0, 10.5, 0.1394169516131295, 0.0); // the winner's beak on the line
        pose(plan, 0, 150.0, 8.104709011174009, 0.24455579873590966, 0.5634651136828593); // celebration bob
        pose(plan, 2, 150.0, 9.643823838633367, -0.18748027885938748, 0.0);
        pose(plan, 0, 175.5, 8.000032077195051, 0.24000000033284752, 0.0);
        pose(plan, 0, 202.0, 8.000000007155052, 0.12000000000000001, 0.0); // tilt fading before the return
        pose(plan, 1, 229.0, 32.49708525370286, 0.0, 0.0); // gliding back
        pose(plan, 3, 247.0, 56.5, 0.0, 0.0); // home
        assertEquals(0.5637669401816108, plan.progress(2, 100.0));
        assertEquals(0.011232960749919085, plan.pace(2, 100.0));
        assertEquals(0.5157937663971696, plan.progress(3, 229.0));
        assertEquals(0.4375, plan.gate(8.5));
        assertEquals(0.75, plan.gate(11.0));
        assertEquals(1.0, plan.gate(247.0));
        assertEquals(0.7975, plan.gate(251.5));
        assertEquals(0.0, plan.gate(257.0));
        quacks(plan, new Object[][] {{11, 2, 0.8f, 1.3039706f}, {14, 0, 0.8f, 1.1548054f}, {18, 1, 0.8f, 0.98165643f},
            {38, 1, 0.7457752f, 0.9640925f}, {46, 0, 0.7456952f, 1.127032f}, {68, 2, 0.7286792f, 1.2137896f},
            {73, 1, 0.81855065f, 0.95739216f}, {81, 0, 0.68203187f, 1.1793798f}, {92, 2, 0.82759905f, 1.2610264f},
            {95, 1, 0.8466589f, 0.9703377f}, {106, 3, 0.8432469f, 0.8161374f}, {112, 0, 0.8321442f, 1.172014f},
            {120, 2, 0.7682148f, 1.236263f}, {123, 1, 0.71403736f, 0.97779465f}, {132, 3, 0.7203424f, 0.76820797f},
            {140, 0, 1.0f, 1.209562f}, {147, 2, 0.75f, 1.2468396f}});
    }

    @Test
    void goldenComebackWhiteWins() {
        DuckDerbyRace.Plan plan = DuckDerbyRace.plan(2, -1L); // story comeback, attempt 7
        ticks(plan, new int[] {160, 164, 140, 147}, new int[] {2, 3, 0, 1}, 3, 208);
        pose(plan, 0, 100.0, 26.319077543280038, -0.23497392286801208, 0.0); // yellow still ahead of the winner
        pose(plan, 2, 100.0, 28.542988603161348, -0.29272623312855284, 0.0);
        pose(plan, 2, 150.0, 8.909896795827052, 0.21125686732230073, 0.5634651136828593);
        pose(plan, 3, 150.0, 9.233005401839314, -0.18749982113651528, 0.0);
        pose(plan, 2, 199.0, 8.661031391331214, 0.11999999989935434, 0.0);
        pose(plan, 3, 226.0, 32.25000071628262, 0.0, 0.0);
        pose(plan, 1, 244.0, 56.5, 0.0, 0.0);
        assertEquals(1.0, plan.gate(244.0));
        assertEquals(0.7975, plan.gate(248.5));
        assertEquals(0.0, plan.gate(254.0));
        quacks(plan, new Object[][] {{11, 2, 0.8f, 1.291491f}, {15, 0, 0.8f, 1.1332664f},
            {34, 0, 0.7946458f, 1.178748f}, {40, 3, 0.6615103f, 0.774669f}, {45, 2, 0.6805796f, 1.2375797f},
            {48, 1, 0.787701f, 0.99849117f},
            {68, 0, 0.7180848f, 1.1153225f}, {71, 2, 0.7909098f, 1.2789252f}, {74, 1, 0.7687384f, 0.96577746f},
            {98, 0, 0.6846383f, 1.1337373f}, {102, 1, 0.7065279f, 0.9556653f}, {105, 2, 0.6734578f, 1.2309324f},
            {124, 0, 0.73399746f, 1.1078099f}, {128, 2, 0.8164683f, 1.2252657f}, {131, 3, 0.81998557f, 0.8186443f},
            {135, 1, 0.65684575f, 0.9711765f}, {140, 2, 1.0f, 1.3352594f}, {143, 0, 0.73495704f, 1.1105533f},
            {147, 3, 0.75f, 0.77283245f}});
    }

    @Test
    void goldenFrontRunnerWhiteWins() {
        DuckDerbyRace.Plan plan = DuckDerbyRace.plan(2, 8L); // story front-runner, 45 of 48 attempts valid, attempt 2
        ticks(plan, new int[] {146, 167, 140, 157}, new int[] {1, 3, 0, 2}, 0, 211);
        pose(plan, 2, 100.0, 23.32506552548473, -0.03318056685636178, 0.0);
        pose(plan, 3, 100.0, 22.057647154957635, -0.14530545662514832, 0.0);
        pose(plan, 2, 140.0, 10.5, 0.011082107547286902, 0.0);
        pose(plan, 0, 140.0, 12.308288295581711, -0.28605052734257436, 0.0);
        pose(plan, 2, 150.0, 9.819522045536694, 0.23676715740525295, 0.5634651136828593);
        pose(plan, 2, 175.5, 9.713664585160863, 0.23999985459262285, 0.0);
        pose(plan, 2, 202.0, 9.713018481427518, 0.11999999999637315, 0.0);
        pose(plan, 0, 229.0, 32.734751062242125, 0.0, 0.0);
        quacks(plan, new Object[][] {{12, 1, 0.8f, 0.9703926f}, {18, 3, 0.8f, 0.78406996f},
            {42, 0, 0.6377184f, 1.0974187f}, {45, 2, 0.70513755f, 1.2674879f}, {66, 1, 0.7250882f, 0.9863736f},
            {71, 3, 0.76463467f, 0.8292762f}, {79, 0, 0.676347f, 1.1334612f}, {87, 1, 0.64000106f, 0.9342665f},
            {106, 3, 0.7618607f, 0.78549445f}, {112, 0, 0.76355124f, 1.16102f}, {136, 0, 0.8355719f, 1.1754717f},
            {140, 2, 1.0f, 1.3222445f}, {146, 0, 0.75f, 1.1602947f}});
    }

    @Test
    void goldenTicksOfMoreSeeds() {
        ticks(DuckDerbyRace.plan(1, 20261107L), new int[] {161, 140, 169, 147}, new int[] {2, 0, 3, 1}, 3, 213);
        ticks(DuckDerbyRace.plan(3, Long.MIN_VALUE), new int[] {151, 146, 167, 140}, new int[] {2, 1, 3, 0}, 1, 211);
    }

    @Test
    void rejectsLanesOutsideZeroToThree() {
        assertThrows(IllegalArgumentException.class, () -> DuckDerbyRace.plan(-1, 0L));
        assertThrows(IllegalArgumentException.class, () -> DuckDerbyRace.plan(4, 0L));
    }

    @Test
    void invariantsOverRandomPlans() {
        int plans = DerbyRtpSimulationTest.scaled("KABOOF_DERBY_PLANS", PLANS_DEFAULT);
        Random random = new Random(20261107L);
        long[] offsets = new long[3];
        long t0 = System.nanoTime();
        DuckDerbyRace.Pose out = new DuckDerbyRace.Pose();
        for (int i = 0; i < plans; i++) {
            int winner = i % 4;
            long seed = random.nextLong();
            DuckDerbyRace.Plan plan = DuckDerbyRace.plan(winner, seed);
            String id = "plan(" + winner + ", " + seed + "L)";
            assertNotNull(plan, id);
            assertEquals(winner, plan.winner());
            assertEquals(seed, plan.seed());

            // the winner finishes 116 race ticks after GO, first; the others at distinct later ticks
            assertEquals(0, plan.rank(winner), id);
            assertEquals(24 + 116, plan.finishTick(winner), id);
            int[] byRank = new int[4];
            Set<Integer> finishes = new HashSet<>();
            for (int lane = 0; lane < 4; lane++) {
                assertTrue(finishes.add(plan.finishTick(lane)), id + " distinct finishes");
                int ahead = 0;
                for (int o = 0; o < 4; o++) {
                    if (plan.finishTick(o) < plan.finishTick(lane)) {
                        ahead++;
                    }
                }
                assertEquals(ahead, plan.rank(lane), id);
                byRank[ahead] = lane;
            }
            int second = DerbyRules.runnerUp(plan);
            assertEquals(byRank[1], second, id);
            assertNotEquals(winner, second, id);
            offsets[Math.floorMod(second - winner, 4) - 1]++;
            int f2 = plan.finishTick(byRank[1]) - 24;
            int f3 = plan.finishTick(byRank[2]) - 24;
            int f4 = plan.finishTick(byRank[3]) - 24;
            assertTrue(f2 >= 121 && f2 <= 130, id + " 2nd at race tick " + f2);
            assertTrue(f3 - f2 >= 4 && f3 - f2 <= 15, id + " 3rd " + f3);
            assertTrue(f4 - f3 >= 4 && f4 - f3 <= 17, id + " 4th " + f4);
            assertEquals(24 + f4, plan.lastFinishTick(), id);
            assertEquals(154, plan.resultTick(), id);
            assertEquals(plan.lastFinishTick() + 44, plan.returnStartTick(), id);
            assertEquals(plan.returnStartTick() + 36, plan.returnEndTick(), id);
            assertEquals(plan.returnEndTick() + 10, plan.endTick(), id);
            assertTrue(plan.endTick() >= 243 && plan.endTick() <= 276, id + " end " + plan.endTick());

            int back = plan.returnStartTick();
            int home = plan.returnEndTick();
            for (int lane = 0; lane < 4; lane++) {
                assertEquals(1.0, plan.progress(lane, plan.finishTick(lane)), id + " progress at the line");
                double previous = Double.POSITIVE_INFINITY;
                for (int step = 0; step <= 2 * (home + 12); step++) {
                    double e = step * 0.5;
                    plan.pose(lane, e, out);
                    if (e <= 24) {
                        assertEquals(56.5, out.z, id + " at the start " + e);
                    } else if (e < back) {
                        assertTrue(out.z <= previous, id + " lane " + lane + " swims backwards at " + e);
                        assertTrue(out.z > 0 && out.z < 56.5, id + " z " + out.z);
                    } else {
                        if (e > back) {
                            assertTrue(out.z >= previous, id + " lane " + lane + " return not monotone at " + e);
                        }
                        assertEquals(0.0, out.pitch, id);
                        assertEquals(0.0, out.lift, id);
                    }
                    if (e >= home) {
                        assertEquals(56.5, out.z, id + " back at the start " + e);
                    }
                    if (e < 10) {
                        assertEquals(0.0, out.pitch, id);
                        assertEquals(0.0, out.lift, id);
                    } else if (e < 24) {
                        assertTrue(Math.abs(out.pitch) <= 0.07 && out.lift >= 0 && out.lift <= 0.12, id);
                    } else if (e < back) {
                        double limit = lane == winner ? 0.3 + 0.24 : 0.3;
                        assertTrue(Math.abs(out.pitch) <= limit, id + " pitch " + out.pitch);
                        assertTrue(lane == winner ? out.lift >= 0 && out.lift <= 2 : out.lift == 0, id);
                    }
                    previous = out.z;
                }
            }
            for (int e = 0; e <= home + 12; e++) {
                double gate = plan.gate(e);
                if (e <= 6 || e >= home + 10) {
                    assertEquals(0.0, gate, id + " gate down at " + e);
                } else if (e >= 16 && e <= home) {
                    assertEquals(1.0, gate, id + " gate up at " + e);
                } else {
                    assertTrue(gate > 0 && gate < 1, id + " gate moving at " + e);
                }
            }

            // quacks: sorted, at least 3 ticks apart, the winner's and the runner-up's finish quacks always kept
            List<DuckDerbyRace.Quack> q = plan.quacks();
            boolean winnerQuack = false;
            boolean secondQuack = false;
            for (int k = 0; k < q.size(); k++) {
                DuckDerbyRace.Quack quack = q.get(k);
                if (k > 0) {
                    assertTrue(quack.tick() - q.get(k - 1).tick() >= 3, id + " quacks crowded");
                }
                assertTrue(quack.tick() >= 10 && quack.tick() < plan.lastFinishTick(),
                        id + " quack at " + quack.tick());
                winnerQuack |= quack.tick() == 140 && quack.lane() == winner && quack.volume() == 1.0f;
                secondQuack |= quack.tick() == plan.finishTick(second) && quack.lane() == second
                        && quack.volume() == 0.75f;
                assertTrue(quack.volume() == 1.0f || quack.volume() == 0.75f || quack.volume() == 0.8f
                        || quack.volume() >= 0.62f && quack.volume() <= 0.85f, id);
            }
            assertTrue(winnerQuack && secondQuack, id + " finish quacks");
        }
        double secs = (System.nanoTime() - t0) / 1e9;
        // 2nd place relative to the winner: uniform (+1/+2/+3 lanes), each within 4.5 SE of a third
        double sd = Math.sqrt(plans * (1 / 3.0) * (2 / 3.0));
        for (long o : offsets) {
            assertEquals(plans / 3.0, o, 4.5 * sd, "runner-up offsets " + java.util.Arrays.toString(offsets));
        }
        System.out.printf(java.util.Locale.ROOT, "derby planner: %,d plans in %.1f s (%.0f plans/s), runner-up offsets"
                + " +1/+2/+3 %s%n", plans, secs, plans / secs, java.util.Arrays.toString(offsets));
    }
}
