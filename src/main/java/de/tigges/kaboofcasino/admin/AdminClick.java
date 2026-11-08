package de.tigges.kaboofcasino.admin;

import de.tigges.kaboofcasino.machine.MachineSettings;
import de.tigges.kaboofcasino.machine.MachineType;
import de.tigges.kaboofcasino.util.Keys;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.api.BinaryTagHolder;

/**
 * One click in a croupier's-hand dialog. Every button sends the custom click {@code kaboofcasino:croupier_hand} with
 * the additions {@code {do:"<action>"}}; the client merges the dialog's input values into that compound (text: a
 * string, checkbox: a boolean, slider: a float). Nothing in the payload names a machine: the target is the dialog the
 * server opened for that player ({@link AdminSessions}). {@link #parse} accepts only the actions of the open dialog's
 * form and only valid inputs: a name of at most 64 characters without control or {@code §} codes, whole numbers in
 * range. Pure.
 *
 * @param inputs the dialog's values, present only if every one of them is valid
 */
record AdminClick(Kind kind, Optional<Inputs> inputs) {

    static final Key KEY = Keys.key("croupier_hand");
    /** {@link #KEY} as text (a Bukkit key never equals an Adventure key object). */
    static final String ID = KEY.asString();
    static final String DO = "do";

    /** Input keys of the machine dialog. */
    static final String NAME = "name";
    static final String LIKE_CASINO = "like_casino";
    static final String VALUE = "value";
    /** Input key of the world dialog's protection checkbox. */
    static final String BREAKABLE = "breakable";

    /** Which dialog is open: a machine with a win chance, a blackjack/poker table, or the whole casino. */
    enum Form {
        CHANCE_MACHINE, BIAS_MACHINE, WORLD;

        static Form of(MachineType type) {
            return MachineSettings.usesBias(type) ? BIAS_MACHINE : CHANCE_MACHINE;
        }

        boolean machine() {
            return this != WORLD;
        }
    }

    enum Kind {
        /** "Speichern": apply the inputs, show the dialog again. */
        SAVE(true, true),
        /** "Kopieren": apply the inputs, copy the machine into the hand. */
        COPY(true, false),
        /** "Einfügen": the copy in the hand onto this machine. */
        PASTE(true, false),
        /** "Zurücksetzen" (click twice): the machine follows the casino again. */
        RESET(true, false),
        /** "Kopie vergessen": apply the inputs, forget the copy in the hand. */
        FORGET(false, true),
        /** "Alles normal" (click twice): every game back to its natural odds. */
        ALL_NORMAL(false, true),
        /** "Fertig" and ESC (the exit action): apply the inputs, close. */
        DONE(true, true);

        private final boolean onMachine;
        private final boolean onWorld;

        Kind(boolean onMachine, boolean onWorld) {
            this.onMachine = onMachine;
            this.onWorld = onWorld;
        }

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        boolean allowedIn(Form form) {
            return form.machine() ? onMachine : onWorld;
        }

        /** The payload as SNBT: {@code {do:"save"}}. */
        String snbt() {
            return "{" + DO + ":\"" + id() + "\"}";
        }

        BinaryTagHolder payload() {
            return BinaryTagHolder.binaryTagHolder(snbt());
        }
    }

    /** The values of a dialog's inputs. */
    sealed interface Inputs permits MachineInputs, WorldInputs {
    }

    /**
     * @param name       the typed name, stripped ("" = automatic)
     * @param likeCasino "Wie das Casino": follow the type
     * @param value      the slider: win chance 0..100 or bias -100..100 (ignored while {@code likeCasino})
     */
    record MachineInputs(String name, boolean likeCasino, int value) implements Inputs {
    }

    /**
     * @param sliders   per type: the win chance slider 0..100, or the bias -100..100 of blackjack and poker
     * @param natural   the games whose "normal" box is ticked (win chances only)
     * @param breakable "Breakable in survival"
     */
    record WorldInputs(Map<MachineType, Integer> sliders, Set<MachineType> natural, boolean breakable)
            implements Inputs {
        WorldInputs {
            sliders = Map.copyOf(sliders);
            natural = Set.copyOf(natural);
        }
    }

    AdminClick {
        inputs = inputs == null ? Optional.empty() : inputs;
    }

    static String naturalKey(MachineType type) {
        return MachineSettings.modType(type) + "_natural";
    }

    static String chanceKey(MachineType type) {
        return MachineSettings.modType(type) + "_chance";
    }

    static String biasKey(MachineType type) {
        return MachineSettings.modType(type) + "_bias";
    }

    /**
     * Reads a click of an open dialog of {@code form} (Paper's {@code DialogResponseView} getters). Empty for an
     * unknown action, an action the form does not have, and a {@link Kind#SAVE} without valid inputs.
     */
    static Optional<AdminClick> parse(Form form, Function<String, String> text, Function<String, Float> number,
                                      Function<String, Boolean> bool) {
        String id = text.apply(DO);
        Kind kind = null;
        for (Kind k : Kind.values()) {
            if (k.id().equals(id)) {
                kind = k;
            }
        }
        if (kind == null || !kind.allowedIn(form)) {
            return Optional.empty();
        }
        Optional<Inputs> inputs = form.machine() ? machineInputs(form, text, number, bool)
                : worldInputs(number, bool);
        if (kind == Kind.SAVE && inputs.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new AdminClick(kind, inputs));
    }

    private static Optional<Inputs> machineInputs(Form form, Function<String, String> text,
                                                  Function<String, Float> number, Function<String, Boolean> bool) {
        Optional<String> name = MachineSettings.validName(text.apply(NAME));
        Boolean likeCasino = bool.apply(LIKE_CASINO);
        boolean bias = form == Form.BIAS_MACHINE;
        Integer value = whole(number.apply(VALUE), bias ? MachineSettings.MIN_BIAS : 0, 100);
        if (name.isEmpty() || likeCasino == null || value == null) {
            return Optional.empty();
        }
        return Optional.of(new MachineInputs(name.get(), likeCasino, value));
    }

    private static Optional<Inputs> worldInputs(Function<String, Float> number, Function<String, Boolean> bool) {
        Map<MachineType, Integer> sliders = new EnumMap<>(MachineType.class);
        Set<MachineType> natural = EnumSet.noneOf(MachineType.class);
        for (MachineType type : MachineSettings.MOD_ORDER) {
            if (MachineSettings.usesBias(type)) {
                Integer bias = whole(number.apply(biasKey(type)), MachineSettings.MIN_BIAS, MachineSettings.MAX_BIAS);
                if (bias == null) {
                    return Optional.empty();
                }
                sliders.put(type, bias);
            } else {
                Boolean ticked = bool.apply(naturalKey(type));
                Integer chance = whole(number.apply(chanceKey(type)), 0, 100);
                if (ticked == null || chance == null) {
                    return Optional.empty();
                }
                sliders.put(type, chance);
                if (ticked) {
                    natural.add(type);
                }
            }
        }
        Boolean breakable = bool.apply(BREAKABLE);
        if (breakable == null) {
            return Optional.empty();
        }
        return Optional.of(new WorldInputs(sliders, natural, breakable));
    }

    /** A slider value: a finite whole number in min..max, else null. */
    static Integer whole(Float value, int min, int max) {
        if (value == null || !Float.isFinite(value) || value != Math.rint(value) || value < min || value > max) {
            return null;
        }
        return value.intValue();
    }
}
