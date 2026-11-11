package de.tigges.kaboofcasino.jukebox;

import de.tigges.kaboofcasino.CasinoContext;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.game.GameMenu;
import de.tigges.kaboofcasino.game.MachineController;
import de.tigges.kaboofcasino.gui.MenuService;
import de.tigges.kaboofcasino.machine.Machine;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * One casino jukebox: its setting ({@link JukeboxState}, saved in the machine record), its timeline (when the current
 * track started; runtime only) and its look. Anybody may open the menu and choose the music; several players can have
 * it open at once (each menu is reopened with the new texts when the music changes). When the current track has played
 * its length, the next one follows by the mode. Never busy: it can be broken while it plays (the music stops).
 * Thread: MAIN.
 */
public final class JukeboxController extends MachineController {

    /** Note particles over a playing jukebox this often (ticks). */
    static final int NOTE_TICKS = 16;
    /** A player's change of the music waits this long after the last one (no restarting the music for everybody). */
    static final int COOLDOWN_TICKS = 20;

    private final JukeboxService service;
    private final JukeboxRig rig;
    private JukeboxState state;
    private long startedNanos = System.nanoTime();
    private long serial;
    private long lastChangeTick = Long.MIN_VALUE / 2;

    JukeboxController(Machine machine, CasinoContext ctx, JukeboxService service) {
        super(machine, ctx);
        this.service = service;
        this.rig = new JukeboxRig(machine);
        this.state = JukeboxState.decode(machine.record().data(), service.catalog());
    }

    // ---- what the service reads -------------------------------------------------------------------------------------

    UUID id() {
        return machine.id();
    }

    int number() {
        return machine.record().number();
    }

    World jukeboxWorld() {
        return world();
    }

    /** The middle of the cabinet (the music comes from here). */
    Location centre() {
        World w = world();
        return w == null ? null : centre(w);
    }

    boolean playing() {
        return state.playing();
    }

    int range() {
        return state.range();
    }

    JukeboxState state() {
        return state;
    }

    JukeboxCatalog.Track track() {
        return service.catalog().track(state.track()).orElse(service.catalog().tracks().getFirst());
    }

    long serial() {
        return serial;
    }

    long elapsedMillis() {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }

    JukeboxService service() {
        return service;
    }

    // ---- MachineGame ------------------------------------------------------------------------------------------------

    @Override
    protected boolean running() {
        return false;
    }

    @Override
    protected Msg busyMessage() {
        return Msg.MACHINE_IN_USE;
    }

    /** Everybody may use the jukebox at the same time. */
    @Override
    protected boolean holdsClaim(Player player) {
        return true;
    }

    @Override
    protected GameMenu newMenu(Player player) {
        return new JukeboxMenu(player.getUniqueId(), this, ctx,
                Math.max(0, service.catalog().genreIndex(track().genre())), 0);
    }

    @Override
    public void interact(Player player) {
        if (mayOpen(player)) {
            ctx.menus().open(player, newMenu(player));
        }
    }

    @Override
    public void spawn() {
        World world = world();
        if (world == null) {
            return;
        }
        rig.spawn(world, track().icon());
        // after a load or a restart the current track starts again (the clients cannot seek)
        restart();
        service.loaded(this);
    }

    @Override
    public void despawn() {
        rig.despawn();
        service.unloaded(this);
        closeMenus();
    }

    @Override
    public void removed() {
        rig.despawn();
        service.unloaded(this);
        closeMenus();
    }

    @Override
    public boolean ownsRound(UUID roundId) {
        return false;
    }

    @Override
    public void checkRig() {
        if (!rig.intact()) {
            World world = world();
            if (world != null) {
                rig.spawn(world, track().icon());
            }
        }
    }

    @Override
    public void tick(long tick) {
        if (!state.playing() || !service.enabled()) {
            return;
        }
        if (elapsedMillis() >= track().millis()) {
            change(state.withTrack(state.next(service.catalog(), n -> ThreadLocalRandom.current().nextInt(n))),
                    null, "next track (" + state.mode().id() + ")");
        }
        if (tick % JukeboxRig.SPIN_TICKS == 0) {
            rig.spin();
        }
        World w = world();
        if (w != null && tick % NOTE_TICKS == 0 && w.getPlayerCount() > 0) {
            Location c = centre(w).add(0, 1.15, 0);
            w.spawnParticle(Particle.NOTE, c, 0, ThreadLocalRandom.current().nextInt(25) / 24.0, 0, 0, 1);
        }
    }

