package de.tigges.kaboofcasino.packtool;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.nio.file.Path;
import java.util.List;

/**
 * Everything the slot machine needs in the pack (games.md 6, render.md 2-3):
 * <ul>
 *   <li>the three static body models (lower/upper/top) with generated item definitions, shown by ItemDisplays;</li>
 *   <li>the machine, reel and mechanism items;</li>
 *   <li>the BER parts as item models: the lever (converted arm + ball meshes), the lights (converted lamp mesh plus
 *       15 generated bulbs, every face with its own {@code tintindex} and {@code light_emission} 15, tinted from
 *       {@code custom_model_data} colours), and 9 reel drums (3 reels x 3 blur strips);</li>
 *   <li>the entity sheet in the blocks atlas ({@code block/casino_entity/slot_machine}, 512x1056: both multiples
 *       of 16, no padding needed);</li>
 *   <li>the slot sounds and the slot lang keys of all 12 languages.</li>
 * </ul>
 * The GUI art lives in {@link SlotMachineGui}.
 */
final class SlotMachinePack {

    static final String SHEET = "roulettecasino:block/casino_entity/slot_machine";
    static final int SHEET_W = 512;
    static final int SHEET_H = 1056;
    /** {@code SlotMachineRenderGeometry}: the drums. */
    static final double RADIUS = 7.002817496043395;
    static final double ROW_ZERO = 682.0;
    static final int[] STRIP_X = {0, 40, 80, 120, 160, 200, 240, 280, 320};
    /** Facets of 7.5 degrees on each side of the front: the arc covers +-82.5 degrees. */
    static final int DRUM_HALF_FACETS = 11;
    static final double FACET_DEGREES = 7.5;
    static final double AXIS_Z = 4.497182503956605;
    /** {@code SlotMachineRenderGeometry.BULBS}: 15 x (x, y, z) px in lower-block coordinates. */
    static final double[] BULBS = {
        -2.0, 36.5, 13.06, 0.5, 37.0, 13.06, 3.0, 37.0, 13.06, 5.5, 37.0, 13.06, 8.0, 37.5, 13.06,
        10.5, 37.0, 13.06, 13.0, 37.0, 13.06, 15.5, 37.0, 13.06, 18.0, 36.5, 13.06, 19.5, 35.5, 13.06,
        19.5, 34.0, 13.06, 19.5, 32.5, 13.06, -3.5, 32.5, 13.06, -3.5, 34.0, 13.06, -3.5, 35.5, 13.06,
    };
    static final double[] BULB_UV = {0.73438, 0.04545, 0.75, 0.05303};
    static final List<String> SOUND_EVENTS = List.of("slot_lever", "slot_whirr", "slot_click", "slot_stop",
            "slot_win", "slot_jackpot", "slot_lose", "slot_push", "slot_payout");
    static final List<String> LANG_PREFIXES = List.of("block.roulettecasino.slot_machine",
            "item.roulettecasino.slot_machine", "gui.roulettecasino.slot.", "message.roulettecasino.slot_machine.",
            "subtitles.roulettecasino.slot_", "gui.roulettecasino.bets.");

    private SlotMachinePack() {
    }

    static void add(ModJar jar, PackTree tree, Path packSrc) {
        ModelCopier copier = new ModelCopier(jar, tree);
        for (String part : List.of("lower", "upper", "top")) {
            String model = "roulettecasino:block/slot_machine_" + part;
            copier.model(model);
            tree.put("assets/roulettecasino/items/block/slot_machine_" + part + ".json",
                    Json.bytes(Json.itemDefinition(model)));
        }
        for (String item : List.of("slot_machine", "slot_machine_reel", "slot_machine_mechanism")) {
            copier.itemDefinition("roulettecasino:" + item);
        }
        copier.copyAs("assets/roulettecasino/textures/entity/slot_machine.png",
                "assets/roulettecasino/textures/block/casino_entity/slot_machine.png");

        JsonObject arm = Json.parse(ModJar.readAll(packSrc.resolve("meshes/slot_arm.json")));
        JsonObject ball = Json.parse(ModJar.readAll(packSrc.resolve("meshes/slot_ball.json")));
        JsonArray leverElements = new JsonArray();
        leverElements.addAll(arm.getAsJsonArray("elements"));
        leverElements.addAll(ball.getAsJsonArray("elements"));
        model(tree, "slot/lever", leverElements);

        model(tree, "slot/lights", lights(Json.parse(ModJar.readAll(packSrc.resolve("meshes/slot_lamp.json")))));
        JsonArray tints = new JsonArray();
        for (int i = 0; i < 16; i++) {
            JsonObject tint = new JsonObject();
            tint.addProperty("type", "minecraft:custom_model_data");
            tint.addProperty("index", i);
            tint.addProperty("default", 0xFFFFFF);
            tints.add(tint);
        }
        JsonObject lightsDef = Json.itemDefinition("kaboofcasino:slot/lights");
        lightsDef.getAsJsonObject("model").add("tints", tints);
        tree.put("assets/kaboofcasino/items/slot/lights.json", Json.bytes(lightsDef));

        for (int reel = 0; reel < 3; reel++) {
            for (int level = 0; level < 3; level++) {
                for (int face = 0; face < 8; face++) {
                    model(tree, "slot/drum_" + reel + "_" + level + "_" + face, drum(reel, level, face));
                }
            }
        }
    }

