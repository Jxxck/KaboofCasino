package de.tigges.kaboofcasino.deco.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.machine.Facing;
import de.tigges.kaboofcasino.util.Pos;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

/**
 * The type table against the mod's registrations (games.md 15-18, 22): carriers, light, hardness, connections,
 * waterlogging, the support the mod's shapes give, the light levels per cell and state, the TV channels and the dealer
 * station's lookup.
 */
class DecoRulesTest {

    private static final Pos A = new Pos("minecraft:overworld", 0, 64, 0);

    @Test
    void idsAreUniqueAndComplete() {
        Set<String> ids = new HashSet<>();
        for (DecoType t : DecoType.values()) {
            assertTrue(ids.add(t.id()), t.id());
            assertEquals(t, DecoType.byId(t.id()).orElseThrow());
        }
        // 38 non-full-cube casinodeco blocks + 26 own stairs and slabs + chair, stool, 3 TVs, dealer station + speaker
        assertEquals(38 + 26, ids.stream().filter(i -> i.startsWith("casinodeco:")).count());
        assertEquals(Set.of("roulettecasino:casino_chair", "roulettecasino:casino_stool", "roulettecasino:casino_tv",
                "roulettecasino:casino_tv_large", "roulettecasino:casino_tv_3x3", "roulettecasino:dealer_station"),
                ids.stream().filter(i -> i.startsWith("roulettecasino:")).collect(java.util.stream.Collectors.toSet()));
        assertEquals(14 + 26, ids.stream().filter(i -> i.endsWith("_stairs") || i.endsWith("_slab")).count());
    }

    @Test
    void lightAndHardnessAreTheMods() {
        assertEquals(11, DecoType.CASINO_SIGN.light());
        assertEquals(7, DecoType.SMALL_CASINO_SIGN.light());
        assertEquals(14, DecoType.CASINO_CHANDELIER.light());
        assertEquals(14, DecoType.CHANDELIER_WALL_SCONCE.light());
        assertEquals(2, DecoType.CASINO_SPEAKER.light());
        assertEquals(2.5, DecoType.CASINO_SOFA.hardness());
        assertEquals(2.5, DecoType.CASINO_CHAIR.hardness());
        assertEquals(0.6, DecoType.GLASS_TABLE.hardness());
        assertEquals(1.0, DecoType.CASINO_CHANDELIER.hardness());
        assertEquals(2.0, DecoType.WALNUT_BAR.hardness());
        assertEquals(0.3, DecoType.SHOT_GLASSES.hardness());
        assertEquals(0.2, DecoType.CASINO_CHIP_PILE.hardness());
        assertEquals(0.8, DecoType.CHIP_CASE.hardness());
        assertEquals(Material.POLISHED_ANDESITE, DecoType.IVORY_MARBLE_BAR.material());
        // RouletteMod: TVs copy oak planks with the metal sound, strength 2.0, light 5 (small) / 7 when on
        assertEquals(5, DecoType.CASINO_TV.light());
        assertEquals(7, DecoType.CASINO_TV_LARGE.light());
        assertEquals(7, DecoType.CASINO_TV_3X3.light());
        for (DecoType tv : new DecoType[] {DecoType.CASINO_TV, DecoType.CASINO_TV_LARGE, DecoType.CASINO_TV_3X3}) {
            assertEquals(2.0, tv.hardness());
            assertEquals(Material.OAK_PLANKS, tv.material());
            assertEquals(Material.IRON_BLOCK, tv.sound()); // SoundType.METAL
            assertEquals(DecoType.Place.TV, tv.place());
        }
        // the dealer station: ofFullCopy(Blocks.LECTERN) (strength 2.5, wood sound, no tool needed)
        assertEquals(2.5, DecoType.DEALER_STATION.hardness());
        assertEquals(Material.LECTERN, DecoType.DEALER_STATION.material());
        assertEquals(Material.LECTERN, DecoType.DEALER_STATION.sound());
        assertEquals(0, DecoType.DEALER_STATION.light());
    }

