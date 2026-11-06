package de.tigges.kaboofcasino.wager;

/**
 * State of a round record (server.md 1.2). Step 1 only needs DECIDED: the outcome and payout are fixed at the commit.
 * Blackjack/poker will add PLAYING (decision phase), the derby ENROLLED (lobby).
 */
public enum RoundState {
    DECIDED
}
