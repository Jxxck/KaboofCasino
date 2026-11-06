package de.tigges.kaboofcasino.game.plinko;

import de.tigges.kaboofcasino.machine.Facing;
import de.tigges.kaboofcasino.machine.MachineRecord;
import de.tigges.kaboofcasino.visual.Coins;
import de.tigges.kaboofcasino.visual.Displays;
import de.tigges.kaboofcasino.visual.FloatingPanel;
import de.tigges.kaboofcasino.visual.RigMath;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Plinko's entities ({@code PlinkoRenderer}): the nine body parts (board, pegs, pocket plates, chute, marquee are
 * static in the mod's models), one display for the 52 peg lamps and 11 bulbs (tints from the mod's orange ramp),
 * one for the 9 pocket rims (grey, hidden while no pocket glows), four balls (three in play plus the one waiting in
 * the chute) moved along {@code PlinkoDrop.Ball.position}, the floating panel and the payout coins. Board px are
 * machine-local px + (16, 16, 0). Thread: MAIN.
 */
final class PlinkoRig {

    static final Display.Brightness FULL = new Display.Brightness(15, 15);
    static final double PANEL_Y = 2.7;
    /** Payout coins leave at board (24, 7, 15). */
    static final double[] COIN_MOUTH = {24 - 16, 7 - 16, 15};
    static final int BALLS = 4;
    static final int LIGHTS = 52 + 11;

    private final MachineRecord record;
    private final World world;
    private final List<ItemDisplay> parts = new ArrayList<>();
    private ItemDisplay lights;
    private ItemDisplay rims;
    private boolean rimsShown;
    private final ItemDisplay[] balls = new ItemDisplay[BALLS];
    private final double[][] ballAt = new double[BALLS][];
    private final FloatingPanel panel;
    private final Coins coins;

    PlinkoRig(MachineRecord record, World world) {
        this.record = record;
        this.world = world;
        this.panel = new FloatingPanel(record.id());
        this.coins = new Coins(record.id());
    }

    private Facing facing() {
        return record.facing();
    }

    /**
     * The rig stands at the machine's bottom centre (one block below the anchor), so the displays' culling boxes
     * (3.2 x 3.2 blocks from there up) cover all three rows; pivots are given in anchor-local px.
     */
    private Location base() {
        return new Location(world, record.anchor().x() + 0.5, record.anchor().y() - 1, record.anchor().z() + 0.5);
    }

    /** An anchor-local px point as a pivot relative to the rig entity (16 px lower). */
    private static org.joml.Vector3f point(double x, double y, double z) {
        return RigMath.point(x, y + 16, z);
    }

    boolean spawned() {
        return lights != null;
    }

    static List<String> modelKeys() {
        List<String> out = new ArrayList<>();
        for (int part = 0; part < 9; part++) {
            out.add("roulettecasino:block/plinko_" + part);
        }
        out.add("kaboofcasino:plinko/ball");
        out.add("kaboofcasino:plinko/lights");
        out.add("kaboofcasino:plinko/rims");
        return out;
    }

    void spawn(float[] lampLevels, float[] bulbLevels) {
        despawn();
        Location base = base();
        for (int part = 0; part < 9; part++) {
            int col = part % 3;
            int row = part / 3;
            parts.add(Displays.item(world, base, record.id(), Displays.model("roulettecasino:block/plinko_" + part),
                    RigMath.part(facing(), point(16 * (col - 1) + 8, 16 * (row - 1) + 8, 8)), 2.0f,
                    d -> { }));
        }
        lights = Displays.item(world, base, record.id(), lightsItem(lampLevels, bulbLevels),
                RigMath.part(facing(), point(8, 8, 8)), 2.0f, d -> d.setBrightness(FULL));
        parts.add(lights);
        rims = Displays.item(world, base, record.id(), ItemStack.empty(),
                RigMath.part(facing(), point(8, 8, 8)), 2.0f, d -> d.setBrightness(FULL));
        parts.add(rims);
        rimsShown = false;
        for (int i = 0; i < BALLS; i++) {
            ballAt[i] = null;
            balls[i] = Displays.item(world, base, record.id(), Displays.model("kaboofcasino:plinko/ball"),
                    hidden(), 2.0f, d -> { });
            parts.add(balls[i]);
        }
        cullingBoxes();
    }

