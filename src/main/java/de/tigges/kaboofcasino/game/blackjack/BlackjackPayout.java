package de.tigges.kaboofcasino.game.blackjack;

import de.tigges.kaboofcasino.game.FairPayout;
import de.tigges.kaboofcasino.wager.ItemAmount;
import java.util.List;

/**
 * What one stake pays ({@code BlackjackTableBlockEntity.Events.decided} 957-966, {@code CasinoExchange.payout} without
 * an exchange rule): {@code halves} of the stake in half items, rounded fairly (an odd blackjack stake pays 2.5 n as
 * floor or ceil, 50:50); a push returns the stake unchanged (the refund path). The mod rolls
 * {@code level.getRandom().nextInt(2)} per stake; the plugin derives the roll from the round's seed and the bet slot
 * ({@link FairPayout#roll}), fixed at the deal, so the stored stand payout and the final payout use the same roll.
 * Pure.
 */
public final class BlackjackPayout {

    private BlackjackPayout() {
    }

    /** The roll of bet slot {@code slot} (0..2) for a round seed. */
    public static int roll(long seed, int slot) {
        return FairPayout.roll(seed, slot, 2);
    }

    /** Items paid back for one stake (empty for a loss). */
    public static List<ItemAmount> payout(ItemAmount stake, BlackjackRound.Result result, int roll) {
        if (result.isPush()) {
            return List.of(stake);
        }
        int n = FairPayout.share(stake.count() * result.halves, 2, roll);
        return n > 0 ? List.of(new ItemAmount(stake.item(), n)) : List.of();
    }
}
