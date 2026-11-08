package de.tigges.kaboofcasino.packtool;

import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * The cashier's chest menu (PLAN-2 C3, no MineVegas screen of its own): the duck derby's felt panels and drawing
 * ({@link DerbyGuiArt}) around a 9x6 chest, three tabs in row 0, the right panel as a receipt. The title is the art
 * (background, one overlay per tab with its tab bar, slot frames and labels) and the right panel's texts at anchors;
 * everything that changes is an {@code oversized_in_gui} item below {@code gui/cashier/} drawn from a fixed slot of
 * {@code CashierMenu}:
 * <pre>
 *  row 0     0-2 Wechseln, 3-5 Kleingeld, 6-8 Preise (tab bar in the title)
 *  Wechseln  9-26 the selected ores, 30-35 the chips the exchange gives (1000 ... 1), 45 rates, 46 clear,
 *            48-51 WECHSELN (art in 49), 53 the value (right panel, row A)
 *  Kleingeld 12-17 the selected chips (1000 ... 1), 20 the marker of the target (frame on 21-26), 21-26 the target
 *            denominations, 30-35 the result, 46 clear, 48-51 WECHSELN, 53 the value
 *  Preise    9-44 the prizes of a page, 45/46 pages, 48-51 KAUFEN, 53 the player's chips (row A), 52 the price (B)
 * </pre>
 * Numbers are drawn right-aligned in ten cells of 6 px (a 2 px gap after every third digit), each cell a
 * {@code select} on its custom model data string ("0".."9", anything else blank).
 */
final class CashierGui {

    static final String PREFIX = "cashier";
    static final String[] TABS = {"exchange", "change", "shop"};
    static final String[] TAB_LABELS = {"Wechseln", "Kleingeld", "Preise"};
    static final String TEXTURE_DIR = "assets/kaboofcasino/textures/item/gui/cashier/";
    static final String TEXTURE_ID = "kaboofcasino:item/gui/cashier/";
    static final String MODEL_DIR = "assets/kaboofcasino/models/gui/cashier/";
    static final String MODEL_ID = "kaboofcasino:gui/cashier/";
    static final String ITEM_DIR = "assets/kaboofcasino/items/gui/cashier/";

    // ---- slots (CashierMenu uses the same) --------------------------------------------------------------------------
    static final int CLEAR_SLOT = 46;
    static final int PREV_SLOT = 45;
    static final int NEXT_SLOT = 46;
    static final int[] COMMIT_SLOTS = {48, 49, 50, 51};
    static final int COMMIT_ART = 49;
    static final int NUM_A_SLOT = 53;
    static final int NUM_B_SLOT = 52;
    static final int MARKER_SLOT = 20;
    /** Change: the target denominations 1000 ... 1 in slots 21..26. */
    static final int FIRST_TARGET = 21;

    // ---- window geometry --------------------------------------------------------------------------------------------
    /** The overlay: tab bar and frames over the chest grid. */
    static final int OVERLAY_X = 7;
    static final int OVERLAY_Y = 17;
    static final int OVERLAY_W = 162;
    static final int OVERLAY_H = 108;
    static final int TAB_W = 54;
    static final int TAB_H = 18;
    static final int COMMIT_X = 61;
    static final int COMMIT_Y = 107;
    static final int COMMIT_W = 72;
    static final int COMMIT_H = 18;
    /** The right panel's receipt. */
    static final int RIGHT_CENTRE = DerbyGuiArt.RIGHT_CENTRE;
    static final int TEXT_X = DerbyGuiArt.RIGHT_X + 7;
    static final int BRAND_Y = -8;
    static final int DIVIDER_Y = 4;
    static final int HEAD_Y = 10;
    static final int HELP_Y = 24;
    static final int HELP_LINES = 5;
    static final int LABEL_A_Y = 80;
    static final int NUM_A_Y = 92;
    static final int LABEL_B_Y = 106;
    static final int NUM_B_Y = 118;
    static final int NUM_RIGHT = DerbyGuiArt.RIGHT_X + DerbyGuiArt.RIGHT_W - 8;
    static final int NUM_CELLS = 10;
    static final int NUM_H = 9;

    private CashierGui() {
    }

    /** Window x of digit cell {@code k} (0 = the rightmost). */
    static int cellX(int k) {
        return NUM_RIGHT - 6 * (k + 1) - 2 * (k / 3);
    }

