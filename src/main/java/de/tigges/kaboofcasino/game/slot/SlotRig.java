package de.tigges.kaboofcasino.game.slot;

import de.tigges.kaboofcasino.machine.Facing;
import de.tigges.kaboofcasino.machine.MachineRecord;
import de.tigges.kaboofcasino.visual.Coins;
import de.tigges.kaboofcasino.visual.Displays;
import de.tigges.kaboofcasino.visual.FloatingPanel;
import de.tigges.kaboofcasino.visual.RigMath;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The slot machine's display entities (render.md 2.5/3.2): 3 body parts (the mod's block models), 3 reel drums,
 * the lever, the lights (lamp + 15 bulbs tinted by {@code custom_model_data} colours), the floating bet panel, and
 * short-lived payout coins. All stand at the anchor's bottom centre, non-persistent. Thread: MAIN.
 */
final class SlotRig {

    private static final String[] BODY = {"lower", "upper", "top"};
    private static final Display.Brightness FULL = new Display.Brightness(15, 15);
    private static final Display.Brightness FLASH = new Display.Brightness(9, 9);

    private final MachineRecord record;
    private final World world;
    private final List<ItemDisplay> parts = new ArrayList<>();
    private final ItemDisplay[] reels = new ItemDisplay[3];
    private final int[] reelLevel = {-1, -1, -1};
    private final int[] reelFace = {-1, -1, -1};
    private final double[] reelTurn = new double[3];
    private ItemDisplay lever;
    private ItemDisplay lights;
    private final FloatingPanel panel;
    private boolean flashDim;
    private final Coins coins;

    SlotRig(MachineRecord record, World world) {
        this.record = record;
        this.world = world;
        this.panel = new FloatingPanel(record.id());
        this.coins = new Coins(record.id());
    }

    private Location base() {
        return new Location(world, record.anchor().x() + 0.5, record.anchor().y(), record.anchor().z() + 0.5);
    }

    private Facing facing() {
        return record.facing();
    }

    boolean spawned() {
        return lights != null;
    }

    void spawn(int[] stops, float[] colors) {
        despawn();
        Location base = base();
        for (int i = 0; i < 3; i++) {
            ItemDisplay d = Displays.item(world, base, record.id(),
                    Displays.model("roulettecasino:block/slot_machine_" + BODY[i]),
                    RigMath.part(facing(), RigMath.point(8, 8 + 16 * i, 8)), 2.0f, x -> { });
            parts.add(d);
        }
        for (int k = 0; k < 3; k++) {
            reelTurn[k] = stops[k];
            reelLevel[k] = 0;
            reelFace[k] = Math.floorMod(stops[k], 8);
            reels[k] = Displays.item(world, base, record.id(), Displays.model(drumModel(k, 0, reelFace[k])),
                    reelTransform(k, 0), 1.0f, d -> d.setBrightness(FULL));
            parts.add(reels[k]);
        }
        lever = Displays.item(world, base, record.id(), Displays.model("kaboofcasino:slot/lever"),
                leverTransform(SlotSpin.LEVER_REST), 1.0f, d -> { });
        parts.add(lever);
        lights = Displays.item(world, base, record.id(), lightsItem(colors),
                RigMath.part(facing(), RigMath.point(8, 40, 8)), 1.0f, d -> d.setBrightness(FULL));
        parts.add(lights);
        flashDim = false;
    }

    /** True if an entity vanished (/kill, another plugin): the controller respawns the rig. */
    boolean broken() {
        for (ItemDisplay d : parts) {
            if (!d.isValid()) {
                return true;
            }
        }
        return panel.broken();
    }

    void despawn() {
        parts.forEach(Displays::remove);
        parts.clear();
        lever = null;
        lights = null;
        panel.hide();
        coins.clear();
        for (int k = 0; k < 3; k++) {
            reels[k] = null;
        }
    }

    private static String drumModel(int reel, int level, int face) {
        return "kaboofcasino:slot/drum_" + reel + "_" + level + "_" + face;
    }

    /** The drum turned by {@code phase} faces (at most half a face: the model of the nearest face is shown). */
    private Transformation reelTransform(int reel, double phase) {
        Vector3f pivot = RigMath.point(SlotGeometry.drumCentreX(reel), SlotGeometry.AXIS_Y, SlotGeometry.AXIS_Z);
        return RigMath.part(facing(), pivot, new Quaternionf().rotationX((float) (phase * Math.PI / 4)));
    }

