package de.tigges.kaboofcasino.cashier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** Prize entries: what may be a prize, their identity for the limits, the YAML form, the day's counts. */
class ShopEntryTest {

    @Test
    void prizesAreItemsButNeverChips() {
        assertNull(ShopEntry.invalid("roulettecasino:casino_chair"));
        assertNull(ShopEntry.invalid("minecraft:diamond_sword[enchantments={sharpness:5}]"));
        assertNull(ShopEntry.invalid("minecraft:written_book[written_book_content={title:\"Hi\",author:\"Jack\"}]"));
        assertNotNull(ShopEntry.invalid("kaboofcasino:chip_25"));
        assertNotNull(ShopEntry.invalid("kaboofcasino:chips"));
        assertNotNull(ShopEntry.invalid("diamond"));
        assertNotNull(ShopEntry.invalid("Minecraft:Diamond"));
        assertNotNull(ShopEntry.invalid(""));
        assertNotNull(ShopEntry.invalid("minecraft:x" + "[" + "a".repeat(ShopEntry.MAX_ITEM) + "]"));
        assertThrows(IllegalArgumentException.class, () -> new ShopEntry("minecraft:cake", 0, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new ShopEntry("minecraft:cake", 65, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new ShopEntry("minecraft:cake", 1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ShopEntry("minecraft:cake", 1,
                ShopEntry.MAX_PRICE + 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new ShopEntry("minecraft:cake", 1, 1, -1));
        assertTrue(new ShopEntry("casinodeco:cash_register", 1, 1, 0).casino());
        assertFalse(new ShopEntry("minecraft:cake", 1, 1, 0).casino());
        assertFalse(new ShopEntry("roulettecasino:casino_chair[custom_name='x']", 1, 1, 0).casino());
        assertEquals("minecraft:diamond_sword", new ShopEntry("minecraft:diamond_sword[damage=3]", 1, 1, 0).id());
    }

    @Test
    void theLimitKeyFollowsItemAndCountNotPriceOrLimit() {
        ShopEntry e = new ShopEntry("minecraft:cake", 2, 40, 3);
        assertEquals(e.key(), e.withPrice(99).withLimit(1).key());
        assertNotEquals(e.key(), new ShopEntry("minecraft:cake", 3, 40, 3).key());
        assertNotEquals(e.key(), new ShopEntry("minecraft:cake[rarity=epic]", 2, 40, 3).key());
        assertTrue(e.key().matches("[0-9a-f]{16}"));
    }

    @Test
    void theYamlFormRoundTripsThroughBukkitsWriter() throws Exception {
        List<ShopEntry> entries = List.of(new ShopEntry("roulettecasino:casino_chair", 1, 64, 0),
                new ShopEntry("minecraft:diamond_sword[enchantments={sharpness:5,\"minecraft:unbreaking\":3}]", 1,
                        5000, 1),
                new ShopEntry("minecraft:written_book[written_book_content={title:\"Kaboof: #1\",author:'J'}]", 2,
                        123_456_789L / 10, 1000),
                new ShopEntry("minecraft:cake", 64, ShopEntry.MAX_PRICE, 7));
        YamlConfiguration out = new YamlConfiguration();
        out.set("shop.items", ShopEntry.toYaml(entries));
        YamlConfiguration in = new YamlConfiguration();
        in.loadFromString(out.saveToString());
        List<String> warnings = new ArrayList<>();
        assertEquals(entries, CashierSettings.shop(in.get("shop.items"), warnings));
        assertEquals(List.of(), warnings);
    }

    @Test
    void badYamlEntriesAreDroppedWithAWarning() {
        List<Object> list = new ArrayList<>();
        list.add("minecraft:cake");
        list.add(map("item", "minecraft:cake"));
        list.add(map("item", "kaboofcasino:chip_100", "price", 5));
        list.add(map("item", "minecraft:cake", "price", 0));
        list.add(map("item", "minecraft:cake", "price", 2.5));
        list.add(map("item", "minecraft:cake", "price", 5, "count", 99));
        list.add(map("item", "minecraft:cake", "price", 5, "limit", -1));
        list.add(map("item", " Minecraft:Cake ", "price", 5));
        list.add(map("item", "minecraft:cake[custom_name='Kuchen']", "price", 5, "limit", 2, "count", 3));
        List<String> warnings = new ArrayList<>();
        List<ShopEntry> read = CashierSettings.shop(list, warnings);
        assertEquals(List.of(new ShopEntry("minecraft:cake", 1, 5, 0),
                new ShopEntry("minecraft:cake[custom_name='Kuchen']", 3, 5, 2)), read);
        assertEquals(7, warnings.size(), warnings.toString());
        warnings.clear();
        assertEquals(List.of(), CashierSettings.shop("nope", warnings));
        assertEquals(1, warnings.size());
        assertEquals(CashierSettings.DEFAULT_SHOP, CashierSettings.shop(null, new ArrayList<>()));
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    void theDaysCountsReadOnlyOnTheirDay() {
        LocalDate day = LocalDate.of(2026, 11, 8);
        ShopEntry cake = new ShopEntry("minecraft:cake", 1, 5, 3);
        ShopEntry free = new ShopEntry("minecraft:apple", 1, 5, 0);
        DailyLimits l = DailyLimits.empty(day).plus(cake.key()).plus(cake.key());
        assertEquals(2, l.count(cake.key()));
        assertEquals(1, l.left(cake));
        assertEquals(Integer.MAX_VALUE, l.left(free));
        String stored = l.encode();
        assertEquals(l, DailyLimits.read(stored, day));
        assertEquals(DailyLimits.empty(day.plusDays(1)), DailyLimits.read(stored, day.plusDays(1)));
        for (String bad : new String[] {"", "x", "2|" + day + "|", "1|8.11.2026|", "1|" + day + "|abc=1",
                "1|" + day + "|" + cake.key() + "=-1", "1|" + day + "|" + cake.key() + "=x"}) {
            assertEquals(DailyLimits.empty(day), DailyLimits.read(bad, day), bad);
        }
        assertEquals(DailyLimits.empty(day), DailyLimits.read(null, day));
        assertEquals("1|" + day + "|", DailyLimits.empty(day).encode());
        // midnight in Berlin, not UTC: 23:30 UTC on 8 Nov is already the 9th in Berlin (CET, UTC+1)
        assertEquals(LocalDate.of(2026, 11, 9), DailyLimits.day(java.time.Instant.parse("2026-11-08T23:30:00Z")));
        assertEquals(LocalDate.of(2026, 7, 1), DailyLimits.day(java.time.Instant.parse("2026-06-30T22:30:00Z")));
    }
}
