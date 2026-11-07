package de.tigges.kaboofcasino.packtool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The full-block deco state table, the pack tool's copy of the plugin's {@code FullDecoStates} (the pack tool does
 * not see plugin classes). Both are checked against {@code tools/census/chosen-states.txt}; see there and in
 * {@code FullDecoStates} why these states. PERSISTENT: never move an entry, only append on spare states.
 */
final class DecoFullStates {

    /** The three huge-mushroom blocks, in table order. */
    static final List<String> BLOCKS = List.of("mushroom_stem", "red_mushroom_block", "brown_mushroom_block");

    static final int UP = 16;
    static final int NORTH = 8;
    static final int EAST = 4;
    static final int SOUTH = 2;
    static final int WEST = 1;
    static final int ALL = 31;

    /** {@code down=true} masks world generation places (campsite_swamp_2, all rotations/mirrors). */
    static final Map<String, Set<Integer>> GENERATED = Map.of(
            "red_mushroom_block", Set.of(UP),
            "mushroom_stem", Set.of(NORTH | EAST | SOUTH | WEST),
            "brown_mushroom_block", Set.of(UP, UP | NORTH, UP | EAST, UP | SOUTH, UP | WEST, UP | NORTH | EAST,
                    UP | EAST | SOUTH, UP | SOUTH | WEST, UP | WEST | NORTH));

    /** The deco blocks of each mushroom block in allocation order; stars take 4 states, pilasters 19. */
    private static final Map<String, List<String>> LAYOUT = new LinkedHashMap<>();

    static {
        LAYOUT.put("mushroom_stem", List.of("ruby_trellis", "ruby_stars", "ruby_velvet", "emerald_trellis",
                "emerald_stars", "emerald_velvet", "sapphire_trellis", "sapphire_stars", "sapphire_velvet",
                "ivory_black_checkers", "jade_inlay", "ivory_marble", "obsidian_marble", "gold_veined_black_marble"));
        LAYOUT.put("red_mushroom_block", List.of("dark_oak_paneling", "gilded_dark_oak_paneling",
                "dark_oak_baseboard", "dark_oak_cornice", "dark_oak_dado_rail", "dark_oak_diamond_panel",
                "classic_gilded_dark_oak_paneling", "classic_dark_oak_baseboard", "classic_dark_oak_cornice",
                "classic_dark_oak_dado_rail", "classic_dark_oak_diamond_panel", "dark_oak_pilaster"));
        LAYOUT.put("brown_mushroom_block", List.of("classic_dark_oak_pilaster"));
    }

    /** The pilaster connections (north, east, south, west) in table order: 19 of the layout, then 4 corners. */
    static final List<int[]> PILASTERS;

    /** The four outer corners at baseboard height, appended after the first 72 states. */
    static final List<int[]> CORNERS = List.of(new int[] {1, 1, 0, 0}, new int[] {0, 1, 1, 0},
            new int[] {0, 0, 1, 1}, new int[] {1, 0, 0, 1});

    /** Appended after the first 72 (deco, connections), on the spare states in pool order. Append only. */
    static final List<Map.Entry<String, int[]>> APPENDIX = List.of(
            Map.entry("dark_oak_pilaster", CORNERS.get(0)), Map.entry("dark_oak_pilaster", CORNERS.get(1)),
            Map.entry("dark_oak_pilaster", CORNERS.get(2)), Map.entry("dark_oak_pilaster", CORNERS.get(3)),
            Map.entry("classic_dark_oak_pilaster", CORNERS.get(0)),
            Map.entry("classic_dark_oak_pilaster", CORNERS.get(1)),
            Map.entry("classic_dark_oak_pilaster", CORNERS.get(2)),
            Map.entry("classic_dark_oak_pilaster", CORNERS.get(3)));

    /**
     * One reserved state.
     *
     * @param block    {@code red_mushroom_block} ...
     * @param mask     the up/north/east/south/west faces ({@code down} is always true)
     * @param deco     the mod's block path ({@code ruby_stars})
     * @param modProps the mod's state properties ({@code facing}, or the pilaster's four sides), sorted by name
     */
    record Entry(String block, int mask, String deco, Map<String, String> modProps) {

