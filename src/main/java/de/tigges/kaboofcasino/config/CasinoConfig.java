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
 * @param cashier  the cashier: exchange rates, craftable, the prize shop (PLAN-2 C3)
 * @param leaderboard the leaderboard boards and {@code /casino top} (PLAN-2 C4)
 * @param chipshop the Chip-Shop: markup, ingredient values, per-item prices, limits and hiding (PLAN-2 C6)
 */
public record CasinoConfig(Pack pack, Bets bets, Rounds rounds, Owed owed, Machines machines, Safety safety, Gui gui,
                           Slot slot, Slot wheel, Slot plinko, Slot roulette, Blackjack blackjack, Poker poker,
                           Slot derby,
                           Lights lights, Music music,
                           de.tigges.kaboofcasino.cashier.CashierSettings cashier, Leaderboard leaderboard,
                           de.tigges.kaboofcasino.chipshop.ChipShopSettings chipshop, Jukebox jukebox,
                           Discord discord) {

    /** The config without a {@code jukebox:} section (tests built before the jukebox). */
    public CasinoConfig(Pack pack, Bets bets, Rounds rounds, Owed owed, Machines machines, Safety safety, Gui gui,
                        Slot slot, Slot wheel, Slot plinko, Slot roulette, Blackjack blackjack, Poker poker, Slot derby,
                        Lights lights, Music music, de.tigges.kaboofcasino.cashier.CashierSettings cashier,
                        Leaderboard leaderboard, de.tigges.kaboofcasino.chipshop.ChipShopSettings chipshop) {
        this(pack, bets, rounds, owed, machines, safety, gui, slot, wheel, plinko, roulette, blackjack, poker, derby,
                lights, music, cashier, leaderboard, chipshop, Jukebox.DEFAULTS, Discord.DEFAULTS);
    }

    /** The config without a {@code discord:} section (tests built before the Discord posts). */
    public CasinoConfig(Pack pack, Bets bets, Rounds rounds, Owed owed, Machines machines, Safety safety, Gui gui,
                        Slot slot, Slot wheel, Slot plinko, Slot roulette, Blackjack blackjack, Poker poker, Slot derby,
                        Lights lights, Music music, de.tigges.kaboofcasino.cashier.CashierSettings cashier,
                        Leaderboard leaderboard, de.tigges.kaboofcasino.chipshop.ChipShopSettings chipshop,
                        Jukebox jukebox) {
        this(pack, bets, rounds, owed, machines, safety, gui, slot, wheel, plinko, roulette, blackjack, poker, derby,
                lights, music, cashier, leaderboard, chipshop, jukebox, Discord.DEFAULTS);
    }

    /**
     * {@code discord:} posts through KaboofDiscord (Jack 2026-11-11), if that plugin is there and its
     * {@code events.casino} is on. Players hidden from the leaderboard never appear.
     *
     * @param bigWins         a single bet that pays at least {@code multiplier} times its stake and at least
     *                        {@code minChips} chips is posted
     * @param multiplier      2..10000
     * @param minChips        0..10 million
     * @param cooldownSeconds a player's next big win is posted only after this many seconds (0..86400)
     * @param weekly          after each week (Monday 00:00 Berlin) its top 3 per leaderboard category are posted once
     */
    public record Discord(boolean bigWins, int multiplier, int minChips, int cooldownSeconds, boolean weekly) {
        public static final Discord DEFAULTS = new Discord(true, 50, 250, 300, true);
    }

    /**
     * {@code jukebox:} the casino jukeboxes (Jack 2026-11-10).
     *
     * @param enabled false: every jukebox is silent (they can still be placed and set)
     */
    public record Jukebox(boolean enabled) {
        public static final Jukebox DEFAULTS = new Jukebox(true);
    }

    /**
     * The leaderboard (step 10, PLAN-2 C4).
     *
     * @param craftable      true: both boards have a recipe; false (default): only {@code /casino give}
     * @param refreshSeconds how often the lists are recomputed from the database at most (30..3600; only when rounds
     *                       were played or someone hid himself since)
     * @param cycleSeconds   a board without a pinned category turns to the next page every so many seconds (0 = only
     *                       by right click; 5..600)
     * @param minStake       the multiplier category counts rounds from this chip stake on (1..1 000 000)
     * @param weeklyLog      after a week (Monday 00:00 Berlin) its winners go to the server log once
     */
    public record Leaderboard(boolean craftable, int refreshSeconds, int cycleSeconds, int minStake,
                              boolean weeklyLog) {
    }

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
     * Stakes are chips (PLAN-2 C2): a stake is a chip value; ores are exchanged at the cashier ({@code cashier.rates}),
     * the old {@code bets.items} list is gone.
     *
     * @param maxStake highest stake in chip value ({@link #capPerRound}: per round, else per bet slot)
     * @param capPer   {@code slot} (per bet slot, as the mod) or {@code round} (all slots of a round together)
     */
    public record Bets(int maxStake, String capPer) {
        public static final String PER_SLOT = "slot";
        public static final String PER_ROUND = "round";

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
     * @param breakable         the mod's world setting "Breakable in survival" (games.md 21.2, set with the croupier's
     *                          hand): false = nobody outside creative mines a machine; creative and
     *                          {@code /casino remove} still remove them
     */
    public record Machines(String breakMode, int verifyTicks, boolean refuseLogoutSpots, boolean breakable) {
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
     * Per machine type ({@code slot:}, {@code wheel:}, {@code plinko:}, {@code roulette:}, {@code derby:}).
     * The derby's win chance applies to solo races only; group races always use natural odds and are allowed only
     * at -1 or 25 ({@code DuckDerbyBlockEntity.groupAllowed}).
     *
     * @param winChance        -1 = natural odds (the mod's default); 0..100 = forced pools (CasinoOdds)
     * @param countdownSeconds the roulette's betting window when others stand at the table (0 = every spin at once)
     */
    public record Slot(int winChance, int idleLightTicks, int idleLightRange, boolean needsSupport,
                       int countdownSeconds) {
        public Slot(int winChance, int idleLightTicks, int idleLightRange, boolean needsSupport) {
            this(winChance, idleLightTicks, idleLightRange, needsSupport, 0);
        }
    }

    /**
     * {@code blackjack:}.
     *
     * @param bias         the mod's admin setting "which side the cards favour" (-100..100, default 0 = a fair
     *                     shoe; {@code CasinoSettings.bias}, {@code BlackjackShoe.drawFavouring}); the win chance does
     *                     not apply to blackjack in the mod
     * @param needsSupport at least one of the eight cells needs solid ground below (false = the mod)
     */
    public record Blackjack(int bias, boolean needsSupport, int countdownSeconds) {
        public Blackjack(int bias, boolean needsSupport) {
            this(bias, needsSupport, 15);
        }
    }

    /**
     * {@code poker:} (Casino Hold'em against the house dealer).
     *
     * @param bias         the mod's admin setting "which side the cards favour" (-100..100, default 0 = a fair deck;
     *                     {@code CasinoSettings.bias}); the win chance does not apply to poker in the mod
     * @param needsSupport at least one of the twelve cells needs solid ground below (false = the mod)
     * @param holdem       Texas Hold'em between the players (Jack 2026-11-10)
     */
    public record Poker(int bias, boolean needsSupport, Holdem holdem) {
        public Poker(int bias, boolean needsSupport) {
            this(bias, needsSupport, Holdem.DEFAULTS);
        }
    }

    /**
     * {@code poker.holdem:} No-Limit Texas Hold'em at a poker table as soon as two players are near it.
     *
     * @param enabled         false: a poker table only plays against the croupier
     * @param smallBlind      the small blind in chips
     * @param bigBlind        the big blind (at least the small blind)
     * @param minBuyIn        the fewest chips to sit down with (at least the big blind)
     * @param maxBuyIn        the most chips to sit down with
     * @param turnSeconds     the time to act; then check if possible, else fold
     * @param startSeconds    the wait before the first hand once two are seated (others may still sit down)
     * @param nextHandSeconds the pause between two hands
     */
    public record Holdem(boolean enabled, int smallBlind, int bigBlind, int minBuyIn, int maxBuyIn, int turnSeconds,
                         int startSeconds, int nextHandSeconds) {
        public static final Holdem DEFAULTS = new Holdem(true, 5, 10, 100, 2000, 30, 10, 6);

        public Holdem {
            bigBlind = Math.max(bigBlind, smallBlind);
            minBuyIn = Math.max(minBuyIn, bigBlind);
            maxBuyIn = Math.max(maxBuyIn, minBuyIn);
        }
    }

    /**
     * {@code music:}.
     *
     * @param enabled offer the optional music pack at login and play the mod's lounge music near switched-on
     *                speakers (false: no music pack, no music, no note particles; the speakers keep their station)
     */
    public record Music(boolean enabled) {
    }

    /** The mod's 18 bet items (ores and their blocks): before 0.9.0 the stakes, now what the cashier exchanges. */
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
            new Bets(2000, Bets.PER_SLOT),
            new Rounds(8),
            new Owed(20),
            new Machines("owner", 100, true, true),
            new Safety(10, 12, 8, 5),
            new Gui(4, 120),
            new Slot(-1, 4, 16, true),
            new Slot(-1, 4, 16, true),
            new Slot(-1, 4, 16, true),
            new Slot(-1, 0, 16, true, 15),
            new Blackjack(0, true),
            new Poker(0, true),
            new Slot(-1, 4, 16, true),
            new Lights(4),
            new Music(true),
            de.tigges.kaboofcasino.cashier.CashierSettings.DEFAULTS,
            new Leaderboard(false, 30, 15, 25, true),
            de.tigges.kaboofcasino.chipshop.ChipShopSettings.DEFAULTS, Jukebox.DEFAULTS, Discord.DEFAULTS);
}
