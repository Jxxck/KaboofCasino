package de.tigges.kaboofcasino.deco.full;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.deco.full.FullDecoStates.Entry;
import de.tigges.kaboofcasino.deco.full.FullDecoStates.Pilaster;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FullDecoStatesTest {

    @Test
    void seventyTwoUniqueStatesNeverDownFalseNeverDefaultNeverGenerated() {
        List<Entry> entries = FullDecoStates.entries();
        assertEquals(72, entries.size());
        Set<MushroomState> states = new HashSet<>();
        Set<String> modStates = new HashSet<>();
        for (Entry e : entries) {
            assertTrue(states.add(e.state()), "duplicate state " + e);
            assertTrue(modStates.add(e.modState()), "duplicate mod state " + e);
            assertTrue(e.state().down(), "down=false is natural generation: " + e);
            assertFalse(e.state().allTrue(), "all-true is vanilla placement: " + e);
            assertFalse(FullDecoStates.GENERATED.get(e.state().block()).contains(e.state().mask()),
                    "placed by world generation: " + e);
        }
        Map<MushroomBlock, Integer> perBlock = new EnumMap<>(MushroomBlock.class);
        entries.forEach(e -> perBlock.merge(e.state().block(), 1, Integer::sum));
        assertEquals(Map.of(MushroomBlock.STEM, 23, MushroomBlock.RED, 30, MushroomBlock.BROWN, 19), perBlock);
        assertEquals(30, FullDecoStates.usableMasks(MushroomBlock.RED).size());
        assertEquals(22, FullDecoStates.usableMasks(MushroomBlock.BROWN).size());
        assertEquals(30, FullDecoStates.usableMasks(MushroomBlock.STEM).size());
    }

    /** All 3 x 64 states: exactly the 72 table states are deco, every other state (natural, vanilla, spare) is not. */
    @Test
    void lookupIsExact() {
        int deco = 0;
        for (MushroomBlock block : MushroomBlock.values()) {
            for (int bits = 0; bits < 64; bits++) {
                MushroomState s = new MushroomState(block, (bits & 32) != 0, (bits & 16) != 0, (bits & 8) != 0,
                        (bits & 4) != 0, (bits & 2) != 0, (bits & 1) != 0);
                if (FullDecoStates.lookup(s).isPresent()) {
                    deco++;
                    assertEquals(s, FullDecoStates.lookup(s).get().state());
                }
            }
        }
        assertEquals(72, deco);
        assertTrue(FullDecoStates.lookup(MushroomState.downTrue(MushroomBlock.RED, MushroomState.ALL)).isEmpty());
        assertTrue(FullDecoStates.lookup(MushroomState.downTrue(MushroomBlock.STEM, 24)).isEmpty(), "spare");
    }

    @Test
    void everyBlockHasItsStates() {
        for (DecoBlock b : DecoBlock.values()) {
            int expected = switch (b.kind()) {
                case PLAIN -> 1;
                case STARS -> 4;
                case PILASTER -> 19;
            };
            assertEquals(expected, FullDecoStates.of(b).size(), b.name());
            Entry item = FullDecoStates.itemState(b);
            switch (b.kind()) {
                case STARS -> assertEquals(Side.NORTH, item.facing(), "the mod's default facing");
                case PILASTER -> assertEquals(new Pilaster(0, 0, 0, 0), item.pilaster());
                case PLAIN -> assertEquals(b.modId(), item.modState());
            }
            if (b.kind() == DecoBlock.Kind.STARS) {
                for (Side side : Side.values()) {
                    assertEquals(side, FullDecoStates.stars(b, side).facing());
                }
            }
            if (b.kind() == DecoBlock.Kind.PILASTER) {
                for (Pilaster p : FullDecoStates.PILASTERS) {
                    assertNotNull(FullDecoStates.pilaster(b, p), b + " " + p);
                }
            }
        }
        assertEquals(27, DecoBlock.values().length);
        assertEquals(19, FullDecoStates.PILASTERS.size());
        assertEquals(19, new HashSet<>(FullDecoStates.PILASTERS).size());
    }

    @Test
    void bandsAreTheMods() {
        assertEquals(1, DecoBlock.DARK_OAK_BASEBOARD.band());
        assertEquals(1, DecoBlock.CLASSIC_DARK_OAK_BASEBOARD.band());
        assertEquals(2, DecoBlock.DARK_OAK_CORNICE.band());
        assertEquals(2, DecoBlock.CLASSIC_DARK_OAK_CORNICE.band());
        assertEquals(3, DecoBlock.DARK_OAK_DADO_RAIL.band());
        assertEquals(3, DecoBlock.CLASSIC_DARK_OAK_DADO_RAIL.band());
        long banded = java.util.Arrays.stream(DecoBlock.values()).filter(b -> b.band() != 0).count();
        assertEquals(6, banded);
    }

    @Test
    void maskRoundTrip() {
        for (MushroomBlock block : MushroomBlock.values()) {
            for (int mask = 0; mask <= MushroomState.ALL; mask++) {
                MushroomState s = MushroomState.downTrue(block, mask);
                assertEquals(mask, s.mask());
                assertTrue(s.key().startsWith(block.id() + "[down=true,east="), s.key());
            }
        }
        assertEquals("minecraft:mushroom_stem[down=true,east=false,north=false,south=false,up=false,west=false]",
                MushroomState.downTrue(MushroomBlock.STEM, 0).key());
    }

    /** The table is persistent: it must stay equal to the file the census and the pack tool use. */
    @Test
    void tableMatchesTheChosenStatesFile() throws IOException {
        List<String> expected = Files.readAllLines(Path.of("tools/census/chosen-states.txt"), StandardCharsets.UTF_8)
                .stream().filter(l -> !l.isBlank() && !l.startsWith("#")).toList();
        assertEquals(expected, FullDecoStates.lines());
    }
}
