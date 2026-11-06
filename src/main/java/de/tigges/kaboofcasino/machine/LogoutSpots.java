package de.tigges.kaboofcasino.machine;

import de.tigges.kaboofcasino.storage.Database;
import de.tigges.kaboofcasino.util.Pos;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where offline players stand (safety 1b): the block box of feet and head at their logout, in SQLite and in memory.
 * A machine may not be placed with a carrier within one block of such a box, or the player would log in inside
 * barriers and suffocate (permadeath). Removed at the player's next join. Thread: MAIN (map), JDBC on the DB thread.
 */
public final class LogoutSpots {

    /** Block box (inclusive) in a world. */
    public record Box(String world, int x0, int y0, int z0, int x1, int y1, int z1) {

        /** True if block {@code p} lies in this box grown by {@code margin} blocks. */
        public boolean near(Pos p, int margin) {
            return world.equals(p.world()) && p.x() >= x0 - margin && p.x() <= x1 + margin && p.y() >= y0 - margin
                    && p.y() <= y1 + margin && p.z() >= z0 - margin && p.z() <= z1 + margin;
        }

        /** The blocks a bounding box (min/max, blocks) touches. */
        public static Box of(String world, double minX, double minY, double minZ, double maxX, double maxY,
                             double maxZ) {
            double e = 1e-6;
            return new Box(world, (int) Math.floor(minX), (int) Math.floor(minY), (int) Math.floor(minZ),
                    (int) Math.floor(maxX - e), (int) Math.floor(maxY - e), (int) Math.floor(maxZ - e));
        }
    }

    private final Database db;
    private final Map<UUID, Box> spots = new ConcurrentHashMap<>();

    public LogoutSpots(Database db) {
        this.db = db;
    }

    /** onEnable: loads every stored spot. */
    public void load() throws SQLException {
        if (db == null) {
            return;
        }
        db.now(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT player, world, x0, y0, z0, x1, y1, z1 FROM logouts");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    spots.put(UUID.fromString(rs.getString(1)), new Box(rs.getString(2), rs.getInt(3), rs.getInt(4),
                            rs.getInt(5), rs.getInt(6), rs.getInt(7), rs.getInt(8)));
                }
            }
        });
    }

    public void record(UUID player, Box box, long at) {
        spots.put(player, box);
        if (db != null) {
            db.submit("logout spot " + player, c -> {
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO logouts (player, world, x0, y0, z0, x1, "
                        + "y1, z1, at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(player) DO UPDATE SET "
                        + "world = excluded.world, x0 = excluded.x0, y0 = excluded.y0, z0 = excluded.z0, "
                        + "x1 = excluded.x1, y1 = excluded.y1, z1 = excluded.z1, at = excluded.at")) {
                    ps.setString(1, player.toString());
                    ps.setString(2, box.world());
                    ps.setInt(3, box.x0());
                    ps.setInt(4, box.y0());
                    ps.setInt(5, box.z0());
                    ps.setInt(6, box.x1());
                    ps.setInt(7, box.y1());
                    ps.setInt(8, box.z1());
                    ps.setLong(9, at);
                    ps.executeUpdate();
                }
            });
        }
    }

    public void forget(UUID player) {
        if (spots.remove(player) != null && db != null) {
            db.submit("logout spot gone " + player, c -> {
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM logouts WHERE player = ?")) {
                    ps.setString(1, player.toString());
                    ps.executeUpdate();
                }
            });
        }
    }

    /** True if block {@code p} lies within one block of an offline player's logout box. */
    public boolean blocks(Pos p) {
        for (Box b : spots.values()) {
            if (b.near(p, 1)) {
                return true;
            }
        }
        return false;
    }

    int size() {
        return spots.size();
    }
}
