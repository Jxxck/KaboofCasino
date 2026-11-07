package de.tigges.kaboofcasino.deco.full;

import de.tigges.kaboofcasino.CasinoContext;
import de.tigges.kaboofcasino.util.Log;
import org.bukkit.event.HandlerList;

/**
 * The mod's 27 full-cube deco blocks (9 fabric floors, 5 stone floors, 13 dark-oak wall blocks) as reserved states
 * of the vanilla huge-mushroom blocks (render.md 4.4 option B): real chunk geometry, no entities. Needs Paper's
 * {@code block-updates.disable-mushroom-block-updates: true}; without it placing is refused (and logged).
 * Registers the items in {@code ItemCatalog} ({@code /casino give}, recipes). Thread: MAIN.
 */
public final class FullDecoService {

    private final CasinoContext ctx;
    private FullDecoListener listener;
    private PilasterUpdates updates;

    public FullDecoService(CasinoContext ctx) {
        this.ctx = ctx;
    }

    /** Registers items (ItemCatalog), listeners and tasks. */
    public void enable() {
        FullDecoItems.register();
        DecoWorld world = new DecoWorld();
        updates = new PilasterUpdates(ctx.plugin(), world);
        listener = new FullDecoListener(world, updates, ctx.messages());
        ctx.plugin().getServer().getPluginManager().registerEvents(listener, ctx.plugin());
        if (DecoWorld.mushroomUpdatesDisabled()) {
            Log.get().info("Full-block deco: {} blocks on {} reserved mushroom states (mushroom block updates are "
                    + "disabled)", DecoBlock.values().length, FullDecoStates.entries().size());
        } else {
            Log.get().warn("Full-block deco: block-updates.disable-mushroom-block-updates is false in "
                    + "config/paper-global.yml - casino deco blocks cannot be placed until it is true (restart)");
        }
    }

    public void disable() {
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
