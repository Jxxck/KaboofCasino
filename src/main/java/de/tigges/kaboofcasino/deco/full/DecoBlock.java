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
    RUBY_TRELLIS(ModMaterial.WOOL, Kind.PLAIN, 0, "Bordeauxroter Teppich mit Goldrauten", "Ruby Gold Trellis Carpet"),
    RUBY_STARS(ModMaterial.WOOL, Kind.STARS, 0, "Bordeauxroter Sternenteppich", "Ruby Star Carpet"),
    RUBY_VELVET(ModMaterial.WOOL, Kind.PLAIN, 0, "Bordeauxroter Samtteppich", "Ruby Velvet Carpet"),
    EMERALD_TRELLIS(ModMaterial.WOOL, Kind.PLAIN, 0, "Smaragdgrüner Teppich mit Goldrauten",
            "Emerald Gold Trellis Carpet"),
    EMERALD_STARS(ModMaterial.WOOL, Kind.STARS, 0, "Smaragdgrüner Sternenteppich", "Emerald Star Carpet"),
    EMERALD_VELVET(ModMaterial.WOOL, Kind.PLAIN, 0, "Smaragdgrüner Samtteppich", "Emerald Velvet Carpet"),
    SAPPHIRE_TRELLIS(ModMaterial.WOOL, Kind.PLAIN, 0, "Nachtblauer Teppich mit Goldrauten",
            "Midnight Blue Gold Trellis Carpet"),
    SAPPHIRE_STARS(ModMaterial.WOOL, Kind.STARS, 0, "Nachtblauer Sternenteppich", "Midnight Blue Star Carpet"),
    SAPPHIRE_VELVET(ModMaterial.WOOL, Kind.PLAIN, 0, "Nachtblauer Samtteppich", "Midnight Blue Velvet Carpet"),
    IVORY_BLACK_CHECKERS(ModMaterial.STONE, Kind.PLAIN, 0, "Schachbrett aus Elfenbein und Schwarz",
            "Ivory and Black Checkerboard"),
    JADE_INLAY(ModMaterial.STONE, Kind.PLAIN, 0, "Eingelegte Jadefliesen", "Inlaid Jade Tiles"),
    IVORY_MARBLE(ModMaterial.STONE, Kind.PLAIN, 0, "Geäderter Elfenbeinmarmor", "Veined Ivory Marble"),
    OBSIDIAN_MARBLE(ModMaterial.STONE, Kind.PLAIN, 0, "Geäderter schwarzer Marmor", "Veined Black Marble"),
    GOLD_VEINED_BLACK_MARBLE(ModMaterial.STONE, Kind.PLAIN, 0, "Schwarzer Marmor mit Goldadern",
            "Gold Veined Black Marble"),
    DARK_OAK_PANELING(ModMaterial.PLANKS, Kind.PLAIN, 0, "Schwarzeichenvertäfelung", "Dark Oak Paneling"),
    GILDED_DARK_OAK_PANELING(ModMaterial.PLANKS, Kind.PLAIN, 0, "Vergoldete Schwarzeichenvertäfelung",
            "Gilded Dark Oak Paneling"),
    DARK_OAK_BASEBOARD(ModMaterial.PLANKS, Kind.PLAIN, 1, "Sockelleiste aus Schwarzeiche und Gold",
            "Dark Oak and Gold Baseboard"),
    DARK_OAK_CORNICE(ModMaterial.PLANKS, Kind.PLAIN, 2, "Gesims aus Schwarzeiche und Gold",
            "Dark Oak and Gold Cornice"),
    DARK_OAK_DADO_RAIL(ModMaterial.PLANKS, Kind.PLAIN, 3, "Zierleiste aus Schwarzeiche und Gold",
            "Dark Oak and Gold Dado Rail"),
    DARK_OAK_PILASTER(ModMaterial.PLANKS, Kind.PILASTER, 0, "Pilaster aus Schwarzeiche und Gold",
            "Dark Oak and Gold Pilaster"),
    DARK_OAK_DIAMOND_PANEL(ModMaterial.PLANKS, Kind.PLAIN, 0, "Schwarzeichenpaneel mit Goldmedaillon",
            "Dark Oak and Gold Medallion Panel"),
    CLASSIC_GILDED_DARK_OAK_PANELING(ModMaterial.PLANKS, Kind.PLAIN, 0,
            "Vergoldete Vertäfelung aus Schwarzeichenholzbrettern", "Gilded Dark Oak Plank Paneling"),
    CLASSIC_DARK_OAK_BASEBOARD(ModMaterial.PLANKS, Kind.PLAIN, 1,
            "Sockelleiste aus Schwarzeichenholzbrettern und Gold", "Dark Oak Plank and Gold Baseboard"),
    CLASSIC_DARK_OAK_CORNICE(ModMaterial.PLANKS, Kind.PLAIN, 2, "Gesims aus Schwarzeichenholzbrettern und Gold",
            "Dark Oak Plank and Gold Cornice"),
    CLASSIC_DARK_OAK_DADO_RAIL(ModMaterial.PLANKS, Kind.PLAIN, 3, "Zierleiste aus Schwarzeichenholzbrettern und Gold",
            "Dark Oak Plank and Gold Dado Rail"),
    CLASSIC_DARK_OAK_PILASTER(ModMaterial.PLANKS, Kind.PILASTER, 0, "Pilaster aus Schwarzeichenholzbrettern und Gold",
            "Dark Oak Plank and Gold Pilaster"),
    CLASSIC_DARK_OAK_DIAMOND_PANEL(ModMaterial.PLANKS, Kind.PLAIN, 0,
            "Paneel aus Schwarzeichenholzbrettern mit Goldmedaillon", "Dark Oak Plank and Gold Medallion Panel");

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
    private final String englishName;

    DecoBlock(ModMaterial material, Kind kind, int band, String germanName, String englishName) {
        this.material = material;
        this.kind = kind;
        this.band = band;
        this.germanName = germanName;
        this.englishName = englishName;
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

    /** The mod's English name ({@code en_us.json}). */
    public String englishName() {
        return englishName;
    }

    /** The name in the casino's language: the fallback of the item's translatable name. */
    public String displayName() {
        return de.tigges.kaboofcasino.config.Language.text(englishName, germanName);
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
