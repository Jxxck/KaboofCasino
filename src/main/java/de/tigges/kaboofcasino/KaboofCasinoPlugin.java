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
import java.util.HashSet;
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
                return new HashSet<>(config.bets().items());
            }
        }, roundLog, System::currentTimeMillis);

        MenuSafety safety = new MenuSafety(() -> config, System::currentTimeMillis);
        DangerSweep sweep = new DangerSweep(() -> config, () -> messages, safety);
        menus = new MenuService(Bukkit::getCurrentTick, sweep, () -> messages);
        machines = new MachineService(() -> config, () -> messages, index, System::currentTimeMillis);
        ledger = new LedgerListener(engine, () -> messages, machines::ownsRound);
        PackSessions sessions = new PackSessions();
        sessions.required(config.pack().enabled());
        CasinoContext ctx = new CasinoContext(this, () -> config, () -> messages, engine, new CasinoRandom(), layout,
                menus, sessions, ledger, machines, System::currentTimeMillis,
                new de.tigges.kaboofcasino.visual.LightBudget(() -> this.config.lights().maxUpdatesPerTick()));
        machines.games(machine -> switch (machine.type()) {
            case SLOT_MACHINE -> new SlotController(machine, ctx);
            case FORTUNE_WHEEL -> new WheelController(machine, ctx);
            case PLINKO -> new PlinkoController(machine, ctx);
            case ROULETTE_TABLE -> new RouletteController(machine, ctx);
        });
        BreakListener breaks = new BreakListener(machines, () -> config, () -> messages);
        for (Listener l : List.of(menus, new MenuListener(menus, Bukkit::getCurrentTick,
                        () -> config.gui().clickGuardTicks(), () -> messages), safety, ledger,
                new PackListener(() -> config, () -> messages, pack, sessions),
                new MachineListener(machines, menus, () -> messages), breaks, new ProtectionListener(machines),
                new ChunkListener(machines), new ItemVetoListener())) {
            getServer().getPluginManager().registerEvents(l, this);
        }
        Recipes.load(getClassLoader());
        Recipes.register(messages);
        CasinoCommand command = new CasinoCommand(ctx, roundLog, this::reload);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register("casino", "KaboofCasino (MineVegas Casino)", List.of(), command));

        if (config.pack().enabled() && config.pack().onGamePort()) {
            gamePort = new GamePortPack(pack, getSLF4JLogger());
            gamePort.install();
        } else if (config.pack().enabled()) {
            http = new PackHttpServer(pack);
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

        machines.scanLoaded();
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            long tick = Bukkit.getCurrentTick();
            machines.tick(tick);
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
            return loadSettings();
        } catch (RuntimeException e) {
            Log.get().error("reload failed; the old settings stay", e);
            return 1;
        }
    }

    @Override
    public void onDisable() {
        try {
            if (menus != null) {
                menus.closeAll();
            }
            if (ledger != null) {
                ledger.disable();
            }
            if (machines != null) {
                machines.disable();
            }
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
