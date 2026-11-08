package de.tigges.kaboofcasino.croupier;

import de.tigges.kaboofcasino.deco.display.DecoRecord;
import de.tigges.kaboofcasino.util.Pos;
import java.util.List;
import java.util.UUID;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * How a croupier comes into the world: the only part in which Jack's options differ. Everything else (the record, the
 * dealer at the counter, the trade, the hiring, the counter's loss) is {@link CroupierService}'s.
 * <ul>
 *   <li>A (live, {@link VillagerOnCounterTrigger}): an unemployed adult villager a player brings onto a free counter is
 *       consumed and the croupier appears when the player right-clicks the counter ({@link #stationUsed}); never
 *       without that click;</li>
 *   <li>A' (not built): the croupier appears with the counter: {@link #stationPlaced} would call
 *       {@code spawns.spawn(station, Origin.NONE, placer)}; such a croupier has no villager to give back and simply
 *       goes when it loses its counter before its first trade.</li>
 * </ul>
 * Thread: MAIN.
 */
public interface CroupierSpawnTrigger {

    /** Short name for the logs. */
    String name();

    /** A player placed a dealer station (its record, carrier and display exist). */
    default void stationPlaced(Spawns spawns, DecoRecord station, Player placer) {
    }

    /**
     * A player right-clicked a counter that has no croupier (main hand, where the mod would use the block).
     *
     * @param inReach the player is within reach of the counter (vanilla's block interaction range)
     * @return {@code OK} if a croupier appeared, {@code NOBODY} if no villager stands on the counter, else the reason
     *         the villager there was refused
     */
    default CroupierEligibility.Verdict stationUsed(Spawns spawns, DecoRecord station, Player player,
                                                   boolean inReach) {
        return CroupierEligibility.Verdict.NOBODY;
    }

    /** What a croupier is made of besides its counter. */
    record Origin(UUID villager, String snapshot, String version, List<String> items) {

        /** No villager (option A'). */
        public static final Origin NONE = new Origin(null, null, null, List.of());

        public Origin {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    /** What a trigger may use. */
    interface Spawns {

        /** The loaded world of a position, or null. */
        World world(Pos pos);

        /**
         * Creates a croupier at {@code station} (its spot chosen opposite {@code player}, who brought it), writes its
         * record and spawns its entities; false if the station is gone or has a croupier by now.
         */
        boolean spawn(DecoRecord station, Origin origin, Player player);
    }
}
