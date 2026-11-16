package de.tigges.kaboofcasino.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.board.BoardCategory;
import de.tigges.kaboofcasino.board.BoardPeriod;
import de.tigges.kaboofcasino.board.BoardView;
import de.tigges.kaboofcasino.chipshop.ChipShopCategory;
import de.tigges.kaboofcasino.game.PanelText;
import de.tigges.kaboofcasino.pack.PackFile;
import de.tigges.kaboofcasino.util.ChipText;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The two languages: both texts files say the same things, numbers and command words follow the language, each
 * language has its resource pack, and a German installation of a version up to 0.19.0 stays what it was.
 */
class LanguageTest {

    private static final Set<String> FORMAT_TAGS = Set.of("gold", "gray", "dark_gray", "white", "yellow", "red", "green",
            "aqua", "b", "i", "u");
    private static final Pattern TAG = Pattern.compile("<([a-z_]+)>");
    private static final String MOD_URL = "https://www.curseforge.com/minecraft/mc-mods/minevegas-casino";

    private Language before;
    private ZoneId zoneBefore;

    @BeforeEach
    void remember() {
        before = Language.current();
        zoneBefore = Language.zone();
    }

    @AfterEach
    void restore() {
        Language.use(before);
        Language.useZone(zoneBefore);
    }

