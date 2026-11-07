package de.tigges.kaboofcasino.game.derby;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * "Own entry first" ({@code DerbyLobby.visible(members, viewer, 8)}, which the mod's renderer evaluates for each
 * client): an entrant near the group panel gets his own copy of it, a text display hidden from everyone else and
 * shown only to him, with his entry first; the shared panel (join order) is hidden from him meanwhile. Only the group
 * panel lists bettors, so only an entrant of a group session ever gets one, and only when his order differs from the
 * shared one (the first entrant's never does).
 *
 * <p>Distances are the eyes' to the panel's centre point (the renderer's rule). A copy is created within
 * {@value #CREATE} blocks and kept up to {@value #KEEP}: the panel shows to 10 blocks (view range), so the swap
 * happens before it is visible (the player scan is up to 4 ticks old) and a player at the edge does not make it
 * churn.
 *
 * <p>Bookkeeping that never leaves a viewer behind: the copy is spawned and shown first, then the shared panel hidden;
 * on removal the shared panel is shown again first (only to a viewer still online; a quit player's visibility state
 * goes with his player object), then the copy hidden from him and removed. Removal happens when he leaves
 * {@value #KEEP} blocks, leaves the world, quits, stops being an entrant, the order no longer differs, the group panel
 * ends, the copy vanished (it is made again), and for all at once with {@link #clear} (rig despawn: chunk unload,
 * respawn, plugin disable; or a new shared panel entity, whose hiding starts afresh). Pure: the server side is
 * {@link Ops}. Thread: MAIN.
 *
 * @param <D> the display handle
 */
final class DerbyPanelViewers<D> {

    static final double CREATE = 12.0;
    static final double KEEP = 14.0;

    /** The server side of the copies (the rig: text displays and {@code Player#showEntity/hideEntity}). */
    interface Ops<D> {

        /** A new copy at the panel's place, hidden from everyone but shown to {@code viewer}. */
        D spawn(UUID viewer, DerbyPanel.Content content, double foot);

        /** New content; {@code moved}: its foot changed (line count). */
        void update(D display, DerbyPanel.Content content, double foot, boolean moved);

        /** Hides the copy from {@code viewer} (if he is online) and removes it. */
        void remove(UUID viewer, D display);

        void hideShared(UUID viewer);

        /** Shows the shared panel to {@code viewer} again if he is online. */
        void showShared(UUID viewer);

        boolean valid(D display);
    }

    private static final class Own<D> {
        final D display;
        DerbyPanel.Content content;
        double foot;

        Own(D display, DerbyPanel.Content content, double foot) {
            this.display = display;
            this.content = content;
            this.foot = foot;
        }
    }

    private final Map<UUID, Own<D>> own = new LinkedHashMap<>();

    boolean isEmpty() {
        return own.isEmpty();
    }

    Set<UUID> viewers() {
        return Collections.unmodifiableSet(own.keySet());
    }

    /**
     * Who should have a copy now: entrants of the shown group panel within {@value #CREATE} blocks (or still within
     * {@value #KEEP} if they have one) whose own order differs from the shared one.
     *
     * @param distSq  squared eye distance to the panel's centre per player in the machine's world (absent = far
     *                away or elsewhere)
     * @param current who has a copy now
     */
    static Set<UUID> wanted(DerbyView view, long gameTime, DerbyPanel.Content shared, Map<UUID, Double> distSq,
                            Set<UUID> current) {
        Set<UUID> out = new LinkedHashSet<>();
        if (!(shared instanceof DerbyPanel.Shared)) {
            return out;
        }
        for (DerbyMember m : view.bettors()) {
            Double d = distSq.get(m.id());
            if (d == null || out.contains(m.id())) {
                continue;
            }
            double limit = current.contains(m.id()) ? KEEP : CREATE;
            if (d > limit * limit) {
                continue;
            }
            if (!shared.equals(DerbyPanel.content(view, gameTime, m.id()))) {
                out.add(m.id());
            }
        }
        return out;
    }

    /** Brings the copies in line with the view at {@code gameTime} ({@code shared}: everyone's panel now). */
    void sync(DerbyView view, long gameTime, DerbyPanel.Content shared, Map<UUID, Double> distSq, Ops<D> ops) {
        Set<UUID> wanted = wanted(view, gameTime, shared, distSq, own.keySet());
        for (Iterator<Map.Entry<UUID, Own<D>>> it = own.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Own<D>> e = it.next();
            if (!wanted.contains(e.getKey()) || !ops.valid(e.getValue().display)) {
                ops.showShared(e.getKey());
                ops.remove(e.getKey(), e.getValue().display);
                it.remove();
            }
        }
        for (UUID viewer : wanted) {
            DerbyPanel.Content content = DerbyPanel.content(view, gameTime, viewer);
            double foot = DerbyRig.panelFoot(content);
            Own<D> o = own.get(viewer);
            if (o == null) {
                own.put(viewer, new Own<>(ops.spawn(viewer, content, foot), content, foot));
                ops.hideShared(viewer);
            } else if (!content.equals(o.content)) {
                ops.update(o.display, content, foot, foot != o.foot);
                o.content = content;
                o.foot = foot;
            }
        }
    }

    /** Removes every copy, each viewer sees the shared panel again. */
    void clear(Ops<D> ops) {
        for (Map.Entry<UUID, Own<D>> e : own.entrySet()) {
            ops.showShared(e.getKey());
            ops.remove(e.getKey(), e.getValue().display);
        }
        own.clear();
    }
}