    /** The left edge of the widest number. */
    static int numLeft() {
        return cellX(NUM_CELLS - 1);
    }

    static List<GuiFont.Anchor> anchors() {
        List<GuiFont.Anchor> out = new ArrayList<>();
        out.add(GuiFont.Anchor.text("cashier_brand", RIGHT_CENTRE, BRAND_Y, "center"));
        out.add(GuiFont.Anchor.text("cashier_head", RIGHT_CENTRE, HEAD_Y, "center"));
        for (int i = 0; i < HELP_LINES; i++) {
            out.add(GuiFont.Anchor.text("cashier_help_" + i, TEXT_X, HELP_Y + 10 * i, "left"));
        }
        out.add(GuiFont.Anchor.text("cashier_label_a", TEXT_X, LABEL_A_Y, "left"));
        out.add(GuiFont.Anchor.text("cashier_label_b", TEXT_X, LABEL_B_Y, "left"));
        return out;
    }

    static void add(ModJar jar, PackTree tree, GuiFont font, TextGlyphs text) {
        BufferedImage bg = background(Images.read(jar.bytes("assets/roulettecasino/textures/gui/duck_derby_felt.png")));
        int tile = (bg.getWidth() + 1) / 2;
        for (int i = 0; i < 2; i++) {
            int w = Math.min(tile, bg.getWidth() - i * tile);
            font.art(PREFIX + "_bg_" + i, Images.crop(bg, i * tile, 0, w, bg.getHeight()), "font/cashier/bg_" + i
                    + ".png", DerbyGuiArt.ART_X + i * tile, DerbyGuiArt.ART_Y);
        }
        for (int tab = 0; tab < TABS.length; tab++) {
            font.art(PREFIX + "_overlay_" + TABS[tab], overlay(text, tab), "font/cashier/overlay_" + TABS[tab] + ".png",
                    OVERLAY_X, OVERLAY_Y);
        }
        for (GuiFont.Anchor a : anchors()) {
            font.anchor(a);
        }
        buttons(tree, text);
        numbers(tree, text);
        marker(tree);
    }

    // ---- the background ---------------------------------------------------------------------------------------------

