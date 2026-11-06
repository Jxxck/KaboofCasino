package de.tigges.kaboofcasino.packtool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;

/**
 * Attribution required by the mod's licence (MIT with the condition to link the CurseForge page visibly): the pack
 * description, {@code credits/CREDITS.md}, and the mod's own licence texts under {@code credits/}.
 */
final class Credits {

    static final String MOD_URL = "https://www.curseforge.com/minecraft/mc-mods/minevegas-casino";

    private Credits() {
    }

    static void add(ModJar jar, PackTree tree) {
        JsonObject title = new JsonObject();
        title.addProperty("text", "KaboofCasino");
        title.addProperty("color", "gold");
        JsonObject rest = new JsonObject();
        rest.addProperty("text", " - Grafiken und Sounds: MineVegas Casino von byMehd (MIT), " + MOD_URL);
        rest.addProperty("color", "gray");
        JsonArray extra = new JsonArray();
        extra.add(rest);
        title.add("extra", extra);
        JsonObject pack = new JsonObject();
        pack.add("description", title);
        pack.addProperty("min_format", 97);
        pack.addProperty("max_format", 97);
        tree.put("pack.mcmeta", Json.bytes(Json.object("pack", pack)));
        tree.put("pack.png", jar.bytes("assets/roulettecasino/icon.png"));
        tree.put("credits/LICENSE.txt", jar.bytes("LICENSE.txt"));
        tree.put("credits/CREDITS.md", credits().getBytes(StandardCharsets.UTF_8));
    }

    static String credits() {
        return """
                # KaboofCasino resource pack

                The models, textures, GUI art, sounds and texts in this pack come from the Minecraft mod
                **MineVegas Casino** 1.1.0 by byMehd:

                %s

                MineVegas Casino is licensed under the MIT licence with an attribution condition (see LICENSE.txt in
                this folder): any port that uses its code or assets must visibly link the page above.

                KaboofCasino (the Paper plugin of the Kaboof SMP server) converts these assets for unmodified
                26.3 clients: block models become item models shown by display entities, the reel drums, the lever
                and the lights are generated from the mod's geometry, the GUI art is re-composed into font glyphs,
                odd texture sizes are padded to multiples of 16 px. GUI text uses glyphs of the vanilla
                Minecraft font.

                The lounge music of the mod (Kevin MacLeod, CC BY 4.0) is not part of this pack.
                """.formatted(MOD_URL);
    }
}
