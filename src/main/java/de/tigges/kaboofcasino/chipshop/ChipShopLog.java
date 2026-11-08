package de.tigges.kaboofcasino.chipshop;

import de.tigges.kaboofcasino.storage.Database;
import de.tigges.kaboofcasino.util.Log;
import java.sql.PreparedStatement;
import java.util.UUID;

/**
 * Every Chip-Shop purchase: one INFO line in the server log (WARN when change was owed) and one row in
 * {@code casino.db} table {@code chipshop} (schema 5), written on the DB thread (not authoritative).
 */
public final class ChipShopLog {

    /** One purchase. {@code paid}: the chips taken, as text ("2xkaboofcasino:chip_100"). */
    public record Entry(UUID player, String name, UUID machine, String item, int quantity, long unitPrice, long price,
                        String paid, long change, long owed, long at) {
    }

    private final Database db;

    public ChipShopLog(Database db) {
        this.db = db;
    }

    static String line(Entry e) {
        return "chipshop BUY by " + e.name() + " (" + e.player() + ") at " + e.machine() + ": " + e.quantity() + "x "
                + e.item() + " for " + e.price() + " (" + e.unitPrice() + " each), paid " + e.paid() + ", change "
                + e.change() + (e.owed() > 0 ? ", owed " + e.owed() : "");
    }

    public void log(Entry e) {
        if (e.owed() > 0) {
            Log.get().warn(line(e));
        } else {
            Log.get().info(line(e));
        }
        db.submit("chipshop " + e.player(), c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO chipshop (player, player_name, machine, item, "
                    + "quantity, unit_price, price, paid, change, owed, at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, e.player().toString());
                ps.setString(2, e.name());
                ps.setString(3, e.machine().toString());
                ps.setString(4, e.item());
                ps.setInt(5, e.quantity());
                ps.setLong(6, e.unitPrice());
                ps.setLong(7, e.price());
                ps.setString(8, e.paid());
                ps.setLong(9, e.change());
                ps.setLong(10, e.owed());
                ps.setLong(11, e.at());
                ps.executeUpdate();
            }
        });
    }
}
