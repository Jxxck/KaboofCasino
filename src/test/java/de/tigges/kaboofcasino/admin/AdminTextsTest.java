package de.tigges.kaboofcasino.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.tigges.kaboofcasino.machine.MachineType;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

/** Every text of the croupier's hand is the mod's German text under the mod's key. */
class AdminTextsTest {

    @Test
    void everyFallbackIsTheModsGermanText() {
        Map<String, String> mod = AdminFixtures.modGerman();
        for (Map.Entry<String, String> e : AdminTexts.GERMAN.entrySet()) {
            assertEquals(mod.get(e.getKey()), e.getValue(), e.getKey());
        }
        assertEquals(67, AdminTexts.GERMAN.size());
    }

    @Test
    void namesAsTheModShowsThem() {
        assertEquals("Spielautomat #3", AdminFixtures.plain(AdminTexts.displayName(MachineType.SLOT_MACHINE, "", 3)));
        assertEquals("Lucky", AdminFixtures.plain(AdminTexts.displayName(MachineType.SLOT_MACHINE, "Lucky", 3)));
        assertEquals("Entenrennen", AdminFixtures.plain(AdminTexts.typeName(MachineType.DUCK_DERBY)));
        for (MachineType t : MachineType.values()) {
            AdminTexts.typeName(t); // every machine has its block name
        }
        assertEquals("Einstellungen auf Glücksrad #1 eingefügt", AdminFixtures.plain(AdminTexts.message("pasted",
                AdminTexts.displayName(MachineType.FORTUNE_WHEEL, "", 1))));
        assertEquals("Kopierte Einstellungen: Pokertisch #2 (1, 64, -9)", AdminFixtures.plain(AdminTexts.item(
                "copied", AdminTexts.displayName(MachineType.POKER_TABLE, "", 2), Component.text(1),
                Component.text(64), Component.text(-9))));
        assertThrows(IllegalArgumentException.class, () -> AdminTexts.settings("no_such_key"));
    }
}
