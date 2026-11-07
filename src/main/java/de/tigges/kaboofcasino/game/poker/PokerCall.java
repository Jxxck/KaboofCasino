package de.tigges.kaboofcasino.game.poker;

import de.tigges.kaboofcasino.game.blackjack.BlackjackShoe;
import java.util.ArrayList;
import java.util.List;

/**
 * The outcome of a hand that is called now (the only decision of Casino Hold'em), finished with the cards the table's
 * shoe would deal next: turn and river onto the board ({@code PokerRound.call}: one board card at once, the second 22
 * ticks later, each with the round's own draw: {@code shoe.draw()} without bias, {@code drawFavouring} with
 * {@code PokerBias.playerValue(player, dealer, board, BOARD, c)} otherwise), then the mod's {@code resolve} and
 * {@code payoutThirds}. Run it on a {@link BlackjackShoe#copy()}: the live round then deals exactly these cards
 * ({@code PokerCallTest}). Before the call the hand settles as a fold (PLAN decision 2, the mod's timeout rule). Pure.
 */
public final class PokerCall {

    /** Target ordinal of a board card ({@code PokerRound.Target.BOARD}). */
    static final int BOARD = 2;

    private PokerCall() {
    }

    /** The result and the payout in thirds of the (called, 3 x ante) stake. */
    public record Outcome(PokerRound.Result result, int thirds) {
    }

    /** The outcome of calling now; consumes the two board cards from {@code shoe}. */
    public static Outcome call(List<Integer> player, List<Integer> dealer, List<Integer> board, BlackjackShoe shoe,
                               int bias) {
        int b = Math.max(-100, Math.min(100, bias));
        List<Integer> p = new ArrayList<>(player);
        List<Integer> d = new ArrayList<>(dealer);
        List<Integer> brd = new ArrayList<>(board);
        while (brd.size() < PokerRound.BOARD) {
            int card = b == 0 ? shoe.draw() : shoe.drawFavouring(Math.abs(b), c -> {
                double value = PokerBias.playerValue(p, d, brd, BOARD, c);
                return b > 0 ? value : -value;
            });
            brd.add(card);
        }
        return resolve(p, d, brd);
    }

    /** {@code PokerRound.resolve} and {@code payoutThirds}, same order. */
    static Outcome resolve(List<Integer> player, List<Integer> dealer, List<Integer> board) {
        List<Integer> mine = new ArrayList<>(player);
        mine.addAll(board);
        List<Integer> his = new ArrayList<>(dealer);
        his.addAll(board);
        int playerScore = PokerHands.score(mine);
        int dealerScore = PokerHands.score(his);
        PokerRound.Result r;
        if (!PokerHands.qualifies(dealerScore)) {
            r = PokerRound.Result.NO_QUALIFY;
        } else if (playerScore > dealerScore) {
            r = PokerRound.Result.WIN;
        } else if (playerScore < dealerScore) {
            r = PokerRound.Result.LOSE;
        } else {
            r = PokerRound.Result.PUSH;
        }
        int thirds = switch (r) {
            case WIN -> 5 + PokerHands.antePays(playerScore);
            case NO_QUALIFY -> 3 + PokerHands.antePays(playerScore);
            case PUSH -> 3;
            default -> 0;
        };
        return new Outcome(r, thirds);
    }
}
