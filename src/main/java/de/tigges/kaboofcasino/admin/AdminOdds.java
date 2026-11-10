package de.tigges.kaboofcasino.admin;

import de.tigges.kaboofcasino.game.blackjack.BlackjackBias;
import de.tigges.kaboofcasino.game.plinko.PlinkoController;
import de.tigges.kaboofcasino.game.poker.PokerBias;
import de.tigges.kaboofcasino.game.slot.SlotGame;
import de.tigges.kaboofcasino.game.wheel.WheelController;
import de.tigges.kaboofcasino.machine.MachineType;
import java.util.Locale;

/**
 * The numbers the mod's settings window shows ({@code CasinoSettingsScreen.normalChance/normalReturn},
 * {@code CasinoOdds.expectedReturn}, {@code BlackjackBias}/{@code PokerBias} estimates) and its number formats. Pure.
 */
final class AdminOdds {

    private AdminOdds() {
    }

    /** {@code normalChance}: the natural win chance in percent. */
    static double naturalChance(MachineType type) {
        return switch (type) {
            case ROULETTE_TABLE -> 48.648648648648646;
            case DUCK_DERBY -> 25.0;
            case SLOT_MACHINE -> 27.34375;
            case FORTUNE_WHEEL -> 43.75;
            case PLINKO -> 28.90625;
            case BLACKJACK_TABLE, POKER_TABLE -> 0.0;
            case CASHIER, LEADERBOARD_WALL, LEADERBOARD_WALL_2X2,
                    LEADERBOARD_WALL_3X3,
                    LEADERBOARD_WALL_4X3,
                    LEADERBOARD_WALL_4X4,
                    LEADERBOARD_WALL_5X3,
                    LEADERBOARD_WALL_5X4,
                    LEADERBOARD_WALL_6X4,
                    LEADERBOARD_COLUMN, CHIP_SHOP, JUKEBOX ->
                    throw new IllegalArgumentException(type + " is no game");
        };
    }

    /** {@code natural}: the slider's value while a type follows its natural odds (the rounded natural chance). */
    static int naturalValue(MachineType type) {
        return (int) Math.round(naturalChance(type));
    }

    /** {@code normalReturn}: the natural average return (1.0 = 100 %). */
    static double naturalReturn(MachineType type) {
        return switch (type) {
            case ROULETTE_TABLE -> 0.972972972972973;
            case DUCK_DERBY -> 1.0;
            case SLOT_MACHINE, PLINKO -> 0.9746094F;
            case FORTUNE_WHEEL -> 0.9765625;
            case BLACKJACK_TABLE, POKER_TABLE -> 0.0;
            case CASHIER, LEADERBOARD_WALL, LEADERBOARD_WALL_2X2,
                    LEADERBOARD_WALL_3X3,
                    LEADERBOARD_WALL_4X3,
                    LEADERBOARD_WALL_4X4,
                    LEADERBOARD_WALL_5X3,
                    LEADERBOARD_WALL_5X4,
                    LEADERBOARD_WALL_6X4,
                    LEADERBOARD_COLUMN, CHIP_SHOP, JUKEBOX ->
                    throw new IllegalArgumentException(type + " is no game");
        };
    }

    /** {@code CasinoOdds.expectedReturn} at a forced chance 0..100, or the natural return at -1. */
    static double expectedReturn(MachineType type, int percent) {
        if (percent < 0) {
            return naturalReturn(type);
        }
        double chance = percent / 100.0;
        return switch (type) {
            case ROULETTE_TABLE -> 2.0 * chance;
            case DUCK_DERBY -> 3.0 * chance + (1.0 - chance) / 3.0;
            case SLOT_MACHINE -> SlotGame.POOL.expected(chance);
            case FORTUNE_WHEEL -> WheelController.POOL.expected(chance);
            case PLINKO -> PlinkoController.POOL.expected(chance);
            case BLACKJACK_TABLE, POKER_TABLE, CASHIER, LEADERBOARD_WALL, LEADERBOARD_WALL_2X2,
                    LEADERBOARD_WALL_3X3,
                    LEADERBOARD_WALL_4X3,
                    LEADERBOARD_WALL_4X4,
                    LEADERBOARD_WALL_5X3,
                    LEADERBOARD_WALL_5X4,
                    LEADERBOARD_WALL_6X4,
                    LEADERBOARD_COLUMN, CHIP_SHOP, JUKEBOX ->
                    throw new IllegalArgumentException("no win chance for "
                    + type);
        };
    }

    /** The player's estimated share of won hands at a bias. */
    static double estimatedWins(MachineType type, int bias) {
        return type == MachineType.POKER_TABLE ? PokerBias.estimatedWins(bias) : BlackjackBias.estimatedWins(bias);
    }

    /** The estimated average return at a bias. */
    static double estimatedReturn(MachineType type, int bias) {
        return type == MachineType.POKER_TABLE ? PokerBias.estimatedReturn(bias) : BlackjackBias.estimatedReturn(bias);
    }

    /** {@code percent}: "27.3%" (one decimal, dot, as the mod). */
    static String percent(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value);
    }

    /** {@code about}: a fraction as "43.1%". */
    static String about(double fraction) {
        return String.format(Locale.ROOT, "%.1f%%", fraction * 100.0);
    }

    /** {@code pct}: "30%". */
    static String pct(int value) {
        return value + "%";
    }
}
