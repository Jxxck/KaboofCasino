package de.tigges.kaboofcasino.chipshop;

import de.tigges.kaboofcasino.CasinoContext;
import de.tigges.kaboofcasino.cashier.Cashbox;
import de.tigges.kaboofcasino.cashier.CashierLog;
import de.tigges.kaboofcasino.cashier.DailyLimits;
import de.tigges.kaboofcasino.cashier.Prizes;
import de.tigges.kaboofcasino.cashier.ShopEntry;
import de.tigges.kaboofcasino.config.CasinoConfig;
import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.machine.CasinoItem;
import de.tigges.kaboofcasino.machine.ItemCatalog;
import de.tigges.kaboofcasino.machine.Machine;
import de.tigges.kaboofcasino.machine.Recipes;
import de.tigges.kaboofcasino.util.ChipText;
import de.tigges.kaboofcasino.util.Keys;
import de.tigges.kaboofcasino.util.Log;
import de.tigges.kaboofcasino.wager.Chips;
import de.tigges.kaboofcasino.wager.LedgerCodec;
import de.tigges.kaboofcasino.wager.PlayerAccount;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * The Chip-Shop (step 11, PLAN-2 C6): the catalog (rebuilt when the config changes), purchases and the optional
 * recipe. A purchase is the cashier's atomic {@link Cashbox#buy}: every check and the payment plan before the first
 * change, the room check counts the goods and the change, change in chips (what does not fit is owed), the goods into
 * the storage slots; the daily limit counts pieces per item and Berlin day in the player PDC
 * ({@code kaboofcasino:chipshop_limits}). Inventory, ledger and limits are all in the player file. Thread: MAIN.
 */
public final class ChipShopService {

    static final NamespacedKey LIMITS = Keys.key("chipshop_limits");

    private final CasinoContext ctx;
    private final ChipShopLog log;
    private CasinoConfig catalogOf;
    private int catalogIds = -1;
    private ChipShopCatalog.Catalog catalog;
    private Boolean recipe;

    public ChipShopService(CasinoContext ctx, ChipShopLog log) {
        this.ctx = ctx;
        this.log = log;
    }

    public ChipShopController controller(Machine machine) {
        return new ChipShopController(machine, ctx, this);
    }

    public ChipShopSettings settings() {
        return ctx.config().get().chipshop();
    }

    /** The catalog of the current config and item catalog (rebuilt after a reload). */
    public ChipShopCatalog.Catalog catalog() {
        CasinoConfig now = ctx.config().get();
        List<String> ids = ItemCatalog.ids();
        if (now != catalogOf || ids.size() != catalogIds || catalog == null) {
            catalog = ChipShopCatalog.build(ids, Recipes.all(), now.chipshop(), now.cashier());
            catalogOf = now;
            catalogIds = ids.size();
            if (!catalog.unpriced().isEmpty()) {
                Log.get().warn("KaboofCasino chip shop: no price for {} (no recipe and no chipshop.items price); not "
                        + "sold", catalog.unpriced());
            }
        }
        return catalog;
    }

    // ---- the recipe -------------------------------------------------------------------------------------------------

    /**
     * The optional recipe ({@code kaboofcasino:chip_shop}): glass pane, small casino sign, glass pane over black
     * marble bar, chip pile, black marble bar; unlocks with a chip pile.
     */
    static Recipes.Def recipe() {
        return new Recipes.Def("chip_shop", false, List.of("GSG", "BCB"), Map.of('G', "minecraft:glass_pane",
                'S', "casinodeco:small_casino_sign", 'B', "casinodeco:black_marble_bar", 'C',
                "casinodeco:casino_chip_pile"), List.of(), "roulettecasino:" + CasinoItem.CHIP_SHOP.id(), 1,
                List.of("casinodeco:casino_chip_pile"));
    }

    public void enable() {
        recipe = settings().craftable();
        Recipes.own("chipshop", recipe ? List.of(recipe()) : List.of());
    }

    public void reloaded() {
        boolean craftable = settings().craftable();
        if (recipe != null && recipe == craftable) {
            return;
        }
        recipe = craftable;
        Recipes.own("chipshop", craftable ? List.of(recipe()) : List.of());
        Recipes.register(ctx.messages().get());
        Log.get().info("KaboofCasino chip shop recipe {}", craftable ? "registered (chipshop.craftable: true)"
                : "removed (chipshop.craftable: false)");
    }

    // ---- limits -----------------------------------------------------------------------------------------------------

    static LocalDate today(CasinoContext ctx) {
        return DailyLimits.day(Instant.ofEpochMilli(ctx.clock().getAsLong()));
    }

