package de.tigges.kaboofcasino.packtool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

/**
 * The casino signs in 2 x 2 and 4 x 4 (not in MineVegas), built like the mod's 3 x 2 sign from its own textures: a
 * walnut back, the dark sign panel, a brass frame, a ring of warm bulbs, the word CASINO in the mod's seven-row red LED
 * letters, and a row of card suits above and below it. Coordinates: column 0 at x 0..16 to the viewer's right, row 0 at
 * y 0..16, the sign lying on the wall at z 0.15..1.5. The 4 x 4 is too big for a model's [-16, 32] range: it is built
 * around its second column and row and stored at half size ({@link DecoDisplayPack#halved}), shown at scale 2.
 */
final class CasinoSignPack {

    static final List<Integer> SIZES = List.of(2, 4);
    static final String WORD = "CASINO";
    static final Map<Character, String[]> LETTERS = Map.of(
            'C', new String[] {".####", "##...", "#....", "#....", "#....", "##...", ".####"},
            'A', new String[] {".###.", "##.##", "#...#", "#...#", "#####", "#...#", "#...#"},
            'S', new String[] {".####", "##...", "#....", ".###.", "....#", "...##", "####."},
            'I', new String[] {"#####", "..#..", "..#..", "..#..", "..#..", "..#..", "#####"},
            'N', new String[] {"#...#", "##..#", "##..#", "#.#.#", "#..##", "#..##", "#...#"},
            'O', new String[] {".###.", "##.##", "#...#", "#...#", "#...#", "##.##", ".###."});
    /** The four suits, seven LEDs square; hearts and diamonds red, spades and clubs in the warm bulbs' colour. */
    static final String[][] SUITS = {
        {"...#...", "..###..", ".#####.", "#######", "#######", ".#.#.#.", "...#..."},
        {".##.##.", "#######", "#######", ".#####.", "..###..", "...#...", "......."},
        {"...#...", "..###..", ".#####.", "#######", ".#####.", "..###..", "...#..."},
        {"..###..", "..###..", "#.###.#", "#######", "#.###.#", "...#...", "..###.."}};
    static final String[] SUIT_LED = {"#bulb", "#led", "#led", "#bulb"};

    private CasinoSignPack() {
    }

    static void add(ModelCopier copier, PackTree tree) {
        for (String t : List.of("casinodeco:block/furniture_brass", "casinodeco:block/furniture_wood",
                "casinodeco:block/sign_red_led", "casinodeco:block/sign_warm_led")) {
            copier.texture(t);
        }
        for (int size : SIZES) {
            String name = "casino_sign_" + size + "x" + size;
            JsonObject model = model(size);
            tree.put("assets/kaboofcasino/models/deco/" + name + ".json", Json.bytes(model));
            DecoDisplayPack.displayDefinition(tree, name, DecoDisplayPack.PREFIX + name);
            tree.put("assets/casinodeco/textures/item/" + name + ".png", Images.write(icon(size)));
            JsonObject item = new JsonObject();
            item.addProperty("parent", "minecraft:item/generated");
            item.add("textures", Json.object("layer0", new JsonPrimitive("casinodeco:item/" + name)));
            tree.put("assets/casinodeco/models/item/" + name + ".json", Json.bytes(item));
            tree.put("assets/casinodeco/items/" + name + ".json",
                    Json.bytes(Json.itemDefinition("casinodeco:item/" + name)));
        }
    }

    static JsonObject model(int size) {
        JsonArray elements = elements(size);
        if (size == 4) {
            JsonArray moved = Models.placed(elements, -16, -16, 0, -1, 0);
            elements = DecoDisplayPack.halved(moved);
        }
        JsonObject textures = new JsonObject();
        textures.addProperty("particle", "casinodeco:block/furniture_brass");
        textures.addProperty("wood", "casinodeco:block/furniture_wood");
        textures.addProperty("gold", "casinodeco:block/furniture_brass");
        textures.addProperty("panel", "kaboofcasino:block/cashier_sign_panel");
        textures.addProperty("led", "casinodeco:block/sign_red_led");
        textures.addProperty("bulb", "casinodeco:block/sign_warm_led");
        JsonObject model = new JsonObject();
        model.add("textures", textures);
        model.add("elements", elements);
        return model;
    }

    static JsonArray elements(int size) {
        double w = size * 16.0;
        JsonArray el = new JsonArray();
        el.add(ChipShopPack.box(0.5, 0.5, 0.15, w - 0.5, w - 0.5, 0.75, "#wood", 0));
        for (int c = 0; c < size; c++) {
            for (int r = 0; r < size; r++) {
                el.add(ChipShopPack.box(Math.max(2, c * 16.0), Math.max(2, r * 16.0), 0.75,
                        Math.min(w - 2, (c + 1) * 16.0), Math.min(w - 2, (r + 1) * 16.0), 1.1, "#panel", 0));
            }
        }
        frame(el, 1, w, 1, 0.75, 1.35);
        if (size == 4) {
            frame(el, 7, w, 0.5, 1.1, 1.3);
        }
        bulbs(el, w);
        double led = size == 4 ? 1.4 : 0.7;
        word(el, w / 2, led);
        double suit = size == 4 ? 1.0 : 0.5;
        double offset = size == 4 ? 16 : 7.25;
        suits(el, w / 2, w / 2 + offset, suit);
        suits(el, w / 2, w / 2 - offset, suit);
        return el;
    }

