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

    /** {@code WorkAtPoi}: a villager works at its job site at most every this many game ticks. */
    public static final long WORK_CHECK_TICKS = 300L;
    /** {@code Villager.allowedToRestock}: the second restock of a day waits this long after the first. */
    public static final long RESTOCK_GAP_TICKS = 2400L;
    /** {@code Villager.shouldRestock}: the restock count resets when this long passed since the last restock. */
    public static final long RESTOCK_RESET_TICKS = 12000L;
    /** {@code Villager.allowedToRestock}: at most this many restocks per day. */
    public static final int RESTOCKS_PER_DAY = 2;
    /** The villager schedule's WORK activity ({@code Schedule.VILLAGER_DEFAULT}): day time 2000 to 9000. */
    public static final long WORK_START = 2000L;
    public static final long WORK_END = 9000L;

    /**
     * Vanilla's restock bookkeeping of a villager ({@code Villager.lastRestockGameTime}, {@code lastRestockCheckDay},
     * {@code numberOfRestocksToday}).
     *
     * @param lastGameTime game time of the last restock (or of the last daily reset)
     * @param lastCheckDay the day of the last check ({@link #day})
     * @param today        restocks since the last reset
     */
    public record RestockState(long lastGameTime, long lastCheckDay, int today) {

        /** A new villager's (all zero). */
        public static final RestockState NONE = new RestockState(0L, 0L, 0);

        public RestockState {
            today = Math.max(0, today);
        }
    }

    /** The result of {@link #shouldRestock}: the state after the check and whether to restock now. */
    public record RestockCheck(RestockState state, boolean restock) {
    }

    /**
     * {@code Villager.shouldRestock} with {@code allowedToRestock} and {@code needsToRestock}: the restock count resets
     * when more than {@value #RESTOCK_RESET_TICKS} game ticks passed since the last restock, or when the day is later
     * than the last check's (only once a check has been made on a day above 0, as vanilla); then a restock is allowed
     * if none was made since the reset, or one was and {@value #RESTOCK_GAP_TICKS} game ticks have passed; it is
     * needed if the trade was used.
     */
    public static RestockCheck shouldRestock(RestockState s, long gameTime, long day, int uses) {
        boolean reset = gameTime > s.lastGameTime() + RESTOCK_RESET_TICKS;
        reset |= s.lastCheckDay() > 0 && day > s.lastCheckDay();
        long last = reset ? gameTime : s.lastGameTime();
        int today = reset ? 0 : s.today();
        boolean allowed = today == 0 || today < RESTOCKS_PER_DAY && gameTime > last + RESTOCK_GAP_TICKS;
        return new RestockCheck(new RestockState(last, day, today), allowed && uses > 0);
    }

    /** {@code Villager.restock}: the uses go back to 0 (done by the caller), the time is noted, one more today. */
    public static RestockState restocked(RestockState s, long gameTime) {
        return new RestockState(gameTime, s.lastCheckDay(), s.today() + 1);
    }

    /** Whether {@code dayTime} lies in the villagers' working hours ({@value #WORK_START} to {@value #WORK_END}). */
    public static boolean workHours(long dayTime) {
        long t = Math.floorMod(dayTime, 24000L);
        return t >= WORK_START && t < WORK_END;
    }

    /**
     * {@code fullTime / 24000}, the day vanilla's restock counts in ({@code Villager.shouldRestock}: the overworld day
     * timeline's period count).
     */
    public static long day(long fullTime) {
        return Math.floorDiv(fullTime, 24000L);
    }
}
