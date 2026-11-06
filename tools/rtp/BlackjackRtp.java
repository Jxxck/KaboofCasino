/*
 * BlackjackRtp: return to player (RTP) of KaboofCasino's blackjack, a 1:1 port of MineVegas Casino 1.1.0
 * (fr.minevegas.casino.BlackjackRound, BlackjackShoe, BlackjackCards, BlackjackBias, BlackjackTableBlockEntity).
 * A single-file program, independent of the plugin code: rules, strategy derivation, shoe and shuffle are
 * reimplemented here from the mod's source. The plugin's BlackjackRtpSimulationTest checks the shipped classes
 * against the numbers below.
 *
 * RULES (mod source; the plugin ships verbatim ports)
 *   6 decks (312 cards), Fisher-Yates with java.util.Random (i = size-1..1, j = nextInt(i+1)), cards drawn from the
 *   end; reshuffle at round start when fewer than 60 cards remain, an empty shoe reshuffles silently mid-round
 *   (BlackjackShoe). Deal player, dealer up, player, dealer hole; the dealer peeks with an ace or ten up; S17
 *   (draws while the soft total is below 17); double on any first two cards, exactly one card; no split, insurance
 *   or surrender; the player stands automatically on 21. Resolve order (BlackjackRound.resolve): bust, both naturals
 *   push, dealer natural, player natural 3:2, dealer bust, then player 21 vs dealer 21 (both non-natural) is a WIN,
 *   otherwise higher total wins, equal pushes. Payout per stake n (BlackjackTableBlockEntity.decided):
 *   CasinoExchange.payout(n, halves, 2, roll), halves = 4 win / 5 blackjack / 2 push (refund) / 0 loss; a double
 *   doubles n first. The player follows the mod's own strategy table BlackjackBias.STRATEGY (derived from an
 *   infinite deck, no split, pairs by total), rebuilt below with the mod's exact floating-point operation order.
 *
 * QUANTITIES
 *   per base stake:  1 + EV, EV = E[items back - items staked] / base stake ("1 + EV per base stake")
 *   per item staked: E[items back] / E[items staked], doubles count as staked (the mod's RETURN column)
 *
 * METHOD
 *   a) exact: off the top of a freshly shuffled 6-deck shoe. Enumerates up card, hole card and the player's two
 *      cards with composition-dependent probabilities, then the player's hits/doubles and the dealer's draws card by
 *      card from the remaining shoe, memoised by the removed cards (5 bits per rank) and the hand state, the dealer
 *      tables cleared per (up, hole). Exact up to double rounding (~1e-15). Gives the 13 outcome classes.
 *   b) infinite deck: the same recursion with P(ace..9) = 1/13, P(ten) = 4/13, plus the mod's own model
 *      sum P(first) FIRST[first] as a cross-check (games.md: -0.593 %).
 *   c) Monte Carlo: the mod's exact shoe (same LCG as java.util.Random, same Fisher-Yates, same draw order, 60-card
 *      cut, so the penetration effect is included), 2 threads with fixed seeds and a fixed number of rounds each,
 *      hence deterministic. Standard errors: multinomial (rounds independent) and batch means (2^22-round batches,
 *      which also covers the correlation of rounds within a shoe). Also: the first round of each shoe (= off the
 *      top, a check of a), and the fair-rounding check of d) with integer payouts for a stake of 1.
 *   d) fair rounding: an odd stake n on a blackjack pays FairPayout.share(5n, 2, roll) = floor(5n/2) + (roll < 1)
 *      with roll in {0, 1}, mean exactly 2.5n; every other payout is a whole number of items (4n/2, 8n/2 after a
 *      double, the refund n). Exact check for n = 1..384 and an MC check with stake 1 (the worst case).
 *   replay: one shoe seeded like the plugin test (new java.util.Random(seed)), prints the exact outcome counts.
 *      BlackjackRtpSimulationTest must reproduce them card for card through BlackjackRound + BlackjackShoe.
 *
 * RUN (from the project root; JAVA = /usr/lib/jvm/temurin-25-jdk-amd64/bin/java)
 *   systemd-run --scope -q -p MemoryMax=3G -p CPUWeight=20 -- choom -n 1000 -- nice -n 15 \
 *       $JAVA -Xmx2g tools/rtp/BlackjackRtp.java exact               # a, b, d   (2 s)
 *   ... $JAVA tools/rtp/BlackjackRtp.java mc 9000000000 2 1          # c: rounds per thread, threads, seed (15 min)
 *   ... $JAVA tools/rtp/BlackjackRtp.java replay 20261106 10000000   # golden counts of the plugin test (1 s)
 *   ... $JAVA tools/rtp/BlackjackRtp.java strategy                   # the derived strategy chart
 *
 * RESULTS (2026-11-06, Temurin 25; mc: 867 s on 2 threads, 20.7 M rounds/s)
 *   a) exact, off the top of a fresh 6-deck shoe: EV per base stake -0.480388 %
 *        RTP per base stake 0.9951961232, per item staked 0.9956177516
 *        (E[items back] 1.0914089230, E[items staked] 1.0962127998); win 43.628 %, push 8.248 %, loss 48.124 %,
 *        doubled 9.621 % of rounds, no doubled hand can bust (the strategy doubles only hard 9-11 and soft hands).
 *   b) infinite deck: EV -0.593328 % -> RTP per base stake 0.9940667239 (games.md: -0.593 % / 99.41 %),
 *        per item staked 0.9945878350; the mod's own model sum P(first) FIRST[first] gives the same -0.0059332761.
 *   c) Monte Carlo, 1.8e10 rounds (2 x 9e9, seeds 1 and 2; 382,865,856 shoes, 47.01 rounds per shoe):
 *        RTP per item staked 0.9954525782 +- 0.0000076, per base stake 0.9950143515 +- 0.0000083
 *        (batch means over 4290 x 2^22 rounds: +- 0.0000074 / +- 0.0000081; per-round SD 1.1167 base stakes).
 *        The 60-card cut costs 0.0165 percentage points against a). The first rounds of the shoes (3.83e8):
 *        0.9956315 +- 0.0000521 per item, every class within 2 SE of a).
 *   d) FairPayout: the mean blackjack payout is exactly 2.5 n for every stake n = 1..384 (n odd: floor or ceil by
 *        the roll, n even: exact), wins pay exactly 2n (4n doubled), pushes refund n, so E[items back] = n x
 *        1.0914089 and the RTP per item does not depend on the stake. Rounding only adds P(BJ)/4 = 0.0113 items^2 of
 *        variance per round at n = 1 (0.9 %). MC with integer payouts at stake 1: RTP per item 0.99545278 vs
 *        0.99545258 with 2.5 items, difference +2.0e-7 +- 7.2e-7 (rounding SE).
 *
 *   outcome class            items back/staked   a) exact        c) MC all rounds   games.md (8 M rounds)
 *   WIN                      2 / 1               0.140056347     0.1400292          0.14007
 *   WIN doubled              4 / 2               0.021192533     0.0212137          0.02128
 *   WIN_DEALER_BUST          2 / 1               0.196732822     0.1967075          0.19663
 *   WIN_DEALER_BUST doubled  4 / 2               0.032970801     0.0330091          0.03307
 *   BLACKJACK                2.5 / 1             0.045322988     0.0452716          0.04525
 *   PUSH                     1 / 1               0.074925780     0.0748821          0.07486
 *   PUSH doubled             2 / 2               0.005388750     0.0053976          0.00538
 *   PUSH_BLACKJACKS          1 / 1               0.002166500     0.0021617          0.00213
 *   LOSE                     0 / 1               0.235069787     0.2352377          0.23517
 *   LOSE doubled             0 / 2               0.036660716     0.0367478          0.03675
 *   LOSE_BUST                0 / 1               0.164189989     0.1640707          0.16417
 *   LOSE_DEALER_BLACKJACK    0 / 1               0.045322988     0.0452714          0.04523
 *   (WIN by 21 vs 21)                            0.003641691     0.0036470
 *   (MC SEs: 0.3e-6 .. 3.2e-6)
 *   The mod's settings table: RETURN[bias 0] = 0.995, WON = 0.436 (c: 0.99545 per item, win 0.43623).
 *
 *   replay 20261106 10000000 (212,718 shuffles): counts {1398610, 211991, 1967594, 330222, 453544, 749482, 53730,
 *        21394, 2351086, 367743, 1642668, 0, 451936, 36274} (class order as above, last = 21 vs 21 wins);
 *        RTP per item 0.9954185 (with 2.5 per blackjack). BlackjackRtpSimulationTest reproduces these counts.
 */

