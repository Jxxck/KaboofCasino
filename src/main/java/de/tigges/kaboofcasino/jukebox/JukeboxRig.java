package de.tigges.kaboofcasino.jukebox;

import de.tigges.kaboofcasino.machine.Facing;
import de.tigges.kaboofcasino.machine.Machine;
import de.tigges.kaboofcasino.visual.Displays;
import de.tigges.kaboofcasino.visual.RigMath;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The jukebox's look: the pack's cabinet ({@code kaboofcasino:jukebox/body}, both blocks) and the current track's
 * record behind the window glass, turning while the jukebox plays (a quarter turn every {@link #SPIN_TICKS} ticks,
 * interpolated by the client). Item displays standing at the anchor's bottom centre ({@link RigMath}); non-persistent,
 * rebuilt at chunk load. Thread: MAIN.
 */
final class JukeboxRig {

    static final String BODY = "kaboofcasino:jukebox/body";
    /** The record: the window's centre (px, south frame) a little in front of the recess's back. */
    static final double DISC_X = 8;
    static final double DISC_Y = 20.25;
    static final double DISC_Z = 12.2;
    static final float DISC_SCALE = 0.38f;
    static final int SPIN_TICKS = 10;
    static final float SPIN_STEP = (float) Math.toRadians(100);
    static final float VIEW_RANGE = 1.0f;

    private final Machine machine;
    private ItemDisplay body;
    private ItemDisplay disc;
    private String discItem;
    private float angle;

    JukeboxRig(Machine machine) {
        this.machine = machine;
    }

    static Transformation bodyTransformation(Facing facing) {
        return RigMath.part(facing, new Vector3f(0, 0.5f, 0));
    }

    static Transformation discTransformation(Facing facing, float angle) {
        Transformation t = RigMath.part(facing, RigMath.point(DISC_X, DISC_Y, DISC_Z),
                new Quaternionf().rotationZ(angle));
        return new Transformation(t.getTranslation(), t.getLeftRotation(), new Vector3f(DISC_SCALE),
                t.getRightRotation());
    }

    void spawn(World world, String discMaterial) {
        despawn();
        Location at = new Location(world, machine.anchor().x() + 0.5, machine.anchor().y(),
                machine.anchor().z() + 0.5);
        body = Displays.item(world, at, machine.id(), Displays.model(BODY), bodyTransformation(machine.facing()),
                VIEW_RANGE, d -> {
                    d.setDisplayWidth(1.4f);
                    d.setDisplayHeight(2.2f);
                });
        discItem = discMaterial;
        disc = Displays.item(world, at, machine.id(), disc(discMaterial), discTransformation(machine.facing(), -angle),
                VIEW_RANGE, d -> {
                    d.setDisplayWidth(1.4f);
                    d.setDisplayHeight(2.2f);
                    d.setInterpolationDuration(SPIN_TICKS);
                });
    }

    private static ItemStack disc(String material) {
        Material m = Material.matchMaterial(material);
        return new ItemStack(m == null || !m.isItem() ? Material.MUSIC_DISC_CAT : m);
    }

    /** The record of the current track (changed only when it differs). */
    void record(String material) {
        if (disc != null && disc.isValid() && !material.equals(discItem)) {
            disc.setItemStack(disc(material));
            discItem = material;
        }
    }

    /** One step of the turning record (called every {@link #SPIN_TICKS} ticks while playing). */
    void spin() {
        if (disc == null || !disc.isValid()) {
            return;
        }
        angle = (float) ((angle + SPIN_STEP) % (Math.PI * 2));
        disc.setInterpolationDelay(0);
        disc.setInterpolationDuration(SPIN_TICKS);
        disc.setTransformation(discTransformation(machine.facing(), -angle));
    }

    void despawn() {
        if (body != null) {
            Displays.remove(body);
            body = null;
        }
        if (disc != null) {
            Displays.remove(disc);
            disc = null;
        }
    }

    boolean intact() {
        return body != null && body.isValid() && disc != null && disc.isValid();
    }
}
