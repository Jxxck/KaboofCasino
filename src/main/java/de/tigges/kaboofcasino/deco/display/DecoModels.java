package de.tigges.kaboofcasino.deco.display;

import de.tigges.kaboofcasino.machine.Facing;
import de.tigges.kaboofcasino.util.Pos;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Which models a decoration shows, exactly as the mod's blockstate files pick them: one {@link Part} per display, the
 * item model {@code kaboofcasino:deco/...} (the pack's item definition of the mod's block model), the blockstate's
 * {@code y} rotation, and the cell it stands in (offset from the anchor). Stairs use models the pack bakes per state
 * (the mod's stairs are uv-locked); the 3 x 2 sign and the chandelier body are merged models. Pure.
 */
public final class DecoModels {

    public static final String PREFIX = "kaboofcasino:deco/";
    /** Scale of displays over a vanilla carrier (render.md 4.4: just outside the carrier's faces, no z-fighting). */
    public static final float VANILLA_SCALE = 1.002f;

    private static final Quaternionf R180 = new Quaternionf().rotationY((float) Math.PI);

    /** Scale of the merged chandelier body, which the pack stores at half size (model range [-16, 32]). */
    public static final float BODY_SCALE = 2.0f;

    /** One display: model key, blockstate y rotation (degrees, clockwise from above), cell offset from the anchor. */
    public record Part(String key, int yRot, int dx, int dy, int dz) {
        /** The model's own scale (the half-size chandelier body), before the carrier's. */
        public float modelScale() {
            return key.endsWith("chandelier_body") ? BODY_SCALE : 1.0f;
        }
    }

    /** The vanilla carrier's state (stairs: facing/half/shape, slabs: type), as the mod's blockstate names it. */
    public record Carrier(String facing, String half, String shape, String slabType) {
        public static Carrier stairs(String facing, String half, String shape) {
            return new Carrier(facing, half, shape, null);
        }

        public static Carrier slab(String type) {
            return new Carrier(null, null, null, type);
        }
    }

    private DecoModels() {
    }

    /** The blockstate {@code y} of a horizontal facing: south 0, west 90, north 180, east 270. */
    public static int yRot(Facing f) {
        return f.turns() * 90;
    }

    /**
     * The displays of {@code r}. {@code carrier} is the vanilla carrier's state (stairs, slabs; null otherwise).
     */
    public static List<Part> parts(DecoRecord r, Carrier carrier) {
        DecoType t = r.type();
        Pos a = r.anchor();
        int y = yRot(r.facing());
        List<Part> out = new ArrayList<>();
        switch (t) {
            case CASINO_SOFA -> out.add(new Part(PREFIX + "sofa_" + Integer.toHexString(
                    SofaLayout.geometry(r.sofaJoins(), r.sofaBack(), cardinal(r.facing()))), 0, 0, 0, 0));
            case CASINO_CHAIR, CASINO_STOOL -> out.add(new Part(PREFIX + t.path(), y, 0, 0, 0));
            case CASINO_SIGN -> {
                int[] c = DecoGeometry.ccw(r.facing());
                out.add(new Part(PREFIX + "casino_sign", y, c[0], 0, c[1]));
            }
            case SMALL_CASINO_SIGN -> out.add(new Part(PREFIX + "small_sign", y, 0, 0, 0));
            case WOOD_COFFEE_TABLE -> out.add(new Part(PREFIX + "table_wood_low_" + hex(r.mask()), 0, 0, 0, 0));
            case GLASS_COFFEE_TABLE -> out.add(new Part(PREFIX + "table_glass_low_" + hex(r.mask()), 0, 0, 0, 0));
            case WOOD_TABLE -> out.add(new Part(PREFIX + "table_wood_tall_" + hex(r.mask()), 0, 0, 0, 0));
            case GLASS_TABLE -> out.add(new Part(PREFIX + "table_glass_tall_" + hex(r.mask()), 0, 0, 0, 0));
            case VELVET_ROPE_POST -> out.add(new Part(PREFIX + "rope_post_" + hex(r.mask()), 0, 0, 0, 0));
            case CASINO_CHANDELIER -> {
                out.add(new Part(PREFIX + "chandelier_body", 0, 0, 0, 0));
                int length = r.chain();
                for (int k = 1; k <= length; k++) {
                    out.add(new Part(PREFIX + (k == length ? "chandelier_10" : "chandelier_9"), 0, 0, k, 0));
                }
            }
            case WALNUT_BAR, IVORY_MARBLE_BAR, BLACK_MARBLE_BAR -> {
                BarPiece piece = bar(r.mask());
                out.add(new Part(PREFIX + t.path() + "_" + piece.kind(), piece.yRot(), 0, 0, 0));
                if (t != DecoType.WALNUT_BAR) {
                    int v = DecoVariants.multipart(a.x(), a.y(), a.z(), 4);
                    out.add(new Part(PREFIX + t.path() + "_top" + DecoVariants.suffix(v), 0, 0, 0, 0));
                }
            }
            case CASINO_BOTTLES, CHAMPAGNE_COUPES, SHOT_GLASSES ->
                    out.add(new Part(PREFIX + t.path() + "_" + r.count(), y, 0, 0, 0));
            case CHIP_CASE -> out.add(new Part(PREFIX + (r.open() ? "chip_case_open" : "chip_case"), y, 0, 0, 0));
            case CASINO_SPEAKER -> out.add(new Part(PREFIX + "speaker_" + switch (r.face()) {
                case DecoRecord.FACE_WALL -> "wall";
                case DecoRecord.FACE_CEILING -> "ceiling";
                default -> "floor";
            } + (r.on() ? "_on" : "_off"), y, 0, 0, 0));
            case CHANDELIER_WALL_SCONCE, CASINO_CHIP_PILE, RECEPTION_PLAQUE, CASH_REGISTER, SERVING_TRAY,
                 VIP_ONLY_PLAQUE, BOTTLE_DISPLAY_SHELF, GLASS_DISPLAY_SHELF ->
                    out.add(new Part(PREFIX + t.path(), y, 0, 0, 0));
            default -> {
                if (t.carrier() == DecoType.Carrier.STAIRS && carrier != null && carrier.facing() != null) {
                    out.add(new Part(stairsKey(t.family(), carrier, variant(t, a)), 0, 0, 0, 0));
                } else if (t.carrier() == DecoType.Carrier.SLAB && carrier != null && carrier.slabType() != null) {
                    String sfx = DecoVariants.suffix(variant(t, a));
                    String model = switch (carrier.slabType()) {
                        case "top" -> t.family() + "_slab_top" + sfx;
                        case "double" -> t.family() + sfx;
                        default -> t.family() + "_slab" + sfx;
                    };
                    out.add(new Part(PREFIX + model, 0, 0, 0, 0));
                }
            }
        }
        return out;
    }

    /** The random variant of stairs and slabs ({@code variants} lists of four marble models; 0 for the rest). */
    static int variant(DecoType t, Pos a) {
        return t.variants() ? DecoVariants.simple(a.x(), a.y(), a.z(), 4) : 0;
    }

    /** {@code kaboofcasino:deco/stairs/<family>_<facing>_<half>_<shape>_<variant>} (baked per state by the pack). */
    public static String stairsKey(String family, Carrier c, int variant) {
        return PREFIX + "stairs/" + family + "_" + c.facing() + "_" + c.half() + "_" + c.shape() + "_" + variant;
    }

    static String hex(int mask) {
        return Integer.toHexString(mask & 15);
    }

    /** The mod's bar multipart: connection mask (bit 0 N, 1 E, 2 S, 3 W) -> model and y. */
    public record BarPiece(String kind, int yRot) {
    }

    public static BarPiece bar(int mask) {
        return switch (mask & 15) {
            case 0 -> new BarPiece("single", 0);
            case 1 -> new BarPiece("end", 0);
            case 2 -> new BarPiece("end", 90);
            case 3 -> new BarPiece("corner", 0);
            case 4 -> new BarPiece("end", 180);
            case 5 -> new BarPiece("straight", 0);
            case 6 -> new BarPiece("corner", 90);
            case 7 -> new BarPiece("tee", 0);
            case 8 -> new BarPiece("end", 270);
            case 9 -> new BarPiece("corner", 270);
            case 10 -> new BarPiece("straight", 90);
            case 11 -> new BarPiece("tee", 270);
            case 12 -> new BarPiece("corner", 180);
            case 13 -> new BarPiece("tee", 180);
            case 14 -> new BarPiece("tee", 90);
            default -> new BarPiece("cross", 0);
        };
    }

    /** The mod's cardinal index (0 north, 1 east, 2 south, 3 west) of a facing. */
    public static int cardinal(Facing f) {
        return switch (f) {
            case NORTH -> 0;
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
        };
    }

    /**
     * The display transformation for a part whose entity stands at its cell's bottom centre: the model's centre
     * (8, 8, 8) at the cell centre, turned by the blockstate's {@code y} (the client draws an item display as
     * {@code L * R180 * (m/16 - 0.5)}, so {@code L = Ry(-y) * R180}; see RigMath).
     */
    public static Transformation transformation(int yRot, float scale) {
        Quaternionf left = new Quaternionf().rotationY((float) Math.toRadians(-yRot)).mul(R180);
        return new Transformation(new Vector3f(0, 0.5f, 0), left, new Vector3f(scale, scale, scale),
                new Quaternionf());
    }

    /** Where model point (px) lands relative to the entity (tests). */
    public static Vector3f apply(Transformation t, double mx, double my, double mz) {
        Vector3f v = new Vector3f((float) (mx / 16 - 0.5), (float) (my / 16 - 0.5), (float) (mz / 16 - 0.5));
        R180.transform(v);
        t.getRightRotation().transform(v);
        v.mul(t.getScale());
        t.getLeftRotation().transform(v);
        return v.add(t.getTranslation());
    }

    /** Every model key the plugin can ask for (pack test). */
    public static List<String> allKeys() {
        List<String> out = new ArrayList<>();
        for (int g : sofaGeometries()) {
            out.add(PREFIX + "sofa_" + Integer.toHexString(g));
        }
        for (String m : List.of("table_wood_low_", "table_glass_low_", "table_wood_tall_", "table_glass_tall_",
                "rope_post_")) {
            for (int mask = 0; mask < 16; mask++) {
                out.add(PREFIX + m + hex(mask));
            }
        }
        for (String bar : List.of("walnut_bar", "ivory_marble_bar", "black_marble_bar")) {
            for (String kind : List.of("single", "end", "corner", "straight", "tee", "cross")) {
                out.add(PREFIX + bar + "_" + kind);
            }
            if (!bar.equals("walnut_bar")) {
                for (int v = 0; v < 4; v++) {
                    out.add(PREFIX + bar + "_top" + DecoVariants.suffix(v));
                }
            }
        }
        for (String s : List.of("casino_chair", "casino_stool", "casino_sign", "small_sign", "chandelier_body",
                "chandelier_9", "chandelier_10", "chandelier_wall_sconce", "casino_chip_pile", "chip_case",
                "chip_case_open", "reception_plaque", "cash_register", "serving_tray", "vip_only_plaque",
                "bottle_display_shelf", "glass_display_shelf")) {
            out.add(PREFIX + s);
        }
        for (String s : List.of("casino_bottles", "champagne_coupes", "shot_glasses")) {
            for (int n = 1; n <= 4; n++) {
                out.add(PREFIX + s + "_" + n);
            }
        }
        for (String face : List.of("floor", "wall", "ceiling")) {
            out.add(PREFIX + "speaker_" + face + "_on");
            out.add(PREFIX + "speaker_" + face + "_off");
        }
        for (DecoType t : DecoType.values()) {
            int variants = t.variants() ? 4 : 1;
            if (t.carrier() == DecoType.Carrier.STAIRS) {
                for (String facing : List.of("north", "east", "south", "west")) {
                    for (String half : List.of("bottom", "top")) {
                        for (String shape : List.of("straight", "inner_left", "inner_right", "outer_left",
                                "outer_right")) {
                            for (int v = 0; v < variants; v++) {
                                out.add(stairsKey(t.family(), Carrier.stairs(facing, half, shape), v));
                            }
                        }
                    }
                }
            } else if (t.carrier() == DecoType.Carrier.SLAB) {
                for (int v = 0; v < variants; v++) {
                    String sfx = DecoVariants.suffix(v);
                    out.add(PREFIX + t.family() + "_slab" + sfx);
                    out.add(PREFIX + t.family() + "_slab_top" + sfx);
                    out.add(PREFIX + t.family() + sfx);
                }
            }
        }
        return out;
    }

    /** Every sofa geometry a state of the mod can have (joins x back x facing; the blockstate's 576 states). */
    static List<Integer> sofaGeometries() {
        java.util.TreeSet<Integer> set = new java.util.TreeSet<>();
        for (int joins = 0; joins < 16; joins++) {
            for (int back : new int[] {0, 1, 2, 4, 8, 3, 6, 12, 9}) {
                for (int facing = 0; facing < 4; facing++) {
                    set.add(SofaLayout.geometry(joins, back, facing));
                }
            }
        }
        return List.copyOf(set);
    }
}
