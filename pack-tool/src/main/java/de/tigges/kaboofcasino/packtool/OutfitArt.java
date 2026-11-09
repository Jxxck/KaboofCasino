package de.tigges.kaboofcasino.packtool;

import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * The art of the casino outfits (not in MineVegas), painted from scratch in the casino's colours (the mod's ruby,
 * emerald and sapphire velvet, obsidian, ivory, brass): for every {@link Style} the two armor textures (64x32, vanilla
 * layout: body at 16,16, arms at 40,16, legs at 0,16; the left limbs mirror the right ones), the hat's colour sheet and
 * the 32x32 icons of its four pieces. No dependencies, deterministic (no fonts, no antialiasing, a fixed fabric noise).
 */
final class OutfitArt {

    enum Cut { SUIT, VEST, GOWN }

    enum Hat { TOP_HAT, VISOR, FEDORA, TIARA }

    enum Piece { HAT, JACKET, TROUSERS, SHOES }

    /** One outfit. {@code lapel} of a gown is unused; {@code toe} is the shoe's cap (a second colour or its shine). */
    record Style(String id, Cut cut, int main, int shade, int light, int lapel, int shirt, int shirtShade, int tie,
                 int button, int trouser, int trouserShade, int stripe, int shoe, int toe, int sole, Hat hat,
                 int hatMain, int hatShade, int hatBand, int hatBrim, boolean sparkle) {
    }

    // ---- the mod's colours ------------------------------------------------------------------------------------------
    static final int OUTLINE = 0x281917;
    static final int BRASS = 0xd2a642;
    static final int BRASS_LIGHT = 0xffde7c;
    static final int BRASS_SHADE = 0xae7e2a;
    static final int BRASS_DARK = 0x644622;
    static final int RUBY = 0x771b28;
    static final int RUBY_SHADE = 0x5a141e;
    static final int RUBY_LIGHT = 0x9a2533;
    static final int EMERALD = 0x15503e;
    static final int EMERALD_SHADE = 0x0e3a2d;
    static final int EMERALD_LIGHT = 0x1d6a53;
    static final int SAPPHIRE = 0x1b2b44;
    static final int SAPPHIRE_SHADE = 0x131f33;
    static final int SAPPHIRE_LIGHT = 0x2a4063;
    static final int IVORY = 0xd8d4c3;
    static final int IVORY_SHADE = 0xb8b4a3;
    static final int IVORY_LIGHT = 0xeae6d6;
    static final int BLACK = 0x1c1d22;
    static final int BLACK_SHADE = 0x121317;
    static final int BLACK_LIGHT = 0x2b2d34;
    static final int SATIN = 0x3a3d45;
    static final int SHIRT = 0xece8da;
    static final int SHIRT_SHADE = 0xcfc9b6;
    static final int SHOE_BLACK = 0x0e0f12;
    static final int SHOE_SHINE = 0x4a4e58;

