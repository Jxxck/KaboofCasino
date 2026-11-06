package de.tigges.kaboofcasino.game.roulette;

// Verbatim port of the MineVegas Casino class of the same name (MIT, byMehd,
// https://www.curseforge.com/minecraft/mc-mods/minevegas-casino): decompiled source, package renamed. Pure.
public final class RouletteSpin {
   public static final int POCKETS = 37;
   public static final double ANGLE = 0.16981581911296179;
   public static final int DURATION = 160;

   public static RouletteSpin.Plan plan(int initial, int roll) {
      if (roll >= 0 && roll < 37) {
         return new RouletteSpin.Plan(initial, 37 + Math.floorMod(roll * 25 % 37 - initial, 37));
      } else {
         throw new IllegalArgumentException("Roulette roll");
      }
   }

   public static int pocket(int frame) {
      return Math.floorMod(3 * frame, 37);
   }

   public static int color(int pocket) {
      return pocket == 0 ? 2 : ((pocket & 1) == 0 ? 1 : 0);
   }

   public static double ballRadius(double track) {
      return 8.0 + 2.1 * track;
   }

   public static double ballHeight(double phase, double track) {
      double relative = 3.0 * phase;
      double ripple = Math.pow(Math.sin(Math.PI * relative), 2.0);
      return 12.8 + 0.75 * track + 0.26 * (1.0 - track) * ripple;
   }

   public static double smooth(double value) {
      double t = Math.clamp(value, 0.0, 1.0);
      return t * t * (3.0 - 2.0 * t);
   }

   private RouletteSpin() {
   }

   public static record Plan(int initial, int steps) {
      public Plan(int initial, int steps) {
         if (initial >= 0 && initial < 37 && steps >= 1 && steps <= 73) {
            this.initial = initial;
            this.steps = steps;
         } else {
            throw new IllegalArgumentException("Roulette plan");
         }
      }

      public int finalFrame() {
         return Math.floorMod(this.initial + this.steps, 37);
      }

      public double phase(double elapsed) {
         double t = Math.clamp(elapsed / 160.0, 0.0, 1.0);
         return (double)this.initial + (double)this.steps * (1.0 - (1.0 - t) * (1.0 - t) * (1.0 - t));
      }

      public double speed(double elapsed) {
         if (!(elapsed < 0.0) && !(elapsed >= 160.0)) {
            double t = elapsed / 160.0;
            return (double)this.steps * 3.0 * (1.0 - t) * (1.0 - t) / 160.0;
         } else {
            return 0.0;
         }
      }

      public double track(double elapsed) {
         return 1.0 - RouletteSpin.smooth((elapsed / 160.0 - 0.58) / 0.28);
      }

      public double contactTick() {
         return 160.0 * (1.0 - Math.cbrt(0.5 / (3.0 * (double)this.steps)));
      }

      public double settleDuration() {
         return 1.2 / (3.0 * this.speed(this.contactTick()));
      }

      public double ballPhase(double elapsed) {
         double contact = this.contactTick();
         if (elapsed <= contact) {
            return 2.0 * this.phase(elapsed);
         } else {
            double u = Math.clamp((elapsed - contact) / this.settleDuration(), 0.0, 1.0);
            double advance = -2.0 * u * u * u + 3.0 * u * u + 2.4 * (u * u * u - 2.0 * u * u + u);
            double wobble = 0.025 * Math.sin((Math.PI * 4) * u) * u * (1.0 - u) * (1.0 - u);
            double relative = 3.0 * (double)(this.initial + this.steps) - 0.5 + 0.5 * advance + wobble;
            return relative - this.phase(elapsed);
         }
      }

      public double ballHeight(double elapsed) {
         double contact = this.contactTick();
         double track = this.track(elapsed);
         if (elapsed <= contact) {
            return RouletteSpin.ballHeight(this.phase(elapsed), track);
         } else {
            double u = Math.clamp((elapsed - contact) / this.settleDuration(), 0.0, 1.0);
            double hops = Math.exp(-3.0 * u) * Math.pow(Math.cos((Math.PI * 2) * u), 2.0) * (1.0 - RouletteSpin.smooth(u));
            return 12.8 + 0.75 * track + 0.26 * (1.0 - track) * hops;
         }
      }

      public int resultTick() {
         return 160;
      }
   }
}
