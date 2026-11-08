package de.tigges.kaboofcasino.packtool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Step 9 casino chips (PLAN-2 C1): the art of the six chip items. The plugin makes a chip as {@code minecraft:paper}
 * with {@code item_model kaboofcasino:chip_<n>} and the name {@code item.kaboofcasino.chip_<n>}; it also shows the
 * icons inline in floating panels as {@code minecraft:items} atlas sprites ({@code kaboofcasino:item/chip_<n>}: the
 * client's {@code atlases/items.json} lists {@code textures/item} of every namespace). Per denomination:
 * <ul>
 *   <li>{@code textures/item/chip_<n>.png}: a 16x16 face-on chip ({@link #BASE} with the denomination's
 *       {@link #INSERTS}), only fully opaque or fully clear pixels;</li>
 *   <li>{@code models/chip_<n>.json}: {@code item/generated} of that icon, so in the hand, on the ground and in an
 *       item frame the chip is the icon extruded by one pixel (about a real chip's thickness);</li>
 *   <li>{@code textures/item/chip_<n>_badge.png} and {@code models/chip_<n>_badge.json}: the value badge, a flat quad
 *       in front of the icon ({@link #BADGE_Z});</li>
 *   <li>{@code items/chip_<n>.json}: the item definition, a {@code select} on {@code display_context}: in the GUI
 *       (inventories, hotbar, the carried stack) a {@code composite} of the chip and its badge, everywhere else (hands,
 *       ground, item frames, shelves, head) the chip alone ({@link #itemModel});</li>
 *   <li>the name in {@code lang/<code>.json} of the plugin's namespace ({@link Lang#own}): "25er-Chip" in German,
 *       "25 Chip" in every other language.</li>
 * </ul>
 *
 * <p>The look follows the mod's chips: the face-on chips of its roulette screen ({@code roulette.png}: gold rim with a
 * light top and a dark bottom edge, cream edge inserts), the gold inlay ring of its chip tops
 * ({@code casino_chip_pile.png}, the chip stacks in {@code casino_decor_atlas.png}), and the light, body and dark
 * tones of its chip stacks as a light upper-left and a dark lower-right inner rim. White, red, green and black are the
 * mod's own chips (the white one with its taupe edge stripes as inserts, as cream would vanish on white); purple and
 * gold are the red chip's tones turned to another hue ({@link #recolour}), and the gold chip gets a darker bronze rim
 * and ring and white inserts, so they stand out of the gold. {@code ChipPackTest} checks that every mod colour used
 * here is in the named mod texture.
 *
 * <p>Every denomination has its own edge-insert shape, so the chips differ without their colours too (colour-blind
 * players; the hand and the ground show no number): 1 four small ticks, 5 four long caps (the mod's inserts made
 * longer), 25 eight short inserts, 100 two wide side stripes, 500 a dotted ring, 1000 a full ring stripe. The value
 * is shown only in the GUI, as a badge in the icon's top-left corner ("1", "5", "25", "100", "500", "1K" in 3x5
 * numerals, the mod's text cream with a one-pixel outline in the mod's chip black); the stack count stays bottom-right.
 * The icons are also the floating panels' 8x8 text sprites: the shapes are 2 px deep and the colours carry the value
 * at that size.
 */
final class ChipPack {

    static final List<String> SOUND_EVENTS = List.of();
    /** None of the mod's texts: the chip names are the plugin's own ({@link Lang#own}). */
    static final List<String> LANG_PREFIXES = List.of();

    /** The denominations, smallest first (the plugin's {@code Chips.DENOMINATIONS}). */
    static final int[] DENOMINATIONS = {1, 5, 25, 100, 500, 1000};

    /**
     * The icon without inserts, a disc of 15 px at (1, 1) around pixel (8, 8). Letters: {@code L}/{@code O}/{@code D}
     * rim (top edge, rim, bottom edge), {@code H}/{@code B}/{@code K} body (light upper-left inner rim, body, dark
     * lower-right inner rim), {@code R} inlay ring, {@code .} clear. The body between rim and ring is the band the
     * inserts lie in.
     */
    static final String[] BASE = {
        "................",
        "......LLLLL.....",
        "....OOHHHHHOO...",
        "...OHHBBBBBBBO..",
        "..OHBBBBBBBBBBO.",
        "..OHBBBRRRBBBBO.",
        ".OHBBBRBBBRBBBKO",
        ".OHBBRBBBBBRBBKO",
        ".OHBBRBBBBBRBBKO",
        ".OHBBRBBBBBRBBKO",
        ".OHBBBRBBBRBBBKO",
        "..OBBBBRRRBBBKO.",
        "..OBBBBBBBBBBKO.",
        "...OBBBBBBBKKO..",
        "....OOKKKKKOO...",
        "......DDDDD.....",
    };

    /**
     * The edge inserts ({@code #}) of each denomination over {@link #BASE}, each a different shape, mirror-symmetric
     * like the disc: 1 four small ticks (1x2), 5 four long caps (5x2), 25 eight short inserts (ticks and corner
     * pieces), 100 two wide stripes left and right (the band's full depth), 500 a dotted ring (every other pixel of a
     * radius-5 circle), 1000 that ring full.
     */
    static final Map<Integer, String[]> INSERTS = Map.of(
            1, new String[] {
                "................",
                "................",
                "........#.......",
                "........#.......",
                "................",
                "................",
                "................",
                "................",
                "..##.........##.",
                "................",
                "................",
                "................",
                "................",
                "........#.......",
                "........#.......",
                "................"},
            5, new String[] {
                "................",
                "................",
                "......#####.....",
                "......#####.....",
                "................",
                "................",
                "..##.........##.",
                "..##.........##.",
                "..##.........##.",
                "..##.........##.",
                "..##.........##.",
                "................",
                "................",
                "......#####.....",
                "......#####.....",
                "................"},
            25, new String[] {
                "................",
                "................",
                "........#.......",
                "........#.......",
                "....##.....##...",
                "....#.......#...",
                "................",
                "................",
                "..##.........##.",
                "................",
                "................",
                "....#.......#...",
                "....##.....##...",
                "........#.......",
                "........#.......",
                "................"},
            100, new String[] {
                "................",
                "................",
                "................",
                "................",
                "................",
                "...##.......##..",
                "..###.......###.",
                "..###.......###.",
                "..###.......###.",
                "..###.......###.",
                "..###.......###.",
                "...##.......##..",
                "................",
                "................",
                "................",
                "................"},
            500, new String[] {
                "................",
                "................",
                "................",
                "......#.#.#.....",
                "................",
                "....#.......#...",
                "................",
                "...#.........#..",
                "................",
                "...#.........#..",
                "................",
                "....#.......#...",
                "................",
                "......#.#.#.....",
                "................",
                "................"},
            1000, new String[] {
                "................",
                "................",
                "................",
                "......#####.....",
                ".....#.....#....",
                "....#.......#...",
                "...#.........#..",
                "...#.........#..",
                "...#.........#..",
                "...#.........#..",
                "...#.........#..",
                "....#.......#...",
                ".....#.....#....",
                "......#####.....",
                "................",
                "................"});

    /** The badge numerals, 3x5 ({@code #} lit), and the badge's labels. */
    static final Map<Character, String[]> NUMERALS = Map.of(
            '0', new String[] {"###", "#.#", "#.#", "#.#", "###"},
            '1', new String[] {".#.", "##.", ".#.", ".#.", "###"},
            '2', new String[] {"###", "..#", "###", "#..", "###"},
            '5', new String[] {"###", "#..", "###", "..#", "###"},
            'K', new String[] {"#.#", "#.#", "##.", "#.#", "#.#"});
    static final int NUMERAL_W = 3;
    static final int NUMERAL_H = 5;
    /** The badge: numerals 1 px apart, a 1 px outline around them, top-left at the icon's (0, 0). */
    static final int BADGE_H = NUMERAL_H + 2;
    /**
     * The badge quad's depth: in front of the icon's front face ({@code item/generated} spans z 7.5..8.5), so the GUI
     * draws it over the chip whatever the order of the composite's layers.
     */
    static final double BADGE_Z = 8.75;

    // the mod's chip art (ChipPackTest finds each colour in the texture named)
    /** roulette.png chips: rim, its top edge, its bottom edge, the cream edge inserts. */
    static final int RIM = 0xFFBD9954;
    static final int RIM_LIGHT = 0xFFE8CB83;
    static final int RIM_DARK = 0xFFA3864C;
    static final int INSERT = 0xFFE4D4A0;
    /** The gold inlay ring of the chip tops (casino_chip_pile.png, casino_decor_atlas.png). */
    static final int RING = 0xFFD2A642;
    /** casino_decor_atlas.png chip stacks: light, body and dark tone of the white, red, green and black chips. */
    static final int[] WHITE = {0xFFF9F4E6, 0xFFF4EFE4, 0xFFE1D5B9};
    static final int[] RED = {0xFFD4353F, 0xFFA5222B, 0xFF7C1A20};
    static final int[] GREEN = {0xFF2DBF6F, 0xFF1C9452, 0xFF156F3E};
    static final int[] BLACK = {0xFF323841, 0xFF20252C, 0xFF16191E};
    /** The white chip's taupe edge stripes (casino_decor_atlas.png): cream inserts would vanish on white. */
    static final int WHITE_INSERT = 0xFFA9A08B;
    /** The badge: the mod's text cream (DerbyUiArt, CasinoText) and the black chip's body as its outline. */
    static final int BADGE_CREAM = 0xFF000000 | DerbyGuiArt.CREAM;
    static final int BADGE_OUTLINE = BLACK[1];

    /** The colours of one chip: body tones (light, body, dark), insert, ring, rim tones (rim, top, bottom). */
    record Palette(int light, int body, int dark, int insert, int ring, int rim, int rimLight, int rimDark) {
        int colour(char role) {
            return switch (role) {
                case 'H' -> light;
                case 'B' -> body;
                case 'K' -> dark;
                case 'I' -> insert;
                case 'R' -> ring;
                case 'O' -> rim;
                case 'L' -> rimLight;
                case 'D' -> rimDark;
                default -> throw new IllegalArgumentException("no chip role " + role);
            };
        }
    }

    private ChipPack() {
    }

    static void add(ModJar jar, PackTree tree, Path packSrc) {
        Map<String, String> german = new LinkedHashMap<>();
        Map<String, String> english = new LinkedHashMap<>();
        for (int d : DENOMINATIONS) {
            String name = "chip_" + d;
            tree.put("assets/kaboofcasino/textures/item/" + name + ".png", Images.write(icon(d)));
            JsonObject model = new JsonObject();
            model.addProperty("parent", "minecraft:item/generated");
            model.add("textures", Json.object("layer0", new JsonPrimitive("kaboofcasino:item/" + name)));
            tree.put("assets/kaboofcasino/models/" + name + ".json", Json.bytes(model));
            tree.put("assets/kaboofcasino/textures/item/" + name + "_badge.png", Images.write(badge(d)));
            tree.put("assets/kaboofcasino/models/" + name + "_badge.json", Json.bytes(badgeModel(d)));
            tree.put("assets/kaboofcasino/items/" + name + ".json", Json.bytes(Json.object("model", itemModel(d))));
            german.put(langKey(d), d + "er-Chip");
            english.put(langKey(d), d + " Chip");
        }
        Lang.own(jar, tree, german, english);
    }

    static String langKey(int denomination) {
        return "item.kaboofcasino.chip_" + denomination;
    }

    /** The roles of a denomination's icon: {@link #BASE} with its {@link #INSERTS} as {@code I} (edge insert). */
    static String[] face(int denomination) {
        String[] inserts = INSERTS.get(denomination);
        if (inserts == null) {
            throw new IllegalArgumentException("no chip denomination " + denomination);
        }
        String[] out = new String[16];
        for (int y = 0; y < 16; y++) {
            StringBuilder row = new StringBuilder(BASE[y]);
            for (int x = 0; x < 16; x++) {
                if (inserts[y].charAt(x) == '#') {
                    if ("HBK".indexOf(BASE[y].charAt(x)) < 0) {
                        throw new IllegalStateException("insert of chip " + denomination + " outside the band at " + x
                                + "," + y);
                    }
                    row.setCharAt(x, 'I');
                }
            }
            out[y] = row.toString();
        }
        return out;
    }

    /** The 16x16 icon of a denomination. */
    static BufferedImage icon(int denomination) {
        Palette palette = palette(denomination);
        String[] face = face(denomination);
        BufferedImage img = Images.blank(16, 16);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                char role = face[y].charAt(x);
                if (role != '.') {
                    img.setRGB(x, y, palette.colour(role));
                }
            }
        }
        return img;
    }

    /** The badge text: the value, 1000 as "1K". */
    static String badgeLabel(int denomination) {
        return denomination == 1000 ? "1K" : Integer.toString(denomination);
    }

    /** Badge width: the numerals 1 px apart plus the outline on both sides. */
    static int badgeWidth(int denomination) {
        int n = badgeLabel(denomination).length();
        return n * NUMERAL_W + (n - 1) + 2;
    }

    /**
     * The badge as a 16x16 texture (so the items atlas keeps its mip levels), the rest clear: cream numerals from
     * (1, 1), every clear pixel next to one (8 neighbours) the outline colour.
     */
    static BufferedImage badge(int denomination) {
        String label = badgeLabel(denomination);
        int w = badgeWidth(denomination);
        boolean[][] lit = new boolean[BADGE_H][w];
        for (int i = 0; i < label.length(); i++) {
            String[] numeral = NUMERALS.get(label.charAt(i));
            for (int y = 0; y < NUMERAL_H; y++) {
                for (int x = 0; x < NUMERAL_W; x++) {
                    lit[1 + y][1 + i * (NUMERAL_W + 1) + x] = numeral[y].charAt(x) == '#';
                }
            }
        }
        BufferedImage img = Images.blank(16, 16);
        for (int y = 0; y < BADGE_H; y++) {
            for (int x = 0; x < w; x++) {
                if (lit[y][x]) {
                    img.setRGB(x, y, BADGE_CREAM);
                    continue;
                }
                boolean next = false;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int ny = y + dy;
                        int nx = x + dx;
                        next |= ny >= 0 && ny < BADGE_H && nx >= 0 && nx < w && lit[ny][nx];
                    }
                }
                if (next) {
                    img.setRGB(x, y, BADGE_OUTLINE);
                }
            }
        }
        return img;
    }

    /**
     * The badge model: one flat south face over the badge's corner of the icon ({@code w} x 7 px at the top-left, its
     * texels 1:1) at {@link #BADGE_Z}, lit like the icon ({@code gui_light front}), no display transform (the icon's
     * {@code item/generated} has none in the GUI either).
     */
    static JsonObject badgeModel(int denomination) {
        String texture = "kaboofcasino:item/chip_" + denomination + "_badge";
        int w = badgeWidth(denomination);
        JsonObject face = new JsonObject();
        face.add("uv", Json.array(0, 0, w, BADGE_H));
        face.addProperty("texture", "#badge");
        JsonObject element = new JsonObject();
        element.add("from", Json.array(0, 16 - BADGE_H, BADGE_Z));
        element.add("to", Json.array(w, 16, BADGE_Z));
        element.add("faces", Json.object("south", face));
        JsonArray elements = new JsonArray();
        elements.add(element);
        JsonObject textures = new JsonObject();
        textures.addProperty("badge", texture);
        textures.addProperty("particle", "kaboofcasino:item/chip_" + denomination);
        JsonObject model = new JsonObject();
        model.addProperty("gui_light", "front");
        model.add("textures", textures);
        model.add("elements", elements);
        return model;
    }

    /**
     * {@code select display_context}: case {@code gui} a {@code composite} of the chip and then its badge, fallback
     * (every other context) the chip alone.
     */
    static JsonObject itemModel(int denomination) {
        JsonObject chip = Json.itemDefinition("kaboofcasino:chip_" + denomination).getAsJsonObject("model");
        JsonObject badge = Json.itemDefinition("kaboofcasino:chip_" + denomination + "_badge")
                .getAsJsonObject("model");
        JsonArray layers = new JsonArray();
        layers.add(chip);
        layers.add(badge);
        JsonObject composite = new JsonObject();
        composite.addProperty("type", "minecraft:composite");
        composite.add("models", layers);
        JsonArray when = new JsonArray();
        when.add("gui");
        JsonObject gui = new JsonObject();
        gui.add("when", when);
        gui.add("model", composite);
        JsonArray cases = new JsonArray();
        cases.add(gui);
        JsonObject select = new JsonObject();
        select.addProperty("type", "minecraft:select");
        select.addProperty("property", "minecraft:display_context");
        select.add("cases", cases);
        select.add("fallback", Json.itemDefinition("kaboofcasino:chip_" + denomination).getAsJsonObject("model"));
        return select;
    }

    static Palette palette(int denomination) {
        return switch (denomination) {
            case 1 -> plain(WHITE, WHITE_INSERT);
            case 5 -> plain(RED, INSERT);
            case 25 -> plain(GREEN, INSERT);
            case 100 -> plain(BLACK, INSERT);
            case 500 -> plain(turned(RED, 280, 0.9, 0), INSERT);
            case 1000 -> {
                int[] body = turned(RED, 42, 1.0, 0.12);
                int bronze = shade(RIM, -0.26, 1.3);
                yield new Palette(body[0], body[1], body[2], WHITE[0], bronze, bronze, shade(RIM, -0.14, 1.3),
                        shade(RIM, -0.34, 1.3));
            }
            default -> throw new IllegalArgumentException("no chip denomination " + denomination);
        };
    }

    private static Palette plain(int[] tones, int insert) {
        return new Palette(tones[0], tones[1], tones[2], insert, RING, RIM, RIM_LIGHT, RIM_DARK);
    }

    private static int[] turned(int[] tones, double hue, double saturation, double lightness) {
        int[] out = new int[tones.length];
        for (int i = 0; i < tones.length; i++) {
            out[i] = recolour(tones[i], hue, saturation, lightness);
        }
        return out;
    }

    /** {@code argb} with its HSL hue set to {@code hue} degrees, saturation scaled and lightness shifted (clamped). */
    static int recolour(int argb, double hue, double saturation, double lightness) {
        double[] hsl = hsl(argb);
        return rgb(argb >>> 24, hue / 360.0, hsl[1] * saturation, hsl[2] + lightness);
    }

    /** {@code argb} with its own hue, saturation scaled and lightness shifted (clamped). */
    static int shade(int argb, double lightness, double saturation) {
        double[] hsl = hsl(argb);
        return rgb(argb >>> 24, hsl[0], hsl[1] * saturation, hsl[2] + lightness);
    }

    /** Hue (0..1), saturation and lightness of an ARGB colour. */
    static double[] hsl(int argb) {
        double r = (argb >> 16 & 255) / 255.0;
        double g = (argb >> 8 & 255) / 255.0;
        double b = (argb & 255) / 255.0;
        double max = Math.max(r, Math.max(g, b));
        double min = Math.min(r, Math.min(g, b));
        double l = (max + min) / 2;
        if (max == min) {
            return new double[] {0, 0, l};
        }
        double d = max - min;
        double s = l <= 0.5 ? d / (max + min) : d / (2 - max - min);
        double h;
        if (max == r) {
            h = (g - b) / d;
        } else if (max == g) {
            h = 2 + (b - r) / d;
        } else {
            h = 4 + (r - g) / d;
        }
        h /= 6;
        return new double[] {h < 0 ? h + 1 : h, s, l};
    }

    private static int rgb(int alpha, double h, double s, double l) {
        s = Math.clamp(s, 0.0, 1.0);
        l = Math.clamp(l, 0.0, 1.0);
        double q = l <= 0.5 ? l * (1 + s) : l + s - l * s;
        double p = 2 * l - q;
        int r = channel(p, q, h + 1 / 3.0);
        int g = channel(p, q, h);
        int b = channel(p, q, h - 1 / 3.0);
        return alpha << 24 | r << 16 | g << 8 | b;
    }

    private static int channel(double p, double q, double t) {
        t = t - Math.floor(t);
        double v;
        if (t < 1 / 6.0) {
            v = p + (q - p) * 6 * t;
        } else if (t < 0.5) {
            v = q;
        } else if (t < 2 / 3.0) {
            v = p + (q - p) * (2 / 3.0 - t) * 6;
        } else {
            v = p;
        }
        return (int) Math.round(v * 255);
    }
}
