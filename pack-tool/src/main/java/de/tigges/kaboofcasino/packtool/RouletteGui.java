package de.tigges.kaboofcasino.packtool;

import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;

/**
 * The roulette's chest GUI (render.md 5.2; mod {@code RouletteScreen}): the mod's art re-composed by
 * {@link MachineGuiArt} (payout panel minus columns 274..288 between the chips and the centred texts, "ROULETTE"
 * re-centred). The nine bet frames (20 px, pitch 56 x 20) move by (+1 / -1 / -3, +4 / +2 / 0) px onto grid columns
 * 1 / 4 / 7, rows 2..4 (neighbouring frames share their border). Texts are anchors: the header
 * ({@code gui.roulettecasino.header_top}), the colour labels over the columns and the payout rows (GREEN, RED, BLACK:
 * colour name and odds, centred). The veil of the colours not chosen is a translucent item (the mod's fill colour
 * {@code 0xB4313B36}), because a title cannot change while the menu is open: per veiled column one
 * {@code oversized_in_gui} item (44 x 106 px at GUI scale 4 over the column's middle bet slot) with the mod's column
 * fill, its {@code roulette_chip_veil.png} over the chip and the colour label redrawn in the muted colour.
 */
final class RouletteGui {

    static final MachineGuiArt ART = new MachineGuiArt(new int[][] {{274, 15}});
    static final int[] FRAME_DX = {1, -1, -3};
    static final int[] FRAME_DY = {4, 2, 0};
    static final int VEIL = 0xB4313B36;
    /** {@code RouletteScreen.veiled(-792641)}: the label colour blended toward the veil by 180/255. */
    static final int MUTED = 0xFF6B6E5F;
    static final String[] LABELS = {"ROT", "SCHWARZ", "GRÜN"};
    /**
     * Window rectangle of a veil: x from {@link #veilX}, y 5..107 (the mod's chip veil and column fill reach mod
     * 25..131; here the fill ends with the last bet frame because the button moved up to window 109).
     */
    static final int VEIL_W = 44;
    static final int VEIL_TOP = 5;
    static final int VEIL_H = 103;

    static int veilX(int colour) {
        return ART.windowX(34 + 56 * colour) + FRAME_DX[colour];
    }

    private RouletteGui() {
    }

    static java.util.List<GuiFont.Anchor> anchors() {
        java.util.List<GuiFont.Anchor> out = new java.util.ArrayList<>();
        out.add(GuiFont.Anchor.text("roulette_headline", ART.windowX(112), ART.windowY(12), "center"));
        out.add(GuiFont.Anchor.text("roulette_payouts", (ART.panelFrom() + ART.panelTo()) / 2 + MachineGuiArt.ART_X,
                ART.panelWindowY(14), "center"));
        for (int c = 0; c < 3; c++) {
            out.add(GuiFont.Anchor.text("roulette_colour_" + c, ART.windowX(56 + 56 * c), ART.windowY(55), "center"));
        }
        for (int row = 0; row < 3; row++) {
            int y = 44 + 53 * row;
            out.add(GuiFont.Anchor.text("roulette_payout_label_" + row, ART.windowX(315), ART.panelWindowY(y + 8),
                    "center"));
            out.add(GuiFont.Anchor.text("roulette_multiplier_" + row, ART.windowX(315), ART.panelWindowY(y + 27),
                    "center"));
        }
        return out;
    }

    static void add(ModJar jar, PackTree tree, GuiFont font, TextGlyphs text) {
        BufferedImage art = compose(Images.read(jar.bytes("assets/roulettecasino/textures/gui/roulette.png")));
        MachineGuiArt.tiles(font, "roulette", art);
        for (GuiFont.Anchor a : anchors()) {
            font.anchor(a);
        }
        SlotMachineGui.button(tree, text, "roulette_on", "DREHEN", true, 14, 4.5, 11.5);
        SlotMachineGui.button(tree, text, "roulette_off", "DREHEN", false, 14, 4.5, 11.5);
        // the floating panel's colour header bar (RouletteBetRenderer: x + 2 .. x + 56): white, tinted by the text
        // colour; ascent 9 so it spans the text line from 2 px above the caps
        BufferedImage bar = Images.blank(54, 11);
        Images.fill(bar, 0, 0, 54, 11, 0xFFFFFFFF);
        font.art("panel_bar", bar, "font/panel/bar.png", 0, 4);
        BufferedImage chipVeil = Images.read(jar.bytes("assets/roulettecasino/textures/gui/roulette_chip_veil.png"));
        for (int c = 0; c < 3; c++) {
            veil(tree, text, c, chipVeil);
        }
    }

