package de.tigges.kaboofcasino;

import de.tigges.kaboofcasino.command.CasinoCommand;
import de.tigges.kaboofcasino.config.CasinoConfig;
import de.tigges.kaboofcasino.config.ConfigLoader;
import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.game.CasinoRandom;
import de.tigges.kaboofcasino.game.plinko.PlinkoController;
import de.tigges.kaboofcasino.game.roulette.RouletteController;
import de.tigges.kaboofcasino.game.slot.SlotController;
import de.tigges.kaboofcasino.game.wheel.WheelController;
import de.tigges.kaboofcasino.gui.DangerSweep;
import de.tigges.kaboofcasino.gui.GuiLayout;
import de.tigges.kaboofcasino.gui.MenuListener;
import de.tigges.kaboofcasino.gui.MenuSafety;
import de.tigges.kaboofcasino.gui.MenuService;
import de.tigges.kaboofcasino.machine.BreakListener;
import de.tigges.kaboofcasino.machine.ChunkListener;
import de.tigges.kaboofcasino.machine.ItemVetoListener;
import de.tigges.kaboofcasino.machine.LogoutSpots;
import de.tigges.kaboofcasino.machine.SafetyListener;
import de.tigges.kaboofcasino.machine.MachineListener;
import de.tigges.kaboofcasino.machine.MachineService;
import de.tigges.kaboofcasino.machine.ProtectionListener;
import de.tigges.kaboofcasino.machine.Recipes;
import de.tigges.kaboofcasino.pack.GamePortPack;
import de.tigges.kaboofcasino.pack.PackFile;
import de.tigges.kaboofcasino.pack.PackHttpServer;
import de.tigges.kaboofcasino.pack.PackListener;
import de.tigges.kaboofcasino.pack.PackSessions;
import de.tigges.kaboofcasino.storage.Database;
import de.tigges.kaboofcasino.storage.MachineIndex;
import de.tigges.kaboofcasino.storage.RoundLog;
import de.tigges.kaboofcasino.util.Log;
import de.tigges.kaboofcasino.util.Threads;
import de.tigges.kaboofcasino.wager.LedgerListener;
import de.tigges.kaboofcasino.wager.WagerEngine;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * KaboofCasino: MineVegas Casino for unmodified 26.3 clients (PLAN.md). Wiring only; the parts are described in
 * docs/IMPLEMENTATION.md.
 *
 * <p>Enable order: logging, config + texts, pack (embedded, re-hashed), GUI layout, SQLite (logs, machine index),
 * bet engine, menus and safety, machines, listeners, recipes, command, pack delivery (game-port listener or HTTP
 * server), already loaded chunks, timers, then for players already online (plugin reload): recipe unlocks, pack
 * status and join recovery of their rounds. Disable order: close menus, settle every round of every online player
 * (STOP; Paper saves the players afterwards, F8), remove rigs, recipes, pack delivery (listener removed, installed
 * sniffers pass everything through), DB.
 */
public final class KaboofCasinoPlugin extends JavaPlugin {

    private volatile CasinoConfig config = CasinoConfig.DEFAULTS;
    private volatile Messages messages;
    private Database db;
    private PackHttpServer http;
    private GamePortPack gamePort;
    private MachineService machines;
    private de.tigges.kaboofcasino.music.MusicService music;
    private de.tigges.kaboofcasino.deco.full.FullDecoService fullDeco;
    private de.tigges.kaboofcasino.deco.display.DisplayDecoService displayDeco;
    private de.tigges.kaboofcasino.croupier.CroupierService croupiers;
    private de.tigges.kaboofcasino.guide.GuideService guide;
    private de.tigges.kaboofcasino.admin.AdminService admin;
    private de.tigges.kaboofcasino.cashier.CashierService cashier;
    private de.tigges.kaboofcasino.board.BoardService boards;
    private de.tigges.kaboofcasino.chipshop.ChipShopService chipShop;
    private de.tigges.kaboofcasino.jukebox.JukeboxService jukebox;
    private LedgerListener ledger;
    private MenuService menus;

