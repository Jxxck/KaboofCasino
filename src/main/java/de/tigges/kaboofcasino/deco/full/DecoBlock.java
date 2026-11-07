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
    RUBY_TRELLIS(Kind.PLAIN, 0, "Bordeauxroter Teppich mit Goldrauten"),
    RUBY_STARS(Kind.STARS, 0, "Bordeauxroter Sternenteppich"),
    RUBY_VELVET(Kind.PLAIN, 0, "Bordeauxroter Samtteppich"),
    EMERALD_TRELLIS(Kind.PLAIN, 0, "Smaragdgrüner Teppich mit Goldrauten"),
    EMERALD_STARS(Kind.STARS, 0, "Smaragdgrüner Sternenteppich"),
    EMERALD_VELVET(Kind.PLAIN, 0, "Smaragdgrüner Samtteppich"),
    SAPPHIRE_TRELLIS(Kind.PLAIN, 0, "Nachtblauer Teppich mit Goldrauten"),
    SAPPHIRE_STARS(Kind.STARS, 0, "Nachtblauer Sternenteppich"),
    SAPPHIRE_VELVET(Kind.PLAIN, 0, "Nachtblauer Samtteppich"),
    IVORY_BLACK_CHECKERS(Kind.PLAIN, 0, "Schachbrett aus Elfenbein und Schwarz"),
    JADE_INLAY(Kind.PLAIN, 0, "Eingelegte Jadefliesen"),
    IVORY_MARBLE(Kind.PLAIN, 0, "Geäderter Elfenbeinmarmor"),
    OBSIDIAN_MARBLE(Kind.PLAIN, 0, "Geäderter schwarzer Marmor"),
    GOLD_VEINED_BLACK_MARBLE(Kind.PLAIN, 0, "Schwarzer Marmor mit Goldadern"),
    DARK_OAK_PANELING(Kind.PLAIN, 0, "Schwarzeichenvertäfelung"),
    GILDED_DARK_OAK_PANELING(Kind.PLAIN, 0, "Vergoldete Schwarzeichenvertäfelung"),
    DARK_OAK_BASEBOARD(Kind.PLAIN, 1, "Sockelleiste aus Schwarzeiche und Gold"),
    DARK_OAK_CORNICE(Kind.PLAIN, 2, "Gesims aus Schwarzeiche und Gold"),
    DARK_OAK_DADO_RAIL(Kind.PLAIN, 3, "Zierleiste aus Schwarzeiche und Gold"),
    DARK_OAK_PILASTER(Kind.PILASTER, 0, "Pilaster aus Schwarzeiche und Gold"),
    DARK_OAK_DIAMOND_PANEL(Kind.PLAIN, 0, "Schwarzeichenpaneel mit Goldmedaillon"),
    CLASSIC_GILDED_DARK_OAK_PANELING(Kind.PLAIN, 0, "Vergoldete Vertäfelung aus Schwarzeichenholzbrettern"),
    CLASSIC_DARK_OAK_BASEBOARD(Kind.PLAIN, 1, "Sockelleiste aus Schwarzeichenholzbrettern und Gold"),
    CLASSIC_DARK_OAK_CORNICE(Kind.PLAIN, 2, "Gesims aus Schwarzeichenholzbrettern und Gold"),
    CLASSIC_DARK_OAK_DADO_RAIL(Kind.PLAIN, 3, "Zierleiste aus Schwarzeichenholzbrettern und Gold"),
    CLASSIC_DARK_OAK_PILASTER(Kind.PILASTER, 0, "Pilaster aus Schwarzeichenholzbrettern und Gold"),
    CLASSIC_DARK_OAK_DIAMOND_PANEL(Kind.PLAIN, 0, "Paneel aus Schwarzeichenholzbrettern mit Goldmedaillon");

    /** How many states a block uses and how its state is chosen on placement. */
    public enum Kind {
        /** one state */
        PLAIN,
        /** four states: {@code facing} = the placer's horizontal look ({@code CasinoStarCarpetBlock}) */
        STARS,
        /** per-side band connections ({@code CasinoPilasterBlock}), see {@link PilasterChoice} */
        PILASTER
    }

    private final Kind kind;
    private final int band;
    private final String germanName;

    DecoBlock(Kind kind, int band, String germanName) {
        this.kind = kind;
        this.band = band;
        this.germanName = germanName;
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