    /** The step 8 blocks are in no mineable tag of the mod: every tool digs them at hand speed. */
    @Test
    void untaggedBlocksIgnoreTheTool() {
        Set<DecoType> any = new HashSet<>();
        for (DecoType t : DecoType.values()) {
            if (t.anyTool()) {
                any.add(t);
            }
        }
        assertEquals(Set.of(DecoType.CASINO_TV, DecoType.CASINO_TV_LARGE, DecoType.CASINO_TV_3X3,
                DecoType.DEALER_STATION), any);
    }

    /**
     * {@code CasinoTelevisionChannels.next}: off -> 3 -> 1 -> 2 -> 4 -> 5 -> 6 -> 7 -> 8 -> off, the light of every cell
     * with the TV on (5 or 7) and 0 when off, a light carrier on every cell.
     */
    @Test
    void televisionChannelsAndLight() {
        java.util.List<Integer> cycle = new java.util.ArrayList<>();
        int channel = 0;
        do {
            channel = DecoRecord.nextChannel(channel);
            cycle.add(channel);
        } while (channel != 0 && cycle.size() < 20);
        assertEquals(java.util.List.of(3, 1, 2, 4, 5, 6, 7, 8, 0), cycle);
        assertEquals(0, DecoRecord.nextChannel(DecoRecord.LAST_CHANNEL));
        for (DecoType tv : new DecoType[] {DecoType.CASINO_TV, DecoType.CASINO_TV_LARGE, DecoType.CASINO_TV_3X3}) {
            assertEquals(DecoType.Carrier.LIGHT, tv.carrier());
            DecoRecord off = new DecoRecord(UUID.randomUUID(), tv, A, Facing.WEST, 0, new UUID(0, 0), 0);
            assertFalse(off.tvOn());
            for (Pos cell : DecoGeometry.cells(off)) {
                assertEquals(0, DisplayDecoService.lightLevel(off, cell, false), tv.id());
            }
            for (int c = 1; c <= DecoRecord.LAST_CHANNEL; c++) {
                DecoRecord on = off.withState(c);
                assertTrue(on.tvOn());
                assertEquals(c, on.channel());
                for (Pos cell : DecoGeometry.cells(on)) {
                    assertEquals(tv.light(), DisplayDecoService.lightLevel(on, cell, false), tv.id());
                }
            }
            assertEquals(DecoRecord.LAST_CHANNEL, off.withState(99).channel()); // clamped
            for (BlockFace face : new BlockFace[] {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.WEST}) {
                assertFalse(DisplayDecoService.decoSturdy(tv, face, true), "a TV panel carries nothing");
            }
        }
    }

    /**
     * The dealer station: a barrier with no support rule and no facing (a plain {@code Block}); its 12 px post carries
     * the centre below it (a lantern or chandelier can hang under it), its 11.7 px top carries nothing.
     */
    @Test
    void dealerStationRules() {
        assertEquals(DecoType.Carrier.BARRIER, DecoType.DEALER_STATION.carrier());
        assertEquals(DecoType.Place.FREE, DecoType.DEALER_STATION.place());
        assertTrue(DisplayDecoService.decoSturdy(DecoType.DEALER_STATION, BlockFace.DOWN, true));
        assertFalse(DisplayDecoService.decoSturdy(DecoType.DEALER_STATION, BlockFace.DOWN, false));
        assertFalse(DisplayDecoService.decoSturdy(DecoType.DEALER_STATION, BlockFace.UP, true));
        assertFalse(DisplayDecoService.decoSturdy(DecoType.DEALER_STATION, BlockFace.NORTH, true));
        assertTrue(DecoGeometry.clickBoxes(new DecoRecord(UUID.randomUUID(), DecoType.DEALER_STATION, A,
                Facing.SOUTH, 0, new UUID(0, 0), 0)).isEmpty(), "a barrier is its own click target");
        assertEquals(java.util.List.of(A), DecoGeometry.cells(DecoType.DEALER_STATION, A, Facing.SOUTH, 0));
    }