    /**
     * The veil of colour column {@code c}: the column fill (mod 34 + 56c, 65..131), the chip veil (mod 41 + 56c, 25)
     * and the label in the muted colour, as one translucent item over grid slot (column, row 3).
     */
    static void veil(PackTree tree, TextGlyphs text, int c, BufferedImage chipVeil) {
        int x0 = veilX(c);
        BufferedImage img = Images.blank(VEIL_W, VEIL_H);
        int fillTop = ART.windowY(65) - VEIL_TOP;
        Images.fill(img, 0, fillTop, VEIL_W, VEIL_H - fillTop, VEIL);
        int chipX = ART.windowX(41 + 56 * c) - x0;
        int chipY = ART.windowY(25) - VEIL_TOP;
        for (int y = 0; y < chipVeil.getHeight(); y++) {
            for (int x = 0; x < chipVeil.getWidth(); x++) {
                int p = chipVeil.getRGB(x, y);
                if ((p >>> 24) != 0 && chipX + x >= 0 && chipX + x < VEIL_W) {
                    img.setRGB(chipX + x, chipY + y, p);
                }
            }
        }
        // the label exactly where the title draws it (centred at the anchor), in the muted colour
        int width = text.width(LABELS[c]);
        int labelX = ART.windowX(56 + 56 * c) - width / 2 - x0;
        int labelY = ART.windowY(55) - VEIL_TOP;
        text.draw(img, LABELS[c], labelX, labelY, MUTED, false);
        String name = "roulette_veil_" + c;
        tree.put("assets/kaboofcasino/textures/item/gui/" + name + ".png", Images.write(img));
        // GUI scale 4 about the slot item centre: unit u -> px 8 + 4 (u - 8) from the item area's left/top
        int slotLeft = 8 + 18 * new int[] {1, 4, 7}[c];
        int slotTop = 18 + 18 * 3;
        double u0 = 8 + (x0 - slotLeft - 8) / 4.0;
        double vTop = 8 + (slotTop + 8 - VEIL_TOP) / 4.0;
        double vBottom = vTop - VEIL_H / 4.0;
        com.google.gson.JsonObject face = new com.google.gson.JsonObject();
        face.add("uv", Json.array(0, 0, 16, 16));
        face.addProperty("texture", "#t");
        com.google.gson.JsonObject element = new com.google.gson.JsonObject();
        element.add("from", Json.array(u0, vBottom, 8));
        element.add("to", Json.array(u0 + VEIL_W / 4.0, vTop, 8));
        element.add("faces", Json.object("south", face));
        com.google.gson.JsonArray elements = new com.google.gson.JsonArray();
        elements.add(element);
        com.google.gson.JsonObject textures = new com.google.gson.JsonObject();
        textures.addProperty("t", "kaboofcasino:item/gui/" + name);
        textures.addProperty("particle", "kaboofcasino:item/gui/" + name);
        com.google.gson.JsonObject gui = new com.google.gson.JsonObject();
        gui.add("rotation", Json.array(0, 0, 0));
        gui.add("translation", Json.array(0, 0, 0));
        gui.add("scale", Json.array(4, 4, 4));
        com.google.gson.JsonObject model = new com.google.gson.JsonObject();
        model.addProperty("gui_light", "front");
        model.add("textures", textures);
        model.add("elements", elements);
        model.add("display", Json.object("gui", gui));
        tree.put("assets/kaboofcasino/models/gui/" + name + ".json", Json.bytes(model));
        com.google.gson.JsonObject definition = Json.itemDefinition("kaboofcasino:gui/" + name);
        definition.addProperty("oversized_in_gui", true);
        tree.put("assets/kaboofcasino/items/gui/" + name + ".json", Json.bytes(definition));
    }

    static BufferedImage compose(BufferedImage source) {
        BufferedImage mod = Images.argb(source);
        BufferedImage original = Images.crop(mod, 0, 0, mod.getWidth(), mod.getHeight());
        MachineGuiArt.felt(mod, 40, 66, 144, 64, 130, 20);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                Images.copy(original, 46 + 56 * c, 68 + 20 * r, 20, 20, mod, 46 + 56 * c + FRAME_DX[c],
                        68 + 20 * r + FRAME_DY[r]);
            }
        }
        return ART.compose(mod, Lettering.find(mod));
    }
}
