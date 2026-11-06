package de.tigges.kaboofcasino.machine;

import de.tigges.kaboofcasino.util.Pos;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/** A loaded machine: its record and its running game. Thread: MAIN. */
public final class Machine {

    private MachineRecord record;
    private final List<Pos> carriers;
    private final MachineGame game;

    Machine(MachineRecord record, Function<Machine, MachineGame> games) {
        this.record = record;
        this.carriers = record.type().carriers(record.anchor(), record.facing());
        this.game = games.apply(this);
    }

    public MachineRecord record() {
        return record;
    }

    void record(MachineRecord updated) {
        this.record = updated;
    }

    public UUID id() {
        return record.id();
    }

    public MachineType type() {
        return record.type();
    }

    public Pos anchor() {
        return record.anchor();
    }

    public Facing facing() {
        return record.facing();
    }

    public List<Pos> carriers() {
        return carriers;
    }

    public MachineGame game() {
        return game;
    }
}
