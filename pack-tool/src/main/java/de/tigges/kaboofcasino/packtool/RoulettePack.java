package de.tigges.kaboofcasino.packtool;

import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.List;

/**
 * Roulette models (render.md 1.2/3.2; mod {@code RouletteBetRenderer}): the four static body models
 * ({@code roulettecasino:block/roulette_body_0..3}, one display per part) with item definitions, the table item, the
 * entity sheet copied into the blocks atlas, and the converted rotor (1808 quads, rotation axis at the model centre)
 * and ball (centre at the model centre) meshes.
 */
final class RoulettePack {

    static final String SHEET = "roulettecasino:block/casino_entity/roulette_wheel";
    static final List<String> SOUND_EVENTS = List.of("start", "tick", "win", "lose", "payout");
    /** Prefixes, or exact keys ending in "=" ({@code gui.roulettecasino.black} must not take the blackjack keys). */
    static final List<String> LANG_PREFIXES = List.of("block.roulettecasino.roulette_table",
            "gui.roulettecasino.red=", "gui.roulettecasino.black=", "gui.roulettecasino.green=",
            "gui.roulettecasino.play=", "gui.roulettecasino.header_top=", "gui.roulettecasino.header_bottom=",
            "gui.roulettecasino.roulette.", "message.roulettecasino.no_bet=", "subtitles.roulettecasino.start=",
            "subtitles.roulettecasino.tick=", "subtitles.roulettecasino.win=", "subtitles.roulettecasino.lose=",
            "subtitles.roulettecasino.payout=");

    private RoulettePack() {
    }

    static void add(ModJar jar, PackTree tree, Path packSrc) {
        ModelCopier copier = new ModelCopier(jar, tree);
        for (int part = 0; part < 4; part++) {
            String model = "roulettecasino:block/roulette_body_" + part;
            copier.model(model);
            tree.put("assets/roulettecasino/items/block/roulette_body_" + part + ".json",
                    Json.bytes(Json.itemDefinition(model)));
        }
        copier.itemDefinition("roulettecasino:roulette_table");
        copier.copyAs("assets/roulettecasino/textures/entity/roulette_wheel.png",
                "assets/roulettecasino/textures/block/casino_entity/roulette_wheel.png");
        for (String name : List.of("rotor", "ball")) {
            JsonObject mesh = Json.parse(ModJar.readAll(packSrc.resolve("meshes/roulette_" + name + ".json")));
            Models.write(tree, "roulette/" + name, SHEET, mesh.getAsJsonArray("elements"), true);
        }
    }
}
