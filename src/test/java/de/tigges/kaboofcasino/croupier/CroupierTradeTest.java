package de.tigges.kaboofcasino.croupier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SplittableRandom;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/** The croupier's trade, its restock and the vanilla villager arithmetic around it. */
class CroupierTradeTest {

    @Test
    void theTradeIsTheModsHiringCardTrade() {
        // data/roulettecasino/villager_trade/croupier/1/hiring_card.json (checked against the jar by the pack tool's
        // CroupierPackTest): wants 24 emeralds, gives 1 hiring card, max_uses 64, xp 2, reputation_discount 0.0
        assertEquals(Material.EMERALD, CroupierTrade.PRICE_ITEM);
        assertEquals(24, CroupierTrade.PRICE);
        assertEquals(64, CroupierTrade.MAX_USES);
        assertEquals(2, CroupierTrade.VILLAGER_XP);
        assertEquals(0.0F, CroupierTrade.PRICE_MULTIPLIER);
        assertEquals(24, CroupierTrade.price(0));
        assertEquals(64, CroupierTrade.clampUses(65));
        assertEquals(0, CroupierTrade.clampUses(-1));
    }

    @Test
    void heroOfTheVillageStillLowersThePrice() {
        assertEquals(0, CroupierTrade.heroDiscount(24, -1));
        assertEquals(7, CroupierTrade.heroDiscount(24, 0)); // floor(0.3 * 24)
        assertEquals(8, CroupierTrade.heroDiscount(24, 1)); // floor(0.3625 * 24)
        assertEquals(13, CroupierTrade.heroDiscount(24, 4)); // floor(0.55 * 24)
        assertEquals(1, CroupierTrade.heroDiscount(2, 0)); // at least 1
        assertEquals(17, CroupierTrade.price(-CroupierTrade.heroDiscount(24, 0)));
        assertEquals(1, CroupierTrade.price(-CroupierTrade.heroDiscount(24, 200)));
    }

    @Test
    void levelsFollowVanillasThresholds() {
        assertEquals(1, CroupierTrade.levelOf(0));
        assertEquals(1, CroupierTrade.levelOf(9));
        assertEquals(2, CroupierTrade.levelOf(10));
        assertEquals(2, CroupierTrade.levelOf(69));
        assertEquals(3, CroupierTrade.levelOf(70));
        assertEquals(4, CroupierTrade.levelOf(150));
        assertEquals(5, CroupierTrade.levelOf(250));
        assertEquals(5, CroupierTrade.levelOf(Integer.MAX_VALUE));
        // 2 per trade: level 2 after the 5th trade
        int xp = 0;
        for (int trade = 1; trade <= 5; trade++) {
            xp += CroupierTrade.VILLAGER_XP;
        }
        assertEquals(2, CroupierTrade.levelOf(xp));
    }

    @Test
    void theTradeOrbIsThreeToSixPlusFiveOnALevelUp() {
        SplittableRandom random = new SplittableRandom(42);
        boolean[] seen = new boolean[7];
        for (int i = 0; i < 1000; i++) {
            int orb = CroupierTrade.tradeOrb(2, 4, random);
            assertTrue(orb >= 3 && orb <= 6, "" + orb);
            seen[orb] = true;
        }
        assertTrue(seen[3] && seen[4] && seen[5] && seen[6]);
        for (int i = 0; i < 100; i++) {
            int orb = CroupierTrade.tradeOrb(8, 10, random);
            assertTrue(orb >= 8 && orb <= 11, "" + orb);
        }
    }

    @Test
    void theVoiceIsAVillagersPitch() {
        SplittableRandom random = new SplittableRandom(7);
        for (int i = 0; i < 1000; i++) {
            float p = CroupierTrade.voicePitch(random);
            assertTrue(p > 0.8F - 1e-6F && p < 1.2F + 1e-6F, "" + p);
        }
    }

