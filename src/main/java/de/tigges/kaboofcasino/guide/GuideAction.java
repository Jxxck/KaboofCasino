package de.tigges.kaboofcasino.guide;

import de.tigges.kaboofcasino.util.Keys;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.api.BinaryTagHolder;
import net.kyori.adventure.text.event.ClickEvent;

/**
 * One click in a guide dialog. Every button and every link of the guide sends the custom click
 * {@code kaboofcasino:guide} with a compound payload of two strings, {@code do} (the kind) and {@code to} (the target
 * spread, 1-based; absent for back and close). The client cannot run anything: the server reads the two strings and
 * validates them ({@link #parse}); the target is absolute, so a double click on a stale dialog lands on the same
 * spread. Pure.
 *
 * @param page target spread (1-based) for {@link Kind#TURN}, {@link Kind#LINK} and {@link Kind#TAB}, else 0
 */
public record GuideAction(Kind kind, int page) {

    public static final Key KEY = Keys.key("guide");
    /** {@link #KEY} as text (compare with this: a Bukkit key never equals an Adventure key object). */
    public static final String ID = KEY.asString();
    static final String DO = "do";
    static final String TO = "to";

    public enum Kind {
        /** Previous / next page: clears the link history (mod: {@code turn(target, false)}). */
        TURN,
        /** A tile or an item with an index entry: remembers the current page for {@link #BACK}. */
        LINK,
        /** A bookmark: like a link (mod: {@code turn(target, true)}). */
        TAB,
        /** "Inhalt": back to the page the last link or tab left. */
        BACK,
        /** "Fertig" and ESC. */
        CLOSE;

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public GuideAction {
        boolean paged = kind == Kind.TURN || kind == Kind.LINK || kind == Kind.TAB;
        if (paged ? page < 1 : page != 0) {
            throw new IllegalArgumentException(kind + " with page " + page);
        }
    }

    public static GuideAction turn(int page) {
        return new GuideAction(Kind.TURN, page);
    }

    public static GuideAction link(int page) {
        return new GuideAction(Kind.LINK, page);
    }

    public static GuideAction tab(int page) {
        return new GuideAction(Kind.TAB, page);
    }

    public static GuideAction back() {
        return new GuideAction(Kind.BACK, 0);
    }

    public static GuideAction close() {
        return new GuideAction(Kind.CLOSE, 0);
    }

    /** The payload as SNBT: {@code {do:"link",to:"12"}}. */
    public String snbt() {
        return page == 0 ? "{" + DO + ":\"" + kind.id() + "\"}"
                : "{" + DO + ":\"" + kind.id() + "\"," + TO + ":\"" + page + "\"}";
    }

    public BinaryTagHolder payload() {
        return BinaryTagHolder.binaryTagHolder(snbt());
    }

    /** The click event of a text link (dialog body text runs it like a button). */
    public ClickEvent<?> click() {
        return ClickEvent.custom(KEY, payload());
    }

    /**
     * Reads a payload: {@code text} returns a string entry of the click's compound or null (Paper's
     * {@code DialogResponseView#getText}). Anything unexpected (unknown kind, missing, non-numeric or out-of-range
     * page, a page on back/close) is empty.
     *
     * @param pages number of spreads; targets must be 1..pages
     */
    public static Optional<GuideAction> parse(Function<String, String> text, int pages) {
        String kindText = text.apply(DO);
        if (kindText == null) {
            return Optional.empty();
        }
        Kind kind = null;
        for (Kind k : Kind.values()) {
            if (k.id().equals(kindText)) {
                kind = k;
            }
        }
        if (kind == null) {
            return Optional.empty();
        }
        String to = text.apply(TO);
        if (kind == Kind.BACK || kind == Kind.CLOSE) {
            return to == null ? Optional.of(new GuideAction(kind, 0)) : Optional.empty();
        }
        if (to == null || to.isEmpty() || to.length() > 3 || !to.chars().allMatch(c -> c >= '0' && c <= '9')) {
            return Optional.empty();
        }
        int page = Integer.parseInt(to);
        return page >= 1 && page <= pages ? Optional.of(new GuideAction(kind, page)) : Optional.empty();
    }
}
