package de.tigges.kaboofcasino.packtool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;

/**
 * The cashier ("Kasse", PLAN-2 C3; not in MineVegas, built from its bar, register and sign deco): the placing item and
 * the one model of its own, everything else of the rig is the deco's display models.
 * <ul>
 *   <li>{@code kaboofcasino:cashier}: the item. In the GUI an icon (the mod's 32x32 cash register icon with a red
 *       chip in its free corner), elsewhere the mod's 3D cash register item model;</li>
 *   <li>{@code kaboofcasino:cashier/screen}: the counter's top, 2 blocks wide, at the customer's edge: a pane of the
 *       mod's clear casino glass in a brass frame (the bar's {@code furniture_brass}), and on top of it a sign built
 *       like the mod's small casino sign (walnut back, dark green panel, brass frame, warm bulbs) whose red LEDs spell
 *       CASHIER (in a German pack {@value #WORD}) in the mod's 7-row LED letters ({@code small_sign}'s CASINO, here at half a pixel per LED).
 *       Coordinates in the anchor cell's frame (x 0..32 over both cells from the viewer's left, y 16..32 the upper
 *       block, z 16 the front); bulbs and LEDs are self-lit like the mod's.</li>
 * </ul>
 * The rest of the rig ({@code de.tigges.kaboofcasino.cashier.CashierRig}): two {@code black_marble_bar} ends with
 * their tops, the {@code cash_register} and a {@code casino_chip_pile} on the counter.
 */
final class CashierPack {

    static final List<String> SOUND_EVENTS = List.of();
    static final List<String> LANG_PREFIXES = List.of();
    static final String WORD = "KASSE";
    static final String WORD_EN = "CASHIER";

    /** The word on the sign in the pack's language. */
    static String word() {
        return PackLang.text(WORD_EN, WORD);
    }
    static final String SCREEN = "kaboofcasino:cashier/screen";

    /**
     * LED letters (7 rows, top first): A, S, C and I as {@code small_sign}'s CASINO has them, K, E, H and R in their
     * style.
     */
    static final java.util.Map<Character, String[]> LETTERS = java.util.Map.of(
            'C', new String[] {".####", "##...", "#....", "#....", "#....", "##...", ".####"},
            'H', new String[] {"#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"},
            'I', new String[] {"#####", "..#..", "..#..", "..#..", "..#..", "..#..", "#####"},
            'R', new String[] {"####.", "#..##", "#...#", "####.", "#.#..", "#..#.", "#...#"},
            'K', new String[] {"#...#", "#..##", "#.##.", "###..", "#.##.", "#..##", "#...#"},
            'A', new String[] {".###.", "##.##", "#...#", "#...#", "#####", "#...#", "#...#"},
            'S', new String[] {".####", "##...", "#....", ".###.", "....#", "...##", "####."},
            'E', new String[] {"#####", "##...", "#....", "####.", "#....", "##...", "#####"});

    /** LED pitch (px) and the sign's geometry in the anchor cell's frame. */
    static final double LED = 0.5;
    static final double SIGN_X0 = 5;
    static final double SIGN_X1 = 27;
    static final double SIGN_Y0 = 26;
    static final double SIGN_Y1 = 32;
    static final double SIGN_Z = 15;

    private CashierPack() {
    }

    static void add(ModJar jar, PackTree tree, Path packSrc) {
        ModelCopier copier = new ModelCopier(jar, tree);
        copier.itemDefinition("casinodeco:cash_register");
        for (String t : List.of("casinodeco:block/casino_glass_clear", "casinodeco:block/furniture_brass",
                "casinodeco:block/furniture_wood", "casinodeco:block/sign_red_led", "casinodeco:block/sign_warm_led",
                "casinodeco:block/sign_panel")) {
            copier.texture(t);
        }
        // the item: icon in the GUI, the mod's register model everywhere else
        BufferedImage icon = icon(Images.read(jar.bytes("assets/casinodeco/textures/item/cash_register.png")));
        tree.put("assets/kaboofcasino/textures/item/cashier.png", Images.write(icon));
        JsonObject iconModel = new JsonObject();
        iconModel.addProperty("parent", "minecraft:item/generated");
        iconModel.add("textures", Json.object("layer0", new JsonPrimitive("kaboofcasino:item/cashier")));
        tree.put("assets/kaboofcasino/models/item/cashier_icon.json", Json.bytes(iconModel));
        tree.put("assets/kaboofcasino/items/cashier.json", Json.bytes(Json.object("model", itemModel())));
        // the panel of the sign: a clean 16x16 piece of the mod's sign panel (no CASINO backing, no gold line)
        BufferedImage panel = Images.crop(Images.argb(Images.read(jar.bytes(
                "assets/casinodeco/textures/block/sign_panel.png"))), 40, 6, 16, 16);
        tree.put("assets/kaboofcasino/textures/block/cashier_sign_panel.png", Images.write(panel));
        tree.put("assets/kaboofcasino/models/cashier/screen.json", Json.bytes(screen()));
        tree.put("assets/kaboofcasino/items/cashier/screen.json", Json.bytes(Json.itemDefinition(SCREEN)));
    }

