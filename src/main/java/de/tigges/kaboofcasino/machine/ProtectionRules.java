package de.tigges.kaboofcasino.machine;

import de.tigges.kaboofcasino.util.Pos;
import java.util.Collection;
import java.util.function.Predicate;

/**
 * Pure protection rules (PostMarkt {@code ProtectionRules}, server.md 3.3). Barrier carriers already resist
 * explosions, pistons, withers, the dragon, endermen, fire and fluids; these rules add defence in depth and close the
 * piston exploits this server allows (F16).
 */
public final class ProtectionRules {

    private ProtectionRules() {
    }

    /**
     * Whether a player may mine a machine at all (before the owner rule): never with the croupier's hand in the main
     * hand ({@code CroupierHandItem.canDestroyBlock}); outside creative only while the world setting "breakable in
     * survival" is on (games.md 21.2: destroy progress 0, no message). Creative players always may.
     */
    public static boolean mayMine(boolean creative, boolean breakable, boolean croupierHand) {
        return !croupierHand && (creative || breakable);
    }

    /**
     * A piston event must be cancelled if it moves a carrier or if any of the piston's 6 neighbours is a carrier
     * (the headless/permanent-break exploits arrive with an empty block list, PostMarkt B9).
     */
    public static boolean pistonMustCancel(Pos piston, Collection<Pos> moved, Predicate<Pos> carrier) {
        for (Pos p : moved) {
            if (carrier.test(p)) {
                return true;
            }
        }
        int[][] around = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] d : around) {
            if (carrier.test(piston.offset(d[0], d[1], d[2]))) {
                return true;
            }
        }
        return false;
    }
}