import java.util.HashMap;
import java.util.Locale;

public final class BlackjackRtp {

    // ---- outcome classes ----------------------------------------------------------------------------------------
    static final int WIN = 0, WIN_DOUBLED = 1, DEALER_BUST = 2, DEALER_BUST_DOUBLED = 3, BLACKJACK = 4, PUSH = 5,
            PUSH_DOUBLED = 6, PUSH_BLACKJACKS = 7, LOSE = 8, LOSE_DOUBLED = 9, BUST = 10, BUST_DOUBLED = 11,
            DEALER_BLACKJACK = 12, CLASSES = 13;
    /** Extra entry (not a class of its own): the WIN / WIN_DOUBLED rounds that were player 21 vs dealer 21. */
    static final int WIN21 = 13, SLOTS = 14;
    static final String[] NAMES = {"WIN", "WIN doubled", "WIN_DEALER_BUST", "WIN_DEALER_BUST doubled", "BLACKJACK",
            "PUSH", "PUSH doubled", "PUSH_BLACKJACKS", "LOSE", "LOSE doubled", "LOSE_BUST", "LOSE_BUST doubled",
            "LOSE_DEALER_BLACKJACK", "(of WIN: 21 vs 21)"};
    /** Items back per base stake, in half items (result.halves x stake multiplier). */
    static final int[] BACK_HALVES = {4, 8, 4, 8, 5, 2, 4, 2, 0, 0, 0, 0, 0};
    /** Items staked per base stake (2 after a double). */
    static final int[] STAKED = {1, 2, 1, 2, 1, 1, 2, 1, 1, 2, 1, 2, 1};

