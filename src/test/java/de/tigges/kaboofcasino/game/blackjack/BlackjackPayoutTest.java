package de.tigges.kaboofcasino.game.blackjack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.wager.ItemAmount;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Per-stake payouts (games.md 3 "Payouts"): halves / 2 with the fair roll, push = the stake back. */
class BlackjackPayoutTest {

    private static long paid(long stake, BlackjackRound.Result r, int roll) {
        return BlackjackPayout.payout(new ItemAmount("minecraft:diamond", stake), r, roll).stream()
                .mapToLong(ItemAmount::count).sum();
    }

    @Test
    void everyResult() {
        for (int roll = 0; roll < 2; roll++) {
            assertEquals(20, paid(10, BlackjackRound.Result.WIN, roll));
            assertEquals(20, paid(10, BlackjackRound.Result.WIN_DEALER_BUST, roll));
            assertEquals(25, paid(10, BlackjackRound.Result.BLACKJACK, roll));
            assertEquals(10, paid(10, BlackjackRound.Result.PUSH, roll));
            assertEquals(10, paid(10, BlackjackRound.Result.PUSH_BLACKJACKS, roll));
            assertEquals(0, paid(10, BlackjackRound.Result.LOSE, roll));
            assertEquals(0, paid(10, BlackjackRound.Result.LOSE_BUST, roll));
            assertEquals(0, paid(10, BlackjackRound.Result.LOSE_DEALER_BLACKJACK, roll));
        }
        assertEquals(List.of(), BlackjackPayout.payout(new ItemAmount("minecraft:diamond", 3),
                BlackjackRound.Result.LOSE, 0));
    }

    @Test
    void anOddBlackjackRoundsFairly() {
        // 1 diamond: 2 or 3 (roll 0 pays the extra half), 3 diamonds: 7 or 8
        assertEquals(3, paid(1, BlackjackRound.Result.BLACKJACK, 0));
        assertEquals(2, paid(1, BlackjackRound.Result.BLACKJACK, 1));
        assertEquals(8, paid(3, BlackjackRound.Result.BLACKJACK, 0));
        assertEquals(7, paid(3, BlackjackRound.Result.BLACKJACK, 1));
        // the seeded roll is 50:50 over seeds: mean 2.5 per item
        long sum = 0;
        int n = 200_000;
        for (int seed = 0; seed < n; seed++) {
            sum += paid(1, BlackjackRound.Result.BLACKJACK, BlackjackPayout.roll(seed * 0x9E3779B97F4A7C15L, seed % 3));
        }
        double mean = (double) sum / n;
        assertTrue(Math.abs(mean - 2.5) < 0.005, "mean " + mean);
    }

    @Test
    void doubledStakesPayFourTimesTheBase() {
        assertEquals(8, paid(4, BlackjackRound.Result.WIN, 0));
        assertEquals(4, paid(4, BlackjackRound.Result.PUSH, 1));
    }
}
