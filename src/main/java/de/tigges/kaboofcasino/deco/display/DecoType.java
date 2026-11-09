package de.tigges.kaboofcasino.deco.display;

import java.util.Optional;
import org.bukkit.Material;

/**
 * The display-entity decoration of MineVegas Casino (games.md 15-18, 22): every non-full-cube block of
 * {@code casinodeco}, the casino chair and stool, the three casino TVs and the dealer station of {@code roulettecasino}
 * and the speaker of {@code casinosound}.
 * Per type: the mod id, the carrier (render.md 4.4 option A), how it is placed (the mod's {@code getStateForPlacement}
 * and {@code canSurvive}), the material whose tool speed, break rules and sounds the mod copies, the hardness, the light
 * the block emits, what it connects to, and the German item name (fallback of the {@code block.<ns>.<id>} key the pack's
 * language files translate). Pure data.
 */
public enum DecoType {
    // ---- seats ----------------------------------------------------------------------------------------------------
    CASINO_SOFA("casinodeco:casino_sofa", Carrier.BARRIER, Place.SOFA, Material.OAK_PLANKS, Material.OAK_PLANKS, 2.5,
            0, Connect.SOFA, "Casino-Sofa"),
    CASINO_CHAIR("roulettecasino:casino_chair", Carrier.BARRIER, Place.SEAT, Material.OAK_PLANKS, Material.OAK_PLANKS,
            2.5, 0, Connect.NONE, "Casino-Stuhl"),
    CASINO_STOOL("roulettecasino:casino_stool", Carrier.BARRIER, Place.SEAT, Material.OAK_PLANKS, Material.OAK_PLANKS,
            2.5, 0, Connect.NONE, "Casino-Hocker"),
    // ---- signs ----------------------------------------------------------------------------------------------------
    CASINO_SIGN("casinodeco:casino_sign", Carrier.LIGHT, Place.LARGE_SIGN, Material.OAK_PLANKS, Material.OAK_PLANKS,
            1.5, 11, Connect.NONE, "Großes Casino-Schild"),
    SMALL_CASINO_SIGN("casinodeco:small_casino_sign", Carrier.LIGHT, Place.SIGN, Material.OAK_PLANKS,
            Material.OAK_PLANKS, 1.5, 7, Connect.NONE, "Kleines Casino-Schild"),
    // ---- tables ---------------------------------------------------------------------------------------------------
    WOOD_COFFEE_TABLE("casinodeco:wood_coffee_table", Carrier.BARRIER, Place.FLOOR_PLAIN, Material.OAK_PLANKS,
            Material.OAK_PLANKS, 1.5, 0, Connect.SAME, "Couchtisch aus Holz"),
    GLASS_COFFEE_TABLE("casinodeco:glass_coffee_table", Carrier.BARRIER, Place.FLOOR_PLAIN, Material.GLASS,
            Material.GLASS, 0.6, 0, Connect.SAME, "Couchtisch aus Glas"),
    WOOD_TABLE("casinodeco:wood_table", Carrier.BARRIER, Place.FLOOR_PLAIN, Material.OAK_PLANKS, Material.OAK_PLANKS,
            1.5, 0, Connect.SAME, "Holztisch"),
    GLASS_TABLE("casinodeco:glass_table", Carrier.BARRIER, Place.FLOOR_PLAIN, Material.GLASS, Material.GLASS, 0.6, 0,
            Connect.SAME, "Glastisch"),
    // ---- chandelier, rope post ------------------------------------------------------------------------------------
    CASINO_CHANDELIER("casinodeco:casino_chandelier", Carrier.LIGHT, Place.CHANDELIER, Material.OAK_PLANKS,
            Material.LANTERN, 1.0, 14, Connect.NONE, "Casino-Kronleuchter"),
    VELVET_ROPE_POST("casinodeco:velvet_rope_post", Carrier.BARRIER, Place.FLOOR_PLAIN, Material.OAK_PLANKS,
            Material.IRON_BLOCK, 1.5, 0, Connect.SAME, "Absperrpfosten mit Samtkordel"),
    // ---- stairs and slabs (vanilla carriers) ----------------------------------------------------------------------
    RUBY_VELVET_STAIRS("casinodeco:ruby_velvet_stairs", Material.CRIMSON_STAIRS, "ruby_velvet",
            "Treppe — Bordeauxroter Samtteppich"),
    RUBY_VELVET_SLAB("casinodeco:ruby_velvet_slab", Material.CRIMSON_SLAB, "ruby_velvet",
            "Stufe — Bordeauxroter Samtteppich"),
    EMERALD_VELVET_STAIRS("casinodeco:emerald_velvet_stairs", Material.WARPED_STAIRS, "emerald_velvet",
            "Treppe — Smaragdgrüner Samtteppich"),
    EMERALD_VELVET_SLAB("casinodeco:emerald_velvet_slab", Material.WARPED_SLAB, "emerald_velvet",
            "Stufe — Smaragdgrüner Samtteppich"),
    SAPPHIRE_VELVET_STAIRS("casinodeco:sapphire_velvet_stairs", Material.WARPED_STAIRS, "sapphire_velvet",
            "Treppe — Nachtblauer Samtteppich"),
    SAPPHIRE_VELVET_SLAB("casinodeco:sapphire_velvet_slab", Material.WARPED_SLAB, "sapphire_velvet",
            "Stufe — Nachtblauer Samtteppich"),
    IVORY_BLACK_CHECKERS_STAIRS("casinodeco:ivory_black_checkers_stairs", Material.POLISHED_ANDESITE_STAIRS,
            "ivory_black_checkers", "Treppe — Schachbrett aus Elfenbein und Schwarz"),
    IVORY_BLACK_CHECKERS_SLAB("casinodeco:ivory_black_checkers_slab", Material.POLISHED_ANDESITE_SLAB,
            "ivory_black_checkers", "Stufe — Schachbrett aus Elfenbein und Schwarz"),
    IVORY_MARBLE_STAIRS("casinodeco:ivory_marble_stairs", Material.POLISHED_DIORITE_STAIRS, "ivory_marble",
            "Treppe — Geäderter Elfenbeinmarmor"),
    IVORY_MARBLE_SLAB("casinodeco:ivory_marble_slab", Material.POLISHED_DIORITE_SLAB, "ivory_marble",
            "Stufe — Geäderter Elfenbeinmarmor"),
    OBSIDIAN_MARBLE_STAIRS("casinodeco:obsidian_marble_stairs", Material.POLISHED_BLACKSTONE_STAIRS,
            "obsidian_marble", "Treppe — Geäderter schwarzer Marmor"),
    OBSIDIAN_MARBLE_SLAB("casinodeco:obsidian_marble_slab", Material.POLISHED_BLACKSTONE_SLAB, "obsidian_marble",
            "Stufe — Geäderter schwarzer Marmor"),
    GOLD_VEINED_BLACK_MARBLE_STAIRS("casinodeco:gold_veined_black_marble_stairs",
            Material.POLISHED_BLACKSTONE_BRICK_STAIRS, "gold_veined_black_marble",
            "Treppe — Schwarzer Marmor mit Goldadern"),
    GOLD_VEINED_BLACK_MARBLE_SLAB("casinodeco:gold_veined_black_marble_slab", Material.POLISHED_BLACKSTONE_BRICK_SLAB,
            "gold_veined_black_marble", "Stufe — Schwarzer Marmor mit Goldadern"),
    // ---- bars -----------------------------------------------------------------------------------------------------
    WALNUT_BAR("casinodeco:walnut_bar", Carrier.BARRIER, Place.FLOOR_PLAIN, Material.DARK_OAK_PLANKS,
            Material.DARK_OAK_PLANKS, 2.0, 0, Connect.BAR, "Theke aus Nussbaum und Messing"),
    IVORY_MARBLE_BAR("casinodeco:ivory_marble_bar", Carrier.BARRIER, Place.FLOOR_PLAIN, Material.POLISHED_ANDESITE,
            Material.POLISHED_ANDESITE, 1.5, 0, Connect.BAR, "Theke aus Elfenbeinmarmor und Nussbaum"),
    BLACK_MARBLE_BAR("casinodeco:black_marble_bar", Carrier.BARRIER, Place.FLOOR_PLAIN, Material.POLISHED_ANDESITE,
            Material.POLISHED_ANDESITE, 1.5, 0, Connect.BAR, "Theke aus schwarzem Marmor und Messing"),
    // ---- bar and table decor --------------------------------------------------------------------------------------
    CHANDELIER_WALL_SCONCE("casinodeco:chandelier_wall_sconce", Carrier.LIGHT, Place.WALL, Material.OAK_PLANKS,
            Material.LANTERN, 1.0, 14, Connect.NONE, "Kerzenwandleuchter"),
    CASINO_BOTTLES("casinodeco:casino_bottles", Carrier.BARRIER, Place.STACKED, Material.GLASS, Material.GLASS, 0.3, 0,
            Connect.NONE, "Barflaschen"),
    CHAMPAGNE_COUPES("casinodeco:champagne_coupes", Carrier.BARRIER, Place.STACKED, Material.GLASS, Material.GLASS,
            0.3, 0, Connect.NONE, "Champagnerschalen"),
    SHOT_GLASSES("casinodeco:shot_glasses", Carrier.BARRIER, Place.STACKED, Material.GLASS, Material.GLASS, 0.3, 0,
            Connect.NONE, "Whiskygläser"),
    CASINO_CHIP_PILE("casinodeco:casino_chip_pile", Carrier.BARRIER, Place.FLOOR, Material.FLOWER_POT, Material.STONE,
            0.2, 0, Connect.NONE, "Jetonstapel"),
    CHIP_CASE("casinodeco:chip_case", Carrier.BARRIER, Place.FLOOR, Material.CHEST, Material.OAK_PLANKS, 0.8, 0,
            Connect.NONE, "Jetonkoffer"),
    RECEPTION_PLAQUE("casinodeco:reception_plaque", Carrier.BARRIER, Place.FLOOR, Material.DARK_OAK_PLANKS,
            Material.IRON_BLOCK, 0.8, 0, Connect.NONE, "Empfangsschild"),
    CASH_REGISTER("casinodeco:cash_register", Carrier.BARRIER, Place.FLOOR, Material.DARK_OAK_PLANKS,
            Material.OAK_PLANKS, 0.8, 0, Connect.NONE, "Registrierkasse"),
    SERVING_TRAY("casinodeco:serving_tray", Carrier.BARRIER, Place.FLOOR, Material.CHEST, Material.IRON_BLOCK, 0.8, 0,
            Connect.NONE, "Serviertablett"),
    VIP_ONLY_PLAQUE("casinodeco:vip_only_plaque", Carrier.LIGHT, Place.WALL, Material.DARK_OAK_PLANKS,
            Material.IRON_BLOCK, 0.8, 0, Connect.NONE, "Schild für VIPs"),
    /** The two display cabinets are solid in the mod (16 x 16 x 6 and 16 x 9.6 x 6 px): barrier carriers. */
    BOTTLE_DISPLAY_SHELF("casinodeco:bottle_display_shelf", Carrier.BARRIER, Place.WALL, Material.CHEST,
            Material.OAK_PLANKS, 0.8, 0, Connect.NONE, "Flaschenregal"),
    GLASS_DISPLAY_SHELF("casinodeco:glass_display_shelf", Carrier.BARRIER, Place.WALL, Material.CHEST,
            Material.OAK_PLANKS, 0.8, 0, Connect.NONE, "Gläserregal"),
    // ---- speaker --------------------------------------------------------------------------------------------------
    CASINO_SPEAKER("casinosound:casino_speaker", Carrier.LIGHT, Place.SPEAKER, Material.OAK_PLANKS,
            Material.OAK_PLANKS, 2.5, 2, Connect.NONE, "Casino-Lautsprecher"),
    // ---- casino TVs (games.md 16): oak planks with the metal sound, light when on ---------------------------------
    CASINO_TV("roulettecasino:casino_tv", Carrier.LIGHT, Place.TV, Material.OAK_PLANKS, Material.IRON_BLOCK, 2.0, 5,
            Connect.NONE, "Kleiner Casino-Fernseher"),
    CASINO_TV_LARGE("roulettecasino:casino_tv_large", Carrier.LIGHT, Place.TV, Material.OAK_PLANKS,
            Material.IRON_BLOCK, 2.0, 7, Connect.NONE, "Großer Casino-Fernseher"),
    CASINO_TV_3X3("roulettecasino:casino_tv_3x3", Carrier.LIGHT, Place.TV, Material.OAK_PLANKS, Material.IRON_BLOCK,
            2.0, 7, Connect.NONE, "Extragroßer Casino-Fernseher"),
    // ---- dealer station (games.md 18): a lectern copy, the croupier's job site ------------------------------------
    DEALER_STATION("roulettecasino:dealer_station", Carrier.BARRIER, Place.FREE, Material.LECTERN, Material.LECTERN,
            2.5, 0, Connect.NONE, "Croupier-Theke"),
    // ---- stairs and slabs the mod does not have (Jack 2026-11-09; at the end: the order of the others stays) ----------
    GILDED_DARK_OAK_STAIRS("casinodeco:gilded_dark_oak_paneling_stairs", Material.CRIMSON_STAIRS,
            "gilded_dark_oak_paneling", "Treppe — Vergoldete Schwarzeichenvertäfelung"),
    GILDED_DARK_OAK_SLAB("casinodeco:gilded_dark_oak_paneling_slab", Material.CRIMSON_SLAB,
            "gilded_dark_oak_paneling", "Stufe — Vergoldete Schwarzeichenvertäfelung"),
    CLASSIC_GILDED_DARK_OAK_STAIRS("casinodeco:classic_gilded_dark_oak_paneling_stairs", Material.WARPED_STAIRS,
            "classic_gilded_dark_oak_paneling", "Treppe — Vergoldete Vertäfelung aus Schwarzeichenholzbrettern"),
    CLASSIC_GILDED_DARK_OAK_SLAB("casinodeco:classic_gilded_dark_oak_paneling_slab", Material.WARPED_SLAB,
            "classic_gilded_dark_oak_paneling", "Stufe — Vergoldete Vertäfelung aus Schwarzeichenholzbrettern"),
    DARK_OAK_PANELING_STAIRS("casinodeco:dark_oak_paneling_stairs", Material.CRIMSON_STAIRS, "dark_oak_paneling",
            "Treppe — Schwarzeichenvertäfelung"),
    DARK_OAK_PANELING_SLAB("casinodeco:dark_oak_paneling_slab", Material.CRIMSON_SLAB, "dark_oak_paneling",
            "Stufe — Schwarzeichenvertäfelung"),
    DARK_OAK_BASEBOARD_STAIRS("casinodeco:dark_oak_baseboard_stairs", Material.CRIMSON_STAIRS, "dark_oak_baseboard",
            "Treppe — Sockelleiste aus Schwarzeiche und Gold"),
    DARK_OAK_BASEBOARD_SLAB("casinodeco:dark_oak_baseboard_slab", Material.CRIMSON_SLAB, "dark_oak_baseboard",
            "Stufe — Sockelleiste aus Schwarzeiche und Gold"),
    DARK_OAK_DADO_RAIL_STAIRS("casinodeco:dark_oak_dado_rail_stairs", Material.CRIMSON_STAIRS, "dark_oak_dado_rail",
            "Treppe — Zierleiste aus Schwarzeiche und Gold"),
    DARK_OAK_DADO_RAIL_SLAB("casinodeco:dark_oak_dado_rail_slab", Material.CRIMSON_SLAB, "dark_oak_dado_rail",
            "Stufe — Zierleiste aus Schwarzeiche und Gold"),
    DARK_OAK_CORNICE_STAIRS("casinodeco:dark_oak_cornice_stairs", Material.CRIMSON_STAIRS, "dark_oak_cornice",
            "Treppe — Gesims aus Schwarzeiche und Gold"),
    DARK_OAK_CORNICE_SLAB("casinodeco:dark_oak_cornice_slab", Material.CRIMSON_SLAB, "dark_oak_cornice",
            "Stufe — Gesims aus Schwarzeiche und Gold"),
    DARK_OAK_PILASTER_STAIRS("casinodeco:dark_oak_pilaster_stairs", Material.CRIMSON_STAIRS, "dark_oak_pilaster",
            "Treppe — Pilaster aus Schwarzeiche und Gold"),
    DARK_OAK_PILASTER_SLAB("casinodeco:dark_oak_pilaster_slab", Material.CRIMSON_SLAB, "dark_oak_pilaster",
            "Stufe — Pilaster aus Schwarzeiche und Gold"),
    DARK_OAK_DIAMOND_PANEL_STAIRS("casinodeco:dark_oak_diamond_panel_stairs", Material.CRIMSON_STAIRS, "dark_oak_diamond_panel",
            "Treppe — Schwarzeichenpaneel mit Goldmedaillon"),
    DARK_OAK_DIAMOND_PANEL_SLAB("casinodeco:dark_oak_diamond_panel_slab", Material.CRIMSON_SLAB, "dark_oak_diamond_panel",
            "Stufe — Schwarzeichenpaneel mit Goldmedaillon"),
    CLASSIC_DARK_OAK_BASEBOARD_STAIRS("casinodeco:classic_dark_oak_baseboard_stairs", Material.WARPED_STAIRS, "classic_dark_oak_baseboard",
            "Treppe — Sockelleiste aus Schwarzeichenholzbrettern und Gold"),
    CLASSIC_DARK_OAK_BASEBOARD_SLAB("casinodeco:classic_dark_oak_baseboard_slab", Material.WARPED_SLAB, "classic_dark_oak_baseboard",
            "Stufe — Sockelleiste aus Schwarzeichenholzbrettern und Gold"),
    CLASSIC_DARK_OAK_DADO_RAIL_STAIRS("casinodeco:classic_dark_oak_dado_rail_stairs", Material.WARPED_STAIRS, "classic_dark_oak_dado_rail",
            "Treppe — Zierleiste aus Schwarzeichenholzbrettern und Gold"),
    CLASSIC_DARK_OAK_DADO_RAIL_SLAB("casinodeco:classic_dark_oak_dado_rail_slab", Material.WARPED_SLAB, "classic_dark_oak_dado_rail",
            "Stufe — Zierleiste aus Schwarzeichenholzbrettern und Gold"),
    CLASSIC_DARK_OAK_CORNICE_STAIRS("casinodeco:classic_dark_oak_cornice_stairs", Material.WARPED_STAIRS, "classic_dark_oak_cornice",
            "Treppe — Gesims aus Schwarzeichenholzbrettern und Gold"),
    CLASSIC_DARK_OAK_CORNICE_SLAB("casinodeco:classic_dark_oak_cornice_slab", Material.WARPED_SLAB, "classic_dark_oak_cornice",
            "Stufe — Gesims aus Schwarzeichenholzbrettern und Gold"),
    CLASSIC_DARK_OAK_PILASTER_STAIRS("casinodeco:classic_dark_oak_pilaster_stairs", Material.WARPED_STAIRS, "classic_dark_oak_pilaster",
            "Treppe — Pilaster aus Schwarzeichenholzbrettern und Gold"),
    CLASSIC_DARK_OAK_PILASTER_SLAB("casinodeco:classic_dark_oak_pilaster_slab", Material.WARPED_SLAB, "classic_dark_oak_pilaster",
            "Stufe — Pilaster aus Schwarzeichenholzbrettern und Gold"),
    CLASSIC_DARK_OAK_DIAMOND_PANEL_STAIRS("casinodeco:classic_dark_oak_diamond_panel_stairs", Material.WARPED_STAIRS, "classic_dark_oak_diamond_panel",
            "Treppe — Paneel aus Schwarzeichenholzbrettern mit Goldmedaillon"),
    CLASSIC_DARK_OAK_DIAMOND_PANEL_SLAB("casinodeco:classic_dark_oak_diamond_panel_slab", Material.WARPED_SLAB, "classic_dark_oak_diamond_panel",
            "Stufe — Paneel aus Schwarzeichenholzbrettern mit Goldmedaillon");

