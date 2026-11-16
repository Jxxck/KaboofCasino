package de.tigges.kaboofcasino.packtool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;

/**
 * Who made what: the plugin's author, and the attribution required by the mod's licence (MIT with the condition to
 * link the CurseForge page visibly) in the pack description, {@code credits/CREDITS.md}, and the mod's own licence
 * texts under {@code credits/}.
 */
final class Credits {

    static final String MOD_URL = "https://www.curseforge.com/minecraft/mc-mods/minevegas-casino";
    /** The plugin's author as the packs name him. */
    static final String AUTHOR = "Jxxck";

    private Credits() {
    }

    static void add(ModJar jar, PackTree tree) {
        JsonObject title = new JsonObject();
        title.addProperty("text", "KaboofCasino");
        title.addProperty("color", "gold");
        JsonObject rest = new JsonObject();
        rest.addProperty("text", PackLang.text(" by " + AUTHOR + " - graphics and sounds: MineVegas Casino by byMehd (MIT), ",
                " von " + AUTHOR + " - Grafiken und Sounds: MineVegas Casino von byMehd (MIT), ") + MOD_URL);
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

                Resource pack of KaboofCasino, a Paper plugin by %s that brings a casino to unmodified Minecraft 26.3
                clients.

                Most of the models, textures, GUI art, sounds and texts in this pack come from the Minecraft mod
                **MineVegas Casino** 1.1.0 by byMehd:

                %s

                MineVegas Casino is licensed under the MIT licence with an attribution condition (see LICENSE.txt in
                this folder): any port that uses its code or assets must visibly link the page above.

                KaboofCasino converts these assets for unmodified clients: block models become item models shown by
                display entities, the reel drums, the lever and the lights are generated from the mod's geometry, the
                GUI art is re-composed into font glyphs, odd texture sizes are padded to multiples of 16 px.

                The plugin's own additions are by %s, drawn in the mod's style and partly built from its textures:
                chips, the cashier, the Chip-Shop, the leaderboards, the casino outfits, the jukebox, the Texas
                Hold'em screens, the bigger casino signs and the stairs and slabs of the wall blocks.

                GUI text uses glyphs of Minecraft's default font. Not an official Minecraft product; not approved by
                or associated with Mojang or Microsoft.

                The lounge music of the mod (Kevin MacLeod, CC BY 4.0) is not part of this pack.
                """.formatted(AUTHOR, MOD_URL, AUTHOR);
    }
}
