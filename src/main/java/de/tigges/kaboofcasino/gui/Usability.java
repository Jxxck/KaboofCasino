package de.tigges.kaboofcasino.gui;

import de.tigges.kaboofcasino.util.FakePlayers;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

/** Who may play (PLAN decision 3): connected, alive, awake, survival or adventure, not a fake player. */
public final class Usability {

    private Usability() {
    }

    public static boolean usable(Player player) {
        GameMode mode = player.getGameMode();
        return player.isConnected() && !player.isDead() && !player.isSleeping()
                && (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE) && !FakePlayers.isFake(player);
    }

    public static boolean creativeOrSpectator(Player player) {
        GameMode mode = player.getGameMode();
        return mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR;
    }
}
