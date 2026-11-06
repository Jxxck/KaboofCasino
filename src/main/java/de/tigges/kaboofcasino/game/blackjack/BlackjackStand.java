package de.tigges.kaboofcasino.game.blackjack;

import java.util.ArrayList;
import java.util.List;

/**
 * The result of a hand that is settled now with the mod's timeout rule, stand (PLAN decision 2: quit, kick, stop,
 * crash, death; server.md 1.2 "PLAYING: finish the hand with the decisions taken, then apply the timeout rule"),
 * finished with the cards the table's shoe would deal next. It replays {@link BlackjackRound} without its clock:
 * the missing cards of the deal in the mod's order (player, dealer up, player, hole; {@code BlackjackRound} 59-66),
 * no dealer draw after a bust or a natural ({@code revealThenResolve}), otherwise the dealer draws below 17
 * ({@code dealerPlay}), then the mod's resolve order ({@code resolve} 288-311). Every draw is the round's own:
 * {@code shoe.draw()} without bias, {@code drawFavouring} with the hands at that moment otherwise ({@code deal}).
 * Run it on a {@link BlackjackShoe#copy()}: with the same shoe state the live round then deals exactly these cards
 * when the player stands ({@code BlackjackStandTest} checks that against the live round). Pure.
 */
public final class BlackjackStand {

    private BlackjackStand() {
    }

    /** The result if the player stands at his next turn (or now). Consumes cards of {@code shoe}. */
    public static BlackjackRound.Result stand(List<Integer> player, List<Integer> dealer, boolean doubled,
                                              BlackjackShoe shoe, int bias) {
        Hands h = new Hands(player, dealer, doubled, clamp(bias));
        h.finishDeal(shoe);
        return h.standAndResolve(shoe);
    }

    /** The result if the player hits once now and stands at his next turn. Consumes cards of {@code shoe}. */
    public static BlackjackRound.Result hitThenStand(List<Integer> player, List<Integer> dealer, boolean doubled,
                                                     BlackjackShoe shoe, int bias) {
        Hands h = new Hands(player, dealer, doubled, clamp(bias));
        h.deal(false, shoe);
        return h.standAndResolve(shoe);
    }

    /** The result of a double down now (one card, then the dealer). Consumes cards of {@code shoe}. */
    public static BlackjackRound.Result doubleDown(List<Integer> player, List<Integer> dealer, BlackjackShoe shoe,
                                                   int bias) {
        Hands h = new Hands(player, dealer, true, clamp(bias));
        h.deal(false, shoe);
        return h.standAndResolve(shoe);
    }

    private static int clamp(int bias) {
        return Math.max(-100, Math.min(100, bias));
    }

    private static final class Hands {
        final List<Integer> player;
        final List<Integer> dealer;
        final boolean doubled;
        final int bias;

        Hands(List<Integer> player, List<Integer> dealer, boolean doubled, int bias) {
            this.player = new ArrayList<>(player);
            this.dealer = new ArrayList<>(dealer);
            this.doubled = doubled;
            this.bias = bias;
        }

        /** {@code BlackjackRound.deal}: the bias scores the card for the hand it goes to, with the hands as they are. */
        void deal(boolean toDealer, BlackjackShoe shoe) {
            int card = bias == 0 ? shoe.draw() : shoe.drawFavouring(Math.abs(bias), c -> {
                double value = BlackjackBias.playerValue(player, dealer, toDealer, doubled, c);
                return bias > 0 ? value : -value;
            });
            (toDealer ? dealer : player).add(card);
        }

        /** The rest of the opening deal: player, dealer, player, dealer. */
        void finishDeal(BlackjackShoe shoe) {
            while (player.size() + dealer.size() < 4) {
                deal(dealer.size() < player.size(), shoe);
            }
        }

        BlackjackRound.Result standAndResolve(BlackjackShoe shoe) {
            boolean playerNatural = BlackjackCards.isBlackjack(player);
            boolean dealerNatural = BlackjackCards.isBlackjack(dealer);
            boolean bust = BlackjackCards.total(player) > 21;
            if (!bust && !playerNatural && !dealerNatural) {
                while (BlackjackCards.total(dealer) < 17) {
                    deal(true, shoe);
                }
            }
            return resolve(bust);
        }

        /** {@code BlackjackRound.resolve}, same order. */
        BlackjackRound.Result resolve(boolean playerBust) {
            boolean playerNatural = BlackjackCards.isBlackjack(player);
            boolean dealerNatural = BlackjackCards.isBlackjack(dealer);
            int p = BlackjackCards.total(player);
            int d = BlackjackCards.total(dealer);
            if (playerBust) {
                return BlackjackRound.Result.LOSE_BUST;
            } else if (playerNatural && dealerNatural) {
                return BlackjackRound.Result.PUSH_BLACKJACKS;
            } else if (dealerNatural) {
                return BlackjackRound.Result.LOSE_DEALER_BLACKJACK;
            } else if (playerNatural) {
                return BlackjackRound.Result.BLACKJACK;
            } else if (d > 21) {
                return BlackjackRound.Result.WIN_DEALER_BUST;
            } else if (p <= d && (p != 21 || d != 21)) {
                return p < d ? BlackjackRound.Result.LOSE : BlackjackRound.Result.PUSH;
            }
            return BlackjackRound.Result.WIN;
        }
    }
}
