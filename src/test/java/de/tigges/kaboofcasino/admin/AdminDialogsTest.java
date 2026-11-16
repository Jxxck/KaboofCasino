package de.tigges.kaboofcasino.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.admin.AdminClick.Form;
import de.tigges.kaboofcasino.admin.AdminDialogs.MachineData;
import de.tigges.kaboofcasino.admin.AdminDialogs.WorldData;
import de.tigges.kaboofcasino.machine.MachineSettings;
import de.tigges.kaboofcasino.machine.MachineType;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The dialogs' structure (snapshots of a German client without the pack's settings keys), and that each dialog's
 * initial values, sent back unchanged, parse as exactly the saved state (a "Fertig" without edits changes nothing).
 */
class AdminDialogsTest {

    private static MachineData slot(MachineSettings.Own own, int typeValue, boolean hasCopy, AdminClick.Kind confirm,
                                    boolean copied, boolean pasted) {
        return new MachineData(MachineType.SLOT_MACHINE, 3, own, typeValue, 12, 64, -5, "minecraft:overworld", hasCopy,
                confirm, copied, pasted);
    }

    private static Map<MachineType, Integer> natural() {
        Map<MachineType, Integer> values = new EnumMap<>(MachineType.class);
        for (MachineType t : MachineSettings.MOD_ORDER) {
            values.put(t, MachineSettings.usesBias(t) ? 0 : -1);
        }
        return values;
    }

    @Tag("german")
    @Test
    void slotMachineFollowingItsType() {
        String expected = """
                title MASCHINEN-EINSTELLUNGEN
                text Spielautomat #3 · 12, 64, -5
                text Die Maschine folgt der Chance ihres Spiels, eingestellt unter „Ganzes Casino“ › Chance.
                text Normale Gewinnchance: 27.3% / Mittlere Auszahlung: 97.5%
                text Automatischer Name: Spielautomat #3 / Ein leeres Feld stellt den automatischen Namen wieder her.
                text-input name [Name der Maschine] '' max 64
                checkbox like_casino [Wie das Casino: normal (27.3%)] true
                slider value [Chance] options.percent_value 0..100 = 27
                button Speichern -> {do:"save"}
                button Kopieren -> {do:"copy"} (Kopieren speichert die Einstellungen dieser Maschine in der Hand; \
                Einfügen überträgt sie hierher. Schleichen + Rechtsklick auf eine andere Maschine fügt sie dort ein.)
                exit {gui.done} -> {do:"done"}""";
        assertEquals(expected, AdminFixtures.describe(AdminDialogs.machine(slot(MachineSettings.Own.NONE, -1, false,
                null, false, false))));
    }

    @Tag("german")
    @Test
    void namedSlotMachineWithItsOwnChanceACopyAndAResetQuestion() {
        String expected = """
                title MASCHINEN-EINSTELLUNGEN
                text Lucky · Spielautomat #3 · 12, 64, -5
                text Die Maschine folgt der Chance ihres Spiels, eingestellt unter „Ganzes Casino“ › Chance.
                text Normale Gewinnchance: 27.3% / Mittlere Auszahlung: 142.6% / Erzeugt mehr Gegenstände als eingesetzt
                text Automatischer Name: Spielautomat #3 / Ein leeres Feld stellt den automatischen Namen wieder her.
                text-input name [Name der Maschine] 'Lucky' max 64
                checkbox like_casino [Wie das Casino: 30%] false
                slider value [Chance] options.percent_value 0..100 = 40
                button Speichern -> {do:"save"}
                button Kopiert! -> {do:"copy"} (Kopieren speichert die Einstellungen dieser Maschine in der Hand; \
                Einfügen überträgt sie hierher. Schleichen + Rechtsklick auf eine andere Maschine fügt sie dort ein.)
                button Einfügen -> {do:"paste"} (Kopieren speichert die Einstellungen dieser Maschine in der Hand; \
                Einfügen überträgt sie hierher. Schleichen + Rechtsklick auf eine andere Maschine fügt sie dort ein.)
                button Sicher? -> {do:"reset"} (Löscht alle eigenen Einstellungen dieser Maschine: Sie folgt wieder in \
                allem dem Casino. Zweimal klicken.)
                exit {gui.done} -> {do:"done"}""";
        assertEquals(expected, AdminFixtures.describe(AdminDialogs.machine(slot(new MachineSettings.Own("Lucky", 40,
                MachineSettings.INHERIT_BIAS), 30, true, AdminClick.Kind.RESET, true, false))));
    }

