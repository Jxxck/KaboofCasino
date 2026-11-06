package de.tigges.kaboofcasino.game.wheel;

import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.pack.PackFiles;
import org.junit.jupiter.api.Test;

/** Every model the wheel rig builds at run time exists in the pack. */
class WheelPackKeysTest {

    @Test
    void rigModelsExist() {
        for (String key : WheelRig.modelKeys()) {
            assertTrue(PackFiles.hasItemModel(key), key);
        }
    }
}