    static final List<Style> STYLES = List.of(
            new Style("smoking", Cut.SUIT, BLACK, BLACK_SHADE, BLACK_LIGHT, SATIN, SHIRT, SHIRT_SHADE, BLACK_SHADE,
                    SATIN, BLACK, BLACK_SHADE, SATIN, SHOE_BLACK, SHOE_SHINE, OUTLINE, Hat.TOP_HAT, BLACK, BLACK_SHADE,
                    RUBY, BLACK_LIGHT, false),
            new Style("croupier", Cut.VEST, EMERALD, EMERALD_SHADE, EMERALD_LIGHT, EMERALD_LIGHT, SHIRT, SHIRT_SHADE,
                    RUBY, BRASS, BLACK, BLACK_SHADE, 0, SHOE_BLACK, SHOE_SHINE, OUTLINE, Hat.VISOR, EMERALD,
                    EMERALD_SHADE, BRASS, EMERALD_LIGHT, false),
            new Style("rubin", Cut.SUIT, RUBY, RUBY_SHADE, RUBY_LIGHT, BLACK, SHIRT, SHIRT_SHADE, BLACK_SHADE, BRASS,
                    BLACK, BLACK_SHADE, RUBY, SHOE_BLACK, SHOE_SHINE, OUTLINE, Hat.FEDORA, RUBY, RUBY_SHADE, BLACK,
                    RUBY_SHADE, false),
            new Style("saphir", Cut.SUIT, SAPPHIRE, SAPPHIRE_SHADE, SAPPHIRE_LIGHT, SAPPHIRE_SHADE, SHIRT, SHIRT_SHADE,
                    BRASS, BRASS, SAPPHIRE, SAPPHIRE_SHADE, 0, 0x3a2416, BRASS_DARK, OUTLINE, Hat.FEDORA, SAPPHIRE,
                    SAPPHIRE_SHADE, BRASS, SAPPHIRE_SHADE, false),
            new Style("elfenbein", Cut.SUIT, IVORY, IVORY_SHADE, IVORY_LIGHT, 0xc4c0af, 0xf5f2e6, SHIRT_SHADE,
                    BLACK_SHADE, BLACK, BLACK, BLACK_SHADE, 0, IVORY, BLACK, OUTLINE, Hat.FEDORA, IVORY, IVORY_SHADE,
                    BLACK, 0xc4c0af, false),
            new Style("highroller", Cut.SUIT, BRASS, BRASS_SHADE, BRASS_LIGHT, BLACK, SHIRT, SHIRT_SHADE, BLACK_SHADE,
                    BLACK, BLACK, BLACK_SHADE, BRASS, BRASS, BRASS_LIGHT, BRASS_DARK, Hat.TOP_HAT, BRASS, BRASS_SHADE,
                    BLACK, BRASS_LIGHT, true),
            new Style("abendkleid", Cut.GOWN, RUBY, RUBY_SHADE, RUBY_LIGHT, RUBY_SHADE, BLACK, BLACK_SHADE, BRASS,
                    BRASS, RUBY, RUBY_SHADE, 0, BRASS, BRASS_LIGHT, BRASS_DARK, Hat.TIARA, BRASS, BRASS_SHADE, RUBY,
                    BRASS_LIGHT, false));

    private OutfitArt() {
    }

    // ---- pixels -----------------------------------------------------------------------------------------------------

    private static BufferedImage image(int w, int h) {
        return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    }

    private static void put(BufferedImage i, int x, int y, int rgb) {
        i.setRGB(x, y, 0xFF000000 | rgb);
    }

    private static int shift(int rgb, int d) {
        int r = Math.clamp(((rgb >> 16) & 255) + d, 0, 255);
        int g = Math.clamp(((rgb >> 8) & 255) + d, 0, 255);
        int b = Math.clamp((rgb & 255) + d, 0, 255);
        return r << 16 | g << 8 | b;
    }

    private static int hash(int x, int y) {
        int h = x * 73856093 ^ y * 19349663;
        h ^= h >>> 13;
        h *= 1274126177;
        return h ^ h >>> 16;
    }

    /** The cloth's weave: a colour varied by a fixed noise, so the same pixel is always the same. */
    private static int cloth(int rgb, int x, int y) {
        return shift(rgb, Math.floorMod(hash(x, y), 5) - 2);
    }

    /** A rectangle of a colour with the weave. */
    private static void rect(BufferedImage i, int x, int y, int w, int h, int rgb) {
        for (int yy = y; yy < y + h; yy++) {
            for (int xx = x; xx < x + w; xx++) {
                put(i, xx, yy, cloth(rgb, xx, yy));
            }
        }
    }

    // ---- the worn textures ------------------------------------------------------------------------------------------

    /** The grid of one face: every character a colour of {@code s} (see {@link #colour}), blank = nothing. */
    private static void grid(BufferedImage i, int x, int y, Style s, String... rows) {
        for (int r = 0; r < rows.length; r++) {
            for (int c = 0; c < rows[r].length(); c++) {
                char k = rows[r].charAt(c);
                if (k != ' ') {
                    int rgb = colour(s, k);
                    boolean flash = s.sparkle() && k == 'B' && Math.floorMod(hash(x + c, y + r), 7) == 0;
                    put(i, x + c, y + r, flash ? s.light() : k == 'A' || k == 'T' ? rgb : cloth(rgb, x + c, y + r));
                }
            }
        }
    }

    private static int colour(Style s, char k) {
        return switch (k) {
            case 'B' -> s.main();
            case 'D' -> s.shade();
            case 'H' -> s.light();
            case 'L' -> s.lapel();
            case 'S' -> s.shirt();
            case 's' -> s.shirtShade();
            case 'T' -> s.tie();
            case 'A' -> s.button();
            case 'G' -> BRASS;
            default -> throw new IllegalArgumentException("colour " + k);
        };
    }

