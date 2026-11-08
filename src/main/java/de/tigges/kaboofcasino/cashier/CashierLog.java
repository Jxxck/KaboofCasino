package de.tigges.kaboofcasino.cashier;

import de.tigges.kaboofcasino.storage.Database;
import de.tigges.kaboofcasino.util.Log;
import de.tigges.kaboofcasino.wager.ItemAmount;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Every cashier transaction into the server log (INFO, WARN if something went owed) and into {@code casino.db}
 * (table {@code cashier}, schema 3). The row is written on the DB thread ({@code KaboofCasino-DB}); the game never
 * waits for it. Never authoritative: the items live in the player's file.
 */
public final class CashierLog {

    public enum Kind {
        EXCHANGE, CHANGE, BUY
    }

    /**
     * One transaction.
     *
     * @param in     what the player gave (ores or chips by denomination key)
     * @param out    what he got (chips by denomination key and/or the prize)
     * @param value  its chip value (exchange: the chips' value; change: both sides; buy: the price)
     * @param owed   chip value that did not fit and went onto his owed list
     * @param detail anything else (the prize's key and price)
     */
    public record Entry(UUID player, String name, Kind kind, UUID machine, String in, String out, long value,
                        long owed, String detail, long at) {
        public Entry {
            Objects.requireNonNull(player);
            Objects.requireNonNull(name);
            Objects.requireNonNull(kind);
            Objects.requireNonNull(machine);
            Objects.requireNonNull(in);
            Objects.requireNonNull(out);
            Objects.requireNonNull(detail);
        }
    }

    private final Database db;

    public CashierLog(Database db) {
        this.db = db;
    }

    /** "64x minecraft:diamond + 3x kaboofcasino:chip_25" (empty list: "-"). */
    public static String items(List<ItemAmount> items) {
        return items.isEmpty() ? "-" : items.stream().map(ItemAmount::toString).collect(Collectors.joining(" + "));
    }

    /** The server log line. */
    public static String line(Entry e) {
        return "cashier " + e.kind() + " by " + e.name() + " (" + e.player() + ") at " + e.machine() + ": in "
                + e.in() + ", out " + e.out() + ", value " + e.value() + " chips"
                + (e.owed() > 0 ? ", owed " + e.owed() + " chips" : "")
                + (e.detail().isEmpty() ? "" : ", " + e.detail());
    }

    public void log(Entry e) {
        if (e.owed() > 0) {
            Log.get().warn(line(e));
        } else {
            Log.get().info(line(e));
        }
        db.submit("cashier " + e.kind() + " of " + e.name(), c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO cashier (player, player_name, kind, machine, "
                    + "items_in, items_out, chip_value, owed, detail, at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, e.player().toString());
                ps.setString(2, e.name());
                ps.setString(3, e.kind().name());
                ps.setString(4, e.machine().toString());
                ps.setString(5, e.in());
                ps.setString(6, e.out());
                ps.setLong(7, e.value());
                ps.setLong(8, e.owed());
                ps.setString(9, e.detail());
                ps.setLong(10, e.at());
                ps.executeUpdate();
            }
        });
    }
}
