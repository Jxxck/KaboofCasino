// Verbatim port of the MineVegas Casino class of the same name (MIT, byMehd,
// https://www.curseforge.com/minecraft/mc-mods/minevegas-casino): decompiled source, package renamed. Pure.
package de.tigges.kaboofcasino.game.derby;

public final class DuckDerbySignLights {
   public static final int BULBS = 12;
   public static final double SIZE = 0.75;

   private DuckDerbySignLights() {
   }

   public static double x(int bulb) {
      return bulb < 2 ? 4.5 : (bulb < 10 ? 5.5 + (double)(bulb - 2) * 5.285714285714286 : 43.5);
   }

   public static double y(int bulb) {
      return bulb < 2 ? 21.0 + 5.0 * (double)bulb : (bulb < 10 ? 30.5 : (bulb == 10 ? 26.0 : 21.0));
   }
}
