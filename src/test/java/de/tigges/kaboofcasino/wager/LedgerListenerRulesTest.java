package de.tigges.kaboofcasino.wager;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.event.player.PlayerRespawnEvent;
import org.junit.jupiter.api.Test;

/** B9: only a death respawn is a recovery point (a revived player joins dead), never the End exit. */
class LedgerListenerRulesTest {

    @Test
    void onlyDeathRespawnsRecover() {
        assertTrue(LedgerListener.recoverOnRespawn(PlayerRespawnEvent.RespawnReason.DEATH));
        assertFalse(LedgerListener.recoverOnRespawn(PlayerRespawnEvent.RespawnReason.END_PORTAL));
        assertFalse(LedgerListener.recoverOnRespawn(PlayerRespawnEvent.RespawnReason.PLUGIN));
    }
}
