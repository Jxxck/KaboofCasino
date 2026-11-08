package de.tigges.kaboofcasino.cashier;

import de.tigges.kaboofcasino.CasinoContext;
import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.machine.CasinoItem;
import de.tigges.kaboofcasino.machine.Machine;
import de.tigges.kaboofcasino.machine.Recipes;
import de.tigges.kaboofcasino.util.ChipText;
import de.tigges.kaboofcasino.util.Keys;
import de.tigges.kaboofcasino.util.Log;
import de.tigges.kaboofcasino.wager.Chips;
import de.tigges.kaboofcasino.wager.ItemAmount;
import de.tigges.kaboofcasino.wager.LedgerCodec;
import de.tigges.kaboofcasino.wager.PlayerAccount;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * The cashier's server side (PLAN-2 C3): runs the three transactions of {@link Cashbox} on a player with the casino's
 * checks, tells him the outcome, logs it ({@link CashierLog}) and keeps the optional recipe in step with
 * {@code cashier.craftable}. The machines' games are {@link CashierController}s. Thread: MAIN.
 */
public final class CashierService {

    /** Player PDC: today's purchases per prize ({@link DailyLimits}). */
    public static final NamespacedKey LIMITS = Keys.key("shop_limits");

    private final CasinoContext ctx;
    private final CashierLog log;
    private Boolean recipe;

    public CashierService(CasinoContext ctx, CashierLog log) {
        this.ctx = ctx;
        this.log = log;
    }

    public CasinoContext ctx() {
        return ctx;
    }

    public CashierSettings settings() {
        return ctx.config().get().cashier();
    }

    private CashierSettings shopOf;
    private List<ShopEntry> shop = List.of();

    /**
     * The prizes players see: {@code shop.items} without entries whose prize holds chips or casino items in a
     * container or bundle (each logged WARN once). Rebuilt when the config changes (reload, the croupier's hand).
     */
    public List<ShopEntry> shop() {
        CashierSettings now = settings();
        if (now != shopOf) {
            Messages m = ctx.messages().get();
            shop = now.shop().stream().filter(e -> !Prizes.refused(e, m)).toList();
            shopOf = now;
        }
        return shop;
    }

    public CashierController controller(Machine machine) {
        return new CashierController(machine, ctx, this);
    }

    // ---- the recipe -------------------------------------------------------------------------------------------------

    /** The cashier's own recipe ({@code kaboofcasino:cashier}), see {@link CashierSettings#RECIPE}. */
    static Recipes.Def recipe() {
        return new Recipes.Def("cashier", false, CashierSettings.RECIPE, CashierSettings.RECIPE_KEY, List.of(),
                "roulettecasino:" + CasinoItem.CASHIER.id(), 1, List.of(CashierSettings.RECIPE_UNLOCK));
    }

    /**
     * Before {@code Recipes.register} at enable: the cashier's recipe joins the casino's when {@code cashier.craftable}
     * is on.
     */
    public void enable() {
        recipe = settings().craftable();
        Recipes.own(recipe ? List.of(recipe()) : List.of());
    }

    /** After {@code /casino reload} (and the croupier's hand's config edits): registers or removes the recipe. */
    public void reloaded() {
        boolean craftable = settings().craftable();
        if (recipe != null && recipe == craftable) {
            return;
        }
        recipe = craftable;
        Recipes.own(craftable ? List.of(recipe()) : List.of());
        Recipes.register(ctx.messages().get());
        Log.get().info("KaboofCasino cashier recipe {}", craftable ? "registered (cashier.craftable: true)"
                : "removed (cashier.craftable: false)");
    }

    // ---- daily limits -----------------------------------------------------------------------------------------------

    static LocalDate today(CasinoContext ctx) {
        return DailyLimits.day(Instant.ofEpochMilli(ctx.clock().getAsLong()));
    }

    public DailyLimits limits(Player player) {
        return DailyLimits.read(player.getPersistentDataContainer().get(LIMITS, PersistentDataType.STRING),
                today(ctx));
    }

