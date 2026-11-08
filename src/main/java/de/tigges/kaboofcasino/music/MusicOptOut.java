package de.tigges.kaboofcasino.music;

import io.papermc.paper.persistence.PersistentDataContainerView;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/**
 * A player's "no lounge music" choice ({@code /casino musik aus|an}), kept in his PDC ({@code kaboofcasino:music_off},
 * a byte 1) and therefore in his player file. It applies from the next login: the configuration phase then offers no
 * music pack, so the music module plays nothing for him. The configuration phase has no player entity yet, so the
 * choice is read from a set remembered since the plugin started (filled at every join and change) or, for the first
 * login after a restart, from the player file ({@code OfflinePlayer#getPersistentDataContainer}, read-only).
 * Thread: the setters MAIN; {@link #optedOut} any thread.
 */
public final class MusicOptOut implements org.bukkit.event.Listener {

    static final String KEY = "music_off";

    private final NamespacedKey key;
    /** Known choices since the plugin started: true = off. */
    private final java.util.Map<UUID, Boolean> known = new ConcurrentHashMap<>();

    public MusicOptOut(org.bukkit.plugin.Plugin plugin) {
        this.key = new NamespacedKey(plugin, KEY);
    }

    /** Sets the choice in the player's PDC (saved with the player) and remembers it. Thread: MAIN. */
    public void set(Player player, boolean off) {
        if (off) {
            player.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        } else {
            player.getPersistentDataContainer().remove(key);
        }
        known.put(player.getUniqueId(), off);
    }

    /** Remembers the choice of a player who joined. Thread: MAIN. */
    public void remember(Player player) {
        known.put(player.getUniqueId(), read(player.getPersistentDataContainer()));
    }

    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        remember(event.getPlayer());
    }

    /** The choice of an online player, from his PDC. */
    public boolean optedOut(Player player) {
        return read(player.getPersistentDataContainer());
    }

    /**
     * The choice for a player who is logging in (configuration phase): remembered, else from his player file; any
     * error counts as "music on" (the offer stays optional anyway).
     */
    public boolean optedOut(UUID player) {
        Boolean k = known.get(player);
        if (k != null) {
            return k;
        }
        try {
            return read(Bukkit.getOfflinePlayer(player).getPersistentDataContainer());
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Players whose choice is remembered as "off" (tests). */
    Set<UUID> rememberedOff() {
        Set<UUID> out = ConcurrentHashMap.newKeySet();
        known.forEach((id, off) -> {
            if (off) {
                out.add(id);
            }
        });
        return out;
    }

    private boolean read(PersistentDataContainerView pdc) {
        Byte b = pdc.get(key, PersistentDataType.BYTE);
        return b != null && b == 1;
    }
}
