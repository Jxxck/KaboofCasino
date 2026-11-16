package de.tigges.kaboofcasino.board;

import de.tigges.kaboofcasino.config.Language;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.Optional;

/**
 * The leaderboard's periods (PLAN-2 C4): this week (from Monday 00:00 in the casino's time zone,
 * {@link Language#zone()}) and all time. {@link #id()} is the English command word, the pin stored in a board and
 * the end of the message key; {@link #word()} is the command word in the casino's language. Pure.
 */
public enum BoardPeriod {
    WEEK("week", "woche"),
    TOTAL("total", "gesamt");

    private final String id;
    private final String german;

    BoardPeriod(String id, String german) {
        this.id = id;
        this.german = german;
    }

    public String id() {
        return id;
    }

    /** The command word in the casino's language ({@code week} / {@code woche}). */
    public String word() {
        return Language.text(id, german);
    }

    /** The message key of the period's subtitle ({@code board.period.<id>}). */
    public String messageKey() {
        return "board.period." + id;
    }

    /** The first millisecond this period counts from at {@code now}: Monday 00:00, or 0 for all time. */
    public long since(long now) {
        return this == TOTAL ? 0L : weekStart(now);
    }

    /** Monday 00:00 (casino time zone) of the week containing {@code now} (epoch milliseconds). */
    public static long weekStart(long now) {
        ZonedDateTime t = Instant.ofEpochMilli(now).atZone(Language.zone());
        ZonedDateTime monday = t.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).truncatedTo(ChronoUnit.DAYS);
        return monday.toInstant().toEpochMilli();
    }

    public static Optional<BoardPeriod> byId(String id) {
        String k = id.toLowerCase(Locale.ROOT);
        for (BoardPeriod p : values()) {
            if (p.id.equals(k) || p.german.equals(k)) {
                return Optional.of(p);
            }
        }
        return switch (k) {
            case "weekly", "this-week", "wochen", "diese-woche" -> Optional.of(WEEK);
            case "all", "all-time", "alltime", "alle", "immer" -> Optional.of(TOTAL);
            default -> Optional.empty();
        };
    }

    public BoardPeriod other() {
        return this == WEEK ? TOTAL : WEEK;
    }
}
