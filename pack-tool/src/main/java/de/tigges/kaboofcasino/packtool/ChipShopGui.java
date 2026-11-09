package de.tigges.kaboofcasino.packtool;

import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * The chip shop's chest menu (PLAN-2 C6): the cashier's menu ({@link CashierGui}: the derby's felt panels around a 9x6
 * chest, the right panel as a receipt) with a row of category tabs. The title is the art and the receipt's texts at
 * the cashier's anchors; everything that changes is an item:
 * <pre>
 *  row 0     0-6 seven one-slot category tabs (the plugin's icon item in each), 7-8 felt
 *  rows 1-4  9-44 the items of a page (slot frames in the background)
 *  row 5     45/46 pages ({@code gui/cashier/prev}, {@code next}), 47 the quantity toggle
 *            ({@code gui/chipshop/qty_one}, {@code qty_stack}), 48-51 KAUFEN ({@code gui/cashier/buy_on} /
 *            {@code buy_off} in 49), 53 the player's chips ({@code gui/cashier/num_a}, row A), 52 the price
 *            ({@code gui/cashier/num_b}, row B)
 * </pre>
 * The background ({@code chipshop_bg_0}, {@code _1}) holds everything that never changes: the cashier's background
 * (felt, inventory frames, the {@link InventoryLabelPlate}, the receipt's divider), the seven tabs unselected and the
 * grid's frames. An overlay per tab ({@code chipshop_overlay_0..6}) draws that tab selected over it: lit fill,
 * a gold ring, all inside row 0. The texts sit where the cashier's do, so the cashier's number displays (drawn from
 * slots 52 and 53 into the receipt) line up unchanged.
 */
final class ChipShopGui {

    static final String PREFIX = "chipshop";
    static final int TABS = 7;
    static final String TEXTURE_DIR = "assets/kaboofcasino/textures/item/gui/chipshop/";
    static final String TEXTURE_ID = "kaboofcasino:item/gui/chipshop/";
    static final String MODEL_DIR = "assets/kaboofcasino/models/gui/chipshop/";
    static final String MODEL_ID = "kaboofcasino:gui/chipshop/";
    static final String ITEM_DIR = "assets/kaboofcasino/items/gui/chipshop/";

    // ---- slots (the plugin's menu uses the same) --------------------------------------------------------------------
    static final int FIRST_TAB = 0;
    static final int FIRST_ITEM = 9;
    static final int LAST_ITEM = 44;
    static final int QTY_SLOT = 47;
    /** The cashier's items this menu shows unchanged (all drawn from the same slots as in the cashier's shop). */
    static final List<String> REUSED = List.of("kaboofcasino:gui/cashier/prev", "kaboofcasino:gui/cashier/next",
            "kaboofcasino:gui/cashier/buy_on", "kaboofcasino:gui/cashier/buy_off", "kaboofcasino:gui/cashier/num_a",
            "kaboofcasino:gui/cashier/num_b");

    // ---- window geometry --------------------------------------------------------------------------------------------
    /** Tab {@code t} is the 18x18 button at ({@code TAB_X + 18 t}, {@code TAB_Y}), around slot t's item area. */
    static final int TAB_X = CashierGui.OVERLAY_X;
    static final int TAB_Y = CashierGui.OVERLAY_Y;
    static final int TAB_W = 18;
    /** The selected tab's overlay: the tab and a gold ring one pixel around it (still inside row 0's 18 rows + 1). */
    static final int OVERLAY_PAD = 1;
    /** The quantity toggle's labels. */
    static final String QTY_ONE = "1×";
    static final String QTY_STACK = "64";

    private ChipShopGui() {
    }

    static List<GuiFont.Anchor> anchors() {
        List<GuiFont.Anchor> out = new ArrayList<>();
        out.add(GuiFont.Anchor.text("chipshop_brand", CashierGui.RIGHT_CENTRE, CashierGui.BRAND_Y, "center"));
        out.add(GuiFont.Anchor.text("chipshop_head", CashierGui.RIGHT_CENTRE, CashierGui.HEAD_Y, "center"));
        for (int i = 0; i < CashierGui.HELP_LINES; i++) {
            out.add(GuiFont.Anchor.text("chipshop_help_" + i, CashierGui.TEXT_X, CashierGui.HELP_Y + 10 * i, "left"));
        }
        out.add(GuiFont.Anchor.text("chipshop_label_a", CashierGui.TEXT_X, CashierGui.LABEL_A_Y, "left"));
        out.add(GuiFont.Anchor.text("chipshop_label_b", CashierGui.TEXT_X, CashierGui.LABEL_B_Y, "left"));
        return out;
    }

    static void add(ModJar jar, PackTree tree, GuiFont font, TextGlyphs text) {
        BufferedImage bg = background(Images.read(jar.bytes("assets/roulettecasino/textures/gui/duck_derby_felt.png")),
                text);
        int tile = (bg.getWidth() + 1) / 2;
        for (int i = 0; i < 2; i++) {
            int w = Math.min(tile, bg.getWidth() - i * tile);
            font.art(PREFIX + "_bg_" + i, Images.crop(bg, i * tile, 0, w, bg.getHeight()), "font/chipshop/bg_" + i
                    + ".png", DerbyGuiArt.ART_X + i * tile, DerbyGuiArt.ART_Y);
        }
        // one picture for all six (the same texture file, one glyph per tab at its place)
        BufferedImage selected = overlay();
        for (int t = 0; t < TABS; t++) {
            font.art(PREFIX + "_overlay_" + t, selected, "font/chipshop/overlay.png", overlayX(t), TAB_Y - OVERLAY_PAD);
        }
        for (GuiFont.Anchor a : anchors()) {
            font.anchor(a);
        }
        qtyButtons(tree, text);
    }

    /** Window x of tab {@code t}'s button. */
    static int tabX(int t) {
        return TAB_X + t * TAB_W;
    }

    static int overlayX(int t) {
        return tabX(t) - OVERLAY_PAD;
    }

    // ---- the background ---------------------------------------------------------------------------------------------

    /**
     * The cashier's background (both felt panels, the inventory frames, the plate under "Inventar", the divider) with
     * row 0's seven tabs unselected and the frames of the item grid (9-44).
     */
    static BufferedImage background(BufferedImage feltSource, TextGlyphs text) {
        BufferedImage out = CashierGui.background(feltSource);
        DerbyGuiArt.Canvas g = new DerbyGuiArt.Canvas(out, DerbyGuiArt.ART_X, DerbyGuiArt.ART_Y, 1);
        for (int t = 0; t < TABS; t++) {
            DerbyGuiArt.button(g, tabX(t), TAB_Y, TAB_W, TAB_W, true, false);
        }
        for (int s = FIRST_ITEM; s <= LAST_ITEM; s++) {
            DerbyGuiArt.slot(g, DerbyGuiArt.slotX(s), DerbyGuiArt.slotY(s));
        }
        return out;
    }

    /** The selected tab: the mod's button lit (its hover fill) inside a gold ring one pixel wider all round. */
    static BufferedImage overlay() {
        int size = TAB_W + 2 * OVERLAY_PAD;
        BufferedImage img = Images.blank(size, size);
        DerbyGuiArt.Canvas g = new DerbyGuiArt.Canvas(img, 0, 0, 1);
        g.fill(0, 0, size, size, DerbyGuiArt.GOLD_LINE);
        g.fill(1, 1, size - 1, size - 1, -15719140);
        DerbyGuiArt.button(g, OVERLAY_PAD, OVERLAY_PAD, TAB_W, TAB_W, true, true);
        g.fill(OVERLAY_PAD, OVERLAY_PAD, OVERLAY_PAD + TAB_W, OVERLAY_PAD + 1, DerbyGuiArt.GOLD_LINE);
        g.fill(OVERLAY_PAD, OVERLAY_PAD + TAB_W - 1, OVERLAY_PAD + TAB_W, OVERLAY_PAD + TAB_W, DerbyGuiArt.GOLD_LINE);
        g.fill(OVERLAY_PAD, OVERLAY_PAD, OVERLAY_PAD + 1, OVERLAY_PAD + TAB_W, DerbyGuiArt.GOLD_LINE);
        g.fill(OVERLAY_PAD + TAB_W - 1, OVERLAY_PAD, OVERLAY_PAD + TAB_W, OVERLAY_PAD + TAB_W, DerbyGuiArt.GOLD_LINE);
        return img;
    }

    // ---- the items --------------------------------------------------------------------------------------------------

    static void texture(PackTree tree, String name, BufferedImage image) {
        tree.put(TEXTURE_DIR + name + ".png", Images.write(image));
    }

    static JsonObject plain(String name) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "minecraft:model");
        o.addProperty("model", MODEL_ID + name);
        return o;
    }

    /** One picture drawn at window (x, y) from {@code slot}: texture, model and item definition (oversized). */
    static void picture(PackTree tree, String name, BufferedImage image, int slot, int x, int y) {
        texture(tree, name, image);
        tree.put(MODEL_DIR + name + ".json", Json.bytes(DerbyGuiItems.model(slot, List.of(
                BlackjackGuiItems.Quad.of(TEXTURE_ID + name, x, y, image.getWidth(), image.getHeight())))));
        JsonObject definition = Json.object("model", plain(name));
        definition.addProperty("oversized_in_gui", true);
        tree.put(ITEM_DIR + name + ".json", Json.bytes(definition));
    }

    /**
     * The quantity toggle in slot 47: the mod's button (the cashier's page and clear buttons) with "1×" or "64" in
     * the mod's cream with shadow, centred in the button's fill (14 px; the cashier's buttons keep 4 px each side,
     * "1×" needs 12).
     */
    static BufferedImage qtyButton(TextGlyphs text, String label) {
        BufferedImage img = Images.blank(TAB_W, TAB_W);
        DerbyGuiArt.Canvas g = new DerbyGuiArt.Canvas(img, 0, 0, 1);
        DerbyGuiArt.button(g, 0, 0, TAB_W, TAB_W, true, false);
        int w = text.width(label);
        if (w > TAB_W - 4) {
            throw new IllegalStateException("'" + label + "' does not fit the quantity button");
        }
        DerbyGuiArt.text(g, text, label, TAB_W / 2 - w / 2, (TAB_W - 8) / 2, DerbyGuiArt.CREAM, false, true);
        return img;
    }

    static void qtyButtons(PackTree tree, TextGlyphs text) {
        int x = DerbyGuiArt.slotX(QTY_SLOT) - 1;
        int y = DerbyGuiArt.slotY(QTY_SLOT) - 1;
        picture(tree, "qty_one", qtyButton(text, QTY_ONE), QTY_SLOT, x, y);
        picture(tree, "qty_stack", qtyButton(text, QTY_STACK), QTY_SLOT, x, y);
    }
}
