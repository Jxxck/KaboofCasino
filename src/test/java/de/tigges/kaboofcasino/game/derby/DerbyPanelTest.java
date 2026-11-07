package de.tigges.kaboofcasino.game.derby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.wager.ItemAmount;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ObjectComponent;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.object.PlayerHeadObjectContents;
import org.junit.jupiter.api.Test;

/** The floating panel's content and lines as {@code DuckDerbyRenderer.extractPanel}/{@code panel}/{@code sharedPanel}
 * derive them, and that nothing of the result shows before {@code resultTick}. */
class DerbyPanelTest {

    private static final long START = 50_000_123L;
    private static final ItemAmount DIAMONDS = new ItemAmount("minecraft:diamond", 16);
    private static final ItemAmount GOLD = new ItemAmount("minecraft:gold_block", 3);
    private static final ItemAmount COAL = new ItemAmount("minecraft:coal", 64);

    private static DerbyMember member(int n, int lane, ItemAmount... stakes) {
        return new DerbyMember(new UUID(7, n), "Spieler" + n, lane, List.of(stakes));
    }

    private static List<ItemAmount> displayed(int lane, ItemAmount... rows) {
        ItemAmount[] slots = new ItemAmount[DerbyView.SLOTS];
        for (int row = 0; row < rows.length; row++) {
            slots[row * 4 + lane] = rows[row];
        }
        return Arrays.asList(slots);
    }

    private static DuckDerbyRace.Plan plan(int winner) {
        return DuckDerbyRace.plan(winner, 0xD0C5L + winner);
    }

    private static DerbyView solo(DuckDerbyRace.Plan plan, int lane, ItemAmount... rows) {
        return new DerbyView(START, plan, false, false, -1, 1, List.of(member(1, lane, rows)), displayed(lane, rows));
    }

    // ---- what counts --------------------------------------------------------------------------------------------------

    @Test
    void idleMachineHasNoPanel() {
        assertNull(DerbyPanel.content(DerbyView.idle(), START));
        assertNull(DerbyPanel.content(null, START));
    }

    @Test
    void soloShowsTheBadgeTheLaneAndItsRows() {
        DerbyView v = solo(plan(1), 2, DIAMONDS, GOLD);
        DerbyPanel.Solo c = assertInstanceOf(DerbyPanel.Solo.class, DerbyPanel.content(v, START + 30));
        assertEquals(new UUID(7, 1), c.badgeId());
        assertEquals("Spieler1", c.badgeName());
        assertEquals(List.of(new DerbyPanel.Lane(2, List.of(DIAMONDS, GOLD))), c.lanes());
        assertEquals(-1, c.winner());
        assertEquals(-1, c.second());
        assertEquals(1 + 1 + 2, c.lines());
        // a hole in the rows is skipped like the renderer's visibleRow
        ItemAmount[] slots = new ItemAmount[12];
        slots[3] = COAL;
        slots[2 * 4 + 3] = GOLD;
        DerbyView holes = new DerbyView(START, plan(0), false, false, -1, 1, List.of(), Arrays.asList(slots));
        DerbyPanel.Solo h = assertInstanceOf(DerbyPanel.Solo.class, DerbyPanel.content(holes, START + 1));
        assertEquals(List.of(new DerbyPanel.Lane(3, List.of(COAL, GOLD))), h.lanes());
        assertNull(h.badgeId(), "no entrant, no badge");
    }