    /** The croupier's lookup: stations within the radius, nearest first, other worlds never; the default hook. */
    @Test
    void dealerStationLookup() {
        DecoRecord far = station(new Pos("minecraft:overworld", 10, 64, 0));
        DecoRecord near = station(new Pos("minecraft:overworld", 2, 65, 1));
        DecoRecord edge = station(new Pos("minecraft:overworld", 0, 64, 5));
        DecoRecord nether = station(new Pos("minecraft:the_nether", 0, 64, 0));
        java.util.List<DecoRecord> all = java.util.List.of(far, near, edge, nether);
        assertEquals(java.util.List.of(near, edge), DisplayDecoService.near(all, A, 5));
        assertEquals(java.util.List.of(near, edge, far), DisplayDecoService.near(all, A, 10));
        assertEquals(java.util.List.of(), DisplayDecoService.near(all, A, 1));
        assertEquals(6.0, DisplayDecoService.distanceSquared(A, near.anchor()));
        assertEquals(Double.POSITIVE_INFINITY, DisplayDecoService.distanceSquared(A, nether.anchor()));
        assertFalse(DealerStationHook.NONE.onUse(null, near), "the mod's station has no use");
    }

    @Test
    void unsupportedBarrierDecoKeepsPlayersClear() {
        // only barrier deco without support below could cap a player in a hole: the counter and the wall shelves
        Set<DecoType> clear = new HashSet<>();
        for (DecoType t : DecoType.values()) {
            if (t.playerClearance()) {
                clear.add(t);
            }
        }
        assertEquals(Set.of(DecoType.DEALER_STATION, DecoType.BOTTLE_DISPLAY_SHELF, DecoType.GLASS_DISPLAY_SHELF),
                clear);
        // a player standing in a 1 x 2 hole at (0, 64, 0): a counter on top of the hole (y 66) is within the two blocks
        org.bukkit.util.BoundingBox inHole = new org.bukkit.util.BoundingBox(0.2, 64, 0.2, 0.8, 65.8, 0.8);
        Pos cap = new Pos("minecraft:overworld", 0, 66, 0);
        assertTrue(de.tigges.kaboofcasino.machine.MachineService.near(inHole, java.util.List.of(cap),
                de.tigges.kaboofcasino.machine.MachineService.PLAYER_CLEARANCE));
        assertFalse(de.tigges.kaboofcasino.machine.MachineService.near(inHole,
                java.util.List.of(new Pos("minecraft:overworld", 4, 66, 0)),
                de.tigges.kaboofcasino.machine.MachineService.PLAYER_CLEARANCE));
    }

    private static DecoRecord station(Pos at) {
        return new DecoRecord(UUID.randomUUID(), DecoType.DEALER_STATION, at, Facing.SOUTH, 0, new UUID(0, 0), 0);
    }

    @Test
    void carriersAndWaterlogging() {
        for (DecoType t : DecoType.values()) {
            if (t.light() > 0) {
                assertEquals(DecoType.Carrier.LIGHT, t.carrier(), t.id() + " emits light");
            }
            if (t.seat()) {
                assertEquals(DecoType.Carrier.BARRIER, t.carrier(), t.id());
            }
            if (t.vanillaCarrier()) {
                assertTrue(t.id().endsWith(t.carrier() == DecoType.Carrier.STAIRS ? "_stairs" : "_slab"), t.id());
                assertTrue(t.vanilla().name().endsWith(t.carrier() == DecoType.Carrier.STAIRS ? "_STAIRS" : "_SLAB"));
                // never a burning or furnace-fuel carrier: nether woods and stone only
                assertTrue(t.vanilla().name().matches("(CRIMSON|WARPED|POLISHED_[A-Z_]+)_(STAIRS|SLAB)"), t.id());
            }
        }
        // SimpleWaterloggedBlock in the mod: the sconce and the three stacked decor blocks (and stairs/slabs)
        Set<DecoType> wet = new HashSet<>();
        for (DecoType t : DecoType.values()) {
            if (t.waterloggable() && !t.vanillaCarrier()) {
                wet.add(t);
            }
        }
        assertEquals(Set.of(DecoType.CHANDELIER_WALL_SCONCE, DecoType.CASINO_BOTTLES, DecoType.CHAMPAGNE_COUPES,
                DecoType.SHOT_GLASSES), wet);
    }