    static final int STAND = 0, HIT = 1, DOUBLE = 2;
    static final int DECKS = 6, RESHUFFLE_BELOW = 60;

    // ---- the mod's strategy, rebuilt (BlackjackBias static init, same operation order) --------------------------
    static final double[] P = new double[11];
    static final double[][][] DEALER = new double[32][2][];
    static final double[][] FROM_UP = new double[11][];
    static final int[][][][] STRATEGY = new int[11][22][2][2];
    /** STRATEGY flattened for the simulation: ((up * 22 + pips) * 2 + ace) * 2 + mayDouble. */
    static final byte[] PLAY = new byte[11 * 22 * 2 * 2];

    static {
        for (int v = 1; v <= 9; v++) {
            P[v] = 1.0 / 13.0;
        }
        P[10] = 4.0 / 13.0;
        for (int pips = 31; pips >= 2; pips--) {
            for (int ace = 0; ace < 2; ace++) {
                infiniteDealer(pips, ace == 1);
            }
        }
        for (int up = 1; up <= 10; up++) {
            double[] result = new double[6];
            double weight = 0.0;
            for (int hole = 1; hole <= 10; hole++) {
                if (!natural(up, hole)) {
                    weight += P[hole];
                    add(result, infiniteDealer(up + hole, up == 1 || hole == 1), P[hole]);
                }
            }
            for (int i = 0; i < 6; i++) {
                result[i] /= weight;
            }
            FROM_UP[up] = result;
            double[][] best = new double[22][2];
            for (int pips = 21; pips >= 2; pips--) {
                for (int ace = 0; ace < 2; ace++) {
                    int total = total(pips, ace == 1);
                    double stand = standValue(total, result);
                    double hit = 0.0;
                    double doubled = 0.0;
                    for (int v = 1; v <= 10; v++) {
                        int next = pips + v;
                        int nextAce = ace != 1 && v != 1 ? 0 : 1;
                        hit += P[v] * (next > 21 ? -1.0 : best[next][nextAce]);
                        doubled += P[v] * (next > 21 ? -2.0 : 2.0 * standValue(total(next, nextAce == 1), result));
                    }
                    best[pips][ace] = total == 21 ? stand : Math.max(stand, hit);
                    STRATEGY[up][pips][ace][0] = total != 21 && !(stand >= hit) ? HIT : STAND;
                    STRATEGY[up][pips][ace][1] = total == 21 ? STAND
                            : (doubled > Math.max(stand, hit) ? DOUBLE : (stand >= hit ? STAND : HIT));
                }
            }
        }
        for (int up = 1; up <= 10; up++) {
            for (int pips = 0; pips < 22; pips++) {
                for (int ace = 0; ace < 2; ace++) {
                    for (int may = 0; may < 2; may++) {
                        PLAY[((up * 22 + pips) * 2 + ace) * 2 + may] = (byte) STRATEGY[up][pips][ace][may];
                    }
                }
            }
        }
    }

    static double[] infiniteDealer(int pips, boolean ace) {
        pips = Math.min(pips, 31);
        double[] known = DEALER[pips][ace ? 1 : 0];
        if (known != null) {
            return known;
        }
        double[] result = new double[6];
        int total = total(pips, ace);
        if (total > 21) {
            result[5] = 1.0;
        } else if (total >= 17) {
            result[total - 17] = 1.0;
        } else {
            for (int v = 1; v <= 10; v++) {
                add(result, infiniteDealer(pips + v, ace || v == 1), P[v]);
            }
        }
        DEALER[pips][ace ? 1 : 0] = result;
        return result;
    }

    /** The mod's BlackjackBias.stand: EV of standing on {@code total} against a final dealer distribution. */
    static double standValue(int total, double[] dealer) {
        double result = dealer[5];
        for (int d = 17; d <= 21; d++) {
            result += dealer[d - 17] * (double) (total <= d && total != 21 ? (total < d ? -1 : 0) : 1);
        }
        return result;
    }

    static void add(double[] into, double[] from, double weight) {
        for (int i = 0; i < from.length; i++) {
            into[i] += weight * from[i];
        }
    }

    static boolean natural(int a, int b) {
        return a == 1 && b == 10 || a == 10 && b == 1;
    }

    static int total(int pips, boolean ace) {
        return ace && pips + 10 <= 21 ? pips + 10 : pips;
    }

    // ---- a) and b): exact recursion ----------------------------------------------------------------------------

    static final double[] DEALER_BUSTS = {0, 0, 0, 0, 0, 1};
    static final double[][] DEALER_STANDS = {{1, 0, 0, 0, 0, 0}, {0, 1, 0, 0, 0, 0}, {0, 0, 1, 0, 0, 0},
            {0, 0, 0, 1, 0, 0}, {0, 0, 0, 0, 1, 0}};

    static final class Exact {
        final boolean infinite;
        final int[] rem = new int[11];
        int remTotal;
        /** Removed cards, 5 bits per rank (rank v at bits 5(v-1)); always 0 for the infinite deck. */
        long removed;
        final HashMap<Long, double[]> dealerMemo = new HashMap<>();
        final HashMap<Long, double[]> playerMemo = new HashMap<>();
        int up;
        int hole;
        long dealerNodes;
        long playerNodes;

