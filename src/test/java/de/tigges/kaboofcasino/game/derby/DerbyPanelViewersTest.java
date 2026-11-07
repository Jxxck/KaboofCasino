package de.tigges.kaboofcasino.game.derby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.wager.ItemAmount;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The per-viewer group panels ("own entry first") against a fake server that keeps Paper's visibility bookkeeping:
 * who gets a copy, the order on it, and every cleanup path (walking away, world change, quit, rejoin, a killed copy,
 * the group session ending, the rig despawning) leaving no copy and no hidden shared panel behind.
 */
class DerbyPanelViewersTest {

    private static final long START = 77_000_000L;
    private static final ItemAmount COAL = new ItemAmount("minecraft:coal", 5);
    private static final UUID A = new UUID(1, 1);
    private static final UUID B = new UUID(1, 2);
    private static final UUID C = new UUID(1, 3);
    private static final UUID D = new UUID(1, 4);
    private static final UUID N = new UUID(1, 99);

    /** The server: copies, who sees which copy, for whom the shared panel is hidden, who is online. */
    private static final class Fake implements DerbyPanelViewers.Ops<Integer> {
        int next = 1;
        final Set<UUID> online = new HashSet<>();
        final Map<Integer, UUID> live = new HashMap<>();
        final Map<Integer, DerbyPanel.Content> content = new HashMap<>();
        final Map<Integer, UUID> copyShownTo = new HashMap<>();
        final Set<UUID> sharedHidden = new HashSet<>();
        final Set<Integer> killed = new HashSet<>();
        int spawns;
        int updates;

        @Override
        public Integer spawn(UUID viewer, DerbyPanel.Content c, double foot) {
            int id = next++;
            live.put(id, viewer);
            content.put(id, c);
            if (online.contains(viewer)) {
                copyShownTo.put(id, viewer);
            }
            spawns++;
            return id;
        }

        @Override
        public void update(Integer display, DerbyPanel.Content c, double foot, boolean moved) {
            content.put(display, c);
            updates++;
        }

        @Override
        public void remove(UUID viewer, Integer display) {
            if (online.contains(viewer)) {
                copyShownTo.remove(display);
            }
            live.remove(display);
            content.remove(display);
            killed.remove(display);
        }

        @Override
        public void hideShared(UUID viewer) {
            if (online.contains(viewer)) {
                sharedHidden.add(viewer);
            }
        }

        @Override
        public void showShared(UUID viewer) {
            if (online.contains(viewer)) {
                sharedHidden.remove(viewer);
            }
        }

        @Override
        public boolean valid(Integer display) {
            return live.containsKey(display) && !killed.contains(display);
        }

        /** A quit: Paper drops the player object with its visibility state. */
        void quit(UUID player) {
            online.remove(player);
            sharedHidden.remove(player);
            copyShownTo.values().removeIf(player::equals);
        }

        Integer copyOf(UUID viewer) {
            for (Map.Entry<Integer, UUID> e : live.entrySet()) {
                if (e.getValue().equals(viewer)) {
                    return e.getKey();
                }
            }
            return null;
        }
    }

    private Fake fake;
    private DerbyPanelViewers<Integer> viewers;
    private final Map<UUID, Double> dist = new HashMap<>();

    @BeforeEach
    void setUp() {
        fake = new Fake();
        viewers = new DerbyPanelViewers<>();
        fake.online.addAll(List.of(A, B, C, D, N));
        dist.clear();
    }

    private static DerbyMember member(UUID id, int lane) {
        return new DerbyMember(id, "p" + id.getLeastSignificantBits(), lane, List.of(COAL));
    }

    private static DerbyView enrolling(List<DerbyMember> members) {
        return new DerbyView(-1, null, true, true, START + 200, members.size(), members, List.of());
    }

    private static final List<DerbyMember> FOUR = List.of(member(A, 0), member(B, 1), member(C, 2), member(D, 3));

    private void at(UUID player, double blocks) {
        dist.put(player, blocks * blocks);
    }

    private void sync(DerbyView view, long now) {
        viewers.sync(view, now, DerbyPanel.content(view, now), dist, fake);
        invariants();
    }

    /** No copy without its viewer and none left over; the shared panel hidden exactly for online copy owners. */
    private void invariants() {
        assertEquals(viewers.viewers(), new HashSet<>(fake.live.values()), "one copy per viewer, no leaks");
        assertEquals(fake.live.size(), viewers.viewers().size());
        Set<UUID> hidden = new HashSet<>(viewers.viewers());
        hidden.retainAll(fake.online);
        assertEquals(hidden, fake.sharedHidden);
        for (Map.Entry<Integer, UUID> e : fake.copyShownTo.entrySet()) {
            assertTrue(fake.live.containsKey(e.getKey()), "a copy shown to someone is alive");
        }
    }

