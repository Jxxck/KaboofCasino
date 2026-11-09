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
 * The chip shop ("Chip-Shop", PLAN-2 C6; not in MineVegas): the cashier's counter with a glass display case on it
 * and a CHIP-SHOP sign over it. The plugin's rig is the cashier's ({@code CashierRig}: two {@code black_marble_bar}
 * ends with their tops) with these two models in place of the screen, the register and the chip pile:
 * <ul>
 *   <li>{@code kaboofcasino:chipshop/sign}, placed like the cashier's screen ({@code Part(model, 0, 0, 0.5, 0)}):
 *       coordinates in the anchor cell's frame (x 0..32 over both cells from the viewer's left, y 16..32 the upper
 *       block, z 16 the front). Two brass posts at the counter's front corners and a brass rail carry a sign built
 *       like the cashier's KASSE sign (walnut back, the mod's dark green sign panel, brass frame, warm bulbs) whose red
 *       LEDs spell {@value #WORD} in the mod's 7-row LED letters at 0.4 pixels per LED. No glass: the display case
 *       stands behind the posts;</li>
 *   <li>{@code kaboofcasino:chipshop/display}, placed like the cashier's register
 *       ({@code Part(model, 0, 0, 1.5, -0.06)}): its y 0 is the counter top, x 0..32 both cells, z = the counter's z
 *       + {@value #Z_SHIFT} (the part stands 0.06 blocks back). A walnut plinth, the mod's clear casino glass in brass
 *       edges, a ruby velvet floor and on it miniatures of what the shop sells: in the back a slot machine, a
 *       fortune wheel and a plinko (the mod's item icons, {@value #ICON_DENSITY} texels per px) and three deco block
 *       samples ({@link #SAMPLES}), in front chip stacks in the six chip colours ({@link ChipPack}); a warm light
 *       strip under the front top edge. Nothing moves;</li>
 *   <li>{@code kaboofcasino:chip_shop}: the placing item, a 32x32 icon ({@link #ICON}) in every display context (the
 *       display case with chip stacks under a red SHOP sign, drawn like the leaderboards' icons).</li>
 * </ul>
 * Only opaque, cutout (alpha 0 or 255) or the mod's glass textures are used, so the client sorts the glass into its
 * translucent pass and the miniatures stay visible behind it.
 */
final class ChipShopPack {

    static final List<String> SOUND_EVENTS = List.of();
    static final List<String> LANG_PREFIXES = List.of();
    static final String WORD = "CHIP-SHOP";
    static final String SIGN = "kaboofcasino:chipshop/sign";
    static final String DISPLAY = "kaboofcasino:chipshop/display";
    static final String ITEM = "kaboofcasino:chip_shop";
    /** The display texture (miniature sheet): 96x64 (6 texels per uv unit across, 4 down). */
    static final String SHEET = "kaboofcasino:block/chipshop_display";
    static final int SHEET_W = 96;
    static final int SHEET_H = 64;
    /** The mod's item icons on the sheet (32x32 each, side by side at x 0, 32, 64). */
    static final List<String> ICONS = List.of("roulettecasino:item/slot_machine", "roulettecasino:item/fortune_wheel",
            "roulettecasino:item/plinko");

    /** LED letters (7 rows, top first) in the style of the mod's sign letters; C, I, O, S verbatim from CASINO. */
    static final Map<Character, String[]> LETTERS = Map.of(
            'C', new String[] {".####", "##...", "#....", "#....", "#....", "##...", ".####"},
            'H', new String[] {"#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"},
            'I', new String[] {"#####", "..#..", "..#..", "..#..", "..#..", "..#..", "#####"},
            'P', new String[] {"####.", "#..##", "#...#", "#..##", "####.", "#....", "#...."},
            'S', new String[] {".####", "##...", "#....", ".###.", "....#", "...##", "####."},
            'O', new String[] {".###.", "##.##", "#...#", "#...#", "#...#", "##.##", ".###."},
            '-', new String[] {"...", "...", "...", "###", "...", "...", "..."});

    // ---- the sign (anchor cell frame) ------------------------------------------------------------------------------
    /** LED pitch (px): finer than the cashier's 0.5, so the nine letters keep the same air around them. */
    static final double LED = 0.4;
    /** Posts and rail: the cashier screen's. */
    static final double POST_Z0 = 14;
    static final double POST_Z1 = 15.25;
    static final double RAIL_Y0 = 25;
    static final double RAIL_Y1 = 26;
    static final double SIGN_X0 = 1.5;
    static final double SIGN_X1 = 30.5;
    static final double SIGN_Y0 = 26;
    static final double SIGN_Y1 = 32;
    static final double SIGN_Z = 15;

    // ---- the display case (its own frame: y 0 = counter top, z = counter z + Z_SHIFT) -----------------------------
    /** The display part stands 0.06 blocks further back than the sign: z(display) = z(sign) + 0.96. */
    static final double Z_SHIFT = 0.96;
    /** The plinth. */
    static final double PLINTH_X0 = 1.5;
    static final double PLINTH_X1 = 30.5;
    static final double PLINTH_H = 1;
    static final double CASE_Z0 = 2.5;
    static final double CASE_Z1 = 14;
    /** The glass box on the plinth. */
    static final double GLASS_X0 = 2;
    static final double GLASS_X1 = 30;
    static final double GLASS_Y1 = 8;
    static final double PANE = 0.25;
    static final double EDGE = 0.25;
    static final double FLOOR = PLINTH_H + 0.25;

    /** Sheet regions (texels): the icons in the top half, the chip strips below. */
    static final int WHEEL_X = 32;
    static final int PLINKO_X = 64;
    static final int CHIPS_Y = 32;
    /**
     * One chip strip: 8 texels wide (a 2 px stack at 4 texels per px), {@value #CHIP_ROWS} rows per chip (0.75 px:
     * the mod's insert row, plain row and dark line), eight chips; its 8x8 top below it.
     */
    static final int CHIP_W = 8;
    static final int CHIP_ROWS = 3;
    static final int CHIP_MAX = 8;
    static final int CHIP_TOP_Y = CHIPS_Y + CHIP_ROWS * CHIP_MAX;
    static final int CHIP_PITCH = 10;
    /** A chip's height in model px and a stack's width. */
    static final double CHIP_H = CHIP_ROWS / 4.0;
    static final double STACK_W = 2;
    /** The mod's dark line between the chips of a stack ({@code casino_chip_pile.png} icon). */
    static final int CHIP_LINE = 0xFF281917;
    /** The chip colours in sheet order (denominations of {@link ChipPack}). */
    static final int[] CHIP_ORDER = {1, 5, 25, 100, 500, 1000};

    private ChipShopPack() {
    }

    static void add(ModJar jar, PackTree tree, Path packSrc) {
        ModelCopier copier = new ModelCopier(jar, tree);
        for (String t : List.of("casinodeco:block/casino_glass_clear", "casinodeco:block/furniture_brass",
                "casinodeco:block/furniture_wood", "casinodeco:block/sign_red_led", "casinodeco:block/sign_warm_led",
                "casinodeco:block/ruby_velvet")) {
            copier.texture(t);
        }
        SAMPLES.forEach(copier::texture);
        // the sign's panel: the cashier's crop of the mod's sign panel (same bytes, so one file for both)
        BufferedImage panel = Images.crop(Images.argb(Images.read(jar.bytes(
                "assets/casinodeco/textures/block/sign_panel.png"))), 40, 6, 16, 16);
        tree.put("assets/kaboofcasino/textures/block/cashier_sign_panel.png", Images.write(panel));
        List<BufferedImage> icons = ICONS.stream().map(id -> Images.read(jar.bytes(texturePath(id)))).toList();
        tree.put(texturePath(SHEET), Images.write(sheet(icons)));
        tree.put("assets/kaboofcasino/models/chipshop/sign.json", Json.bytes(sign()));
        tree.put("assets/kaboofcasino/items/chipshop/sign.json", Json.bytes(Json.itemDefinition(SIGN)));
        tree.put("assets/kaboofcasino/models/chipshop/display.json", Json.bytes(display()));
        tree.put("assets/kaboofcasino/items/chipshop/display.json", Json.bytes(Json.itemDefinition(DISPLAY)));
        // the item: the icon everywhere (GUI, hands, ground, frames)
        tree.put("assets/kaboofcasino/textures/item/chip_shop.png", Images.write(icon()));
        JsonObject iconModel = new JsonObject();
        iconModel.addProperty("parent", "minecraft:item/generated");
        iconModel.add("textures", Json.object("layer0", new JsonPrimitive("kaboofcasino:item/chip_shop")));
        tree.put("assets/kaboofcasino/models/item/chip_shop.json", Json.bytes(iconModel));
        tree.put("assets/kaboofcasino/items/chip_shop.json",
                Json.bytes(Json.itemDefinition("kaboofcasino:item/chip_shop")));
    }

    static String texturePath(String id) {
        return "assets/" + id.substring(0, id.indexOf(':')) + "/textures/" + id.substring(id.indexOf(':') + 1)
                + ".png";
    }

    // ---- LEDs -------------------------------------------------------------------------------------------------------

    /** The LED grid of {@code word}: [row][column] lit, one free column between letters (letters of any width). */
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

    // ---- elements ---------------------------------------------------------------------------------------------------

    /** One face: texture reference and uv (u0, v0, u1, v1). */
    record Face(String texture, double[] uv) {
        static Face of(String texture, double u0, double v0, double u1, double v1) {
            return new Face(texture, new double[] {u0, v0, u1, v1});
        }

        static Face full(String texture) {
            return of(texture, 0, 0, 16, 16);
        }
    }

    static final List<String> SIDES = List.of("north", "east", "south", "west", "up", "down");

    /** A box with the given faces (side name to face; sides missing from the map have no face). */
    static JsonObject box(double x0, double y0, double z0, double x1, double y1, double z1, Map<String, Face> faces,
                          int light) {
        if (x0 > x1 || y0 > y1 || z0 > z1) {
            throw new IllegalArgumentException("inverted box " + x0 + "," + y0 + "," + z0 + " .. " + x1 + "," + y1
                    + "," + z1);
        }
        JsonObject out = new JsonObject();
        for (String side : SIDES) {
            Face f = faces.get(side);
            if (f == null) {
                continue;
            }
            JsonObject face = new JsonObject();
            face.add("uv", Json.array(f.uv()));
            face.addProperty("texture", f.texture());
            out.add(side, face);
        }
        JsonObject element = new JsonObject();
        element.add("from", Json.array(x0, y0, z0));
        element.add("to", Json.array(x1, y1, z1));
        element.add("faces", out);
        if (light > 0) {
            element.addProperty("light_emission", light);
        }
        return element;
    }

    /** A box with one face on every side. */
    static JsonObject box(double x0, double y0, double z0, double x1, double y1, double z1, Face face, int light) {
        Map<String, Face> all = new LinkedHashMap<>();
        for (String side : SIDES) {
            all.put(side, face);
        }
        return box(x0, y0, z0, x1, y1, z1, all, light);
    }

    /** A box whose six sides show the whole texture. */
    static JsonObject box(double x0, double y0, double z0, double x1, double y1, double z1, String texture, int light) {
        return box(x0, y0, z0, x1, y1, z1, Face.full(texture), light);
    }

    // ---- the sign ---------------------------------------------------------------------------------------------------

    /** Posts, rail and the CHIP-SHOP sign (see the class comment). */
    static JsonObject sign() {
        JsonArray el = new JsonArray();
        el.add(box(0.5, 16, POST_Z0, 1.5, RAIL_Y1, POST_Z1, "#brass", 0));
        el.add(box(30.5, 16, POST_Z0, 31.5, RAIL_Y1, POST_Z1, "#brass", 0));
        el.add(box(1.5, RAIL_Y0, POST_Z0, 30.5, RAIL_Y1, POST_Z1, "#brass", 0));
        // the sign: walnut back, panel, brass frame
        el.add(box(SIGN_X0, SIGN_Y0, 14.25, SIGN_X1, SIGN_Y1, SIGN_Z, "#wood", 0));
        el.add(box(SIGN_X0 + 0.5, SIGN_Y0 + 0.5, SIGN_Z, SIGN_X1 - 0.5, SIGN_Y1 - 0.5, SIGN_Z + 0.1, "#panel", 0));
        el.add(box(SIGN_X0, SIGN_Y0, SIGN_Z, SIGN_X0 + 0.5, SIGN_Y1, SIGN_Z + 0.35, "#gold", 0));
        el.add(box(SIGN_X1 - 0.5, SIGN_Y0, SIGN_Z, SIGN_X1, SIGN_Y1, SIGN_Z + 0.35, "#gold", 0));
        el.add(box(SIGN_X0 + 0.5, SIGN_Y0, SIGN_Z, SIGN_X1 - 0.5, SIGN_Y0 + 0.5, SIGN_Z + 0.35, "#gold", 0));
        el.add(box(SIGN_X0 + 0.5, SIGN_Y1 - 0.5, SIGN_Z, SIGN_X1 - 0.5, SIGN_Y1, SIGN_Z + 0.35, "#gold", 0));
        // warm bulbs on the top and bottom bar, every 1.5 px
        for (double x = SIGN_X0 + 1.25; x + 0.25 <= SIGN_X1 - 1; x += 1.5) {
            el.add(box(x, SIGN_Y0 + 0.125, SIGN_Z + 0.35, x + 0.25, SIGN_Y0 + 0.375, SIGN_Z + 0.5, "#bulb", 15));
            el.add(box(x, SIGN_Y1 - 0.375, SIGN_Z + 0.35, x + 0.25, SIGN_Y1 - 0.125, SIGN_Z + 0.5, "#bulb", 15));
        }
        // the LEDs, one element per horizontal run of a row
        boolean[][] grid = leds(WORD);
        int cols = grid[0].length;
        double x0 = (SIGN_X0 + SIGN_X1) / 2 - cols * LED / 2;
        double yTop = (SIGN_Y0 + SIGN_Y1) / 2 + 7 * LED / 2;
        for (int r = 0; r < 7; r++) {
            int c = 0;
            while (c < cols) {
                if (!grid[r][c]) {
                    c++;
                    continue;
                }
                int start = c;
                while (c < cols && grid[r][c]) {
                    c++;
                }
                el.add(box(x0 + start * LED, yTop - (r + 1) * LED, SIGN_Z + 0.1, x0 + c * LED, yTop - r * LED,
                        SIGN_Z + 0.35, "#led", 15));
            }
        }
        JsonObject textures = new JsonObject();
        textures.addProperty("particle", "casinodeco:block/furniture_brass");
        textures.addProperty("brass", "casinodeco:block/furniture_brass");
        textures.addProperty("gold", "casinodeco:block/furniture_brass");
        textures.addProperty("wood", "casinodeco:block/furniture_wood");
        textures.addProperty("panel", "kaboofcasino:block/cashier_sign_panel");
        textures.addProperty("led", "casinodeco:block/sign_red_led");
        textures.addProperty("bulb", "casinodeco:block/sign_warm_led");
        JsonObject model = new JsonObject();
        model.add("textures", textures);
        model.add("elements", el);
        return model;
    }

    // ---- the display case -------------------------------------------------------------------------------------------

    /** A face showing sheet texels (x0, y0)..(x1, y1) (uv units are 1/16 of the sheet on each axis). */
    private static Face sheet(double x0, double y0, double x1, double y1) {
        return Face.of("#sheet", x0 * 16 / SHEET_W, y0 * 16 / SHEET_H, x1 * 16 / SHEET_W, y1 * 16 / SHEET_H);
    }

    /** The slot machine icon's body (texels 5..26 x 2..31 of the mod's 32x32 icon): opaque, 21 x 29. */
    static final int[] MACHINE = {5, 2, 26, 31};
    /** Its lever beside the body (texels 26..29 x 10..20). */
    static final int[] LEVER = {26, 10, 29, 20};
    /** The fortune wheel icon's wheel and stand. */
    static final int[] WHEEL = {2, 1, 30, 32};
    /** The plinko icon's cabinet without the wider foot's ends (opaque, 26 x 29). */
    static final int[] PLINKO = {3, 2, 29, 31};
    /** Texels per model pixel of the icon miniatures. */
    static final double ICON_DENSITY = 5;
    /** Back row: the slot machine (box), the fortune wheel (card), the block samples (two cubes, one on top). */
    static final double MACHINE_X = 3.5;
    static final double MACHINE_Z0 = 4.5;
    static final double MACHINE_Z1 = 7.5;
    static final double WHEEL_POS_X = 9.5;
    static final double WHEEL_Z = 6;
    static final double PLINKO_POS_X = 16;
    static final double PLINKO_Z0 = 5;
    static final double PLINKO_Z1 = 7;
    static final double CUBE = 2.75;
    static final double CUBES_X = 22.5;
    static final double CUBES_Z = 4.75;
    /** The block samples: the mod's deco textures, bottom left, bottom right, top. */
    static final List<String> SAMPLES = List.of("casinodeco:block/emerald_trellis",
            "casinodeco:block/ivory_black_checkers", "casinodeco:block/ivory_marble");

    /** A chip stack of colour {@code k} (sheet order) standing at (x, z) on the floor, {@code chips} chips tall. */
    static JsonObject chipStack(int k, double x, double z, int chips) {
        if (chips < 1 || chips > CHIP_MAX) {
            throw new IllegalArgumentException("a stack of " + chips + " chips");
        }
        int sx = k * CHIP_PITCH;
        Face side = sheet(sx, CHIPS_Y, sx + CHIP_W, CHIPS_Y + chips * CHIP_ROWS);
        Map<String, Face> faces = new LinkedHashMap<>();
        for (String s : List.of("north", "east", "south", "west")) {
            faces.put(s, side);
        }
        faces.put("up", sheet(sx, CHIP_TOP_Y, sx + CHIP_W, CHIP_TOP_Y + CHIP_W));
        return box(x, FLOOR, z, x + STACK_W, FLOOR + chips * CHIP_H, z + STACK_W, faces, 0);
    }

    /** The front row of chip stacks: {colour (sheet order), x, z, chips}. */
    static final double[][] STACKS = {
        {1, 3, 10.5, 4}, {3, 5.5, 11, 3}, {0, 9, 10.5, 2}, {5, 11.5, 11, 4}, {4, 14, 10.5, 3},
        {2, 17.5, 11, 3}, {1, 21, 10.5, 2}, {3, 23.5, 11, 3}, {5, 27, 10.5, 4}};

    /** Plinth, floor, glass in brass edges, light strip and the miniatures (see the class comment). */
    static JsonObject display() {
        JsonArray el = new JsonArray();
        double y1 = GLASS_Y1;
        // walnut plinth, ruby velvet floor
        Map<String, Face> plinth = new LinkedHashMap<>();
        for (String s : List.of("north", "south")) {
            plinth.put(s, Face.of("#wood", 0, 0, 16, 1));
        }
        for (String s : List.of("east", "west")) {
            plinth.put(s, Face.of("#wood", 0, 0, 11.5, 1));
        }
        plinth.put("up", Face.of("#wood", 0, 0, 16, 11.5));
        el.add(box(PLINTH_X0, 0, CASE_Z0 - 0.5, PLINTH_X1, PLINTH_H, CASE_Z1 + 0.5, plinth, 0));
        el.add(box(GLASS_X0 + PANE, PLINTH_H, CASE_Z0 + PANE, GLASS_X1 - PANE, FLOOR, CASE_Z1 - PANE,
                Map.of("up", Face.full("#velvet")), 0));
        // the glass: front and top in four parts each (closer sorting of the translucent quads), back and ends whole
        double w = (GLASS_X1 - GLASS_X0) / 4;
        for (int i = 0; i < 4; i++) {
            double a = GLASS_X0 + i * w;
            el.add(box(a, PLINTH_H, CASE_Z1 - PANE, a + w, y1, CASE_Z1,
                    Map.of("south", Face.full("#glass"), "north", Face.full("#glass")), 0));
            el.add(box(a, y1 - PANE, CASE_Z0, a + w, y1, CASE_Z1,
                    Map.of("up", Face.full("#glass"), "down", Face.full("#glass")), 0));
        }
        el.add(box(GLASS_X0, PLINTH_H, CASE_Z0, GLASS_X1, y1, CASE_Z0 + PANE,
                Map.of("south", Face.full("#glass"), "north", Face.full("#glass")), 0));
        for (double x : new double[] {GLASS_X0, GLASS_X1 - PANE}) {
            el.add(box(x, PLINTH_H, CASE_Z0, x + PANE, y1, CASE_Z1,
                    Map.of("east", Face.full("#glass"), "west", Face.full("#glass")), 0));
        }
        // brass edges: four uprights, the top frame, the bottom rails on the plinth
        Face brass = Face.of("#brass", 4, 4, 12, 12);
        for (double x : new double[] {GLASS_X0, GLASS_X1}) {
            for (double z : new double[] {CASE_Z0, CASE_Z1}) {
                el.add(box(x - EDGE, PLINTH_H, z - EDGE, x + EDGE, y1 + EDGE, z + EDGE, brass, 0));
            }
        }
        for (double z : new double[] {CASE_Z0, CASE_Z1}) {
            el.add(box(GLASS_X0 + EDGE, y1 - EDGE, z - EDGE, GLASS_X1 - EDGE, y1 + EDGE, z + EDGE, brass, 0));
            el.add(box(GLASS_X0 + EDGE, PLINTH_H, z - EDGE, GLASS_X1 - EDGE, PLINTH_H + 2 * EDGE, z + EDGE, brass,
                    0));
        }
        for (double x : new double[] {GLASS_X0, GLASS_X1}) {
            el.add(box(x - EDGE, y1 - EDGE, CASE_Z0 + EDGE, x + EDGE, y1 + EDGE, CASE_Z1 - EDGE, brass, 0));
            el.add(box(x - EDGE, PLINTH_H, CASE_Z0 + EDGE, x + EDGE, PLINTH_H + 2 * EDGE, CASE_Z1 - EDGE, brass, 0));
        }
        // the light strip under the front top edge
        el.add(box(GLASS_X0 + 1, y1 - 0.75, CASE_Z1 - 0.75, GLASS_X1 - 1, y1 - 0.5, CASE_Z1 - 0.5,
                Face.of("#bulb", 0, 0, 16, 16), 15));
        // the slot machine: the icon's body on a box, its lever beside it
        double mw = (MACHINE[2] - MACHINE[0]) / ICON_DENSITY;
        double mh = (MACHINE[3] - MACHINE[1]) / ICON_DENSITY;
        Map<String, Face> machine = new LinkedHashMap<>();
        machine.put("south", sheet(MACHINE[0], MACHINE[1], MACHINE[2], MACHINE[3]));
        Face dark = sheet(MACHINE[0], 20, MACHINE[0] + 1, 30);
        machine.put("east", dark);
        machine.put("west", dark);
        machine.put("north", dark);
        machine.put("up", sheet(MACHINE[0] + 1, MACHINE[1], MACHINE[2] - 1, MACHINE[1] + 1));
        el.add(box(MACHINE_X, FLOOR, MACHINE_Z0, MACHINE_X + mw, FLOOR + mh, MACHINE_Z1, machine, 0));
        double lw = (LEVER[2] - LEVER[0]) / ICON_DENSITY;
        double ly1 = FLOOR + mh - (LEVER[1] - MACHINE[1]) / ICON_DENSITY;
        double ly0 = ly1 - (LEVER[3] - LEVER[1]) / ICON_DENSITY;
        double lz = (MACHINE_Z0 + MACHINE_Z1) / 2;
        el.add(box(MACHINE_X + mw, ly0, lz, MACHINE_X + mw + lw, ly1, lz,
                Map.of("south", sheet(LEVER[0], LEVER[1], LEVER[2], LEVER[3]),
                        "north", sheet(LEVER[2], LEVER[1], LEVER[0], LEVER[3])), 0));
        // the fortune wheel: the icon as a standing card
        double ww = (WHEEL[2] - WHEEL[0]) / ICON_DENSITY;
        double wh = (WHEEL[3] - WHEEL[1]) / ICON_DENSITY;
        el.add(box(WHEEL_POS_X, FLOOR, WHEEL_Z, WHEEL_POS_X + ww, FLOOR + wh, WHEEL_Z,
                Map.of("south", sheet(WHEEL_X + WHEEL[0], WHEEL[1], WHEEL_X + WHEEL[2], WHEEL[3]),
                        "north", sheet(WHEEL_X + WHEEL[2], WHEEL[1], WHEEL_X + WHEEL[0], WHEEL[3])), 0));
        // the plinko: the icon's cabinet on a box (sides from its brass frame column, top from its top row)
        double pw = (PLINKO[2] - PLINKO[0]) / ICON_DENSITY;
        double ph = (PLINKO[3] - PLINKO[1]) / ICON_DENSITY;
        Map<String, Face> plinko = new LinkedHashMap<>();
        plinko.put("south", sheet(PLINKO_X + PLINKO[0], PLINKO[1], PLINKO_X + PLINKO[2], PLINKO[3]));
        Face frame = sheet(PLINKO_X + PLINKO[0], PLINKO[1] + 4, PLINKO_X + PLINKO[0] + 1, PLINKO[3] - 4);
        plinko.put("east", frame);
        plinko.put("west", frame);
        plinko.put("north", frame);
        plinko.put("up", sheet(PLINKO_X + PLINKO[0] + 1, PLINKO[1], PLINKO_X + PLINKO[2] - 1, PLINKO[1] + 1));
        el.add(box(PLINKO_POS_X, FLOOR, PLINKO_Z0, PLINKO_POS_X + pw, FLOOR + ph, PLINKO_Z1, plinko, 0));
        // deco block samples: two cubes and one on top of them
        double c = CUBE;
        el.add(box(CUBES_X, FLOOR, CUBES_Z, CUBES_X + c, FLOOR + c, CUBES_Z + c, "#sample_0", 0));
        el.add(box(CUBES_X + c + 0.5, FLOOR, CUBES_Z, CUBES_X + 2 * c + 0.5, FLOOR + c, CUBES_Z + c, "#sample_1", 0));
        el.add(box(CUBES_X + c / 2 + 0.25, FLOOR + c, CUBES_Z, CUBES_X + 1.5 * c + 0.25, FLOOR + 2 * c, CUBES_Z + c,
                "#sample_2", 0));
        for (double[] st : STACKS) {
            el.add(chipStack((int) st[0], st[1], st[2], (int) st[3]));
        }
        JsonObject textures = new JsonObject();
        textures.addProperty("particle", "casinodeco:block/furniture_brass");
        textures.addProperty("brass", "casinodeco:block/furniture_brass");
        textures.addProperty("wood", "casinodeco:block/furniture_wood");
        textures.addProperty("glass", "casinodeco:block/casino_glass_clear");
        textures.addProperty("velvet", "casinodeco:block/ruby_velvet");
        for (int i = 0; i < SAMPLES.size(); i++) {
            textures.addProperty("sample_" + i, SAMPLES.get(i));
        }
        textures.addProperty("bulb", "casinodeco:block/sign_warm_led");
        textures.addProperty("sheet", SHEET);
        JsonObject model = new JsonObject();
        model.add("textures", textures);
        model.add("elements", el);
        return model;
    }

    // ---- the miniature sheet ----------------------------------------------------------------------------------------

    /**
     * 96x64: the mod's slot machine, fortune wheel and plinko icons ({@link #ICONS}) side by side, below them per chip
     * colour
     * ({@link #CHIP_ORDER}, {@value #CHIP_PITCH} texels apart) an 8 x 24 side strip like the mod's chip stacks (per
     * chip a row with two edge inserts, a plain row and the dark line under it; light left and dark right column) and
     * an 8x8 top (insert ring and gold inlay centre). Only alpha 0 or 255.
     */
    static BufferedImage sheet(List<BufferedImage> icons) {
        BufferedImage out = Images.blank(SHEET_W, SHEET_H);
        for (int i = 0; i < icons.size(); i++) {
            BufferedImage icon = icons.get(i);
            if (icon.getWidth() != 32 || icon.getHeight() != 32) {
                throw new IllegalStateException("unexpected icon size " + icon.getWidth() + "x" + icon.getHeight());
            }
            Images.copy(Images.argb(icon), 0, 0, 32, 32, out, 32 * i, 0);
        }
        for (int k = 0; k < CHIP_ORDER.length; k++) {
            ChipPack.Palette p = ChipPack.palette(CHIP_ORDER[k]);
            int x0 = k * CHIP_PITCH;
            int insert = p.insert();
            int[] inserts = {p.light(), p.body(), insert, p.body(), p.body(), insert, p.body(), p.dark()};
            int[] plain = {p.light(), p.body(), p.body(), p.body(), p.body(), p.body(), p.body(), p.dark()};
            for (int chip = 0; chip < CHIP_MAX; chip++) {
                for (int c = 0; c < CHIP_W; c++) {
                    out.setRGB(x0 + c, CHIPS_Y + chip * CHIP_ROWS, inserts[c]);
                    out.setRGB(x0 + c, CHIPS_Y + chip * CHIP_ROWS + 1, plain[c]);
                    out.setRGB(x0 + c, CHIPS_Y + chip * CHIP_ROWS + 2, CHIP_LINE);
                }
            }
            String[] top = {"BBBBBBBB", "BBIIIIBB", "BIBBBBIB", "BIBRRBIB", "BIBRRBIB", "BIBBBBIB", "BBIIIIBB",
                "BBBBBBBB"};
            for (int y = 0; y < CHIP_W; y++) {
                for (int x = 0; x < CHIP_W; x++) {
                    char role = top[y].charAt(x);
                    out.setRGB(x0 + x, CHIP_TOP_Y + y, role == 'R' ? p.ring() : role == 'I' ? insert : p.body());
                }
            }
        }
        for (int y = 0; y < SHEET_H; y++) {
            for (int x = 0; x < SHEET_W; x++) {
                int a = out.getRGB(x, y) >>> 24;
                if (a != 0 && a != 255) {
                    throw new IllegalStateException("half transparent sheet texel at " + x + "," + y);
                }
            }
        }
        return out;
    }

    // ---- the item icon ----------------------------------------------------------------------------------------------

    /**
     * The icon: a red SHOP sign (the leaderboard icons' brass frame with bulbs, dark green felt, the LED word on its
     * dark red backing) on two brass posts over a glass display case: the warm light strip, chip stacks like the
     * mod's (a row with an edge insert and the dark line per chip, a lit top) in red, green, black and white on ruby
     * velvet, the black marble counter top under it.
     */
    static final String[] ICON = {
        "................................",
        "...hhohhhohhhohhhohhhohhhohhk...",
        "...hfffccccccccccccccccccfffd...",
        "...hfffcrrrcrcrcrrrcrrrccfffd...",
        "...hfffcrcccrcrcrcrcrcrccfffd...",
        "...hfffcrrrcrrrcrcrcrrrccfffd...",
        "...hfffcccrcrcrcrcrcrccccfffd...",
        "...hfffcrrrcrcrcrrrcrccccfffd...",
        "...hfffccccccccccccccccccfffd...",
        "...ddodddodddodddodddodddoddd...",
        ".....hd..................hd.....",
        "..hhhhhhhhhhhhhhhhhhhhhhhhhhhd..",
        "..hYYYYYYYYYYYYYYYYYYYYYYYYYYd..",
        "..hqqqqQqqggggGqqqqqqqqqqqqqqd..",
        "..hqqqQqqqgGGijqqqqqqqqqqqQqqd..",
        "..hqqQqqqqeeeeeqqqqqqqqqqQqqqd..",
        "..hqqqqqqqgiGGjqqqqqqqqqQqqqqd..",
        "..hqLLLLRqeeeeeqqqqqqqWWWWWqqd..",
        "..hqLRRiDqgGGijqqqqqqqWWWtwqqd..",
        "..hqeeeeeqeeeeeqnnnnKqeeeeeqqd..",
        "..hqLiRRDqgiGGjqniKKNqWtWWwqqd..",
        "..hqeeeeeqeeeeeqeeeeeqeeeeeqqd..",
        "..hqLRRiDqgGGijqnKKiNqWWWtwqqd..",
        "..hqeeeeeqeeeeeqeeeeeqeeeeeqqd..",
        "..hqLiRRDqgiGGjqniKKNqWtWWwqqd..",
        "..hqeeeeeqeeeeeqeeeeeqeeeeeqqd..",
        "..hvvvvvvvvvvvvvvvvvvvvvvvvvvd..",
        "..bbbbbbbbbbbbbbbbbbbbbbbbbbbb..",
        ".yyyyyyyyyyyyyyyyyyyyyyyyyyyyyy.",
        ".xxxxxxxxxxxxxxxxxxxxxxxxxxxxxx.",
        ".kkkkkkkkkkkkkkkkkkkkkkkkkkkkkk.",
        "................................"
    };
    /**
     * Icon palette: brass b/h/d, bulb o, LED felt f, backing c and red r (the leaderboard icons'), light strip Y,
     * glass q with highlights Q, ruby velvet v, the chips' light/body/dark tones (ChipPack's red L/R/D, green g/G/j,
     * black n/K/N, white W/w) with their inserts i and t and the dark line e, the counter's marble y/x and shadow k.
     */
    static final Map<Character, Integer> ICON_COLOURS = Map.ofEntries(
            Map.entry('b', 0xFFB28D38), Map.entry('h', 0xFFD8BC69), Map.entry('d', 0xFF936B23),
            Map.entry('o', 0xFFFFE38E), Map.entry('f', 0xFF0B231C), Map.entry('c', 0xFF381C19),
            Map.entry('r', 0xFFD6272E), Map.entry('Y', 0xFFFFE38E), Map.entry('q', 0xFF3E5653),
            Map.entry('Q', 0xFFD9EEEA), Map.entry('v', 0xFF7C1A20), Map.entry('L', ChipPack.RED[0]),
            Map.entry('R', ChipPack.RED[1]), Map.entry('D', ChipPack.RED[2]), Map.entry('g', ChipPack.GREEN[0]),
            Map.entry('G', ChipPack.GREEN[1]), Map.entry('j', ChipPack.GREEN[2]), Map.entry('n', ChipPack.BLACK[0]),
            Map.entry('K', ChipPack.BLACK[1]), Map.entry('N', ChipPack.BLACK[2]), Map.entry('W', ChipPack.WHITE[0]),
            Map.entry('w', ChipPack.WHITE[2]), Map.entry('i', ChipPack.INSERT), Map.entry('t', ChipPack.WHITE_INSERT),
            Map.entry('e', CHIP_LINE), Map.entry('y', 0xFF3A3F47), Map.entry('x', 0xFF1E2126),
            Map.entry('k', 0xFF2A1A12));

    static BufferedImage icon() {
        BufferedImage image = Images.blank(32, 32);
        if (ICON.length != 32) {
            throw new IllegalStateException("the icon has " + ICON.length + " rows");
        }
        for (int y = 0; y < 32; y++) {
            if (ICON[y].length() != 32) {
                throw new IllegalStateException("icon row " + y + " is " + ICON[y].length() + " wide");
            }
            for (int x = 0; x < 32; x++) {
                char c = ICON[y].charAt(x);
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
}
