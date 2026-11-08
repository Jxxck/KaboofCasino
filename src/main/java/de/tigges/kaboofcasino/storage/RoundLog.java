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
    /** Runs on the DB thread after a settled row was written (the leaderboard's "recompute" mark). */
    private volatile Runnable afterSettle = () -> { };

    public RoundLog(Database db) {
        this.db = db;
    }

    /** Sets the hook run on the DB thread after each settled row (step 10: the leaderboard). */
    public void afterSettle(Runnable hook) {
        this.afterSettle = hook;
    }

    @Override
    public void started(UUID player, String playerName, RoundRecord r) {
        ItemAmount pay = r.payout().isEmpty() ? null : r.payout().getFirst();
        db.submit("round started " + r.id(), c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT OR IGNORE INTO rounds (id, player, player_name, "
                    + "machine, game, stake_item, stake_count, payout_item, payout_count, detail, created_at, status, "
                    + "play, refund) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'OPEN', ?, ?)")) {
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
                ps.setString(12, play(player, r));
                ps.setInt(13, refund(r.detail()) ? 1 : 0);
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
                    + "reason = ?, stake_count = ?, payout_item = ?, payout_count = ?, detail = ?, refund = ? "
                    + "WHERE id = ?")) {
                ps.setLong(1, settledAt);
                ps.setString(2, reason.name());
                ps.setLong(3, r.stake().count());
                ps.setString(4, pay == null ? null : pay.item());
                ps.setLong(5, pay == null ? 0 : pay.count());
                ps.setString(6, r.detail());
                ps.setInt(7, refund(r.detail()) ? 1 : 0);
                ps.setString(8, r.id().toString());
                ps.executeUpdate();
            }
            afterSettle.run();
        });
    }

    /**
     * The play a round belongs to (schema 4): every record of one bet commit shares its player, machine and creation
     * time (a three-slot wheel spin, a derby entry with three lanes); a blackjack or poker hand is a play of its own.
     */
    public static String play(UUID player, RoundRecord r) {
        return player + ":" + r.machine() + ":" + r.created();
    }

    /**
     * True for a round that only gave the stake back: a derby group entry that never raced (withdrawn, countdown
     * cancelled, quit or crash before the start: its detail still says "refund until the start") or a blackjack or
     * poker hand whose table was removed ("refund (table removed)"). The boards leave such rows out.
     */
    public static boolean refund(String detail) {
        return detail != null && detail.contains("refund");
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
