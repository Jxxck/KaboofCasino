package de.tigges.kaboofcasino.game.poker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.wager.ItemAmount;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The spectator panel's lines as {@code PokerTableRenderer.extractPanel}/{@code panel} derive them. */
class PokerPanelTest {

    private static final PokerPanel.Content CONTENT = new PokerPanel.Content(UUID.randomUUID(), "Jxxq",
            List.of(new ItemAmount("minecraft:diamond", 4), new ItemAmount("minecraft:gold_block", 2)));

    /** The view of a whole hand at every tick (the view object keeps growing, as the block entity's). */
    private static PokerTableState hand(PokerGeometryTest.Play play, int decideAfter, int... rigged) {
        return PokerGeometryTest.play(1, play, decideAfter, 5, null, rigged);
    }

    @Test
    void handsFollowTheAppearDelayFlipAndResultDelay() {
        // player A A, dealer 2 7 (king high with the board: does not qualify), board K 9 4 + 5 J; call at 209
        PokerTableState v = hand(PokerGeometryTest.Play.CALL, 0, PokerGeometryTest.NO_QUALIFY);
        long s = v.start;
        // player card 1 (ace) dealt at +50: counts from +72 (high card)
        assertEquals(-1, PokerPanel.hands(v, s + 71).player());
        assertEquals(PokerHands.HIGH_CARD, PokerPanel.hands(v, s + 72).player());
        // the second ace (dealt at +94) from +116: a pair; the dealer's cards are face down (no dealer hand)
        assertEquals(PokerHands.HIGH_CARD, PokerPanel.hands(v, s + 115).player());
        assertEquals(new PokerPanel.Hands(PokerHands.PAIR, -1, false, 0), PokerPanel.hands(v, s + 116));
        // the reveal: index 1 at +258 (c + 49), index 0 at +280; each counts 10 ticks after its flip
        assertEquals(-1, PokerPanel.hands(v, s + 289).dealer());
        PokerPanel.Hands shown = PokerPanel.hands(v, s + 290);
        assertEquals(PokerHands.HIGH_CARD, shown.dealer());
        assertFalse(shown.qualifies());
        // the decision at +304 (c + 95) shows 4 ticks later
        assertEquals(0, PokerPanel.hands(v, s + 307).result());
        assertEquals(PokerRound.Result.NO_QUALIFY.code, PokerPanel.hands(v, s + 308).result());
    }

    @Test
    void aQualifyingDealerIsWhiteAndTheBoardCounts() {
        // dealer 5 7 with the board's kings: a pair that qualifies; the player's aces make a full house with the board
        PokerTableState v = hand(PokerGeometryTest.Play.CALL, 0, PokerGeometryTest.CELEBRATE);
        PokerPanel.Hands h = PokerPanel.hands(v, v.resultAt + 4);
        assertEquals(new PokerPanel.Hands(PokerHands.FULL_HOUSE, PokerHands.PAIR, true, PokerRound.Result.WIN.code), h);
        Component text = PokerPanel.render(CONTENT, h);
        TextComponent dealer = line(text, PokerPanel.DEALER_KEY);
        assertEquals(TextColor.color(0xFFFFFF), dealer.color());
        assertEquals(TextColor.color(0x88CF86), line(text, "gui.roulettecasino.poker.result_1").color());
    }

    @Tag("german")
    @Test
    void aFoldShowsNoDealerHand() {
        PokerTableState v = hand(PokerGeometryTest.Play.FOLD, 30, PokerGeometryTest.PUSH);
        PokerPanel.Hands h = PokerPanel.hands(v, v.resultAt + 4);
        assertEquals(-1, h.dealer());
        assertEquals(PokerRound.Result.FOLD.code, h.result());
        // 2 3 with the flop 10 J Q: the straight would have needed the turn and the river
        assertEquals(PokerHands.HIGH_CARD, h.player());
        Component text = PokerPanel.render(CONTENT, h);
        assertNull(find(text, PokerPanel.DEALER_KEY));
        TextComponent result = line(text, "gui.roulettecasino.poker.result_5");
        assertEquals(TextColor.color(0xE79882), result.color());
        assertTrue(result.hasDecoration(TextDecoration.BOLD));
        assertEquals("AUSGESTIEGEN", find(text, "gui.roulettecasino.poker.result_5").fallback());
    }