    @Tag("german")
    @Test
    void blackjackTableInTheNether() {
        String expected = """
                title MASCHINEN-EINSTELLUNGEN
                text Blackjack-Tisch #1 · 0, 70, 0 · Nether
                text Welche Seite die Karten dieses Tisches bevorzugen. In der Mitte: normal.
                text Der Spieler gewinnt etwa <A> der Runden (normal: <B>) / Geschätzte mittlere Auszahlung: <C> / \
                Kartenschlitten, Aktionen und Auszahlungen bleiben unverändert.
                text Gemessen über 400 000 Runden je Einstellung, der Spieler mit der besten Strategie: Wer schlechter \
                spielt, gewinnt weniger. Nur in der Hand des Croupiers sichtbar.
                text Automatischer Name: Blackjack-Tisch #1 / Ein leeres Feld stellt den automatischen Namen wieder her.
                text-input name [Name der Maschine] '' max 64
                checkbox like_casino [Wie das Casino: Croupier +20%] true
                slider value [Croupier ← Normal → Spieler] options.generic_value -100..100 = -20
                button Speichern -> {do:"save"}
                button Kopieren -> {do:"copy"} (Kopieren speichert die Einstellungen dieser Maschine in der Hand; \
                Einfügen überträgt sie hierher. Schleichen + Rechtsklick auf eine andere Maschine fügt sie dort ein.)
                exit {gui.done} -> {do:"done"}"""
                .replace("<A>", AdminOdds.about(AdminOdds.estimatedWins(MachineType.BLACKJACK_TABLE, -20)))
                .replace("<B>", AdminOdds.about(AdminOdds.estimatedWins(MachineType.BLACKJACK_TABLE, 0)))
                .replace("<C>", AdminOdds.about(AdminOdds.estimatedReturn(MachineType.BLACKJACK_TABLE, -20)));
        assertEquals(expected, AdminFixtures.describe(AdminDialogs.machine(new MachineData(MachineType.BLACKJACK_TABLE,
                1, MachineSettings.Own.NONE, -20, 0, 70, 0, "minecraft:the_nether", false, null, false, false))));
    }

    @Tag("german")
    @Test
    void rouletteAndDerbyCarryTheirNotes() {
        String roulette = AdminFixtures.describe(AdminDialogs.machine(new MachineData(MachineType.ROULETTE_TABLE, 2,
                MachineSettings.Own.NONE, -1, 0, 0, 0, "minecraft:the_end", false, null, false, false)));
        assertTrue(roulette.contains("text Casino-Roulette #2 · 0, 0, 0 · Ende\n"), roulette);
        assertTrue(roulette.contains("text Normale Gewinnchance: 48.6% / Mittlere Auszahlung: 97.3%\n"), roulette);
        assertTrue(roulette.contains("text Das ist die Gewinnchance für Rot/Schwarz. Grün gewinnt mit einem "
                + "Achtzehntel dieser Chance.\n"), roulette);
        assertTrue(roulette.contains("slider value [Chance] options.percent_value 0..100 = 49\n"), roulette);
        String derby = AdminFixtures.describe(AdminDialogs.machine(new MachineData(MachineType.DUCK_DERBY, 1,
                new MachineSettings.Own("", 25, MachineSettings.INHERIT_BIAS), -1, 0, 0, 0, "", false, null, false,
                false)));
        assertTrue(derby.contains("text Gruppen nutzen natürliche Chancen (1 zu 4). Andere Werte gelten nur solo.\n"),
                derby);
        assertTrue(derby.contains("text Normale Gewinnchance: 25.0% / Mittlere Auszahlung: 100.0%\n"), derby);
        assertTrue(derby.contains("button Zurücksetzen -> {do:\"reset\"}"), derby);
    }