    /** Culling box of every display: the whole 3x3 machine. */
    private void cullingBoxes() {
        for (ItemDisplay d : parts) {
            d.setDisplayWidth(3.2f);
            d.setDisplayHeight(3.2f);
        }
    }

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
        lights = null;
        rims = null;
        java.util.Arrays.fill(balls, null);
        panel.hide();
        coins.clear();
    }

    private Transformation hidden() {
        return new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(), new Quaternionf());
    }

    /** The ball's model centre at board (x, y, 12). */
    Transformation ballTransform(double x, double y) {
        return RigMath.part(facing(), point(x - 16, y - 16, PlinkoGame.BALL_Z));
    }

    /**
     * Ball {@code i} at board (x, y), or hidden for {@code null}; moving balls interpolate over {@code duration}
     * ticks, a ball that appears jumps.
     */
    void ball(int i, double[] at, int duration) {
        ItemDisplay d = balls[i];
        if (d == null) {
            return;
        }
        double[] before = ballAt[i];
        if (at == null) {
            if (before != null) {
                ballAt[i] = null;
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(0);
                d.setTransformation(hidden());
            }
            return;
        }
        if (before != null && Math.abs(before[0] - at[0]) < 1e-4 && Math.abs(before[1] - at[1]) < 1e-4) {
            return;
        }
        ballAt[i] = at.clone();
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(before == null ? 0 : duration);
        d.setTransformation(ballTransform(at[0], at[1]));
    }

    /** The mod's {@code lampTint}: 0 -> #A87542 ... 1 -> white (lamps and bulbs). */
    static Color lampTint(double level) {
        double t = Math.clamp(level, 0.0, 1.0);
        return Color.fromRGB((int) Math.round(255 * (0.66 + 0.34 * t)), (int) Math.round(255 * (0.46 + 0.54 * t)),
                (int) Math.round(255 * (0.26 + 0.74 * t)));
    }

    void lights(float[] lamps, float[] bulbs) {
        if (lights != null) {
            lights.setItemStack(lightsItem(lamps, bulbs));
        }
    }

    private static ItemStack lightsItem(float[] lamps, float[] bulbs) {
        ItemStack item = Displays.model("kaboofcasino:plinko/lights");
        CustomModelData.Builder data = CustomModelData.customModelData();
        for (float l : lamps) {
            data.addColor(lampTint(l));
        }
        for (float b : bulbs) {
            data.addColor(lampTint(b));
        }
        item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, data.build());
        return item;
    }

    /** Pocket rims {@code gray(0.45 + 0.55 p)}; the mod draws none while every level is at most 0.01. */
    void pockets(float[] levels) {
        if (rims == null) {
            return;
        }
        boolean any = false;
        for (float p : levels) {
            any |= p > 0.01f;
        }
        if (!any) {
            if (rimsShown) {
                rims.setItemStack(ItemStack.empty());
                rimsShown = false;
            }
            return;
        }
        ItemStack item = Displays.model("kaboofcasino:plinko/rims");
        CustomModelData.Builder data = CustomModelData.customModelData();
        for (float p : levels) {
            int g = (int) Math.round(255 * Math.clamp(0.45 + 0.55 * p, 0.0, 1.0));
            data.addColor(Color.fromRGB(g, g, g));
        }
        item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, data.build());
        rims.setItemStack(item);
        rimsShown = true;
    }

    void panel(Component text) {
        panel.show(world, new Location(world, record.anchor().x() + 0.5, record.anchor().y() + PANEL_Y,
                record.anchor().z() + 0.5), text);
    }

    void hidePanel() {
        panel.hide();
    }

    void coin(Material material, Location target, long now) {
        double[] at = facing().toBlock(COIN_MOUTH[0], COIN_MOUTH[1], COIN_MOUTH[2]);
        coins.spawn(world, new Location(world, record.anchor().x() + at[0], record.anchor().y() + at[1],
                record.anchor().z() + at[2]), material, target, now);
    }

    void tickCoins(long now) {
        coins.tick(now);
    }
}
