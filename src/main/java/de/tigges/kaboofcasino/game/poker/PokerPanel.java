package de.tigges.kaboofcasino.game.poker;

import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.game.PanelText;
import de.tigges.kaboofcasino.game.blackjack.BlackjackCardMotion;
import de.tigges.kaboofcasino.wager.ItemAmount;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * The floating spectator panel above the poker table ({@code PokerTableRenderer.extractPanel} 87-151 and
 * {@code panel} 231-358): the owner's head and name, up to three stakes (item sprite and "×N"), then "YOU &lt;hand&gt;"
 * once the player has a card on the felt, "DEALER &lt;hand&gt;" (red when the dealer does not qualify) once both
 * dealer cards are face up, then the result in bold. Shown while the round is active ({@code view.isActive()}) and
 * something is staked.
 *
 * <p>Hands as the mod computes them every frame from the table's view: a card counts 22 ticks after it was dealt
 * ({@code APPEAR_DELAY}), a card that is turning over (less than 10 ticks after its flip) counts as hidden; the
 * player's hand is his hole cards plus the board, the dealer's only once both hole cards are known (the board is
 * added then); the result shows from {@code resultAt + 4}.
 *
 * <p>The words are the mod's translation keys (in the pack for all twelve languages, so every client reads its own
 * language like in the mod) with the German {@code de_de.json} text as fallback for a client without the pack.
 */
public final class PokerPanel {

    /** {@code RESULT_COLORS} (renderer 46) by result code: none, WIN, NO_QUALIFY, PUSH, LOSE, FOLD. */
    static final int[] RESULT_COLORS = {0xFFFFFF, 0x88CF86, 0x88CF86, 0xFFFFFF, 0xE79882, 0xE79882};
    /** The YOU line (renderer 313: -865166). */
    static final TextColor GOLD = TextColor.color(0xF2CC72);
    /** The DEALER line of a dealer who does not qualify (renderer 330: -1599358); else white. */
    static final TextColor RED = TextColor.color(0xE79882);
    static final String YOU_KEY = "gui.roulettecasino.blackjack.you";
    static final String DEALER_KEY = "gui.roulettecasino.blackjack.dealer";
    /** German fallbacks ({@code de_de.json}). */
    static final String YOU = "DU";
    static final String DEALER = "CROUPIER";
    static final String[] HANDS = {"Höchste Karte", "Ein Paar", "Zwei Paare", "Drilling", "Straße", "Flush",
        "Full House", "Vierling", "Straight Flush", "Royal Flush"};
    static final String[] RESULTS = {"", "GEWONNEN!", "CROUPIER SPIELT NICHT", "UNENTSCHIEDEN", "VERLOREN",
        "AUSGESTIEGEN"};
    /** {@code PokerTableRenderState.STAKES}. */
    static final int STAKES = 3;
    /** {@code extractPanel} 118, 120: a card counts this long after its deal, a flipping card is hidden this long. */
    static final double APPEAR_DELAY = 22.0;
    static final double FLIP_HIDE = 10.0;
    /** {@code extractPanel} 146: the result shows this long after the decision. */
    static final long RESULT_DELAY = 4L;

    private PokerPanel() {
    }

    /**
     * Who plays and what he staked (the block entity's {@code ownerName} and {@code shownWager}: the ante counts,
     * after the call ×3). Hands and result are not part of it: the rig derives them from the view every tick, as the
     * renderer does.
     */
    public record Content(UUID owner, String name, List<ItemAmount> stakes) {
        public Content {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(name, "name");
            stakes = List.copyOf(stakes);
        }
    }

    /**
     * What the view shows at a moment: hand categories ({@code PokerHands.category}, -1 = none), whether the dealer
     * qualifies, and the result code (0 = none yet).
     */
    record Hands(int player, int dealer, boolean qualifies, int result) {
    }

    /** {@code extractPanel} 94: the panel is drawn at all. */
    static boolean visible(Content content, PokerTableState view) {
        return content != null && view != null && view.isActive() && !content.stakes().isEmpty();
    }

    /** {@code extractPanel} 113-148 at {@code now} (game ticks). */
    static Hands hands(PokerTableState view, double now) {
        List<Integer> player = new ArrayList<>();
        List<Integer> dealer = new ArrayList<>();
        List<Integer> board = new ArrayList<>();
        for (BlackjackCardMotion.Card card : view.cards) {
            if (!(now - (double) card.dealtAt < APPEAR_DELAY)) {
                int value = card.value;
                if (card.flipAt >= 0L && now - (double) card.flipAt < FLIP_HIDE) {
                    value = -1;
                }
                if (!card.toDealer) {
                    player.add(value);
                } else if (card.index < 2) {
                    dealer.add(value);
                } else {
                    board.add(value);
                }
            }
        }
        int playerHand = -1;
        if (!player.isEmpty()) {
            player.addAll(board);
            playerHand = PokerHands.category(PokerHands.score(player));
        }
        int dealerHand = -1;
        boolean qualifies = false;
        if (dealer.size() == 2 && !dealer.contains(-1)) {
            dealer.addAll(board);
            int score = PokerHands.score(dealer);
            dealerHand = PokerHands.category(score);
            qualifies = PokerHands.qualifies(score);
        }
        int result = view.result != PokerRound.Result.NONE && now >= (double) (view.resultAt + RESULT_DELAY)
                ? view.result.code : 0;
        return new Hands(playerHand, dealerHand, qualifies, result);
    }

