package de.tigges.kaboofcasino.guide;

import de.tigges.kaboofcasino.CasinoContext;
import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.gui.DangerSweep;
import de.tigges.kaboofcasino.util.FakePlayers;
import de.tigges.kaboofcasino.util.Log;
import de.tigges.kaboofcasino.util.Threads;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * The casino guide book (games.md 11) as dialogs (PLAN decision 6): using the guide item opens the cover, clicks in
 * the dialog turn pages ({@link GuideSessions}, {@link GuideDialogs}, {@link GuidePaper}), each page turn plays the
 * mod's {@code item.book.page_turn} (volume 0.6, pitch 1.0) to the reader only. Nothing is consumed (the mod's
 * {@code use} only returns SUCCESS).
 *
 * <p>Permadeath (PLAN decision 3, server.md 2.3: dialogs get the menus' close): in survival and adventure the guide
 * does not open in danger or combat ({@link DangerSweep#safeToOpen}, "Nicht jetzt – du bist in Gefahr."), and an
 * open guide closes on damage, when a mob targets the reader and when the danger sweep (every
 * {@code safety.sweep-ticks}) finds danger ("Gefahr – Handbuch zu."). Creative readers cannot be hurt and read
 * anywhere; spectators cannot use items. Thread: MAIN.
 */
public final class GuideService {

    static final float PAGE_TURN_VOLUME = 0.6f;
    static final float PAGE_TURN_PITCH = 1.0f;

    private final CasinoContext ctx;
    private final DangerSweep sweep;
    private final Map<UUID, Long> openedTick = new HashMap<>();
    private GuideBook book;
    private GuideDialogs dialogs;
    private GuideSessions sessions;
    private GuideItems items;
    private BukkitTask task;

    public GuideService(CasinoContext ctx, DangerSweep sweep) {
        this.ctx = ctx;
        this.sweep = sweep;
    }

    /**
     * Loads {@value GuideBook#RESOURCE} and the recipe pictures ({@value RecipePictures#RESOURCE}), registers the
     * listener and the danger check.
     */
    public void enable() {
        try (InputStream in = ctx.plugin().getResource(GuideBook.RESOURCE)) {
            book = GuideBook.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + GuideBook.RESOURCE, e);
        }
        RecipePictures pictures;
        try (InputStream in = ctx.plugin().getResource(RecipePictures.RESOURCE)) {
            pictures = RecipePictures.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + RecipePictures.RESOURCE, e);
        }
        dialogs = new GuideDialogs(book, pictures);
        sessions = new GuideSessions(book.size());
        items = new GuideItems(book, ctx.messages());
        Bukkit.getPluginManager().registerEvents(new GuideListener(this), ctx.plugin());
        task = Bukkit.getScheduler().runTaskTimer(ctx.plugin(), this::tick, 1L, 1L);
        Log.get().info("KaboofCasino guide: {} spreads, {} recipe pictures", book.size(), pictures.all().size());
    }

    /** Closes every open guide (plugin disable). */
    public void disable() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (sessions != null) {
            for (UUID id : sessions.open()) {
                Player player = Bukkit.getPlayer(id);
                if (player != null) {
                    player.closeDialog();
                }
            }
            sessions.clear();
        }
        openedTick.clear();
    }

    int pages() {
        return book.size();
    }

    /** The guide item was used (right click in the air or on a plain block). */
    void use(Player player) {
        Threads.requireMain();
        long tick = Bukkit.getCurrentTick();
        Long before = openedTick.put(player.getUniqueId(), tick);
        if (before != null && before == tick) {
            return; // both hands in one click
        }
        if (!readable(player)) {
            return;
        }
        if (!invulnerable(player) && !sweep.safeToOpen(player)) {
            ctx.messages().get().actionBar(player, Msg.ERROR_UNSAFE);
            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
            return;
        }
        show(player, sessions.open(player.getUniqueId(), tick));
    }

    /** A click in a guide dialog (already parsed and range-checked). */
    void click(Player player, GuideAction action) {
        Threads.requireMain();
        GuideSessions.Outcome outcome = sessions.apply(player.getUniqueId(), action, Bukkit.getCurrentTick());
        switch (outcome) {
            case GuideSessions.Show show -> {
                if (!readable(player) || !invulnerable(player) && !sweep.safeToOpen(player)) {
                    danger(player, "page turn in danger");
                    return;
                }
                if (show(player, show)) {
                    player.playSound(player, Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, PAGE_TURN_VOLUME,
                            PAGE_TURN_PITCH);
                }
            }
            case GuideSessions.Closed closed -> player.closeDialog();
            case GuideSessions.Ignored ignored -> Log.get().debug("KaboofCasino guide: click {} of {} ignored ({})",
                    action, player.getName(), ignored.why());
        }
    }

    /** Closes the reader's guide for danger and tells him (nothing if no guide is open). */
    void danger(Player player, String why) {
        if (sessions.forget(player.getUniqueId())) {
            Log.get().debug("KaboofCasino closes the guide of {} ({})", player.getName(), why);
            player.closeDialog();
            ctx.messages().get().actionBar(player, Msg.GUIDE_DANGER_CLOSE);
        }
    }

    /** The client closed the dialog by itself (death screen, world change, quit). */
    void forget(Player player) {
        sessions.forget(player.getUniqueId());
    }

    void quit(Player player) {
        sessions.forget(player.getUniqueId());
        openedTick.remove(player.getUniqueId());
    }

    private boolean show(Player player, GuideSessions.Show show) {
        try {
            GuideView view = dialogs.view(show.page(), show.history(), texts());
            player.showDialog(GuidePaper.dialog(view, items::stack));
            return true;
        } catch (RuntimeException e) {
            sessions.forget(player.getUniqueId());
            player.closeDialog();
            String code = Log.incident("guide page " + show.page() + " for " + player.getName(), e);
            ctx.messages().get().send(player, Msg.ERROR_INTERNAL, Messages.text("code", code));
            return false;
        }
    }

    private GuideTexts texts() {
        Messages m = ctx.messages().get();
        return new GuideTexts(m.render(Msg.GUIDE_RECIPE), m.render(Msg.GUIDE_ANY_KIND), m.render(Msg.GUIDE_CREDITS),
                page -> m.render(Msg.GUIDE_GOTO, Messages.text("page", Integer.toString(page))), items::vanillaName,
                id -> items.stack(id, 1),
                Map.of(NOTE_COUNTER, m.render(Msg.GUIDE_NOTE_COUNTER), NOTE_HAND, m.render(Msg.GUIDE_NOTE_HAND),
                        NOTE_BETS, m.render(Msg.GUIDE_NOTE_BETS), NOTE_NAMES, m.render(Msg.GUIDE_NOTE_NAMES),
                        NOTE_NUMBER, m.render(Msg.GUIDE_NOTE_NUMBER), NOTE_CHIPS, m.render(Msg.GUIDE_NOTE_CHIPS)));
    }

    /**
     * The mod texts that get a plugin note: spread 3 "In einem Dorf vergibt die Theke den Beruf des Croupiers." (here a
     * click on the counter converts the villager standing on it), spread 33 "Im Kreativ-Tab des Casinos, ohne Rezept."
     * and "Einsätze und Auszahlung" (not built: config.yml), spread 34 "Der Reiter Name benennt sie um." (no tabs: the
     * name field) and "Ziehe den Balken oder tippe eine Zahl." (sliders only).
     */
    static final String NOTE_COUNTER = "guide.roulettecasino.text.043";
    static final String NOTE_HAND = "guide.roulettecasino.text.219";
    static final String NOTE_BETS = "guide.roulettecasino.text.213";
    static final String NOTE_NAMES = "guide.roulettecasino.text.262";
    static final String NOTE_NUMBER = "guide.roulettecasino.text.260";
    /** Spread 2 "MASCHINEN": here the machines take chips, ores go to the cashier (step 9). */
    static final String NOTE_CHIPS = "guide.roulettecasino.text.012";

    /** Every {@code safety.sweep-ticks}: the danger check of every open guide. */
    private void tick() {
        if (sessions.isEmpty()) {
            return;
        }
        int every = Math.max(1, ctx.config().get().safety().sweepTicks());
        if (Bukkit.getCurrentTick() % every != 0) {
            return;
        }
        for (UUID id : sessions.open()) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                sessions.forget(id);
            } else if (!invulnerable(player) && !sweep.safeToOpen(player)) {
                String why = sweep.danger(player);
                danger(player, why == null ? "combat" : why);
            }
        }
    }

    /** Who may read: connected, alive, awake, no spectator, no fake player. */
    static boolean readable(Player player) {
        return player.isConnected() && !player.isDead() && !player.isSleeping() && !FakePlayers.isFake(player)
                && player.getGameMode() != GameMode.SPECTATOR;
    }

    private static boolean invulnerable(Player player) {
        return player.getGameMode() == GameMode.CREATIVE;
    }
}
