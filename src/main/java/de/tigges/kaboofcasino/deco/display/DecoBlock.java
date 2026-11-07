package de.tigges.kaboofcasino.deco.display;

import de.tigges.kaboofcasino.util.Pos;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;

/**
 * A loaded decoration: its record, its cells and the non-persistent entities that draw it (displays) and take its
 * clicks (Interactions of light carriers). Thread: MAIN.
 */
public final class DecoBlock {

    private DecoRecord record;
    private List<Pos> cells;
    final List<ItemDisplay> displays = new ArrayList<>();
    final List<String> keys = new ArrayList<>();
    final List<Integer> yaws = new ArrayList<>();
    final List<Interaction> clicks = new ArrayList<>();
    String clickSignature = "";

    DecoBlock(DecoRecord record) {
        this.record = record;
        this.cells = DecoGeometry.cells(record);
    }

    public DecoRecord record() {
        return record;
    }

    public UUID id() {
        return record.id();
    }

    public DecoType type() {
        return record.type();
    }

    public Pos anchor() {
        return record.anchor();
    }

    /** Every cell (the multi-block types' parts included); anchor first. */
    public List<Pos> cells() {
        return cells;
    }

    void record(DecoRecord updated) {
        this.record = updated;
        this.cells = DecoGeometry.cells(updated);
    }

    boolean spawned() {
        return !displays.isEmpty();
    }
}
