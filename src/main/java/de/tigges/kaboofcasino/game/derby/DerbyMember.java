package de.tigges.kaboofcasino.game.derby;

import de.tigges.kaboofcasino.wager.ItemAmount;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One entrant of the running or announced race ({@code DerbyLobby.Member}): who, his duck and his 1-3 stakes (one per
 * bet row), in join order.
 */
public record DerbyMember(UUID id, String name, int lane, List<ItemAmount> stakes) {

    public DerbyMember {
        stakes = List.copyOf(stakes);
    }

    /** {@code DerbyLobby.visible}: the viewer's own entry first (if any), then the others in join order, at most limit. */
    public static List<DerbyMember> visible(List<DerbyMember> members, UUID viewer, int limit) {
        List<DerbyMember> result = new ArrayList<>();
        for (DerbyMember m : members) {
            if (m.id().equals(viewer)) {
                result.add(m);
                break;
            }
        }
        for (DerbyMember m : members) {
            if (result.size() < limit && !m.id().equals(viewer)) {
                result.add(m);
            }
        }
        return result;
    }
}
