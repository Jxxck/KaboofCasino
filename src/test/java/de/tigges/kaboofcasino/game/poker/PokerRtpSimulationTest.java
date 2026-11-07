package de.tigges.kaboofcasino.game.poker;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.game.FairPayout;
import de.tigges.kaboofcasino.game.blackjack.BlackjackRound;
import de.tigges.kaboofcasino.game.blackjack.BlackjackShoe;
import de.tigges.kaboofcasino.game.blackjack.ShoeRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Return to player of the shipped poker (Casino Hold'em): {@link PokerRound} + {@link BlackjackShoe} with one deck
 * (verbatim ports of MineVegas Casino, bias 0) ticked one at a time, a player that applies the mod's own strategy
 * ({@link PokerBias#simpleCall}) at DECISION, payouts with {@link FairPayout#share} (thirds of the called stake, a
 * seeded roll per stake, the refund on a push), ante 1 item.
 * <p>
 * The reference numbers come from {@code tools/rtp/PokerRtp.java} (independent of the plugin code; method and results
 * in its header): a) the exact outcome probabilities of simpleCall (full enumeration of all deals), b) a Monte Carlo
 * of the mod's exact game path, and the "replay" counts, which the tool's own literal round produces for this test's
 * shoe seed. 10 million hands (3.45e9 ticks) take about 95 s.
 */
class PokerRtpSimulationTest {

    private static final int HANDS = 10_000_000;
    private static final long SHOE_SEED = 20261107L;
    private static final long ROLL_SEED = 7L;

    // Outcome classes (as PokerRtp): FOLD, PUSH, LOSE, NO_QUALIFY x the player's category, WIN x category.
    private static final int C_FOLD = 0, C_PUSH = 1, C_LOSE = 2, C_NOQ = 3, C_WIN = 13, CLASSES = 23;
    private static final String[] CATEGORY = {"high card", "pair", "two pair", "three of a kind", "straight",
            "flush", "full house", "four of a kind", "straight flush", "royal flush"};
    /** PokerHands.antePays by category. */
    private static final int[] K = {1, 1, 1, 1, 1, 2, 3, 10, 20, 100};
    private static final String[] NAMES = new String[CLASSES];
    /** Items back and items staked per class for an ante of 1 item. */
    private static final int[] BACK = new int[CLASSES];
    private static final int[] STAKED = new int[CLASSES];

    static {
        NAMES[C_FOLD] = "FOLD";
        NAMES[C_PUSH] = "PUSH";
        NAMES[C_LOSE] = "LOSE";
        STAKED[C_FOLD] = 1;
        STAKED[C_PUSH] = 3;
        STAKED[C_LOSE] = 3;
        BACK[C_PUSH] = 3;
        for (int c = 0; c < 10; c++) {
            NAMES[C_NOQ + c] = "NO_QUALIFY " + CATEGORY[c];
            NAMES[C_WIN + c] = "WIN " + CATEGORY[c];
            BACK[C_NOQ + c] = 3 + K[c];
            BACK[C_WIN + c] = 5 + K[c];
            STAKED[C_NOQ + c] = 3;
            STAKED[C_WIN + c] = 3;
        }
    }

    /**
     * PokerRtp b): 8e9 hands of the mod's game path (seeds 1 and 2): RTP per item staked (ante + call) and per ante,
     * with their standard errors (the larger of multinomial and batch means).
     */
    private static final double MC_RTP_PER_ITEM = 0.98447715, MC_RTP_PER_ITEM_SE = 0.0000109;
    private static final double MC_RTP_PER_ANTE = 0.95729849, MC_RTP_PER_ANTE_SE = 0.0000300;

