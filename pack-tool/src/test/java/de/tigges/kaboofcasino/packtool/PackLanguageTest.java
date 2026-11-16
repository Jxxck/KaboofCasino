package de.tigges.kaboofcasino.packtool;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The casino pack in both languages: the same files, the same layout for the plugin, and only the lettering, the
 * description and what is drawn from the mod's lang file differ.
 */
class PackLanguageTest {

    private static PackLang before;
    private static PackTool.Result english;
    private static PackTool.Result german;
    private static Map<String, byte[]> en;
    private static Map<String, byte[]> de;

    private static Map<String, byte[]> unzip(byte[] zip) throws IOException {
        Map<String, byte[]> out = new TreeMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                out.put(e.getName(), in.readAllBytes());
            }
        }
        return out;
    }

    @BeforeAll
    static void build() throws IOException {
        before = PackLang.current();
        Path jar = Path.of(System.getProperty("kaboofcasino.modJar"));
        String sha1 = System.getProperty("kaboofcasino.modJarSha1");
        Path src = Path.of(System.getProperty("kaboofcasino.packSrc"));
        english = PackTool.build(jar, sha1, src, PackLang.EN);
        assertEquals(PackLang.EN, PackLang.current());
        german = PackTool.build(jar, sha1, src, PackLang.DE);
        en = unzip(english.zip());
        de = unzip(german.zip());
        PackLang.use(before);
    }

    @AfterAll
    static void restore() {
        PackLang.use(before);
    }

    @Test
    void bothPacksHaveTheSameFilesAndTheSameLayout() {
        assertEquals(de.keySet(), en.keySet());
        assertNotEquals(german.sha1(), english.sha1());
        assertArrayEquals(german.layout(), english.layout(), "the plugin places text and pictures alike in both");
        assertArrayEquals(german.recipes(), english.recipes());
    }

    @Test
    void theEnglishBuildIsRepeatable() {
        PackTool.Result again = PackTool.build(Path.of(System.getProperty("kaboofcasino.modJar")),
                System.getProperty("kaboofcasino.modJarSha1"), Path.of(System.getProperty("kaboofcasino.packSrc")),
                PackLang.EN);
        PackLang.use(before);
        assertArrayEquals(english.zip(), again.zip());
    }

    @Test
    void onlyLetteringDiffers() {
        Set<String> different = new TreeSet<>();
        for (Map.Entry<String, byte[]> e : en.entrySet()) {
            if (!Arrays.equals(e.getValue(), de.get(e.getKey()))) {
                different.add(e.getKey());
            }
        }
        // the description, the buttons and status lines, and the models that spell a word in LEDs
        assertTrue(different.contains("pack.mcmeta"));
        for (String path : different) {
            assertTrue(path.equals("pack.mcmeta") || path.endsWith(".png")
                    || path.startsWith("assets/kaboofcasino/models/"), "differs between the languages: " + path);
        }
        assertTrue(different.stream().anyMatch(p -> p.contains("models/cashier")), "the cashier's sign: " + different);
        assertTrue(different.stream().anyMatch(p -> p.contains("leaderboard")), "the leaderboards' headers");
        // every pack carries all of the mod's languages: the client picks its own
        for (String code : new String[] {"en_us", "de_de", "fr_fr"}) {
            assertFalse(different.contains("assets/roulettecasino/lang/" + code + ".json"));
            assertTrue(en.containsKey("assets/roulettecasino/lang/" + code + ".json"));
        }
        assertFalse(different.contains("credits/LICENSE.txt"));
        assertFalse(different.contains("credits/CREDITS.md"));
    }

    @Test
    void theDescriptionCreditsTheModInBothLanguages() {
        String enMeta = new String(en.get("pack.mcmeta"), StandardCharsets.UTF_8);
        String deMeta = new String(de.get("pack.mcmeta"), StandardCharsets.UTF_8);
        assertTrue(enMeta.contains(Credits.MOD_URL) && enMeta.contains("graphics and sounds") && enMeta.contains("byMehd"));
        assertTrue(deMeta.contains(Credits.MOD_URL) && deMeta.contains("Grafiken und Sounds"));
        assertFalse(enMeta.matches("(?s).*[äöüß].*"));
    }

    @Test
    void wordsAndLabelsOfBothLanguages() {
        PackLang.use(PackLang.EN);
        try {
            assertEquals("CASHIER", CashierPack.word());
            assertEquals("CHIP SHOP", ChipShopPack.word());
            assertEquals("LEADERBOARD", LeaderboardPack.word(LeaderboardPack.WALL_BOARD));
            assertEquals("TOP 10", LeaderboardPack.word(LeaderboardPack.COLUMN));
            assertEquals("SPIN", SlotMachineGui.spin());
            assertEquals("COLLECT", SlotMachineGui.collect());
            assertEquals("RED", RouletteGui.label(0));
            assertEquals("YELLOW", DerbyGui.duck(0));
            assertEquals("Ores", CashierGui.tabLabel(0));
            assertEquals("en_us", PackLang.current().modCode());
            // every LED word can be spelled
            CashierPack.leds(CashierPack.word());
            ChipShopPack.leds(ChipShopPack.word());
            LeaderboardPack.leds(LeaderboardPack.word(LeaderboardPack.WALL_BOARD));
            PackLang.use(PackLang.DE);
            assertEquals("KASSE", CashierPack.word());
            assertEquals("CHIP-SHOP", ChipShopPack.word());
            assertEquals("BESTENLISTE", LeaderboardPack.word(LeaderboardPack.WALL_BOARD));
            assertEquals("DREHEN", SlotMachineGui.spin());
            assertEquals("GELB", DerbyGui.duck(0));
            assertEquals("assets/casinodeco/lang/de_de.json", PackLang.current().modLangPath("casinodeco"));
            assertEquals(HoldemGui.STATUSES.keySet(), HoldemGui.STATUSES_EN.keySet());
            for (String[] b : HoldemGui.BUTTONS) {
                assertEquals(4, b.length);
                assertFalse(b[3].matches(".*[ÄÖÜ].*"));
            }
        } finally {
            PackLang.use(before);
        }
    }
}