        Exact(boolean infinite) {
            this.infinite = infinite;
            for (int v = 1; v <= 9; v++) {
                rem[v] = 4 * DECKS;
            }
            rem[10] = 16 * DECKS;
            remTotal = 52 * DECKS;
        }

        double p(int v) {
            return infinite ? P[v] : (double) rem[v] / remTotal;
        }

        void take(int v) {
            if (!infinite) {
                rem[v]--;
                remTotal--;
                removed += 1L << (5 * (v - 1));
                if (rem[v] < 0 || ((removed >>> (5 * (v - 1))) & 31) == 0) {
                    throw new IllegalStateException("card count out of range");
                }
            }
        }

        void put(int v) {
            if (!infinite) {
                rem[v]++;
                remTotal++;
                removed -= 1L << (5 * (v - 1));
            }
        }

        /** Probabilities of the 13 outcome classes (+ WIN21) for one round off the top. */
        double[] run() {
            double[] out = new double[SLOTS];
            for (int u = 1; u <= 10; u++) {
                double pu = p(u);
                take(u);
                for (int h = 1; h <= 10; h++) {
                    double ph = p(h);
                    if (ph == 0) {
                        continue;
                    }
                    take(h);
                    up = u;
                    hole = h;
                    dealerMemo.clear();
                    playerMemo.clear();
                    boolean dealerNatural = natural(u, h);
                    for (int p1 = 1; p1 <= 10; p1++) {
                        double pp1 = p(p1);
                        take(p1);
                        for (int p2 = 1; p2 <= 10; p2++) {
                            double pp2 = p(p2);
                            if (pp2 == 0) {
                                continue;
                            }
                            take(p2);
                            double w = pu * ph * pp1 * pp2;
                            boolean playerNatural = natural(p1, p2);
                            if (dealerNatural) {
                                out[playerNatural ? PUSH_BLACKJACKS : DEALER_BLACKJACK] += w;
                            } else if (playerNatural) {
                                out[BLACKJACK] += w;
                            } else {
                                add(out, player(p1 + p2, p1 == 1 || p2 == 1, 2), w);
                            }
                            put(p2);
                        }
                        put(p1);
                    }
                    put(h);
                }
                put(u);
            }
            return out;
        }

        /** The player's hand (pips counting aces as 1) at a turn: outcome class probabilities from here on. */
        double[] player(int pips, boolean ace, int cards) {
            long key = (removed << 7) | ((long) pips << 2) | (ace ? 2 : 0) | (cards == 2 ? 1 : 0);
            double[] known = playerMemo.get(key);
            if (known != null) {
                return known;
            }
            playerNodes++;
            double[] r = new double[SLOTS];
            int total = total(pips, ace);
            int action = total == 21 ? STAND : STRATEGY[up][pips][ace ? 1 : 0][cards == 2 ? 1 : 0];
            if (action == STAND) {
                settle(r, total, false, 1.0);
            } else {
                for (int v = 1; v <= 10; v++) {
                    double pv = p(v);
                    if (pv == 0) {
                        continue;
                    }
                    int next = pips + v;
                    boolean nextAce = ace || v == 1;
                    if (next > 21) {
                        r[action == DOUBLE ? BUST_DOUBLED : BUST] += pv;
                        continue;
                    }
                    take(v);
                    if (action == DOUBLE) {
                        settle(r, total(next, nextAce), true, pv);
                    } else {
                        add(r, player(next, nextAce, cards + 1), pv);
                    }
                    put(v);
                }
            }
            playerMemo.put(key, r);
            return r;
        }

        /** The player stands on {@code total}: the dealer plays from the current shoe. */
        void settle(double[] r, int total, boolean doubled, double w) {
            double[] d = dealer(up + hole, up == 1 || hole == 1);
            r[doubled ? DEALER_BUST_DOUBLED : DEALER_BUST] += w * d[5];
            for (int dv = 17; dv <= 21; dv++) {
                double q = w * d[dv - 17];
                if (total > dv || total == 21 && dv == 21) {
                    r[doubled ? WIN_DOUBLED : WIN] += q;
                    if (total == 21 && dv == 21) {
                        r[WIN21] += q;
                    }
                } else if (total == dv) {
                    r[doubled ? PUSH_DOUBLED : PUSH] += q;
                } else {
                    r[doubled ? LOSE_DOUBLED : LOSE] += q;
                }
            }
        }

        /** Final dealer distribution [17, 18, 19, 20, 21, bust] from (pips, ace), drawing from the current shoe. */
        double[] dealer(int pips, boolean ace) {
            int total = total(pips, ace);
            if (total > 21) {
                return DEALER_BUSTS;
            }
            if (total >= 17) {
                return DEALER_STANDS[total - 17];
            }
            long key = (removed << 6) | ((long) pips << 1) | (ace ? 1 : 0);
            double[] known = dealerMemo.get(key);
            if (known != null) {
                return known;
            }
            dealerNodes++;
            double[] r = new double[6];
            for (int v = 1; v <= 10; v++) {
                double pv = p(v);
                if (pv == 0) {
                    continue;
                }
                take(v);
                add(r, dealer(pips + v, ace || v == 1), pv);
                put(v);
            }
            dealerMemo.put(key, r);
            return r;
        }
    }

