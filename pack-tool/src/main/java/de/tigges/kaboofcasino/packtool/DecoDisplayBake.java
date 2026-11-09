package de.tigges.kaboofcasino.packtool;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Map;

/**
 * Bakes blockstate rotations into model elements for displays, which can only turn a whole model and know nothing of
 * {@code uvlock}. The mod's stairs are vanilla stairs ({@code minecraft:block/stairs}, {@code inner_stairs},
 * {@code outer_stairs}, uv-locked when turned): a turned, uv-locked face shows the texture as a face at that place
 * would in an unturned model, i.e. with the default UVs of its turned bounds ({@code FaceBakery.defaultFaceUV}); every
 * face of the three vanilla templates uses exactly those UVs (checked by the tests), so baking = turning the bounds and
 * the face directions, then giving every face the default UVs of its new direction. Pure JSON.
 */
final class DecoDisplayBake {

    /**
     * The three vanilla 26.3 stair templates ({@code assets/minecraft/models/block/*.json} of the 26.3 client), the
     * geometry the mod's stair models inherit. The client has them; displays need the turned copies.
     */
    static final Map<String, String> TEMPLATES = Map.of(
            "minecraft:block/stairs",
            "[{\"from\":[0,0,0],\"to\":[16,8,16],\"faces\":{\"down\":{\"uv\":[0,0,16,16],\"texture\":\"#bottom\","
                    + "\"cullface\":\"down\"},\"up\":{\"uv\":[0,0,16,16],\"texture\":\"#top\"},\"north\":{\"uv\":"
                    + "[0,8,16,16],\"texture\":\"#side\",\"cullface\":\"north\"},\"south\":{\"uv\":[0,8,16,16],"
                    + "\"texture\":\"#side\",\"cullface\":\"south\"},\"west\":{\"uv\":[0,8,16,16],\"texture\":"
                    + "\"#side\",\"cullface\":\"west\"},\"east\":{\"uv\":[0,8,16,16],\"texture\":\"#side\","
                    + "\"cullface\":\"east\"}}},{\"from\":[8,8,0],\"to\":[16,16,16],\"faces\":{\"up\":{\"uv\":"
                    + "[8,0,16,16],\"texture\":\"#top\",\"cullface\":\"up\"},\"north\":{\"uv\":[0,0,8,8],\"texture\":"
                    + "\"#side\",\"cullface\":\"north\"},\"south\":{\"uv\":[8,0,16,8],\"texture\":\"#side\","
                    + "\"cullface\":\"south\"},\"west\":{\"uv\":[0,0,16,8],\"texture\":\"#side\"},\"east\":{\"uv\":"
                    + "[0,0,16,8],\"texture\":\"#side\",\"cullface\":\"east\"}}}]",
            "minecraft:block/inner_stairs",
            "[{\"from\":[0,0,0],\"to\":[16,8,16],\"faces\":{\"down\":{\"uv\":[0,0,16,16],\"texture\":\"#bottom\","
                    + "\"cullface\":\"down\"},\"up\":{\"uv\":[0,0,16,16],\"texture\":\"#top\"},\"north\":{\"uv\":"
                    + "[0,8,16,16],\"texture\":\"#side\",\"cullface\":\"north\"},\"south\":{\"uv\":[0,8,16,16],"
                    + "\"texture\":\"#side\",\"cullface\":\"south\"},\"west\":{\"uv\":[0,8,16,16],\"texture\":"
                    + "\"#side\",\"cullface\":\"west\"},\"east\":{\"uv\":[0,8,16,16],\"texture\":\"#side\","
                    + "\"cullface\":\"east\"}}},{\"from\":[8,8,0],\"to\":[16,16,16],\"faces\":{\"up\":{\"uv\":"
                    + "[8,0,16,16],\"texture\":\"#top\",\"cullface\":\"up\"},\"north\":{\"uv\":[0,0,8,8],\"texture\":"
                    + "\"#side\",\"cullface\":\"north\"},\"south\":{\"uv\":[8,0,16,8],\"texture\":\"#side\","
                    + "\"cullface\":\"south\"},\"west\":{\"uv\":[0,0,16,8],\"texture\":\"#side\"},\"east\":{\"uv\":"
                    + "[0,0,16,8],\"texture\":\"#side\",\"cullface\":\"east\"}}},{\"from\":[0,8,8],\"to\":[8,16,16],"
                    + "\"faces\":{\"up\":{\"uv\":[0,8,8,16],\"texture\":\"#top\",\"cullface\":\"up\"},\"north\":"
                    + "{\"uv\":[8,0,16,8],\"texture\":\"#side\"},\"south\":{\"uv\":[0,0,8,8],\"texture\":\"#side\","
                    + "\"cullface\":\"south\"},\"west\":{\"uv\":[8,0,16,8],\"texture\":\"#side\",\"cullface\":"
                    + "\"west\"}}}]",
            "minecraft:block/outer_stairs",
            "[{\"from\":[0,0,0],\"to\":[16,8,16],\"faces\":{\"down\":{\"uv\":[0,0,16,16],\"texture\":\"#bottom\","
                    + "\"cullface\":\"down\"},\"up\":{\"uv\":[0,0,16,16],\"texture\":\"#top\"},\"north\":{\"uv\":"
                    + "[0,8,16,16],\"texture\":\"#side\",\"cullface\":\"north\"},\"south\":{\"uv\":[0,8,16,16],"
                    + "\"texture\":\"#side\",\"cullface\":\"south\"},\"west\":{\"uv\":[0,8,16,16],\"texture\":"
                    + "\"#side\",\"cullface\":\"west\"},\"east\":{\"uv\":[0,8,16,16],\"texture\":\"#side\","
                    + "\"cullface\":\"east\"}}},{\"from\":[8,8,8],\"to\":[16,16,16],\"faces\":{\"up\":{\"uv\":"
                    + "[8,8,16,16],\"texture\":\"#top\",\"cullface\":\"up\"},\"north\":{\"uv\":[0,0,8,8],\"texture\":"
                    + "\"#side\"},\"south\":{\"uv\":[8,0,16,8],\"texture\":\"#side\",\"cullface\":\"south\"},\"west\":"
                    + "{\"uv\":[8,0,16,8],\"texture\":\"#side\"},\"east\":{\"uv\":[0,0,8,8],\"texture\":\"#side\","
                    + "\"cullface\":\"east\"}}}]");

