package de.tigges.kaboofcasino.board;

import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.util.ChipText;
import de.tigges.kaboofcasino.util.FontWidth;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.TextColor;

/**
 * The texts of a leaderboard page, server-free: title, subtitle, and the rows as two columns (rank and name left,
 * value right). Each column ends with a line of spaces that is wider than any entry, so the client aligns every line
 * of a column to the same edge (a text display centres its widest line). Pure.
 */
public final class BoardView {

    /** The mod's gold, silver and bronze for places 1-3, cream for the rest. */
    static final TextColor GOLD = TextColor.color(0xF2C65A);
    static final TextColor SILVER = TextColor.color(0xB9C3CE);
    static final TextColor BRONZE = TextColor.color(0xD08A4B);
    static final TextColor CREAM = TextColor.color(0xF3E7BF);
    static final TextColor MUTED = TextColor.color(0xA89F86);

    /** One rendered page: title, subtitle, the left and right columns (with their spacer line) and the row count. */
    public record Page(Component title, Component subtitle, Component names, Component values, int rows) {
    }

    private BoardView() {
    }

    public static Msg categoryMsg(BoardCategory c) {
        return switch (c) {
            case BIGGEST_WIN -> Msg.BOARD_CAT_GEWINN;
            case MULTIPLIER -> Msg.BOARD_CAT_MULTIPLIKATOR;
            case NET -> Msg.BOARD_CAT_NETTO;
            case STAKED -> Msg.BOARD_CAT_EINSATZ;
            case ROUNDS -> Msg.BOARD_CAT_RUNDEN;
            case SLOT_MACHINE -> Msg.BOARD_CAT_SPIELAUTOMAT;
            case FORTUNE_WHEEL -> Msg.BOARD_CAT_GLUECKSRAD;
            case PLINKO -> Msg.BOARD_CAT_PLINKO;
            case ROULETTE -> Msg.BOARD_CAT_ROULETTE;
            case BLACKJACK -> Msg.BOARD_CAT_BLACKJACK;
            case POKER -> Msg.BOARD_CAT_POKER;
            case DERBY -> Msg.BOARD_CAT_ENTENRENNEN;
        };
    }

    public static Msg periodMsg(BoardPeriod p) {
        return p == BoardPeriod.WEEK ? Msg.BOARD_PERIOD_WEEK : Msg.BOARD_PERIOD_TOTAL;
    }

    /** "in Chips", "ab 25 Einsatz" or "Runden". */
    public static Component unit(BoardCategory c, int minStake, Messages m) {
        return switch (c) {
            case MULTIPLIER -> m.render(Msg.BOARD_UNIT_MULTIPLIER, Messages.text("min", ChipText.chips(minStake)));
            case ROUNDS -> m.render(Msg.BOARD_UNIT_ROUNDS);
            default -> m.render(Msg.BOARD_UNIT_CHIPS);
        };
    }

    /** A value as the board shows it: chips and rounds "1.250", the multiplier "×12,5" (hundredths, trimmed). */
    public static String value(BoardCategory c, long v) {
        return value(c, v, false);
    }

    /** As {@link #value(BoardCategory, long)}; {@code compact} (the narrow column) shortens big numbers. */
    public static String value(BoardCategory c, long v, boolean compact) {
        if (c != BoardCategory.MULTIPLIER) {
            return compact ? compact(v) : ChipText.number(v);
        }
        long whole = v / 100;
        long frac = v % 100;
        String f = frac == 0 ? "" : frac % 10 == 0 ? "," + frac / 10 : "," + (frac < 10 ? "0" : "") + frac;
        return "×" + ChipText.number(whole) + f;
    }

    /**
     * A big number short, never rounded up: up to 9.999 as it is, then "12,3 Tsd.", "123 Tsd.", "1,2 Mio.",
     * "123 Mio.".
     */
    public static String compact(long v) {
        if (v < 10_000) {
            return ChipText.number(v);
        }
        if (v < 1_000_000) {
            return v < 100_000 ? (v / 1000) + "," + (v / 100 % 10) + " Tsd." : ChipText.number(v / 1000) + " Tsd.";
        }
        return v < 100_000_000 ? (v / 1_000_000) + "," + (v / 100_000 % 10) + " Mio."
                : ChipText.number(v / 1_000_000) + " Mio.";
    }

    public static TextColor rankColor(int rank) {
        return switch (rank) {
            case 1 -> GOLD;
            case 2 -> SILVER;
            case 3 -> BRONZE;
            default -> CREAM;
        };
    }

    /** A name cut to {@code maxPx} pixels of the default font, with "…" when cut ({@link FontWidth#fit}). */
    public static String fit(String name, int maxPx) {
        return FontWidth.fit(name, maxPx);
    }

    static int advance(char ch) {
        return FontWidth.advance(ch);
    }

    public static int width(String s) {
        return FontWidth.width(s);
    }

    /**
     * The page: {@code entries} (already the top list) with ranks; names cut to {@code namePx}, always
     * {@link BoardRig#ROWS} rows (blank ones below), each column padded to {@code leftPx} / {@code rightPx} by its
     * spacer line. Empty: the "Noch keine Einträge" line in the left column.
     */
    public static Page page(BoardPin.Page page, List<BoardEntry> entries, int minStake, int namePx, int leftPx,
                            int rightPx, boolean loading, Messages m) {
        return page(page, entries, minStake, namePx, leftPx, rightPx, false, loading, m);
    }

    /** As above; {@code compact} values for the narrow column. */
    public static Page page(BoardPin.Page page, List<BoardEntry> entries, int minStake, int namePx, int leftPx,
                            int rightPx, boolean compact, boolean loading, Messages m) {
        Component title = m.render(Msg.BOARD_TITLE, Messages.component("category", m.render(categoryMsg(
                page.category()))));
        Component subtitle = m.render(Msg.BOARD_SUBTITLE, Messages.component("period", m.render(periodMsg(
                page.period()))), Messages.component("unit", unit(page.category(), minStake, m)));
        List<Component> left = new ArrayList<>();
        List<Component> right = new ArrayList<>();
        if (entries.isEmpty()) {
            left.add(m.render(loading ? Msg.BOARD_LOADING : Msg.BOARD_EMPTY));
            right.add(Component.empty());
        }
        for (int i = 0; i < entries.size(); i++) {
            BoardEntry e = entries.get(i);
            TextColor color = rankColor(i + 1);
            left.add(Component.text().append(Component.text((i + 1) + ". ", i < 3 ? color : MUTED))
                    .append(Component.text(fit(e.name(), namePx), color)).build());
            right.add(Component.text(value(page.category(), e.value(), compact), color));
        }
        while (left.size() < BoardRig.ROWS) {
            left.add(Component.empty());
            right.add(Component.empty());
        }
        left.add(Component.text(" ".repeat(Math.max(1, leftPx / advance(' ')))));
        right.add(Component.text(" ".repeat(Math.max(1, rightPx / advance(' ')))));
        return new Page(title, subtitle, Component.join(JoinConfiguration.newlines(), left),
                Component.join(JoinConfiguration.newlines(), right), left.size());
    }
}
