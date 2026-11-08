package de.tigges.kaboofcasino.packtool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The leaderboards (step 10; not in MineVegas): two boards built like the mod's big casino sign
 * ({@code casinodeco:block/sign_0..5}: walnut back, brass frame, warm bulbs, dark green panel with the red LED word
 * on its dark red backing), with the bulbs on the frame and a header band over blank felt where the plugin's text
 * displays draw the list. Model units, each model's centre (8, 8, 8) is where the plugin's item display stands, south
 * frame (x to the viewer's right, y up, z towards the viewer).
 * <ul>
 *   <li>{@code kaboofcasino:leaderboard/wall}: 3 x 2 blocks on a wall, centre = the bottom middle cell's centre:
 *       x -16..32, y 0..32, back on the wall at z 0, felt face at z {@value #WALL_FELT}, nothing beyond z 3; the
 *       header says {@value #WALL_WORD};</li>
 *   <li>{@code kaboofcasino:leaderboard/column_post}: the bottom cell of the standing column, centre = that cell's
 *       centre: brass foot, walnut plinth, brass pole (x 7..9, z 7..9) up to y 16, all inside the cell;</li>
 *   <li>{@code kaboofcasino:leaderboard/column_board}: the column's board, centre = the middle cell's centre:
 *       x -4..20, y -8..24 (the step-10 contract's numbers: one block below to one block above that centre), body
 *       z 6..10 on the pole's axis with a walnut and brass back at z 6, felt face at z {@value #COLUMN_FELT}; the
 *       header says {@value #COLUMN_WORD};</li>
 *   <li>{@code kaboofcasino:leaderboard_wall} / {@code kaboofcasino:leaderboard_column}: the placing items, a 32x32
 *       icon in every display context (like the mod's sign icons: brass frame with bulbs, red LED header).</li>
 * </ul>
 * Each board leaves its text area ({@link Board#textArea()}) as blank felt: nothing of the model is in front of the
 * felt face there. The frame (rim 0.5, brass 1.5) and the header band (7 from the top: a 0.5 brass divider, 4.5 of
 * felt with the LEDs, the top frame) lie outside it. Bulbs and LEDs are self-lit like the mod's
 * ({@code light_emission} 15 on their elements), so there is no separate lights model; nothing is animated.
 */
final class LeaderboardPack {

    static final List<String> SOUND_EVENTS = List.of();
    static final List<String> LANG_PREFIXES = List.of();

    static final String WALL = "kaboofcasino:leaderboard/wall";
    static final String COLUMN_POST = "kaboofcasino:leaderboard/column_post";
    static final String COLUMN_BOARD = "kaboofcasino:leaderboard/column_board";
    static final String WALL_ITEM = "kaboofcasino:leaderboard_wall";
    static final String COLUMN_ITEM = "kaboofcasino:leaderboard_column";
    static final String WALL_WORD = "BESTENLISTE";
    static final String COLUMN_WORD = "TOP 10";
    static final double WALL_FELT = 2;
    static final double COLUMN_FELT = 10;

    /** Frame: walnut rim, brass band; together the border around the felt. */
    static final double RIM = 0.5;
    static final double BAND = 1.5;
    static final double BORDER = RIM + BAND;
    /** Header: from the text area's top to the board's top (divider, LED felt, top border). */
    static final double HEADER = 7;
    static final double DIVIDER = 0.5;
    /** Relief in front of the felt face: brass frame and divider, LEDs, bulbs (on the brass). */
    static final double BRASS_RELIEF = 0.5;
    static final double LED_RELIEF = 0.3;
    static final double BULB_RELIEF = 0.35;
    static final double FELT_DEPTH = 1;
    /** LED pitch (the cashier's), bulb size and the bulbs' target spacing along the band (the mod's is 4). */
    static final double LED = 0.5;
    static final double BULB = 0.75;
    static final double BULB_SPACING = 3.75;
    /** Panel texture density: texels per model unit (the LED backing sits on a 0.25 grid). */
    static final int TEXELS = 4;
    /** The mod's sign panel colours ({@code sign_panel.png}): the felt and the LED word's dark red backing. */
    static final int FELT = 0xFF0E2A21;
    static final int BACKING = 0xFF43221E;

    /**
     * LED letters, 7 rows top first, in the style of the mod's sign letters (CASINO on {@code sign_0..5} and
     * {@code small_sign}): S, I, N and O verbatim, E as the cashier's KASSE has it, the rest drawn to match; a space
     * is 3 columns. The zero is slashed so "10" does not read "1O".
     */
    static final Map<Character, String[]> LETTERS = Map.ofEntries(
            Map.entry('B', new String[] {"####.", "#..##", "#...#", "####.", "#...#", "#..##", "####."}),
            Map.entry('E', new String[] {"#####", "##...", "#....", "####.", "#....", "##...", "#####"}),
            Map.entry('I', new String[] {"#####", "..#..", "..#..", "..#..", "..#..", "..#..", "#####"}),
            Map.entry('L', new String[] {"#....", "#....", "#....", "#....", "#....", "##...", "#####"}),
            Map.entry('N', new String[] {"#...#", "##..#", "##..#", "#.#.#", "#..##", "#..##", "#...#"}),
            Map.entry('O', new String[] {".###.", "##.##", "#...#", "#...#", "#...#", "##.##", ".###."}),
            Map.entry('P', new String[] {"####.", "#..##", "#...#", "#..##", "####.", "#....", "#...."}),
            Map.entry('S', new String[] {".####", "##...", "#....", ".###.", "....#", "...##", "####."}),
            Map.entry('T', new String[] {"#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."}),
            Map.entry('1', new String[] {"..#..", ".##..", "#.#..", "..#..", "..#..", "..#..", "#####"}),
            Map.entry('0', new String[] {".###.", "##.##", "#..##", "#.#.#", "##..#", "##.##", ".###."}),
            Map.entry(' ', new String[] {"...", "...", "...", "...", "...", "...", "..."}));

    /**
     * One board: path below {@code kaboofcasino:}, header word, outer rectangle (x0, y0) .. (x1, y1), the back z,
     * the felt face z, and whether the back is a walnut panel in a brass frame (free standing) or lies on a wall.
     */
    record Board(String path, String word, double x0, double y0, double x1, double y1, double back, double felt,
                 boolean backFrame) {

        /** {x0, y0, x1, y1}: blank felt, free for the plugin's text displays. */
        double[] textArea() {
            return new double[] {x0 + BORDER, y0 + BORDER, x1 - BORDER, y1 - HEADER};
        }

        /** The felt element: inside the border, from the bottom border to the top border (text area + header). */
        double[] feltRect() {
            return new double[] {x0 + BORDER, y0 + BORDER, x1 - BORDER, y1 - BORDER};
        }

        String panelTexture() {
            return "kaboofcasino:block/" + path.replace('/', '_') + "_panel";
        }

        /** Left x of the LED word (centred) and top y of its 7 rows (centred in the header's felt). */
        double ledX(int columns) {
            return (x0 + x1) / 2 - columns * LED / 2;
        }

        double ledTop() {
            double bottom = y1 - HEADER + DIVIDER;
            double top = y1 - BORDER;
            return (bottom + top) / 2 + 7 * LED / 2;
        }
    }

    static final Board WALL_BOARD = new Board("leaderboard/wall", WALL_WORD, -16, 0, 32, 32, 0, WALL_FELT, false);
    static final Board COLUMN = new Board("leaderboard/column_board", COLUMN_WORD, -4, -8, 20, 24, 6, COLUMN_FELT,
            true);

    private static final Set<String> ALL = Set.of("north", "east", "south", "west", "up", "down");
    private static final Set<String> FRONT = Set.of("east", "south", "west", "up", "down");
    private static final Set<String> BACK = Set.of("north", "east", "west", "up", "down");
    private static final List<String> SIDES = List.of("north", "east", "south", "west", "up", "down");

    /** Icons (32x32 pixel maps, top row first, '.' transparent); the palette is {@link #ICON_COLOURS}. */
    static final String[] WALL_ICON = {
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        ".hhohhhhohhhhohhhhohhhhohhhhohk.",
        ".hffccccccccccccccccccccccccffd.",
        ".hffcrrrcrrrcrrrcccccrccrrrcffd.",
        ".offccrccrcrcrcrccccrrccrcrcffo.",
        ".hffccrccrcrcrrrcccccrccrcrcffd.",
        ".hffccrccrcrcrcccccccrccrcrcffd.",
        ".hffccrccrrrcrccccccrrrcrrrcffd.",
        ".hffccccccccccccccccccccccccffd.",
        ".offffffffffffffffffffffffffffo.",
        ".hbbbbbbbbbbbbbbbbbbbbbbbbbbbbd.",
        ".hffffffffffffffffffffffffffffd.",
        ".hffggffffffffffffffffffffffffd.",
        ".hffgGfwwwwwwwwwwwwwwffwwwwwffd.",
        ".offffffffffffffffffffffffffffo.",
        ".hffllffffffffffffffffffffffffd.",
        ".hfflLfsssssssssssfffffsssssffd.",
        ".hffffffffffffffffffffffffffffd.",
        ".hffzzffffffffffffffffffffffffd.",
        ".offzZfssssssssssssffffsssssffo.",
        ".hffffffffffffffffffffffffffffd.",
        ".hffffffffffffffffffffffffffffd.",
        ".ddoddddoddddoddddoddddoddddodk.",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
    };
    static final String[] COLUMN_ICON = {
        "................................",
        "......hhhhohhhhohhhhohhhk.......",
        "......hffcccccccccccccffd.......",
        "......hffcrrrcrrrcrrrcffd.......",
        "......hffccrccrcrcrcrcffd.......",
        "......offccrccrcrcrrrcffo.......",
        "......hffccrccrcrcrcccffd.......",
        "......hffccrccrrrcrcccffd.......",
        "......hffcccccccccccccffd.......",
        "......hfffffffffffffffffd.......",
        "......obbbbbbbbbbbbbbbbbo.......",
        "......hfffffffffffffffffd.......",
        "......hfggffffffffffffffd.......",
        "......hfgGfwwwwwffwwwwwfd.......",
        "......hfffffffffffffffffd.......",
        "......ofllffffffffffffffo.......",
        "......hflLfssssfffsssssfd.......",
        "......hfffffffffffffffffd.......",
        "......hfffffffffffffffffd.......",
        "......ddddoddddoddddodddk.......",
        "..............hbd...............",
        "..............hbd...............",
        "..............hbd...............",
        "..............hbd...............",
        "..............hbd...............",
        "..............hbd...............",
        "..............hbd...............",
        "..........mmmmhbdmmmm...........",
        "..........nnnnnnnnnnn...........",
        ".........hbbbbbbbbbbbd..........",
        "................................",
        "................................",
    };
    /**
     * Icon palette: brass (the mod's sign icon: b, light h, dark d, darkest k), bulb o, felt f, LED backing c and red
     * r, gold medal g/G, silver l/L, bronze z/Z, name lines w (first) and s, walnut n/m.
     */
    static final Map<Character, Integer> ICON_COLOURS = Map.ofEntries(
            Map.entry('b', 0xFFB28D38), Map.entry('h', 0xFFD8BC69), Map.entry('d', 0xFF936B23),
            Map.entry('k', 0xFF553B1C), Map.entry('o', 0xFFFFE38E), Map.entry('f', 0xFF0B231C),
            Map.entry('c', 0xFF381C19), Map.entry('r', 0xFFD6272E), Map.entry('g', 0xFFFFDE7C),
            Map.entry('G', 0xFFC9952F), Map.entry('l', 0xFFD8D8D8), Map.entry('L', 0xFF9A9A9A),
            Map.entry('z', 0xFFC8834A), Map.entry('Z', 0xFF8A5A2E), Map.entry('w', 0xFFF9F5D7),
            Map.entry('s', 0xFFA8A28A), Map.entry('n', 0xFF47291D), Map.entry('m', 0xFF5B3522));

    private LeaderboardPack() {
    }

    static void add(ModJar jar, PackTree tree, Path packSrc) {
        ModelCopier copier = new ModelCopier(jar, tree);
        for (String t : List.of("casinodeco:block/furniture_brass", "casinodeco:block/furniture_wood",
                "casinodeco:block/sign_red_led", "casinodeco:block/sign_warm_led")) {
            copier.texture(t);
        }
        for (Board board : List.of(WALL_BOARD, COLUMN)) {
            String texture = board.panelTexture();
            tree.put("assets/kaboofcasino/textures/" + texture.substring(texture.indexOf(':') + 1) + ".png",
                    Images.write(panel(board)));
            tree.put("assets/kaboofcasino/models/" + board.path() + ".json", Json.bytes(board(board)));
            tree.put("assets/kaboofcasino/items/" + board.path() + ".json",
                    Json.bytes(Json.itemDefinition("kaboofcasino:" + board.path())));
        }
        tree.put("assets/kaboofcasino/models/leaderboard/column_post.json", Json.bytes(post()));
        tree.put("assets/kaboofcasino/items/leaderboard/column_post.json",
                Json.bytes(Json.itemDefinition(COLUMN_POST)));
        item(tree, "leaderboard_wall", WALL_ICON);
        item(tree, "leaderboard_column", COLUMN_ICON);
    }

    /** {@code items/<name>.json}: the flat icon {@code item/<name>} in every display context. */
    private static void item(PackTree tree, String name, String[] map) {
        tree.put("assets/kaboofcasino/textures/item/" + name + ".png", Images.write(icon(map)));
        JsonObject model = new JsonObject();
        model.addProperty("parent", "minecraft:item/generated");
        model.add("textures", Json.object("layer0", new JsonPrimitive("kaboofcasino:item/" + name)));
        tree.put("assets/kaboofcasino/models/item/" + name + ".json", Json.bytes(model));
        tree.put("assets/kaboofcasino/items/" + name + ".json",
                Json.bytes(Json.itemDefinition("kaboofcasino:item/" + name)));
    }

    static BufferedImage icon(String[] map) {
        if (map.length != 32) {
            throw new IllegalArgumentException("an icon has 32 rows, not " + map.length);
        }
        BufferedImage image = Images.blank(32, 32);
        for (int y = 0; y < 32; y++) {
            if (map[y].length() != 32) {
                throw new IllegalArgumentException("icon row " + y + " is " + map[y].length() + " wide");
            }
            for (int x = 0; x < 32; x++) {
                char c = map[y].charAt(x);
                if (c == '.') {
                    continue;
                }
                Integer argb = ICON_COLOURS.get(c);
                if (argb == null) {
                    throw new IllegalArgumentException("no icon colour '" + c + "'");
                }
                image.setRGB(x, y, argb);
            }
        }
        return image;
    }

    /** The LED grid of {@code word}: [row][column] lit, one free column between letters. */
    static boolean[][] leds(String word) {
        int cols = -1;
        for (int i = 0; i < word.length(); i++) {
            cols += letter(word.charAt(i))[0].length() + 1;
        }
        boolean[][] grid = new boolean[7][cols];
        int x = 0;
        for (int i = 0; i < word.length(); i++) {
            String[] rows = letter(word.charAt(i));
            for (int r = 0; r < 7; r++) {
                for (int c = 0; c < rows[r].length(); c++) {
                    grid[r][x + c] = rows[r].charAt(c) == '#';
                }
            }
            x += rows[0].length() + 1;
        }
        return grid;
    }

    private static String[] letter(char c) {
        String[] rows = LETTERS.get(c);
        if (rows == null) {
            throw new IllegalArgumentException("no LED letter '" + c + "'");
        }
        return rows;
    }

    /** One texel coordinate of the panel texture: {@code units} model units from the felt's edge, on the grid. */
    private static int texel(double units) {
        double t = units * TEXELS;
        long r = Math.round(t);
        if (Math.abs(t - r) > 1e-9) {
            throw new IllegalStateException("off the panel's texel grid: " + units);
        }
        return (int) r;
    }

    /**
     * The board's felt (its south face, the whole texture): the mod's panel green, behind the LEDs their dark red
     * backing (every LED grown by half a pitch on each side, as the mod's sign panel has it under its word).
     */
    static BufferedImage panel(Board board) {
        double[] felt = board.feltRect();
        BufferedImage image = Images.blank(texel(felt[2] - felt[0]), texel(felt[3] - felt[1]));
        Images.fill(image, 0, 0, image.getWidth(), image.getHeight(), FELT);
        boolean[][] grid = leds(board.word());
        double x0 = board.ledX(grid[0].length);
        double top = board.ledTop();
        for (int r = 0; r < 7; r++) {
            for (int c = 0; c < grid[r].length; c++) {
                if (!grid[r][c]) {
                    continue;
                }
                double lx = x0 + c * LED - LED / 2;
                double ly = top - r * LED + LED / 2;
                int tx = texel(lx - felt[0]);
                int ty = texel(felt[3] - ly);
                Images.fill(image, tx, ty, texel(2 * LED), texel(2 * LED), BACKING);
            }
        }
        return image;
    }

    /** UV of one face: the whole texture, or the face's own size in model units up to 16 (the grain at its size). */
    private static double[] uv(String side, double dx, double dy, double dz, boolean whole) {
        if (whole) {
            return new double[] {0, 0, 16, 16};
        }
        double w = switch (side) {
            case "east", "west" -> dz;
            default -> dx;
        };
        double h = switch (side) {
            case "up", "down" -> dz;
            default -> dy;
        };
        return new double[] {0, 0, Math.min(16, w), Math.min(16, h)};
    }

    static JsonObject box(double x0, double y0, double z0, double x1, double y1, double z1, String texture,
                          Set<String> faces, boolean wholeTexture, int light) {
        if (!(x0 < x1 && y0 < y1 && z0 < z1)) {
            throw new IllegalArgumentException("empty box " + x0 + "," + y0 + "," + z0 + " .. " + x1 + "," + y1 + ","
                    + z1);
        }
        JsonObject faceObjects = new JsonObject();
        for (String side : SIDES) {
            if (!faces.contains(side)) {
                continue;
            }
            JsonObject face = new JsonObject();
            face.add("uv", Json.array(uv(side, x1 - x0, y1 - y0, z1 - z0, wholeTexture)));
            face.addProperty("texture", texture);
            faceObjects.add(side, face);
        }
        JsonObject element = new JsonObject();
        element.add("from", Json.array(x0, y0, z0));
        element.add("to", Json.array(x1, y1, z1));
        element.add("faces", faceObjects);
        if (light > 0) {
            element.addProperty("light_emission", light);
        }
        return element;
    }

    /**
     * The four brass bands of a frame (left and right full height, bottom and top between them) at z0..z1. With
     * {@code bevel} their front faces show the brass texture's edges ({@code furniture_brass}: a light first and a
     * dark last column) on the band's outer edge, lit from the top left: the left band its light quarter, the right
     * band its dark quarter, the top and bottom bands the same turned by 90 degrees.
     */
    private static void bands(JsonArray el, Board b, double z0, double z1, Set<String> faces, boolean bevel) {
        double r = RIM;
        JsonObject left = box(b.x0() + r, b.y0() + r, z0, b.x0() + BORDER, b.y1() - r, z1, "#brass", faces, true, 0);
        JsonObject right = box(b.x1() - BORDER, b.y0() + r, z0, b.x1() - r, b.y1() - r, z1, "#brass", faces, true, 0);
        JsonObject bottom = box(b.x0() + BORDER, b.y0() + r, z0, b.x1() - BORDER, b.y0() + BORDER, z1, "#brass", faces,
                true, 0);
        JsonObject top = box(b.x0() + BORDER, b.y1() - BORDER, z0, b.x1() - BORDER, b.y1() - r, z1, "#brass", faces,
                true, 0);
        if (bevel) {
            south(left, new double[] {0, 0, 4, 16}, 0);
            south(right, new double[] {12, 0, 16, 16}, 0);
            south(top, new double[] {0, 0, 4, 16}, 90);
            south(bottom, new double[] {12, 0, 16, 16}, 90);
        }
        el.add(left);
        el.add(right);
        el.add(bottom);
        el.add(top);
    }

    /** Sets the south face's uv and texture rotation (90: the uv's left column along the face's top edge). */
    private static void south(JsonObject element, double[] uv, int rotation) {
        JsonObject face = element.getAsJsonObject("faces").getAsJsonObject("south");
        face.add("uv", Json.array(uv));
        if (rotation != 0) {
            face.addProperty("rotation", rotation);
        }
    }

    /** Bulb centres {x, y}: corners and evenly along the middle of the brass bands, about {@link #BULB_SPACING}. */
    static List<double[]> bulbs(Board b) {
        double bx0 = b.x0() + RIM + BAND / 2;
        double bx1 = b.x1() - RIM - BAND / 2;
        double by0 = b.y0() + RIM + BAND / 2;
        double by1 = b.y1() - RIM - BAND / 2;
        int nx = (int) Math.round((bx1 - bx0) / BULB_SPACING);
        int ny = (int) Math.round((by1 - by0) / BULB_SPACING);
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i <= nx; i++) {
            double x = bx0 + (bx1 - bx0) * i / nx;
            out.add(new double[] {x, by0});
            out.add(new double[] {x, by1});
        }
        for (int j = 1; j < ny; j++) {
            double y = by0 + (by1 - by0) * j / ny;
            out.add(new double[] {bx0, y});
            out.add(new double[] {bx1, y});
        }
        return out;
    }

    /** A board: walnut body, felt, brass frame (and back frame), divider, bulbs, LED word (see the class comment). */
    static JsonObject board(Board b) {
        JsonArray el = new JsonArray();
        double core0 = b.back() + (b.backFrame() ? BRASS_RELIEF : 0);
        double feltBack = b.felt() - FELT_DEPTH;
        el.add(box(b.x0(), b.y0(), core0, b.x1(), b.y1(), feltBack, "#wood", ALL, false, 0));
        double[] felt = b.feltRect();
        el.add(box(felt[0], felt[1], feltBack, felt[2], felt[3], b.felt(), "#panel", Set.of("south"), true, 0));
        bands(el, b, feltBack, b.felt() + BRASS_RELIEF, FRONT, true);
        if (b.backFrame()) {
            bands(el, b, b.back(), core0, BACK, false);
        }
        double dividerY = b.y1() - HEADER;
        JsonObject divider = box(felt[0], dividerY, b.felt(), felt[2], dividerY + DIVIDER, b.felt() + BRASS_RELIEF,
                "#brass", Set.of("south", "up", "down"), true, 0);
        south(divider, new double[] {2, 0, 14, 16}, 90);
        el.add(divider);
        double bulbZ = b.felt() + BRASS_RELIEF;
        for (double[] c : bulbs(b)) {
            el.add(box(c[0] - BULB / 2, c[1] - BULB / 2, bulbZ, c[0] + BULB / 2, c[1] + BULB / 2,
                    bulbZ + BULB_RELIEF, "#bulb", FRONT, true, 15));
        }
        // the LEDs, one element per LED like the mod's signs (each shows the whole LED texture)
        boolean[][] grid = leds(b.word());
        double x0 = b.ledX(grid[0].length);
        double top = b.ledTop();
        for (int r = 0; r < 7; r++) {
            for (int c = 0; c < grid[r].length; c++) {
                if (grid[r][c]) {
                    double x = x0 + c * LED;
                    double y = top - (r + 1) * LED;
                    el.add(box(x, y, b.felt(), x + LED, y + LED, b.felt() + LED_RELIEF, "#led", FRONT, true, 15));
                }
            }
        }
        JsonObject textures = new JsonObject();
        textures.addProperty("particle", "casinodeco:block/furniture_brass");
        textures.addProperty("wood", "casinodeco:block/furniture_wood");
        textures.addProperty("brass", "casinodeco:block/furniture_brass");
        textures.addProperty("panel", b.panelTexture());
        textures.addProperty("led", "casinodeco:block/sign_red_led");
        textures.addProperty("bulb", "casinodeco:block/sign_warm_led");
        JsonObject model = new JsonObject();
        model.add("textures", textures);
        model.add("elements", el);
        return model;
    }

    /** The column's bottom cell: brass foot, walnut plinth, brass ring, brass pole up to the cell's top, collar. */
    static JsonObject post() {
        JsonArray el = new JsonArray();
        el.add(box(3, 0, 3, 13, 0.5, 13, "#brass", ALL, true, 0));
        el.add(box(3.5, 0.5, 3.5, 12.5, 2.5, 12.5, "#wood", ALL, false, 0));
        el.add(box(6.5, 2.5, 6.5, 9.5, 3.5, 9.5, "#brass", ALL, true, 0));
        el.add(box(7, 3.5, 7, 9, 15, 9, "#brass", ALL, true, 0));
        el.add(box(6.5, 15, 6.5, 9.5, 16, 9.5, "#brass", ALL, true, 0));
        JsonObject textures = new JsonObject();
        textures.addProperty("particle", "casinodeco:block/furniture_brass");
        textures.addProperty("wood", "casinodeco:block/furniture_wood");
        textures.addProperty("brass", "casinodeco:block/furniture_brass");
        JsonObject model = new JsonObject();
        model.add("textures", textures);
        model.add("elements", el);
        return model;
    }
}
