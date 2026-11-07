package de.tigges.kaboofcasino.deco.full;

import de.tigges.kaboofcasino.CasinoContext;
import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.util.Log;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.scheduler.BukkitTask;

/**
 * The mod's 27 full-cube deco blocks (9 fabric floors, 5 stone floors, 13 dark-oak wall blocks) as reserved states
 * of the vanilla huge-mushroom blocks (render.md 4.4 option B): real chunk geometry, no entities. Registers the items
 * in {@code ItemCatalog} ({@code /casino give}, recipes).
 *
 * <p>Needs Paper's {@code block-updates.disable-mushroom-block-updates: true}, read live ({@link #active()}). While it
 * is false (at enable, on {@code /casino reload}, or switched at runtime: logged CRITICAL) the whole module stands
 * still: no placing, no drop conversion (reserved states drop vanilla loot), no pick-block items, no digging
 * modifier - nothing new can be minted from blocks that neighbour updates may have rewritten. Thread: MAIN.
 */
public final class FullDecoService {

    private final CasinoContext ctx;
    private FullDecoListener listener;
    private DecoDigging digging;
    private PilasterUpdates updates;
    private BukkitTask task;
    private Boolean lastActive;

    public FullDecoService(CasinoContext ctx) {
        this.ctx = ctx;
    }

    /** Registers items (ItemCatalog), listeners and tasks. */
    public void enable() {
        FullDecoItems.register();
        DecoWorld world = new DecoWorld();
        updates = new PilasterUpdates(ctx.plugin(), world);
        listener = new FullDecoListener(world, updates, ctx.messages(), this::active);
        digging = new DecoDigging(this::active);
        ctx.plugin().getServer().getPluginManager().registerEvents(listener, ctx.plugin());
        ctx.plugin().getServer().getPluginManager().registerEvents(digging, ctx.plugin());
        task = Bukkit.getScheduler().runTaskTimer(ctx.plugin(), digging::tick, 1L, 1L);
        check("enable");
    }

    /** {@code /casino reload}: reads the switch again; 1 (a warning) if it is off, else 0. */
    public int reloaded() {
        return check("reload") ? 0 : 1;
    }

    private boolean check(String when) {
        boolean on = PaperSwitch.on();
        lastActive = on;
        if (on) {
            Log.get().info("Full-block deco active on {}: {} blocks on {} reserved mushroom states (Paper "
                    + "disable-mushroom-block-updates = true, read via {})", when, DecoBlock.values().length,
                    FullDecoStates.entries().size(), PaperSwitch.source());
        } else {
            switchOff(when);
        }
        return on;
    }

    private static void switchOff(String when) {
        Log.critical("FULL_DECO_SWITCH_OFF", "on {}: Paper block-updates.disable-mushroom-block-updates is false "
                + "(read via {}) - full-block deco stopped: no placing, reserved mushroom states drop vanilla loot. "
                + "Set it to true in config/paper-global.yml and restart.", when, PaperSwitch.source());
    }

    /** Paper's switch, read live on every call; a change at runtime is logged (CRITICAL when it goes off). */
    public boolean active() {
        boolean on = PaperSwitch.on();
        if (lastActive != null && on != lastActive) {
            if (on) {
                Log.get().info("Full-block deco active again: Paper disable-mushroom-block-updates is true");
            } else {
                switchOff("runtime change");
            }
        }
        lastActive = on;
        return on;
    }

    /** One line for the admin output of {@code /casino}: active or off, and why. */
    public Component status() {
        Messages m = ctx.messages().get();
        return active()
                ? m.render(Msg.DECO_STATUS_ON, Messages.text("states", Integer.toString(FullDecoStates.entries()
                        .size())), Messages.text("source", PaperSwitch.source()))
                : m.render(Msg.DECO_STATUS_OFF, Messages.text("source", PaperSwitch.source()));
    }

    public void disable() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (digging != null) {
            HandlerList.unregisterAll(digging);
            digging.disable();
            digging = null;
        }
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener.clear();
            listener = null;
        }
        if (updates != null) {
            updates.clear();
            updates = null;
        }
    }
}
