package de.tigges.kaboofcasino.config;

import java.time.ZoneId;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * The language of the casino ({@code language:} in config.yml): the texts file, the fallback names of the items, the
 * number format, the command words that are suggested, and the resource pack with its lettering. One language for
 * the whole server; it is fixed while the plugin runs (the pack is loaded once), so a change needs a restart.
 *
 * <p>The time zone of the weekly leaderboard and the daily shop limits ({@code time-zone:}) lives here too, because
 * the same pure classes need it.
 *
 * <p>Both are process-wide and set by the plugin when it starts. Before that (unit tests, tools) they come from
 * the system properties {@code kaboofcasino.language} and {@code kaboofcasino.time-zone}, else English and the
 * system's zone.
 */
public enum Language {
    EN("en", ',', '.', "k", "M"),
    DE("de", '.', ',', " Tsd.", " Mio.");

    private static volatile Language current = byCode(System.getProperty("kaboofcasino.language", "en")).orElse(EN);
    private static volatile ZoneId zone = zoneOf(System.getProperty("kaboofcasino.time-zone", "system"))
            .orElse(ZoneId.systemDefault());

    private final String code;
    private final char grouping;
    private final char decimal;
    private final String thousands;
    private final String millions;

    Language(String code, char grouping, char decimal, String thousands, String millions) {
        this.code = code;
        this.grouping = grouping;
        this.decimal = decimal;
        this.thousands = thousands;
        this.millions = millions;
    }

    /** The language everything is shown in. */
    public static Language current() {
        return current;
    }

    /** Sets the language (plugin start; tests). */
    public static void use(Language language) {
        current = Objects.requireNonNull(language);
    }

    /** The zone of weeks and days. */
    public static ZoneId zone() {
        return zone;
    }

    public static void useZone(ZoneId z) {
        zone = Objects.requireNonNull(z);
    }

    public static Optional<Language> byCode(String code) {
        String c = code == null ? "" : code.trim().toLowerCase(Locale.ROOT);
        for (Language l : values()) {
            if (l.code.equals(c)) {
                return Optional.of(l);
            }
        }
        return Optional.empty();
    }

    /** {@code system} (the server's own zone) or a zone id such as {@code Europe/Berlin}; empty if unknown. */
    public static Optional<ZoneId> zoneOf(String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty() || n.equalsIgnoreCase("system")) {
            return Optional.of(ZoneId.systemDefault());
        }
        try {
            return Optional.of(ZoneId.of(n));
        } catch (java.time.DateTimeException e) {
            return Optional.empty();
        }
    }

    public String code() {
        return code;
    }

    /** The English text in English, the German one in German. */
    public String pick(String english, String german) {
        return this == DE ? german : english;
    }

    /** {@link #pick} for the current language. */
    public static String text(String english, String german) {
        return current.pick(english, german);
    }

    /** {@code messages_en.yml} / {@code messages_de.yml}. */
    public String messagesFile() {
        return "messages_" + code + ".yml";
    }

    /** A file the pack tool built for this language, inside the plugin jar: {@code pack/<code>/<name>}. */
    public String packResource(String name) {
        return "pack/" + code + "/" + name;
    }

    /** Between groups of three digits: 1,250 / 1.250. */
    public char grouping() {
        return grouping;
    }

    /** Before the decimals: 2.5 / 2,5. */
    public char decimal() {
        return decimal;
    }

    /** After a number counted in thousands: 12.3k / 12,3 Tsd. */
    public String thousands() {
        return thousands;
    }

    /** After a number counted in millions: 1.2M / 1,2 Mio. */
    public String millions() {
        return millions;
    }
}
