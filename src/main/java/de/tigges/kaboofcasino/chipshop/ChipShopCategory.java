package de.tigges.kaboofcasino.chipshop;

import java.util.Set;

/**
 * The Chip-Shop's tabs (PLAN-2 C6), in tab order, each with the item whose icon its tab shows. Which item belongs where
 * is decided by {@link #of}. Pure.
 */
public enum ChipShopCategory {
    FLOORS("boeden", "casinodeco:ruby_velvet"),
    FURNITURE("moebel", "roulettecasino:casino_chair"),
    BAR("bar", "casinodeco:black_marble_bar"),
    LIGHTS("licht", "casinodeco:casino_chandelier"),
    TECH("technik", "roulettecasino:casino_tv"),
    MACHINES("maschinen", "roulettecasino:slot_machine");

    private static final Set<String> FURNITURE_IDS = Set.of("casinodeco:casino_sofa", "roulettecasino:casino_chair",
            "roulettecasino:casino_stool", "casinodeco:velvet_rope_post");
    private static final Set<String> LIGHT_IDS = Set.of("casinodeco:casino_sign", "casinodeco:small_casino_sign",
            "casinodeco:casino_chandelier", "casinodeco:chandelier_wall_sconce", "casinodeco:reception_plaque",
            "casinodeco:vip_only_plaque");
    private static final Set<String> TECH_IDS = Set.of("casinosound:casino_speaker", "roulettecasino:casino_tv",
            "roulettecasino:casino_tv_large", "roulettecasino:casino_tv_3x3", "roulettecasino:dealer_station",
            "roulettecasino:casino_guide");
    private static final Set<String> MACHINE_IDS = Set.of("roulettecasino:slot_machine",
            "roulettecasino:slot_machine_reel", "roulettecasino:slot_machine_mechanism",
            "roulettecasino:roulette_mechanism", "roulettecasino:fortune_wheel", "roulettecasino:plinko",
            "roulettecasino:roulette_table", "roulettecasino:blackjack_table", "roulettecasino:poker_table",
            "roulettecasino:derby_duck_yellow", "roulettecasino:derby_duck_red", "roulettecasino:derby_duck_white",
            "roulettecasino:derby_duck_black", "roulettecasino:duck_derby_mechanism", "roulettecasino:duck_derby");

    private final String id;
    private final String icon;

    ChipShopCategory(String id, String icon) {
        this.id = id;
        this.icon = icon;
    }

    public String id() {
        return id;
    }

    /** The casino item whose icon the tab shows. */
    public String icon() {
        return icon;
    }

    /**
     * The tab of a catalog item: the listed seats, lights and signs, technology and machines; the rest of the bar
     * ({@code *_bar}, glasses, bottles, tables, shelves, chip pile and case, register, tray) at the bar; everything else
     * (the full blocks, stairs and slabs) under floors and walls.
     */
    public static ChipShopCategory of(String id) {
        if (MACHINE_IDS.contains(id)) {
            return MACHINES;
        }
        if (TECH_IDS.contains(id)) {
            return TECH;
        }
        if (LIGHT_IDS.contains(id)) {
            return LIGHTS;
        }
        if (FURNITURE_IDS.contains(id)) {
            return FURNITURE;
        }
        String path = id.substring(id.indexOf(':') + 1);
        if (path.endsWith("_bar") || path.contains("table") || path.contains("glass") || path.contains("bottle")
                || path.contains("coupes") || path.contains("chip_") || path.equals("cash_register")
                || path.equals("serving_tray") || path.contains("shelf")) {
            return BAR;
        }
        return FLOORS;
    }
}
