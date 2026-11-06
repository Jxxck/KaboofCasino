package de.tigges.kaboofcasino.game.blackjack;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.wager.ItemAmount;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The round view's client logic ({@code BlackjackScreen}) on snapshots: no server, no items. */
class BlackjackRoundMenuTest {

    private static final String M = BlackjackRoundMenu.MODEL;

    private static BlackjackSnapshot snap(int phase, List<Integer> player, List<Long> playerAt, List<Integer> dealer,
                                          List<Long> dealerAt, int turnLeft, int result, boolean[] flags,
                                          List<ItemAmount> stakes, long now) {
        return new BlackjackSnapshot(phase, player, dealer, playerAt, dealerAt, turnLeft, result, flags[0], flags[1],
                flags[2], flags[3], flags[4], stakes, now);
    }

    /** hit, stand, double, doubled, afford */
    private static boolean[] flags(boolean... f) {
        return f;
    }

    private static final List<ItemAmount> ONE = List.of(new ItemAmount("minecraft:diamond", 10));

    private static BlackjackSnapshot turn(long now, boolean canDouble, boolean afford, int left) {
        return snap(BlackjackSnapshot.PHASE_TURN, List.of(8, 1), List.of(0L, 0L), List.of(9, -1), List.of(0L, 0L),
                left, 0, flags(true, true, canDouble, false, afford), ONE, now);
    }

    private static String model(BlackjackRoundMenu.Cell c) {
        return c == null ? null : c.model();
    }

    @Test
    void cardsAppearTwentyTwoTicksAfterTheDeal() {
        BlackjackRoundMenu.View view = new BlackjackRoundMenu.View();
        List<Integer> cards = new ArrayList<>();
        List<Long> at = new ArrayList<>();
        // opened before any card: four ticks of "instant" pass without cards
        for (long t = 100; t < 105; t++) {
            view.cells(snap(1, cards, at, List.of(), List.of(), 0, 0, flags(false, false, false, false, false), ONE,
                    t), true);
        }
        cards.add(5);
        at.add(110L);
        for (long t = 110; t < 132; t++) {
            BlackjackRoundMenu.Cell[] c = view.cells(snap(1, cards, at, List.of(), List.of(), 0, 0,
                    flags(false, false, false, false, false), ONE, t), true);
            assertNull(c[BlackjackRoundMenu.PLAYER_ROW], "tick " + t);
            assertNull(c[BlackjackRoundMenu.PLAYER_BADGE]);
        }
        BlackjackRoundMenu.Cell[] c = view.cells(snap(1, cards, at, List.of(), List.of(), 0, 0,
                flags(false, false, false, false, false), ONE, 132), true);
        assertEquals(M + "card_5", model(c[BlackjackRoundMenu.PLAYER_ROW]));
        assertEquals(M + "badge_6", model(c[BlackjackRoundMenu.PLAYER_BADGE]));
    }

    @Test
    void cardsOnTheTableWhenOpeningShowAtOnce() {
        BlackjackRoundMenu.View view = new BlackjackRoundMenu.View();
        BlackjackRoundMenu.Cell[] c = view.cells(turn(1000, true, true, 300), false);
        // dealt at tick 0 of a long-running hand, and also when dealt just now: the first ticks are instant
        assertEquals(M + "card_8", model(c[BlackjackRoundMenu.PLAYER_ROW]));
        assertEquals(M + "card_1_9", model(c[BlackjackRoundMenu.PLAYER_ROW + 1]));
        assertEquals(M + "card_9", model(c[BlackjackRoundMenu.DEALER_ROW]));
        assertEquals(M + "card_back_9", model(c[BlackjackRoundMenu.DEALER_ROW + 1]));
        // dealer total counts the up card only (10), player 9 + 2
        assertEquals(M + "badge_10", model(c[BlackjackRoundMenu.DEALER_BADGE]));
        assertEquals(M + "badge_11", model(c[BlackjackRoundMenu.PLAYER_BADGE]));
    }

