package de.tigges.kaboofcasino.packtool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;

/**
 * The slot machine's chest GUI art (render.md 5.2): the mod's 366x266 screen around a vanilla 9x6 chest.
 *
 * <p>The mod draws its window so the player inventory sits at (31, 176); the chest has it at (8, 140), so the
 * inventory part of the art is drawn at window offset (-23, -36). The chest is centred on its own 222 px height,
 * which would put the art's top 12 px above the screen at GUI scale 4 on 1920x1080 (270 px tall). Re-composition
 * before cutting the art into glyph tiles (composite origin = window (-23, -24)):
 * <ol>
 *   <li>left panel: the 12 plain felt rows 118..129 between the bet slot and the SPIN button are removed, so
 *       everything above them (headline, reel window, bet slot) moves 12 px down (window = mod + (-23, -24)); the
 *       button area, the frame's bottom line and the inventory stay (window = mod + (-23, -36));</li>
 *   <li>the bet-slot frame and its laurels (mod 78..146 x 90..118) move 1 px left onto grid slot (4, 3) (item at
 *       window (80, 72), the mod draws it at mod (104, 96));</li>
 *   <li>the 8 transparent columns 224..231 between the panels are removed: the payout panel moves 8 px left and
 *       12 px down as a whole (only its outer border is clipped at scale 4 on 1920x1080).</li>
 * </ol>
 * The result (358x266) is cut into 2x2 tiles (<= 256 px per glyph). The reel window faces are 24 glyphs (3 reels x
 * 8 faces) from {@code gui/slot_machine_reels.png}, drawn per open at window (25 + 44 k, 6). The SPIN/collect
 * buttons are {@code oversized_in_gui} item models (72x18, the mod's {@code DerbyUiArt.button} colours, label baked
 * in) in grid slot (4, 4), covering the mod's button position window (53, 97).
 */
final class SlotMachineGui {

    static final int ART_X = -23;
    static final int ART_Y = -24;
    /** Felt rows of the left panel removed between the button and the inventory label. */
    static final int CUT_FROM = 118;
    static final int CUT_ROWS = 12;
    static final int TILE_W = 179;
    static final int TILE_H = 133;
    static final String[] SYMBOLS = {"seven", "diamond", "bar", "bell", "lemon", "cherry"};

    private SlotMachineGui() {
    }

