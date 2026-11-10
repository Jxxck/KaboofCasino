package de.tigges.kaboofcasino.game.poker;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.game.blackjack.BlackjackShoe;
import de.tigges.kaboofcasino.game.blackjack.ShoeRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/**
 * Texas Hold'em between players: blinds and order, the betting rules, side pots, splits, the uncalled bet, timeouts,
 * a player leaving, and that no chip is ever made or lost over thousands of random hands.
 */
class HoldemHandTest {

    static final int TURN = 600;

    /** A card id from "As", "Td", "9h", "2c" (rank then suit c d h s). */
    static int c(String s) {
        int rank = "A23456789TJQK".indexOf(s.charAt(0));
        int suit = "cdhs".indexOf(s.charAt(1));
        return suit * 13 + rank;
    }

    static BlackjackShoe rigged(String... cards) {
        int[] ids = Arrays.stream(cards).mapToInt(HoldemHandTest::c).toArray();
        return BlackjackShoe.riggedDecks(new ShoeRandom(1), 1, ids);
    }

    static final class Log implements HoldemHand.Listener {
        final List<String> events = new ArrayList<>();
        final List<Integer> turns = new ArrayList<>();
        final List<Integer> timedOut = new ArrayList<>();
        List<HoldemHand.Award> awards;
        int[] stacks;
        boolean showdown;
        int revealed;

        @Override
        public void shuffled(int at) {
        }

        @Override
        public void blind(int seat, int amount, boolean big, int at) {
            events.add((big ? "BB" : "SB") + seat + "=" + amount);
        }

        @Override
        public void cardDealt(int seat, int index, int card, int at) {
        }

        @Override
        public void streetChanged(HoldemHand.Street street, int at) {
        }

        @Override
        public void turnOpened(int seat, int deadline, int at) {
            turns.add(seat);
            assertEquals(at + TURN, deadline);
        }

        @Override
        public void turnTimedOut(int seat, int at) {
            timedOut.add(seat);
        }

        @Override
        public void acted(int seat, HoldemHand.Action action, int amount, int at) {
            events.add(seat + ":" + action + (amount > 0 ? "=" + amount : ""));
        }

        @Override
        public void revealed(int seat, List<Integer> cards, int at) {
            revealed++;
        }

        @Override
        public void decided(List<HoldemHand.Award> awards, int[] stacks, boolean showdown, int at) {
            this.awards = awards;
            this.stacks = stacks;
            this.showdown = showdown;
        }

        @Override
        public void cleared(int at) {
        }
    }

    /** Ticks until somebody may act (or the hand is decided / over). */
    static int untilTurn(HoldemHand h) {
        for (int i = 0; i < 5_000 && h.current() < 0 && h.street() != HoldemHand.Street.RESULT && !h.isOver(); i++) {
            h.tick();
        }
        return h.current();
    }

    static void runOut(HoldemHand h) {
        for (int i = 0; i < 5_000 && !h.isOver(); i++) {
            h.tick();
        }
    }

    @Test
    void headsUpTheButtonPostsTheSmallBlindAndActsFirstBeforeTheFlopLastAfterIt() {
        Log log = new Log();
        HoldemHand h = new HoldemHand(rigged(), log, new int[] {1000, 1000}, 0, 5, 10, TURN);
        assertEquals(List.of("SB0=5", "BB1=10"), log.events);
        assertEquals(0, untilTurn(h), "the button acts first pre-flop");
        assertEquals(5, h.toCall(0));
        assertTrue(h.checkOrCall(0));
        assertEquals(1, h.current(), "the big blind has the option");
        assertTrue(h.canCheck(1));
        assertTrue(h.checkOrCall(1));
        assertEquals(1, untilTurn(h), "after the flop the big blind acts first");
        assertEquals(HoldemHand.Street.FLOP, h.street());
        assertEquals(3, h.board().size());
        assertEquals(20, h.pot());
    }

