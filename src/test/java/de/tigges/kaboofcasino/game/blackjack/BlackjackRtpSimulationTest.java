package de.tigges.kaboofcasino.game.blackjack;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.game.FairPayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Return to player of the shipped blackjack: {@link BlackjackRound} + {@link BlackjackShoe} (verbatim ports of
 * MineVegas Casino, bias 0) driven tick by tick, a player that applies the mod's own strategy
 * ({@link BlackjackBias#bestPlay}) at every PLAYER_TURN, payouts with {@link FairPayout} (halves / 2, a seeded roll
 * per stake, the refund on a push), base stake 1 item (the worst case for the 3:2 rounding).
 * <p>
 * The reference numbers come from {@code tools/rtp/BlackjackRtp.java} (independent of the plugin code; method and
 * results in its header): a) exact off-the-top probabilities of a fresh 6-deck shoe, c) a Monte Carlo of 1.8e10 rounds
 * with the mod's shoe, shuffle and 60-card cut, and the "replay" counts, which the tool's own round logic produces for
 * this test's shoe seed. 10 million rounds take about 35 s.
 */
class BlackjackRtpSimulationTest {

    private static final int ROUNDS = 10_000_000;
    private static final long SHOE_SEED = 20261106L;
    private static final long ROLL_SEED = 7L;

    // Outcome classes (as BlackjackRtp): result x doubled, plus WIN21 = the WINs that were 21 vs 21 (not a class).
    private static final int WIN = 0, WIN_DOUBLED = 1, DEALER_BUST = 2, DEALER_BUST_DOUBLED = 3, BLACKJACK = 4,
            PUSH = 5, PUSH_DOUBLED = 6, PUSH_BLACKJACKS = 7, LOSE = 8, LOSE_DOUBLED = 9, BUST = 10, BUST_DOUBLED = 11,
            DEALER_BLACKJACK = 12, CLASSES = 13, WIN21 = 13, SLOTS = 14;
    private static final String[] NAMES = {"WIN", "WIN doubled", "WIN_DEALER_BUST", "WIN_DEALER_BUST doubled",
            "BLACKJACK", "PUSH", "PUSH doubled", "PUSH_BLACKJACKS", "LOSE", "LOSE doubled", "LOSE_BUST",
            "LOSE_BUST doubled", "LOSE_DEALER_BLACKJACK", "WIN 21 vs 21"};

    /**
     * BlackjackRtp c): 2 x 9e9 rounds, seeds 1 and 2; RTP per item staked (doubles count) and per base stake, with
     * their standard errors (the larger of multinomial and batch means).
     */
    private static final double MC_RTP_PER_ITEM = 0.995452578193, MC_RTP_PER_ITEM_SE = 0.0000076;
    private static final double MC_RTP_PER_BASE = 0.995014351528, MC_RTP_PER_BASE_SE = 0.0000083;
    /** BlackjackRtp c): outcome counts over all rounds (the 60-card cut included). */
    private static final long[] MC_COUNTS = {2520526089L, 381846053L, 3540735182L, 594164459L, 814888381L,
            1347877596L, 97156094L, 38909851L, 4234277819L, 661460244L, 2953273244L, 0L, 814884988L, 65645978L};
    private static final long MC_ROUNDS = 18_000_000_000L;
    private static final double[] MC_P = new double[SLOTS];

    static {
        for (int c = 0; c < SLOTS; c++) {
            MC_P[c] = (double) MC_COUNTS[c] / MC_ROUNDS;
        }
    }

    /** BlackjackRtp a): exact class probabilities off the top of a fresh shoe (= the first round of every shoe). */
    private static final double[] EXACT_P = {0.140056346633, 0.021192533175, 0.196732821932, 0.032970800631,
            0.045322987716, 0.074925780161, 0.005388750464, 0.002166500288, 0.235069786695, 0.036660715510,
            0.164189989078, 0.0, 0.045322987716, 0.003641690508};
    private static final double EXACT_RTP_PER_ITEM = 0.9956177516;

    /** BlackjackRtp "replay 20261106 10000000": the tool's own round logic on this test's shoe, card for card. */
    private static final long[] REPLAY_COUNTS = {1398610L, 211991L, 1967594L, 330222L, 453544L, 749482L, 53730L,
            21394L, 2351086L, 367743L, 1642668L, 0L, 451936L, 36274L};

