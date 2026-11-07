package de.tigges.kaboofcasino.game.derby;

import de.tigges.kaboofcasino.wager.ItemAmount;
import java.util.List;

/**
 * The bet menu's lobby data ({@code DerbyLobby.json} / {@code View}) plus the viewer's escrow: built by the controller
 * for one viewer, refreshed every tick while the menu is open.
 *
 * @param racing         a race runs (the machine cannot be opened then; an open menu closes at the launch)
 * @param enrolling      the group countdown runs
 * @param seconds        seconds left in the countdown ({@code enrollmentSeconds})
 * @param confirmed      the viewer is an entrant of the countdown ({@code confirmed}): escrow view + WITHDRAW
 * @param groupAllowed   {@code groupAllowed()}
 * @param credits        the viewer has owed items to collect (the mod's due-credit count; CLAIM is shown when > 0)
 * @param members        entrants in join order
 * @param confirmedLane  the viewer's lane while confirmed, else -1
 * @param confirmedStakes the viewer's escrowed stakes (row order) while confirmed, else empty
 * @param gameTime       the world's game time
 */
public record DerbySnapshot(boolean racing, boolean enrolling, int seconds, boolean confirmed, boolean groupAllowed,
                            int credits, List<DerbyMember> members, int confirmedLane, List<ItemAmount> confirmedStakes,
                            long gameTime) {

    public DerbySnapshot {
        members = List.copyOf(members);
        confirmedStakes = List.copyOf(confirmedStakes);
    }
}
