package de.tigges.kaboofcasino.admin;

import de.tigges.kaboofcasino.config.Language;
import de.tigges.kaboofcasino.machine.MachineType;
import java.util.LinkedHashMap;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;

/**
 * The texts of the croupier's hand: the mod's lang keys ({@code gui.roulettecasino.settings.*},
 * {@code message.roulettecasino.croupier_hand.*}, {@code item.roulettecasino.croupier_hand.*}, the machines'
 * {@code block.roulettecasino.*} names) as translatable components with the mod's text in the casino's language
 * ({@code en_us.json} or {@code de_de.json}) as fallback, so the client's language applies wherever the pack ships the
 * key (the guide's {@code item.} and {@code block.} keys) and the casino's everywhere else ({@code AdminTextsTest}
 * compares every fallback with the mod jar).
 * Vanilla keys ({@code gui.done}, {@code options.percent_value}) come from the client. Pure.
 */
public final class AdminTexts {

    static final String S = "gui.roulettecasino.settings.";
    static final String M = "message.roulettecasino.croupier_hand.";
    static final String I = "item.roulettecasino.croupier_hand.";

    /** Key -> the mod's German text. */
    static final Map<String, String> GERMAN = new LinkedHashMap<>();
    /** Key -> the mod's English text. */
    static final Map<String, String> ENGLISH = new LinkedHashMap<>();

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

    static {
        en(S + "title", "MACHINE SETTINGS");
        en(S + "chance", "Chance");
        en(S + "save", "Save");
        en(S + "copy", "Copy");
        en(S + "copied", "Copied!");
        en(S + "paste", "Paste");
        en(S + "pasted", "Pasted!");
        en(S + "confirm", "Sure?");
        en(S + "copy_help", "Copy keeps this machine's settings in the hand; Paste applies them here. Crouch and "
                + "right-click another machine to paste them onto it.");
        en(S + "reset_machine", "Reset");
        en(S + "reset_machine_help", "Clears all of this machine's own settings: it follows the casino in everything "
                + "again. Click twice.");
        en(S + "forget_copy", "Forget copy");
        en(S + "forget_copy_help", "Forgets the settings copied from a machine that the hand keeps.");
        en(S + "all_normal", "All back to normal");
        en(S + "all_normal_help", "Every game back to its normal chance; machines with their own setting keep it. "
                + "Click twice.");
        en(S + "everything", "Whole casino");
        en(S + "everything_subtitle", "Settings every machine follows");
        en(S + "dimension_nether", "Nether");
        en(S + "dimension_end", "End");
        en(S + "name", "Machine name");
        en(S + "automatic_name", "Automatic name: %s");
        en(S + "name_help", "Leave the field empty to restore the automatic name.");
        en(S + "help_name", "Its name in this list, and above it while you hold the croupier's hand.");
        en(S + "help_chance_machine", "This machine's own win chance. Drag the bar or type a number.");
        en(S + "help_chance_blackjack", "Which side this table's cards favour. In the middle: normal.");
        en(S + "help_chance_world", "Each game's win chance, for all its machines. Drag a bar or type a number.");
        en(S + "like_casino_value", "Like the casino: %s");
        en(S + "like_casino_normal", "Like the casino: normal (%s)");
        en(S + "like_casino_chance_help", "The machine follows its game's chance, set in “Whole casino” › Chance.");
        en(S + "natural", "Normal win chance: %s");
        en(S + "return", "Average return: %s");
        en(S + "return_help", "Average returned per stake. Above 100%, players receive more than they stake: the "
                + "machine creates items.");
        en(S + "creates_items", "Creates more items than are staked");
        en(S + "roulette_green", "This is the red/black win chance. Green wins at one eighteenth of this chance.");
        en(S + "derby_group", "Group races use natural odds (1 in 4). Other rates apply to solo play.");
        en(S + "favour_dealer", "Dealer");
        en(S + "bias_normal", "Normal");
        en(S + "favour_player", "Player");
        en(S + "bias_help", "Which side the cards favour. For each card, with the intensity below, the dealer deals "
                + "whichever of the two top cards of the shoe suits that side; the other stays on top. Rules, "
                + "actions and payouts do not change.");
        en(S + "bias_wins", "Player wins about %s of hands (normal: %s)");
        en(S + "bias_return", "Estimated average return: %s");
        en(S + "bias_estimate_help", "Measured over 400,000 hands per setting, the player using the best strategy: a "
                + "player who plays worse wins less. Shown only in the Croupier's Hand.");
        en(S + "bias_estimate_help_poker", "Measured over 40,000 hands per setting, the player calling with a pair, "
                + "a draw or a ten or better: a player who plays worse wins less. Shown only in the Croupier's Hand.");
        en(S + "blackjack_rules", "The current shoe, actions and payouts stay the same.");
        en(S + "bias_short_normal", "normal");
        en(S + "bias_short_dealer", "dealer +%s");
        en(S + "bias_short_player", "player +%s");
        en(S + "type_slider_normal", "%s: normal (%s)");
        en(S + "stats_type", "%s: average return %s");
        en(S + "stats_blackjack", "Blackjack: player wins ~%s · return ~%s");
        en(S + "stats_poker", "Poker: player wins ~%s · return ~%s");
        en(S + "protection", "Protection");
        en(S + "help_protection", "Can players break the casino's machines in survival?");
        en(S + "breakable_help", "Applies to all six casino machines. Creative players can always break them.");
        en(M + "creative_only", "The croupier's hand only works in creative mode.");
        en(M + "pasted", "Settings pasted onto %s");
        en(M + "nothing_to_paste", "No copy in the hand: open a machine with it and click Copy.");
        en(M + "copy_forgotten", "Copied settings forgotten.");
        en(I + "copied", "Copied settings: %s (%s, %s, %s)");
        en(I + "copied_help", "Crouch + right-click a machine: paste · in the air: forget");
        en("block.roulettecasino.roulette_table", "Casino Roulette");
        en("block.roulettecasino.blackjack_table", "Blackjack Table");
        en("block.roulettecasino.poker_table", "Poker Table");
        en("block.roulettecasino.duck_derby", "Duck Derby");
        en("block.roulettecasino.slot_machine", "Slot Machine");
        en("block.roulettecasino.fortune_wheel", "Fortune Wheel");
        en("block.roulettecasino.plinko", "Casino Plinko");
    }

