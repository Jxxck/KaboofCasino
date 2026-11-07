package de.tigges.kaboofcasino.game.derby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.machine.Facing;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongPredicate;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

/**
 * The duck/gate cadence by the nearest player: every tick within 64 blocks, every 4th tick (budget-granted) up to the
 * tracking range, nothing beyond; and the resulting traffic of whole races (one metadata update per changed display
 * per send, which is what every tracking player receives).
 */
class DerbyRigCadenceTest {

    private static final long START = 33_389_712L;

    private static double sq(double d) {
        return d * d;
    }

    @Test
    void tiersFollowTheRendererAndTheTracker() {
        int range = DerbyRigCadence.trackingRange(10);
        assertEquals(128, range);
        assertEquals(64, DerbyRigCadence.trackingRange(4), "the tracker never reaches beyond the view distance");
        assertEquals(DerbyRigCadence.Tier.NEAR, DerbyRigCadence.tier(sq(20), sq(20), range));
        assertEquals(DerbyRigCadence.Tier.NEAR, DerbyRigCadence.tier(sq(64), sq(64), range));
        assertEquals(DerbyRigCadence.Tier.FAR, DerbyRigCadence.tier(sq(64.01), sq(64.01), range));
        assertEquals(DerbyRigCadence.Tier.FAR, DerbyRigCadence.tier(sq(100), sq(100), range));
        // the tracker measures horizontally: 128 blocks away and 40 up is still tracked
        assertEquals(DerbyRigCadence.Tier.FAR, DerbyRigCadence.tier(sq(128) + sq(40), sq(128), range));
        assertEquals(DerbyRigCadence.Tier.NONE, DerbyRigCadence.tier(sq(129), sq(129), range));
        assertEquals(DerbyRigCadence.Tier.NONE, DerbyRigCadence.tier(Double.POSITIVE_INFINITY,
                Double.POSITIVE_INFINITY, range));
    }

    @Test
    void farSendsAskTheBudgetOnlyWhenDue() {
        DerbyRigCadence c = new DerbyRigCadence();
        List<Long> asked = new ArrayList<>();
        boolean[] answer = {true};
        LongPredicate grant = now -> {
            asked.add(now);
            return answer[0];
        };
        assertEquals(4, c.step(100, DerbyRigCadence.Tier.FAR, grant));
        assertEquals(0, c.step(101, DerbyRigCadence.Tier.FAR, grant));
        assertEquals(0, c.step(103, DerbyRigCadence.Tier.FAR, grant));
        assertEquals(List.of(100L), asked, "not asked before the next send is due");
        answer[0] = false;
        assertEquals(0, c.step(104, DerbyRigCadence.Tier.FAR, grant));
        assertEquals(0, c.step(105, DerbyRigCadence.Tier.FAR, grant));
        answer[0] = true;
        assertEquals(4, c.step(106, DerbyRigCadence.Tier.FAR, grant), "granted late: sent then");
        assertEquals(List.of(100L, 104L, 105L, 106L), asked, "retried every tick until granted");
        assertEquals(0, c.step(109, DerbyRigCadence.Tier.FAR, grant));
        // near never asks and sends every tick; nobody tracking: nothing
        asked.clear();
        assertEquals(1, c.step(110, DerbyRigCadence.Tier.NEAR, grant));
        assertEquals(1, c.step(111, DerbyRigCadence.Tier.NEAR, grant));
        assertEquals(0, c.step(112, DerbyRigCadence.Tier.NONE, grant));
        assertTrue(asked.isEmpty());
        // back to far: the next far send counts from the last near one
        assertEquals(0, c.step(113, DerbyRigCadence.Tier.FAR, grant));
        assertEquals(4, c.step(115, DerbyRigCadence.Tier.FAR, grant));
    }

