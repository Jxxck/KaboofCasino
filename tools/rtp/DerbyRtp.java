/*
 * DerbyRtp: return to player (RTP) and race-planner properties of KaboofCasino's duck derby, a 1:1 port of
 * MineVegas Casino 1.1.0 (fr.minevegas.casino.DuckDerbyRace, DuckDerbyBlockEntity.launchPersonal/runnerUp,
 * CasinoOdds.derby/expectedReturn). A single-file program, independent of the plugin code: the planner below is its
 * own transcription of DuckDerbyRace from the mod's source (constants checked against the mod's bytecode). The
 * plugin's DuckDerbyRaceTest takes its golden values from here; DerbyRtpSimulationTest checks the shipped path
 * (DerbyRules.draw -> DuckDerbyRace.plan -> runnerUp -> payout) against the exact numbers below.
 *
 * RULES (mod source)
 *   Winner (launchPersonal, DuckDerbyBlockEntity.java:354-364): group races and natural odds (chance -1) draw
 *   nextInt(4); a solo race with chance c in 0..100 uses CasinoOdds.derby(lane, c, nextInt): nextInt(100) < c -> the
 *   player's lane, else the other lanes by nextInt(3) (CasinoOdds.java:25-38). Then seed = nextLong() and
 *   plan = DuckDerbyRace.plan(winner, seed). 2nd place = the lane with rank 1 (runnerUp, DuckDerbyBlockEntity.java
 *   :478-490). Per stake stack: 1st 3 x count, 2nd 1 x count (stake back), 3rd/4th nothing (:379-397).
 *
 * WHY 3p + (1-p)/3 IS EXACT (p = chance of the player's lane winning)
 *   P(win) = p exactly: 1/4 for nextInt(4), c/100 for nextInt(100) < c. The return is 3 x stake on a win, 1 x stake
 *   when the player's lane is 2nd, so RTP = 3p + P(2nd). Given the winner W, the planner treats the three other
 *   lanes symmetrically: (1) finishingTimes puts them in a Fisher-Yates-shuffled order (uniform permutation, its
 *   first entry gets the 2nd-place time) and the shuffle is the only place a lane index picks a finishing time;
 *   (2) every non-winner gets its progress table from the same slope range (only the champion's range differs) as
 *   i.i.d. draws; (3) score() depends on the lanes only through symmetric functions (max/min/argmax of progress,
 *   counts of rank changes, max non-winner progress, midRank/rank comparisons, the winner's own progress); lane
 *   indices matter only to break exact ties of two doubles (argmax keeps the lower lane), an event of probability
 *   ~0; (4) the best of 48 attempts is chosen by score alone, and the null plan (all 48 rejected) is a symmetric
 *   event. So relabelling the non-winners by any permutation maps the plan distribution onto itself, and the lane in
 *   2nd place is uniform over the 3 non-winners, also conditional on the story and on a non-null plan (the plugin's
 *   retry with a new seed therefore changes nothing). With an ideal generator: P(2nd = L | W != L) = 1/3, so
 *   P(2nd) = (1-p)/3 and RTP = 3p + (1-p)/3 = (2c + 25)/75 for c in percent (CasinoOdds.java:59). The only
 *   deviation of the real generators is below(n) = (int)(next()*n) on 2^53 grid values (bias < 2^-51) and the
 *   tie-break, both far below anything measurable. The Monte Carlo measures P(2nd = L | W) for all 12 pairs.
 *   Natural odds and every group race: p = 1/4 -> RTP = 1 exactly (win 1/4, push 1/4, loss 1/2, SD 1.2247/item).
 *   The payout is count x multiplier with no rounding, so the RTP per item staked is the same for every stake size.
 *
 * MODES (JAVA = /usr/lib/jvm/temurin-25-jdk-amd64/bin/java; every run inside
 *        systemd-run --scope -q -p MemoryMax=3G -p CPUWeight=20 -- choom -n 1000 -- nice -n 15 ...)
 *   $JAVA tools/rtp/DerbyRtp.java exact                       # RTP table c = 0..100, by enumeration of the draw
 *   $JAVA tools/rtp/DerbyRtp.java speed 400                   # planner speed (plans/s, one thread)
 *   $JAVA tools/rtp/DerbyRtp.java mc 66000 2 20261107         # Monte Carlo: plans per thread, threads, seed
 *   $JAVA tools/rtp/DerbyRtp.java golden                      # golden values for DuckDerbyRaceTest
 *   $JAVA tools/rtp/DerbyRtp.java symmetry 10000 2 5          # score() invariant under relabelling non-winners
 *   $JAVA tools/rtp/DerbyRtp.java cross 10000 2 <root>...     # this transcription vs other DuckDerbyRace builds
 *       each root is a class directory or jar holding de.tigges.kaboofcasino.game.derby.DuckDerbyRace (the
 *       plugin, e.g. javac -d /some/dir src/main/java/de/tigges/kaboofcasino/game/derby/DuckDerbyRace.java) or
 *       fr.minevegas.casino.DuckDerbyRace (the mod jar; only that pure class and its nested classes are loaded:
 *       its bytecode references nothing but java.lang/java.util). Compares bit for bit: null plan, ranks, finish/
 *       result/return/end ticks, quacks, progress, pace, gate and poses (z, pitch, lift) at ~800 times per plan.
 *
 * RESULTS (2026-11-07, Temurin 25.0.5, capped scope as above)
 *   exact: RTP per item = (2c + 25) / 75; natural odds and group races 1 (win 1/4, push 1/4, loss 1/2, SD 1.2247);
 *     c = 0: 1/3, 10: 3/5, 15: 11/15, 20: 13/15, 24: 73/75, 25: 1, 30: 17/15, 50: 5/3, 100: 3 (all 101 chances x
 *     4 lanes enumerated through the transcribed CasinoOdds.derby; equal to the mod's 3p + (1-p)/3).
 *   speed: 116-124 plans/s per thread here (8.1-8.6 ms per plan: 48 attempts x 4 tables x 481 samples of
 *     StrictMath sin/sin/exp/exp); 99-110 plans/s in the Gradle test JVM.
 *   mc 110000 2 20261107: 220,000 plans in 888 s (247.8 plans/s on 2 threads).
 *     null plans 0 of 220,000 (95 % upper bound 1.36e-5). Rejected attempts: comeback 0.00006, front-runner
 *       0.02102 +- 0.00008 (spread 0.01859, runner-up 0.00243), photo finish 0.00775 +- 0.00005 (0.00683 /
 *       0.00091); valid attempts per plan are exactly binomial (variance 0.989 vs 0.988, 0.369 vs 0.369), fewest
 *       valid attempts seen 46 / 41 / 43 of 48. So P(null plan) = sum P(story) q^48 = 9.3e-82 (<= 1.9e-81 with
 *       q at its z = 4 upper bound): the plugin's retry with a new seed (DerbyRules.draw) is never needed in practice.
 *     2nd place given the winner, 12 cells each 0.3297..0.3366 (SE 0.0020): chi-square 8.77 on 8 df, p = 0.36.
 *       Offsets +1/+2/+3 from the winner 73,308 / 73,876 / 72,816 (chi-square 7.67, 2 df, p = 0.022; the same 12
 *       cells regrouped, a 2.4-SE wobble; by position in the shuffled list 73,238 / 73,451 / 73,311). The symmetry
 *       mode below shows the planner has no lane preference to find; per story p = 0.047 / 0.994 / 0.036.
 *     RTP implied by the measured table, per player lane 0..3 (SE): natural 0.99968 / 1.00005 / 0.99971 / 1.00056
 *       (0.00087); c 10: 0.59961 / 0.60006 / 0.59966 / 0.60067 (0.00104); c 50: 1.66645 / 1.66670 / 1.66648 /
 *       1.66704 (0.00058) -- all within 0.7 SE of exact. Direct estimate, player on lane 0: 0.99790 +- 0.00261.
 *     stories 34.946 / 29.979 / 35.075 % (chi-square 0.57, p = 0.75).
 *     race ticks after GO: 1st always 116; 2nd 121..130, median 123, mean 123.90 (the score favours a close 2nd:
 *       21.9 % at 121 down to 3.6 % at 130); 3rd 125..145, median 133; last 129..162, median 143, mean 143.25,
 *       SD 5.88. resultTick always 154, endTick 243..276, median 257, mean 257.25 (a round lasts 12.15..13.8 s).
 *     every 16th plan (13,750): 10..23 quacks (mean 16.3), z never rises during the race, all ducks back at z 56.5
 *       at returnEndTick. The chosen attempt is uniform over 0..47.
 *   symmetry 10000 2 5: 20,000 plans, 960,000 candidates, 4,800,000 relabelled scores: 0 differ bitwise, the -inf
 *     rejections and the runner-up move with the labels (105 s). With the uniform shuffle and the i.i.d. tables
 *     this makes P(2nd = L | W != L) = 1/3 exact up to the generator; the Monte Carlo above agrees.
 *   cross 10000 2 build/classes/java/main ../Casino-work/mod/minevegas-casino-fabric-26.3-1.1.0.jar: 20,000 plans
 *     (plus seeds 0, -1, Long.MIN_VALUE, Long.MAX_VALUE) x 2 ports, 342,263,392 values equal bit for bit (405 s):
 *     the plugin's DuckDerbyRace, the mod's own bytecode and this transcription agree on null plans, ranks, ticks,
 *     quacks and every pose/progress/pace/gate sample. (The decompiled source writes 2^-53 as the float literal
 *     1.110223E-16F; that float is exactly 2^-53, the bytecode's double constant, so the port is exact.)
 */

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;
import java.util.function.IntUnaryOperator;

