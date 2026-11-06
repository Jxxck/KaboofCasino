package de.tigges.kaboofcasino.util;

import org.bukkit.NamespacedKey;

/** Every persistent-data key of the plugin (namespace {@code kaboofcasino}). */
public final class Keys {

    /** Item PDC: the casino item type ({@code slot_machine}, ...). Identity of casino items, never name or model. */
    public static final NamespacedKey ITEM = key("item");
    /** Item PDC: a display copy inside a casino menu (purged from inventories on close). */
    public static final NamespacedKey DISPLAY = key("display");
    /** Entity PDC: part of a machine rig (non-persistent; an orphan on load is removed). */
    public static final NamespacedKey PART = key("part");
    /** Player PDC: the wager ledger (open rounds + owed items), one container written atomically. */
    public static final NamespacedKey LEDGER = key("ledger");
    /** Chunk PDC: the machines anchored in this chunk. */
    public static final NamespacedKey MACHINES = key("machines");

    private Keys() {
    }

    public static NamespacedKey key(String path) {
        return new NamespacedKey("kaboofcasino", path);
    }
}
