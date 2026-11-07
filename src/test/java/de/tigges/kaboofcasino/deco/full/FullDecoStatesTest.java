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
    void eightyUniqueStatesNeverDownFalseNeverDefaultNeverGenerated() {
        List<Entry> entries = FullDecoStates.entries();
        assertEquals(80, entries.size());
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
        assertEquals(Map.of(MushroomBlock.STEM, 30, MushroomBlock.RED, 30, MushroomBlock.BROWN, 20), perBlock);
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
        assertEquals(80, deco);
        assertTrue(FullDecoStates.lookup(MushroomState.downTrue(MushroomBlock.RED, MushroomState.ALL)).isEmpty());
        assertTrue(FullDecoStates.lookup(MushroomState.downTrue(MushroomBlock.BROWN, 29)).isEmpty(), "spare");
        assertTrue(FullDecoStates.lookup(MushroomState.downTrue(MushroomBlock.BROWN, 30)).isEmpty(), "spare");
    }

    @Test
    void everyBlockHasItsStates() {
        for (DecoBlock b : DecoBlock.values()) {
            int expected = switch (b.kind()) {
                case PLAIN -> 1;
                case STARS -> 4;
                case PILASTER -> 23;
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
        assertEquals(23, FullDecoStates.PILASTERS.size());
        assertEquals(23, new HashSet<>(FullDecoStates.PILASTERS).size());
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

    /**
     * Append only: the first 72 states (step 4, possibly placed in worlds) are byte-for-byte what they were; the
     * appendix are the pilasters' baseboard corners on the former spare states (censused 07.11. 03:15 UTC: 0 each).
     */
    @Test
    void theFirstSeventyTwoNeverChange() throws Exception {
        List<String> lines = FullDecoStates.lines();
        byte[] sha = java.security.MessageDigest.getInstance("SHA-256")
                .digest(String.join("\n", lines.subList(0, 72)).getBytes(StandardCharsets.UTF_8));
        assertEquals("5ae688c52c79151545b8d3c3b0a34a8993627c60ad8caff65ecf036fcc29331a",
                java.util.HexFormat.of().formatHex(sha));
        List<Entry> appended = FullDecoStates.entries().subList(72, 80);
        for (int i = 0; i < 8; i++) {
            Entry e = appended.get(i);
            assertEquals(i < 4 ? DecoBlock.DARK_OAK_PILASTER : DecoBlock.CLASSIC_DARK_OAK_PILASTER, e.block());
            assertEquals(FullDecoStates.CORNERS.get(i % 4), e.pilaster());
        }
        assertEquals(MushroomState.downTrue(MushroomBlock.STEM, 24), appended.get(0).state());
        assertEquals(MushroomState.downTrue(MushroomBlock.BROWN, 27), appended.get(7).state());
        assertEquals(FullDecoStates.CORNERS, FullDecoStates.PILASTERS.subList(19, 23));
    }

    /** The table is persistent: it must stay equal to the file the census and the pack tool use. */
    @Test
    void tableMatchesTheChosenStatesFile() throws IOException {
        List<String> expected = Files.readAllLines(Path.of("tools/census/chosen-states.txt"), StandardCharsets.UTF_8)
                .stream().filter(l -> !l.isBlank() && !l.startsWith("#")).toList();
        assertEquals(expected, FullDecoStates.lines());
    }
}
