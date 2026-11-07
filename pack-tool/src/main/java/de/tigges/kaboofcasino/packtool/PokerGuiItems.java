package de.tigges.kaboofcasino.packtool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * The poker GUI's item files below {@code gui/poker/}: the geometry and the model JSON are {@link BlackjackGuiItems}'
 * (flat quads drawn at fixed window positions from a fixed slot, {@code oversized_in_gui}); textures of the blackjack
 * GUI (card faces, the clear quad, the plain result-label digits) are referenced by their full id instead of copied.
 */
final class PokerGuiItems {

    static final String TEXTURE_DIR = "assets/kaboofcasino/textures/item/gui/poker/";
    static final String TEXTURE_ID = "kaboofcasino:item/gui/poker/";
    static final String MODEL_DIR = "assets/kaboofcasino/models/gui/poker/";
    static final String MODEL_ID = "kaboofcasino:gui/poker/";
    static final String ITEM_DIR = "assets/kaboofcasino/items/gui/poker/";
    /** Textures the blackjack GUI already ships. */
    static final String BLACKJACK = BlackjackGuiItems.TEXTURE_ID;

    private PokerGuiItems() {
    }

    static JsonObject model(int slot, List<BlackjackGuiItems.Quad> quads) {
        return BlackjackGuiItems.model(TEXTURE_ID, slot, quads);
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

    /** {@code {"type":"minecraft:model","model":"kaboofcasino:gui/poker/<name>"}} (+ tints from custom model data). */
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

    /** A {@code select} on custom model data string {@code index}: one case per value, else nothing. */
    static JsonObject select(int index, List<String> values, List<JsonObject> models) {
        JsonArray cases = new JsonArray();
        for (int i = 0; i < values.size(); i++) {
            JsonObject when = new JsonObject();
            when.addProperty("when", values.get(i));
            when.add("model", models.get(i));
            cases.add(when);
        }
        JsonObject select = new JsonObject();
        select.addProperty("type", "minecraft:select");
        select.addProperty("property", "minecraft:custom_model_data");
        select.addProperty("index", index);
        select.add("cases", cases);
        select.add("fallback", BlackjackGuiItems.empty());
        return select;
    }

    /** Texture + model + item definition of one picture drawn at window (x, y) from {@code slot}. */
    static void picture(PackTree tree, String name, BufferedImage image, int slot, int x, int y) {
        texture(tree, name, image);
        model(tree, name, model(slot, List.of(BlackjackGuiItems.Quad.of(name, x, y, image.getWidth(),
                image.getHeight()))));
        item(tree, name, plain(name, 0));
    }
}