    /** What stands in the world under the display. */
    public enum Carrier {
        /** Invisible full cube with collision and clicks; placed and broken by the plugin (render.md 2.1). */
        BARRIER,
        /** Invisible light block (the mod's light level, 0 for the dark ones) plus an Interaction entity for clicks. */
        LIGHT,
        /** A vanilla stair block placed by vanilla from the deco item; the display follows its state. */
        STAIRS,
        /** A vanilla slab block placed by vanilla (doubles like the mod's slab). */
        SLAB
    }

    /** The mod's placement rule. */
    public enum Place {
        /** Vanilla stair/slab placement (the mod's blocks are vanilla {@code StairBlock}/{@code SlabBlock}). */
        VANILLA,
        /** Centre support below, no facing (tables, bars, rope post). */
        FLOOR_PLAIN,
        /** Centre support below, facing = opposite of the player's horizontal direction ({@code CasinoDecorBlock}). */
        FLOOR,
        /** {@link #FLOOR} plus stacking 1..4 with the same item, waterloggable ({@code CasinoStackedDecorBlock}). */
        STACKED,
        /** {@link #FLOOR} plus a block above without collision ({@code CasinoSeatBlock}). */
        SEAT,
        /** Block above without collision, centre support below, facing ({@code CasinoSofaBlock}). */
        SOFA,
        /** The nearest horizontal looking direction whose wall is sturdy ({@code onWall} decor, the sconce). */
        WALL,
        /** The clicked horizontal face, sturdy wall behind ({@code CasinoSignBlock}). */
        SIGN,
        /** {@link #SIGN} for all 6 parts of the 3 x 2 sign ({@code LargeCasinoSignBlock}). */
        LARGE_SIGN,
        /** Floor, wall or ceiling ({@code FaceAttachedHorizontalDirectionalBlock}). */
        SPEAKER,
        /** Hanging 3 x 3 body under 1-2 chain blocks ({@code CasinoChandelierBlock}). */
        CHANDELIER,
        /**
         * The clicked horizontal face; a panel of {@link DecoType#tvColumns()} squared cells growing to the
         * counter-clockwise side and upward, a sturdy wall behind every cell ({@code CasinoTelevisionBlock}).
         */
        TV,
        /** Any replaceable cell: no support, no facing (a plain {@code Block}: the dealer station). */
        FREE
    }

