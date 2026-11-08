package de.tigges.kaboofcasino.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.tigges.kaboofcasino.util.Log;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

/**
 * SQLite behind a one-connection HikariCP pool and one worker thread {@code KaboofCasino-DB} (PostMarkt pattern:
 * WAL, synchronous NORMAL). Never authoritative for items (server.md 7): a lost DB costs logs and statistics only.
 * Migrations: ordered resources {@code db/migration/V<n>__*.sql}, checksummed; an applied script is never edited.
 */
public final class Database implements AutoCloseable {

    /** One unit of JDBC work. */
    @FunctionalInterface
    public interface Work {
        void run(Connection c) throws SQLException;
    }

    static final List<String> MIGRATIONS = List.of("db/migration/V1__init.sql", "db/migration/V2__logouts.sql",
            "db/migration/V3__cashier.sql", "db/migration/V4__leaderboard.sql",
            "db/migration/V5__chipshop.sql");

    private final HikariDataSource pool;
    private final ExecutorService worker;

    private Database(HikariDataSource pool) {
        this.pool = pool;
        this.worker = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "KaboofCasino-DB");
            t.setDaemon(true);
            return t;
        });
    }

    public static Database open(Path file, ClassLoader resources) throws SQLException {
        SQLiteConfig sqlite = new SQLiteConfig();
        sqlite.setJournalMode(SQLiteConfig.JournalMode.WAL);
        sqlite.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
        sqlite.setBusyTimeout(5_000);
        SQLiteDataSource ds = new SQLiteDataSource(sqlite);
        ds.setUrl("jdbc:sqlite:" + file.toAbsolutePath());
        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("KaboofCasino-SQLite");
        hikari.setDataSource(ds);
        hikari.setMaximumPoolSize(1);
        hikari.setMinimumIdle(1);
        hikari.setMaxLifetime(0);
        hikari.setIdleTimeout(0);
        hikari.setConnectionTimeout(10_000);
        Database db = new Database(new HikariDataSource(hikari));
        db.migrate(resources);
        return db;
    }

    /** Runs {@code work} on the DB thread; failures are logged, never thrown at the caller. */
    public void submit(String what, Work work) {
        worker.execute(() -> {
            try (Connection c = pool.getConnection()) {
                work.run(c);
            } catch (SQLException | RuntimeException e) {
                Log.get().error("KaboofCasino DB job failed: {}", what, e);
            }
        });
    }

    /** Runs {@code work} synchronously on the calling thread (onEnable only, before the server ticks). */
    public void now(Work work) throws SQLException {
        try (Connection c = pool.getConnection()) {
            work.run(c);
        }
    }

    private void migrate(ClassLoader resources) throws SQLException {
        try (Connection c = pool.getConnection()) {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER PRIMARY KEY, "
                        + "name TEXT NOT NULL, checksum TEXT NOT NULL, applied_at INTEGER NOT NULL)");
            }
            int current = 0;
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_version")) {
                if (rs.next()) {
                    current = rs.getInt(1);
                }
            }
            if (current > MIGRATIONS.size()) {
                throw new SQLException("casino.db schema " + current + " is newer than this plugin ("
                        + MIGRATIONS.size() + "); refusing to start");
            }
            for (int i = 0; i < MIGRATIONS.size(); i++) {
                String name = MIGRATIONS.get(i);
                String sql = load(resources, name);
                String sum = sha256(sql);
                int version = i + 1;
                if (version <= current) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "SELECT checksum FROM schema_version WHERE version = ?")) {
                        ps.setInt(1, version);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next() && !rs.getString(1).equals(sum)) {
                                throw new SQLException(name + " was changed after it was applied");
                            }
                        }
                    }
                    continue;
                }
                c.setAutoCommit(false);
                try (Statement st = c.createStatement()) {
                    for (String statement : sql.split(";")) {
                        String s = statement.lines().filter(l -> !l.strip().startsWith("--"))
                                .reduce("", (a, b) -> a + "\n" + b).strip();
                        if (!s.isEmpty()) {
                            st.executeUpdate(s);
                        }
                    }
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO schema_version (version, name, checksum, applied_at) VALUES (?, ?, ?, ?)")) {
                        ps.setInt(1, version);
                        ps.setString(2, name);
                        ps.setString(3, sum);
                        ps.setLong(4, System.currentTimeMillis());
                        ps.executeUpdate();
                    }
                    c.setAutoCommit(true);
                } catch (SQLException | RuntimeException e) {
                    c.rollback();
                    c.setAutoCommit(true);
                    throw e;
                }
                Log.get().info("casino.db: applied {}", name);
            }
        }
    }

    private static String load(ClassLoader resources, String name) throws SQLException {
        try (InputStream in = resources.getResourceAsStream(name)) {
            if (in == null) {
                throw new SQLException("missing migration " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SQLException("cannot read " + name, e);
        }
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() {
        worker.shutdown();
        try {
            if (!worker.awaitTermination(10, TimeUnit.SECONDS)) {
                Log.get().warn("KaboofCasino DB jobs still running at shutdown");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        pool.close();
    }
}
