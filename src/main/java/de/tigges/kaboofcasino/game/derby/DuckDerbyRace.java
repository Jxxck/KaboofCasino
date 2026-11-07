// Verbatim port of the MineVegas Casino class of the same name (MIT, byMehd,
// https://www.curseforge.com/minecraft/mc-mods/minevegas-casino): decompiled source, package renamed. Pure.
package de.tigges.kaboofcasino.game.derby;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DuckDerbyRace {
   public static final int LANES = 4;
   public static final int GATE_START = 6;
   public static final int GATE_TICKS = 10;
   public static final int GO = 24;
   public static final int RACE = 116;
   public static final int RESULT_DELAY = 14;
   public static final int HOLD = 44;
   public static final int RETURN = 36;
   public static final int GATE_CLOSE = 10;
   public static final double STRIDE_PX = 6.5;
   public static final double PITCH_MAX = 0.3;
   public static final double FULL_PACE_PX = 0.26;
   private static final int SAMPLES = 480;
   private static final int ATTEMPTS = 48;
   private static final double COAST_TICKS = 5.0;
   private static final int COMEBACK = 0;
   private static final int FRONT_RUNNER = 1;
   private static final int PHOTO_FINISH = 2;

   private DuckDerbyRace() {
   }

   private static double smooth(double x) {
      double c = Math.max(0.0, Math.min(1.0, x));
      return c * c * (3.0 - 2.0 * c);
   }

   public static DuckDerbyRace.Plan plan(int winner, long seed) {
      if (winner >= 0 && winner < 4) {
         DuckDerbyRace.Rng rng = new DuckDerbyRace.Rng(seed ^ 25214903917L * (long)(winner + 1));
         double dice = rng.next();
         int story = dice < 0.35 ? 0 : (dice < 0.65 ? 1 : 2);
         DuckDerbyRace.Plan best = null;
         double bestScore = Double.NEGATIVE_INFINITY;
         DuckDerbyRace.Rng quackRng = new DuckDerbyRace.Rng(rng.nextLong());

         for (int attempt = 0; attempt < 48; attempt++) {
            int[] finish = finishingTimes(winner, rng);
            double[][] table = new double[4][];

            for (int lane = 0; lane < 4; lane++) {
               boolean champion = lane == winner;
               double lo = -0.65;
               double hi = 0.65;
               if (story == 0) {
                  if (champion) {
                     hi = -0.1;
                  } else {
                     lo = -0.25;
                  }
               } else if (story == 1) {
                  if (champion) {
                     lo = 0.1;
                  } else {
                     hi = 0.25;
                  }
               }

               table[lane] = progressTable(rng, lo, hi);
            }

            DuckDerbyRace.Plan candidate = new DuckDerbyRace.Plan(winner, seed, finish, table, new DuckDerbyRace.Rng(quackRng.nextLong()));
            double score = score(candidate, story);
            if (score > bestScore) {
               bestScore = score;
               best = candidate;
            }
         }

         return best;
      } else {
         throw new IllegalArgumentException("lane " + winner);
      }
   }

   private static int[] finishingTimes(int winner, DuckDerbyRace.Rng rng) {
      int[] others = new int[3];
      int n = 0;

      for (int lane = 0; lane < 4; lane++) {
         if (lane != winner) {
            others[n++] = lane;
         }
      }

      for (int i = others.length - 1; i > 0; i--) {
         int j = rng.below(i + 1);
         int t = others[i];
         others[i] = others[j];
         others[j] = t;
      }

      int[] finish = new int[4];
      finish[winner] = 116;
      int gap = 5 + rng.below(10);

      for (int k = 0; k < others.length; k++) {
         finish[others[k]] = 116 + gap;
         gap += 4 + rng.below(12 + 2 * k);
      }

      return finish;
   }

   private static double[] progressTable(DuckDerbyRace.Rng rng, double slopeLo, double slopeHi) {
      double slope = rng.range(slopeLo, slopeHi);
      double f1 = rng.range(0.7, 1.5);
      double f2 = rng.range(1.9, 3.4);
      double a1 = rng.range(0.14, 0.36);
      double a2 = rng.range(0.05, 0.2);
      double p1 = rng.next();
      double p2 = rng.next();
      double bump = rng.range(-0.55, 0.85);
      double centre = rng.range(0.18, 0.82);
      double width = rng.range(0.06, 0.13);
      double[] cumulative = new double[481];
      double previous = 0.0;

      for (int i = 0; i <= 480; i++) {
         double x = (double)i / 480.0;
         double z = (x - centre) / width;
         double s = slope * (1.0 - 2.0 * x)
            + a1 * StrictMath.sin((Math.PI * 2) * (f1 * x + p1))
            + a2 * StrictMath.sin((Math.PI * 2) * (f2 * x + p2))
            + bump * StrictMath.exp(-0.5 * z * z);
         s = Math.max(-0.9, Math.min(0.9, s));
         double v = StrictMath.exp(s) * (0.12 + 0.88 * smooth(x / 0.07));
         if (i > 0) {
            cumulative[i] = cumulative[i - 1] + 0.5 * (previous + v);
         }

         previous = v;
      }

      double total = cumulative[480];

      for (int i = 0; i <= 480; i++) {
         cumulative[i] /= total;
      }

      cumulative[480] = 1.0;
      return cumulative;
   }

   private static double score(DuckDerbyRace.Plan plan, int story) {
      int leader = -1;
      int changes = 0;
      double[] p = new double[4];
      int[] midRank = new int[4];
      int winnerLedTicks = 0;
      int lateTicks = 0;
      double deepestDeficit = 0.0;

      for (int t = 1; t <= 116; t++) {
         double top = -1.0;
         double bottom = 2.0;
         int ahead = 0;

         for (int lane = 0; lane < 4; lane++) {
            p[lane] = plan.raceProgress(lane, (double)t);
            if (p[lane] > top) {
               top = p[lane];
               ahead = lane;
            }

            bottom = Math.min(bottom, p[lane]);
         }

         if ((double)t < 104.4 && top - bottom > 0.5) {
            return Double.NEGATIVE_INFINITY;
         }

         if ((double)t >= 34.8) {
            lateTicks++;
            if (ahead == plan.winner()) {
               winnerLedTicks++;
            }
         }

         if ((double)t >= 63.800000000000004 && (double)t <= 106.72) {
            deepestDeficit = Math.max(deepestDeficit, top - p[plan.winner()]);
         }

         if (t >= 12) {
            if (leader < 0) {
               leader = ahead;
            } else if (ahead != leader && p[ahead] - p[leader] >= 0.012) {
               leader = ahead;
               changes++;
            }

            if (t == 58) {
               for (int lane = 0; lane < 4; lane++) {
                  int r = 0;

                  for (int other = 0; other < 4; other++) {
                     if (p[other] > p[lane]) {
                        r++;
                     }
                  }

                  midRank[lane] = r;
               }
            }
         }
      }

      double runnerUp = 0.0;

      for (int lane = 0; lane < 4; lane++) {
         if (lane != plan.winner()) {
            runnerUp = Math.max(runnerUp, p[lane]);
         }
      }

      if (runnerUp < 0.8) {
         return Double.NEGATIVE_INFINITY;
      } else {
         int reshuffled = 0;

         for (int lanex = 0; lanex < 4; lanex++) {
            if (midRank[lanex] != plan.rank(lanex)) {
               reshuffled++;
            }
         }

         double score = 2.5 * (double)Math.min(changes, 3)
            - 2.0 * (double)Math.max(0, changes - 4)
            + 6.0 * runnerUp
            + 0.4 * (double)reshuffled
            - (changes < 2 ? 6.0 : 0.0);
         double ledShare = (double)winnerLedTicks / (double)lateTicks;
         if (story == 0) {
            score += deepestDeficit >= 0.03 ? 6.0 : -6.0;
         } else if (story == 1) {
            score += ledShare >= 0.7 ? 6.0 : -8.0 * (0.7 - ledShare);
         } else {
            score += runnerUp >= 0.94 ? 6.0 : -20.0 * (0.94 - runnerUp);
         }

         return score;
      }
   }

   public static final class Plan {
      private final int winner;
      private final long seed;
      private final int[] finish;
      private final double[][] table;
      private final double[] coast;
      private final double[] tau;
      private final int[] rank;
      private final List<DuckDerbyRace.Quack> quacks;

      private Plan(int winner, long seed, int[] finish, double[][] table, DuckDerbyRace.Rng quackRng) {
         this.winner = winner;
         this.seed = seed;
         this.finish = finish;
         this.table = table;
         this.coast = new double[4];
         this.tau = new double[4];
         this.rank = new int[4];

         for (int lane = 0; lane < 4; lane++) {
            int ahead = 0;

            for (int other = 0; other < 4; other++) {
               if (finish[other] < finish[lane]) {
                  ahead++;
               }
            }

            this.rank[lane] = ahead;
            double[] t = table[lane];
            double pace = (t[480] - t[479]) * 480.0 / (double)finish[lane];
            double cap = 0.05434782608695652;
            this.coast[lane] = Math.min(cap, pace * 5.0);
            this.tau[lane] = this.coast[lane] / pace;
         }

         this.quacks = Collections.unmodifiableList(this.makeQuacks(quackRng));
      }

      public int winner() {
         return this.winner;
      }

      public long seed() {
         return this.seed;
      }

      public int rank(int lane) {
         return this.rank[lane];
      }

      public int finishTick(int lane) {
         return 24 + this.finish[lane];
      }

      public int winnerFinishTick() {
         return 24 + this.finish[this.winner];
      }

      public int lastFinishTick() {
         int last = 0;

         for (int f : this.finish) {
            last = Math.max(last, f);
         }

         return 24 + last;
      }

      public int resultTick() {
         return this.winnerFinishTick() + 14;
      }

      public int returnStartTick() {
         return this.lastFinishTick() + 44;
      }

      public int returnEndTick() {
         return this.returnStartTick() + 36;
      }

      public int endTick() {
         return this.returnEndTick() + 10;
      }

      public List<DuckDerbyRace.Quack> quacks() {
         return this.quacks;
      }

      public double raceProgress(int lane, double t) {
         if (t <= 0.0) {
            return 0.0;
         } else {
            int total = this.finish[lane];
            if (t < (double)total) {
               double position = t / (double)total * 480.0;
               int i = Math.min(479, (int)position);
               double f = position - (double)i;
               return this.table[lane][i] + (this.table[lane][i + 1] - this.table[lane][i]) * f;
            } else {
               return 1.0 + this.coast[lane] * (1.0 - StrictMath.exp(-(t - (double)total) / this.tau[lane]));
            }
         }
      }

      public double progress(int lane, double elapsed) {
         int back = this.returnStartTick();
         if (elapsed < (double)back) {
            return this.raceProgress(lane, elapsed - 24.0);
         } else {
            double resting = this.raceProgress(lane, (double)(back - 24));
            return resting * (1.0 - DuckDerbyRace.smooth((elapsed - (double)back) / 36.0));
         }
      }

      public double pace(int lane, double elapsed) {
         double h = 0.5;
         return (this.progress(lane, elapsed + h) - this.progress(lane, elapsed - h)) / (2.0 * h);
      }

      public double gate(double elapsed) {
         double up = Math.max(0.0, Math.min(1.0, (elapsed - 6.0) / 10.0));
         double down = Math.max(0.0, Math.min(1.0, (elapsed - (double)this.returnEndTick()) / 10.0));
         return (1.0 - (1.0 - up) * (1.0 - up)) * (1.0 - down * down);
      }

      public void pose(int lane, double elapsed, DuckDerbyRace.Pose out) {
         double p = this.progress(lane, elapsed);
         out.z = 56.5 - p * 46.0;
         out.pitch = 0.0;
         out.lift = 0.0;
         int back = this.returnStartTick();
         int eager = 10;
         if (!(elapsed < (double)eager)) {
            if (elapsed < 24.0) {
               double k = DuckDerbyRace.smooth((elapsed - (double)eager) / (double)(24 - eager));
               double wave = StrictMath.sin(elapsed * 1.1 + (double)lane * 1.9);
               out.pitch = 0.07 * k * wave;
               out.lift = 0.12 * k * Math.abs(wave);
            } else {
               if (elapsed < (double)back) {
                  double pace = Math.abs(this.pace(lane, elapsed)) * 46.0;
                  double amplitude = 0.3 * DuckDerbyRace.smooth(pace / 0.26);
                  double phase = p * 46.0 * 0.966643893412244 + (double)lane * 1.3;
                  out.pitch = amplitude * StrictMath.sin(phase);
                  if (lane == this.winner && elapsed >= (double)this.finishTick(lane)) {
                     this.celebrate(elapsed - (double)this.finishTick(lane), elapsed, back, out);
                  }
               }
            }
         }
      }

      private void celebrate(double since, double elapsed, int back, DuckDerbyRace.Pose out) {
         double in = DuckDerbyRace.smooth(since / 6.0);
         double fadeOut = 1.0 - DuckDerbyRace.smooth((elapsed - (double)(back - 14)) / 10.0);
         out.pitch += 0.24 * in * fadeOut;
         if (since < 33.0) {
            out.lift = out.lift + 2.0 * Math.abs(StrictMath.sin(Math.PI * since / 11.0)) * (1.0 - DuckDerbyRace.smooth((since - 22.0) / 11.0));
         }
      }

      private List<DuckDerbyRace.Quack> makeQuacks(DuckDerbyRace.Rng rng) {
         double[] voice = new double[]{1.14, 0.97, 1.26, 0.8};
         List<DuckDerbyRace.Quack> important = new ArrayList<>();
         List<DuckDerbyRace.Quack> others = new ArrayList<>();

         for (int lane = 0; lane < 4; lane++) {
            if (this.rank[lane] == 0) {
               important.add(new DuckDerbyRace.Quack(this.finishTick(lane), lane, 1.0F, voice(rng, voice[lane] * 1.05)));
            }

            if (this.rank[lane] == 1) {
               important.add(new DuckDerbyRace.Quack(this.finishTick(lane), lane, 0.75F, voice(rng, voice[lane])));
            }
         }

         int[] order = shuffled(rng);

         for (int k = 0; k < 3; k++) {
            important.add(new DuckDerbyRace.Quack(10 + k * 3 + rng.below(3), order[k], 0.8F, voice(rng, voice[order[k]])));
         }

         for (int lane = 0; lane < 4; lane++) {
            double t = 24.0 + rng.range(10.0, 30.0);

            while (t < (double)(24 + this.finish[lane] - 8)) {
               others.add(new DuckDerbyRace.Quack((int)t, lane, (float)rng.range(0.62, 0.85), voice(rng, voice[lane])));
               double near = this.progressAt(lane, t - 24.0);
               t += rng.range(24.0, 42.0) * (1.0 - 0.35 * near);
            }
         }

         List<DuckDerbyRace.Quack> kept = new ArrayList<>();

         for (List<DuckDerbyRace.Quack> group : List.of(important, others)) {
            for (DuckDerbyRace.Quack q : group) {
               boolean crowded = false;

               for (DuckDerbyRace.Quack other : kept) {
                  crowded |= Math.abs(other.tick() - q.tick()) < 3;
               }

               if (!crowded) {
                  kept.add(q);
               }
            }
         }

         kept.sort((a, b) -> Integer.compare(a.tick(), b.tick()));
         return kept;
      }

      private double progressAt(int lane, double t) {
         return Math.min(1.0, this.raceProgress(lane, t));
      }

      private static float voice(DuckDerbyRace.Rng rng, double base) {
         return (float)(base * rng.range(0.96, 1.04));
      }

      private static int[] shuffled(DuckDerbyRace.Rng rng) {
         int[] a = new int[]{0, 1, 2, 3};

         for (int i = 3; i > 0; i--) {
            int j = rng.below(i + 1);
            int t = a[i];
            a[i] = a[j];
            a[j] = t;
         }

         return a;
      }
   }

   public static final class Pose {
      public double z;
      public double pitch;
      public double lift;
   }

   public static record Quack(int tick, int lane, float volume, float pitch) {
   }

   private static final class Rng {
      private long state;

      Rng(long seed) {
         this.state = seed;
      }

      long nextLong() {
         long z = this.state += -7046029254386353131L;
         z = (z ^ z >>> 30) * -4658895280553007687L;
         z = (z ^ z >>> 27) * -7723592293110705685L;
         return z ^ z >>> 31;
      }

      double next() {
         return (double)(this.nextLong() >>> 11) * 1.110223E-16F;
      }

      double range(double lo, double hi) {
         return lo + (hi - lo) * this.next();
      }

      int below(int n) {
         return (int)(this.next() * (double)n);
      }
   }
}
