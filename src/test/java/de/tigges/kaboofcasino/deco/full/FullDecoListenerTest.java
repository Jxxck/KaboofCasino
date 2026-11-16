package de.tigges.kaboofcasino.deco.full;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import de.tigges.kaboofcasino.config.Messages;
import io.papermc.paper.event.player.PlayerPickBlockEvent;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.ExplosionResult;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Paper's switch off: reserved states behave like vanilla mushroom blocks (vanilla loot, nothing placed, nothing
 * minted); on: the loot becomes the deco item, creative and unbroken blocks give nothing.
 */
class FullDecoListenerTest {

    private boolean on;
    private GameMode mode;
    private World world;
    private Player player;
    private BlockData trellis;
    private BlockData air;
    private FullDecoListener listener;

    @BeforeEach
    void setUp() throws Exception {
        on = true;
        mode = GameMode.SURVIVAL;
        world = proxy(World.class, (p, m, a) -> switch (m.getName()) {
            case "equals" -> p == a[0];
            case "hashCode" -> System.identityHashCode(p);
            default -> dflt(m.getReturnType());
        });
        // ruby_trellis = mushroom_stem[down=true], every other face false
        trellis = mushroom(FullDecoStates.itemState(DecoBlock.RUBY_TRELLIS).state());
        air = proxy(BlockData.class, (p, m, a) -> switch (m.getName()) {
            case "getMaterial" -> Material.AIR;
            case "equals" -> p == a[0];
            case "hashCode" -> System.identityHashCode(p);
            default -> dflt(m.getReturnType());
        });
        PlayerInventory inv = proxy(PlayerInventory.class, (p, m, a) -> dflt(m.getReturnType()));
        player = proxy(Player.class, (p, m, a) -> switch (m.getName()) {
            case "getGameMode" -> mode;
            case "getInventory" -> inv;
            case "getWorld" -> world;
            case "equals" -> p == a[0];
            case "hashCode" -> System.identityHashCode(p);
            default -> dflt(m.getReturnType());
        });
        Path file = Files.createTempDirectory("fulldeco").resolve(de.tigges.kaboofcasino.config.Language.current().messagesFile());
        Messages messages = Messages.load(file, () -> getClass().getClassLoader()
                .getResourceAsStream(de.tigges.kaboofcasino.config.Language.current().messagesFile())).messages();
        listener = new FullDecoListener(null, null, () -> messages, () -> on);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler h) {
        return (T) Proxy.newProxyInstance(FullDecoListenerTest.class.getClassLoader(), new Class<?>[] {type}, h);
    }

    private static Object dflt(Class<?> type) {
        return !type.isPrimitive() || type == void.class ? null
                : java.lang.reflect.Array.get(java.lang.reflect.Array.newInstance(type, 1), 0);
    }

    private static BlockData mushroom(MushroomState s) {
        return proxy(MultipleFacing.class, (p, m, a) -> switch (m.getName()) {
            case "getMaterial" -> Material.MUSHROOM_STEM;
            case "hasFace" -> switch ((BlockFace) a[0]) {
                case DOWN -> s.down();
                case UP -> s.up();
                case NORTH -> s.north();
                case EAST -> s.east();
                case SOUTH -> s.south();
                case WEST -> s.west();
                default -> false;
            };
            case "equals" -> p == a[0];
            case "hashCode" -> System.identityHashCode(p);
            default -> dflt(m.getReturnType());
        });
    }

    private Block block(BlockData data) {
        return proxy(Block.class, (p, m, a) -> switch (m.getName()) {
            case "getBlockData" -> data;
            case "getType" -> data.getMaterial();
            case "getWorld" -> world;
            case "getLocation" -> new Location(world, 0, 64, 0);
            case "equals" -> p == a[0];
            case "hashCode" -> System.identityHashCode(p);
            default -> dflt(m.getReturnType());
        });
    }