    /** The result (winner, second, their lines and colours) appears exactly at {@code resultTick}, never before. */
    @Test
    void nothingOfTheResultBeforeTheResultTick() {
        for (int winner = 0; winner < 4; winner++) {
            DuckDerbyRace.Plan plan = plan(winner);
            int second = DerbyRules.runnerUp(plan);
            DerbyView v = solo(plan, (winner + 1) % 4, DIAMONDS);
            DerbyView g = new DerbyView(START, plan, true, false, -1, 2,
                    List.of(member(1, winner, COAL), member(2, (winner + 2) % 4, GOLD)), List.of());
            for (long t = START - 5; t <= START + plan.endTick() + 5; t++) {
                boolean shown = t - START >= plan.resultTick() && t - START <= plan.endTick() + 1;
                for (DerbyView view : List.of(v, g)) {
                    DerbyPanel.Content c = DerbyPanel.content(view, t);
                    assertNotNull(c);
                    Component text = DerbyPanel.render(c);
                    int w = c instanceof DerbyPanel.Solo s ? s.winner() : ((DerbyPanel.Shared) c).winner();
                    int s2 = c instanceof DerbyPanel.Solo s ? s.second() : ((DerbyPanel.Shared) c).second();
                    if (shown) {
                        assertEquals(winner, w);
                        assertEquals(second, s2);
                        assertEquals(1, count(text, DerbyPanel.PAYOUT_KEY));
                        assertEquals(1, count(text, DerbyPanel.SECOND_KEY));
                    } else {
                        assertEquals(-1, w, "at " + (t - START));
                        assertEquals(-1, s2, "at " + (t - START));
                        assertEquals(0, count(text, DerbyPanel.PAYOUT_KEY), "at " + (t - START));
                        assertEquals(0, count(text, DerbyPanel.SECOND_KEY), "at " + (t - START));
                        assertEquals(0, count(text, DerbyPanel.RESULT_LINE_KEY), "at " + (t - START));
                        if (view == v) {
                            // the only duck name on a solo panel before the result is the staked lane's header
                            assertEquals(1, names(text));
                        }
                    }
                }
            }
        }
    }

    @Test
    void soloResultLinesAreBoldWithTheDucksColour() {
        DuckDerbyRace.Plan plan = plan(3);
        int second = DerbyRules.runnerUp(plan);
        DerbyView v = solo(plan, 3, DIAMONDS);
        Component text = DerbyPanel.render(DerbyPanel.content(v, START + plan.resultTick()));
        TranslatableComponent line = find(text, DerbyPanel.RESULT_LINE_KEY);
        assertNotNull(line);
        assertTrue(line.hasDecoration(TextDecoration.BOLD));
        TranslatableComponent duck = (TranslatableComponent) line.arguments().get(1).asComponent();
        assertEquals(DerbyPanel.NAME_KEYS[3], duck.key());
        assertEquals(TextColor.color(0xAAAAAA), duck.color());
        List<TranslatableComponent> all = new ArrayList<>();
        collect(text, DerbyPanel.RESULT_LINE_KEY, all);
        assertEquals(2, all.size());
        TranslatableComponent secondDuck = (TranslatableComponent) all.get(1).arguments().get(1).asComponent();
        assertEquals(DerbyPanel.NAME_KEYS[second], secondDuck.key());
        assertEquals(TextColor.color(DerbyPanel.WINNER_NAME[second]), secondDuck.color());
        assertEquals(1 + 1 + 1 + 2, DerbyPanel.content(v, START + plan.resultTick()).lines());
        assertEquals(lines(text), DerbyPanel.content(v, START + plan.resultTick()).lines());
    }

