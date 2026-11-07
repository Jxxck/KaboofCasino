package de.tigges.kaboofcasino.deco.full;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The state table: which reserved huge-mushroom state shows which deco block (render.md 4.4, option B).
 *
 * <p><b>The table is persistent.</b> Placed blocks are stored in the world as these states, so an entry must never
 * move to another state. New blocks may only take spare states (append to the end of a list). The pack tool holds the
 * same table ({@code DecoFullStates}); both are checked against {@code tools/census/chosen-states.txt}.
 *
 * <p>Usable: the 32 {@code down=true} states of each block, minus
 * <ul>
 *   <li>the all-true default (mask 31): vanilla placement with {@code disable-mushroom-block-updates} gives it;</li>
 *   <li>the {@code down=true} states that world generation places ({@link #GENERATED}): the 26.x structure template
 *       {@code abandoned_camp/camp/swamp/campsite_swamp_2} (swamp abandoned camps) has a red cap
 *       {@code up} only, a stem with all sides but {@code up=false}, and brown caps {@code up} plus no side, one
 *       side or two adjacent sides; jigsaw pieces are rotated/mirrored, so every rotation of those patterns
 *       counts. The huge-mushroom features themselves always place {@code down=false}.</li>
 * </ul>
 * That leaves 30 red, 22 brown and 30 stem states (82). Used: 80 - the first 72 by the layout (stem 23, red 30,
 * brown 19), then the appendix ({@link #APPENDIX}) on the spare states in pool order (stem, red, brown); spare now:
 * brown masks 29 and 30. The remaining mod content (poker, duck derby, TVs, dealer station, speaker, guide) has no
 * full cube, so nothing else will need them.
 *
 * <p>Pilasters have 4 x 4 x 4 x 4 = 256 states in the mod; 23 per pilaster fit ({@link #PILASTERS}): no band; on
 * each axis the same band on both sides (a straight wall) or on one side (a wall end); and the four outer corners at
 * baseboard height (appended). Everything else is shown by the nearest of them ({@link PilasterChoice}).
 */
public final class FullDecoStates {

    /** Pilaster connections, the mod's {@code north/east/south/west} values 0..3 (the neighbour's band). */
    public record Pilaster(int north, int east, int south, int west) {

        public Pilaster {
            for (int v : new int[] {north, east, south, west}) {
                if (v < 0 || v > 3) {
                    throw new IllegalArgumentException("band " + v);
                }
            }
        }

        public int get(Side side) {
            return switch (side) {
                case NORTH -> north;
                case EAST -> east;
                case SOUTH -> south;
                case WEST -> west;
            };
        }

        /** {@code east=1,north=0,south=0,west=1} (alphabetical, like Minecraft) */
        public String properties() {
            return "east=" + east + ",north=" + north + ",south=" + south + ",west=" + west;
        }
    }

    /**
     * One reserved state.
     *
     * @param facing   STARS only: the {@code facing} property
     * @param pilaster PILASTER only: the connections
     */
    public record Entry(MushroomState state, DecoBlock block, Side facing, Pilaster pilaster) {

        /** The mod's block state, e.g. {@code casinodeco:ruby_stars[facing=east]}. */
        public String modState() {
            return switch (block.kind()) {
                case PLAIN -> block.modId();
                case STARS -> block.modId() + "[facing=" + facing.id() + "]";
                case PILASTER -> block.modId() + "[" + pilaster.properties() + "]";
            };
        }
    }

    /** The {@code down=true} masks world generation places (campsite_swamp_2 and its rotations/mirrors). */
    public static final Map<MushroomBlock, Set<Integer>> GENERATED = Map.of(
            MushroomBlock.RED, Set.of(MushroomState.UP),
            MushroomBlock.STEM, Set.of(MushroomState.NORTH | MushroomState.EAST | MushroomState.SOUTH
                    | MushroomState.WEST),
            MushroomBlock.BROWN, Set.of(MushroomState.UP,
                    MushroomState.UP | MushroomState.NORTH, MushroomState.UP | MushroomState.EAST,
                    MushroomState.UP | MushroomState.SOUTH, MushroomState.UP | MushroomState.WEST,
                    MushroomState.UP | MushroomState.NORTH | MushroomState.EAST,
                    MushroomState.UP | MushroomState.EAST | MushroomState.SOUTH,
                    MushroomState.UP | MushroomState.SOUTH | MushroomState.WEST,
                    MushroomState.UP | MushroomState.WEST | MushroomState.NORTH));

    /** The pilaster states of each pilaster, in table order: the 19 of the layout, then the 4 appended corners. */
    public static final List<Pilaster> PILASTERS;

    /** The four outer corners at baseboard height (band 1 on two adjacent sides): north-east, east-south, ... */
    public static final List<Pilaster> CORNERS = List.of(new Pilaster(1, 1, 0, 0), new Pilaster(0, 1, 1, 0),
            new Pilaster(0, 0, 1, 1), new Pilaster(1, 0, 0, 1));

    /** One appended state: a deco block variant placed on the next spare state (append only, never reorder). */
    public record Appended(DecoBlock block, Side facing, Pilaster pilaster) {
    }

    /** Added after the first 72; they take the spare states in pool order (stem, red, brown; ascending masks). */
    public static final List<Appended> APPENDIX = List.of(
            new Appended(DecoBlock.DARK_OAK_PILASTER, null, CORNERS.get(0)),
            new Appended(DecoBlock.DARK_OAK_PILASTER, null, CORNERS.get(1)),
            new Appended(DecoBlock.DARK_OAK_PILASTER, null, CORNERS.get(2)),
            new Appended(DecoBlock.DARK_OAK_PILASTER, null, CORNERS.get(3)),
            new Appended(DecoBlock.CLASSIC_DARK_OAK_PILASTER, null, CORNERS.get(0)),
            new Appended(DecoBlock.CLASSIC_DARK_OAK_PILASTER, null, CORNERS.get(1)),
            new Appended(DecoBlock.CLASSIC_DARK_OAK_PILASTER, null, CORNERS.get(2)),
            new Appended(DecoBlock.CLASSIC_DARK_OAK_PILASTER, null, CORNERS.get(3)));

    private static final List<Entry> ENTRIES;
    private static final Map<MushroomState, Entry> BY_STATE = new HashMap<>();
    private static final Map<String, Entry> BY_MOD_STATE = new HashMap<>();
    private static final Map<DecoBlock, List<Entry>> BY_BLOCK = new EnumMap<>(DecoBlock.class);

    static {
        List<Pilaster> pilasters = new ArrayList<>();
        pilasters.add(new Pilaster(0, 0, 0, 0));
        for (int b = 1; b <= 3; b++) {
            pilasters.add(new Pilaster(0, b, 0, b));
            pilasters.add(new Pilaster(0, b, 0, 0));
            pilasters.add(new Pilaster(0, 0, 0, b));
            pilasters.add(new Pilaster(b, 0, b, 0));
            pilasters.add(new Pilaster(b, 0, 0, 0));
            pilasters.add(new Pilaster(0, 0, b, 0));
        }
        List<Pilaster> base = List.copyOf(pilasters);
        pilasters.addAll(CORNERS);
        PILASTERS = List.copyOf(pilasters);

        Map<MushroomBlock, List<DecoBlock>> layout = new LinkedHashMap<>();
        layout.put(MushroomBlock.STEM, List.of(DecoBlock.RUBY_TRELLIS, DecoBlock.RUBY_STARS, DecoBlock.RUBY_VELVET,
                DecoBlock.EMERALD_TRELLIS, DecoBlock.EMERALD_STARS, DecoBlock.EMERALD_VELVET,
                DecoBlock.SAPPHIRE_TRELLIS, DecoBlock.SAPPHIRE_STARS, DecoBlock.SAPPHIRE_VELVET,
                DecoBlock.IVORY_BLACK_CHECKERS, DecoBlock.JADE_INLAY, DecoBlock.IVORY_MARBLE, DecoBlock.OBSIDIAN_MARBLE,
                DecoBlock.GOLD_VEINED_BLACK_MARBLE));
        layout.put(MushroomBlock.RED, List.of(DecoBlock.DARK_OAK_PANELING, DecoBlock.GILDED_DARK_OAK_PANELING,
                DecoBlock.DARK_OAK_BASEBOARD, DecoBlock.DARK_OAK_CORNICE, DecoBlock.DARK_OAK_DADO_RAIL,
                DecoBlock.DARK_OAK_DIAMOND_PANEL, DecoBlock.CLASSIC_GILDED_DARK_OAK_PANELING,
                DecoBlock.CLASSIC_DARK_OAK_BASEBOARD, DecoBlock.CLASSIC_DARK_OAK_CORNICE,
                DecoBlock.CLASSIC_DARK_OAK_DADO_RAIL, DecoBlock.CLASSIC_DARK_OAK_DIAMOND_PANEL,
                DecoBlock.DARK_OAK_PILASTER));
        layout.put(MushroomBlock.BROWN, List.of(DecoBlock.CLASSIC_DARK_OAK_PILASTER));

        List<Entry> entries = new ArrayList<>();
        List<MushroomState> spare = new ArrayList<>();
        for (Map.Entry<MushroomBlock, List<DecoBlock>> e : layout.entrySet()) {
            List<Integer> masks = usableMasks(e.getKey());
            int next = 0;
            for (DecoBlock block : e.getValue()) {
                switch (block.kind()) {
                    case PLAIN -> entries.add(new Entry(MushroomState.downTrue(e.getKey(), masks.get(next++)), block,
                            null, null));
                    case STARS -> {
                        for (Side facing : Side.values()) {
                            entries.add(new Entry(MushroomState.downTrue(e.getKey(), masks.get(next++)), block,
                                    facing, null));
                        }
                    }
                    case PILASTER -> {
                        for (Pilaster p : base) {
                            entries.add(new Entry(MushroomState.downTrue(e.getKey(), masks.get(next++)), block,
                                    null, p));
                        }
                    }
                }
            }
            masks.subList(next, masks.size()).forEach(m -> spare.add(MushroomState.downTrue(e.getKey(), m)));
        }
        int next = 0;
        for (Appended a : APPENDIX) {
            entries.add(new Entry(spare.get(next++), a.block(), a.facing(), a.pilaster()));
        }
        ENTRIES = List.copyOf(entries);
        for (Entry entry : ENTRIES) {
            if (BY_STATE.put(entry.state(), entry) != null || BY_MOD_STATE.put(entry.modState(), entry) != null) {
                throw new IllegalStateException("duplicate " + entry);
            }
            BY_BLOCK.computeIfAbsent(entry.block(), k -> new ArrayList<>()).add(entry);
        }
    }

    private FullDecoStates() {
    }

    /** The {@code down=true} masks of a block that may carry deco, ascending. */
    public static List<Integer> usableMasks(MushroomBlock block) {
        List<Integer> out = new ArrayList<>();
        for (int mask = 0; mask < MushroomState.ALL; mask++) {
            if (!GENERATED.get(block).contains(mask)) {
                out.add(mask);
            }
        }
        return out;
    }

    /** Every reserved state in table order. */
    public static List<Entry> entries() {
        return ENTRIES;
    }

    /** The deco block a state shows; empty for every non-reserved state (natural, vanilla-placed, spare). */
    public static Optional<Entry> lookup(MushroomState state) {
        return Optional.ofNullable(BY_STATE.get(state));
    }

    /** The states of one deco block in table order. */
    public static List<Entry> of(DecoBlock block) {
        return Collections.unmodifiableList(BY_BLOCK.get(block));
    }

    /** The state an item carries: the only one, facing north (the mod's default), or no connections. */
    public static Entry itemState(DecoBlock block) {
        return BY_BLOCK.get(block).getFirst();
    }

    public static Entry stars(DecoBlock block, Side facing) {
        return BY_MOD_STATE.get(block.modId() + "[facing=" + facing.id() + "]");
    }

    /** The entry of a pilaster with these connections; null if the combination is not in {@link #PILASTERS}. */
    public static Entry pilaster(DecoBlock block, Pilaster connections) {
        return BY_MOD_STATE.get(block.modId() + "[" + connections.properties() + "]");
    }

    /** The table as {@code tools/census/chosen-states.txt} holds it (one line per state). */
    public static List<String> lines() {
        return ENTRIES.stream().map(e -> e.state().key() + " # " + e.modState()).toList();
    }
}
