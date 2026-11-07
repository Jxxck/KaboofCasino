package de.tigges.kaboofcasino.game.derby;

import de.tigges.kaboofcasino.wager.ItemAmount;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What the machine shows, as the mod's renderer reads it from {@code DuckDerbyBlockEntity} ({@code extractRenderState},
 * {@code extractPanel}). Built by the controller every tick; all times are the world's game time.
 *
 * @param raceStart       game tick of the launch ({@code raceStart}), -1 without a race
 * @param plan            the race plan ({@code plan()}), null without a race; poses come from
 *                        {@code plan.pose(lane, gameTime - raceStart)} while {@code 0 <= elapsed <= endTick + 1}
 * @param shared          {@code groupSession()}: a group race is enrolling, running or still showing its result
 * @param enrolling       the group countdown runs
 * @param enrollmentUntil game tick at which the countdown ends (-1 when not enrolling; seconds shown =
 *                        {@code (enrollmentUntil - gameTime + 19) / 20}, progress over 200 ticks)
 * @param members         {@code participantCount()}: entrants now, or the last race's count
 * @param bettors         the entrants in join order (solo: the one player), for the panel's heads and stakes
 * @param displayed       the 12 shown bets of a solo race ({@code displayedBets}: slot = row * 4 + lane), null = empty
 */
public record DerbyView(long raceStart, DuckDerbyRace.Plan plan, boolean shared, boolean enrolling, long enrollmentUntil,
                        int members, List<DerbyMember> bettors, List<ItemAmount> displayed) {

    public static final int SLOTS = 12;

    public DerbyView {
        bettors = List.copyOf(bettors);
        List<ItemAmount> d = new ArrayList<>(Collections.nCopies(SLOTS, null));
        for (int i = 0; i < Math.min(SLOTS, displayed.size()); i++) {
            d.set(i, displayed.get(i));
        }
        displayed = Collections.unmodifiableList(d);
    }

    /** The idle machine. */
    public static DerbyView idle() {
        return new DerbyView(-1, null, false, false, -1, 0, List.of(), List.of());
    }

    /** {@code state.winnerShown}: the winner from the result tick on, else -1. */
    public int winnerShown(long gameTime) {
        double e = elapsed(gameTime);
        return e >= 0 && e >= plan.resultTick() ? plan.winner() : -1;
    }

    /** {@code state.secondShown}. */
    public int secondShown(long gameTime) {
        return winnerShown(gameTime) >= 0 ? DerbyRules.runnerUp(plan) : -1;
    }

    /** Race ticks since the launch, or -1 outside {@code 0 .. endTick + 1} (the renderer's {@code elapsed}). */
    public double elapsed(double gameTime) {
        if (plan == null || raceStart < 0) {
            return -1;
        }
        double e = gameTime - raceStart;
        return e < 0 || e > plan.endTick() + 1 ? -1 : e;
    }
}
