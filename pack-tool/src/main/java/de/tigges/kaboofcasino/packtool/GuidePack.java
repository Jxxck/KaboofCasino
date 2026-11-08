package de.tigges.kaboofcasino.packtool;

import java.nio.file.Path;
import java.util.List;

/**
 * The casino guide book (games.md 11). On vanilla clients the book is a series of dialogs built by the plugin
 * ({@code de.tigges.kaboofcasino.guide}) from {@link GuideData}; the pack only needs the item definitions of the book
 * and of the croupier's hand (which the guide shows and nothing else ships yet), and the lang keys the dialogs send as
 * translatable components: the guide texts, the screen's buttons and the names of every item, block and the croupier
 * the guide shows (the casino's blocks and items of all three namespaces, so the client's own language is used; the
 * plugin's fallback is German). {@code GuideDataTest} checks that every key the guide uses is in the shipped lang
 * files.
 */
final class GuidePack {

    static final List<String> SOUND_EVENTS = List.of();
    /** Prefixes, or exact keys ending in "=". */
    static final List<String> LANG_PREFIXES = List.of("item.roulettecasino.casino_guide=", "guide.roulettecasino.",
            "block.roulettecasino.", "item.roulettecasino.", "entity.roulettecasino.villager.croupier=",
            "block.casinodeco.", "block.casinosound.");

    private GuidePack() {
    }

    static void add(ModJar jar, PackTree tree, Path packSrc) {
        ModelCopier copier = new ModelCopier(jar, tree);
        copier.itemDefinition("roulettecasino:casino_guide");
        // the guide shows the croupier's hand (cover tile, spreads 33-34) before the croupier settings ship it (the
        // same bytes then, which the pack tree accepts)
        copier.itemDefinition("roulettecasino:croupier_hand");
    }
}
