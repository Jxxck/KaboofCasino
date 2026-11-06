package de.tigges.kaboofcasino.game;

import de.tigges.kaboofcasino.CasinoContext;
import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.gui.MenuService;
import de.tigges.kaboofcasino.gui.Usability;
import de.tigges.kaboofcasino.machine.Heartbeat;
import de.tigges.kaboofcasino.machine.Machine;
import de.tigges.kaboofcasino.machine.MachineGame;
import de.tigges.kaboofcasino.util.ItemNames;
import de.tigges.kaboofcasino.util.Log;
import de.tigges.kaboofcasino.wager.ItemAmount;
import de.tigges.kaboofcasino.wager.LedgerCodec;
import de.tigges.kaboofcasino.wager.PlayerAccount;
import de.tigges.kaboofcasino.wager.SettleReason;
import de.tigges.kaboofcasino.wager.WagerEngine;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;

/**
 * What every machine game shares (the mod's block entities differ only in their game): opening the menu with the
 * casino's checks, one player per machine (the claim), the menu's validity (idle, loaded, within interaction range
 * + 4.5 of the anchor block), committing stakes through the {@link WagerEngine}, settling records, the heartbeat that
 * lets the ledger watchdog leave running rounds alone, the payout target of the coin shower, and sounds and
 * particles at machine-local pixel positions. Thread: MAIN.
 */
public abstract class MachineController implements MachineGame {

    protected final Machine machine;
    protected final CasinoContext ctx;
    /** The player who has (or last had) the menu open; one per machine. */
    protected UUID claimant;
    protected final Heartbeat heartbeat = new Heartbeat();
    /** Tick of the last idle light update. */
    private long lastIdleLights = Long.MIN_VALUE / 2;

    protected MachineController(Machine machine, CasinoContext ctx) {
        this.machine = machine;
        this.ctx = ctx;
    }

    // ---- hooks -----------------------------------------------------------------------------------------------------

    /** True while a round runs: no menu opens, the machine cannot be broken. */
    protected abstract boolean running();

    /** The overlay shown when someone clicks a running machine ({@code message.roulettecasino.<game>.busy}). */
    protected abstract Msg busyMessage();

    /** A fresh menu for {@code player} (after all checks passed). */
    protected abstract GameMenu newMenu(Player player);

    /** A machine-local pixel position (the mod's {@code world(x, y, z)}) as block offsets from the anchor corner. */
    protected double[] toBlock(double x, double y, double z) {
        return machine.facing().toBlock(x, y, z);
    }

    @Override
    public boolean busy() {
        return running();
    }

    // ---- menu ------------------------------------------------------------------------------------------------------

    @Override
    public void interact(Player player) {
        Messages m = ctx.messages().get();
        if (running()) {
            m.actionBar(player, busyMessage());
            return;
        }
        if (!Usability.usable(player)) {
            m.actionBar(player, Usability.creativeOrSpectator(player) ? Msg.ERROR_CREATIVE : Msg.ERROR_NOT_USABLE);
            return;
        }
        if (!player.hasPermission("kaboofcasino.use")) {
            m.actionBar(player, Msg.ERROR_NO_PERMISSION);
            return;
        }
        if (!ctx.packs().hasPack(player.getUniqueId())) {
            m.send(player, Msg.ERROR_NO_PACK);
            return;
        }
        if (ctx.ledger().blocked(player.getUniqueId())) {
            m.send(player, Msg.ERROR_INTERNAL, Messages.text("code", "LEDGER"));
            return;
        }
        if (claimant != null && !claimant.equals(player.getUniqueId()) && claimantActive()) {
            Player other = Bukkit.getPlayer(claimant);
            m.actionBar(player, Msg.MACHINE_IN_USE, Messages.text("name", other == null ? "?" : other.getName()));
            return;
        }
        claimant = player.getUniqueId();
        if (!ctx.menus().open(player, newMenu(player))) {
            claimant = null;
        }
    }

    private boolean claimantActive() {
        Player p = Bukkit.getPlayer(claimant);
        return p != null && MenuService.openMenu(p) instanceof GameMenu menu && menu.controller() == this
                && menu.stillValid(p);
    }