    private static Cashbox.LimitStore store(Player player) {
        return new Cashbox.LimitStore() {
            @Override
            public String read() {
                return player.getPersistentDataContainer().get(LIMITS, PersistentDataType.STRING);
            }

            @Override
            public void write(String value) {
                player.getPersistentDataContainer().set(LIMITS, PersistentDataType.STRING, value);
            }
        };
    }

    // ---- transactions -----------------------------------------------------------------------------------------------

    /** Wechseln. Returns the outcome (null after an unreadable ledger, which the player was told about). */
    public Cashbox.Outcome exchange(Player player, Machine machine, List<ItemAmount> selection) {
        CashierSettings s = settings();
        Cashbox.Outcome outcome = run(player, () -> Cashbox.exchange(new PlayerAccount(player), selection, s::rate));
        if (outcome instanceof Cashbox.Exchanged done) {
            log.log(new CashierLog.Entry(player.getUniqueId(), player.getName(), CashierLog.Kind.EXCHANGE,
                    machine.id(), CashierLog.items(done.taken()), CashierLog.items(Chips.breakdown(done.value())),
                    done.value(), done.owed(), "", ctx.clock().getAsLong()));
            Messages m = ctx.messages().get();
            m.send(player, Msg.CASHIER_EXCHANGED, Messages.component("items", items(done.taken())),
                    Messages.text("value", ChipText.chips(done.value())));
            if (done.owed() > 0) {
                m.send(player, Msg.CASHIER_EXCHANGED_OWED, Messages.text("owed", ChipText.chips(done.owed())));
            }
            success(player);
        } else if (outcome instanceof Cashbox.Refused refused) {
            refuse(player, refused);
        }
        return outcome;
    }

    /** Kleingeld. */
    public Cashbox.Outcome change(Player player, Machine machine, Chips.Selection selection, int target) {
        Cashbox.Outcome outcome = run(player, () -> Cashbox.change(new PlayerAccount(player), selection, target));
        if (outcome instanceof Cashbox.Changed done) {
            log.log(new CashierLog.Entry(player.getUniqueId(), player.getName(), CashierLog.Kind.CHANGE, machine.id(),
                    CashierLog.items(done.taken()), CashierLog.items(done.given()), done.value(), done.owed(),
                    "target " + target, ctx.clock().getAsLong()));
            if (done.owed() > 0) {
                Log.critical("CASHIER_ROOM_CHECK", "change for {}: {} chips did not fit after the room check; kept "
                        + "owed", player.getName(), done.owed());
            }
            ctx.messages().get().send(player, Msg.CASHIER_CHANGED, Messages.text("value",
                    ChipText.chips(done.value())));
            success(player);
        } else if (outcome instanceof Cashbox.Refused refused) {
            refuse(player, refused);
        }
        return outcome;
    }

