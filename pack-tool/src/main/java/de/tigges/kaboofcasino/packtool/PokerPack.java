package de.tigges.kaboofcasino.packtool;

import java.nio.file.Path;
import java.util.List;

/**
 * Poker models (render.md 1.2/2.2; mod {@code PokerTableRenderer}): the table item and the twelve static table models
 * {@code roulettecasino:block/poker_0..11} (felt with the printed seats and board, rim, pedestal, the shoe on part 3;
 * copied verbatim with their textures, one item definition {@code roulettecasino:block/poker_<p>} each, one display
 * per part like the blackjack table). The renderer draws nothing else of its own: the dealer is the blackjack dealer
 * ({@code BlackjackDealerModel}, same five texture layers) and the cards are the blackjack cards
 * ({@code blackjack_cards.png}), so the rig uses {@link BlackjackPack}'s {@code kaboofcasino:blackjack/dealer_*} and
 * {@code kaboofcasino:blackjack/card_*} models; no chips or stake items lie on the felt.
 */
final class PokerPack {

    static final List<String> SOUND_EVENTS = List.of("poker_card_deal", "poker_card_flip", "poker_shuffle",
            "poker_turn", "poker_win", "poker_lose", "poker_push", "poker_payout");
    /** Prefixes, or exact keys ending in "=". */
    static final List<String> LANG_PREFIXES = List.of("block.roulettecasino.poker_table",
            "gui.roulettecasino.poker.", "message.roulettecasino.poker.", "subtitles.roulettecasino.poker_");
    /** {@code PokerTableBlock.PART} 0..11: 4 columns x 3 rows. */
    static final int PARTS = 12;

    private PokerPack() {
    }

    /** {@code roulettecasino:block/poker_<part>}: the mod's block model, also the item model key of its display. */
    static String bodyModel(int part) {
        return "roulettecasino:block/poker_" + part;
    }

    static void add(ModJar jar, PackTree tree, Path packSrc) {
        ModelCopier copier = new ModelCopier(jar, tree);
        copier.itemDefinition("roulettecasino:poker_table");
        for (int part = 0; part < PARTS; part++) {
            String model = bodyModel(part);
            copier.model(model);
            tree.put("assets/roulettecasino/items/block/poker_" + part + ".json",
                    Json.bytes(Json.itemDefinition(model)));
        }
    }
}
