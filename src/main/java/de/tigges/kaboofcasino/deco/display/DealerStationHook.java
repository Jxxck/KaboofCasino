package de.tigges.kaboofcasino.deco.display;

import org.bukkit.entity.Player;

/**
 * What another module may do when a dealer station ({@code roulettecasino:dealer_station}, games.md 18) is placed,
 * removed or used. In the mod the station has no behaviour of its own (no interaction, no block entity); it is a job
 * site. {@link #NONE} is registered until a module sets its own with
 * {@link DisplayDecoService#dealerStationHook(DealerStationHook)}. Loaded stations are listed by
 * {@link DisplayDecoService#dealerStations()} and {@link DisplayDecoService#dealerStationsNear}. Every call is made on
 * the main thread; an exception is logged as an incident and never stops the deco's own work. Thread: MAIN.
 */
public interface DealerStationHook {

    /** Does nothing (the mod's station: decoration only). */
    DealerStationHook NONE = new DealerStationHook() {
    };

    /** A player placed a station (its record, carrier and display exist). */
    default void onPlaced(DecoRecord station, Player placer) {
    }

    /**
     * A station is gone for good: broken (also in creative) or its carrier lost ({@code /fill}, an exploit). Not called
     * when its chunk unloads or the plugin disables (the station is then only no longer listed as loaded).
     */
    default void onRemoved(DecoRecord station) {
    }

    /**
     * A right click with the main hand on a station, where the mod would use the block ({@code useWithoutItem}: not
     * while sneaking with an item in either hand; never for spectators or fake players).
     *
     * @return true if handled: the held item is then not used on the station
     */
    default boolean onUse(Player player, DecoRecord station) {
        return false;
    }
}