    // ---- a table with several players (PLAN-2 C5, not in the mod) ---------------------------------------------------

    /** One player's line at a table with several: stakes, hand category (-1 = none yet), result code (0 = none). */
    public record SeatLine(UUID player, String name, List<ItemAmount> stakes, int hand, int result) {
        public SeatLine {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(name, "name");
            stakes = List.copyOf(stakes);
        }
    }

    /**
     * The panel of a table with several players: the dealer's hand category (-1 = not both cards up yet) and whether he
     * qualifies; {@code betSeconds} / {@code decideSeconds} -1 = no such countdown.
     */
    public record TableContent(List<SeatLine> seats, int dealer, boolean qualifies, long betSeconds,
                               long decideSeconds) {
        public TableContent {
            seats = List.copyOf(seats);
        }
    }

    /** The hand of the cards at {@code spot} plus the board as the panel shows them ({@link #hands}), -1 before. */
    static int seatHand(PokerTableState view, int spot, double now) {
        List<Integer> player = new ArrayList<>();
        List<Integer> board = new ArrayList<>();
        for (BlackjackCardMotion.Card card : view.cards) {
            if (now - (double) card.dealtAt < APPEAR_DELAY) {
                continue;
            }
            int value = card.flipAt >= 0L && now - (double) card.flipAt < FLIP_HIDE ? -1 : card.value;
            if (!card.toDealer && card.spot == spot) {
                player.add(value);
            } else if (card.toDealer && card.index >= 2) {
                board.add(value);
            }
        }
        if (player.isEmpty()) {
            return -1;
        }
        player.addAll(board);
        return PokerHands.category(PokerHands.score(player));
    }

    /** The dealer's hand as the panel shows it: {@link #hands} without the player. */
    static Hands dealerHand(PokerTableState view, double now) {
        Hands h = hands(view, now);
        return new Hands(-1, h.dealer(), h.qualifies(), 0);
    }

    /** The time left to bet or to decide, one line per player, the dealer's hand last. */
    static Component table(Messages m, TableContent c) {
        Component nl = Component.newline();
        TextComponent.Builder text = Component.text();
        boolean first = true;
        if (c.betSeconds() >= 0) {
            text.append(m.render(Msg.POKER_COUNTDOWN, Messages.text("seconds", Long.toString(c.betSeconds()))));
            first = false;
        } else if (c.decideSeconds() >= 0) {
            text.append(m.render(Msg.POKER_DECIDE_COUNTDOWN,
                    Messages.text("seconds", Long.toString(c.decideSeconds()))));
            first = false;
        }
        for (SeatLine l : c.seats()) {
            if (!first) {
                text.append(nl);
            }
            first = false;
            text.append(PanelText.header(l.player(), l.name())).append(Component.text("  "))
                    .append(PanelText.stakes(l.stakes().subList(0, Math.min(STAKES, l.stakes().size()))));
            if (l.hand() >= 0) {
                text.append(Component.text("  ")).append(handName(l.hand()).color(GOLD));
            }
            if (l.result() != 0) {
                int code = Math.max(0, Math.min(l.result(), RESULT_COLORS.length - 1));
                text.append(Component.text("  ")).append(Component.text()
                        .append(word("gui.roulettecasino.poker.result_" + code, RESULTS[code]))
                        .color(TextColor.color(RESULT_COLORS[code])).decorate(TextDecoration.BOLD).build());
            }
        }
        if (c.dealer() >= 0) {
            text.append(nl).append(Component.text()
                    .append(word(DEALER_KEY, DEALER)).append(Component.text(" ")).append(handName(c.dealer()))
                    .color(c.qualifies() ? PanelText.WHITE : RED).build());
        }
        return text.build();
    }

    private static Component word(String key, String fallback) {
        return Component.translatable().key(key).fallback(fallback).build();
    }

    /** {@code PokerScreen.handName}: {@code gui.roulettecasino.poker.hand_<category>}. */
    static Component handName(int category) {
        int c = Math.max(0, Math.min(HANDS.length - 1, category));
        return word("gui.roulettecasino.poker.hand_" + c, HANDS[c]);
    }

    /**
     * The panel text: badge line, stake line, then (as the renderer's rows) "YOU &lt;hand&gt;", "DEALER &lt;hand&gt;"
     * (only together with the player's line) and the result line.
     */
    static Component render(Content content, Hands hands) {
        Component nl = Component.newline();
        TextComponent.Builder text = Component.text()
                .append(PanelText.header(content.owner(), content.name())).append(nl)
                .append(PanelText.stakes(content.stakes().subList(0, Math.min(STAKES, content.stakes().size()))));
        if (hands.player() >= 0) {
            text.append(nl).append(Component.text()
                    .append(word(YOU_KEY, YOU)).append(Component.text(" ")).append(handName(hands.player()))
                    .color(GOLD).build());
            if (hands.dealer() >= 0) {
                text.append(nl).append(Component.text()
                        .append(word(DEALER_KEY, DEALER)).append(Component.text(" ")).append(handName(hands.dealer()))
                        .color(hands.qualifies() ? PanelText.WHITE : RED).build());
            }
        }
        if (hands.result() != 0) {
            int code = Math.max(0, Math.min(hands.result(), RESULT_COLORS.length - 1));
            text.append(nl).append(Component.text()
                    .append(word("gui.roulettecasino.poker.result_" + code, RESULTS[code]))
                    .color(TextColor.color(RESULT_COLORS[code])).decorate(TextDecoration.BOLD).build());
        }
        return text.build();
    }
}
