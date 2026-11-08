package de.tigges.kaboofcasino.board;

import de.tigges.kaboofcasino.CasinoContext;
import de.tigges.kaboofcasino.config.CasinoConfig;
import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.machine.CasinoItem;
import de.tigges.kaboofcasino.machine.Machine;
import de.tigges.kaboofcasino.machine.Recipes;
import de.tigges.kaboofcasino.storage.Database;
import de.tigges.kaboofcasino.util.Keys;
import de.tigges.kaboofcasino.util.Log;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

/**
 * The leaderboard (step 10, PLAN-2 C4): one {@link BoardSnapshot} of every category and period, computed on the DB
 * thread from the round log at most every {@code leaderboard.refresh-seconds} (30 s or more) and only after rounds were
 * settled, a player hid or showed himself, the week turned or the settings changed; the boards and
 * {@code /casino top} read the latest snapshot on MAIN and never touch the database. Players hide themselves with
 * {@code /casino bestenliste aus} (PDC flag {@code kaboofcasino:board_hidden}, mirrored into {@code board_hidden} for
 * the DB thread at every change and join). After a week ends its winners go to the server log once
 * ({@code leaderboard.weekly-log}). Thread: MAIN unless noted.
 */
public final class BoardService implements Listener {

    /** Places per list. */
    public static final int TOP = 10;

    static final String HIDDEN = "board_hidden";

    private final CasinoContext ctx;
    private final Database db;
    private final NamespacedKey hiddenKey = Keys.key(HIDDEN);
    /** Rounds were settled or the hidden list changed since the last computation (any thread). */
    private final AtomicBoolean dirty = new AtomicBoolean(true);
    private final AtomicBoolean computing = new AtomicBoolean();
    private final AtomicLong versions = new AtomicLong();
    private volatile BoardSnapshot snapshot;
    private long lastStart = Long.MIN_VALUE / 2;
    private BukkitTask task;
    private Boolean recipe;

    public BoardService(CasinoContext ctx, Database db) {
        this.ctx = ctx;
        this.db = db;
    }

    public BoardController controller(Machine machine) {
        return new BoardController(machine, ctx, this);
    }

    /** The latest computation, or null before the first. Any thread. */
    public BoardSnapshot snapshot() {
        return snapshot;
    }

    /** A round was settled (the round log's hook, DB thread): the next refresh recomputes. */
    public void roundSettled() {
        dirty.set(true);
    }

    private CasinoConfig.Leaderboard settings() {
        return ctx.config().get().leaderboard();
    }

    // ---- lifecycle ------------------------------------------------------------------------------------------------

    /** At enable, before {@code Recipes.register}: the recipes join when craftable; the refresh timer starts. */
    public void enable() {
        recipe = settings().craftable();
        Recipes.own("leaderboard", recipe ? recipes() : List.of());
        task = Bukkit.getScheduler().runTaskTimer(ctx.plugin(), this::refreshIfDue, 40L, 20L);
    }

    /** After {@code /casino reload}: recipes on or off, and a fresh computation (the minimum stake may differ). */
    public void reloaded() {
        boolean craftable = settings().craftable();
        dirty.set(true);
        if (recipe != null && recipe == craftable) {
            return;
        }
        recipe = craftable;
        Recipes.own("leaderboard", craftable ? recipes() : List.of());
        Recipes.register(ctx.messages().get());
        Log.get().info("KaboofCasino leaderboard recipes {}", craftable ? "registered (leaderboard.craftable: true)"
                : "removed (leaderboard.craftable: false)");
    }

    public void disable() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /**
     * The optional recipes (namespace {@code kaboofcasino}): the wall board from gold, a small casino sign and green
     * wool (the felt); the column from a small casino sign, green wool and a black marble bar (the post). Both unlock
     * with a small casino sign.
     */
    static List<Recipes.Def> recipes() {
        return List.of(
                new Recipes.Def("leaderboard_wall", false, List.of("GSG", "WWW"), Map.of('G', "minecraft:gold_ingot",
                        'S', "casinodeco:small_casino_sign", 'W', "minecraft:green_wool"), List.of(),
                        "roulettecasino:" + CasinoItem.LEADERBOARD_WALL.id(), 1,
                        List.of("casinodeco:small_casino_sign")),
                new Recipes.Def("leaderboard_column", false, List.of("S", "W", "B"), Map.of('S',
                        "casinodeco:small_casino_sign", 'W', "minecraft:green_wool", 'B',
                        "casinodeco:black_marble_bar"), List.of(), "roulettecasino:" + CasinoItem.LEADERBOARD_COLUMN.id(),
                        1, List.of("casinodeco:small_casino_sign")));
    }

    // ---- computing ------------------------------------------------------------------------------------------------

    /** Every second: starts a computation when one is due (never two at once). */
    void refreshIfDue() {
        long now = ctx.clock().getAsLong();
        BoardSnapshot s = snapshot;
        boolean weekTurned = s != null && BoardPeriod.weekStart(now) != s.weekStart();
        boolean due = now - lastStart >= settings().refreshSeconds() * 1000L;
        if ((s == null || weekTurned || dirty.get()) && due) {
            refresh(now);
        }
    }