    /** The mod's own infinite-deck model: EV = sum P(first) FIRST[first] (BlackjackBias opening/follow). */
    static double modModelEv() {
        double ev = 0;
        for (int first = 1; first <= 10; first++) {
            double firstEv = 0;
            for (int up = 1; up <= 10; up++) {
                double upEv = 0;
                for (int second = 1; second <= 10; second++) {
                    double dealerNatural = up == 1 ? P[10] : (up == 10 ? P[1] : 0.0);
                    double opening = natural(first, second) ? 1.5 * (1.0 - dealerNatural)
                            : -dealerNatural + (1.0 - dealerNatural)
                            * follow(first + second, first == 1 || second == 1, true, up);
                    upEv += P[second] * opening;
                }
                firstEv += P[up] * upEv;
            }
            ev += P[first] * firstEv;
        }
        return ev;
    }

    static double follow(int pips, boolean ace, boolean mayDouble, int up) {
        int total = total(pips, ace);
        if (total > 21) {
            return -1.0;
        }
        double[] dealer = FROM_UP[up];
        if (total == 21) {
            return standValue(21, dealer);
        }
        int action = STRATEGY[up][pips][ace ? 1 : 0][mayDouble ? 1 : 0];
        if (action == STAND) {
            return standValue(total, dealer);
        }
        double result = 0;
        for (int v = 1; v <= 10; v++) {
            int next = pips + v;
            boolean nextAce = ace || v == 1;
            if (action == DOUBLE) {
                result += P[v] * (total(next, nextAce) > 21 ? -2.0 : 2.0 * standValue(total(next, nextAce), dealer));
            } else {
                result += P[v] * follow(next, nextAce, false, up);
            }
        }
        return result;
    }

    // ---- c): Monte Carlo with the mod's exact shoe -------------------------------------------------------------

    /** java.util.Random's 48-bit LCG and its nextInt(bound) (same seed, same numbers), without the atomic. */
    static final class Lcg {
        private long state;

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
    }

    /** BlackjackShoe without bias: card ids 0..51 per deck, Fisher-Yates, draw from the end. */
    static final class Shoe {
        final Lcg random;
        final int[] cards = new int[52 * DECKS];
        int size;
        long shuffles;

        Shoe(Lcg random) {
            this.random = random;
            shuffle();
        }

        void shuffle() {
            int k = 0;
            for (int deck = 0; deck < DECKS; deck++) {
                for (int card = 0; card < 52; card++) {
                    cards[k++] = card;
                }
            }
            size = k;
            for (int i = size - 1; i > 0; i--) {
                int j = random.nextInt(i + 1);
                int swap = cards[i];
                cards[i] = cards[j];
                cards[j] = swap;
            }
            shuffles++;
        }

        /** Value of the next card: 1 = ace, 2..9, 10 = ten/J/Q/K (BlackjackCards.pips). */
        int draw() {
            if (size == 0) {
                shuffle();
            }
            int rank = cards[--size] % 13;
            return rank >= 9 ? 10 : rank + 1;
        }
    }

    /**
     * One round of BlackjackRound with the strategy player: the outcome class, +16 when it was a 21 vs 21 win.
     * Draw order as the mod: player, up, player, hole, the player's cards, then the dealer's.
     */
    static int playRound(Shoe shoe) {
        if (shoe.size < RESHUFFLE_BELOW) {
            shoe.shuffle();
        }
        int p1 = shoe.draw();
        int up = shoe.draw();
        int p2 = shoe.draw();
        int hole = shoe.draw();
        boolean playerNatural = natural(p1, p2);
        if (natural(up, hole)) {
            return playerNatural ? PUSH_BLACKJACKS : DEALER_BLACKJACK;
        }
        if (playerNatural) {
            return BLACKJACK;
        }
        int pips = p1 + p2;
        boolean ace = p1 == 1 || p2 == 1;
        int cards = 2;
        boolean doubled = false;
        while (true) {
            int action = PLAY[((up * 22 + pips) * 2 + (ace ? 1 : 0)) * 2 + (cards == 2 ? 1 : 0)];
            if (action == STAND) {
                break;
            }
            int v = shoe.draw();
            pips += v;
            ace |= v == 1;
            cards++;
            if (pips > 21) {
                return action == DOUBLE ? BUST_DOUBLED : BUST;
            }
            if (action == DOUBLE) {
                doubled = true;
                break;
            }
            if (total(pips, ace) == 21) {
                break;
            }
        }
        int p = total(pips, ace);
        int dPips = up + hole;
        boolean dAce = up == 1 || hole == 1;
        while (total(dPips, dAce) < 17) {
            int v = shoe.draw();
            dPips += v;
            dAce |= v == 1;
        }
        int d = total(dPips, dAce);
        if (d > 21) {
            return doubled ? DEALER_BUST_DOUBLED : DEALER_BUST;
        }
        if (p <= d && (p != 21 || d != 21)) {
            return p < d ? (doubled ? LOSE_DOUBLED : LOSE) : (doubled ? PUSH_DOUBLED : PUSH);
        }
        return (doubled ? WIN_DOUBLED : WIN) | (p == 21 && d == 21 ? 16 : 0);
    }

