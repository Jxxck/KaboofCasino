package de.tigges.kaboofcasino.jukebox;

import de.tigges.kaboofcasino.CasinoContext;
import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.machine.CasinoItem;
import de.tigges.kaboofcasino.machine.Machine;
import de.tigges.kaboofcasino.machine.Recipes;
import de.tigges.kaboofcasino.util.Log;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.sound.SoundStop;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

/**
 * The casino jukeboxes (Jack 2026-11-10, not in MineVegas): the catalog, the loaded jukeboxes and what every player
 * hears of them ({@link JukeboxListening}, RECORDS, non-positional like the lounge music). A player hears the nearest
 * playing jukebox whose range covers him; inside it the casino speakers' lounge music is silent ({@link #covers}) and
 * vanilla background music stops. Players who turned the music off ({@code /casino musik aus}) or have no casino pack
 * hear nothing; the lounge genre needs the music pack too. {@code jukebox.enabled: false} silences every jukebox.
 * Thread: MAIN.
 */
public final class JukeboxService implements Listener {

    private final CasinoContext ctx;
    private final JukeboxCatalog catalog;
    private final Map<UUID, JukeboxController> loaded = new HashMap<>();
    private final Map<UUID, JukeboxListening> listeners = new HashMap<>();
    /** Worlds whose jukeboxes changed this tick: their players are checked at the next tick. */
    private final Set<String> changed = new HashSet<>();
    private Predicate<UUID> optedOut = id -> false;
    private BukkitTask task;

    public JukeboxService(CasinoContext ctx, JukeboxCatalog catalog) {
        this.ctx = ctx;
        this.catalog = catalog;
    }

    public JukeboxCatalog catalog() {
        return catalog;
    }

    /** Players who turned the casino music off ({@code MusicOptOut}). */
    public void optedOut(Predicate<UUID> test) {
        this.optedOut = test;
    }

    public JukeboxController controller(Machine machine) {
        return new JukeboxController(machine, ctx, this);
    }

    boolean enabled() {
        return ctx.config().get().jukebox().enabled();
    }

    /**
     * The recipe ({@code kaboofcasino:jukebox}): gold, a casino speaker, gold over dark oak, a diamond, dark oak over
     * three dark oak planks; unlocks with a casino speaker.
     */
    public static Recipes.Def recipe() {
        return new Recipes.Def("jukebox", false, List.of("GSG", "PDP", "PPP"), Map.of('G', "minecraft:gold_ingot",
                'S', "casinosound:casino_speaker", 'P', "minecraft:dark_oak_planks", 'D', "minecraft:diamond"),
                List.of(), "roulettecasino:" + CasinoItem.JUKEBOX.id(), 1, List.of("casinosound:casino_speaker"));
    }

    /** Before {@code Recipes.register}: the recipe, the listeners and the 1-tick task. */
    public void enable() {
        Recipes.own("jukebox", List.of(recipe()));
        Bukkit.getPluginManager().registerEvents(this, ctx.plugin());
        task = Bukkit.getScheduler().runTaskTimer(ctx.plugin(), this::tick, 1L, 1L);
    }

    public void disable() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            JukeboxListening l = listeners.get(p.getUniqueId());
            if (l != null) {
                l.stop(new Out(p));
            }
        }
        listeners.clear();
        HandlerList.unregisterAll(this);
    }

    // ---- the loaded jukeboxes ---------------------------------------------------------------------------------------

    void loaded(JukeboxController jukebox) {
        loaded.put(jukebox.id(), jukebox);
        changed(jukebox);
    }

    void unloaded(JukeboxController jukebox) {
        if (loaded.remove(jukebox.id()) != null) {
            changed(jukebox);
        }
    }

    /** The jukebox changed its track or stopped: its world's players are checked at the next tick. */
    void changed(JukeboxController jukebox) {
        World w = jukebox.jukeboxWorld();
        if (w != null) {
            changed.add(w.getKey().asString());
        }
    }

    /** The nearest playing jukebox whose range covers {@code at}, or null. */
    JukeboxController nearest(Location at) {
        JukeboxController best = null;
        double bestSq = Double.POSITIVE_INFINITY;
        for (JukeboxController j : loaded.values()) {
            if (!j.playing()) {
                continue;
            }
            Location c = j.centre();
            if (c == null || !c.getWorld().equals(at.getWorld())) {
                continue;
            }
            double sq = c.distanceSquared(at);
            if (sq <= (double) j.range() * j.range() && sq < bestSq) {
                best = j;
                bestSq = sq;
            }
        }
        return best;
    }

    /** True if a playing jukebox covers this player: the casino speakers stay silent for him. */
    public boolean covers(Player player) {
        return enabled() && nearest(player.getLocation()) != null;
    }

    // ---- what the players hear --------------------------------------------------------------------------------------

    private void tick() {
        long tick = Bukkit.getCurrentTick();
        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean due = (tick + p.getEntityId()) % JukeboxListening.CHECK_TICKS == 0
                    || changed.contains(p.getWorld().getKey().asString());
            if (due) {
                check(p, tick);
            }
        }
        changed.clear();
    }

    private void check(Player p, long tick) {
        UUID id = p.getUniqueId();
        boolean may = enabled() && !p.isDead() && !optedOut.test(id) && ctx.packs().hasPack(id);
        JukeboxController j = may ? nearest(p.getLocation()) : null;
        JukeboxListening l = listeners.get(id);
        if (j == null) {
            if (l != null) {
                l.stop(new Out(p));
                listeners.remove(id);
            }
            return;
        }
        if (l == null) {
            l = new JukeboxListening();
            listeners.put(id, l);
        }
        JukeboxCatalog.Track t = j.track();
        boolean audible = !t.needsMusicPack() || ctx.packs().hasMusic(id);
        double distance = Math.sqrt(j.centre().distanceSquared(p.getLocation()));
        l.check(tick, new JukeboxListening.Source(j.id(), j.serial(), t.sound(), t.title(), j.elapsedMillis(),
                t.millis(), distance, j.range(), audible), new Out(p));
    }

    private void forget(Player p) {
        JukeboxListening l = listeners.remove(p.getUniqueId());
        if (l != null) {
            l.stop(new Out(p));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        listeners.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        forget(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent event) {
        forget(event.getPlayer());
    }

    /** The sounds of one player. */
    private final class Out implements JukeboxListening.Out {
        private final Player player;

        Out(Player player) {
            this.player = player;
        }

        @Override
        public void play(Key sound, float volume, String title) {
            player.playSound(Sound.sound(sound, Sound.Source.RECORD, volume, 1.0f), Sound.Emitter.self());
            ctx.messages().get().actionBar(player, Msg.JUKEBOX_NOW_PLAYING, Messages.text("title", title));
        }

        @Override
        public void stop(Key sound) {
            player.stopSound(SoundStop.namedOnSource(sound, Sound.Source.RECORD));
        }

        @Override
        public void stopVanillaMusic() {
            player.stopSound(SoundStop.source(Sound.Source.MUSIC));
        }
    }

    void logChange(JukeboxController j, String who, String what) {
        Log.get().info("jukebox #{}: {} {}", j.number(), who, what);
    }
}