    @Test
    void connections() {
        assertTrue(DisplayDecoService.joins(DecoType.WALNUT_BAR, DecoType.BLACK_MARBLE_BAR));
        assertTrue(DisplayDecoService.joins(DecoType.WOOD_TABLE, DecoType.WOOD_TABLE));
        assertFalse(DisplayDecoService.joins(DecoType.WOOD_TABLE, DecoType.GLASS_TABLE));
        assertFalse(DisplayDecoService.joins(DecoType.WOOD_COFFEE_TABLE, DecoType.WOOD_TABLE));
        assertTrue(DisplayDecoService.joins(DecoType.VELVET_ROPE_POST, DecoType.VELVET_ROPE_POST));
        assertFalse(DisplayDecoService.joins(DecoType.CASINO_CHAIR, DecoType.CASINO_CHAIR));
    }

    /**
     * What a decoration cell supports above or beside it is the mod's shape, not the barrier: bars are full cubes,
     * the tall tables' top plate (14-16 px) is a full top, the rope post's 6 px post carries the centre
     * ({@code canSupportCenter}: 7..9 px column), the low tables (0-8 px), seats and small decor carry nothing.
     */
    @Test
    void supportFromTheModsShapes() {
        assertTrue(DisplayDecoService.decoSturdy(DecoType.WALNUT_BAR, BlockFace.UP, false));
        assertTrue(DisplayDecoService.decoSturdy(DecoType.IVORY_MARBLE_BAR, BlockFace.NORTH, false));
        assertTrue(DisplayDecoService.decoSturdy(DecoType.WOOD_TABLE, BlockFace.UP, true));
        assertFalse(DisplayDecoService.decoSturdy(DecoType.WOOD_TABLE, BlockFace.NORTH, false));
        assertFalse(DisplayDecoService.decoSturdy(DecoType.WOOD_COFFEE_TABLE, BlockFace.UP, true));
        assertTrue(DisplayDecoService.decoSturdy(DecoType.VELVET_ROPE_POST, BlockFace.UP, true));
        assertFalse(DisplayDecoService.decoSturdy(DecoType.VELVET_ROPE_POST, BlockFace.UP, false));
        assertFalse(DisplayDecoService.decoSturdy(DecoType.CASINO_CHAIR, BlockFace.UP, true));
        assertFalse(DisplayDecoService.decoSturdy(DecoType.CASINO_SOFA, BlockFace.UP, true));
    }

    @Test
    void lightLevelsPerCellAndState() {
        DecoRecord chandelier = new DecoRecord(UUID.randomUUID(), DecoType.CASINO_CHANDELIER, A, Facing.SOUTH, 2,
                new UUID(0, 0), 0);
        assertEquals(14, DisplayDecoService.lightLevel(chandelier, A.offset(1, 0, -1), false));
        assertEquals(0, DisplayDecoService.lightLevel(chandelier, A.offset(0, 2, 0), false));
        DecoRecord sconce = new DecoRecord(UUID.randomUUID(), DecoType.CHANDELIER_WALL_SCONCE, A, Facing.NORTH, 0,
                new UUID(0, 0), 0);
        assertEquals(14, DisplayDecoService.lightLevel(sconce, A, false));
        assertEquals(0, DisplayDecoService.lightLevel(sconce, A, true)); // lightLevel: waterlogged ? 0 : 14
        DecoRecord speaker = new DecoRecord(UUID.randomUUID(), DecoType.CASINO_SPEAKER, A, Facing.EAST,
                DecoRecord.speakerState(false, DecoRecord.FACE_WALL), new UUID(0, 0), 0);
        assertEquals(0, DisplayDecoService.lightLevel(speaker, A, false));
        assertEquals(2, DisplayDecoService.lightLevel(speaker.withState(DecoRecord.speakerState(true,
                DecoRecord.FACE_WALL)), A, false));
        DecoRecord plaque = new DecoRecord(UUID.randomUUID(), DecoType.VIP_ONLY_PLAQUE, A, Facing.EAST, 0,
                new UUID(0, 0), 0);
        assertEquals(0, DisplayDecoService.lightLevel(plaque, A, false));
    }

