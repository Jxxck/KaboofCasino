package de.tigges.kaboofcasino.music;

import de.tigges.kaboofcasino.CasinoContext;
import de.tigges.kaboofcasino.machine.Facing;
import de.tigges.kaboofcasino.util.Log;
import de.tigges.kaboofcasino.util.Pos;
import de.tigges.kaboofcasino.util.Threads;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.sound.SoundStop;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.scheduler.BukkitTask;

/**
 * The mod's lounge music ({@code casinosound}, games.md 15) for players who loaded the optional music pack.
 * <ul>
 *   <li>Per world a {@link MusicWorld}: the station (epoch, seed, switched-on speakers; world PDC, see
 *       {@link StationStore}) and the loaded switched-on speakers in a {@link SpeakerGrid}. The speaker block calls
 *       the {@link SpeakerHooks}.</li>
 *   <li>Per player with the music pack a {@link Playback}: checked every {@link Playback#CHECK_TICKS} (staggered by
 *       entity id), when his track ends, and in the tick after a speaker of his world changed. The nearest speaker
 *       comes from the grid (at most 3 x 3 buckets), so the cost is players x nearby speakers, never players x all
 *       speakers per tick.</li>
 *   <li>Note particles like the mod's client, for everybody near a switched-on speaker (vanilla particles, no pack
 *       needed). Players without the music pack get no sound calls at all.</li>
 *   <li>{@code music.enabled: false} stops all music and particles at the next tick; the stations keep being
 *       maintained, so switching it on again resumes the same playlists.</li>
 * </ul>
 * Thread: MAIN (hooks, events, the 1-tick task).
 */
public final class MusicService implements SpeakerHooks, Listener {

    /** {@code casinosound:<id>} for every track of {@link MusicTimeline#IDS}. */
    static final Key[] TRACK_KEYS = new Key[MusicTimeline.IDS.length];

    static {
        for (int i = 0; i < TRACK_KEYS.length; i++) {
            TRACK_KEYS[i] = Key.key("casinosound", MusicTimeline.IDS[i]);
        }
    }

    /** A loaded world and its music. */
    private record Entry(World world, MusicWorld music) {
    }

    private final CasinoContext ctx;
    private final Map<String, Entry> worlds = new HashMap<>();
    private final Map<UUID, Playback> playbacks = new HashMap<>();
    /** Worlds whose speakers changed this tick: their listeners are checked at the next tick. */
    private final Set<String> changed = new HashSet<>();
    private BukkitTask task;
    /** Players a casino jukebox covers: the speakers' lounge music stays silent for them. */
    private java.util.function.Predicate<Player> silenced = p -> false;

    public MusicService(CasinoContext ctx) {
        this.ctx = ctx;
    }

    /** A playing jukebox wins over the speakers within its range. */
    public void silencedBy(java.util.function.Predicate<Player> test) {
        this.silenced = test;
    }

