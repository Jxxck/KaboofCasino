package de.tigges.kaboofcasino.board;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The leaderboard's SQL (PLAN-2 C4), run on the DB thread only. Rows: settled chip rounds (stake item
 * {@code kaboofcasino:chips}; ore rounds of 0.8.0 and older never match) settled in {@code [since, until)}, without
 * players in {@code board_hidden}. A round's win is payout minus stake; see {@link BoardCategory}. Ties: the player
 * whose latest name sorts first. Index {@code rounds_board (status, stake_item, settled_at)} (schema 4).
 */
public final class BoardQueries {

    public static final String CHIPS = "kaboofcasino:chips";

    private static final String WHERE = " FROM rounds WHERE status = 'SETTLED' AND stake_item = '" + CHIPS
            + "' AND settled_at >= ? AND settled_at < ? AND player NOT IN (SELECT player FROM board_hidden)";

    private BoardQueries() {
    }

    /** The top {@code limit} of one category over {@code [since, until)}. */
    public static List<BoardEntry> top(Connection c, BoardCategory category, long since, long until, int minStake,
                                       int limit) throws SQLException {
        String value;
        String extra = "";
        String having = " HAVING v > 0";
        switch (category) {
            case BIGGEST_WIN -> value = "MAX(payout_count - stake_count)";
            case MULTIPLIER -> {
                value = "MAX(payout_count * 100 / stake_count)";
                extra = " AND stake_count >= ? AND payout_count > stake_count";
                having = " HAVING v > 100";
            }
            case NET -> value = "SUM(payout_count - stake_count)";
            case STAKED -> value = "SUM(stake_count)";
            case ROUNDS -> value = "COUNT(*)";
            default -> {
                value = "MAX(payout_count - stake_count)";
                extra = " AND game = ?";
            }
        }
        String sql = "SELECT player, " + value + " AS v" + WHERE + extra + " GROUP BY player" + having
                + " ORDER BY v DESC, (SELECT player_name FROM rounds n WHERE n.player = rounds.player "
                + "ORDER BY n.created_at DESC LIMIT 1) ASC LIMIT ?";
        List<UUID> players = new ArrayList<>();
        List<Long> values = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            ps.setLong(i++, since);
            ps.setLong(i++, until);
            if (category == BoardCategory.MULTIPLIER) {
                ps.setLong(i++, minStake);
            } else if (category.perGame()) {
                ps.setString(i++, category.game());
            }
            ps.setInt(i, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    players.add(UUID.fromString(rs.getString(1)));
                    values.add(rs.getLong(2));
                }
            }
        }
        List<BoardEntry> out = new ArrayList<>(players.size());
        for (int k = 0; k < players.size(); k++) {
            out.add(new BoardEntry(players.get(k), name(c, players.get(k)), values.get(k)));
        }
        return List.copyOf(out);
    }

    /** The player's latest name in the round log. */
    static String name(Connection c, UUID player) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT player_name FROM rounds WHERE player = ? "
                + "ORDER BY created_at DESC LIMIT 1")) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : "?";
            }
        }
    }

    /** Every category for both periods at {@code now}: the snapshot the boards and {@code /casino top} show. */
    public static BoardSnapshot snapshot(Connection c, long now, int minStake, int limit, long version)
            throws SQLException {
        long week = BoardPeriod.weekStart(now);
        Map<BoardCategory, List<BoardEntry>> weekly = new EnumMap<>(BoardCategory.class);
        Map<BoardCategory, List<BoardEntry>> total = new EnumMap<>(BoardCategory.class);
        for (BoardCategory cat : BoardCategory.values()) {
            weekly.put(cat, top(c, cat, week, Long.MAX_VALUE, minStake, limit));
            total.put(cat, top(c, cat, 0L, Long.MAX_VALUE, minStake, limit));
        }
        return new BoardSnapshot(now, week, minStake, version, weekly, total);
    }

    // ---- players who hid themselves -----------------------------------------------------------------------------

    /** Mirrors a player's choice into {@code board_hidden}. */
    public static void hide(Connection c, UUID player, boolean hidden, long now) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(hidden
                ? "INSERT OR IGNORE INTO board_hidden (player, at) VALUES (?, ?)"
                : "DELETE FROM board_hidden WHERE player = ?")) {
            ps.setString(1, player.toString());
            if (hidden) {
                ps.setLong(2, now);
            }
            ps.executeUpdate();
        }
    }

    // ---- the weekly summary ---------------------------------------------------------------------------------------

    /**
     * True (and marks it) if the week starting at {@code weekStart} was not logged yet; the mark is written in the
     * same call, so a crash before the log line loses that one summary rather than repeating it.
     */
    public static boolean claimWeek(Connection c, long weekStart, long now) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT OR IGNORE INTO board_weeks (week_start, logged_at) "
                + "VALUES (?, ?)")) {
            ps.setLong(1, weekStart);
            ps.setLong(2, now);
            return ps.executeUpdate() == 1;
        }
    }

    /** True if any chip round was settled in {@code [since, until)} (a week without play gets no summary). */
    public static boolean anyRounds(Connection c, long since, long until) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM rounds WHERE status = 'SETTLED' "
                + "AND stake_item = ? AND settled_at >= ? AND settled_at < ? LIMIT 1")) {
            ps.setString(1, CHIPS);
            ps.setLong(2, since);
            ps.setLong(3, until);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
