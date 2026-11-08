package de.tigges.kaboofcasino.board;

import java.util.Locale;
import java.util.Optional;

/**
 * What a leaderboard ranks (PLAN-2 C4), over chip rounds only. A round's win is its payout minus its stake (a push or
 * a loss is no win); the multiplier is payout / stake, counted from a minimum stake ({@code leaderboard.min-stake});
 * net profit is the sum of payout minus stake, listed only while positive. The per-game categories are the biggest win
 * in one game ({@link #game()} = the round log's game id). {@link #id()} is the command word and the pin stored in a
 * board's record. Pure.
 */
public enum BoardCategory {
    BIGGEST_WIN("gewinn", null),
    MULTIPLIER("multiplikator", null),
    NET("netto", null),
    STAKED("einsatz", null),
    ROUNDS("runden", null),
    SLOT_MACHINE("spielautomat", "slot_machine"),
    FORTUNE_WHEEL("gluecksrad", "fortune_wheel"),
    PLINKO("plinko", "plinko"),
    ROULETTE("roulette", "roulette"),
    BLACKJACK("blackjack", "blackjack"),
    POKER("poker", "poker"),
    DERBY("entenrennen", "derby");

    private final String id;
    private final String game;

    BoardCategory(String id, String game) {
        this.id = id;
        this.game = game;
    }

    public String id() {
        return id;
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

    /** By command word, case-insensitive ("Glücksrad" and "gluecksrad" both work). */
    public static Optional<BoardCategory> byId(String id) {
        String k = id.toLowerCase(Locale.ROOT).replace("ü", "ue").replace("ä", "ae").replace("ö", "oe")
                .replace("ß", "ss");
        for (BoardCategory c : values()) {
            if (c.id.equals(k)) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }

    /** The next category in the board's cycle (after the last: the first). */
    public BoardCategory next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