    /** Registers listeners and tasks. */
    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, ctx.plugin());
        for (World w : Bukkit.getWorlds()) {
            entry(w);
        }
        task = Bukkit.getScheduler().runTaskTimer(ctx.plugin(), this::tick, 1L, 1L);
    }

    /** Stops every player's music. */
    public void disable() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        stopAll();
        for (Entry e : worlds.values()) {
            save(e);
        }
        worlds.clear();
        HandlerList.unregisterAll(this);
    }

    // ---- speaker hooks ----------------------------------------------------------------------------------------------

    @Override
    public void enable(Pos pos, Facing facing) {
        Threads.requireMain();
        Entry e = entry(pos.world());
        if (e != null) {
            e.music().enable(cell(pos), facing, e.world().getGameTime(), seeds());
            changed(e);
        }
    }

    @Override
    public void disable(Pos pos) {
        Threads.requireMain();
        Entry e = entry(pos.world());
        if (e != null) {
            e.music().disable(cell(pos));
            changed(e);
        }
    }

    @Override
    public void loaded(Pos pos, Facing facing, boolean on) {
        Threads.requireMain();
        Entry e = entry(pos.world());
        if (e != null) {
            e.music().loaded(cell(pos), facing, on, e.world().getGameTime(), seeds());
            changed(e);
        }
    }

    @Override
    public void unloaded(Pos pos) {
        Threads.requireMain();
        Entry e = worlds.get(pos.world());
        if (e != null) {
            e.music().unloaded(cell(pos));
            changed(e);
        }
    }

    private static MusicStation.Cell cell(Pos pos) {
        return new MusicStation.Cell(pos.x(), pos.y(), pos.z());
    }

    private LongSupplier seeds() {
        return () -> ctx.random().nextLong();
    }

    private void changed(Entry e) {
        save(e);
        changed.add(e.world().getKey().asString());
    }

    private static void save(Entry e) {
        if (e.music().station().takeDirty()) {
            StationStore.write(e.world().getPersistentDataContainer(), e.music().station());
        }
    }

    private Entry entry(String worldKey) {
        Entry e = worlds.get(worldKey);
        if (e != null) {
            return e;
        }
        NamespacedKey key = NamespacedKey.fromString(worldKey);
        World w = key == null ? null : Bukkit.getWorld(key);
        if (w == null) {
            Log.get().warn("Casino speaker in unknown world {}", worldKey);
            return null;
        }
        return entry(w);
    }

    private Entry entry(World w) {
        return worlds.computeIfAbsent(w.getKey().asString(),
                k -> new Entry(w, new MusicWorld(StationStore.read(w.getPersistentDataContainer()))));
    }

    // ---- tick -------------------------------------------------------------------------------------------------------

    private void tick() {
        long tick = Bukkit.getCurrentTick();
        if (tick % MusicWorld.HOUSEKEEPING_TICKS == 0) {
            housekeeping();
        }
        if (!ctx.config().get().music().enabled()) {
            stopAll();
            changed.clear();
            return;
        }
        notes();
        long nanos = System.nanoTime();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!ctx.packs().hasMusic(p.getUniqueId())) {
                continue;
            }
            Playback pb = playbacks.computeIfAbsent(p.getUniqueId(), id -> new Playback());
            if (Math.floorMod(tick + p.getEntityId(), Playback.CHECK_TICKS) == 0 || pb.due(nanos)
                    || !changed.isEmpty() && changed.contains(p.getWorld().getKey().asString())) {
                check(p, pb, tick, nanos);
            }
        }
        changed.clear();
    }

    private void housekeeping() {
        for (Entry e : worlds.values()) {
            World w = e.world();
            Playback.Station before = e.music().playing();
            e.music().housekeeping(w.getGameTime(), seeds(), c -> w.isChunkLoaded(c.x() >> 4, c.z() >> 4));
            if (e.music().station().takeDirty()) {
                StationStore.write(w.getPersistentDataContainer(), e.music().station());
                if (!Objects.equals(before, e.music().playing())) {
                    changed.add(w.getKey().asString());
                }
            }
        }
    }

    private void check(Player p, Playback pb, long tick, long nanos) {
        Out out = new Out(p);
        if (p.isDead()) {
            pb.stop(out);
            return;
        }
        World w = p.getWorld();
        Entry e = worlds.get(w.getKey().asString());
        Playback.Station station = e == null ? null : e.music().playing();
        double distanceSq = Double.POSITIVE_INFINITY;
        if (station != null && !silenced.test(p)) {
            Location at = p.getLocation();
            SpeakerGrid.Nearest nearest = e.music().grid().nearest(at.getX(), at.getY(), at.getZ(), Playback.RANGE);
            if (nearest != null) {
                distanceSq = nearest.distanceSq();
            }
        }
        pb.check(tick, nanos, w.getGameTime(), distanceSq, station, out);
    }

    /** {@code CasinoSoundClient}: one note per switched-on loaded speaker every 32 ticks, seen within 32 blocks. */
    private void notes() {
        for (Entry e : worlds.values()) {
            SpeakerGrid grid = e.music().grid();
            if (grid.isEmpty() || e.world().getPlayerCount() == 0) {
                continue;
            }
            long gameTime = e.world().getGameTime();
            for (SpeakerGrid.Speaker s : grid.all()) {
                if (SpeakerNotes.due(gameTime, s.pos())) {
                    double[] at = SpeakerNotes.position(s.pos(), s.facing());
                    double colour = SpeakerNotes.colour(ThreadLocalRandom.current().nextInt(SpeakerNotes.COLOURS));
                    // count 0: the offset is the note's colour parameter (extra 1 = unscaled), like addParticle(xd)
                    e.world().spawnParticle(Particle.NOTE, at[0], at[1], at[2], 0, colour, 0.0, 0.0, 1.0);
                }
            }
        }
    }

    private void stopAll() {
        if (playbacks.isEmpty()) {
            return;
        }
        for (Iterator<Map.Entry<UUID, Playback>> it = playbacks.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Playback> entry = it.next();
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null) {
                entry.getValue().stop(new Out(p));
            }
            it.remove();
        }
    }

    // ---- events -----------------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        playbacks.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        stop(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        stop(event.getPlayer());
    }

    private void stop(Player p) {
        Playback pb = playbacks.get(p.getUniqueId());
        if (pb != null) {
            pb.stop(new Out(p));
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        entry(event.getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        Entry e = worlds.remove(event.getWorld().getKey().asString());
        if (e != null) {
            save(e);
        }
    }

    /** The sound calls for one player (only ever for players who loaded the music pack). */
    private record Out(Player player) implements Playback.Out {

        @Override
        public void play(int track, float volume) {
            // non-positional like the mod's relative/NONE instance: the files are stereo, so the client does not
            // attenuate them; bound to the player himself
            player.playSound(Sound.sound(TRACK_KEYS[track], Sound.Source.RECORD, volume, 1.0f), Sound.Emitter.self());
        }

        @Override
        public void stop(int track) {
            player.stopSound(SoundStop.namedOnSource(TRACK_KEYS[track], Sound.Source.RECORD));
        }

        @Override
        public void stopVanillaMusic() {
            player.stopSound(SoundStop.source(Sound.Source.MUSIC));
        }
    }
}