    private static YamlConfiguration yaml(String resource) {
        InputStream in = LanguageTest.class.getClassLoader().getResourceAsStream(resource);
        assertTrue(in != null, resource + " is missing");
        return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    private static Set<String> placeholders(String text) {
        Set<String> out = new TreeSet<>();
        Matcher m = TAG.matcher(text);
        while (m.find()) {
            if (!FORMAT_TAGS.contains(m.group(1))) {
                out.add(m.group(1));
            }
        }
        return out;
    }

    @Test
    void bothTextsFilesHaveEveryKeyWithTheSamePlaceholders() {
        YamlConfiguration en = yaml(Language.EN.messagesFile());
        YamlConfiguration de = yaml(Language.DE.messagesFile());
        assertEquals("messages_en.yml", Language.EN.messagesFile());
        assertEquals("messages_de.yml", Language.DE.messagesFile());
        Set<String> enKeys = new TreeSet<>();
        Set<String> deKeys = new TreeSet<>();
        en.getKeys(true).stream().filter(k -> !en.isConfigurationSection(k)).forEach(enKeys::add);
        de.getKeys(true).stream().filter(k -> !de.isConfigurationSection(k)).forEach(deKeys::add);
        assertEquals(deKeys, enKeys);
        for (Msg m : Msg.values()) {
            assertTrue(en.isString(m.key()), "messages_en.yml lacks " + m.key());
            assertTrue(de.isString(m.key()), "messages_de.yml lacks " + m.key());
            if (m == Msg.COMMAND_USAGE) {
                continue; // names its arguments in its own language (<item> / <maschine>)
            }
            assertEquals(placeholders(de.getString(m.key())), placeholders(en.getString(m.key())), m.key());
            assertEquals(de.getString(m.key()).split("\n", -1).length > 1, en.getString(m.key()).split("\n", -1).length > 1,
                    m.key() + ": one line in one language, several in the other");
        }
        assertEquals(Msg.values().length, enKeys.size(), "a key without a Msg");
    }

    @Test
    void theEnglishFilesAreEnglish() throws IOException {
        Pattern german = Pattern.compile("[äöüßÄÖÜ]|\\b(und|oder|nicht|für|dein|deine|Einsatz|Spieler|Gewinn|Runde)\\b");
        YamlConfiguration en = yaml(Language.EN.messagesFile());
        for (String key : en.getKeys(true)) {
            if (en.isString(key)) {
                assertFalse(german.matcher(en.getString(key)).find(), key + ": " + en.getString(key));
            }
        }
        for (String file : new String[] {"config.yml", "messages_en.yml", "plugin.yml"}) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(file)) {
                String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                assertFalse(Pattern.compile("[äöüßÄÖÜ]").matcher(text).find(), file + " has German text");
            }
        }
        // the commands the English texts name are the English words
        assertTrue(en.getString("command.info").contains("/casino collect"));
        assertTrue(en.getString("command.usage").contains("leaderboard|collect|credits|music"));
        assertTrue(en.getString("owed.pending").contains("run_command:'/casino collect'"));
        assertTrue(en.getString("command.music-usage").contains("/casino music off"));
        assertTrue(en.getString("board.top-usage").contains("[week|total]"));
    }

    @Test
    void theEnglishCreditsNameEverythingTheLicencesAskFor() {
        YamlConfiguration en = yaml(Language.EN.messagesFile());
        assertTrue(en.getString("command.info").contains(MOD_URL));
        assertTrue(en.getString("guide.credits").contains(MOD_URL));
        String credits = en.getString("command.credits");
        assertTrue(credits.contains(MOD_URL) && credits.contains("byMehd") && credits.contains("Jxxck"));
        for (String title : new String[] {"Airport Lounge", "Backbay Lounge", "Jazz Brunch", "George Street Shuffle",
            "Cool Vibes", "Bossa Antigua"}) {
            assertTrue(credits.contains(title), title);
        }
        assertTrue(credits.contains("Kevin MacLeod") && credits.contains("incompetech.com"));
        assertTrue(credits.contains("CC BY 4.0") && credits.contains("https://creativecommons.org/licenses/by/4.0/"));
        assertTrue(credits.contains("Ogg Vorbis") && credits.contains("-20 LUFS"), "the changes are stated");
        String prompt = en.getString("pack.music-prompt");
        assertTrue(prompt.contains("Kevin MacLeod") && prompt.contains("CC BY 4.0"));
        assertTrue(en.getString("pack.prompt").contains("MineVegas Casino"));
        // the mod's own English wording where the plugin shows the mod's texts
        assertEquals("<yellow>Place a bet before pulling the lever.", en.getString("slot.no-bet"));
        assertEquals("Slot Machine", en.getString("gui.slot.title"));
    }

    @Test
    void numbersFollowTheLanguage() {
        Language.use(Language.EN);
        assertEquals("1,250", ChipText.number(1250));
        assertEquals("100,000,000", ChipText.number(100_000_000));
        assertEquals("-40", ChipText.number(-40));
        assertEquals("1 chip", ChipText.chips(1));
        assertEquals("1,250 chips", ChipText.chips(1250));
        assertEquals("×2.5", PanelText.multiplier(10));
        assertEquals("×0.25", PanelText.multiplier(1));
        assertEquals("×1,234.5", BoardView.value(BoardCategory.MULTIPLIER, 123_450));
        assertEquals("12.3k", BoardView.compact(12_345));
        assertEquals("123k", BoardView.compact(123_456));
        assertEquals("1.2M", BoardView.compact(1_234_567));
        assertEquals("9,999", BoardView.compact(9_999));
        Language.use(Language.DE);
        assertEquals("1.250", ChipText.number(1250));
        assertEquals("1 Chip", ChipText.chips(1));
        assertEquals("1.250 Chips", ChipText.chips(1250));
        assertEquals("×2,5", PanelText.multiplier(10));
        assertEquals("×1.234,5", BoardView.value(BoardCategory.MULTIPLIER, 123_450));
        assertEquals("12,3 Tsd.", BoardView.compact(12_345));
        assertEquals("1,2 Mio.", BoardView.compact(1_234_567));
    }

    @Test
    void commandWordsAreReadInBothLanguagesAndShownInOne() {
        for (Language l : Language.values()) {
            Language.use(l);
            for (BoardCategory c : BoardCategory.values()) {
                assertEquals(Optional.of(c), BoardCategory.byId(c.id()), c.id());
                assertEquals(Optional.of(c), BoardCategory.byId(c.word()), c.word());
                assertEquals("board.category." + c.id(), c.messageKey());
            }
            for (BoardPeriod p : BoardPeriod.values()) {
                assertEquals(Optional.of(p), BoardPeriod.byId(p.id()));
                assertEquals(Optional.of(p), BoardPeriod.byId(p.word()));
            }
            // the words of versions up to 0.19.0 (typed by habit, or stored in a pinned board)
            assertEquals(Optional.of(BoardCategory.BIGGEST_WIN), BoardCategory.byId("gewinn"));
            assertEquals(Optional.of(BoardCategory.FORTUNE_WHEEL), BoardCategory.byId("Glücksrad"));
            assertEquals(Optional.of(BoardCategory.DERBY), BoardCategory.byId("entenrennen"));
            assertEquals(Optional.of(BoardPeriod.WEEK), BoardPeriod.byId("woche"));
            assertEquals(Optional.of(BoardPeriod.TOTAL), BoardPeriod.byId("gesamt"));
            assertEquals(Optional.empty(), BoardCategory.byId("bogus"));
        }
        Language.use(Language.EN);
        assertEquals("wheel", BoardCategory.FORTUNE_WHEEL.word());
        assertEquals("week", BoardPeriod.WEEK.word());
        Language.use(Language.DE);
        assertEquals("gluecksrad", BoardCategory.FORTUNE_WHEEL.word());
        assertEquals("woche", BoardPeriod.WEEK.word());
        // ids are the English words in both languages: they are stored
        assertEquals("wheel", BoardCategory.FORTUNE_WHEEL.id());
        assertEquals("week", BoardPeriod.WEEK.id());
        for (ChipShopCategory c : ChipShopCategory.values()) {
            assertTrue(c.id().matches("[a-z]+"), c.id());
        }
        assertEquals("floors", ChipShopCategory.FLOORS.id());
    }

    @Test
    void languagesAndZonesByName() {
        assertEquals(Optional.of(Language.EN), Language.byCode("en"));
        assertEquals(Optional.of(Language.DE), Language.byCode(" DE "));
        assertEquals(Optional.empty(), Language.byCode("fr"));
        assertEquals(Optional.empty(), Language.byCode(null));
        assertEquals(Optional.of(ZoneId.systemDefault()), Language.zoneOf("system"));
        assertEquals(Optional.of(ZoneId.of("America/New_York")), Language.zoneOf("America/New_York"));
        assertEquals(Optional.empty(), Language.zoneOf("Nowhere/Land"));
        assertEquals("pack/en/guide.json", Language.EN.packResource("guide.json"));
        assertEquals("Chip Shop", Language.EN.pick("Chip Shop", "Chip-Shop"));
        assertEquals("Chip-Shop", Language.DE.pick("Chip Shop", "Chip-Shop"));
    }

    @Test
    void theWeekStartsOnMondayInTheConfiguredZone() {
        long noonUtc = java.time.ZonedDateTime.of(2026, 11, 16, 3, 0, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli();
        Language.useZone(ZoneId.of("Europe/Berlin")); // Monday 04:00 there: this week began at midnight
        long berlin = BoardPeriod.weekStart(noonUtc);
        Language.useZone(ZoneId.of("America/New_York")); // still Sunday 22:00 there: the week began a week ago
        long newYork = BoardPeriod.weekStart(noonUtc);
        assertEquals(java.time.ZonedDateTime.of(2026, 11, 16, 0, 0, 0, 0, ZoneId.of("Europe/Berlin")).toInstant()
                .toEpochMilli(), berlin);
        assertEquals(java.time.ZonedDateTime.of(2026, 11, 9, 0, 0, 0, 0, ZoneId.of("America/New_York")).toInstant()
                .toEpochMilli(), newYork);
    }

    @Test
    void eachLanguageHasItsPackAndItsFiles() throws IOException {
        PackFile en = PackFile.load(getClass().getClassLoader(), Language.EN);
        PackFile de = PackFile.load(getClass().getClassLoader(), Language.DE);
        assertNotEquals(en.sha1(), de.sha1(), "the lettering differs");
        assertEquals(PackFile.PACK_ID, en.id());
        for (Language l : Language.values()) {
            for (String file : new String[] {"gui-layout.json", "guide.json", "guide-pictures.json"}) {
                try (InputStream in = getClass().getClassLoader().getResourceAsStream(l.packResource(file))) {
                    assertTrue(in != null && in.readAllBytes().length > 1000, l.packResource(file));
                }
            }
        }
        // the guide's texts are the language's
        String enGuide = new String(getClass().getClassLoader().getResourceAsStream(Language.EN.packResource("guide.json"))
                .readAllBytes(), StandardCharsets.UTF_8);
        String deGuide = new String(getClass().getClassLoader().getResourceAsStream(Language.DE.packResource("guide.json"))
                .readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(enGuide.contains("\"item.roulettecasino.casino_guide\":\"Casino Guide\""));
        assertTrue(deGuide.contains("\"item.roulettecasino.casino_guide\":\"Casino-Handbuch\""));
    }

    // ---- upgrading ------------------------------------------------------------------------------------------------

    private static Path copy(Path dir, String resource, String name) throws IOException {
        Path file = dir.resolve(name);
        try (InputStream in = LanguageTest.class.getClassLoader().getResourceAsStream(resource)) {
            Files.write(file, in.readAllBytes());
        }
        return file;
    }

    @Test
    void aFreshInstallationIsEnglishInTheServersZone(@TempDir Path dir) {
        Path file = dir.resolve("config.yml");
        ConfigLoader.Loaded loaded = ConfigLoader.load(file, () -> getClass().getClassLoader()
                .getResourceAsStream("config.yml"));
        assertEquals(java.util.List.of(), loaded.warnings());
        assertEquals(0, loaded.added());
        assertEquals(Language.EN, loaded.config().region().language());
        assertEquals(ZoneId.systemDefault(), loaded.config().region().zone());
        assertEquals(CasinoConfig.DEFAULTS, loaded.config());
    }

    @Test
    void anInstallationOfTheGermanVersionsStaysGermanAndKeepsItsSettings(@TempDir Path dir) throws IOException {
        Path file = copy(dir, "legacy/0.19.0/config.yml", "config.yml");
        // what an admin had set
        String text = Files.readString(file).replace("    boeden:\n      markup: 1.25", "    boeden:\n      markup: 2.5")
                .replace("    technik:\n      markup: 3.75\n      min-price: 5", "    technik:\n      markup: 3.75\n      min-price: 40");
        assertTrue(text.contains("markup: 2.5") && text.contains("min-price: 40") && text.contains("discord:"));
        Files.writeString(file, text);
        ConfigLoader.Loaded loaded = ConfigLoader.load(file, () -> getClass().getClassLoader()
                .getResourceAsStream("config.yml"));
        assertEquals(java.util.List.of(), loaded.warnings(), "nothing unknown is left behind");
        assertTrue(loaded.added() > 0);
        CasinoConfig c = loaded.config();
        assertEquals(Language.DE, c.region().language());
        assertEquals(ZoneId.of("Europe/Berlin"), c.region().zone());
        assertEquals(2.5, c.chipshop().categories().get(ChipShopCategory.FLOORS).markup());
        assertEquals(40, c.chipshop().categories().get(ChipShopCategory.TECH).minPrice());
        // everything else is what 0.19.0 shipped, which are still the defaults
        assertEquals(CasinoConfig.DEFAULTS.pack(), c.pack());
        assertEquals(CasinoConfig.DEFAULTS.cashier(), c.cashier());
        assertEquals(CasinoConfig.DEFAULTS.poker(), c.poker());
        YamlConfiguration written = YamlConfiguration.loadConfiguration(file.toFile());
        assertEquals("de", written.getString("language"));
        assertEquals("Europe/Berlin", written.getString("time-zone"));
        assertEquals(2.5, written.getDouble("chipshop.categories.floors.markup"));
        assertFalse(written.contains("chipshop.categories.boeden"));
        assertFalse(written.contains("chipshop.categories.technik"));
        assertFalse(written.contains("discord"));
        // the second start changes nothing
        String once = Files.readString(file);
        ConfigLoader.Loaded again = ConfigLoader.load(file, () -> getClass().getClassLoader()
                .getResourceAsStream("config.yml"));
        assertEquals(0, again.added());
        assertEquals(java.util.List.of(), again.warnings());
        assertEquals(once, Files.readString(file));
        assertEquals(c, again.config());
    }

    @Test
    void theGermanTextsOfAnOlderVersionKeepWhatAnAdminWrote(@TempDir Path dir) throws IOException {
        Path file = copy(dir, "legacy/0.19.0/messages_de.yml", "messages_de.yml");
        String text = Files.readString(file).replace("gewinn: \"Größter Gewinn\"", "gewinn: \"Dickster Gewinn\"")
                .replace("woche: \"Diese Woche\"", "woche: \"Laufende Woche\"");
        assertTrue(text.contains("Dickster Gewinn") && text.contains("Laufende Woche") && text.contains("discord:"));
        Files.writeString(file, text);
        Messages.Loaded loaded = Messages.load(file, () -> getClass().getClassLoader()
                .getResourceAsStream("messages_de.yml"));
        assertEquals(java.util.List.of(), loaded.warnings());
        assertEquals("Dickster Gewinn", loaded.messages().raw(Msg.BOARD_CAT_WIN));
        assertEquals("Laufende Woche", loaded.messages().raw(Msg.BOARD_PERIOD_WEEK));
        assertEquals("Böden und Wände", loaded.messages().raw(Msg.CHIPSHOP_CAT_FLOORS));
        YamlConfiguration written = YamlConfiguration.loadConfiguration(file.toFile());
        assertNull(written.get("board.category.gewinn"));
        assertNull(written.get("discord"));
        for (Msg m : Msg.values()) {
            assertTrue(written.isString(m.key()), m.key());
        }
        String once = Files.readString(file);
        assertEquals(0, Messages.load(file, () -> getClass().getClassLoader().getResourceAsStream("messages_de.yml"))
                .added());
        assertEquals(once, Files.readString(file));
    }
}