    static final int BATCH = 1 << 22;

    static final class Sim implements Runnable {
        final long seed;
        final long rounds;
        final long[] counts = new long[SLOTS];
        final long[] firstCounts = new long[SLOTS];
        long[] batchBack;
        long[] batchStaked;
        int batches;
        /** Items back with stake 1 and integer FairPayout blackjacks (2 or 3 items). */
        long integerBack;
        long shuffles;

        Sim(long seed, long rounds) {
            this.seed = seed;
            this.rounds = rounds;
            int max = (int) ((rounds + BATCH - 1) / BATCH);
            batchBack = new long[max];
            batchStaked = new long[max];
        }

        @Override
        public void run() {
            Shoe shoe = new Shoe(new Lcg(seed));
            Lcg rolls = new Lcg(~seed * 0x9E3779B97F4A7C15L);
            long back = 0;
            long staked = 0;
            int inBatch = 0;
            for (long i = 0; i < rounds; i++) {
                boolean fresh = shoe.size == 52 * DECKS || shoe.size < RESHUFFLE_BELOW;
                int r = playRound(shoe);
                int c = r & 15;
                counts[c]++;
                if ((r & 16) != 0) {
                    counts[WIN21]++;
                }
                if (fresh) {
                    firstCounts[c]++;
                    if ((r & 16) != 0) {
                        firstCounts[WIN21]++;
                    }
                }
                if (c == BLACKJACK) {
                    // FairPayout.share(5 * 1, 2, roll) = 2 + (roll < 1 ? 1 : 0)
                    integerBack += 2 + (rolls.nextInt(2) < 1 ? 1 : 0);
                } else {
                    integerBack += BACK_HALVES[c] / 2;
                }
                back += BACK_HALVES[c];
                staked += STAKED[c];
                if (++inBatch == BATCH) {
                    batchBack[batches] = back;
                    batchStaked[batches] = staked;
                    batches++;
                    back = 0;
                    staked = 0;
                    inBatch = 0;
                }
            }
            shuffles = shoe.shuffles;
        }
    }

    // ---- reporting ---------------------------------------------------------------------------------------------

    static double itemsBack(double[] p) {
        double s = 0;
        for (int c = 0; c < CLASSES; c++) {
            s += p[c] * BACK_HALVES[c] / 2.0;
        }
        return s;
    }

    static double itemsStaked(double[] p) {
        double s = 0;
        for (int c = 0; c < CLASSES; c++) {
            s += p[c] * STAKED[c];
        }
        return s;
    }

    static void printClasses(String title, double[] p, double[] se) {
        System.out.println(title);
        for (int c = 0; c < SLOTS; c++) {
            String back = c < CLASSES ? String.format(Locale.ROOT, "back %.1f / staked %d", BACK_HALVES[c] / 2.0,
                    STAKED[c]) : "";
            if (se == null) {
                System.out.printf(Locale.ROOT, "  %-26s p = %.12f   %s%n", NAMES[c], p[c], back);
            } else {
                System.out.printf(Locale.ROOT, "  %-26s p = %.7f +- %.7f   %s%n", NAMES[c], p[c], se[c], back);
            }
        }
        double win = p[WIN] + p[WIN_DOUBLED] + p[DEALER_BUST] + p[DEALER_BUST_DOUBLED] + p[BLACKJACK];
        double push = p[PUSH] + p[PUSH_DOUBLED] + p[PUSH_BLACKJACKS];
        double doubled = p[WIN_DOUBLED] + p[DEALER_BUST_DOUBLED] + p[PUSH_DOUBLED] + p[LOSE_DOUBLED]
                + p[BUST_DOUBLED];
        System.out.printf(Locale.ROOT,
                "  win %.7f  push %.7f  loss %.7f  doubled %.7f  LOSE_BUST %.7f  LOSE all %.7f%n", win, push,
                1 - win - push, doubled, p[BUST] + p[BUST_DOUBLED], p[LOSE] + p[LOSE_DOUBLED]);
        double back = itemsBack(p);
        double staked = itemsStaked(p);
        System.out.printf(Locale.ROOT,
                "  E[items back] %.10f  E[items staked] %.10f  EV per base stake %+.10f%n"
                        + "  RTP per base stake (1 + EV) %.10f   RTP per item staked %.10f%n",
                back, staked, back - staked, 1 + back - staked, back / staked);
    }