    @Test
    void theHoleCardTurnsEightTicksAfterTheReveal() {
        BlackjackRoundMenu.View view = new BlackjackRoundMenu.View();
        for (int t = 0; t < 5; t++) {
            view.cells(turn(1000 + t, false, false, 300), true);
        }
        BlackjackSnapshot revealed = snap(BlackjackSnapshot.PHASE_DEALER, List.of(8, 1), List.of(0L, 0L),
                List.of(9, 20), List.of(0L, 0L), 0, 0, flags(false, false, false, false, false), ONE, 1010);
        for (long t = 1010; t < 1018; t++) {
            BlackjackRoundMenu.Cell[] c = view.cells(new BlackjackSnapshot(revealed.phase(), revealed.player(),
                    revealed.dealer(), revealed.playerDealtAt(), revealed.dealerDealtAt(), 0, 0, false, false, false,
                    false, false, ONE, t), true);
            assertEquals(M + "card_back_9", model(c[BlackjackRoundMenu.DEALER_ROW + 1]), "tick " + t);
            assertEquals(M + "badge_10", model(c[BlackjackRoundMenu.DEALER_BADGE]));
        }
        BlackjackRoundMenu.Cell[] c = view.cells(new BlackjackSnapshot(revealed.phase(), revealed.player(),
                revealed.dealer(), revealed.playerDealtAt(), revealed.dealerDealtAt(), 0, 0, false, false, false,
                false, false, ONE, 1018), true);
        assertEquals(M + "card_20_9", model(c[BlackjackRoundMenu.DEALER_ROW + 1]));
        assertEquals(M + "badge_18", model(c[BlackjackRoundMenu.DEALER_BADGE]));
        assertEquals(M + "status_4", model(c[BlackjackRoundMenu.STATUS]));
    }

    @Test
    void cardPlacesFollowTheModsSteps() {
        assertEquals(List.of(new BlackjackRoundMenu.Place(0, 0, false), new BlackjackRoundMenu.Place(1, 1, true)),
                BlackjackRoundMenu.places(2));
        int[] five = BlackjackRoundMenu.places(5).stream().mapToInt(p -> 8 + 18 * p.column() + (p.shifted() ? 9 : 0))
                .toArray();
        assertArrayEquals(new int[] {8, 35, 62, 89, 116}, five); // the mod's step 27 for five cards
        assertEquals(6, BlackjackRoundMenu.places(6).size());
        assertTrue(BlackjackRoundMenu.places(9).stream().noneMatch(BlackjackRoundMenu.Place::shifted));
        List<BlackjackRoundMenu.Place> twelve = BlackjackRoundMenu.places(12);
        assertEquals(9, twelve.size());
        assertEquals(new BlackjackRoundMenu.Place(11, 8, false), twelve.get(8));
        for (int n = 0; n <= 12; n++) {
            List<BlackjackRoundMenu.Place> places = BlackjackRoundMenu.places(n);
            for (int i = 1; i < places.size(); i++) {
                assertTrue(places.get(i).column() > places.get(i - 1).column(), "later cards lie on top (n " + n + ")");
            }
        }
    }

    @Test
    void buttonsFollowPhaseFlagsAffordabilityAndThePressLock() {
        BlackjackRoundMenu.View view = new BlackjackRoundMenu.View();
        BlackjackRoundMenu.Cell[] c = view.cells(turn(1000, true, true, 600), true);
        for (int b = 0; b < 3; b++) {
            for (int slot : BlackjackRoundMenu.BUTTONS[b]) {
                assertTrue(c[slot].enabled(), "button " + b);
                assertEquals(BlackjackRoundMenu.Action.values()[b], c[slot].action());
            }
            assertEquals(M + BlackjackRoundMenu.Action.values()[b].model + "_on",
                    model(c[BlackjackRoundMenu.BUTTON_ART[b]]));
        }
        // cannot afford: DOPPELN greyed, the others stay
        c = view.cells(turn(1001, true, false, 600), true);
        assertTrue(c[45].enabled());
        assertFalse(c[51].enabled());
        assertEquals(M + "double_off", model(c[52]));
        // a press locks all three for the next five ticks; the sixth tick frees them
        view.pressLock = BlackjackRoundMenu.PRESS_LOCK;
        for (int t = 1; t <= 5; t++) {
            c = view.cells(turn(1001 + t, true, true, 600), true);
            assertFalse(c[45].enabled() || c[48].enabled() || c[51].enabled(), "tick " + t);
        }
        c = view.cells(turn(1007, true, true, 600), true);
        assertTrue(c[45].enabled() && c[48].enabled() && c[51].enabled());
        // outside the player's turn nothing is clickable
        c = view.cells(snap(BlackjackSnapshot.PHASE_DRAWING, List.of(8, 1, 3), List.of(0L, 0L, 0L), List.of(9, -1),
                List.of(0L, 0L), 0, 0, flags(false, false, false, false, false), ONE, 1008), true);
        for (int slot = 45; slot < 54; slot++) {
            assertFalse(c[slot].enabled());
        }
        assertEquals(M + "status_3", model(c[BlackjackRoundMenu.STATUS]));
    }

