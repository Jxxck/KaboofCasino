package de.tigges.kaboofcasino.admin;

import de.tigges.kaboofcasino.admin.AdminView.BoolInput;
import de.tigges.kaboofcasino.admin.AdminView.Button;
import de.tigges.kaboofcasino.admin.AdminView.Input;
import de.tigges.kaboofcasino.admin.AdminView.RangeInput;
import de.tigges.kaboofcasino.admin.AdminView.Text;
import de.tigges.kaboofcasino.admin.AdminView.TextInput;
import de.tigges.kaboofcasino.machine.MachineSettings;
import de.tigges.kaboofcasino.machine.MachineType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * The croupier's-hand dialogs (games.md 20.5) for vanilla clients (PLAN decision 6): the parts of the mod's settings
 * window that this port has (Jack's partial port: name, win chance or bias, copy/paste, the type values and
 * "breakable in survival"; no bet lists, no payouts, no machine list), in the mod's texts and colours.
 *
 * <p>Machine (use on a machine): title, the subtitle ({@code subtitle()}: name, automatic name, position, dimension),
 * the chance tab's figures for the saved values (natural chance and average return, or the bias estimates), then the
 * inputs "Name der Maschine", "Wie das Casino: ..." (follow the type) and the slider (win chance 0..100 % or the bias
 * -100 dealer .. +100 player), buttons "Speichern", "Kopieren", "Einfügen" (with a copy in the hand),
 * "Zurücksetzen" (with own values; click twice); exit "Fertig".
 *
 * <p>Whole casino (use in the air): the average return of each game at its saved value, then per game "normal"
 * plus a slider (win chance) or a slider (blackjack and poker bias), and the protection checkbox; buttons
 * "Speichern", "Alles normal" (when a game is off its natural odds; click twice), "Kopie vergessen" (with a copy);
 * exit "Fertig".
 *
 * <p>A dialog cannot change while it is open, so "Kopiert!", "Eingefügt!" and "Sicher?" show in the dialog the
 * server sends back after the click. Pure.
 */
final class AdminDialogs {

    static final int WIDTH = 300;
    static final int BUTTON_WIDTH = 148;
    static final int EXIT_WIDTH = 200;
    static final String PERCENT_FORMAT = "options.percent_value";
    static final String VALUE_FORMAT = "options.generic_value";

    /** The mod's colours ({@code CasinoSettingsScreen}). */
    static final TextColor TITLE = TextColor.color(0xFFDE7C);
    static final TextColor CREAM = TextColor.color(0xCDD6C4);
    static final TextColor MUTED = TextColor.color(0x92AB98);
    static final TextColor GOOD = TextColor.color(0x8BD39B);
    static final TextColor WARN = TextColor.color(0xFFB35E);

    /**
     * A machine's dialog data.
     *
     * @param typeValue  the type's value in config.yml (win chance -1..100, or the bias)
     * @param world      the world key ({@code minecraft:the_nether} shows "Nether")
     * @param confirming the two-click action waiting for its second click, or null
     * @param copied     "Kopiert!" on the copy button (the click before was Copy)
     * @param pasted     "Eingefügt!" on the paste button
     */
    record MachineData(MachineType type, int number, MachineSettings.Own own, int typeValue, int x, int y, int z,
                       String world, boolean hasCopy, AdminClick.Kind confirming, boolean copied, boolean pasted) {
    }

    /**
     * The whole casino's dialog data.
     *
     * @param values per type: win chance -1..100 or the bias
     */
    record WorldData(Map<MachineType, Integer> values, boolean breakable, boolean hasCopy,
                     AdminClick.Kind confirming) {
        WorldData {
            values = Map.copyOf(values);
        }
    }

    private AdminDialogs() {
    }

