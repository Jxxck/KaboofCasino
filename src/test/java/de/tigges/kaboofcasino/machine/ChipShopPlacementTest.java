package de.tigges.kaboofcasino.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.cashier.DailyLimits;
import de.tigges.kaboofcasino.util.Pos;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The Chip-Shop as a machine (step 11): the cashier's footprint and rules, no game, its own item. */
class ChipShopPlacementTest {

    @Test
    void theShopStandsLikeTheCashier() {
        MachineType t = MachineType.CHIP_SHOP;
        assertFalse(t.isGame());
        assertFalse(t.isBoard());
        assertTrue(t.needsSupport());
        for (Facing f : Facing.values()) {
            Pos anchor = new Pos("w", 8, 64, 8);
            assertEquals(Set.copyOf(MachineType.CASHIER.carriers(anchor, f)), Set.copyOf(t.carriers(anchor, f)));
        }
        assertEquals(CasinoItem.CHIP_SHOP, CasinoItem.forMachine(t));
        assertEquals(t, MachineType.byId("chip_shop").orElseThrow());
        assertEquals("kaboofcasino:chip_shop", CasinoItem.CHIP_SHOP.model());
        assertFalse(MachineSettings.MOD_ORDER.contains(t));
    }

    @Test
    void dailyLimitsCountPieces() {
        DailyLimits l = DailyLimits.empty(LocalDate.of(2026, 11, 9)).plus("0123456789abcdef", 16).plus(
                "0123456789abcdef");
        assertEquals(17, l.count("0123456789abcdef"));
        assertEquals(17, DailyLimits.read(l.encode(), LocalDate.of(2026, 11, 9)).count("0123456789abcdef"));
        assertEquals(0, DailyLimits.read(l.encode(), LocalDate.of(2026, 11, 10)).count("0123456789abcdef"),
                "a new Berlin day starts at zero");
    }
}
