package de.tigges.kaboofcasino.machine;

import java.util.UUID;
import org.bukkit.entity.Player;

/**
 * The game running on one machine (extension point for the seven games). Created when the machine is placed or its
 * chunk loads, dropped when it unloads or is removed. Rounds never depend on it: money is in the player's ledger
 * (server.md 1.3, "Machine broken ... mid-round"). Thread: MAIN.
 */
public interface MachineGame {

    /** Builds the display rig (non-persistent entities). */
    void spawn();

    /** Removes the rig (chunk unload, removal, disable). */
    void despawn();

    /** Every server tick while loaded. */
    void tick(long tick);

    /** A round is running: breaking is refused. */
    boolean busy();

    /** Right click on a carrier by a usable player. */
    void interact(Player player);

    /** The machine is being removed while loaded; settle what runs now ({@code SettleReason.MACHINE_GONE}). */
    void removed();

    /** True while this machine will settle the round itself (the ledger watchdog skips it). */
    boolean ownsRound(UUID roundId);

    /** Respawns rig entities that disappeared (/kill, other plugins). */
    void checkRig();
}
