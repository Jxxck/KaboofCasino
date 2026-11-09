package de.tigges.kaboofcasino.deco.display;

import de.tigges.kaboofcasino.util.Keys;
import de.tigges.kaboofcasino.util.Pos;
import de.tigges.kaboofcasino.visual.Displays;
import java.util.List;
import java.util.Locale;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.persistence.PersistentDataType;

/**
 * The entities of one decoration (server.md 3.5): one item display per {@link DecoModels.Part} at its cell's bottom
 * centre (non-persistent, tagged {@code kaboofcasino:part}, so an orphan saved by a bug is removed at load), plus one
 * Interaction per click box for light carriers. A refresh swaps models in place (connections, open/closed, on/off,
 * stack count, stair shape) and respawns anything killed. Thread: MAIN.
 */
final class DecoRig {

    private static final BlockFace[] SIDES = {BlockFace.UP, BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH,
            BlockFace.WEST, BlockFace.DOWN};

    private DecoRig() {
    }

    /** The vanilla carrier's state as the mod's blockstate names it (null if the carrier is not the expected one). */
    static DecoModels.Carrier carrier(DecoType type, Block block) {
        BlockData data = block.getBlockData();
        if (type.carrier() == DecoType.Carrier.STAIRS && data instanceof Stairs s) {
            return DecoModels.Carrier.stairs(s.getFacing().name().toLowerCase(Locale.ROOT),
                    s.getHalf() == Bisected.Half.TOP ? "top" : "bottom", s.getShape().name().toLowerCase(Locale.ROOT));
        }
        if (type.carrier() == DecoType.Carrier.SLAB && data instanceof Slab s) {
            return DecoModels.Carrier.slab(s.getType().name().toLowerCase(Locale.ROOT));
        }
        return null;
    }

    static List<DecoModels.Part> parts(DecoBlock b, World world) {
        DecoRecord r = b.record();
        DecoModels.Carrier c = r.type().vanillaCarrier() ? carrier(r.type(), r.anchor().block(world)) : null;
        return DecoModels.parts(r, c);
    }

    /** Spawns everything (after a despawn). */
    static void spawn(DecoBlock b, World world) {
        despawn(b);
        for (DecoModels.Part part : parts(b, world)) {
            b.displays.add(display(b, world, part));
            b.keys.add(part.key());
            b.yaws.add(part.yRot());
        }
        if (b.type().carrier() == DecoType.Carrier.LIGHT) {
            for (DecoGeometry.Box box : DecoGeometry.clickBoxes(b.record())) {
                b.clicks.add(interaction(b, world, box));
            }
        }
        b.clickSignature = clickSignature(b);
        brightness(b, world);
    }

    /**
     * Brings the entities in line with the record and the carrier: swaps changed models, respawns when the number of
     * parts changed or an entity is gone. Returns true if anything changed.
     */
    static boolean refresh(DecoBlock b, World world) {
        List<DecoModels.Part> parts = parts(b, world);
        boolean dead = b.displays.stream().anyMatch(d -> !d.isValid()) || b.clicks.stream().anyMatch(i -> !i.isValid());
        if (dead || parts.size() != b.displays.size() || !clickSignature(b).equals(b.clickSignature)) {
            spawn(b, world);
            return true;
        }
        boolean changed = false;
        for (int i = 0; i < parts.size(); i++) {
            DecoModels.Part part = parts.get(i);
            if (!part.key().equals(b.keys.get(i)) || part.yRot() != b.yaws.get(i)) {
                ItemDisplay d = b.displays.get(i);
                d.setItemStack(Displays.model(part.key()));
                d.setTransformation(transformation(part));
                b.keys.set(i, part.key());
                b.yaws.set(i, part.yRot());
                changed = true;
            }
        }
        brightness(b, world);
        return changed;
    }

    /** The click boxes the record wants (light carriers; empty otherwise), as a comparable string. */
    static String clickSignature(DecoBlock b) {
        return b.type().carrier() == DecoType.Carrier.LIGHT ? DecoGeometry.clickBoxes(b.record()).toString() : "";
    }

    static void despawn(DecoBlock b) {
        b.displays.forEach(Displays::remove);
        b.clicks.forEach(Displays::remove);
        b.displays.clear();
        b.keys.clear();
        b.yaws.clear();
        b.clicks.clear();
        b.clickSignature = "";
    }

