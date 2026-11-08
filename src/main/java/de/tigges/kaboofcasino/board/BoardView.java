package de.tigges.kaboofcasino.board;

import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.util.ChipText;
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
    static final TextColor SILVER = TextColor.color(0xD4D8DE);
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
        if (c != BoardCategory.MULTIPLIER) {
            return ChipText.number(v);
        }
        long whole = v / 100;
        long frac = v % 100;
        String f = frac == 0 ? "" : frac % 10 == 0 ? "," + frac / 10 : "," + (frac < 10 ? "0" : "") + frac;
        return "×" + ChipText.number(whole) + f;
    }

    public static TextColor rankColor(int rank) {
        return switch (rank) {
            case 1 -> GOLD;
            case 2 -> SILVER;
            case 3 -> BRONZE;
            default -> CREAM;
        };
    }

    /**
     * A name cut to {@code maxPx} pixels of the default font (approximate advances), with "…" when cut. Minecraft
     * names are at most 16 characters.
     */
    public static String fit(String name, int maxPx) {
        if (width(name) <= maxPx) {
            return name;
        }
        StringBuilder b = new StringBuilder();
        int used = width("…");
        for (int i = 0; i < name.length(); i++) {
            int w = advance(name.charAt(i));
            if (used + w > maxPx) {
                break;
            }
            b.append(name.charAt(i));
            used += w;
        }
        return b + "…";
    }

    /** The default font's advance of a character (glyph width + 1), close enough for names and numbers. */
    static int advance(char ch) {
        return switch (ch) {
            case 'i', '!', '.', ',', ':', ';', '|', '\'' -> 2;
            case 'l', '`' -> 3;
            case 't', 'I', '[', ']', ' ' -> 4;
            case 'f', 'k', '<', '>', '(', ')', '{', '}', '"', '*' -> 5;
            case '@', '~', '…' -> 7;
            default -> 6;
        };
    }

    public static int width(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            w += advance(s.charAt(i));
        }
        return w;
    }

    /**
     * The page: {@code entries} (already the top list) with ranks; names cut to {@code namePx}, always
     * {@link BoardRig#ROWS} rows (blank ones below), each column padded to {@code leftPx} / {@code rightPx} by its
     * spacer line. Empty: the "Noch keine Einträge" line in the left column.
     */
    public static Page page(BoardPin.Page page, List<BoardEntry> entries, int minStake, int namePx, int leftPx,
                            int rightPx, boolean loading, Messages m) {
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
            right.add(Component.text(value(page.category(), e.value()), color));
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
