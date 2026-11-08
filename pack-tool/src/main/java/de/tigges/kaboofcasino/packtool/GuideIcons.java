package de.tigges.kaboofcasino.packtool;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What an item looks like in a slot of the guide's recipe pictures, as font glyph layers (render.md 5; step 8 guide
 * book, Jack's request for recipe pictures). Fonts can only draw flat images, so every icon is the flat picture the
 * client would show for a flat item, and the front face for a block:
 * <ul>
 *   <li>vanilla items ({@link #VANILLA}): the vanilla texture by resource location (the client has it; nothing of
 *       Mojang's is copied into the pack). Block faces of full cubes are opaque, so their glyph is 17 px wide on any
 *       sane resource pack; every other vanilla texture (flat items, glass) has a width only the client knows
 *       ({@link Layer#UNKNOWN}), which the layout handles ({@code GuidePictures});</li>
 *   <li>casino items: resolved from the pack's own item definition (the {@code gui} case of a display-context select,
 *       as the mod's icons), its model and its parents: {@code item/generated} layers, the {@code all} or {@code side}
 *       texture of a cube, the lower half of a slab's side, the lower half plus the upper left quarter of a stair's
 *       side (the step is on the right in the inventory icon);</li>
 *   <li>anything else (own geometry, special models, a missing texture): a fallback question mark, counted by the
 *       build.</li>
 * </ul>
 * Animated textures show their first frame. Pure apart from reading the pack tree.
 */
final class GuideIcons {

    /**
     * One glyph of an icon: cell {@code col,row} of a {@code cols x rows} grid over the texture, drawn {@code dy} below
     * the top of the slot's 16 x 16 icon area, {@code height} px high; {@code advance} as the client computes it.
     */
    record Layer(String texture, int cols, int rows, int col, int row, int dy, int height, int advance) {
        /** The glyph's advance depends on the client's texture. */
        static final int UNKNOWN = -1;

        boolean known() {
            return advance != UNKNOWN;
        }
    }

    /** The layers of one item's icon, bottom first; {@code source} is vanilla, pack or fallback. */
    record Icon(String id, List<Layer> layers, String source) {
        Icon {
            layers = List.copyOf(layers);
        }
    }

    /** How a vanilla item is shown. */
    enum Shape {
        /** A flat item texture (transparent around it): the width is the client's. */
        FLAT,
        /** An opaque block face (17 px wide). */
        BLOCK,
        /** The lower half of an opaque block face (a slab seen from the front). */
        SLAB,
        /** A block face with transparent pixels (glass): the width is the client's. */
        GLASS
    }

    record Vanilla(String texture, Shape shape) {
    }

    /**
     * Every vanilla item a guide recipe uses (cells and the members shown for tags), with the texture the client draws
     * for it in a slot (or the front of the block). The pack test checks that the guide needs no other; the client
     * jar, when given, checks that the textures exist, are 16 x 16 and not animated, and that the opaque ones are.
     */
    static final Map<String, Vanilla> VANILLA = Map.ofEntries(
            vanilla("amethyst_shard", "item/amethyst_shard", Shape.FLAT),
            vanilla("black_dye", "item/black_dye", Shape.FLAT),
            vanilla("black_wool", "block/black_wool", Shape.BLOCK),
            vanilla("blue_wool", "block/blue_wool", Shape.BLOCK),
            vanilla("calcite", "block/calcite", Shape.BLOCK),
            vanilla("clay_ball", "item/clay_ball", Shape.FLAT),
            vanilla("dark_oak_planks", "block/dark_oak_planks", Shape.BLOCK),
            vanilla("diamond", "item/diamond", Shape.FLAT),
            vanilla("diamond_block", "block/diamond_block", Shape.BLOCK),
            vanilla("dropper", "block/dropper_front", Shape.BLOCK),
            vanilla("emerald", "item/emerald", Shape.FLAT),
            vanilla("glass", "block/glass", Shape.GLASS),
            vanilla("glass_bottle", "item/glass_bottle", Shape.FLAT),
            vanilla("glowstone", "block/glowstone", Shape.BLOCK),
            vanilla("glowstone_dust", "item/glowstone_dust", Shape.FLAT),
            vanilla("gold_block", "block/gold_block", Shape.BLOCK),
            vanilla("gold_ingot", "item/gold_ingot", Shape.FLAT),
            vanilla("gold_nugget", "item/gold_nugget", Shape.FLAT),
            vanilla("green_dye", "item/green_dye", Shape.FLAT),
            vanilla("green_stained_glass", "block/green_stained_glass", Shape.GLASS),
            vanilla("green_wool", "block/green_wool", Shape.BLOCK),
            vanilla("iron_chain", "item/iron_chain", Shape.FLAT),
            vanilla("iron_ingot", "item/iron_ingot", Shape.FLAT),
            vanilla("iron_nugget", "item/iron_nugget", Shape.FLAT),
            vanilla("leather", "item/leather", Shape.FLAT),
            vanilla("oak_planks", "block/oak_planks", Shape.BLOCK),
            vanilla("oak_sign", "item/oak_sign", Shape.FLAT),
            vanilla("oak_slab", "block/oak_planks", Shape.SLAB),
            vanilla("paper", "item/paper", Shape.FLAT),
            vanilla("piston", "block/piston_side", Shape.BLOCK),
            vanilla("red_dye", "item/red_dye", Shape.FLAT),
            vanilla("red_wool", "block/red_wool", Shape.BLOCK),
            vanilla("redstone", "item/redstone", Shape.FLAT),
            vanilla("redstone_block", "block/redstone_block", Shape.BLOCK),
            vanilla("stick", "item/stick", Shape.FLAT),
            vanilla("tinted_glass", "block/tinted_glass", Shape.GLASS),
            vanilla("torch", "block/torch", Shape.FLAT),
            vanilla("white_dye", "item/white_dye", Shape.FLAT),
            vanilla("white_wool", "block/white_wool", Shape.BLOCK),
            vanilla("yellow_dye", "item/yellow_dye", Shape.FLAT));

    /** Tags as the guide shows them: their first member (the plugin's {@code GuideBook.TAG_SHOWN}). */
    static final Map<String, String> TAG_SHOWN = Map.of(
            "#minecraft:planks", "minecraft:oak_planks",
            "#minecraft:signs", "minecraft:oak_sign",
            "#minecraft:wooden_slabs", "minecraft:oak_slab");

    /** The fallback glyph (ours): a gold question mark on a dark 16 x 16 tile (opaque: 17 px wide). */
    static final String FALLBACK_TEXTURE = "kaboofcasino:font/guide/unknown";
    static final int ICON = 16;

    private final PackTree tree;
    private final Map<String, Icon> cache = new HashMap<>();

    GuideIcons(PackTree tree) {
        this.tree = tree;
    }

    private static Map.Entry<String, Vanilla> vanilla(String id, String texture, Shape shape) {
        return Map.entry("minecraft:" + id, new Vanilla("minecraft:" + texture, shape));
    }

    /** The item a cell shows: a tag's first member, else the id. */
    static String shown(String ref) {
        if (!ref.startsWith("#")) {
            return ref;
        }
        String member = TAG_SHOWN.get(ref);
        if (member == null) {
            throw new IllegalStateException("no member to show for the tag " + ref);
        }
        return member;
    }

    Icon icon(String id) {
        return cache.computeIfAbsent(id, this::resolve);
    }

    private Icon resolve(String id) {
        if (id.startsWith("minecraft:")) {
            Vanilla v = VANILLA.get(id);
            if (v == null) {
                throw new IllegalStateException("the guide shows the vanilla item " + id + ", which GuideIcons.VANILLA"
                        + " does not know; add its texture");
            }
            return switch (v.shape()) {
                case FLAT, GLASS -> new Icon(id, List.of(new Layer(v.texture(), 1, 1, 0, 0, 0, ICON,
                        Layer.UNKNOWN)), "vanilla");
                case BLOCK -> new Icon(id, List.of(new Layer(v.texture(), 1, 1, 0, 0, 0, ICON, ICON + 1)),
                        "vanilla");
                case SLAB -> new Icon(id, List.of(new Layer(v.texture(), 1, 2, 0, 1, ICON / 2, ICON / 2,
                        ICON + 1)), "vanilla");
            };
        }
        List<Layer> layers = fromPack(id);
        return layers == null ? fallback(id) : new Icon(id, layers, "pack");
    }

    private static Icon fallback(String id) {
        return new Icon(id, List.of(new Layer(FALLBACK_TEXTURE, 1, 1, 0, 0, 0, ICON, ICON + 1)), "fallback");
    }

    /** The icon from the pack's item definition, or null if it cannot be shown flat. */
    private List<Layer> fromPack(String id) {
        String definition = path(id, "items", ".json");
        if (!tree.has(definition)) {
            return null;
        }
        String model = guiModel(Json.parse(tree.get(definition)).get("model"));
        if (model == null) {
            return null;
        }
        Map<String, String> textures = new HashMap<>();
        String parent = withNamespace(model);
        boolean elements = false;
        for (int depth = 0; !parent.startsWith("minecraft:"); depth++) {
            String file = path(parent, "models", ".json");
            if (depth > 16 || !tree.has(file)) {
                return null;
            }
            JsonObject json = Json.parse(tree.get(file));
            elements |= json.has("elements");
            if (json.has("textures")) {
                for (Map.Entry<String, JsonElement> t : json.getAsJsonObject("textures").entrySet()) {
                    textures.putIfAbsent(t.getKey(), t.getValue().getAsString());
                }
            }
            if (!json.has("parent")) {
                return null;
            }
            parent = withNamespace(json.get("parent").getAsString());
        }
        if (elements) {
            return null; // own geometry: no flat picture
        }
        return switch (parent) {
            case "minecraft:item/generated", "minecraft:item/handheld" -> {
                List<Layer> out = new ArrayList<>();
                for (int i = 0; textures.containsKey("layer" + i); i++) {
                    Layer l = layer(texture(textures, "layer" + i), 1, 0);
                    if (l == null) {
                        yield null;
                    }
                    out.add(l);
                }
                yield out.isEmpty() ? null : out;
            }
            case "minecraft:block/cube_all", "minecraft:block/leaves" -> single(texture(textures, "all"));
            case "minecraft:block/cube_column", "minecraft:block/cube_column_horizontal",
                 "minecraft:block/cube_bottom_top" -> single(texture(textures, "side"));
            case "minecraft:block/slab" -> {
                Layer lower = layer(texture(textures, "side"), 2, ICON / 2);
                yield lower == null ? null : List.of(lower);
            }
            case "minecraft:block/stairs" -> {
                Layer lower = layer(texture(textures, "side"), 2, ICON / 2);
                Layer step = layer(texture(textures, "side"), 4, 0);
                yield lower == null || step == null ? null : List.of(lower, step);
            }
            default -> null;
        };
    }

    private List<Layer> single(String texture) {
        Layer l = layer(texture, 1, 0);
        return l == null ? null : List.of(l);
    }

    /**
     * A glyph over (the first frame of) a texture, drawn {@code dy} below the slot's top: {@code split} 1 = whole,
     * 2 = lower half (cell row 1 of 1 x 2), 4 = upper left quarter (cell 0,0 of 2 x 2).
     */
    private Layer layer(String texture, int split, int dy) {
        if (texture == null) {
            return null;
        }
        if (texture.startsWith("minecraft:")) {
            // a vanilla texture inside a casino model: its width is the client's
            return switch (split) {
                case 1 -> new Layer(texture, 1, 1, 0, 0, dy, ICON, Layer.UNKNOWN);
                case 2 -> new Layer(texture, 1, 2, 0, 1, dy, ICON / 2, Layer.UNKNOWN);
                default -> new Layer(texture, 2, 2, 0, 0, dy, ICON / 2, Layer.UNKNOWN);
            };
        }
        String file = path(texture, "textures", ".png");
        if (!tree.has(file)) {
            return null;
        }
        BufferedImage image = Images.read(tree.get(file));
        int w = image.getWidth();
        int frames = 1;
        if (tree.has(file + ".mcmeta")) {
            frames = Math.max(1, image.getHeight() / w); // animation strip of square frames
        } else if (image.getHeight() != w) {
            return null;
        }
        int rows = frames;
        int cols = 1;
        int row = 0;
        int cellW = w;
        int cellH = image.getHeight() / frames;
        int height = ICON;
        if (split == 2) {
            rows = frames * 2;
            row = 1;
            cellH /= 2;
            height = ICON / 2;
        } else if (split == 4) {
            rows = frames * 2;
            cols = 2;
            cellW /= 2;
            cellH /= 2;
            height = ICON / 2;
        }
        if (cellW <= 0 || cellH <= 0 || cellW > 256 || cellH > 256) {
            return null;
        }
        double scale = (double) height / cellH;
        int actual = Images.actualGlyphWidth(image, 0, row * cellH, cellW, cellH);
        int advance = (int) (0.5 + actual * scale) + 1;
        return new Layer(texture, cols, rows, 0, row, dy, height, advance);
    }

    private static String texture(Map<String, String> textures, String key) {
        String t = textures.get(key);
        for (int i = 0; t != null && t.startsWith("#") && i < 8; i++) {
            t = textures.get(t.substring(1));
        }
        return t == null || t.startsWith("#") ? null : withNamespace(t);
    }

    /** The model a GUI shows: a model node, the {@code gui} case of a display-context select, else the fallback. */
    private static String guiModel(JsonElement node) {
        if (node == null || !node.isJsonObject()) {
            return null;
        }
        JsonObject o = node.getAsJsonObject();
        String type = withNamespace(o.get("type").getAsString());
        if (type.equals("minecraft:model")) {
            return o.has("tints") ? null : o.get("model").getAsString();
        }
        if (type.equals("minecraft:select") && o.has("property")
                && withNamespace(o.get("property").getAsString()).equals("minecraft:display_context")) {
            for (JsonElement c : o.getAsJsonArray("cases")) {
                JsonElement when = c.getAsJsonObject().get("when");
                boolean gui = when.isJsonArray() ? when.getAsJsonArray().contains(new com.google.gson.JsonPrimitive(
                        "gui")) : when.getAsString().equals("gui");
                if (gui) {
                    return guiModel(c.getAsJsonObject().get("model"));
                }
            }
            return guiModel(o.get("fallback"));
        }
        return null;
    }

    static String withNamespace(String id) {
        return id.contains(":") ? id : "minecraft:" + id;
    }

    /** {@code ns:path} -> {@code assets/ns/<dir>/path<ext>}. */
    static String path(String id, String dir, String ext) {
        int colon = id.indexOf(':');
        return "assets/" + id.substring(0, colon) + "/" + dir + "/" + id.substring(colon + 1) + ext;
    }
}