    @Tag("german")
    @Test
    void theWholeCasino() {
        Map<MachineType, Integer> values = natural();
        values.put(MachineType.FORTUNE_WHEEL, 60);
        values.put(MachineType.POKER_TABLE, 15);
        String expected = """
                title MASCHINEN-EINSTELLUNGEN
                text Ganzes Casino · Einstellungen, denen alle Maschinen folgen
                text Casino-Roulette: mittlere Auszahlung 97.3% / Blackjack: Spieler gewinnt ~<A> · Auszahlung ~<B> / \
                Poker: Spieler gewinnt ~<C> · Auszahlung ~<D> / Entenrennen: mittlere Auszahlung 100.0% / \
                Spielautomat: mittlere Auszahlung 97.5% / Glücksrad: mittlere Auszahlung <E> / \
                Casino-Plinko: mittlere Auszahlung 97.5%
                text Das ist die Gewinnchance für Rot/Schwarz. Grün gewinnt mit einem Achtzehntel dieser Chance. / \
                Gruppen nutzen natürliche Chancen (1 zu 4). Andere Werte gelten nur solo.
                text Gilt für alle sechs Casinomaschinen. Im Kreativmodus bleiben sie abbaubar.
                checkbox roulette_natural [Casino-Roulette: normal (48.6%)] true
                slider roulette_chance [Casino-Roulette] options.percent_value 0..100 = 49
                slider blackjack_bias [Blackjack-Tisch] options.generic_value -100..100 = 0
                slider poker_bias [Pokertisch] options.generic_value -100..100 = 15
                checkbox derby_natural [Entenrennen: normal (25.0%)] true
                slider derby_chance [Entenrennen] options.percent_value 0..100 = 25
                checkbox slots_natural [Spielautomat: normal (27.3%)] true
                slider slots_chance [Spielautomat] options.percent_value 0..100 = 27
                checkbox wheel_natural [Glücksrad: normal (43.8%)] false
                slider wheel_chance [Glücksrad] options.percent_value 0..100 = 60
                checkbox plinko_natural [Casino-Plinko: normal (28.9%)] true
                slider plinko_chance [Casino-Plinko] options.percent_value 0..100 = 29
                checkbox breakable [Dürfen Spieler die Maschinen des Casinos im Überlebensmodus abbauen?] false
                button Speichern -> {do:"save"}
                button Alles normal -> {do:"all_normal"} (Alle Spiele zurück auf ihre normale Chance; Maschinen mit \
                eigener Einstellung behalten sie. Zweimal klicken.)
                button Kopie vergessen -> {do:"forget"} (Vergisst die Einstellungen einer Maschine, die die Hand \
                kopiert hat.)
                exit {gui.done} -> {do:"done"}"""
                .replace("<A>", AdminOdds.about(AdminOdds.estimatedWins(MachineType.BLACKJACK_TABLE, 0)))
                .replace("<B>", AdminOdds.about(AdminOdds.estimatedReturn(MachineType.BLACKJACK_TABLE, 0)))
                .replace("<C>", AdminOdds.about(AdminOdds.estimatedWins(MachineType.POKER_TABLE, 15)))
                .replace("<D>", AdminOdds.about(AdminOdds.estimatedReturn(MachineType.POKER_TABLE, 15)))
                .replace("<E>", AdminOdds.percent(AdminOdds.expectedReturn(MachineType.FORTUNE_WHEEL, 60) * 100));
        assertEquals(expected, AdminFixtures.describe(AdminDialogs.world(new WorldData(values, false, true, null))));
        // all natural: no "Alles normal"; the question after its first click
        String plain = AdminFixtures.describe(AdminDialogs.world(new WorldData(natural(), true, false, null)));
        assertTrue(!plain.contains("all_normal") && !plain.contains("forget"), plain);
        String asking = AdminFixtures.describe(AdminDialogs.world(new WorldData(values, true, false,
                AdminClick.Kind.ALL_NORMAL)));
        assertTrue(asking.contains("button Sicher? -> {do:\"all_normal\"}"), asking);
    }

    /** The initial values a dialog shows, sent back as they are. */
    private static Optional<AdminClick> echo(AdminView view, Form form) {
        Map<String, Object> values = new HashMap<>();
        values.put("do", "done");
        for (AdminView.Input in : view.inputs()) {
            switch (in) {
                case AdminView.TextInput t -> values.put(t.key(), t.initial());
                case AdminView.BoolInput b -> values.put(b.key(), b.initial());
                case AdminView.RangeInput r -> values.put(r.key(), (float) r.initial());
            }
        }
        return AdminClick.parse(form, k -> values.get(k) instanceof String s ? s : null,
                k -> values.get(k) instanceof Float f ? f : null, k -> values.get(k) instanceof Boolean b ? b : null);
    }

