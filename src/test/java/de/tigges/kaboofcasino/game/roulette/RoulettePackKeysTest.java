package de.tigges.kaboofcasino.game.roulette;

import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.pack.PackFiles;
import org.junit.jupiter.api.Test;

/** Every model the roulette rig builds at run time exists in the pack. */
class RoulettePackKeysTest {

    @Test
    void rigModelsExist() {
        for (String key : RouletteRig.modelKeys()) {
            assertTrue(PackFiles.hasItemModel(key), key);
        }
    }
}
