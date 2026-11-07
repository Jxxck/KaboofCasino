package de.tigges.kaboofcasino.deco.full;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Digging speed: the factor against the mod's numbers, and that the modifier never stays behind. */
class DecoDiggingTest {

    // ---- the factor --------------------------------------------------------------------------------------------

    /** Vanilla getDestroyProgress per tick. */
    private static double progress(double speed, double hardness, boolean harvest) {
        return speed / hardness / (harvest ? 30.0 : 100.0);
    }

    /**
     * The mushroom block (axe speed, never needs a tool) with the factor digs exactly like the mod's block, for any
     * Haste / Fatigue / other break-speed factor (they multiply both the same).
     */
    @Test
    void factorMakesTheMushroomDigLikeTheModsBlock() {
        float[] speeds = {1f, 1.5f, 2f, 4f, 5f, 6f, 8f, 9f, 12f, 15f, 32f};
        double[] others = {1.0, 0.3, 1.2, 1.4, 1.4 * 0.3, 0.2};
        for (DecoBlock.ModMaterial m : DecoBlock.ModMaterial.values()) {
            for (float modSpeed : speeds) {
                for (float mushSpeed : speeds) {
                    for (boolean harvest : new boolean[] {true, false}) {
                        double f = DecoDigging.factor(modSpeed, mushSpeed, m.hardness(), harvest);
                        for (double other : others) {
                            double mod = progress(modSpeed * other, m.hardness(), harvest);
                            double mushroom = progress(mushSpeed * other * f, DecoDigging.MUSHROOM_HARDNESS, true);
                            assertEquals(mod, mushroom, mod * 1e-6, m + " " + modSpeed + "/" + mushSpeed);
                        }
                    }
                }
            }
        }
    }

    /**
     * Ticks of digging as the client counts them: {@code Player.getDestroySpeed} (float: tool speed, x Haste, x the
     * {@code block_break_speed} value cast to float) / hardness / 30 or 100, summed in float until 1.0.
     */
    private static int clientTicks(float toolSpeed, float haste, double breakSpeed, float hardness, boolean harvest) {
        float speed = toolSpeed * haste * (float) breakSpeed;
        float perTick = speed / hardness / (float) (harvest ? 30 : 100);
        float sum = 0f;
        int ticks = 0;
        while (sum < 1.0f) {
            sum += perTick;
            ticks++;
        }
        return ticks;
    }

    /** The mushroom with the modifier: same number of ticks as the mod's block, in the client's float arithmetic. */
    private static int ticks(float modSpeed, float mushSpeed, DecoBlock.ModMaterial m, boolean harvest, float haste) {
        double factor = DecoDigging.factor(modSpeed, mushSpeed, m.hardness(), harvest);
        int mod = clientTicks(modSpeed, haste, 1.0, m.hardness(), harvest);
        int mushroom = clientTicks(mushSpeed, haste, 1.0 * (1.0 + (factor - 1.0)), DecoDigging.MUSHROOM_HARDNESS, true);
        assertEquals(mod, mushroom, m + " " + modSpeed + "/" + mushSpeed + " haste " + haste);
        return mod;
    }

    /** The times to tell the bot test (ticks; 20 per second), from vanilla 26.3 tool speeds. */
    @Test
    void expectedTimes() {
        // tool speeds: hand 1; iron axe 6 on mushroom/planks (mineable/axe), 1 on wool/stone; iron pickaxe 6 on
        // stone, 1 on mushroom/wool/planks; shears 5 on wool, 1 on mushroom; Haste II = x1.4
        assertEquals(24, ticks(1, 1, DecoBlock.ModMaterial.WOOL, true, 1f), "wool by hand");
        assertEquals(24, ticks(1, 6, DecoBlock.ModMaterial.WOOL, true, 1f), "wool, iron axe");
        assertEquals(18, ticks(1, 6, DecoBlock.ModMaterial.WOOL, true, 1.4f), "wool, iron axe + Haste II");
        assertEquals(5, ticks(5, 1, DecoBlock.ModMaterial.WOOL, true, 1f), "wool, shears");
        assertEquals(61, ticks(1, 1, DecoBlock.ModMaterial.PLANKS, true, 1f), "panel by hand (float sum)");
        assertEquals(10, ticks(6, 6, DecoBlock.ModMaterial.PLANKS, true, 1f), "panel, iron axe");
        assertEquals(8, ticks(6, 6, DecoBlock.ModMaterial.PLANKS, true, 1.4f), "panel, iron axe + Haste II");
        assertEquals(151, ticks(1, 1, DecoBlock.ModMaterial.STONE, false, 1f), "stone by hand (no drop)");
        assertEquals(151, ticks(1, 6, DecoBlock.ModMaterial.STONE, false, 1f), "stone, iron axe (no drop)");
        assertEquals(108, ticks(1, 6, DecoBlock.ModMaterial.STONE, false, 1.4f), "stone, iron axe + Haste II");
        assertEquals(8, ticks(6, 1, DecoBlock.ModMaterial.STONE, true, 1f), "stone, iron pickaxe");
        assertEquals(6, ticks(6, 1, DecoBlock.ModMaterial.STONE, true, 1.4f), "stone, iron pickaxe + Haste II");
        // the bare mushroom block for comparison: by hand 6 ticks, iron axe 1 (insta-mine)
        assertEquals(6, clientTicks(1, 1f, 1.0, DecoDigging.MUSHROOM_HARDNESS, true));
        assertEquals(1, clientTicks(6, 1f, 1.0, DecoDigging.MUSHROOM_HARDNESS, true));
    }