    /** The daily-limit key of an item (16 hex digits, like the cashier's prizes). */
    static String limitKey(String id) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(("chipshop|" + id)
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public DailyLimits limits(Player player) {
        return DailyLimits.read(player.getPersistentDataContainer().get(LIMITS, PersistentDataType.STRING),
                today(ctx));
    }

    /** Pieces of {@code entry} the player may still buy today ({@code Integer.MAX_VALUE} without a limit). */
    public int left(Player player, ChipShopCatalog.Entry entry) {
        return entry.limit() == 0 ? Integer.MAX_VALUE
                : Math.max(0, entry.limit() - limits(player).count(limitKey(entry.id())));
    }

    // ---- buying -----------------------------------------------------------------------------------------------------

    /** One item of {@code id}, as the shop shows and sells it. */
    public Optional<ItemStack> one(String id) {
        return Recipes.casinoStack(id, ctx.messages().get(), 1);
    }

    /** Buys {@code quantity} of {@code entry} (1 or a stack) in one atomic transaction. */
    public Cashbox.Outcome buy(Player player, Machine machine, ChipShopCatalog.Entry entry, int quantity) {
        Messages m = ctx.messages().get();
        Optional<ItemStack> one = one(entry.id());
        if (one.isEmpty() || quantity < 1) {
            m.actionBar(player, Msg.CHIPSHOP_UNAVAILABLE);
            return new Cashbox.Refused(Cashbox.Refusal.NOT_ACCEPTED, entry.id());
        }
        int left = left(player, entry);
        if (quantity > left) {
            m.actionBar(player, Msg.CHIPSHOP_LIMIT, Messages.text("left", ChipText.number(left)));
            return new Cashbox.Refused(Cashbox.Refusal.LIMIT, entry.id());
        }
        long price = entry.total(quantity);
        if (quantity > ShopEntry.MAX_COUNT || price > ShopEntry.MAX_PRICE) {
            // more than a purchase can hold: a normal answer, never an internal error
            m.actionBar(player, Msg.CHIPSHOP_TOO_EXPENSIVE);
            return new Cashbox.Refused(Cashbox.Refusal.NOT_ACCEPTED, entry.id());
        }
        // the cashier's purchase with this article as a one-off prize (no prize limit: the shop counts pieces)
        ShopEntry article = new ShopEntry(entry.id(), quantity, price, 0);
        Cashbox.Outcome outcome;
        try {
            outcome = Cashbox.buy(new PlayerAccount(player), article, Prizes.goods(player, one.get(), quantity),
                    NO_LIMITS, today(ctx));
        } catch (LedgerCodec.CorruptLedgerException e) {
            Log.critical("LEDGER_UNREADABLE", "chip shop refused for {}: {}", player.getName(), e.getMessage());
            m.send(player, Msg.ERROR_INTERNAL, Messages.text("code", "LEDGER"));
            return null;
        }
        if (outcome instanceof Cashbox.Bought done) {
            if (entry.limit() > 0) {
                player.getPersistentDataContainer().set(LIMITS, PersistentDataType.STRING,
                        limits(player).plus(limitKey(entry.id()), quantity).encode());
            }
            log.log(new ChipShopLog.Entry(player.getUniqueId(), player.getName(), machine.id(), entry.id(), quantity,
                    entry.total(1), price, CashierLog.items(done.paid()), done.change(), done.owed(),
                    ctx.clock().getAsLong()));
            if (done.goodsLeft() > 0 || done.owed() > 0) {
                Log.critical("CHIPSHOP_ROOM_CHECK", "chip shop purchase of {}x {} by {}: {} item(s) and {} chips did "
                        + "not fit after the room check (the rest lies at his feet, locked to him; the chips are "
                        + "owed)", quantity, entry.id(), player.getName(), done.goodsLeft(), done.owed());
            }
            m.send(player, Msg.CHIPSHOP_BOUGHT, Messages.text("count", ChipText.number(quantity)),
                    Messages.component("item", one.get().effectiveName()), Messages.text("price",
                            ChipText.chips(price)));
            player.playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
        } else if (outcome instanceof Cashbox.Refused refused) {
            switch (refused.refusal()) {
                case NOT_ENOUGH -> m.actionBar(player, Msg.CHIPSHOP_NOT_ENOUGH, Messages.text("price",
                        ChipText.chips(price)));
                case NO_ROOM -> m.actionBar(player, Msg.CHIPSHOP_NO_ROOM);
                default -> m.actionBar(player, Msg.CHIPSHOP_UNAVAILABLE);
            }
            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
        }
        return outcome;
    }

    /** The cashier's prize limits stay untouched: the shop's article never has one. */
    private static final Cashbox.LimitStore NO_LIMITS = new Cashbox.LimitStore() {
        @Override
        public String read() {
            return null;
        }

        @Override
        public void write(String value) {
            throw new IllegalStateException("the chip shop keeps its own limits");
        }
    };

    /** The chip value the player holds. */
    static long balance(Player player) {
        return new PlayerAccount(player).countPlain(Chips.VALUE);
    }

    static Component name(Optional<ItemStack> one, String id) {
        return one.map(ItemStack::effectiveName).orElse(Component.text(id));
    }
}
