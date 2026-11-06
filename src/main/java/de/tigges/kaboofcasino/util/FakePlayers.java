package de.tigges.kaboofcasino.util;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

/**
 * Detects EgorsFakePlayer bots (server.md F18): {@code Bot extends ServerPlayer}, joined through
 * {@code placeNewPlayer} (no configuration phase, so the pack is never sent to them). They may never play, claim
 * machines or take part in rounds. Matched by the handle's class (also a subclass) and remembered by UUID; never by
 * a name (a real player could be named like a bot or join a team).
 */
public final class FakePlayers {

    static final String BOT_CLASS = "dev.egor.fakeplayer.Bot";
    private static final Set<UUID> KNOWN = ConcurrentHashMap.newKeySet();

    private FakePlayers() {
    }

    public static boolean isFake(Player player) {
        UUID id = player.getUniqueId();
        if (KNOWN.contains(id)) {
            return true;
        }
        Object handle = handle(player);
        if (handle != null && isBotClass(handle.getClass())) {
            KNOWN.add(id);
            return true;
        }
        return false;
    }

    /** True if {@code type} is EgorsFakePlayer's bot class or a subclass of it. */
    static boolean isBotClass(Class<?> type) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            if (c.getName().equals(BOT_CLASS)) {
                return true;
            }
        }
        return false;
    }

    private static Object handle(Player player) {
        try {
            Method handle = player.getClass().getMethod("getHandle");
            return handle.invoke(player);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** A bot left: its UUID may later belong to a real login of the same offline name. */
    public static void forget(UUID player) {
        KNOWN.remove(player);
    }
}
