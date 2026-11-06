package de.tigges.kaboofcasino.packtool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Item models that draw a picture at a fixed window position while the item lies in a given top-inventory slot
 * (the dynamic parts of the blackjack screens: cards, totals, status line, timer bar, labels, buttons). Every model is
 * flat quads facing the viewer, lit "front" (full bright like a GUI blit), and the item definition sets
 * {@code oversized_in_gui}: the client then renders the model's whole bounding box (it must exceed 16 px in one
 * direction, else it is clipped to the slot) instead of the 16x16 slot cell ({@code GuiItemRenderState}).
 *
 * <p>Geometry: in the GUI an item model unit (u, v) of an item in a slot whose item area starts at window (sx, sy)
 * lands at window {@code (sx + 8 + (u - 8) s, sy + 8 - (v - 8) s)} for display scale {@code s}. Element coordinates
 * must stay within -16..32 (the client rejects others), so the smallest scale of 1, 2, 4 that fits is used; with
 * whole-pixel rectangles every texel lands on exactly one GUI pixel.
 */
final class BlackjackGuiItems {

    static final String TEXTURE_DIR = "assets/kaboofcasino/textures/item/gui/blackjack/";
    static final String TEXTURE_ID = "kaboofcasino:item/gui/blackjack/";
    static final String MODEL_DIR = "assets/kaboofcasino/models/gui/blackjack/";
    static final String MODEL_ID = "kaboofcasino:gui/blackjack/";
    static final String ITEM_DIR = "assets/kaboofcasino/items/gui/blackjack/";

    private BlackjackGuiItems() {
    }

    /** Window x of the item area of top-inventory slot {@code slot} (9 columns). */
    static int slotX(int slot) {
        return 8 + 18 * (slot % 9);
    }

    /** Window y of the item area of top-inventory slot {@code slot}. */
    static int slotY(int slot) {
        return 18 + 18 * (slot / 9);
    }

    /**
     * One textured quad at a window rectangle.
     *
     * @param texture texture name below {@code item/gui/blackjack/}
     * @param tint    tint index (-1 = none)
     * @param z       depth in model units (larger = in front)
     */
    record Quad(String texture, int x, int y, int w, int h, int tint, double z) {
        static Quad of(String texture, int x, int y, int w, int h) {
            return new Quad(texture, x, y, w, h, -1, 8);
        }
    }

    /** The smallest display scale (1, 2 or 4) at which every quad of an item in {@code slot} fits -16..32. */
    static int scaleFor(int slot, List<Quad> quads) {
        for (int s : new int[] {1, 2, 4}) {
            boolean fits = true;
            for (Quad q : quads) {
                double u0 = u(slot, q.x(), s);
                double u1 = u(slot, q.x() + q.w(), s);
                double vTop = v(slot, q.y(), s);
                double vBottom = v(slot, q.y() + q.h(), s);
                fits &= u0 >= -16 && u1 <= 32 && vBottom >= -16 && vTop <= 32;
            }
            if (fits) {
                return s;
            }
        }
        throw new IllegalArgumentException("quads too far from slot " + slot + ": " + quads);
    }

    static double u(int slot, int windowX, int scale) {
        return 8 + (windowX - slotX(slot) - 8) / (double) scale;
    }

    static double v(int slot, int windowY, int scale) {
        return 8 - (windowY - slotY(slot) - 8) / (double) scale;
    }

    /** A model of flat quads (all textures as variables {@code t0, t1, ...}) for an item in {@code slot}. */
    static JsonObject model(int slot, List<Quad> quads) {
        int s = scaleFor(slot, quads);
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        JsonObject textures = new JsonObject();
        java.util.List<String> names = new java.util.ArrayList<>();
        JsonArray elements = new JsonArray();
        for (Quad q : quads) {
            int index = names.indexOf(q.texture());
            if (index < 0) {
                index = names.size();
                names.add(q.texture());
                textures.addProperty("t" + index, TEXTURE_ID + q.texture());
            }
            JsonObject face = new JsonObject();
            face.add("uv", Json.array(0, 0, 16, 16));
            face.addProperty("texture", "#t" + index);
            if (q.tint() >= 0) {
                face.addProperty("tintindex", q.tint());
            }
            JsonObject element = new JsonObject();
            element.add("from", Json.array(u(slot, q.x(), s), v(slot, q.y() + q.h(), s), q.z()));
            element.add("to", Json.array(u(slot, q.x() + q.w(), s), v(slot, q.y(), s), q.z()));
            element.add("faces", Json.object("south", face));
            elements.add(element);
            minX = Math.min(minX, q.x());
            minY = Math.min(minY, q.y());
            maxX = Math.max(maxX, q.x() + q.w());
            maxY = Math.max(maxY, q.y() + q.h());
        }
        if (maxX - minX <= 16 && maxY - minY <= 16) {
            throw new IllegalArgumentException("slot " + slot + ": a model of at most 16x16 px is not drawn oversized");
        }
        textures.addProperty("particle", TEXTURE_ID + names.get(0));
        JsonObject gui = new JsonObject();
        gui.add("rotation", Json.array(0, 0, 0));
        gui.add("translation", Json.array(0, 0, 0));
        gui.add("scale", Json.array(s, s, s));
        JsonObject model = new JsonObject();
        model.addProperty("gui_light", "front");
        model.add("textures", textures);
        model.add("elements", elements);
        model.add("display", Json.object("gui", gui));
        return model;
    }

    static void texture(PackTree tree, String name, BufferedImage image) {
        tree.put(TEXTURE_DIR + name + ".png", Images.write(image));
    }

    static void model(PackTree tree, String name, JsonObject model) {
        tree.put(MODEL_DIR + name + ".json", Json.bytes(model));
    }

    /** An item definition ({@code oversized_in_gui}) with the given item model. */
    static void item(PackTree tree, String name, JsonObject itemModel) {
        JsonObject definition = Json.object("model", itemModel);
        definition.addProperty("oversized_in_gui", true);
        tree.put(ITEM_DIR + name + ".json", Json.bytes(definition));
    }

    /** {@code {"type":"minecraft:model","model":"kaboofcasino:gui/blackjack/<name>"}} (+ tints from custom model data). */
    static JsonObject plain(String modelName, int tints) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "minecraft:model");
        o.addProperty("model", MODEL_ID + modelName);
        if (tints > 0) {
            JsonArray list = new JsonArray();
            for (int i = 0; i < tints; i++) {
                JsonObject tint = new JsonObject();
                tint.addProperty("type", "minecraft:custom_model_data");
                tint.addProperty("index", i);
                tint.addProperty("default", 0xFFFFFF);
                list.add(tint);
            }
            o.add("tints", list);
        }
        return o;
    }

    static JsonObject empty() {
        return Json.object("type", new JsonPrimitive("minecraft:empty"));
    }

    /** Texture + model + item definition of one picture drawn at window (x, y) from {@code slot}. */
    static void picture(PackTree tree, String name, BufferedImage image, int slot, int x, int y) {
        texture(tree, name, image);
        model(tree, name, model(slot, List.of(Quad.of(name, x, y, image.getWidth(), image.getHeight()))));
        item(tree, name, plain(name, 0));
    }
}
