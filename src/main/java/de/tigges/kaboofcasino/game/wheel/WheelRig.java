package de.tigges.kaboofcasino.game.wheel;

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

/**
 * The fortune wheel's entities ({@code FortuneWheelRenderer}): the nine body parts, the plate (rotating about the
 * axle (8, 11) px by {@code -turn x 22.5} degrees; blur model by speed), the pennant (rotating about its hinge
 * (8, 30) px by the flapper angle, world light like the mod), the 16 rim bulbs (one model, tints = brightness),
 * the floating panel and the payout coins. All stand at the anchor's bottom centre, non-persistent. Thread: MAIN.
 */
final class WheelRig {

    static final Display.Brightness FULL = new Display.Brightness(15, 15);
    /** The mod tints the plate grey 0.62 on the flash beats: brightness 9 of 15. */
    static final Display.Brightness FLASH = new Display.Brightness(9, 9);
    static final double PANEL_Y = 2.7;
    static final double[] COIN_MOUTH = {8, -11.5, 15};
    /**
     * The pennant's swing limit, the mod's own (36 degrees). There a top corner of the pennant reaches 0.154 px above
     * the machine (y 32.154), exactly as in the mod (WheelGeometryTest allows that).
     */
    static final double PENNANT_MAX = FortuneWheelSpin.FLAP_LIMIT;

    private final MachineRecord record;
    private final World world;
    private final List<ItemDisplay> parts = new ArrayList<>();
    private ItemDisplay plate;
    private ItemDisplay pennant;
    private ItemDisplay bulbs;
    private int plateLevel = -1;
    private double plateTurn = Double.NaN;
    private double pennantAngle = Double.NaN;
    private boolean plateDim;
    private final FloatingPanel panel;
    private final Coins coins;

    WheelRig(MachineRecord record, World world) {
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
        return bulbs != null;
    }

    /** Model keys the rig uses (checked against the pack by a unit test). */
    static List<String> modelKeys() {
        List<String> out = new ArrayList<>();
        for (int part = 0; part < 9; part++) {
            out.add("roulettecasino:block/fortune_wheel_" + part);
        }
        out.add(PLATE);
        out.add("kaboofcasino:wheel/pennant");
        out.add("kaboofcasino:wheel/bulbs");
        return out;
    }

    /** The plate item: sharp, blurred or a cross-fade, chosen by its custom_model_data float (see the pack). */
    static final String PLATE = "kaboofcasino:wheel/plate";

    private static ItemStack plateItem(double blend) {
        ItemStack item = Displays.model(PLATE);
        item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData()
                .addFloat((float) blend).build());
        return item;
    }

    void spawn(int stop, float[] levels) {
        despawn();
        Location base = base();
        for (int part = 0; part < 9; part++) {
            int col = part % 3;
            int row = part / 3;
            parts.add(Displays.item(world, base, record.id(),
                    Displays.model("roulettecasino:block/fortune_wheel_" + part),
                    RigMath.part(facing(), point(16 * (col - 1) + 8, 16 * (row - 1) + 8, 8)), 2.0f,
                    d -> { }));
        }
        plateLevel = 0;
        plateTurn = FortuneWheelSpin.restTurn(stop);
        plate = Displays.item(world, base, record.id(), plateItem(0), plateTransform(plateTurn),
                2.0f, d -> d.setBrightness(FULL));
        parts.add(plate);
        pennantAngle = 0;
        pennant = Displays.item(world, base, record.id(), Displays.model("kaboofcasino:wheel/pennant"),
                pennantTransform(0), 2.0f, d -> { });
        parts.add(pennant);
        bulbs = Displays.item(world, base, record.id(), bulbsItem(levels),
                RigMath.part(facing(), point(8, 8, 8)), 2.0f, d -> d.setBrightness(FULL));
        parts.add(bulbs);
        cullingBoxes();
        plateDim = false;
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
        plate = null;
        pennant = null;
        bulbs = null;
        panel.hide();
        coins.clear();
    }

    /** The plate about the axle: model centre = axle (8, 11) at z 11. */
    Transformation plateTransform(double turn) {
        return RigMath.part(facing(), point(8, 11, 11),
                new Quaternionf().rotationZ((float) Math.toRadians(-turn * FortuneWheelSpin.DEGREES)));
    }

    /** The pennant about its hinge (8, 30); its model has z - 8 = wheel-local z, so the pivot is at z 0. */
    Transformation pennantTransform(double degrees) {
        return RigMath.part(facing(), point(8, 30, 0),
                new Quaternionf().rotationZ((float) Math.toRadians(degrees)));
    }

    /**
     * The mod's blur state as one number: {@code lower + weight} of {@code FortuneWheelBlur.at(speed)} (0 = sharp,
     * 1 = blur 1, 2 = blur 2, in between the cross-fade), quantised to the pack's quarter steps.
     */
    static int blendStep(double speed) {
        FortuneWheelBlur.Blend b = FortuneWheelBlur.at(speed);
        return (int) Math.round((b.lower() + (b.lower() == b.upper() ? 0 : b.weight())) * 4);
    }

    /** Plate position for {@code turn}; the client interpolates over {@code duration} ticks. */
    void plate(double turn, double speed, int duration) {
        if (plate == null) {
            return;
        }
        int step = blendStep(speed);
        if (step != plateLevel) {
            plateLevel = step;
            plate.setItemStack(plateItem(step / 4.0));
        }
        if (Math.abs(turn - plateTurn) > 1e-4) {
            plateTurn = turn;
            plate.setInterpolationDelay(0);
            plate.setInterpolationDuration(duration);
            plate.setTransformation(plateTransform(turn));
        }
    }

    void pennant(double flapper, int duration) {
        double degrees = Math.clamp(flapper, -PENNANT_MAX, PENNANT_MAX);
        if (pennant == null || Math.abs(degrees - pennantAngle) < 1e-3) {
            return;
        }
        pennantAngle = degrees;
        pennant.setInterpolationDelay(0);
        pennant.setInterpolationDuration(duration);
        pennant.setTransformation(pennantTransform(degrees));
    }

    /** The mod's face flash (plate tint 1.0 / 0.62): brightness 15 / 9. */
    void plateFlash(boolean dim) {
        if (dim == plateDim || plate == null) {
            return;
        }
        plateDim = dim;
        plate.setBrightness(dim ? FLASH : FULL);
    }

    /** levels[i] = brightness 0..1 of bulb i. */
    void bulbs(float[] levels) {
        if (bulbs != null) {
            bulbs.setItemStack(bulbsItem(levels));
        }
    }

    /** The mod's bulb tint {@code gray(0.28 + 0.72 b)}. */
    private static ItemStack bulbsItem(float[] levels) {
        ItemStack item = Displays.model("kaboofcasino:wheel/bulbs");
        CustomModelData.Builder data = CustomModelData.customModelData();
        for (int i = 0; i < FortuneWheelSpin.BULBS; i++) {
            int g = (int) Math.round(255 * Math.clamp(0.28 + 0.72 * levels[i], 0.0, 1.0));
            data.addColor(Color.fromRGB(g, g, g));
        }
        item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, data.build());
        return item;
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