    static void exact() {
        long t0 = System.nanoTime();
        Exact six = new Exact(false);
        double[] a = six.run();
        long t1 = System.nanoTime();
        double sum = 0;
        for (int c = 0; c < CLASSES; c++) {
            sum += a[c];
        }
        System.out.printf(Locale.ROOT, "a) exact, off the top of a fresh 6-deck shoe (%.1f s, %d player / %d dealer "
                + "nodes, total probability %.15f)%n", (t1 - t0) / 1e9, six.playerNodes, six.dealerNodes, sum);
        printClasses("", a, null);

        Exact inf = new Exact(true);
        double[] b = inf.run();
        System.out.println();
        System.out.println("b) infinite deck (P(ace..9) = 1/13, P(ten) = 4/13)");
        printClasses("", b, null);
        double model = modModelEv();
        System.out.printf(Locale.ROOT, "  mod's own model sum P(first) FIRST[first]: EV %+.10f -> RTP per base stake "
                + "%.10f%n", model, 1 + model);

        System.out.println();
        System.out.println("d) fair rounding: FairPayout.share(amount, 2, roll), roll in {0, 1}");
        boolean allExact = true;
        for (int n = 1; n <= 384; n++) {
            long sum2 = share(5L * n, 2, 0) + share(5L * n, 2, 1);
            boolean ok = sum2 == 5L * n
                    && share(4L * n, 2, 0) == 2L * n && share(4L * n, 2, 1) == 2L * n
                    && share(4L * 2 * n, 2, 0) == 4L * n && share(4L * 2 * n, 2, 1) == 4L * n;
            allExact &= ok;
            if (n <= 4) {
                System.out.printf(Locale.ROOT, "  stake %d: blackjack pays %d (roll 0) or %d (roll 1), mean %.1f"
                        + " = 2.5 x %d; win %d, doubled win %d%n", n, share(5L * n, 2, 0), share(5L * n, 2, 1),
                        sum2 / 2.0, n, share(4L * n, 2, 0), share(8L * n, 2, 0));
            }
        }
        System.out.println("  stakes 1..384: mean blackjack payout exactly 2.5 n, wins exactly 2n / 4n: " + allExact);
        double bj = a[BLACKJACK];
        System.out.printf(Locale.ROOT, "  -> E[items back] for stake n = n x %.10f for every n, RTP per item"
                + " unchanged; extra variance per round for odd n: P(BJ)/4 = %.6f items^2 (%.4f%% of the per-round"
                + " variance at n = 1)%n",
                itemsBack(a), bj / 4, 100 * (bj / 4) / variance(a));
    }

    static long share(long amount, int parts, int roll) {
        long whole = amount / parts;
        long rest = amount % parts;
        return whole + (Math.floorMod(roll, parts) < rest ? 1 : 0);
    }

    /** Per-round variance of (items back - items staked) per base stake. */
    static double variance(double[] p) {
        double m = 0;
        double m2 = 0;
        for (int c = 0; c < CLASSES; c++) {
            double x = BACK_HALVES[c] / 2.0 - STAKED[c];
            m += p[c] * x;
            m2 += p[c] * x * x;
        }
        return m2 - m * m;
    }

    static void monteCarlo(long perThread, int threads, long baseSeed) throws InterruptedException {
        Sim[] sims = new Sim[threads];
        Thread[] workers = new Thread[threads];
        long t0 = System.nanoTime();
        for (int k = 0; k < threads; k++) {
            sims[k] = new Sim(baseSeed + k, perThread);
            workers[k] = new Thread(sims[k], "mc-" + k);
            workers[k].start();
        }
        for (Thread w : workers) {
            w.join();
        }
        double seconds = (System.nanoTime() - t0) / 1e9;
        long[] counts = new long[SLOTS];
        long[] first = new long[SLOTS];
        long integerBack = 0;
        long shuffles = 0;
        int batches = 0;
        for (Sim s : sims) {
            for (int c = 0; c < SLOTS; c++) {
                counts[c] += s.counts[c];
                first[c] += s.firstCounts[c];
            }
            integerBack += s.integerBack;
            shuffles += s.shuffles;
            batches += s.batches;
        }
        long n = perThread * threads;
        System.out.printf(Locale.ROOT, "c) Monte Carlo: %,d rounds (%d threads x %,d, seeds %d..%d), %.0f s,"
                + " %.1f M rounds/s, %,d shuffles (%.2f rounds per shoe)%n", n, threads, perThread, baseSeed,
                baseSeed + threads - 1, seconds, n / seconds / 1e6, shuffles, (double) n / shuffles);
        report("all rounds (6-deck shoe, 60-card cut)", counts, n);

        // batch means (equal-size batches, rounds within a shoe are not independent)
        double totalBack = 0;
        double totalStaked = 0;
        for (Sim s : sims) {
            for (int b = 0; b < s.batches; b++) {
                totalBack += s.batchBack[b] / 2.0;
                totalStaked += s.batchStaked[b];
            }
        }
        double ratio = totalBack / totalStaked;
        double meanNet = (totalBack - totalStaked) / ((double) batches * BATCH);
        double sr = 0;
        double sn = 0;
        for (Sim s : sims) {
            for (int b = 0; b < s.batches; b++) {
                double bb = s.batchBack[b] / 2.0;
                double bs = s.batchStaked[b];
                sr += Math.pow(bb - ratio * bs, 2);
                sn += Math.pow((bb - bs) / BATCH - meanNet, 2);
            }
        }
        double seRatio = Math.sqrt(sr / ((double) batches * (batches - 1))) / (totalStaked / batches);
        double seNet = Math.sqrt(sn / ((double) batches * (batches - 1)));
        if (batches >= 2) {
            System.out.printf(Locale.ROOT, "  batch means (%d batches of %,d rounds): RTP per item %.7f +- %.7f,"
                    + " per base stake %.7f +- %.7f%n", batches, BATCH, ratio, seRatio, 1 + meanNet, seNet);
        }

        long staked = 0;
        long bjCount = counts[BLACKJACK];
        for (int c = 0; c < CLASSES; c++) {
            staked += counts[c] * STAKED[c];
        }
        double rtpInt = (double) integerBack / staked;
        double rtpExact = itemsBack(freq(counts, n)) / itemsStaked(freq(counts, n));
        System.out.printf(Locale.ROOT, "  d) stake 1 with integer FairPayout blackjacks (2 or 3 items): RTP per"
                + " item %.8f vs %.8f with 2.5 -> difference %+.2e, rounding SE %.2e (%,d blackjacks)%n", rtpInt,
                rtpExact, rtpInt - rtpExact, 0.5 * Math.sqrt(bjCount) / staked, bjCount);

        long firstN = 0;
        for (int c = 0; c < CLASSES; c++) {
            firstN += first[c];
        }
        System.out.println();
        report("first round of each shoe (= off the top, compare a)", first, firstN);
    }

