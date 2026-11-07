package de.tigges.kaboofcasino.game.poker;

import de.tigges.kaboofcasino.wager.ItemAmount;
import java.util.List;

/**
 * What the round menu shows: the mod's menu data ({@code PokerTableBlockEntity.refreshSnapshot},
 * {@code PokerMenu.D_*}) plus the deal times the screen uses for the card appear delay, the shown wager, the call
 * affordability the client greys the button with, and the NET lines after the result. Built by the controller.
 *
 * @param phase         {@code PokerMenu.PHASE_*}: 0 betting, 1 dealing, 2 decision, 3 board, 4 showdown, 5 result
 * @param player        the player's hole cards (ids 0..51)
 * @param dealer        the dealer's visible cards ({@code -1} = face down)
 * @param board         the board cards (flop, turn, river)
 * @param playerDealtAt game tick at which each player card was dealt
 * @param dealerDealtAt game tick at which each dealer card was dealt
 * @param boardDealtAt  game tick at which each board card was dealt
 * @param dealerRevealedAt game tick at which each dealer card was turned face up ({@code -1} = not yet)
 * @param turnTicksLeft ticks left in the decision (0 outside {@code DECISION})
 * @param result        {@code PokerRound.Result.code} (0 = none yet)
 * @param canCall       {@code PokerRound.canCall()}
 * @param canFold       {@code PokerRound.canFold()}
 * @param called        the call was paid (stakes are 3 x the ante)
 * @param affordCall    the viewer has 2 x the ante of every item kind in his 36 storage slots
 * @param payoutThirds  {@code PokerRound.payoutThirds()}
 * @param stakes        the wager per filled bet slot, as the mod's {@code shownWager}: the ante, after the call
 *                      3 x the ante ({@code called}); the ante alone is {@code count / 3} then ({@code anteStakes})
 * @param net           after the result: the signed net per item kind (payouts minus stakes, {@code addResultDelta}),
 *                      at most 6, else empty
 * @param gameTime      the world game tick the snapshot was taken at
 */
public record PokerSnapshot(int phase, List<Integer> player, List<Integer> dealer, List<Integer> board,
                            List<Long> playerDealtAt, List<Long> dealerDealtAt, List<Long> boardDealtAt,
                            List<Long> dealerRevealedAt, int turnTicksLeft, int result, boolean canCall,
                            boolean canFold, boolean called, boolean affordCall, int payoutThirds,
                            List<ItemAmount> stakes, List<Net> net, long gameTime) {

    public static final int PHASE_BETTING = 0;
    public static final int PHASE_DEALING = 1;
    public static final int PHASE_DECISION = 2;
    public static final int PHASE_BOARD = 3;
    public static final int PHASE_SHOWDOWN = 4;
    public static final int PHASE_RESULT = 5;

    /** One NET line: an item id and a signed count (a loss is negative; all zero only when nothing changed). */
    public record Net(String item, long count) {
    }

    public PokerSnapshot {
        player = List.copyOf(player);
        dealer = List.copyOf(dealer);
        board = List.copyOf(board);
        playerDealtAt = List.copyOf(playerDealtAt);
        dealerDealtAt = List.copyOf(dealerDealtAt);
        boardDealtAt = List.copyOf(boardDealtAt);
        dealerRevealedAt = List.copyOf(dealerRevealedAt);
        stakes = List.copyOf(stakes);
        net = List.copyOf(net);
    }

    /** The betting state (no round). */
    public static PokerSnapshot betting(long gameTime) {
        return new PokerSnapshot(PHASE_BETTING, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), 0, 0, false, false, false, false, 0, List.of(), List.of(), gameTime);
    }
}