    /** What a block's connections ({@code north/east/south/west}, sofa {@code joins}) follow. */
    public enum Connect {
        NONE,
        /** The same block ({@code CasinoTableBlock}, {@code VelvetRopePostBlock}: {@code neighbor.is(this)}). */
        SAME,
        /** Any bar ({@code CasinoBarBlock.joins}: {@code instanceof CasinoBarBlock}). */
        BAR,
        /** {@code SofaLayout} over the connected sofas. */
        SOFA
    }

    private final String id;
    private final Carrier carrier;
    private final Place place;
    private final Material material;
    private final Material sound;
    private final double hardness;
    private final int light;
    private final Connect connect;
    private final String german;
    private final Material vanilla;
    private final String family;

    DecoType(String id, Carrier carrier, Place place, Material material, Material sound, double hardness, int light,
             Connect connect, String german) {
        this.id = id;
        this.carrier = carrier;
        this.place = place;
        this.material = material;
        this.sound = sound;
        this.hardness = hardness;
        this.light = light;
        this.connect = connect;
        this.german = german;
        this.vanilla = null;
        this.family = null;
    }

    /** Stairs and slabs: the vanilla carrier block and the texture family ({@code CasinoFloorShapes}). */
    DecoType(String id, Material vanilla, String family, String german) {
        this.id = id;
        this.carrier = vanilla.name().endsWith("_STAIRS") ? Carrier.STAIRS : Carrier.SLAB;
        this.place = Place.VANILLA;
        this.material = vanilla;
        this.sound = vanilla;
        this.hardness = 0;
        this.light = 0;
        this.connect = Connect.NONE;
        this.german = german;
        this.vanilla = vanilla;
        this.family = family;
    }

