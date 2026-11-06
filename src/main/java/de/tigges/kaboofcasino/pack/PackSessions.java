package de.tigges.kaboofcasino.pack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.resource.ResourcePackStatus;

/**
 * The pack status each player reached in the configuration phase (written on the configuration thread, read on MAIN).
 * A player without a loaded pack sees barriers and boxes, so the casino refuses him with a hint. New pack versions
 * take effect only at the next login (render.md 8.3).
 */
public final class PackSessions {

    private final Map<UUID, ResourcePackStatus> status = new ConcurrentHashMap<>();
    private volatile boolean required = true;

    void put(UUID player, ResourcePackStatus s) {
        status.put(player, s);
    }

    /** A player who was already online when the plugin (re)loaded and has a loaded pack. */
    public void markLoaded(UUID player) {
        status.put(player, ResourcePackStatus.SUCCESSFULLY_LOADED);
    }

    public void forget(UUID player) {
        status.remove(player);
    }

    /** False when the pack is disabled in the config: then nobody is refused. */
    public void required(boolean value) {
        this.required = value;
    }

    public boolean hasPack(UUID player) {
        return !required || status.get(player) == ResourcePackStatus.SUCCESSFULLY_LOADED;
    }

    public ResourcePackStatus status(UUID player) {
        return status.get(player);
    }
}
