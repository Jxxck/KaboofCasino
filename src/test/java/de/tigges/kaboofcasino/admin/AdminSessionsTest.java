package de.tigges.kaboofcasino.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.admin.AdminClick.Form;
import de.tigges.kaboofcasino.admin.AdminClick.Kind;
import de.tigges.kaboofcasino.admin.AdminSessions.Target;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Clicks act on the dialog the server opened, once per tick; resetting needs a second click within 60 ticks. */
class AdminSessionsTest {

    private static final UUID PLAYER = new UUID(0, 1);
    private static final UUID MACHINE = new UUID(0, 2);

    private static AdminClick click(Kind kind) {
        return new AdminClick(kind, Optional.empty());
    }

    @Test
    void clicksNeedAnOpenDialogAndActOnItsTarget() {
        AdminSessions s = new AdminSessions();
        assertInstanceOf(AdminSessions.Ignored.class, s.apply(PLAYER, click(Kind.SAVE), 10));
        Target machine = new Target(Form.CHANCE_MACHINE, MACHINE);
        s.open(PLAYER, machine, 10);
        assertInstanceOf(AdminSessions.Ignored.class, s.apply(PLAYER, click(Kind.SAVE), 10), "same tick as the open");
        assertEquals(new AdminSessions.Run(click(Kind.SAVE), machine), s.apply(PLAYER, click(Kind.SAVE), 11));
        assertInstanceOf(AdminSessions.Ignored.class, s.apply(PLAYER, click(Kind.COPY), 11), "second click in a tick");
        assertInstanceOf(AdminSessions.Ignored.class, s.apply(PLAYER, click(Kind.ALL_NORMAL), 12),
                "a world action on a machine dialog");
        assertEquals(new AdminSessions.Run(click(Kind.COPY), machine), s.apply(PLAYER, click(Kind.COPY), 13));
        assertEquals(new AdminSessions.Closed(click(Kind.DONE), machine), s.apply(PLAYER, click(Kind.DONE), 13),
                "Fertig and ESC always close");
        assertFalse(s.isOpen(PLAYER));
        assertInstanceOf(AdminSessions.Ignored.class, s.apply(PLAYER, click(Kind.SAVE), 20), "after the close");
    }

    @Test
    void resettingTakesTwoClicksWithinSixtyTicks() {
        AdminSessions s = new AdminSessions();
        Target machine = new Target(Form.BIAS_MACHINE, MACHINE);
        s.open(PLAYER, machine, 0);
        assertEquals(new AdminSessions.Confirm(Kind.RESET, machine), s.apply(PLAYER, click(Kind.RESET), 5));
        assertEquals(Optional.of(Kind.RESET), s.confirming(PLAYER, 65));
        assertEquals(Optional.empty(), s.confirming(PLAYER, 66));
        assertEquals(new AdminSessions.Run(click(Kind.RESET), machine), s.apply(PLAYER, click(Kind.RESET), 65));
        assertEquals(Optional.empty(), s.confirming(PLAYER, 66));
        // too late: the second click only asks again
        assertInstanceOf(AdminSessions.Confirm.class, s.apply(PLAYER, click(Kind.RESET), 100));
        assertInstanceOf(AdminSessions.Confirm.class, s.apply(PLAYER, click(Kind.RESET), 161));
        // another click in between cancels the question
        assertInstanceOf(AdminSessions.Run.class, s.apply(PLAYER, click(Kind.SAVE), 162));
        assertInstanceOf(AdminSessions.Confirm.class, s.apply(PLAYER, click(Kind.RESET), 163));

        Target world = Target.world();
        s.open(PLAYER, world, 200);
        assertEquals(Optional.empty(), s.confirming(PLAYER, 201), "a new dialog asks nothing");
        assertEquals(new AdminSessions.Confirm(Kind.ALL_NORMAL, world), s.apply(PLAYER, click(Kind.ALL_NORMAL), 201));
        assertEquals(new AdminSessions.Run(click(Kind.ALL_NORMAL), world), s.apply(PLAYER, click(Kind.ALL_NORMAL),
                202));
    }

    @Test
    void targetsMatchTheirForm() {
        assertThrows(IllegalArgumentException.class, () -> new Target(Form.WORLD, MACHINE));
        assertThrows(IllegalArgumentException.class, () -> new Target(Form.CHANCE_MACHINE, null));
        AdminSessions s = new AdminSessions();
        s.open(PLAYER, Target.world(), 0);
        assertTrue(s.forget(PLAYER));
        assertFalse(s.forget(PLAYER));
        assertTrue(s.isEmpty());
    }
}
