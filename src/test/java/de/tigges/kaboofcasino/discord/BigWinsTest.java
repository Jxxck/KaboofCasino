package de.tigges.kaboofcasino.discord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.config.Msg;
import org.junit.jupiter.api.Test;

/** What counts as a big win, how it reads, and names as plain Discord text. */
class BigWinsTest {

    @Test
    void aBigWinIsEnoughTimesTheStakeAndEnoughChips() {
        assertTrue(BigWins.big(10, 1000, 50, 250), "100x, 1.000 chips");
        assertTrue(BigWins.big(5, 250, 50, 250), "exactly 50x and exactly the minimum");
        assertFalse(BigWins.big(10, 490, 50, 250), "49x");
        assertFalse(BigWins.big(1, 100, 50, 250), "100x of one chip: below the minimum");
        assertFalse(BigWins.big(0, 1000, 50, 250), "no stake (a refund row)");
        assertFalse(BigWins.big(2000, 36 * 2000, 50, 250), "a straight-up roulette number pays 36x");
    }

    @Test
    void theMultiplierReadsLikeTheBoards() {
        assertEquals("100", BigWins.multiplier(10, 1000));
        assertEquals("52,5", BigWins.multiplier(2, 105));
        assertEquals("1.000", BigWins.multiplier(1, 1000));
        assertEquals("66,6", BigWins.multiplier(3, 200), "cut, not rounded");
    }

    @Test
    void everyGameHasAName() {
        assertEquals(Msg.DISCORD_GAME_SLOT_MACHINE, BigWins.gameMsg("slot_machine"));
        assertEquals(Msg.DISCORD_GAME_DERBY, BigWins.gameMsg("derby"));
        assertEquals(Msg.DISCORD_GAME_OTHER, BigWins.gameMsg("something_new"));
        assertEquals(Msg.DISCORD_GAME_OTHER, BigWins.gameMsg(null));
    }

    @Test
    void namesStayPlainInDiscord() {
        assertEquals("\\_\\_Bob\\_\\_", DiscordBridge.md("__Bob__"));
        assertEquals("Steve", DiscordBridge.md("Steve"));
        assertEquals("\\*x\\*", DiscordBridge.md("*x*"));
    }
}
