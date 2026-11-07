package de.tigges.kaboofcasino.game.poker;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.wager.ItemAmount;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.Test;

/** The round view's client logic ({@code PokerScreen}) on snapshots: no server, no items. */
class PokerRoundMenuTest {

    /** {@code PokerScreen.keyPressed}: 1 = MITGEHEN, 2 and F = AUSSTEIGEN; nothing else. */
    @Test
    void keyboardShortcuts() {
        assertEquals(PokerRoundMenu.Action.CALL, PokerRoundMenu.keyAction(ClickType.NUMBER_KEY, 0));
        assertEquals(PokerRoundMenu.Action.FOLD, PokerRoundMenu.keyAction(ClickType.NUMBER_KEY, 1));
        assertEquals(PokerRoundMenu.Action.FOLD, PokerRoundMenu.keyAction(ClickType.SWAP_OFFHAND, -1));
        for (int key = 2; key < 9; key++) {
            assertNull(PokerRoundMenu.keyAction(ClickType.NUMBER_KEY, key));
        }
        assertNull(PokerRoundMenu.keyAction(ClickType.DROP, 0));
        assertNull(PokerRoundMenu.keyAction(ClickType.LEFT, 0));
    }

    private static final String M = PokerRoundMenu.MODEL;
    private static final int H = PokerRoundMenu.HIDDEN;
    private static final List<ItemAmount> ONE = List.of(new ItemAmount("minecraft:diamond", 10));

    /** Ace = rank 0 .. king = 12; suits 0..3. */
    private static int card(int rank, int suit) {
        return suit * 13 + rank;
    }

