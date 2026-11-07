package de.tigges.kaboofcasino.deco.full;

/**
 * The mod's full-cube deco blocks (casinodeco, {@code CasinoFloorsAndWalls}), in the mod's registration order:
 * 9 fabric floors ({@code Block} / {@code CasinoStarCarpetBlock}), 5 stone floors and the 13 dark-oak wall blocks
 * ({@code CasinoWoodPanelBlock} / {@code CasinoPilasterBlock}). Every model is a full cube ({@code cube_all},
 * {@code cube_column} or the pilaster's six single faces).
 *
 * <p>{@link #band()} is the mod's {@code CasinoFloorsAndWalls.band}: 1 baseboard, 2 cornice, 3 dado rail (both
 * series), 0 for everything else. The German name is the mod's {@code de_de} text, used as the fallback of the
 * translatable item name (the pack carries all twelve languages).
 */
public enum DecoBlock {
    RUBY_TRELLIS(ModMaterial.WOOL, Kind.PLAIN, 0, "Bordeauxroter Teppich mit Goldrauten"),
    RUBY_STARS(ModMaterial.WOOL, Kind.STARS, 0, "Bordeauxroter Sternenteppich"),
    RUBY_VELVET(ModMaterial.WOOL, Kind.PLAIN, 0, "Bordeauxroter Samtteppich"),
    EMERALD_TRELLIS(ModMaterial.WOOL, Kind.PLAIN, 0, "Smaragdgrüner Teppich mit Goldrauten"),
    EMERALD_STARS(ModMaterial.WOOL, Kind.STARS, 0, "Smaragdgrüner Sternenteppich"),
    EMERALD_VELVET(ModMaterial.WOOL, Kind.PLAIN, 0, "Smaragdgrüner Samtteppich"),
    SAPPHIRE_TRELLIS(ModMaterial.WOOL, Kind.PLAIN, 0, "Nachtblauer Teppich mit Goldrauten"),
    SAPPHIRE_STARS(ModMaterial.WOOL, Kind.STARS, 0, "Nachtblauer Sternenteppich"),
    SAPPHIRE_VELVET(ModMaterial.WOOL, Kind.PLAIN, 0, "Nachtblauer Samtteppich"),
    IVORY_BLACK_CHECKERS(ModMaterial.STONE, Kind.PLAIN, 0, "Schachbrett aus Elfenbein und Schwarz"),
    JADE_INLAY(ModMaterial.STONE, Kind.PLAIN, 0, "Eingelegte Jadefliesen"),
    IVORY_MARBLE(ModMaterial.STONE, Kind.PLAIN, 0, "Geäderter Elfenbeinmarmor"),
    OBSIDIAN_MARBLE(ModMaterial.STONE, Kind.PLAIN, 0, "Geäderter schwarzer Marmor"),
    GOLD_VEINED_BLACK_MARBLE(ModMaterial.STONE, Kind.PLAIN, 0, "Schwarzer Marmor mit Goldadern"),
    DARK_OAK_PANELING(ModMaterial.PLANKS, Kind.PLAIN, 0, "Schwarzeichenvertäfelung"),
    GILDED_DARK_OAK_PANELING(ModMaterial.PLANKS, Kind.PLAIN, 0, "Vergoldete Schwarzeichenvertäfelung"),
    DARK_OAK_BASEBOARD(ModMaterial.PLANKS, Kind.PLAIN, 1, "Sockelleiste aus Schwarzeiche und Gold"),
    DARK_OAK_CORNICE(ModMaterial.PLANKS, Kind.PLAIN, 2, "Gesims aus Schwarzeiche und Gold"),
    DARK_OAK_DADO_RAIL(ModMaterial.PLANKS, Kind.PLAIN, 3, "Zierleiste aus Schwarzeiche und Gold"),
    DARK_OAK_PILASTER(ModMaterial.PLANKS, Kind.PILASTER, 0, "Pilaster aus Schwarzeiche und Gold"),
    DARK_OAK_DIAMOND_PANEL(ModMaterial.PLANKS, Kind.PLAIN, 0, "Schwarzeichenpaneel mit Goldmedaillon"),
    CLASSIC_GILDED_DARK_OAK_PANELING(ModMaterial.PLANKS, Kind.PLAIN, 0,
            "Vergoldete Vertäfelung aus Schwarzeichenholzbrettern"),
    CLASSIC_DARK_OAK_BASEBOARD(ModMaterial.PLANKS, Kind.PLAIN, 1,
            "Sockelleiste aus Schwarzeichenholzbrettern und Gold"),
    CLASSIC_DARK_OAK_CORNICE(ModMaterial.PLANKS, Kind.PLAIN, 2, "Gesims aus Schwarzeichenholzbrettern und Gold"),
    CLASSIC_DARK_OAK_DADO_RAIL(ModMaterial.PLANKS, Kind.PLAIN, 3, "Zierleiste aus Schwarzeichenholzbrettern und Gold"),
    CLASSIC_DARK_OAK_PILASTER(ModMaterial.PLANKS, Kind.PILASTER, 0, "Pilaster aus Schwarzeichenholzbrettern und Gold"),
    CLASSIC_DARK_OAK_DIAMOND_PANEL(ModMaterial.PLANKS, Kind.PLAIN, 0,
            "Paneel aus Schwarzeichenholzbrettern mit Goldmedaillon");

    /**
     * The vanilla block the mod copies the properties of ({@code Properties.ofFullCopy}): hardness, tool speeds and
     * the harvest rule. The deco itself is a mushroom block (hardness 0.2, axe); {@code DecoDigging} scales the
     * breaker's speed so vanilla digging takes the mod's time.
     */
    public enum ModMaterial {
        /** {@code Blocks.WOOL.red()}: 0.8, shears are fast, no tool needed */
        WOOL(0.8f, false),
        /** {@code Blocks.POLISHED_ANDESITE}: 1.5, needs a pickaxe to drop */
        STONE(1.5f, true),
        /** {@code Blocks.DARK_OAK_PLANKS}: 2.0, axe */
        PLANKS(2.0f, false);

        private final float hardness;
        private final boolean requiresTool;

        ModMaterial(float hardness, boolean requiresTool) {
            this.hardness = hardness;
            this.requiresTool = requiresTool;
        }

        public float hardness() {
            return hardness;
        }

        /** {@code requiresCorrectToolForDrops}: without the right tool the block drops nothing. */
        public boolean requiresTool() {
            return requiresTool;
        }
    }

    /** How many states a block uses and how its state is chosen on placement. */
    public enum Kind {
        /** one state */
        PLAIN,
        /** four states: {@code facing} = the placer's horizontal look ({@code CasinoStarCarpetBlock}) */
        STARS,
        /** per-side band connections ({@code CasinoPilasterBlock}), see {@link PilasterChoice} */
        PILASTER
    }

    private final ModMaterial material;
    private final Kind kind;
    private final int band;
    private final String germanName;

    DecoBlock(ModMaterial material, Kind kind, int band, String germanName) {
        this.material = material;
        this.kind = kind;
        this.band = band;
        this.germanName = germanName;
    }

    public ModMaterial material() {
        return material;
    }

    public Kind kind() {
        return kind;
    }

    public int band() {
        return band;
    }

    public String germanName() {
        return germanName;
    }

    /** {@code ruby_trellis} ... */
    public String path() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /** The mod id, {@code casinodeco:ruby_trellis} ... (also the PDC tag and the item model). */
    public String modId() {
        return "casinodeco:" + path();
    }

    /** {@code block.casinodeco.ruby_trellis} */
    public String translationKey() {
        return "block.casinodeco." + path();
    }
}
