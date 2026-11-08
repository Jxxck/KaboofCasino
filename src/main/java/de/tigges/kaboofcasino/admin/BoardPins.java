package de.tigges.kaboofcasino.admin;

import de.tigges.kaboofcasino.CasinoContext;
import de.tigges.kaboofcasino.board.BoardCategory;
import de.tigges.kaboofcasino.board.BoardController;
import de.tigges.kaboofcasino.board.BoardPeriod;
import de.tigges.kaboofcasino.board.BoardPin;
import de.tigges.kaboofcasino.board.BoardView;
import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.machine.Machine;
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
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.api.BinaryTagHolder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * The croupier's hand on a leaderboard (step 10): a dialog to pin the board's category and period. One button per
 * choice ("automatisch" and every category; "automatisch", this week, all time), the current ones marked; a click
 * applies at once (saved in the machine record) and shows the dialog again; "Fertig" or ESC closes. Custom click
 * {@code kaboofcasino:board_pin} with {@code {do:"cat:<id>|cat:auto|period:<id>|period:auto|done"}}; nothing in the
 * payload names the board: the target is the session's. Thread: MAIN.
 */
final class BoardPins implements Listener {

    static final Key KEY = Keys.key("board_pin");
    static final String ID = KEY.asString();
    static final String DO = "do";
    static final String DONE = "done";
    static final int WIDTH = 300;

    /** A button: label, its action, whether it is the current choice. */
    record Button(Component label, String action, boolean current) {
    }

    /** The dialog, server-free. */
    record View(Component title, List<Component> body, List<Button> buttons, Button exit) {
        View {
            body = List.copyOf(body);
            buttons = List.copyOf(buttons);
        }
    }

    private record Session(UUID machine, int lastTick) {
    }

    private final CasinoContext ctx;
    private final Predicate<Player> allowed;
    private final Predicate<Player> limited;
    private final Map<UUID, Session> sessions = new HashMap<>();

    BoardPins(CasinoContext ctx, Predicate<Player> allowed, Predicate<Player> limited) {
        this.ctx = ctx;
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

    // ---- the pure part --------------------------------------------------------------------------------------------

    static View view(int number, BoardPin pin, Messages m) {
        Component auto = m.render(Msg.BOARD_PIN_AUTO);
        Component category = pin.category() == null ? auto : m.render(BoardView.categoryMsg(pin.category()));
        Component period = pin.period() == null ? auto : m.render(BoardView.periodMsg(pin.period()));
        List<Component> body = List.of(m.render(Msg.BOARD_PIN_STATE, Messages.component("category", category),
                Messages.component("period", period)), m.render(Msg.BOARD_PIN_HELP));
        List<Button> buttons = new ArrayList<>();
        buttons.add(new Button(m.render(Msg.BOARD_PIN_AUTO_CATEGORY), "cat:auto", pin.category() == null));
        for (BoardCategory c : BoardCategory.values()) {
            buttons.add(new Button(m.render(BoardView.categoryMsg(c)), "cat:" + c.id(), pin.category() == c));
        }
        buttons.add(new Button(m.render(Msg.BOARD_PIN_AUTO_PERIOD), "period:auto", pin.period() == null));
        for (BoardPeriod p : BoardPeriod.values()) {
            buttons.add(new Button(m.render(BoardView.periodMsg(p)), "period:" + p.id(), pin.period() == p));
        }
        return new View(m.render(Msg.BOARD_PIN_TITLE, Messages.text("number", Integer.toString(number))), body,
                buttons, new Button(m.render(Msg.BOARD_PIN_DONE), DONE, false));
    }

    /** The pin after a click, or empty for "done" and anything unknown. */
    static Optional<BoardPin> apply(BoardPin pin, String action) {
        if (action == null) {
            return Optional.empty();
        }
        if (action.equals("cat:auto")) {
            return Optional.of(pin.withCategory(null));
        }
        if (action.equals("period:auto")) {
            return Optional.of(pin.withPeriod(null));
        }
        if (action.startsWith("cat:")) {
            return BoardCategory.byId(action.substring(4)).map(pin::withCategory);
        }
        if (action.startsWith("period:")) {
            return BoardPeriod.byId(action.substring(7)).map(pin::withPeriod);
        }
        return Optional.empty();
    }

    // ---- Paper -------------------------------------------------------------------------------------------------------

    /** Opens the dialog for the board {@code machine} (the hand on a leaderboard). */
    void open(Player player, Machine machine) {
        Threads.requireMain();
        if (!(machine.game() instanceof BoardController board)) {
            return;
        }
        sessions.put(player.getUniqueId(), new Session(machine.id(), Bukkit.getCurrentTick()));
        show(player, machine.record().number(), board.pin());
    }

    private void show(Player player, int number, BoardPin pin) {
        View v = view(number, pin, ctx.messages().get());
        List<DialogBody> body = v.body().stream().map(c -> (DialogBody) DialogBody.plainMessage(c, WIDTH)).toList();
        DialogBase base = DialogBase.builder(v.title())
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .body(body)
                .build();
        var type = DialogType.multiAction(v.buttons().stream().map(BoardPins::button).toList())
                .columns(2)
                .exitAction(button(v.exit()))
                .build();
        player.showDialog(Dialog.create(factory -> factory.empty().base(base).type(type)));
    }

    private static ActionButton button(Button b) {
        Component label = b.current() ? Component.text("» ").append(b.label()).append(Component.text(" «"))
                .color(NamedTextColor.GOLD) : b.label();
        return ActionButton.builder(label)
                .width(140)
                .action(DialogAction.customClick(KEY, BinaryTagHolder.binaryTagHolder("{" + DO + ":\"" + b.action()
                        + "\"}")))
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
            Log.incident("leaderboard pin click of " + player.getName(), e);
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
        String action = view == null ? null : view.getText(DO);
        if (DONE.equals(action)) {
            sessions.remove(player.getUniqueId());
            player.closeDialog();
            return;
        }
        int tick = Bukkit.getCurrentTick();
        if (s.lastTick() == tick || !allowed.test(player) || limited.test(player)) {
            return;
        }
        Optional<Machine> machine = ctx.machines().byId(s.machine());
        if (machine.isEmpty() || !(machine.get().game() instanceof BoardController board)) {
            sessions.remove(player.getUniqueId());
            player.closeDialog();
            return;
        }
        Optional<BoardPin> next = apply(board.pin(), action);
        if (next.isEmpty()) {
            Log.get().warn("KaboofCasino leaderboard pin: invalid click payload from {} ignored", player.getName());
            return;
        }
        sessions.put(player.getUniqueId(), new Session(s.machine(), tick));
        BoardPin before = board.pin();
        board.pinned(next.get());
        Messages m = ctx.messages().get();
        Component auto = m.render(Msg.BOARD_PIN_AUTO);
        BoardPin p = next.get();
        m.actionBar(player, Msg.BOARD_PIN_SAVED, Messages.text("number",
                Integer.toString(machine.get().record().number())), Messages.component("category",
                p.category() == null ? auto : m.render(BoardView.categoryMsg(p.category()))),
                Messages.component("period", p.period() == null ? auto : m.render(BoardView.periodMsg(p.period()))));
        Log.get().info("{} pinned leaderboard #{}: '{}' -> '{}'", player.getName(), machine.get().record().number(),
                before.format(), p.format());
        show(player, machine.get().record().number(), p);
    }
}
