package de.tigges.kaboofcasino.admin;

import de.tigges.kaboofcasino.machine.MachineType;
import java.util.LinkedHashMap;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;

/**
 * The texts of the croupier's hand: the mod's lang keys ({@code gui.roulettecasino.settings.*},
 * {@code message.roulettecasino.croupier_hand.*}, {@code item.roulettecasino.croupier_hand.*}, the machines'
 * {@code block.roulettecasino.*} names) as translatable components with the mod's German text ({@code de_de.json}) as
 * fallback, so the client's language applies wherever the pack ships the key (the guide's {@code item.} and
 * {@code block.} keys) and German everywhere else ({@code AdminTextsTest} compares every fallback with the mod jar).
 * Vanilla keys ({@code gui.done}, {@code options.percent_value}) come from the client. Pure.
 */
public final class AdminTexts {

    static final String S = "gui.roulettecasino.settings.";
    static final String M = "message.roulettecasino.croupier_hand.";
    static final String I = "item.roulettecasino.croupier_hand.";

    /** Key -> the mod's German text. */
    static final Map<String, String> GERMAN = new LinkedHashMap<>();

    static {
        de(S + "title", "MASCHINEN-EINSTELLUNGEN");
        de(S + "chance", "Chance");
        de(S + "save", "Speichern");
        de(S + "copy", "Kopieren");
        de(S + "copied", "Kopiert!");
        de(S + "paste", "Einfügen");
        de(S + "pasted", "Eingefügt!");
        de(S + "confirm", "Sicher?");
        de(S + "copy_help", "Kopieren speichert die Einstellungen dieser Maschine in der Hand; Einfügen überträgt sie "
                + "hierher. Schleichen + Rechtsklick auf eine andere Maschine fügt sie dort ein.");
        de(S + "reset_machine", "Zurücksetzen");
        de(S + "reset_machine_help", "Löscht alle eigenen Einstellungen dieser Maschine: Sie folgt wieder in allem dem "
                + "Casino. Zweimal klicken.");
        de(S + "forget_copy", "Kopie vergessen");
        de(S + "forget_copy_help", "Vergisst die Einstellungen einer Maschine, die die Hand kopiert hat.");
        de(S + "all_normal", "Alles normal");
        de(S + "all_normal_help", "Alle Spiele zurück auf ihre normale Chance; Maschinen mit eigener Einstellung "
                + "behalten sie. Zweimal klicken.");
        de(S + "everything", "Ganzes Casino");
        de(S + "everything_subtitle", "Einstellungen, denen alle Maschinen folgen");
        de(S + "dimension_nether", "Nether");
        de(S + "dimension_end", "Ende");
        de(S + "name", "Name der Maschine");
        de(S + "automatic_name", "Automatischer Name: %s");
        de(S + "name_help", "Ein leeres Feld stellt den automatischen Namen wieder her.");
        de(S + "help_name", "Ihr Name in dieser Liste und über ihr, solange du die Hand des Croupiers hältst.");
        de(S + "help_chance_machine", "Die Gewinnchance nur dieser Maschine. Ziehe den Balken oder tippe eine Zahl.");
        de(S + "help_chance_blackjack", "Welche Seite die Karten dieses Tisches bevorzugen. In der Mitte: normal.");
        de(S + "help_chance_world", "Die Gewinnchance jedes Spiels für alle seine Maschinen. Ziehe einen Balken oder "
                + "tippe eine Zahl.");
        de(S + "like_casino_value", "Wie das Casino: %s");
        de(S + "like_casino_normal", "Wie das Casino: normal (%s)");
        de(S + "like_casino_chance_help", "Die Maschine folgt der Chance ihres Spiels, eingestellt unter „Ganzes "
                + "Casino“ › Chance.");
        de(S + "natural", "Normale Gewinnchance: %s");
        de(S + "return", "Mittlere Auszahlung: %s");
        de(S + "return_help", "Mittlere Auszahlung pro Einsatz. Über 100 % erhalten Spieler mehr als sie einsetzen: Die "
                + "Maschine erzeugt Gegenstände.");
        de(S + "creates_items", "Erzeugt mehr Gegenstände als eingesetzt");
        de(S + "roulette_green", "Das ist die Gewinnchance für Rot/Schwarz. Grün gewinnt mit einem Achtzehntel dieser "
                + "Chance.");
        de(S + "derby_group", "Gruppen nutzen natürliche Chancen (1 zu 4). Andere Werte gelten nur solo.");
        de(S + "favour_dealer", "Croupier");
        de(S + "bias_normal", "Normal");
        de(S + "favour_player", "Spieler");
        de(S + "bias_help", "Welche Seite die Karten begünstigen. Bei jeder Karte gibt der Croupier mit der unten "
                + "eingestellten Stärke von den zwei obersten Karten des Schlittens die, die dieser Seite nützt; die "
                + "andere bleibt oben. Regeln, Aktionen und Auszahlungen ändern sich nicht.");
        de(S + "bias_wins", "Der Spieler gewinnt etwa %s der Runden (normal: %s)");
        de(S + "bias_return", "Geschätzte mittlere Auszahlung: %s");
        de(S + "bias_estimate_help", "Gemessen über 400 000 Runden je Einstellung, der Spieler mit der besten "
                + "Strategie: Wer schlechter spielt, gewinnt weniger. Nur in der Hand des Croupiers sichtbar.");
        de(S + "bias_estimate_help_poker", "Gemessen über 40 000 Runden je Einstellung, der Spieler geht mit einem "
                + "Paar, einem Draw oder einer Karte ab der Zehn mit: Wer schlechter spielt, gewinnt weniger. Nur in "
                + "der Hand des Croupiers sichtbar.");
        de(S + "blackjack_rules", "Kartenschlitten, Aktionen und Auszahlungen bleiben unverändert.");
        de(S + "bias_short_normal", "normal");
        de(S + "bias_short_dealer", "Croupier +%s");
        de(S + "bias_short_player", "Spieler +%s");
        de(S + "type_slider_normal", "%s: normal (%s)");
        de(S + "stats_type", "%s: mittlere Auszahlung %s");
        de(S + "stats_blackjack", "Blackjack: Spieler gewinnt ~%s · Auszahlung ~%s");
        de(S + "stats_poker", "Poker: Spieler gewinnt ~%s · Auszahlung ~%s");
        de(S + "protection", "Schutz");
        de(S + "help_protection", "Dürfen Spieler die Maschinen des Casinos im Überlebensmodus abbauen?");
        de(S + "breakable_help", "Gilt für alle sechs Casinomaschinen. Im Kreativmodus bleiben sie abbaubar.");
        de(M + "creative_only", "Die Hand des Croupiers funktioniert nur im Kreativmodus.");
        de(M + "pasted", "Einstellungen auf %s eingefügt");
        de(M + "nothing_to_paste", "Keine Kopie in der Hand: Öffne damit eine Maschine und klicke auf Kopieren.");
        de(M + "copy_forgotten", "Kopierte Einstellungen vergessen.");
        de(I + "copied", "Kopierte Einstellungen: %s (%s, %s, %s)");
        de(I + "copied_help", "Schleichen + Rechtsklick auf eine Maschine: einfügen · in die Luft: vergessen");
        de("block.roulettecasino.roulette_table", "Casino-Roulette");
        de("block.roulettecasino.blackjack_table", "Blackjack-Tisch");
        de("block.roulettecasino.poker_table", "Pokertisch");
        de("block.roulettecasino.duck_derby", "Entenrennen");
        de("block.roulettecasino.slot_machine", "Spielautomat");
        de("block.roulettecasino.fortune_wheel", "Glücksrad");
        de("block.roulettecasino.plinko", "Casino-Plinko");
    }