    @Test
    void onlyNearEntrantsWhoseOrderDiffersGetACopyWithTheirEntryFirst() {
        at(A, 5);
        at(B, 5);
        at(C, 12.5);
        at(D, 30);
        at(N, 3);
        DerbyView view = enrolling(FOUR);
        sync(view, START);
        // A joined first: his order is everyone's; C is beyond 12, D far away, N no entrant
        assertEquals(Set.of(B), viewers.viewers());
        DerbyPanel.Shared copy = assertInstanceOf(DerbyPanel.Shared.class, fake.content.get(fake.copyOf(B)));
        assertEquals(List.of(B, A, C, D), copy.bettors().stream().map(DerbyPanel.Bettor::id).toList());
        assertEquals(B, fake.copyShownTo.get(fake.copyOf(B)), "shown to him");
        DerbyPanel.Shared shared = (DerbyPanel.Shared) DerbyPanel.content(view, START);
        assertEquals(List.of(A, B, C, D), shared.bettors().stream().map(DerbyPanel.Bettor::id).toList());
        // the countdown changes the copy in place
        sync(view, START + 20);
        assertEquals(1, fake.spawns);
        assertEquals(1, fake.updates);
        assertEquals(9, ((DerbyPanel.Shared) fake.content.get(fake.copyOf(B))).seconds());
    }

    @Test
    void createdWithinTwelveKeptToFourteen() {
        DerbyView view = enrolling(FOUR);
        at(C, 12.01);
        sync(view, START);
        assertTrue(viewers.isEmpty());
        at(C, 11.9);
        sync(view, START);
        assertEquals(Set.of(C), viewers.viewers());
        Integer first = fake.copyOf(C);
        at(C, 13.9);
        sync(view, START + 1);
        assertEquals(first, fake.copyOf(C), "kept, not respawned");
        at(C, 14.1);
        sync(view, START + 2);
        assertTrue(viewers.isEmpty());
        assertTrue(fake.sharedHidden.isEmpty(), "the shared panel is his again");
    }

    @Test
    void leavingTheWorldQuittingAndRejoining() {
        DerbyView view = enrolling(FOUR);
        at(B, 4);
        at(C, 4);
        sync(view, START);
        assertEquals(Set.of(B, C), viewers.viewers());
        // B changes world: he is no longer in this world's scan, but online
        dist.remove(B);
        sync(view, START + 1);
        assertEquals(Set.of(C), viewers.viewers());
        assertTrue(!fake.sharedHidden.contains(B));
        // C quits: his copy goes, nothing is left for an offline player
        fake.quit(C);
        dist.remove(C);
        sync(view, START + 2);
        assertTrue(viewers.isEmpty());
        assertTrue(fake.live.isEmpty());
        // C rejoins and walks up again: a fresh copy
        fake.online.add(C);
        at(C, 6);
        sync(view, START + 3);
        assertEquals(Set.of(C), viewers.viewers());
        assertEquals(C, fake.copyShownTo.get(fake.copyOf(C)));
    }

    @Test
    void aQuitPlayerStillInTheLastScanLosesHisCopyAtTheNextScan() {
        DerbyView view = enrolling(FOUR);
        at(D, 4);
        sync(view, START);
        fake.quit(D);
        // the scan is up to 4 ticks old: the copy may stay that long, hidden from everyone (shown to nobody)
        sync(view, START + 1);
        assertTrue(fake.copyShownTo.isEmpty());
        dist.remove(D);
        sync(view, START + 4);
        assertTrue(fake.live.isEmpty());
    }

    @Test
    void aKilledCopyIsMadeAgainAndTheSharedPanelStaysHidden() {
        DerbyView view = enrolling(FOUR);
        at(B, 4);
        sync(view, START);
        Integer first = fake.copyOf(B);
        fake.killed.add(first);
        sync(view, START + 1);
        assertNotNull(fake.copyOf(B));
        assertTrue(!first.equals(fake.copyOf(B)));
        assertEquals(Set.of(B), fake.sharedHidden);
    }

    @Test
    void theGroupSessionEndingOrTheRigDespawningLeavesNothing() {
        at(B, 4);
        at(C, 4);
        DerbyView view = enrolling(FOUR);
        sync(view, START);
        assertEquals(2, fake.live.size());
        // the session is over: the solo rule (a shown bet, no group) or no panel at all
        List<ItemAmount> shown = new ArrayList<>(java.util.Collections.nCopies(12, (ItemAmount) null));
        shown.set(1, COAL);
        DerbyView solo = new DerbyView(START, DuckDerbyRace.plan(1, 5L), false, false, -1, 1,
                List.of(member(B, 1)), shown);
        sync(solo, START + 30);
        assertTrue(fake.live.isEmpty());
        assertTrue(fake.sharedHidden.isEmpty());
        // again, then the rig despawns (chunk unload, respawn, plugin disable)
        sync(view, START + 40);
        assertEquals(2, fake.live.size());
        viewers.clear(fake);
        invariants();
        assertTrue(fake.live.isEmpty());
        assertTrue(fake.sharedHidden.isEmpty());
        assertTrue(fake.copyShownTo.isEmpty());
    }

    @Test
    void aViewerBeyondTheEightShownSeesHimselfFirst() {
        List<DerbyMember> many = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            many.add(member(new UUID(2, i), i % 4));
        }
        UUID tenth = new UUID(2, 9);
        fake.online.add(tenth);
        at(tenth, 2);
        DerbyView view = enrolling(many);
        sync(view, START);
        DerbyPanel.Shared copy = (DerbyPanel.Shared) fake.content.get(fake.copyOf(tenth));
        assertEquals(8, copy.bettors().size());
        assertEquals(tenth, copy.bettors().get(0).id());
        assertEquals(new UUID(2, 0), copy.bettors().get(1).id());
        assertEquals(new UUID(2, 6), copy.bettors().get(7).id(), "the eighth in join order drops out");
        assertEquals(11, copy.members());
    }
}
