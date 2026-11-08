package de.tigges.kaboofcasino.admin;

import de.tigges.kaboofcasino.CasinoContext;
import de.tigges.kaboofcasino.cashier.CashierSettings;
import de.tigges.kaboofcasino.cashier.Prizes;
import de.tigges.kaboofcasino.cashier.ShopEntry;
import de.tigges.kaboofcasino.config.ConfigEdits;
import de.tigges.kaboofcasino.config.ConfigLoader;
import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.machine.Machine;
import de.tigges.kaboofcasino.util.Log;
import de.tigges.kaboofcasino.util.Threads;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

/**
 * The croupier's hand on a cashier (step 9, PLAN-2 C3): the shop dialog ({@link ShopDialogs}) instead of the machine
 * dialog. Admins in creative change a prize's price and daily limit, remove prizes and add the item in their off
 * hand (the main hand holds the croupier's hand); every change is written into the live {@code config.yml}
 * ({@code shop.items}, {@link ConfigEdits}: comments, mode and owner kept) and applied like {@code /casino reload},
 * and logged with old and new values.
 * <ul>
 *   <li>The target is the server's session (cashier, page, the page's prizes as shown, the off-hand item as shown):
 *       nothing in a click names a prize. If the list changed since the dialog was shown (a reload, another admin),
 *       nothing is applied and the current list is shown ("inzwischen geändert").</li>
 *   <li>At most one click per player and tick, the hand's rate limit (20 edits per game second), the hand's access
 *       rule (creative, {@code kaboofcasino.admin}, the hand held) at every click; a cashier that is gone closes the
 *       dialog.</li>
 * </ul>
 * Thread: MAIN.
 */
final class ShopEditor implements Listener {

    /**
     * An open shop dialog.
     *
     * @param shown    the page's prizes as the dialog showed them
     * @param newSpec  the off-hand item as shown (null: none)
     */
    record Session(UUID machine, int page, List<ShopEntry> shown, String newSpec, int newCount, long lastTick) {
        Session {
            shown = List.copyOf(shown);
        }
    }

    private final CasinoContext ctx;
    private final IntSupplier reload;
    private final Predicate<Player> allowed;
    private final Predicate<Player> limited;
    private final Map<UUID, Session> sessions = new HashMap<>();

    ShopEditor(CasinoContext ctx, IntSupplier reload, Predicate<Player> allowed, Predicate<Player> limited) {
        this.ctx = ctx;
        this.reload = reload;
        this.allowed = allowed;
        this.limited = limited;
    }

    void enable() {
        Bukkit.getPluginManager().registerEvents(this, ctx.plugin());
    }

    void disable() {
        for (UUID id : List.copyOf(sessions.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.closeDialog();
            }
        }
        sessions.clear();
    }

