package de.tigges.kaboofcasino.admin;

import de.tigges.kaboofcasino.CasinoContext;
import de.tigges.kaboofcasino.chipshop.ChipShopCatalog;
import de.tigges.kaboofcasino.chipshop.ChipShopService;
import de.tigges.kaboofcasino.chipshop.ChipShopSettings;
import de.tigges.kaboofcasino.config.CasinoConfig;
import de.tigges.kaboofcasino.config.ConfigEdits;
import de.tigges.kaboofcasino.config.ConfigLoader;
import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.machine.ItemCatalog;
import de.tigges.kaboofcasino.machine.Machine;
import de.tigges.kaboofcasino.machine.Recipes;
import de.tigges.kaboofcasino.util.ChipText;
import de.tigges.kaboofcasino.util.Keys;
import de.tigges.kaboofcasino.util.Log;
import de.tigges.kaboofcasino.util.Threads;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.api.BinaryTagHolder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

/**
 * The croupier's hand on a Chip-Shop (step 11): its price dialog ({@link ChipShopDialogs}). Each click applies the
 * page's valid inputs: the shown items' overrides are written into {@code chipshop.items} of config.yml
 * ({@link ConfigEdits}: comments, mode and owner kept), then the config is reloaded and every change logged. If the
 * overrides of the shown items changed since the dialog opened (another admin, a reload), nothing is written and the
 * current list is shown. One click per tick; the hand's rate limit. Thread: MAIN.
 */
final class ChipShopEditor implements Listener {

    static final Key KEY = Keys.key("chipshop_prices");
    static final String ID = KEY.asString();

    private record Session(UUID machine, int page, List<ChipShopCatalog.Row> shown, int lastTick) {
        Session {
            shown = List.copyOf(shown);
        }
    }

    private final CasinoContext ctx;
    private final IntSupplier reload;
    private final Predicate<Player> allowed;
    private final Predicate<Player> limited;
    private final Map<UUID, Session> sessions = new HashMap<>();