    private static final String[] SUIT_FRONT = {
        "BLSSSSLB",
        "BLTSSTLB",
        "BLTTTTLB",
        "BLTSSTLB",
        "BBLSsLBB",
        "BBLSsLBB",
        "BBBLLBBB",
        "BBBABBBB",
        "BBBBBBBB",
        "BDDBBDDB",
        "BBBBBBBB",
        "DDDDDDDD"};
    private static final String[] SUIT_BACK = {
        "LLLLLLLL",
        "BBBBBBBB",
        "BBBBBBBB",
        "BBBBBBBB",
        "BBBDBBBB",
        "BBBDBBBB",
        "BBBDBBBB",
        "BBBDBBBB",
        "BBBDDBBB",
        "BBBDDBBB",
        "BBBDDBBB",
        "DDDDDDDD"};
    private static final String[] VEST_FRONT = {
        "sSSSSSSs",
        "BSTSSTSB",
        "BSTTTTSB",
        "BSTSSTSB",
        "BBSSsSBB",
        "BBBSsBBB",
        "BBBABBBB",
        "BBBBBBBB",
        "BBBABBBB",
        "BBBBBBBB",
        "BBBABBBB",
        "BDBBBBDB"};
    private static final String[] VEST_BACK = {
        "ssssssss",
        "DBBBBBBD",
        "DBBBBBBD",
        "DBBBBBBD",
        "DBBBBBBD",
        "DBBBBBBD",
        "DDDDDDDD",
        "DBBAABBD",
        "DBBBBBBD",
        "DBBBBBBD",
        "DBBBBBBD",
        "DDDDDDDD"};
    private static final String[] GOWN_FRONT = {
        "DGGGGGGD",
        "BBBGGBBB",
        "BHBBBBHB",
        "BHBBBBHB",
        "BBBBBBBB",
        "DBBBBBBD",
        "DBBBBBBD",
        "DDBBBBDD",
        "GGGGGGGG",
        "BBBGGBBB",
        "BBBBBBBB",
        "BDBBBBDB"};
    private static final String[] GOWN_BACK = {
        "DGGGGGGD",
        "BBBBBBBB",
        "BBBDDBBB",
        "BBBDDBBB",
        "BBBDDBBB",
        "BBBDDBBB",
        "DBBDDBBD",
        "DDBDDBDD",
        "GGGGGGGG",
        "BBBDDBBB",
        "BBBDDBBB",
        "BDBDDBDB"};

    /** Jacket (or vest, or the gown's top) and shoes: the {@code humanoid} layer. */
    static BufferedImage humanoid(Style s) {
        BufferedImage i = image(64, 32);
        String[] front = s.cut() == Cut.SUIT ? SUIT_FRONT : s.cut() == Cut.VEST ? VEST_FRONT : GOWN_FRONT;
        String[] back = s.cut() == Cut.SUIT ? SUIT_BACK : s.cut() == Cut.VEST ? VEST_BACK : GOWN_BACK;
        grid(i, 20, 20, s, front);
        grid(i, 32, 20, s, back);
        rect(i, 16, 20, 4, 12, s.shade());
        rect(i, 28, 20, 4, 12, s.shade());
        if (s.cut() == Cut.GOWN) {
            // the belt runs round the sides too
            rect(i, 16, 28, 4, 1, BRASS_SHADE);
            rect(i, 28, 28, 4, 1, BRASS_SHADE);
        }
        rect(i, 20, 16, 8, 4, s.cut() == Cut.VEST ? s.shirtShade() : s.light());
        rect(i, 28, 16, 8, 4, s.shade());
        arms(i, s);
        shoes(i, s);
        return i;
    }

    /** The sleeves, on arm faces right (outer) 40, front 44, left (inner) 48, back 52; the hand stays bare. */
    private static void arms(BufferedImage i, Style s) {
        if (s.cut() == Cut.GOWN) {
            // long evening gloves from the elbow down, hand included
            for (int f = 0; f < 4; f++) {
                rect(i, 40 + 4 * f, 25, 4, 7, f % 2 == 1 ? s.shirt() : s.shirtShade());
                rect(i, 40 + 4 * f, 25, 4, 1, BRASS_SHADE);
            }
            rect(i, 48, 16, 4, 4, s.shirtShade());
            return;
        }
        boolean shirtSleeve = s.cut() == Cut.VEST;
        int sleeve = shirtSleeve ? s.shirt() : s.main();
        int side = shirtSleeve ? s.shirtShade() : s.shade();
        for (int f = 0; f < 4; f++) {
            rect(i, 40 + 4 * f, 20, 4, 9, f % 2 == 1 ? sleeve : side);
            rect(i, 40 + 4 * f, 29, 4, 1, s.cut() == Cut.SUIT ? s.shirt() : s.shirtShade());
            if (shirtSleeve) {
                rect(i, 40 + 4 * f, 23, 4, 1, BRASS);
            }
        }
        if (!shirtSleeve) {
            put(i, 41, 27, s.button());
            put(i, 41, 25, s.button());
        }
        if (s.sparkle()) {
            for (int y = 20; y < 29; y++) {
                for (int x = 40; x < 56; x++) {
                    if (Math.floorMod(hash(x, y), 7) == 0) {
                        put(i, x, y, s.light());
                    }
                }
            }
        }
        rect(i, 44, 16, 4, 4, shirtSleeve ? sleeve : s.light());
    }