    private static int[] kept(int from, int to, int[] cut) {
        List<Integer> out = new ArrayList<>();
        for (int i = from; i < to; i++) {
            if (i < cut[0] || i >= cut[0] + cut[1]) {
                out.add(i);
            }
        }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Both felt panels exactly as the derby cuts them (the left one with its gold box around the chest grid), the
     * player inventory's slot frames and the receipt's divider; the grid's frames come with each tab's overlay.
     */
    static BufferedImage background(BufferedImage feltSource) {
        BufferedImage felt = Images.argb(feltSource);
        if (felt.getWidth() != 366 || felt.getHeight() != 266) {
            throw new IllegalStateException("unexpected felt size " + felt.getWidth() + "x" + felt.getHeight());
        }
        int[] rows = DerbyGuiArt.keptRows();
        int[] left = kept(0, 224, DerbyGuiArt.LEFT_CUT);
        int[] right = kept(232, 366, DerbyGuiArt.RIGHT_CUT);
        BufferedImage out = Images.blank(DerbyGuiArt.ART_W, DerbyGuiArt.ART_H);
        for (int r = 0; r < rows.length; r++) {
            for (int c = 0; c < left.length; c++) {
                out.setRGB(DerbyGuiArt.LEFT_X - DerbyGuiArt.ART_X + c, r, felt.getRGB(left[c], rows[r]));
            }
            for (int c = 0; c < right.length; c++) {
                out.setRGB(DerbyGuiArt.RIGHT_X - DerbyGuiArt.ART_X + c, r, felt.getRGB(right[c], rows[r]));
            }
        }
        DerbyGuiArt.Canvas g = new DerbyGuiArt.Canvas(out, DerbyGuiArt.ART_X, DerbyGuiArt.ART_Y, 1);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                DerbyGuiArt.slot(g, 8 + col * 18, 140 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            DerbyGuiArt.slot(g, 8 + col * 18, 198);
        }
        g.fill(DerbyGuiArt.RIGHT_X + 8, DIVIDER_Y, DerbyGuiArt.RIGHT_X + DerbyGuiArt.RIGHT_W - 8, DIVIDER_Y + 1,
                DerbyGuiArt.DIVIDER);
        return out;
    }

    /** The grid slots that get a frame in tab {@code tab}. */
    static List<Integer> framed(int tab) {
        List<Integer> out = new ArrayList<>();
        switch (tab) {
            case 0 -> {
                for (int s = 9; s <= 26; s++) {
                    out.add(s);
                }
                for (int s = 30; s <= 35; s++) {
                    out.add(s);
                }
                out.add(45);
            }
            case 1 -> {
                for (int row = 1; row <= 3; row++) {
                    for (int col = 3; col <= 8; col++) {
                        out.add(row * 9 + col);
                    }
                }
            }
            default -> {
                for (int s = 9; s <= 44; s++) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    /** The labels of a tab left of its rows: {text, row}. */
    static List<String[]> labels(int tab) {
        return switch (tab) {
            case 0 -> List.<String[]>of(new String[] {"Chips:", "3"});
            case 1 -> List.of(new String[] {"Auswahl", "1"}, new String[] {"Nennwert", "2"},
                    new String[] {"Ergebnis", "3"});
            default -> List.of();
        };
    }

    /** One tab's overlay: the tab bar with this tab active, the slot frames and the row labels. */
    static BufferedImage overlay(TextGlyphs text, int tab) {
        BufferedImage img = Images.blank(OVERLAY_W, OVERLAY_H);
        DerbyGuiArt.Canvas g = new DerbyGuiArt.Canvas(img, OVERLAY_X, OVERLAY_Y, 1);
        for (int t = 0; t < TABS.length; t++) {
            int x = OVERLAY_X + t * TAB_W;
            boolean active = t == tab;
            DerbyGuiArt.button(g, x, OVERLAY_Y, TAB_W, TAB_H, true, active);
            if (active) {
                g.fill(x + 2, OVERLAY_Y + TAB_H - 3, x + TAB_W - 2, OVERLAY_Y + TAB_H - 2, DerbyGuiArt.GOLD_LINE);
            }
            String label = TAB_LABELS[t];
            if (text.width(label) > TAB_W - 8) {
                throw new IllegalStateException("'" + label + "' does not fit a tab");
            }
            DerbyGuiArt.text(g, text, label, x + TAB_W / 2 - text.width(label) / 2, OVERLAY_Y + 5,
                    active ? DerbyGuiArt.CREAM : DerbyGuiArt.GREY, false, active);
        }
        for (int slot : framed(tab)) {
            DerbyGuiArt.slot(g, DerbyGuiArt.slotX(slot), DerbyGuiArt.slotY(slot));
        }
        for (String[] label : labels(tab)) {
            int row = Integer.parseInt(label[1]);
            if (text.width(label[0]) > 3 * 18 - 6) {
                throw new IllegalStateException("'" + label[0] + "' does not fit left of its row");
            }
            DerbyGuiArt.text(g, text, label[0], OVERLAY_X + 4, 18 + 18 * row + 4, DerbyGuiArt.CREAM, false, true);
        }
        return img;
    }

    // ---- the items --------------------------------------------------------------------------------------------------

    static void texture(PackTree tree, String name, BufferedImage image) {
        tree.put(TEXTURE_DIR + name + ".png", Images.write(image));
    }

    static void model(PackTree tree, String name, int slot, List<BlackjackGuiItems.Quad> quads) {
        tree.put(MODEL_DIR + name + ".json", Json.bytes(DerbyGuiItems.model(slot, quads)));
    }

    static void item(PackTree tree, String name, JsonObject itemModel) {
        JsonObject definition = Json.object("model", itemModel);
        definition.addProperty("oversized_in_gui", true);
        tree.put(ITEM_DIR + name + ".json", Json.bytes(definition));
    }

    static JsonObject plain(String name) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "minecraft:model");
        o.addProperty("model", MODEL_ID + name);
        return o;
    }

    /** One picture drawn at window (x, y) from {@code slot}: texture, model and item definition. */
    static void picture(PackTree tree, String name, BufferedImage image, int slot, int x, int y) {
        texture(tree, name, image);
        model(tree, name, slot, List.of(BlackjackGuiItems.Quad.of(TEXTURE_ID + name, x, y, image.getWidth(),
                image.getHeight())));
        item(tree, name, plain(name));
    }

    /** WECHSELN / KAUFEN (on, off), the clear button and the two page buttons. */
    static void buttons(PackTree tree, TextGlyphs text) {
        for (boolean on : new boolean[] {true, false}) {
            String state = on ? "_on" : "_off";
            picture(tree, "exchange" + state, DerbyGui.button(text, "WECHSELN", COMMIT_W, COMMIT_H, on), COMMIT_ART,
                    COMMIT_X, COMMIT_Y);
            picture(tree, "buy" + state, DerbyGui.button(text, "KAUFEN", COMMIT_W, COMMIT_H, on), COMMIT_ART,
                    COMMIT_X, COMMIT_Y);
        }
        picture(tree, "clear", DerbyGui.button(text, "×", 18, 18, true), CLEAR_SLOT,
                DerbyGuiArt.slotX(CLEAR_SLOT) - 1, DerbyGuiArt.slotY(CLEAR_SLOT) - 1);
        picture(tree, "prev", DerbyGui.button(text, "<", 18, 18, true), PREV_SLOT,
                DerbyGuiArt.slotX(PREV_SLOT) - 1, DerbyGuiArt.slotY(PREV_SLOT) - 1);
        picture(tree, "next", DerbyGui.button(text, ">", 18, 18, true), NEXT_SLOT,
                DerbyGuiArt.slotX(NEXT_SLOT) - 1, DerbyGuiArt.slotY(NEXT_SLOT) - 1);
    }

    /**
     * The two number displays ({@code num_a} from slot 53 on row A, {@code num_b} from slot 52 on row B): per cell a
     * select on custom model data string {@code k} whose digit model holds the digit and a transparent spacer quad
     * over the whole display (so every case is wider than a slot and drawn oversized).
     */
    static void numbers(PackTree tree, TextGlyphs text) {
        for (int d = 0; d < 10; d++) {
            BufferedImage img = Images.blank(6, NUM_H);
            BlackjackGuiText.draw(img, text, Integer.toString(d), 0, 0, DerbyGuiArt.CREAM, false, true);
            texture(tree, "digit_" + d, img);
        }
        int width = NUM_RIGHT - numLeft();
        texture(tree, "spacer", Images.blank(width, NUM_H)); // one texel per pixel like every picture
        for (String display : new String[] {"a", "b"}) {
            int slot = display.equals("a") ? NUM_A_SLOT : NUM_B_SLOT;
            int y = display.equals("a") ? NUM_A_Y : NUM_B_Y;
            List<JsonObject> cells = new ArrayList<>();
            for (int k = 0; k < NUM_CELLS; k++) {
                List<String> values = new ArrayList<>();
                List<JsonObject> models = new ArrayList<>();
                for (int d = 0; d < 10; d++) {
                    String name = "num_" + display + "_" + k + "_" + d;
                    model(tree, name, slot, List.of(
                            new BlackjackGuiItems.Quad(TEXTURE_ID + "spacer", numLeft(), y, width, NUM_H, -1, 7.9),
                            BlackjackGuiItems.Quad.of(TEXTURE_ID + "digit_" + d, cellX(k), y, 6, NUM_H)));
                    values.add(Integer.toString(d));
                    models.add(plain(name));
                }
                cells.add(DerbyGuiItems.select(k, values, models));
            }
            item(tree, "num_" + display, DerbyGuiItems.composite(cells));
        }
    }

    /** The gold frame around the chosen target denomination (Kleingeld), drawn from slot 20: string 0 = "0".."5". */
    static void marker(PackTree tree) {
        BufferedImage frame = Images.blank(18, 18);
        Images.fill(frame, 0, 0, 18, 1, DerbyGuiArt.GOLD_LINE);
        Images.fill(frame, 0, 17, 18, 1, DerbyGuiArt.GOLD_LINE);
        Images.fill(frame, 0, 0, 1, 18, DerbyGuiArt.GOLD_LINE);
        Images.fill(frame, 17, 0, 1, 18, DerbyGuiArt.GOLD_LINE);
        texture(tree, "marker", frame);
        List<String> values = new ArrayList<>();
        List<JsonObject> models = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            int slot = FIRST_TARGET + i;
            String name = "marker_" + i;
            model(tree, name, MARKER_SLOT, List.of(BlackjackGuiItems.Quad.of(TEXTURE_ID + "marker",
                    DerbyGuiArt.slotX(slot) - 1, DerbyGuiArt.slotY(slot) - 1, 18, 18)));
            values.add(Integer.toString(i));
            models.add(plain(name));
        }
        item(tree, "marker", DerbyGuiItems.select(0, values, models));
    }
}
