package de.tigges.kaboofcasino.pack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.resource.ResourcePackStatus;

/**
 * The pack status each player reached in the configuration phase (written on the configuration thread, read on MAIN).
 * A player without a loaded pack sees barriers and boxes, so the casino refuses him with a hint. New pack versions
 * take effect only at the next login (render.md 8.3).
 *
 * <p>The optional music pack has its own status: only players who loaded it get music (sounds of an unknown event
 * would only log client errors). It is never required, so it never refuses anybody anything else.
 */
public final class PackSessions {

    private final Map<UUID, ResourcePackStatus> status = new ConcurrentHashMap<>();
    private final Map<UUID, ResourcePackStatus> music = new ConcurrentHashMap<>();
    private volatile boolean required = true;

    void put(UUID player, ResourcePackStatus s) {
        status.put(player, s);
    }

    void putMusic(UUID player, ResourcePackStatus s) {
        if (s == null) {
            music.remove(player);
        } else {
            music.put(player, s);
        }
    }

    /** A player who was already online when the plugin (re)loaded and has a loaded pack. */
    public void markLoaded(UUID player) {
        status.put(player, ResourcePackStatus.SUCCESSFULLY_LOADED);
    }

    public void forget(UUID player) {
        status.remove(player);
        music.remove(player);
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

    /** True only if the music pack reached SUCCESSFULLY_LOADED at this login (unknown after a plugin reload). */
    public boolean hasMusic(UUID player) {
        return music.get(player) == ResourcePackStatus.SUCCESSFULLY_LOADED;
    }

    /** The music pack's final status at this login, null if it was not offered or not answered. */
    public ResourcePackStatus musicStatus(UUID player) {
        return music.get(player);
    }
}