    /** Menu stays valid while the machine is loaded and idle and the player is in range (mod: range + 4.5). */
    public boolean menuValid(Player player) {
        if (running() || ctx.machines().byId(machine.id()).isEmpty()) {
            return false;
        }
        World w = world();
        if (w == null || !player.getWorld().equals(w)) {
            return false;
        }
        var range = player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE);
        double max = (range == null ? 4.5 : range.getValue()) + menuReach();
        Location eye = player.getEyeLocation();
        return distanceSquaredToBlock(eye.getX(), eye.getY(), eye.getZ(), machine.anchor().x(), machine.anchor().y(),
                machine.anchor().z()) < max * max;
    }

    /** Extra reach of the menu beyond the block interaction range (the mod's {@code stillValid}: 4.5, roulette 1.5). */
    protected double menuReach() {
        return 4.5;
    }

    /** The mod's {@code isWithinBlockInteractionRange}: eye to the nearest point of the anchor block's box. */
    public static double distanceSquaredToBlock(double x, double y, double z, int bx, int by, int bz) {
        double dx = Math.max(Math.max(bx - x, 0), x - (bx + 1));
        double dy = Math.max(Math.max(by - y, 0), y - (by + 1));
        double dz = Math.max(Math.max(bz - z, 0), z - (bz + 1));
        return dx * dx + dy * dy + dz * dz;
    }

    public void menuClosed(Player player) {
        if (!running() && player.getUniqueId().equals(claimant)) {
            claimant = null;
        }
    }

    /** Closes every open menu of this machine (spin start, removal). */
    protected void closeMenus() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (MenuService.openMenu(p) instanceof GameMenu menu && menu.controller() == this) {
                p.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
            }
        }
    }

    // ---- committing and settling -----------------------------------------------------------------------------------

    /** The checks before a commit click: idle machine, usable claimant, no danger (closes the menu if unsafe). */
    protected boolean mayCommit(Player player) {
        Messages m = ctx.messages().get();
        if (running()) {
            m.actionBar(player, busyMessage());
            return false;
        }
        if (!Usability.usable(player) || !player.getUniqueId().equals(claimant)) {
            return false;
        }
        if (!ctx.menus().safe(player)) {
            m.actionBar(player, Msg.ERROR_UNSAFE);
            player.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
            return false;
        }
        return true;
    }

    /**
     * Commits all stakes of one round (one record each) or nothing. Returns the committed records, or null after
     * telling the player why not.
     */
    protected List<UUID> commit(Player player, List<WagerEngine.Request> requests, Msg noBet) {
        Messages m = ctx.messages().get();
        WagerEngine.Commit commit;
        try {
            commit = ctx.engine().commitAll(new PlayerAccount(player), requests);
        } catch (LedgerCodec.CorruptLedgerException e) {
            Log.critical("LEDGER_UNREADABLE", "round refused for {}: {}", player.getName(), e.getMessage());
            m.send(player, Msg.ERROR_INTERNAL, Messages.text("code", "LEDGER"));
            return null;
        }
        if (commit instanceof WagerEngine.Refused refused) {
            refuse(player, refused, noBet);
            return null;
        }
        return ((WagerEngine.Committed) commit).rounds().stream().map(r -> r.id()).toList();
    }

    protected void refuse(Player player, WagerEngine.Refused refused, Msg noBet) {
        Messages m = ctx.messages().get();
        String item = refused.item();
        switch (refused.refusal()) {
            case OWED_PENDING -> m.send(player, Msg.OWED_PENDING);
            case ROUNDS_FULL -> m.actionBar(player, Msg.ROUNDS_FULL);
            case NOT_ALLOWED -> m.actionBar(player, Msg.BET_NOT_ALLOWED, Messages.component("item",
                    ItemNames.name(item == null ? "minecraft:air" : item)));
            case BAD_COUNT -> m.actionBar(player, noBet);
            case MAX_STAKE -> m.actionBar(player, Msg.BET_MAX, Messages.text("max",
                    Integer.toString(ctx.config().get().bets().maxStake())));
            case NOT_ENOUGH -> m.actionBar(player, Msg.BET_NOT_ENOUGH, Messages.component("item",
                    ItemNames.name(item == null ? "minecraft:air" : item)));
        }
    }

    /**
     * Settles one record of an online, living player (payout into the inventory, the rest owed). Offline or dead:
     * the quit/death hook already settled it; after a crash the join does.
     */
    protected void settleRound(UUID playerId, String name, UUID round, SettleReason reason) {
        Player p = Bukkit.getPlayer(playerId);
        if (p == null || p.isDead()) {
            return;
        }
        try {
            ctx.engine().settle(new PlayerAccount(p), round, reason);
            List<ItemAmount> owed = new PlayerAccount(p).ledger().owed();
            if (!owed.isEmpty()) {
                ctx.messages().get().send(p, Msg.OWED_REMAINING, Messages.component("items", ItemNames.list(owed)));
            }
        } catch (LedgerCodec.CorruptLedgerException e) {
            Log.critical("LEDGER_UNREADABLE", "settlement of {} for {} failed: {}", round, name, e.getMessage());
        }
    }

    /** Number of stacks {@code payout} splits into (one coin per stack, at most 200). */
    protected static int stacks(List<ItemAmount> payout) {
        long stacks = 0;
        for (ItemAmount a : payout) {
            int max = PlayerAccount.material(a.item()).getMaxStackSize();
            stacks += (a.count() + max - 1) / max;
        }
        return (int) Math.min(stacks, 200);
    }

    /** Adds one entry per stack of {@code payout} (the stack's item) to a coin queue. */
    protected static void queueCoins(java.util.Collection<String> queue, List<ItemAmount> payout) {
        for (ItemAmount a : payout) {
            int max = PlayerAccount.material(a.item()).getMaxStackSize();
            long stacks = Math.min((a.count() + max - 1) / max, 200);
            for (long i = 0; i < stacks; i++) {
                queue.add(a.item());
            }
        }
    }

    /** Where a payout coin flies: the winner if he is within 10 blocks, else 1.2 blocks in front of the machine. */
    protected Location coinTarget(World world, UUID player) {
        Player p = Bukkit.getPlayer(player);
        Location centre = centre(world);
        if (p != null && p.getWorld().equals(world) && p.getLocation().distanceSquared(centre) < 100) {
            return p.getLocation().add(0, 1.0, 0);
        }
        int[] step = machine.facing().step();
        return centre.add(step[0] * 1.2, 0, step[1] * 1.2);
    }

    // ---- world helpers ---------------------------------------------------------------------------------------------

    protected World world() {
        return Bukkit.getWorld(NamespacedKey.fromString(machine.anchor().world()));
    }

    /** A machine-local pixel position in the world. */
    protected Location at(World world, double[] px) {
        double[] b = toBlock(px[0], px[1], px[2]);
        return new Location(world, machine.anchor().x() + b[0], machine.anchor().y() + b[1],
                machine.anchor().z() + b[2]);
    }

    /** The anchor block's centre, one block up (where the coin targets and proximity checks measure from). */
    protected Location centre(World world) {
        return new Location(world, machine.anchor().x() + 0.5, machine.anchor().y() + 1.0,
                machine.anchor().z() + 0.5);
    }

    /**
     * True if this machine's idle lights should be sent now: its cadence ({@code idle-light-ticks}) passed, a
     * player is within {@code idle-light-range}, and the global {@link de.tigges.kaboofcasino.visual.LightBudget}
     * grants it a place this tick.
     */
    protected boolean idleLightsDue(World world, de.tigges.kaboofcasino.config.CasinoConfig.Slot settings, long now) {
        int every = settings.idleLightTicks();
        if (every <= 0 || now - lastIdleLights < every || !playerNear(world, settings.idleLightRange())) {
            return false;
        }
        if (!ctx.lights().grant(machine.id(), now)) {
            return false;
        }
        lastIdleLights = now;
        return true;
    }

    protected boolean playerNear(World world, int range) {
        Location c = centre(world);
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(c) <= (double) range * range) {
                return true;
            }
        }
        return false;
    }

    /** A {@code roulettecasino:<id>} sound, category BLOCKS, at a machine-local pixel position. */
    protected void sound(World world, String id, double[] px, float volume, float pitch) {
        Location l = at(world, px);
        world.playSound(Sound.sound(Key.key("roulettecasino", id), Sound.Source.BLOCK, volume, pitch), l.getX(),
                l.getY(), l.getZ());
    }

    protected <T> void particle(World world, Particle particle, double[] px, int count, double dx, double dy,
                                double dz, double speed, T data) {
        world.spawnParticle(particle, at(world, px), count, dx, dy, dz, speed, data);
    }
}
