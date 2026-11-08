package de.tigges.kaboofcasino.cashier;

import de.tigges.kaboofcasino.machine.Facing;
import de.tigges.kaboofcasino.machine.Machine;
import de.tigges.kaboofcasino.visual.Displays;
import de.tigges.kaboofcasino.visual.RigMath;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The cashier's look (step 9): MineVegas deco on its four barrier carriers, as item displays standing at the anchor's
 * bottom centre ({@link RigMath}): the counter is two {@code black_marble_bar} ends joined in the middle (the mod's
 * multipart: the left cell connects east, the right west, south frame) with their marble tops, the
 * {@code cash_register} and a {@code casino_chip_pile} on it, and the pack's glass screen with the KASSE sign at the
 * customer's edge ({@code kaboofcasino:cashier/screen}). Non-persistent, rebuilt at chunk load; no animation, no
 * light updates. Thread: MAIN.
 */
final class CashierRig {

    /**
     * One display: model, the blockstate {@code y} it is turned by in the south frame (degrees), and where the model's
     * centre (8, 8, 8) goes (blocks from the anchor's bottom centre, south frame: x to the viewer's right, z towards
     * him).
     */
    record Part(String model, int yRot, double x, double y, double z) {
    }

    static final List<Part> PARTS = List.of(
            new Part("kaboofcasino:deco/black_marble_bar_end", 90, 0, 0.5, 0),
            new Part("kaboofcasino:deco/black_marble_bar_top", 0, 0, 0.5, 0),
            new Part("kaboofcasino:deco/black_marble_bar_end", 270, 1, 0.5, 0),
            new Part("kaboofcasino:deco/black_marble_bar_top_v2", 0, 1, 0.5, 0),
            new Part("kaboofcasino:deco/cash_register", 0, 0, 1.5, -0.06),
            new Part("kaboofcasino:deco/casino_chip_pile", 0, 1, 1.5, -0.06),
            new Part("kaboofcasino:cashier/screen", 0, 0, 0.5, 0));

    /** The displays' culling box: both cells and the sign. */
    static final float WIDTH = 3.2f;
    static final float HEIGHT = 2.2f;
    static final float VIEW_RANGE = 1.0f;

    private final Machine machine;
    private final List<ItemDisplay> displays = new ArrayList<>();

    CashierRig(Machine machine) {
        this.machine = machine;
    }

    /** Every model key the rig uses (pack test). */
    static List<String> modelKeys() {
        return PARTS.stream().map(Part::model).distinct().toList();
    }

    static Transformation transformation(Facing facing, Part p) {
        Quaternionf local = new Quaternionf().rotationY((float) Math.toRadians(-p.yRot()));
        return RigMath.part(facing, new Vector3f((float) p.x(), (float) p.y(), (float) p.z()), local);
    }

    void spawn(World world) {
        despawn();
        Location at = new Location(world, machine.anchor().x() + 0.5, machine.anchor().y(),
                machine.anchor().z() + 0.5);
        for (Part p : PARTS) {
            displays.add(Displays.item(world, at, machine.id(), Displays.model(p.model()),
                    transformation(machine.facing(), p), VIEW_RANGE, d -> {
                        d.setDisplayWidth(WIDTH);
                        d.setDisplayHeight(HEIGHT);
                    }));
        }
    }

    void despawn() {
        displays.forEach(Displays::remove);
        displays.clear();
    }

    /** True if every display still exists ({@code /kill}, other plugins). */
    boolean intact() {
        return displays.size() == PARTS.size() && displays.stream().allMatch(ItemDisplay::isValid);
    }
}
