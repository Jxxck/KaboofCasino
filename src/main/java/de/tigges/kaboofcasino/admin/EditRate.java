package de.tigges.kaboofcasino.admin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The mod's admin rate limit ({@code CasinoSettings.limited}): at most {@value #PER_SECOND} edits per player in one game
 * second ({@code gameTime / 20}); every further edit in that second is dropped. Pure; thread: MAIN.
 */
final class EditRate {

    static final int PER_SECOND = 20;

    private record Rate(long second, int count) {
    }

    private final Map<UUID, Rate> rates = new HashMap<>();

    /** Counts one edit; true if it is over the limit (and must be dropped). */
    boolean limited(UUID player, long gameTime) {
        long second = Math.floorDiv(gameTime, 20L);
        Rate rate = rates.get(player);
        if (rate == null || rate.second() != second) {
            rates.put(player, new Rate(second, 1));
            return false;
        }
        if (rate.count() >= PER_SECOND) {
            return true;
        }
        rates.put(player, new Rate(second, rate.count() + 1));
        return false;
    }

    void forget(UUID player) {
        rates.remove(player);
    }

    void clear() {
        rates.clear();
    }
}
