package de.tigges.kaboofcasino.wager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** B1/B3: winnings into DeathBan's 54-slot grave only as far as they fit; a cancelled death takes back what is left. */
class DeathDropsTest {

    static final String D = "minecraft:diamond";
    static final String I = "minecraft:iron_ingot";

    /** A mutable stand-in for an ItemStack. */
    static final class Stack {
        final String item;
        int amount;

        Stack(String item, int amount) {
            this.item = item;
            this.amount = amount;
        }
    }

    static final DeathDrops.StackOps<Stack> OPS = new DeathDrops.StackOps<>() {
        @Override
        public String item(Stack s) {
            return s.item;
        }

        @Override
        public int amount(Stack s) {
            return s.amount;
        }

        @Override
        public void amount(Stack s, int amount) {
            s.amount = amount;
        }
    };

    private static List<DeathDrops.Existing> existing(int full, DeathDrops.Existing... extra) {
        List<DeathDrops.Existing> out = new ArrayList<>();
        for (int i = 0; i < full; i++) {
            out.add(new DeathDrops.Existing("minecraft:dirt", 64, 64, true));
        }
        out.addAll(List.of(extra));
        return out;
    }

    @Test
    void aFullGraveKeepsEverythingOwed() {
        DeathDrops.Plan p = DeathDrops.plan(existing(54), List.of(new ItemAmount(D, 100)), k -> 64, 54);
        assertTrue(p.newStacks().isEmpty());
        assertTrue(p.topUps().isEmpty());
        assertEquals(List.of(new ItemAmount(D, 100)), p.kept());
    }

    @Test
    void partialStacksAreToppedUpThenFreeSlotsUsedThenTheRestKept() {
        // 50 used slots, one of them 29 plain diamonds, one 30 renamed diamonds (not plain: not touched)
        List<DeathDrops.Existing> drops = existing(48, new DeathDrops.Existing(D, 29, 64, true),
                new DeathDrops.Existing(D, 30, 64, false));
        DeathDrops.Plan p = DeathDrops.plan(drops, List.of(new ItemAmount(D, 400), new ItemAmount(I, 70)), k -> 64,
                54);
        assertEquals(Map.of(48, 35), p.topUps());
        // 4 free slots: 4 x 64 diamonds, no room for iron
        assertEquals(List.of(new ItemAmount(D, 64), new ItemAmount(D, 64), new ItemAmount(D, 64),
                new ItemAmount(D, 64)), p.newStacks());
        assertEquals(List.of(new ItemAmount(D, 400 - 35 - 256), new ItemAmount(I, 70)), p.kept());
    }

    @Test
    void everythingFitsIntoAnEmptyGrave() {
        DeathDrops.Plan p = DeathDrops.plan(List.of(), List.of(new ItemAmount(D, 6400)), k -> 64, 54);
        assertEquals(54, p.newStacks().size());
        assertEquals(List.of(new ItemAmount(D, 6400 - 54 * 64)), p.kept());
    }

    @Test
    void aCancelledDeathRestoresOnlyWhatIsStillThere() {
        Stack partial = new Stack(D, 29);
        Stack dirt = new Stack("minecraft:dirt", 64);
        List<Stack> drops = new ArrayList<>(List.of(dirt, partial));
        DeathDrops.Applied<Stack> applied = new DeathDrops.Applied<>();
        partial.amount += 35;
        applied.toppedUp(partial, 35);
        Stack a = new Stack(D, 64);
        Stack b = new Stack(D, 10);
        drops.add(a);
        drops.add(b);
        applied.added(a);
        applied.added(b);
        drops.remove(b); // another plugin took this stack away

        DeathDrops.Restore r = applied.restore(drops, OPS);
        assertEquals(List.of(new ItemAmount(D, 64 + 35)), r.restored());
        assertEquals(List.of(new ItemAmount(D, 10)), r.missing());
        assertEquals(List.of(dirt, partial), drops);
        assertSame(partial, drops.get(1));
        assertEquals(29, partial.amount, "the player's own 29 stay in the drops");
    }

    @Test
    void aShrunkTopUpIsReportedMissing() {
        Stack partial = new Stack(D, 64);
        List<Stack> drops = new ArrayList<>(List.of(partial));
        DeathDrops.Applied<Stack> applied = new DeathDrops.Applied<>();
        applied.toppedUp(partial, 40);
        partial.amount = 20; // someone took 44 of the 64
        DeathDrops.Restore r = applied.restore(drops, OPS);
        assertEquals(List.of(new ItemAmount(D, 20)), r.restored());
        assertEquals(List.of(new ItemAmount(D, 20)), r.missing());
        assertTrue(drops.isEmpty());
    }
}