    private static void frame(JsonArray el, double inset, double w, double width, double z0, double z1) {
        double a = inset;
        double b = inset + width;
        double c = w - inset - width;
        double d = w - inset;
        el.add(ChipShopPack.box(a, a, z0, d, b, z1, "#gold", 0));
        el.add(ChipShopPack.box(a, c, z0, d, d, z1, "#gold", 0));
        el.add(ChipShopPack.box(a, b, z0, b, c, z1, "#gold", 0));
        el.add(ChipShopPack.box(c, b, z0, d, c, z1, "#gold", 0));
    }

    /** A ring of warm bulbs inside the frame, one every 4 px like the mod's sign. */
    private static void bulbs(JsonArray el, double w) {
        double s = 0.65;
        for (double x = 3; x + s <= w - 3 + 1e-6; x += 4) {
            bulb(el, x, 3, s);
            bulb(el, x, w - 3 - s, s);
        }
        for (double y = 7; y + s <= w - 7 + 1e-6; y += 4) {
            bulb(el, 3, y, s);
            bulb(el, w - 3 - s, y, s);
        }
    }

    private static void bulb(JsonArray el, double x, double y, double s) {
        el.add(ChipShopPack.box(x, y, 1.15, x + s, y + s, 1.5, "#bulb", 15));
    }

    /** One row of a pattern as boxes, a box per horizontal run. */
    private static void row(JsonArray el, String row, double x0, double yTop, double pitch, String texture) {
        int c = 0;
        while (c < row.length()) {
            if (row.charAt(c) != '#') {
                c++;
                continue;
            }
            int start = c;
            while (c < row.length() && row.charAt(c) == '#') {
                c++;
            }
            el.add(ChipShopPack.box(x0 + start * pitch, yTop - pitch, 1.1, x0 + c * pitch, yTop, 1.35, texture, 15));
        }
    }

    /** CASINO centred on (centre, centre). */
    private static void word(JsonArray el, double centre, double led) {
        int cols = WORD.length() * 6 - 1;
        double x0 = centre - cols * led / 2;
        double yTop = centre + 7 * led / 2;
        for (int i = 0; i < WORD.length(); i++) {
            String[] letter = LETTERS.get(WORD.charAt(i));
            for (int r = 0; r < 7; r++) {
                row(el, letter[r], x0 + i * 6 * led, yTop - r * led, led, "#led");
            }
        }
    }

    /** The four suits in a row, centred on {@code cx}, their middle at {@code cy}. */
    private static void suits(JsonArray el, double cx, double cy, double led) {
        double gap = 3 * led;
        double width = 4 * 7 * led + 3 * gap;
        for (int s = 0; s < 4; s++) {
            double x0 = cx - width / 2 + s * (7 * led + gap);
            for (int r = 0; r < 7; r++) {
                row(el, SUITS[s][r], x0, cy + 3.5 * led - r * led, led, SUIT_LED[s]);
            }
        }
    }

    // ---- the icon -------------------------------------------------------------------------------------------------

    private static final Map<Character, String[]> DIGITS = Map.of(
            '2', new String[] {"####.", "....#", "....#", ".###.", "#....", "#....", "#####"},
            '4', new String[] {"#...#", "#...#", "#...#", "#####", "....#", "....#", "....#"},
            'x', new String[] {"...", "...", "#.#", ".#.", "#.#", "...", "..."});

    /** 32 x 32: the brass frame, the panel, bulbs, and the size in red LEDs ("2x2"). */
    static BufferedImage icon(int size) {
        BufferedImage i = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Images.fill(i, 1, 1, 30, 30, 0xFF281917);
        Images.fill(i, 2, 2, 28, 28, 0xFFD2A642);
        Images.fill(i, 3, 3, 26, 26, 0xFF644622);
        Images.fill(i, 4, 4, 24, 24, 0xFF123F32);
        for (int p = 6; p < 26; p += 4) {
            for (int[] at : new int[][] {{p, 5}, {p, 26}, {5, p}, {26, p}}) {
                Images.fill(i, at[0], at[1], 1, 1, 0xFFFFDE7C);
            }
        }
        String text = size + "x" + size;
        int width = 5 + 1 + 3 + 1 + 5;
        int x = (32 - width) / 2;
        int y = (32 - 7) / 2;
        for (char ch : text.toCharArray()) {
            String[] glyph = DIGITS.get(ch);
            for (int r = 0; r < 7; r++) {
                for (int c = 0; c < glyph[r].length(); c++) {
                    if (glyph[r].charAt(c) == '#') {
                        Images.fill(i, x + c, y + r, 1, 1, 0xFFE0333F);
                    }
                }
            }
            x += glyph[0].length() + 1;
        }
        return i;
    }
}