    // ---- what the players do ----------------------------------------------------------------------------------------

    /**
     * False (with a word to the player) while jukeboxes are switched off ({@code starts}: the click would start music)
     * or the last change of the music was a moment ago.
     */
    private boolean may(Player player, boolean starts) {
        if (starts && !service.enabled()) {
            ctx.messages().get().actionBar(player, Msg.JUKEBOX_DISABLED);
            return false;
        }
        long now = Bukkit.getCurrentTick();
        if (now - lastChangeTick < COOLDOWN_TICKS) {
            ctx.messages().get().actionBar(player, Msg.JUKEBOX_COOLDOWN);
            return false;
        }
        lastChangeTick = now;
        return true;
    }

    /** Plays {@code trackId} from its beginning (its genre becomes the playlist). */
    void choose(Player player, String trackId) {
        if (service.catalog().track(trackId).isEmpty() || !may(player, true)) {
            return;
        }
        change(state.withTrack(trackId).withPlaying(true), player, "plays " + trackId);
    }

    /** ABSPIELEN or STOPPEN as the player's menu showed it (not a toggle: a stale menu cannot do the opposite). */
    void setPlaying(Player player, boolean on) {
        if (state.playing() == on) {
            reopenMenus();
            return;
        }
        if (!may(player, on)) {
            return;
        }
        change(state.withPlaying(on), player, on ? "starts it" : "stops it");
    }

    void skip(Player player) {
        if (!state.playing() || !may(player, true)) {
            return;
        }
        change(state.withTrack(state.skipped(service.catalog(), n -> ThreadLocalRandom.current().nextInt(n))),
                player, "skips to the next track");
    }

    /** Mode and range touch nobody's music: no waiting between clicks (cycling through them takes a few). */
    void cycleMode(Player player) {
        settle(state.withNextMode(), player, "mode");
    }

    void cycleRange(Player player) {
        settle(state.withNextRange(), player, "range");
    }

    /** A new track or start/stop: the timeline starts again, every listener switches, the menus show it. */
    private void change(JukeboxState next, Player by, String what) {
        boolean newTrack = !next.track().equals(state.track());
        state = next;
        restart();
        if (newTrack) {
            rig.record(track().icon());
        }
        save();
        service.changed(this);
        service.logChange(this, by == null ? null : by.getName(), what + " -> " + (state.playing()
                ? track().id() : "stopped"));
        reopenMenus();
    }

    /** A setting that does not touch the timeline (mode, range). */
    private void settle(JukeboxState next, Player by, String what) {
        state = next;
        save();
        service.changed(this);
        service.logChange(this, by == null ? null : by.getName(), what + " -> " + state.mode().id() + "/"
                + state.range());
        reopenMenus();
    }

    private void restart() {
        startedNanos = System.nanoTime();
        serial++;
    }

    private void save() {
        ctx.machines().saveData(machine, state.encode());
    }

    /**
     * Every open menu of this jukebox shows the new state: its texts are part of the title, so each is replaced by a
     * fresh copy on the same tab and page ({@code MenuService.replace}: also for viewers who did not click).
     */
    void reopenMenus() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (MenuService.openMenu(p) instanceof JukeboxMenu menu && menu.controller() == this) {
                ctx.menus().replace(p, new JukeboxMenu(p.getUniqueId(), this, ctx, menu.genre(), menu.page()),
                        open -> open instanceof JukeboxMenu m && m.controller() == this);
            }
        }
    }

    /** Shows another genre tab or page in the player's menu. */
    void show(Player player, int genre, int page) {
        if (menuValid(player)) {
            ctx.menus().replace(player, new JukeboxMenu(player.getUniqueId(), this, ctx, genre, page),
                    open -> open instanceof JukeboxMenu m && m.controller() == this);
        }
    }
}
