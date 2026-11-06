package de.tigges.kaboofcasino.visual;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntSupplier;

/**
 * A global budget for idle light updates ({@code lights.max-updates-per-tick}): every update resends the machine's
 * lights item (all tint colours) to everyone tracking it, so the packet volume must not grow with the number of
 * machines. A machine that is due (its idle cadence passed and a player is near) asks every tick until it is
 * granted; it queues once, and a request is granted only while it is among the first free places of the queue, so
 * the machines take turns in the order they became due, whatever the iteration order. With few machines nobody
 * waits and every machine keeps its cadence. Pure; thread: MAIN.
 */
public final class LightBudget {

    private final IntSupplier perTick;
    private final ArrayDeque<UUID> queue = new ArrayDeque<>();
    private final Map<UUID, Long> lastRequest = new HashMap<>();
    private long tick = Long.MIN_VALUE;
    private int used;

    public LightBudget(IntSupplier perTick) {
        this.perTick = perTick;
    }

    /** {@code machine} is due at tick {@code now}; true = send its update now. */
    public boolean grant(UUID machine, long now) {
        if (now != tick) {
            tick = now;
            used = 0;
            purge(now);
        }
        if (lastRequest.put(machine, now) == null) {
            queue.addLast(machine);
        }
        int free = perTick.getAsInt() - used;
        if (free <= 0) {
            return false;
        }
        int index = 0;
        for (UUID id : queue) {
            if (id.equals(machine)) {
                break;
            }
            index++;
        }
        if (index >= free) {
            return false;
        }
        queue.remove(machine);
        lastRequest.remove(machine);
        used++;
        return true;
    }

    /** Machines that stopped asking (no player near any more, unloaded) leave the queue after one tick. */
    private void purge(long now) {
        for (Iterator<UUID> it = queue.iterator(); it.hasNext(); ) {
            UUID id = it.next();
            Long last = lastRequest.get(id);
            if (last == null || last < now - 1) {
                it.remove();
                lastRequest.remove(id);
            }
        }
    }

    int waiting() {
        return queue.size();
    }
}
