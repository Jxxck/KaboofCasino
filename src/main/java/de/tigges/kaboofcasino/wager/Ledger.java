package de.tigges.kaboofcasino.wager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The casino part of a player's file: open rounds and owed items (what did not fit into the inventory). Immutable;
 * every change produces a new ledger that is written in one PDC write.
 */
public record Ledger(List<RoundRecord> rounds, List<ItemAmount> owed) {

    public static final Ledger EMPTY = new Ledger(List.of(), List.of());

    public Ledger {
        rounds = List.copyOf(rounds);
        owed = ItemAmount.merge(owed);
    }

    public boolean isEmpty() {
        return rounds.isEmpty() && owed.isEmpty();
    }

    public Optional<RoundRecord> round(UUID id) {
        return rounds.stream().filter(r -> r.id().equals(id)).findFirst();
    }

    public Ledger withRound(RoundRecord round) {
        if (round(round.id()).isPresent()) {
            throw new IllegalArgumentException("round " + round.id() + " is already open");
        }
        List<RoundRecord> next = new ArrayList<>(rounds);
        next.add(round);
        return new Ledger(next, owed);
    }

    public Ledger withoutRound(UUID id) {
        return new Ledger(rounds.stream().filter(r -> !r.id().equals(id)).toList(), owed);
    }

    public Ledger withOwed(List<ItemAmount> items) {
        return new Ledger(rounds, items);
    }

    public Ledger plusOwed(List<ItemAmount> items) {
        List<ItemAmount> next = new ArrayList<>(owed);
        next.addAll(items);
        return new Ledger(rounds, next);
    }
}