    /** Starts a computation on the DB thread; the result replaces the snapshot. */
    void refresh(long now) {
        if (!computing.compareAndSet(false, true)) {
            return;
        }
        lastStart = now;
        dirty.set(false);
        CasinoConfig.Leaderboard cfg = settings();
        Messages m = ctx.messages().get();
        db.submit("leaderboard", c -> {
            try {
                snapshot = BoardQueries.snapshot(c, now, cfg.minStake(), TOP, versions.incrementAndGet());
                if (cfg.weeklyLog()) {
                    weekly(c, now, cfg.minStake(), m);
                }
            } finally {
                computing.set(false);
            }
        });
    }

    /** The previous week's winners into the server log, once (DB thread). */
    private void weekly(java.sql.Connection c, long now, int minStake, Messages m) throws java.sql.SQLException {
        long thisWeek = BoardPeriod.weekStart(now);
        long lastWeek = BoardPeriod.weekStart(thisWeek - 1);
        if (!BoardQueries.anyRounds(c, lastWeek, thisWeek) || !BoardQueries.claimWeek(c, lastWeek, now)) {
            return;
        }
        List<String> parts = new ArrayList<>();
        for (BoardCategory cat : BoardCategory.values()) {
            List<BoardEntry> top = BoardQueries.top(c, cat, lastWeek, thisWeek, minStake, 3);
            if (top.isEmpty()) {
                continue;
            }
            List<String> names = new ArrayList<>();
            for (int i = 0; i < top.size(); i++) {
                names.add((i + 1) + ". " + top.get(i).name() + " " + BoardView.value(cat, top.get(i).value()));
            }
            parts.add(plain(m.render(BoardView.categoryMsg(cat))) + ": " + String.join(", ", names));
        }
        DateTimeFormatter day = DateTimeFormatter.ofPattern("dd.MM.").withZone(BoardPeriod.ZONE);
        DateTimeFormatter year = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(BoardPeriod.ZONE);
        String week = day.format(Instant.ofEpochMilli(lastWeek)) + "–" + year.format(Instant.ofEpochMilli(thisWeek - 1));
        Log.get().info("KaboofCasino {}", plain(m.render(Msg.BOARD_WEEKLY, Messages.text("week", week),
                Messages.text("lines", String.join(" | ", parts)))));
    }

    static String plain(net.kyori.adventure.text.Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    // ---- hiding ---------------------------------------------------------------------------------------------------

    public boolean hidden(Player player) {
        return player.getPersistentDataContainer().has(hiddenKey, PersistentDataType.BYTE);
    }

    /** {@code /casino bestenliste aus|an}: the PDC flag, its DB mirror, a recomputation at the next refresh. */
    public void hide(Player player, boolean hide) {
        if (hide) {
            player.getPersistentDataContainer().set(hiddenKey, PersistentDataType.BYTE, (byte) 1);
        } else {
            player.getPersistentDataContainer().remove(hiddenKey);
        }
        mirror(player.getUniqueId(), hide);
        Log.get().info("{} {} the leaderboard", player.getName(), hide ? "hid himself from" : "shows himself on");
    }

    private void mirror(UUID player, boolean hide) {
        long now = ctx.clock().getAsLong();
        db.submit("leaderboard hidden " + player, c -> {
            BoardQueries.hide(c, player, hide, now);
            dirty.set(true);
        });
    }

    /** The PDC is authoritative: every join repairs the mirror (a restored player file, a lost DB write). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        mirror(event.getPlayer().getUniqueId(), hidden(event.getPlayer()));
    }

    // ---- /casino top ------------------------------------------------------------------------------------------------

    /** The chat list of {@code /casino top}: header and up to ten lines, or "loading" before the first snapshot. */
    public void top(Player viewer, org.bukkit.command.CommandSender to, BoardCategory category, BoardPeriod period) {
        Messages m = ctx.messages().get();
        BoardSnapshot s = snapshot;
        if (s == null) {
            m.send(to, Msg.BOARD_TOP_LOADING);
            refreshIfDue();
            return;
        }
        m.send(to, Msg.BOARD_TOP_HEADER, Messages.component("category", m.render(BoardView.categoryMsg(category))),
                Messages.component("period", m.render(BoardView.periodMsg(period))),
                Messages.component("unit", BoardView.unit(category, s.minStake(), m)));
        List<BoardEntry> top = s.top(category, period);
        if (top.isEmpty()) {
            m.send(to, Msg.BOARD_TOP_EMPTY);
        }
        for (int i = 0; i < top.size(); i++) {
            BoardEntry e = top.get(i);
            to.sendMessage(m.render(Msg.BOARD_TOP_LINE, Messages.text("rank", Integer.toString(i + 1)),
                    Messages.text("name", e.name()), Messages.text("value", BoardView.value(category, e.value()))));
        }
        if (viewer != null && hidden(viewer)) {
            m.send(to, Msg.BOARD_TOP_YOU_HIDDEN);
        }
    }

    /** Test hook: the snapshot as the DB thread would set it. */
    void snapshot(BoardSnapshot s) {
        snapshot = s;
    }
}
