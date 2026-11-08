package de.tigges.kaboofcasino.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.storage.Database;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The leaderboard's SQL on a real schema-4 database: every category, the week boundary (Monday 00:00 Berlin), ore
 * rounds and unsettled rows left out, hidden players skipped, the multiplier's minimum stake, per-game lists, the
 * latest name, and the weekly summary claimed once.
 */
class BoardQueriesTest {

    static final UUID ALICE = new UUID(0, 1);
    static final UUID BOB = new UUID(0, 2);
    static final UUID CARL = new UUID(0, 3);
    /** Monday 2026-11-09 00:00 Berlin: the week starts here. */
    static final long MONDAY = ZonedDateTime.of(2026, 11, 9, 0, 0, 0, 0, BoardPeriod.ZONE).toInstant().toEpochMilli();
    static int ids;

    static void round(Connection c, UUID player, String name, String game, String stakeItem, long stake, long payout,
                      long settled, String status) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO rounds (id, player, player_name, machine, game, "
                + "stake_item, stake_count, payout_item, payout_count, detail, created_at, settled_at, reason, status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, '', ?, ?, 'NORMAL', ?)")) {
            ps.setString(1, new UUID(9, ids++).toString());
            ps.setString(2, player.toString());
            ps.setString(3, name);
            ps.setString(4, new UUID(7, 7).toString());
            ps.setString(5, game);
            ps.setString(6, stakeItem);
            ps.setLong(7, stake);
            ps.setString(8, payout == 0 ? null : stakeItem);
            ps.setLong(9, payout);
            ps.setLong(10, settled - 1000);
            ps.setLong(11, settled);
            ps.setString(12, status);
            ps.executeUpdate();
        }
    }

    static void chips(Connection c, UUID player, String name, String game, long stake, long payout, long settled)
            throws SQLException {
        round(c, player, name, game, BoardQueries.CHIPS, stake, payout, settled, "SETTLED");
    }

    static List<String> top(Connection c, BoardCategory cat, long since) throws SQLException {
        return BoardQueries.top(c, cat, since, Long.MAX_VALUE, 25, 10).stream()
                .map(e -> e.name() + "=" + e.value()).toList();
    }

    @Test
    void everyCategoryAndTheWeekBoundary(@TempDir Path dir) throws SQLException {
        try (Database db = Database.open(dir.resolve("casino.db"), getClass().getClassLoader())) {
            db.now(c -> {
                long lastSunday = MONDAY - 1; // 23:59:59.999 on Sunday: last week
                chips(c, ALICE, "Alice", "slot_machine", 100, 1000, lastSunday); // last week: +900, x10
                chips(c, ALICE, "Alice", "roulette", 50, 0, MONDAY);              // this week: -50
                chips(c, ALICE, "Alice", "blackjack", 40, 100, MONDAY + 5000);   // +60, x2.5
                chips(c, BOB, "Bob", "derby", 30, 90, MONDAY + 1000);             // +60, x3
                chips(c, BOB, "Bob", "slot_machine", 10, 200, MONDAY + 2000);    // +190, x20 but stake < 25
                chips(c, CARL, "Carl", "poker", 20, 20, MONDAY + 3000);           // a push: no win
                // left out: ore rounds of 0.8.0, open and void rows
                round(c, CARL, "Carl", "slot_machine", "minecraft:diamond", 5, 5000, MONDAY + 4000, "SETTLED");
                round(c, CARL, "Carl", "slot_machine", BoardQueries.CHIPS, 5, 5000, MONDAY + 4000, "VOID_CRASH");
                long week = BoardPeriod.weekStart(MONDAY + 10_000);
                assertEquals(MONDAY, week);
                assertEquals(List.of("Bob=190", "Alice=60"), top(c, BoardCategory.BIGGEST_WIN, week));
                assertEquals(List.of("Alice=900", "Bob=190"), top(c, BoardCategory.BIGGEST_WIN, 0));
                // the multiplier counts from 25 chips: Bob's x20 on 10 chips is out
                assertEquals(List.of("Bob=300", "Alice=250"), top(c, BoardCategory.MULTIPLIER, week));
                assertEquals(List.of("Alice=1000", "Bob=300"), top(c, BoardCategory.MULTIPLIER, 0));
                // net only while positive: Alice 60 - 50 = 10 this week, Carl's push is 0
                assertEquals(List.of("Bob=250", "Alice=10"), top(c, BoardCategory.NET, week));
                assertEquals(List.of("Alice=910", "Bob=250"), top(c, BoardCategory.NET, 0));
                assertEquals(List.of("Alice=90", "Bob=40", "Carl=20"), top(c, BoardCategory.STAKED, week));
                assertEquals(List.of("Alice=3", "Bob=2", "Carl=1"), top(c, BoardCategory.ROUNDS, 0));
                assertEquals(List.of("Bob=190"), top(c, BoardCategory.SLOT_MACHINE, week));
                assertEquals(List.of("Alice=900", "Bob=190"), top(c, BoardCategory.SLOT_MACHINE, 0));
                assertEquals(List.of("Bob=60"), top(c, BoardCategory.DERBY, 0));
                assertEquals(List.of(), top(c, BoardCategory.POKER, 0));
                assertEquals(List.of(), top(c, BoardCategory.PLINKO, 0));
                // a hidden player is skipped everywhere; showing him again brings him back
                BoardQueries.hide(c, BOB, true, 1);
                assertEquals(List.of("Alice=60"), top(c, BoardCategory.BIGGEST_WIN, week));
                assertEquals(List.of("Alice=3", "Carl=1"), top(c, BoardCategory.ROUNDS, 0));
                BoardQueries.hide(c, BOB, false, 2);
                assertEquals(List.of("Bob=190", "Alice=60"), top(c, BoardCategory.BIGGEST_WIN, week));
                // the latest name is shown
                chips(c, BOB, "Bobby", "plinko", 1, 0, MONDAY + 9000);
                assertEquals(List.of("Bobby=190", "Alice=60"), top(c, BoardCategory.BIGGEST_WIN, week));
                // the snapshot: both periods from one call
                BoardSnapshot s = BoardQueries.snapshot(c, MONDAY + 10_000, 25, 10, 7);
                assertEquals(7, s.version());
                assertEquals(MONDAY, s.weekStart());
                assertEquals(2, s.top(BoardCategory.BIGGEST_WIN, BoardPeriod.WEEK).size());
                assertEquals(900, s.top(BoardCategory.BIGGEST_WIN, BoardPeriod.TOTAL).getFirst().value());
                // a week later "diese Woche" is empty again, the total keeps everything
                BoardSnapshot next = BoardQueries.snapshot(c, MONDAY + 7 * 86_400_000L, 25, 10, 8);
                assertTrue(next.top(BoardCategory.ROUNDS, BoardPeriod.WEEK).isEmpty());
                assertEquals(3, next.top(BoardCategory.ROUNDS, BoardPeriod.TOTAL).size());
            });
        }
    }

    /** A row of a play: explicit play key (null = 0.9.x, grouped by player, machine, created_at) and refund flag. */
    static void row(Connection c, UUID player, String name, String game, UUID machine, String play, long created,
                    long stake, long payout, String detail) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO rounds (id, player, player_name, machine, game, "
                + "stake_item, stake_count, payout_item, payout_count, detail, created_at, settled_at, reason, status, "
                + "play, refund) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'NORMAL', 'SETTLED', ?, ?)")) {
            ps.setString(1, new UUID(8, ids++).toString());
            ps.setString(2, player.toString());
            ps.setString(3, name);
            ps.setString(4, machine.toString());
            ps.setString(5, game);
            ps.setString(6, BoardQueries.CHIPS);
            ps.setLong(7, stake);
            ps.setString(8, payout == 0 ? null : BoardQueries.CHIPS);
            ps.setLong(9, payout);
            ps.setString(10, detail);
            ps.setLong(11, created);
            ps.setLong(12, created + 4000);
            ps.setString(13, play);
            ps.setInt(14, de.tigges.kaboofcasino.storage.RoundLog.refund(detail) ? 1 : 0);
            ps.executeUpdate();
        }
    }

    /**
     * A play counts once, with its rows summed: a three-slot roulette spin, a three-slot wheel spin of 0.9.x (no play
     * key: grouped by player, machine and time), a derby group entry with three lanes; blackjack hands are a play
     * each; refunds (derby entries that never raced, a table removed mid-hand) count nowhere.
     */
    @Test
    void playsCountOnceAndRefundsAreLeftOut(@TempDir Path dir) throws SQLException {
        try (Database db = Database.open(dir.resolve("casino.db"), getClass().getClassLoader())) {
            db.now(c -> {
                UUID roulette = new UUID(5, 1);
                UUID wheel = new UUID(5, 2);
                UUID derby = new UUID(5, 3);
                UUID blackjack = new UUID(5, 4);
                UUID poker = new UUID(5, 5);
                long t = MONDAY + 60_000;
                // Alice: a roulette spin on three colours (-20 together), a 0.9.x wheel spin on three slots (+25)
                row(c, ALICE, "Alice", "roulette", roulette, "a-roulette", t, 10, 0, "slot=0 colour=0");
                row(c, ALICE, "Alice", "roulette", roulette, "a-roulette", t, 20, 40, "slot=1 colour=1");
                row(c, ALICE, "Alice", "roulette", roulette, "a-roulette", t, 30, 0, "slot=2 colour=2");
                row(c, ALICE, "Alice", "fortune_wheel", wheel, null, t + 1000, 10, 30, "seg=1 slot=0");
                row(c, ALICE, "Alice", "fortune_wheel", wheel, null, t + 1000, 10, 0, "seg=1 slot=1");
                row(c, ALICE, "Alice", "fortune_wheel", wheel, null, t + 1000, 10, 25, "seg=1 slot=2");
                // Bob: a derby group race on three lanes (+10), and an entry that never raced (a refund)
                row(c, BOB, "Bob", "derby", derby, "b-derby", t + 2000, 10, 30, "slot=0 lane=0 winner=0 group");
                row(c, BOB, "Bob", "derby", derby, "b-derby", t + 2000, 10, 10, "slot=1 lane=1 winner=0 group");
                row(c, BOB, "Bob", "derby", derby, "b-derby", t + 2000, 10, 0, "slot=2 lane=2 winner=0 group");
                row(c, BOB, "Bob", "derby", derby, "b-derby-2", t + 3000, 50, 50,
                        "slot=0 lane=0 group, refund until the start");
                // Carl: two blackjack hands (+10, -10) and a poker hand refunded when the table was removed
                row(c, CARL, "Carl", "blackjack", blackjack, "c-bj-1", t + 4000, 10, 20, "result WIN");
                row(c, CARL, "Carl", "blackjack", blackjack, "c-bj-2", t + 5000, 10, 0, "result LOSE");
                row(c, CARL, "Carl", "poker", poker, "c-poker", t + 6000, 40, 40, "refund (table removed)");
                long week = MONDAY;
                assertEquals(List.of("Alice=2", "Carl=2", "Bob=1"), top(c, BoardCategory.ROUNDS, week));
                assertEquals(List.of("Alice=25", "Bob=10", "Carl=10"), top(c, BoardCategory.BIGGEST_WIN, week));
                assertEquals(List.of("Alice=90", "Bob=30", "Carl=20"), top(c, BoardCategory.STAKED, week));
                assertEquals(List.of("Alice=183", "Bob=133"), top(c, BoardCategory.MULTIPLIER, week),
                        "summed per play; Carl's hands stake less than 25");
                assertEquals(List.of("Bob=10", "Alice=5"), top(c, BoardCategory.NET, week));
                assertEquals(List.of("Alice=25"), top(c, BoardCategory.FORTUNE_WHEEL, week));
                assertEquals(List.of(), top(c, BoardCategory.ROULETTE, week));
                assertEquals(List.of("Bob=10"), top(c, BoardCategory.DERBY, week));
                assertEquals(List.of("Carl=10"), top(c, BoardCategory.BLACKJACK, week));
                assertEquals(List.of(), top(c, BoardCategory.POKER, week));
                // the round log's play key: a commit's records share it, a later hand gets its own
                de.tigges.kaboofcasino.wager.RoundRecord r = new de.tigges.kaboofcasino.wager.RoundRecord(
                        new UUID(1, 1), wheel, "fortune_wheel", de.tigges.kaboofcasino.wager.RoundState.DECIDED,
                        new de.tigges.kaboofcasino.wager.ItemAmount(BoardQueries.CHIPS, 5), List.of(), 1234L, 2000L,
                        "x");
                assertEquals(ALICE + ":" + wheel + ":1234", de.tigges.kaboofcasino.storage.RoundLog.play(ALICE, r));
                assertTrue(de.tigges.kaboofcasino.storage.RoundLog.refund("slot=0 lane=0 group, refund until the start"));
                assertFalse(de.tigges.kaboofcasino.storage.RoundLog.refund("slot=0 lane=0 winner=0 group"));
            });
        }
    }

    @Test
    void tenPlacesAtMostAndTiesByName(@TempDir Path dir) throws SQLException {
        try (Database db = Database.open(dir.resolve("casino.db"), getClass().getClassLoader())) {
            db.now(c -> {
                for (int i = 0; i < 12; i++) {
                    chips(c, new UUID(1, i), "P" + (char) ('a' + 11 - i), "plinko", 5, 5 + (i < 2 ? 50 : 10), MONDAY);
                }
                List<String> top = top(c, BoardCategory.BIGGEST_WIN, 0);
                assertEquals(10, top.size());
                assertEquals(List.of("Pk=50", "Pl=50", "Pa=10"), top.subList(0, 3));
            });
        }
    }

    @Test
    void theWeeklySummaryIsClaimedOnce(@TempDir Path dir) throws SQLException {
        try (Database db = Database.open(dir.resolve("casino.db"), getClass().getClassLoader())) {
            db.now(c -> {
                long last = BoardPeriod.weekStart(MONDAY - 1);
                assertFalse(BoardQueries.anyRounds(c, last, MONDAY));
                chips(c, ALICE, "Alice", "plinko", 5, 10, MONDAY - 60_000);
                assertTrue(BoardQueries.anyRounds(c, last, MONDAY));
                assertFalse(BoardQueries.anyRounds(c, MONDAY, Long.MAX_VALUE));
                assertTrue(BoardQueries.claimWeek(c, last, MONDAY + 1));
                assertFalse(BoardQueries.claimWeek(c, last, MONDAY + 2));
            });
        }
    }
}
