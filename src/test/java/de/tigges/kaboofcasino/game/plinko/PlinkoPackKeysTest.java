package de.tigges.kaboofcasino.game.plinko;

import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.pack.PackFiles;
import org.junit.jupiter.api.Test;

/** Every model the Plinko rig builds at run time exists in the pack. */
class PlinkoPackKeysTest {

    @Test
    void rigModelsExist() {
        for (String key : PlinkoRig.modelKeys()) {
            assertTrue(PackFiles.hasItemModel(key), key);
        }
    }
}
