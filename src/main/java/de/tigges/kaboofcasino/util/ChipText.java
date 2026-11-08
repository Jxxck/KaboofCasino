package de.tigges.kaboofcasino.util;

/**
 * The one number format of the casino texts (step 9 review): German digit grouping with dots ("1.250",
 * "100.000.000"), and chip amounts with the right noun ("1 Chip", "25 Chips"). The receipt digit tiles of the cashier
 * group the same way. Pure.
 */
public final class ChipText {

    private ChipText() {
    }

    /** {@code 1250} -> "1.250", {@code -40} -> "-40". */
    public static String number(long n) {
        String digits = Long.toString(Math.abs(n));
        StringBuilder b = new StringBuilder(n < 0 ? "-" : "");
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) {
                b.append('.');
            }
            b.append(digits.charAt(i));
        }
        return b.toString();
    }

    /** A chip amount: "1 Chip", "0 Chips", "1.250 Chips". */
    public static String chips(long n) {
        return number(n) + (n == 1 ? " Chip" : " Chips");
    }
}