    static final BlackjackRound.Listener QUIET = new BlackjackRound.Listener() {
        @Override
        public void cardDealt(boolean toDealer, int index, int card, boolean faceUp, int at) {
        }

        @Override
        public void holeCardRevealed(int card, int at) {
        }

        @Override
        public void turnOpened(int deadline, int at) {
        }

        @Override
        public void phaseChanged(BlackjackRound.Phase phase, int at) {
        }

        @Override
        public void decided(BlackjackRound.Result result, int at) {
        }

        @Override
        public void reaction(BlackjackRound.Reaction reaction, int at) {
        }

        @Override
        public void cleared(int at) {
        }
    };

    /** The mod's strategy at a PLAYER_TURN: hit, stand or double as {@link BlackjackBias#bestPlay} says. */
    private static void play(BlackjackRound round) {
        switch (BlackjackBias.bestPlay(round.player(), round.dealer().get(0), round.canDouble())) {
            case BlackjackBias.STAND -> assertTrue(round.stand());
            case BlackjackBias.HIT -> assertTrue(round.hit());
            case BlackjackBias.DOUBLE -> assertTrue(round.doubleDown());
            default -> throw new AssertionError("unknown play");
        }
    }

    /** Items paid back for a stake of {@code count} items (BlackjackTableBlockEntity.decided, no exchange rule). */
    private static int paid(BlackjackRound.Result result, int count, int roll) {
        return result.isPush() ? count : FairPayout.share((long) count * result.halves, 2, roll);
    }

    private static int classOf(BlackjackRound round) {
        boolean d = round.doubled();
        return switch (round.result()) {
            case WIN -> d ? WIN_DOUBLED : WIN;
            case WIN_DEALER_BUST -> d ? DEALER_BUST_DOUBLED : DEALER_BUST;
            case BLACKJACK -> BLACKJACK;
            case PUSH -> d ? PUSH_DOUBLED : PUSH;
            case PUSH_BLACKJACKS -> PUSH_BLACKJACKS;
            case LOSE -> d ? LOSE_DOUBLED : LOSE;
            case LOSE_BUST -> d ? BUST_DOUBLED : BUST;
            case LOSE_DEALER_BLACKJACK -> DEALER_BLACKJACK;
            case NONE -> throw new AssertionError("round ended without a result");
        };
    }