    @Test
    void stateAccessors() {
        assertEquals(DecoRecord.FACE_CEILING, record(DecoRecord.speakerState(true, DecoRecord.FACE_CEILING)).face());
        assertTrue(record(DecoRecord.speakerState(true, DecoRecord.FACE_CEILING)).on());
        assertEquals(9, record(DecoRecord.sofaState(5, 9)).sofaBack());
        assertEquals(5, record(DecoRecord.sofaState(5, 9)).sofaJoins());
        assertEquals(4, record(7).count()); // clamped
        assertEquals(1, record(0).chain());
    }

    /**
     * The slab dupe (review): a deco slab never merges into a plain slab of its carrier, and what a slab cell drops is
     * the stored count, capped by the carrier; a 0.5.0 record (0) reads the carrier.
     */
    @Test
    void slabsCountWhatWasPlaced() {
        assertTrue(DecoListener.mergesIntoForeignSlab(DecoType.RUBY_VELVET_SLAB, Material.CRIMSON_SLAB));
        assertTrue(DecoListener.mergesIntoForeignSlab(DecoType.SAPPHIRE_VELVET_SLAB, Material.WARPED_SLAB));
        assertFalse(DecoListener.mergesIntoForeignSlab(DecoType.RUBY_VELVET_SLAB, Material.AIR));
        assertFalse(DecoListener.mergesIntoForeignSlab(DecoType.RUBY_VELVET_SLAB, Material.WARPED_SLAB));
        assertFalse(DecoListener.mergesIntoForeignSlab(DecoType.RUBY_VELVET_STAIRS, Material.CRIMSON_STAIRS));
        assertEquals(1, DisplayDecoService.slabDrop(1, false));
        assertEquals(1, DisplayDecoService.slabDrop(1, true)); // a deco half in a double block: one deco slab
        assertEquals(2, DisplayDecoService.slabDrop(2, true));
        assertEquals(1, DisplayDecoService.slabDrop(2, false)); // never more than the carrier holds
        assertEquals(2, DisplayDecoService.slabDrop(0, true)); // 0.5.0 record: as 0.5.0 did
        assertEquals(1, DisplayDecoService.slabDrop(0, false));
    }

    /**
     * A decoration that lost one carrier is removed; its other cells are cleared only where they still hold its
     * carrier (the sign's and chandelier's other light blocks), the block that replaced the missing one stays.
     */
    @Test
    void onlyOwnCarriersAreCleared() {
        assertTrue(DisplayDecoService.carrierMaterial(DecoType.CASINO_SIGN, Material.LIGHT));
        assertFalse(DisplayDecoService.carrierMaterial(DecoType.CASINO_SIGN, Material.STONE));
        assertFalse(DisplayDecoService.carrierMaterial(DecoType.CASINO_SIGN, Material.AIR));
        assertTrue(DisplayDecoService.carrierMaterial(DecoType.CASINO_CHAIR, Material.BARRIER));
        assertFalse(DisplayDecoService.carrierMaterial(DecoType.CASINO_CHAIR, Material.LIGHT));
        assertTrue(DisplayDecoService.carrierMaterial(DecoType.IVORY_MARBLE_SLAB, Material.POLISHED_DIORITE_SLAB));
        assertFalse(DisplayDecoService.carrierMaterial(DecoType.IVORY_MARBLE_SLAB, Material.POLISHED_DIORITE));
    }

