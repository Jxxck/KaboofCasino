package de.tigges.kaboofcasino.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.config.CasinoConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Effective values (machine over type), the name rule, and that every controller asks {@link MachineSettings}. */
class MachineSettingsTest {

    private static CasinoConfig config(int slot, int roulette, int derby, int blackjack, int poker) {
        CasinoConfig d = CasinoConfig.DEFAULTS;
        return new CasinoConfig(d.pack(), d.bets(), d.rounds(), d.owed(), d.machines(), d.safety(), d.gui(),
                new CasinoConfig.Slot(slot, 4, 16, true), d.wheel(), d.plinko(),
                new CasinoConfig.Slot(roulette, 0, 16, true), new CasinoConfig.Blackjack(blackjack, true),
                new CasinoConfig.Poker(poker, true), new CasinoConfig.Slot(derby, 4, 16, true), d.lights(),
                d.music(), d.cashier(), d.leaderboard(), d.chipshop());
    }

    @Test
    void theMachineValueOverridesTheTypeValue() {
        // CasinoSettings.chance: own >= 0 ? own : type (which may be -1 = natural)
        assertEquals(-1, MachineSettings.effectiveChance(-1, -1));
        assertEquals(30, MachineSettings.effectiveChance(-1, 30));
        assertEquals(0, MachineSettings.effectiveChance(0, 30));
        assertEquals(100, MachineSettings.effectiveChance(100, -1));
        // CasinoSettings.bias: own != INHERIT ? own : type (INHERIT -> 0)
        assertEquals(0, MachineSettings.effectiveBias(MachineSettings.INHERIT_BIAS, MachineSettings.INHERIT_BIAS));
        assertEquals(-40, MachineSettings.effectiveBias(MachineSettings.INHERIT_BIAS, -40));
        assertEquals(0, MachineSettings.effectiveBias(0, -40));
        assertEquals(25, MachineSettings.effectiveBias(25, 0));

        CasinoConfig c = config(30, -1, 25, -20, 10);
        MachineSettings.Own none = MachineSettings.Own.NONE;
        assertEquals(30, MachineSettings.winChance(c, MachineType.SLOT_MACHINE, none));
        assertEquals(5, MachineSettings.winChance(c, MachineType.SLOT_MACHINE, none.withChance(5)));
        assertEquals(-1, MachineSettings.winChance(c, MachineType.ROULETTE_TABLE, none));
        assertEquals(70, MachineSettings.winChance(c, MachineType.ROULETTE_TABLE, none.withChance(70)));
        assertEquals(25, MachineSettings.winChance(c, MachineType.DUCK_DERBY, none));
        assertEquals(-1, MachineSettings.winChance(c, MachineType.FORTUNE_WHEEL, none));
        assertEquals(-1, MachineSettings.winChance(c, MachineType.PLINKO, none));
        assertEquals(-20, MachineSettings.bias(c, MachineType.BLACKJACK_TABLE, none));
        assertEquals(50, MachineSettings.bias(c, MachineType.BLACKJACK_TABLE, none.withBias(50)));
        assertEquals(10, MachineSettings.bias(c, MachineType.POKER_TABLE, none));
        assertEquals(0, MachineSettings.bias(c, MachineType.POKER_TABLE, none.withBias(0)));
        assertThrows(IllegalArgumentException.class, () -> MachineSettings.winChance(c, MachineType.POKER_TABLE,
                none));
        assertThrows(IllegalArgumentException.class, () -> MachineSettings.bias(c, MachineType.PLINKO, none));
    }

    @Test
    void typeValuesAndConfigPathsCoverEveryGameOnce() {
        // every game once; the cashier is no game (no win chance, no bias)
        assertEquals(EnumSet.complementOf(EnumSet.of(MachineType.CASHIER, MachineType.LEADERBOARD_WALL,
                MachineType.LEADERBOARD_COLUMN, MachineType.CHIP_SHOP)), EnumSet.copyOf(MachineSettings.MOD_ORDER));
        assertEquals(List.of("roulette", "blackjack", "poker", "derby", "slots", "wheel", "plinko"),
                MachineSettings.MOD_ORDER.stream().map(MachineSettings::modType).toList());
        CasinoConfig d = CasinoConfig.DEFAULTS;
        for (MachineType t : MachineSettings.MOD_ORDER) {
            assertEquals(MachineSettings.usesBias(t) ? 0 : -1, MachineSettings.typeValue(d, t), t.name());
            assertEquals(MachineSettings.usesBias(t), MachineSettings.configPath(t).endsWith(".bias"), t.name());
        }
        CasinoConfig c = config(1, 2, 3, 4, 5);
        assertEquals(1, MachineSettings.typeValue(c, MachineType.SLOT_MACHINE));
        assertEquals(2, MachineSettings.typeValue(c, MachineType.ROULETTE_TABLE));
        assertEquals(3, MachineSettings.typeValue(c, MachineType.DUCK_DERBY));
        assertEquals(4, MachineSettings.typeValue(c, MachineType.BLACKJACK_TABLE));
        assertEquals(5, MachineSettings.typeValue(c, MachineType.POKER_TABLE));
    }