    @Test
    void visibleOnlyWhileActiveWithAStake() {
        PokerTableState v = new PokerTableState(1000L, 1);
        v.phaseChanged(PokerRound.Phase.DEALING, 0);
        assertTrue(PokerPanel.visible(CONTENT, v));
        assertFalse(PokerPanel.visible(null, v));
        assertFalse(PokerPanel.visible(CONTENT, null));
        assertFalse(PokerPanel.visible(CONTENT, new PokerTableState()));
        assertFalse(PokerPanel.visible(new PokerPanel.Content(CONTENT.owner(), "x", List.of()), v));
        v.phaseChanged(PokerRound.Phase.DONE, 407);
        assertFalse(PokerPanel.visible(CONTENT, v));
    }

    @Tag("german")
    @Test
    void linesAppearInTheModsOrderAndColours() {
        Component early = PokerPanel.render(CONTENT, new PokerPanel.Hands(-1, -1, false, 0));
        assertNull(find(early, PokerPanel.YOU_KEY));
        // the dealer's line needs the player's (renderer 237)
        Component dealerOnly = PokerPanel.render(CONTENT, new PokerPanel.Hands(-1, 2, true, 0));
        assertNull(find(dealerOnly, PokerPanel.DEALER_KEY));
        Component all = PokerPanel.render(CONTENT, new PokerPanel.Hands(PokerHands.TWO_PAIR, PokerHands.HIGH_CARD,
                false, PokerRound.Result.NO_QUALIFY.code));
        List<String> keys = new ArrayList<>();
        collect(all, keys);
        assertEquals(List.of(PokerPanel.YOU_KEY, "gui.roulettecasino.poker.hand_2", PokerPanel.DEALER_KEY,
                "gui.roulettecasino.poker.hand_0", "gui.roulettecasino.poker.result_2"), keys);
        assertEquals(PokerPanel.GOLD, line(all, PokerPanel.YOU_KEY).color());
        assertEquals(PokerPanel.RED, line(all, PokerPanel.DEALER_KEY).color());
        assertEquals("Zwei Paare", find(all, "gui.roulettecasino.poker.hand_2").fallback());
        assertEquals("CROUPIER SPIELT NICHT", find(all, "gui.roulettecasino.poker.result_2").fallback());
        // five lines: badge, stakes, you, dealer, result
        assertEquals(4, newlines(all));
        // the colours by result code (renderer 46)
        int[] colours = {0, 0x88CF86, 0x88CF86, 0xFFFFFF, 0xE79882, 0xE79882};
        for (int code = 1; code <= 5; code++) {
            Component c = PokerPanel.render(CONTENT, new PokerPanel.Hands(1, 1, true, code));
            assertEquals(TextColor.color(colours[code]), line(c, "gui.roulettecasino.poker.result_" + code).color());
        }
    }

    @Test
    void contentCopiesItsStakes() {
        List<ItemAmount> stakes = new ArrayList<>(List.of(new ItemAmount("minecraft:emerald", 3)));
        PokerPanel.Content c = new PokerPanel.Content(UUID.randomUUID(), "a", stakes);
        stakes.clear();
        assertEquals(1, c.stakes().size());
        assertEquals(c, new PokerPanel.Content(c.owner(), "a", List.of(new ItemAmount("minecraft:emerald", 3))));
    }

    private static int newlines(Component c) {
        int n = c instanceof TextComponent t && t.content().equals("\n") ? 1 : 0;
        for (Component child : c.children()) {
            n += newlines(child);
        }
        return n;
    }

    private static void collect(Component c, List<String> keys) {
        if (c instanceof TranslatableComponent t) {
            keys.add(t.key());
        }
        for (Component child : c.children()) {
            collect(child, keys);
        }
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

    /** The line (a direct child of the panel text) holding {@code key}. */
    private static TextComponent line(Component text, String key) {
        for (Component child : text.children()) {
            if (find(child, key) != null && child instanceof TextComponent t) {
                return t;
            }
        }
        assertNotNull(null, key);
        return null;
    }
}
