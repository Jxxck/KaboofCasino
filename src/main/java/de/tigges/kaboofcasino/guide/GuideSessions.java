package de.tigges.kaboofcasino.guide;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Who has the guide open and on which spread, with the mod's link history ({@code CasinoGuideScreen.turn/back}):
 * links and bookmarks remember the page they leave, "Inhalt" goes back one step, previous/next forget the way back;
 * a target equal to the open page does nothing. Opening the book starts on the cover with no history, as the mod's
 * new screen does. Clicks are only accepted from a player with an open guide and at most one per player and server
 * tick (the client sends at most one click per frame; more is a flood or a stale double click); "Fertig" and ESC are
 * always accepted. The history keeps at most {@value #MAX_HISTORY} pages. Pure; thread: MAIN.
 */
final class GuideSessions {

    static final int MAX_HISTORY = 64;

    /** What a click leads to. */
    sealed interface Outcome permits Show, Closed, Ignored {
    }

    /** Show spread {@code page}; {@code history}: "Inhalt" leads back. */
    record Show(int page, boolean history) implements Outcome {
    }

    /** The guide was closed. */
    record Closed() implements Outcome {
    }

    /** Nothing happens (no open guide, same page, nothing to go back to, second click in this tick). */
    record Ignored(String why) implements Outcome {
    }

    private static final class Session {
        int page = 1;
        final Deque<Integer> history = new ArrayDeque<>();
        long lastTick = Long.MIN_VALUE;
    }

    private final Map<UUID, Session> sessions = new HashMap<>();
    private final int pages;

    GuideSessions(int pages) {
        if (pages < 1) {
            throw new IllegalArgumentException("a guide needs pages");
        }
        this.pages = pages;
    }

    /** Opens the guide on the cover (also when it was open already). */
    Show open(UUID player, long tick) {
        Session s = new Session();
        s.lastTick = tick;
        sessions.put(player, s);
        return new Show(1, false);
    }

    Outcome apply(UUID player, GuideAction action, long tick) {
        Session s = sessions.get(player);
        if (s == null) {
            return new Ignored("no open guide");
        }
        if (action.kind() == GuideAction.Kind.CLOSE) {
            sessions.remove(player);
            return new Closed();
        }
        if (s.lastTick == tick) {
            return new Ignored("second click in tick " + tick);
        }
        s.lastTick = tick;
        switch (action.kind()) {
            case TURN, LINK, TAB -> {
                int to = action.page();
                if (to < 1 || to > pages || to == s.page) {
                    return new Ignored("target " + to + " from " + s.page);
                }
                if (action.kind() == GuideAction.Kind.TURN) {
                    s.history.clear();
                } else {
                    s.history.push(s.page);
                    while (s.history.size() > MAX_HISTORY) {
                        s.history.removeLast();
                    }
                }
                s.page = to;
            }
            case BACK -> {
                if (s.history.isEmpty()) {
                    return new Ignored("no way back");
                }
                s.page = s.history.pop();
            }
            case CLOSE -> throw new AssertionError("handled above");
        }
        return new Show(s.page, !s.history.isEmpty());
    }

    boolean isOpen(UUID player) {
        return sessions.containsKey(player);
    }

    /** The open spread, or 0. */
    int page(UUID player) {
        Session s = sessions.get(player);
        return s == null ? 0 : s.page;
    }

    /** Forgets a player's guide (quit, death, closed by the server). Returns true if one was open. */
    boolean forget(UUID player) {
        return sessions.remove(player) != null;
    }

    /** True if nobody has the guide open. */
    boolean isEmpty() {
        return sessions.isEmpty();
    }

    /** The players with an open guide (a copy). */
    Set<UUID> open() {
        return Set.copyOf(sessions.keySet());
    }

    void clear() {
        sessions.clear();
    }
}