    /**
     * PokerRtp a): exact class counts of simpleCall over all 2,598,960 boards x 10 flop splits x 1081 player holes x
     * 990 dealer holes (every deal equally likely), and the exact RTP per item staked they give.
     */
    private static final long[] EXACT_COUNTS = {3464333452800L, 811402216160L, 9877458071180L,
            1482997918692L, 2853262812708L, 831098718468L, 144640121400L, 298248655308L, 166162601376L, 24602292000L,
            911196000L, 1064378952L, 119892960L,
            0L, 1982748707016L, 3236689198140L, 812608861896L, 664616255880L, 509078288508L, 603502712640L,
            41497452000L, 6029960876L, 736259040L};
    private static final double EXACT_RTP_PER_ITEM = 0.9844680063114767;
    private static final double[] EXACT_P = new double[CLASSES];

    static {
        long total = 0;
        for (long n : EXACT_COUNTS) {
            total += n;
        }
        for (int c = 0; c < CLASSES; c++) {
            EXACT_P[c] = (double) EXACT_COUNTS[c] / total;
        }
    }

    /** PokerRtp "replay 20261107 10000000": the tool's own literal round on this test's shoe, card for card. */
    private static final long[] REPLAY_COUNTS = {1245443L, 292070L, 3550926L, 532224L, 1027193L, 298612L,
            52471L, 107109L, 59720L, 8898L, 353L, 387L, 41L, 0L, 712101L, 1163933L, 291932L, 238826L, 183126L, 217496L,
            14747L, 2126L, 266L};

    static final class Recorder implements PokerRound.Listener {
        int reveals;
        int decidedAt = -1;
        int timedOutAt = -1;

        @Override
        public void cardDealt(boolean toDealer, int index, int card, boolean faceUp, int at) {
        }

        @Override
        public void holeCardRevealed(int index, int card, int at) {
            reveals++;
        }

        @Override
        public void turnOpened(int deadline, int at) {
        }

        @Override
        public void turnTimedOut(int at) {
            timedOutAt = at;
        }

        @Override
        public void phaseChanged(PokerRound.Phase phase, int at) {
        }

        @Override
        public void decided(PokerRound.Result result, int at) {
            decidedAt = at;
        }

        @Override
        public void reaction(BlackjackRound.Reaction reaction, int at) {
        }

        @Override
        public void cleared(int at) {
        }
    }

    private static final PokerRound.Listener QUIET = new Recorder();

    /** Items paid back for a stake of {@code count} items (PokerTableBlockEntity.decided, no exchange rule). */
    private static int paid(PokerRound.Result result, int count, int thirds, int roll) {
        if (result.isPush()) {
            return count; // CasinoExchange.payout(stake, 1, 1, roll, rule, refund = true)
        }
        return result.isWin() ? FairPayout.share((long) count * thirds, 3, roll) : 0;
    }

    private static int classOf(PokerRound round) {
        return switch (round.result()) {
            case FOLD -> C_FOLD;
            case PUSH -> C_PUSH;
            case LOSE -> C_LOSE;
            case NO_QUALIFY -> C_NOQ + PokerHands.category(round.playerScore());
            case WIN -> C_WIN + PokerHands.category(round.playerScore());
            case NONE -> throw new AssertionError("hand ended without a result");
        };
    }