    /** Writes {@code models/<path>.json} (one texture, the sheet) and its plain item definition. */
    private static void model(PackTree tree, String path, JsonArray elements) {
        JsonObject textures = new JsonObject();
        textures.addProperty("sheet", SHEET);
        textures.addProperty("particle", SHEET);
        JsonObject model = new JsonObject();
        model.add("textures", textures);
        model.add("elements", elements);
        tree.put("assets/kaboofcasino/models/" + path + ".json", Json.bytes(model));
        if (!path.equals("slot/lights")) {
            tree.put("assets/kaboofcasino/items/" + path + ".json",
                    Json.bytes(Json.itemDefinition("kaboofcasino:" + path)));
        }
    }

    /**
     * The lamp mesh (tint 0) plus the 15 bulbs (tint 1..15) in top-block coordinates, all faces self-lit
     * ({@code light_emission} 15) like the mod's full-bright BER pass.
     */
    static JsonArray lights(JsonObject lamp) {
        JsonArray out = new JsonArray();
        for (JsonElement e : lamp.getAsJsonArray("elements")) {
            JsonObject element = e.getAsJsonObject().deepCopy();
            for (var face : element.getAsJsonObject("faces").entrySet()) {
                face.getValue().getAsJsonObject().addProperty("tintindex", 0);
            }
            element.addProperty("light_emission", 15);
            out.add(element);
        }
        for (int i = 0; i < 15; i++) {
            double cx = BULBS[3 * i];
            double cy = BULBS[3 * i + 1] - 32.0;
            double cz = BULBS[3 * i + 2];
            JsonObject face = new JsonObject();
            face.add("uv", Json.array(BULB_UV[0] * 16, BULB_UV[1] * 16, BULB_UV[2] * 16, BULB_UV[3] * 16));
            face.addProperty("texture", "#sheet");
            face.addProperty("tintindex", i + 1);
            JsonObject element = new JsonObject();
            element.add("from", Json.array(cx - 0.5, cy - 0.5, cz));
            element.add("to", Json.array(cx + 0.5, cy + 0.5, cz));
            element.add("faces", Json.object("south", face));
            element.addProperty("light_emission", 15);
            out.add(element);
        }
        return out;
    }

    /**
     * A partial reel drum about the model centre (axis along X through y = z = 8) with face {@code face} at the front:
     * 22 facets of 7.5 degrees covering +-82.5 degrees. The mod draws 16 facets over +-60 degrees and scrolls the
     * texture ({@code v = (682 - 44 turn + 8 r a) / 1056}: exactly 45 degrees per symbol). Here the display shows the
     * model of the nearest face and turns it by {@code (turn - round(turn)) x 45} degrees (at most 22.5), so the window
     * (+-60) stays covered while the drum never reaches behind the cabinet's back plate (z >= 2 px; the lowest facet
     * corner is at 4.497 + 7.003 cos(105) = 2.69 px). Angle a grows from +Z toward -Y ({@code y = -r sin a,
     * z = r cos a}); each facet is a south face at the chord distance, rotated about X by its mid angle (JOML
     * right-handed: the +Z normal goes to (-sin a, cos a)).
     */
    static JsonArray drum(int reel, int level, int face) {
        double step = Math.toRadians(FACET_DEGREES);
        double half = RADIUS * Math.sin(step / 2);
        double chord = RADIUS * Math.cos(step / 2);
        int sheetX = STRIP_X[3 * reel + level];
        double u0 = (sheetX + 0.5) / SHEET_W * 16;
        double u1 = (sheetX + 40 - 0.5) / SHEET_W * 16;
        double row = ROW_ZERO - 44.0 * face;
        JsonArray out = new JsonArray();
        for (int j = -DRUM_HALF_FACETS; j < DRUM_HALF_FACETS; j++) {
            double a0 = j * step;
            double a1 = a0 + step;
            double mid = a0 + step / 2;
            double v0 = (row + 8 * RADIUS * a0) / SHEET_H * 16;
            double v1 = (row + 8 * RADIUS * a1) / SHEET_H * 16;
            JsonObject faceJson = new JsonObject();
            faceJson.add("uv", Json.array(u0, v0, u1, v1));
            faceJson.addProperty("texture", "#sheet");
            JsonObject rotation = new JsonObject();
            rotation.add("origin", Json.array(8, 8, 8));
            rotation.addProperty("axis", "x");
            rotation.add("angle", new JsonPrimitive(Json.round(Math.toDegrees(mid))));
            JsonObject element = new JsonObject();
            element.add("from", Json.array(5.5, 8 - half, 8 + chord));
            element.add("to", Json.array(10.5, 8 + half, 8 + chord));
            element.add("rotation", rotation);
            element.add("faces", Json.object("south", faceJson));
            out.add(element);
        }
        return out;
    }

}
