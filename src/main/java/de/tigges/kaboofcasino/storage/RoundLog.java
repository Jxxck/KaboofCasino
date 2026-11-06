package de.tigges.kaboofcasino.storage;

import de.tigges.kaboofcasino.wager.ItemAmount;
import de.tigges.kaboofcasino.wager.RoundRecord;
import de.tigges.kaboofcasino.wager.RoundSink;
import de.tigges.kaboofcasino.wager.SettleReason;
import de.tigges.kaboofcasino.util.Log;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The round log (server.md 7): one row per round, upserted by id (idempotent). Writes go to the DB thread; the game
 * never waits for them. {@code settled} rows also go to the server log at INFO (WARN for crash/death/stop reasons).
 */
public final class RoundLog implements RoundSink {

    /** One statistics line: per stake item. */
    public record StatLine(String item, long rounds, long staked, long won) {
    }

    private final Database db;

    public RoundLog(Database db) {
        this.db = db;
    }

    @Override
    public void started(UUID player, String playerName, RoundRecord r) {
        ItemAmount pay = r.payout().isEmpty() ? null : r.payout().getFirst();
        db.submit("round started " + r.id(), c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT OR IGNORE INTO rounds (id, player, player_name, "
                    + "machine, game, stake_item, stake_count, payout_item, payout_count, detail, created_at, status) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'OPEN')")) {
                ps.setString(1, r.id().toString());
                ps.setString(2, player.toString());
                ps.setString(3, playerName);
                ps.setString(4, r.machine().toString());
                ps.setString(5, r.game());
                ps.setString(6, r.stake().item());
                ps.setLong(7, r.stake().count());
                ps.setString(8, pay == null ? null : pay.item());
                ps.setLong(9, pay == null ? 0 : pay.count());
                ps.setString(10, r.detail());
                ps.setLong(11, r.created());
                ps.executeUpdate();
            }
        });
    }

    @Override
    public void settled(UUID player, RoundRecord r, SettleReason reason, long settledAt) {
        String line = "round " + r.id() + " of " + player + " settled (" + reason + "): stake " + r.stake() + ", "
                + r.detail() + ", payout " + r.payout();
        if (reason == SettleReason.NORMAL) {
            Log.get().info(line);
        } else {
            Log.get().warn(line);
        }
        // a blackjack hand's stake (double down) and payout change while it runs: the row gets the final values
        ItemAmount pay = r.payout().isEmpty() ? null : r.payout().getFirst();
        db.submit("round settled " + r.id(), c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE rounds SET status = 'SETTLED', settled_at = ?, "
                    + "reason = ?, stake_count = ?, payout_item = ?, payout_count = ?, detail = ? WHERE id = ?")) {
                ps.setLong(1, settledAt);
                ps.setString(2, reason.name());
                ps.setLong(3, r.stake().count());
                ps.setString(4, pay == null ? null : pay.item());
                ps.setLong(5, pay == null ? 0 : pay.count());
                ps.setString(6, r.detail());
                ps.setString(7, r.id().toString());
                ps.executeUpdate();
            }
        });
    }

    /** Startup sweep: OPEN rows older than an hour belong to rounds a crash rolled back (server.md 1.3). */
    public void voidOldOpenRounds(long now) {
        db.submit("void old open rounds", c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE rounds SET status = 'VOID_CRASH' "
                    + "WHERE status = 'OPEN' AND created_at < ?")) {
                ps.setLong(1, now - 3_600_000L);
                int n = ps.executeUpdate();
                if (n > 0) {
                    Log.get().warn("casino.db: {} open round row(s) older than 1 h marked VOID_CRASH", n);
                }
            }
        });
    }

    /** Per-item statistics of one player, delivered on the DB thread to {@code result}. */
    public void stats(UUID player, Consumer<List<StatLine>> result) {
        db.submit("stats " + player, c -> {
            List<StatLine> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT stake_item, COUNT(*), SUM(stake_count), "
                    + "SUM(CASE WHEN payout_item = stake_item THEN payout_count ELSE 0 END) FROM rounds "
                    + "WHERE player = ? AND status = 'SETTLED' GROUP BY stake_item ORDER BY stake_item")) {
                ps.setString(1, player.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new StatLine(rs.getString(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)));
                    }
                }
            }
            result.accept(List.copyOf(out));
        });
    }
}
