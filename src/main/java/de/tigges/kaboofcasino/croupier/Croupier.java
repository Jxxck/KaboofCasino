package de.tigges.kaboofcasino.croupier;

import java.util.UUID;
import org.bukkit.inventory.Merchant;

/**
 * A loaded croupier: its record, its entities (null until the first spawn after the chunk loaded), its merchant (made
 * when first traded with) and who has its trade screen open, plus the vanilla villager's sound and work clocks.
 * Thread: MAIN.
 */
final class Croupier {

    private CroupierRecord record;
    CroupierRig rig;
    Merchant merchant;
    /** The player whose trade screen is open, or null. */
    UUID trader;
    /** What the payment slots and the selected offer looked like last tick (vanilla's trade-updated voice). */
    String inputs = "";
    /** Vanilla's {@code ambientSoundTime}. */
    int ambient = -CroupierTrade.AMBIENT_INTERVAL;
    /** A player within hearing range (refreshed every scan). */
    boolean heard;
    /** A player within the idle loop's range (refreshed every scan). */
    boolean watched;
    /** Game time of its last work at the counter ({@code WorkAtPoi.lastCheck}; 0 after a chunk load, as vanilla's). */
    long lastWork;

    Croupier(CroupierRecord record) {
        this.record = record;
    }

    CroupierRecord record() {
        return record;
    }

    void record(CroupierRecord updated) {
        this.record = updated;
    }

    UUID id() {
        return record.id();
    }

    boolean trading() {
        return trader != null;
    }
}
