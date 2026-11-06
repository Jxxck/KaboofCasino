package de.tigges.kaboofcasino.packtool;

import java.nio.file.Path;
import java.util.List;

/**
 * Blackjack models (render.md 1.2/3.2; mod {@code BlackjackTableRenderer}): the eight static table models
 * ({@code roulettecasino:block/blackjack_0..7}: felt top, rim, pedestal, the shoe on parts 2/3; one display per part)
 * with item definitions, the table and badge items, the 53 card models ({@link BlackjackCardModels}) and the eight
 * dealer part models with their composited texture ({@link BlackjackDealerModel}). The renderer draws nothing else:
 * no stake items on the felt, the shoe and the discard tray are part of the block models.
 */
final class BlackjackPack {

    static final List<String> SOUND_EVENTS = List.of("card_deal", "card_flip", "card_shuffle", "blackjack_push",
            "tick", "win", "lose", "payout");
    /** Prefixes, or exact keys ending in "=". */
    static final List<String> LANG_PREFIXES = List.of("block.roulettecasino.blackjack_table",
            "gui.roulettecasino.blackjack.", "message.roulettecasino.blackjack.",
            "subtitles.roulettecasino.card_deal=", "subtitles.roulettecasino.card_flip=",
            "subtitles.roulettecasino.card_shuffle=", "subtitles.roulettecasino.blackjack_push=",
            "item.roulettecasino.croupier_badge=");
    static final int PARTS = 8;

    private BlackjackPack() {
    }

    static void add(ModJar jar, PackTree tree, Path packSrc) {
        ModelCopier copier = new ModelCopier(jar, tree);
        copier.itemDefinition("roulettecasino:blackjack_table");
        copier.itemDefinition("roulettecasino:croupier_badge");
        for (int part = 0; part < PARTS; part++) {
            String model = "roulettecasino:block/blackjack_" + part;
            copier.model(model);
            tree.put("assets/roulettecasino/items/block/blackjack_" + part + ".json",
                    Json.bytes(Json.itemDefinition(model)));
        }
        BlackjackCardModels.write(jar, tree);
        BlackjackDealerModel.write(jar, tree);
    }
}