    @Test
    void theTimerBarIsTheModsQuarterSecondBar() {
        BlackjackRoundMenu.View view = new BlackjackRoundMenu.View();
        BlackjackRoundMenu.Cell bar = view.cells(turn(1, true, true, 600), true)[BlackjackRoundMenu.BAR];
        assertEquals(M + "bar", bar.model());
        assertEquals(List.of(136f), bar.floats());
        assertEquals(List.of(BlackjackRoundMenu.BAR_COLOUR), bar.colors());
        bar = view.cells(turn(2, true, true, 300), true)[BlackjackRoundMenu.BAR];
        assertEquals(List.of(68f), bar.floats()); // (300 + 3) / 4 = 75 -> 136 * 75 / 150
        bar = view.cells(turn(3, true, true, 152), true)[BlackjackRoundMenu.BAR];
        assertEquals(List.of(BlackjackRoundMenu.BAR_LOW_COLOUR), bar.colors()); // 38 quarter seconds left
        assertEquals(List.of(BlackjackRoundMenu.BAR_COLOUR),
                view.cells(turn(4, true, true, 153), true)[BlackjackRoundMenu.BAR].colors());
        assertNull(view.cells(turn(5, true, true, 0), true)[BlackjackRoundMenu.BAR]);
        assertEquals(M + "status_2", model(view.cells(turn(6, true, true, 10), true)[BlackjackRoundMenu.STATUS]));
    }

    @Test
    void stakesShowCountsAndDoubledBaseCounts() {
        List<ItemAmount> two = List.of(new ItemAmount("minecraft:diamond", 128), new ItemAmount("minecraft:coal", 6));
        BlackjackRoundMenu.Cell[] c = new BlackjackRoundMenu.View().cells(snap(BlackjackSnapshot.PHASE_DEALER,
                List.of(8, 1, 3), List.of(0L, 0L, 0L), List.of(9, 20), List.of(0L, 0L), 0, 0,
                flags(false, false, false, true, false), two, 50), false);
        assertEquals("minecraft:diamond", c[27].item());
        assertEquals(64, c[27].amount());
        assertEquals(3, c[28].amount());
        assertEquals(M + "doubled", model(c[BlackjackRoundMenu.DOUBLED]));
        c = new BlackjackRoundMenu.View().cells(snap(BlackjackSnapshot.PHASE_TURN, List.of(8, 1), List.of(0L, 0L),
                List.of(9, -1), List.of(0L, 0L), 100, 0, flags(true, true, true, false, true), two, 50), false);
        assertEquals(99, c[27].amount()); // never above what a slot can show
        assertNull(c[BlackjackRoundMenu.DOUBLED]);
    }

