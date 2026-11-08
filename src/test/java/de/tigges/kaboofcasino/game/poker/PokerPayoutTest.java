package de.tigges.kaboofcasino.game.poker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.tigges.kaboofcasino.wager.ItemAmount;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Per-stake payouts (games.md 4 "Payouts"): thirds of the called stake (3 x the ante), push = the stake back, fold and
 * loss nothing. Ante A, call 2A: WIN = A + kA + 2A + 2A, NO_QUALIFY = A + kA + 2A, PUSH = 3A.
 */
class PokerPayoutTest {

    private static long paid(long stake, PokerRound.Result r, int thirds, int roll) {
        return PokerPayout.payout(new ItemAmount("minecraft:diamond", stake), r, thirds, roll).stream()
                .mapToLong(ItemAmount::count).sum();
    }

    @Test
    void everyResultAndEveryPaytableLine() {
        int[] k = {100, 20, 10, 3, 2, 1};
        for (long ante : de.tigges.kaboofcasino.game.wheel.WheelOddsTest.stakes()) {
            long called = ante * 3;
            for (int roll = 0; roll < 3; roll++) {
                for (int pays : k) {
                    assertEquals(5 * ante + pays * ante, paid(called, PokerRound.Result.WIN, 5 + pays, roll));
                    assertEquals(2 * ante + ante + pays * ante, paid(called, PokerRound.Result.NO_QUALIFY,
                            3 + pays, roll));
                }
                assertEquals(called, paid(called, PokerRound.Result.PUSH, 3, roll));
                assertEquals(0, paid(called, PokerRound.Result.LOSE, 0, roll));
                assertEquals(0, paid(ante, PokerRound.Result.FOLD, 0, roll), "a fold: the ante is lost");
            }
        }
    }

    @Test
    void thePushReturnsTheStakeItself() {
        ItemAmount stake = new ItemAmount("minecraft:emerald", 12);
        assertEquals(List.of(stake), PokerPayout.payout(stake, PokerRound.Result.PUSH, 3, 2));
        assertEquals(List.of(), PokerPayout.payout(stake, PokerRound.Result.LOSE, 0, 0));
        assertEquals(List.of(new ItemAmount("minecraft:emerald", 12 * 105 / 3)),
                PokerPayout.payout(stake, PokerRound.Result.WIN, 105, 1), "royal flush: 100:1 on the ante");
    }

    @Test
    void netLinesAreSignedPerItemKind() {
        java.util.UUID id = java.util.UUID.randomUUID();
        List<PokerController.Seat> antes = List.of(
                new PokerController.Seat(0, new ItemAmount("minecraft:diamond", 2), id),
                new PokerController.Seat(1, new ItemAmount("minecraft:emerald", 5), id),
                new PokerController.Seat(2, new ItemAmount("minecraft:diamond", 1), id));
        assertEquals(List.of(new PokerSnapshot.Net("minecraft:diamond", -3), new PokerSnapshot.Net("minecraft:emerald",
                -5)), PokerController.net(antes, PokerRound.Result.FOLD, 0, 1L), "a fold: both antes lost");
        List<PokerController.Seat> called = antes.stream().map(s -> new PokerController.Seat(s.slot(),
                new ItemAmount(s.stake().item(), s.stake().count() * 3), s.round())).toList();
        // a flush wins: ante 2:1, call 1:1 -> net + 4 x the ante per stake
        assertEquals(List.of(new PokerSnapshot.Net("minecraft:diamond", 12), new PokerSnapshot.Net("minecraft:emerald",
                20)), PokerController.net(called, PokerRound.Result.WIN, 7, 1L));
        // a push changes nothing: all lines kept with 0 (the mod drops 0-lines only when another line is not 0)
        assertEquals(List.of(new PokerSnapshot.Net("minecraft:diamond", 0), new PokerSnapshot.Net("minecraft:emerald",
                0)), PokerController.net(called, PokerRound.Result.PUSH, 3, 1L));
        assertEquals(List.of(new PokerSnapshot.Net("minecraft:diamond", -9), new PokerSnapshot.Net("minecraft:emerald",
                -15)), PokerController.net(called, PokerRound.Result.LOSE, 0, 1L));
    }

    @Test
    void theRollIsPerSlot() {
        assertEquals(PokerPayout.roll(42L, 1), PokerPayout.roll(42L, 1));
        for (int slot = 0; slot < 3; slot++) {
            int roll = PokerPayout.roll(99L, slot);
            assertEquals(true, roll >= 0 && roll < 3);
        }
    }
}