    /** The full mod id, e.g. {@code casinodeco:casino_sofa}. */
    public String id() {
        return id;
    }

    /** The id's path, e.g. {@code casino_sofa}. */
    public String path() {
        return id.substring(id.indexOf(':') + 1);
    }

    /** The translation key of the block name ({@code block.casinodeco.casino_sofa}). */
    public String translationKey() {
        return "block." + id.substring(0, id.indexOf(':')) + "." + path();
    }

    public Carrier carrier() {
        return carrier;
    }

    public Place place() {
        return place;
    }

    /** The block the mod copies for tool speed, harvest rule and (vanilla carriers) the carrier itself. */
    public Material material() {
        return material;
    }

    /** The block whose sound group the mod uses ({@code SoundType}). */
    public Material sound() {
        return sound;
    }

    /** Destroy time of the mod's block (unused for vanilla carriers, the client breaks those itself). */
    public double hardness() {
        return hardness;
    }

    /** Light level of the block (switched blocks: when on; the chandelier: its body). */
    public int light() {
        return light;
    }

    public Connect connect() {
        return connect;
    }

    public String german() {
        return german;
    }

    /** The vanilla carrier block of stairs/slabs (null for the other carriers). */
    public Material vanilla() {
        return vanilla;
    }

    /** Stairs and slabs the mod does not have: their files come from the pack tool, their recipes from here. */
    public boolean own() {
        return family != null && family.contains("dark_oak");
    }