    /** {@code select display_context}: the icon in the GUI, the mod's cash register model elsewhere. */
    static JsonObject itemModel() {
        JsonObject gui = new JsonObject();
        gui.add("when", Json.array());
        gui.getAsJsonArray("when").add("gui");
        JsonObject iconRef = new JsonObject();
        iconRef.addProperty("type", "minecraft:model");
        iconRef.addProperty("model", "kaboofcasino:item/cashier_icon");
        gui.add("model", iconRef);
        JsonArray cases = new JsonArray();
        cases.add(gui);
        JsonObject fallback = new JsonObject();
        fallback.addProperty("type", "minecraft:model");
        fallback.addProperty("model", "casinodeco:item/cash_register");
        JsonObject select = new JsonObject();
        select.addProperty("type", "minecraft:select");
        select.addProperty("property", "minecraft:display_context");
        select.add("cases", cases);
        select.add("fallback", fallback);
        return select;
    }

    /** The register icon (32x32) with a red casino chip (gold rim, light edge marks) in its free top-left corner. */
    static BufferedImage icon(BufferedImage register) {
        BufferedImage in = Images.argb(register);
        if (in.getWidth() != 32 || in.getHeight() != 32) {
            throw new IllegalStateException("unexpected cash register icon size " + in.getWidth() + "x"
                    + in.getHeight());
        }
        BufferedImage copy = Images.crop(in, 0, 0, 32, 32);
        String[] chip = {"..####..", ".#rrrr#.", "#rwrrwr#", "#rrrrrr#", "#rrrrrr#", "#rwrrwr#", ".#rrrr#.",
            "..####.."};
        for (int y = 0; y < chip.length; y++) {
            for (int x = 0; x < chip[y].length(); x++) {
                char c = chip[y].charAt(x);
                if (c == '.') {
                    continue;
                }
                if ((copy.getRGB(x, y) >>> 24) != 0) {
                    throw new IllegalStateException("the chip would cover the register at " + x + "," + y);
                }
                copy.setRGB(x, y, switch (c) {
                    case '#' -> 0xFFE0B040;
                    case 'w' -> 0xFFF0E0B0;
                    default -> 0xFFC02828;
                });
            }
        }
        return copy;
    }

    /** The LED grid of {@code word}: [row][column] lit, letters 5 wide with one free column between. */
    static boolean[][] leds(String word) {
        int cols = word.length() * 6 - 1;
        boolean[][] grid = new boolean[7][cols];
        for (int i = 0; i < word.length(); i++) {
            String[] rows = LETTERS.get(word.charAt(i));
            if (rows == null) {
                throw new IllegalArgumentException("no LED letter " + word.charAt(i));
            }
            for (int r = 0; r < 7; r++) {
                for (int c = 0; c < 5; c++) {
                    grid[r][i * 6 + c] = rows[r].charAt(c) == '#';
                }
            }
        }
        return grid;
    }

    private static JsonObject box(double x0, double y0, double z0, double x1, double y1, double z1, String texture,
                                  int light) {
        JsonObject faces = new JsonObject();
        for (String side : List.of("north", "east", "south", "west", "up", "down")) {
            JsonObject face = new JsonObject();
            face.add("uv", Json.array(0, 0, 16, 16));
            face.addProperty("texture", texture);
            faces.add(side, face);
        }
        JsonObject element = new JsonObject();
        element.add("from", Json.array(x0, y0, z0));
        element.add("to", Json.array(x1, y1, z1));
        element.add("faces", faces);
        if (light > 0) {
            element.addProperty("light_emission", light);
        }
        return element;
    }

    /** Glass, brass frame and the KASSE sign (see the class comment). */
    static JsonObject screen() {
        JsonArray el = new JsonArray();
        // brass posts and top rail, the glass between them (a gap of 2 px above the counter to pass chips)
        el.add(box(0.5, 16, 14, 1.5, 26, 15.25, "#brass", 0));
        el.add(box(30.5, 16, 14, 31.5, 26, 15.25, "#brass", 0));
        el.add(box(0.5, 25, 14, 31.5, 26, 15.25, "#brass", 0));
        el.add(box(1.5, 18, 14.5, 30.5, 25, 14.75, "#glass", 0));
        el.add(box(1.5, 17.5, 14.25, 30.5, 18, 15, "#brass", 0));
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
        boolean[][] grid = leds(word());
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
        textures.addProperty("glass", "casinodeco:block/casino_glass_clear");
        textures.addProperty("wood", "casinodeco:block/furniture_wood");
        textures.addProperty("panel", "kaboofcasino:block/cashier_sign_panel");
        textures.addProperty("led", "casinodeco:block/sign_red_led");
        textures.addProperty("bulb", "casinodeco:block/sign_warm_led");
        JsonObject model = new JsonObject();
        model.add("textures", textures);
        model.add("elements", el);
        return model;
    }
}
