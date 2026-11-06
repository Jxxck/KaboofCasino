package de.tigges.kaboofcasino.game.wheel;

// Verbatim port of the MineVegas Casino class of the same name (MIT, byMehd,
// https://www.curseforge.com/minecraft/mc-mods/minevegas-casino): decompiled source, package renamed. Pure.
public final class FortuneWheelBlur {
   public static final double CLEAR_SPEED = 0.6;

   public static final double SOFT_SPEED = 0.85;
   public static final double FULL_SPEED = 1.05;

   public static FortuneWheelBlur.Blend at(double speed) {
      if (speed <= 0.6) {
         return new FortuneWheelBlur.Blend(0, 0, 0.0);
      } else if (speed >= 1.05) {
         return new FortuneWheelBlur.Blend(2, 2, 0.0);
      } else {
         int lower = speed < 0.85 ? 0 : 1;
         double start = lower == 0 ? 0.6 : 0.85;
         double end = lower == 0 ? 0.85 : 1.05;
         double t = Math.clamp((speed - start) / (end - start), 0.0, 1.0);
         return new FortuneWheelBlur.Blend(lower, lower + 1, t * t * (3.0 - 2.0 * t));
      }
   }

   private FortuneWheelBlur() {
   }

   public static record Blend(int lower, int upper, double weight) {
      public int alpha() {
         return (int)Math.round(255.0 * this.weight);
      }
   }
}