    @Test
    void tenMillionRoundsThroughTheShippedRoundReturnWhatTheModelSays() {
        long start = System.nanoTime();
        BlackjackShoe shoe = new BlackjackShoe(new Random(SHOE_SEED));
        Random rolls = new Random(ROLL_SEED);
        long[] counts = new long[SLOTS];
        long[] firstCounts = new long[SLOTS];
        long firstRounds = 0;
        long back = 0;
        long staked = 0;
        long backSquares = 0;
        long stakedSquares = 0;
        long backTimesStaked = 0;
        long ticks = 0;
        for (int i = 0; i < ROUNDS; i++) {
            boolean fresh = shoe.remaining() == 52 * BlackjackShoe.DECKS || shoe.needsShuffle();
            BlackjackRound round = new BlackjackRound(shoe, QUIET);
            int guard = 0;
            while (!round.isOver()) {
                if (++guard > 10_000) {
                    throw new AssertionError("round " + i + " does not end");
                }
                round.tick();
                if (round.phase() == BlackjackRound.Phase.PLAYER_TURN) {
                    play(round);
                }
            }
            ticks += guard;
            int c = classOf(round);
            boolean win21 = round.result() == BlackjackRound.Result.WIN && round.playerTotal() == 21
                    && BlackjackCards.total(round.dealer()) == 21;
            counts[c]++;
            if (win21) {
                counts[WIN21]++;
            }
            if (fresh) {
                firstRounds++;
                firstCounts[c]++;
                if (win21) {
                    firstCounts[WIN21]++;
                }
            }
            int stake = round.doubled() ? 2 : 1;
            int got = paid(round.result(), stake, rolls.nextInt(2));
            back += got;
            staked += stake;
            backSquares += (long) got * got;
            stakedSquares += (long) stake * stake;
            backTimesStaked += (long) got * stake;
        }
        double seconds = (System.nanoTime() - start) / 1e9;

        double n = ROUNDS;
        double rtpPerItem = (double) back / staked;
        double meanNet = (double) (back - staked) / n;
        double rtpPerBase = 1 + meanNet;
        // ratio estimator: Var(back - R staked) / (n mean(staked)^2); per base stake: Var(back - staked) / n
        double r = rtpPerItem;
        double residual = (backSquares - 2 * r * backTimesStaked + r * r * stakedSquares) / n;
        double seItem = Math.sqrt(residual / n) / (staked / n);
        double netSquares = (backSquares - 2.0 * backTimesStaked + stakedSquares) / n;
        double seBase = Math.sqrt((netSquares - meanNet * meanNet) / n);
        System.out.printf(Locale.ROOT, "blackjack, %,d rounds through BlackjackRound (%,d ticks) in %.1f s:%n"
                        + "  RTP per item staked %.5f +- %.5f (model %.5f), per base stake %.5f +- %.5f (model %.5f)%n",
                ROUNDS, ticks, seconds, rtpPerItem, seItem, MC_RTP_PER_ITEM, rtpPerBase, seBase, MC_RTP_PER_BASE);
        for (int c = 0; c < SLOTS; c++) {
            System.out.printf(Locale.ROOT, "  %-24s %.5f (model %.5f)%n", NAMES[c], counts[c] / n, MC_P[c]);
        }

        // 1. the shipped code deals exactly what the independent model deals: same cards, same decisions
        assertArrayEquals(REPLAY_COUNTS, counts, "outcome counts differ from BlackjackRtp replay " + SHOE_SEED);

        // 2. RTP within 4 standard errors of the high-precision value
        double sigmaItem = Math.hypot(seItem, MC_RTP_PER_ITEM_SE);
        assertTrue(Math.abs(rtpPerItem - MC_RTP_PER_ITEM) < 4 * sigmaItem, "RTP per item " + rtpPerItem + " vs "
                + MC_RTP_PER_ITEM + " (4 sigma = " + 4 * sigmaItem + ")");
        double sigmaBase = Math.hypot(seBase, MC_RTP_PER_BASE_SE);
        assertTrue(Math.abs(rtpPerBase - MC_RTP_PER_BASE) < 4 * sigmaBase, "RTP per base stake " + rtpPerBase
                + " vs " + MC_RTP_PER_BASE + " (4 sigma = " + 4 * sigmaBase + ")");

        // 3. outcome classes: all rounds against c), the first round of each shoe against the exact a)
        for (int c = 0; c < SLOTS; c++) {
            double f = counts[c] / n;
            double p = MC_P[c];
            double sigma = Math.sqrt(p * (1 - p) / n + p * (1 - p) / MC_ROUNDS);
            assertTrue(Math.abs(f - p) <= 4 * sigma, NAMES[c] + " " + f + " vs " + p + " (4 sigma = " + 4 * sigma
                    + ")");
            double first = firstCounts[c] / (double) firstRounds;
            double exact = EXACT_P[c];
            double sigmaFirst = Math.sqrt(exact * (1 - exact) / firstRounds);
            assertTrue(Math.abs(first - exact) <= 4 * sigmaFirst, "first rounds: " + NAMES[c] + " " + first
                    + " vs exact " + exact + " (4 sigma = " + 4 * sigmaFirst + ")");
        }
        assertEquals(0, counts[BUST_DOUBLED], "the strategy never doubles a hand that can bust");
    }

    /** RTP per item of the exact class probabilities (sanity of the hard-coded tables). */
    @Test
    void referenceTablesAreConsistent() {
        double sumMc = 0;
        double sumExact = 0;
        double backExact = 0;
        double stakedExact = 0;
        int[] halves = {4, 8, 4, 8, 5, 2, 4, 2, 0, 0, 0, 0, 0};
        int[] stakes = {1, 2, 1, 2, 1, 1, 2, 1, 1, 2, 1, 2, 1};
        double backMc = 0;
        double stakedMc = 0;
        for (int c = 0; c < CLASSES; c++) {
            sumMc += MC_P[c];
            sumExact += EXACT_P[c];
            backExact += EXACT_P[c] * halves[c] / 2.0;
            stakedExact += EXACT_P[c] * stakes[c];
            backMc += MC_P[c] * halves[c] / 2.0;
            stakedMc += MC_P[c] * stakes[c];
        }
        assertEquals(1.0, sumExact, 1e-9);
        assertEquals(1.0, sumMc, 1e-12);
        assertEquals(EXACT_RTP_PER_ITEM, backExact / stakedExact, 1e-9);
        assertEquals(MC_RTP_PER_ITEM, backMc / stakedMc, 1e-9);
        assertEquals(MC_RTP_PER_BASE, 1 + backMc - stakedMc, 1e-9);
        long replayRounds = 0;
        for (int c = 0; c < CLASSES; c++) {
            replayRounds += REPLAY_COUNTS[c];
        }
        assertEquals(ROUNDS, replayRounds);
    }