    @Test
    void tenMillionHandsThroughTheShippedRoundReturnWhatTheModelSays() {
        long start = System.nanoTime();
        // one shoe per table, as the mod (PokerTableBlockEntity 356-358); every round reshuffles all 52 cards
        BlackjackShoe shoe = new BlackjackShoe(new ShoeRandom(SHOE_SEED), 1);
        Random rolls = new Random(ROLL_SEED);
        long[] counts = new long[CLASSES];
        long back = 0;
        long staked = 0;
        long backSquares = 0;
        long stakedSquares = 0;
        long backTimesStaked = 0;
        long ticks = 0;
        long oddBack = 0;
        long oddStaked = 0;
        for (int i = 0; i < HANDS; i++) {
            PokerRound round = new PokerRound(shoe, QUIET);
            int guard = 0;
            while (!round.isOver()) {
                if (++guard > 2_000) {
                    throw new AssertionError("hand " + i + " does not end");
                }
                round.tick();
                if (round.phase() == PokerRound.Phase.DECISION) {
                    // the mod's own strategy (PokerBias.simpleCall: hole cards + flop)
                    assertTrue(PokerBias.simpleCall(round.player(), round.board()) ? round.call() : round.fold());
                }
            }
            ticks += guard;
            int c = classOf(round);
            counts[c]++;
            int thirds = round.payoutThirds();
            if (thirds != (c == C_FOLD ? 0 : BACK[c])) {
                throw new AssertionError("hand " + i + ": " + NAMES[c] + " pays " + thirds + " thirds");
            }
            int stake = round.called() ? 3 : 1; // takeCallStake: every stake becomes count x 3
            int roll = rolls.nextInt(3);
            int got = paid(round.result(), stake, thirds, roll);
            back += got;
            staked += stake;
            backSquares += (long) got * got;
            stakedSquares += (long) stake * stake;
            backTimesStaked += (long) got * stake;
            // an odd ante (1, 3, .., 63) with another roll: exactly ante x the class's items, no rounding
            int ante = 2 * (i & 31) + 1;
            int oddStake = round.called() ? 3 * ante : ante;
            int oddGot = paid(round.result(), oddStake, thirds, (roll + 1 + (i & 1)) % 3);
            if (oddGot != ante * BACK[c]) {
                throw new AssertionError("hand " + i + ": ante " + ante + " " + NAMES[c] + " paid " + oddGot);
            }
            oddBack += oddGot;
            oddStaked += oddStake;
        }
        double seconds = (System.nanoTime() - start) / 1e9;

        double n = HANDS;
        double rtpPerItem = (double) back / staked;
        double meanNet = (double) (back - staked) / n;
        double rtpPerAnte = 1 + meanNet;
        double r = rtpPerItem;
        double residual = (backSquares - 2 * r * backTimesStaked + r * r * stakedSquares) / n;
        double seItem = Math.sqrt(residual / n) / (staked / n);
        double netSquares = (backSquares - 2.0 * backTimesStaked + stakedSquares) / n;
        double seAnte = Math.sqrt((netSquares - meanNet * meanNet) / n);
        System.out.printf(Locale.ROOT, "poker, %,d hands through PokerRound (%,d ticks) in %.1f s:%n"
                        + "  RTP per item staked %.5f +- %.5f (MC %.5f, exact %.5f), per ante %.5f +- %.5f (MC %.5f)%n"
                        + "  odd antes 1..63: RTP per item %.5f%n", HANDS, ticks, seconds, rtpPerItem, seItem,
                MC_RTP_PER_ITEM, EXACT_RTP_PER_ITEM, rtpPerAnte, seAnte, MC_RTP_PER_ANTE,
                (double) oddBack / oddStaked);
        for (int c = 0; c < CLASSES; c++) {
            System.out.printf(Locale.ROOT, "  %-28s %.6f (exact %.6f)%n", NAMES[c], counts[c] / n, EXACT_P[c]);
        }

        // 1. the shipped code deals and decides exactly what the independent model does
        assertArrayEquals(REPLAY_COUNTS, counts, "outcome counts differ from PokerRtp replay " + SHOE_SEED);

        // 2. RTP within 4 standard errors of the high-precision Monte Carlo (and of the exact value)
        double sigmaItem = Math.hypot(seItem, MC_RTP_PER_ITEM_SE);
        assertTrue(Math.abs(rtpPerItem - MC_RTP_PER_ITEM) < 4 * sigmaItem, "RTP per item " + rtpPerItem + " vs "
                + MC_RTP_PER_ITEM + " (4 sigma = " + 4 * sigmaItem + ")");
        assertTrue(Math.abs(rtpPerItem - EXACT_RTP_PER_ITEM) < 4 * seItem, "RTP per item " + rtpPerItem
                + " vs exact " + EXACT_RTP_PER_ITEM);
        double sigmaAnte = Math.hypot(seAnte, MC_RTP_PER_ANTE_SE);
        assertTrue(Math.abs(rtpPerAnte - MC_RTP_PER_ANTE) < 4 * sigmaAnte, "RTP per ante " + rtpPerAnte + " vs "
                + MC_RTP_PER_ANTE + " (4 sigma = " + 4 * sigmaAnte + ")");

        // 3. every outcome class and the five results within 4 sigma of the exact probabilities
        for (int c = 0; c < CLASSES; c++) {
            assertShare(NAMES[c], counts[c], EXACT_P[c]);
        }
        long noq = 0;
        long win = 0;
        double pNoq = 0;
        double pWin = 0;
        for (int c = 0; c < 10; c++) {
            noq += counts[C_NOQ + c];
            win += counts[C_WIN + c];
            pNoq += EXACT_P[C_NOQ + c];
            pWin += EXACT_P[C_WIN + c];
        }
        assertShare("NO_QUALIFY", noq, pNoq);
        assertShare("WIN", win, pWin);
        assertShare("player wins (WIN + NO_QUALIFY)", noq + win, pNoq + pWin);
        assertEquals(0, counts[C_WIN], "a qualified dealer always beats high card");
        assertTrue(counts[C_NOQ + 9] + counts[C_WIN + 9] > 0, "10 M hands hold royal flushes");
    }

