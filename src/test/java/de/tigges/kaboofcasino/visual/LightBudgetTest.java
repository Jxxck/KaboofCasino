package de.tigges.kaboofcasino.visual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Safety 4: idle light updates are bounded per tick and handed out in turns. */
class LightBudgetTest {

    @Test
    void atMostTheBudgetPerTickAndEveryMachineGetsItsTurn() {
        LightBudget budget = new LightBudget(() -> 4);
        List<UUID> machines = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            machines.add(UUID.randomUUID());
        }
        Map<UUID, Integer> granted = new HashMap<>();
        Map<UUID, Long> lastUpdate = new HashMap<>();
        long worstWait = 0;
        for (long tick = 0; tick < 400; tick++) {
            int thisTick = 0;
            // always the same iteration order: earlier machines must not starve later ones
            for (UUID m : machines) {
                long last = lastUpdate.getOrDefault(m, -100L);
                if (tick - last >= 4 && budget.grant(m, tick)) {
                    thisTick++;
                    if (lastUpdate.containsKey(m)) {
                        worstWait = Math.max(worstWait, tick - last);
                    }
                    lastUpdate.put(m, tick);
                    granted.merge(m, 1, Integer::sum);
                }
            }
            assertTrue(thisTick <= 4, "tick " + tick + ": " + thisTick + " updates");
        }
        int min = granted.values().stream().mapToInt(Integer::intValue).min().orElse(0);
        int max = granted.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        assertEquals(40, granted.size(), "every machine was updated");
        assertTrue(max - min <= 1, "fair: " + min + ".." + max);
        assertTrue(worstWait <= 11, "40 machines / 4 per tick: about 10 ticks between updates, was " + worstWait);
    }

    @Test
    void fewMachinesKeepTheirCadence() {
        LightBudget budget = new LightBudget(() -> 4);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        for (long tick = 0; tick < 40; tick += 4) {
            assertTrue(budget.grant(a, tick));
            assertTrue(budget.grant(b, tick));
        }
    }

    @Test
    void machinesThatStopAskingLeaveTheQueue() {
        LightBudget budget = new LightBudget(() -> 1);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertTrue(budget.grant(a, 0));
        assertTrue(!budget.grant(b, 0));
        // b stops asking (player left); a is due again two ticks later and is not blocked by b
        assertTrue(budget.grant(a, 3));
        assertEquals(0, budget.waiting());
    }
}
