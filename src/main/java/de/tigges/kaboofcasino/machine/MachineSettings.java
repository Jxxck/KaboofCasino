package de.tigges.kaboofcasino.machine;

import de.tigges.kaboofcasino.config.CasinoConfig;
import java.util.List;
import java.util.Optional;

/**
 * The croupier's-hand settings (games.md 20.2): a machine's own values in its record ({@link Own}) over the type values
 * of {@code config.yml}. Every controller asks here, never the config directly ({@link #winChance}, {@link #bias}).
 *
 * <ul>
 *   <li>win chance (roulette, derby, slot machine, wheel, Plinko): the machine's value if set (0..100), else the type's
 *       ({@code <type>.win-chance}, -1 = natural odds) ({@code CasinoSettings.chance});</li>
 *   <li>bias (blackjack, poker): the machine's value if set (-100..100), else the type's ({@code blackjack.bias},
 *       {@code poker.bias}; the config has no "inherit", so its value is the mod's type value or 0)
 *       ({@code CasinoSettings.bias});</li>
 *   <li>name: at most 64 characters, no control or formatting ({@code §}) codes, stripped; empty = the automatic name
 *       "{@code <Type> #<n>}" ({@code CasinoMachineIdentity}).</li>
 * </ul>
 * Pure.
 */
public final class MachineSettings {

    /** The machine follows its type's win chance ({@code CroupierSettings} {@code win_chance} -1). */
    public static final int INHERIT_CHANCE = -1;
    /** The machine follows its type's bias ({@code CroupierSettings.INHERIT}). */
    public static final int INHERIT_BIAS = Integer.MIN_VALUE;
    public static final int MAX_NAME = 64;
    public static final int MIN_BIAS = -100;
    public static final int MAX_BIAS = 100;

    /** The mod's game order ({@code CasinoMachine.TYPES}: roulette, blackjack, poker, derby, slots, wheel, plinko). */
    public static final List<MachineType> MOD_ORDER = List.of(MachineType.ROULETTE_TABLE, MachineType.BLACKJACK_TABLE,
            MachineType.POKER_TABLE, MachineType.DUCK_DERBY, MachineType.SLOT_MACHINE, MachineType.FORTUNE_WHEEL,
            MachineType.PLINKO);

    /**
     * A machine's own settings. {@link #NONE} (follows its type in everything, automatic name) is what an old record
     * without the fields reads as.
     *
     * @param name   own name, "" = automatic
     * @param chance own win chance 0..100, or {@link #INHERIT_CHANCE}
     * @param bias   own bias -100..100, or {@link #INHERIT_BIAS}
     */
    public record Own(String name, int chance, int bias) {

        public static final Own NONE = new Own("", INHERIT_CHANCE, INHERIT_BIAS);

        public Own {
            if (name == null || validName(name).filter(name::equals).isEmpty()) {
                throw new IllegalArgumentException("invalid machine name");
            }
            if (chance != INHERIT_CHANCE && (chance < 0 || chance > 100)) {
                throw new IllegalArgumentException("win chance outside -1..100: " + chance);
            }
            if (bias != INHERIT_BIAS && (bias < MIN_BIAS || bias > MAX_BIAS)) {
                throw new IllegalArgumentException("bias outside -100..100: " + bias);
            }
        }

        /**
         * Reads stored values leniently (a hand-edited chunk): an invalid part becomes "inherit"/automatic, never an
         * unreadable record. {@code null} = not stored.
         */
        public static Own read(String name, Integer chance, Integer bias) {
            String n = name == null ? "" : validName(name).orElse("");
            int c = chance != null && chance >= 0 && chance <= 100 ? chance : INHERIT_CHANCE;
            int b = bias != null && bias >= MIN_BIAS && bias <= MAX_BIAS ? bias : INHERIT_BIAS;
            return new Own(n, c, b);
        }

        public Own withName(String newName) {
            return new Own(newName, chance, bias);
        }

        public Own withChance(int newChance) {
            return new Own(name, newChance, bias);
        }

        public Own withBias(int newBias) {
            return new Own(name, chance, newBias);
        }

        /** The croupier settings only (the mod's {@code reset}: the name is the machine's identity and stays). */
        public Own reset() {
            return new Own(name, INHERIT_CHANCE, INHERIT_BIAS);
        }