    private static void assertShare(String name, long count, double p) {
        double f = count / (double) HANDS;
        double sigma = Math.sqrt(p * (1 - p) / HANDS);
        assertTrue(Math.abs(f - p) <= 4 * sigma + 1e-12, name + " " + f + " vs exact " + p + " (4 sigma = "
                + 4 * sigma + ")");
    }

    /** The hard-coded reference numbers agree with each other. */
    @Test
    void referenceTablesAreConsistent() {
        double sum = 0;
        double back = 0;
        double staked = 0;
        for (int c = 0; c < CLASSES; c++) {
            sum += EXACT_P[c];
            back += EXACT_P[c] * BACK[c];
            staked += EXACT_P[c] * STAKED[c];
        }
        assertEquals(1.0, sum, 1e-12);
        assertEquals(EXACT_RTP_PER_ITEM, back / staked, 1e-12);
        assertTrue(Math.abs(MC_RTP_PER_ITEM - EXACT_RTP_PER_ITEM) < 4 * MC_RTP_PER_ITEM_SE, "MC vs exact");
        assertTrue(Math.abs(MC_RTP_PER_ANTE - (1 + back - staked)) < 4 * MC_RTP_PER_ANTE_SE, "MC vs exact per ante");
        long replay = 0;
        for (long r : REPLAY_COUNTS) {
            replay += r;
        }
        assertEquals(HANDS, replay);
    }

    // ---- the mod's strategy -------------------------------------------------------------------------------------

    /** Card from text: rank 23456789TJQKA, suit s h d c (labels for suits 0..3; h and d are the red ones). */
    static int card(String text) {
        int rank = "A23456789TJQK".indexOf(text.charAt(0)); // BlackjackCards: rank 0 = ace
        int suit = "shdc".indexOf(text.charAt(1));
        if (rank < 0 || suit < 0 || text.length() != 2) {
            throw new IllegalArgumentException(text);
        }
        return suit * 13 + rank;
    }

    static List<Integer> cards(String text) {
        List<Integer> out = new ArrayList<>();
        for (String c : text.trim().split("\\s+")) {
            out.add(card(c));
        }
        return out;
    }

