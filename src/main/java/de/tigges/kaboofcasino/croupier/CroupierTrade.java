package de.tigges.kaboofcasino.croupier;

import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.machine.CasinoItem;
import de.tigges.kaboofcasino.machine.CasinoItems;
import java.util.List;
import java.util.random.RandomGenerator;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;

/**
 * The croupier's one trade (games.md 10.2, {@code data/roulettecasino/villager_trade/croupier/1/hiring_card.json}): 24
 * emeralds for a hiring card, 64 uses, 2 villager experience, reputation discount (price multiplier) 0, so demand and
 * gossip never change the price; Hero of the Village still lowers it as in vanilla ({@link #heroDiscount}). Plus the
 * vanilla villager arithmetic the trade screen of a real croupier would run: levels, the trade's experience orb, the
 * voice pitch, the restock. Pure apart from {@link #recipe}.
 */
public final class CroupierTrade {

    public static final Material PRICE_ITEM = Material.EMERALD;
    public static final int PRICE = 24;
    public static final int MAX_USES = 64;
    /** Villager experience per trade ({@code "xp": 2}; the mod's {@code CroupierOfferXpMixin} floor is 2 as well). */
    public static final int VILLAGER_XP = 2;
    /** {@code "reputation_discount": 0.0}. */
    public static final float PRICE_MULTIPLIER = 0.0F;
    /** {@code VillagerData.NEXT_LEVEL_XP_THRESHOLDS}. */
    static final int[] LEVEL_XP = {0, 10, 70, 150, 250};
    /** {@code Mob.getAmbientSoundInterval} (villagers do not override it). */
    public static final int AMBIENT_INTERVAL = 80;

    private CroupierTrade() {
    }

    /** The trade as the merchant screen shows it, with {@code uses} used and {@code specialPrice} (negative = off). */
    public static MerchantRecipe recipe(Messages messages, int uses, int specialPrice) {
        ItemStack card = CasinoItems.create(CasinoItem.CROUPIER_HIRING_CARD, messages, 1);
        // experienceReward false: the player's orb comes from the croupier (vanilla rewardTradeXp), not from Paper's
        // custom merchant (which would drop the villager experience at the player)
        MerchantRecipe r = new MerchantRecipe(card, clampUses(uses), MAX_USES, false, VILLAGER_XP, PRICE_MULTIPLIER, 0,
                specialPrice, false);
        r.setIngredients(List.of(ItemStack.of(PRICE_ITEM, PRICE)));
        return r;
    }

    static int clampUses(int uses) {
        return Math.max(0, Math.min(MAX_USES, uses));
    }

    /**
     * Vanilla's Hero of the Village discount ({@code Villager.updateSpecialPrices}): {@code max(floor((0.3 + 0.0625 *
     * amplifier) * cost), 1)} emeralds off, independent of the price multiplier; 0 without the effect
     * ({@code amplifier < 0}).
     */
    public static int heroDiscount(int cost, int amplifier) {
        if (amplifier < 0) {
            return 0;
        }
        double d = 0.3 + 0.0625 * amplifier;
        return Math.max((int) Math.floor(d * cost), 1);
    }

    /** The price the screen shows: {@code MerchantOffer.getCostA} with demand 0 (1..64, an emerald stack). */
    static int price(int specialPrice) {
        return Math.max(1, Math.min(64, PRICE + specialPrice));
    }

    /** The villager level of {@code xp} (1..5): a level is reached at 10, 70, 150, 250 experience. */
    public static int levelOf(int xp) {
        int level = 1;
        while (level < 5 && xp >= LEVEL_XP[level]) {
            level++;
        }
        return level;
    }

    /**
     * The experience orb a trade drops at the croupier ({@code Villager.rewardTradeXp}): 3 + 0..3, and 5 more when the
     * trade's villager experience reaches the next level.
     */
    public static int tradeOrb(int xpBefore, int xpAfter, RandomGenerator random) {
        int orb = 3 + random.nextInt(4);
        return levelOf(xpAfter) > levelOf(xpBefore) ? orb + 5 : orb;
    }

    /** {@code LivingEntity.getVoicePitch} of an adult. */
    public static float voicePitch(RandomGenerator random) {
        return (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F;
    }

    /** The result of a restock check. */
    public record Restock(int uses, long day, boolean restocked) {
    }

    /**
     * Once per Minecraft day while it has its counter (its job site) and nobody trades, the croupier restocks: uses
     * back to 0. A day that went backwards ({@code /time set}) only moves the mark. Without a counter nothing changes.
     */
    public static Restock restock(int uses, long lastDay, long today, boolean hasCounter, boolean trading) {
        if (!hasCounter || trading) {
            return new Restock(uses, lastDay, false);
        }
        if (today < lastDay) {
            return new Restock(uses, today, false);
        }
        if (today == lastDay) {
            return new Restock(uses, lastDay, false);
        }
        return new Restock(0, today, uses > 0);
    }

    /** {@code fullTime / 24000}, the day vanilla's restock counts in ({@code Villager.shouldRestock}). */
    public static long day(long fullTime) {
        return Math.floorDiv(fullTime, 24000L);
    }
}
