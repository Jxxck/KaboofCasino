package de.tigges.kaboofcasino.admin;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Who has a croupier's-hand dialog open and for what: a machine (by id) or the whole casino. A click only acts on the
 * dialog the server opened for that player, never on a target named by the client. At most one click per player and
 * server tick counts (a second one is a flood or a stale double click); "Fertig" and ESC always close. "Zurücksetzen"
 * and "Alles normal" need a second click within {@value #CONFIRM_TICKS} ticks, as the mod's
 * {@code CasinoSettingsScreen.confirm} (60 screen ticks); the dialog shows "Sicher?" meanwhile. Pure; thread: MAIN.
 */
final class AdminSessions {

    static final int CONFIRM_TICKS = 60;

    /** The open dialog: {@code machine} is null for the whole casino. */
    record Target(AdminClick.Form form, UUID machine) {
        Target {
            if (form.machine() != (machine != null)) {
                throw new IllegalArgumentException(form + " with machine " + machine);
            }
        }

        static Target world() {
            return new Target(AdminClick.Form.WORLD, null);
        }
    }

    sealed interface Outcome permits Run, Confirm, Closed, Ignored {
    }

    /** Do it on {@code target}. */
    record Run(AdminClick click, Target target) implements Outcome {
    }

    /** First click of a two-click action: show the dialog with "Sicher?" on that button. */
    record Confirm(AdminClick.Kind kind, Target target) implements Outcome {
    }

    /** The dialog was closed ("Fertig" or ESC); its inputs may still be applied. */
    record Closed(AdminClick click, Target target) implements Outcome {
    }

    record Ignored(String why) implements Outcome {
    }

    private static final class Session {
        final Target target;
        long lastTick;
        AdminClick.Kind confirming;
        long confirmUntil = Long.MIN_VALUE;

        Session(Target target, long tick) {
            this.target = target;
            this.lastTick = tick;
        }
    }

    private final Map<UUID, Session> sessions = new HashMap<>();

    /** Opens (or replaces) a player's dialog. */
    void open(UUID player, Target target, long tick) {
        sessions.put(player, new Session(target, tick));
    }

    Optional<Target> target(UUID player) {
        Session s = sessions.get(player);
        return s == null ? Optional.empty() : Optional.of(s.target);
    }

    /** The two-click action waiting for its second click at {@code tick}, if any (its button reads "Sicher?"). */
    Optional<AdminClick.Kind> confirming(UUID player, long tick) {
        Session s = sessions.get(player);
        return s == null || s.confirming == null || tick > s.confirmUntil ? Optional.empty()
                : Optional.of(s.confirming);
    }

    Outcome apply(UUID player, AdminClick click, long tick) {
        Session s = sessions.get(player);
        if (s == null) {
            return new Ignored("no open dialog");
        }
        if (!click.kind().allowedIn(s.target.form())) {
            return new Ignored(click.kind() + " in " + s.target.form());
        }
        if (click.kind() == AdminClick.Kind.DONE) {
            sessions.remove(player);
            return new Closed(click, s.target);
        }
        if (s.lastTick == tick) {
            return new Ignored("second click in tick " + tick);
        }
        s.lastTick = tick;
        boolean twoClicks = click.kind() == AdminClick.Kind.RESET || click.kind() == AdminClick.Kind.ALL_NORMAL;
        if (twoClicks && !(s.confirming == click.kind() && tick <= s.confirmUntil)) {
            s.confirming = click.kind();
            s.confirmUntil = tick + CONFIRM_TICKS;
            return new Confirm(click.kind(), s.target);
        }
        s.confirming = null;
        s.confirmUntil = Long.MIN_VALUE;
        return new Run(click, s.target);
    }

    boolean isOpen(UUID player) {
        return sessions.containsKey(player);
    }

    /** Forgets a player's dialog (quit, death, closed by the server). Returns true if one was open. */
    boolean forget(UUID player) {
        return sessions.remove(player) != null;
    }

    Set<UUID> open() {
        return Set.copyOf(sessions.keySet());
    }

    boolean isEmpty() {
        return sessions.isEmpty();
    }

    void clear() {
        sessions.clear();
    }
}