    @Test
    void simpleCallCallsWithAPairADrawOrATenOrBetter() {
        // pair or better among hole + flop, also a pair on the board alone
        assertTrue(PokerBias.simpleCall(cards("7s 2h"), cards("7d 9c Kh")));
        assertTrue(PokerBias.simpleCall(cards("7s 2h"), cards("9d 9c 4h")));
        // 4-flush, 4 of 5 consecutive ranks (also inside and with the ace low), a T or higher in the hole
        assertTrue(PokerBias.simpleCall(cards("7s 2s"), cards("9s Ks 4h")));
        assertTrue(PokerBias.simpleCall(cards("7s 6h"), cards("4d 5c Kh")));
        assertTrue(PokerBias.simpleCall(cards("7s 3h"), cards("4d 6c Kh")));
        assertTrue(PokerBias.simpleCall(cards("As 2h"), cards("3d 5c 9h")));
        assertTrue(PokerBias.simpleCall(cards("Ts 2h"), cards("4d 7c 8h")));
        // nothing: 3-flush, 3 to a straight, overcards on the board only
        assertFalse(PokerBias.simpleCall(cards("7s 2h"), cards("9d 4c Kh")));
        assertFalse(PokerBias.simpleCall(cards("7s 2s"), cards("9s 4c Kh")));
        assertFalse(PokerBias.simpleCall(cards("9s 2h"), cards("Ad Kc Qh")));
    }

    // ---- rigged shoes: every result, every paytable line, the qualifier and the tie rules --------------------------

    private record Hand(PokerRound round, Recorder events) {
        PokerRound.Result result() {
            return round.result();
        }
    }

    /**
     * Plays a rigged one-deck shoe, cards in deal order: player, dealer, player, dealer, flop x 3, turn, river. At
     * DECISION it calls or folds as told (and checks what simpleCall would do).
     */
    private static Hand play(boolean call, boolean simple, String deal) {
        List<Integer> order = cards(deal);
        int[] rig = new int[order.size()];
        for (int i = 0; i < rig.length; i++) {
            rig[i] = order.get(i);
        }
        Recorder events = new Recorder();
        PokerRound round = new PokerRound(BlackjackShoe.riggedDecks(new ShoeRandom(1L), 1, rig), events);
        boolean decided = false;
        for (int guard = 0; !round.isOver(); guard++) {
            assertTrue(guard < 2_000, "hand does not end");
            round.tick();
            if (round.phase() == PokerRound.Phase.DECISION) {
                assertFalse(decided);
                decided = true;
                assertEquals(List.of(order.get(0), order.get(2)), round.player());
                assertEquals(order.subList(4, 7), round.board());
                assertEquals(simple, PokerBias.simpleCall(round.player(), round.board()), "simpleCall on " + deal);
                assertTrue(call ? round.call() : round.fold());
            }
        }
        assertTrue(decided);
        return new Hand(round, events);
    }

    private static Hand called(String deal) {
        return play(true, true, deal);
    }

    /** Result, the player's category, the thirds, and the items back for antes 1..7 with every roll. */
    private static void assertPays(Hand hand, PokerRound.Result result, int category, int thirds) {
        PokerRound round = hand.round();
        assertEquals(result, round.result());
        assertEquals(thirds, round.payoutThirds(), result + " thirds");
        if (result != PokerRound.Result.FOLD) {
            assertEquals(category, PokerHands.category(round.playerScore()), "player " + CATEGORY[category]);
            assertEquals(5, round.board().size());
            assertEquals(2, hand.events().reveals, "both dealer cards shown at the showdown");
        }
        for (int ante = 1; ante <= 7; ante++) {
            int stake = round.called() ? 3 * ante : ante;
            for (int roll = 0; roll < 3; roll++) {
                int expected = result == PokerRound.Result.PUSH ? 3 * ante : ante * thirds;
                assertEquals(expected, paid(round.result(), stake, thirds, roll), result + " ante " + ante
                        + " roll " + roll);
            }
        }
    }