    /**
     * Every tool speed pairing and Haste level: at most one tick apart from the mod's block. The client sums float
     * progress, and where the mod's own sum lands within a float ulp of 1.0 the two orders of float operations can
     * round to different sides (e.g. 45 vs 46 ticks = 2.25 vs 2.3 s); the realistic table above is exact.
     */
    @Test
    void neverMoreThanATickFromTheMod() {
        float[] speeds = {1f, 1.5f, 2f, 4f, 5f, 6f, 8f, 9f, 12f, 15f, 32f, 35f};
        float[] haste = {1f, 1.2f, 1.4f, 0.3f};
        int cases = 0;
        int off = 0;
        for (DecoBlock.ModMaterial m : DecoBlock.ModMaterial.values()) {
            for (float modSpeed : speeds) {
                for (float mushSpeed : new float[] {1f, 2f, 4f, 6f, 8f, 9f, 12f, 35f}) {
                    for (boolean harvest : new boolean[] {true, false}) {
                        for (float h : haste) {
                            double factor = DecoDigging.factor(modSpeed, mushSpeed, m.hardness(), harvest);
                            int mod = clientTicks(modSpeed, h, 1.0, m.hardness(), harvest);
                            int mushroom = clientTicks(mushSpeed, h, factor, DecoDigging.MUSHROOM_HARDNESS, true);
                            assertTrue(Math.abs(mod - mushroom) <= 1, m + " " + modSpeed + "/" + mushSpeed);
                            cases++;
                            off += mod == mushroom ? 0 : 1;
                        }
                    }
                }
            }
        }
        assertTrue(off * 20 < cases, off + " of " + cases + " a tick off");
    }

    @Test
    void onlyStoneFloorsNeedATool() {
        for (DecoBlock b : DecoBlock.values()) {
            boolean stone = switch (b) {
                case IVORY_BLACK_CHECKERS, JADE_INLAY, IVORY_MARBLE, OBSIDIAN_MARBLE, GOLD_VEINED_BLACK_MARBLE -> true;
                default -> false;
            };
            assertEquals(stone, b.material().requiresTool(), b.name());
            assertEquals(stone ? DecoBlock.ModMaterial.STONE : b.name().contains("OAK")
                    ? DecoBlock.ModMaterial.PLANKS : DecoBlock.ModMaterial.WOOL, b.material(), b.name());
        }
        assertEquals(Material.POLISHED_ANDESITE, DecoDigging.material(DecoBlock.ModMaterial.STONE));
        assertEquals(Material.DARK_OAK_PLANKS, DecoDigging.material(DecoBlock.ModMaterial.PLANKS));
        assertEquals(Material.RED_WOOL, DecoDigging.material(DecoBlock.ModMaterial.WOOL));
        assertEquals("kaboofcasino:full_deco_dig", DecoDigging.KEY.asString());
    }

    // ---- the modifier's life -----------------------------------------------------------------------------------

    /** Records what the digging code does to each player's modifier. */
    private static final class FakeAccess implements DecoDigging.Access {
        final Map<UUID, Double> modifier = new HashMap<>();
        final List<String> calls = new ArrayList<>();

        @Override
        public void apply(Player player, double factor) {
            modifier.put(player.getUniqueId(), factor);
            calls.add("apply");
        }

        @Override
        public void clear(Player player) {
            modifier.remove(player.getUniqueId());
            calls.add("clear");
        }

        @Override
        public double reach(Player player) {
            return 4.5;
        }

