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
 * tint i, self-lit). The plate is one item ({@code kaboofcasino:wheel/plate}): a {@code range_dispatch} on the
 * {@code custom_model_data} float {@code lower + weight} of {@code FortuneWheelBlur.at(speed)} picks the sharp or
 * blurred plate or one of three baked cross-fades (weights 0.25 / 0.5 / 0.75) per transition, like the mod's
 * translucent second pass. The plate model has the axle at its centre (the display rotates about it, pivot = wheel-local
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
        plateBlends(jar, tree);
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

    /** Cross-fade steps per transition (weights step / 4). */
    static final int BLEND_STEPS = 3;

    /**
     * The mod draws the lower blur level (cutout) and the upper one on top with alpha = weight. Baked per pixel: where
     * both are opaque the colours mix; where only one is, that one shows (the upper only from weight 0.5 on, so the
     * result stays cut-out like the plate).
     */
    static java.awt.image.BufferedImage blend(java.awt.image.BufferedImage sheet, int lower, double w) {
        int size = 320 * sheet.getWidth() / SHEET_W;
        java.awt.image.BufferedImage out = Images.blank(size, size);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int l = sheet.getRGB(320 * lower * sheet.getWidth() / SHEET_W + x, y);
                int u = sheet.getRGB(320 * (lower + 1) * sheet.getWidth() / SHEET_W + x, y);
                boolean lo = (l >>> 24) == 255;
                boolean uo = (u >>> 24) == 255;
                int p;
                if (lo && uo) {
                    int r = (int) Math.round((l >> 16 & 255) * (1 - w) + (u >> 16 & 255) * w);
                    int g = (int) Math.round((l >> 8 & 255) * (1 - w) + (u >> 8 & 255) * w);
                    int b = (int) Math.round((l & 255) * (1 - w) + (u & 255) * w);
                    p = 0xFF000000 | r << 16 | g << 8 | b;
                } else if (lo) {
                    p = l;
                } else if (uo && w >= 0.5) {
                    p = u;
                } else {
                    p = 0;
                }
                out.setRGB(x, y, p);
            }
        }
        return out;
    }

    /** Baked cross-fade plates and the dispatching item definition. */
    static void plateBlends(ModJar jar, PackTree tree) {
        java.awt.image.BufferedImage sheet = Images.read(jar.bytes(
                "assets/roulettecasino/textures/entity/fortune_wheel.png"));
        JsonArray entries = new JsonArray();
        entries.add(PlinkoPack.entry(0, ref("kaboofcasino:wheel/plate_0")));
        for (int lower = 0; lower < 2; lower++) {
            for (int step = 1; step <= BLEND_STEPS; step++) {
                double w = step / (BLEND_STEPS + 1.0);
                String name = "wheel_plate_" + lower + "_" + step;
                tree.put("assets/kaboofcasino/textures/block/" + name + ".png", Images.write(blend(sheet, lower, w)));
                double inset = 0.5 / 320 * 16;
                JsonArray plate = new JsonArray();
                plate.add(Models.quad(8 - PLATE_HALF, 8 - PLATE_HALF, 8 + PLATE_HALF, 8 + PLATE_HALF, 8,
                        new double[] {inset, inset, 16 - inset, 16 - inset}, -1, 0));
                Models.write(tree, "wheel/plate_" + lower + "_" + step, "kaboofcasino:block/" + name, plate, false);
                entries.add(PlinkoPack.entry(lower + w - 0.125, ref("kaboofcasino:wheel/plate_" + lower + "_" + step)));
            }
            entries.add(PlinkoPack.entry(lower + 0.875, ref("kaboofcasino:wheel/plate_" + (lower + 1))));
        }
        JsonObject dispatch = new JsonObject();
        dispatch.addProperty("type", "minecraft:range_dispatch");
        dispatch.addProperty("property", "minecraft:custom_model_data");
        dispatch.addProperty("index", 0);
        dispatch.add("entries", entries);
        dispatch.add("fallback", ref("kaboofcasino:wheel/plate_0"));
        tree.put("assets/kaboofcasino/items/wheel/plate.json", Json.bytes(Json.object("model", dispatch)));
    }

    private static JsonObject ref(String model) {
        JsonObject r = new JsonObject();
        r.addProperty("type", "minecraft:model");
        r.addProperty("model", model);
        return r;
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
