package de.tigges.kaboofcasino.wager;

/**
 * State of a round record (server.md 1.2).
 * <ul>
 *   <li>{@code DECIDED}: the outcome and payout are fixed at the commit (slot, wheel, Plinko, roulette).</li>
 *   <li>{@code PLAYING}: a hand with decisions still open (blackjack). Its payout is always what the hand pays if it
 *       is settled now with the mod's timeout rule (blackjack: stand), finished with the cards the table's shoe would
 *       deal; the machine rewrites it after every decision (hit, double down). Quit, stop, death, the watchdog and
 *       the join after a crash therefore settle it like any record: the stored payout.</li>
 * </ul>
 * Added in ledger version 2 ({@code PLAYING}); version 1 only knows {@code DECIDED}.
 */
public enum RoundState {
    DECIDED,
    PLAYING
}
