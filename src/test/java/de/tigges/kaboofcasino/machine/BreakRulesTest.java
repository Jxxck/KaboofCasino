package de.tigges.kaboofcasino.machine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.config.CasinoConfig;
import org.junit.jupiter.api.Test;

/** "Breakable in survival" (games.md 21.2) and the croupier's hand that never breaks a machine. */
class BreakRulesTest {

    @Test
    void breakableInSurvival() {
        // default true: everybody may mine (the owner rule comes after)
        assertTrue(CasinoConfig.DEFAULTS.machines().breakable());
        assertTrue(ProtectionRules.mayMine(false, true, false));
        assertTrue(ProtectionRules.mayMine(true, true, false));
        // off: survival players have destroy progress 0, creative players still break
        assertFalse(ProtectionRules.mayMine(false, false, false));
        assertTrue(ProtectionRules.mayMine(true, false, false));
    }

    @Test
    void theCroupiersHandNeverBreaksAMachine() {
        // CroupierHandItem.canDestroyBlock: false on any machine part, in every mode
        assertFalse(ProtectionRules.mayMine(true, true, true));
        assertFalse(ProtectionRules.mayMine(false, true, true));
        assertFalse(ProtectionRules.mayMine(true, false, true));
    }
}