    /** The texture family of stairs/slabs ({@code ivory_marble}); null for the rest. */
    public String family() {
        return family;
    }

    /** Families whose models come in four random variants (the mod's marble blockstates). */
    public boolean variants() {
        return family != null && family.contains("marble") || this == IVORY_MARBLE_BAR || this == BLACK_MARBLE_BAR;
    }

    public boolean vanillaCarrier() {
        return carrier == Carrier.STAIRS || carrier == Carrier.SLAB;
    }

    /** True if the type is placed and broken by the plugin (barrier or light carriers). */
    public boolean pluginCarrier() {
        return !vanillaCarrier();
    }

    /**
     * Digging ignores the tool: every tool digs at hand speed, because the mod's block is in no {@code mineable/*} tag
     * (games.md 21.1). True for the step 8 blocks; the chair and stool (also untagged in the mod) keep the planks' axe
     * speed of step 4. Light carriers break at the first hit anyway, so only the dealer station's timer uses it.
     */
    public boolean anyTool() {
        return place == Place.TV || this == DEALER_STATION;
    }

    /** Columns (= rows) of a TV: 1, 2 or 3 ({@code CasinoTelevisionBlock.columns}); 0 for everything else. */
    public int tvColumns() {
        return switch (this) {
            case CASINO_TV -> 1;
            case CASINO_TV_LARGE -> 2;
            case CASINO_TV_3X3 -> 3;
            default -> 0;
        };
    }