    // ---- the mod's strategy is the chart of games.md (appendix of section 3) ---------------------------------------

    /** Rows of the chart; columns dealer up 2..10, A. D = double if allowed, the second letter otherwise. */
    private static final String[][] HARD = {
            {"5-8", "H  H  H  H  H  H  H  H  H  H"},
            {"9", "H DH DH DH DH  H  H  H  H  H"},
            {"10", "DH DH DH DH DH DH DH DH  H  H"},
            {"11", "DH DH DH DH DH DH DH DH DH  H"},
            {"12", "H  H  H  S  S  H  H  H  H  H"},
            {"13-16", "S  S  S  S  S  H  H  H  H  H"},
            {"17-20", "S  S  S  S  S  S  S  S  S  S"},
    };
    private static final String[][] SOFT = {
            {"2", "H  H  H  H DH  H  H  H  H  H"},
            {"3", "H  H  H DH DH  H  H  H  H  H"},
            {"4", "H  H DH DH DH  H  H  H  H  H"},
            {"5", "H  H DH DH DH  H  H  H  H  H"},
            {"6", "DH DH DH DH DH  H  H  H  H  H"},
            {"7", "DS DS DS DS DS  S  S  H  H  H"},
            {"8-9", "S  S  S  S  S  S  S  S  S  S"},
    };

    private static int card(int value) {
        return value == 1 ? 0 : value - 1; // suit 0: ace = 0, 2..9 = 1..8, ten = 9
    }

    @Test
    void theModsStrategyIsTheChart() {
        int checked = 0;
        for (String[][] chart : new String[][][] {HARD, SOFT}) {
            for (String[] row : chart) {
                String[] range = row[0].split("-");
                String[] cells = row[1].trim().split("\\s+");
                assertEquals(10, cells.length);
                for (int x = Integer.parseInt(range[0]); x <= Integer.parseInt(range[range.length - 1]); x++) {
                    // hard x: two non-aces; soft: ace + x
                    List<Integer> hand = chart == SOFT ? List.of(card(1), card(x))
                            : x <= 11 ? List.of(card(2), card(x - 2)) : List.of(card(10), card(x - 10));
                    for (int i = 0; i < 10; i++) {
                        int up = card(i == 9 ? 1 : i + 2);
                        String cell = cells[i];
                        int allowed = BlackjackBias.bestPlay(hand, up, true);
                        int notAllowed = BlackjackBias.bestPlay(hand, up, false);
                        String what = row[0] + " (" + x + ") vs " + (i == 9 ? "A" : i + 2);
                        if (cell.startsWith("D")) {
                            assertEquals(BlackjackBias.DOUBLE, allowed, what);
                            assertEquals(cell.charAt(1) == 'H' ? BlackjackBias.HIT : BlackjackBias.STAND, notAllowed,
                                    what);
                        } else {
                            int play = cell.equals("H") ? BlackjackBias.HIT : BlackjackBias.STAND;
                            assertEquals(play, allowed, what);
                            assertEquals(play, notAllowed, what);
                        }
                        checked++;
                    }
                }
            }
        }
        assertEquals((16 + 8) * 10, checked); // hard 5..20, soft A,2..A,9
    }

    // ---- rigged shoes: every resolve rule, its Result and its payout ---------------------------------------------

    private record Hand(BlackjackRound.Result result, boolean doubled, List<Integer> player, List<Integer> dealer,
                        boolean turn, boolean peeked) {
    }

