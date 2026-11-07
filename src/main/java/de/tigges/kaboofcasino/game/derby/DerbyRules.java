package de.tigges.kaboofcasino.game.derby;

import de.tigges.kaboofcasino.game.Odds;
import de.tigges.kaboofcasino.wager.ItemAmount;
import java.util.List;
import java.util.function.IntUnaryOperator;
import java.util.function.LongSupplier;

/**
 * The derby's money rules ({@code DuckDerbyBlockEntity.launchPersonal}, games.md 7): the winner draw, the plan (with a
 * new seed if the planner rejects every attempt, games.md 7 open question 1), the runner-up and the payout per stake.
 * Pure.
 */
public final class DerbyRules {

    public static final int LANES = 4;
    public static final int PAYOUT_MULTIPLIER = 3;
    public static final int SECOND_MULTIPLIER = 1;
    /** Seeds tried before giving up on a plan (the mod: one, then a null plan; never seen in 3000 simulated plans). */
    static final int PLAN_SEEDS = 16;

    private DerbyRules() {
    }

    /** A drawn race: winner, seed and the plan of that pair. */
    public record Race(int winner, long seed, DuckDerbyRace.Plan plan) {

        public int second() {
            return runnerUp(plan);
        }
    }

    /**
     * {@code launchPersonal}: group races and natural odds ({@code chance < 0}) draw {@code nextInt(4)}; a solo race
     * with a forced chance uses {@code CasinoOdds.derby(lane, chance, nextInt)}; then {@code seed = nextLong()}.
     */
    public static Race draw(boolean group, int soloLane, int chance, IntUnaryOperator nextInt, LongSupplier nextLong) {
        int winner = group || chance < 0 ? nextInt.applyAsInt(LANES) : Odds.derby(soloLane, chance, nextInt);
        for (int i = 0; i < PLAN_SEEDS; i++) {
            long seed = nextLong.getAsLong();
            DuckDerbyRace.Plan plan = DuckDerbyRace.plan(winner, seed);
            if (plan != null) {
                return new Race(winner, seed, plan);
            }
        }
        throw new IllegalStateException("no race plan for winner " + winner + " after " + PLAN_SEEDS + " seeds");
    }

    /** {@code runnerUp}: the lane with rank 1, -1 without a plan. */
    public static int runnerUp(DuckDerbyRace.Plan plan) {
        if (plan == null) {
            return -1;
        }
        for (int lane = 0; lane < LANES; lane++) {
            if (plan.rank(lane) == 1) {
                return lane;
            }
        }
        return -1;
    }

    /** {@code CasinoExchange.payout(stake, 3 or 1, 1, 0, no rule, false)}: 3 x for the winner, the stake for 2nd. */
    public static List<ItemAmount> payout(ItemAmount stake, int lane, int winner, int second) {
        if (lane == winner) {
            return List.of(new ItemAmount(stake.item(), stake.count() * PAYOUT_MULTIPLIER));
        }
        if (lane == second) {
            return List.of(new ItemAmount(stake.item(), stake.count() * SECOND_MULTIPLIER));
        }
        return List.of();
    }

    /** {@code groupAllowed}: natural odds or exactly 25. */
    public static boolean groupAllowed(int chance) {
        return chance < 0 || chance == 25;
    }
}