        public boolean ownChance() {
            return chance != INHERIT_CHANCE;
        }

        public boolean ownBias() {
            return bias != INHERIT_BIAS;
        }
    }

    private MachineSettings() {
    }

    /** Blackjack and poker take a bias, the other games a win chance ({@code CasinoMachine.usesBias}). */
    public static boolean usesBias(MachineType type) {
        return type == MachineType.BLACKJACK_TABLE || type == MachineType.POKER_TABLE;
    }

    /** The mod's type id ({@code CasinoMachine.type}). */
    public static String modType(MachineType type) {
        return switch (type) {
            case ROULETTE_TABLE -> "roulette";
            case BLACKJACK_TABLE -> "blackjack";
            case POKER_TABLE -> "poker";
            case DUCK_DERBY -> "derby";
            case SLOT_MACHINE -> "slots";
            case FORTUNE_WHEEL -> "wheel";
            case PLINKO -> "plinko";
            case CASHIER -> throw new IllegalArgumentException("the cashier is no game");
        };
    }

    /** The {@code config.yml} path of a type's value (win chance or bias). */
    public static String configPath(MachineType type) {
        return switch (type) {
            case ROULETTE_TABLE -> "roulette.win-chance";
            case BLACKJACK_TABLE -> "blackjack.bias";
            case POKER_TABLE -> "poker.bias";
            case DUCK_DERBY -> "derby.win-chance";
            case SLOT_MACHINE -> "slot.win-chance";
            case FORTUNE_WHEEL -> "wheel.win-chance";
            case PLINKO -> "plinko.win-chance";
            case CASHIER -> throw new IllegalArgumentException("the cashier has no win chance");
        };
    }

    /** The type's configured value: win chance -1..100, or for blackjack and poker the bias -100..100. */
    public static int typeValue(CasinoConfig config, MachineType type) {
        return switch (type) {
            case ROULETTE_TABLE -> config.roulette().winChance();
            case BLACKJACK_TABLE -> config.blackjack().bias();
            case POKER_TABLE -> config.poker().bias();
            case DUCK_DERBY -> config.derby().winChance();
            case SLOT_MACHINE -> config.slot().winChance();
            case FORTUNE_WHEEL -> config.wheel().winChance();
            case PLINKO -> config.plinko().winChance();
            case CASHIER -> throw new IllegalArgumentException("the cashier has no win chance");
        };
    }

    /** {@code CasinoSettings.chance}: own if set, else the type's (which may be -1 = natural odds). */
    public static int effectiveChance(int own, int type) {
        return own >= 0 ? own : type;
    }

    /** {@code CasinoSettings.bias}: own if set, else the type's, else 0. */
    public static int effectiveBias(int own, int type) {
        if (own != INHERIT_BIAS) {
            return own;
        }
        return type == INHERIT_BIAS ? 0 : type;
    }

    /** The win chance a machine plays with: -1 = natural odds, 0..100 = forced (CasinoOdds). */
    public static int winChance(CasinoConfig config, Machine machine) {
        return winChance(config, machine.type(), machine.record().own());
    }

    static int winChance(CasinoConfig config, MachineType type, Own own) {
        if (usesBias(type)) {
            throw new IllegalArgumentException(type + " has a bias, no win chance");
        }
        return effectiveChance(own.chance(), typeValue(config, type));
    }

    /** The bias a blackjack or poker table deals with (-100..100, 0 = fair). */
    public static int bias(CasinoConfig config, Machine machine) {
        return bias(config, machine.type(), machine.record().own());
    }

    static int bias(CasinoConfig config, MachineType type, Own own) {
        if (!usesBias(type)) {
            throw new IllegalArgumentException(type + " has a win chance, no bias");
        }
        return effectiveBias(own.bias(), typeValue(config, type));
    }

    /**
     * {@code CasinoMachineIdentity}: at most {@value #MAX_NAME} characters (before stripping), no ISO control
     * character and no {@code §}; the stripped name, or empty if invalid.
     */
    public static Optional<String> validName(String raw) {
        if (raw == null || raw.length() > MAX_NAME
                || raw.codePoints().anyMatch(c -> Character.isISOControl(c) || c == '§')) {
            return Optional.empty();
        }
        return Optional.of(raw.strip());
    }
}
