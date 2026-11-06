package de.tigges.kaboofcasino.game.blackjack;

import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.machine.Facing;
import java.util.function.LongUnaryOperator;
import org.bukkit.util.Transformation;
import org.junit.jupiter.api.Test;

/** Idle dealer traffic: metadata packets per second per table (one per changed part per send). */
class BlackjackIdleCadenceTest {

    private static double packetsPerSecond(LongUnaryOperator every, long phase, long start) {
        BlackjackTableState idle = new BlackjackTableState();
        Transformation[] sent = new Transformation[BlackjackDealerRig.PARTS.length];
        long packets = 0;
        long sends = 0;
        long ticks = 480L * 20;
        long last = Long.MIN_VALUE / 2;
        for (long now = start; now < start + ticks; now++) {
            long cadence = every.applyAsLong(now);
            if (now - last < cadence) {
                continue;
            }
            last = now;
            sends++;
            Transformation[] t = BlackjackDealerRig.transforms(Facing.SOUTH,
                    BlackjackDealerRig.pose(idle, (double) (now + cadence + 1), phase));
            for (int i = 0; i < t.length; i++) {
                if (!t[i].equals(sent[i])) {
                    sent[i] = t[i];
                    packets++;
                }
            }
        }
        System.out.printf("sends/s %.2f packets/s %.2f%n", sends * 20.0 / ticks, packets * 20.0 / ticks);
        return packets * 20.0 / ticks;
    }

    @Test
    void adaptiveIdleCadenceHalvesTheTraffic() {
        long phase = BlackjackDealerRig.phase(29, -57, 6);
        // a young world (exact float times) and the live world's game time (33.4 M: the mod's float clock steps by 2)
        for (long start : new long[] {1_000_000L, 33_389_712L}) {
            double before = packetsPerSecond(now -> 4, phase, start);
            double after = packetsPerSecond(now -> BlackjackRig.idleEvery(now, phase), phase, start);
            System.out.printf("game time %d: idle dealer packets/s per table: every 4 ticks %.1f, adaptive %.1f%n",
                    start, before, after);
            assertTrue(after < before * 0.85, before + " -> " + after);
        }
    }
}
