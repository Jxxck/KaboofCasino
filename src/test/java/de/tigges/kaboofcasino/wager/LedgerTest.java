package de.tigges.kaboofcasino.wager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LedgerTest {

    @Test
    void owedListsAreMergedPerItem() {
        Ledger l = Ledger.EMPTY.plusOwed(List.of(new ItemAmount("minecraft:diamond", 3),
                new ItemAmount("minecraft:iron_ingot", 1), new ItemAmount("minecraft:diamond", 4)));
        assertEquals(List.of(new ItemAmount("minecraft:diamond", 7), new ItemAmount("minecraft:iron_ingot", 1)),
                l.owed());
    }

    @Test
    void aRoundCannotBeAddedTwice() {
        RoundRecord r = new RoundRecord(UUID.randomUUID(), UUID.randomUUID(), "g", RoundState.DECIDED,
                new ItemAmount("minecraft:coal", 1), List.of(), 0, 0, "");
        Ledger l = Ledger.EMPTY.withRound(r);
        assertThrows(IllegalArgumentException.class, () -> l.withRound(r));
        assertTrue(l.withoutRound(r.id()).isEmpty());
    }

    @Test
    void amountsArePositive() {
        assertThrows(IllegalArgumentException.class, () -> new ItemAmount("minecraft:coal", 0));
        assertThrows(IllegalArgumentException.class, () -> new ItemAmount(" ", 1));
    }
}
