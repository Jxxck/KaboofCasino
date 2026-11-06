package de.tigges.kaboofcasino.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** B11: bots are recognised by their handle class (also subclasses), never by a name. */
class FakePlayersTest {

    static class SubBot extends dev.egor.fakeplayer.Bot {
    }

    static class ServerPlayerLike {
    }

    @Test
    void botClassAndSubclasses() {
        assertTrue(FakePlayers.isBotClass(dev.egor.fakeplayer.Bot.class));
        assertTrue(FakePlayers.isBotClass(SubBot.class));
        assertFalse(FakePlayers.isBotClass(ServerPlayerLike.class));
        assertFalse(FakePlayers.isBotClass(Object.class));
    }
}
