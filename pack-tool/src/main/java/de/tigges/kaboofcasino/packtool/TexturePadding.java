package de.tigges.kaboofcasino.packtool;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Pads atlas textures to multiples of 16 px and rescales the UVs of every model face that uses them.
 *
 * <p>Why: the 26.3 client lowers the mip level of a whole atlas to the largest power of two that divides every sprite
 * ({@code SpriteLoader}, render.md 1.5). One 304x40 texture would make every block in the world blurrier at a
 * distance. Padding with transparent pixels (right and bottom) keeps level 4; the UVs shrink by
 * {@code old / new} so the same texels are sampled.
 *
 * <p>Only textures in {@code textures/block} and {@code textures/item} are atlas sprites. Animated textures and
 * textures used as {@code item/generated} layers cannot be padded this way and fail the build.
 */
final class TexturePadding {

    /** One padded texture: id and the factors its UVs are multiplied with. */
    record Padded(String id, int oldWidth, int oldHeight, int newWidth, int newHeight) {
        double sx() {
            return (double) oldWidth / newWidth;
        }

        double sy() {
            return (double) oldHeight / newHeight;
        }
    }

    private TexturePadding() {
    }

    static int ceil16(int v) {
        return (v + 15) / 16 * 16;
    }

    /** Runs the pass; returns what was padded (sorted by id). */
    static List<Padded> apply(PackTree tree) {
        Map<String, Padded> padded = new TreeMap<>();
        for (String path : List.copyOf(tree.files().keySet())) {
            String id = atlasTextureId(path);
            if (id == null) {
                continue;
            }
            BufferedImage image = Images.read(tree.get(path));
            int w = image.getWidth();
            int h = image.getHeight();
            if (w % 16 == 0 && h % 16 == 0) {
                continue;
            }
            if (tree.has(path + ".mcmeta")) {
                throw new IllegalStateException("cannot pad the animated texture " + path + " (" + w + "x" + h + ")");
            }
            BufferedImage out = Images.blank(ceil16(w), ceil16(h));
            Images.copy(image, 0, 0, w, h, out, 0, 0);
            tree.replace(path, Images.write(out));
            padded.put(id, new Padded(id, w, h, out.getWidth(), out.getHeight()));
        }
        if (!padded.isEmpty()) {
            rescaleModels(tree, padded);
        }
        return List.copyOf(padded.values());
    }

    /** {@code assets/ns/textures/block/x.png} -> {@code ns:block/x}; null for non-atlas files. */
    static String atlasTextureId(String path) {
        if (!path.startsWith("assets/") || !path.endsWith(".png")) {
            return null;
        }
        String[] parts = path.split("/", 4);
        if (parts.length < 4 || !parts[2].equals("textures")) {
            return null;
        }
        String rest = parts[3];
        if (!rest.startsWith("block/") && !rest.startsWith("item/")) {
            return null;
        }
        return parts[1] + ":" + rest.substring(0, rest.length() - 4);
    }

    private static void rescaleModels(PackTree tree, Map<String, Padded> padded) {
        Map<String, JsonObject> models = new TreeMap<>();
        for (String path : tree.files().keySet()) {
            if (path.startsWith("assets/") && path.contains("/models/") && path.endsWith(".json")) {
                models.put(path, Json.parse(tree.get(path)));
            }
        }
        for (Map.Entry<String, JsonObject> e : models.entrySet()) {
            JsonObject model = e.getValue();
            Map<String, String> textures = textureMap(model, models);
            List<String> used = new ArrayList<>();
            for (Map.Entry<String, String> t : textures.entrySet()) {
                if (padded.containsKey(normalize(t.getValue()))) {
                    used.add(t.getKey());
                }
            }
            if (used.isEmpty()) {
                continue;
            }
            if (!model.has("elements")) {
                throw new IllegalStateException(e.getKey() + " uses padded textures " + used
                        + " through a parent's elements or as an item/generated layer; cannot rescale its UVs");
            }
            boolean changed = false;
            for (JsonElement el : model.getAsJsonArray("elements")) {
                JsonObject element = el.getAsJsonObject();
                JsonObject faces = element.getAsJsonObject("faces");
                for (Map.Entry<String, JsonElement> f : faces.entrySet()) {
                    JsonObject face = f.getValue().getAsJsonObject();
                    String ref = face.get("texture").getAsString();
                    String id = normalize(resolve(ref, textures));
                    Padded p = padded.get(id);
                    if (p == null) {
                        continue;
                    }
                    double[] uv = face.has("uv") ? toDoubles(face.getAsJsonArray("uv")) : defaultUv(element,
                            f.getKey());
                    face.add("uv", Json.array(uv[0] * p.sx(), uv[1] * p.sy(), uv[2] * p.sx(), uv[3] * p.sy()));
                    changed = true;
                }
            }
            if (changed) {
                tree.replace(e.getKey(), Json.bytes(model));
            }
        }
    }

    /** The model's texture variables including those of parents that are in the pack (child wins). */
    private static Map<String, String> textureMap(JsonObject model, Map<String, JsonObject> models) {
        Map<String, String> map = new HashMap<>();
        JsonObject current = model;
        int depth = 0;
        while (current != null && depth++ < 16) {
            if (current.has("textures")) {
                for (Map.Entry<String, JsonElement> t : current.getAsJsonObject("textures").entrySet()) {
                    map.putIfAbsent(t.getKey(), t.getValue().getAsString());
                }
            }
            if (!current.has("parent")) {
                break;
            }
            ModelCopier.Id parent = ModelCopier.Id.parse(current.get("parent").getAsString());
            current = models.get("assets/" + parent.namespace() + "/models/" + parent.path() + ".json");
        }
        return map;
    }

    private static String resolve(String ref, Map<String, String> textures) {
        String value = ref;
        for (int i = 0; i < 16 && value.startsWith("#"); i++) {
            value = textures.getOrDefault(value.substring(1), value);
        }
        return value;
    }

    private static String normalize(String id) {
        return ModelCopier.Id.parse(id).toString();
    }

    private static double[] toDoubles(JsonArray array) {
        double[] out = new double[array.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = array.get(i).getAsDouble();
        }
        return out;
    }

    /** The UVs the client derives from the element bounds when a face has none ({@code CuboidFace} defaults). */
    static double[] defaultUv(JsonObject element, String direction) {
        double[] f = toDoubles(element.getAsJsonArray("from"));
        double[] t = toDoubles(element.getAsJsonArray("to"));
        return switch (direction) {
            case "down" -> new double[] {f[0], 16 - t[2], t[0], 16 - f[2]};
            case "up" -> new double[] {f[0], f[2], t[0], t[2]};
            case "north" -> new double[] {16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]};
            case "south" -> new double[] {f[0], 16 - t[1], t[0], 16 - f[1]};
            case "west" -> new double[] {f[2], 16 - t[1], t[2], 16 - f[1]};
            case "east" -> new double[] {16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]};
            default -> throw new IllegalStateException("unknown face " + direction);
        };
    }
}