    static AdminView machine(MachineData d) {
        MachineType type = d.type();
        boolean bias = MachineSettings.usesBias(type);
        MachineSettings.Own own = d.own();
        List<Text> body = new ArrayList<>();
        body.add(text(subtitle(d).color(CREAM)));
        List<Input> inputs = new ArrayList<>();
        inputs.add(new TextInput(AdminClick.NAME, AdminTexts.settings("name"), own.name(), MachineSettings.MAX_NAME,
                WIDTH));
        if (bias) {
            int effective = MachineSettings.effectiveBias(own.bias(), d.typeValue());
            body.add(text(AdminTexts.settings("help_chance_blackjack").color(MUTED)));
            double back = AdminOdds.estimatedReturn(type, effective);
            body.add(text(lines(
                    AdminTexts.settings("bias_wins", Component.text(AdminOdds.about(AdminOdds.estimatedWins(type,
                            effective))), Component.text(AdminOdds.about(AdminOdds.estimatedWins(type, 0))))
                            .color(CREAM),
                    AdminTexts.settings("bias_return", Component.text(AdminOdds.about(back))).color(returnColour(back)),
                    (back > 1.0 ? AdminTexts.settings("creates_items") : AdminTexts.settings("blackjack_rules"))
                            .color(back > 1.0 ? WARN : MUTED))));
            body.add(text(AdminTexts.settings(type == MachineType.POKER_TABLE ? "bias_estimate_help_poker"
                    : "bias_estimate_help").color(MUTED)));
            inputs.add(new BoolInput(AdminClick.LIKE_CASINO, AdminTexts.settings("like_casino_value",
                    biasShort(d.typeValue())), !own.ownBias()));
            inputs.add(new RangeInput(AdminClick.VALUE, sides(), VALUE_FORMAT, MachineSettings.MIN_BIAS,
                    MachineSettings.MAX_BIAS, slider(type, own, d.typeValue()), WIDTH));
        } else {
            int effective = MachineSettings.effectiveChance(own.chance(), d.typeValue());
            body.add(text(AdminTexts.settings("like_casino_chance_help").color(MUTED)));
            double expected = AdminOdds.expectedReturn(type, effective);
            List<Component> figures = new ArrayList<>();
            figures.add(AdminTexts.settings("natural", Component.text(AdminOdds.percent(AdminOdds.naturalChance(type))))
                    .color(MUTED));
            figures.add(AdminTexts.settings("return", Component.text(AdminOdds.percent(expected * 100.0)))
                    .color(returnColour(expected)));
            if (expected > 1.0) {
                figures.add(AdminTexts.settings("creates_items").color(WARN));
            }
            body.add(text(lines(figures.toArray(Component[]::new))));
            if (type == MachineType.ROULETTE_TABLE) {
                body.add(text(AdminTexts.settings("roulette_green").color(MUTED)));
            } else if (type == MachineType.DUCK_DERBY) {
                body.add(text(AdminTexts.settings("derby_group").color(MUTED)));
            }
            Component follows = d.typeValue() < 0
                    ? AdminTexts.settings("like_casino_normal", Component.text(AdminOdds.percent(
                            AdminOdds.naturalChance(type))))
                    : AdminTexts.settings("like_casino_value", Component.text(AdminOdds.pct(d.typeValue())));
            inputs.add(new BoolInput(AdminClick.LIKE_CASINO, follows, !own.ownChance()));
            inputs.add(new RangeInput(AdminClick.VALUE, AdminTexts.settings("chance"), PERCENT_FORMAT, 0, 100,
                    slider(type, own, d.typeValue()), WIDTH));
        }
        Component automatic = AdminTexts.displayName(type, "", d.number());
        body.add(text(lines(AdminTexts.settings("automatic_name", automatic), AdminTexts.settings("name_help"))
                .color(MUTED)));
        List<Button> buttons = new ArrayList<>();
        buttons.add(button(AdminTexts.settings("save"), null, AdminClick.Kind.SAVE));
        buttons.add(button(AdminTexts.settings(d.copied() ? "copied" : "copy"), AdminTexts.settings("copy_help"),
                AdminClick.Kind.COPY));
        if (d.hasCopy()) {
            buttons.add(button(AdminTexts.settings(d.pasted() ? "pasted" : "paste"), AdminTexts.settings("copy_help"),
                    AdminClick.Kind.PASTE));
        }
        if (own.ownChance() || own.ownBias()) {
            buttons.add(button(AdminTexts.settings(d.confirming() == AdminClick.Kind.RESET ? "confirm"
                    : "reset_machine"), AdminTexts.settings("reset_machine_help"), AdminClick.Kind.RESET));
        }
        return new AdminView(title(), body, inputs, buttons, exit());
    }

