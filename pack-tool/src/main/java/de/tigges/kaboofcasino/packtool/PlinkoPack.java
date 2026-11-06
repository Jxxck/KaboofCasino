package de.tigges.kaboofcasino.packtool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.List;

/**
 * Plinko models (render.md 1.2/3.2; mod {@code PlinkoRenderer}): the nine static body models
 * ({@code roulettecasino:block/plinko_0..8}: board, pegs, pocket plates, chute, marquee) with item definitions, the
 * Plinko item, the entity sheet copied into the blocks atlas, the ball (converted mesh, centre = model centre), the
 * lights in one model (52 peg lamps at (pinX, rowY, 13.5) with tints 0..51 and 11 bulbs at z 11.5 with tints
 * 52..62, self-lit) and the 9 pocket rims in another (z 14.1125, tints 0..8). Model coordinates of the lights and
 * rims are machine-local px = board px - (16, 16, 0) (pivot (8, 8, 8)).
 */
final class PlinkoPack {

    static final String SHEET = "roulettecasino:block/casino_entity/plinko";
    static final int LAMPS = 52;
    static final int BULBS = 11;
    static final int POCKETS = 9;
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
        Models.write(tree, "plinko/rims", SHEET, rims(mesh(packSrc, "plinko_rim")), false);
        Models.tintedDefinition(tree, "plinko/rims", POCKETS);
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

    /** The 9 pocket rims at (8 + 4k, 12.5, 14.1125). */
    static JsonArray rims(JsonArray rim) {
        JsonArray out = new JsonArray();
        for (int k = 0; k < POCKETS; k++) {
            out.addAll(Models.placed(rim, 8 + 4 * k - 16 - 8, 12.5 - 16 - 8, 14.1125 - 8, k, 15));
        }
        return out;
    }
}