    ChipShopEditor(CasinoContext ctx, IntSupplier reload, Predicate<Player> allowed, Predicate<Player> limited) {
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

    void forget(Player player) {
        sessions.remove(player.getUniqueId());
    }

    void open(Player player, Machine shop) {
        Threads.requireMain();
        show(player, shop.id(), 0);
    }

    private List<ChipShopCatalog.Row> rows() {
        CasinoConfig c = ctx.config().get();
        return ChipShopCatalog.rows(ItemCatalog.ids(), ChipShopService.pricing(), c.chipshop(), c.cashier());
    }

    private Component name(String id) {
        return Recipes.casinoStack(id, ctx.messages().get(), 1).map(ItemStack::effectiveName)
                .orElse(Component.text(id));
    }

    private Component tab(ChipShopCatalog.Row r) {
        Messages m = ctx.messages().get();
        return m.render(switch (r.category()) {
            case FLOORS -> Msg.CHIPSHOP_CAT_BOEDEN;
            case FURNITURE -> Msg.CHIPSHOP_CAT_MOEBEL;
            case BAR -> Msg.CHIPSHOP_CAT_BAR;
            case LIGHTS -> Msg.CHIPSHOP_CAT_LICHT;
            case TECH -> Msg.CHIPSHOP_CAT_TECHNIK;
            case MACHINES -> Msg.CHIPSHOP_CAT_MASCHINEN;
            case OUTFITS -> Msg.CHIPSHOP_CAT_OUTFITS;
        });
    }

    private void show(Player player, UUID machine, int page) {
        Messages m = ctx.messages().get();
        try {
            List<ChipShopCatalog.Row> rows = rows();
            int p = ChipShopDialogs.clamp(page, rows.size());
            ChipShopDialogs.View v = ChipShopDialogs.view(rows, p, this::name, this::tab, m);
            sessions.put(player.getUniqueId(), new Session(machine, p, ChipShopDialogs.shown(rows, p),
                    Bukkit.getCurrentTick()));
            player.showDialog(dialog(v));
        } catch (RuntimeException e) {
            sessions.remove(player.getUniqueId());
            player.closeDialog();
            String code = Log.incident("chip shop dialog for " + player.getName(), e);
            m.send(player, Msg.ERROR_INTERNAL, Messages.text("code", code));
        }
    }

    private Dialog dialog(ChipShopDialogs.View v) {
        List<DialogBody> body = new ArrayList<>();
        for (ChipShopDialogs.Line line : v.body()) {
            Optional<ItemStack> one = line.item() == null ? Optional.empty()
                    : Recipes.casinoStack(line.item(), ctx.messages().get(), 1);
            if (one.isPresent()) {
                body.add(DialogBody.item(one.get()).description(DialogBody.plainMessage(line.text(),
                        ChipShopDialogs.WIDTH - 40)).showTooltip(true).build());
            } else {
                body.add(DialogBody.plainMessage(line.text(), ChipShopDialogs.WIDTH));
            }
        }
        List<DialogInput> inputs = v.inputs().stream().map(ChipShopEditor::input).toList();
        DialogBase base = DialogBase.builder(v.title())
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .body(body)
                .inputs(inputs)
                .build();
        var type = DialogType.multiAction(v.buttons().stream().map(ChipShopEditor::button).toList())
                .columns(AdminView.COLUMNS)
                .exitAction(button(v.exit()))
                .build();
        return Dialog.create(factory -> factory.empty().base(base).type(type));
    }

    private static DialogInput input(AdminView.Input input) {
        return switch (input) {
            case AdminView.TextInput t -> DialogInput.text(t.key(), t.label())
                    .width(t.width())
                    .initial(t.initial())
                    .maxLength(t.maxLength())
                    .build();
            case AdminView.BoolInput b -> DialogInput.bool(b.key(), b.label())
                    .initial(b.initial())
                    .build();
            case AdminView.RangeInput r -> throw new IllegalArgumentException("no sliders here: " + r.key());
        };
    }

    private static ActionButton button(ChipShopDialogs.Button b) {
        return ActionButton.builder(b.label())
                .width(b.width())
                .action(DialogAction.customClick(KEY, BinaryTagHolder.binaryTagHolder("{" + ChipShopDialogs.DO + ":\""
                        + b.action().id() + "\"}")))
                .build();
    }

    @EventHandler
    public void onCustomClick(PlayerCustomClickEvent event) {
        if (!ID.equals(event.getIdentifier().asString())
                || !(event.getCommonConnection() instanceof PlayerGameConnection connection)) {
            return;
        }
        Player player = connection.getPlayer();
        try {
            click(player, event.getDialogResponseView());
        } catch (RuntimeException e) {
            Log.incident("chip shop dialog click of " + player.getName(), e);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        forget(event.getPlayer());
    }

    void click(Player player, DialogResponseView view) {
        Threads.requireMain();
        Session s = sessions.get(player.getUniqueId());
        if (s == null || view == null) {
            return;
        }
        Messages m = ctx.messages().get();
        Optional<ChipShopDialogs.Action> action = ChipShopDialogs.Action.of(view.getText(ChipShopDialogs.DO));
        if (action.isEmpty()) {
            Log.get().warn("KaboofCasino chip shop dialog: invalid click payload from {} ignored", player.getName());
            return;
        }
        boolean done = action.get() == ChipShopDialogs.Action.DONE;
        if (!done && s.lastTick() == Bukkit.getCurrentTick()) {
            return;
        }
        Optional<Machine> shop = ctx.machines().byId(s.machine());
        if (!allowed.test(player) || shop.isEmpty()) {
            sessions.remove(player.getUniqueId());
            player.closeDialog();
            return;
        }
        if (limited.test(player)) {
            return;
        }
        List<ChipShopCatalog.Row> rows = rows();
        List<ChipShopCatalog.Row> nowShown = ChipShopDialogs.shown(rows, s.page());
        if (!nowShown.equals(s.shown())) {
            m.actionBar(player, Msg.CHIPSHOP_ADMIN_STALE);
            reshow(player, s, done, s.page());
            return;
        }
        Optional<List<ChipShopSettings.Item>> edited = ChipShopDialogs.parse(s.shown(), view::getText,
                view::getBoolean);
        if (edited.isEmpty()) {
            m.actionBar(player, Msg.CHIPSHOP_ADMIN_INVALID, Messages.text("max",
                    ChipText.number(ChipShopSettings.MAX_PRICE)), Messages.text("limit",
                    ChipText.number(ChipShopSettings.MAX_LIMIT)));
            reshow(player, s, false, s.page());
            return;
        }
        List<String> changes = ChipShopDialogs.changes(s.shown(), edited.get());
        if (!changes.isEmpty()) {
            List<ChipShopSettings.Item> before = ctx.config().get().chipshop().items();
            List<ChipShopSettings.Item> after = ChipShopDialogs.merged(before, edited.get());
            if (!write(player, shop.get(), after, changes)) {
                return;
            }
            int n = changes.size();
            m.actionBar(player, Msg.CHIPSHOP_ADMIN_SAVED, Messages.text("changes",
                    ChipText.number(n) + (n == 1 ? " Änderung" : " Änderungen")));
        }
        int pages = ChipShopDialogs.pages(rows.size());
        int page = switch (action.get()) {
            case PREV -> Math.floorMod(s.page() - 1, pages);
            case NEXT -> Math.floorMod(s.page() + 1, pages);
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

    private boolean write(Player player, Machine shop, List<ChipShopSettings.Item> after, List<String> changes) {
        Path file = ctx.plugin().getDataFolder().toPath().resolve(ConfigLoader.FILE);
        try {
            ConfigEdits.write(file, Map.of("chipshop.items", after.stream().map(ChipShopSettings.Item::toYaml)
                    .toList()));
        } catch (IOException | RuntimeException e) {
            String code = Log.incident("chip shop dialog: writing chipshop.items into " + file, e);
            ctx.messages().get().send(player, Msg.ERROR_INTERNAL, Messages.text("code", code));
            return false;
        }
        int warnings = reload.getAsInt();
        Log.get().info("{} changed the chip shop prices at chip shop #{} ({}): {}; config.yml rewritten and reloaded "
                + "({} warning(s))", player.getName(), shop.record().number(), shop.anchor(), String.join("; ",
                changes), warnings);
        return true;
    }
}