    @Test
    void threeHandedTheBlindsFollowTheButtonAndTheButtonOpens() {
        Log log = new Log();
        HoldemHand h = new HoldemHand(rigged(), log, new int[] {500, 500, 500}, 2, 5, 10, TURN);
        assertEquals(List.of("SB0=5", "BB1=10"), log.events);
        assertEquals(2, untilTurn(h), "the seat after the big blind opens");
        assertTrue(h.checkOrCall(2));
        assertTrue(h.checkOrCall(0));
        assertTrue(h.checkOrCall(1), "the big blind checks his option");
        assertEquals(0, untilTurn(h), "after the flop the seat after the button acts first");
    }

    @Test
    void raisesNeedAtLeastTheLastFullRaise() {
        HoldemHand h = new HoldemHand(rigged(), new Log(), new int[] {1000, 1000}, 0, 5, 10, TURN);
        untilTurn(h);
        assertEquals(20, h.minRaiseTo(0));
        assertFalse(h.raiseTo(0, 15), "below a full raise");
        assertTrue(h.raiseTo(0, 30));
        assertEquals(50, h.minRaiseTo(1), "30 + the raise of 20");
        assertFalse(h.raiseTo(1, 45));
        assertTrue(h.raiseTo(1, 50));
        assertEquals(1000, h.maxRaiseTo(0));
        assertTrue(h.checkOrCall(0));
        untilTurn(h);
        assertEquals(10, h.minRaiseTo(1), "a new street: at least the big blind");
        assertFalse(h.raiseTo(1, 5));
        assertTrue(h.raiseTo(1, 10));
    }

    @Test
    void theBestHandWinsAndAnUncalledBetComesBack() {
        // seat 1 gets As Ad, seat 0 Kc Kd; board 2c 7h 9s Jd 3c
        Log log = new Log();
        HoldemHand h = new HoldemHand(rigged("As", "Kc", "Ad", "Kd", "2c", "7h", "9s", "Jd", "3c"), log,
                new int[] {1000, 300}, 0, 5, 10, TURN);
        untilTurn(h);
        assertTrue(h.goAllIn(0), "1000 all-in");
        assertFalse(h.canRaise(1), "nobody left to raise against");
        assertTrue(h.checkOrCall(1), "the call for 300 is all-in: 700 of the bet is uncalled");
        runOut(h);
        assertTrue(log.showdown);
        assertEquals(2, log.revealed);
        assertArrayEquals(new int[] {700, 600}, log.stacks);
        assertEquals(2, log.awards.size(), "the main pot and the uncalled 700");
        assertEquals(600, log.awards.get(0).amount());
        assertEquals(List.of(1), log.awards.get(0).winners());
        assertEquals(700, log.awards.get(1).amount());
        assertEquals(List.of(0), log.awards.get(1).eligible());
    }

    @Test
    void sidePotsGoToTheBestHandAmongThoseInThem() {
        // button 0; deal order 1, 2, 0, 1, 2, 0: seat 1 Qc Qd (second), seat 2 Ac Ad (best, short), seat 0 Kc Kd
        Log log = new Log();
        HoldemHand h = new HoldemHand(rigged("Qc", "Ac", "Kc", "Qd", "Ad", "Kd", "2h", "7s", "9h", "4s", "3d"), log,
                new int[] {500, 500, 100}, 0, 5, 10, TURN);
        assertEquals(0, untilTurn(h));
        assertTrue(h.raiseTo(0, 500), "seat 0 all-in 500");
        assertTrue(h.checkOrCall(1), "seat 1 calls 500 (all-in)");
        assertTrue(h.checkOrCall(2), "seat 2 calls all-in for 100");
        runOut(h);
        // main pot 300 to the aces, the side pot 800 between the kings and the queens: the kings
        assertEquals(List.of(300, 800), log.awards.stream().map(HoldemHand.Award::amount).toList());
        assertEquals(List.of(2), log.awards.get(0).winners());
        assertEquals(List.of(0), log.awards.get(1).winners());
        assertArrayEquals(new int[] {800, 0, 300}, log.stacks);
    }