    private AdminTexts() {
    }

    private static void de(String key, String german) {
        GERMAN.put(key, german);
    }

    /** A known key with its German fallback. */
    static Component t(String key, ComponentLike... args) {
        String german = GERMAN.get(key);
        if (german == null) {
            throw new IllegalArgumentException("no German text for " + key);
        }
        return Component.translatable(key, german, args);
    }

    /** {@code gui.roulettecasino.settings.<name>}. */
    static Component settings(String name, ComponentLike... args) {
        return t(S + name, args);
    }

    /** {@code message.roulettecasino.croupier_hand.<name>}. */
    static Component message(String name, ComponentLike... args) {
        return t(M + name, args);
    }

    /** {@code item.roulettecasino.croupier_hand.<name>}. */
    static Component item(String name, ComponentLike... args) {
        return t(I + name, args);
    }

    /** {@code CasinoMachine.typeName}: the machine's block name ({@code block.roulettecasino.<id>}). */
    public static Component typeName(MachineType type) {
        if (type == MachineType.CASHIER) {
            return Component.translatable("item.kaboofcasino.cashier", "Kasse"); // the plugin's own: no mod text
        }
        return t("block.roulettecasino." + type.id());
    }

    /** {@code CasinoMachine.displayName}: its own name, else "{@code <Type> #<n>}". */
    public static Component displayName(MachineType type, String name, int number) {
        return !name.isBlank() ? Component.text(name) : typeName(type).append(Component.text(" #" + number));
    }

    /** A vanilla key the client always has. */
    static Component vanilla(String key, ComponentLike... args) {
        return Component.translatable(key, args);
    }
}
