// Verbatim port of the MineVegas Casino class of the same name (MIT, byMehd,
// https://www.curseforge.com/minecraft/mc-mods/minevegas-casino): decompiled source, package renamed. Pure.
package de.tigges.kaboofcasino.game.derby;

import java.util.List;

public final class DuckDerbyFootprint {
   private DuckDerbyFootprint() {
   }

   public static List<int[]> collisionBoxes() {
      return List.of(
         new int[]{3, 0, 3, 45, 10, 61},
         new int[]{0, 10, 0, 48, 14, 64},
         new int[]{0, 14, 0, 4, 31, 4},
         new int[]{44, 14, 0, 48, 31, 4},
         new int[]{4, 17, 1, 44, 31, 3}
      );
   }
}
