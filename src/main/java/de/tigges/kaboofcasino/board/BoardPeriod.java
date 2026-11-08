package de.tigges.kaboofcasino.board;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.Optional;

/**
 * The leaderboard's periods (PLAN-2 C4): this week (from Monday 00:00 Europe/Berlin, summer and winter time alike)
 * and all time. Pure.
 */
public enum BoardPeriod {
    WEEK("woche"),
    TOTAL("gesamt");

    /** The week's clock: Jack's server runs on Berlin time. */
    public static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    private final String id;

    BoardPeriod(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /** The message key of the period's subtitle ({@code board.period.<id>}). */
    public String messageKey() {
        return "board.period." + id;
    }

    /** The first millisecond this period counts from at {@code now}: Monday 00:00 Berlin, or 0 for all time. */
    public long since(long now) {
        return this == TOTAL ? 0L : weekStart(now);
    }

    /** Monday 00:00 Europe/Berlin of the week containing {@code now} (epoch milliseconds). */
    public static long weekStart(long now) {
        ZonedDateTime t = Instant.ofEpochMilli(now).atZone(ZONE);
        ZonedDateTime monday = t.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).truncatedTo(ChronoUnit.DAYS);
        return monday.toInstant().toEpochMilli();
    }

    public static Optional<BoardPeriod> byId(String id) {
        String k = id.toLowerCase(Locale.ROOT);
        for (BoardPeriod p : values()) {
            if (p.id.equals(k)) {
                return Optional.of(p);
            }
        }
        return switch (k) {
            case "week", "wochen", "diese-woche" -> Optional.of(WEEK);
            case "total", "all", "alle", "immer" -> Optional.of(TOTAL);
            default -> Optional.empty();
        };
    }

    public BoardPeriod other() {
        return this == WEEK ? TOTAL : WEEK;
    }
}
