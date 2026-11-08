package de.tigges.kaboofcasino.board;

import java.util.UUID;

/**
 * One line of a leaderboard: the player, his latest name and the value (chips, rounds, or for the multiplier hundredths
 * of the factor: 1250 = x12.5).
 */
public record BoardEntry(UUID player, String name, long value) {
}
