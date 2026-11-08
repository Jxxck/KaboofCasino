package de.tigges.kaboofcasino.chipshop;

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
 * One Chip-Shop (step 11): one player at a time like the cashier (the claim), a menu per category tab, idle close
 * after {@code gui.idle-close-seconds} without a click. No rounds, never busy. Thread: MAIN.
 */
public final class ChipShopController extends MachineController {

    private final ChipShopService service;
    private final ChipShopRig rig;

    ChipShopController(Machine machine, CasinoContext ctx, ChipShopService service) {
        super(machine, ctx);
        this.service = service;
        this.rig = new ChipShopRig(machine);
    }

    ChipShopService service() {
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
        return Msg.CHIPSHOP_IN_USE;
    }

    @Override
    protected GameMenu newMenu(Player player) {
        return new ChipShopMenu(ChipShopCategory.FLOORS, player.getUniqueId(), this, ctx);
    }

    @Override
    public void interact(Player player) {
        if (!mayOpen(player)) {
            return;
        }
        if (claimant != null && !claimant.equals(player.getUniqueId()) && claimantActive()) {
            Player other = Bukkit.getPlayer(claimant);
            ctx.messages().get().actionBar(player, Msg.CHIPSHOP_IN_USE, Messages.text("name",
                    other == null ? "?" : other.getName()));
            return;
        }
        claimant = player.getUniqueId();
        if (!ctx.menus().open(player, newMenu(player))) {
            claimant = null;
        }
    }

    /** Opens the tab {@code category} for the claimant (a tab click of the open menu). */
    void show(Player player, ChipShopCategory category, boolean stack) {
        if (!player.getUniqueId().equals(claimant) || !menuValid(player)) {
            return;
        }
        ChipShopMenu next = new ChipShopMenu(category, player.getUniqueId(), this, ctx);
        next.stack(stack);
        boolean opened = ctx.menus().open(player, next);
        claimant = opened || claimantActive() ? player.getUniqueId() : null;
    }

    /** The commit checks of the machines plus: the shop still stands and the player is in reach. */
    boolean mayTransact(Player player) {
        return mayCommit(player) && menuValid(player);
    }

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
        if (p != null && idle > 0 && MenuService.openMenu(p) instanceof ChipShopMenu menu && menu.controller() == this
                && Bukkit.getCurrentTick() - menu.lastAction() >= idle * 20L) {
            p.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
            ctx.messages().get().actionBar(p, Msg.CHIPSHOP_IDLE_CLOSED);
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
