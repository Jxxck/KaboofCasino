package de.tigges.kaboofcasino.wager;

/** Why a round was settled (round log, server.md 1.2). */
public enum SettleReason {
    /** At its scheduled time by the machine. */
    NORMAL,
    /** The player quit, was kicked or lost the connection: settled before the quit save. */
    QUIT,
    /** The player died: settled into the death drops. */
    DEATH,
    /** Server stop or plugin disable: settled before Paper saves the players. */
    STOP,
    /** A record found at join: the server crashed after a save that contained it. */
    JOIN_RECOVERY,
    /** The machine was removed (/fill, exploit, admin) while the round ran. */
    MACHINE_GONE,
    /** No machine settled it in time (chunk unloaded, plugin reload): the watchdog did. */
    TIMEOUT
}