    static void add(ModJar jar, PackTree tree, GuiFont font, TextGlyphs text) {
        BufferedImage art = compose(Images.read(jar.bytes("assets/roulettecasino/textures/gui/slot_machine.png")));
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                BufferedImage tile = Images.crop(art, col * TILE_W, row * TILE_H, TILE_W, TILE_H);
                font.art("slot_bg_" + col + "_" + row, tile, "font/slot/bg_" + col + "_" + row + ".png",
                        ART_X + col * TILE_W, ART_Y + row * TILE_H);
            }
        }
        BufferedImage reels = Images.read(jar.bytes("assets/roulettecasino/textures/gui/slot_machine_reels.png"));
        String[] names = new String[24];
        for (int reel = 0; reel < 3; reel++) {
            for (int face = 0; face < 8; face++) {
                names[reel * 8 + face] = "slot_reel_" + reel + "_" + face;
            }
        }
        // mod: reel k at (48 + 44k, 30) -> window (25 + 44k, 6); ascent 13 - 6
        font.grid(names, reels, "font/slot/reels.png", 8, 3, 41, 7, 25, 6);

        BufferedImage sheet = Images.read(jar.bytes("assets/roulettecasino/textures/entity/slot_machine.png"));
        BufferedImage icons = Images.crop(sheet, 388, 0, 96, 16); // SlotMachineRenderGeometry.ICON_UV
        String[] symbolNames = new String[SYMBOLS.length];
        for (int s = 0; s < SYMBOLS.length; s++) {
            symbolNames[s] = "symbol_" + SYMBOLS[s];
        }
        // for floating panels (text displays): 9 px tall like a text line, top 1 px above the cap height
        font.grid(symbolNames, icons, "font/slot/symbols.png", 6, 1, 9, 8, 0, 0);

        button(tree, text, "spin_on", "DREHEN", true);
        button(tree, text, "spin_off", "DREHEN", false);
        button(tree, text, "collect_on", "ABHOLEN", true);
    }

    /** The re-composition steps described in the class comment. */
    static BufferedImage compose(BufferedImage mod) {
        if (mod.getWidth() != 366 || mod.getHeight() != 266) {
            throw new IllegalStateException("unexpected slot GUI size " + mod.getWidth() + "x" + mod.getHeight());
        }
        for (int x = 224; x < 232; x++) {
            for (int y = 0; y < 266; y++) {
                if ((mod.getRGB(x, y) >>> 24) != 0) {
                    throw new IllegalStateException("column " + x + " of the slot GUI is not transparent");
                }
            }
        }
        BufferedImage left = Images.crop(mod, 0, 0, 224, 266);
        BufferedImage slot = Images.crop(left, 78, 90, 69, 29);
        Images.copy(slot, 0, 0, 69, 29, left, 77, 90);
        BufferedImage out = Images.blank(358, 266);
        Images.copy(left, 0, 0, 224, CUT_FROM, out, 0, 0);
        Images.copy(left, 0, CUT_FROM + CUT_ROWS, 224, 266 - CUT_FROM - CUT_ROWS, out, 0, CUT_FROM);
        Images.copy(mod, 232, 0, 134, 266, out, 224, 0);
        return out;
    }

    /**
     * A 72x18 button item. Model units = GUI pixels; the quad is 36x9 units at display scale 2 around the model
     * centre, placed so that in grid slot (4, 4) (item area window 80..96 x 90..106, centre 88/98) it covers the
     * mod's button at window 53..125 x 97..115 (centre 89/106: +1 right, +8 down = model +0.5 x, -4 y).
     */
    static void button(PackTree tree, TextGlyphs text, String name, String label, boolean enabled) {
        BufferedImage img = Images.blank(72, 18);
        int w = 72;
        int h = 18;
        Images.fill(img, 0, 0, w, h, -15719140);
        Images.fill(img, 1, 1, w - 2, h - 2, enabled ? -4878005 : -11773114);
        Images.fill(img, 2, 2, w - 4, h - 4, enabled ? -13938371 : -14139856);
        Images.fill(img, 2, 2, w - 4, 1, enabled ? -11373737 : -12956100);
        Images.fill(img, 2, h - 3, w - 4, 1, -15257821);
        int width = text.width(label);
        text.draw(img, label, w / 2 - width / 2, 5, enabled ? -792641 : -8153987, true);
        String texture = "kaboofcasino:item/gui/" + name;
        tree.put("assets/kaboofcasino/textures/item/gui/" + name + ".png", Images.write(img));

        JsonObject face = new JsonObject();
        face.add("uv", Json.array(0, 0, 16, 16));
        face.addProperty("texture", "#t");
        JsonObject element = new JsonObject();
        element.add("from", Json.array(-9.5, -0.5, 8));
        element.add("to", Json.array(26.5, 8.5, 8));
        element.add("faces", Json.object("south", face));
        JsonArray elements = new JsonArray();
        elements.add(element);
        JsonObject textures = new JsonObject();
        textures.addProperty("t", texture);
        textures.addProperty("particle", texture);
        JsonObject gui = new JsonObject();
        gui.add("rotation", Json.array(0, 0, 0));
        gui.add("translation", Json.array(0, 0, 0));
        gui.add("scale", Json.array(2, 2, 2));
        JsonObject model = new JsonObject();
        model.addProperty("gui_light", "front");
        model.add("textures", textures);
        model.add("elements", elements);
        model.add("display", Json.object("gui", gui));
        tree.put("assets/kaboofcasino/models/gui/" + name + ".json", Json.bytes(model));

        JsonObject definition = Json.itemDefinition("kaboofcasino:gui/" + name);
        definition.addProperty("oversized_in_gui", true);
        tree.put("assets/kaboofcasino/items/gui/" + name + ".json", Json.bytes(definition));
    }
}