    @Test
    void restockFollowsVanillasShouldRestock() {
        CroupierTrade.RestockState s = CroupierTrade.RestockState.NONE;
        // a new croupier, day 5, game time 100 000, one card sold: the first check resets (12 000 ticks since 0) and
        // restocks at once (none today yet)
        CroupierTrade.RestockCheck c = CroupierTrade.shouldRestock(s, 100_000, 5, 1);
        assertTrue(c.restock());
        assertEquals(new CroupierTrade.RestockState(100_000, 5, 0), c.state());
        s = CroupierTrade.restocked(c.state(), 100_000);
        assertEquals(new CroupierTrade.RestockState(100_000, 5, 1), s);
        // nothing used: no restock (and nothing counts)
        assertFalse(CroupierTrade.shouldRestock(s, 103_000, 5, 0).restock());
        // the second restock of the day: not before 2 400 ticks after the first, then once
        assertFalse(CroupierTrade.shouldRestock(s, 102_400, 5, 3).restock());
        c = CroupierTrade.shouldRestock(s, 102_401, 5, 3);
        assertTrue(c.restock());
        s = CroupierTrade.restocked(c.state(), 102_401);
        assertEquals(2, s.today());
        // a third one the same day: never, however long it waits within 12 000 ticks
        assertFalse(CroupierTrade.shouldRestock(s, 110_000, 5, 64).restock());
        assertFalse(CroupierTrade.shouldRestock(s, 114_401, 5, 64).restock());
        // more than 12 000 ticks after the last restock: the count resets (vanilla), one more is allowed
        c = CroupierTrade.shouldRestock(s, 114_402, 5, 64);
        assertTrue(c.restock());
        assertEquals(new CroupierTrade.RestockState(114_402, 5, 0), c.state());
        // a later day resets as well, even right after a restock (/time add)
        s = CroupierTrade.restocked(c.state(), 114_402);
        s = CroupierTrade.restocked(s, 117_000);
        assertFalse(CroupierTrade.shouldRestock(s, 117_100, 5, 9).restock());
        c = CroupierTrade.shouldRestock(s, 117_100, 6, 9);
        assertTrue(c.restock());
        assertEquals(new CroupierTrade.RestockState(117_100, 6, 0), c.state());
        // vanilla quirk: on day 0 no check has a day to compare with (lastCheckDay > 0 is required)
        CroupierTrade.RestockState d0 = new CroupierTrade.RestockState(500, 0, 2);
        assertFalse(CroupierTrade.shouldRestock(d0, 600, 1, 9).restock());
        assertEquals(1, CroupierTrade.shouldRestock(d0, 600, 1, 9).state().lastCheckDay());
        // a day that went backwards (/time set) only moves the mark
        CroupierTrade.RestockState back = new CroupierTrade.RestockState(1_000, 9, 2);
        c = CroupierTrade.shouldRestock(back, 1_100, 7, 9);
        assertFalse(c.restock());
        assertEquals(new CroupierTrade.RestockState(1_000, 7, 2), c.state());
    }

    @Test
    void itWorksInTheVillagersWorkingHours() {
        assertFalse(CroupierTrade.workHours(0));
        assertFalse(CroupierTrade.workHours(1_999));
        assertTrue(CroupierTrade.workHours(2_000));
        assertTrue(CroupierTrade.workHours(8_999));
        assertFalse(CroupierTrade.workHours(9_000));
        assertTrue(CroupierTrade.workHours(24_000L * 1391 + 6_000));
        assertFalse(CroupierTrade.workHours(-1));
    }

    @Test
    void daysCountInFullTime() {
        assertEquals(0, CroupierTrade.day(0));
        assertEquals(0, CroupierTrade.day(23_999));
        assertEquals(1, CroupierTrade.day(24_000));
        assertEquals(1391, CroupierTrade.day(33_389_712L));
        assertEquals(-1, CroupierTrade.day(-1));
    }
}
