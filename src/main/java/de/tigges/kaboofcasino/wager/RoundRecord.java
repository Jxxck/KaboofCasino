package de.tigges.kaboofcasino.wager;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * One open round in the player's PDC (server.md 1.2). Written in the same tick in which the stake leaves the
 * inventory; removed in the same tick in which the payout is credited.
 *
 * @param id       round id, also the log key
 * @param machine  machine id
 * @param game     game type ({@code slot_machine})
 * @param state    DECIDED: payout fixed
 * @param stake    what was taken
 * @param payout   what the round pays (empty for a loss); credited at settlement
 * @param created  commit time, epoch millis
 * @param ends     scheduled settlement time, epoch millis (the watchdog settles late records)
 * @param detail   outcome for logs, e.g. {@code stops=0,0,0 x100}
 */
public record RoundRecord(UUID id, UUID machine, String game, RoundState state, ItemAmount stake,
                          List<ItemAmount> payout, long created, long ends, String detail) {

    public RoundRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(machine, "machine");
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(stake, "stake");
        Objects.requireNonNull(detail, "detail");
        payout = List.copyOf(payout);
    }
}
