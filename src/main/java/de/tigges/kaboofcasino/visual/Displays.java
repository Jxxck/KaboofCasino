package de.tigges.kaboofcasino.visual;

import de.tigges.kaboofcasino.util.Keys;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.key.Key;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;

/**
 * Spawns rig entities: never persistent ({@code setPersistent(false)} inside the spawn consumer, before the entity is
 * added, so no autosave can catch it; server.md 3.5), tagged {@code kaboofcasino:part} with the machine id, culling box
 * set to the machine's size. Thread: MAIN.
 */
public final class Displays {

    private Displays() {
    }

    /** A display-only stack: paper with an {@code item_model}. Never handed to players. */
    public static ItemStack model(String modelKey) {
        ItemStack stack = ItemStack.of(Material.PAPER);
        stack.setData(DataComponentTypes.ITEM_MODEL, Key.key(modelKey));
        return stack;
    }

    public static ItemDisplay item(World world, Location at, UUID machine, ItemStack item, Transformation t,
                                   float viewRange, Consumer<ItemDisplay> extra) {
        return world.spawn(at, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setItemStack(item);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setTransformation(t);
            d.setViewRange(viewRange);
            d.setDisplayWidth(1.8f);
            d.setDisplayHeight(3.2f);
            d.setBillboard(Display.Billboard.FIXED);
            tag(d, machine);
            extra.accept(d);
        });
    }

    public static TextDisplay text(World world, Location at, UUID machine, Consumer<TextDisplay> extra) {
        return world.spawn(at, TextDisplay.class, d -> {
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.CENTER);
            tag(d, machine);
            extra.accept(d);
        });
    }

    private static void tag(Entity e, UUID machine) {
        e.getPersistentDataContainer().set(Keys.PART, PersistentDataType.STRING, machine.toString());
    }

    public static boolean isPart(Entity e) {
        return e.getPersistentDataContainer().has(Keys.PART);
    }

    public static void remove(Entity e) {
        if (e != null && e.isValid()) {
            e.remove();
        }
    }
}