    boolean isOpen(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    /** Forgets the player's dialog (another dialog opened, quit, death). */
    void forget(Player player) {
        sessions.remove(player.getUniqueId());
    }

    /** The hand used on a cashier: its shop dialog at the first page. */
    void open(Player player, Machine cashier) {
        Threads.requireMain();
        show(player, cashier.id(), 0);
        Log.get().debug("{} opened the shop dialog of cashier #{}", player.getName(), cashier.record().number());
    }

    private List<ShopEntry> shop() {
        return ctx.config().get().cashier().shop();
    }

    private void show(Player player, UUID machine, int page) {
        Messages m = ctx.messages().get();
        try {
            List<ShopEntry> shop = shop();
            int p = ShopDialogs.clamp(page, shop.size());
            ItemStack off = player.getInventory().getItemInOffHand();
            Optional<String> spec = Prizes.spec(off);
            Component newName = spec.isPresent() ? off.effectiveName() : Component.empty();
            int newCount = spec.isPresent() ? Math.max(1, Math.min(ShopEntry.MAX_COUNT, off.getAmount())) : 0;
            ShopDialogs.View view = ShopDialogs.view(new ShopDialogs.Data(shop, p, spec.orElse(null), newCount,
                    e -> displayName(e, m), newName), m);
            sessions.put(player.getUniqueId(), new Session(machine, p, ShopDialogs.shown(shop, p), spec.orElse(null),
                    newCount, Bukkit.getCurrentTick()));
            player.showDialog(ShopPaper.dialog(view, m));
        } catch (RuntimeException e) {
            sessions.remove(player.getUniqueId());
            player.closeDialog();
            String code = Log.incident("shop dialog for " + player.getName(), e);
            m.send(player, Msg.ERROR_INTERNAL, Messages.text("code", code));
        }
    }

    private static Component displayName(ShopEntry e, Messages m) {
        return Component.text(e.count() + "× ").append(Prizes.one(e, m).map(ItemStack::effectiveName)
                .orElse(Component.text(e.item())));
    }

    @EventHandler
    public void onCustomClick(PlayerCustomClickEvent event) {
        if (!ShopClick.ID.equals(event.getIdentifier().asString())
                || !(event.getCommonConnection() instanceof PlayerGameConnection connection)) {
            return;
        }
        Player player = connection.getPlayer();
        try {
            click(player, event.getDialogResponseView());
        } catch (RuntimeException e) {
            Log.incident("shop dialog click of " + player.getName(), e);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        forget(event.getPlayer());
    }

    void click(Player player, DialogResponseView view) {
        Threads.requireMain();
        Session s = sessions.get(player.getUniqueId());
        if (s == null) {
            return;
        }
        Messages m = ctx.messages().get();
        Optional<ShopClick> parsed = view == null ? Optional.empty()
                : ShopClick.parse(s.shown().size(), s.newSpec() != null, view::getText, view::getBoolean);
        if (parsed.isEmpty()) {
            Log.get().warn("KaboofCasino shop dialog: invalid click payload from {} ignored", player.getName());
            return;
        }
        ShopClick click = parsed.get();
        boolean done = click.action() == ShopClick.Action.DONE;
        if (!done && s.lastTick() == Bukkit.getCurrentTick()) {
            return; // a second click in one tick
        }
        Optional<Machine> cashier = ctx.machines().byId(s.machine());
        if (!allowed.test(player) || cashier.isEmpty()) {
            sessions.remove(player.getUniqueId());
            player.closeDialog();
            return;
        }
        if (limited.test(player)) {
            return;
        }
        List<ShopEntry> shop = shop();
        if (!ShopDialogs.shown(shop, s.page()).equals(s.shown()) || ShopDialogs.clamp(s.page(), shop.size())
                != s.page()) {
            m.actionBar(player, Msg.SHOP_ADMIN_STALE);
            reshow(player, s, done, s.page());
            return;
        }
        if (click.edits().isEmpty()) {
            m.actionBar(player, Msg.SHOP_ADMIN_INVALID, Messages.text("max", Long.toString(ShopEntry.MAX_PRICE)),
                    Messages.text("limit", Integer.toString(ShopEntry.MAX_LIMIT)));
            reshow(player, s, done, s.page());
            return;
        }
        ShopEntry added = null;
        if (click.action() == ShopClick.Action.ADD) {
            Optional<String> now = Prizes.spec(player.getInventory().getItemInOffHand());
            if (now.isEmpty() || !now.get().equals(s.newSpec())) {
                m.actionBar(player, Msg.SHOP_ADMIN_NO_ITEM);
                reshow(player, s, false, s.page());
                return;
            }
            if (click.added().isEmpty()) {
                m.actionBar(player, Msg.SHOP_ADMIN_INVALID, Messages.text("max", Long.toString(ShopEntry.MAX_PRICE)),
                        Messages.text("limit", Integer.toString(ShopEntry.MAX_LIMIT)));
                reshow(player, s, false, s.page());
                return;
            }
            if (shop.size() >= CashierSettings.MAX_SHOP) {
                m.actionBar(player, Msg.SHOP_ADMIN_FULL, Messages.text("max",
                        Integer.toString(CashierSettings.MAX_SHOP)));
                reshow(player, s, false, s.page());
                return;
            }
            added = new ShopEntry(s.newSpec(), s.newCount(), click.added().get().price(), click.added().get().limit());
        }
        ShopDialogs.Changes changes = ShopDialogs.apply(shop, s.page(), click.edits().get(), added);
        if (!changes.lines().isEmpty() && !write(player, cashier.get(), shop, changes)) {
            return;
        }
        if (added != null) {
            m.actionBar(player, Msg.SHOP_ADMIN_ADDED, Messages.component("item", displayName(added, m)),
                    Messages.text("price", Long.toString(added.price())));
        } else if (!changes.lines().isEmpty()) {
            m.actionBar(player, Msg.SHOP_ADMIN_SAVED, Messages.text("changes",
                    Integer.toString(changes.lines().size())));
        }
        int pages = ShopDialogs.pages(changes.after().size());
        int page = switch (click.action()) {
            case PREV -> Math.floorMod(s.page() - 1, pages);
            case NEXT -> Math.floorMod(s.page() + 1, pages);
            case ADD -> pages - 1;
            default -> s.page();
        };
        reshow(player, s, done, page);
    }

    private void reshow(Player player, Session s, boolean close, int page) {
        if (close) {
            sessions.remove(player.getUniqueId());
            player.closeDialog();
        } else {
            show(player, s.machine(), page);
        }
    }

    /** Writes the new list into config.yml and reloads; logs every change. False (and told) if writing failed. */
    private boolean write(Player player, Machine cashier, List<ShopEntry> before, ShopDialogs.Changes changes) {
        Path file = ctx.plugin().getDataFolder().toPath().resolve(ConfigLoader.FILE);
        try {
            ConfigEdits.write(file, Map.of("shop.items", ShopEntry.toYaml(changes.after())));
        } catch (IOException | RuntimeException e) {
            String code = Log.incident("shop dialog: writing shop.items into " + file, e);
            ctx.messages().get().send(player, Msg.ERROR_INTERNAL, Messages.text("code", code));
            return false;
        }
        int warnings = reload.getAsInt();
        Log.get().info("{} changed the cashier shop at cashier #{} ({}): {}; {} -> {} prizes; config.yml rewritten "
                + "and reloaded ({} warning(s))", player.getName(), cashier.record().number(), cashier.anchor(),
                String.join("; ", changes.lines()), before.size(), changes.after().size(), warnings);
        return true;
    }
}
