package de.tigges.kaboofcasino.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

/** The week starts Monday 00:00 Berlin, in winter and summer time and across the clock changes. */
class BoardPeriodTest {

    static long berlin(int y, int mo, int d, int h, int mi) {
        return ZonedDateTime.of(y, mo, d, h, mi, 0, 0, BoardPeriod.ZONE).toInstant().toEpochMilli();
    }

    @Test
    void theWeekStartsMondayMidnightBerlin() {
        long monday = berlin(2026, 11, 9, 0, 0);
        assertEquals(monday, BoardPeriod.weekStart(monday));
        assertEquals(monday, BoardPeriod.weekStart(berlin(2026, 11, 15, 23, 59)));
        assertEquals(berlin(2026, 11, 2, 0, 0), BoardPeriod.weekStart(monday - 1));
        // Monday 00:30 Berlin is still Sunday 23:30 UTC
        assertEquals(monday, BoardPeriod.weekStart(berlin(2026, 11, 9, 0, 30)));
        // summer time (2026-03-29 02:00 -> 03:00) and back (2026-10-25 03:00 -> 02:00)
        assertEquals(berlin(2026, 3, 23, 0, 0), BoardPeriod.weekStart(berlin(2026, 3, 29, 12, 0)));
        assertEquals(berlin(2026, 3, 30, 0, 0), BoardPeriod.weekStart(berlin(2026, 4, 1, 12, 0)));
        assertEquals(berlin(2026, 10, 19, 0, 0), BoardPeriod.weekStart(berlin(2026, 10, 25, 12, 0)));
        assertEquals(berlin(2026, 10, 26, 0, 0), BoardPeriod.weekStart(berlin(2026, 10, 26, 0, 0)));
        assertEquals(0, BoardPeriod.TOTAL.since(monday));
        assertEquals(monday, BoardPeriod.WEEK.since(monday + 5));
    }

    @Test
    void wordsForTheCommand() {
        assertEquals(BoardPeriod.WEEK, BoardPeriod.byId("Woche").orElseThrow());
        assertEquals(BoardPeriod.TOTAL, BoardPeriod.byId("gesamt").orElseThrow());
        assertEquals(BoardCategory.FORTUNE_WHEEL, BoardCategory.byId("Glücksrad").orElseThrow());
        assertEquals(BoardCategory.DERBY, BoardCategory.byId("entenrennen").orElseThrow());
        assertEquals(BoardCategory.BIGGEST_WIN, BoardCategory.DERBY.next());
    }
}