    static AdminView world(WorldData d) {
        List<Text> body = new ArrayList<>();
        body.add(text(AdminTexts.settings("everything").append(Component.text(" · "))
                .append(AdminTexts.settings("everything_subtitle")).color(CREAM)));
        List<Component> stats = new ArrayList<>();
        List<Input> inputs = new ArrayList<>();
        boolean offNatural = false;
        for (MachineType type : MachineSettings.MOD_ORDER) {
            int value = d.values().get(type);
            Component game = AdminTexts.typeName(type);
            if (MachineSettings.usesBias(type)) {
                offNatural |= value != 0;
                double back = AdminOdds.estimatedReturn(type, value);
                stats.add(AdminTexts.settings(type == MachineType.POKER_TABLE ? "stats_poker" : "stats_blackjack",
                        Component.text(AdminOdds.about(AdminOdds.estimatedWins(type, value))),
                        Component.text(AdminOdds.about(back))).color(returnColour(back)));
                inputs.add(new RangeInput(AdminClick.biasKey(type), game, VALUE_FORMAT, MachineSettings.MIN_BIAS,
                        MachineSettings.MAX_BIAS, value, WIDTH));
            } else {
                offNatural |= value >= 0;
                double expected = AdminOdds.expectedReturn(type, value);
                stats.add(AdminTexts.settings("stats_type", game,
                        Component.text(AdminOdds.percent(expected * 100.0))).color(returnColour(expected)));
                inputs.add(new BoolInput(AdminClick.naturalKey(type), AdminTexts.settings("type_slider_normal", game,
                        Component.text(AdminOdds.percent(AdminOdds.naturalChance(type)))), value < 0));
                inputs.add(new RangeInput(AdminClick.chanceKey(type), game, PERCENT_FORMAT, 0, 100,
                        typeSlider(type, value), WIDTH));
            }
        }
        body.add(text(lines(stats.toArray(Component[]::new))));
        body.add(text(lines(AdminTexts.settings("roulette_green"), AdminTexts.settings("derby_group")).color(MUTED)));
        body.add(text(AdminTexts.settings("breakable_help").color(MUTED)));
        inputs.add(new BoolInput(AdminClick.BREAKABLE, AdminTexts.settings("help_protection"), d.breakable()));
        List<Button> buttons = new ArrayList<>();
        buttons.add(button(AdminTexts.settings("save"), null, AdminClick.Kind.SAVE));
        if (offNatural) {
            buttons.add(button(AdminTexts.settings(d.confirming() == AdminClick.Kind.ALL_NORMAL ? "confirm"
                    : "all_normal"), AdminTexts.settings("all_normal_help"), AdminClick.Kind.ALL_NORMAL));
        }
        if (d.hasCopy()) {
            buttons.add(button(AdminTexts.settings("forget_copy"), AdminTexts.settings("forget_copy_help"),
                    AdminClick.Kind.FORGET));
        }
        return new AdminView(title(), body, inputs, buttons, exit());
    }

    /**
     * The machine slider's position: its own value, else what it follows ({@code machineValue}: the type's value, for
     * a win chance at natural odds the rounded natural chance).
     */
    static int slider(MachineType type, MachineSettings.Own own, int typeValue) {
        if (MachineSettings.usesBias(type)) {
            return MachineSettings.effectiveBias(own.bias(), typeValue);
        }
        return typeSlider(type, MachineSettings.effectiveChance(own.chance(), typeValue));
    }

    /** A game's slider in the whole casino's dialog ({@code typeValue}: natural odds show the rounded natural chance). */
    static int typeSlider(MachineType type, int value) {
        return MachineSettings.usesBias(type) || value >= 0 ? value : AdminOdds.naturalValue(type);
    }