    /** The shoes: the leg's lower four rows (faces outer 0, front 4, inner 8, back 12), the sole underneath. */
    private static void shoes(BufferedImage i, Style s) {
        for (int f = 0; f < 4; f++) {
            int x = 4 * f;
            rect(i, x, 28, 4, 4, f == 1 ? s.shoe() : shift(s.shoe(), -4));
            rect(i, x, 31, 4, 1, s.sole());
        }
        if (s.toe() == SHOE_SHINE) {
            put(i, 5, 29, s.toe()); // patent leather: only a highlight
            put(i, 6, 29, s.toe());
        } else {
            rect(i, 4, 29, 4, 2, s.toe()); // a toe cap in a second colour
        }
        rect(i, 8, 16, 4, 4, s.sole());
    }

    /** Trousers, or the gown's skirt: the {@code humanoid_leggings} layer (waist of the body and both legs). */
    static BufferedImage leggings(Style s) {
        BufferedImage i = image(64, 32);
        boolean gown = s.cut() == Cut.GOWN;
        for (int f = 0; f < 4; f++) {
            rect(i, 16 + (f == 0 ? 0 : f == 1 ? 4 : f == 2 ? 12 : 16), 29, f % 2 == 0 ? 4 : 8, 3, f % 2 == 1
                    ? s.trouser() : s.trouserShade());
        }
        if (!gown) {
            rect(i, 20, 29, 8, 1, BLACK_SHADE);
            rect(i, 32, 29, 8, 1, BLACK_SHADE);
            put(i, 23, 29, s.button());
            put(i, 24, 29, s.button());
        }
        rect(i, 28, 16, 8, 4, s.trouserShade());
        rect(i, 4, 16, 4, 4, s.trouser());
        for (int f = 0; f < 4; f++) {
            int x = 4 * f;
            rect(i, x, 20, 4, 12, f == 1 ? s.trouser() : s.trouserShade());
        }
        if (gown) {
            // the skirt falls in folds to a brass hem
            for (int y = 20; y < 31; y++) {
                put(i, 5, y, s.light());
                put(i, 13, y, s.main());
                put(i, 1, y, s.main());
            }
            rect(i, 0, 31, 16, 1, BRASS);
        } else {
            for (int y = 20; y < 32; y++) {
                put(i, 5, y, shift(s.trouser(), 10)); // the crease
                if (s.stripe() != 0) {
                    put(i, 1, y, s.stripe());
                    put(i, 2, y, s.stripe());
                }
            }
            rect(i, 0, 31, 16, 1, s.trouserShade());
        }
        rect(i, 8, 16, 4, 4, s.trouserShade());
        return i;
    }

    // ---- the worn hat -----------------------------------------------------------------------------------------------

    /** The worn hat's texture: four 8x8 fields of cloth, main (0,0), band (8,0), brim (0,8), shade (8,8). */
    static BufferedImage hatSheet(Style s) {
        BufferedImage i = image(16, 16);
        rect(i, 0, 0, 8, 8, s.hatMain());
        rect(i, 8, 0, 8, 8, s.hatBand());
        rect(i, 0, 8, 8, 8, s.hatBrim());
        rect(i, 8, 8, 8, 8, s.hatShade());
        return i;
    }

    // ---- the icons (32x32, the mod's dark outline) --------------------------------------------------------------------

    /** A polygon of a colour, by the pixel centres, without antialiasing. */
    private static void poly(BufferedImage i, int rgb, int... xy) {
        Polygon p = new Polygon();
        for (int k = 0; k + 1 < xy.length; k += 2) {
            p.addPoint(xy[k], xy[k + 1]);
        }
        for (int y = 0; y < i.getHeight(); y++) {
            for (int x = 0; x < i.getWidth(); x++) {
                if (p.contains(x + 0.5, y + 0.5)) {
                    put(i, x, y, rgb);
                }
            }
        }
    }