    /**
     * Plays a rigged shoe (cards in deal order: player, dealer up, player, hole, then the draws) with scripted
     * decisions at the turns: H hit, S stand, D double.
     */
    private static Hand rigged(String decisions, int... values) {
        int[] cards = new int[values.length];
        for (int i = 0; i < values.length; i++) {
            cards[i] = card(values[i]);
        }
        boolean[] peeked = new boolean[1];
        BlackjackRound.Listener listener = new BlackjackRound.Listener() {
            @Override
            public void cardDealt(boolean toDealer, int index, int card, boolean faceUp, int at) {
            }

            @Override
            public void holeCardRevealed(int card, int at) {
            }

            @Override
            public void dealerPeek(boolean blackjack, int at) {
                peeked[0] = true;
            }

            @Override
            public void turnOpened(int deadline, int at) {
            }

            @Override
            public void phaseChanged(BlackjackRound.Phase phase, int at) {
            }

            @Override
            public void decided(BlackjackRound.Result result, int at) {
            }

            @Override
            public void reaction(BlackjackRound.Reaction reaction, int at) {
            }

            @Override
            public void cleared(int at) {
            }
        };
        BlackjackRound round = new BlackjackRound(BlackjackShoe.rigged(new Random(1), cards), listener);
        int next = 0;
        boolean turn = false;
        for (int guard = 0; !round.isOver(); guard++) {
            assertTrue(guard < 10_000, "round does not end");
            round.tick();
            if (round.phase() == BlackjackRound.Phase.PLAYER_TURN) {
                turn = true;
                assertTrue(next < decisions.length(), "unexpected turn after " + decisions.substring(0, next));
                char d = decisions.charAt(next++);
                assertTrue(switch (d) {
                    case 'H' -> round.hit();
                    case 'S' -> round.stand();
                    case 'D' -> round.doubleDown();
                    default -> throw new IllegalArgumentException("decision " + d);
                });
            }
        }
        assertEquals(decisions.length(), next, "decisions left over");
        assertTrue(round.holeRevealed());
        return new Hand(round.result(), round.doubled(), new ArrayList<>(round.player()),
                new ArrayList<>(round.dealer()), turn, peeked[0]);
    }

    /** Items back for base stakes 1, 2, 3 with roll 0 and roll 1 (doubles take the stake twice). */
    private static void assertPays(Hand hand, int halves, int... expected) {
        assertEquals(halves, hand.result().halves, hand.result() + " halves");
        int k = 0;
        for (int base = 1; base <= 3; base++) {
            for (int roll = 0; roll < 2; roll++) {
                int stake = hand.doubled() ? 2 * base : base;
                assertEquals(expected[k++], paid(hand.result(), stake, roll), hand.result() + " base " + base
                        + " roll " + roll);
            }
        }
    }

    @Test
    void playerBustLosesAndTheDealerDoesNotDraw() {
        Hand h = rigged("H", 10, 9, 6, 8, 10);
        assertEquals(BlackjackRound.Result.LOSE_BUST, h.result());
        assertEquals(2, h.dealer().size());
        assertPays(h, 0, 0, 0, 0, 0, 0, 0);
    }

    @Test
    void bothNaturalsPushAfterThePeek() {
        Hand h = rigged("", 1, 1, 10, 10);
        assertEquals(BlackjackRound.Result.PUSH_BLACKJACKS, h.result());
        assertTrue(h.peeked());
        assertFalse(h.turn());
        assertPays(h, 2, 1, 1, 2, 2, 3, 3);
    }

    @Test
    void dealerNaturalBeatsEverythingButANatural() {
        Hand ace = rigged("", 10, 1, 9, 10);
        assertEquals(BlackjackRound.Result.LOSE_DEALER_BLACKJACK, ace.result());
        assertTrue(ace.peeked());
        assertFalse(ace.turn());
        assertPays(ace, 0, 0, 0, 0, 0, 0, 0);
        Hand ten = rigged("", 9, 10, 9, 1);
        assertEquals(BlackjackRound.Result.LOSE_DEALER_BLACKJACK, ten.result());
        assertFalse(ten.turn());
    }

    @Test
    void playerNaturalPaysThreeToTwoWithFairRounding() {
        Hand noPeek = rigged("", 1, 9, 10, 7);
        assertEquals(BlackjackRound.Result.BLACKJACK, noPeek.result());
        assertFalse(noPeek.peeked());
        assertFalse(noPeek.turn());
        assertEquals(2, noPeek.dealer().size());
        // 2.5 n: odd stakes pay floor or ceil by the roll (mean exactly 2.5 n), even stakes exactly
        assertPays(noPeek, 5, 3, 2, 5, 5, 8, 7);
        Hand afterPeek = rigged("", 10, 10, 1, 9);
        assertEquals(BlackjackRound.Result.BLACKJACK, afterPeek.result());
        assertTrue(afterPeek.peeked());
    }

