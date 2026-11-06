package de.tigges.kaboofcasino.visual;

import de.tigges.kaboofcasino.machine.Facing;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Display transformations for machine parts (render.md 2.2). All rig entities stand at the anchor block's bottom
 * centre with yaw/pitch 0; a part's transformation places and turns its model:
 *
 * <pre>
 * world = entity + T + L * R180 * (m/16 - 0.5)       (client: ItemDisplayTransform NONE, then a 180 degree Y turn)
 * want  = entity + Rf * (pivot + Q * (m - 8)/16)      (Rf = facing turn, Q = the part's own rotation)
 * =>  T = Rf * pivot,  L = Rf * Q * R180  (R180 is its own inverse)
 * </pre>
 * {@code pivot} is the machine-frame point (blocks, south facing, relative to the anchor's bottom centre) where the
 * model's centre (8, 8, 8) goes. Rf = rotationY(-turns * 90 degrees), exactly the mod's renderer turn. Pure.
 */
public final class RigMath {

    private static final Quaternionf R180 = new Quaternionf().rotationY((float) Math.PI);

    private RigMath() {
    }

    /** A model point in px (south frame, lower-block coordinates) as a machine-frame vector in blocks. */
    public static Vector3f point(double px, double py, double pz) {
        return new Vector3f((float) ((px - 8) / 16.0), (float) (py / 16.0), (float) ((pz - 8) / 16.0));
    }

    public static Quaternionf facing(Facing facing) {
        return new Quaternionf().rotationY((float) (-facing.turns() * Math.PI / 2));
    }

    public static Transformation part(Facing facing, Vector3f pivot, Quaternionf local) {
        Quaternionf rf = facing(facing);
        Vector3f t = rf.transform(new Vector3f(pivot));
        Quaternionf left = new Quaternionf(rf).mul(local).mul(R180);
        return new Transformation(t, left, new Vector3f(1, 1, 1), new Quaternionf());
    }

    public static Transformation part(Facing facing, Vector3f pivot) {
        return part(facing, pivot, new Quaternionf());
    }

    /** Where model point m (px, in a part's model space) ends up, relative to the entity (for tests). */
    public static Vector3f apply(Transformation t, double mx, double my, double mz) {
        Vector3f v = new Vector3f((float) (mx / 16 - 0.5), (float) (my / 16 - 0.5), (float) (mz / 16 - 0.5));
        R180.transform(v);
        t.getRightRotation().transform(v);
        v.mul(t.getScale());
        t.getLeftRotation().transform(v);
        return v.add(t.getTranslation());
    }
}
