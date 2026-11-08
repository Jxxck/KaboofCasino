package de.tigges.kaboofcasino.chipshop;

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
 * The Chip-Shop's look (step 11): the cashier's counter (two {@code black_marble_bar} ends with their marble tops), the
 * pack's glass display case on it ({@code kaboofcasino:chipshop/display}) and its CHIP-SHOP sign at the customer's
 * edge ({@code kaboofcasino:chipshop/sign}), as item displays standing at the anchor's bottom centre
 * ({@link RigMath}). Non-persistent, rebuilt at chunk load; no animation, no light updates. Thread: MAIN.
 */
final class ChipShopRig {

    /** One display: model, blockstate y (degrees, south frame) and where the model's centre (8, 8, 8) goes. */
    record Part(String model, int yRot, double x, double y, double z) {
    }

    static final List<Part> PARTS = List.of(
            new Part("kaboofcasino:deco/black_marble_bar_end", 90, 0, 0.5, 0),
            new Part("kaboofcasino:deco/black_marble_bar_top", 0, 0, 0.5, 0),
            new Part("kaboofcasino:deco/black_marble_bar_end", 270, 1, 0.5, 0),
            new Part("kaboofcasino:deco/black_marble_bar_top_v2", 0, 1, 0.5, 0),
            new Part("kaboofcasino:chipshop/display", 0, 0, 1.5, -0.06),
            new Part("kaboofcasino:chipshop/sign", 0, 0, 0.5, 0));

    static final float WIDTH = 3.2f;
    static final float HEIGHT = 2.2f;
    static final float VIEW_RANGE = 1.0f;

    private final Machine machine;
    private final List<ItemDisplay> displays = new ArrayList<>();

    ChipShopRig(Machine machine) {
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

    boolean intact() {
        return displays.size() == PARTS.size() && displays.stream().allMatch(ItemDisplay::isValid);
    }
}
