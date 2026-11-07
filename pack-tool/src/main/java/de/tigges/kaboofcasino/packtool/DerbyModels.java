package de.tigges.kaboofcasino.packtool;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.Map;

/**
 * The duck derby's moving and lit parts as item models ({@code DuckDerbyRenderer.submit} / {@code signLights}; the
 * tub itself is the mod's twelve block models). Machine px: the mod's 48 x 64 px model space (x 0..48 left to right,
 * z 0 = the sign side, 64 = the front, anchor cell centre (24, 56)). Every model has one pivot: the machine point that
 * is its model centre (8, 8, 8), where the plugin's display puts it.
 * <ul>
 *   <li>{@code kaboofcasino:derby/duck_<lane>}: the converted 48-quad {@code DuckDerbyGeometry.DUCK} mesh
 *       ({@code pack-src/meshes/derby_duck.json}, {@code tools/meshes/convert-derby.sh}) with the lane's UV offset
 *       {@code LANE_UV} baked in; pivot = the renderer's translation (lane centre, 13 + lift, z) px, the mesh drawn
 *       0.5 px lower ({@code quads(..., 0, -0.5, 0, du, dv)}); the display turns it about X by the pitch.</li>
 *   <li>{@code kaboofcasino:derby/gates}: the four-quad {@code GATE} bar once per lane at {@code GATE_X} (the renderer
 *       draws the same quads four times with one sink); pivot (24, 15.5, 51.5).</li>
 *   <li>{@code kaboofcasino:derby/sign}: {@code signLights}: the sign overlay ({@code dd_sign.png} u 0..1, v 0..0.875 on
 *       x 4..44, y 17..31 at z 3.025, full bright) and the 12 bulbs (the slot sheet's {@code BULB_UV}, 0.75 px, at
 *       {@code DuckDerbySignLights} x/y and z 3.08, tint = bulb index, full bright); pivot (24, 24, 3). The item
 *       definition takes the 12 tints from {@code custom_model_data} colours ({@code gray(0.28 + 0.72 b)}).</li>
 * </ul>
 */
final class DerbyModels {

    static final String SHEET = "roulettecasino:block/casino_entity/duck_derby";
    static final String SIGN_TEXTURE = "roulettecasino:block/dd_sign";
    static final int SHEET_W = 192;
    static final int SHEET_H = 176;
    static final int LANES = 4;
    /** {@code DuckDerbyGeometry.LANE_UV}: (du, dv) per lane, fractions of the sheet. */
    static final double[] LANE_UV = {0.0, 0.0, 0.5, 0.0, 0.0, 0.454545, 0.5, 0.454545};
    /** {@code DuckDerbyGeometry.GATE_X}. */
    static final double[] GATE_X = {1.0, 13.0, 25.0, 37.0};
    /** Pivots (machine px) of the gate and sign models. */
    static final double[] GATE_PIVOT = {24.0, 15.5, 51.5};
    static final double[] SIGN_PIVOT = {24.0, 24.0, 3.0};
    /** {@code signLights}: the overlay at z 3.025 over x 4..44, y 17..31, v 0..0.875; bulbs at z 3.08, half 0.375. */
    static final double SIGN_Z = 3.025;
    static final double[] SIGN_RECT = {4.0, 17.0, 44.0, 31.0};
    static final double SIGN_V = 0.875;
    static final double BULB_Z = 3.08;
    static final double BULB_HALF = 0.375;
    static final int BULBS = 12;

    private DerbyModels() {
    }

    static String duck(int lane) {
        return "derby/duck_" + lane;
    }

    static void add(PackTree tree, Path packSrc) {
        JsonArray duck = mesh(packSrc, "derby_duck");
        for (int lane = 0; lane < LANES; lane++) {
            Models.write(tree, duck(lane), SHEET, laneUv(duck, lane), true);
        }
        Models.write(tree, "derby/gates", SHEET, gates(mesh(packSrc, "derby_gate")), true);
        sign(tree);
    }

    private static JsonArray mesh(Path packSrc, String name) {
        return Json.parse(ModJar.readAll(packSrc.resolve("meshes/" + name + ".json"))).getAsJsonArray("elements");
    }

