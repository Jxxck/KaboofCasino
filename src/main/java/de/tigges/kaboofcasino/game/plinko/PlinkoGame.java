package de.tigges.kaboofcasino.game.plinko;

import de.tigges.kaboofcasino.game.FairPayout;

// Verbatim port of the MineVegas Casino class of the same name (MIT, byMehd,
// https://www.curseforge.com/minecraft/mc-mods/minevegas-casino): decompiled source, package renamed. Pure.
public final class PlinkoGame {
   public static final int ROWS = 8;

   public static final int POCKETS = 9;
   public static final int BALLS = 3;
   public static final int PATHS = 256;
   public static final double CENTRE_X = 24.0;
   public static final double TOP_ROW_Y = 36.5;
   public static final double ROW_PITCH = 3.0;
   public static final double PIN_PITCH = 4.0;
   public static final double POCKET_LEFT = 8.0;
   public static final double POST_RADIUS = 0.5;
   public static final double BALL_RADIUS = 0.9;
   public static final double BALL_Z = 12.0;
   public static final double CHUTE_X = 24.0;
   public static final double CHUTE_Y = 38.5;
   public static final double POCKET_TOP = 14.5;
   public static final double POCKET_FLOOR = 11.5;
   public static final double PLATE_TOP = 14.0;
   private static final int[] QUARTERS = new int[]{32, 12, 8, 2, 1, 2, 8, 12, 32};
   public static final int[] ROW_QUARTERS = new int[]{32, 12, 8, 2, 1};
   public static final int PIN_COUNT = firstPin(8);

   private PlinkoGame() {
   }

   public static int pinsInRow(int row) {
      return row + 3;
   }

   public static double pinX(int row, int index) {
      return 24.0 + ((double)index - (double)(row + 2) / 2.0) * 4.0;
   }

   public static double rowY(int row) {
      return 36.5 - (double)row * 3.0;
   }

   public static int firstPin(int row) {
      return row * (row + 5) / 2;
   }

   public static double pocketX(int pocket) {
      return 8.0 + (double)pocket * 4.0;
   }

   public static boolean right(int path, int row) {
      return (path >> row & 1) != 0;
   }

   public static int pocketOf(int path) {
      return Integer.bitCount(path & 0xFF);
   }

   public static int pinStruck(int path, int row) {
      int rights = Integer.bitCount(path & (1 << row) - 1);
      return 1 + rights;
   }

   public static int quarters(int pocket) {
      return QUARTERS[Math.floorMod(pocket, 9)];
   }

   public static PlinkoGame.Kind kindOf(int quarters) {
      if (quarters <= 0) {
         return PlinkoGame.Kind.NONE;
      } else if (quarters < 4) {
         return PlinkoGame.Kind.PARTIAL;
      } else if (quarters < 12) {
         return PlinkoGame.Kind.WIN;
      } else {
         return quarters < 32 ? PlinkoGame.Kind.BIG : PlinkoGame.Kind.JACKPOT;
      }
   }

   public static int payout(int stake, int quarters, int roll) {
      return stake > 0 && quarters > 0 ? FairPayout.share((long)stake * (long)quarters, 4, roll) : 0;
   }

   public static PlinkoGame.Kind kindFor(int quarters, int paid) {
      PlinkoGame.Kind kind = kindOf(quarters);
      return kind == PlinkoGame.Kind.PARTIAL && paid == 0 ? PlinkoGame.Kind.NONE : kind;
   }

   public static String multiplierKey(int quarters) {
      return "gui.roulettecasino.plinko.mult." + quarters;
   }

   public static double probability(int pocket) {
      if (pocket >= 0 && pocket <= 8) {
         long c = 1L;

         for (int i = 0; i < pocket; i++) {
            c = c * (long)(8 - i) / (long)(i + 1);
         }

         return (double)c / 256.0;
      } else {
         return 0.0;
      }
   }

   public static double expectedReturn() {
      double total = 0.0;

      for (int k = 0; k < 9; k++) {
         total += probability(k) * (double)QUARTERS[k] / 4.0;
      }

      return total;
   }

   public static double effectiveReturn(int stake) {
      if (stake <= 0) {
         return 0.0;
      } else {
         double total = 0.0;

         for (int k = 0; k < 9; k++) {
            for (int roll = 0; roll < 4; roll++) {
               total += probability(k) * (double)payout(stake, QUARTERS[k], roll) / 4.0;
            }
         }

         return total / (double)stake;
      }
   }

   public static enum Kind {
      NONE,
      PARTIAL,
      WIN,
      BIG,
      JACKPOT;
   }
}
