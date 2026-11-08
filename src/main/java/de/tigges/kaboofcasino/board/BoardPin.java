package de.tigges.kaboofcasino.board;

import java.util.ArrayList;
import java.util.List;

/**
 * What the croupier's hand fixed on one board: a category and/or a period (null = the board cycles through them).
 * Stored in the machine record's data as {@code "cat=<id>;period=<id>"} (either part may be missing). Pure.
 */
public record BoardPin(BoardCategory category, BoardPeriod period) {

    public static final BoardPin NONE = new BoardPin(null, null);

    /** Reads a record's data; unknown parts are ignored (a board never fails to load over its pin). */
    public static BoardPin parse(String data) {
        BoardCategory category = null;
        BoardPeriod period = null;
        if (data != null) {
            for (String part : data.split(";")) {
                int eq = part.indexOf('=');
                if (eq < 0) {
                    continue;
                }
                String key = part.substring(0, eq).strip();
                String value = part.substring(eq + 1).strip();
                if (key.equals("cat")) {
                    category = BoardCategory.byId(value).orElse(null);
                } else if (key.equals("period")) {
                    period = BoardPeriod.byId(value).orElse(null);
                }
            }
        }
        return new BoardPin(category, period);
    }

    public String format() {
        List<String> parts = new ArrayList<>();
        if (category != null) {
            parts.add("cat=" + category.id());
        }
        if (period != null) {
            parts.add("period=" + period.id());
        }
        return String.join(";", parts);
    }

    public BoardPin withCategory(BoardCategory c) {
        return new BoardPin(c, period);
    }

    public BoardPin withPeriod(BoardPeriod p) {
        return new BoardPin(category, p);
    }

    /**
     * The pages a board shows in turn: every category (or the pinned one), each for both periods (or the pinned one),
     * category by category.
     */
    public List<Page> pages() {
        List<Page> out = new ArrayList<>();
        for (BoardCategory c : category != null ? List.of(category) : List.of(BoardCategory.values())) {
            for (BoardPeriod p : period != null ? List.of(period) : List.of(BoardPeriod.values())) {
                out.add(new Page(c, p));
            }
        }
        return List.copyOf(out);
    }

    /** One page of a board. */
    public record Page(BoardCategory category, BoardPeriod period) {
    }

    /**
     * Where a right click goes from {@code current}: the next category with the same period; with the category pinned,
     * the other period; with both pinned, nowhere (empty).
     */
    public java.util.Optional<Page> clicked(Page current) {
        if (category == null) {
            return java.util.Optional.of(new Page(current.category().next(), current.period()));
        }
        if (period == null) {
            return java.util.Optional.of(new Page(current.category(), current.period().other()));
        }
        return java.util.Optional.empty();
    }

    /** The page {@code current} becomes under this pin (after the hand changed it). */
    public Page fit(Page current) {
        return new Page(category != null ? category : current.category(), period != null ? period
                : current.period());
    }
}
