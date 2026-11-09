package de.tigges.kaboofcasino.packtool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import de.tigges.kaboofcasino.packtool.OutfitArt.Piece;
import de.tigges.kaboofcasino.packtool.OutfitArt.Style;
import java.util.List;
import java.util.Locale;

/**
 * The casino outfits' part of the pack (not in MineVegas). For each style of {@link OutfitArt}:
 * <ul>
 *   <li>the equipment asset {@code kaboofcasino:outfit_<style>} with its two armor textures (the worn jacket and shoes,
 *       the worn trousers or skirt);</li>
 *   <li>per piece an item definition {@code kaboofcasino:outfit/<style>_<piece>} with its 32x32 icon;</li>
 *   <li>the hat's definition selects by display context: its flat icon everywhere, the 3D model on the head.</li>
 * </ul>
 * A worn hat's model coordinates follow the client's head transform (centre 8,8,8 at the middle of the head, scale
 * 0.625, north is the face): the head's top is at y 14.4, its side at 1.6 and 14.4, with the skin's overlay at 0.8 and
 * 15.2, so anything around the head starts at 0.4 at the latest.
 */
final class OutfitPack {

    private static final int MAIN = 0;
    private static final int BAND = 1;
    private static final int BRIM = 2;

    private OutfitPack() {
    }

    static void add(PackTree tree) {
        for (Style s : OutfitArt.STYLES) {
            String asset = "outfit_" + s.id();
            tree.put("assets/kaboofcasino/equipment/" + asset + ".json", Json.bytes(equipment(asset)));
            tree.put("assets/kaboofcasino/textures/entity/equipment/humanoid/" + asset + ".png",
                    Images.write(OutfitArt.humanoid(s)));
            tree.put("assets/kaboofcasino/textures/entity/equipment/humanoid_leggings/" + asset + ".png",
                    Images.write(OutfitArt.leggings(s)));
            for (Piece p : Piece.values()) {
                String name = s.id() + "_" + p.name().toLowerCase(Locale.ROOT);
                tree.put("assets/kaboofcasino/textures/item/outfit/" + name + ".png",
                        Images.write(OutfitArt.icon(s, p)));
                JsonObject icon = new JsonObject();
                icon.addProperty("parent", "minecraft:item/generated");
                icon.add("textures", Json.object("layer0", new JsonPrimitive("kaboofcasino:item/outfit/" + name)));
                tree.put("assets/kaboofcasino/models/item/outfit/" + name + ".json", Json.bytes(icon));
                tree.put("assets/kaboofcasino/items/outfit/" + name + ".json", Json.bytes(p == Piece.HAT
                        ? hatDefinition(name) : Json.itemDefinition("kaboofcasino:item/outfit/" + name)));
            }
            String worn = s.id() + "_hat_worn";
            tree.put("assets/kaboofcasino/textures/item/outfit/" + worn + ".png", Images.write(OutfitArt.hatSheet(s)));
            tree.put("assets/kaboofcasino/models/item/outfit/" + worn + ".json", Json.bytes(hatModel(s, worn)));
        }
    }

    /** One texture for the humanoid layer (jacket, shoes) and one for the leggings layer, no tint. */
    static JsonObject equipment(String asset) {
        JsonObject layers = new JsonObject();
        for (String layer : List.of("humanoid", "humanoid_leggings")) {
            JsonArray list = new JsonArray();
            list.add(Json.object("texture", new JsonPrimitive("kaboofcasino:" + asset)));
            layers.add(layer, list);
        }
        return Json.object("layers", layers);
    }

    /** The flat icon, but the worn model in the display context {@code head}. */
    static JsonObject hatDefinition(String name) {
        JsonObject when = new JsonObject();
        when.addProperty("when", "head");
        when.add("model", model("kaboofcasino:item/outfit/" + name + "_worn"));
        JsonArray cases = new JsonArray();
        cases.add(when);
        JsonObject select = new JsonObject();
        select.addProperty("type", "minecraft:select");
        select.addProperty("property", "minecraft:display_context");
        select.add("cases", cases);
        select.add("fallback", model("kaboofcasino:item/outfit/" + name));
        return Json.object("model", select);
    }