    /** The display cabinets are solid barriers dug at the mod's speed; 0.5.0 shelves on light blocks are converted. */
    @Test
    void displayShelvesAreSolid() {
        for (DecoType t : new DecoType[] {DecoType.BOTTLE_DISPLAY_SHELF, DecoType.GLASS_DISPLAY_SHELF}) {
            assertEquals(DecoType.Carrier.BARRIER, t.carrier());
            assertEquals(DecoType.Place.WALL, t.place());
            assertEquals(Material.CHEST, t.material()); // the axe is the fast tool, no tool needed for the drop
            assertEquals(0.8, t.hardness()); // CasinoBarAndDecor.small(Blocks.CHEST, ...): strength(0.8F)
            assertTrue(DecoGeometry.clickBoxes(new DecoRecord(UUID.randomUUID(), t, A, Facing.NORTH, 0,
                    new UUID(0, 0), 0)).isEmpty(), "a barrier is its own click target");
            assertTrue(DisplayDecoService.legacyShelf(t, Material.LIGHT));
            assertFalse(DisplayDecoService.legacyShelf(t, Material.BARRIER));
        }
        assertFalse(DisplayDecoService.legacyShelf(DecoType.VIP_ONLY_PLAQUE, Material.LIGHT));
    }

    /** Digging ignores the plugin's own break-speed modifiers and computes the rest like vanilla. */
    @Test
    void breakSpeedWithoutForeignModifiers() {
        assertTrue(DecoBreaks.FOREIGN_BREAK_SPEED.test(new org.bukkit.NamespacedKey("kaboofcasino", "deco_dig")));
        assertFalse(DecoBreaks.FOREIGN_BREAK_SPEED.test(org.bukkit.NamespacedKey.minecraft("effect.haste")));
        var add = org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER;
        var scalar = org.bukkit.attribute.AttributeModifier.Operation.ADD_SCALAR;
        var mult = org.bukkit.attribute.AttributeModifier.Operation.MULTIPLY_SCALAR_1;
        assertEquals(1.0, DecoBreaks.attributeValue(1.0, java.util.List.of()), 1e-12);
        // (1 + 0.5) + 1.5 * 0.2 = 1.8, then x 1.5 = 2.7
        assertEquals(2.7, DecoBreaks.attributeValue(1.0, java.util.List.of(new DecoBreaks.Modifier(0.5, add),
                new DecoBreaks.Modifier(0.2, scalar), new DecoBreaks.Modifier(0.5, mult))), 1e-12);
        assertEquals(0.0, DecoBreaks.attributeValue(1.0, java.util.List.of(new DecoBreaks.Modifier(-5, add))), 1e-12);
        assertEquals(1024.0, DecoBreaks.attributeValue(1.0, java.util.List.of(new DecoBreaks.Modifier(5000, add))),
                1e-12);
    }

    private static DecoRecord record(int state) {
        return new DecoRecord(UUID.randomUUID(), DecoType.CASINO_SOFA, A, Facing.SOUTH, state, new UUID(0, 0), 0);
    }

    /** German fallback names, used when a client lacks the pack's language file. */
    @Test
    void namesAndKeys() {
        assertEquals("block.roulettecasino.casino_chair", DecoType.CASINO_CHAIR.translationKey());
        assertEquals("block.casinosound.casino_speaker", DecoType.CASINO_SPEAKER.translationKey());
        assertEquals("Casino-Lautsprecher", DecoType.CASINO_SPEAKER.german());
        assertEquals("rechts", DecoPlacer.direction(BlockFace.NORTH, new int[] {1, 0}));
        assertEquals("links", DecoPlacer.direction(BlockFace.NORTH, new int[] {-1, 0}));
    }
}
