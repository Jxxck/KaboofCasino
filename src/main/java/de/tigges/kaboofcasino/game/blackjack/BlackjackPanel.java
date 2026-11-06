package de.tigges.kaboofcasino.game.blackjack;

import de.tigges.kaboofcasino.game.PanelText;
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
 * The floating spectator panel above the table ({@code BlackjackTableRenderer.extractPanel} 87-134 and
 * {@code panel} 214-332): the owner's head and name, up to three stakes (item sprite and "×N"), then "YOU n" and
 * "DEALER n" once the player has a card on the felt, then the result in bold. Shown while the round is active
 * ({@code view.isActive()}) and something is staked.
 *
 * <p>Totals as the mod computes them every frame from the table's view: a card counts 22 ticks after it was dealt
 * ({@code APPEAR_DELAY}), a card that is turning over (less than 10 ticks after its flip) counts as hidden, the hole
 * card counts as hidden until it is revealed; the dealer's total only shows with the player's. The result shows from
 * {@code resultAt + 4}.
 *
 * <p>The words are the mod's translation keys (in the pack for all twelve languages, so every client reads its own
 * language like in the mod) with the German {@code de_de.json} text as fallback for a client without the pack.
 */
public final class BlackjackPanel {

    /** {@code RESULT_COLORS} (renderer 46) by result code; YOU: -865166, a bust: -1599358, DEALER: white. */
    static final int[] RESULT_COLORS = {0xFFFFFF, 0x88CF86, 0x88CF86, 0xF2CC72, 0xE79882, 0xE79882, 0xE79882,
        0xFFFFFF, 0xFFFFFF};
    static final TextColor GOLD = TextColor.color(0xF2CC72);
    static final TextColor BUST = TextColor.color(0xE79882);
    /** German fallbacks ({@code de_de.json}): you, dealer, result_1..8. */
    static final String YOU = "DU";
    static final String DEALER = "CROUPIER";
    static final String[] RESULTS = {"", "GEWONNEN!", "CROUPIER ÜBERKAUFT!", "BLACKJACK!", "VERLOREN", "ÜBERKAUFT",
        "CROUPIER-BLACKJACK", "UNENTSCHIEDEN", "DOPPEL-BLACKJACK"};
    /** {@code BlackjackTableRenderState.STAKES}. */
    static final int STAKES = 3;

    private BlackjackPanel() {
    }

    /**
     * Who plays and what he staked (the block entity's {@code ownerName}/{@code panelPlayer} and {@code shownWager};
     * after a double the doubled counts). Totals and result are not part of it: the rig derives them from the view
     * every tick, as the renderer does.
     */
    public record Content(UUID owner, String name, List<ItemAmount> stakes) {
        public Content {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(name, "name");
            stakes = List.copyOf(stakes);
        }
    }

    /** What the view shows at a moment: totals (-1 = none) and the result code (0 = none yet). */
    record Totals(int player, int dealer, int result) {
    }

    /** {@code extractPanel} 94: the panel is drawn at all. */
    static boolean visible(Content content, BlackjackTableState view) {
        return content != null && view != null && view.isActive() && !content.stakes().isEmpty();
    }

    /** {@code extractPanel} 113-131 at {@code now} (game ticks). */
    static Totals totals(BlackjackTableState view, double now) {
        List<Integer> player = new ArrayList<>();
        List<Integer> dealer = new ArrayList<>();
        for (BlackjackCardMotion.Card card : view.cards) {
            if (!(now - (double) card.dealtAt < 22.0)) {
                int value = card.value;
                if (card.flipAt >= 0L && now - (double) card.flipAt < 10.0) {
                    value = -1;
                }
                (card.toDealer ? dealer : player).add(value);
            }
        }
        int playerTotal = player.isEmpty() ? -1 : BlackjackCards.total(player);
        int dealerTotal = dealer.isEmpty() ? -1 : BlackjackCards.total(dealer);
        int result = view.result != BlackjackRound.Result.NONE && now >= (double) (view.resultAt + 4L)
                ? view.result.code : 0;
        return new Totals(playerTotal, dealerTotal, result);
    }

    private static Component word(String key, String fallback) {
        return Component.translatable().key(key).fallback(fallback).build();
    }

    /** The panel text: badge line, stake line, totals line (once the player has cards), result line. */
    static Component render(Content content, Totals totals) {
        Component nl = Component.newline();
        TextComponent.Builder text = Component.text()
                .append(PanelText.header(content.owner(), content.name())).append(nl)
                .append(PanelText.stakes(content.stakes().subList(0, Math.min(STAKES, content.stakes().size()))));
        if (totals.player() >= 0) {
            text.append(nl).append(Component.text()
                    .append(word("gui.roulettecasino.blackjack.you", YOU))
                    .append(Component.text(" " + totals.player()))
                    .color(totals.player() > 21 ? BUST : GOLD).build());
            if (totals.dealer() >= 0) {
                text.append(Component.text("    ")).append(Component.text()
                        .append(word("gui.roulettecasino.blackjack.dealer", DEALER))
                        .append(Component.text(" " + totals.dealer()))
                        .color(totals.dealer() > 21 ? BUST : PanelText.WHITE).build());
            }
        }
        if (totals.result() != 0) {
            int code = Math.min(totals.result(), RESULT_COLORS.length - 1);
            text.append(nl).append(Component.text()
                    .append(word("gui.roulettecasino.blackjack.result_" + code, RESULTS[code]))
                    .color(TextColor.color(RESULT_COLORS[code])).decorate(TextDecoration.BOLD).build());
        }
        return text.build();
    }
}
