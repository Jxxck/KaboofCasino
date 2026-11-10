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

    /** Places per list shown. */
    public static final int TOP = 10;
    /**
     * Places per list computed: a few more than shown, so players who hide themselves between two computations drop
     * out at once and the list still shows ten.
     */
    static final int STORED = 15;
    /** {@code /casino bestenliste} at most every 3 s per player. */
    static final long HIDE_GAP_MS = 3000;

    static final String HIDDEN = "board_hidden";

    private final CasinoContext ctx;
    private final Database db;
    private final NamespacedKey hiddenKey = Keys.key(HIDDEN);
    /** Rounds were settled or the hidden list changed since the last computation (any thread). */
    private final AtomicBoolean dirty = new AtomicBoolean(true);
    private final AtomicBoolean computing = new AtomicBoolean();
    private final AtomicLong versions = new AtomicLong();
    /** Players who hid themselves (PDC or mirror), known on MAIN at once; filled from the mirror at enable. */
    private final java.util.Set<UUID> hiddenNow = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastToggle = new java.util.HashMap<>();
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
        db.submit("leaderboard hidden players", c -> hiddenNow.addAll(BoardQueries.allHidden(c)));
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
     * with a small casino sign. The other sizes grow from a smaller board with gold and green wool: a row more with the
     * wool on top, a column more with the wool beside it; the 2 x 2 is a short version of the wall board's recipe.
     */
    public static List<Recipes.Def> recipes() {
        return List.of(
                new Recipes.Def("leaderboard_wall_2x2", false, List.of("GS", "WW"), Map.of('G', "minecraft:gold_ingot",
                        'S', "casinodeco:small_casino_sign", 'W', "minecraft:green_wool"), List.of(),
                        "roulettecasino:" + CasinoItem.LEADERBOARD_WALL_2X2.id(), 1,
                        List.of("casinodeco:small_casino_sign")),
                taller(CasinoItem.LEADERBOARD_WALL, CasinoItem.LEADERBOARD_WALL_3X3),
                wider(CasinoItem.LEADERBOARD_WALL_3X3, CasinoItem.LEADERBOARD_WALL_4X3),
                taller(CasinoItem.LEADERBOARD_WALL_4X3, CasinoItem.LEADERBOARD_WALL_4X4),
                wider(CasinoItem.LEADERBOARD_WALL_4X3, CasinoItem.LEADERBOARD_WALL_5X3),
                wider(CasinoItem.LEADERBOARD_WALL_4X4, CasinoItem.LEADERBOARD_WALL_5X4),
                wider(CasinoItem.LEADERBOARD_WALL_5X4, CasinoItem.LEADERBOARD_WALL_6X4),
                new Recipes.Def("leaderboard_wall", false, List.of("GSG", "WWW"), Map.of('G', "minecraft:gold_ingot",
                        'S', "casinodeco:small_casino_sign", 'W', "minecraft:green_wool"), List.of(),
                        "roulettecasino:" + CasinoItem.LEADERBOARD_WALL.id(), 1,
                        List.of("casinodeco:small_casino_sign")),
                new Recipes.Def("leaderboard_column", false, List.of("S", "W", "B"), Map.of('S',
                        "casinodeco:small_casino_sign", 'W', "minecraft:green_wool", 'B',
                        "casinodeco:black_marble_bar"), List.of(), "roulettecasino:" + CasinoItem.LEADERBOARD_COLUMN.id(),
                        1, List.of("casinodeco:small_casino_sign")));
    }

    private static Recipes.Def taller(CasinoItem from, CasinoItem to) {
        return grown(List.of("WWW", "GBG"), from, to);
    }

    private static Recipes.Def wider(CasinoItem from, CasinoItem to) {
        return grown(List.of("GW", "BW", "GW"), from, to);
    }

    private static Recipes.Def grown(List<String> pattern, CasinoItem from, CasinoItem to) {
        String board = "roulettecasino:" + from.id();
        return new Recipes.Def(to.id(), false, pattern, Map.of('G', "minecraft:gold_ingot", 'W', "minecraft:green_wool",
                'B', board), List.of(), "roulettecasino:" + to.id(), 1, List.of(board));
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

    /**
     * Starts a computation on the DB thread; the result replaces the snapshot. Whatever happens (the job fails, the
     * executor refuses it), {@code computing} is released and a failed computation leaves the data marked dirty, so
     * the next second tries again instead of freezing the boards.
     */
    void refresh(long now) {
        if (!computing.compareAndSet(false, true)) {
            return;
        }
        lastStart = now;
        dirty.set(false);
        CasinoConfig.Leaderboard cfg = settings();
        Messages m = ctx.messages().get();
        try {
            db.submit("leaderboard", c -> {
                boolean done = false;
                try {
                    snapshot = BoardQueries.snapshot(c, now, cfg.minStake(), STORED, versions.incrementAndGet());
                    done = true;
                    if (cfg.weeklyLog()) {
                        weekly(c, now, cfg.minStake(), m);
                    }
                } finally {
                    if (!done) {
                        dirty.set(true);
                    }
                    computing.set(false);
                }
            });
        } catch (RuntimeException e) {
            dirty.set(true);
            computing.set(false);
            Log.get().warn("KaboofCasino leaderboard: computation not started ({})", e.toString());
        }
    }

    /** {@code list} without the players who hid themselves, at most {@link #TOP} places. Thread: MAIN. */
    public List<BoardEntry> visible(List<BoardEntry> list) {
        return visible(list, hiddenNow, TOP);
    }

    /** The first {@code top} entries of {@code list} whose players are not in {@code hidden}. Pure. */
    static List<BoardEntry> visible(List<BoardEntry> list, java.util.Set<UUID> hidden, int top) {
        List<BoardEntry> out = new ArrayList<>(top);
        for (BoardEntry e : list) {
            if (!hidden.contains(e.player())) {
                out.add(e);
                if (out.size() == top) {
                    break;
                }
            }
        }
        return out;
    }

    /** The previous week's winners into the server log, once (DB thread). */
    private void weekly(java.sql.Connection c, long now, int minStake, Messages m) throws java.sql.SQLException {
        long thisWeek = BoardPeriod.weekStart(now);
        long lastWeek = BoardPeriod.weekStart(thisWeek - 1);
        if (!BoardQueries.anyRounds(c, lastWeek, thisWeek) || !BoardQueries.claimWeek(c, lastWeek, now)) {
            return;
        }
        List<String> parts = new ArrayList<>();
        List<BoardQueries.Play> plays = BoardQueries.plays(c, lastWeek, thisWeek);
        Map<UUID, String> names = BoardQueries.names(c);
        for (BoardCategory cat : BoardCategory.values()) {
            List<BoardEntry> top = BoardQueries.aggregate(plays, cat, lastWeek, minStake, 3, names);
            if (top.isEmpty()) {
                continue;
            }
            List<String> places = new ArrayList<>();
            for (int i = 0; i < top.size(); i++) {
                places.add((i + 1) + ". " + top.get(i).name() + " " + BoardView.value(cat, top.get(i).value()));
            }
            parts.add(plain(m.render(BoardView.categoryMsg(cat))) + ": " + String.join(", ", places));
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
        return player.getPersistentDataContainer().has(hiddenKey, PersistentDataType.BYTE)
                || hiddenNow.contains(player.getUniqueId());
    }

    /** False (and nothing done) if the player toggled less than 3 s ago. Thread: MAIN. */
    public boolean mayToggle(Player player) {
        long now = ctx.clock().getAsLong();
        Long last = lastToggle.get(player.getUniqueId());
        if (last != null && now - last < HIDE_GAP_MS) {
            return false;
        }
        lastToggle.put(player.getUniqueId(), now);
        return true;
    }

    /**
     * {@code /casino bestenliste aus|an}: the PDC flag and its DB mirror. Takes effect at once: the boards and
     * {@code /casino top} drop a hidden player from the lists they already hold, and a computation starts now.
     */
    public void hide(Player player, boolean hide) {
        if (hide) {
            player.getPersistentDataContainer().set(hiddenKey, PersistentDataType.BYTE, (byte) 1);
            hiddenNow.add(player.getUniqueId());
        } else {
            player.getPersistentDataContainer().remove(hiddenKey);
            hiddenNow.remove(player.getUniqueId());
        }
        mirror(player.getUniqueId(), hide, true);
        Log.get().info("{} {} the leaderboard", player.getName(), hide ? "hid himself from" : "shows himself on");
    }

    /** Writes the mirror; marks the data dirty only if the row changed (and recomputes at once if asked). */
    private void mirror(UUID player, boolean hide, boolean now) {
        long at = ctx.clock().getAsLong();
        db.submit("leaderboard hidden " + player, c -> {
            if (BoardQueries.hide(c, player, hide, at)) {
                dirty.set(true);
                if (now) {
                    Bukkit.getScheduler().runTask(ctx.plugin(), () -> {
                        lastStart = Long.MIN_VALUE / 2;
                        refreshIfDue();
                    });
                }
            }
        });
    }

    /**
     * Either the PDC flag or the mirror hides a player (a kill -9 can lose the PDC change, a restored DB the mirror);
     * the join sets both to "hidden" then. Only {@code /casino bestenliste an} clears them.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        boolean pdc = player.getPersistentDataContainer().has(hiddenKey, PersistentDataType.BYTE);
        if (pdc) {
            hiddenNow.add(id);
            mirror(id, true, false);
            return;
        }
        db.submit("leaderboard hidden at join " + id, c -> {
            if (BoardQueries.hidden(c, id)) {
                Bukkit.getScheduler().runTask(ctx.plugin(), () -> {
                    hiddenNow.add(id);
                    Player p = Bukkit.getPlayer(id);
                    if (p != null) {
                        p.getPersistentDataContainer().set(hiddenKey, PersistentDataType.BYTE, (byte) 1);
                    }
                });
            }
        });
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
        List<BoardEntry> top = visible(s.top(category, period));
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