    /**
     * The machine's settings after its dialog: the typed name; for the value the mod's slider semantics (moving the
     * slider is the edit): unticked "Wie das Casino" or a slider moved away from where the dialog put it gives an own
     * value, ticked (and the slider left alone, or the box newly ticked) follows the type again.
     */
    static MachineSettings.Own resolve(MachineType type, MachineSettings.Own old, int typeValue,
                                       AdminClick.MachineInputs in) {
        boolean bias = MachineSettings.usesBias(type);
        boolean followed = bias ? !old.ownBias() : !old.ownChance();
        boolean moved = in.value() != slider(type, old, typeValue);
        boolean follow = in.likeCasino() && !(followed && moved);
        MachineSettings.Own next = old.withName(in.name());
        if (bias) {
            return next.withBias(follow ? MachineSettings.INHERIT_BIAS : in.value());
        }
        return next.withChance(follow ? MachineSettings.INHERIT_CHANCE : in.value());
    }

    /**
     * The games' type values after the whole casino's dialog, with the same rule per win chance: "normal" ticked gives
     * natural odds (-1) unless it was ticked already and the slider was moved; the biases are their sliders.
     */
    static Map<MachineType, Integer> resolve(Map<MachineType, Integer> old, AdminClick.WorldInputs in) {
        Map<MachineType, Integer> out = new java.util.EnumMap<>(MachineType.class);
        for (MachineType type : MachineSettings.MOD_ORDER) {
            int slider = in.sliders().get(type);
            if (MachineSettings.usesBias(type)) {
                out.put(type, slider);
                continue;
            }
            int before = old.get(type);
            boolean moved = slider != typeSlider(type, before);
            boolean natural = in.natural().contains(type) && !(before < 0 && moved);
            out.put(type, natural ? MachineSettings.INHERIT_CHANCE : slider);
        }
        return out;
    }

    /** {@code subtitle()}: name [· automatic name] · x, y, z [· dimension]. */
    static Component subtitle(MachineData d) {
        Component line = AdminTexts.displayName(d.type(), d.own().name(), d.number());
        if (!d.own().name().isBlank()) {
            line = line.append(Component.text(" · ")).append(AdminTexts.displayName(d.type(), "", d.number()));
        }
        line = line.append(Component.text(" · " + d.x() + ", " + d.y() + ", " + d.z()));
        Component dimension = switch (d.world()) {
            case "", "minecraft:overworld" -> null;
            case "minecraft:the_nether" -> AdminTexts.settings("dimension_nether");
            case "minecraft:the_end" -> AdminTexts.settings("dimension_end");
            default -> Component.text(d.world());
        };
        return dimension == null ? line : line.append(Component.text(" · ")).append(dimension);
    }

    /** {@code biasShort}: "normal", "Croupier +20%", "Spieler +20%". */
    static Component biasShort(int bias) {
        if (bias == 0) {
            return AdminTexts.settings("bias_short_normal");
        }
        return AdminTexts.settings(bias < 0 ? "bias_short_dealer" : "bias_short_player",
                Component.text(AdminOdds.pct(Math.abs(bias))));
    }

    /** The bias slider's label: the mod's captions under its bar, "Croupier ← Normal → Spieler". */
    static Component sides() {
        return AdminTexts.settings("favour_dealer").append(Component.text(" ← "))
                .append(AdminTexts.settings("bias_normal")).append(Component.text(" → "))
                .append(AdminTexts.settings("favour_player"));
    }

    private static TextColor returnColour(double value) {
        return value > 1.0 ? WARN : GOOD;
    }

    private static Component title() {
        return AdminTexts.settings("title").color(TITLE).decorate(TextDecoration.BOLD);
    }

    private static Button exit() {
        return new Button(AdminTexts.vanilla("gui.done"), null, EXIT_WIDTH, AdminClick.Kind.DONE);
    }

    private static Button button(Component label, Component tooltip, AdminClick.Kind action) {
        return new Button(label, tooltip, BUTTON_WIDTH, action);
    }

    private static Text text(Component c) {
        return new Text(c, WIDTH);
    }

    private static Component lines(Component... parts) {
        Component out = Component.empty();
        for (int i = 0; i < parts.length; i++) {
            out = out.append(i == 0 ? parts[i] : Component.newline().append(parts[i]));
        }
        return out;
    }
}
