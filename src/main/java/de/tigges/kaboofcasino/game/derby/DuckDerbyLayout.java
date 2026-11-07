// Verbatim port of the MineVegas Casino class of the same name (MIT, byMehd,
// https://www.curseforge.com/minecraft/mc-mods/minevegas-casino): decompiled source, package renamed. Pure.
package de.tigges.kaboofcasino.game.derby;

public final class DuckDerbyLayout {
   private DuckDerbyLayout() {
   }

   public static double[] toBlock(double x, double y, double z, int turns) {
      double dx = x - 24.0;
      double dz = z - 56.0;

      for (int i = 0; i < turns; i++) {
         double nx = -dz;
         dz = dx;
         dx = nx;
      }

      return new double[]{0.5 + dx / 16.0, y / 16.0, 0.5 + dz / 16.0};
   }

   public static int[] facingStep(int turns) {
      int dx = 0;
      int dz = 1;

      for (int i = 0; i < turns; i++) {
         int nx = -dz;
         dz = dx;
         dx = nx;
      }

      return new int[]{dx, dz};
   }
}
