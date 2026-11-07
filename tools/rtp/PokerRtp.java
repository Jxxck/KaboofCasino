/*
 * PokerRtp: return to player (RTP) of KaboofCasino's poker (Casino Hold'em against the house dealer), a 1:1 port of
 * MineVegas Casino 1.1.0 (fr.minevegas.casino.PokerRound, PokerHands, PokerBias, BlackjackShoe, BlackjackCards,
 * PokerTableBlockEntity, CasinoExchange, FairPayout). A single-file program, independent of the plugin code: rules,
 * hand evaluator, strategy, shoe and shuffle are reimplemented here from the mod's source. The plugin's
 * PokerRtpSimulationTest checks the shipped classes against the numbers below.
 *
 * RULES (mod source; the plugin ships verbatim ports)
 *   One deck, reshuffled for every round: PokerRound's constructor shuffles because needsShuffle() is size < 60
 *   (BlackjackShoe; the shoe's own constructor shuffles once more when a table first deals). Fisher-Yates with
 *   java.util.Random (i = 51..1, j = nextInt(i + 1)), cards drawn from the end. Deal: player, dealer, player, dealer,
 *   flop x 3; DECISION: CALL (2 x ante) or FOLD (also the 30 s timeout); after a call the turn and the river.
 *   Showdown: PokerHands.score of 2 + 5 cards (exactly the standard poker ranking, see "check"; suits never break
 *   ties). Dealer below a pair of 4s (score < 1310720): NO_QUALIFY, the ante pays k:1 and the call pushes; otherwise
 *   higher wins (ante k:1 + call 1:1), equal pushes (refund), lower loses ante and call. Ante paytable k: royal 100,
 *   straight flush 20 (A-2-3-4-5 suited too), quads 10, full house 3, flush 2, straight or less 1. Payout per stake
 *   (PokerTableBlockEntity.decided): the stake is 3A after the call; WIN / NO_QUALIFY pay CasinoExchange.payout(3A,
 *   thirds, 3, roll) with thirds = 5 + k (WIN) or 3 + k (NO_QUALIFY); PUSH refunds the 3A (CasinoExchange.payout
 *   with refund = true, not the thirds); LOSE and FOLD pay nothing.
 *   Strategy: the mod's own PokerBias.simpleCall (hole cards + flop): call with a pair or better among the 5 cards
 *   (a pair on the flop alone counts), a 4-flush, 4 of 5 consecutive ranks (inside draws, ace also low) or a T, J, Q,
 *   K or A in the hole; fold otherwise.
 *
 * QUANTITIES (ante = 1 item; a hand stakes 1 item (fold) or 3 (call))
 *   RTP per item staked = E[items back] / E[items staked] (the mod's RETURN column and games.md's "RTP")
 *   net per ante = E[items back - items staked] per ante; house edge = -net (the usual Casino Hold'em figure);
 *   RTP per ante = 1 + net per ante
 *
 * METHOD
 *   a) exact: every deal (player hole H, dealer hole D, flop F, turn + river T) is equally likely. For each of the
 *      2,598,960 boards B = F + T: score all C(47,2) = 1081 holes, sort them, and count for every player hole the
 *      990 disjoint dealer holes that do not qualify / qualify below / tie / beat it (the counts over all holes minus
 *      the holes sharing a card with H, by inclusion-exclusion along the sorted order). Each of the 10 splits of B
 *      into flop and turn/river is a decision point (H, F) with its own call/fold; the integer sum of the call
 *      outcome over the 990 dealer holes is added into a table EV[F][H], which after all boards holds the call EV of
 *      every decision point summed over its 1081 x 990 completions. Pass 1 counts the outcome classes of simpleCall
 *      and always-call and fills the table; the table gives the optimal strategy (call iff call EV >= -1 ante) and
 *      pass 2 counts its outcome classes. Integers over all 27,813,810,024,000 equally likely deals: exact rationals,
 *      no sampling, no suit-isomorphism shortcut.
 *   b) Monte Carlo of the exact game path: java.util.Random's LCG, the shoe's constructor shuffle, then per hand the
 *      full 51-step Fisher-Yates and the deal order above, fresh shuffle every hand. Fisher-Yates fixes position i at
 *      step i, so only the 9 dealt positions (51..43) are swapped; the other 42 steps still draw from the generator
 *      (the same calls, rejection included, without the division). 2 threads with seeds 1 and 2 and a fixed hand
 *      count each: deterministic. Standard errors: multinomial over the 23 outcome classes (ratio estimator for the
 *      RTP per item), cross-checked with batch means over 2^22-hand batches.
 *   c) rounding: FairPayout.share(3A x thirds, 3, roll) for every reachable thirds value, A = 1..10000, all rolls.
 *   replay: one shoe seeded like the plugin test (new java.util.Random(seed), 1 deck), literal shoe, literal
 *      evaluator, literal simpleCall: the exact class counts, which PokerRtpSimulationTest must reproduce hand for
 *      hand through PokerRound + BlackjackShoe.
 *   check: the fast evaluator equals a literal transcription of PokerHands.score on all 133,784,560 seven-card sets
 *      (category counts = the standard ones); the five-card scores have the standard category counts and 7462
 *      distinct values, and a seven-card score is the best of its 21 five-card subsets (2 M random hands), so the mod
 *      ranks exactly like standard poker; the dealer counts of a) equal brute force on 300 random boards; b)'s
 *      shuffle equals the literal shoe for 3 M rounds (dealt cards and generator state) and on crafted generator
 *      states at every rejection edge. Built into "exact": the fast simpleCall equals the literal one on all
 *      25,989,600 decision points.
 *
 * RUN (from the project root; JAVA = /usr/lib/jvm/temurin-25-jdk-amd64/bin/java)
 *   systemd-run --scope -q -p MemoryMax=3G -p CPUWeight=20 -- choom -n 1000 -- nice -n 15 \
 *       $JAVA -Xmx2500m tools/rtp/PokerRtp.java exact 2              # a: 2 threads, 2 x 117 MB EV tables (4.5 min)
 *   ... $JAVA tools/rtp/PokerRtp.java mc 4000000000 2 1              # b: hands per thread, threads, seed (17 min)
 *   ... $JAVA tools/rtp/PokerRtp.java replay 20261107 10000000       # golden counts of the plugin test (6 s)
 *   ... $JAVA tools/rtp/PokerRtp.java rounding                       # c (1 s)
 *   ... $JAVA -Xmx2g tools/rtp/PokerRtp.java check 2                 # the checks (20 s)
 *
 * RESULTS (2026-11-07, Temurin 25, i7-6700; exact 268 s, check 18 s)
 *   a) exact (27,813,810,024,000 deals)  simpleCall          optimal (EV >= -1)  always call
 *      RTP per item staked              0.984468006311      0.991826524446      0.973667855904
 *      net per ante                     -0.042726823602     -0.021576458362     -0.078996432288
 *      (house edge per ante)            (4.2727 %)          (2.1576 %)          (7.8996 %)
 *      RTP per ante                     0.957273176398      0.978423541638      0.921003567712
 *      items staked per ante            2.750891126         2.639814387         3
 *      call rate                        0.875445563         0.819907194         1
 *      FOLD                             0.124554437         0.180092806         0
 *      NO_QUALIFY                       0.208641268         0.205544673         0.242228401
 *      WIN                              0.282503824         0.272147676         0.292781137
 *      PUSH                             0.029172638         0.024397841         0.034245842
 *      LOSE                             0.355127833         0.317817003         0.430744620
 *      player wins (WIN + NO_QUALIFY)   0.491145092         0.477692349         0.535009537
 *      Per paytable line (simpleCall): NO_QUALIFY with a flush 0.005974, full house 0.000885, quads 0.0000328,
 *      straight flush 0.0000383, royal 0.0000043; WIN with a flush 0.018303, full house 0.021698, quads 0.001492,
 *      straight flush 0.000217, royal 0.0000265 (all 23 classes: the "java:" lines of "exact", the test's
 *      EXACT_COUNTS). A WIN with high card is impossible (a qualified dealer has a pair of 4s or better).
 *      The call EV of a decision point ranges from -2.1424 to +101.68 antes; 36 of the 25,989,600 points have a
 *      call EV of exactly -1 (the optimal strategy calls them; per ante it makes no difference).
 *      simpleCall against the optimal strategy: it calls at 1,871,916 points with a call EV below -1 (cost 0.019445
 *      antes per hand) and folds at 428,472 points with an EV above -1 (cost 0.001705): 2.115 points of house edge.
 *   b) Monte Carlo, 8e9 hands (2 x 4e9, seeds 1 and 2; 989 s on 2 threads, 8.1 M hands/s):
 *        RTP per item staked 0.98447715 +- 0.0000109 (batch means over 1906 x 2^22 hands: +- 0.0000108)
 *        net per ante -0.04270151 +- 0.0000300, RTP per ante 0.95729849 +- 0.0000300 (per-hand SD 2.6855 antes)
 *        FOLD 0.12455949, NO_QUALIFY 0.20863518, WIN 0.28250740, PUSH 0.02917409, LOSE 0.35512383 (SE <= 0.0000054),
 *        player wins 0.49114258 +- 0.0000056, call rate 0.87544051 +- 0.0000037.
 *        Against a): RTP per item and net per ante both +0.84 SE; the 21 possible classes chi^2 = 28.3 (20 dof,
 *        p = 0.1), the largest deviation NO_QUALIFY with high card at -2.4 SE.
 *   c) FairPayout: share(3A x thirds, 3, roll) == A x thirds for A = 1..10000, thirds in {0, 3, 4, 5, 6, 7, 8, 13,
 *      15, 23, 25, 103, 105} (every value payoutThirds can return) and rolls -3..5: 1,170,000 checks, all exact.
 *      WIN / NO_QUALIFY / PUSH only happen after the call, when the stake is 3A, so the roll never matters; a fold
 *      pays 0. The items back are A x a class constant, so the RTP (per item and per ante) is the same for every
 *      ante, odd or even, and for any mix of stakes (each stake pays alone). Odd antes have no effect.
 *   replay 20261107 10000000 (5.7 s): RTP per item 0.98444588 +- 0.00030877, net per ante -0.04278800 +-
 *      0.00084894 (chi^2 of the classes against a) 16.1, 20 dof); the class counts are REPLAY_COUNTS of
 *      PokerRtpSimulationTest, which reproduces them through PokerRound + BlackjackShoe ticked one at a time
 *      (10 M hands, 3.45e9 ticks, 94 s in the capped scope).
 *
 *   comparison                         this tool              games.md (research)        the mod / published
 *   simpleCall RTP per item staked     0.984468 (exact)       0.98482 +- 0.00035 (8 M)   RETURN 98.2 % (40,000 hands)
 *                                      0.984477 +- 0.000011 (b, 8e9)
 *   simpleCall net per ante            -0.042727              -0.04176 +- 0.00095        -
 *   simpleCall player wins             0.491145               0.4913                     WON 49.1 %
 *   simpleCall call rate               0.875446               0.87516                    -
 *   always call RTP per item           0.973668               0.97427 (4 M)              -
 *   always call net per ante           -0.078996              -0.07719 +- 0.00141        -
 *   optimal house edge per ante        0.021576               -                          about 2.16 % (published)
 *   optimal RTP per item staked        0.991827               about 99.2 % (from 2.16)   -
 *   The research values are 1.0, 1.0 and 1.3 standard errors off the exact ones; the mod's RETURN 98.2 % is a
 *   40,000-hand estimate (SE about 0.49 points per item at that size), 0.25 points below the exact 98.447 %; WON
 *   49.1 % is exact to its digits. The optimal strategy's 2.1576 % confirms that these are the standard Casino
 *   Hold'em rules (dealer qualifies with 4s, 100/20/10/3/2/1).
 */

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public final class PokerRtp {

    // ---- cards and hand values (BlackjackCards: rank = c % 13 with 0 = ace, suit = c / 13; PokerHands.value) ------

    static int value(int card) {
        int rank = card % 13;
        return rank == 0 ? 14 : rank + 1;
    }

    static int suit(int card) {
        return card / 13;
    }

    /** PokerHands.QUALIFIES: 1 << 20 | 4 << 16, any pair of 4s or better. */
    static final int QUALIFIES = 1310720;
    /** PokerHands.antePays by category: royal 100, straight flush 20, quads 10, full house 3, flush 2, else 1. */
    static final int[] ANTE_PAYS = {1, 1, 1, 1, 1, 2, 3, 10, 20, 100};
    static final String[] CATEGORY = {"high card", "pair", "two pair", "three of a kind", "straight", "flush",
            "full house", "four of a kind", "straight flush", "royal flush"};

    // ---- outcome classes: result x the player's hand category (the ante pays by category) ----------------------

    static final int FOLD = 0, PUSH = 1, LOSE = 2, NOQ = 3, WIN = 13, CLASSES = 23;
    /** Items back per class for an ante of 1 (thirds of the 3-item called stake = items; fold: nothing back). */
    static final int[] BACK = new int[CLASSES];
    /** Items staked per class: the ante, plus 2 x ante after a call. */
    static final int[] STAKED = new int[CLASSES];
    static final String[] NAMES = new String[CLASSES];

    static {
        BACK[FOLD] = 0;
        STAKED[FOLD] = 1;
        NAMES[FOLD] = "FOLD";
        BACK[PUSH] = 3;
        STAKED[PUSH] = 3;
        NAMES[PUSH] = "PUSH";
        BACK[LOSE] = 0;
        STAKED[LOSE] = 3;
        NAMES[LOSE] = "LOSE";
        for (int c = 0; c < 10; c++) {
            BACK[NOQ + c] = 3 + ANTE_PAYS[c]; // PokerRound.payoutThirds: NO_QUALIFY 3 + k
            STAKED[NOQ + c] = 3;
            NAMES[NOQ + c] = "NO_QUALIFY " + CATEGORY[c];
            BACK[WIN + c] = 5 + ANTE_PAYS[c]; // WIN 5 + k
            STAKED[WIN + c] = 3;
            NAMES[WIN + c] = "WIN " + CATEGORY[c];
        }
    }

    // ---- the mod's evaluator, transcribed literally (PokerHands.score / highest / straightHigh) -------------------

    static int highestLiteral(int mask, int n) {
        int out = 0;
        int found = 0;
        for (int v = 14; v >= 2 && found < n; v--) {
            if ((mask & 1 << v) != 0) {
                out = out << 4 | v;
                found++;
            }
        }
        return out << 4 * (n - found);
    }

    static int straightHighLiteral(int mask) {
        if ((mask & 16384) != 0) {
            mask |= 2;
        }
        for (int high = 14; high >= 5; high--) {
            if ((mask >> high - 4 & 31) == 31) {
                return high;
            }
        }
        return 0;
    }

    static int scoreLiteral(int[] cards, int n) {
        int[] count = new int[15];
        int[] suitCount = new int[4];
        int[] suitMask = new int[4];
        int mask = 0;
        int total = 0;
        for (int i = 0; i < n; i++) {
            int card = cards[i];
            if (card >= 0 && card < 52) {
                int v = value(card);
                int s = suit(card);
                count[v]++;
                suitCount[s]++;
                suitMask[s] |= 1 << v;
                mask |= 1 << v;
                total++;
            }
        }
        if (total >= 5) {
            for (int s = 0; s < 4; s++) {
                if (suitCount[s] >= 5) {
                    int high = straightHighLiteral(suitMask[s]);
                    if (high > 0) {
                        return (high == 14 ? 9 : 8) << 20 | high << 16;
                    }
                }
            }
        }
        int quads = 0;
        int trips = 0;
        int secondTrips = 0;
        int pair = 0;
        int secondPair = 0;
        for (int v = 14; v >= 2; v--) {
            switch (count[v]) {
                case 2 -> {
                    if (pair == 0) {
                        pair = v;
                    } else if (secondPair == 0) {
                        secondPair = v;
                    }
                }
                case 3 -> {
                    if (trips == 0) {
                        trips = v;
                    } else if (secondTrips == 0) {
                        secondTrips = v;
                    }
                }
                case 4 -> quads = v;
                default -> {
                }
            }
        }
        if (quads > 0) {
            return 7 << 20 | quads << 16 | highestLiteral(mask & ~(1 << quads), 1) << 12;
        } else if (trips <= 0 || secondTrips <= 0 && pair <= 0) {
            if (total >= 5) {
                for (int s = 0; s < 4; s++) {
                    if (suitCount[s] >= 5) {
                        return 5 << 20 | highestLiteral(suitMask[s], 5);
                    }
                }
                int high = straightHighLiteral(mask);
                if (high > 0) {
                    return 4 << 20 | high << 16;
                }
            }
            if (trips > 0) {
                return 3 << 20 | trips << 16 | highestLiteral(mask & ~(1 << trips), 2) << 8;
            } else if (pair > 0 && secondPair > 0) {
                return 2 << 20 | pair << 16 | secondPair << 12
                        | highestLiteral(mask & ~(1 << pair) & ~(1 << secondPair), 1) << 8;
            } else {
                return pair > 0 ? 1 << 20 | pair << 16 | highestLiteral(mask & ~(1 << pair), 3) << 4
                        : highestLiteral(mask, 5);
            }
        } else {
            return 6 << 20 | trips << 16 | Math.max(secondTrips, pair) << 12;
        }
    }

    /** PokerBias.simpleCall, transcribed literally: call with a pair or better, a 4-flush, 4 of 5 ranks, a T+. */
    static boolean simpleCallLiteral(int[] own, int[] flop) {
        int[] cards = new int[5];
        int n = 0;
        for (int c : own) {
            cards[n++] = c;
        }
        for (int c : flop) {
            cards[n++] = c;
        }
        if (scoreLiteral(cards, n) >>> 20 >= 1) {
            return true;
        }
        int[] suits = new int[4];
        int mask = 0;
        for (int i = 0; i < n; i++) {
            suits[suit(cards[i])]++;
            mask |= 1 << value(cards[i]);
        }
        for (int s : suits) {
            if (s >= 4) {
                return true;
            }
        }
        if ((mask & 16384) != 0) {
            mask |= 2;
        }
        for (int low = 1; low <= 10; low++) {
            if (Integer.bitCount(mask >> low & 31) == 4) {
                return true;
            }
        }
        for (int c : own) {
            if (value(c) >= 10) {
                return true;
            }
        }
        return false;
    }

    // ---- fast evaluator: the same ints from four suit masks (bits 2..14 = values), table lookups ---------------

    static final int[] STRAIGHT = new int[1 << 13];
    static final int[] TOP1 = new int[1 << 13];
    static final int[] TOP2 = new int[1 << 13];
    static final int[] TOP3 = new int[1 << 13];
    static final int[] TOP5 = new int[1 << 13];
    /** Some window of 5 consecutive values (ace also low) holds exactly 4 of the mask (simpleCall's draw rule). */
    static final boolean[] DRAW4 = new boolean[1 << 13];

    static {
        for (int m = 0; m < 1 << 13; m++) {
            int mask = m << 2;
            STRAIGHT[m] = straightHighLiteral(mask);
            TOP1[m] = highestLiteral(mask, 1);
            TOP2[m] = highestLiteral(mask, 2);
            TOP3[m] = highestLiteral(mask, 3);
            TOP5[m] = highestLiteral(mask, 5);
            int low = (mask & 16384) != 0 ? mask | 2 : mask;
            for (int w = 1; w <= 10; w++) {
                if (Integer.bitCount(low >> w & 31) == 4) {
                    DRAW4[m] = true;
                }
            }
        }
    }

    static int top(int bits) {
        return 31 - Integer.numberOfLeadingZeros(bits);
    }

    /** PokerHands.score of 5 to 7 distinct cards given as suit masks; equal to scoreLiteral (checked on all 7-sets). */
    static int score(int s0, int s1, int s2, int s3) {
        int all = s0 | s1 | s2 | s3;
        int flush = Integer.bitCount(s0) >= 5 ? s0 : Integer.bitCount(s1) >= 5 ? s1
                : Integer.bitCount(s2) >= 5 ? s2 : Integer.bitCount(s3) >= 5 ? s3 : 0;
        if (flush != 0) {
            int high = STRAIGHT[flush >>> 2];
            if (high > 0) {
                return (high == 14 ? 9 : 8) << 20 | high << 16;
            }
        }
        int a01 = s0 & s1;
        int a23 = s2 & s3;
        int o01 = s0 | s1;
        int o23 = s2 | s3;
        int four = a01 & a23;
        if (four != 0) {
            int quads = Integer.numberOfTrailingZeros(four); // the mod keeps the last (lowest) count-4 value
            return 7 << 20 | quads << 16 | TOP1[(all & ~(1 << quads)) >>> 2] << 12;
        }
        int atLeast3 = a01 & o23 | a23 & o01;
        int three = atLeast3; // no value has 4 here
        int two = (a01 | a23 | o01 & o23) & ~atLeast3;
        int trips = three == 0 ? 0 : top(three);
        int restT = three & ~(1 << trips);
        int secondTrips = restT == 0 ? 0 : top(restT);
        int pair = two == 0 ? 0 : top(two);
        int restP = two & ~(1 << pair);
        int secondPair = restP == 0 ? 0 : top(restP);
        if (trips > 0 && (secondTrips > 0 || pair > 0)) {
            return 6 << 20 | trips << 16 | Math.max(secondTrips, pair) << 12;
        }
        if (flush != 0) {
            return 5 << 20 | TOP5[flush >>> 2];
        }
        int high = STRAIGHT[all >>> 2];
        if (high > 0) {
            return 4 << 20 | high << 16;
        }
        if (trips > 0) {
            return 3 << 20 | trips << 16 | TOP2[(all & ~(1 << trips)) >>> 2] << 8;
        }
        if (secondPair > 0) {
            return 2 << 20 | pair << 16 | secondPair << 12 | TOP1[(all & ~(1 << pair) & ~(1 << secondPair)) >>> 2] << 8;
        }
        if (pair > 0) {
            return 1 << 20 | pair << 16 | TOP3[(all & ~(1 << pair)) >>> 2] << 4;
        }
        return TOP5[all >>> 2];
    }

    /** simpleCall from the 5 cards' suit masks and the two hole cards (equal to the literal one on all (H, F)). */
    static boolean simpleCall(int s0, int s1, int s2, int s3, int hole1, int hole2) {
        int all = (s0 | s1 | s2 | s3) >>> 2;
        // a pair or better among the 5 cards: two equal values, a straight or a flush (both also caught below)
        return Integer.bitCount(all) < 5 || STRAIGHT[all] > 0 || Integer.bitCount(s0) >= 4
                || Integer.bitCount(s1) >= 4 || Integer.bitCount(s2) >= 4 || Integer.bitCount(s3) >= 4 || DRAW4[all]
                || value(hole1) >= 10 || value(hole2) >= 10;
    }

    static final int[] SUIT = new int[52];
    static final int[] BIT = new int[52];

    static {
        for (int c = 0; c < 52; c++) {
            SUIT[c] = suit(c);
            BIT[c] = 1 << value(c);
        }
    }

    /** PokerRound.resolve + the category of the player's hand: the outcome class of a called hand. */
    static int resolve(int playerScore, int dealerScore) {
        int cat = playerScore >>> 20;
        if (dealerScore < QUALIFIES) {
            return NOQ + cat;
        } else if (playerScore > dealerScore) {
            return WIN + cat;
        } else if (playerScore < dealerScore) {
            return LOSE;
        }
        return PUSH;
    }

    // ---- combinatorial indices -------------------------------------------------------------------------------

    static final int HANDS = 1326; // C(52, 2)
    static final int FLOPS = 22100; // C(52, 3)
    static final int[] HAND_A = new int[HANDS];
    static final int[] HAND_B = new int[HANDS];

    static {
        for (int b = 1; b < 52; b++) {
            for (int a = 0; a < b; a++) {
                HAND_A[handIndex(a, b)] = a;
                HAND_B[handIndex(a, b)] = b;
            }
        }
    }

    /** Colex index of a < b. */
    static int handIndex(int a, int b) {
        return b * (b - 1) / 2 + a;
    }

    /** Colex index of a < b < c. */
    static int flopIndex(int a, int b, int c) {
        return c * (c - 1) * (c - 2) / 6 + b * (b - 1) / 2 + a;
    }

    // ---- a) exact enumeration --------------------------------------------------------------------------------
    //
    // Every deal (player hole H, dealer hole D, flop F, turn + river T; all disjoint) is equally likely. Group by the
    // board B = F + T (2,598,960 sets): for a board, score all C(47,2) = 1081 holes, sort them, and for each player
    // hole H count the 990 dealer holes disjoint from H that do not qualify / qualify below H / tie / beat H (counts
    // over all holes minus the holes that share a card with H, by inclusion-exclusion over the sorted order). Each of
    // the 10 ways to split B into flop (3) and turn/river (2) is one decision point (H, F) with its own call/fold.
    // The integer sum of the call outcome over the 990 dealer holes is added into EV[F][H]; after all boards
    // EV[F][H] holds the call outcome summed over all 1081 x 990 completions of (H, F): the exact call EV.

    static final int COMPLETIONS = 1081 * 990; // turn/river pairs x dealer holes for one (H, F)
    static final int DEALER_HOLES = 990; // C(45, 2)
    static final int[][] SPLITS = {{0, 1, 2}, {0, 1, 3}, {0, 1, 4}, {0, 2, 3}, {0, 2, 4}, {0, 3, 4}, {1, 2, 3},
            {1, 2, 4}, {1, 3, 4}, {2, 3, 4}};

    static final class Enumerator implements Runnable {
        final AtomicInteger next;
        final byte[] strategy;
        final int[] ev; // null: do not accumulate the EV table
        final long[] counts = new long[CLASSES];
        final long[] always = new long[CLASSES];
        long boards;

        private final int[] rem = new int[47];
        private final int[] ha = new int[1081];
        private final int[] hb = new int[1081];
        private final int[] global = new int[1081];
        private final int[] sc = new int[1081];
        private final long[] keys = new long[1081];
        private final int[] cnt = new int[52];
        private final int[] cntQ = new int[52];
        private final int[] grp = new int[52];
        private final int[] less = new int[1081];
        private final int[] eq = new int[1081];
        private final int[] noq = new int[1081];
        private final int[] win = new int[1081];
        private final int[] push = new int[1081];
        private final int[] lose = new int[1081];
        private final int[] sum = new int[1081];
        private final int[] calls = new int[1081];
        private final int[] bc = new int[5];

        Enumerator(AtomicInteger next, byte[] strategy, int[] ev) {
            this.next = next;
            this.strategy = strategy;
            this.ev = ev;
        }

        @Override
        public void run() {
            // work unit = the two lowest board cards (colex order: the largest units first)
            for (int unit; (unit = next.getAndIncrement()) < HANDS; ) {
                int c0 = HAND_A[unit];
                int c1 = HAND_B[unit];
                for (int c2 = c1 + 1; c2 < 52; c2++) {
                    for (int c3 = c2 + 1; c3 < 52; c3++) {
                        for (int c4 = c3 + 1; c4 < 52; c4++) {
                            board(c0, c1, c2, c3, c4);
                        }
                    }
                }
            }
        }

        void board(int c0, int c1, int c2, int c3, int c4) {
            boards++;
            bc[0] = c0;
            bc[1] = c1;
            bc[2] = c2;
            bc[3] = c3;
            bc[4] = c4;
            int[] bm = new int[4];
            long used = 0;
            for (int c : bc) {
                bm[SUIT[c]] |= BIT[c];
                used |= 1L << c;
            }
            int r = 0;
            for (int c = 0; c < 52; c++) {
                if ((used >>> c & 1) == 0) {
                    rem[r++] = c;
                }
            }
            // 1. score every hole of the remaining 47 cards
            int n = 0;
            for (int i = 0; i < 47; i++) {
                int a = rem[i];
                int a0 = bm[0], a1 = bm[1], a2 = bm[2], a3 = bm[3];
                switch (SUIT[a]) {
                    case 0 -> a0 |= BIT[a];
                    case 1 -> a1 |= BIT[a];
                    case 2 -> a2 |= BIT[a];
                    default -> a3 |= BIT[a];
                }
                for (int j = i + 1; j < 47; j++) {
                    int b = rem[j];
                    int x0 = a0, x1 = a1, x2 = a2, x3 = a3;
                    switch (SUIT[b]) {
                        case 0 -> x0 |= BIT[b];
                        case 1 -> x1 |= BIT[b];
                        case 2 -> x2 |= BIT[b];
                        default -> x3 |= BIT[b];
                    }
                    int s = score(x0, x1, x2, x3);
                    ha[n] = a;
                    hb[n] = b;
                    global[n] = handIndex(a, b);
                    sc[n] = s;
                    keys[n] = (long) s << 11 | n;
                    n++;
                }
            }
            // 2. sweep the holes in score order; per card the number of holes already passed (score below)
            Arrays.sort(keys, 0, n);
            Arrays.fill(cnt, 0);
            int processed = 0;
            int processedQ = -1;
            for (int pos = 0; pos < n; ) {
                int s = (int) (keys[pos] >>> 11);
                int end = pos + 1;
                while (end < n && (int) (keys[end] >>> 11) == s) {
                    end++;
                }
                if (processedQ < 0 && s >= QUALIFIES) {
                    processedQ = processed;
                    System.arraycopy(cnt, 0, cntQ, 0, 52);
                }
                for (int k = pos; k < end; k++) {
                    int i = (int) keys[k] & 2047;
                    grp[ha[i]]++;
                    grp[hb[i]]++;
                }
                int g = end - pos;
                for (int k = pos; k < end; k++) {
                    int i = (int) keys[k] & 2047;
                    less[i] = processed - cnt[ha[i]] - cnt[hb[i]]; // disjoint holes scoring lower
                    eq[i] = g - grp[ha[i]] - grp[hb[i]] + 1; // disjoint holes scoring the same (H itself drops out)
                }
                for (int k = pos; k < end; k++) {
                    int i = (int) keys[k] & 2047;
                    cnt[ha[i]]++;
                    cnt[hb[i]]++;
                    grp[ha[i]] = 0;
                    grp[hb[i]] = 0;
                }
                processed += g;
                pos = end;
            }
            if (processedQ < 0) {
                processedQ = processed;
                System.arraycopy(cnt, 0, cntQ, 0, 52);
            }
            // 3. the call outcome against the 990 dealer holes
            for (int i = 0; i < n; i++) {
                int s = sc[i];
                int nq = processedQ - cntQ[ha[i]] - cntQ[hb[i]] + (s < QUALIFIES ? 1 : 0);
                int k = ANTE_PAYS[s >>> 20];
                int w;
                int p;
                int l;
                if (s < QUALIFIES) {
                    w = 0;
                    p = 0;
                    l = DEALER_HOLES - nq;
                } else {
                    w = less[i] - nq;
                    p = eq[i];
                    l = DEALER_HOLES - less[i] - eq[i];
                }
                noq[i] = nq;
                win[i] = w;
                push[i] = p;
                lose[i] = l;
                sum[i] = nq * k + w * (k + 2) - 3 * l;
                calls[i] = 0;
            }
            // 4. the 10 decision points of this board
            for (int[] split : SPLITS) {
                int base = flopIndex(bc[split[0]], bc[split[1]], bc[split[2]]) * HANDS;
                if (ev != null) {
                    for (int i = 0; i < n; i++) {
                        ev[base + global[i]] += sum[i];
                    }
                }
                for (int i = 0; i < n; i++) {
                    calls[i] += strategy[base + global[i]];
                }
            }
            for (int i = 0; i < n; i++) {
                int cat = sc[i] >>> 20;
                long m = calls[i];
                counts[FOLD] += (10 - m) * DEALER_HOLES;
                counts[NOQ + cat] += m * noq[i];
                counts[WIN + cat] += m * win[i];
                counts[PUSH] += m * push[i];
                counts[LOSE] += m * lose[i];
                always[NOQ + cat] += 10L * noq[i];
                always[WIN + cat] += 10L * win[i];
                always[PUSH] += 10L * push[i];
                always[LOSE] += 10L * lose[i];
            }
        }
    }

    static final MathContext MC = new MathContext(40);

    static BigDecimal frac(BigInteger num, BigInteger den) {
        return new BigDecimal(num).divide(new BigDecimal(den), MC);
    }

    static String fmt(BigDecimal x, int digits) {
        return x.setScale(digits, RoundingMode.HALF_EVEN).toPlainString();
    }

    /** The strategy-independent summary of a class-count vector (exact rationals). */
    static void reportExact(String title, long[] counts) {
        BigInteger total = BigInteger.ZERO;
        BigInteger back = BigInteger.ZERO;
        BigInteger staked = BigInteger.ZERO;
        long won = 0;
        long calls = 0;
        for (int c = 0; c < CLASSES; c++) {
            BigInteger n = BigInteger.valueOf(counts[c]);
            total = total.add(n);
            back = back.add(n.multiply(BigInteger.valueOf(BACK[c])));
            staked = staked.add(n.multiply(BigInteger.valueOf(STAKED[c])));
            if (c >= NOQ) {
                won += counts[c];
            }
            if (c != FOLD) {
                calls += counts[c];
            }
        }
        BigInteger net = back.subtract(staked);
        System.out.printf(Locale.ROOT, "%s (%,d equally likely deals)%n", title, total);
        System.out.printf(Locale.ROOT, "  RTP per item staked   %s   (= items back / (ante + call))%n",
                fmt(frac(back, staked), 12));
        System.out.printf(Locale.ROOT, "  net per ante          %s   (house edge per ante %s %%)%n",
                fmt(frac(net, total), 12), fmt(frac(net.negate(), total).movePointRight(2), 6));
        System.out.printf(Locale.ROOT, "  items back per ante   %s,  items staked per ante %s%n",
                fmt(frac(back, total), 12), fmt(frac(staked, total), 12));
        System.out.printf(Locale.ROOT, "  call rate %s, player wins (WIN + NO_QUALIFY) %s%n",
                fmt(frac(BigInteger.valueOf(calls), total), 9), fmt(frac(BigInteger.valueOf(won), total), 9));
        long noq = 0;
        long win = 0;
        for (int c = 0; c < 10; c++) {
            noq += counts[NOQ + c];
            win += counts[WIN + c];
        }
        System.out.printf(Locale.ROOT, "  fold %s  no-qualify %s  win %s  push %s  lose %s%n",
                fmt(frac(BigInteger.valueOf(counts[FOLD]), total), 9), fmt(frac(BigInteger.valueOf(noq), total), 9),
                fmt(frac(BigInteger.valueOf(win), total), 9), fmt(frac(BigInteger.valueOf(counts[PUSH]), total), 9),
                fmt(frac(BigInteger.valueOf(counts[LOSE]), total), 9));
        for (int c = 0; c < CLASSES; c++) {
            if (counts[c] != 0) {
                System.out.printf(Locale.ROOT, "    %-30s %22d  %s%n", NAMES[c], counts[c],
                        fmt(frac(BigInteger.valueOf(counts[c]), total), 12));
            }
        }
        StringBuilder java = new StringBuilder("  java: {");
        for (int c = 0; c < CLASSES; c++) {
            java.append(c == 0 ? "" : ", ").append(counts[c]).append('L');
        }
        System.out.println(java.append('}'));
    }

    static long[] runEnumeration(int threads, byte[] strategy, int[] ev0, int[] ev1, long[] alwaysOut)
            throws InterruptedException {
        AtomicInteger next = new AtomicInteger();
        Enumerator[] workers = new Enumerator[threads];
        Thread[] ts = new Thread[threads];
        for (int t = 0; t < threads; t++) {
            workers[t] = new Enumerator(next, strategy, t == 0 ? ev0 : ev1);
            ts[t] = new Thread(workers[t], "enum-" + t);
            ts[t].start();
        }
        for (Thread t : ts) {
            t.join();
        }
        long[] counts = new long[CLASSES];
        long boards = 0;
        for (Enumerator w : workers) {
            boards += w.boards;
            for (int c = 0; c < CLASSES; c++) {
                counts[c] += w.counts[c];
                alwaysOut[c] += w.always[c];
            }
        }
        if (boards != 2_598_960L) {
            throw new AssertionError("boards " + boards);
        }
        return counts;
    }

    static void exact(int threads) throws InterruptedException {
        if (threads > 2) {
            throw new IllegalArgumentException("at most 2 threads");
        }
        long t0 = System.nanoTime();
        // simpleCall for every decision point (H, F), literal transcription; the fast version must agree
        byte[] simple = new byte[FLOPS * HANDS];
        long simpleCalls = 0;
        long decisionPoints = 0;
        int[] own = new int[2];
        int[] flop = new int[3];
        for (int c = 2; c < 52; c++) {
            for (int b = 1; b < c; b++) {
                for (int a = 0; a < b; a++) {
                    int f = flopIndex(a, b, c);
                    flop[0] = a;
                    flop[1] = b;
                    flop[2] = c;
                    for (int h = 0; h < HANDS; h++) {
                        int x = HAND_A[h];
                        int y = HAND_B[h];
                        if (x == a || x == b || x == c || y == a || y == b || y == c) {
                            continue;
                        }
                        own[0] = x;
                        own[1] = y;
                        boolean literal = simpleCallLiteral(own, flop);
                        int[] m = new int[4];
                        for (int card : new int[] {a, b, c, x, y}) {
                            m[SUIT[card]] |= BIT[card];
                        }
                        if (literal != simpleCall(m[0], m[1], m[2], m[3], x, y)) {
                            throw new AssertionError("fast simpleCall differs at " + x + "," + y + " | " + a + ","
                                    + b + "," + c);
                        }
                        decisionPoints++;
                        if (literal) {
                            simple[f * HANDS + h] = 1;
                            simpleCalls++;
                        }
                    }
                }
            }
        }
        System.out.printf(Locale.ROOT, "decision points (H, F): %,d, simpleCall calls at %,d (fast = literal on all),"
                + " %.0f s%n", decisionPoints, simpleCalls, (System.nanoTime() - t0) / 1e9);

        // pass 1: simpleCall and always-call outcome counts, the exact call EV of every (H, F)
        int[] ev0 = new int[FLOPS * HANDS];
        int[] ev1 = threads > 1 ? new int[FLOPS * HANDS] : null;
        long[] always = new long[CLASSES];
        long t1 = System.nanoTime();
        long[] simpleCounts = runEnumeration(threads, simple, ev0, ev1 == null ? ev0 : ev1, always);
        System.out.printf(Locale.ROOT, "pass 1 (2,598,960 boards, %d threads): %.0f s%n", threads,
                (System.nanoTime() - t1) / 1e9);
        if (ev1 != null) {
            for (int i = 0; i < ev0.length; i++) {
                ev0[i] += ev1[i];
            }
            ev1 = null;
        }
        int[] ev = ev0;

        // the EV table: always, simpleCall and the optimal strategy (call iff call EV >= -1 ante)
        byte[] optimal = new byte[FLOPS * HANDS];
        long points = 0;
        long sumAll = 0;
        long sumSimple = 0;
        long nSimple = 0;
        long sumOpt = 0;
        long nOpt = 0;
        long ties = 0;
        long badCalls = 0;
        long badCallCost = 0;
        long badFolds = 0;
        long badFoldCost = 0;
        long minEv = Long.MAX_VALUE;
        long maxEv = Long.MIN_VALUE;
        for (int fc = 2; fc < 52; fc++) {
          for (int fb = 1; fb < fc; fb++) {
           for (int fa = 0; fa < fb; fa++) {
            int f = flopIndex(fa, fb, fc);
            for (int h = 0; h < HANDS; h++) {
                int i = f * HANDS + h;
                int x = HAND_A[h];
                int y = HAND_B[h];
                if (x == fa || x == fb || x == fc || y == fa || y == fb || y == fc) {
                    if (ev[i] != 0) {
                        throw new AssertionError("EV of an impossible (H, F)");
                    }
                    continue;
                }
                points++;
                long v = ev[i];
                minEv = Math.min(minEv, v);
                maxEv = Math.max(maxEv, v);
                sumAll += v;
                boolean callOpt = v >= -COMPLETIONS;
                if (v == -COMPLETIONS) {
                    ties++;
                }
                if (callOpt) {
                    optimal[i] = 1;
                    sumOpt += v;
                    nOpt++;
                } else {
                    sumOpt -= COMPLETIONS;
                }
                if (simple[i] == 1) {
                    sumSimple += v;
                    nSimple++;
                    if (!callOpt) {
                        badCalls++;
                        badCallCost += -COMPLETIONS - v;
                    }
                } else {
                    sumSimple -= COMPLETIONS;
                    if (callOpt && v > -COMPLETIONS) {
                        badFolds++;
                        badFoldCost += v + COMPLETIONS;
                    }
                }
            }
           }
          }
        }
        BigInteger den = BigInteger.valueOf(points).multiply(BigInteger.valueOf(COMPLETIONS));
        System.out.printf(Locale.ROOT, "%nEV table: %,d decision points, call EV from %s to %s antes, %d exact ties"
                        + " (EV = -1)%n", points,
                fmt(frac(BigInteger.valueOf(minEv), BigInteger.valueOf(COMPLETIONS)), 6),
                fmt(frac(BigInteger.valueOf(maxEv), BigInteger.valueOf(COMPLETIONS)), 6), ties);
        System.out.println(evLine("always call", sumAll, points, points, den));
        System.out.println(evLine("simpleCall", sumSimple, nSimple, points, den));
        System.out.println(evLine("optimal (call iff EV >= -1)", sumOpt, nOpt, points, den));
        System.out.printf(Locale.ROOT, "simpleCall vs optimal: calls %,d bad points (EV < -1), cost %s antes per hand;"
                        + " folds %,d good points (EV > -1), cost %s antes per hand%n", badCalls,
                fmt(frac(BigInteger.valueOf(badCallCost), den), 9), badFolds,
                fmt(frac(BigInteger.valueOf(badFoldCost), den), 9));

        System.out.println();
        reportExact("a) simpleCall (PokerBias.simpleCall), exact", simpleCounts);
        System.out.println();
        reportExact("a) always call, exact", always);

        // pass 2: outcome counts of the optimal strategy
        long t2 = System.nanoTime();
        long[] always2 = new long[CLASSES];
        long[] optCounts = runEnumeration(threads, optimal, null, null, always2);
        if (!Arrays.equals(always, always2)) {
            throw new AssertionError("always-call counts differ between the passes");
        }
        System.out.printf(Locale.ROOT, "%npass 2 (optimal strategy): %.0f s%n", (System.nanoTime() - t2) / 1e9);
        reportExact("a) optimal strategy (call iff call EV >= -1 ante), exact", optCounts);
        System.out.printf(Locale.ROOT, "%nexact: %.0f s in total%n", (System.nanoTime() - t0) / 1e9);
    }

    static String evLine(String name, long sum, long calls, long points, BigInteger den) {
        BigInteger net = BigInteger.valueOf(sum);
        BigInteger p = BigInteger.valueOf(points);
        // per hand: staked = 1 + 2 P(call); back = staked + net
        BigDecimal netPerAnte = frac(net, den);
        BigDecimal staked = BigDecimal.ONE.add(frac(BigInteger.valueOf(2 * calls), p));
        BigDecimal rtp = BigDecimal.ONE.add(netPerAnte.divide(staked, MC));
        return String.format(Locale.ROOT, "  %-30s call rate %s  net per ante %s  RTP per item staked %s", name,
                fmt(frac(BigInteger.valueOf(calls), p), 9), fmt(netPerAnte, 12), fmt(rtp, 12));
    }

    // ---- the mod's shoe: java.util.Random's LCG, Fisher-Yates, draw from the end ------------------------------

    /** java.util.Random's 48-bit LCG and its nextInt(bound) (same seed, same numbers), without the atomic. */
    static final class Lcg {
        long state;

        Lcg(long seed) {
            state = (seed ^ 0x5DEECE66DL) & ((1L << 48) - 1);
        }

        int next(int bits) {
            state = (state * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);
            return (int) (state >>> (48 - bits));
        }

        int nextInt(int bound) {
            int r = next(31);
            int m = bound - 1;
            if ((bound & m) == 0) {
                r = (int) ((bound * (long) r) >> 31);
            } else {
                for (int u = r; u - (r = u % bound) + m < 0; u = next(31)) {
                    // rejection, as java.util.Random
                }
            }
            return r;
        }

        /** nextInt(bound) whose value is not needed: the same generator steps, no division. */
        void skipInt(int bound) {
            long limit = LIMIT[bound];
            while (next(31) >= limit) {
                // rejected: nextInt draws again
            }
        }
    }

    /** nextInt(bound) rejects u >= floor(2^31 / bound) * bound; never for a power of two (limit 2^31). */
    static final long[] LIMIT = new long[53];

    static {
        for (int bound = 1; bound <= 52; bound++) {
            LIMIT[bound] = (bound & (bound - 1)) == 0 ? 1L << 31 : (1L << 31) / bound * bound;
        }
    }

    /** BlackjackShoe(random, 1) without bias, literally: shuffle in the constructor, shuffle(), draw(). */
    static final class LiteralShoe {
        final Lcg random;
        final int[] cards = new int[52];
        int size;

        LiteralShoe(Lcg random) {
            this.random = random;
            shuffle();
        }

        void shuffle() {
            for (int c = 0; c < 52; c++) {
                cards[c] = c;
            }
            size = 52;
            for (int i = size - 1; i > 0; i--) {
                int j = random.nextInt(i + 1);
                int swap = cards[i];
                cards[i] = cards[j];
                cards[j] = swap;
            }
        }

        boolean needsShuffle() {
            return size < 60;
        }

        int draw() {
            if (size == 0) {
                shuffle();
            }
            return cards[--size];
        }
    }

    /** One PokerRound with the simpleCall player, literally (mod order of draws, literal evaluator): its class. */
    static int literalRound(LiteralShoe shoe) {
        if (shoe.needsShuffle()) { // always: 52 < 60
            shoe.shuffle();
        }
        int p1 = shoe.draw();
        int d1 = shoe.draw();
        int p2 = shoe.draw();
        int d2 = shoe.draw();
        int f1 = shoe.draw();
        int f2 = shoe.draw();
        int f3 = shoe.draw();
        if (!simpleCallLiteral(new int[] {p1, p2}, new int[] {f1, f2, f3})) {
            return FOLD;
        }
        int t = shoe.draw();
        int r = shoe.draw();
        int ps = scoreLiteral(new int[] {p1, p2, f1, f2, f3, t, r}, 7);
        int ds = scoreLiteral(new int[] {d1, d2, f1, f2, f3, t, r}, 7);
        return resolve(ps, ds);
    }

    // ---- b) Monte Carlo of the exact game path --------------------------------------------------------------

    static final int BATCH = 1 << 22;

    static final class Sim implements Runnable {
        final long seed;
        final long hands;
        final long[] counts = new long[CLASSES];
        final long[] batchBack;
        final long[] batchStaked;
        int batches;

        Sim(long seed, long hands) {
            this.seed = seed;
            this.hands = hands;
            batchBack = new long[(int) (hands / BATCH) + 1];
            batchStaked = new long[(int) (hands / BATCH) + 1];
        }

        @Override
        public void run() {
            Lcg random = new Lcg(seed);
            // new BlackjackShoe(random, 1) shuffles once in its constructor
            for (int i = 51; i > 0; i--) {
                random.skipInt(i + 1);
            }
            int[] deck = new int[52];
            int[] m = new int[4];
            long back = 0;
            long staked = 0;
            int inBatch = 0;
            for (long h = 0; h < hands; h++) {
                // PokerRound(...): needsShuffle() (52 < 60) -> shuffle(). Fisher-Yates i = 51..1 fixes position i at
                // step i, so the 9 cards drawn from the end (positions 51..43) are final after the steps 51..43; the
                // other 42 steps only advance the generator (same calls, values not needed).
                for (int c = 0; c < 52; c++) {
                    deck[c] = c;
                }
                for (int i = 51; i >= 43; i--) {
                    int j = random.nextInt(i + 1);
                    int swap = deck[i];
                    deck[i] = deck[j];
                    deck[j] = swap;
                }
                for (int i = 42; i > 0; i--) {
                    random.skipInt(i + 1);
                }
                // deal order: player, dealer, player, dealer, flop x 3, (after a call) turn, river
                int p1 = deck[51];
                int d1 = deck[50];
                int p2 = deck[49];
                int d2 = deck[48];
                int f1 = deck[47];
                int f2 = deck[46];
                int f3 = deck[45];
                m[0] = 0;
                m[1] = 0;
                m[2] = 0;
                m[3] = 0;
                m[SUIT[f1]] |= BIT[f1];
                m[SUIT[f2]] |= BIT[f2];
                m[SUIT[f3]] |= BIT[f3];
                int b0 = m[0], b1 = m[1], b2 = m[2], b3 = m[3];
                m[SUIT[p1]] |= BIT[p1];
                m[SUIT[p2]] |= BIT[p2];
                int cls;
                if (!simpleCall(m[0], m[1], m[2], m[3], p1, p2)) {
                    cls = FOLD;
                } else {
                    m[0] = b0;
                    m[1] = b1;
                    m[2] = b2;
                    m[3] = b3;
                    m[SUIT[deck[44]]] |= BIT[deck[44]];
                    m[SUIT[deck[43]]] |= BIT[deck[43]];
                    int ps = scoreWith(m[0], m[1], m[2], m[3], p1, p2);
                    int ds = scoreWith(m[0], m[1], m[2], m[3], d1, d2);
                    cls = resolve(ps, ds);
                }
                counts[cls]++;
                back += BACK[cls];
                staked += STAKED[cls];
                if (++inBatch == BATCH) {
                    batchBack[batches] = back;
                    batchStaked[batches] = staked;
                    batches++;
                    back = 0;
                    staked = 0;
                    inBatch = 0;
                }
            }
        }
    }

    static int scoreWith(int b0, int b1, int b2, int b3, int x, int y) {
        int s0 = b0, s1 = b1, s2 = b2, s3 = b3;
        switch (SUIT[x]) {
            case 0 -> s0 |= BIT[x];
            case 1 -> s1 |= BIT[x];
            case 2 -> s2 |= BIT[x];
            default -> s3 |= BIT[x];
        }
        switch (SUIT[y]) {
            case 0 -> s0 |= BIT[y];
            case 1 -> s1 |= BIT[y];
            case 2 -> s2 |= BIT[y];
            default -> s3 |= BIT[y];
        }
        return score(s0, s1, s2, s3);
    }

    static void monteCarlo(long perThread, int threads, long baseSeed) throws InterruptedException {
        if (threads > 2) {
            throw new IllegalArgumentException("at most 2 threads");
        }
        Sim[] sims = new Sim[threads];
        Thread[] ts = new Thread[threads];
        long t0 = System.nanoTime();
        for (int k = 0; k < threads; k++) {
            sims[k] = new Sim(baseSeed + k, perThread);
            ts[k] = new Thread(sims[k], "mc-" + k);
            ts[k].start();
        }
        for (Thread t : ts) {
            t.join();
        }
        double seconds = (System.nanoTime() - t0) / 1e9;
        long[] counts = new long[CLASSES];
        for (Sim s : sims) {
            for (int c = 0; c < CLASSES; c++) {
                counts[c] += s.counts[c];
            }
        }
        long n = perThread * threads;
        System.out.printf(Locale.ROOT, "b) Monte Carlo: %,d hands (%d threads x %,d, seeds %d..%d), %.0f s, %.2f M"
                + " hands/s%n", n, threads, perThread, baseSeed, baseSeed + threads - 1, seconds, n / seconds / 1e6);
        reportMc(counts, n);
        // batch means over equal batches (a check of the multinomial standard error)
        double sumBack = 0;
        double sumStaked = 0;
        int batches = 0;
        for (Sim s : sims) {
            for (int b = 0; b < s.batches; b++) {
                sumBack += s.batchBack[b];
                sumStaked += s.batchStaked[b];
                batches++;
            }
        }
        if (batches > 1) {
            double ratio = sumBack / sumStaked;
            double meanNet = (sumBack - sumStaked) / ((double) batches * BATCH);
            double sr = 0;
            double sn = 0;
            for (Sim s : sims) {
                for (int b = 0; b < s.batches; b++) {
                    sr += Math.pow(s.batchBack[b] - ratio * s.batchStaked[b], 2);
                    sn += Math.pow((s.batchBack[b] - s.batchStaked[b]) / (double) BATCH - meanNet, 2);
                }
            }
            double seRatio = Math.sqrt(sr / (batches - 1) / batches) / (sumStaked / batches);
            double seNet = Math.sqrt(sn / (batches - 1) / batches);
            System.out.printf(Locale.ROOT, "  batch means (%d x 2^22 hands): RTP per item %.8f +- %.8f, net per ante"
                    + " %.8f +- %.8f%n", batches, ratio, seRatio, meanNet, seNet);
        }
    }

    static void reportMc(long[] counts, long n) {
        double back = 0;
        double staked = 0;
        double b2 = 0;
        double s2 = 0;
        double bs = 0;
        for (int c = 0; c < CLASSES; c++) {
            double p = (double) counts[c] / n;
            back += p * BACK[c];
            staked += p * STAKED[c];
            b2 += p * BACK[c] * BACK[c];
            s2 += p * STAKED[c] * STAKED[c];
            bs += p * BACK[c] * STAKED[c];
        }
        double rtp = back / staked;
        // ratio estimator: Var(back - R staked) / (n E[staked]^2)
        double resid = b2 - 2 * rtp * bs + rtp * rtp * s2;
        double seRtp = Math.sqrt(resid / n) / staked;
        double net = back - staked;
        double varNet = b2 - 2 * bs + s2 - net * net;
        double seNet = Math.sqrt(varNet / n);
        System.out.printf(Locale.ROOT, "  RTP per item staked %.8f +- %.8f%n", rtp, seRtp);
        System.out.printf(Locale.ROOT, "  net per ante %.8f +- %.8f (per-hand SD %.4f antes), items back per ante"
                + " %.8f, staked per ante %.8f%n", net, seNet, Math.sqrt(varNet), back, staked);
        long noq = 0;
        long win = 0;
        for (int c = 0; c < 10; c++) {
            noq += counts[NOQ + c];
            win += counts[WIN + c];
        }
        long[] groups = {counts[FOLD], noq, win, counts[PUSH], counts[LOSE], noq + win, n - counts[FOLD]};
        String[] gnames = {"fold", "no-qualify", "win", "push", "lose", "player wins (WIN + NO_QUALIFY)", "call"};
        for (int g = 0; g < groups.length; g++) {
            double p = (double) groups[g] / n;
            System.out.printf(Locale.ROOT, "    %-32s %.8f +- %.8f%n", gnames[g], p, Math.sqrt(p * (1 - p) / n));
        }
        for (int c = 0; c < CLASSES; c++) {
            if (counts[c] != 0) {
                double p = (double) counts[c] / n;
                System.out.printf(Locale.ROOT, "    %-32s %16d  %.10f +- %.10f%n", NAMES[c], counts[c], p,
                        Math.sqrt(p * (1 - p) / n));
            }
        }
        StringBuilder java = new StringBuilder("  java: {");
        for (int c = 0; c < CLASSES; c++) {
            java.append(c == 0 ? "" : ", ").append(counts[c]).append('L');
        }
        System.out.println(java.append('}'));
    }

    // ---- replay: the plugin test's shoe, card for card -------------------------------------------------------

    static void replay(long seed, long hands) {
        long t0 = System.nanoTime();
        LiteralShoe shoe = new LiteralShoe(new Lcg(seed));
        long[] counts = new long[CLASSES];
        for (long h = 0; h < hands; h++) {
            counts[literalRound(shoe)]++;
        }
        System.out.printf(Locale.ROOT, "replay: new BlackjackShoe(new Random(%d), 1), %,d hands, simpleCall,"
                + " literal evaluator, %.1f s%n", seed, hands, (System.nanoTime() - t0) / 1e9);
        reportMc(counts, hands);
    }

    // ---- c) the payout in thirds (FairPayout.share) ----------------------------------------------------------

    /** FairPayout.share, transcribed. */
    static int share(long amount, int parts, int roll) {
        if (amount > 0L && parts > 0) {
            long whole = amount / (long) parts;
            long rest = amount % (long) parts;
            return (int) (whole + (long) ((long) Math.floorMod(roll, parts) < rest ? 1 : 0));
        }
        return 0;
    }

    static void rounding() {
        // thirds that PokerRound.payoutThirds can return: WIN 5 + k, NO_QUALIFY 3 + k, PUSH 3 (a refund), else 0
        int[] thirds = {0, 3, 4, 5, 6, 7, 8, 13, 15, 23, 25, 103, 105};
        long checks = 0;
        int fractional = 0;
        for (int ante = 1; ante <= 10_000; ante++) {
            int stake = 3 * ante; // takeCallStake: every stake becomes count x 3 before any WIN / NO_QUALIFY / PUSH
            for (int t : thirds) {
                for (int roll = -3; roll <= 5; roll++) {
                    if (share((long) stake * t, 3, roll) != ante * t) {
                        throw new AssertionError("ante " + ante + " thirds " + t + " roll " + roll);
                    }
                    checks++;
                }
                // the same thirds on an uncalled stake would round (never happens: a fold pays 0)
                if ((long) ante * t % 3 != 0) {
                    fractional++;
                }
            }
        }
        System.out.printf(Locale.ROOT, "c) rounding: share(3A x thirds, 3, roll) == A x thirds for A = 1..10000, every"
                + " reachable thirds value and rolls -3..5 (%,d checks): the roll never matters.%n", checks);
        System.out.printf(Locale.ROOT, "   (on an uncalled stake A the same thirds would leave a remainder in %,d of"
                + " the %,d (A, thirds) pairs; the only uncalled result is FOLD, which pays 0)%n", fractional,
                10_000 * thirds.length);
        System.out.println("   The items back are A x (a class constant), linear in A: the RTP per item and per ante"
                + " are the same for every ante, odd or even, and for any mix of stakes (each stake pays alone).");
    }

    // ---- checks ----------------------------------------------------------------------------------------------

    static void check(int threads) throws InterruptedException {
        long t0 = System.nanoTime();
        // 1. fast evaluator == literal transcription on all C(52, 7) = 133,784,560 seven-card sets; category counts
        long[][] cats = new long[threads][10];
        long[] sets = new long[threads];
        Thread[] ts = new Thread[threads];
        AtomicInteger next = new AtomicInteger();
        for (int t = 0; t < threads; t++) {
            final int me = t;
            ts[t] = new Thread(() -> {
                int[] cards = new int[7];
                for (int unit; (unit = next.getAndIncrement()) < HANDS; ) {
                    cards[0] = HAND_A[unit];
                    cards[1] = HAND_B[unit];
                    for (cards[2] = cards[1] + 1; cards[2] < 52; cards[2]++) {
                        for (cards[3] = cards[2] + 1; cards[3] < 52; cards[3]++) {
                            for (cards[4] = cards[3] + 1; cards[4] < 52; cards[4]++) {
                                for (cards[5] = cards[4] + 1; cards[5] < 52; cards[5]++) {
                                    for (cards[6] = cards[5] + 1; cards[6] < 52; cards[6]++) {
                                        int[] m = new int[4];
                                        for (int c : cards) {
                                            m[SUIT[c]] |= BIT[c];
                                        }
                                        int fast = score(m[0], m[1], m[2], m[3]);
                                        int literal = scoreLiteral(cards, 7);
                                        if (fast != literal) {
                                            throw new AssertionError(Arrays.toString(cards) + " fast " + fast
                                                    + " literal " + literal);
                                        }
                                        cats[me][fast >>> 20]++;
                                        sets[me]++;
                                    }
                                }
                            }
                        }
                    }
                }
            });
            ts[t].start();
        }
        for (Thread t : ts) {
            t.join();
        }
        long[] cat7 = new long[10];
        long total7 = 0;
        for (int t = 0; t < threads; t++) {
            total7 += sets[t];
            for (int c = 0; c < 10; c++) {
                cat7[c] += cats[t][c];
            }
        }
        // the standard 7-card counts (high card .. royal)
        long[] standard7 = {23294460L, 58627800L, 31433400L, 6461620L, 6180020L, 4047644L, 3473184L, 224848L,
                37260L, 4324L};
        System.out.printf(Locale.ROOT, "1. fast == literal on all %,d seven-card sets; categories %s (standard: %s)"
                + " -> %s, %.0f s%n", total7, Arrays.toString(cat7), Arrays.toString(standard7),
                Arrays.equals(cat7, standard7) ? "equal" : "DIFFERENT", (System.nanoTime() - t0) / 1e9);

        // 2. five-card scores: standard category counts and 7462 distinct ranks; 7-card = best of its 21 five-sets
        long[] cat5 = new long[10];
        java.util.HashSet<Integer> distinct = new java.util.HashSet<>();
        int[] five = new int[5];
        for (five[0] = 0; five[0] < 52; five[0]++) {
            for (five[1] = five[0] + 1; five[1] < 52; five[1]++) {
                for (five[2] = five[1] + 1; five[2] < 52; five[2]++) {
                    for (five[3] = five[2] + 1; five[3] < 52; five[3]++) {
                        for (five[4] = five[3] + 1; five[4] < 52; five[4]++) {
                            int s = scoreLiteral(five, 5);
                            cat5[s >>> 20]++;
                            distinct.add(s);
                        }
                    }
                }
            }
        }
        long[] standard5 = {1302540L, 1098240L, 123552L, 54912L, 10200L, 5108L, 3744L, 624L, 36L, 4L};
        System.out.printf(Locale.ROOT, "2. five-card categories %s (standard %s) -> %s; %d distinct scores"
                + " (standard 7462)%n", Arrays.toString(cat5), Arrays.toString(standard5),
                Arrays.equals(cat5, standard5) ? "equal" : "DIFFERENT", distinct.size());
        java.util.SplittableRandom rnd = new java.util.SplittableRandom(7);
        int samples = 2_000_000;
        for (int s = 0; s < samples; s++) {
            int[] seven = deal(rnd, 7);
            int best = -1;
            for (int skip1 = 0; skip1 < 7; skip1++) {
                for (int skip2 = skip1 + 1; skip2 < 7; skip2++) {
                    int k = 0;
                    for (int i = 0; i < 7; i++) {
                        if (i != skip1 && i != skip2) {
                            five[k++] = seven[i];
                        }
                    }
                    best = Math.max(best, scoreLiteral(five, 5));
                }
            }
            if (best != scoreLiteral(seven, 7)) {
                throw new AssertionError("7-card score is not the best five: " + Arrays.toString(seven));
            }
        }
        System.out.printf(Locale.ROOT, "   7-card score == max over its 21 five-card subsets on %,d random hands%n",
                samples);

        // 3. the dealer counts of the enumeration against brute force on random boards
        int boards = 300;
        AtomicInteger dummy = new AtomicInteger(HANDS);
        Enumerator e = new Enumerator(dummy, new byte[FLOPS * HANDS], null);
        for (int t = 0; t < boards; t++) {
            int[] b = deal(rnd, 5);
            Arrays.sort(b);
            e.board(b[0], b[1], b[2], b[3], b[4]);
            for (int i = 0; i < 1081; i++) {
                int nq = 0;
                int w = 0;
                int p = 0;
                int l = 0;
                for (int j = 0; j < 1081; j++) {
                    if (e.ha[j] == e.ha[i] || e.ha[j] == e.hb[i] || e.hb[j] == e.ha[i] || e.hb[j] == e.hb[i]) {
                        continue;
                    }
                    int cls = resolve(e.sc[i], e.sc[j]);
                    if (cls == PUSH) {
                        p++;
                    } else if (cls == LOSE) {
                        l++;
                    } else if (cls >= WIN) {
                        w++;
                    } else {
                        nq++;
                    }
                }
                if (nq != e.noq[i] || w != e.win[i] || p != e.push[i] || l != e.lose[i]) {
                    throw new AssertionError("dealer counts differ on board " + Arrays.toString(b));
                }
            }
        }
        System.out.printf(Locale.ROOT, "3. dealer outcome counts == brute force for every hole on %d random boards%n",
                boards);

        // 4. the fast shuffle (only the 9 dealt positions, skipInt) == the literal shoe, generator state included
        Lcg a = new Lcg(99);
        LiteralShoe literal = new LiteralShoe(new Lcg(99));
        for (int i = 51; i > 0; i--) {
            a.skipInt(i + 1);
        }
        int[] deck = new int[52];
        int rounds = 3_000_000;
        for (int r = 0; r < rounds; r++) {
            literal.shuffle();
            for (int c = 0; c < 52; c++) {
                deck[c] = c;
            }
            for (int i = 51; i >= 43; i--) {
                int j = a.nextInt(i + 1);
                int swap = deck[i];
                deck[i] = deck[j];
                deck[j] = swap;
            }
            for (int i = 42; i > 0; i--) {
                a.skipInt(i + 1);
            }
            for (int pos = 43; pos < 52; pos++) {
                if (deck[pos] != literal.cards[pos]) {
                    throw new AssertionError("fast shuffle differs in round " + r);
                }
            }
            if (a.state != literal.random.state) {
                throw new AssertionError("generator state differs in round " + r);
            }
        }
        // skipInt == nextInt in generator steps, on crafted states whose next output is the edge values
        long inverse = BigInteger.valueOf(0x5DEECE66DL).modInverse(BigInteger.ONE.shiftLeft(48)).longValue();
        int crafted = 0;
        for (int bound = 2; bound <= 52; bound++) {
            for (long target : new long[] {LIMIT[bound] - 1, LIMIT[bound], LIMIT[bound] + 1, Integer.MAX_VALUE,
                    Integer.MAX_VALUE - 1, 0}) {
                if (target < 0 || target > Integer.MAX_VALUE) {
                    continue;
                }
                long after = target << 17 | 0x1D2C3L; // a state whose next(31) is target
                long before = (after - 0xBL) * inverse & ((1L << 48) - 1);
                Lcg x = new Lcg(0);
                x.state = before;
                Lcg y = new Lcg(0);
                y.state = before;
                Lcg z = new Lcg(0);
                z.state = before;
                if (z.next(31) != target) {
                    throw new AssertionError("crafted state");
                }
                x.nextInt(bound);
                y.skipInt(bound);
                if (x.state != y.state) {
                    throw new AssertionError("skipInt != nextInt for bound " + bound + " at u = " + target);
                }
                crafted++;
            }
        }
        System.out.printf(Locale.ROOT, "4. fast shuffle == literal Fisher-Yates (dealt cards and generator state) for"
                + " %,d rounds; skipInt == nextInt on %d crafted edge states (u = limit - 1, limit, limit + 1,"
                + " 2^31 - 2, 2^31 - 1, 0 for every bound)%n", rounds, crafted);
        System.out.printf(Locale.ROOT, "checks done, %.0f s%n", (System.nanoTime() - t0) / 1e9);
    }

    static int[] deal(java.util.SplittableRandom rnd, int n) {
        int[] deck = new int[52];
        for (int c = 0; c < 52; c++) {
            deck[c] = c;
        }
        for (int i = 0; i < n; i++) {
            int j = i + rnd.nextInt(52 - i);
            int swap = deck[i];
            deck[i] = deck[j];
            deck[j] = swap;
        }
        return Arrays.copyOf(deck, n);
    }

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "exact";
        switch (mode) {
            case "exact" -> exact(args.length > 1 ? Integer.parseInt(args[1]) : 2);
            case "mc" -> monteCarlo(args.length > 1 ? Long.parseLong(args[1]) : 100_000_000L,
                    args.length > 2 ? Integer.parseInt(args[2]) : 2, args.length > 3 ? Long.parseLong(args[3]) : 1L);
            case "replay" -> replay(Long.parseLong(args[1]), Long.parseLong(args[2]));
            case "rounding" -> rounding();
            case "check" -> check(args.length > 1 ? Integer.parseInt(args[1]) : 2);
            default -> System.out.println("usage: exact [threads] | mc [handsPerThread] [threads] [seed] | replay seed"
                    + " hands | rounding | check [threads]");
        }
    }
}