        /** {@code down,east,north,south,up,west} -> "true"/"false" (the vanilla property map). */
        Map<String, String> vanillaProps() {
            return vanillaProps(mask, true);
        }

        static Map<String, String> vanillaProps(int mask, boolean down) {
            Map<String, String> p = new LinkedHashMap<>();
            p.put("down", String.valueOf(down));
            p.put("east", String.valueOf((mask & EAST) != 0));
            p.put("north", String.valueOf((mask & NORTH) != 0));
            p.put("south", String.valueOf((mask & SOUTH) != 0));
            p.put("up", String.valueOf((mask & UP) != 0));
            p.put("west", String.valueOf((mask & WEST) != 0));
            return p;
        }

        /** {@code minecraft:mushroom_stem[down=true,...] # casinodeco:ruby_stars[facing=north]} */
        String line() {
            StringBuilder s = new StringBuilder("minecraft:").append(block).append('[');
            vanillaProps().forEach((k, v) -> s.append(k).append('=').append(v).append(','));
            s.setLength(s.length() - 1);
            s.append("] # casinodeco:").append(deco);
            if (!modProps.isEmpty()) {
                s.append('[');
                modProps.forEach((k, v) -> s.append(k).append('=').append(v).append(','));
                s.setLength(s.length() - 1);
                s.append(']');
            }
            return s.toString();
        }
    }

    static final List<Entry> ENTRIES;

    static {
        List<int[]> p = new ArrayList<>();
        p.add(new int[] {0, 0, 0, 0});
        for (int b = 1; b <= 3; b++) {
            p.add(new int[] {0, b, 0, b});
            p.add(new int[] {0, b, 0, 0});
            p.add(new int[] {0, 0, 0, b});
            p.add(new int[] {b, 0, b, 0});
            p.add(new int[] {b, 0, 0, 0});
            p.add(new int[] {0, 0, b, 0});
        }
        List<int[]> base = List.copyOf(p);
        p.addAll(CORNERS);
        PILASTERS = List.copyOf(p);

        List<Entry> entries = new ArrayList<>();
        List<Map.Entry<String, Integer>> spare = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : LAYOUT.entrySet()) {
            List<Integer> masks = usableMasks(e.getKey());
            int next = 0;
            for (String deco : e.getValue()) {
                if (deco.endsWith("_stars")) {
                    for (String facing : List.of("north", "east", "south", "west")) {
                        entries.add(new Entry(e.getKey(), masks.get(next++), deco, Map.of("facing", facing)));
                    }
                } else if (deco.endsWith("_pilaster")) {
                    for (int[] c : base) {
                        entries.add(new Entry(e.getKey(), masks.get(next++), deco, pilasterProps(c)));
                    }
                } else {
                    entries.add(new Entry(e.getKey(), masks.get(next++), deco, Map.of()));
                }
            }
            for (int mask : masks.subList(next, masks.size())) {
                spare.add(Map.entry(e.getKey(), mask));
            }
        }
        int next = 0;
        for (Map.Entry<String, int[]> a : APPENDIX) {
            Map.Entry<String, Integer> slot = spare.get(next++);
            entries.add(new Entry(slot.getKey(), slot.getValue(), a.getKey(), pilasterProps(a.getValue())));
        }
        ENTRIES = List.copyOf(entries);
    }

    private DecoFullStates() {
    }

    private static Map<String, String> pilasterProps(int[] c) {
        Map<String, String> props = new LinkedHashMap<>();
        props.put("east", String.valueOf(c[1]));
        props.put("north", String.valueOf(c[0]));
        props.put("south", String.valueOf(c[2]));
        props.put("west", String.valueOf(c[3]));
        return props;
    }

    static List<Integer> usableMasks(String block) {
        List<Integer> out = new ArrayList<>();
        for (int mask = 0; mask < ALL; mask++) {
            if (!GENERATED.get(block).contains(mask)) {
                out.add(mask);
            }
        }
        return out;
    }

    /** The 27 deco block paths, first appearance order. */
    static List<String> decoBlocks() {
        return ENTRIES.stream().map(Entry::deco).distinct().toList();
    }

    static List<Entry> of(String block) {
        return ENTRIES.stream().filter(e -> e.block().equals(block)).toList();
    }
}
