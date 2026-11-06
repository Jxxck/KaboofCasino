package de.tigges.kaboofcasino.game;

import de.tigges.kaboofcasino.gui.Menu;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * A machine's game menu: valid only while its controller says so (idle, loaded, in range), frees the claim when
 * closed, and closes itself after {@code gui.idle-close-seconds} without a round.
 */
public abstract class GameMenu extends Menu {

    protected GameMenu(UUID viewer, Component title) {
        super(viewer, 6, title);
    }

    public abstract MachineController controller();

    @Override
    public boolean stillValid(Player player) {
        return controller().menuValid(player);
    }

    @Override
    public void onClose(Player player) {
        controller().menuClosed(player);
    }

    @Override
    public boolean closesWhenIdle() {
        return true;
    }
}
