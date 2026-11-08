package de.tigges.kaboofcasino.croupier;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.game.blackjack.BlackjackDealerRig;
import de.tigges.kaboofcasino.game.blackjack.DealerIdleAnimation;
import de.tigges.kaboofcasino.machine.Facing;
import de.tigges.kaboofcasino.pack.PackFiles;
import de.tigges.kaboofcasino.util.Pos;
import de.tigges.kaboofcasino.visual.RigMath;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/** The croupier's dealer: feet at its spot, the table dealer's pose and model, the pack keys it needs. */
class CroupierRigTest {

    @Test
    void theFeetOffsetIsTheDealersPlacement() {
        // rest pose: both legs hang straight below the hips, so their pivots' midpoint is the feet
        DealerIdleAnimation.Pose rest = new DealerIdleAnimation.Pose();
        Transformation[] table = BlackjackDealerRig.transforms(Facing.SOUTH, rest);
        Vector3f mid = new Vector3f(table[6].getTranslation()).add(table[7].getTranslation()).mul(0.5F);
        assertEquals(CroupierRig.DEALER_FEET.x, mid.x, 1e-6);
        assertEquals(CroupierRig.DEALER_FEET.z, mid.z, 1e-6);
        for (Facing f : Facing.values()) {
            Transformation[] t = CroupierRig.transforms(f, rest);
            Vector3f m = new Vector3f(t[6].getTranslation()).add(t[7].getTranslation()).mul(0.5F);
            assertEquals(0.0, m.x, 1e-6, f.name());
            assertEquals(0.0, m.z, 1e-6, f.name());
            assertTrue(m.y > 0.6 && m.y < 0.8, f + " hips at " + m.y);
        }
    }

    @Test
    void theCroupierIsTheTableDealerMovedToItsFeet() {
        for (Facing f : Facing.values()) {
            Vector3f feet = RigMath.facing(f).transform(new Vector3f(CroupierRig.DEALER_FEET));
            for (double now : new double[] {0.0, 137.0, 33_389_712.0}) {
                DealerIdleAnimation.Pose pose = CroupierRig.idlePose(now, 211);
                Transformation[] table = BlackjackDealerRig.transforms(f, pose);
                Transformation[] mine = CroupierRig.transforms(f, pose);
                for (int i = 0; i < table.length; i++) {
                    Vector3f want = new Vector3f(table[i].getTranslation()).sub(feet);
                    assertEquals(want, mine[i].getTranslation());
                    assertEquals(table[i].getLeftRotation(), mine[i].getLeftRotation());
                    assertEquals(table[i].getRightRotation(), mine[i].getRightRotation());
                    assertEquals(table[i].getScale(), mine[i].getScale());
                }
            }
        }
    }

    @Test
    void itLooksAlongItsFacingLikeTheTableDealer() {
        // the table dealer stands 1.7875 blocks against the table's facing and looks along it at the players; the
        // croupier has the dealer's rotations, so with facing f it looks along f (its counter is one step along f)
        for (Facing f : Facing.values()) {
            Vector3f feet = RigMath.facing(f).transform(new Vector3f(CroupierRig.DEALER_FEET));
            int[] step = f.step();
            assertEquals(-1.7875, feet.x * step[0] + feet.z * step[1], 1e-5, f.name());
        }
    }

    @Test
    void lightComesFromTheBodysBlocks() {
        Pos feet = new Pos("minecraft:overworld", 3, 70, 3);
        assertArrayEquals(new int[] {70, 71}, CroupierRig.lightBlocks(new CroupierPlacement.Stand(feet, 0.0,
                Facing.NORTH)));
        assertArrayEquals(new int[] {70, 71}, CroupierRig.lightBlocks(new CroupierPlacement.Stand(feet, -1.0 / 16,
                Facing.NORTH)));
        assertArrayEquals(new int[] {70, 71}, CroupierRig.lightBlocks(new CroupierPlacement.Stand(feet, 1.0 / 16,
                Facing.NORTH)));
        // on the counter's top the body is in the two blocks above the counter
        assertArrayEquals(new int[] {71, 72}, CroupierRig.lightBlocks(new CroupierPlacement.Stand(feet,
                CroupierPlacement.COUNTER_TOP, Facing.NORTH)));
        assertEquals(List.of(new Pos("minecraft:overworld", 3, 71, 3), new Pos("minecraft:overworld", 3, 72, 3)),
                CroupierService.cells(new CroupierRecord(java.util.UUID.randomUUID(), feet,
                        CroupierPlacement.COUNTER_TOP, Facing.NORTH, null, null, null, null, null, List.of(), 0, 0,
                        null, 0, null)));
    }

    @Test
    void thePackHasWhatTheCroupierUses() {
        for (String key : CroupierRig.modelKeys()) {
            assertTrue(PackFiles.hasItemModel(key), key);
        }
        assertEquals(8, CroupierRig.modelKeys().size());
        assertTrue(PackFiles.hasItemModel("roulettecasino:croupier_hiring_card"));
        assertTrue(PackFiles.hasItemModel("roulettecasino:croupier_badge"));
        String sounds = new String(PackFiles.all().get("assets/roulettecasino/sounds.json"), StandardCharsets.UTF_8);
        assertTrue(sounds.contains("\"card_flip\""), "roulettecasino:card_flip (end of the stream)");
        com.google.gson.JsonObject de = com.google.gson.JsonParser.parseString(new String(
                PackFiles.all().get("assets/roulettecasino/lang/de_de.json"), StandardCharsets.UTF_8))
                .getAsJsonObject();
        assertEquals("Croupier", de.get("entity.roulettecasino.villager.croupier").getAsString());
        assertEquals("Dieser Croupier ist beschäftigt.", de.get("message.roulettecasino.croupier_busy").getAsString());
        com.google.gson.JsonObject en = com.google.gson.JsonParser.parseString(new String(
                PackFiles.all().get("assets/roulettecasino/lang/en_us.json"), StandardCharsets.UTF_8))
                .getAsJsonObject();
        assertEquals("This croupier is busy.", en.get("message.roulettecasino.croupier_busy").getAsString());
    }
}
