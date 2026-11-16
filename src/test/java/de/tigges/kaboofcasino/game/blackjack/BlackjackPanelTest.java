package de.tigges.kaboofcasino.game.blackjack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.wager.ItemAmount;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The spectator panel's lines as {@code BlackjackTableRenderer.extractPanel}/{@code panel} derive them. */
class BlackjackPanelTest {

    private static final BlackjackPanel.Content CONTENT = new BlackjackPanel.Content(UUID.randomUUID(), "Jxxq",
            List.of(new ItemAmount("minecraft:diamond", 4), new ItemAmount("minecraft:gold_block", 2)));

    @Test
    void totalsFollowTheAppearDelayFlipAndResultDelay() {
        BlackjackTableState v = BlackjackGeometryTest.demoView();
        long s = v.start;
        // player card 0 (id 5 = six) dealt at +50: counts from +72
        assertEquals(-1, BlackjackPanel.totals(v, s + 71).player());
        assertEquals(6, BlackjackPanel.totals(v, s + 72).player());
        assertEquals(-1, BlackjackPanel.totals(v, s + 72).dealer());
        // dealer up card (12 = king) from +94, player 6 + 8 (id 20) from +116
        assertEquals(new BlackjackPanel.Totals(14, 10, 0), BlackjackPanel.totals(v, s + 116));
        // the hole card (dealt face down, -1) adds nothing; after the reveal at +300 (id 30 = five) it counts from +310
        assertEquals(10, BlackjackPanel.totals(v, s + 309).dealer());
        assertEquals(15, BlackjackPanel.totals(v, s + 310).dealer());
        // the result shows 4 ticks after the decision
        assertEquals(0, BlackjackPanel.totals(v, s + 356).result());
        assertEquals(BlackjackRound.Result.WIN.code, BlackjackPanel.totals(v, s + 357).result());
    }

    @Test
    void visibleOnlyWhileActiveWithAStake() {
        BlackjackTableState v = BlackjackGeometryTest.demoView();
        assertTrue(BlackjackPanel.visible(CONTENT, v));
        assertFalse(BlackjackPanel.visible(null, v));
        assertFalse(BlackjackPanel.visible(CONTENT, new BlackjackTableState()));
        assertFalse(BlackjackPanel.visible(new BlackjackPanel.Content(CONTENT.owner(), "x", List.of()), v));
        v.phaseChanged(BlackjackRound.Phase.DONE, 407);
        assertFalse(BlackjackPanel.visible(CONTENT, v));
    }

    private static int count(Component c, String key) {
        int n = c instanceof TranslatableComponent t && t.key().equals(key) ? 1 : 0;
        for (Component child : c.children()) {
            n += count(child, key);
        }
        return n;
    }

    private static TranslatableComponent find(Component c, String key) {
        if (c instanceof TranslatableComponent t && t.key().equals(key)) {
            return t;
        }
        for (Component child : c.children()) {
            TranslatableComponent t = find(child, key);
            if (t != null) {
                return t;
            }
        }
        return null;
    }

    @Tag("german")
    @Test
    void linesAppearInTheModsOrder() {
        Component early = BlackjackPanel.render(CONTENT, new BlackjackPanel.Totals(-1, -1, 0));
        assertEquals(0, count(early, "gui.roulettecasino.blackjack.you"));
        Component player = BlackjackPanel.render(CONTENT, new BlackjackPanel.Totals(15, -1, 0));
        assertEquals(1, count(player, "gui.roulettecasino.blackjack.you"));
        assertEquals(0, count(player, "gui.roulettecasino.blackjack.dealer"));
        Component both = BlackjackPanel.render(CONTENT, new BlackjackPanel.Totals(23, 17, 5));
        assertEquals(1, count(both, "gui.roulettecasino.blackjack.dealer"));
        TranslatableComponent result = find(both, "gui.roulettecasino.blackjack.result_5");
        assertEquals("ÜBERKAUFT", result.fallback());
        assertEquals("DU", find(both, "gui.roulettecasino.blackjack.you").fallback());
        assertTrue(both.children().stream().anyMatch(c -> c.hasDecoration(TextDecoration.BOLD)));
    }
}
