package de.tigges.kaboofcasino.game.blackjack;

import de.tigges.kaboofcasino.wager.ItemAmount;
import java.util.List;

/**
 * What the round menu shows: the mod's 34 menu data values ({@code BlackjackTableBlockEntity.refreshSnapshot},
 * {@code BlackjackMenu.D_*}) plus the deal times the screen uses for {@code APPEAR_DELAY}, the shown wager and the
 * double affordability the client greys the button with ({@code BlackjackMenu} 149-176). Built by the controller.
 *
 * @param phase         {@code BlackjackMenu.PHASE_*}: 0 betting, 1 dealing, 2 player turn, 3 player draw, 4 dealer,
 *                      5 result/clearing
 * @param player        the player's cards (ids 0..51)
 * @param dealer        the dealer's visible cards ({@code -1} = hole card face down)
 * @param playerDealtAt game tick at which each player card was dealt (same order as {@code player})
 * @param dealerDealtAt game tick at which each dealer card was dealt
 * @param turnTicksLeft ticks left in the player's turn (0 outside {@code PLAYER_TURN})
 * @param result        {@code BlackjackRound.Result.code} (0 = none yet)
 * @param canHit        {@code BlackjackRound.canHit()}
 * @param canStand      {@code BlackjackRound.canStand()}
 * @param canDouble     {@code BlackjackRound.canDouble()}
 * @param doubled       the stake was doubled
 * @param affordDouble  the viewer has the items for a double (plain stacks in his 36 storage slots)
 * @param stakes        the shown wager, one entry per filled bet slot (counts already doubled)
 * @param gameTime      the game tick the snapshot was taken at
 */
public record BlackjackSnapshot(int phase, List<Integer> player, List<Integer> dealer, List<Long> playerDealtAt,
                                List<Long> dealerDealtAt, int turnTicksLeft, int result, boolean canHit,
                                boolean canStand, boolean canDouble, boolean doubled, boolean affordDouble,
                                List<ItemAmount> stakes, long gameTime) {

    public static final int PHASE_BETTING = 0;
    public static final int PHASE_DEALING = 1;
    public static final int PHASE_TURN = 2;
    public static final int PHASE_DRAWING = 3;
    public static final int PHASE_DEALER = 4;
    public static final int PHASE_RESULT = 5;

    public BlackjackSnapshot {
        player = List.copyOf(player);
        dealer = List.copyOf(dealer);
        playerDealtAt = List.copyOf(playerDealtAt);
        dealerDealtAt = List.copyOf(dealerDealtAt);
        stakes = List.copyOf(stakes);
    }

    /** The betting state (no round). */
    public static BlackjackSnapshot betting(long gameTime) {
        return new BlackjackSnapshot(PHASE_BETTING, List.of(), List.of(), List.of(), List.of(), 0, 0, false, false,
                false, false, false, List.of(), gameTime);
    }
}
