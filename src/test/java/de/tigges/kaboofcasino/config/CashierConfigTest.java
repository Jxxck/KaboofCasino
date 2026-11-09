package de.tigges.kaboofcasino.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.cashier.CashierSettings;
import de.tigges.kaboofcasino.cashier.ShopEntry;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The cashier's settings in config.yml (PLAN-2 C3): the shipped file is the defaults (the rates table of C3), rates
 * and prizes are lists that the start-up merge never refills, bad entries are dropped with a warning, and the
 * croupier's hand writes the prize list whole while every comment stays.
 */
class CashierConfigTest {

    private static String shipped() throws IOException {
        try (InputStream in = CashierConfigTest.class.getClassLoader().getResourceAsStream("config.yml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<String> comments(String text) {
        List<String> out = new ArrayList<>();
        for (String line : text.split("\n", -1)) {
            if (line.strip().startsWith("#")) {
                out.add(line.strip());
            }
        }
        return out;
    }

    @Test
    void theShippedFileIsTheDefaultsAndC3sRates() throws IOException {
        List<String> warnings = new ArrayList<>();
        CasinoConfig c = ConfigLoader.read(YamlFiles.parse(shipped(), "test"), warnings);
        assertEquals(List.of(), warnings);
        assertEquals(CashierSettings.DEFAULTS, c.cashier());
        assertFalse(c.cashier().craftable());
        Map<String, Long> c3 = Map.ofEntries(Map.entry("coal", 1L), Map.entry("copper_ingot", 1L),
                Map.entry("redstone", 1L), Map.entry("lapis_lazuli", 2L), Map.entry("iron_ingot", 4L),
                Map.entry("gold_ingot", 8L), Map.entry("emerald", 16L), Map.entry("diamond", 32L),
                Map.entry("netherite_ingot", 256L), Map.entry("coal_block", 9L), Map.entry("copper_block", 9L),
                Map.entry("redstone_block", 9L), Map.entry("lapis_block", 18L), Map.entry("iron_block", 36L),
                Map.entry("gold_block", 72L), Map.entry("emerald_block", 144L), Map.entry("diamond_block", 288L),
                Map.entry("netherite_block", 2304L));
        assertEquals(c3.size(), c.cashier().rates().size());
        c3.forEach((item, rate) -> assertEquals(rate, c.cashier().rate("minecraft:" + item).orElseThrow(), item));
        assertTrue(c.cashier().rate("minecraft:dirt").isEmpty());
        assertTrue(c.cashier().shop().isEmpty(), "no example prizes: the deco is in the Chip-Shop");
    }

    @Test
    void badRatesAreDroppedWithAWarning() {
        String text = """
                cashier:
                  craftable: maybe
                  rates:
                  - item: coal
                    value: 3
                  - item: minecraft:coal
                    value: 5
                  - item: kaboofcasino:chip_5
                    value: 5
                  - item: minecraft:diamond
                    value: 0
                  - item: minecraft:emerald
                    value: 1.5
                  - minecraft:gold_ingot
                  - item: Minecraft:Iron_Ingot
                    value: 4
                shop:
                  items: []
                """;
        List<String> warnings = new ArrayList<>();
        CashierSettings s = CashierSettings.read(YamlFiles.parse(text, "test"), warnings);
        assertEquals(List.of(new CashierSettings.Rate("minecraft:coal", 3), new CashierSettings.Rate(
                "minecraft:iron_ingot", 4)), s.rates());
        assertFalse(s.craftable());
        assertEquals(List.of(), s.shop());
        assertEquals(6, warnings.size(), warnings.toString());
        warnings.clear();
        CashierSettings missing = CashierSettings.read(YamlFiles.parse("cashier:\n  craftable: true\n", "test"),
                warnings);
        assertTrue(missing.craftable());
        assertEquals(CashierSettings.DEFAULT_RATES, missing.rates());
        assertEquals(CashierSettings.DEFAULT_SHOP, missing.shop());
        assertEquals(2, warnings.size());
    }

    @Test
    void theHandWritesTheChipShopOverridesAndKeepsEveryComment() throws IOException {
        String text = ConfigEdits.apply(shipped(), Map.of());
        List<de.tigges.kaboofcasino.chipshop.ChipShopSettings.Item> next = List.of(
                new de.tigges.kaboofcasino.chipshop.ChipShopSettings.Item("roulettecasino:slot_machine", 500, 1, false),
                new de.tigges.kaboofcasino.chipshop.ChipShopSettings.Item("casinodeco:ruby_velvet", 0, 0, true));
        String after = ConfigEdits.apply(text, Map.of("chipshop.items", next.stream()
                .map(de.tigges.kaboofcasino.chipshop.ChipShopSettings.Item::toYaml).toList()));
        assertEquals(comments(text), comments(after));
        List<String> warnings = new ArrayList<>();
        CasinoConfig c = ConfigLoader.read(YamlFiles.parse(after, "test"), warnings);
        assertEquals(List.of(), warnings);
        assertEquals(next, c.chipshop().items());
        assertEquals(CashierSettings.DEFAULT_SHOP, c.cashier().shop(), "the cashier's prizes stay");
        assertEquals(de.tigges.kaboofcasino.chipshop.ChipShopSettings.DEFAULT_VALUES, c.chipshop().values());
    }

    @Test
    void theHandWritesThePrizeListWholeAndKeepsEveryComment() throws IOException {
        String text = ConfigEdits.apply(shipped(), Map.of());
        assertEquals(text, ConfigEdits.apply(text, Map.of()));
        List<ShopEntry> next = List.of(new ShopEntry("roulettecasino:casino_chair", 1, 80, 2),
                new ShopEntry("minecraft:diamond_sword[enchantments={sharpness:5}]", 1, 4000, 1));
        String after = ConfigEdits.apply(text, Map.of("shop.items", ShopEntry.toYaml(next)));
        assertEquals(comments(text), comments(after));
        List<String> warnings = new ArrayList<>();
        CasinoConfig c = ConfigLoader.read(YamlFiles.parse(after, "test"), warnings);
        assertEquals(List.of(), warnings);
        assertEquals(next, c.cashier().shop());
        assertEquals(CashierSettings.DEFAULT_RATES, c.cashier().rates());
        assertEquals(CasinoConfig.DEFAULTS.bets(), c.bets());
        assertEquals(CasinoConfig.DEFAULTS.machines(), c.machines());
        // the rest of the file is untouched: only the lines of the list changed
        int start = after.lastIndexOf("  items:");
        assertTrue(start > after.indexOf("shop:"), "the prize list, not the bet items");
        assertEquals(text.substring(0, start), after.substring(0, start));
        // only the prize list is a writable list, and only as a list
        assertThrows(IllegalArgumentException.class, () -> ConfigEdits.apply(text, Map.of("cashier.rates",
                List.of())));
        assertThrows(IllegalArgumentException.class, () -> ConfigEdits.apply(text, Map.of("shop.items", 3)));
        assertThrows(IllegalArgumentException.class, () -> ConfigEdits.apply(text, Map.of("slot.win-chance",
                List.of())));
    }

    @Test
    void theMergeNeverRefillsAListJackShortened(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("config.yml");
        Files.writeString(file, """
                cashier:
                  rates:
                  - item: minecraft:diamond
                    value: 30
                shop:
                  items: []
                """, StandardCharsets.UTF_8);
        byte[] jar = shipped().getBytes(StandardCharsets.UTF_8);
        ConfigLoader.Loaded loaded = ConfigLoader.load(file, () -> new ByteArrayInputStream(jar));
        assertEquals(List.of(new CashierSettings.Rate("minecraft:diamond", 30)), loaded.config().cashier().rates());
        assertEquals(List.of(), loaded.config().cashier().shop());
        assertFalse(loaded.config().cashier().craftable(), "a missing key is merged in");
        // the merged file keeps both lists as Jack left them
        CashierSettings again = CashierSettings.read(YamlFiles.parse(Files.readString(file, StandardCharsets.UTF_8),
                "test"), new ArrayList<>());
        assertEquals(loaded.config().cashier(), again);
        // a live file from 0.8.0 (no cashier at all) gets the whole defaults
        Path old = dir.resolve("old.yml");
        Files.writeString(old, "slot:\n  win-chance: -1\n", StandardCharsets.UTF_8);
        assertEquals(CashierSettings.DEFAULTS, ConfigLoader.load(old, () -> new ByteArrayInputStream(jar)).config()
                .cashier());
    }
}
