package de.tigges.kaboofcasino.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.guide.GuideSessions.Closed;
import de.tigges.kaboofcasino.guide.GuideSessions.Ignored;
import de.tigges.kaboofcasino.guide.GuideSessions.Outcome;
import de.tigges.kaboofcasino.guide.GuideSessions.Show;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The mod's navigation (CasinoGuideScreen.turn / back) and the click rules of the guide sessions. */
class GuideSessionsTest {

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final GuideSessions sessions = new GuideSessions(34);
    private long tick = 100;

    private Outcome click(GuideAction action) {
        return sessions.apply(alice, action, ++tick);
    }

    @Test
    void opensOnTheCoverWithoutHistory() {
        assertTrue(sessions.isEmpty());
        assertEquals(new Show(1, false), sessions.open(alice, tick));
        assertFalse(sessions.isEmpty());
        assertTrue(sessions.isOpen(alice));
        assertFalse(sessions.isOpen(bob));
        assertEquals(1, sessions.page(alice));
        assertEquals(0, sessions.page(bob));
    }

    @Test
    void linksAndTabsRememberTheWayBackTurningForgetsIt() {
        sessions.open(alice, tick);
        assertEquals(new Show(2, true), click(GuideAction.tab(2)));
        assertEquals(new Show(4, true), click(GuideAction.link(4)));
        assertEquals(new Show(2, true), click(GuideAction.back()));
        assertEquals(new Show(1, false), click(GuideAction.back()));
        assertInstanceOf(Ignored.class, click(GuideAction.back()));
        assertEquals(new Show(11, true), click(GuideAction.link(11)));
        assertEquals(new Show(12, false), click(GuideAction.turn(12)));
        assertInstanceOf(Ignored.class, click(GuideAction.back()));
        assertEquals(new Show(11, false), click(GuideAction.turn(11)));
    }

    @Test
    void theOpenPageOutOfRangeAndForeignPlayersDoNothing() {
        sessions.open(alice, tick);
        assertInstanceOf(Ignored.class, click(GuideAction.link(1)));
        assertInstanceOf(Ignored.class, click(GuideAction.turn(35)));
        assertInstanceOf(Ignored.class, sessions.apply(bob, GuideAction.turn(2), ++tick));
        assertInstanceOf(Ignored.class, sessions.apply(bob, GuideAction.close(), ++tick));
        assertEquals(1, sessions.page(alice));
    }

    @Test
    void oneClickPerTickButCloseAlways() {
        sessions.open(alice, tick);
        assertInstanceOf(Ignored.class, sessions.apply(alice, GuideAction.turn(2), tick), "the opening tick");
        assertEquals(new Show(2, false), sessions.apply(alice, GuideAction.turn(2), tick + 1));
        assertInstanceOf(Ignored.class, sessions.apply(alice, GuideAction.turn(3), tick + 1));
        assertInstanceOf(Closed.class, sessions.apply(alice, GuideAction.close(), tick + 1));
        assertFalse(sessions.isOpen(alice));
        assertInstanceOf(Ignored.class, sessions.apply(alice, GuideAction.turn(3), tick + 2));
    }

    @Test
    void reopeningStartsOverAndForgetEnds() {
        sessions.open(alice, tick);
        click(GuideAction.link(20));
        assertEquals(new Show(1, false), sessions.open(alice, ++tick));
        assertInstanceOf(Ignored.class, click(GuideAction.back()));
        sessions.open(bob, tick);
        assertEquals(2, sessions.open().size());
        assertTrue(sessions.forget(alice));
        assertFalse(sessions.forget(alice));
        sessions.clear();
        assertTrue(sessions.open().isEmpty());
    }

    @Test
    void theHistoryIsBounded() {
        sessions.open(alice, tick);
        for (int i = 0; i < GuideSessions.MAX_HISTORY + 10; i++) {
            click(GuideAction.link(i % 2 == 0 ? 2 : 3));
        }
        int backs = 0;
        while (click(GuideAction.back()) instanceof Show) {
            backs++;
        }
        assertEquals(GuideSessions.MAX_HISTORY, backs);
    }
}
