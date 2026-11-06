package de.tigges.kaboofcasino.config;

import java.util.List;
import java.util.Objects;

/**
 * The validated {@code config.yml}. {@link #DEFAULTS} equals the shipped file (ConfigLoaderTest).
 *
 * @param pack     resource pack delivery (render.md 8)
 * @param bets     accepted bet items and the stake cap (PLAN decision 1)
 * @param rounds   open rounds per player
 * @param owed     delivery of owed items
 * @param machines breaking and verification
 * @param safety   permadeath guards (PLAN decision 3)
 * @param gui      menu behaviour
 * @param slot     slot machine settings
 */
public record CasinoConfig(Pack pack, Bets bets, Rounds rounds, Owed owed, Machines machines, Safety safety, Gui gui,
                           Slot slot) {

    /**
     * @param enabled        send the pack at all
     * @param enforce        kill switch: true = disconnect players who decline or fail; false = offer only
     * @param urlHost        host in the URL sent to clients (Jack's public address)
     * @param port           TCP port of the embedded HTTP server (also the port in the URL)
     * @param bind           bind address of the HTTP server
     * @param timeoutSeconds how long the configuration phase waits for the client's answer
     */
    public record Pack(boolean enabled, boolean enforce, String urlHost, int port, String bind, int timeoutSeconds) {
    }

    /** @param items material keys like {@code minecraft:diamond} */
    public record Bets(int maxStake, List<String> items) {
        public Bets {
            items = List.copyOf(items);
        }
    }

    public record Rounds(int maxOpen) {
    }

    public record Owed(int deliveryTicks) {
    }

    /** @param breakMode {@code owner} (placer + ops) or {@code anyone} */
    public record Machines(String breakMode, int verifyTicks) {
        public Machines {
            Objects.requireNonNull(breakMode);
        }

        public boolean anyoneMayBreak() {
            return breakMode.equals("anyone");
        }
    }

    public record Safety(int combatSeconds, int monsterRadius, int tntRadius, int sweepTicks) {
    }

    public record Gui(int clickGuardTicks) {
    }

    /** @param winChance -1 = natural odds (the mod's default); 0..100 = forced pools (CasinoOdds) */
    public record Slot(int winChance, int idleLightTicks, int idleLightRange) {
    }

    public static final List<String> DEFAULT_BET_ITEMS = List.of(
            "minecraft:coal", "minecraft:coal_block",
            "minecraft:copper_ingot", "minecraft:copper_block",
            "minecraft:iron_ingot", "minecraft:iron_block",
            "minecraft:gold_ingot", "minecraft:gold_block",
            "minecraft:redstone", "minecraft:redstone_block",
            "minecraft:lapis_lazuli", "minecraft:lapis_block",
            "minecraft:emerald", "minecraft:emerald_block",
            "minecraft:diamond", "minecraft:diamond_block",
            "minecraft:netherite_ingot", "minecraft:netherite_block");

    public static final CasinoConfig DEFAULTS = new CasinoConfig(
            new Pack(true, false, "5.9.137.26", 25580, "0.0.0.0", 300),
            new Bets(64, DEFAULT_BET_ITEMS),
            new Rounds(4),
            new Owed(20),
            new Machines("owner", 100),
            new Safety(10, 12, 8, 5),
            new Gui(4),
            new Slot(-1, 4, 16));
}
