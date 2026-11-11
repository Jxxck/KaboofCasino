package de.tigges.kaboofcasino.packtool;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Block shading for the stair and slab covers (Jack 2026-11-11: the black marble stairs and slabs had another colour
 * than the blocks). The 26.3 client shades a block face by its direction ({@code CardinalLighting.DEFAULT}: up 1.0,
 * down 0.5, north/south 0.8, east/west 0.6, baked into the terrain's vertex colours), but an item display by its normal
 * against the two level lights ({@code Lighting.DIFFUSE_LIGHT_0/1}, {@code light.glsl}: {@code 0.4 + 0.6 * (max(0,
 * l0.n) + max(0, l1.n))}, at most 1): up 1.0, down 0.4, north/south 0.74, east/west 0.50. Next to the full deco blocks
 * (real blocks) the covers' sides were up to a fifth darker.
 *
 * <p>Every cover face that does not look up therefore shows a copy of its texture brightened by block / display shade
 * ({@link #factor}); the covers stand unturned (stairs are baked per state, slabs have no turn), so a model face is the
 * world face. The faces name {@code #<role>_<side>} ({@link #faces}), each cover child maps those names to the copies
 * ({@link #textures}). Vanilla textures cannot be copied (the client has them, the pack does not): their faces keep the
 * plain texture (only the bottom of the classic dark oak stairs and slabs). In the Nether blocks are shaded 0.9 up and
 * down and the second light points down: the sides still match, the bottom comes out a little bright there. Pure apart
 * from the pack tree.
 */
final class DecoShade {

    /** {@code light.glsl}: MINECRAFT_LIGHT_POWER and MINECRAFT_AMBIENT_LIGHT. */
    static final double LIGHT_POWER = 0.6;
    static final double AMBIENT = 0.4;
    /** {@code Lighting.DIFFUSE_LIGHT_0} and {@code _1} before normalising (the overworld's and the End's). */
    static final double[] LIGHT_0 = {0.2, 1.0, -0.7};
    static final double[] LIGHT_1 = {-0.2, 1.0, 0.7};

    /** The texture roles of the covers ({@link DecoDisplayPack}). */
    static final List<String> ROLES = List.of("bottom", "top", "side", "half", "quarter");
    /** The side classes of the face directions that are not {@code up}. */
    static final List<String> SIDES = List.of("down", "ns", "ew");

    private DecoShade() {
    }

    /** {@code CardinalLighting.DEFAULT}: how bright the client draws a block face looking this way. */
    static double blockShade(String dir) {
        return switch (dir) {
            case "down" -> 0.5;
            case "up" -> 1.0;
            case "north", "south" -> 0.8;
            case "west", "east" -> 0.6;
            default -> throw new IllegalArgumentException(dir);
        };
    }

    /** How bright the client draws a display face with this normal ({@code minecraft_mix_light}). */
    static double displayShade(String dir) {
        double[] n = switch (dir) {
            case "down" -> new double[] {0, -1, 0};
            case "up" -> new double[] {0, 1, 0};
            case "north" -> new double[] {0, 0, -1};
            case "south" -> new double[] {0, 0, 1};
            case "west" -> new double[] {-1, 0, 0};
            case "east" -> new double[] {1, 0, 0};
            default -> throw new IllegalArgumentException(dir);
        };
        double light = Math.max(0, dot(LIGHT_0, n)) + Math.max(0, dot(LIGHT_1, n));
        return Math.min(1.0, light * LIGHT_POWER + AMBIENT);
    }

    private static double dot(double[] light, double[] n) {
        double length = Math.sqrt(light[0] * light[0] + light[1] * light[1] + light[2] * light[2]);
        return (light[0] * n[0] + light[1] * n[1] + light[2] * n[2]) / length;
    }

    /** The brightening of a side class: block shade / display shade (north and south, east and west are alike). */
    static double factor(String side) {
        String dir = switch (side) {
            case "down" -> "down";
            case "ns" -> "north";
            case "ew" -> "east";
            default -> throw new IllegalArgumentException(side);
        };
        return blockShade(dir) / displayShade(dir);
    }

    /** The side class of a face direction; null for {@code up} (shaded alike). */
    static String side(String dir) {
        return switch (dir) {
            case "up" -> null;
            case "down" -> "down";
            case "north", "south" -> "ns";
            case "west", "east" -> "ew";
            default -> throw new IllegalArgumentException(dir);
        };
    }

    /** A copy of {@code elements} whose faces that do not look up name {@code #<role>_<side>}. */
    static JsonArray faces(JsonArray elements) {
        JsonArray out = new JsonArray();
        for (JsonElement e : elements) {
            JsonObject el = e.getAsJsonObject().deepCopy();
            for (Map.Entry<String, JsonElement> f : el.getAsJsonObject("faces").entrySet()) {
                String side = side(f.getKey());
                JsonObject face = f.getValue().getAsJsonObject();
                String texture = face.get("texture").getAsString();
                if (side != null) {
                    if (!texture.startsWith("#") || !ROLES.contains(texture.substring(1))) {
                        throw new IllegalStateException("cover face with texture " + texture);
                    }
                    face.addProperty("texture", texture + "_" + side);
                }
            }
            out.add(el);
        }
        return out;
    }

    /** Every texture variable the elements' faces name (without {@code #}). */
    static Set<String> variables(JsonArray elements) {
        Set<String> out = new TreeSet<>();
        for (JsonElement e : elements) {
            for (Map.Entry<String, JsonElement> f : e.getAsJsonObject().getAsJsonObject("faces").entrySet()) {
                String texture = f.getValue().getAsJsonObject().get("texture").getAsString();
                if (texture.startsWith("#")) {
                    out.add(texture.substring(1));
                }
            }
        }
        return out;
    }

    /**
     * Adds to a cover child's {@code textures} every {@code <role>_<side>} its template names: the brightened copy of
     * the role's texture (made once per texture and side), or the role itself for a vanilla texture.
     */
    static void textures(PackTree tree, JsonObject textures, Set<String> wanted) {
        for (String name : wanted) {
            int cut = name.lastIndexOf('_');
            if (cut < 0 || !SIDES.contains(name.substring(cut + 1))) {
                continue; // a plain role (an up face)
            }
            String role = name.substring(0, cut);
            String side = name.substring(cut + 1);
            JsonElement given = textures.get(role);
            if (given == null || !given.isJsonPrimitive()) {
                throw new IllegalStateException("cover without texture " + role);
            }
            String id = given.getAsString();
            textures.addProperty(name, id.startsWith("#") || id.startsWith("minecraft:") ? "#" + role
                    : shaded(tree, id, side));
        }
    }

    /** For models that use a cover template unshaded (the own stairs' placing item): every shaded name is its role. */
    static void plain(JsonObject textures) {
        for (String role : ROLES) {
            if (textures.has(role)) {
                for (String side : SIDES) {
                    textures.addProperty(role + "_" + side, "#" + role);
                }
            }
        }
    }

    /**
     * {@code kaboofcasino:block/shade/<ns>/<path>_<side>} (a leading {@code block/} dropped): the texture brightened by
     * {@link #factor}, its {@code .mcmeta} kept. The texture must already be in the tree.
     */
    static String shaded(PackTree tree, String id, String side) {
        ModelCopier.Id ref = ModelCopier.Id.parse(id);
        String path = ref.path().startsWith("block/") ? ref.path().substring("block/".length()) : ref.path();
        String name = "block/shade/" + ref.namespace() + "/" + path + "_" + side;
        String target = "assets/kaboofcasino/textures/" + name + ".png";
        if (!tree.has(target)) {
            String source = "assets/" + ref.namespace() + "/textures/" + ref.path() + ".png";
            tree.put(target, Images.write(brighten(Images.read(tree.get(source)), factor(side))));
            if (tree.has(source + ".mcmeta")) {
                tree.put(target + ".mcmeta", tree.get(source + ".mcmeta"));
            }
        }
        return "kaboofcasino:" + name;
    }

    /** Every colour channel times {@code factor} (rounded, at most 255); alpha stays. */
    static BufferedImage brighten(BufferedImage image, double factor) {
        BufferedImage out = Images.blank(image.getWidth(), image.getHeight());
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int p = image.getRGB(x, y);
                int r = channel((p >> 16) & 255, factor);
                int g = channel((p >> 8) & 255, factor);
                int b = channel(p & 255, factor);
                out.setRGB(x, y, (p & 0xFF000000) | (r << 16) | (g << 8) | b);
            }
        }
        return out;
    }

    private static int channel(int value, double factor) {
        return Math.min(255, (int) Math.round(value * factor));
    }
}
