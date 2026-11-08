package de.tigges.kaboofcasino.cashier;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * How often a player bought each limited prize today (PLAN-2 C3: "Limit pro Spieler und Tag"). Kept in the player's
 * PDC ({@code kaboofcasino:shop_limits}), i.e. in the same file as his inventory, so a crash rolls a purchase and its
 * count back together. One string {@code 1|2026-11-08|<key>=<n>,<key>=<n>}; a stored day other than today (Berlin)
 * reads as empty, so the counts start afresh at midnight without any timer. Immutable, pure.
 *
 * @param day    the Berlin date the counts belong to
 * @param counts purchases per {@link ShopEntry#key()}
 */
public record DailyLimits(LocalDate day, Map<String, Integer> counts) {

    public static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    static final String VERSION = "1";
    private static final Pattern KEY = Pattern.compile("[0-9a-f]{16}");

    public DailyLimits {
        Objects.requireNonNull(day, "day");
        counts = Map.copyOf(counts);
    }

    /** The Berlin date of an instant (the limits' day). */
    public static LocalDate day(Instant now) {
        return LocalDate.ofInstant(now, BERLIN);
    }

    public static DailyLimits empty(LocalDate today) {
        return new DailyLimits(today, Map.of());
    }

    /**
     * Reads the stored string for {@code today}: nothing stored, another day or an unreadable string count as no
     * purchases today (an unreadable one cannot hold today's counts: it was not written by this version).
     */
    public static DailyLimits read(String stored, LocalDate today) {
        if (stored == null) {
            return empty(today);
        }
        String[] parts = stored.split("\\|", -1);
        if (parts.length != 3 || !parts[0].equals(VERSION)) {
            return empty(today);
        }
        LocalDate day;
        try {
            day = LocalDate.parse(parts[1]);
        } catch (DateTimeParseException e) {
            return empty(today);
        }
        if (!day.equals(today)) {
            return empty(today);
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (!parts[2].isEmpty()) {
            for (String pair : parts[2].split(",")) {
                int eq = pair.indexOf('=');
                if (eq < 0 || !KEY.matcher(pair.substring(0, eq)).matches()) {
                    return empty(today);
                }
                try {
                    int n = Integer.parseInt(pair.substring(eq + 1));
                    if (n < 0) {
                        return empty(today);
                    }
                    counts.put(pair.substring(0, eq), n);
                } catch (NumberFormatException e) {
                    return empty(today);
                }
            }
        }
        return new DailyLimits(day, counts);
    }

    public int count(String key) {
        return counts.getOrDefault(key, 0);
    }

    /** Purchases of the entry still possible today ({@code Integer.MAX_VALUE} without a limit). */
    public int left(ShopEntry entry) {
        return entry.limit() == 0 ? Integer.MAX_VALUE : Math.max(0, entry.limit() - count(entry.key()));
    }

    /** One more purchase of {@code key}. */
    public DailyLimits plus(String key) {
        Map<String, Integer> next = new LinkedHashMap<>(counts);
        next.merge(key, 1, Integer::sum);
        return new DailyLimits(day, next);
    }

    public String encode() {
        StringBuilder b = new StringBuilder(VERSION).append('|').append(day).append('|');
        boolean first = true;
        for (Map.Entry<String, Integer> e : new java.util.TreeMap<>(counts).entrySet()) {
            if (!first) {
                b.append(',');
            }
            b.append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }
        return b.toString();
    }
}