public final class DerbyRtp {

    // ================================================================================================================
    // Transcription of fr.minevegas.casino.DuckDerbyRace (MineVegas Casino 1.1.0)
    // ================================================================================================================

    static final int LANES = 4;
    static final int GO = 24;
    static final int RACE = 116;
    static final int RESULT_DELAY = 14;
    static final int HOLD = 44;
    static final int RETURN = 36;
    static final int GATE_CLOSE = 10;
    static final int SAMPLES = 480;
    static final int ATTEMPTS = 48;
    static final int COMEBACK = 0;
    static final int FRONT_RUNNER = 1;
    static final int PHOTO_FINISH = 2;
    static final String[] STORY = {"comeback", "front-runner", "photo finish"};
    /** Math.PI * 2 as the mod's bytecode holds it (6.283185307179586). */
    static final double TWO_PI = 6.283185307179586;
    /** 2.5 px / 46 px: the coasting cap as the mod's bytecode holds it. */
    static final double COAST_CAP = 0.05434782608695652;
    /** 2 pi / 6.5 px stride. */
    static final double STRIDE = 0.966643893412244;
    static final double[] VOICE = {1.14, 0.97, 1.26, 0.8};

    /** The planner's own generator: SplitMix64, unit() on the 2^-53 grid (the bytecode multiplies by 2^-53). */
    static final class SplitMix {
        private long state;

        SplitMix(long seed) {
            state = seed;
        }

        long nextLong() {
            state += 0x9E3779B97F4A7C15L;
            long z = state;
            z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
            z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
            return z ^ (z >>> 31);
        }

        double unit() {
            return (nextLong() >>> 11) * 0x1.0p-53;
        }

        double between(double lo, double hi) {
            return lo + (hi - lo) * unit();
        }

        int under(int n) {
            return (int) (unit() * n);
        }
    }

    static double smoothstep(double x) {
        double c = x < 0.0 ? 0.0 : (x > 1.0 ? 1.0 : x);
        return c * c * (3.0 - 2.0 * c);
    }

    record Quack(int tick, int lane, float volume, float pitch) {
    }

    /** What the planner did, for the statistics. */
    static final class Trace {
        int story;
        int valid;
        int spreadRejects;
        int runnerRejects;
        int bestAttempt;
    }

    /** One candidate race / the chosen plan. */
    static final class Race {
        final int winner;
        final long seed;
        final int[] finish;
        final double[][] table;
        final double[] coast = new double[LANES];
        final double[] tau = new double[LANES];
        final int[] rank = new int[LANES];
        private final long quackSeed;
        private List<Quack> quacks;
        /** -inf reason of score(): 0 none, 1 spread > 0.5 before t 104.4, 2 runner-up < 0.8 at t 116. */
        int rejected;

        Race(int winner, long seed, int[] finish, double[][] table, long quackSeed) {
            this.winner = winner;
            this.seed = seed;
            this.finish = finish;
            this.table = table;
            this.quackSeed = quackSeed;
            for (int lane = 0; lane < LANES; lane++) {
                int before = 0;
                for (int o = 0; o < LANES; o++) {
                    if (finish[o] < finish[lane]) {
                        before++;
                    }
                }
                rank[lane] = before;
                double[] t = table[lane];
                double speed = (t[SAMPLES] - t[SAMPLES - 1]) * 480.0 / finish[lane];
                coast[lane] = Math.min(COAST_CAP, speed * 5.0);
                tau[lane] = coast[lane] / speed;
            }
        }

        int finishTick(int lane) {
            return GO + finish[lane];
        }

        int lastFinishTick() {
            int m = 0;
            for (int f : finish) {
                m = Math.max(m, f);
            }
            return GO + m;
        }

        int resultTick() {
            return finishTick(winner) + RESULT_DELAY;
        }

        int returnStartTick() {
            return lastFinishTick() + HOLD;
        }

        int returnEndTick() {
            return returnStartTick() + RETURN;
        }

        int endTick() {
            return returnEndTick() + GATE_CLOSE;
        }

        int second() {
            for (int lane = 0; lane < LANES; lane++) {
                if (rank[lane] == 1) {
                    return lane;
                }
            }
            return -1;
        }

        /** raceProgress: race ticks after GO. */
        double at(int lane, double t) {
            if (t <= 0.0) {
                return 0.0;
            }
            int total = finish[lane];
            if (t < total) {
                double pos = t / total * 480.0;
                int i = Math.min(SAMPLES - 1, (int) pos);
                double frac = pos - i;
                double[] tb = table[lane];
                return tb[i] + (tb[i + 1] - tb[i]) * frac;
            }
            return 1.0 + coast[lane] * (1.0 - StrictMath.exp(-(t - total) / tau[lane]));
        }

        double progress(int lane, double elapsed) {
            int back = returnStartTick();
            if (elapsed < back) {
                return at(lane, elapsed - GO);
            }
            double resting = at(lane, back - GO);
            return resting * (1.0 - smoothstep((elapsed - back) / RETURN));
        }

        double pace(int lane, double elapsed) {
            return (progress(lane, elapsed + 0.5) - progress(lane, elapsed - 0.5)) / 1.0;
        }

        double gate(double elapsed) {
            double up = clamp01((elapsed - 6.0) / 10.0);
            double down = clamp01((elapsed - returnEndTick()) / 10.0);
            return (1.0 - (1.0 - up) * (1.0 - up)) * (1.0 - down * down);
        }

