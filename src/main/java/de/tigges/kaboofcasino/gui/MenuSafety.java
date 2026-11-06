package de.tigges.kaboofcasino.gui;

import de.tigges.kaboofcasino.config.CasinoConfig;
import de.tigges.kaboofcasino.util.Log;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Permadeath safety of menus, copied from PostMarkt's {@code MenuSafety} (PLAN decision 3): no menu opens while
 * gliding, falling more than 3 blocks or in combat (damage taken or dealt within {@code safety.combat-seconds}, or a
 * mob still targeting the player); an open menu closes on any damage and when any mob targets the player.
 * {@link DangerSweep} adds the 5-tick environment check. A danger close never decides a round. Thread: MAIN.
 */
public final class MenuSafety implements Listener {

    static final float MAX_FALL_DISTANCE = 3.0f;
    static final double TARGET_SWEEP_RADIUS = 24.0;

    private final Supplier<CasinoConfig> config;
    private final LongSupplier clock;
    private final Map<UUID, Long> lastCombat = new HashMap<>();
    private final Map<UUID, Set<Mob>> hunters = new HashMap<>();
    private Closer closer = (player, why) -> { };

    /** Closes a casino menu for danger (wired to {@link DangerSweep#close}). */
    @FunctionalInterface
    public interface Closer {
        void close(Player player, String why);
    }

    public MenuSafety(Supplier<CasinoConfig> config, LongSupplier clock) {
        this.config = config;
        this.clock = clock;
    }

    void closer(Closer c) {
        this.closer = c;
    }

    /** True if a menu may open now (combat part; {@link DangerSweep#danger} covers the environment). */
    public boolean canOpen(Player player) {
        return safe(player.isGliding(), player.getFallDistance(), inCombat(player.getUniqueId()) || hunted(player));
    }

    static boolean safe(boolean gliding, float fallDistance, boolean inCombat) {
        return !gliding && fallDistance <= MAX_FALL_DISTANCE && !inCombat;
    }

    boolean inCombat(UUID player) {
        Long last = lastCombat.get(player);
        if (last == null) {
            return false;
        }
        long since = clock.getAsLong() - last;
        return since >= 0 && since < config.get().safety().combatSeconds() * 1000L;
    }

    boolean hunted(Player player) {
        UUID id = player.getUniqueId();
        Set<Mob> mobs = hunters.get(id);
        if (mobs == null) {
            return false;
        }
        mobs.removeIf(mob -> !targets(mob, id));
        if (mobs.isEmpty()) {
            hunters.remove(id);
            return false;
        }
        return true;
    }

    /** Monsters that target without an event (Warden, Creaking): checked by the sweep. */
    boolean closeIfTargeted(Player player) {
        UUID id = player.getUniqueId();
        for (Entity entity : player.getNearbyEntities(TARGET_SWEEP_RADIUS, TARGET_SWEEP_RADIUS, TARGET_SWEEP_RADIUS)) {
            if (entity instanceof Mob mob && targets(mob, id)) {
                remember(mob, player);
                recordCombat(id);
                closer.close(player, "targeted without an event");
                return true;
            }
        }
        return false;
    }

    void recordCombat(UUID player) {
        long now = clock.getAsLong();
        long keep = config.get().safety().combatSeconds() * 1000L;
        lastCombat.values().removeIf(t -> now - t >= keep || now < t);
        lastCombat.put(player, now);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            recordCombat(player.getUniqueId());
            closer.close(player, "damage " + event.getCause().name());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageDealt(EntityDamageByEntityEvent event) {
        Player attacker = attacker(event.getDamager(), event.getDamageSource().getCausingEntity());
        if (attacker != null) {
            recordCombat(attacker.getUniqueId());
        }
    }

    /** Any mob that targets the player is danger (also iron golems, angry wolves, bees, not only monsters). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getEntity() instanceof Mob mob && event.getTarget() instanceof Player player) {
            remember(mob, player);
            recordCombat(player.getUniqueId());
            closer.close(player, "targeted (" + event.getReason() + ")");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        hunters.remove(event.getPlayer().getUniqueId());
    }

    private void remember(Mob mob, Player player) {
        hunted(player);
        hunters.computeIfAbsent(player.getUniqueId(), id -> new HashSet<>()).add(mob);
    }

    private static boolean targets(Mob mob, UUID player) {
        return mob.isValid() && mob.getTarget() instanceof Player target && target.getUniqueId().equals(player);
    }

    static Player attacker(Entity damager, Entity causing) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return causing instanceof Player player ? player : null;
    }

    static void debug(String message, Object... args) {
        Log.get().debug(message, args);
    }
}
