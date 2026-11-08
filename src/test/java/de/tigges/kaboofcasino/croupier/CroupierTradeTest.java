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
    void restockOncePerDayWhileItHasItsCounter() {
        // same day: nothing
        assertEquals(new CroupierTrade.Restock(30, 100, false), CroupierTrade.restock(30, 100, 100, true, false));
        // the next day: stock back, the mark moves
        assertEquals(new CroupierTrade.Restock(0, 101, true), CroupierTrade.restock(30, 100, 101, true, false));
        // several days later: still once
        assertEquals(new CroupierTrade.Restock(0, 140, true), CroupierTrade.restock(64, 100, 140, true, false));
        // nothing was used: the mark moves, nothing to restock (no work sound)
        assertEquals(new CroupierTrade.Restock(0, 101, false), CroupierTrade.restock(0, 100, 101, true, false));
        // without a counter (its job site) or while someone trades: nothing
        assertEquals(new CroupierTrade.Restock(30, 100, false), CroupierTrade.restock(30, 100, 105, false, false));
        assertEquals(new CroupierTrade.Restock(30, 100, false), CroupierTrade.restock(30, 100, 105, true, true));
        // /time set back: only the mark follows, no extra restock
        assertEquals(new CroupierTrade.Restock(30, 90, false), CroupierTrade.restock(30, 100, 90, true, false));
        CroupierTrade.Restock again = CroupierTrade.restock(30, 90, 90, true, false);
        assertFalse(again.restocked());
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
