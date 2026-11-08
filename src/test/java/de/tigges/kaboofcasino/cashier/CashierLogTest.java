package de.tigges.kaboofcasino.cashier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.storage.Database;
import de.tigges.kaboofcasino.wager.Chips;
import de.tigges.kaboofcasino.wager.ItemAmount;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Schema 3 ({@code V3__cashier.sql}) on a fresh and on a schema-2 database, and the cashier's rows written on the DB
 * thread with player, name, kind, what went in and out, the chip value and the time.
 */
class CashierLogTest {

    @Test
    void theMigrationCreatesTheCashierTableAndTheLogFillsIt(@TempDir Path dir) throws SQLException {
        Path file = dir.resolve("casino.db");
        UUID player = new UUID(1, 2);
        UUID machine = new UUID(3, 4);
        try (Database db = Database.open(file, getClass().getClassLoader())) {
            CashierLog log = new CashierLog(db);
            log.log(new CashierLog.Entry(player, "Alice", CashierLog.Kind.EXCHANGE, machine,
                    CashierLog.items(List.of(new ItemAmount("minecraft:diamond", 40))),
                    CashierLog.items(Chips.breakdown(1280)), 1280, 0, "", 1_000L));
            log.log(new CashierLog.Entry(player, "Alice", CashierLog.Kind.BUY, machine,
                    CashierLog.items(List.of(new ItemAmount(Chips.key(100), 1))), "1x minecraft:cake + 2x "
                            + Chips.key(25), 50, 0, "prize abc", 2_000L));
        }
        try (Database db = Database.open(file, getClass().getClassLoader())) {
            List<String> rows = new ArrayList<>();
            db.now(c -> {
                try (Statement st = c.createStatement();
                     ResultSet rs = st.executeQuery("SELECT MAX(version) FROM schema_version")) {
                    rs.next();
                    assertEquals(4, rs.getInt(1)); // schema 4 (step 10) came after the cashier
                }
                try (PreparedStatement ps = c.prepareStatement("SELECT player, player_name, kind, machine, items_in, "
                        + "items_out, chip_value, owed, detail, at FROM cashier ORDER BY id");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rows.add(rs.getString(1) + "|" + rs.getString(2) + "|" + rs.getString(3) + "|"
                                + rs.getString(4) + "|" + rs.getString(5) + "|" + rs.getString(6) + "|"
                                + rs.getLong(7) + "|" + rs.getLong(8) + "|" + rs.getString(9) + "|" + rs.getLong(10));
                    }
                }
            });
            assertEquals(List.of(
                    player + "|Alice|EXCHANGE|" + machine + "|40xminecraft:diamond|1xkaboofcasino:chip_1000 + "
                            + "2xkaboofcasino:chip_100 + 3xkaboofcasino:chip_25 + 1xkaboofcasino:chip_5|1280|0||1000",
                    player + "|Alice|BUY|" + machine + "|1xkaboofcasino:chip_100|1x minecraft:cake + "
                            + "2x kaboofcasino:chip_25|50|0|prize abc|2000"), rows);
        }
    }

    @Test
    void aSchemaTwoDatabaseMigratesAndTheKindIsChecked(@TempDir Path dir) throws SQLException {
        Path file = dir.resolve("casino.db");
        try (Database db = Database.open(file, getClass().getClassLoader())) {
            db.now(c -> {
                // back to schema 2, as a 0.8.0 database
                try (Statement st = c.createStatement()) {
                    st.executeUpdate("DROP TABLE cashier");
                    st.executeUpdate("DROP INDEX rounds_board"); // schema 4 (step 10) as well
                    st.executeUpdate("ALTER TABLE rounds DROP COLUMN play");
                    st.executeUpdate("ALTER TABLE rounds DROP COLUMN refund");
                    st.executeUpdate("DROP TABLE board_hidden");
                    st.executeUpdate("DROP TABLE board_weeks");
                    st.executeUpdate("DELETE FROM schema_version WHERE version >= 3");
                }
            });
        }
        try (Database db = Database.open(file, getClass().getClassLoader())) {
            db.now(c -> {
                try (Statement st = c.createStatement()) {
                    st.executeUpdate("INSERT INTO cashier (player, player_name, kind, machine, items_in, items_out, "
                            + "chip_value, at) VALUES ('p', 'n', 'CHANGE', 'm', 'a', 'b', 5, 1)");
                    boolean refused = false;
                    try {
                        st.executeUpdate("INSERT INTO cashier (player, player_name, kind, machine, items_in, "
                                + "items_out, chip_value, at) VALUES ('p', 'n', 'STEAL', 'm', 'a', 'b', 5, 1)");
                    } catch (SQLException e) {
                        refused = true;
                    }
                    assertTrue(refused, "kind is EXCHANGE, CHANGE or BUY");
                }
            });
        }
    }

    @Test
    void theLogLineNamesEverything() {
        UUID player = new UUID(0, 7);
        UUID machine = new UUID(0, 9);
        String line = CashierLog.line(new CashierLog.Entry(player, "Bob", CashierLog.Kind.EXCHANGE, machine,
                "64xminecraft:iron_block", "2xkaboofcasino:chip_1000", 2304, 304, "", 0));
        assertTrue(line.contains("EXCHANGE by Bob (" + player + ") at " + machine), line);
        assertTrue(line.contains("value 2304 chips, owed 304 chips"), line);
        assertEquals("-", CashierLog.items(List.of()));
    }
}