    private static List<Long> at(int n, long tick) {
        List<Long> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(tick);
        }
        return out;
    }

    /** A snapshot with every card dealt at tick 0 and nothing revealed. */
    private static PokerSnapshot snap(int phase, List<Integer> player, List<Integer> dealer, List<Integer> board,
                                      int turnLeft, int result, boolean canCall, boolean afford, boolean called,
                                      List<ItemAmount> stakes, List<PokerSnapshot.Net> net, long now) {
        return new PokerSnapshot(phase, player, dealer, board, at(player.size(), 0), at(dealer.size(), 0),
                at(board.size(), 0), at(dealer.size(), -1), turnLeft, result, canCall, phase == 2, called, afford, 0,
                stakes, net, now);
    }

    private static PokerSnapshot decision(long now, boolean canCall, boolean afford, int turnLeft) {
        return snap(PokerSnapshot.PHASE_DECISION, List.of(card(0, 0), card(12, 1)), List.of(H, H),
                List.of(card(11, 0), card(10, 2), card(1, 3)), turnLeft, 0, canCall, afford, false, ONE, List.of(),
                now);
    }

    private static PokerSnapshot result(int code, List<PokerSnapshot.Net> net) {
        return snap(PokerSnapshot.PHASE_RESULT, List.of(card(0, 0), card(12, 1)), List.of(card(8, 3), card(8, 2)),
                List.of(card(11, 0), card(10, 2), card(1, 3), card(9, 1), card(6, 2)), 0, code, false, false, true,
                ONE, net, 5000);
    }

    private static String model(PokerRoundMenu.Cell c) {
        return c == null ? null : c.model();
    }

    /** A hand name cell as "hand_player_4 yellow": item, category and text colour (its shadow checked too). */
    private static String hand(PokerRoundMenu.Cell c) {
        if (c == null) {
            return null;
        }
        int rgb = c.colors().get(0);
        assertEquals(List.of(rgb, (rgb & 0xFCFCFC) >> 2), c.colors());
        String colour = rgb == PokerRoundMenu.YELLOW ? "yellow" : rgb == PokerRoundMenu.WHITE ? "white"
                : rgb == PokerRoundMenu.RED ? "red" : Integer.toHexString(rgb);
        return c.model().substring(M.length()) + "_" + c.strings().get(0) + " " + colour;
    }

    private static String face(PokerRoundMenu.Cell c) {
        return c == null ? null : c.strings().get(0);
    }

    @Test
    void cardsAppearTwentyTwoTicksAfterTheDeal() {
        PokerRoundMenu.View view = new PokerRoundMenu.View();
        List<Integer> player = new ArrayList<>();
        List<Long> dealt = new ArrayList<>();
        for (long t = 100; t < 105; t++) {
            view.cells(new PokerSnapshot(1, player, List.of(), List.of(), dealt, List.of(), List.of(), List.of(), 0,
                    0, false, false, false, false, 0, ONE, List.of(), t), true);
        }
        player.add(card(4, 1));
        dealt.add(110L);
        for (long t = 110; t < 132; t++) {
            PokerRoundMenu.Cell[] c = view.cells(new PokerSnapshot(1, player, List.of(), List.of(), dealt, List.of(),
                    List.of(), List.of(), 0, 0, false, false, false, false, 0, ONE, List.of(), t), true);
            assertNull(c[PokerRoundMenu.PLAYER[0]], "tick " + t);
            assertNull(c[PokerRoundMenu.PLAYER_NAME]);
        }
        PokerRoundMenu.Cell[] c = view.cells(new PokerSnapshot(1, player, List.of(), List.of(), dealt, List.of(),
                List.of(), List.of(), 0, 0, false, false, false, false, 0, ONE, List.of(), 132), true);
        assertEquals(M + "card_player_0", model(c[PokerRoundMenu.PLAYER[0]]));
        assertEquals(Integer.toString(card(4, 1)), face(c[PokerRoundMenu.PLAYER[0]]));
        assertEquals("hand_player_0 yellow", hand(c[PokerRoundMenu.PLAYER_NAME]));
        assertEquals(M + "status_1", model(c[PokerRoundMenu.STATUS]));
    }

    @Test
    void cardsOnTheTableWhenOpeningShowAtOnceInTheirPlaces() {
        PokerRoundMenu.View view = new PokerRoundMenu.View();
        PokerRoundMenu.Cell[] c = view.cells(decision(1000, true, true, 400), false);
        assertEquals(M + "card_player_0", model(c[16]));
        assertEquals(M + "card_player_1", model(c[17]));
        assertEquals(M + "card_dealer_0", model(c[9]));
        assertEquals(M + "card_dealer_1", model(c[10]));
        assertEquals("back", face(c[9]));
        assertEquals("back", face(c[10]));
        assertEquals(List.of("card_board_a", "card_board_b", "card_board_a"), List.of(
                c[0].model().substring(M.length()), c[2].model().substring(M.length()),
                c[3].model().substring(M.length())));
        assertNull(c[5]);
        assertNull(c[6]);
        // A K + Q J 2: high card ace; the dealer's name only once both his cards are face up
        assertEquals("hand_player_0 yellow", hand(c[PokerRoundMenu.PLAYER_NAME]));
        assertNull(c[PokerRoundMenu.DEALER_NAME]);
        assertEquals(M + "status_2", model(c[PokerRoundMenu.STATUS]));
    }

    @Test
    void theDealersCardsTurnEightTicksAfterTheirReveal() {
        PokerRoundMenu.View view = new PokerRoundMenu.View();
        for (int t = 0; t < 5; t++) {
            view.cells(decision(1000 + t, true, true, 300), true);
        }
        List<Integer> board = List.of(card(11, 0), card(10, 2), card(1, 3), card(9, 1), card(6, 2));
        for (long t = 1100; t <= 1130; t++) {
            // index 1 turned at 1100, index 0 at 1122 (PokerRound: c + 49, c + 71)
            List<Integer> dealer = List.of(t >= 1122 ? card(3, 3) : H, card(2, 2));
            List<Long> turned = List.of(t >= 1122 ? 1122L : -1L, 1100L);
            PokerSnapshot s = new PokerSnapshot(PokerSnapshot.PHASE_SHOWDOWN, List.of(card(0, 0), card(12, 1)),
                    dealer, board, at(2, 0), at(2, 0), at(5, 0), turned, 0, 0, false, false, true, false, 0, ONE,
                    List.of(), t);
            PokerRoundMenu.Cell[] c = view.cells(s, true);
            assertEquals(t >= 1108 ? Integer.toString(card(2, 2)) : "back", face(c[10]), "tick " + t);
            assertEquals(t >= 1130 ? Integer.toString(card(3, 3)) : "back", face(c[9]), "tick " + t);
            // 4 + 3 with Q J T 7 2 (no pair): the dealer does not qualify, his name is red
            assertEquals(t >= 1130 ? "hand_dealer_0 red" : null, hand(c[PokerRoundMenu.DEALER_NAME]));
            assertEquals(M + "status_4", model(c[PokerRoundMenu.STATUS]));
            assertEquals(M + "tripled", model(c[PokerRoundMenu.TRIPLED]));
        }
    }

    @Test
    void handNamesUseTheBoardAndTheQualifier() {
        PokerRoundMenu.View view = new PokerRoundMenu.View();
        // player A K + board Q J T 9 7: straight; dealer 9 9: pair of nines qualifies (white)
        PokerRoundMenu.Cell[] c = view.cells(snap(PokerSnapshot.PHASE_SHOWDOWN, List.of(card(0, 0), card(12, 1)),
                List.of(card(8, 3), card(8, 2)), List.of(card(11, 0), card(10, 2), card(9, 3), card(8, 1),
                        card(6, 2)), 0, 0, false, false, true, ONE, List.of(), 1000), false);
        assertEquals("hand_player_4 yellow", hand(c[PokerRoundMenu.PLAYER_NAME]));
        assertEquals("hand_dealer_3 white", hand(c[PokerRoundMenu.DEALER_NAME])); // 9 9 + 9 on the board: trips
        c = new PokerRoundMenu.View().cells(snap(PokerSnapshot.PHASE_SHOWDOWN, List.of(card(0, 0), card(12, 1)),
                List.of(card(2, 3), card(2, 2)), List.of(card(11, 0), card(10, 2), card(9, 3), card(7, 1),
                        card(6, 2)), 0, 0, false, false, true, ONE, List.of(), 1000), false);
        assertEquals("hand_dealer_1 red", hand(c[PokerRoundMenu.DEALER_NAME])); // pair of threes
        c = new PokerRoundMenu.View().cells(snap(PokerSnapshot.PHASE_SHOWDOWN, List.of(card(0, 0), card(12, 1)),
                List.of(card(3, 3), card(3, 2)), List.of(card(11, 0), card(10, 2), card(9, 3), card(7, 1),
                        card(6, 2)), 0, 0, false, false, true, ONE, List.of(), 1000), false);
        assertEquals("hand_dealer_1 white", hand(c[PokerRoundMenu.DEALER_NAME])); // pair of fours qualifies
    }

    @Test
    void buttonsFollowTheFlagsAffordabilityAndThePressLock() {
        PokerRoundMenu.View view = new PokerRoundMenu.View();
        PokerRoundMenu.Cell[] c = view.cells(decision(1000, true, true, 600), true);
        for (int b = 0; b < 2; b++) {
            for (int slot : PokerRoundMenu.BUTTONS[b]) {
                assertTrue(c[slot].enabled(), "button " + b);
                assertEquals(PokerRoundMenu.Action.values()[b], c[slot].action());
            }
        }
        assertEquals(M + "call_on", model(c[46]));
        assertEquals(M + "fold_on", model(c[51]));
        assertNull(c[49], "the felt between the buttons");
        // not enough items: MITGEHEN greyed, AUSSTEIGEN stays
        c = view.cells(decision(1001, true, false, 600), true);
        assertFalse(c[45].enabled());
        assertEquals(M + "call_off", model(c[46]));
        assertTrue(c[50].enabled());
        c = view.cells(decision(1002, false, true, 600), true);
        assertFalse(c[48].enabled());
        // a press locks both for the next five ticks; the sixth frees them
        view.pressLock = PokerRoundMenu.PRESS_LOCK;
        for (int t = 1; t <= 5; t++) {
            c = view.cells(decision(1002 + t, true, true, 600), true);
            assertFalse(c[45].enabled() || c[53].enabled(), "tick " + t);
        }
        c = view.cells(decision(1008, true, true, 600), true);
        assertTrue(c[45].enabled() && c[53].enabled());
        // outside the decision nothing is clickable
        c = view.cells(result(1, List.of()), true);
        for (int[] slots : PokerRoundMenu.BUTTONS) {
            for (int slot : slots) {
                assertFalse(c[slot].enabled());
            }
        }
        assertEquals(M + "call_off", model(c[46]));
        assertEquals(M + "fold_off", model(c[51]));
    }

    @Test
    void theTimerBarCountsQuarterSecondsAndTurnsRed() {
        PokerRoundMenu.View view = new PokerRoundMenu.View();
        PokerRoundMenu.Cell[] c = view.cells(decision(1000, true, true, 600), false);
        assertEquals(List.of(136f), c[PokerRoundMenu.BAR].floats());
        assertEquals(List.of(PokerRoundMenu.BAR_COLOUR), c[PokerRoundMenu.BAR].colors());
        assertEquals(PokerRoundMenu.BAR_MODEL, c[PokerRoundMenu.BAR].model());
        // 153 ticks: (153 + 3) / 4 = 39 steps, still orange; 152 ticks: 38 steps, red
        c = view.cells(decision(1001, true, true, 153), false);
        assertEquals(List.of((float) (136 * 39 / 150)), c[PokerRoundMenu.BAR].floats());
        assertEquals(List.of(PokerRoundMenu.BAR_COLOUR), c[PokerRoundMenu.BAR].colors());
        c = view.cells(decision(1002, true, true, 152), false);
        assertEquals(List.of(PokerRoundMenu.BAR_LOW_COLOUR), c[PokerRoundMenu.BAR].colors());
        c = view.cells(decision(1003, true, true, 1), false);
        assertNull(c[PokerRoundMenu.BAR], "one step of 136 / 150 px rounds to nothing");
        c = view.cells(decision(1004, true, true, 0), false);
        assertNull(c[PokerRoundMenu.BAR]);
        assertNull(view.cells(result(1, List.of()), false)[PokerRoundMenu.BAR]);
    }

    @Test
    void theStakeRowShowsTheAntesAndX3AfterTheCall() {
        List<ItemAmount> three = List.of(new ItemAmount("minecraft:diamond", 12),
                new ItemAmount("minecraft:gold_ingot", 64), new ItemAmount("minecraft:diamond", 150));
        PokerRoundMenu.Cell[] c = new PokerRoundMenu.View().cells(snap(PokerSnapshot.PHASE_DECISION,
                List.of(card(0, 0)), List.of(H), List.of(), 400, 0, true, true, false, three, List.of(), 1000), false);
        assertEquals(M + "label_stake", model(c[PokerRoundMenu.STAKE_LABEL]));
        assertEquals("minecraft:diamond", c[27].item());
        assertEquals(12, c[27].amount());
        assertEquals(64, c[28].amount());
        assertEquals(99, c[29].amount(), "a slot shows at most 99");
        assertNull(c[PokerRoundMenu.TRIPLED]);
        // after the call the wager is 3 x the ante (shownWager); the slots show the ante and "x3" like the mod
        List<ItemAmount> tripled = List.of(new ItemAmount("minecraft:diamond", 36),
                new ItemAmount("minecraft:gold_ingot", 192), new ItemAmount("minecraft:diamond", 450));
        c = new PokerRoundMenu.View().cells(snap(PokerSnapshot.PHASE_BOARD, List.of(card(0, 0)), List.of(H),
                List.of(), 0, 0, false, false, true, tripled, List.of(), 1000), false);
        assertEquals(M + "tripled", model(c[PokerRoundMenu.TRIPLED]));
        assertEquals(12, c[27].amount());
        assertEquals(64, c[28].amount());
        assertEquals(99, c[29].amount());
        assertEquals(M + "status_3", model(c[PokerRoundMenu.STATUS]));
    }

    @Test
    void netLinesFollowTheModsTwoLayouts() {
        // one or two kinds: BILANZ, labels right of the items (wide), left to right
        PokerRoundMenu.Cell[] c = new PokerRoundMenu.View().cells(result(1, List.of(
                new PokerSnapshot.Net("minecraft:diamond", 36), new PokerSnapshot.Net("minecraft:gold_ingot",
                        -192))), false);
        assertEquals(M + "label_net", model(c[PokerRoundMenu.STAKE_LABEL]));
        assertEquals(M + "result_1", model(c[PokerRoundMenu.STATUS]));
        assertNull(c[PokerRoundMenu.TRIPLED]);
        assertEquals("minecraft:diamond", c[27].item());
        assertEquals(1, c[27].amount());
        assertEquals("minecraft:gold_ingot", c[30].item());
        assertEquals(M + "net_wide", model(c[36]));
        assertEquals(List.of("+", "3", "6"), c[36].strings());
        assertEquals(List.of(PokerRoundMenu.GREEN, (PokerRoundMenu.GREEN & 0xFCFCFC) >> 2), c[36].colors());
        assertEquals(List.of("-", "1", "9", "2"), c[39].strings());
        assertEquals(PokerRoundMenu.RED, c[39].colors().get(0));
        // three kinds: BILANZ, compact labels counted from the right
        c = new PokerRoundMenu.View().cells(result(2, List.of(new PokerSnapshot.Net("minecraft:emerald", 3),
                new PokerSnapshot.Net("minecraft:coal", 0), new PokerSnapshot.Net("minecraft:iron_block", 64))),
                false);
        assertEquals(M + "label_net", model(c[PokerRoundMenu.STAKE_LABEL]));
        assertEquals("minecraft:coal", c[29].item());
        assertEquals(M + "net_compact", model(c[38]));
        assertEquals(List.of("0"), c[38].strings());
        assertEquals(PokerRoundMenu.WHITE, c[38].colors().get(0));
        assertEquals(List.of("4", "6", "+"), c[40].strings());
        // more than three: no BILANZ
        List<PokerSnapshot.Net> six = new ArrayList<>();
        for (String item : List.of("coal", "iron_ingot", "gold_ingot", "emerald", "diamond", "redstone")) {
            six.add(new PokerSnapshot.Net("minecraft:" + item, -1));
        }
        c = new PokerRoundMenu.View().cells(result(4, six), false);
        assertNull(c[PokerRoundMenu.STAKE_LABEL]);
        for (int i = 0; i < 6; i++) {
            assertEquals(six.get(i).item(), c[27 + i].item());
            assertEquals(M + "net_compact", model(c[36 + i]));
        }
        assertArrayEquals(new int[] {0, 2, 4, 6, 8}, PokerRoundMenu.netColumns(5));
        assertEquals("+19584", PokerRoundMenu.netLabel(19_584, 6)); // 192 items at a royal flush
        assertEquals("+99999", PokerRoundMenu.netLabel(1_234_567, 6));
        assertEquals("-99999", PokerRoundMenu.netLabel(-1_234_567, 6));
        assertEquals("+6528", PokerRoundMenu.netLabel(6_528, 5));
        assertEquals("-9999", PokerRoundMenu.netLabel(-20_000, 5));
        assertEquals("0", PokerRoundMenu.netLabel(0, 5));
    }

    @Test
    void statusLinesFollowThePhase() {
        assertEquals(M + "status_5", model(new PokerRoundMenu.View().cells(result(0, List.of()), false)[22]));
        assertEquals(M + "result_5", model(new PokerRoundMenu.View().cells(result(5, List.of(
                new PokerSnapshot.Net("minecraft:diamond", -10))), false)[22]));
        assertEquals(M + "status_1", model(new PokerRoundMenu.View().cells(PokerSnapshot.betting(0), false)[22]));
    }
}