    /** One display update per changed duck/gate transformation per send, over whole races. */
    private static double[] traffic(DerbyRigCadence.Tier tier, LongPredicate grant, double[] worstStep) {
        long updates = 0;
        long ticks = 0;
        for (DerbyView race : DerbyGeometryTest.races()) {
            DerbyRigCadence cadence = new DerbyRigCadence();
            Transformation[] sent = new Transformation[5];
            for (int lane = 0; lane < 4; lane++) {
                sent[lane] = DerbyRig.duckTransform(Facing.EAST, lane, DerbyRig.pose(race, lane, START - 1));
            }
            sent[4] = DerbyRig.gateTransform(Facing.EAST, DerbyRig.gate(race, START - 1));
            long end = START + race.plan().endTick() + 10;
            for (long now = START - 1; now <= end; now++) {
                int ahead = cadence.step(now, tier, grant);
                if (ahead == 0) {
                    continue;
                }
                Transformation[] t = new Transformation[5];
                for (int lane = 0; lane < 4; lane++) {
                    t[lane] = DerbyRig.duckTransform(Facing.EAST, lane, DerbyRig.pose(race, lane, now + ahead));
                }
                t[4] = DerbyRig.gateTransform(Facing.EAST, DerbyRig.gate(race, now + ahead));
                for (int i = 0; i < 5; i++) {
                    if (!t[i].equals(sent[i])) {
                        if (i < 4) {
                            worstStep[0] = Math.max(worstStep[0],
                                    angle(sent[i].getLeftRotation(), t[i].getLeftRotation()));
                        }
                        sent[i] = t[i];
                        updates++;
                    }
                }
            }
            // whatever the cadence, the last send leaves everything at rest
            for (int lane = 0; lane < 4; lane++) {
                assertEquals(DerbyRig.duckTransform(Facing.EAST, lane, DerbyRig.pose(DerbyView.idle(), lane, 0)),
                        sent[lane]);
            }
            assertEquals(DerbyRig.gateTransform(Facing.EAST, 0.0), sent[4]);
            ticks += race.plan().endTick();
        }
        double perTick = (double) updates / ticks;
        return new double[] {perTick, perTick * 20.0};
    }

    private static double angle(Quaternionf a, Quaternionf b) {
        double dot = Math.abs(a.x * b.x + a.y * b.y + a.z * b.z + a.w * b.w);
        return Math.toDegrees(2.0 * Math.acos(Math.min(1.0, dot)));
    }

    @Test
    void raceTrafficPerTier() {
        double[] nearStep = {0};
        double[] farStep = {0};
        double[] slowStep = {0};
        double[] near = traffic(DerbyRigCadence.Tier.NEAR, t -> true, nearStep);
        double[] far = traffic(DerbyRigCadence.Tier.FAR, t -> true, farStep);
        // a busy budget that grants every other request
        long[] calls = {0};
        double[] busy = traffic(DerbyRigCadence.Tier.FAR, t -> calls[0]++ % 2 == 1, slowStep);
        double[] none = traffic(DerbyRigCadence.Tier.NONE, t -> true, new double[1]);
        System.out.printf("derby race traffic: 1 player at 20 blocks %.2f updates/tick = %.1f/s; at 100 blocks "
                + "%.2f/tick = %.1f/s (budget granting every other ask: %.2f/tick); nobody: %.2f%n",
                near[0], near[1], far[0], far[1], busy[0], none[0]);
        System.out.printf("largest interpolated duck rotation: near %.1f deg, far %.1f deg, far delayed %.1f deg%n",
                nearStep[0], farStep[0], slowStep[0]);
        assertTrue(near[0] > 3.0 && near[0] < 5.0, "near " + near[0]);
        assertTrue(far[0] > 0.6 && far[0] < 1.3, "far " + far[0]);
        assertTrue(busy[0] < far[0], "busy " + busy[0]);
        assertEquals(0.0, none[0]);
        assertTrue(farStep[0] < 90.0 && slowStep[0] < 90.0, "far step " + farStep[0] + " / " + slowStep[0]);
    }
}