    @Test
    void groupPanelCountdownTitleBettorsAndMore() {
        List<DerbyMember> members = new ArrayList<>();
        for (int n = 0; n < 11; n++) {
            members.add(member(n, n % 4, DIAMONDS, GOLD, COAL, DIAMONDS));
        }
        long until = START + 200;
        DerbyView enrolling = new DerbyView(-1, null, true, true, until, 11, members, List.of());
        DerbyPanel.Shared c = assertInstanceOf(DerbyPanel.Shared.class, DerbyPanel.content(enrolling, START));
        assertEquals(10, c.seconds());
        assertEquals(DerbyPanel.BAR_STEPS, c.bar());
        assertEquals(8, c.bettors().size());
        assertEquals(3, c.bettors().get(0).stakes().size());
        assertEquals(new UUID(7, 0), c.bettors().get(0).id(), "join order");
        assertEquals(2 + 1 + 8 + 1, c.lines());
        Component text = DerbyPanel.render(c);
        assertEquals(c.lines(), lines(text));
        assertEquals(1, count(text, DerbyPanel.COUNTDOWN_KEY));
        assertEquals(0, count(text, DerbyPanel.RACING_KEY));
        assertEquals(1, count(text, DerbyPanel.MORE_KEY));
        assertEquals("3", plain(find(text, DerbyPanel.MORE_KEY).arguments().get(0).asComponent()));
        assertEquals(1, count(text, "gui.roulettecasino.derby.people"));
        assertEquals(8, heads(text));
        // seconds = (until - now + 19) / 20, the bar the remaining part of the 200 ticks in 20 steps
        assertEquals(1, ((DerbyPanel.Shared) DerbyPanel.content(enrolling, until - 1)).seconds());
        assertEquals(0, ((DerbyPanel.Shared) DerbyPanel.content(enrolling, until)).seconds());
        assertEquals(10, ((DerbyPanel.Shared) DerbyPanel.content(enrolling, START + 100)).bar());
        assertEquals(0, ((DerbyPanel.Shared) DerbyPanel.content(enrolling, until)).bar());
        assertEquals(7, ((DerbyPanel.Shared) DerbyPanel.content(enrolling, START + 61)).seconds());
        // racing: no countdown, "N Spieler · Rennen läuft"
        DuckDerbyRace.Plan plan = plan(2);
        DerbyView racing = new DerbyView(START, plan, true, false, -1, 2, members.subList(0, 2), List.of());
        DerbyPanel.Shared r = (DerbyPanel.Shared) DerbyPanel.content(racing, START + 40);
        assertEquals(1 + 2, r.lines());
        Component rt = DerbyPanel.render(r);
        assertEquals(1, count(rt, DerbyPanel.RACING_KEY));
        assertEquals(0, count(rt, DerbyPanel.COUNTDOWN_KEY));
        assertEquals(0, count(rt, DerbyPanel.MORE_KEY));
        // the result replaces the racing line by a second line
        Component done = DerbyPanel.render(DerbyPanel.content(racing, START + plan.resultTick()));
        assertEquals(0, count(done, DerbyPanel.RACING_KEY));
        assertEquals(2, count(done, DerbyPanel.RESULT_LINE_KEY));
        // title, the result line, the two bettors
        assertEquals(2 + 2, lines(done));
        assertEquals(lines(done), DerbyPanel.content(racing, START + plan.resultTick()).lines());
        // one entrant: "person"
        DerbyView one = new DerbyView(-1, null, true, true, until, 1, members.subList(0, 1), List.of());
        assertEquals(1, count(DerbyPanel.render(DerbyPanel.content(one, START)), "gui.roulettecasino.derby.person"));
    }

    @Test
    void groupSessionWithoutMembersFallsBackToTheSoloRule() {
        // shared but members 0: the renderer draws the solo columns (no badge during a group session)
        DerbyView v = new DerbyView(START, plan(0), true, false, -1, 0, List.of(member(1, 0, DIAMONDS)),
                displayed(0, DIAMONDS));
        DerbyPanel.Solo c = assertInstanceOf(DerbyPanel.Solo.class, DerbyPanel.content(v, START + 5));
        assertNull(c.badgeId());
    }

    // ---- line geometry -------------------------------------------------------------------------------------------------

    @Test
    void headerLineIsTheEdgeWideWithTheNameCentredOnTheBox() {
        for (int lane = 0; lane < 4; lane++) {
            List<int[]> runs = new ArrayList<>();
            int total = advance(DerbyPanel.header(lane), null, runs);
            assertEquals(DerbyPanel.EDGE_ADVANCE, total, "lane " + lane);
            // runs: {start, width} of the label; the box spans 1 .. 55
            assertEquals(1, runs.size());
            int start = runs.get(0)[0];
            int width = runs.get(0)[1];
            assertEquals(DerbyPanel.width(DerbyPanel.NAMES[lane]), width);
            assertTrue(Math.abs((start + width / 2.0) - 28.0) <= 0.5, "lane " + lane + " start " + start);
        }
        assertEquals(TextColor.color(0x9B2631), fillColor(DerbyPanel.header(1)));
        assertEquals(TextColor.color(0x21252B), fillColor(DerbyPanel.header(3)));
    }

