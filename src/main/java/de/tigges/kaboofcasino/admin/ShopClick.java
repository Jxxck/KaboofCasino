package de.tigges.kaboofcasino.admin;

import de.tigges.kaboofcasino.cashier.ShopEntry;
import de.tigges.kaboofcasino.util.Keys;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.api.BinaryTagHolder;

/**
 * One click in the cashier's shop dialog (the croupier's hand on a cashier, step 9). Every button sends the custom
 * click {@code kaboofcasino:cashier_shop} with {@code {do:"<action>"}}; the client merges the dialog's inputs: per
 * prize of the page {@code price_<i>} and {@code limit_<i>} (text) and {@code remove_<i>} (checkbox), and for the
 * off-hand item {@code new_price} and {@code new_limit}. Nothing in the payload names a cashier or a prize: the target
 * and the page's prizes are the server's session ({@link ShopEditor}). {@link #parse} accepts only known actions, a
 * whole price 1..{@value ShopEntry#MAX_PRICE} (digits, dots or spaces as separators), a limit
 * 0..{@value ShopEntry#MAX_LIMIT} (empty = 0) and a checkbox per prize; anything else makes the inputs invalid. Pure.
 *
 * @param edits the page's prizes by their index on the page, present only if every input is valid
 * @param added price and limit of the off-hand item (only for {@link Action#ADD}, only if valid)
 */
record ShopClick(Action action, Optional<Map<Integer, Edit>> edits, Optional<Edit> added) {

    static final Key KEY = Keys.key("cashier_shop");
    /** {@link #KEY} as text (a Bukkit key never equals an Adventure key object). */
    static final String ID = KEY.asString();
    static final String DO = "do";
    static final String NEW_PRICE = "new_price";
    static final String NEW_LIMIT = "new_limit";
    /** Longest typed price ("100.000.000") and limit. */
    static final int PRICE_LENGTH = 13;
    static final int LIMIT_LENGTH = 4;

    enum Action {
        /** "Speichern": apply the page's inputs, show again. */
        SAVE,
        /** "Nebenhand hinzufügen": apply the inputs, add the off-hand item. */
        ADD,
        /** Page buttons: apply the inputs, show the other page. */
        PREV, NEXT,
        /** "Fertig" and ESC: apply valid inputs, close. */
        DONE;

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        String snbt() {
            return "{" + DO + ":\"" + id() + "\"}";
        }

        BinaryTagHolder payload() {
            return BinaryTagHolder.binaryTagHolder(snbt());
        }
    }

    /** A prize's new values ({@code remove}: drop it). */
    record Edit(long price, int limit, boolean remove) {
    }

    static String price(int i) {
        return "price_" + i;
    }

    static String limit(int i) {
        return "limit_" + i;
    }

    static String remove(int i) {
        return "remove_" + i;
    }

    /**
     * Reads a click of a dialog that showed {@code shown} prizes and ({@code withNew}) the off-hand item. Empty for an
     * unknown action or {@link Action#ADD} without an off-hand item.
     */
    static Optional<ShopClick> parse(int shown, boolean withNew, Function<String, String> text,
                                     Function<String, Boolean> bool) {
        String id = text.apply(DO);
        Action action = null;
        for (Action a : Action.values()) {
            if (a.id().equals(id)) {
                action = a;
            }
        }
        if (action == null || action == Action.ADD && !withNew) {
            return Optional.empty();
        }
        Map<Integer, Edit> edits = new LinkedHashMap<>();
        boolean valid = true;
        for (int i = 0; i < shown && valid; i++) {
            Long price = number(text.apply(price(i)), 1, ShopEntry.MAX_PRICE, false);
            Long limit = number(text.apply(limit(i)), 0, ShopEntry.MAX_LIMIT, true);
            Boolean remove = bool.apply(remove(i));
            if (price == null || limit == null || remove == null) {
                valid = false;
            } else {
                edits.put(i, new Edit(price, limit.intValue(), remove));
            }
        }
        Optional<Edit> added = Optional.empty();
        if (action == Action.ADD) {
            Long price = number(text.apply(NEW_PRICE), 1, ShopEntry.MAX_PRICE, false);
            Long limit = number(text.apply(NEW_LIMIT), 0, ShopEntry.MAX_LIMIT, true);
            if (price != null && limit != null) {
                added = Optional.of(new Edit(price, limit.intValue(), false));
            }
        }
        return Optional.of(new ShopClick(action, valid ? Optional.of(Map.copyOf(edits)) : Optional.empty(), added));
    }

    /**
     * A typed whole number in min..max: digits, optionally grouped in thousands by ',', '.', ' ' or '_' ("1,250",
     * "1.250"), surrounding spaces ignored; empty is 0 if {@code emptyIsZero}. Null if invalid.
     */
    static Long number(String typed, long min, long max, boolean emptyIsZero) {
        if (typed == null) {
            return null;
        }
        String s = typed.strip();
        if (s.isEmpty()) {
            return emptyIsZero && min <= 0 ? 0L : null;
        }
        if (s.length() > PRICE_LENGTH || !s.matches("[0-9]+|[0-9]{1,3}([,. _][0-9]{3})+")) {
            return null;
        }
        String digits = s.replaceAll("[,. _]", "");
        if (digits.length() > 12) {
            return null;
        }
        long v = Long.parseLong(digits);
        return v < min || v > max ? null : v;
    }
}