    private static ItemDisplay display(DecoBlock b, World world, DecoModels.Part part) {
        Pos a = b.anchor();
        double lift = part.lift();
        Location at = new Location(world, a.x() + part.dx() + 0.5, a.y() + part.dy() - lift, a.z() + part.dz() + 0.5);
        org.bukkit.util.Transformation transform = transformation(part);
        float[] cull = cull(b.type(), part);
        return world.spawn(at, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setItemStack(Displays.model(part.key()));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setTransformation(transform);
            d.setViewRange(b.type().viewRange());
            d.setDisplayWidth(cull[0]);
            d.setDisplayHeight(cull[1]);
            d.setBillboard(Display.Billboard.FIXED);
            tag(d, b);
        });
    }

    /**
     * The part's transformation; the chandelier body's entity stands 0.75 below its cell (its culling box grows upward
     * only, and the body hangs 11 px below the anchor), so its model is lifted back by as much.
     */
    static org.bukkit.util.Transformation transformation(DecoModels.Part part) {
        org.bukkit.util.Transformation t = DecoModels.transformation(part.yRot(), part.modelScale());
        if (part.lift() > 0) {
            t = new org.bukkit.util.Transformation(t.getTranslation().add(0, part.lift(), 0), t.getLeftRotation(),
                    t.getScale(), t.getRightRotation());
        }
        return t;
    }

    /** Culling box (width, height) of a part: the model's extent from its entity (the box grows upwards only). */
    static float[] cull(DecoType type, DecoModels.Part part) {
        return switch (type) {
            case CASINO_SIGN -> new float[] {3.1f, 2.1f};
            case CASINO_SIGN_2X2 -> new float[] {4.2f, 2.1f};
            case CASINO_SIGN_4X4 -> new float[] {5.2f, 4.2f};
            case CASINO_CHANDELIER -> part.key().endsWith("chandelier_body") ? new float[] {3.0f, 1.8f}
                    : new float[] {1.0f, 1.1f};
            case CASINO_CHAIR, VELVET_ROPE_POST -> new float[] {1.0f, 1.5f};
            case CASINO_SOFA -> new float[] {1.0f, 1.2f};
            default -> new float[] {1.0f, 1.0f};
        };
    }

    private static Interaction interaction(DecoBlock b, World world, DecoGeometry.Box box) {
        Pos a = b.anchor();
        Location at = new Location(world, a.x() + box.cx(), a.y() + box.y0(), a.z() + box.cz());
        return world.spawn(at, Interaction.class, i -> {
            i.setPersistent(false);
            i.setInteractionWidth((float) box.width());
            i.setInteractionHeight((float) box.height());
            i.setResponsive(true);
            tag(i, b);
        });
    }

    private static void tag(Entity e, DecoBlock b) {
        e.getPersistentDataContainer().set(Keys.PART, PersistentDataType.STRING, b.id().toString());
    }

    /**
     * Displays sample the light at their own position; inside an opaque carrier (a double slab) that is 0, so they get
     * the brightest light of the open cells around instead (refreshed with the verifier).
     */
    static void brightness(DecoBlock b, World world) {
        if (!b.type().vanillaCarrier() || b.displays.isEmpty()) {
            return;
        }
        Block carrier = b.anchor().block(world);
        if (!carrier.getType().isOccluding()) {
            for (ItemDisplay d : b.displays) {
                if (d.getBrightness() != null) {
                    d.setBrightness(null);
                }
            }
            return;
        }
        int blockLight = 0;
        int skyLight = 0;
        for (BlockFace face : SIDES) {
            int nx = carrier.getX() + face.getModX();
            int nz = carrier.getZ() + face.getModZ();
            if (!world.isChunkLoaded(nx >> 4, nz >> 4)) {
                continue;
            }
            Block n = carrier.getRelative(face);
            if (!n.getType().isOccluding()) {
                blockLight = Math.max(blockLight, n.getLightFromBlocks());
                skyLight = Math.max(skyLight, n.getLightFromSky());
            }
        }
        Display.Brightness want = new Display.Brightness(blockLight, skyLight);
        for (ItemDisplay d : b.displays) {
            Display.Brightness now = d.getBrightness();
            if (now == null || now.getBlockLight() != blockLight || now.getSkyLight() != skyLight) {
                d.setBrightness(want);
            }
        }
    }
}
