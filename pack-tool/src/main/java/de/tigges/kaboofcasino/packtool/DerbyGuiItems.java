package de.tigges.kaboofcasino.packtool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * The derby GUI's item files below {@code gui/derby/}: flat quads drawn at fixed window positions from a fixed slot,
 * {@code oversized_in_gui} (the geometry of {@link BlackjackGuiItems}), plus a display translation so a picture can
 * lie far from its slot (the right panel, the help panel): GUI x = slot x + 8 + translation + scale (u - 8), GUI y =
 * slot y + 8 - translation - scale (v - 8), with element coordinates within -16..32, the scale 1, 2 or 4 and the
 * translation within the client's +-80 (ItemTransform clamps 5 blocks).
 */
final class DerbyGuiItems {

    static final String TEXTURE_DIR = "assets/kaboofcasino/textures/item/gui/derby/";
    static final String TEXTURE_ID = "kaboofcasino:item/gui/derby/";
    static final String MODEL_DIR = "assets/kaboofcasino/models/gui/derby/";
    static final String MODEL_ID = "kaboofcasino:gui/derby/";
    static final String ITEM_DIR = "assets/kaboofcasino/items/gui/derby/";
    static final int MAX_TRANSLATION = 80;

    private DerbyGuiItems() {
    }

    /** Scale and translation of one model. */
    record Placement(int scale, int tx, int ty) {
    }

    /**
     * The smallest scale (and a translation, 0 where possible) at which every quad of an item in {@code slot} fits the
     * element limits.
     */
    static Placement placement(int slot, List<BlackjackGuiItems.Quad> quads) {
        int sx = BlackjackGuiItems.slotX(slot);
        int sy = BlackjackGuiItems.slotY(slot);
        int x0 = Integer.MAX_VALUE;
        int y0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE;
        int y1 = Integer.MIN_VALUE;
        for (BlackjackGuiItems.Quad q : quads) {
            x0 = Math.min(x0, q.x());
            y0 = Math.min(y0, q.y());
            x1 = Math.max(x1, q.x() + q.w());
            y1 = Math.max(y1, q.y() + q.h());
        }
        for (int s : new int[] {1, 2, 4}) {
            // x range of the elements: [sx + 8 + tx - 24 s, sx + 8 + tx + 24 s]
            int txMin = x1 - sx - 8 - 24 * s;
            int txMax = x0 - sx - 8 + 24 * s;
            // y range: [sy + 8 - ty - 24 s, sy + 8 - ty + 24 s]
            int tyMin = sy + 8 - 24 * s - y0;
            int tyMax = sy + 8 + 24 * s - y1;
            if (txMin > txMax || tyMin > tyMax) {
                continue;
            }
            int tx = Math.max(txMin, Math.min(txMax, 0));
            int ty = Math.max(tyMin, Math.min(tyMax, 0));
            if (Math.abs(tx) <= MAX_TRANSLATION && Math.abs(ty) <= MAX_TRANSLATION) {
                return new Placement(s, tx, ty);
            }
        }
        throw new IllegalArgumentException("quads " + x0 + ".." + x1 + " x " + y0 + ".." + y1 + " out of reach of slot "
                + slot);
    }

    /** A model of flat quads (textures below {@code item/gui/derby/} unless namespaced) for an item in {@code slot}. */
    static JsonObject model(int slot, List<BlackjackGuiItems.Quad> quads) {
        Placement p = placement(slot, quads);
        int sx = BlackjackGuiItems.slotX(slot);
        int sy = BlackjackGuiItems.slotY(slot);
        int s = p.scale();
        JsonObject textures = new JsonObject();
        List<String> names = new ArrayList<>();
        JsonArray elements = new JsonArray();
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (BlackjackGuiItems.Quad q : quads) {
            int index = names.indexOf(q.texture());
            if (index < 0) {
                index = names.size();
                names.add(q.texture());
                textures.addProperty("t" + index, ref(q.texture()));
            }
            JsonObject face = new JsonObject();
            face.add("uv", Json.array(0, 0, 16, 16));
            face.addProperty("texture", "#t" + index);
            if (q.tint() >= 0) {
                face.addProperty("tintindex", q.tint());
            }
            double u0 = 8 + (q.x() - sx - 8 - p.tx()) / (double) s;
            double u1 = 8 + (q.x() + q.w() - sx - 8 - p.tx()) / (double) s;
            double vTop = 8 + (sy + 8 - p.ty() - q.y()) / (double) s;
            double vBottom = 8 + (sy + 8 - p.ty() - q.y() - q.h()) / (double) s;
            JsonObject element = new JsonObject();
            element.add("from", Json.array(u0, vBottom, q.z()));
            element.add("to", Json.array(u1, vTop, q.z()));
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
        textures.addProperty("particle", ref(names.get(0)));
        JsonObject gui = new JsonObject();
        gui.add("rotation", Json.array(0, 0, 0));
        gui.add("translation", Json.array(p.tx(), p.ty(), 0));
        gui.add("scale", Json.array(s, s, s));
        JsonObject model = new JsonObject();
        model.addProperty("gui_light", "front");
        model.add("textures", textures);
        model.add("elements", elements);
        model.add("display", Json.object("gui", gui));
        return model;
    }

    private static String ref(String name) {
        return name.indexOf(':') >= 0 ? name : TEXTURE_ID + name;
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

    /** {@code {"type":"minecraft:model","model":"kaboofcasino:gui/derby/<name>"}}. */
    static JsonObject plain(String modelName) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "minecraft:model");
        o.addProperty("model", MODEL_ID + modelName);
        return o;
    }

    /** A {@code select} on custom model data string {@code index}: one case per value, else nothing. */
    static JsonObject select(int index, List<String> values, List<JsonObject> models) {
        return PokerGuiItems.select(index, values, models);
    }

    static JsonObject composite(List<JsonObject> parts) {
        JsonArray models = new JsonArray();
        parts.forEach(models::add);
        JsonObject composite = new JsonObject();
        composite.addProperty("type", "minecraft:composite");
        composite.add("models", models);
        return composite;
    }

    /** Texture + model of one picture at window (x, y) from {@code slot}; returns the item model that shows it. */
    static JsonObject pictureModel(PackTree tree, String name, BufferedImage image, int slot, int x, int y) {
        texture(tree, name, image);
        model(tree, name, model(slot, List.of(BlackjackGuiItems.Quad.of(name, x, y, image.getWidth(),
                image.getHeight()))));
        return plain(name);
    }

    /** Texture + model + item definition of one picture drawn at window (x, y) from {@code slot}. */
    static void picture(PackTree tree, String name, BufferedImage image, int slot, int x, int y) {
        item(tree, name, pictureModel(tree, name, image, slot, x, y));
    }
}
