package de.tigges.kaboofcasino.machine;

import de.tigges.kaboofcasino.config.CasinoConfig;
import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.util.FakePlayers;
import de.tigges.kaboofcasino.util.Pos;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Breaking machines (PLAN decision 4, render.md 2.4): barriers cannot be mined, so a left click on a carrier
 * ({@code BlockDamageEvent}) starts the plugin's own break timer with oak-plank speed (hardness 3.0) and shows the
 * cracks with {@code sendBlockDamage}; {@code BlockDamageAbortEvent} stops it. Only the placer and ops may break
 * (config {@code machines.break}); a running round refuses. At 100 % the machine drops its item at the anchor.
 * Creative players (placer or op) remove it at once without a drop. Thread: MAIN.
 */
public final class BreakListener implements Listener {

    private static final BlockData PLANKS = Material.OAK_PLANKS.createBlockData();

    private final MachineService machines;
    private final Supplier<CasinoConfig> config;
    private final Supplier<Messages> messages;
    private final Map<UUID, Task> tasks = new HashMap<>();

    private static final class Task {
        final Machine machine;
        final Pos block;
        double progress;
        int lastStage = -1;

        Task(Machine machine, Pos block) {
            this.machine = machine;
            this.block = block;
        }
    }

    public BreakListener(MachineService machines, Supplier<CasinoConfig> config, Supplier<Messages> messages) {
        this.machines = machines;
        this.config = config;
        this.messages = messages;
    }

    private boolean mayBreak(Player player, Machine machine) {
        return player.isOp() || player.hasPermission("kaboofcasino.admin")
                || config.get().machines().anyoneMayBreak() || machine.record().placer().equals(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(BlockDamageEvent event) {
        Optional<Machine> machine = machines.at(event.getBlock());
        if (machine.isEmpty()) {
            return;
        }
        Player player = event.getPlayer();
        if (FakePlayers.isFake(player) || player.getGameMode() == GameMode.ADVENTURE) {
            return;
        }
        if (!refused(player, machine.get())) {
            tasks.put(player.getUniqueId(), new Task(machine.get(), Pos.of(event.getBlock())));
        }
    }

    private boolean refused(Player player, Machine machine) {
        if (!mayBreak(player, machine)) {
            String name = Bukkit.getOfflinePlayer(machine.record().placer()).getName();
            messages.get().actionBar(player, Msg.MACHINE_NOT_YOURS, Messages.text("name", name == null ? "?" : name));
            return true;
        }
        if (machine.game().busy()) {
            messages.get().actionBar(player, Msg.MACHINE_RUNNING);
            return true;
        }
        return false;
    }

    @EventHandler
    public void onAbort(BlockDamageAbortEvent event) {
        stop(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stop(event.getPlayer());
    }

    /** Creative players break barriers through the normal path: treat it as removal without a drop. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent event) {
        Optional<Machine> machine = machines.at(event.getBlock());
        if (machine.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE && !refused(player, machine.get())) {
            machines.remove(machine.get(), "broken in creative by " + player.getName(), null);
        }
    }

    /** Every tick: advance the timers. */
    public void tick() {
        for (Map.Entry<UUID, Task> e : Map.copyOf(tasks).entrySet()) {
            Player player = Bukkit.getPlayer(e.getKey());
            Task task = e.getValue();
            World machineWorld = Bukkit.getWorld(org.bukkit.NamespacedKey.fromString(task.machine.anchor().world()));
            if (player == null || !machines.byId(task.machine.id()).isPresent() || player.isDead()
                    || machineWorld == null || !player.getWorld().equals(machineWorld)
                    || player.getLocation().distanceSquared(center(task.block, machineWorld)) > 49) {
                stop(e.getKey(), task);
                continue;
            }
            task.progress += speed(player);
            int stage = (int) (task.progress * 10);
            if (stage != task.lastStage) {
                task.lastStage = stage;
                cracks(task, (float) Math.min(1.0, task.progress));
                if (stage % 3 == 0) {
                    player.getWorld().playSound(center(task.block, player.getWorld()), Sound.BLOCK_METAL_HIT, 0.5f,
                            0.8f);
                }
            }
            if (task.progress >= 1.0) {
                stop(e.getKey(), task);
                if (!refused(player, task.machine)) {
                    World w = player.getWorld();
                    w.playSound(center(task.block, w), Sound.BLOCK_METAL_BREAK, 1f, 0.8f);
                    machines.remove(task.machine, "broken by " + player.getName(),
                            player.getGameMode() == GameMode.CREATIVE ? null
                                    : CasinoItems.create(CasinoItem.forMachine(task.machine.type()), messages.get(), 1),
                            player);
                }
            }
        }
    }

    private static Location center(Pos p, World w) {
        return new Location(w, p.x() + 0.5, p.y() + 0.5, p.z() + 0.5);
    }

    /** Oak-plank mining speed with the player's tool and effects ({@link BreakProgress}). */
    static double speed(Player player) {
        ItemStack tool = player.getInventory().getItemInMainHand();
        double toolSpeed = PLANKS.getDestroySpeed(tool, true);
        PotionEffect haste = player.getPotionEffect(PotionEffectType.HASTE);
        PotionEffect fatigue = player.getPotionEffect(PotionEffectType.MINING_FATIGUE);
        double breakSpeed = attribute(player, Attribute.BLOCK_BREAK_SPEED, 1.0);
        double submerged = 1.0;
        if (player.isInWater() && player.getEyeLocation().getBlock().getType() == Material.WATER) {
            ItemStack helmet = player.getInventory().getHelmet();
            boolean aqua = helmet != null && helmet.containsEnchantment(Enchantment.AQUA_AFFINITY);
            submerged = aqua ? 1.0 : attribute(player, Attribute.SUBMERGED_MINING_SPEED, 0.2);
        }
        boolean onGround = player.getLocation().subtract(0, 0.05, 0).getBlock().getType().isSolid();
        return BreakProgress.perTick(toolSpeed, haste == null ? 0 : haste.getAmplifier() + 1,
                fatigue == null ? 0 : fatigue.getAmplifier() + 1, breakSpeed, submerged, onGround);
    }

    private static double attribute(Player player, Attribute attribute, double def) {
        AttributeInstance a = player.getAttribute(attribute);
        return a == null ? def : a.getValue();
    }

    private void cracks(Task task, float progress) {
        World world = Bukkit.getWorld(org.bukkit.NamespacedKey.fromString(task.machine.anchor().world()));
        if (world == null) {
            return;
        }
        int base = task.machine.id().hashCode() & 0x3fffffff;
        int i = 0;
        for (Pos p : task.machine.carriers()) {
            Location loc = new Location(world, p.x(), p.y(), p.z());
            int source = -(base + i++) - 1;
            for (Player viewer : world.getPlayers()) {
                if (viewer.getLocation().distanceSquared(loc) < 32 * 32) {
                    viewer.sendBlockDamage(loc, progress, source);
                }
            }
        }
    }

    private void stop(Player player) {
        Task task = tasks.get(player.getUniqueId());
        if (task != null) {
            stop(player.getUniqueId(), task);
        }
    }

    private void stop(UUID player, Task task) {
        tasks.remove(player);
        if (task.lastStage >= 0) {
            cracks(task, 0f);
        }
    }
}
