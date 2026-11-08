package de.tigges.kaboofcasino.board;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import de.tigges.kaboofcasino.storage.Database;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * How long one leaderboard snapshot takes on 200 000 round rows (50 players, 7 games, plays of 1-3 rows over eight
 * weeks, some refunds). Runs only with the environment variable {@code KABOOF_BENCH=1}; prints the time.
 */
class BoardBenchmarkTest {

    static final String[] GAMES = {"slot_machine", "fortune_wheel", "plinko", "roulette", "blackjack", "poker", "derby"};

    @Test
    void snapshotOn200kRows(@TempDir Path dir) throws SQLException {
        assumeTrue("1".equals(System.getenv("KABOOF_BENCH")), "KABOOF_BENCH=1 runs the benchmark");
        try (Database db = Database.open(dir.resolve("casino.db"), getClass().getClassLoader())) {
            long monday = BoardPeriod.weekStart(System.currentTimeMillis());
            db.now(c -> {
                c.setAutoCommit(false);
                Random r = new Random(42);
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO rounds (id, player, player_name, machine, "
                        + "game, stake_item, stake_count, payout_item, payout_count, detail, created_at, settled_at, "
                        + "reason, status) VALUES (?, ?, ?, ?, ?, 'kaboofcasino:chips', ?, 'kaboofcasino:chips', ?, ?, "
                        + "?, ?, 'NORMAL', 'SETTLED')")) {
                    int rows = 0;
                    while (rows < 200_000) {
                        int p = r.nextInt(50);
                        String game = GAMES[r.nextInt(GAMES.length)];
                        long created = monday - r.nextLong(56L * 86_400_000L);
                        int slots = 1 + r.nextInt(3);
                        boolean refund = r.nextInt(20) == 0;
                        for (int s = 0; s < slots && rows < 200_000; s++, rows++) {
                            long stake = 1 + r.nextInt(500);
                            long payout = refund ? stake : r.nextInt(3) == 0 ? stake * (1 + r.nextInt(5)) : 0;
                            ps.setString(1, UUID.randomUUID().toString());
                            ps.setString(2, new UUID(0, p).toString());
                            ps.setString(3, "Player" + p);
                            ps.setString(4, new UUID(1, r.nextInt(30)).toString());
                            ps.setString(5, game);
                            ps.setLong(6, stake);
                            ps.setLong(7, payout);
                            ps.setString(8, refund ? "slot=0 lane=1 group, refund until the start" : "slot=" + s);
                            ps.setLong(9, created);
                            ps.setLong(10, created + 5000);
                            ps.addBatch();
                        }
                        if (rows % 10_000 < 3) {
                            ps.executeBatch();
                        }
                    }
                    ps.executeBatch();
                }
                c.commit();
                c.setAutoCommit(true);
                try (var st = c.createStatement()) {
                    st.execute("ANALYZE");
                }
                for (int run = 0; run < 3; run++) {
                    long t0 = System.nanoTime();
                    BoardQueries.snapshot(c, monday + 3 * 86_400_000L, 25, 10, run);
                    long ms = (System.nanoTime() - t0) / 1_000_000;
                    System.out.println("BENCH snapshot on 200k rows, run " + run + ": " + ms + " ms");
                }
            });
        }
    }
}