    @Test
    void namesFollowCasinoMachineIdentity() {
        assertEquals(Optional.of(""), MachineSettings.validName(""));
        assertEquals(Optional.of("Lucky 7"), MachineSettings.validName("  Lucky 7 "));
        assertEquals(Optional.of("Glücksrad am Eingang – Süd"), MachineSettings.validName("Glücksrad am Eingang – Süd"));
        assertEquals(Optional.of("x".repeat(64)), MachineSettings.validName("x".repeat(64)));
        // the mod checks the length before stripping
        assertEquals(Optional.empty(), MachineSettings.validName("x".repeat(64) + " "));
        assertEquals(Optional.empty(), MachineSettings.validName("x".repeat(65)));
        assertEquals(Optional.empty(), MachineSettings.validName("§cRot"));
        assertEquals(Optional.empty(), MachineSettings.validName("a\nb"));
        assertEquals(Optional.empty(), MachineSettings.validName("a\tb"));
        assertEquals(Optional.empty(), MachineSettings.validName("a\u007fb"));
        assertEquals(Optional.empty(), MachineSettings.validName("a\u0085b"));
        assertEquals(Optional.empty(), MachineSettings.validName(null));
    }

    @Test
    void ownSettingsAreRangeChecked() {
        assertThrows(IllegalArgumentException.class, () -> new MachineSettings.Own(" padded", -1,
                MachineSettings.INHERIT_BIAS));
        assertThrows(IllegalArgumentException.class, () -> new MachineSettings.Own("", 101,
                MachineSettings.INHERIT_BIAS));
        assertThrows(IllegalArgumentException.class, () -> new MachineSettings.Own("", -2,
                MachineSettings.INHERIT_BIAS));
        assertThrows(IllegalArgumentException.class, () -> new MachineSettings.Own("", -1, 101));
        assertThrows(IllegalArgumentException.class, () -> new MachineSettings.Own("", -1, -101));
        MachineSettings.Own own = new MachineSettings.Own("Tisch", 40, -30);
        assertTrue(own.ownChance() && own.ownBias());
        MachineSettings.Own reset = own.reset();
        assertEquals("Tisch", reset.name(), "reset keeps the name (the mod's identity)");
        assertFalse(reset.ownChance() || reset.ownBias());
        assertEquals(new MachineSettings.Own("x", -1, MachineSettings.INHERIT_BIAS),
                MachineSettings.Own.read("x", null, null));
        assertEquals(new MachineSettings.Own("", 7, 8), MachineSettings.Own.read(null, 7, 8));
    }

    private static final Pattern DIRECT = Pattern.compile(
            "config\\(\\)\\.get\\(\\)\\.(slot|wheel|plinko|roulette|derby|blackjack|poker)\\(\\)\\.(winChance|bias)\\(\\)"
                    + "|\\b(settings|cfg|config)\\.(winChance|bias)\\(\\)");

    /** "No controller keeps reading the config directly": the games ask MachineSettings, nothing else. */
    @Test
    void noGameReadsTheTypeValueDirectly() throws IOException {
        Path games = Path.of("src/main/java/de/tigges/kaboofcasino/game");
        List<String> hits = new ArrayList<>();
        int asks = 0;
        try (Stream<Path> files = Files.walk(games)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String text = Files.readString(f, StandardCharsets.UTF_8);
                Matcher m = DIRECT.matcher(text);
                while (m.find()) {
                    hits.add(f.getFileName() + ": " + m.group());
                }
                Matcher ask = Pattern.compile("MachineSettings\\.(winChance|bias)\\(ctx\\.config\\(\\)\\.get\\(\\), "
                        + "machine\\)").matcher(text);
                while (ask.find()) {
                    asks++;
                }
            }
        }
        assertEquals(List.of(), hits);
        // slot, wheel, plinko, roulette, blackjack, poker, derby solo + group allowed + group draw + lobby snapshot
        assertEquals(10, asks);
    }
}
