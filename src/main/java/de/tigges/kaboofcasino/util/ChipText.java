package de.tigges.kaboofcasino.util;

import de.tigges.kaboofcasino.config.Language;

/**
 * The one number format of the casino texts (step 9 review): digits grouped in threes as the casino's language does
 * it ("1,250" in English, "1.250" in German; {@link Language#grouping()}), and chip amounts with the right noun
 * ("1 chip", "25 chips"; German "1 Chip", "25 Chips"). The receipt digit tiles of the cashier group the same way.
 * Pure.
 */
public final class ChipText {

    private ChipText() {
    }

    /** {@code 1250} -> "1,250" ("1.250" in German), {@code -40} -> "-40". */
    public static String number(long n) {
        char group = Language.current().grouping();
        String digits = Long.toString(Math.abs(n));
        StringBuilder b = new StringBuilder(n < 0 ? "-" : "");
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) {
                b.append(group);
            }
            b.append(digits.charAt(i));
        }
        return b.toString();
    }

    /** A chip amount: "1 chip", "0 chips", "1,250 chips" (German: "1 Chip", "1.250 Chips"). */
    public static String chips(long n) {
        return number(n) + " " + noun(n);
    }

    /** "chip" / "chips" ("Chip" / "Chips" in German). */
    public static String noun(long n) {
        return Language.text(n == 1 ? "chip" : "chips", n == 1 ? "Chip" : "Chips");
    }

    /** A number with decimals as the casino's language writes it: "2.5" -> "2,5" in German. */
    public static String decimal(String plain) {
        return plain.replace('.', Language.current().decimal());
    }
}