    /**
     * A barrier (collision) deco that does not stand on support below: the dealer station ({@link Place#FREE}) and the
     * wall shelves ({@link Place#WALL}). Such a block could cap a player in a hole, so it keeps the machines' distance
     * of {@code MachineService.PLAYER_CLEARANCE} blocks to other players (and, as every barrier deco, the logout-spot
     * rule).
     */
    public boolean playerClearance() {
        return carrier == Carrier.BARRIER && (place == Place.FREE || place == Place.WALL);
    }

    /** True for chair, stool and sofa. */
    public boolean seat() {
        return place == Place.SEAT || place == Place.SOFA;
    }

    /** The mod lets water into it ({@code SimpleWaterloggedBlock}): the sconce and the stacked decor. */
    public boolean waterloggable() {
        return place == Place.STACKED || this == CHANDELIER_WALL_SCONCE || vanillaCarrier();
    }

    /**
     * Range of the display entities: {@code view_range} x 64 blocks (x the client's entity distance setting). Covers
     * over vanilla stairs and slabs reach as far as the server tracks displays (128 blocks, Paper's
     * {@code entity-tracking-range.display}), so the vanilla carrier shows only where no display could (hotfix 0.9.1).
     */
    public float viewRange() {
        if (vanillaCarrier()) {
            return 2.0f;
        }
        return switch (this) {
            case CASINO_SIGN, CASINO_CHANDELIER, CASINO_TV_LARGE, CASINO_TV_3X3 -> 2.0f;
            case CASINO_BOTTLES, CHAMPAGNE_COUPES, SHOT_GLASSES, CASINO_CHIP_PILE, RECEPTION_PLAQUE, CASH_REGISTER,
                 SERVING_TRAY, VIP_ONLY_PLAQUE -> 0.75f;
            default -> 1.25f;
        };
    }

    public static Optional<DecoType> byId(String id) {
        for (DecoType t : values()) {
            if (t.id.equals(id)) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }
}