    static final String[] DIRECTIONS = {"down", "up", "north", "south", "west", "east"};

    /**
     * How far a cover's faces lie outside the vanilla carrier's (px: 0.008 blocks): enough for the client's depth test
     * up to the display's view range (the smallest gap the 0.9.1 covers had, never seen flickering).
     */
    static final double COVER_OUT = 0.128;
    /** The box under the faces (px), half way out: it only closes the hairline along the edges. */
    static final double COVER_UNDER = COVER_OUT / 2;

    private DecoDisplayBake() {
    }

    static JsonArray template(String parent) {
        String json = TEMPLATES.get(parent);
        if (json == null) {
            throw new IllegalStateException("no stair template " + parent);
        }
        return com.google.gson.JsonParser.parseString(json).getAsJsonArray();
    }

    /**
     * A point (px) turned like a blockstate {@code x} then {@code y} (multiples of 90): {@code BlockModelRotation}
     * = {@code rotateY(-y) * rotateX(-x)} about the block centre.
     */
    static double[] turn(double[] p, int x, int y) {
        double px = p[0] - 8;
        double py = p[1] - 8;
        double pz = p[2] - 8;
        for (int i = 0; i < ((x / 90) & 3); i++) {
            // rotateX(-90): (y, z) -> (z, -y)
            double ny = pz;
            double nz = -py;
            py = ny;
            pz = nz;
        }
        for (int i = 0; i < ((y / 90) & 3); i++) {
            // rotateY(-90): (x, z) -> (-z, x)
            double nx = -pz;
            double nz = px;
            px = nx;
            pz = nz;
        }
        return new double[] {px + 8, py + 8, pz + 8};
    }

    /** The direction a face normal ends up in after the same turn. */
    static String turnDirection(String dir, int x, int y) {
        double[] n = switch (dir) {
            case "down" -> new double[] {0, -1, 0};
            case "up" -> new double[] {0, 1, 0};
            case "north" -> new double[] {0, 0, -1};
            case "south" -> new double[] {0, 0, 1};
            case "west" -> new double[] {-1, 0, 0};
            case "east" -> new double[] {1, 0, 0};
            default -> throw new IllegalArgumentException(dir);
        };
        double[] t = turn(new double[] {n[0] + 8, n[1] + 8, n[2] + 8}, x, y);
        int dx = (int) Math.round(t[0] - 8);
        int dy = (int) Math.round(t[1] - 8);
        int dz = (int) Math.round(t[2] - 8);
        if (dy < 0) {
            return "down";
        }
        if (dy > 0) {
            return "up";
        }
        if (dz < 0) {
            return "north";
        }
        if (dz > 0) {
            return "south";
        }
        return dx < 0 ? "west" : "east";
    }

