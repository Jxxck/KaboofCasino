package de.tigges.kaboofcasino.packtool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * The blackjack dealer (mod {@code BlackjackDealerModel.createLayer}, drawn by {@code BlackjackTableRenderer} 139-157)
 * as one item model per moving part, for one ItemDisplay each: torso (body + jacket), head (head + nose), the two
 * upper arms, the two forearms and the two legs.
 *
 * <p>Pivot convention: a part's model centre (8, 8, 8) is its {@code ModelPart} pivot (the part's offset point), and
 * model units are the part's own px with y and z negated: {@code m = (8 + x, 8 - y, 8 - z)} for a cube vertex
 * (x, y, z) of the {@code ModelPart} frame (y down, the dealer looking towards -z). That is a 180 degree turn about X,
 * so in model space the dealer stands upright and faces south (+z), exactly as the mod's {@code DealerPlacement}
 * (rotation {@code 180 - facing}, scale (-1, -1, 1)) shows him at a south-facing table; the rig turns each part about
 * its pivot with {@code rotationZYX(-zRot, -yRot, xRot)} (the part's rotation seen through the same flip). Children
 * without a rotation of their own (body, jacket, nose) are baked into their parent's model at their offset.
 *
 * <p>UVs: the 26.3 {@code ModelPart.Cube} box layout (texOffs, mirror, {@code CubeDeformation} grows the box but not
 * the UVs) is evaluated per polygon and each polygon becomes the element face whose baked corners carry exactly the
 * same texels ({@link BlackjackFaces}). The texture is the renderer's five 64x64 layers composited in its draw order
 * (base, clothes, croupier outfit, badge, arms; {@code BlackjackTableRenderer} 140-144) with its cutout rule (a texel
 * with alpha below 0.1 is discarded, so the last layer with alpha >= 0.1 wins). The arms layer shares no opaque
 * texel with the three slightly inflated outfit layers ({@code LayerOffset}), so draw order and depth order agree.
 */
final class BlackjackDealerModel {

    static final String TEXTURE = "kaboofcasino:block/blackjack_dealer";
    static final String TEXTURE_PATH = "assets/kaboofcasino/textures/block/blackjack_dealer.png";
    static final int TEX_SIZE = 64;
    /** {@code BlackjackTableRenderer} 35-39, in draw order (140-144). */
    static final List<String> LAYERS = List.of("blackjack_dealer_base", "blackjack_dealer_clothes",
            "villager/profession/croupier", "blackjack_dealer_badge", "blackjack_dealer");
    /** {@code RenderTypes.entityCutout}: texels with alpha below 0.1 are discarded. */
    static final int CUTOUT_ALPHA = 26;

    /** One {@code addBox} in the display part's frame (px, ModelPart axes), with its texOffs, grow and mirror. */
    record Cube(int u, int v, float x, float y, float z, float w, float h, float d, float growX, float growY,
                float growZ, boolean mirror) {
    }

    /** One display part and the cubes it carries. */
    record Part(String name, List<Cube> cubes) {
    }

    private static final float FOREARM = -0.04F;

    /** {@code createLayer} (BlackjackDealerModel 36-72); the nose's offset (0, -2, 0) is baked into its cube. */
    static final List<Part> PARTS = List.of(
            new Part("torso", List.of(
                    new Cube(16, 20, -4, -12, -3, 8, 12, 6, 0, 0, 0, false),
                    new Cube(0, 38, -4, -12, -3, 8, 20, 6, 0.5F, 0.5F, 0.5F, false))),
            new Part("head", List.of(
                    new Cube(0, 0, -4, -10, -4, 8, 10, 8, 0, 0, 0, false),
                    new Cube(24, 0, -1, -1 - 2, -6, 2, 4, 2, 0, 0, 0, false))),
            new Part("right_arm", List.of(new Cube(28, 46, -2, -2, -2, 4, 6, 4, 0, 0, 0, false))),
            new Part("right_forearm", List.of(new Cube(44, 46, -2, 0, -2, 4, 6, 4, FOREARM, 0, 0, false))),
            new Part("left_arm", List.of(new Cube(28, 46, -2, -2, -2, 4, 6, 4, 0, 0, 0, true))),
            new Part("left_forearm", List.of(new Cube(44, 46, -2, 0, -2, 4, 6, 4, FOREARM, 0, 0, true))),
            new Part("right_leg", List.of(new Cube(0, 22, -2, 0, -2, 4, 12, 4, 0, 0, 0, false))),
            new Part("left_leg", List.of(new Cube(0, 22, -2, 0, -2, 4, 12, 4, 0, 0, 0, true))));

    private BlackjackDealerModel() {
    }

    /** A cube polygon: 4 vertices (px, ModelPart frame) with their UVs (texels), in {@code ModelPart} order. */
    record Polygon(float[][] pos, float[][] uv) {
    }

    /** {@code ModelPart.Cube} (26.3): the six polygons of a box with the box UV layout. */
    static List<Polygon> polygons(Cube c) {
        float minX = c.x();
        float minY = c.y();
        float minZ = c.z();
        float maxX = minX + c.w();
        float maxY = minY + c.h();
        float maxZ = minZ + c.d();
        minX -= c.growX();
        minY -= c.growY();
        minZ -= c.growZ();
        maxX += c.growX();
        maxY += c.growY();
        maxZ += c.growZ();
        if (c.mirror()) {
            float tmp = maxX;
            maxX = minX;
            minX = tmp;
        }
        float[] t0 = {minX, minY, minZ};
        float[] t1 = {maxX, minY, minZ};
        float[] t2 = {maxX, maxY, minZ};
        float[] t3 = {minX, maxY, minZ};
        float[] l0 = {minX, minY, maxZ};
        float[] l1 = {maxX, minY, maxZ};
        float[] l2 = {maxX, maxY, maxZ};
        float[] l3 = {minX, maxY, maxZ};
        float u0 = c.u();
        float u1 = c.u() + c.d();
        float u2 = c.u() + c.d() + c.w();
        float u22 = c.u() + c.d() + c.w() + c.w();
        float u3 = c.u() + c.d() + c.w() + c.d();
        float u4 = c.u() + c.d() + c.w() + c.d() + c.w();
        float v0 = c.v();
        float v1 = c.v() + c.d();
        float v2 = c.v() + c.d() + c.h();
        List<Polygon> out = new ArrayList<>(6);
        out.add(polygon(new float[][] {l1, l0, t0, t1}, u1, v0, u2, v1));   // DOWN
        out.add(polygon(new float[][] {t2, t3, l3, l2}, u2, v1, u22, v0));  // UP
        out.add(polygon(new float[][] {t0, l0, l3, t3}, u0, v1, u1, v2));   // WEST
        out.add(polygon(new float[][] {t1, t0, t3, t2}, u1, v1, u2, v2));   // NORTH
        out.add(polygon(new float[][] {l1, t1, t2, l2}, u2, v1, u3, v2));   // EAST
        out.add(polygon(new float[][] {l0, l1, l2, l3}, u3, v1, u4, v2));   // SOUTH
        return out;
    }

    /** {@code ModelPart.Polygon}: corner 0 gets (u1, v0), 1 (u0, v0), 2 (u0, v1), 3 (u1, v1). The mirror's vertex
     * reversal only changes the winding, never which position carries which UV. */
    private static Polygon polygon(float[][] pos, float pu0, float pv0, float pu1, float pv1) {
        float[][] uv = {{pu1, pv0}, {pu0, pv0}, {pu0, pv1}, {pu1, pv1}};
        return new Polygon(pos, uv);
    }

    /** ModelPart px to model units (pivot at the model centre, y and z negated). */
    static double[] toModel(float[] p) {
        return new double[] {8.0 + p[0], 8.0 - p[1], 8.0 - p[2]};
    }

    /** The elements of one part: one element per cube, six faces, all on {@code #sheet}. */
    static JsonArray elements(Part part) {
        JsonArray elements = new JsonArray();
        for (Cube cube : part.cubes()) {
            List<Polygon> polygons = polygons(cube);
            double[] from = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
            double[] to = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
            for (Polygon p : polygons) {
                for (float[] v : p.pos()) {
                    double[] m = toModel(v);
                    for (int k = 0; k < 3; k++) {
                        from[k] = Math.min(from[k], m[k]);
                        to[k] = Math.max(to[k], m[k]);
                    }
                }
            }
            JsonObject faces = new JsonObject();
            for (Polygon p : polygons) {
                double[][] corners = new double[4][];
                double[][] uvs = new double[4][];
                for (int i = 0; i < 4; i++) {
                    corners[i] = toModel(p.pos()[i]);
                    uvs[i] = new double[] {p.uv()[i][0] * 16.0 / TEX_SIZE, p.uv()[i][1] * 16.0 / TEX_SIZE};
                }
                BlackjackFaces.Face face = BlackjackFaces.solve(from, to, corners, uvs);
                if (faces.has(face.direction())) {
                    throw new IllegalStateException("two polygons on " + face.direction() + " of " + part.name());
                }
                faces.add(face.direction(), face.json("#sheet"));
            }
            for (int k = 0; k < 3; k++) {
                if (from[k] < -16 || to[k] > 32) {
                    throw new IllegalStateException(part.name() + " leaves the model range");
                }
            }
            JsonObject element = new JsonObject();
            element.add("from", Json.array(from));
            element.add("to", Json.array(to));
            element.add("faces", faces);
            elements.add(element);
        }
        return elements;
    }

    /** The five layers composited with the cutout rule, in the renderer's draw order. */
    static BufferedImage composite(ModJar jar) {
        BufferedImage out = Images.blank(TEX_SIZE, TEX_SIZE);
        for (String layer : LAYERS) {
            BufferedImage image = Images.read(jar.bytes("assets/roulettecasino/textures/entity/" + layer + ".png"));
            if (image.getWidth() != TEX_SIZE || image.getHeight() != TEX_SIZE) {
                throw new IllegalStateException("dealer layer " + layer + " is not 64x64");
            }
            for (int y = 0; y < TEX_SIZE; y++) {
                for (int x = 0; x < TEX_SIZE; x++) {
                    int argb = image.getRGB(x, y);
                    if (argb >>> 24 >= CUTOUT_ALPHA) {
                        out.setRGB(x, y, 0xFF000000 | argb & 0xFFFFFF);
                    }
                }
            }
        }
        return out;
    }

    /** Model key of a part ({@code kaboofcasino:blackjack/dealer_<part>}). */
    static String path(Part part) {
        return "blackjack/dealer_" + part.name();
    }

    static void write(ModJar jar, PackTree tree) {
        tree.put(TEXTURE_PATH, Images.write(composite(jar)));
        for (Part part : PARTS) {
            Models.write(tree, path(part), TEXTURE, elements(part), true);
        }
    }
}
