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
 * @param music    the speakers' lounge music and its optional pack (render.md 6)
 */
public record CasinoConfig(Pack pack, Bets bets, Rounds rounds, Owed owed, Machines machines, Safety safety, Gui gui,
                           Slot slot, Slot wheel, Slot plinko, Slot roulette, Blackjack blackjack, Poker poker,
                           Lights lights, Music music) {

    /**
     * @param enabled        send the pack at all
     * @param enforce        kill switch: true = disconnect players who decline or fail; false = offer only
     * @param serve          {@code minecraft-port}: HTTP on the game port (sniffer); {@code http-port}: own HttpServer
     * @param urlHost        host in the URL sent to clients (Jack's public address)
     * @param urlPort        public port in the URL in {@code minecraft-port} mode (the game port)
     * @param port           TCP port of the embedded HTTP server ({@code http-port} mode; also its URL port)
     * @param bind           bind address of the HTTP server
     * @param timeoutSeconds how long the configuration phase waits for the client's answer
     */
    public record Pack(boolean enabled, boolean enforce, String serve, String urlHost, int urlPort, int port,
                       String bind, int timeoutSeconds) {
        public static final String MINECRAFT_PORT = "minecraft-port";
        public static final String HTTP_PORT = "http-port";

        public boolean onGamePort() {
            return serve.equals(MINECRAFT_PORT);
        }

        /** Port in the download URL. */
        public int publicPort() {
            return onGamePort() ? urlPort : port;
        }
    }

    /**
     * @param maxStake highest stake per unit ({@link #capPerRound}: per round, else per bet slot)
     * @param items    material keys like {@code minecraft:diamond}
     * @param capPer   {@code slot} (the mod: a stack per bet slot) or {@code round} (all slots of a round together)
     */
    public record Bets(int maxStake, List<String> items, String capPer) {
        public static final String PER_SLOT = "slot";
        public static final String PER_ROUND = "round";

        public Bets {
            items = List.copyOf(items);
        }

        public boolean capPerRound() {
            return PER_ROUND.equals(capPer);
        }
    }

    public record Rounds(int maxOpen) {
    }

    public record Owed(int deliveryTicks) {
    }

    /**
     * @param breakMode         {@code owner} (placer + ops) or {@code anyone}
     * @param refuseLogoutSpots refuse placing a carrier within one block of where an offline player logged out
     *                          (safety; only switched off for tests)
     */
    public record Machines(String breakMode, int verifyTicks, boolean refuseLogoutSpots) {
        public Machines {
            Objects.requireNonNull(breakMode);
        }

        public boolean anyoneMayBreak() {
            return breakMode.equals("anyone");
        }
    }

    public record Safety(int combatSeconds, int monsterRadius, int tntRadius, int sweepTicks) {
    }

    /** @param idleCloseSeconds a machine menu without a spin closes after this long (0 = never) */
    public record Gui(int clickGuardTicks, int idleCloseSeconds) {
    }

    /** @param maxUpdatesPerTick idle light updates of all machines together per tick (round robin) */
    public record Lights(int maxUpdatesPerTick) {
    }

    /**
     * Per machine type ({@code slot:}, {@code wheel:}, {@code plinko:}).
     *
     * @param winChance -1 = natural odds (the mod's default); 0..100 = forced pools (CasinoOdds)
     */
    public record Slot(int winChance, int idleLightTicks, int idleLightRange, boolean needsSupport) {
    }

    /**
     * {@code blackjack:}.
     *
     * @param bias         the mod's admin setting "which side the cards favour" (-100..100, default 0 = a fair
     *                     shoe; {@code CasinoSettings.bias}, {@code BlackjackShoe.drawFavouring}); the win chance does
     *                     not apply to blackjack in the mod
     * @param needsSupport at least one of the eight cells needs solid ground below (false = the mod)
     */
    public record Blackjack(int bias, boolean needsSupport) {
    }

    /**
     * {@code poker:} (Casino Hold'em against the house dealer).
     *
     * @param bias         the mod's admin setting "which side the cards favour" (-100..100, default 0 = a fair deck;
     *                     {@code CasinoSettings.bias}); the win chance does not apply to poker in the mod
     * @param needsSupport at least one of the twelve cells needs solid ground below (false = the mod)
     */
    public record Poker(int bias, boolean needsSupport) {
    }

    /**
     * {@code music:}.
     *
     * @param enabled offer the optional music pack at login and play the mod's lounge music near switched-on
     *                speakers (false: no music pack, no music, no note particles; the speakers keep their station)
     */
    public record Music(boolean enabled) {
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
            new Pack(true, false, Pack.MINECRAFT_PORT, "5.9.137.26", 25565, 25580, "0.0.0.0", 300),
            new Bets(64, DEFAULT_BET_ITEMS, Bets.PER_SLOT),
            new Rounds(8),
            new Owed(20),
            new Machines("owner", 100, true),
            new Safety(10, 12, 8, 5),
            new Gui(4, 120),
            new Slot(-1, 4, 16, true),
            new Slot(-1, 4, 16, true),
            new Slot(-1, 4, 16, true),
            new Slot(-1, 0, 16, true),
            new Blackjack(0, true),
            new Poker(0, true),
            new Lights(4),
            new Music(true));
}
