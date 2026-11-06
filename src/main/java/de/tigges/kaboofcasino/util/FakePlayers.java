package de.tigges.kaboofcasino.util;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;

/**
 * Detects EgorsFakePlayer bots (server.md F18): {@code Bot extends ServerPlayer}, joined through
 * {@code placeNewPlayer} (no configuration phase, so the pack is never sent to them), address 127.0.0.1:0, member of
 * the main-scoreboard team {@code fakeplayers}. They may never play, claim machines or take part in rounds.
 */
public final class FakePlayers {

    static final String BOT_CLASS = "dev.egor.fakeplayer.Bot";

    private FakePlayers() {
    }

    public static boolean isFake(Player player) {
        try {
            Method handle = player.getClass().getMethod("getHandle");
            Object nms = handle.invoke(player);
            if (nms != null && nms.getClass().getName().equals(BOT_CLASS)) {
                return true;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // not CraftPlayer-shaped: fall through to the other signs
        }
        InetSocketAddress address = player.getAddress();
        if (address != null && address.getPort() == 0 && address.getAddress() != null
                && address.getAddress().isLoopbackAddress()) {
            return true;
        }
        Team team = Bukkit.getScoreboardManager().getMainScoreboard().getEntryTeam(player.getName());
        return team != null && team.getName().equals("fakeplayers");
    }
}
