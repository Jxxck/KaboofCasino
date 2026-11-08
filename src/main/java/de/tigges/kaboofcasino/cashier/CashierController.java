package de.tigges.kaboofcasino.cashier;

import de.tigges.kaboofcasino.CasinoContext;
import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.game.GameMenu;
import de.tigges.kaboofcasino.game.MachineController;
import de.tigges.kaboofcasino.gui.MenuService;
import de.tigges.kaboofcasino.machine.Machine;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;

/**
 * The cashier as a machine (step 9; no game: no rounds, never busy, nothing to settle): the rig, and the menu with the
 * machines' rules ({@link MachineController}: usable player, permission, pack, readable ledger, one player per cashier,
 * the menu valid only while the cashier stands and he is in reach, the danger sweep and {@code MenuSafety} close it).
 * A tab is its own chest menu (the title is the art), so switching tabs opens the next one and keeps the claim. A
 * menu without a click for {@code gui.idle-close-seconds} closes and frees the cashier. Thread: MAIN.
 */
public final class CashierController extends MachineController {

    private final CashierService service;
    private final CashierRig rig;

    CashierController(Machine machine, CasinoContext ctx, CashierService service) {
        super(machine, ctx);
        this.service = service;
        this.rig = new CashierRig(machine);
    }

    CashierService service() {
        return service;
    }

    Machine machine() {
        return machine;
    }

    @Override
    protected boolean running() {
        return false;
    }

    @Override
    protected Msg busyMessage() {
        return Msg.CASHIER_IN_USE;
    }

    @Override
    protected GameMenu newMenu(Player player) {
        return CashierMenu.of(CashierView.Tab.EXCHANGE, player.getUniqueId(), this, ctx);
    }

    @Override
    public void interact(Player player) {
        if (!mayOpen(player)) {
            return;
        }
        if (claimant != null && !claimant.equals(player.getUniqueId()) && claimantActive()) {
            Player other = Bukkit.getPlayer(claimant);
            ctx.messages().get().actionBar(player, Msg.CASHIER_IN_USE, Messages.text("name",
                    other == null ? "?" : other.getName()));
            return;
        }
        claimant = player.getUniqueId();
        if (!ctx.menus().open(player, newMenu(player))) {
            claimant = null;
        }
    }

    /** Opens {@code tab} for the claimant (a button of the open menu; the click is the interaction). */
    void show(Player player, CashierView.Tab tab) {
        if (!player.getUniqueId().equals(claimant) || !menuValid(player)) {
            return;
        }
        boolean opened = ctx.menus().open(player, CashierMenu.of(tab, player.getUniqueId(), this, ctx));
        // closing the old tab released the claim; the new one takes it again
        claimant = opened || claimantActive() ? player.getUniqueId() : null;
    }

    /** The commit checks of the machines plus: the cashier still stands and the player is in reach. */
    boolean mayTransact(Player player) {
        return mayCommit(player) && menuValid(player);
    }

    // ---- MachineGame ------------------------------------------------------------------------------------------------

    @Override
    public void spawn() {
        World world = world();
        if (world != null) {
            rig.spawn(world);
        }
    }

    @Override
    public void despawn() {
        rig.despawn();
    }

    @Override
    public void tick(long tick) {
        if (claimant == null) {
            return;
        }
        Player p = Bukkit.getPlayer(claimant);
        int idle = ctx.config().get().gui().idleCloseSeconds();
        if (p != null && idle > 0 && MenuService.openMenu(p) instanceof CashierMenu menu && menu.controller() == this
                && Bukkit.getCurrentTick() - menu.lastAction() >= idle * 20L) {
            p.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
            ctx.messages().get().actionBar(p, Msg.CASHIER_IDLE_CLOSED);
        }
    }

    @Override
    public void removed() {
        closeMenus();
        claimant = null;
    }

    @Override
    public boolean ownsRound(UUID roundId) {
        return false;
    }

    @Override
    public void checkRig() {
        if (!rig.intact()) {
            spawn();
        }
    }
}
