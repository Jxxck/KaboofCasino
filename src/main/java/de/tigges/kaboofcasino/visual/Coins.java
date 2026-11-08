package de.tigges.kaboofcasino.visual;

import de.tigges.kaboofcasino.util.Keys;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Payout "coins" of every machine: one short-lived ItemDisplay per won stack, flying out of the machine's mouth
 * towards the winner (the mod ejects item entities; here the items are already credited, so this is cosmetic).
 * Non-persistent and tagged as a machine part. Thread: MAIN.
 */
public final class Coins {

    private final UUID machine;
    private final List<Coin> coins = new ArrayList<>();

    private static final class Coin {
        final ItemDisplay display;
        final long removeAt;
        final Location target;
        boolean moved;

        Coin(ItemDisplay display, long removeAt, Location target) {
            this.display = display;
            this.removeAt = removeAt;
            this.target = target;
        }
    }

    public Coins(UUID machine) {
        this.machine = machine;
    }

    /**
     * A stack of {@code item} (a material key, or a chip denomination key: the chip's look, never a real chip) flying
     * from {@code from} to {@code target} over 10 ticks.
     */
    public void spawn(World world, Location from, String item, Location target, long now) {
        ItemStack look = de.tigges.kaboofcasino.wager.ChipItems.lookOf(item);
        ItemDisplay d = world.spawn(from, ItemDisplay.class, x -> {
            x.setPersistent(false);
            x.setItemStack(look);
            x.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
            x.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                    new Vector3f(0.6f, 0.6f, 0.6f), new Quaternionf()));
            x.setTeleportDuration(10);
            x.setViewRange(0.5f);
            x.getPersistentDataContainer().set(Keys.PART, PersistentDataType.STRING, machine.toString());
        });
        coins.add(new Coin(d, now + 12, target));
    }

    /** Moves fresh coins one tick after their spawn (teleport interpolation needs a spawned entity) and expires them. */
    public void tick(long now) {
        for (Iterator<Coin> it = coins.iterator(); it.hasNext(); ) {
            Coin c = it.next();
            if (!c.display.isValid() || now >= c.removeAt) {
                Displays.remove(c.display);
                it.remove();
            } else if (!c.moved) {
                c.moved = true;
                c.display.teleport(c.target);
            }
        }
    }

    public void clear() {
        for (Coin c : coins) {
            Displays.remove(c.display);
        }
        coins.clear();
    }

    public boolean flying() {
        return !coins.isEmpty();
    }
}