    @Test
    void countdownBarIsTheTrackWideAndFilledByFiveSteps() {
        for (int steps = 0; steps <= DerbyPanel.BAR_STEPS; steps++) {
            Component bar = DerbyPanel.bar(steps);
            assertEquals(DerbyPanel.TRACK_ADVANCE, advance(bar, null, new ArrayList<>()), "steps " + steps);
            int filled = 0;
            for (int cp : glyphs(bar)) {
                if (cp >= DerbyPanel.BAR && cp < DerbyPanel.BAR + 5) {
                    filled += DerbyPanel.BAR_STEP_PX << (cp - DerbyPanel.BAR);
                }
            }
            assertEquals(steps * DerbyPanel.BAR_STEP_PX, filled);
        }
    }

    @Test
    void bettorLineHasTheLaneStripUnderTheHead() {
        DerbyPanel.Bettor b = new DerbyPanel.Bettor(new UUID(1, 2), "Jxxq", 1, List.of(DIAMONDS, GOLD));
        Component line = DerbyPanel.bettor(b);
        List<Component> flat = new ArrayList<>();
        flatten(line, flat);
        // the strip glyph and its back-space first, in the lane colour, then the head
        TextComponent strip = (TextComponent) flat.get(1);
        assertEquals(Character.toString(DerbyPanel.STRIP) + DerbyPanel.spaces(-DerbyPanel.STRIP_ADVANCE),
                strip.content());
        assertEquals(TextColor.color(DerbyPanel.HEADER[1]), strip.color());
        assertTrue(flat.get(2) instanceof ObjectComponent o && o.contents() instanceof PlayerHeadObjectContents,
                "the head right after the strip");
        assertEquals(1, heads(line));
    }

    @Test
    void spacesAddUp() {
        for (int d = -300; d <= 300; d++) {
            int sum = 0;
            for (int cp : DerbyPanel.spaces(d).codePoints().toArray()) {
                sum += cp < DerbyPanel.POS_SPACE ? -(1 << (cp - DerbyPanel.NEG_SPACE)) : 1 << (cp - DerbyPanel.POS_SPACE);
            }
            assertEquals(d, sum);
        }
    }

    @Test
    void panelFootCentresTheText() {
        DerbyPanel.Solo small = new DerbyPanel.Solo(null, null, List.of(new DerbyPanel.Lane(0, List.of(DIAMONDS))),
                -1, -1);
        double foot = DerbyRig.panelFoot(small);
        double h = (small.lines() * 10 - 1) * DerbyRig.PANEL_PX;
        assertEquals(2.6, foot + h / 2, 1e-9);
        List<DerbyPanel.Bettor> eight = new ArrayList<>();
        for (int n = 0; n < 8; n++) {
            eight.add(new DerbyPanel.Bettor(new UUID(0, n), "p" + n, n % 4, List.of(COAL)));
        }
        DerbyPanel.Shared tall = new DerbyPanel.Shared(9, true, 5, 10, eight, -1, -1);
        // taller than 70 px: the bottom stays 0.385 below 2.6 like the renderer's lifted panel
        assertEquals(2.6 - 70 * DerbyRig.PANEL_PX / 2, DerbyRig.panelFoot(tall), 1e-9);
        assertFalse(DerbyRig.panelFoot(tall) < 2.0, "above the sign (31 px)");
    }

    // ---- helpers ------------------------------------------------------------------------------------------------------

    private static void flatten(Component c, List<Component> out) {
        out.add(c);
        for (Component child : c.children()) {
            flatten(child, out);
        }
    }

    private static int count(Component c, String key) {
        int n = c instanceof TranslatableComponent t && t.key().equals(key) ? 1 : 0;
        if (c instanceof TranslatableComponent t) {
            for (var a : t.arguments()) {
                n += count(a.asComponent(), key);
            }
        }
        for (Component child : c.children()) {
            n += count(child, key);
        }
        return n;
    }

    /** Duck names anywhere in the text. */
    private static int names(Component c) {
        int n = 0;
        for (String key : DerbyPanel.NAME_KEYS) {
            n += count(c, key);
        }
        return n;
    }

    /** Player heads (not item sprites). */
    private static int heads(Component c) {
        int n = c instanceof ObjectComponent o && o.contents() instanceof PlayerHeadObjectContents ? 1 : 0;
        for (Component child : c.children()) {
            n += heads(child);
        }
        return n;
    }

