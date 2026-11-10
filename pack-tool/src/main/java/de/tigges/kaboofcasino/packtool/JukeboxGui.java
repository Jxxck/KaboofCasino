package de.tigges.kaboofcasino.packtool;

import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * The jukebox's chest menu: the chip shop's look ({@link ChipShopGui}: the cashier's felt panels around a 9x6 chest,
 * the right panel as a receipt) with nine genre tabs instead of seven. The receipt's texts use the chip shop's
 * anchors. Everything that changes is an item:
 * <pre>
 *  row 0     0-8 the nine genre tabs (the plugin's icon item in each; {@code jukebox_overlay_t} draws tab t selected)
 *  rows 1-4  9-44 the tracks of a page
 *  row 5     45/46 pages (the cashier's {@code prev}/{@code next}), 47 the mode ({@code gui/jukebox/mode_order},
 *            {@code mode_shuffle}, {@code mode_repeat}), 48-51 ABSPIELEN / STOPPEN ({@code play}, {@code stop} in 49),
 *            52 the next track ({@code skip_on}, {@code skip_off}), 53 the range ({@code range_16}, {@code _32},
 *            {@code _48})
 * </pre>
 */
final class JukeboxGui {

    static final String PREFIX = "jukebox";
    static final int TABS = 9;
    static final String TEXTURE_DIR = "assets/kaboofcasino/textures/item/gui/jukebox/";
    static final String TEXTURE_ID = "kaboofcasino:item/gui/jukebox/";
    static final String MODEL_DIR = "assets/kaboofcasino/models/gui/jukebox/";
    static final String MODEL_ID = "kaboofcasino:gui/jukebox/";
    static final String ITEM_DIR = "assets/kaboofcasino/items/gui/jukebox/";
    static final int MODE_SLOT = 47;
    static final int PLAY_ART = 49;
    static final int SKIP_SLOT = 52;
    static final int RANGE_SLOT = 53;
    static final int[] RANGES = {16, 32, 48};

    private JukeboxGui() {
    }

    static void add(ModJar jar, PackTree tree, GuiFont font, TextGlyphs text) {
        BufferedImage bg = background(Images.read(jar.bytes("assets/roulettecasino/textures/gui/duck_derby_felt.png")));
        int tile = (bg.getWidth() + 1) / 2;
        for (int i = 0; i < 2; i++) {
            int w = Math.min(tile, bg.getWidth() - i * tile);
            font.art(PREFIX + "_bg_" + i, Images.crop(bg, i * tile, 0, w, bg.getHeight()), "font/jukebox/bg_" + i
                    + ".png", DerbyGuiArt.ART_X + i * tile, DerbyGuiArt.ART_Y);
        }
        BufferedImage selected = ChipShopGui.overlay();
        for (int t = 0; t < TABS; t++) {
            font.art(PREFIX + "_overlay_" + t, selected, "font/jukebox/overlay.png", ChipShopGui.overlayX(t),
                    ChipShopGui.TAB_Y - ChipShopGui.OVERLAY_PAD);
        }
        buttons(tree, text);
    }

    /** The cashier's background with nine unselected tabs in row 0 and the frames of the track grid (9-44). */
    static BufferedImage background(BufferedImage feltSource) {
        BufferedImage out = CashierGui.background(feltSource);
        DerbyGuiArt.Canvas g = new DerbyGuiArt.Canvas(out, DerbyGuiArt.ART_X, DerbyGuiArt.ART_Y, 1);
        for (int t = 0; t < TABS; t++) {
            DerbyGuiArt.button(g, ChipShopGui.tabX(t), ChipShopGui.TAB_Y, ChipShopGui.TAB_W, ChipShopGui.TAB_W, true,
                    false);
        }
        for (int s = ChipShopGui.FIRST_ITEM; s <= ChipShopGui.LAST_ITEM; s++) {
            DerbyGuiArt.slot(g, DerbyGuiArt.slotX(s), DerbyGuiArt.slotY(s));
        }
        return out;
    }

    static void picture(PackTree tree, String name, BufferedImage image, int slot, int x, int y) {
        tree.put(TEXTURE_DIR + name + ".png", Images.write(image));
        tree.put(MODEL_DIR + name + ".json", Json.bytes(DerbyGuiItems.model(slot, List.of(
                BlackjackGuiItems.Quad.of(TEXTURE_ID + name, x, y, image.getWidth(), image.getHeight())))));
        JsonObject model = new JsonObject();
        model.addProperty("type", "minecraft:model");
        model.addProperty("model", MODEL_ID + name);
        JsonObject definition = Json.object("model", model);
        definition.addProperty("oversized_in_gui", true);
        tree.put(ITEM_DIR + name + ".json", Json.bytes(definition));
    }

    private static void slotPicture(PackTree tree, String name, BufferedImage image, int slot) {
        picture(tree, name, image, slot, DerbyGuiArt.slotX(slot) - 1, DerbyGuiArt.slotY(slot) - 1);
    }

    static void buttons(PackTree tree, TextGlyphs text) {
        picture(tree, "play", DerbyGui.button(text, "ABSPIELEN", CashierGui.COMMIT_W, CashierGui.COMMIT_H, true),
                PLAY_ART, CashierGui.COMMIT_X, CashierGui.COMMIT_Y);
        picture(tree, "stop", DerbyGui.button(text, "STOPPEN", CashierGui.COMMIT_W, CashierGui.COMMIT_H, true),
                PLAY_ART, CashierGui.COMMIT_X, CashierGui.COMMIT_Y);
        slotPicture(tree, "skip_on", DerbyGui.button(text, "»", 18, 18, true), SKIP_SLOT);
        slotPicture(tree, "skip_off", DerbyGui.button(text, "»", 18, 18, false), SKIP_SLOT);
        for (int r : RANGES) {
            slotPicture(tree, "range_" + r, ChipShopGui.qtyButton(text, Integer.toString(r)), RANGE_SLOT);
        }
        for (String mode : List.of("order", "shuffle", "repeat")) {
            slotPicture(tree, "mode_" + mode, modeButton(mode), MODE_SLOT);
        }
    }

    /**
     * The mode button: the mod's button with a drawn sign in cream: in order (three lines and an arrow), shuffled (two
     * crossing arrows), one track again and again (a loop with a "1").
     */
    static BufferedImage modeButton(String mode) {
        BufferedImage img = Images.blank(18, 18);
        DerbyGuiArt.Canvas g = new DerbyGuiArt.Canvas(img, 0, 0, 1);
        DerbyGuiArt.button(g, 0, 0, 18, 18, true, false);
        int c = DerbyGuiArt.CREAM;
        switch (mode) {
            case "order" -> {
                Images.fill(img, 4, 5, 7, 1, c);
                Images.fill(img, 4, 8, 7, 1, c);
                Images.fill(img, 4, 11, 7, 1, c);
                // arrow pointing down on the right
                Images.fill(img, 13, 4, 1, 8, c);
                Images.fill(img, 12, 10, 3, 1, c);
                Images.fill(img, 11, 9, 1, 1, c);
                Images.fill(img, 15, 9, 1, 1, c);
                Images.fill(img, 13, 12, 1, 1, c);
            }
            case "shuffle" -> {
                for (int i = 0; i < 8; i++) {
                    Images.fill(img, 4 + i, 4 + i, 1, 1, c);
                    Images.fill(img, 4 + i, 11 - i, 1, 1, c);
                }
                // arrow heads at the right ends
                Images.fill(img, 10, 4, 3, 1, c);
                Images.fill(img, 12, 4, 1, 3, c);
                Images.fill(img, 10, 11, 3, 1, c);
                Images.fill(img, 12, 9, 1, 3, c);
            }
            default -> {
                // a loop: top and bottom lines, the sides, an arrow head on the top line, a "1" inside
                Images.fill(img, 5, 4, 8, 1, c);
                Images.fill(img, 5, 12, 8, 1, c);
                Images.fill(img, 4, 5, 1, 7, c);
                Images.fill(img, 13, 5, 1, 7, c);
                Images.fill(img, 10, 3, 1, 3, c);
                Images.fill(img, 11, 4, 1, 1, c);
                Images.fill(img, 8, 6, 1, 5, c);
                Images.fill(img, 7, 7, 1, 1, c);
            }
        }
        return img;
    }
}