    /** Preise: one purchase of {@code entry}. */
    public Cashbox.Outcome buy(Player player, Machine machine, ShopEntry entry) {
        Messages m = ctx.messages().get();
        Optional<ItemStack> one = Prizes.one(entry, m);
        if (one.isEmpty()) {
            m.actionBar(player, Msg.SHOP_UNAVAILABLE);
            return new Cashbox.Refused(Cashbox.Refusal.NOT_ACCEPTED, entry.item());
        }
        Cashbox.Outcome outcome = run(player, () -> Cashbox.buy(new PlayerAccount(player), entry,
                Prizes.goods(player, one.get(), entry.count()), store(player), today(ctx)));
        if (outcome instanceof Cashbox.Bought done) {
            List<ItemAmount> out = new ArrayList<>();
            out.add(new ItemAmount(entry.id(), entry.count()));
            out.addAll(Chips.breakdown(done.change()));
            log.log(new CashierLog.Entry(player.getUniqueId(), player.getName(), CashierLog.Kind.BUY, machine.id(),
                    CashierLog.items(done.paid()), CashierLog.items(out), entry.price(), done.owed(),
                    "prize " + entry.key() + " " + entry.item() + ", price " + entry.price() + ", change "
                            + done.change() + (entry.limit() > 0 ? ", today " + done.limits().count(entry.key()) + "/"
                            + entry.limit() : ""), ctx.clock().getAsLong()));
            if (done.goodsLeft() > 0 || done.owed() > 0) {
                Log.critical("CASHIER_ROOM_CHECK", "purchase of {} by {}: {} prize item(s) and {} chips did not fit "
                        + "after the room check (the prize rest lies at his feet, locked to him; the chips are "
                        + "owed)", entry.item(), player.getName(), done.goodsLeft(), done.owed());
            }
            m.send(player, Msg.SHOP_BOUGHT, Messages.component("item", Component.text(entry.count() + "× ")
                    .append(one.get().effectiveName())), Messages.text("price", ChipText.chips(entry.price())));
            success(player);
        } else if (outcome instanceof Cashbox.Refused refused) {
            switch (refused.refusal()) {
                case LIMIT -> m.actionBar(player, Msg.SHOP_LIMIT);
                case NOT_ENOUGH -> m.actionBar(player, Msg.SHOP_NOT_ENOUGH, Messages.text("price",
                        ChipText.chips(entry.price())));
                case NO_ROOM -> m.actionBar(player, Msg.SHOP_NO_ROOM);
                default -> refuse(player, refused);
            }
            if (refused.refusal() != Cashbox.Refusal.LIMIT) {
                fail(player);
            }
        }
        return outcome;
    }

    /** Runs a transaction; an unreadable ledger refuses it (nothing changed) and blocks nothing else here. */
    private Cashbox.Outcome run(Player player, java.util.function.Supplier<Cashbox.Outcome> transaction) {
        try {
            return transaction.get();
        } catch (LedgerCodec.CorruptLedgerException e) {
            Log.critical("LEDGER_UNREADABLE", "cashier refused for {}: {}", player.getName(), e.getMessage());
            ctx.messages().get().send(player, Msg.ERROR_INTERNAL, Messages.text("code", "LEDGER"));
            return null;
        }
    }

    private void refuse(Player player, Cashbox.Refused refused) {
        Messages m = ctx.messages().get();
        switch (refused.refusal()) {
            case EMPTY -> m.actionBar(player, Msg.CASHIER_EMPTY);
            case NOT_ACCEPTED -> m.actionBar(player, Msg.CASHIER_NOT_ACCEPTED, Messages.component("item",
                    name(refused.item())));
            case NOT_ENOUGH -> m.actionBar(player, Msg.CASHIER_NOT_ENOUGH);
            case NO_ROOM -> m.actionBar(player, Msg.CASHIER_NO_ROOM);
            case NOTHING_TO_CHANGE -> m.actionBar(player, Msg.CASHIER_NOTHING_TO_CHANGE);
            case LIMIT -> m.actionBar(player, Msg.SHOP_LIMIT);
        }
        fail(player);
    }

    /** The client's name of an item key (a material; chips by their denomination). */
    static Component name(String item) {
        if (item == null) {
            return Component.text("?");
        }
        java.util.OptionalInt d = Chips.denomination(item);
        if (d.isPresent()) {
            return de.tigges.kaboofcasino.wager.ChipItems.name(d.getAsInt());
        }
        Material material = Material.matchMaterial(item);
        return material == null ? Component.text(item) : Component.translatable(material);
    }

    /** "64× Diamant, 3× Eisenbarren". */
    static Component items(List<ItemAmount> items) {
        return Component.join(JoinConfiguration.commas(true), items.stream()
                .map(a -> Component.text(a.count() + "× ").append(name(a.item()))).toList());
    }

    private static void success(Player player) {
        player.playSound(Sound.sound(Key.key("roulettecasino", "payout"), Sound.Source.MASTER, 0.6f, 1.0f));
    }

    private static void fail(Player player) {
        player.playSound(player, org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
    }
}
