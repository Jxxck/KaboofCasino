package de.tigges.kaboofcasino.packtool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * The 3D cards (mod {@code BlackjackCardGeometry}): 53 item models, {@code kaboofcasino:blackjack/card_<id>} for the
 * card ids 0..51 and {@code kaboofcasino:blackjack/card_back} for a face-down hole card (id -1), one item definition
 * each (the rig swaps the {@code item_model} when the hole card is revealed, while it still lies face down).
 *
 * <p>A card is one element of 5 x 0.06 x 6.4 px around the model centre: the {@code up} face carries the cell of the
 * card (column = rank, row = suit, 25 x 32 px cells of the 350 x 128 sheet, inset 0.02 px), the {@code down} face the
 * back cell (column 13, row 0), so the card is two-sided like the mod's two quads 0.06 px apart. Model frame = the
 * mod's card frame at yaw -pi/2, flip 0: x = the card's width axis {@code u}, y = its normal, z = minus its length
 * axis; the cell's top-left texel sits at (-x, -z). The rig scales x and z by the pose's scale (the mod's thickness is
 * not scaled). The sheet is copied into the blocks atlas ({@code casino_entity/blackjack_cards}); the texture padding
 * pass widens it to 352 px and rescales these UVs (render.md 1.5).
 */
final class BlackjackCardModels {

    static final String SHEET = "roulettecasino:block/casino_entity/blackjack_cards";
    static final String SHEET_PATH = "assets/roulettecasino/textures/block/casino_entity/blackjack_cards.png";
    /** {@code BlackjackCardGeometry} constants. */
    static final int CELL_W = 25;
    static final int CELL_H = 32;
    static final int SHEET_W = 350;
    static final int SHEET_H = 128;
    static final int BACK_COLUMN = 13;
    static final double HALF_W = 2.5;
    static final double HALF_L = 3.2;
    static final double HALF_THICKNESS = 0.03;
    static final double INSET = 0.02;

    private BlackjackCardModels() {
    }

    /** {@code BlackjackCardGeometry.cellUv} in model UV units (0..16 across the 350 x 128 sheet). */
    static double[][] cellUv(int column, int row) {
        double u0 = (column * CELL_W + INSET) / SHEET_W * 16;
        double u1 = ((column + 1) * CELL_W - INSET) / SHEET_W * 16;
        double v0 = (row * CELL_H + INSET) / SHEET_H * 16;
        double v1 = ((row + 1) * CELL_H - INSET) / SHEET_H * 16;
        return new double[][] {{u0, v0}, {u0, v1}, {u1, v1}, {u1, v0}};
    }

    static String path(int card) {
        return card < 0 ? "blackjack/card_back" : "blackjack/card_" + card;
    }

    /** The card element: the mod's corners {(-w, l), (-w, -l), (w, -l), (w, l)} (u, v) at y = +-0.03. */
    static JsonArray elements(int card) {
        double[] from = {8 - HALF_W, 8 - HALF_THICKNESS, 8 - HALF_L};
        double[] to = {8 + HALF_W, 8 + HALF_THICKNESS, 8 + HALF_L};
        double[][] corner = {{-HALF_W, HALF_L}, {-HALF_W, -HALF_L}, {HALF_W, -HALF_L}, {HALF_W, HALF_L}};
        double[][] faceUv = card < 0 ? cellUv(BACK_COLUMN, 0) : cellUv(card % 13, card / 13);
        double[][] backUv = cellUv(BACK_COLUMN, 0);
        JsonObject faces = new JsonObject();
        for (int side = 0; side < 2; side++) {
            double y = 8 + (side == 0 ? HALF_THICKNESS : -HALF_THICKNESS);
            double[][] corners = new double[4][];
            for (int i = 0; i < 4; i++) {
                // model x = u component, model z = -(v component)
                corners[i] = new double[] {8 + corner[i][0], y, 8 - corner[i][1]};
            }
            BlackjackFaces.Face face = BlackjackFaces.solve(from, to, corners, side == 0 ? faceUv : backUv);
            faces.add(face.direction(), face.json("#sheet"));
        }
        JsonObject element = new JsonObject();
        element.add("from", Json.array(from));
        element.add("to", Json.array(to));
        element.add("faces", faces);
        JsonArray elements = new JsonArray();
        elements.add(element);
        return elements;
    }

    static void write(ModJar jar, PackTree tree) {
        tree.put(SHEET_PATH, jar.bytes("assets/roulettecasino/textures/entity/blackjack_cards.png"));
        for (int card = -1; card < 52; card++) {
            Models.write(tree, path(card), SHEET, elements(card), true);
        }
    }
}