        /** {z, pitch, lift} in model px / radians. */
        double[] pose(int lane, double elapsed) {
            double p = progress(lane, elapsed);
            double z = 56.5 - p * 46.0;
            double pitch = 0.0;
            double lift = 0.0;
            int back = returnStartTick();
            if (!(elapsed < 10.0)) {
                if (elapsed < 24.0) {
                    double k = smoothstep((elapsed - 10.0) / 14.0);
                    double wave = StrictMath.sin(elapsed * 1.1 + lane * 1.9);
                    pitch = 0.07 * k * wave;
                    lift = 0.12 * k * Math.abs(wave);
                } else if (elapsed < back) {
                    double speed = Math.abs(pace(lane, elapsed)) * 46.0;
                    double amplitude = 0.3 * smoothstep(speed / 0.26);
                    pitch = amplitude * StrictMath.sin(p * 46.0 * STRIDE + lane * 1.3);
                    int crossed = finishTick(lane);
                    if (lane == winner && elapsed >= crossed) {
                        double since = elapsed - crossed;
                        double in = smoothstep(since / 6.0);
                        double fade = 1.0 - smoothstep((elapsed - (back - 14)) / 10.0);
                        pitch = pitch + 0.24 * in * fade;
                        if (since < 33.0) {
                            lift = lift + 2.0 * Math.abs(StrictMath.sin(Math.PI * since / 11.0))
                                    * (1.0 - smoothstep((since - 22.0) / 11.0));
                        }
                    }
                }
            }
            return new double[] {z, pitch, lift};
        }

        List<Quack> quacks() {
            if (quacks == null) {
                quacks = makeQuacks();
            }
            return quacks;
        }

        private List<Quack> makeQuacks() {
            SplitMix r = new SplitMix(quackSeed);
            List<Quack> first = new ArrayList<>();
            List<Quack> rest = new ArrayList<>();
            for (int lane = 0; lane < LANES; lane++) {
                if (rank[lane] == 0) {
                    first.add(new Quack(finishTick(lane), lane, 1.0f, voice(r, VOICE[lane] * 1.05)));
                }
                if (rank[lane] == 1) {
                    first.add(new Quack(finishTick(lane), lane, 0.75f, voice(r, VOICE[lane])));
                }
            }
            int[] order = {0, 1, 2, 3};
            for (int i = 3; i > 0; i--) {
                int j = r.under(i + 1);
                int s = order[i];
                order[i] = order[j];
                order[j] = s;
            }
            for (int k = 0; k < 3; k++) {
                int tick = 10 + 3 * k + r.under(3);
                first.add(new Quack(tick, order[k], 0.8f, voice(r, VOICE[order[k]])));
            }
            for (int lane = 0; lane < LANES; lane++) {
                double t = 24.0 + r.between(10.0, 30.0);
                double stop = GO + finish[lane] - 8;
                while (t < stop) {
                    float volume = (float) r.between(0.62, 0.85);
                    float pitch = voice(r, VOICE[lane]);
                    rest.add(new Quack((int) t, lane, volume, pitch));
                    double near = Math.min(1.0, at(lane, t - 24.0));
                    t += r.between(24.0, 42.0) * (1.0 - 0.35 * near);
                }
            }
            List<Quack> kept = new ArrayList<>();
            for (List<Quack> group : List.of(first, rest)) {
                for (Quack q : group) {
                    boolean free = true;
                    for (Quack o : kept) {
                        if (Math.abs(o.tick() - q.tick()) < 3) {
                            free = false;
                            break;
                        }
                    }
                    if (free) {
                        kept.add(q);
                    }
                }
            }
            kept.sort(Comparator.comparingInt(Quack::tick));
            return List.copyOf(kept);
        }

        private static float voice(SplitMix r, double base) {
            return (float) (base * r.between(0.96, 1.04));
        }
    }

    static double clamp01(double x) {
        return x < 0.0 ? 0.0 : (x > 1.0 ? 1.0 : x);
    }

    static Race plan(int winner, long seed) {
        return plan(winner, seed, null);
    }