    @Test
    void aTieSplitsThePotAndTheOddChipGoesToTheSeatAfterTheButton() {
        // both play the board's straight 5-9; seat 1 (after the button) gets the odd chip
        Log log = new Log();
        HoldemHand h = new HoldemHand(rigged("2c", "2d", "3c", "3d", "5h", "6s", "7h", "8s", "9d"), log,
                new int[] {101, 100}, 0, 5, 10, TURN);
        untilTurn(h);
        assertTrue(h.goAllIn(0), "101 all-in");
        assertTrue(h.checkOrCall(1), "100 all-in call");
        runOut(h);
        // the uncalled 1 back to seat 0, the 200 split
        assertEquals(List.of(200, 1), log.awards.stream().map(HoldemHand.Award::amount).toList());
        assertEquals(List.of(1, 0), log.awards.get(0).winners());
        assertArrayEquals(new int[] {101, 100}, log.stacks);
    }

    @Test
    void anOddPotSplitGivesTheChipFromTheSeatAfterTheButton() {
        List<HoldemHand.Pot> pots = HoldemHand.pots(new int[] {35, 35, 35}, new boolean[] {false, true, false});
        assertEquals(List.of(new HoldemHand.Pot(105, List.of(0, 2))), pots);
    }

    @Test
    void everybodyFoldsToABetAndTheBettorTakesItWithoutShowingDown() {
        Log log = new Log();
        HoldemHand h = new HoldemHand(rigged(), log, new int[] {1000, 1000, 1000}, 0, 5, 10, TURN);
        untilTurn(h);
        assertTrue(h.raiseTo(0, 40));
        assertTrue(h.fold(1));
        assertTrue(h.fold(2));
        assertFalse(log.showdown);
        assertEquals(0, log.revealed);
        assertArrayEquals(new int[] {1015, 995, 990}, log.stacks);
        runOut(h);
        assertTrue(h.isOver());
    }

    @Test
    void anIncompleteAllInRaiseDoesNotReopenTheBettingForWhoAlreadyActed() {
        // seat 0 raises to 100, seat 1 calls, seat 2 goes all-in for 150 (50 more: not a full raise of 90)
        Log log = new Log();
        HoldemHand h = new HoldemHand(rigged(), log, new int[] {1000, 1000, 150}, 2, 5, 10, TURN);
        assertEquals(2, untilTurn(h));
        assertTrue(h.checkOrCall(2), "the button calls 10");
        assertTrue(h.raiseTo(0, 100));
        assertTrue(h.checkOrCall(1));
        assertTrue(h.goAllIn(2), "all-in to 150");
        assertEquals(0, h.current());
        assertTrue(h.canCall(0));
        assertFalse(h.canRaise(0), "seat 0 acted on the last full raise: call or fold only");
        assertTrue(h.checkOrCall(0));
        assertFalse(h.canRaise(1));
        assertTrue(h.checkOrCall(1));
        assertEquals(HoldemHand.Street.DEALING, h.street(), "the flop comes");
    }

    @Test
    void aTimeoutChecksWhenItCanAndFoldsWhenItCannot() {
        Log log = new Log();
        HoldemHand h = new HoldemHand(rigged(), log, new int[] {1000, 1000}, 0, 5, 10, TURN);
        untilTurn(h);
        for (int i = 0; i < TURN; i++) {
            h.tick();
        }
        assertEquals(List.of(0), log.timedOut);
        assertTrue(log.events.contains("0:FOLD"), "the small blind cannot check: folds");
        assertArrayEquals(new int[] {995, 1005}, log.stacks);

        Log log2 = new Log();
        HoldemHand h2 = new HoldemHand(rigged(), log2, new int[] {1000, 1000}, 0, 5, 10, TURN);
        untilTurn(h2);
        assertTrue(h2.checkOrCall(0));
        for (int i = 0; i < TURN; i++) {
            h2.tick();
        }
        assertTrue(log2.events.contains("1:CHECK"), "the big blind's option times out to a check");
        assertEquals(HoldemHand.Street.DEALING, h2.street());
    }