    @Test
    void foldLosesTheAnteAndShowsNothing() {
        // 7 2 offsuit, flop 9 4 K: no pair, no draw, no T+ -> simpleCall folds; the turn and river stay in the shoe
        Hand h = play(false, false, "7s As 2h Ah 9d 4c Kh 5s 5h");
        assertPays(h, PokerRound.Result.FOLD, 0, 0);
        assertFalse(h.round().called());
        assertEquals(3, h.round().board().size());
        assertEquals(-1, h.round().dealerScore());
        assertEquals(0, h.events().reveals);
        assertEquals(List.of(-1, -1), h.round().dealerVisible());
        assertEquals(0, paid(PokerRound.Result.FOLD, 5, 0, 2));
        // folding a strong hand is still a fold
        assertPays(play(false, true, "Ks 2c Kd 3c 9s 9h 9d 2d 3d"), PokerRound.Result.FOLD, 0, 0);
    }

    @Test
    void theTimeoutFoldsAtTheDeadline() {
        Recorder events = new Recorder();
        PokerRound round = new PokerRound(BlackjackShoe.riggedDecks(new ShoeRandom(1L), 1,
                card("As"), card("2c"), card("Ah"), card("3c"), card("Ad"), card("Ac"), card("Ks")), events);
        while (!round.isOver()) {
            round.tick();
        }
        assertEquals(PokerRound.Result.FOLD, round.result());
        assertEquals(209 + 600, events.timedOutAt, "DECISION opens at 209, 30 s to decide");
        assertEquals(809, events.decidedAt);
        assertEquals(0, round.payoutThirds());
    }

    @Test
    void lowerHandAgainstAQualifiedDealerLosesEverything() {
        // player K high vs dealer pair of aces
        assertPays(called("Kc Ah 7d As 9s 5d 2c Jh 3s"), PokerRound.Result.LOSE, 0, 0);
    }

    @Test
    void equalHandsPushAndRefundTheStake() {
        // both play the board's straight 5-9
        assertPays(called("2c 3c 2d 3h 5h 6d 7c 8s 9h"), PokerRound.Result.PUSH, 4, 3);
        // both play the board's flush: suits never break ties
        assertPays(called("3c 6c 4d 7d 2h 5h 9h Jh Kh"), PokerRound.Result.PUSH, 5, 3);
    }

    @Test
    void kickersBeyondFiveCardsDoNotPlay() {
        // K K A Q J for both: the 2 and the 3 do not play -> push
        Hand h = called("As Ad 2c 3c Ks Kd Qc Jh 8s");
        assertPays(h, PokerRound.Result.PUSH, 1, 3);
        assertEquals(h.round().playerScore(), h.round().dealerScore());
    }

    @Test
    void theKickerDecides() {
        // pair of kings from the board: A kicker beats Q kicker
        assertPays(called("Ac Qh 3d 5d Ks Kd 9c 7h 2s"), PokerRound.Result.WIN, 1, 6);
        assertPays(called("Qh Ac 5d 3d Ks Kd 9c 7h 2s"), PokerRound.Result.LOSE, 1, 0);
        // two pair K K 9 9 from the board: the hole Q beats the hole J
        assertPays(called("Qd Jc 2d 3c Ks Kd 9c 9h 4s"), PokerRound.Result.WIN, 2, 6);
        // three pairs: the third pair's rank is the kicker (K K 9 9 + 5 vs K K 9 9 + 4)
        assertPays(called("5c 4c 5d 2c Ks Kd 9c 9h 4s"), PokerRound.Result.WIN, 2, 6);
    }

    @Test
    void theDealerQualifiesWithAPairOfFoursButNotThrees() {
        // dealer pair of 4s qualifies: player K high loses
        Hand fours = called("Th 4h 6c 4d 2s 8d Kc 5h Jc");
        assertPays(fours, PokerRound.Result.LOSE, 0, 0);
        assertTrue(PokerHands.qualifies(fours.round().dealerScore()));
        assertEquals(PokerHands.PAIR, PokerHands.category(fours.round().dealerScore()));
        assertEquals(4, fours.round().dealerScore() >> 16 & 15, "pair of 4s");
        // pair of 3s does not qualify: the ante pays 1:1 and the call pushes, although the dealer's hand is better
        Hand threes = called("Th 3h 6c 3d 2s 8d Kc 5h Jc");
        assertPays(threes, PokerRound.Result.NO_QUALIFY, 0, 4);
        assertFalse(PokerHands.qualifies(threes.round().dealerScore()));
        assertTrue(threes.round().dealerScore() > threes.round().playerScore());
        assertTrue(PokerHands.QUALIFIES > threes.round().dealerScore());
        assertTrue(PokerHands.QUALIFIES <= fours.round().dealerScore());
    }