    @Test
    void dealerBustPaysEvenMoney() {
        Hand h = rigged("S", 10, 6, 8, 10, 10);
        assertEquals(BlackjackRound.Result.WIN_DEALER_BUST, h.result());
        assertEquals(3, h.dealer().size());
        assertPays(h, 4, 2, 2, 4, 4, 6, 6);
    }

    @Test
    void player21BeatsDealer21() {
        // 7 + 4 = 11 hits a ten: 21 stands automatically; dealer 10 + 6 draws a 5: 21 too -> WIN, not PUSH
        Hand h = rigged("H", 7, 10, 4, 6, 10, 5);
        assertEquals(BlackjackRound.Result.WIN, h.result());
        assertEquals(21, BlackjackCards.total(h.player()));
        assertEquals(21, BlackjackCards.total(h.dealer()));
        assertEquals(3, h.player().size());
        assertPays(h, 4, 2, 2, 4, 4, 6, 6);
    }

    @Test
    void equalTotalsPushAndReturnTheStake() {
        Hand h = rigged("S", 10, 10, 8, 8);
        assertEquals(BlackjackRound.Result.PUSH, h.result());
        assertPays(h, 2, 1, 1, 2, 2, 3, 3);
    }

    @Test
    void lowerTotalLoses() {
        Hand h = rigged("S", 10, 10, 7, 9);
        assertEquals(BlackjackRound.Result.LOSE, h.result());
        assertPays(h, 0, 0, 0, 0, 0, 0, 0);
    }

    @Test
    void higherTotalWins() {
        Hand h = rigged("S", 10, 9, 10, 8);
        assertEquals(BlackjackRound.Result.WIN, h.result());
        assertPays(h, 4, 2, 2, 4, 4, 6, 6);
    }

    @Test
    void doubledWinPaysFourTimesTheBaseStake() {
        // 5 + 6 = 11 doubles, draws a ten: 21 vs 9 + 8 = 17 (the dealer stands)
        Hand h = rigged("D", 5, 9, 6, 8, 10);
        assertEquals(BlackjackRound.Result.WIN, h.result());
        assertTrue(h.doubled());
        assertEquals(3, h.player().size());
        assertEquals(2, h.dealer().size());
        assertPays(h, 4, 4, 4, 8, 8, 12, 12);
    }

    @Test
    void doubledLossLosesTwiceTheBaseStake() {
        // 6 + 4 = 10 doubles, draws a 2: 12 vs 10 + 9 = 19
        Hand h = rigged("D", 6, 10, 4, 9, 2);
        assertEquals(BlackjackRound.Result.LOSE, h.result());
        assertTrue(h.doubled());
        assertPays(h, 0, 0, 0, 0, 0, 0, 0);
    }

    @Test
    void dealerStandsOnSoft17() {
        // dealer A + 6 = soft 17 stands (peek with the ace, no natural); player 19 wins
        Hand h = rigged("S", 10, 1, 9, 6);
        assertEquals(BlackjackRound.Result.WIN, h.result());
        assertTrue(h.peeked());
        assertEquals(2, h.dealer().size());
        assertEquals(17, BlackjackCards.total(h.dealer()));
    }

    @Test
    void noDoubleAfterAHit() {
        BlackjackRound round = new BlackjackRound(BlackjackShoe.rigged(new Random(1), card(2), card(7), card(3),
                card(10), card(4)), QUIET);
        int turns = 0;
        for (int guard = 0; !round.isOver(); guard++) {
            assertTrue(guard < 10_000);
            round.tick();
            if (round.phase() == BlackjackRound.Phase.PLAYER_TURN) {
                if (turns++ == 0) {
                    assertTrue(round.canDouble());
                    assertTrue(round.hit());
                } else {
                    assertFalse(round.canDouble());
                    assertFalse(round.doubleDown());
                    assertTrue(round.stand());
                }
            }
        }
        assertEquals(2, turns);
        assertEquals(BlackjackRound.Result.LOSE, round.result()); // 9 vs 17
        assertFalse(round.doubled());
    }
}
