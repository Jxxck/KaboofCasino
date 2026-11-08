package de.tigges.kaboofcasino.board;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The leaderboard's SQL (PLAN-2 C4), run on the DB thread only, and the aggregation over plays (pure,
 * {@link #aggregate}). One scan of the covering index {@code rounds_board} (schema 4) reads every play: the settled chip
 * rounds (stake item {@code kaboofcasino:chips}; ore rounds of 0.8.0 and older never match) without refunds and
 * without players in {@code board_hidden}, grouped by play into its summed stake and payout. A play is one bet commit:
 * its rows share player, machine and creation time (the round log's play key is exactly that, so rows of 0.9.x
 * without a key group the same way, and the index order makes the grouping a single pass). A play's win is payout minus stake; see
 * {@link BoardCategory}. Ties: the latest name, alphabetically.
 */
public final class BoardQueries {

    public static final String CHIPS = "kaboofcasino:chips";

    /** One play: its player and game, the summed stake and payout of its rows, when its last row was settled. */
    public record Play(UUID player, String game, long stake, long payout, long settledAt) {
    }

    private BoardQueries() {
    }

    /** Every play settled in {@code [since, until)}. */
    public static List<Play> plays(Connection c, long since, long until) throws SQLException {
        List<Play> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT player, MAX(game), SUM(stake_count), SUM(payout_count), "
                + "MAX(settled_at) FROM rounds WHERE status = 'SETTLED' AND stake_item = ? AND settled_at >= ? "
                + "AND settled_at < ? AND refund = 0 AND player NOT IN (SELECT player FROM board_hidden) "
                + "GROUP BY player, machine, created_at")) {
            ps.setString(1, CHIPS);
            ps.setLong(2, since);
            ps.setLong(3, until);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Play(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getLong(3), rs.getLong(4),
                            rs.getLong(5)));
                }
            }
        }
        return out;
    }

    /** Every player's latest name in the round log. */
    public static Map<UUID, String> names(Connection c) throws SQLException {
        Map<UUID, String> out = new HashMap<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT player, player_name, MAX(created_at) FROM rounds "
                + "GROUP BY player");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.put(UUID.fromString(rs.getString(1)), rs.getString(2));
            }
        }
        return out;
    }

    /**
     * The top {@code limit} of one category over the plays settled from {@code since} on (pure): the biggest win of
     * one play (overall or in the category's game), the highest payout / stake of one play with at least
     * {@code minStake} staked (hundredths), the net sum while positive, the staked sum, the number of plays.
     */
    public static List<BoardEntry> aggregate(List<Play> plays, BoardCategory category, long since, int minStake,
                                             int limit, Map<UUID, String> names) {
        Map<UUID, Long> value = new HashMap<>();
        for (Play p : plays) {
            if (p.settledAt() < since || category.perGame() && !category.game().equals(p.game())) {
                continue;
            }
            long win = p.payout() - p.stake();
            switch (category) {
                case MULTIPLIER -> {
                    if (p.stake() >= minStake && p.stake() > 0 && p.payout() > p.stake()) {
                        value.merge(p.player(), p.payout() * 100 / p.stake(), Math::max);
                    }
                }
                case NET -> value.merge(p.player(), win, Long::sum);
                case STAKED -> value.merge(p.player(), p.stake(), Long::sum);
                case ROUNDS -> value.merge(p.player(), 1L, Long::sum);
                default -> {
                    if (win > 0) {
                        value.merge(p.player(), win, Math::max);
                    }
                }
            }
        }
        List<BoardEntry> out = new ArrayList<>();
        value.forEach((player, v) -> {
            if (v > 0) {
                out.add(new BoardEntry(player, names.getOrDefault(player, "?"), v));
            }
        });
        out.sort(Comparator.comparingLong(BoardEntry::value).reversed().thenComparing(BoardEntry::name)
                .thenComparing(BoardEntry::player));
        return List.copyOf(out.subList(0, Math.min(limit, out.size())));
    }

    /** The top {@code limit} of one category over {@code [since, until)}. */
    public static List<BoardEntry> top(Connection c, BoardCategory category, long since, long until, int minStake,
                                       int limit) throws SQLException {
        return aggregate(plays(c, since, until), category, since, minStake, limit, names(c));
    }

    /**
     * Every category for both periods at {@code now} from one scan: the snapshot the boards and {@code /casino top}
     * show. Each list holds {@code limit} places (the readers drop players who hid themselves since, and show 10).
     */
    public static BoardSnapshot snapshot(Connection c, long now, int minStake, int limit, long version)
            throws SQLException {
        long week = BoardPeriod.weekStart(now);
        List<Play> plays = plays(c, 0L, Long.MAX_VALUE);
        Map<UUID, String> names = names(c);
        Map<BoardCategory, List<BoardEntry>> weekly = new EnumMap<>(BoardCategory.class);
        Map<BoardCategory, List<BoardEntry>> total = new EnumMap<>(BoardCategory.class);
        for (BoardCategory cat : BoardCategory.values()) {
            weekly.put(cat, aggregate(plays, cat, week, minStake, limit, names));
            total.put(cat, aggregate(plays, cat, 0L, minStake, limit, names));
        }
        return new BoardSnapshot(now, week, minStake, version, weekly, total);
    }

    // ---- players who hid themselves -----------------------------------------------------------------------------

    /** Mirrors a player's choice into {@code board_hidden}; true if the row changed. */
    public static boolean hide(Connection c, UUID player, boolean hidden, long now) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(hidden
                ? "INSERT OR IGNORE INTO board_hidden (player, at) VALUES (?, ?)"
                : "DELETE FROM board_hidden WHERE player = ?")) {
            ps.setString(1, player.toString());
            if (hidden) {
                ps.setLong(2, now);
            }
            return ps.executeUpdate() > 0;
        }
    }

    /** True if the mirror says {@code player} hid himself. */
    public static boolean hidden(Connection c, UUID player) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM board_hidden WHERE player = ?")) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Every player the mirror lists as hidden. */
    public static java.util.Set<UUID> allHidden(Connection c) throws SQLException {
        java.util.Set<UUID> out = new java.util.HashSet<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT player FROM board_hidden");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(UUID.fromString(rs.getString(1)));
            }
        }
        return out;
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
                + "AND stake_item = ? AND settled_at >= ? AND settled_at < ? AND refund = 0 LIMIT 1")) {
            ps.setString(1, CHIPS);
            ps.setLong(2, since);
            ps.setLong(3, until);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
