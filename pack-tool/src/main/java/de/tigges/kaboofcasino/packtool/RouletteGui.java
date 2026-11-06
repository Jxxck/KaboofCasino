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
 * {@code 0xB4313B36}), because a title cannot change while the menu is open.
 */
final class RouletteGui {

    static final MachineGuiArt ART = new MachineGuiArt(new int[][] {{274, 15}});
    static final int[] FRAME_DX = {1, -1, -3};
    static final int[] FRAME_DY = {4, 2, 0};
    static final int VEIL = 0xB4313B36;

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
        BufferedImage veil = Images.blank(16, 16);
        Images.fill(veil, 0, 0, 16, 16, VEIL);
        tree.put("assets/kaboofcasino/textures/item/gui/roulette_veil.png", Images.write(veil));
        JsonObject model = new JsonObject();
        model.addProperty("parent", "minecraft:item/generated");
        model.add("textures", Json.object("layer0", new com.google.gson.JsonPrimitive(
                "kaboofcasino:item/gui/roulette_veil")));
        tree.put("assets/kaboofcasino/models/gui/roulette_veil.json", Json.bytes(model));
        tree.put("assets/kaboofcasino/items/gui/roulette_veil.json",
                Json.bytes(Json.itemDefinition("kaboofcasino:gui/roulette_veil")));
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