    /** The duck mesh with every face's UV moved by the lane's {@code LANE_UV} (model units: 16 = the whole sheet). */
    static JsonArray laneUv(JsonArray duck, int lane) {
        double du = LANE_UV[2 * lane] * 16.0;
        double dv = LANE_UV[2 * lane + 1] * 16.0;
        JsonArray out = new JsonArray();
        for (JsonElement e : duck) {
            JsonObject el = e.getAsJsonObject().deepCopy();
            for (Map.Entry<String, JsonElement> face : el.getAsJsonObject("faces").entrySet()) {
                JsonObject f = face.getValue().getAsJsonObject();
                JsonArray uv = f.getAsJsonArray("uv");
                f.add("uv", Json.array(round4(uv.get(0).getAsDouble() + du), round4(uv.get(1).getAsDouble() + dv),
                        round4(uv.get(2).getAsDouble() + du), round4(uv.get(3).getAsDouble() + dv)));
            }
            out.add(el);
        }
        return out;
    }

    private static double round4(double v) {
        return Math.round(v * 10_000.0) / 10_000.0;
    }

    /** The converted gate (machine x 0..10 at model x -16..-6) once per lane, moved by {@code GATE_X}. */
    static JsonArray gates(JsonArray gate) {
        JsonArray out = new JsonArray();
        for (double x : GATE_X) {
            out.addAll(Models.placed(gate, x, 0, 0, -1, 0));
        }
        return out;
    }

    /** {@code DuckDerbySignLights.x} (verbatim). */
    static double bulbX(int bulb) {
        return bulb < 2 ? 4.5 : (bulb < 10 ? 5.5 + (double) (bulb - 2) * 5.285714285714286 : 43.5);
    }

    /** {@code DuckDerbySignLights.y} (verbatim). */
    static double bulbY(int bulb) {
        return bulb < 2 ? 21.0 + 5.0 * (double) bulb : (bulb < 10 ? 30.5 : (bulb == 10 ? 26.0 : 21.0));
    }

    /** One flat south face (machine px rectangle at z) in sign-model coordinates, full bright, unshaded. */
    private static JsonObject signFace(double x0, double y0, double x1, double y1, double z, String texture,
                                       double[] uv, int tint) {
        double ox = 8 - SIGN_PIVOT[0];
        double oy = 8 - SIGN_PIVOT[1];
        double oz = 8 - SIGN_PIVOT[2];
        JsonObject face = new JsonObject();
        face.add("uv", Json.array(uv));
        face.addProperty("texture", texture);
        if (tint >= 0) {
            face.addProperty("tintindex", tint);
        }
        JsonObject element = new JsonObject();
        element.add("from", Json.array(x0 + ox, y0 + oy, z + oz));
        element.add("to", Json.array(x1 + ox, y1 + oy, z + oz));
        element.addProperty("shade", false);
        element.addProperty("light_emission", 15);
        element.add("faces", Json.object("south", face));
        return element;
    }

    /** The sign overlay and the 12 bulbs ({@code signQuad}: (x0,y1) uv (u0,v0) ... a south face's corner order). */
    static JsonArray signElements() {
        JsonArray out = new JsonArray();
        out.add(signFace(SIGN_RECT[0], SIGN_RECT[1], SIGN_RECT[2], SIGN_RECT[3], SIGN_Z, "#sign",
                new double[] {0, 0, 16, 16 * SIGN_V}, -1));
        double[] bulbUv = new double[4];
        for (int k = 0; k < 4; k++) {
            bulbUv[k] = SlotMachinePack.BULB_UV[k] * 16;
        }
        for (int i = 0; i < BULBS; i++) {
            double x = bulbX(i);
            double y = bulbY(i);
            out.add(signFace(x - BULB_HALF, y - BULB_HALF, x + BULB_HALF, y + BULB_HALF, BULB_Z, "#bulb", bulbUv, i));
        }
        return out;
    }

    static void sign(PackTree tree) {
        JsonObject textures = new JsonObject();
        textures.addProperty("sign", SIGN_TEXTURE);
        textures.addProperty("bulb", SlotMachinePack.SHEET);
        textures.addProperty("particle", SIGN_TEXTURE);
        JsonObject model = new JsonObject();
        model.add("textures", textures);
        model.add("elements", signElements());
        tree.put("assets/kaboofcasino/models/derby/sign.json", Json.bytes(model));
        Models.tintedDefinition(tree, "derby/sign", BULBS);
    }
}
