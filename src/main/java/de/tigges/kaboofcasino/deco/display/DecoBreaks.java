package de.tigges.kaboofcasino.deco.display;

import de.tigges.kaboofcasino.util.Pos;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.SoundGroup;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Breaking decoration on plugin carriers. A barrier cannot be mined, so a left click starts the plugin's timer at the
 * mod's speed for that block (its hardness, the copied block's tool speed and harvest rule: vanilla
 * {@code getDestroyProgress} = speed / hardness / (30 or 100 without the right tool)), with cracks shown through
 * {@code sendBlockDamage} and the mod's hit and break sounds; a light carrier's Interaction breaks at the first hit (an
 * entity click has no "held" state, like an item frame). Creative breaks at once without a drop. Anybody may break
 * decoration (the mod's and vanilla's rule), except inside spawn protection for non-ops. Thread: MAIN.
 */
final class DecoBreaks {

    private final DisplayDecoService service;
    private final Map<UUID, Task> tasks = new HashMap<>();

    private static final class Task {
        final DecoBlock deco;
        final Pos block;
        double progress;
        int ticks;
        int lastStage = -1;

        Task(DecoBlock deco, Pos block) {
            this.deco = deco;
            this.block = block;
        }
    }

    DecoBreaks(DisplayDecoService service) {
        this.service = service;
    }

    /** A survival player starts digging a barrier carrier. */
    void start(Player player, DecoBlock deco, Pos block) {
        if (refused(player, deco)) {
            return;
        }
        tasks.put(player.getUniqueId(), new Task(deco, block));
    }

    void stop(Player player) {
        Task task = tasks.remove(player.getUniqueId());
        if (task != null && task.lastStage >= 0) {
            cracks(task, 0f);
        }
    }

    /** A decoration went away: drop its timers. */
    void forget(DecoBlock deco) {
        tasks.values().removeIf(t -> t.deco == deco);
    }

    void clear() {
        tasks.clear();
    }

    private boolean refused(Player player, DecoBlock deco) {
        if (player.isOp() || player.hasPermission("kaboofcasino.admin")) {
            return false;
        }
        World world = DisplayDecoService.world(deco.anchor());
        return world == null || DecoPlacer.underSpawnProtection(world, deco.anchor());
    }

    /** Creative breaks (BlockBreakEvent on a barrier, any hit on an Interaction) and survival hits on Interactions. */
    void breakNow(Player player, DecoBlock deco) {
        if (refused(player, deco)) {
            return;
        }
        World world = DisplayDecoService.world(deco.anchor());
        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        boolean harvest = !creative && harvests(deco.type(), player.getInventory().getItemInMainHand());
        breakSound(deco, world);
        service.remove(deco, "broken by " + player.getName(), harvest ? DisplayDecoService.Drop.TO_PLAYER
                : DisplayDecoService.Drop.NONE, DisplayDecoService.dropCount(deco, world), player, false);
    }

    /** Every tick: advance the timers. */
    void tick() {
        for (Map.Entry<UUID, Task> e : Map.copyOf(tasks).entrySet()) {
            Player player = Bukkit.getPlayer(e.getKey());
            Task task = e.getValue();
            World world = DisplayDecoService.world(task.deco.anchor());
            if (player == null || player.isDead() || world == null || !player.getWorld().equals(world)
                    || service.byId(task.deco.id()).isEmpty()
                    || player.getLocation().distanceSquared(center(task.block, world)) > 49) {
                tasks.remove(e.getKey());
                if (task.lastStage >= 0) {
                    cracks(task, 0f);
                }
                continue;
            }
            task.progress += progress(task.deco.type(), player);
            if (task.ticks++ % 4 == 0) {
                SoundGroup g = task.deco.type().sound().createBlockData().getSoundGroup();
                world.playSound(center(task.block, world), g.getHitSound(), SoundCategory.BLOCKS,
                        (g.getVolume() + 1.0F) / 8.0F, g.getPitch() * 0.5F);
            }
            int stage = (int) (task.progress * 10);
            if (stage != task.lastStage) {
                task.lastStage = stage;
                cracks(task, (float) Math.min(1.0, task.progress));
            }
            if (task.progress >= 1.0) {
                tasks.remove(e.getKey());
                cracks(task, 0f);
                breakNow(player, task.deco);
            }
        }
    }

    static void breakSound(DecoBlock deco, World world) {
        if (world == null) {
            return;
        }
        SoundGroup g = deco.type().sound().createBlockData().getSoundGroup();
        world.playSound(center(deco.anchor(), world), g.getBreakSound(), SoundCategory.BLOCKS,
                (g.getVolume() + 1.0F) / 2.0F, g.getPitch() * 0.8F);
    }

    /** The copied block's harvest rule ({@code requiresCorrectToolForDrops}): marble bars need a pickaxe. */
    static boolean harvests(DecoType type, ItemStack tool) {
        BlockData data = type.material().createBlockData();
        return !data.requiresCorrectToolForDrops() || data.isPreferredTool(tool);
    }

    /** Vanilla {@code getDestroyProgress} per tick for the mod's block. */
    static double progress(DecoType type, Player player) {
        ItemStack tool = player.getInventory().getItemInMainHand();
        BlockData data = type.material().createBlockData();
        double speed = data.getDestroySpeed(tool, true);
        PotionEffect haste = player.getPotionEffect(PotionEffectType.HASTE);
        PotionEffect fatigue = player.getPotionEffect(PotionEffectType.MINING_FATIGUE);
        if (haste != null) {
            speed *= 1.0 + 0.2 * (haste.getAmplifier() + 1);
        }
        if (fatigue != null) {
            speed *= switch (fatigue.getAmplifier()) {
                case 0 -> 0.3;
                case 1 -> 0.09;
                case 2 -> 0.0027;
                default -> 8.1e-4;
            };
        }
        speed *= attribute(player, Attribute.BLOCK_BREAK_SPEED, 1.0);
        if (player.isInWater() && player.getEyeLocation().getBlock().getType() == Material.WATER) {
            ItemStack helmet = player.getInventory().getHelmet();
            boolean aqua = helmet != null && helmet.containsEnchantment(Enchantment.AQUA_AFFINITY);
            speed *= aqua ? 1.0 : attribute(player, Attribute.SUBMERGED_MINING_SPEED, 0.2);
        }
        boolean onGround = player.getLocation().subtract(0, 0.05, 0).getBlock().getType().isSolid();
        if (!onGround) {
            speed /= 5.0;
        }
        return speed / type.hardness() / (harvests(type, tool) ? 30.0 : 100.0);
    }

    private static double attribute(Player player, Attribute attribute, double def) {
        AttributeInstance a = player.getAttribute(attribute);
        return a == null ? def : a.getValue();
    }

    private static Location center(Pos p, World w) {
        return new Location(w, p.x() + 0.5, p.y() + 0.5, p.z() + 0.5);
    }

    private void cracks(Task task, float progress) {
        World world = DisplayDecoService.world(task.deco.anchor());
        if (world == null) {
            return;
        }
        Location loc = new Location(world, task.block.x(), task.block.y(), task.block.z());
        int source = -(task.deco.id().hashCode() & 0x3fffffff) - 1;
        for (Player viewer : world.getPlayers()) {
            if (viewer.getLocation().distanceSquared(loc) < 32 * 32) {
                viewer.sendBlockDamage(loc, progress, source);
            }
        }
    }
}
