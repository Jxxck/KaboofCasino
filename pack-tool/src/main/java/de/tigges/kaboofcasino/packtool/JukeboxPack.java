package de.tigges.kaboofcasino.packtool;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The casino jukebox (Jack 2026-11-10, not in MineVegas): a retro jukebox in the casino's materials, 1 block wide and 2
 * tall, and the sound events of its music.
 * <ul>
 *   <li>{@code kaboofcasino:jukebox/body}: the whole cabinet in the anchor cell's frame (x 0..16, y 0..32 over both
 *       blocks, z 16 the front): a walnut plinth and cabinet, a ruby velvet speaker grille behind brass bars, glowing
 *       amber tubes up both sides, a selector strip with red buttons, a recessed window of the mod's casino glass in a
 *       brass frame (the plugin's rig turns the playing record behind it), and a stepped walnut arch outlined in amber
 *       light with a red note in its middle;</li>
 *   <li>{@code kaboofcasino:jukebox}: the placing item, a 32x32 icon of the jukebox ({@link #icon()});</li>
 *   <li>{@code kaboofcasino:jukebox.<id>} sound events for every track of {@code pack-src/jukebox/tracks.json}: they
 *       point at the client's own file ({@code minecraft:records/cat}, {@code minecraft:music/game/...}) at the vanilla
 *       volume, or at the music pack's lounge file ({@code casinosound:music/<id>}), streamed, so the pack ships no music
 *       and the jukebox never shares a sound key with the casino speakers.</li>
 * </ul>
 */
final class JukeboxPack {

    static final String BODY = "kaboofcasino:jukebox/body";
    static final String ITEM = "kaboofcasino:jukebox";
    static final String CATALOG = "jukebox/tracks.json";
    /** The window's recess: the record turns between its back (z) and the glass. */
    static final double RECESS_Z = 11;
    static final double GLASS_Z = 13.4;
    static final double WINDOW_X0 = 3;
    static final double WINDOW_X1 = 13;
    static final double WINDOW_Y0 = 17;
    static final double WINDOW_Y1 = 23.5;

    private JukeboxPack() {
    }

    static void add(ModJar jar, PackTree tree, Path packSrc) {
        ModelCopier copier = new ModelCopier(jar, tree);
        for (String t : List.of("casinodeco:block/furniture_wood", "casinodeco:block/furniture_brass",
                "casinodeco:block/furniture_black", "casinodeco:block/ruby_velvet",
                "casinodeco:block/casino_glass_clear", "casinodeco:block/sign_warm_led",
                "casinodeco:block/sign_red_led")) {
            copier.texture(t);
        }
        tree.put("assets/kaboofcasino/models/jukebox/body.json", Json.bytes(body()));
        tree.put("assets/kaboofcasino/items/jukebox/body.json", Json.bytes(Json.itemDefinition(BODY)));
        tree.put("assets/kaboofcasino/textures/item/jukebox.png", Images.write(icon()));
        JsonObject iconModel = new JsonObject();
        iconModel.addProperty("parent", "minecraft:item/generated");
        iconModel.add("textures", Json.object("layer0", new JsonPrimitive("kaboofcasino:item/jukebox")));
        tree.put("assets/kaboofcasino/models/item/jukebox.json", Json.bytes(iconModel));
        tree.put("assets/kaboofcasino/items/jukebox.json", Json.bytes(Json.itemDefinition("kaboofcasino:item/jukebox")));
        tree.put("assets/kaboofcasino/sounds.json", Json.bytes(sounds(catalog(packSrc))));
    }

    static JsonObject catalog(Path packSrc) {
        try {
            return Json.parse(Files.readAllBytes(packSrc.resolve(CATALOG)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** One streamed event per vanilla-file track: {@code jukebox.<id>} -> the file at its vanilla volume. */
    static JsonObject sounds(JsonObject catalog) {
        JsonObject out = new JsonObject();
        int n = 0;
        for (JsonElement e : catalog.getAsJsonArray("tracks")) {
            JsonObject t = e.getAsJsonObject();
            if (!t.has("file")) {
                continue;
            }
            JsonObject sound = new JsonObject();
            sound.addProperty("name", t.get("file").getAsString());
            sound.addProperty("stream", true);
            double volume = t.get("volume").getAsDouble();
            if (volume != 1.0) {
                sound.addProperty("volume", volume);
            }
            JsonArray list = new JsonArray();
            list.add(sound);
            JsonObject event = new JsonObject();
            event.add("sounds", list);
            out.add("jukebox." + t.get("id").getAsString(), event);
            n++;
        }
        if (n < 98) {
            throw new IllegalStateException("the jukebox catalog has only " + n + " vanilla tracks");
        }
        return out;
    }

    // ---- the cabinet ------------------------------------------------------------------------------------------------

    private static JsonObject box(double x0, double y0, double z0, double x1, double y1, double z1, String texture,
                                  int light) {
        return ChipShopPack.box(x0, y0, z0, x1, y1, z1, texture, light);
    }

    /** A glowing outline on the front of a box: both sides and the top, {@code w} wide, standing out by 0.3. */
    private static void outline(JsonArray el, double x0, double x1, double y0, double y1, double z, double w) {
        el.add(box(x0, y0, z, x0 + w, y1, z + 0.3, "#tube", 15));
        el.add(box(x1 - w, y0, z, x1, y1, z + 0.3, "#tube", 15));
        el.add(box(x0 + w, y1 - w, z, x1 - w, y1, z + 0.3, "#tube", 15));
    }

    /** The red eighth note on the arch (rows top first, 0.5 px per LED, at most 5 wide). */
    static final String[] NOTE = {"..##", "..#.#", "..#..", "..#..", ".##..", "###..", ".#..."};

    static JsonObject body() {
        JsonArray el = new JsonArray();
        // plinth, cabinet and its brass foot rail
        el.add(box(0.5, 0, 1.5, 15.5, 1.5, 14.5, "#wood", 0));
        el.add(box(0.5, 1.5, 14.2, 15.5, 1.9, 14.5, "#brass", 0));
        el.add(box(1, 1.5, 2, 15, 16.5, 13.5, "#wood", 0));
        // the speaker grille: brass frame, velvet, four bars
        el.add(box(3, 3.5, 13.5, 13, 12.5, 13.7, "#brass", 0));
        el.add(box(3.5, 4, 13.7, 12.5, 12, 13.8, "#velvet", 0));
        for (double x = 4.75; x < 12; x += 2) {
            el.add(box(x, 4, 13.8, x + 0.5, 12, 14, "#brass", 0));
        }
        // the selector strip with its red buttons
        el.add(box(1.5, 13.5, 12.6, 14.5, 15.5, 13.9, "#black", 0));
        for (double x = 3; x < 13; x += 2) {
            el.add(box(x, 14.25, 13.9, x + 1, 14.75, 14.15, "#red", 15));
        }
        // the window section: back, cheeks, lips, the velvet back of the recess, glass in a brass frame
        el.add(box(1, 16.5, 2, 15, 24, RECESS_Z, "#wood", 0));
        el.add(box(1, 16.5, RECESS_Z, WINDOW_X0, 24, 13.5, "#wood", 0));
        el.add(box(WINDOW_X1, 16.5, RECESS_Z, 15, 24, 13.5, "#wood", 0));
        el.add(box(WINDOW_X0, 16.5, RECESS_Z, WINDOW_X1, WINDOW_Y0, 13.5, "#wood", 0));
        el.add(box(WINDOW_X0, WINDOW_Y1, RECESS_Z, WINDOW_X1, 24, 13.5, "#wood", 0));
        el.add(box(WINDOW_X0, WINDOW_Y0, RECESS_Z, WINDOW_X1, WINDOW_Y1, RECESS_Z + 0.05, "#velvet", 0));
        el.add(box(WINDOW_X0, WINDOW_Y0, GLASS_Z, WINDOW_X1, WINDOW_Y1, GLASS_Z + 0.1, "#glass", 0));
        el.add(box(WINDOW_X0 - 0.5, WINDOW_Y0 - 0.5, 13.5, WINDOW_X1 + 0.5, WINDOW_Y0, 13.75, "#brass", 0));
        el.add(box(WINDOW_X0 - 0.5, WINDOW_Y1, 13.5, WINDOW_X1 + 0.5, WINDOW_Y1 + 0.5, 13.75, "#brass", 0));
        el.add(box(WINDOW_X0 - 0.5, WINDOW_Y0, 13.5, WINDOW_X0, WINDOW_Y1, 13.75, "#brass", 0));
        el.add(box(WINDOW_X1, WINDOW_Y0, 13.5, WINDOW_X1 + 0.5, WINDOW_Y1, 13.75, "#brass", 0));
        // the glowing tubes up both sides, brass caps
        el.add(box(0, 1.5, 10.5, 1.25, 24, 14, "#tube", 15));
        el.add(box(14.75, 1.5, 10.5, 16, 24, 14, "#tube", 15));
        el.add(box(-0.1, 23.5, 10.4, 1.35, 24.25, 14.1, "#brass", 0));
        el.add(box(14.65, 23.5, 10.4, 16.1, 24.25, 14.1, "#brass", 0));
        // the stepped arch, each step outlined in amber light
        double[][] steps = {{1, 15, 24, 26.5, 2, 13.5}, {2, 14, 26.5, 28.5, 2.5, 13}, {3.5, 12.5, 28.5, 30, 3, 12.5},
                {5.5, 10.5, 30, 31.25, 3.5, 12}};
        for (double[] s : steps) {
            el.add(box(s[0], s[2], s[4], s[1], s[3], s[5], "#wood", 0));
            outline(el, s[0], s[1], s[2], s[3], s[5], 0.5);
        }
        // the red note over the arch's first two steps, in front of their light outlines
        double led = 0.5;
        double nx = 8 - 5 * led / 2;
        double top = 28.25;
        for (int r = 0; r < NOTE.length; r++) {
            String row = NOTE[r];
            for (int c = 0; c < row.length(); c++) {
                if (row.charAt(c) == '#') {
                    double y1 = top - r * led;
                    el.add(box(nx + c * led, y1 - led, 13.85, nx + (c + 1) * led, y1, 14.05, "#red", 15));
                }
            }
        }
        JsonObject textures = new JsonObject();
        textures.addProperty("particle", "casinodeco:block/furniture_wood");
        textures.addProperty("wood", "casinodeco:block/furniture_wood");
        textures.addProperty("brass", "casinodeco:block/furniture_brass");
        textures.addProperty("black", "casinodeco:block/furniture_black");
        textures.addProperty("velvet", "casinodeco:block/ruby_velvet");
        textures.addProperty("glass", "casinodeco:block/casino_glass_clear");
        textures.addProperty("tube", "casinodeco:block/sign_warm_led");
        textures.addProperty("red", "casinodeco:block/sign_red_led");
        JsonObject model = new JsonObject();
        model.add("textures", textures);
        model.add("elements", el);
        return model;
    }

    // ---- the item icon ----------------------------------------------------------------------------------------------

    static final int WOOD = 0xFF5A3420;
    static final int WOOD_DARK = 0xFF3A2114;
    static final int BRASS = 0xFFD9A93A;
    static final int AMBER = 0xFFFFC34A;
    static final int RED = 0xFFE0383E;
    static final int VELVET = 0xFF8E1E2C;
    static final int GLASS = 0xFF9FC9D6;
    static final int DISC = 0xFF1A1A1A;

    /** A 32x32 icon: the arch with its amber outline, the window with a record, the grille, the plinth. */
    static BufferedImage icon() {
        BufferedImage img = Images.blank(32, 32);
        int[][] arch = {{11, 21, 2, 3}, {9, 23, 4, 5}, {7, 25, 6, 7}, {6, 26, 8, 9}};
        for (int[] a : arch) {
            Images.fill(img, a[0], a[2], a[1] - a[0], a[3] - a[2] + 1, AMBER);
            Images.fill(img, a[0] + 1, a[2] + 1, a[1] - a[0] - 2, a[3] - a[2] + 1, WOOD);
        }
        Images.fill(img, 5, 10, 22, 19, WOOD);
        Images.fill(img, 4, 10, 2, 19, AMBER);
        Images.fill(img, 26, 10, 2, 19, AMBER);
        // note in the arch
        Images.fill(img, 16, 4, 1, 4, RED);
        Images.fill(img, 17, 4, 2, 1, RED);
        Images.fill(img, 14, 7, 3, 2, RED);
        // window with a record
        Images.fill(img, 8, 11, 16, 7, BRASS);
        Images.fill(img, 9, 12, 14, 5, GLASS);
        Images.fill(img, 13, 12, 6, 5, DISC);
        Images.fill(img, 15, 14, 2, 1, RED);
        // selector strip
        Images.fill(img, 7, 19, 18, 1, WOOD_DARK);
        for (int x = 9; x < 24; x += 3) {
            Images.fill(img, x, 19, 1, 1, RED);
        }
        // grille
        Images.fill(img, 8, 21, 16, 6, BRASS);
        Images.fill(img, 9, 22, 14, 4, VELVET);
        for (int x = 11; x < 23; x += 3) {
            Images.fill(img, x, 22, 1, 4, BRASS);
        }
        // plinth
        Images.fill(img, 4, 29, 24, 2, WOOD_DARK);
        Images.fill(img, 4, 28, 24, 1, BRASS);
        return img;
    }
}
