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
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.potion.PotionEffectType;

/**
 * The casino's 5-tick danger check (PLAN decision 3, server.md 2.2), for players with an open casino menu only:
 * air, fire, lava, frost, falling, the void, nearby monsters (with line of sight), primed TNT, a fusing creeper, a
 * wither boss and the wither effect. Any hit closes the menu and its dialog; the round (if any) runs on. The same
 * checks refuse opening a menu. Thread: MAIN.
 */
public final class DangerSweep {

    private final Supplier<CasinoConfig> config;
    private final Supplier<Messages> messages;
    private final MenuSafety safety;

    public DangerSweep(Supplier<CasinoConfig> config, Supplier<Messages> messages, MenuSafety safety) {
        this.config = config;
        this.messages = messages;
        this.safety = safety;
        safety.closer(this::close);
    }

    /** The danger right now, or null. */
    public String danger(Player player) {
        CasinoConfig.Safety s = config.get().safety();
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
            if (e instanceof Creeper creeper && creeper.isIgnited() && d <= s.tntRadius()) {
                return "creeper";
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

    /** Every {@code safety.sweep-ticks}: checks every player with an open casino menu. */
    public void sweep() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Menu menu = MenuService.openMenu(player);
            if (menu == null) {
                continue;
            }
            if (!menu.stillValid(player)) {
                player.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
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
