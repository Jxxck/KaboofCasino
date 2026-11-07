package de.tigges.kaboofcasino.game.derby;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.gui.GuiLayout;
import de.tigges.kaboofcasino.wager.ItemAmount;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** The derby menu's client logic ({@code DuckDerbyScreen} / {@code DuckDerbyMenu}) on snapshots: no server, no items. */
class DerbyBetMenuTest {

    private static final UUID ME = new UUID(0, 1);
    private static final ItemAmount DIAMOND = new ItemAmount("minecraft:diamond", 5);

    private static DerbySnapshot snap(boolean enrolling, int seconds, boolean confirmed, boolean groupAllowed,
                                      int credits, List<DerbyMember> members, int lane, List<ItemAmount> escrow) {
        return new DerbySnapshot(false, enrolling, seconds, confirmed, groupAllowed, credits, members, lane, escrow,
                100);
    }

    private static DerbySnapshot idle() {
        return snap(false, 0, false, true, 0, List.of(), -1, List.of());
    }

    private static ItemAmount[] draft(int... indices) {
        ItemAmount[] out = new ItemAmount[12];
        for (int i : indices) {
            out[i] = DIAMOND;
        }
        return out;
    }

    private static List<DerbyMember> members(int n) {
        List<DerbyMember> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(new DerbyMember(new UUID(1, i), "P" + i, i % 4, List.of(DIAMOND)));
        }
        return out;
    }

    private static DerbyMenuView.Cell[] cells(DerbySnapshot s, ItemAmount[] draft) {
        return DerbyMenuView.cells(s, ME, draft, 0, 0, false, false);
    }

    private static String hint(DerbyMenuView.Cell[] cells) {
        return cells[DerbyMenuView.HINT].strings().get(0);
    }

    /** Bet i: lane i % 4 in grid column 0 / 3 / 5 / 8, row i / 4 on grid rows 1..3 (DuckDerbyMenu.BET_X/BET_Y). */
    @Test
    void betsLieOnTheLaneColumns() {
        assertArrayEquals(new int[] {9, 12, 14, 17, 18, 21, 23, 26, 27, 30, 32, 35}, java.util.stream.IntStream
                .range(0, 12).map(DerbyMenuView::betSlot).toArray());
        Set<Integer> used = new HashSet<>();
        for (int i = 0; i < 12; i++) {
            assertTrue(used.add(DerbyMenuView.betSlot(i)));
        }
        List<Integer> others = new ArrayList<>(List.of(DerbyMenuView.LOBBY, DerbyMenuView.INFO, DerbyMenuView.HINT,
                DerbyMenuView.HELP_LEFT, DerbyMenuView.HELP, DerbyMenuView.PAGER));
        for (int s : DerbyMenuView.LANE_SLOTS) {
            others.add(s);
        }
        for (int s : DerbyMenuView.SOLO) {
            others.add(s);
        }
        for (int s : DerbyMenuView.GROUP) {
            others.add(s);
        }
        for (int k = 0; k < DerbyMenuView.PAGE_SIZE; k++) {
            others.add(DerbyMenuView.FIRST_HEAD + k);
        }
        for (int s : others) {
            assertFalse(used.contains(s), "slot " + s + " is a bet slot");
        }
    }

    /** {@code DuckDerbyMenu.stakedLane}: -1 nothing, the lane, -2 several lanes. */
    @Test
    void stakedLane() {
        assertEquals(-1, DerbyMenuView.stakedLane(draft()));
        assertEquals(2, DerbyMenuView.stakedLane(draft(2, 6, 10)));
        assertEquals(3, DerbyMenuView.stakedLane(draft(7)));
        assertEquals(-2, DerbyMenuView.stakedLane(draft(0, 5)));
    }

    /** {@code extractLabels}: pick / picked / several, the countdown while enrolling, else "confirmed". */
    @Test
    void hintFollowsTheModsOrder() {
        assertEquals("pick", hint(cells(idle(), draft())));
        assertEquals("picked_1", hint(cells(idle(), draft(1, 5))));
        assertEquals("several", hint(DerbyMenuView.cells(idle(), ME, draft(1), 0, 0, false, true)));
        DerbySnapshot countdown = snap(true, 7, false, true, 0, members(1), -1, List.of());
        assertEquals("countdown_7", hint(cells(countdown, draft(1))));
        assertEquals("countdown_10", hint(cells(snap(true, 12, false, true, 0, members(1), -1, List.of()), draft())));
        assertEquals("countdown_0", hint(cells(snap(true, -1, false, true, 0, members(1), -1, List.of()), draft())));
        // the mod checks enrolling() before confirmed(): an entrant sees the countdown
        assertEquals("countdown_3", hint(cells(snap(true, 3, true, true, 0, members(1), 2, List.of(DIAMOND)),
                draft())));
        assertEquals("confirmed", hint(cells(snap(false, 0, true, true, 0, members(1), 2, List.of(DIAMOND)),
                draft())));
    }

    /** A refused click flashes "several" but keeps the lane veils of the real draft. */
    @Test
    void severalOnlyChangesTheHint() {
        DerbyMenuView.Cell[] normal = DerbyMenuView.cells(idle(), ME, draft(1), 1, 0, false, false);
        DerbyMenuView.Cell[] flash = DerbyMenuView.cells(idle(), ME, draft(1), 1, 0, false, true);
        for (int slot = 0; slot < DerbyMenuView.SLOTS; slot++) {
            if (slot != DerbyMenuView.HINT) {
                assertEquals(normal[slot], flash[slot], "slot " + slot);
            }
        }
        assertEquals("several", hint(flash));
    }

    private static String model(DerbyMenuView.Cell c) {
        return c == null || c.model() == null ? null : c.model().substring(c.model().lastIndexOf('/') + 1);
    }

    /** The staked lane's card gets the gold lines, the others the veil; nothing staked: no lane picture. */
    @Test
    void laneVeils() {
        DerbyMenuView.Cell[] none = cells(idle(), draft());
        for (int s : DerbyMenuView.LANE_SLOTS) {
            assertNull(none[s]);
        }
        DerbyMenuView.Cell[] two = cells(idle(), draft(2, 6));
        assertEquals(List.of("veil_0", "veil_1", "chosen_2", "veil_3"), List.of(model(two[0]), model(two[3]),
                model(two[5]), model(two[8])));
        for (int i = 0; i < 12; i++) {
            DerbyMenuView.Cell bet = two[DerbyMenuView.betSlot(i)];
            assertEquals(DerbyMenuView.Kind.BET, bet.kind());
            assertEquals(i % 4 == 2 ? "free" : "locked", bet.state(), "bet " + i);
        }
    }

    /** {@code updatePlayButton}: SOLO only with a lane, outside a countdown and not confirmed; CLAIM replaces it. */
    @Test
    void soloAndClaim() {
        assertEquals("solo_off", model(cells(idle(), draft())[DerbyMenuView.SOLO_ART]));
        DerbyMenuView.Cell solo = cells(idle(), draft(3))[DerbyMenuView.SOLO_ART];
        assertEquals("solo_on", model(solo));
        assertTrue(solo.active());
        assertEquals(DerbyMenuView.Action.SOLO, solo.action());
        DerbyMenuView.Cell during = cells(snap(true, 5, false, true, 0, members(1), -1, List.of()), draft(3))
                [DerbyMenuView.SOLO_ART];
        assertEquals("solo_off", model(during));
        assertFalse(during.active());
        DerbyMenuView.Cell claim = cells(snap(false, 0, false, true, 3, List.of(), -1, List.of()), draft())
                [DerbyMenuView.SOLO_ART];
        assertEquals("claim_on", model(claim));
        assertEquals(DerbyMenuView.Action.CLAIM, claim.action());
        assertTrue(claim.active());
        for (int s : DerbyMenuView.SOLO) {
            DerbyMenuView.Cell c = cells(idle(), draft(3))[s];
            assertEquals(DerbyMenuView.Action.SOLO, c.action());
            assertEquals(s == DerbyMenuView.SOLO_ART ? "solo_on" : "blank", model(c));
        }
    }

    /** GROUP (10s) / CONFIRM / WITHDRAW: the label, when it is active and the settings' tooltip. */
    @Test
    void groupButton() {
        DerbyMenuView.Cell none = cells(idle(), draft())[DerbyMenuView.GROUP_ART];
        assertEquals("group_off", model(none));
        assertFalse(none.active());
        DerbyMenuView.Cell group = cells(idle(), draft(0))[DerbyMenuView.GROUP_ART];
        assertEquals("group_on", model(group));
        assertEquals(DerbyMenuView.Action.GROUP, group.action());
        DerbyMenuView.Cell confirm = cells(snap(true, 6, false, true, 0, members(2), -1, List.of()), draft(0))
                [DerbyMenuView.GROUP_ART];
        assertEquals("confirm_on", model(confirm));
        assertEquals(DerbyMenuView.Action.CONFIRM, confirm.action());
        assertTrue(confirm.active());
        DerbyMenuView.Cell full = cells(snap(true, 6, false, true, 0, members(64), -1, List.of()), draft(0))
                [DerbyMenuView.GROUP_ART];
        assertEquals("confirm_off", model(full));
        assertFalse(full.active());
        DerbyMenuView.Cell withdraw = cells(snap(true, 6, true, true, 0, members(2), 1, List.of(DIAMOND)), draft())
                [DerbyMenuView.GROUP_ART];
        assertEquals("withdraw_on", model(withdraw));
        assertEquals(DerbyMenuView.Action.WITHDRAW, withdraw.action());
        assertTrue(withdraw.active());
        DerbyMenuView.Cell forbidden = cells(snap(false, 0, false, false, 0, List.of(), -1, List.of()), draft(0))
                [DerbyMenuView.GROUP_ART];
        assertEquals("group_off", model(forbidden));
        assertEquals("unavailable", forbidden.state());
        for (int s : DerbyMenuView.GROUP) {
            assertEquals(DerbyMenuView.Action.GROUP, cells(idle(), draft(0))[s].action());
        }
    }

    /** Confirmed: the escrow read-only in its column (row order), no draft bets, the column's card chosen. */
    @Test
    void escrowView() {
        ItemAmount gold = new ItemAmount("minecraft:gold_ingot", 12);
        DerbyMenuView.Cell[] c = cells(snap(true, 4, true, true, 0, members(3), 3, List.of(gold, DIAMOND)),
                draft(0, 4));
        assertEquals(DerbyMenuView.Kind.ESCROW, c[DerbyMenuView.betSlot(3)].kind());
        assertEquals(gold, c[DerbyMenuView.betSlot(3)].stake());
        assertEquals(DIAMOND, c[DerbyMenuView.betSlot(7)].stake());
        for (int i = 0; i < 12; i++) {
            if (i != 3 && i != 7) {
                assertNull(c[DerbyMenuView.betSlot(i)], "bet " + i);
            }
        }
        assertEquals("chosen_3", model(c[8]));
        assertEquals("veil_0", model(c[0]));
        assertEquals("solo_off", model(c[DerbyMenuView.SOLO_ART]));
    }

    /** The lobby: own entry first, 8 per page, the pager and page only with two pages or more. */
    @Test
    void lobbyPages() {
        List<DerbyMember> nine = members(9);
        nine.add(4, new DerbyMember(ME, "Jxxq", 2, List.of(DIAMOND)));
        DerbySnapshot s = snap(true, 5, true, true, 0, nine, 2, List.of(DIAMOND));
        DerbyMenuView.Cell[] first = DerbyMenuView.cells(s, ME, draft(), 0, 0, false, false);
        assertEquals(ME, first[DerbyMenuView.FIRST_HEAD].member().id());
        assertEquals("P0", first[DerbyMenuView.FIRST_HEAD + 1].member().name());
        assertEquals(List.of("2", "0", "1", "2", "3", "0", "1", "2"), first[DerbyMenuView.LOBBY].strings());
        assertEquals(List.of("10", "1/2", ""), first[DerbyMenuView.INFO].strings());
        assertEquals(DerbyMenuView.Action.PAGER, first[DerbyMenuView.PAGER].action());
        DerbyMenuView.Cell[] second = DerbyMenuView.cells(s, ME, draft(), 0, 1, false, false);
        assertEquals("P7", second[DerbyMenuView.FIRST_HEAD].member().name());
        assertEquals("P8", second[DerbyMenuView.FIRST_HEAD + 1].member().name());
        assertNull(second[DerbyMenuView.FIRST_HEAD + 2]);
        assertEquals(List.of("3", "0", "", "", "", "", "", ""), second[DerbyMenuView.LOBBY].strings());
        assertEquals("2/2", second[DerbyMenuView.INFO].strings().get(1));
        // a page beyond the last is clamped (peoplePage = min(peoplePage, pages - 1))
        assertEquals(second[DerbyMenuView.FIRST_HEAD], DerbyMenuView.cells(s, ME, draft(), 0, 5, false, false)
                [DerbyMenuView.FIRST_HEAD]);
        DerbyMenuView.Cell[] empty = cells(idle(), draft());
        assertEquals(List.of("0", "", "empty"), empty[DerbyMenuView.INFO].strings());
        assertNull(empty[DerbyMenuView.LOBBY]);
        assertNull(empty[DerbyMenuView.PAGER]);
        DerbyMenuView.Cell[] eight = cells(snap(true, 5, false, true, 0, members(8), -1, List.of()), draft());
        assertNull(eight[DerbyMenuView.PAGER]);
        assertEquals(List.of("8", "", ""), eight[DerbyMenuView.INFO].strings());
        assertEquals(8, DerbyMenuView.pages(64));
        assertEquals(1, DerbyMenuView.pages(0));
    }

    /** The help panel replaces everything; every slot closes it. */
    @Test
    void helpPanel() {
        DerbyMenuView.Cell[] c = DerbyMenuView.cells(idle(), ME, draft(1), 1, 0, true, false);
        for (int slot = 0; slot < DerbyMenuView.SLOTS; slot++) {
            assertEquals(DerbyMenuView.Action.CLOSE_HELP, c[slot].action(), "slot " + slot);
            assertTrue(c[slot].active());
        }
        assertEquals("help_left", model(c[DerbyMenuView.HELP_LEFT]));
        assertEquals("help_right", model(c[DerbyMenuView.HELP_RIGHT]));
        assertEquals("help", model(cells(idle(), draft())[DerbyMenuView.HELP]));
    }

    /** Equal states give equal cells (only changed slots are re-sent). */
    @Test
    void cellsAreValues() {
        DerbySnapshot s = snap(true, 5, false, true, 0, members(11), -1, List.of());
        assertArrayEquals(DerbyMenuView.cells(s, ME, draft(2), 2, 1, false, false),
                DerbyMenuView.cells(s, ME, draft(2), 2, 1, false, false));
        DerbyMenuView.Cell[] a = DerbyMenuView.cells(s, ME, draft(2), 2, 0, false, false);
        DerbyMenuView.Cell[] b = DerbyMenuView.cells(snap(true, 4, false, true, 0, members(11), -1, List.of()), ME,
                draft(2), 2, 0, false, false);
        List<Integer> changed = new ArrayList<>();
        for (int slot = 0; slot < DerbyMenuView.SLOTS; slot++) {
            if (!java.util.Objects.equals(a[slot], b[slot])) {
                changed.add(slot);
            }
        }
        assertEquals(List.of(DerbyMenuView.HINT), changed, "a second of the countdown changes only the hint");
    }

    /** The title: both background tiles, the four cards and "DUCK DERBY" from the messages. */
    @Test
    void titleUsesThePackLayout() {
        GuiLayout layout = GuiLayout.load(getClass().getClassLoader().getResourceAsStream("pack/gui-layout.json"));
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(
                getClass().getClassLoader().getResourceAsStream("messages_de.yml"), StandardCharsets.UTF_8));
        Component title = DerbyBetMenu.title(layout, m -> yaml.getString(m.key()));
        StringBuilder text = new StringBuilder();
        for (Component c : title.children()) {
            text.append(((TextComponent) c).content());
        }
        for (String glyph : List.of("derby_bg_0", "derby_bg_1", "derby_card_0", "derby_card_1", "derby_card_2",
                "derby_card_3")) {
            assertTrue(text.indexOf(Character.toString(layout.glyph(glyph).codePoint())) >= 0, glyph);
        }
        assertTrue(text.indexOf(yaml.getString(Msg.GUI_DERBY_TITLE.key())) >= 0);
        assertEquals("DUCK DERBY", yaml.getString(Msg.GUI_DERBY_TITLE.key()));
    }
}
