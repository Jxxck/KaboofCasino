package de.tigges.kaboofcasino.board;

import java.util.List;
import java.util.Map;

/**
 * One computation of every leaderboard (both periods), immutable; the boards and {@code /casino top} read it on MAIN.
 *
 * @param computedAt when it was computed (epoch ms)
 * @param weekStart  the week it counted "diese Woche" from
 * @param minStake   the multiplier's minimum stake it used
 * @param version    increases with every computation (boards redraw only on a new version)
 */
public record BoardSnapshot(long computedAt, long weekStart, int minStake, long version,
                            Map<BoardCategory, List<BoardEntry>> weekly, Map<BoardCategory, List<BoardEntry>> total) {

    public BoardSnapshot {
        weekly = Map.copyOf(weekly);
        total = Map.copyOf(total);
    }

    public List<BoardEntry> top(BoardCategory category, BoardPeriod period) {
        return (period == BoardPeriod.WEEK ? weekly : total).getOrDefault(category, List.of());
    }
}