    private BlockState state(BlockData data) {
        return proxy(BlockState.class, (p, m, a) -> m.getName().equals("getBlockData") ? data
                : dflt(m.getReturnType()));
    }

    private List<Item> mushrooms() {
        List<Item> items = new ArrayList<>();
        items.add(proxy(Item.class, (p, m, a) -> dflt(m.getReturnType())));
        return items;
    }

    /** A survival break of ruby_trellis: the block is air now, the event carries the old state. */
    private BlockDropItemEvent broken(BlockData now) {
        return new BlockDropItemEvent(block(now), state(trellis), player, mushrooms());
    }

    @Test
    void switchOnTheLootBecomesOneDecoItem() {
        BlockDropItemEvent e = broken(air);
        listener.onDropItems(e);
        assertTrue(e.getItems().isEmpty(), "no mushrooms");
        assertEquals(1, listener.pendingDrops());
    }

    @Test
    void creativeAndUnbrokenGiveNothing() {
        mode = GameMode.CREATIVE;
        BlockDropItemEvent creative = broken(air);
        listener.onDropItems(creative);
        assertTrue(creative.getItems().isEmpty());
        assertEquals(0, listener.pendingDrops());
        mode = GameMode.SURVIVAL;
        BlockDropItemEvent stillThere = broken(trellis);
        listener.onDropItems(stillThere);
        assertEquals(0, listener.pendingDrops(), "the block was not removed: no dupe");
    }

    @Test
    void switchOffVanillaLootAndNothingMinted() {
        on = false;
        BlockDropItemEvent e = broken(air);
        listener.onDropItems(e);
        assertEquals(1, e.getItems().size(), "vanilla mushroom loot stays");
        assertEquals(0, listener.pendingDrops());

        BlockDestroyEvent destroy = new BlockDestroyEvent(block(trellis), air, trellis, 0, true);
        listener.onDestroy(destroy);
        assertTrue(destroy.willDrop(), "vanilla loot");

        List<Block> blocks = new ArrayList<>(List.of(block(trellis)));
        Entity tnt = proxy(Entity.class, (p, m, a) -> dflt(m.getReturnType()));
        listener.onEntityExplode(new EntityExplodeEvent(tnt, new Location(world, 0, 64, 0), blocks, 1f,
                ExplosionResult.DESTROY));
        assertEquals(1, blocks.size(), "stays in the vanilla explosion");

        PlayerPickBlockEvent pick = new PlayerPickBlockEvent(player, block(trellis), null, false, 0, 0);
        listener.onPick(pick);
        assertFalse(pick.isCancelled(), "vanilla pick");

        BlockPlaceEvent place = new BlockPlaceEvent(block(trellis), state(air), block(air), null, player, true,
                EquipmentSlot.HAND);
        listener.onPlaceGuard(place);
        assertTrue(place.isCancelled(), "no placing");
    }

    @Test
    void switchOnExplosionsTakeDecoOutOfTheVanillaList() {
        Block deco = block(trellis);
        Block stone = block(air);
        Entity creeper = proxy(Entity.class, (p, m, a) -> dflt(m.getReturnType()));
        List<Block> blocks = new ArrayList<>(List.of(deco, stone));
        listener.onEntityExplode(new EntityExplodeEvent(creeper, new Location(world, 0, 64, 0), blocks, 1f / 3f,
                ExplosionResult.DESTROY_WITH_DECAY));
        assertEquals(List.of(stone), blocks);
        List<Block> wind = new ArrayList<>(List.of(deco));
        listener.onEntityExplode(new EntityExplodeEvent(creeper, new Location(world, 0, 64, 0), wind, 1f,
                ExplosionResult.TRIGGER_BLOCK));
        assertEquals(1, wind.size(), "wind charges do not break blocks");

        BlockPlaceEvent place = new BlockPlaceEvent(block(trellis), state(air), block(air), null, player, true,
                EquipmentSlot.HAND);
        listener.onPlaceGuard(place);
        assertFalse(place.isCancelled());
    }
}
