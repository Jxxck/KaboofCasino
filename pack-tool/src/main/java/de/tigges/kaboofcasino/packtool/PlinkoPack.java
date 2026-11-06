package de.tigges.kaboofcasino.packtool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.nio.file.Path;
import java.util.List;

/**
 * Plinko models (render.md 1.2/3.2; mod {@code PlinkoRenderer}): the nine static body models
 * ({@code roulettecasino:block/plinko_0..8}: board, pegs, pocket plates, chute, marquee) with item definitions, the
 * Plinko item, the entity sheet copied into the blocks atlas, the ball (converted mesh, centre = model centre), the
 * lights in one model (52 peg lamps at (pinX, rowY, 13.5) with tints 0..51 and 11 bulbs at z 11.5 with tints
 * 52..62, self-lit) and the 9 pocket rims in another (z 14.1125, tints 0..8). Model coordinates of the lights and
 * rims are machine-local px = board px - (16, 16, 0) (pivot (8, 8, 8)). The pockets ({@code PlinkoRenderer.rims}
 * and {@code glows}) are one item: a composite of nine {@code range_dispatch} entries on the pocket's
 * {@code custom_model_data} float: above 0.01 the rim (grey tint = colour k), and from the glow's thresholds on the
 * converted glow quad with one of {@link #GLOW_STEPS} baked alpha steps (the mod: white x the sheet's glow texel,
 * alpha {@code round(96.9 x clamp((p - 0.45) / 0.55))}).
 */
final class PlinkoPack {

    static final String SHEET = "roulettecasino:block/casino_entity/plinko";
    static final int LAMPS = 52;
    static final int BULBS = 11;
    static final int POCKETS = 9;
    static final int GLOW_STEPS = 4;
    static final double GLOW_ALPHA = 96.9;
    /** {@code PlinkoRenderGeometry.BULBS}: crest x 14..34 at y 47, left x 3 and right x 45 at y 41.5/43.5/45.5. */
    static final double[] BULB_XY = {14, 47, 19, 47, 24, 47, 29, 47, 34, 47, 3, 41.5, 3, 43.5, 3, 45.5, 45, 41.5,
        45, 43.5, 45, 45.5};
    static final List<String> SOUND_EVENTS = List.of("plinko_drop", "plinko_peg", "plinko_pocket", "plinko_win",
            "plinko_jackpot", "plinko_lose", "plinko_partial", "plinko_payout");
    static final List<String> LANG_PREFIXES = List.of("block.roulettecasino.plinko", "gui.roulettecasino.plinko.",
            "message.roulettecasino.plinko.", "subtitles.roulettecasino.plinko_");

    private PlinkoPack() {
    }

    static double pinX(int row, int index) {
        return 24.0 + (index - (row + 2) / 2.0) * 4.0;
    }

    static double rowY(int row) {
        return 36.5 - row * 3.0;
    }

    static void add(ModJar jar, PackTree tree, Path packSrc) {
        ModelCopier copier = new ModelCopier(jar, tree);
        for (int part = 0; part < 9; part++) {
            String model = "roulettecasino:block/plinko_" + part;
            copier.model(model);
            tree.put("assets/roulettecasino/items/block/plinko_" + part + ".json",
                    Json.bytes(Json.itemDefinition(model)));
        }
        copier.itemDefinition("roulettecasino:plinko");
        copier.copyAs("assets/roulettecasino/textures/entity/plinko.png",
                "assets/roulettecasino/textures/block/casino_entity/plinko.png");
        JsonObject ball = Json.parse(ModJar.readAll(packSrc.resolve("meshes/plinko_ball.json")));
        Models.write(tree, "plinko/ball", SHEET, ball.getAsJsonArray("elements"), true);
        Models.write(tree, "plinko/lights", SHEET, lights(mesh(packSrc, "plinko_lamp"), mesh(packSrc, "plinko_bulb")),
                false);
        Models.tintedDefinition(tree, "plinko/lights", LAMPS + BULBS);
        pockets(jar, tree, mesh(packSrc, "plinko_rim"), mesh(packSrc, "plinko_glow"));
    }

    private static JsonArray mesh(Path packSrc, String name) {
        return Json.parse(ModJar.readAll(packSrc.resolve("meshes/" + name + ".json"))).getAsJsonArray("elements");
    }

    /** Lamps (one per peg, row by row) then bulbs; each mesh moved from the model centre to its board point. */
    static JsonArray lights(JsonArray lamp, JsonArray bulb) {
        JsonArray out = new JsonArray();
        int pin = 0;
        for (int row = 0; row < 8; row++) {
            for (int i = 0; i < row + 3; i++) {
                out.addAll(Models.placed(lamp, pinX(row, i) - 16 - 8, rowY(row) - 16 - 8, 13.5 - 8, pin++, 15));
            }
        }
        if (pin != LAMPS) {
            throw new IllegalStateException(pin + " pegs");
        }
        for (int b = 0; b < BULBS; b++) {
            out.addAll(Models.placed(bulb, BULB_XY[2 * b] - 16 - 8, BULB_XY[2 * b + 1] - 16 - 8, 11.5 - 8,
                    LAMPS + b, 15));
        }
        return out;
    }