    @Test
    void resultLabelsAreTheModsNetAmounts() {
        // one stake of 5 on a blackjack: 5 * 5 / 2 - 5 = +7, bold to the right (wide)
        BlackjackRoundMenu.Cell[] c = new BlackjackRoundMenu.View().cells(snap(BlackjackSnapshot.PHASE_RESULT,
                List.of(0, 25), List.of(0L, 0L), List.of(22, 45), List.of(0L, 0L), 0, 3,
                flags(false, false, false, false, false), List.of(new ItemAmount("minecraft:emerald", 5)), 50), false);
        assertEquals(1, c[27].amount());
        assertEquals(M + "net_wide", c[28].model());
        assertEquals(List.of("+", "7"), c[28].strings());
        assertEquals(List.of(BlackjackRoundMenu.GREEN, (BlackjackRoundMenu.GREEN & 0xFCFCFC) >> 2), c[28].colors());
        assertEquals(M + "result_3", model(c[BlackjackRoundMenu.STATUS]));
        // two stakes, push: "0" in white at columns 1 and 4
        List<ItemAmount> two = List.of(new ItemAmount("minecraft:diamond", 3), new ItemAmount("minecraft:coal", 6));
        c = new BlackjackRoundMenu.View().cells(snap(BlackjackSnapshot.PHASE_RESULT, List.of(9, 22),
                List.of(0L, 0L), List.of(10, 23), List.of(0L, 0L), 0, 7, flags(false, false, false, false, false),
                two, 50), false);
        assertEquals("minecraft:coal", c[30].item());
        assertEquals(List.of("0"), c[31].strings());
        assertEquals(BlackjackRoundMenu.WHITE, c[31].colors().get(0));
        // three stakes (or doubled): compact, right-aligned, characters from the right
        List<ItemAmount> three = List.of(new ItemAmount("minecraft:diamond", 3), new ItemAmount("minecraft:coal", 64),
                new ItemAmount("minecraft:iron_ingot", 1));
        c = new BlackjackRoundMenu.View().cells(snap(BlackjackSnapshot.PHASE_RESULT, List.of(9, 5, 7),
                List.of(0L, 0L, 0L), List.of(1, 2), List.of(0L, 0L), 0, 5, flags(false, false, false, false, false),
                three, 50), false);
        assertEquals("minecraft:diamond", c[27].item());
        assertEquals(M + "net_compact", c[28].model());
        assertEquals(List.of("3", "-"), c[28].strings());
        assertEquals("minecraft:coal", c[29].item());
        assertEquals(List.of("4", "6", "-"), c[30].strings());
        assertEquals(List.of("1", "-"), c[32].strings());
        assertEquals(BlackjackRoundMenu.RED, c[32].colors().get(0));
        // doubled win of 2 x 10: +20, compact although there is one stake
        c = new BlackjackRoundMenu.View().cells(snap(BlackjackSnapshot.PHASE_RESULT, List.of(5, 4, 9),
                List.of(0L, 0L, 0L), List.of(9, 20), List.of(0L, 0L), 0, 1, flags(false, false, false, true, false),
                List.of(new ItemAmount("minecraft:diamond", 20)), 50), false);
        assertEquals(List.of("0", "2", "+"), c[28].strings());
        assertEquals(M + "doubled", model(c[BlackjackRoundMenu.DOUBLED]));
    }

    @Test
    void anUnchangedStateGivesEqualCells() {
        BlackjackRoundMenu.View view = new BlackjackRoundMenu.View();
        for (int t = 0; t < 6; t++) {
            view.cells(turn(1000 + t, true, true, 400), true);
        }
        BlackjackRoundMenu.Cell[] a = view.cells(turn(1006, true, true, 400), true);
        BlackjackRoundMenu.Cell[] b = view.cells(turn(1007, true, true, 400), true);
        assertArrayEquals(a, b);
        BlackjackRoundMenu.Cell[] c = view.cells(turn(1008, true, true, 396), true);
        int changed = 0;
        for (int i = 0; i < a.length; i++) {
            if (!java.util.Objects.equals(a[i], c[i])) {
                changed++;
                assertEquals(BlackjackRoundMenu.BAR, i);
            }
        }
        assertEquals(1, changed);
    }

    @Test
    void aNewHandStartsWithNoCards() {
        BlackjackRoundMenu.View view = new BlackjackRoundMenu.View();
        view.cells(turn(1000, true, true, 400), false);
        BlackjackRoundMenu.Cell[] c = view.cells(snap(BlackjackSnapshot.PHASE_DEALING, List.of(), List.of(),
                List.of(), List.of(), 0, 0, flags(false, false, false, false, false), ONE, 1001), true);
        for (int slot = 0; slot < 27; slot++) {
            assertNull(c[slot]);
        }
        assertEquals(M + "status_1", model(c[BlackjackRoundMenu.STATUS]));
    }
}