    /** DuckDerbyRace.plan; null if all 48 attempts score -infinity. */
    static Race plan(int winner, long seed, Trace trace) {
        if (winner < 0 || winner >= LANES) {
            throw new IllegalArgumentException("lane " + winner);
        }
        SplitMix rng = new SplitMix(seed ^ (25214903917L * (winner + 1)));
        double dice = rng.unit();
        int story = dice < 0.35 ? COMEBACK : (dice < 0.65 ? FRONT_RUNNER : PHOTO_FINISH);
        SplitMix quackSeeds = new SplitMix(rng.nextLong());
        Race best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        int valid = 0;
        int spread = 0;
        int runner = 0;
        int bestAt = -1;
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            int[] finish = finishes(winner, rng);
            double[][] tables = new double[LANES][];
            for (int lane = 0; lane < LANES; lane++) {
                double lo = -0.65;
                double hi = 0.65;
                boolean champion = lane == winner;
                if (story == COMEBACK) {
                    if (champion) {
                        hi = -0.1;
                    } else {
                        lo = -0.25;
                    }
                } else if (story == FRONT_RUNNER) {
                    if (champion) {
                        lo = 0.1;
                    } else {
                        hi = 0.25;
                    }
                }
                tables[lane] = profile(rng, lo, hi);
            }
            Race candidate = new Race(winner, seed, finish, tables, quackSeeds.nextLong());
            double s = score(candidate, story);
            if (candidate.rejected == 0) {
                valid++;
            } else if (candidate.rejected == 1) {
                spread++;
            } else {
                runner++;
            }
            if (s > bestScore) {
                bestScore = s;
                best = candidate;
                bestAt = attempt;
            }
        }
        if (trace != null) {
            trace.story = story;
            trace.valid = valid;
            trace.spreadRejects = spread;
            trace.runnerRejects = runner;
            trace.bestAttempt = bestAt;
        }
        return best;
    }

    static int[] finishes(int winner, SplitMix rng) {
        int[] rest = new int[3];
        int n = 0;
        for (int lane = 0; lane < LANES; lane++) {
            if (lane != winner) {
                rest[n++] = lane;
            }
        }
        for (int i = 2; i > 0; i--) {
            int j = rng.under(i + 1);
            int s = rest[i];
            rest[i] = rest[j];
            rest[j] = s;
        }
        int[] finish = new int[LANES];
        finish[winner] = RACE;
        int gap = 5 + rng.under(10);
        for (int k = 0; k < 3; k++) {
            finish[rest[k]] = RACE + gap;
            gap += 4 + rng.under(12 + 2 * k); // the k = 2 draw is never used but is consumed
        }
        return finish;
    }

    static double[] profile(SplitMix rng, double slopeLo, double slopeHi) {
        double slope = rng.between(slopeLo, slopeHi);
        double f1 = rng.between(0.7, 1.5);
        double f2 = rng.between(1.9, 3.4);
        double a1 = rng.between(0.14, 0.36);
        double a2 = rng.between(0.05, 0.2);
        double p1 = rng.unit();
        double p2 = rng.unit();
        double bump = rng.between(-0.55, 0.85);
        double centre = rng.between(0.18, 0.82);
        double width = rng.between(0.06, 0.13);
        double[] cum = new double[SAMPLES + 1];
        double previous = 0.0;
        for (int i = 0; i <= SAMPLES; i++) {
            double x = i / 480.0;
            double z = (x - centre) / width;
            double s = slope * (1.0 - 2.0 * x) + a1 * StrictMath.sin(TWO_PI * (f1 * x + p1))
                    + a2 * StrictMath.sin(TWO_PI * (f2 * x + p2)) + bump * StrictMath.exp(-0.5 * z * z);
            s = s < -0.9 ? -0.9 : (s > 0.9 ? 0.9 : s);
            double v = StrictMath.exp(s) * (0.12 + 0.88 * smoothstep(x / 0.07));
            if (i > 0) {
                cum[i] = cum[i - 1] + 0.5 * (previous + v);
            }
            previous = v;
        }
        double total = cum[SAMPLES];
        for (int i = 0; i <= SAMPLES; i++) {
            cum[i] /= total;
        }
        cum[SAMPLES] = 1.0;
        return cum;
    }

    static double score(Race c, int story) {
        int leader = -1;
        int changes = 0;
        double[] p = new double[LANES];
        int[] midRank = new int[LANES];
        int led = 0;
        int late = 0;
        double deficit = 0.0;
        for (int t = 1; t <= RACE; t++) {
            double top = -1.0;
            double bottom = 2.0;
            int ahead = 0;
            for (int lane = 0; lane < LANES; lane++) {
                p[lane] = c.at(lane, t);
                if (p[lane] > top) {
                    top = p[lane];
                    ahead = lane;
                }
                bottom = Math.min(bottom, p[lane]);
            }
            if (t < 104.4 && top - bottom > 0.5) {
                c.rejected = 1;
                return Double.NEGATIVE_INFINITY;
            }
            if (t >= 34.8) {
                late++;
                if (ahead == c.winner) {
                    led++;
                }
            }
            if (t >= 63.800000000000004 && t <= 106.72) {
                deficit = Math.max(deficit, top - p[c.winner]);
            }
            if (t >= 12) {
                if (leader < 0) {
                    leader = ahead;
                } else if (ahead != leader && p[ahead] - p[leader] >= 0.012) {
                    leader = ahead;
                    changes++;
                }
                if (t == 58) {
                    for (int lane = 0; lane < LANES; lane++) {
                        int r = 0;
                        for (int o = 0; o < LANES; o++) {
                            if (p[o] > p[lane]) {
                                r++;
                            }
                        }
                        midRank[lane] = r;
                    }
                }
            }
        }
        double runnerUp = 0.0;
        for (int lane = 0; lane < LANES; lane++) {
            if (lane != c.winner) {
                runnerUp = Math.max(runnerUp, p[lane]);
            }
        }
        if (runnerUp < 0.8) {
            c.rejected = 2;
            return Double.NEGATIVE_INFINITY;
        }
        int reshuffled = 0;
        for (int lane = 0; lane < LANES; lane++) {
            if (midRank[lane] != c.rank[lane]) {
                reshuffled++;
            }
        }
        double s = 2.5 * Math.min(changes, 3) - 2.0 * Math.max(0, changes - 4) + 6.0 * runnerUp + 0.4 * reshuffled
                - (changes < 2 ? 6.0 : 0.0);
        double share = (double) led / late;
        if (story == COMEBACK) {
            s += deficit >= 0.03 ? 6.0 : -6.0;
        } else if (story == FRONT_RUNNER) {
            s += share >= 0.7 ? 6.0 : -8.0 * (0.7 - share);
        } else {
            s += runnerUp >= 0.94 ? 6.0 : -20.0 * (0.94 - runnerUp);
        }
        return s;
    }

    // ================================================================================================================
    // CasinoOdds.derby / expectedReturn (transcription)
    // ================================================================================================================

    static int oddsDerby(int lane, int percent, IntUnaryOperator nextInt) {
        if (lane < 0 || lane >= LANES || percent < 0 || percent > 100) {
            throw new IllegalArgumentException();
        }
        if (nextInt.applyAsInt(100) < percent) {
            return lane;
        }
        int other = nextInt.applyAsInt(3);
        return other < lane ? other : other + 1;
    }

    // ================================================================================================================
    // exact
    // ================================================================================================================

    static void exact() {
        selfCheck();
        System.out.println("RTP per item staked, exact. Natural odds and every group race: p = 1/4,");
        System.out.println("  RTP = 3/4 + (3/4)/3 = 1 exactly; win 1/4 (x3), push 1/4 (x1), loss 1/2; SD per item"
                + " sqrt(9/4 + 1/4 - 1) = sqrt(3/2) = " + fmt(Math.sqrt(1.5), 6));
        System.out.println();
        System.out.println("Forced chance c (solo only): enumeration of CasinoOdds.derby over nextInt(100) x nextInt(3)"
                + " for each player lane,");
        System.out.println("times P(2nd = player | player not winning) = 1/3 (lane symmetry), as exact fractions:");
        System.out.println("   c   P(win)  P(2nd)      RTP = (2c+25)/75     mod formula 3p+(1-p)/3   SD/item  group?");
        for (int c = 0; c <= 100; c++) {
            // count, over the 100 x 3 equally likely pairs (a, b), how often each lane wins, for every player lane
            for (int lane = 0; lane < LANES; lane++) {
                long wins = 0;
                long others = 0;
                for (int a = 0; a < 100; a++) {
                    for (int b = 0; b < 3; b++) {
                        int[] script = {a, b};
                        int[] k = {0};
                        int w = oddsDerby(lane, c, bound -> script[k[0]++]);
                        if (w == lane) {
                            wins++;
                        } else {
                            others++;
                        }
                    }
                }
                // RTP numerator over 300: 3 * wins + 1 * others / 3  ->  over 900: 9 wins + others
                BigInteger num = BigInteger.valueOf(9 * wins + others);
                BigInteger den = BigInteger.valueOf(900);
                BigInteger g = num.gcd(den);
                BigInteger expectNum = BigInteger.valueOf(2L * c + 25);
                BigInteger expectDen = BigInteger.valueOf(75);
                if (!num.multiply(expectDen).equals(expectNum.multiply(den))) {
                    throw new AssertionError("c " + c + " lane " + lane);
                }
                if (lane == 0 && (c % 5 == 0 || c == 1 || c == 24 || c == 26)) {
                    double p = c / 100.0;
                    double rtp = (2.0 * c + 25) / 75;
                    double mod = 3.0 * p + (1.0 - p) / 3.0;
                    double sd = Math.sqrt(9 * p + (1 - p) / 3 - rtp * rtp);
                    System.out.printf(Locale.ROOT, "%4d   %.2f   %.4f   %4s/%-3s = %.6f   %.15f   %.4f   %s%n", c, p,
                            (1 - p) / 3, num.divide(g), den.divide(g), rtp, mod, sd,
                            c == 25 ? "yes" : "no");
                }
            }
        }
        System.out.println("(all 101 x 4 lanes checked; break-even at c = 25; group races only at -1 or 25)");
    }

    static void selfCheck() {
        if (0x9E3779B97F4A7C15L != -7046029254386353131L || 0xBF58476D1CE4E5B9L != -4658895280553007687L
                || 0x94D049BB133111EBL != -7723592293110705685L || 0x1.0p-53 != 1.1102230246251565E-16
                || (double) 1.110223E-16F != 0x1.0p-53 || Math.PI * 2 != TWO_PI || 2.5 / 46 != COAST_CAP) {
            throw new AssertionError("constants");
        }
    }

    // ================================================================================================================
    // speed / Monte Carlo
    // ================================================================================================================

    static void speed(int n) {
        SplittableRandom r = new SplittableRandom(1);
        for (int i = 0; i < 60; i++) {
            plan(r.nextInt(4), r.nextLong());
        }
        long t0 = System.nanoTime();
        for (int i = 0; i < n; i++) {
            plan(r.nextInt(4), r.nextLong());
        }
        double s = (System.nanoTime() - t0) / 1e9;
        System.out.printf(Locale.ROOT, "%d plans in %.2f s: %.1f plans/s (one thread)%n", n, s, n / s);
    }

    /** Per-thread counters, merged at the end. */
    static final class Stats {
        long plans;
        long nulls;
        final long[] story = new long[3];
        final long[][] validHist = new long[3][ATTEMPTS + 1];
        final long[] attempts = new long[3];
        final long[] valid = new long[3];
        final long[] spread = new long[3];
        final long[] runner = new long[3];
        final long[][] second = new long[LANES][LANES]; // [winner][second]
        final long[][][] secondByStory = new long[3][LANES][3]; // [story][winner][offset-1]
        final long[][] finishHist = new long[LANES][200]; // [rank][race tick]
        final long[] endHist = new long[400];
        final long[] resultHist = new long[400];
        final long[] bestAttempt = new long[ATTEMPTS];
        final long[] quackCount = new long[64];
        long monotoneViolations;
        long backViolations;

        void add(Stats o) {
            plans += o.plans;
            nulls += o.nulls;
            for (int s = 0; s < 3; s++) {
                story[s] += o.story[s];
                attempts[s] += o.attempts[s];
                valid[s] += o.valid[s];
                spread[s] += o.spread[s];
                runner[s] += o.runner[s];
                for (int v = 0; v <= ATTEMPTS; v++) {
                    validHist[s][v] += o.validHist[s][v];
                }
                for (int w = 0; w < LANES; w++) {
                    for (int k = 0; k < 3; k++) {
                        secondByStory[s][w][k] += o.secondByStory[s][w][k];
                    }
                }
            }
            for (int w = 0; w < LANES; w++) {
                for (int l = 0; l < LANES; l++) {
                    second[w][l] += o.second[w][l];
                }
                for (int t = 0; t < 200; t++) {
                    finishHist[w][t] += o.finishHist[w][t];
                }
            }
            for (int t = 0; t < 400; t++) {
                endHist[t] += o.endHist[t];
                resultHist[t] += o.resultHist[t];
            }
            for (int a = 0; a < ATTEMPTS; a++) {
                bestAttempt[a] += o.bestAttempt[a];
            }
            for (int q = 0; q < 64; q++) {
                quackCount[q] += o.quackCount[q];
            }
            monotoneViolations += o.monotoneViolations;
            backViolations += o.backViolations;
        }
    }

    static Stats run(long plans, long seed) {
        Stats st = new Stats();
        SplittableRandom r = new SplittableRandom(seed);
        Trace tr = new Trace();
        for (long i = 0; i < plans; i++) {
            int winner = r.nextInt(LANES);
            long s = r.nextLong();
            Race race = plan(winner, s, tr);
            st.plans++;
            st.story[tr.story]++;
            st.attempts[tr.story] += ATTEMPTS;
            st.valid[tr.story] += tr.valid;
            st.spread[tr.story] += tr.spreadRejects;
            st.runner[tr.story] += tr.runnerRejects;
            st.validHist[tr.story][tr.valid]++;
            if (race == null) {
                st.nulls++;
                continue;
            }
            st.bestAttempt[tr.bestAttempt]++;
            int second = race.second();
            st.second[winner][second]++;
            st.secondByStory[tr.story][winner][Math.floorMod(second - winner, LANES) - 1]++;
            for (int lane = 0; lane < LANES; lane++) {
                st.finishHist[race.rank[lane]][race.finish[lane]]++;
            }
            st.endHist[race.endTick()]++;
            st.resultHist[race.resultTick()]++;
            if (i % 16 == 0) { // spot checks on every 16th plan: quack count, monotone z, back home
                st.quackCount[Math.min(63, race.quacks().size())]++;
                for (int lane = 0; lane < LANES; lane++) {
                    double last = Double.POSITIVE_INFINITY;
                    for (int e = GO; e < race.returnStartTick(); e++) {
                        double z = race.pose(lane, e)[0];
                        if (z > last) {
                            st.monotoneViolations++;
                        }
                        last = z;
                    }
                    if (race.pose(lane, race.returnEndTick())[0] != 56.5) {
                        st.backViolations++;
                    }
                }
            }
        }
        return st;
    }

    static void mc(long perThread, int threads, long seed) throws InterruptedException {
        selfCheck();
        Stats[] parts = new Stats[threads];
        Thread[] th = new Thread[threads];
        long t0 = System.nanoTime();
        for (int k = 0; k < threads; k++) {
            int kk = k;
            th[k] = new Thread(() -> parts[kk] = run(perThread, seed + kk));
            th[k].start();
        }
        for (Thread t : th) {
            t.join();
        }
        double secs = (System.nanoTime() - t0) / 1e9;
        Stats st = new Stats();
        for (Stats p : parts) {
            st.add(p);
        }
        long n = st.plans;
        long ok = n - st.nulls;
        System.out.printf(Locale.ROOT, "Monte Carlo: %,d plans (%d threads x %,d, seeds %d..%d) in %.0f s"
                + " = %.1f plans/s (%.1f per thread)%n", n, threads, perThread, seed, seed + threads - 1, secs,
                n / secs, n / secs / threads);

        // ---- null plans
        System.out.printf(Locale.ROOT, "%nnull plans: %d of %,d", st.nulls, n);
        if (st.nulls == 0) {
            System.out.printf(Locale.ROOT, " -> 95 %% upper bound (exact, 1 - 0.05^(1/n)) %.3g%n",
                    1 - Math.pow(0.05, 1.0 / n));
        } else {
            System.out.printf(Locale.ROOT, " = %.3g%n", (double) st.nulls / n);
        }
        System.out.println("attempts per story (given the story the 48 attempts are i.i.d.,"
                + " so valid ~ Binomial(48, 1-q)):");
        double nullModel = 0;
        double nullModelHi = 0;
        for (int s = 0; s < 3; s++) {
            double q = 1 - (double) st.valid[s] / st.attempts[s];
            double se = Math.sqrt(q * (1 - q) / st.attempts[s]);
            double pi = (double) st.story[s] / n;
            // Wilson score upper bound with z = 4 (also sensible when no attempt was rejected)
            double a = st.attempts[s];
            double z2 = 16;
            double qHi = (q + z2 / (2 * a) + 4 * Math.sqrt(q * (1 - q) / a + z2 / (4 * a * a))) / (1 + z2 / a);
            nullModel += pi * Math.pow(q, ATTEMPTS);
            nullModelHi += pi * Math.pow(qHi, ATTEMPTS);
            // dispersion of the valid count vs the binomial
            double mean = 0;
            double sq = 0;
            int min = ATTEMPTS;
            for (int v = 0; v <= ATTEMPTS; v++) {
                mean += v * (double) st.validHist[s][v];
                sq += (double) v * v * st.validHist[s][v];
                if (st.validHist[s][v] > 0) {
                    min = Math.min(min, v);
                }
            }
            mean /= st.story[s];
            double var = sq / st.story[s] - mean * mean;
            System.out.printf(Locale.ROOT, "  %-12s rejected %.5f +- %.5f (spread > 0.5: %.5f, runner-up < 0.8: %.5f);"
                    + " valid per plan mean %.3f, var %.3f (binomial %.3f), min %d; q^48 = %.3g%n", STORY[s], q, se,
                    (double) st.spread[s] / st.attempts[s], (double) st.runner[s] / st.attempts[s], mean, var,
                    ATTEMPTS * q * (1 - q), min, Math.pow(q, ATTEMPTS));
        }
        System.out.printf(Locale.ROOT, "  model P(null plan) = sum_s P(s) q_s^48 = %.3g (q_s at its z = 4 Wilson upper"
                + " bound: %.3g); the plugin's retry is needed that often, a 2nd retry ~ its square%n", nullModel,
                nullModelHi);

        // ---- 2nd place
        System.out.println();
        System.out.println("2nd place given the winner (rows winner 0..3 = yellow/red/white/black,"
                + " columns lane 0..3):");
        double chi = 0;
        for (int w = 0; w < LANES; w++) {
            long row = 0;
            for (int l = 0; l < LANES; l++) {
                row += st.second[w][l];
            }
            StringBuilder sb = new StringBuilder();
            for (int l = 0; l < LANES; l++) {
                if (l == w) {
                    if (st.second[w][l] != 0) {
                        throw new AssertionError("winner in 2nd");
                    }
                    sb.append(String.format(Locale.ROOT, "%23s", "-"));
                } else {
                    double f = (double) st.second[w][l] / row;
                    double e = row / 3.0;
                    chi += (st.second[w][l] - e) * (st.second[w][l] - e) / e;
                    sb.append(String.format(Locale.ROOT, "%,10d %.4f+-%.4f", st.second[w][l], f,
                            Math.sqrt(f * (1 - f) / row)));
                }
            }
            System.out.println("  W" + w + sb);
        }
        System.out.printf(Locale.ROOT, "  chi-square vs 1/3 each: %.2f on 8 df, p = %.3f%n", chi, chiSquareSf(chi, 8));
        long[] off = new long[3];
        for (int s = 0; s < 3; s++) {
            long[] o = new long[3];
            for (int w = 0; w < LANES; w++) {
                for (int k = 0; k < 3; k++) {
                    o[k] += st.secondByStory[s][w][k];
                    off[k] += st.secondByStory[s][w][k];
                }
            }
            double c2 = chi3(o);
            System.out.printf(Locale.ROOT, "  %-12s offsets +1/+2/+3: %,d / %,d / %,d, chi-square %.2f (2 df),"
                    + " p = %.3f%n", STORY[s], o[0], o[1], o[2], c2, Math.exp(-c2 / 2));
        }
        double c2 = chi3(off);
        System.out.printf(Locale.ROOT, "  all          offsets +1/+2/+3: %,d / %,d / %,d, chi-square %.2f (2 df),"
                + " p = %.3f%n", off[0], off[1], off[2], c2, Math.exp(-c2 / 2));

        // ---- RTP implied by the measured 2nd-place table
        System.out.println();
        System.out.println("RTP per item from the measured P(2nd = L | W) (winner draw exact):");
        for (int c : new int[] {-1, 0, 10, 20, 25, 30, 50}) {
            StringBuilder sb = new StringBuilder();
            for (int lane = 0; lane < LANES; lane++) {
                // P(2nd = lane | W != lane), W uniform over the other three lanes (nextInt(3), or nextInt(4)
                // given W != lane)
                double pr = 0;
                double var = 0;
                for (int w = 0; w < LANES; w++) {
                    if (w == lane) {
                        continue;
                    }
                    long row = 0;
                    for (int l = 0; l < LANES; l++) {
                        row += st.second[w][l];
                    }
                    double f = (double) st.second[w][lane] / row;
                    pr += f / 3;
                    var += f * (1 - f) / row / 9;
                }
                double p = c < 0 ? 0.25 : c / 100.0;
                double rtp = 3 * p + (1 - p) * pr;
                double se = (1 - p) * Math.sqrt(var);
                sb.append(String.format(Locale.ROOT, "  L%d %.5f+-%.5f", lane, rtp, se));
            }
            double exactRtp = c < 0 ? 1.0 : (2.0 * c + 25) / 75;
            System.out.printf(Locale.ROOT, "  c %3s exact %.5f:%s%n", c < 0 ? "-1" : Integer.toString(c), exactRtp, sb);
        }
        // the direct estimator for natural odds: a player on lane 0
        long w0 = 0;
        long s0 = 0;
        for (int l = 0; l < LANES; l++) {
            w0 += st.second[0][l];
        }
        for (int w = 0; w < LANES; w++) {
            s0 += st.second[w][0];
        }
        double direct = (3.0 * w0 + s0) / ok;
        System.out.printf(Locale.ROOT, "  natural odds, player always on lane 0, direct mean over %,d races:"
                + " %.5f +- %.5f%n", ok, direct, Math.sqrt(1.5 / ok));

        // ---- stories
        System.out.println();
        double chiS = 0;
        double[] expect = {0.35, 0.30, 0.35};
        for (int s = 0; s < 3; s++) {
            double e = expect[s] * n;
            chiS += (st.story[s] - e) * (st.story[s] - e) / e;
        }
        System.out.printf(Locale.ROOT, "stories: comeback %,d (%.3f %%), front-runner %,d (%.3f %%), photo finish %,d"
                + " (%.3f %%); chi-square vs 35/30/35 %.2f (2 df), p = %.3f%n", st.story[0], 100.0 * st.story[0] / n,
                st.story[1], 100.0 * st.story[1] / n, st.story[2], 100.0 * st.story[2] / n, chiS, Math.exp(-chiS / 2));

        // ---- ticks
        System.out.println();
        String[] place = {"1st", "2nd", "3rd", "4th"};
        for (int k = 0; k < LANES; k++) {
            System.out.printf(Locale.ROOT, "%s finish (race ticks after GO): %s%n", place[k],
                    summary(st.finishHist[k], ok));
        }
        System.out.printf(Locale.ROOT, "resultTick: %s%n", summary(st.resultHist, ok));
        System.out.printf(Locale.ROOT, "endTick:    %s%n", summary(st.endHist, ok));
        System.out.println("  last finisher tick = endTick - 114 (race ticks); endTick histogram:");
        System.out.println("  " + hist(st.endHist));
        System.out.println("  2nd-place race tick histogram: " + hist(st.finishHist[1]));
        System.out.println("  3rd: " + hist(st.finishHist[2]));
        System.out.println("  4th: " + hist(st.finishHist[3]));
        long sampled = 0;
        for (long q : st.quackCount) {
            sampled += q;
        }
        System.out.printf(Locale.ROOT, "%nspot checks on %,d plans: quacks per plan %s; z rising during the race: %d,"
                + " not back at z 56.5 at returnEnd: %d%n", sampled, summary(st.quackCount, sampled),
                st.monotoneViolations, st.backViolations);
        System.out.println("best attempt index (0..47): " + hist(st.bestAttempt));
    }

    static double chi3(long[] o) {
        double tot = o[0] + o[1] + o[2];
        double c = 0;
        for (long x : o) {
            c += (x - tot / 3) * (x - tot / 3) / (tot / 3);
        }
        return c;
    }

    /** Survival function of chi-square with an even number of degrees of freedom. */
    static double chiSquareSf(double x, int df) {
        double term = 1;
        double sum = 1;
        for (int i = 1; i < df / 2; i++) {
            term *= x / 2 / i;
            sum += term;
        }
        return Math.exp(-x / 2) * sum;
    }

    static String summary(long[] h, long n) {
        int min = -1;
        int max = -1;
        double mean = 0;
        double sq = 0;
        long cum = 0;
        int median = -1;
        for (int i = 0; i < h.length; i++) {
            if (h[i] == 0) {
                continue;
            }
            if (min < 0) {
                min = i;
            }
            max = i;
            mean += (double) i * h[i];
            sq += (double) i * i * h[i];
            cum += h[i];
            if (median < 0 && cum * 2 >= n) {
                median = i;
            }
        }
        mean /= n;
        return String.format(Locale.ROOT, "min %d, median %d, mean %.3f, max %d, SD %.3f", min, median, mean, max,
                Math.sqrt(sq / n - mean * mean));
    }

    static String hist(long[] h) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < h.length; i++) {
            if (h[i] != 0) {
                sb.append(i).append(':').append(h[i]).append(' ');
            }
        }
        return sb.toString().trim();
    }

    static String fmt(double v, int digits) {
        return String.format(Locale.ROOT, "%." + digits + "f", v);
    }

    // ================================================================================================================
    // symmetry: score() is invariant under relabelling the non-winner lanes
    // ================================================================================================================

    static final int[][] PERMS3 = {{0, 1, 2}, {0, 2, 1}, {1, 0, 2}, {1, 2, 0}, {2, 0, 1}, {2, 1, 0}};

    /**
     * Step (3) of the argument in the header, checked directly: every candidate of the planner is scored again under
     * the 5 other relabellings of its three non-winner lanes (finish time, progress table, and with them rank, coast
     * and tau move together). The score must be bit-identical, the -inf rejections equal, and the runner-up must move
     * with the relabelling. Same candidates as plan() (same generator stream), so the planner itself is unchanged.
     */
    static void symmetry(long perThread, int threads, long seed) throws InterruptedException {
        selfCheck();
        long[][] counts = new long[threads][3]; // candidates, relabelled scores compared, mismatches
        Thread[] th = new Thread[threads];
        long t0 = System.nanoTime();
        for (int k = 0; k < threads; k++) {
            int kk = k;
            th[k] = new Thread(() -> {
                SplittableRandom r = new SplittableRandom(seed + kk);
                for (long i = 0; i < perThread; i++) {
                    int winner = r.nextInt(LANES);
                    long s = r.nextLong();
                    SplitMix rng = new SplitMix(s ^ (25214903917L * (winner + 1)));
                    double dice = rng.unit();
                    int story = dice < 0.35 ? COMEBACK : (dice < 0.65 ? FRONT_RUNNER : PHOTO_FINISH);
                    SplitMix quackSeeds = new SplitMix(rng.nextLong());
                    int[] rest = new int[3];
                    int n = 0;
                    for (int lane = 0; lane < LANES; lane++) {
                        if (lane != winner) {
                            rest[n++] = lane;
                        }
                    }
                    for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
                        int[] finish = finishes(winner, rng);
                        double[][] tables = new double[LANES][];
                        for (int lane = 0; lane < LANES; lane++) {
                            boolean champion = lane == winner;
                            double lo = story == COMEBACK && !champion ? -0.25
                                    : (story == FRONT_RUNNER && champion ? 0.1 : -0.65);
                            double hi = story == COMEBACK && champion ? -0.1
                                    : (story == FRONT_RUNNER && !champion ? 0.25 : 0.65);
                            tables[lane] = profile(rng, lo, hi);
                        }
                        Race c = new Race(winner, s, finish, tables, quackSeeds.nextLong());
                        double base = score(c, story);
                        counts[kk][0]++;
                        for (int pi = 1; pi < PERMS3.length; pi++) {
                            int[] map = new int[LANES]; // old lane -> new lane
                            map[winner] = winner;
                            for (int m = 0; m < 3; m++) {
                                map[rest[m]] = rest[PERMS3[pi][m]];
                            }
                            int[] f2 = new int[LANES];
                            double[][] t2 = new double[LANES][];
                            for (int lane = 0; lane < LANES; lane++) {
                                f2[map[lane]] = finish[lane];
                                t2[map[lane]] = tables[lane];
                            }
                            Race d = new Race(winner, s, f2, t2, 0L);
                            double other = score(d, story);
                            counts[kk][1]++;
                            if (Double.doubleToRawLongBits(base) != Double.doubleToRawLongBits(other)
                                    || c.rejected != d.rejected || map[c.second()] != d.second()) {
                                counts[kk][2]++;
                            }
                        }
                    }
                }
            });
            th[k].start();
        }
        for (Thread t : th) {
            t.join();
        }
        long[] tot = new long[3];
        for (long[] c : counts) {
            for (int i = 0; i < 3; i++) {
                tot[i] += c[i];
            }
        }
        System.out.printf(Locale.ROOT, "symmetry: %,d plans, %,d candidates, %,d relabelled scores compared,"
                + " %d differ (bitwise), %.0f s%n", perThread * threads, tot[0], tot[1], tot[2],
                (System.nanoTime() - t0) / 1e9);
        if (tot[2] != 0) {
            throw new AssertionError("score is not symmetric in the non-winner lanes");
        }
    }

    // ================================================================================================================
    // golden values for DuckDerbyRaceTest
    // ================================================================================================================

    static final long[][] GOLDEN = {{0, 0L}, {1, 20261107L}, {2, -1L}, {3, Long.MIN_VALUE}, {0, 0x5DEECE66DL},
        {3, 123456789L}, {2, 1L}, {2, 2L}, {2, 3L}, {2, 4L}, {2, 5L}, {2, 6L}, {2, 7L}, {2, 8L}};
    static final double[] POSE_TIMES = {0.0, 12.5, 23.0, 24.0, 60.25, 100.0, 139.5, 140.0, 150.0, 175.5};

    static void golden() {
        selfCheck();
        for (long[] g : GOLDEN) {
            int w = (int) g[0];
            long seed = g[1];
            Trace tr = new Trace();
            Race r = plan(w, seed, tr);
            System.out.printf(Locale.ROOT, "// plan(%d, %dL): story %s, %d valid attempts, best attempt %d%n", w, seed,
                    STORY[tr.story], tr.valid, tr.bestAttempt);
            if (r == null) {
                System.out.println("null");
                continue;
            }
            StringBuilder ft = new StringBuilder();
            StringBuilder rk = new StringBuilder();
            for (int lane = 0; lane < LANES; lane++) {
                ft.append(lane == 0 ? "" : ", ").append(r.finishTick(lane));
                rk.append(lane == 0 ? "" : ", ").append(r.rank[lane]);
            }
            System.out.printf(Locale.ROOT, "finishTicks {%s}, ranks {%s}, second %d, result %d, returnStart %d,"
                    + " returnEnd %d, end %d%n", ft, rk, r.second(), r.resultTick(), r.returnStartTick(),
                    r.returnEndTick(), r.endTick());
            double back = r.returnStartTick();
            for (double e : new double[] {POSE_TIMES[0], POSE_TIMES[1], POSE_TIMES[2], POSE_TIMES[3], POSE_TIMES[4],
                POSE_TIMES[5], POSE_TIMES[6], POSE_TIMES[7], POSE_TIMES[8], POSE_TIMES[9], back - 9.0, back + 18.0,
                r.returnEndTick()}) {
                for (int lane = 0; lane < LANES; lane++) {
                    double[] p = r.pose(lane, e);
                    System.out.printf(Locale.ROOT, "pose(%d, %s) = {%s, %s, %s}  progress %s  pace %s%n", lane, e,
                            Double.toString(p[0]), Double.toString(p[1]), Double.toString(p[2]),
                            Double.toString(r.progress(lane, e)), Double.toString(r.pace(lane, e)));
                }
            }
            for (double e : new double[] {0, 6, 8.5, 11, 16, 30, r.returnEndTick(), r.returnEndTick() + 4.5,
                r.endTick()}) {
                System.out.printf(Locale.ROOT, "gate(%s) = %s%n", e, Double.toString(r.gate(e)));
            }
            StringBuilder q = new StringBuilder();
            for (Quack k : r.quacks()) {
                q.append(String.format(Locale.ROOT, "{%d, %d, %sf, %sf}, ", k.tick(), k.lane(),
                        Float.toString(k.volume()), Float.toString(k.pitch())));
            }
            System.out.println("quacks " + r.quacks().size() + ": " + q);
        }
    }

    // ================================================================================================================
    // cross-check against compiled DuckDerbyRace classes (the plugin's port, the mod's own bytecode)
    // ================================================================================================================

    /** Reflection view of one compiled DuckDerbyRace. */
    static final class Port {
        final String name;
        final Method plan;
        final Method rank;
        final Method finishTick;
        final Method resultTick;
        final Method returnStartTick;
        final Method returnEndTick;
        final Method endTick;
        final Method winner;
        final Method seed;
        final Method quacks;
        final Method progress;
        final Method pace;
        final Method gate;
        final Method pose;
        final Constructor<?> poseNew;
        final Field z;
        final Field pitch;
        final Field lift;
        final Method qTick;
        final Method qLane;
        final Method qVolume;
        final Method qPitch;

        Port(String root) throws Exception {
            URLClassLoader cl = new URLClassLoader(new URL[] {new File(root).toURI().toURL()},
                    ClassLoader.getPlatformClassLoader());
            Class<?> race = null;
            for (String fqn : List.of("de.tigges.kaboofcasino.game.derby.DuckDerbyRace",
                    "fr.minevegas.casino.DuckDerbyRace")) {
                try {
                    race = Class.forName(fqn, true, cl);
                    break;
                } catch (ClassNotFoundException e) {
                    // next
                }
            }
            if (race == null) {
                throw new IllegalArgumentException("no DuckDerbyRace in " + root);
            }
            name = race.getName() + " @ " + root;
            Class<?> planCls = Class.forName(race.getName() + "$Plan", true, cl);
            Class<?> poseCls = Class.forName(race.getName() + "$Pose", true, cl);
            Class<?> quackCls = Class.forName(race.getName() + "$Quack", true, cl);
            plan = race.getMethod("plan", int.class, long.class);
            rank = planCls.getMethod("rank", int.class);
            finishTick = planCls.getMethod("finishTick", int.class);
            resultTick = planCls.getMethod("resultTick");
            returnStartTick = planCls.getMethod("returnStartTick");
            returnEndTick = planCls.getMethod("returnEndTick");
            endTick = planCls.getMethod("endTick");
            winner = planCls.getMethod("winner");
            seed = planCls.getMethod("seed");
            quacks = planCls.getMethod("quacks");
            progress = planCls.getMethod("progress", int.class, double.class);
            pace = planCls.getMethod("pace", int.class, double.class);
            gate = planCls.getMethod("gate", double.class);
            pose = planCls.getMethod("pose", int.class, double.class, poseCls);
            poseNew = poseCls.getDeclaredConstructor();
            z = poseCls.getField("z");
            pitch = poseCls.getField("pitch");
            lift = poseCls.getField("lift");
            qTick = quackCls.getMethod("tick");
            qLane = quackCls.getMethod("lane");
            qVolume = quackCls.getMethod("volume");
            qPitch = quackCls.getMethod("pitch");
        }
    }

    static void cross(long perThread, int threads, List<String> roots) throws Exception {
        selfCheck();
        List<Port> ports = new ArrayList<>();
        for (String root : roots) {
            ports.add(new Port(root));
        }
        long[] compared = new long[threads];
        long[] values = new long[threads];
        Throwable[] failure = new Throwable[1];
        Thread[] th = new Thread[threads];
        long t0 = System.nanoTime();
        for (int k = 0; k < threads; k++) {
            int kk = k;
            th[k] = new Thread(() -> {
                try {
                    SplittableRandom r = new SplittableRandom(777 + kk);
                    for (long i = 0; i < perThread; i++) {
                        int w = r.nextInt(LANES);
                        long s = switch ((int) (i % 1000)) {
                            case 0 -> kk == 0 ? 0L : Long.MIN_VALUE;
                            case 1 -> kk == 0 ? -1L : Long.MAX_VALUE;
                            default -> r.nextLong();
                        };
                        for (Port p : ports) {
                            values[kk] += compare(plan(w, s), p, w, s);
                        }
                        compared[kk]++;
                    }
                } catch (Throwable t) {
                    synchronized (failure) {
                        failure[0] = t;
                    }
                }
            });
            th[k].start();
        }
        for (Thread t : th) {
            t.join();
        }
        if (failure[0] != null) {
            throw new RuntimeException(failure[0]);
        }
        long n = 0;
        long v = 0;
        for (int k = 0; k < threads; k++) {
            n += compared[k];
            v += values[k];
        }
        System.out.printf(Locale.ROOT, "cross-check OK: %,d plans x %d ports, %,d values equal bit for bit, %.0f s%n",
                n, ports.size(), v, (System.nanoTime() - t0) / 1e9);
        for (Port p : ports) {
            System.out.println("  " + p.name);
        }
    }

    static long compare(Race mine, Port port, int w, long s) throws Exception {
        Object theirs = port.plan.invoke(null, w, s);
        String where = port.name + " plan(" + w + ", " + s + "L)";
        if ((mine == null) != (theirs == null)) {
            throw new AssertionError(where + ": null " + (mine == null) + " vs " + (theirs == null));
        }
        if (mine == null) {
            return 1;
        }
        long n = 0;
        n += eq(where + " winner", mine.winner, (int) port.winner.invoke(theirs));
        n += eq(where + " seed", mine.seed, (long) port.seed.invoke(theirs));
        for (int lane = 0; lane < LANES; lane++) {
            n += eq(where + " rank " + lane, mine.rank[lane], (int) port.rank.invoke(theirs, lane));
            n += eq(where + " finishTick " + lane, mine.finishTick(lane), (int) port.finishTick.invoke(theirs, lane));
        }
        n += eq(where + " resultTick", mine.resultTick(), (int) port.resultTick.invoke(theirs));
        n += eq(where + " returnStartTick", mine.returnStartTick(), (int) port.returnStartTick.invoke(theirs));
        n += eq(where + " returnEndTick", mine.returnEndTick(), (int) port.returnEndTick.invoke(theirs));
        n += eq(where + " endTick", mine.endTick(), (int) port.endTick.invoke(theirs));
        List<?> q = (List<?>) port.quacks.invoke(theirs);
        List<Quack> mq = mine.quacks();
        n += eq(where + " quacks", mq.size(), q.size());
        for (int i = 0; i < mq.size(); i++) {
            Object o = q.get(i);
            Quack m = mq.get(i);
            n += eq(where + " quack tick " + i, m.tick(), (int) port.qTick.invoke(o));
            n += eq(where + " quack lane " + i, m.lane(), (int) port.qLane.invoke(o));
            n += eq(where + " quack volume " + i, Float.floatToRawIntBits(m.volume()),
                    Float.floatToRawIntBits((float) port.qVolume.invoke(o)));
            n += eq(where + " quack pitch " + i, Float.floatToRawIntBits(m.pitch()),
                    Float.floatToRawIntBits((float) port.qPitch.invoke(o)));
        }
        Object pose = port.poseNew.newInstance();
        int end = mine.endTick() + 6;
        for (int step = -4; step <= 4 * end; step++) {
            // quarter ticks plus an irrational offset every other sample: every branch of pose/progress/gate
            double e = step * 0.25 + ((step & 1) == 0 ? 0.0 : 0.0137);
            n += eq(where + " gate " + e, Double.doubleToRawLongBits(mine.gate(e)),
                    Double.doubleToRawLongBits((double) port.gate.invoke(theirs, e)));
            for (int lane = 0; lane < LANES; lane++) {
                if ((step & 3) == 0 || lane == mine.winner) {
                    double[] mp = mine.pose(lane, e);
                    port.pose.invoke(theirs, lane, e, pose);
                    n += eq(where + " z " + lane + "@" + e, Double.doubleToRawLongBits(mp[0]),
                            Double.doubleToRawLongBits(port.z.getDouble(pose)));
                    n += eq(where + " pitch " + lane + "@" + e, Double.doubleToRawLongBits(mp[1]),
                            Double.doubleToRawLongBits(port.pitch.getDouble(pose)));
                    n += eq(where + " lift " + lane + "@" + e, Double.doubleToRawLongBits(mp[2]),
                            Double.doubleToRawLongBits(port.lift.getDouble(pose)));
                    n += eq(where + " pace " + lane + "@" + e, Double.doubleToRawLongBits(mine.pace(lane, e)),
                            Double.doubleToRawLongBits((double) port.pace.invoke(theirs, lane, e)));
                }
            }
        }
        return n;
    }

    static int eq(String what, long a, long b) {
        if (a != b) {
            throw new AssertionError(what + ": " + a + " vs " + b);
        }
        return 1;
    }

    // ================================================================================================================

    public static void main(String[] args) throws Exception {
        String mode = args.length == 0 ? "exact" : args[0];
        switch (mode) {
            case "exact" -> exact();
            case "speed" -> speed(args.length > 1 ? Integer.parseInt(args[1]) : 300);
            case "mc" -> mc(Long.parseLong(args[1]), Integer.parseInt(args[2]), Long.parseLong(args[3]));
            case "golden" -> golden();
            case "symmetry" -> symmetry(Long.parseLong(args[1]), Integer.parseInt(args[2]), Long.parseLong(args[3]));
            case "cross" -> cross(Long.parseLong(args[1]), Integer.parseInt(args[2]),
                    List.of(args).subList(3, args.length));
            default -> throw new IllegalArgumentException("modes: exact | speed n | mc perThread threads seed | golden"
                    + " | symmetry perThread threads seed | cross perThread threads root...");
        }
    }
}
