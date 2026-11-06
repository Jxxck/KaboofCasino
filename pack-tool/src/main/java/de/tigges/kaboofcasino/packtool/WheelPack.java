package de.tigges.kaboofcasino.packtool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.List;

/**
 * Fortune wheel models (render.md 1.2/3.2; mod {@code FortuneWheelRenderer}): the nine static body models
 * ({@code roulettecasino:block/fortune_wheel_0..8}, one display per part) with item definitions, the wheel and
 * roulette-mechanism items, the entity sheet copied into the blocks atlas, the plate in three blur levels (one
 * 40x40 px quad at z 11 about the axle, the mod's {@code PLATE_UV}: 320x320 tiles of the 1024x512 sheet), the
 * pennant (converted mesh, hinge at the model centre) and the 16 rim bulbs in one model (1x1 px quads at z 13.06,
 * tint i, self-lit). The plate model has the axle at its centre (the display rotates about it, pivot = wheel-local
 * (8, 11, 11)); the bulbs' model coordinates are wheel-local px (pivot (8, 8, 8)).
 */
final class WheelPack {

    static final String SHEET = "roulettecasino:block/casino_entity/fortune_wheel";
    static final int SHEET_W = 1024;
    static final int SHEET_H = 512;
    static final double PLATE_HALF = 20;
    static final double[] AXLE = {8, 11};
    static final double PLATE_Z = 11;
    static final double BULB_Z = 13.06;
    /** {@code FortuneWheelRenderGeometry.BULBS} (x, y), clockwise from the top. */
    static final double[] BULBS = {11.5, 29, 18.5, 26.5, 23.5, 21.5, 26, 14.5, 26, 7.5, 23.5, 0.5, 18.5, -4.5,
        11.5, -7, 4.5, -7, -2.5, -4.5, -7.5, 0.5, -10, 7.5, -10, 14.5, -7.5, 21.5, -2.5, 26.5, 4.5, 29};
    static final List<String> SOUND_EVENTS = List.of("wheel_spin", "wheel_whirr", "wheel_click", "wheel_stop",
            "wheel_win", "wheel_jackpot", "wheel_lose", "wheel_push", "wheel_partial", "wheel_payout");
    static final List<String> LANG_PREFIXES = List.of("block.roulettecasino.fortune_wheel",
            "item.roulettecasino.roulette_mechanism", "gui.roulettecasino.wheel.",
            "message.roulettecasino.fortune_wheel.", "subtitles.roulettecasino.wheel_");

    private WheelPack() {
    }

    static void add(ModJar jar, PackTree tree, Path packSrc) {
        ModelCopier copier = new ModelCopier(jar, tree);
        for (int part = 0; part < 9; part++) {
            String model = "roulettecasino:block/fortune_wheel_" + part;
            copier.model(model);
            tree.put("assets/roulettecasino/items/block/fortune_wheel_" + part + ".json",
                    Json.bytes(Json.itemDefinition(model)));
        }
        copier.itemDefinition("roulettecasino:fortune_wheel");
        copier.itemDefinition("roulettecasino:roulette_mechanism");
        copier.copyAs("assets/roulettecasino/textures/entity/fortune_wheel.png",
                "assets/roulettecasino/textures/block/casino_entity/fortune_wheel.png");
        for (int level = 0; level < 3; level++) {
            JsonArray plate = new JsonArray();
            plate.add(plate(level));
            Models.write(tree, "wheel/plate_" + level, SHEET, plate, true);
        }
        JsonObject pennant = Json.parse(ModJar.readAll(packSrc.resolve("meshes/wheel_pennant.json")));
        Models.write(tree, "wheel/pennant", SHEET, pennant.getAsJsonArray("elements"), true);
        Models.write(tree, "wheel/bulbs", SHEET, bulbs(), false);
        Models.tintedDefinition(tree, "wheel/bulbs", 16);
    }

    /** The plate quad of blur level {@code level}, centred on the model centre (= the axle at z 11). */
    static JsonObject plate(int level) {
        double[] uv = {
            (0.5 + 320 * level) / SHEET_W * 16, 0.5 / SHEET_H * 16,
            (319.5 + 320 * level) / SHEET_W * 16, 319.5 / SHEET_H * 16};
        return Models.quad(8 - PLATE_HALF, 8 - PLATE_HALF, 8 + PLATE_HALF, 8 + PLATE_HALF, 8, uv, -1, 0);
    }

    /** The 16 bulbs, tint = bulb index, self-lit; UV = the 8x8 bulb tile at sheet (0, 320). */
    static JsonArray bulbs() {
        double[] uv = {0, 320.0 / SHEET_H * 16, 8.0 / SHEET_W * 16, 328.0 / SHEET_H * 16};
        JsonArray out = new JsonArray();
        for (int i = 0; i < 16; i++) {
            double x = BULBS[2 * i];
            double y = BULBS[2 * i + 1];
            out.add(Models.quad(x - 0.5, y - 0.5, x + 0.5, y + 0.5, BULB_Z, uv, i, 15));
        }
        return out;
    }
}