    private static void box(BufferedImage i, int rgb, int x, int y, int w, int h) {
        for (int yy = y; yy < y + h; yy++) {
            for (int xx = x; xx < x + w; xx++) {
                put(i, xx, yy, rgb);
            }
        }
    }

    /** Both halves of a polygon that is symmetric around the icon's middle. */
    private static void mirrored(BufferedImage i, int rgb, int... xy) {
        poly(i, rgb, xy);
        int[] m = xy.clone();
        for (int k = 0; k < m.length; k += 2) {
            m[k] = 32 - m[k];
        }
        poly(i, rgb, m);
    }

    /** The mod's dark outline round everything painted. */
    private static BufferedImage outlined(BufferedImage in) {
        BufferedImage out = image(32, 32);
        out.getRaster().setRect(in.getRaster());
        int line = 0xFF000000 | OUTLINE;
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                if (in.getRGB(x, y) >>> 24 == 0 && touches(in, x, y)) {
                    out.setRGB(x, y, line);
                }
            }
        }
        return out;
    }

    private static boolean touches(BufferedImage in, int x, int y) {
        int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : around) {
            int nx = x + d[0];
            int ny = y + d[1];
            if (nx >= 0 && ny >= 0 && nx < 32 && ny < 32 && in.getRGB(nx, ny) >>> 24 != 0) {
                return true;
            }
        }
        return false;
    }

    /** The icon of one piece. */
    static BufferedImage icon(Style s, Piece piece) {
        BufferedImage i = image(32, 32);
        switch (piece) {
            case HAT -> hatIcon(i, s);
            case JACKET -> {
                switch (s.cut()) {
                    case SUIT -> jacketIcon(i, s);
                    case VEST -> vestIcon(i, s);
                    case GOWN -> gownIcon(i, s);
                }
            }
            case TROUSERS -> {
                if (s.cut() == Cut.GOWN) {
                    skirtIcon(i, s);
                } else {
                    trousersIcon(i, s);
                }
            }
            case SHOES -> shoesIcon(i, s);
        }
        return outlined(i);
    }

    private static void bowTie(BufferedImage i, int rgb, int y) {
        poly(i, rgb, 12, y - 2, 16, y, 12, y + 3);
        poly(i, rgb, 20, y - 2, 16, y, 20, y + 3);
        box(i, rgb, 15, y - 1, 2, 3);
    }

    private static void jacketIcon(BufferedImage i, Style s) {
        mirrored(i, s.shade(), 10, 5, 4, 9, 3, 26, 8, 26, 10, 14);
        mirrored(i, s.main(), 10, 5, 5, 9, 4, 24, 7, 24, 10, 13);
        box(i, s.shirt(), 4, 25, 4, 2);
        box(i, s.shirt(), 24, 25, 4, 2);
        poly(i, s.main(), 10, 4, 22, 4, 23, 29, 9, 29);
        box(i, s.shade(), 15, 22, 2, 7);
        poly(i, s.shirt(), 12, 4, 20, 4, 16, 19);
        mirrored(i, s.lapel(), 10, 4, 12, 4, 16, 19, 14, 20, 10, 10);
        bowTie(i, s.tie(), 8);
        box(i, s.light(), 11, 5, 1, 1);
        box(i, s.button(), 15, 21, 2, 2);
        if (s.sparkle()) {
            for (int y = 5; y < 29; y += 4) {
                box(i, BRASS_LIGHT, 11, y + 2, 1, 1);
                box(i, BRASS_LIGHT, 21, y, 1, 1);
            }
        }
    }

    private static void vestIcon(BufferedImage i, Style s) {
        mirrored(i, s.shirtShade(), 10, 5, 5, 9, 4, 24, 8, 24, 10, 13);
        mirrored(i, s.shirt(), 10, 5, 6, 9, 5, 22, 7, 22, 10, 12);
        box(i, BRASS, 5, 15, 4, 2);
        box(i, BRASS, 23, 15, 4, 2);
        poly(i, s.main(), 10, 4, 22, 4, 23, 28, 9, 28);
        poly(i, s.shirt(), 12, 4, 20, 4, 16, 18);
        bowTie(i, s.tie(), 8);
        for (int y = 17; y < 27; y += 4) {
            box(i, BRASS, 15, y, 2, 2);
        }
    }

    private static void gownIcon(BufferedImage i, Style s) {
        poly(i, s.main(), 12, 3, 20, 3, 19, 14, 25, 30, 7, 30, 13, 14);
        poly(i, s.shade(), 12, 3, 14, 3, 14, 14, 11, 30, 7, 30, 13, 14);
        poly(i, s.light(), 16, 15, 18, 15, 22, 30, 19, 30);
        box(i, BRASS, 13, 13, 6, 2);
        box(i, BRASS, 12, 3, 8, 2);
        box(i, BRASS_LIGHT, 15, 6, 2, 2);
    }

    private static void skirtIcon(BufferedImage i, Style s) {
        poly(i, s.main(), 12, 5, 20, 5, 26, 30, 6, 30);
        poly(i, s.shade(), 12, 5, 14, 5, 11, 30, 6, 30);
        poly(i, s.light(), 16, 8, 18, 8, 22, 30, 19, 30);
        box(i, BRASS, 12, 5, 8, 2);
        box(i, BRASS, 6, 29, 20, 1);
    }

    private static void trousersIcon(BufferedImage i, Style s) {
        poly(i, s.trouser(), 9, 4, 23, 4, 24, 30, 18, 30, 16, 13, 14, 30, 8, 30);
        poly(i, s.trouserShade(), 9, 4, 11, 4, 11, 30, 8, 30);
        poly(i, s.trouserShade(), 21, 4, 23, 4, 24, 30, 22, 30);
        box(i, BLACK_SHADE, 9, 4, 14, 2);
        box(i, s.button(), 15, 4, 2, 2);
        if (s.stripe() != 0) {
            poly(i, s.stripe(), 10, 6, 11, 6, 10, 30, 9, 30);
            poly(i, s.stripe(), 21, 6, 22, 6, 23, 30, 22, 30);
        }
    }

    private static void oneShoe(BufferedImage i, Style s, int x, int y) {
        poly(i, s.shoe(), x, y, x + 6, y, x + 7, y + 4, x + 12, y + 5, x + 13, y + 8, x, y + 8);
        poly(i, s.toe(), x + 7, y + 4, x + 12, y + 5, x + 13, y + 8, x + 8, y + 8);
        box(i, s.sole(), x, y + 8, 14, 2);
        box(i, s.sole(), x + 1, y + 1, 4, 1);
    }

    private static void shoesIcon(BufferedImage i, Style s) {
        oneShoe(i, s, 3, 7);
        oneShoe(i, s, 14, 18);
    }

    private static void hatIcon(BufferedImage i, Style s) {
        switch (s.hat()) {
            case TOP_HAT -> {
                box(i, s.hatMain(), 9, 4, 14, 19);
                box(i, s.hatShade(), 9, 4, 3, 19);
                box(i, s.hatBand(), 9, 17, 14, 4);
                box(i, s.hatBrim(), 3, 23, 26, 3);
                box(i, s.hatShade(), 3, 25, 26, 1);
            }
            case FEDORA -> {
                poly(i, s.hatMain(), 9, 21, 10, 9, 15, 7, 17, 7, 22, 9, 23, 21);
                poly(i, s.hatShade(), 14, 7, 16, 10, 18, 7);
                box(i, s.hatBand(), 9, 17, 14, 4);
                box(i, s.hatBrim(), 2, 21, 28, 3);
                box(i, s.hatShade(), 2, 23, 28, 1);
            }
            case VISOR -> {
                poly(i, s.hatMain(), 4, 16, 28, 16, 31, 24, 1, 24);
                poly(i, s.hatBrim(), 7, 17, 25, 17, 27, 23, 5, 23);
                box(i, s.hatBand(), 4, 12, 24, 4);
                box(i, BRASS_LIGHT, 15, 12, 2, 4);
            }
            case TIARA -> {
                box(i, s.hatMain(), 5, 20, 22, 4);
                box(i, s.hatShade(), 5, 23, 22, 1);
                poly(i, s.hatMain(), 12, 20, 16, 7, 20, 20);
                poly(i, s.hatMain(), 5, 20, 9, 12, 13, 20);
                poly(i, s.hatMain(), 19, 20, 23, 12, 27, 20);
                box(i, s.hatBand(), 15, 12, 2, 4);
                box(i, s.hatBrim(), 8, 16, 2, 2);
                box(i, s.hatBrim(), 22, 16, 2, 2);
            }
        }
    }
}