    @Override
    public void onLoad() {
        PackHttpServer.configureLimits();
    }

    @Override
    public void onEnable() {
        Log.install(getSLF4JLogger());
        Threads.enableChecks(Bukkit::isPrimaryThread);
        try {
            enable();
        } catch (RuntimeException | IOException | java.sql.SQLException e) {
            Log.get().error("KaboofCasino could not start and disables itself", e);
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    private void enable() throws IOException, java.sql.SQLException {
        int warnings = loadSettings();
        if (warnings > 0) {
            Log.get().warn("{} warning(s) in config.yml/messages_de.yml (see above)", warnings);
        }
        PackFile pack = PackFile.load(getClassLoader());
        PackFile musicPack = PackFile.loadMusic(getClassLoader());
        GuiLayout layout;
        try (InputStream in = getResource("pack/gui-layout.json")) {
            if (in == null) {
                throw new IllegalStateException("pack/gui-layout.json is missing in the plugin jar");
            }
            layout = GuiLayout.load(in);
        }
        Path folder = getDataFolder().toPath();
        java.nio.file.Files.createDirectories(folder);
        db = Database.open(folder.resolve("casino.db"), getClassLoader());
        RoundLog roundLog = new RoundLog(db);
        roundLog.voidOldOpenRounds(System.currentTimeMillis());
        MachineIndex index = new MachineIndex(db);
        index.load();

        WagerEngine engine = new WagerEngine(new WagerEngine.Limits() {
            @Override
            public int maxStake() {
                return config.bets().maxStake();
            }

            @Override
            public boolean capPerRound() {
                return config.bets().capPerRound();
            }

            @Override
            public int maxOpenRounds() {
                return config.rounds().maxOpen();
            }

            @Override
            public Set<String> betItems() {
                return Set.of(de.tigges.kaboofcasino.wager.Chips.VALUE); // stakes are chips only (PLAN-2 C2)
            }
        }, roundLog, System::currentTimeMillis);

        MenuSafety safety = new MenuSafety(() -> config, System::currentTimeMillis);
        DangerSweep sweep = new DangerSweep(() -> config, () -> messages, safety);
        menus = new MenuService(Bukkit::getCurrentTick, sweep, () -> messages);
        LogoutSpots logouts = new LogoutSpots(db);
        logouts.load();
        machines = new MachineService(() -> config, () -> messages, index, System::currentTimeMillis, logouts);
        ledger = new LedgerListener(engine, () -> messages, machines::ownsRound, machines::holdsForReconnect,
                machines::playerLeaving);
        PackSessions sessions = new PackSessions();
        sessions.required(config.pack().enabled());
        CasinoContext ctx = new CasinoContext(this, () -> config, () -> messages, engine, new CasinoRandom(), layout,
                menus, sessions, ledger, machines, System::currentTimeMillis,
                new de.tigges.kaboofcasino.visual.LightBudget(() -> this.config.lights().maxUpdatesPerTick()));
        cashier = new de.tigges.kaboofcasino.cashier.CashierService(ctx,
                new de.tigges.kaboofcasino.cashier.CashierLog(db));
        boards = new de.tigges.kaboofcasino.board.BoardService(ctx, db);
        chipShop = new de.tigges.kaboofcasino.chipshop.ChipShopService(ctx,
                new de.tigges.kaboofcasino.chipshop.ChipShopLog(db));
        jukebox = new de.tigges.kaboofcasino.jukebox.JukeboxService(ctx,
                de.tigges.kaboofcasino.jukebox.JukeboxCatalog.load(getClassLoader()));
        roundLog.afterSettle(boards::roundSettled);
        // Discord (Jack 2026-11-11): only through KaboofDiscord, if it is there
        de.tigges.kaboofcasino.discord.DiscordBridge discord = new de.tigges.kaboofcasino.discord.DiscordBridge();
        boards.discord(discord);
        roundLog.onSettled(new de.tigges.kaboofcasino.discord.BigWins(ctx, discord, boards::hidden)::settled);
        machines.games(machine -> switch (machine.type()) {
            case SLOT_MACHINE -> new SlotController(machine, ctx);
            case FORTUNE_WHEEL -> new WheelController(machine, ctx);
            case PLINKO -> new PlinkoController(machine, ctx);
            case ROULETTE_TABLE -> new RouletteController(machine, ctx);
            case BLACKJACK_TABLE -> new de.tigges.kaboofcasino.game.blackjack.BlackjackController(machine, ctx);
            case POKER_TABLE -> new de.tigges.kaboofcasino.game.poker.PokerController(machine, ctx);
            case DUCK_DERBY -> new de.tigges.kaboofcasino.game.derby.DerbyController(machine, ctx);
            case CASHIER -> cashier.controller(machine);
            case LEADERBOARD_WALL, LEADERBOARD_WALL_2X2,
                    LEADERBOARD_WALL_3X3,
                    LEADERBOARD_WALL_4X3,
                    LEADERBOARD_WALL_4X4,
                    LEADERBOARD_WALL_5X3,
                    LEADERBOARD_WALL_5X4,
                    LEADERBOARD_WALL_6X4,
                    LEADERBOARD_COLUMN -> boards.controller(machine);
            case CHIP_SHOP -> chipShop.controller(machine);
            case JUKEBOX -> jukebox.controller(machine);
        });
        BreakListener breaks = new BreakListener(machines, () -> config, () -> messages);
        de.tigges.kaboofcasino.music.MusicOptOut musicOptOut = new de.tigges.kaboofcasino.music.MusicOptOut(this);
        PackListener packListener = new PackListener(() -> config, () -> messages, pack, musicPack, sessions);
        packListener.musicOptOut(musicOptOut::optedOut);
        for (Listener l : List.of(menus, new MenuListener(menus, Bukkit::getCurrentTick,
                        () -> config.gui().clickGuardTicks(), () -> messages), safety, ledger, packListener, musicOptOut,
                new MachineListener(machines, menus, () -> messages), breaks, new ProtectionListener(machines),
                new ChunkListener(machines), new ItemVetoListener(), new SafetyListener(machines, this), boards)) {
            getServer().getPluginManager().registerEvents(l, this);
        }
        music = new de.tigges.kaboofcasino.music.MusicService(ctx);
        music.silencedBy(jukebox::covers);
        jukebox.optedOut(musicOptOut::optedOut);
        fullDeco = new de.tigges.kaboofcasino.deco.full.FullDecoService(ctx);
        displayDeco = new de.tigges.kaboofcasino.deco.display.DisplayDecoService(ctx, safety, sweep, music);
        music.enable();
        fullDeco.enable();
        displayDeco.enable();
        croupiers = new de.tigges.kaboofcasino.croupier.CroupierService(ctx, displayDeco);
        croupiers.enable();
        guide = new de.tigges.kaboofcasino.guide.GuideService(ctx, sweep);
        guide.enable();
        // the croupier's hand: machine and casino settings; type values are written into config.yml and reloaded
        admin = new de.tigges.kaboofcasino.admin.AdminService(ctx, this::reload);
        admin.enable();
        de.tigges.kaboofcasino.wager.ChipItems.register();
        Recipes.load(getClassLoader());
        cashier.enable();
        boards.enable();
        chipShop.enable();
        jukebox.enable();
        de.tigges.kaboofcasino.outfit.Outfits.enable();
        Recipes.register(messages);
        CasinoCommand command = new CasinoCommand(ctx, roundLog, this::reload, () -> fullDeco.status());
        command.boards(boards);
        command.musicOptOut(musicOptOut);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register("casino", "KaboofCasino (MineVegas Casino)", List.of(), command));

        if (config.pack().enabled() && config.pack().onGamePort()) {
            gamePort = new GamePortPack(List.of(pack, musicPack), getSLF4JLogger());
            gamePort.install();
        } else if (config.pack().enabled()) {
            http = new PackHttpServer(List.of(pack, musicPack));
            try {
                http.start(config.pack().bind(), config.pack().port());
            } catch (IOException e) {
                Log.get().error("The resource pack server cannot listen on {}:{} - clients will fail to download "
                        + "the pack (pack.enforce is {})", config.pack().bind(), config.pack().port(),
                        config.pack().enforce(), e);
                http = null;
            }
        }
        Log.get().info("KaboofCasino pack {} ({} bytes), URL http://{}:{}{} ({}), enforce={}", pack.sha1(),
                pack.size(), config.pack().urlHost(), config.pack().publicPort(), pack.path(), config.pack().serve(),
                config.pack().enforce());
        Log.get().info("KaboofCasino music pack {} ({} bytes), offered: {}", musicPack.sha1(), musicPack.size(),
                config.pack().enabled() && config.music().enabled());

        machines.scanLoaded();
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            long tick = Bukkit.getCurrentTick();
            machines.tick(tick);
            displayDeco.tick(tick);
            croupiers.tick(tick);
            breaks.tick();
            if (tick % config.safety().sweepTicks() == 0) {
                sweep.sweep();
            }
            if (tick % config.owed().deliveryTicks() == 0) {
                ledger.tick();
            }
        }, 1L, 1L);
        // players already online (plugin reload): their pack status and open rounds
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (org.bukkit.inventory.ItemStack stack : player.getInventory().getContents()) {
                ItemVetoListener.unlock(player, stack);
            }
            if (player.getResourcePackStatus() == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {
                sessions.markLoaded(player.getUniqueId());
            }
            if (!player.isDead()) {
                ledger.recover(player);
            }
        }
        Log.get().info("KaboofCasino enabled: {} machine(s) in loaded chunks", machines.all().size());
    }

    /** Loads config.yml and messages_de.yml (merging new keys); returns the number of warnings. */
    private int loadSettings() {
        Path folder = getDataFolder().toPath();
        ConfigLoader.Loaded c = ConfigLoader.load(folder.resolve(ConfigLoader.FILE),
                () -> getResource(ConfigLoader.FILE));
        Messages.Loaded m = Messages.load(folder.resolve(Messages.FILE), () -> getResource(Messages.FILE));
        c.warnings().forEach(w -> Log.get().warn(w));
        m.warnings().forEach(w -> Log.get().warn(w));
        if (c.added() + m.added() > 0) {
            Log.get().info("config.yml/messages_de.yml: added {} new setting(s) from the plugin jar",
                    c.added() + m.added());
        }
        this.config = c.config();
        this.messages = m.messages();
        return c.warnings().size() + m.warnings().size();
    }

    /** {@code /casino reload}: config and texts (not the pack, not the HTTP port). */
    private int reload() {
        try {
            int warnings = loadSettings();
            if (cashier != null) {
                cashier.reloaded();
            }
            if (boards != null) {
                boards.reloaded();
            }
            if (chipShop != null) {
                chipShop.reloaded();
            }
            return fullDeco == null ? warnings : warnings + fullDeco.reloaded();
        } catch (RuntimeException e) {
            Log.get().error("reload failed; the old settings stay", e);
            return 1;
        }
    }

    @Override
    public void onDisable() {
        try {
            if (boards != null) {
                boards.disable();
            }
            if (guide != null) {
                guide.disable();
            }
            if (admin != null) {
                admin.disable();
            }
            if (menus != null) {
                menus.closeAll();
            }
            if (ledger != null) {
                ledger.disable();
            }
            if (machines != null) {
                machines.disable();
            }
            if (croupiers != null) {
                croupiers.disable();
            }
            if (displayDeco != null) {
                displayDeco.disable();
            }
            if (fullDeco != null) {
                fullDeco.disable();
            }
            if (jukebox != null) {
                jukebox.disable();
            }
            if (music != null) {
                music.disable();
            }
            de.tigges.kaboofcasino.machine.ItemCatalog.clear();
            Recipes.unregister();
        } catch (RuntimeException e) {
            Log.incident("disable", e);
        }
        if (gamePort != null) {
            gamePort.remove();
        }
        if (http != null) {
            http.stop();
        }
        if (db != null) {
            db.close();
        }
        Threads.disableChecks();
    }
}