    @Test
    void untouchedDialogsChangeNothing() {
        MachineSettings.Own[] owns = {MachineSettings.Own.NONE, new MachineSettings.Own("Tisch 4", 0, -100),
                new MachineSettings.Own("x", 100, 100), new MachineSettings.Own("", -1, 35)};
        for (MachineType type : MachineSettings.MOD_ORDER) {
            for (MachineSettings.Own own : owns) {
                for (int typeValue : MachineSettings.usesBias(type) ? new int[] {-100, 0, 40} : new int[] {-1, 0, 77}) {
                    AdminView view = AdminDialogs.machine(new MachineData(type, 9, own, typeValue, 1, 2, 3, "", true,
                            null, false, false));
                    AdminClick.MachineInputs in = (AdminClick.MachineInputs) echo(view, Form.of(type)).orElseThrow()
                            .inputs().orElseThrow();
                    assertEquals(own, AdminDialogs.resolve(type, own, typeValue, in), type + " " + own + " "
                            + typeValue);
                }
            }
        }
        Map<MachineType, Integer> values = natural();
        values.put(MachineType.ROULETTE_TABLE, 0);
        values.put(MachineType.PLINKO, 100);
        values.put(MachineType.BLACKJACK_TABLE, -100);
        AdminClick.WorldInputs in = (AdminClick.WorldInputs) echo(AdminDialogs.world(new WorldData(values, false,
                false, null)), Form.WORLD).orElseThrow().inputs().orElseThrow();
        assertEquals(values, AdminDialogs.resolve(values, in));
        assertEquals(false, in.breakable());
    }

    /** As in the mod, moving a slider is the edit; ticking "Wie das Casino"/"normal" goes back to the type. */
    @Test
    void movingTheSliderSetsAnOwnValue() {
        MachineSettings.Own none = MachineSettings.Own.NONE;
        MachineType slot = MachineType.SLOT_MACHINE;
        // following natural odds, the slider shows 27: moved -> own value even with the box still ticked
        assertEquals(none.withChance(40), AdminDialogs.resolve(slot, none, -1,
                new AdminClick.MachineInputs("", true, 40)));
        assertEquals(none, AdminDialogs.resolve(slot, none, -1, new AdminClick.MachineInputs("", true, 27)));
        // the box unticked: whatever the slider shows
        assertEquals(none.withChance(27), AdminDialogs.resolve(slot, none, -1,
                new AdminClick.MachineInputs("", false, 27)));
        // an own value: ticking the box follows the type again, slider moved or not
        MachineSettings.Own own = none.withChance(60);
        assertEquals(none, AdminDialogs.resolve(slot, own, 30, new AdminClick.MachineInputs("", true, 60)));
        assertEquals(none, AdminDialogs.resolve(slot, own, 30, new AdminClick.MachineInputs("", true, 10)));
        assertEquals(none.withChance(10), AdminDialogs.resolve(slot, own, 30,
                new AdminClick.MachineInputs("", false, 10)));
        // the bias: following a type bias of -20
        MachineType bj = MachineType.BLACKJACK_TABLE;
        assertEquals(none, AdminDialogs.resolve(bj, none, -20, new AdminClick.MachineInputs("", true, -20)));
        assertEquals(none.withBias(0), AdminDialogs.resolve(bj, none, -20, new AdminClick.MachineInputs("", true,
                0)));
        assertEquals(none.withBias(-20).withName("T"), AdminDialogs.resolve(bj, none, -20,
                new AdminClick.MachineInputs("T", false, -20)));

        Map<MachineType, Integer> old = natural();
        old.put(MachineType.FORTUNE_WHEEL, 60);
        Map<MachineType, Integer> sliders = new EnumMap<>(MachineType.class);
        for (MachineType t : MachineSettings.MOD_ORDER) {
            sliders.put(t, AdminDialogs.typeSlider(t, old.get(t)));
        }
        sliders.put(MachineType.SLOT_MACHINE, 35); // moved while "normal" stays ticked
        sliders.put(MachineType.POKER_TABLE, -5);
        java.util.Set<MachineType> ticked = java.util.EnumSet.of(MachineType.ROULETTE_TABLE, MachineType.DUCK_DERBY,
                MachineType.SLOT_MACHINE, MachineType.PLINKO, MachineType.FORTUNE_WHEEL); // wheel newly ticked
        Map<MachineType, Integer> expected = natural();
        expected.put(MachineType.SLOT_MACHINE, 35);
        expected.put(MachineType.POKER_TABLE, -5);
        assertEquals(expected, AdminDialogs.resolve(old, new AdminClick.WorldInputs(sliders, ticked, true)));
    }
}