    /** Glow step s (1..GLOW_STEPS): its alpha and the pocket level from which it is the nearest step. */
    static int glowAlpha(int step) {
        return (int) Math.round(GLOW_ALPHA * step / GLOW_STEPS);
    }

    static double glowThreshold(int step) {
        return 0.45 + 0.55 * (step - 0.5) / GLOW_STEPS;
    }

    /** Rim and glow models per pocket and the composite item definition {@code kaboofcasino:plinko/pockets}. */
    static void pockets(ModJar jar, PackTree tree, JsonArray rim, JsonArray glow) {
        java.awt.image.BufferedImage sheet = Images.read(jar.bytes("assets/roulettecasino/textures/entity/plinko.png"));
        // GLOW_UV {0.25, 0.5, 0.3125, 0.75} of the 64x16 sheet = texels 16..20 x 8..12, scaled to 16x16
        for (int s = 1; s <= GLOW_STEPS; s++) {
            java.awt.image.BufferedImage img = Images.blank(16, 16);
            int alpha = glowAlpha(s);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int texel = sheet.getRGB(16 + x / 4, 8 + y / 4) & 0xFFFFFF;
                    img.setRGB(x, y, alpha << 24 | texel);
                }
            }
            tree.put("assets/kaboofcasino/textures/block/plinko_glow_" + s + ".png", Images.write(img));
        }
        JsonArray pockets = new JsonArray();
        for (int k = 0; k < POCKETS; k++) {
            double dx = 8 + 4 * k - 16 - 8;
            Models.write(tree, "plinko/rim_" + k, SHEET, Models.placed(rim, dx, 12.5 - 16 - 8, 14.1125 - 8, 0, 15),
                    false);
            JsonArray entries = new JsonArray();
            entries.add(entry(0.0100001, composite(rimRef(k))));
            for (int s = 1; s <= GLOW_STEPS; s++) {
                JsonArray quad = Models.placed(glow, dx, 12.5 - 16 - 8, 14.0925 - 8, -1, 15);
                for (var e : quad) {
                    for (var f : e.getAsJsonObject().getAsJsonObject("faces").entrySet()) {
                        f.getValue().getAsJsonObject().add("uv", Json.array(0, 0, 16, 16));
                    }
                }
                Models.write(tree, "plinko/glow_" + k + "_" + s, "kaboofcasino:block/plinko_glow_" + s, quad, false);
                JsonObject glowRef = new JsonObject();
                glowRef.addProperty("type", "minecraft:model");
                glowRef.addProperty("model", "kaboofcasino:plinko/glow_" + k + "_" + s);
                entries.add(entry(glowThreshold(s), composite(rimRef(k), glowRef)));
            }
            JsonObject dispatch = new JsonObject();
            dispatch.addProperty("type", "minecraft:range_dispatch");
            dispatch.addProperty("property", "minecraft:custom_model_data");
            dispatch.addProperty("index", k);
            dispatch.add("entries", entries);
            dispatch.add("fallback", Json.object("type", new JsonPrimitive("minecraft:empty")));
            pockets.add(dispatch);
        }
        JsonObject all = new JsonObject();
        all.addProperty("type", "minecraft:composite");
        all.add("models", pockets);
        tree.put("assets/kaboofcasino/items/plinko/pockets.json", Json.bytes(Json.object("model", all)));
    }

    /** Rim k, its tint from {@code custom_model_data} colour k. */
    private static JsonObject rimRef(int k) {
        JsonObject tint = new JsonObject();
        tint.addProperty("type", "minecraft:custom_model_data");
        tint.addProperty("index", k);
        tint.addProperty("default", 0xFFFFFF);
        JsonArray tints = new JsonArray();
        tints.add(tint);
        JsonObject ref = new JsonObject();
        ref.addProperty("type", "minecraft:model");
        ref.addProperty("model", "kaboofcasino:plinko/rim_" + k);
        ref.add("tints", tints);
        return ref;
    }

    static JsonObject entry(double threshold, JsonObject model) {
        JsonObject e = new JsonObject();
        e.addProperty("threshold", threshold);
        e.add("model", model);
        return e;
    }

    static JsonObject composite(JsonObject... models) {
        JsonArray list = new JsonArray();
        for (JsonObject m : models) {
            list.add(m);
        }
        JsonObject c = new JsonObject();
        c.addProperty("type", "minecraft:composite");
        c.add("models", list);
        return c;
    }
}
