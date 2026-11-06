package de.tigges.kaboofcasino.game.plinko;

// Verbatim port of the MineVegas Casino class of the same name (MIT, byMehd,
// https://www.curseforge.com/minecraft/mc-mods/minevegas-casino): decompiled source, package renamed. Pure.
public final class PlinkoLights {
   public static final int BULBS = 11;

   public static final int SPAN = 120;
   public static final int CROSSFADE = 14;
   public static final int PATTERNS = 6;
   public static final int RETURN = 14;
   private static final int[] ROW_OF = new int[PlinkoGame.PIN_COUNT];
   private static final int[] INDEX_OF = new int[PlinkoGame.PIN_COUNT];
   private static final int[] EDGE = new int[PlinkoGame.PIN_COUNT];
   private static final int EDGE_LENGTH;
   private static final int[] CHASE = new int[]{5, 6, 7, 0, 1, 2, 3, 4, 10, 9, 8};

   private PlinkoLights() {
   }

   public static int rowOf(int pin) {
      return ROW_OF[pin];
   }

   public static int indexOf(int pin) {
      return INDEX_OF[pin];
   }

   public static double pinX(int pin) {
      return PlinkoGame.pinX(ROW_OF[pin], INDEX_OF[pin]);
   }

   public static double pinY(int pin) {
      return PlinkoGame.rowY(ROW_OF[pin]);
   }

   public static int chasePlace(int bulb) {
      for (int k = 0; k < CHASE.length; k++) {
         if (CHASE[k] == bulb) {
            return k;
         }
      }

      return 0;
   }

   private static double bump(double d, double width) {
      return Math.max(0.0, 1.0 - Math.abs(d) / width);
   }

   private static double smooth(double t) {
      double x = Math.max(0.0, Math.min(1.0, t));
      return x * x * (3.0 - 2.0 * x);
   }

   private static double hash(int pin) {
      long z = (long)(pin + 1) * -7046029254386353131L;
      z = (z ^ z >>> 30) * -4658895280553007687L;
      z = (z ^ z >>> 27) * -7723592293110705685L;
      return (double)((z ^ z >>> 31) >>> 11) * 1.110223E-16F;
   }

   static double pattern(int pattern, int pin, double t) {
      int row = ROW_OF[pin];
      double x = pinX(pin);
      double y = pinY(pin);

      double level = switch (pattern) {
         case 0 -> {
            double p = t % 36.0 / 36.0 * 13.0 - 1.0;
            double d = p - (double)row;
            yield d < -0.6 ? 0.0 : (d < 0.0 ? 1.0 + d / 0.6 : Math.max(0.0, 1.0 - d / 3.5));
         }
         case 1 -> {
            double s = (x - 6.0) / 36.0 * 0.8 + (double)row / 7.0 * 0.2;
            double phase = t % 64.0 / 32.0;
            double p = (phase < 1.0 ? phase : 2.0 - phase) * 1.3 - 0.15;
            yield bump(s - p, 0.16);
         }
         case 2 -> {
            double phase = StrictMath.sin((Math.PI * 2) * (t / 26.0 + hash(pin)));
            yield Math.pow(Math.max(0.0, phase), 3.0);
         }
         case 3 -> {
            if (EDGE[pin] < 0) {
               yield 0.15;
            } else {
               double p = t / 2.0 % (double)EDGE_LENGTH;
               double best = 0.2;

               for (int k = 0; k < 3; k++) {
                  best = Math.max(best, trailOf(wrap(p + (double)(k * EDGE_LENGTH) / 3.0 - (double)EDGE[pin])));
               }

               yield best;
            }
         }
         case 4 -> {
            double d = Math.hypot(x - 24.0, (y - 36.5) * 1.15) / 4.0;
            double p = t % 36.0 / 36.0 * 9.0;
            yield Math.max(Math.max(bump(d - p, 0.9), bump(d - p + 9.0, 0.9)), Math.max(bump(d - p - 4.5, 0.9), bump(d - p + 4.5, 0.9)));
         }
         default -> {
            double fill = smooth((t - 6.0 * (double)row) / 4.0);
            double empty = 1.0 - smooth((t - 96.0 - 3.0 * (double)(7 - row)) / 4.0);
            double flash = !(t < 52.0) && !(t > 92.0) ? 0.55 + 0.45 * StrictMath.cos((Math.PI * 2) * (t - 52.0) / 10.0) : 1.0;
            yield fill * empty * flash;
         }
      };
      return 0.22 + 0.78 * level;
   }

   private static double wrap(double d) {
      double w = d % (double)EDGE_LENGTH;
      if (w < (double)(-EDGE_LENGTH) / 2.0) {
         w += (double)EDGE_LENGTH;
      }

      if (w >= (double)EDGE_LENGTH / 2.0) {
         w -= (double)EDGE_LENGTH;
      }

      return w;
   }

   private static double trailOf(double behind) {
      if (behind < -0.5 || behind > 4.0) {
         return 0.0;
      } else {
         return behind < 0.0 ? 1.0 + behind / 0.5 : 1.0 - behind / 4.0 * 0.85;
      }
   }

   public static double lamp(int pin, double time) {
      double cycle = (double)Math.floorMod((long)Math.floor(time), 720L) + (time - Math.floor(time));
      int pattern = (int)(cycle / 120.0);
      double into = cycle - (double)(pattern * 120);
      double now = pattern(pattern, pin, into);
      if (into < 14.0) {
         int previous = (pattern + 6 - 1) % 6;
         double before = pattern(previous, pin, into + 120.0);
         double w = smooth(into / 14.0);
         return before * (1.0 - w) + now * w;
      } else {
         return now;
      }
   }

   public static double bulb(int bulb, double time) {
      return Math.floorMod(chasePlace(bulb) - (int)Math.floor(time / 6.0), 3) == 0 ? 1.0 : 0.3;
   }

   public static double back(double game, double idle, double since) {
      double w = smooth(since / 14.0);
      return game * (1.0 - w) + idle * w;
   }

   public static double into(double idle, double game, double elapsed) {
      double w = smooth(elapsed / 4.0);
      return idle * (1.0 - w) + game * w;
   }

   static {
      int pin = 0;

      for (int row = 0; row < 8; row++) {
         for (int i = 0; i < PlinkoGame.pinsInRow(row); pin++) {
            ROW_OF[pin] = row;
            INDEX_OF[pin] = i;
            EDGE[pin] = -1;
            i++;
         }
      }

      int place = 0;

      for (int row = 7; row >= 0; row--) {
         EDGE[PlinkoGame.firstPin(row)] = place++;
      }

      for (int i = 1; i < PlinkoGame.pinsInRow(0); i++) {
         EDGE[PlinkoGame.firstPin(0) + i] = place++;
      }

      for (int row = 1; row < 8; row++) {
         EDGE[PlinkoGame.firstPin(row) + PlinkoGame.pinsInRow(row) - 1] = place++;
      }

      int bottom = 7;

      for (int i = PlinkoGame.pinsInRow(bottom) - 2; i >= 1; i--) {
         EDGE[PlinkoGame.firstPin(bottom) + i] = place++;
      }

      EDGE_LENGTH = place;
   }
}
