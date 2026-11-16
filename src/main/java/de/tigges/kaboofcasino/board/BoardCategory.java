package de.tigges.kaboofcasino.board;

import java.util.Locale;
import java.util.Optional;

/**
 * What a leaderboard ranks (PLAN-2 C4), over chip rounds only. A round's win is its payout minus its stake (a push or
 * a loss is no win); the multiplier is payout / stake, counted from a minimum stake ({@code leaderboard.min-stake});
 * net profit is the sum of payout minus stake, listed only while positive. The per-game categories are the biggest win
 * in one game ({@link #game()} = the round log's game id). {@link #id()} is the English command word, the pin
 * stored in a board's record and the end of the message key; boards pinned by a German version carry the German word,
 * which {@link #byId} reads as well. Pure.
 */
public enum BoardCategory {
    BIGGEST_WIN("win", "gewinn", null),
    MULTIPLIER("multiplier", "multiplikator", null),
    NET("net", "netto", null),
    STAKED("staked", "einsatz", null),
    ROUNDS("rounds", "runden", null),
    SLOT_MACHINE("slots", "spielautomat", "slot_machine"),
    FORTUNE_WHEEL("wheel", "gluecksrad", "fortune_wheel"),
    PLINKO("plinko", "plinko", "plinko"),
    ROULETTE("roulette", "roulette", "roulette"),
    BLACKJACK("blackjack", "blackjack", "blackjack"),
    POKER("poker", "poker", "poker"),
    DERBY("derby", "entenrennen", "derby");

    private final String id;
    private final String german;
    private final String game;

    BoardCategory(String id, String german, String game) {
        this.id = id;
        this.german = german;
        this.game = game;
    }

    public String id() {
        return id;
    }

    /** The command word in the casino's language ({@code wheel} / {@code gluecksrad}). */
    public String word() {
        return de.tigges.kaboofcasino.config.Language.text(id, german);
    }

    /** The round log's game id for a per-game category, else null. */
    public String game() {
        return game;
    }

    /** A per-game "biggest win" category. */
    public boolean perGame() {
        return game != null;
    }

    /** The message key of the category's title ({@code board.category.<id>}). */
    public String messageKey() {
        return "board.category." + id;
    }

    /** By command word in either language, case-insensitive ("Glücksrad" and "gluecksrad" both work). */
    public static Optional<BoardCategory> byId(String id) {
        String k = id.toLowerCase(Locale.ROOT).replace("ü", "ue").replace("ä", "ae").replace("ö", "oe")
                .replace("ß", "ss");
        for (BoardCategory c : values()) {
            if (c.id.equals(k) || c.german.equals(k)) {
                return Optional.of(c);
            }
        }
        return switch (k) {
            case "slot", "slot-machine", "slot_machine" -> Optional.of(SLOT_MACHINE);
            case "fortune-wheel", "fortune_wheel" -> Optional.of(FORTUNE_WHEEL);
            case "biggest-win", "biggest_win" -> Optional.of(BIGGEST_WIN);
            default -> Optional.empty();
        };
    }

    /** The next category in the board's cycle (after the last: the first). */
    public BoardCategory next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
