package de.tigges.kaboofcasino.croupier;

import de.tigges.kaboofcasino.deco.display.DecoRecord;
import de.tigges.kaboofcasino.util.Pos;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * How a croupier comes into the world: the only part in which Jack's options differ. Everything else (the record, the
 * dealer at the counter, the trade, the hiring, the counter's loss) is {@link CroupierService}'s.
 * <ul>
 *   <li>A (live, {@link VillagerOnCounterTrigger}): an unemployed adult villager a player brings onto a free counter is
 *       consumed and the croupier appears ({@link #scan});</li>
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

    /** Every {@link CroupierService#SCAN_TICKS} ticks. */
    default void scan(Spawns spawns) {
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

        /** The loaded dealer stations that have no croupier. */
        List<DecoRecord> freeStations();

        /** The loaded world of a position, or null. */
        World world(Pos pos);

        /**
         * The nearest player (not spectating, not a server-side bot) within {@code range} blocks of the station's
         * block centre.
         */
        Optional<Player> nearestPlayer(Pos station, double range);

        /**
         * Creates a croupier at {@code station} (its spot chosen opposite {@code nearest}), writes its record and
         * spawns its entities; false if the station is gone or has a croupier by now.
         */
        boolean spawn(DecoRecord station, Origin origin, Player nearest);
    }
}
