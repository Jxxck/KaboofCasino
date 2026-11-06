package de.tigges.kaboofcasino.packtool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;

/**
 * The slot machine's chest GUI art (render.md 5.2): the mod's 366x266 screen around a vanilla 9x6 chest.
 *
 * <p>The mod draws its window so the player inventory sits at (31, 176); the chest has it at (8, 140), so the
 * inventory part of the art is drawn at window offset (-23, -36). Target screens (scaled GUI size): 480x270
 * (1080p, scale 4), 456x256 (1366x768), 427x240 (720p/1440p/4K "Auto"); the chest is centred, so there is
 * {@code (W - 176) / 2} px room on each side (125 at 427) and only 9 px above/below at 240. Re-composition before
 * cutting the art into glyph tiles (composite origin = window (-23, -20)):
 * <ol>
 *   <li>left panel: the 16 plain felt rows 116..131 between the bet slot and the SPIN button are removed, so the
 *       headline, the reel window and the bet slot move 16 px down (window = mod + (-23, -20)); the button area, the
 *       frame's bottom line and the inventory stay (window = mod + (-23, -36));</li>
 *   <li>the bet-slot frame and its laurels move by (-1, -4) onto grid slot (4, 3) (item at window (80, 72); the mod
 *       draws it at mod (104, 96)); the vacated rows get plain felt;</li>
 *   <li>the left panel loses 19 plain felt columns (mod 192..210, between the inventory art and its right frame
 *       border; only horizontal frame lines cross them), the transparent gap 224..231 goes, and the payout panel
 *       loses 15 plain columns (mod 304..318, between the symbols and the multipliers): the art ends at window x 301,
 *       125 px right of the chest; the panel's JACKPOT lettering is pasted back centred; the panel uses
 *       window = mod + (x, -20).</li>
 * </ol>
 * The result (324x266) is cut into 2x2 tiles (<= 256 px per glyph). The reel window faces are 24 glyphs (3 reels x
 * 8 faces) from {@code gui/slot_machine_reels.png}, drawn per open at window (25 + 44 k, 10). Text positions are
 * declared here as anchors ({@link #anchors}) and written to the manifest, so the plugin never hard-codes them. The
 * SPIN/collect buttons are {@code oversized_in_gui} item models (72x18, the mod's {@code DerbyUiArt.button} colours,
 * label baked in) in grid slot (4, 4), covering the mod's button position window (53, 97).
 */
final class SlotMachineGui {

    static final int ART_X = -23;
    static final int ART_Y = -20;
    /** Felt rows of the left panel removed between the bet slot and the button. */
    static final int CUT_FROM = 116;
    static final int CUT_ROWS = 16;
    /** Plain columns removed from the left panel (mod x). */
    static final int LEFT_CUT_FROM = 192;
    static final int LEFT_CUT = 19;
    static final int LEFT_W = 224 - LEFT_CUT;
    /** Plain columns removed from the payout panel (mod x). */
    static final int PANEL_CUT_FROM = 304;
    static final int PANEL_CUT = 15;
    static final int PANEL_W = 134 - PANEL_CUT;
    static final int WIDTH = LEFT_W + PANEL_W;
    static final int TILE_W = WIDTH / 2;
    static final int TILE_H = 133;
    static final String[] SYMBOLS = {"seven", "diamond", "bar", "bell", "lemon", "cherry"};

    private SlotMachineGui() {
    }

    /** Text and glyph positions of the slot screen (window coordinates). */
    static java.util.List<GuiFont.Anchor> anchors() {
        java.util.List<GuiFont.Anchor> out = new java.util.ArrayList<>();
        out.add(GuiFont.Anchor.text("slot_headline", 112 + ART_X, 12 + ART_Y, "center"));
        out.add(GuiFont.Anchor.text("slot_payouts", LEFT_W + PANEL_W / 2 + ART_X, 14 + ART_Y, "center"));
        out.add(GuiFont.Anchor.text("slot_bet", 112 + ART_X, 59, "center"));
        int multiplierRight = LEFT_W + (351 - 232 - PANEL_CUT) + ART_X;
        for (int row = 0; row < 8; row++) {
            out.add(GuiFont.Anchor.text("slot_multiplier_" + row, multiplierRight, 46 + 21 * row + ART_Y, "right"));
        }
        for (int reel = 0; reel < 3; reel++) {
            out.add(GuiFont.Anchor.glyph("slot_reel_" + reel, 48 + 44 * reel + ART_X, 30 + ART_Y));
        }
        return out;
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
        // mod: reel k at (48 + 44k, 30) -> window (25 + 44k, 10); ascent 13 - 10
        int reelY = 30 + ART_Y;
        font.grid(names, reels, "font/slot/reels.png", 8, 3, 41, 13 - reelY, 25, reelY);
        for (GuiFont.Anchor a : anchors()) {
            font.anchor(a);
        }

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
        // left panel: bet slot (-1, -4), refill the vacated rows with felt, cut 16 felt rows
        BufferedImage left = Images.crop(mod, 0, 0, 224, 266);
        BufferedImage slot = Images.crop(left, 77, 88, 71, 28);
        Images.copy(mod, 77, 120, 71, 4, left, 77, 112);
        Images.copy(slot, 0, 0, 71, 28, left, 76, 84);
        // payout panel: plain columns out, JACKPOT lettering back in the middle
        BufferedImage panel = Images.blank(PANEL_W, 266);
        int keep = PANEL_CUT_FROM - 232;
        Images.copy(mod, 232, 0, keep, 266, panel, 0, 0);
        Images.copy(mod, PANEL_CUT_FROM + PANEL_CUT, 0, PANEL_W - keep, 266, panel, keep, 0);
        int jx = 262;
        int jw = 74;
        Images.copy(mod, jx, 226, jw, 24, panel, (PANEL_W - jw) / 2, 226);

        BufferedImage narrow = Images.blank(LEFT_W, 266);
        Images.copy(left, 0, 0, LEFT_CUT_FROM, 266, narrow, 0, 0);
        Images.copy(left, LEFT_CUT_FROM + LEFT_CUT, 0, 224 - LEFT_CUT_FROM - LEFT_CUT, 266, narrow, LEFT_CUT_FROM, 0);
        BufferedImage out = Images.blank(WIDTH, 266);
        Images.copy(narrow, 0, 0, LEFT_W, CUT_FROM, out, 0, 0);
        Images.copy(narrow, 0, CUT_FROM + CUT_ROWS, LEFT_W, 266 - CUT_FROM - CUT_ROWS, out, 0, CUT_FROM);
        Images.copy(panel, 0, 0, PANEL_W, 266, out, LEFT_W, 0);
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
