package de.tigges.kaboofcasino.gui;

import de.tigges.kaboofcasino.config.CasinoConfig;
import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Wither;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.potion.PotionEffectType;

/**
 * The casino's 5-tick danger check (PLAN decision 3, server.md 2.2), for players with an open casino menu only:
 * a vehicle (casino seats exempt via {@link #casinoSeats}), water (also bubble columns), levitation, air, fire, lava,
 * frost, falling, the void, nearby monsters (with line of sight), primed TNT and TNT minecarts, a fusing creeper, a
 * wither boss and the wither effect; with the menu open also being pushed (more than {@value #DRIFT} blocks per sweep
 * after a short grace: the player cannot walk with a container open, so drift means ice or a current) and the idle
 * time ({@code gui.idle-close-seconds}). Any hit closes the menu and its dialog; the round (if any) runs on. The same
 * checks (without drift and idle) refuse opening a menu. Thread: MAIN.
 */
public final class DangerSweep {

    /** Horizontal drift (blocks per sweep) of a player with an open menu that counts as being pushed. */
    static final double DRIFT = 0.2;
    /** Ticks after opening in which the player may still slide to a stop. */
    static final int DRIFT_GRACE_TICKS = 10;

    private final Supplier<CasinoConfig> config;
    private final Supplier<Messages> messages;
    private final MenuSafety safety;
    private final java.util.Map<java.util.UUID, org.bukkit.Location> last = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Double> drift = new java.util.HashMap<>();
    private java.util.function.Predicate<org.bukkit.entity.Entity> casinoSeat = vehicle -> false;

    public DangerSweep(Supplier<CasinoConfig> config, Supplier<Messages> messages, MenuSafety safety) {
        this.config = config;
        this.messages = messages;
        this.safety = safety;
        safety.closer(this::close);
    }

    /** Hook for the seats of a later step: riding a casino seat is no danger. */
    public void casinoSeats(java.util.function.Predicate<org.bukkit.entity.Entity> isSeat) {
        this.casinoSeat = isSeat;
    }

    /** The danger right now, or null. */
    public String danger(Player player) {
        CasinoConfig.Safety s = config.get().safety();
        if (player.isInsideVehicle() && !casinoSeat.test(player.getVehicle())) {
            return "vehicle";
        }
        if (player.isInWater()) {
            return "water";
        }
        if (player.hasPotionEffect(PotionEffectType.LEVITATION)) {
            return "levitation";
        }
        if (player.getRemainingAir() < player.getMaximumAir()) {
            return "air";
        }
        if (player.getFireTicks() > 0 || player.isInLava()) {
            return "fire";
        }
        if (player.getFreezeTicks() > 0 || player.isInPowderedSnow()) {
            return "frost";
        }
        if (player.getFallDistance() > MenuSafety.MAX_FALL_DISTANCE || player.isGliding()) {
            return "falling";
        }
        if (player.getLocation().getY() < player.getWorld().getMinHeight()) {
            return "void";
        }
        if (player.hasPotionEffect(PotionEffectType.WITHER)) {
            return "wither effect";
        }
        double r = Math.max(s.monsterRadius(), s.tntRadius());
        for (Entity e : player.getNearbyEntities(r, r / 2 + 2, r)) {
            double d = e.getLocation().distance(player.getLocation());
            if (e instanceof TNTPrimed && d <= s.tntRadius()) {
                return "primed TNT";
            }
            if (e instanceof Creeper creeper && (creeper.isIgnited() || creeper.getFuseTicks() > 0)
                    && d <= s.tntRadius()) {
                return "creeper";
            }
            if (e instanceof ExplosiveMinecart cart && cart.isIgnited() && d <= s.tntRadius()) {
                return "primed TNT minecart";
            }
            if (e instanceof Wither && d <= s.monsterRadius()) {
                return "wither";
            }
            if (e instanceof Enemy && e instanceof LivingEntity living && !living.isDead() && d <= s.monsterRadius()
                    && player.hasLineOfSight(e)) {
                return "monster " + e.getType().getKey().getKey();
            }
        }
        return null;
    }

    /** True if a menu may open now (combat + environment). */
    public boolean safeToOpen(Player player) {
        return safety.canOpen(player) && danger(player) == null;
    }

    /** Horizontal distance moved since the previous sweep. */
    private double measureDrift(Player player) {
        org.bukkit.Location now = player.getLocation();
        org.bukkit.Location before = last.put(player.getUniqueId(), now);
        double d = before == null || !before.getWorld().equals(now.getWorld()) ? 0
                : Math.hypot(now.getX() - before.getX(), now.getZ() - before.getZ());
        drift.put(player.getUniqueId(), d);
        return d;
    }

    /** Every {@code safety.sweep-ticks}: checks every player with an open casino menu. */
    public void sweep() {
        long tick = Bukkit.getCurrentTick();
        int idle = config.get().gui().idleCloseSeconds();
        last.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
        for (Player player : Bukkit.getOnlinePlayers()) {
            double moved = measureDrift(player);
            Menu menu = MenuService.openMenu(player);
            if (menu == null) {
                continue;
            }
            if (!menu.stillValid(player)) {
                player.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
                continue;
            }
            if (idle > 0 && menu.closesWhenIdle() && menu.openedAtTick() >= 0
                    && tick - menu.openedAtTick() >= idle * 20L) {
                player.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
                messages.get().actionBar(player, Msg.MACHINE_IDLE_CLOSED);
                continue;
            }
            if (moved > DRIFT && menu.openedAtTick() >= 0 && tick - menu.openedAtTick() > DRIFT_GRACE_TICKS) {
                close(player, "pushed (" + String.format(java.util.Locale.ROOT, "%.2f", moved) + " blocks)");
                continue;
            }
            String why = danger(player);
            if (why != null) {
                close(player, why);
            } else {
                safety.closeIfTargeted(player);
            }
        }
    }

    /** Closes the casino menu and dialog of a player in danger and tells him so. */
    public void close(Player player, String why) {
        if (MenuService.openMenu(player) == null) {
            return;
        }
        MenuSafety.debug("KaboofCasino closes the menu of {} ({})", player.getName(), why);
        player.closeDialog();
        player.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
        messages.get().actionBar(player, Msg.ERROR_DANGER_CLOSE);
    }
}