    /** {@code FaceBakery.defaultFaceUV}. */
    static double[] defaultUv(double[] from, double[] to, String dir) {
        return switch (dir) {
            case "down" -> new double[] {from[0], 16 - to[2], to[0], 16 - from[2]};
            case "up" -> new double[] {from[0], from[2], to[0], to[2]};
            case "north" -> new double[] {16 - to[0], 16 - to[1], 16 - from[0], 16 - from[1]};
            case "south" -> new double[] {from[0], 16 - to[1], to[0], 16 - from[1]};
            case "west" -> new double[] {from[2], 16 - to[1], to[2], 16 - from[1]};
            case "east" -> new double[] {16 - to[2], 16 - to[1], 16 - from[2], 16 - from[1]};
            default -> throw new IllegalArgumentException(dir);
        };
    }

    /**
     * Turns uv-locked elements without element rotations (the stair templates): bounds and face directions turned,
     * every face gets the default UVs of its new place, cull faces dropped (item models draw every face anyway).
     */
    static JsonArray bakeUvLocked(JsonArray elements, int x, int y) {
        JsonArray out = new JsonArray();
        for (JsonElement e : elements) {
            JsonObject el = e.getAsJsonObject();
            if (el.has("rotation")) {
                throw new IllegalStateException("uv-locked baking of rotated elements is not supported");
            }
            double[] a = turn(doubles(el.getAsJsonArray("from")), x, y);
            double[] b = turn(doubles(el.getAsJsonArray("to")), x, y);
            double[] from = {Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2])};
            double[] to = {Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2])};
            JsonObject faces = new JsonObject();
            JsonObject old = el.getAsJsonObject("faces");
            for (String dir : DIRECTIONS) {
                // keep a deterministic order: look for the old face that turns into this direction
                for (Map.Entry<String, JsonElement> f : old.entrySet()) {
                    if (turnDirection(f.getKey(), x, y).equals(dir)) {
                        JsonObject face = new JsonObject();
                        face.add("uv", Json.array(defaultUv(from, to, dir)));
                        face.add("texture", f.getValue().getAsJsonObject().get("texture"));
                        faces.add(dir, face);
                    }
                }
            }
            JsonObject baked = new JsonObject();
            baked.add("from", Json.array(from));
            baked.add("to", Json.array(to));
            baked.add("faces", faces);
            out.add(baked);
        }
        return out;
    }

    /**
     * The cover of a carrier from its exact elements. Each face stays its exact size with its exact UVs, so the pixels
     * lie on the same grid as a full block next to it, and only moves {@link #COVER_OUT} along its own normal (a flat
     * element per face). Faces moved apart leave a hairline at each edge where the carrier would show, so the element
     * itself stays underneath, grown by {@link #COVER_UNDER}: it is seen only in that hairline.
     */
    static JsonArray cover(JsonArray exact) {
        JsonArray out = new JsonArray();
        for (JsonElement e : exact) {
            JsonObject el = e.getAsJsonObject();
            double[] from = doubles(el.getAsJsonArray("from"));
            double[] to = doubles(el.getAsJsonArray("to"));
            JsonObject under = new JsonObject();
            under.add("from", Json.array(from[0] - COVER_UNDER, from[1] - COVER_UNDER, from[2] - COVER_UNDER));
            under.add("to", Json.array(to[0] + COVER_UNDER, to[1] + COVER_UNDER, to[2] + COVER_UNDER));
            under.add("faces", el.getAsJsonObject("faces").deepCopy());
            out.add(under);
            for (Map.Entry<String, JsonElement> f : el.getAsJsonObject("faces").entrySet()) {
                out.add(plane(from, to, f.getKey(), f.getValue().getAsJsonObject()));
            }
        }
        return out;
    }

    private static JsonObject plane(double[] from, double[] to, String dir, JsonObject face) {
        int axis = switch (dir) {
            case "west", "east" -> 0;
            case "down", "up" -> 1;
            default -> 2;
        };
        boolean high = dir.equals("east") || dir.equals("up") || dir.equals("south");
        double[] a = from.clone();
        double[] b = to.clone();
        a[axis] = high ? to[axis] + COVER_OUT : from[axis] - COVER_OUT;
        b[axis] = a[axis];
        JsonObject faces = new JsonObject();
        faces.add(dir, face.deepCopy());
        JsonObject p = new JsonObject();
        p.add("from", Json.array(a));
        p.add("to", Json.array(b));
        p.add("faces", faces);
        return p;
    }

    /**
     * Gives every vertical face the texture variable of its shape, for blocks with a pattern made for a whole face
     * ({@link DecoTrim}): {@code #side} if its plane is covered completely (the back of a stair, a double slab), else
     * {@code #half} for a free 16 wide face and {@code #quarter} for an 8 wide one or one half hidden by a neighbour
     * box (an inner stair's riser). The texture at the default UVs then is a whole frame on each of them.
     */
    static JsonArray faceRoles(JsonArray baked) {
        Map<String, Double> covered = new java.util.HashMap<>();
        for (JsonElement e : baked) {
            JsonObject el = e.getAsJsonObject();
            double[] from = doubles(el.getAsJsonArray("from"));
            double[] to = doubles(el.getAsJsonArray("to"));
            for (String dir : el.getAsJsonObject("faces").keySet()) {
                if (vertical(dir)) {
                    covered.merge(dir + "@" + plane(from, to, dir), width(from, to, dir) * (to[1] - from[1]),
                            Double::sum);
                }
            }
        }
        JsonArray out = new JsonArray();
        for (JsonElement e : baked) {
            JsonObject el = e.getAsJsonObject().deepCopy();
            double[] from = doubles(el.getAsJsonArray("from"));
            double[] to = doubles(el.getAsJsonArray("to"));
            for (Map.Entry<String, JsonElement> f : el.getAsJsonObject("faces").entrySet()) {
                String dir = f.getKey();
                if (vertical(dir) && covered.get(dir + "@" + plane(from, to, dir)) < 256 - 1e-6) {
                    boolean free = width(from, to, dir) > 16 - 1e-6 && !partlyHidden(baked, from, to, dir);
                    f.getValue().getAsJsonObject().addProperty("texture", free ? "#half" : "#quarter");
                }
            }
            out.add(el);
        }
        return out;
    }

    private static boolean vertical(String dir) {
        return !dir.equals("up") && !dir.equals("down");
    }

    private static double plane(double[] from, double[] to, String dir) {
        return switch (dir) {
            case "north" -> from[2];
            case "south" -> to[2];
            case "west" -> from[0];
            default -> to[0];
        };
    }

    private static double width(double[] from, double[] to, String dir) {
        return dir.equals("north") || dir.equals("south") ? to[0] - from[0] : to[2] - from[2];
    }

    /** True if another box stands directly in front of this face over a part of it. */
    private static boolean partlyHidden(JsonArray baked, double[] from, double[] to, String dir) {
        int axis = dir.equals("north") || dir.equals("south") ? 2 : 0;
        boolean high = dir.equals("south") || dir.equals("east");
        double at = plane(from, to, dir);
        for (JsonElement e : baked) {
            double[] f = doubles(e.getAsJsonObject().getAsJsonArray("from"));
            double[] t = doubles(e.getAsJsonObject().getAsJsonArray("to"));
            boolean touches = high ? f[axis] == at : t[axis] == at;
            boolean overlaps = true;
            for (int a = 0; a < 3; a++) {
                if (a != axis) {
                    overlaps &= Math.min(t[a], to[a]) - Math.max(f[a], from[a]) > 1e-6;
                }
            }
            if (touches && overlaps) {
                return true;
            }
        }
        return false;
    }

    /** One exact full-width box from {@code y0} to {@code y1} (px) as a slab template: top, bottom, the rest side. */
    static JsonArray slab(double y0, double y1) {
        JsonObject faces = new JsonObject();
        for (String dir : DIRECTIONS) {
            JsonObject face = new JsonObject();
            face.addProperty("texture", dir.equals("down") ? "#bottom" : dir.equals("up") ? "#top" : "#side");
            faces.add(dir, face);
        }
        JsonObject el = new JsonObject();
        el.add("from", Json.array(0, y0, 0));
        el.add("to", Json.array(16, y1, 16));
        el.add("faces", faces);
        JsonArray list = new JsonArray();
        list.add(el);
        return faceRoles(bakeUvLocked(list, 0, 0));
    }

    static double[] doubles(JsonArray a) {
        double[] out = new double[a.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = a.get(i).getAsDouble();
        }
        return out;
    }
}