    private Transformation leverTransform(double degrees) {
        double[] p = SlotGeometry.LEVER_PIVOT;
        return RigMath.part(facing(), RigMath.point(p[0], p[1], p[2]),
                new Quaternionf().rotationX((float) Math.toRadians(degrees)));
    }

    /**
     * Reel positions: the model of the nearest face ({@code round(turn)}) turned by {@code turn - round(turn)} faces;
     * blur strip by speed ({@code < 0.35 / < 0.62 / else}). A model swap jumps (interpolation 0), otherwise the client
     * interpolates over {@code duration} ticks.
     */
    void reels(SlotSpin.Plan plan, double elapsed, int duration) {
        if (!spawned()) {
            return;
        }
        for (int k = 0; k < 3; k++) {
            double turn = plan.turn(k, elapsed);
            double speed = plan.speed(k, elapsed);
            int level = speed < 0.35 ? 0 : speed < 0.62 ? 1 : 2;
            long nearest = Math.round(turn);
            int face = (int) Math.floorMod(nearest, 8L);
            double phase = turn - nearest;
            ItemDisplay d = reels[k];
            boolean swap = level != reelLevel[k] || face != reelFace[k];
            if (swap) {
                reelLevel[k] = level;
                reelFace[k] = face;
                d.setItemStack(Displays.model(drumModel(k, level, face)));
            }
            if (swap || Math.abs(turn - reelTurn[k]) > 1e-4) {
                reelTurn[k] = turn;
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(swap ? 0 : duration);
                d.setTransformation(reelTransform(k, phase));
            }
        }
    }

    void lever(double degrees, int duration) {
        if (lever == null) {
            return;
        }
        lever.setInterpolationDelay(0);
        lever.setInterpolationDuration(duration);
        lever.setTransformation(leverTransform(degrees));
    }

    /** Drum flash during the celebration (the mod tints the drums 1.0 / 0.62): brightness 15 / 9. */
    void drumFlash(boolean dim) {
        if (dim == flashDim || !spawned()) {
            return;
        }
        flashDim = dim;
        for (ItemDisplay d : reels) {
            d.setBrightness(dim ? FLASH : FULL);
        }
    }

    /** colors[0] = lamp brightness, colors[1..15] = bulbs, all 0..1. */
    void lights(float[] colors) {
        if (lights != null) {
            lights.setItemStack(lightsItem(colors));
        }
    }

    /** The mod's tints: lamp {@code gray(0.3 + 0.7 l)}, bulbs {@code gray(0.28 + 0.72 b)}. */
    private static ItemStack lightsItem(float[] values) {
        ItemStack item = Displays.model("kaboofcasino:slot/lights");
        CustomModelData.Builder data = CustomModelData.customModelData();
        for (int i = 0; i < 16; i++) {
            double v = i == 0 ? 0.3 + 0.7 * values[0] : 0.28 + 0.72 * values[i];
            int g = (int) Math.round(255 * Math.clamp(v, 0.0, 1.0));
            data.addColor(Color.fromRGB(g, g, g));
        }
        item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, data.build());
        return item;
    }

    // ---- floating panel ------------------------------------------------------------------------------------------

    void panel(Component text) {
        panel.show(world, new Location(world, record.anchor().x() + 0.5, record.anchor().y() + SlotGeometry.PANEL_Y,
                record.anchor().z() + 0.5), text);
    }

    void hidePanel() {
        panel.hide();
    }

    // ---- payout coins --------------------------------------------------------------------------------------------

    /** A won stack flying out of the mouth towards {@code target} (cosmetic; the items are already credited). */
    void coin(String material, Location target, long now) {
        double[] m = SlotGeometry.COIN_MOUTH;
        double[] at = facing().toBlock(m[0], m[1], m[2]);
        coins.spawn(world, new Location(world, record.anchor().x() + at[0], record.anchor().y() + at[1],
                record.anchor().z() + at[2]), material, target, now);
    }

    void tickCoins(long now) {
        coins.tick(now);
    }

    boolean coinsFlying() {
        return coins.flying();
    }
}