    /** Lines of the text: newlines + 1. */
    private static int lines(Component c) {
        return newlines(c) + 1;
    }

    private static int newlines(Component c) {
        int n = c instanceof TextComponent t ? (int) t.content().chars().filter(ch -> ch == '\n').count() : 0;
        for (Component child : c.children()) {
            n += newlines(child);
        }
        return n;
    }

    private static TranslatableComponent find(Component c, String key) {
        List<TranslatableComponent> out = new ArrayList<>();
        collect(c, key, out);
        return out.isEmpty() ? null : out.get(0);
    }

    private static void collect(Component c, String key, List<TranslatableComponent> out) {
        if (c instanceof TranslatableComponent t) {
            if (t.key().equals(key)) {
                out.add(t);
            }
            for (var a : t.arguments()) {
                collect(a.asComponent(), key, out);
            }
        }
        for (Component child : c.children()) {
            collect(child, key, out);
        }
    }

    private static String plain(Component c) {
        StringBuilder sb = new StringBuilder();
        if (c instanceof TextComponent t) {
            sb.append(t.content());
        }
        for (Component child : c.children()) {
            sb.append(plain(child));
        }
        return sb.toString();
    }

    private static List<Integer> glyphs(Component c) {
        List<Integer> out = new ArrayList<>();
        List<Component> flat = new ArrayList<>();
        flatten(c, flat);
        for (Component f : flat) {
            if (f instanceof TextComponent t) {
                t.content().codePoints().forEach(out::add);
            }
        }
        return out;
    }

    private static TextColor fillColor(Component header) {
        List<Component> flat = new ArrayList<>();
        flatten(header, flat);
        for (Component f : flat) {
            if (f instanceof TextComponent t && t.content().equals(Character.toString(DerbyPanel.FILL))) {
                return t.color();
            }
        }
        return null;
    }

    /** The panel font's advances (DerbyPackKeysTest checks them against the pack). */
    private static int glyphAdvance(int cp) {
        if (cp >= DerbyPanel.NEG_SPACE && cp < DerbyPanel.POS_SPACE) {
            return -(1 << (cp - DerbyPanel.NEG_SPACE));
        }
        if (cp >= DerbyPanel.POS_SPACE && cp < DerbyPanel.POS_SPACE + 8) {
            return 1 << (cp - DerbyPanel.POS_SPACE);
        }
        if (cp >= DerbyPanel.BAR && cp < DerbyPanel.BAR + 5) {
            return (DerbyPanel.BAR_STEP_PX << (cp - DerbyPanel.BAR)) + 1;
        }
        return switch (cp) {
            case DerbyPanel.EDGE -> DerbyPanel.EDGE_ADVANCE;
            case DerbyPanel.FILL -> DerbyPanel.FILL_ADVANCE;
            case DerbyPanel.STRIP -> DerbyPanel.STRIP_ADVANCE;
            case DerbyPanel.TRACK -> DerbyPanel.TRACK_ADVANCE;
            default -> throw new AssertionError("not a panel glyph " + Integer.toHexString(cp));
        };
    }

    /**
     * The pen advance of a line of panel-font glyphs and translatable labels (German fallback widths); the start and
     * width of each label go to {@code labels}.
     */
    private static int advance(Component c, Key inherited, List<int[]> labels) {
        Key font = c.font() != null ? c.font() : inherited;
        int pen = 0;
        if (c instanceof TextComponent t) {
            for (int cp : t.content().codePoints().toArray()) {
                assertEquals(DerbyPanel.FONT, font, "glyph outside the panel font");
                pen += glyphAdvance(cp);
            }
        } else if (c instanceof TranslatableComponent t) {
            assertNull(font, "labels in the default font");
            int w = DerbyPanel.width(t.fallback());
            labels.add(new int[] {0, w});
            pen += w;
        }
        for (Component child : c.children()) {
            int before = labels.size();
            int adv = advance(child, font, labels);
            for (int i = before; i < labels.size(); i++) {
                labels.get(i)[0] += pen;
            }
            pen += adv;
        }
        return pen;
    }
}