        @Override
        public double factor(DecoBlock deco, Block block, org.bukkit.inventory.ItemStack tool) {
            return deco == DecoBlock.IVORY_MARBLE ? 0.04 : 0.5;
        }
    }

    private FakeAccess access;
    private boolean on;
    private World world;
    private Block marble;
    private Block stone;
    private FakePlayer bot;
    private DecoDigging digging;

    /** The player's state the proxy answers from. */
    private static final class FakePlayer {
        final UUID id = UUID.randomUUID();
        GameMode mode = GameMode.SURVIVAL;
        Block looking;
        Player proxy;
    }

    @BeforeEach
    void setUp() {
        access = new FakeAccess();
        on = true;
        world = (World) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {World.class},
                (p, m, a) -> switch (m.getName()) {
                    case "equals" -> p == a[0];
                    case "hashCode" -> System.identityHashCode(p);
                    default -> dflt(m.getReturnType());
                });
        marble = block(MushroomState.downTrue(MushroomBlock.STEM, MushroomState.UP | MushroomState.EAST
                | MushroomState.WEST), 2);
        stone = block(null, 3);
        bot = player();
        digging = new DecoDigging(() -> on, access, () -> List.of(bot.proxy));
    }

    /** A block at (0, 64, z); {@code state} null = plain stone. */
    private Block block(MushroomState state, int z) {
        BlockData data = state == null ? (BlockData) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {BlockData.class}, (p, m, a) -> m.getName().equals("getMaterial") ? Material.STONE
                        : dflt(m.getReturnType()))
                : (BlockData) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {MultipleFacing.class}, (p, m, a) -> switch (m.getName()) {
                    case "getMaterial" -> Material.MUSHROOM_STEM;
                    case "hasFace" -> switch ((BlockFace) a[0]) {
                        case DOWN -> state.down();
                        case UP -> state.up();
                        case NORTH -> state.north();
                        case EAST -> state.east();
                        case SOUTH -> state.south();
                        case WEST -> state.west();
                        default -> false;
                    };
                    default -> dflt(m.getReturnType());
                });
        return (Block) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Block.class},
                (p, m, a) -> switch (m.getName()) {
                    case "getBlockData" -> data;
                    case "getWorld" -> world;
                    case "getLocation" -> new Location(world, 0, 64, z);
                    case "equals" -> p == a[0];
                    case "hashCode" -> System.identityHashCode(p);
                    default -> dflt(m.getReturnType());
                });
    }

    private FakePlayer player() {
        FakePlayer f = new FakePlayer();
        PlayerInventory inv = (PlayerInventory) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {PlayerInventory.class}, (p, m, a) -> dflt(m.getReturnType()));
        f.proxy = (Player) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Player.class},
                (p, m, a) -> switch (m.getName()) {
                    case "getUniqueId" -> f.id;
                    case "getGameMode" -> f.mode;
                    case "isDead" -> false;
                    case "getInventory" -> inv;
                    case "getWorld" -> world;
                    case "getEyeLocation" -> new Location(world, 0.5, 65.6, 0.5);
                    case "rayTraceBlocks" -> f.looking == null ? null
                            : new RayTraceResult(new Vector(0.5, 64.5, 2.0), f.looking, BlockFace.NORTH);
                    case "equals" -> p == a[0];
                    case "hashCode" -> f.id.hashCode();
                    case "toString" -> "bot";
                    default -> dflt(m.getReturnType());
                });
        return f;
    }

    /** What an unanswered proxy method returns: zero for primitives, else null. */
    private static Object dflt(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return (char) 0;
        }
        return java.lang.reflect.Array.get(java.lang.reflect.Array.newInstance(type, 1), 0);
    }

    private boolean hasModifier() {
        return access.modifier.containsKey(bot.id);
    }

    private void lookAtMarble() {
        bot.looking = marble;
        digging.tick();
        assertEquals(0.04, access.modifier.get(bot.id));
    }

    @Test
    void lookingAtDecoSetsItLookingAwayRemovesIt() {
        digging.tick();
        assertFalse(hasModifier());
        lookAtMarble();
        bot.looking = stone;
        digging.tick();
        assertFalse(hasModifier());
        lookAtMarble();
        bot.looking = null;
        digging.tick();
        assertFalse(hasModifier());
    }

    @Test
    void creativeSpectatorAndSwitchOffNeverKeepIt() {
        lookAtMarble();
        bot.mode = GameMode.CREATIVE;
        digging.tick();
        assertFalse(hasModifier());
        bot.mode = GameMode.SPECTATOR;
        digging.tick();
        assertFalse(hasModifier());
        bot.mode = GameMode.ADVENTURE;
        lookAtMarble();
        on = false;
        digging.tick();
        assertFalse(hasModifier(), "Paper switch off: vanilla digging");
    }

    /** The left click sets it before Paper's insta-mine check; while digging it stays even if the ray slips off. */
    @Test
    void diggingKeepsItUntilAbortOrBreak() {
        bot.looking = null;
        digging.onLeftClick(new PlayerInteractEvent(bot.proxy, Action.LEFT_CLICK_BLOCK, null, marble,
                BlockFace.UP));
        assertEquals(0.04, access.modifier.get(bot.id), "set on the click, not a tick later");
        assertEquals(1, digging.diggingCount());
        digging.tick();
        assertTrue(hasModifier(), "the ray slipped off, the dig goes on");
        digging.onAbort(new BlockDamageAbortEvent(bot.proxy, marble, null));
        assertFalse(hasModifier());
        assertEquals(0, digging.diggingCount());

        digging.onLeftClick(new PlayerInteractEvent(bot.proxy, Action.LEFT_CLICK_BLOCK, null, marble,
                BlockFace.UP));
        digging.onBreak(new BlockBreakEvent(marble, bot.proxy));
        assertFalse(hasModifier(), "finished");
        assertEquals(0, digging.diggingCount());

        digging.onLeftClick(new PlayerInteractEvent(bot.proxy, Action.LEFT_CLICK_BLOCK, null, marble,
                BlockFace.UP));
        digging.onLeftClick(new PlayerInteractEvent(bot.proxy, Action.LEFT_CLICK_BLOCK, null, stone,
                BlockFace.UP));
        assertFalse(hasModifier(), "another block: vanilla speed at once");
        assertEquals(0, digging.diggingCount());
    }

    @Test
    void abortWhileStillLookingReappliesAtOnce() {
        lookAtMarble();
        access.calls.clear();
        digging.onAbort(new BlockDamageAbortEvent(bot.proxy, marble, null));
        assertEquals(List.of("clear", "apply"), access.calls);
        assertTrue(hasModifier());
    }

    @Test
    void slotAndHandChangesRemoveByKeyAndRecomputeForTheNewTool() {
        lookAtMarble();
        access.calls.clear();
        digging.onHeld(new PlayerItemHeldEvent(bot.proxy, 0, 3));
        assertEquals(List.of("clear", "apply"), access.calls);
        access.calls.clear();
        digging.onSwap(new PlayerSwapHandItemsEvent(bot.proxy, null, null));
        assertEquals(List.of("clear", "apply"), access.calls);
        bot.looking = stone;
        digging.onHeld(new PlayerItemHeldEvent(bot.proxy, 3, 4));
        assertFalse(hasModifier());
    }

    @Test
    void joinQuitDeathRespawnWorldAndGameModeRemoveIt() {
        List<Runnable> paths = List.of(
                () -> digging.onJoin(new PlayerJoinEvent(bot.proxy, Component.empty())),
                () -> digging.onQuit(new PlayerQuitEvent(bot.proxy, Component.empty(),
                        PlayerQuitEvent.QuitReason.DISCONNECTED)),
                () -> digging.onDeath(new org.bukkit.event.entity.PlayerDeathEvent(bot.proxy,
                        (org.bukkit.damage.DamageSource) Proxy.newProxyInstance(getClass().getClassLoader(),
                                new Class<?>[] {org.bukkit.damage.DamageSource.class},
                                (p, m, a) -> dflt(m.getReturnType())), new ArrayList<>(), 0, Component.empty(),
                        false)),
                () -> digging.onRespawn(new PlayerRespawnEvent(bot.proxy, new Location(world, 0, 64, 0), false,
                        false, false, PlayerRespawnEvent.RespawnReason.DEATH)),
                () -> digging.onWorld(new PlayerChangedWorldEvent(bot.proxy, world)),
                () -> digging.onGameMode(new PlayerGameModeChangeEvent(bot.proxy, GameMode.CREATIVE,
                        PlayerGameModeChangeEvent.Cause.COMMAND, Component.empty())));
        for (Runnable path : paths) {
            digging.onLeftClick(new PlayerInteractEvent(bot.proxy, Action.LEFT_CLICK_BLOCK, null, marble,
                    BlockFace.UP));
            assertTrue(hasModifier());
            path.run();
            assertFalse(hasModifier());
            assertEquals(0, digging.diggingCount());
        }
    }

    @Test
    void disableRemovesItFromEverybody() {
        lookAtMarble();
        digging.disable();
        assertFalse(hasModifier());
        assertEquals(0, digging.diggingCount());
    }
}