    private AdminTexts() {
    }

    private static void de(String key, String german) {
        GERMAN.put(key, german);
    }

    private static void en(String key, String english) {
        ENGLISH.put(key, english);
    }

    /** The mod's text of a known key in the casino's language. */
    static String fallback(String key) {
        String text = (Language.current() == Language.DE ? GERMAN : ENGLISH).get(key);
        if (text == null) {
            throw new IllegalArgumentException("no text for " + key);
        }
        return text;
    }

    /** A known key with its text in the casino's language as fallback. */
    static Component t(String key, ComponentLike... args) {
        return Component.translatable(key, fallback(key), args);
    }

    /** The plugin's own machines (no mod text): their names in the casino's language. */
    static String ownName(MachineType type) {
        if (type.onWall() && type != MachineType.LEADERBOARD_WALL) {
            return Language.text("Leaderboard (wall board ", "Bestenliste (Wandtafel ") + type.wallColumns() + "×"
                    + type.wallRows() + ")";
        }
        return switch (type) {
            case CASHIER -> Language.text("Cashier", "Kasse");
            case LEADERBOARD_WALL -> Language.text("Leaderboard (wall board)", "Bestenliste (Wandtafel)");
            case LEADERBOARD_COLUMN -> Language.text("Leaderboard (column)", "Bestenliste (Säule)");
            case CHIP_SHOP -> Language.text("Chip Shop", "Chip-Shop");
            case JUKEBOX -> Language.text("Casino Jukebox", "Casino-Jukebox");
            default -> throw new IllegalArgumentException(type + " is the mod's");
        };
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
        if (type == MachineType.CASHIER || type == MachineType.CHIP_SHOP || type == MachineType.JUKEBOX
                || type.isBoard()) {
            return Component.translatable("item.kaboofcasino." + type.id(), ownName(type)); // no mod text
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
