package de.tigges.kaboofcasino.game.poker;

import de.tigges.kaboofcasino.game.FairPayout;
import de.tigges.kaboofcasino.wager.ItemAmount;
import java.util.List;

/**
 * What one stake pays ({@code PokerTableBlockEntity.Events.decided}: {@code CasinoExchange.payout(stake, thirds, 3,
 * roll, exchange, refund = isPush)} without an exchange rule): {@code thirds} of the stake in thirds of an item, a push
 * returns the stake unchanged. After the call the stake is 3 x the ante, so the thirds always come out whole (the roll
 * never matters, games.md 4); before the call the only result is the fold (0). The roll comes from the round's seed
 * and the bet slot ({@link FairPayout#roll}) instead of {@code level.getRandom().nextInt(3)}. Pure.
 */
public final class PokerPayout {

    private PokerPayout() {
    }

    public static int roll(long seed, int slot) {
        return FairPayout.roll(seed, slot, 3);
    }

    /** Items paid back for one stake (empty for a fold or a loss). */
    public static List<ItemAmount> payout(ItemAmount stake, PokerRound.Result result, int thirds, int roll) {
        if (result.isPush()) {
            return List.of(stake);
        }
        int n = FairPayout.share(stake.count() * thirds, 3, roll);
        return n > 0 ? List.of(new ItemAmount(stake.item(), n)) : List.of();
    }
}
