package de.tigges.kaboofcasino.croupier;

import de.tigges.kaboofcasino.machine.Facing;
import de.tigges.kaboofcasino.util.Pos;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * One croupier (chunk PDC {@code kaboofcasino:croupiers} of the chunk it stands in; {@link CroupierCodec}). The
 * croupier, its counter and its record always share one chunk: it is only placed beside a counter of its own chunk and
 * only re-attaches to one there, so a loaded croupier always sees its counter and nothing has to be looked up across a
 * chunk border. Pure.
 *
 * @param id              the croupier's id (also the {@code kaboofcasino:part} tag of its entities)
 * @param stand           the block its feet are in
 * @param lift            feet height above that block's bottom: 0 on a floor, a carpet's height, -1/16 on a path,
 *                        {@link CroupierPlacement#COUNTER_TOP} on its counter
 * @param facing          where it looks (towards its counter)
 * @param counter         the dealer station's deco record id; null once the counter is lost
 * @param counterPos      that station's block; null once the counter is lost
 * @param villager        the UUID the consumed villager had (logs); null if the croupier came without one
 * @param snapshot        that villager's {@code EntitySnapshot#getAsString} (taken after its job site, bed and meeting
 *                        point were released), restored when the croupier loses its counter before its first trade;
 *                        null if none
 * @param snapshotVersion the Minecraft version the snapshot was taken on (a snapshot is not data-fixed)
 * @param items           the villager's inventory at the conversion, each stack {@code ItemStack#serializeAsBytes} in
 *                        Base64 (dropped when it is hired; the snapshot holds the same items for a restore)
 * @param uses            trades of the hiring card since the last restock (0..{@link CroupierTrade#MAX_USES})
 * @param xp              villager experience (2 per trade); above 0 it keeps its profession without a counter
 * @param restock         vanilla's restock bookkeeping (last restock, last check's day, restocks today)
 * @param createdAt       epoch millis of the conversion
 * @param createdNear     the nearest player at the conversion (logs); null if none
 */
public record CroupierRecord(UUID id, Pos stand, double lift, Facing facing, UUID counter, Pos counterPos,
                             UUID villager, String snapshot, String snapshotVersion, List<String> items, int uses,
                             int xp, CroupierTrade.RestockState restock, long createdAt,
                             UUID createdNear) {

    public CroupierRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(stand, "stand");
        Objects.requireNonNull(facing, "facing");
        if ((counter == null) != (counterPos == null)) {
            throw new IllegalArgumentException("counter and counterPos go together");
        }
        if (counterPos != null && !counterPos.world().equals(stand.world())) {
            throw new IllegalArgumentException("counter in another world");
        }
        if (!Double.isFinite(lift) || Math.abs(lift) > 1.0) {
            throw new IllegalArgumentException("lift out of range: " + lift);
        }
        items = items == null ? List.of() : List.copyOf(items);
        uses = Math.max(0, Math.min(CroupierTrade.MAX_USES, uses));
        xp = Math.max(0, xp);
        restock = restock == null ? CroupierTrade.RestockState.NONE : restock;
    }

    public boolean hasCounter() {
        return counter != null;
    }

    /** The villager level its experience gives (1..5, vanilla's thresholds). */
    public int level() {
        return CroupierTrade.levelOf(xp);
    }

    public CroupierRecord withTrade(int newUses, int newXp) {
        return new CroupierRecord(id, stand, lift, facing, counter, counterPos, villager, snapshot, snapshotVersion,
                items, newUses, newXp, restock, createdAt, createdNear);
    }

    public CroupierRecord withRestock(int newUses, CroupierTrade.RestockState state) {
        return new CroupierRecord(id, stand, lift, facing, counter, counterPos, villager, snapshot, snapshotVersion,
                items, newUses, xp, state, createdAt, createdNear);
    }

    /** Without its counter (lost), standing where {@code where} says. */
    public CroupierRecord withoutCounter(CroupierPlacement.Stand where) {
        return new CroupierRecord(id, where.feet(), where.lift(), where.facing(), null, null, villager, snapshot,
                snapshotVersion, items, uses, xp, restock, createdAt, createdNear);
    }

    /** Attached to the counter {@code station} at {@code counterAt}, standing where {@code where} says. */
    public CroupierRecord withCounter(UUID station, Pos counterAt, CroupierPlacement.Stand where) {
        return new CroupierRecord(id, where.feet(), where.lift(), where.facing(), station, counterAt, villager,
                snapshot, snapshotVersion, items, uses, xp, restock, createdAt, createdNear);
    }

    /** Where it stands now. */
    public CroupierPlacement.Stand standing() {
        return new CroupierPlacement.Stand(stand, lift, facing);
    }
}