    @Test
    void aPlayerWhoLeavesFoldsAtOnceAndTheLastOneWins() {
        Log log = new Log();
        HoldemHand h = new HoldemHand(rigged(), log, new int[] {1000, 1000, 1000}, 0, 5, 10, TURN);
        h.tick();
        h.leave(2);
        untilTurn(h);
        assertEquals(0, h.current(), "the button opens, seat 2 is out");
        assertTrue(h.raiseTo(0, 30));
        h.leave(1);
        assertArrayEquals(new int[] {1015, 995, 990}, log.stacks, "the big blind left: the button takes the blinds");
    }

    @Test
    void shortBlindsGoAllInAndTheBoardRunsOut() {
        Log log = new Log();
        HoldemHand h = new HoldemHand(rigged(), log, new int[] {3, 1000}, 0, 5, 10, TURN);
        assertEquals(List.of("SB0=3", "BB1=10"), log.events);
        runOut(h);
        assertTrue(log.showdown, "nobody can bet against an all-in small blind who matched: run-out");
        assertEquals(1003, Arrays.stream(log.stacks).sum());
        assertEquals(5, h.board().size());
    }

    /** Thousands of random hands with random legal actions: the chips at the table never change. */
    @Test
    void noChipIsEverMadeOrLost() {
        SplittableRandom r = new SplittableRandom(7);
        int showdowns = 0;
        int sidePots = 0;
        for (int hand = 0; hand < 4_000; hand++) {
            int n = 2 + r.nextInt(2);
            int[] stacks = new int[n];
            for (int s = 0; s < n; s++) {
                stacks[s] = 1 + r.nextInt(r.nextBoolean() ? 60 : 2_000);
            }
            int total = Arrays.stream(stacks).sum();
            Log log = new Log();
            HoldemHand h = new HoldemHand(new BlackjackShoe(new ShoeRandom(hand), 1), log, stacks, r.nextInt(n), 5,
                    10, TURN);
            for (int guard = 0; guard < 10_000 && !h.isOver(); guard++) {
                int seat = h.current();
                if (seat >= 0 && r.nextInt(4) == 0) {
                    int what = r.nextInt(10);
                    if (what == 0 && r.nextInt(8) == 0) {
                        h.leave(seat);
                    } else if (what < 2) {
                        h.fold(seat);
                    } else if (what < 6 || !h.canRaise(seat)) {
                        h.checkOrCall(seat);
                    } else if (what < 8) {
                        int lo = h.minRaiseTo(seat);
                        int hi = h.maxRaiseTo(seat);
                        assertTrue(h.raiseTo(seat, lo + (hi > lo ? r.nextInt(hi - lo + 1) : 0)), "legal raise");
                    } else {
                        assertTrue(h.goAllIn(seat));
                    }
                }
                h.tick();
                int inPlay = h.pot();
                for (int s = 0; s < n; s++) {
                    inPlay += h.street() == HoldemHand.Street.RESULT || h.street() == HoldemHand.Street.CLEARING
                            || h.isOver() ? 0 : h.shownStack(s);
                }
                if (log.stacks == null) {
                    assertEquals(total, inPlay, "stacks + pot during the hand " + hand);
                }
            }
            assertTrue(h.isOver(), "hand " + hand + " ended");
            assertEquals(total, Arrays.stream(log.stacks).sum(), "hand " + hand);
            int paid = log.awards.stream().mapToInt(HoldemHand.Award::amount).sum();
            assertEquals(h.pot(), paid, "every committed chip was in a pot, hand " + hand);
            showdowns += log.showdown ? 1 : 0;
            sidePots += log.awards.size() > 1 ? 1 : 0;
        }
        assertTrue(showdowns > 300, "showdowns: " + showdowns);
        assertTrue(sidePots > 100, "hands with side pots: " + sidePots);
    }
}