    private static JsonObject model(String id) {
        JsonObject m = new JsonObject();
        m.addProperty("type", "minecraft:model");
        m.addProperty("model", id);
        return m;
    }

    /** An element whose six faces show one field of the hat's sheet (fields 8x8: 0 main, 1 band, 2 brim, 3 shade). */
    private static JsonObject box(double x0, double y0, double z0, double x1, double y1, double z1, int field) {
        int u = field % 2 * 8;
        int v = field / 2 * 8;
        JsonObject faces = new JsonObject();
        for (String side : List.of("north", "south", "east", "west", "up", "down")) {
            JsonObject face = new JsonObject();
            face.add("uv", Json.array(u, v, u + 8, v + 8));
            face.addProperty("texture", "#t");
            faces.add(side, face);
        }
        JsonObject e = new JsonObject();
        e.add("from", Json.array(x0, y0, z0));
        e.add("to", Json.array(x1, y1, z1));
        e.add("faces", faces);
        return e;
    }

    /** The four thin walls of a band round the head, from {@code y0} to {@code y1}. */
    private static void ring(JsonArray el, double y0, double y1, int field) {
        el.add(box(0.4, y0, 0.4, 15.6, y1, 0.75, field));
        el.add(box(0.4, y0, 15.25, 15.6, y1, 15.6, field));
        el.add(box(0.4, y0, 0.75, 0.75, y1, 15.25, field));
        el.add(box(15.25, y0, 0.75, 15.6, y1, 15.25, field));
    }

    static JsonObject hatModel(Style s, String texture) {
        JsonArray el = new JsonArray();
        switch (s.hat()) {
            case TOP_HAT -> {
                el.add(box(-2, 12.8, -2, 18, 13.6, 18, BRIM));
                el.add(box(0.4, 13.6, 0.4, 15.6, 25, 15.6, MAIN));
                el.add(box(0.2, 13.6, 0.2, 15.8, 15.6, 15.8, BAND));
            }
            case FEDORA -> {
                el.add(box(-2.4, 13.2, -2.4, 18.4, 13.8, 18.4, BRIM));
                el.add(box(0.4, 13.8, 0.4, 15.6, 18.2, 15.6, MAIN));
                el.add(box(1.4, 18.2, 1.4, 14.6, 20.4, 14.6, MAIN));
                el.add(box(0.2, 13.8, 0.2, 15.8, 15.6, 15.8, BAND));
            }
            case VISOR -> {
                ring(el, 11, 13, BAND);
                // the eyeshade over the eyes, its front edge tipped down
                JsonObject bill = box(1.6, 12.2, -5, 14.4, 12.5, 0.4, MAIN);
                bill.add("rotation", billRotation());
                el.add(bill);
                JsonObject rim = box(1.6, 12.15, -5, 14.4, 12.55, -4.6, BRIM);
                rim.add("rotation", billRotation());
                el.add(rim);
            }
            case TIARA -> {
                ring(el, 13.2, 14, MAIN);
                el.add(box(6.8, 14, 0.4, 9.2, 16.8, 0.75, MAIN));
                el.add(box(3.8, 14, 0.4, 5.4, 15.6, 0.75, MAIN));
                el.add(box(10.6, 14, 0.4, 12.2, 15.6, 0.75, MAIN));
                el.add(box(7.4, 14.6, 0.1, 8.6, 16, 0.4, BAND));
            }
        }
        JsonObject textures = new JsonObject();
        textures.addProperty("t", "kaboofcasino:item/outfit/" + texture);
        textures.addProperty("particle", "kaboofcasino:item/outfit/" + texture);
        JsonObject model = new JsonObject();
        model.add("textures", textures);
        model.add("elements", el);
        return model;
    }

    private static JsonObject billRotation() {
        JsonObject r = new JsonObject();
        r.add("origin", Json.array(8, 12.35, 0.4));
        r.addProperty("axis", "x");
        r.addProperty("angle", -22.5);
        return r;
    }
}