    @Test
    void aDealerWhoDoesNotQualifyPaysTheAnteByThePaytable() {
        assertPays(called("Ac Kh 9d 7s 2s 5d 8c Jh 3c"), PokerRound.Result.NO_QUALIFY, 0, 4); // A high, k = 1
        assertPays(called("9c Jc 8d 3d 7s 6h 5c Kd 2h"), PokerRound.Result.NO_QUALIFY, 4, 4); // straight, k = 1
        assertPays(called("Ah 3c 9h 8d Kh 7h 2h 5c Jd"), PokerRound.Result.NO_QUALIFY, 5, 5); // flush, k = 2
        assertPays(called("3s 5c 3h 7d 3d 2c 2d 9s Kh"), PokerRound.Result.NO_QUALIFY, 6, 6); // full house, k = 3
        assertPays(called("2s 5c 2h Jd 2d 2c 9s Kh 7d"), PokerRound.Result.NO_QUALIFY, 7, 13); // quads, k = 10
        assertPays(called("9s Jc 8s 3d 7s 6s 5s Kh 2d"), PokerRound.Result.NO_QUALIFY, 8, 23); // straight flush, 20
        assertPays(called("As 7h Ks 8d Qs Js Ts 4d 2c"), PokerRound.Result.NO_QUALIFY, 9, 103); // royal, k = 100
    }

    @Test
    void aWinPaysTheAnteByThePaytableAndTheCallEvenMoney() {
        assertPays(called("Kc 5h Kd 5s 2s 8d Jc 9h 3c"), PokerRound.Result.WIN, 1, 6); // KK vs 55, k = 1
        assertPays(called("Ah Jc 9h Js Kh 7h 2h 5c Jd"), PokerRound.Result.WIN, 5, 7); // flush vs trips, k = 2
        assertPays(called("9s Ac 9h Ad 9d Kc Kh 2s 5d"), PokerRound.Result.WIN, 6, 8); // full house vs two pair
        assertPays(called("7s Kd 7h Kh 7d 7c Ks 2h 9d"), PokerRound.Result.WIN, 7, 15); // quads vs full house
        assertPays(called("9s Kc 8s 2h 7s 6s 5s Kh Kd"), PokerRound.Result.WIN, 8, 25); // straight flush vs trips
        assertPays(called("As 9c Ks 2h Qs Js Ts 9h 9d"), PokerRound.Result.WIN, 9, 105); // royal vs trips
    }

    @Test
    void theWheelIsTheLowestStraightAndTheSteelWheelNoRoyal() {
        // A-2-3-4-5 is a straight (high 5) that beats a pair of kings...
        Hand wheel = called("Ac Kh 2d 7c 3s 4h 5c Kd 9s");
        assertPays(wheel, PokerRound.Result.WIN, 4, 6);
        assertEquals(4 << 20 | 5 << 16, wheel.round().playerScore());
        // ... and loses to 3-7
        assertPays(called("Ac 6h 2d 7c 3s 4h 5c Kd 9s"), PokerRound.Result.LOSE, 4, 0);
        // the suited wheel is a straight flush (k = 20), not a royal flush
        Hand steel = called("As Jc 2s 7d 3s 4s 5s Kh 9d");
        assertPays(steel, PokerRound.Result.NO_QUALIFY, 8, 23);
        assertEquals(8 << 20 | 5 << 16, steel.round().playerScore());
    }
}