    static double[] freq(long[] counts, long n) {
        double[] p = new double[SLOTS];
        for (int c = 0; c < SLOTS; c++) {
            p[c] = (double) counts[c] / n;
        }
        return p;
    }

    /** Class frequencies with binomial SEs; RTP SEs from the multinomial (independent rounds). */
    static void report(String title, long[] counts, long n) {
        double[] p = freq(counts, n);
        double[] se = new double[SLOTS];
        for (int c = 0; c < SLOTS; c++) {
            se[c] = Math.sqrt(p[c] * (1 - p[c]) / n);
        }
        printClasses("  " + title + ", " + String.format(Locale.ROOT, "%,d", n) + " rounds", p, se);
        double back = itemsBack(p);
        double staked = itemsStaked(p);
        double ratio = back / staked;
        double varResidual = 0;
        for (int c = 0; c < CLASSES; c++) {
            double x = BACK_HALVES[c] / 2.0 - ratio * STAKED[c];
            varResidual += p[c] * x * x;
        }
        System.out.printf(Locale.ROOT, "  SE (independent rounds): RTP per item +- %.7f, per base stake +- %.7f;"
                + " per-round SD %.5f (base stake), %.5f (ratio residual)%n", Math.sqrt(varResidual / n) / staked,
                Math.sqrt(variance(p) / n), Math.sqrt(variance(p)), Math.sqrt(varResidual));
        StringBuilder java = new StringBuilder("  counts {");
        for (int c = 0; c < SLOTS; c++) {
            java.append(c == 0 ? "" : ", ").append(counts[c]).append('L');
        }
        System.out.println(java.append('}'));
    }

    static void replay(long seed, long rounds) {
        Shoe shoe = new Shoe(new Lcg(seed));
        long[] counts = new long[SLOTS];
        for (long i = 0; i < rounds; i++) {
            int r = playRound(shoe);
            counts[r & 15]++;
            if ((r & 16) != 0) {
                counts[WIN21]++;
            }
        }
        System.out.printf(Locale.ROOT, "replay: one shoe, new java.util.Random(%d), %,d rounds, %,d shuffles%n", seed,
                rounds, shoe.shuffles);
        report("replay", counts, rounds);
    }

    static void strategy() {
        System.out.println("STRATEGY (rows: pips with ace = 1; H hit, S stand, D double, Dx = double if allowed"
                + " else x)");
        for (int ace = 0; ace < 2; ace++) {
            System.out.println((ace == 0 ? "hard" : "soft") + "   2  3  4  5  6  7  8  9 10  A");
            for (int pips = ace == 0 ? 4 : 2; pips <= (ace == 0 ? 21 : 11); pips++) {
                StringBuilder row = new StringBuilder(String.format(Locale.ROOT, "%5s",
                        ace == 0 ? Integer.toString(pips) : "A," + (pips - 1)));
                for (int i = 0; i < 10; i++) {
                    int up = i == 9 ? 1 : i + 2;
                    int may = STRATEGY[up][pips][ace][1];
                    int not = STRATEGY[up][pips][ace][0];
                    String cell = may == DOUBLE ? "D" + (not == HIT ? "H" : "S") : (may == HIT ? "H" : "S");
                    if (may != DOUBLE && may != not) {
                        cell = "?";
                    }
                    row.append(String.format(Locale.ROOT, "%3s", cell));
                }
                System.out.println(row);
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        String mode = args.length > 0 ? args[0] : "exact";
        switch (mode) {
            case "exact" -> exact();
            case "mc" -> monteCarlo(args.length > 1 ? Long.parseLong(args[1]) : 100_000_000L,
                    args.length > 2 ? Integer.parseInt(args[2]) : 2, args.length > 3 ? Long.parseLong(args[3]) : 1L);
            case "replay" -> replay(Long.parseLong(args[1]), Long.parseLong(args[2]));
            case "strategy" -> strategy();
            default -> System.out.println("usage: exact | mc [roundsPerThread] [threads] [seed] | replay seed rounds"
                    + " | strategy");
        }
    }
}
