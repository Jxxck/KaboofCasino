package de.tigges.kaboofcasino.storage;

import de.tigges.kaboofcasino.machine.MachineRecord;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The machine index (server.md 7): a mirror of the chunk-PDC records for lists, statistics and the per-type running
 * numbers. Not authoritative: the chunk PDC is. Numbers are handed out on MAIN from counters loaded at enable.
 */
public final class MachineIndex {

    private final Database db;
    private final Map<String, Integer> lastNumber = new HashMap<>();

    public MachineIndex(Database db) {
        this.db = db;
    }

    /** onEnable (before the first tick): loads the highest number per type. */
    public void load() throws SQLException {
        db.now(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT type, MAX(number) FROM machines GROUP BY type");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lastNumber.put(rs.getString(1), rs.getInt(2));
                }
            }
        });
    }

    /** The next running number for a type (MAIN). */
    public int nextNumber(String type) {
        return lastNumber.merge(type, 1, Integer::sum);
    }

    public void placed(MachineRecord r) {
        db.submit("machine placed " + r.id(), c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO machines (id, type, number, world, x, y, z, "
                    + "facing, placer, placed_at, state) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PLACED') "
                    + "ON CONFLICT(id) DO UPDATE SET state = 'PLACED', removed_at = NULL, removed_why = NULL")) {
                ps.setString(1, r.id().toString());
                ps.setString(2, r.type().id());
                ps.setInt(3, r.number());
                ps.setString(4, r.anchor().world());
                ps.setInt(5, r.anchor().x());
                ps.setInt(6, r.anchor().y());
                ps.setInt(7, r.anchor().z());
                ps.setString(8, r.facing().name());
                ps.setString(9, r.placer().toString());
                ps.setLong(10, r.placedAt());
                ps.executeUpdate();
            }
        });
    }

    public void gone(UUID id, String why, long at) {
        db.submit("machine gone " + id, c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE machines SET state = 'GONE', removed_at = ?, "
                    + "removed_why = ? WHERE id = ?")) {
                ps.setLong(1, at);
                ps.setString(2, why);
                ps.setString(3, id.toString());
                ps.executeUpdate();
            }
        });
    }
}
