package de.tigges.kaboofcasino.packtool;

import java.nio.file.Path;
import java.util.List;

/**
 * Step 8 croupier (games.md 10): the hiring card and the croupier's texts (its name, the merchant screen's title, and
 * the busy message). The croupier itself needs nothing else of its own: it is the blackjack dealer's model, which
 * wears the mod's croupier profession texture, and the badge and the stream's {@code card_flip} sound are shipped by
 * {@link BlackjackPack}. Its trade screen is the vanilla merchant screen (the mod's {@code croupier_trade.png} look is
 * a client mixin).
 */
final class CroupierPack {

    static final List<String> SOUND_EVENTS = List.of();
    /** Prefixes, or exact keys ending in "=". */
    static final List<String> LANG_PREFIXES = List.of("item.roulettecasino.croupier_hiring_card=",
            "entity.roulettecasino.villager.croupier=", "message.roulettecasino.croupier_busy=");

    private CroupierPack() {
    }

    static void add(ModJar jar, PackTree tree, Path packSrc) {
        ModelCopier copier = new ModelCopier(jar, tree);
        copier.itemDefinition("roulettecasino:croupier_hiring_card");
    }
}
