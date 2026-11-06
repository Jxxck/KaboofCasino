package de.tigges.kaboofcasino.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.Test;

/**
 * Draws the slot machine title the way the 26.3 client does (bitmap glyph top = title y 6 + 7 - ascent, pen advance,
 * spaces, glyph colour = texture x text colour) from the embedded pack onto the common scaled screens 480x270
 * (1080p/4), 456x256 (1366x768) and 427x240 (720p, 1440p and 4K with "Auto"), chest centred, and writes
 * {@code build/reports/gui/slot-preview-WxH.png} for a visual check. Asserts the art fits horizontally (at most 125 px
 * right of the chest) and that no content is clipped; only the outer frame may be cut at 240 px height.
 */
class GuiPreviewTest {

    record GlyphImage(BufferedImage image, int x, int y, int w, int h, int ascent, int advance) {
    }

    private final Map<String, byte[]> pack = new HashMap<>();
    private final Map<String, Map<Integer, GlyphImage>> fonts = new HashMap<>();

    private void loadPack() throws IOException {
        try (InputStream in = GuiPreviewTest.class.getClassLoader().getResourceAsStream(
                "pack/KaboofCasino-pack.zip"); ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                pack.put(e.getName(), zip.readAllBytes());
            }
        }
    }

    private Map<Integer, GlyphImage> font(Key key) throws IOException {
        Map<Integer, GlyphImage> cached = fonts.get(key.asString());
        if (cached != null) {
            return cached;
        }
        Map<Integer, GlyphImage> out = new HashMap<>();
        JsonObject json = JsonParser.parseString(new String(pack.get("assets/" + key.namespace() + "/font/"
                + key.value() + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
        for (JsonElement p : json.getAsJsonArray("providers")) {
            JsonObject provider = p.getAsJsonObject();
            String type = provider.get("type").getAsString();
            if (type.equals("space")) {
                for (Map.Entry<String, JsonElement> a : provider.getAsJsonObject("advances").entrySet()) {
                    out.put(a.getKey().codePointAt(0), new GlyphImage(null, 0, 0, 0, 0, 0, a.getValue().getAsInt()));
                }
                continue;
            }
            String file = provider.get("file").getAsString();
            String path = "assets/" + file.substring(0, file.indexOf(':')) + "/textures/"
                    + file.substring(file.indexOf(':') + 1);
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(pack.get(path)));
            JsonArray rows = provider.getAsJsonArray("chars");
            int cols = rows.get(0).getAsString().codePointCount(0, rows.get(0).getAsString().length());
            int cw = image.getWidth() / cols;
            int ch = image.getHeight() / rows.size();
            if (provider.get("height").getAsInt() != ch) {
                continue; // scaled glyphs (panel symbols) are not used in titles
            }
            for (int r = 0; r < rows.size(); r++) {
                int[] cps = rows.get(r).getAsString().codePoints().toArray();
                for (int c = 0; c < cps.length; c++) {
                    if (cps[c] == 0 || out.containsKey(cps[c])) {
                        continue;
                    }
                    int actual = 0;
                    for (int w = cw - 1; w >= 0 && actual == 0; w--) {
                        for (int y = 0; y < ch; y++) {
                            if ((image.getRGB(c * cw + w, r * ch + y) >>> 24) != 0) {
                                actual = w + 1;
                                break;
                            }
                        }
                    }
                    out.put(cps[c], new GlyphImage(image, c * cw, r * ch, cw, ch,
                            provider.get("ascent").getAsInt(), actual + 1));
                }
            }
        }
        fonts.put(key.asString(), out);
        return out;
    }

    /** A rectangle in window coordinates that must be fully on screen. */
    record Important(String name, int x0, int y0, int x1, int y1) {
    }

    /** The slot screen's content (not its outer frame), window coordinates, from the composition in the pack tool. */
    static final List<Important> IMPORTANT = List.of(
            new Important("headline", 60, -8, 118, 0),
            new Important("payouts header", 220, -6, 262, 2),
            new Important("reel window", 25, 10, 157, 51),
            new Important("multiplier column", 250, 26, 287, 181),
            new Important("JACKPOT", 204, 206, 278, 230),
            new Important("inventory", 0, 128, 176, 222),
            new Important("left frame bottom", -23, 222, 201, 229));

    @Test
    void slotTitleFitsTheCommonScreens() throws IOException {
        loadPack();
        GuiLayout layout = GuiLayout.load(GuiPreviewTest.class.getClassLoader()
                .getResourceAsStream("pack/gui-layout.json"));
        TitleBuilder t = new TitleBuilder(layout);
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                t.art("slot_bg_" + col + "_" + row);
            }
        }
        for (int k = 0; k < 3; k++) {
            t.artAt("slot_reel_" + k + "_" + 0, "slot_reel_" + k);
        }
        TextColor cream = TextColor.color(0xF3E7BF);
        t.text("slot_headline", "Spielautomat", cream);
        t.text("slot_payouts", "Gewinne", cream);
        t.text("slot_bet", "EINSATZ", cream);
        int[] mult = {100, 30, 15, 10, 6, 4, 3, 2};
        for (int row = 0; row < 8; row++) {
            t.text("slot_multiplier_" + row, "×" + mult[row], row == 0 ? TextColor.color(0xFFD770) : cream);
        }
        Component title = t.build();
        for (int[] size : new int[][] {{480, 270}, {456, 256}, {427, 240}}) {
            render("slot", title, size[0], size[1], IMPORTANT, java.util.Set.of(31, 39, 40, 41), "spin_on", 97, 18);
        }
    }

    static final List<Important> WHEEL_IMPORTANT = List.of(
            new Important("headline", 60, -8, 118, 0),
            new Important("payouts header", 220, -6, 262, 2),
            new Important("disc", 61, 8, 117, 72),
            new Important("EINSATZ", 70, 74, 108, 82),
            new Important("bet slots", 41, 87, 135, 109),
            new Important("button", 53, 109, 125, 123),
            new Important("multiplier column", 250, 26, 287, 181),
            new Important("inventory", 0, 128, 176, 222));

    static final List<Important> PLINKO_IMPORTANT = List.of(
            new Important("headline", 60, -8, 118, 0),
            new Important("payouts header", 220, -6, 262, 2),
            new Important("EINSÄTZE", 10, 12, 50, 20),
            new Important("ball labels and slots", -7, 33, 63, 91),
            new Important("board picture", 72, 10, 162, 107),
            new Important("button", 53, 109, 125, 123),
            new Important("multiplier column", 250, 32, 287, 176),
            new Important("inventory", 0, 128, 176, 222));

    static final List<Important> ROULETTE_IMPORTANT = List.of(
            new Important("headline", 60, -8, 118, 0),
            new Important("payouts header", 220, -6, 262, 2),
            new Important("chips and colour labels", 15, 7, 165, 43),
            new Important("bet slots", 24, 52, 152, 108),
            new Important("button", 53, 109, 125, 123),
            new Important("payout rows", 190, 32, 290, 165),
            new Important("inventory", 0, 128, 176, 222));

    @Test
    void rouletteTitleFitsTheCommonScreens() throws IOException {
        loadPack();
        GuiLayout layout = GuiLayout.load(GuiPreviewTest.class.getClassLoader()
                .getResourceAsStream("pack/gui-layout.json"));
        TitleBuilder t = new TitleBuilder(layout);
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                t.art("roulette_bg_" + col + "_" + row);
            }
        }
        TextColor cream = TextColor.color(0xF3E7BF);
        TextColor gold = TextColor.color(0xFFD770);
        t.text("roulette_headline", "ROULETTE", cream);
        t.text("roulette_payouts", "Gewinne", cream);
        String[] names = {"ROT", "SCHWARZ", "GRÜN"};
        for (int c = 0; c < 3; c++) {
            t.text("roulette_colour_" + c, names[c], cream);
        }
        String[] rows = {"GRÜN", "ROT", "SCHWARZ"};
        String[] odds = {"×36", "×2", "×2"};
        for (int r = 0; r < 3; r++) {
            t.text("roulette_payout_label_" + r, rows[r], cream);
            t.text("roulette_multiplier_" + r, odds[r], gold);
        }
        Component title = t.build();
        java.util.Set<Integer> slots = new java.util.HashSet<>(java.util.Set.of(48, 49, 50));
        for (int i = 0; i < 9; i++) {
            slots.add((2 + i / 3) * 9 + new int[] {1, 4, 7}[i % 3]);
        }
        for (int[] size : new int[][] {{480, 270}, {456, 256}, {427, 240}}) {
            render("roulette", title, size[0], size[1], ROULETTE_IMPORTANT, slots, "roulette_on", 109, 14);
        }
    }

    @Test
    void wheelTitleFitsTheCommonScreens() throws IOException {
        loadPack();
        GuiLayout layout = GuiLayout.load(GuiPreviewTest.class.getClassLoader()
                .getResourceAsStream("pack/gui-layout.json"));
        TitleBuilder t = new TitleBuilder(layout);
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                t.art("wheel_bg_" + col + "_" + row);
            }
        }
        t.artAt("wheel_disc_9", "wheel_disc");
        TextColor cream = TextColor.color(0xF3E7BF);
        t.text("wheel_headline", "Glücksrad", cream);
        t.text("wheel_payouts", "Gewinne", cream);
        t.text("wheel_bet", "EINSATZ", cream);
        int[] quarters = {20, 8, 6, 4, 3, 2, 1, 0};
        for (int row = 0; row < quarters.length; row++) {
            t.text("wheel_multiplier_" + row, de.tigges.kaboofcasino.game.PanelText.multiplier(quarters[row]),
                    row == 0 ? TextColor.color(0xFFD770) : cream);
        }
        Component title = t.build();
        for (int[] size : new int[][] {{480, 270}, {456, 256}, {427, 240}}) {
            render("wheel", title, size[0], size[1], WHEEL_IMPORTANT, java.util.Set.of(38, 40, 42, 48, 49, 50),
                    "wheel_on", 109, 14);
        }
    }

    @Test
    void plinkoTitleFitsTheCommonScreens() throws IOException {
        loadPack();
        GuiLayout layout = GuiLayout.load(GuiPreviewTest.class.getClassLoader()
                .getResourceAsStream("pack/gui-layout.json"));
        TitleBuilder t = new TitleBuilder(layout);
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                t.art("plinko_bg_" + col + "_" + row);
            }
        }
        TextColor cream = TextColor.color(0xF3E7BF);
        t.text("plinko_headline", "Casino-Plinko", cream);
        t.text("plinko_payouts", "Gewinne", cream);
        t.text("plinko_bets", "EINSÄTZE", cream);
        for (int i = 0; i < 3; i++) {
            t.text("plinko_ball_" + i, "1 Kugel", cream);
        }
        int[] quarters = {32, 12, 8, 2, 1};
        for (int row = 0; row < quarters.length; row++) {
            t.text("plinko_multiplier_" + row, de.tigges.kaboofcasino.game.PanelText.multiplier(quarters[row]),
                    row == 0 ? TextColor.color(0xFFD770) : cream);
        }
        Component title = t.build();
        for (int[] size : new int[][] {{480, 270}, {456, 256}, {427, 240}}) {
            render("plinko", title, size[0], size[1], PLINKO_IMPORTANT, java.util.Set.of(11, 20, 29, 48, 49, 50),
                    "plinko_on", 109, 14);
        }
    }

    private void render(String name, Component title, int width, int height, List<Important> important,
                        java.util.Set<Integer> highlight, String buttonTexture, int buttonY, int buttonH)
            throws IOException {
        int left = (width - 176) / 2;
        int top = (height - 222) / 2;
        BufferedImage screen = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        fill(screen, 0, 0, width, height, 0xff202020);
        fill(screen, left, top, 176, 222, 0xffc6c6c6); // vanilla chest background stand-in
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int pen = 8;
        for (Component c : title.children()) {
            TextComponent text = (TextComponent) c;
            Map<Integer, GlyphImage> font = font(text.font());
            int color = text.color() == null ? 0xffffff : text.color().value();
            for (int cp : text.content().codePoints().toArray()) {
                GlyphImage g = font.get(cp);
                assertTrue(g != null, "font " + text.font() + " lacks U+" + Integer.toHexString(cp));
                if (g.image() != null) {
                    int gx = left + pen;
                    int gy = top + 6 + 7 - g.ascent();
                    draw(screen, g, gx, gy, color);
                    minX = Math.min(minX, gx);
                    maxX = Math.max(maxX, gx + g.w());
                }
                pen += g.advance();
            }
        }
        for (int r = 0; r < 6; r++) {
            for (int col = 0; col < 9; col++) {
                outline(screen, left + 8 + 18 * col, top + 18 + 18 * r, 16, 16,
                        highlight.contains(9 * r + col) ? 0xffff3030 : 0x60ffffff);
            }
        }
        // the play button item (72 x buttonH at window 53, buttonY) as the client draws it over its slot
        BufferedImage button = ImageIO.read(new ByteArrayInputStream(pack.get(
                "assets/kaboofcasino/textures/item/gui/" + buttonTexture + ".png")));
        for (int y = 0; y < buttonH; y++) {
            for (int x = 0; x < 72; x++) {
                blend(screen, left + 53 + x, top + buttonY + y, button.getRGB(x, y));
            }
        }
        Path out = Path.of("build/reports/gui/" + name + "-preview-" + width + "x" + height + ".png");
        Files.createDirectories(out.getParent());
        BufferedImage big = new BufferedImage(width * 2, height * 2, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height * 2; y++) {
            for (int x = 0; x < width * 2; x++) {
                big.setRGB(x, y, screen.getRGB(x / 2, y / 2));
            }
        }
        ImageIO.write(big, "png", out.toFile());
        assertTrue(minX >= 0, width + "x" + height + ": art left of the screen: " + minX);
        assertTrue(maxX <= width, width + "x" + height + ": art right of the screen: " + maxX);
        assertTrue(maxX - (left + 176) <= 125, "right overhang beyond the chest: " + (maxX - left - 176));
        for (Important i : important) {
            assertTrue(left + i.x0() >= 0 && left + i.x1() <= width && top + i.y0() >= 0 && top + i.y1() <= height,
                    name + " " + width + "x" + height + ": " + i.name() + " is clipped");
        }
    }

    private static void fill(BufferedImage img, int x, int y, int w, int h, int argb) {
        for (int j = y; j < y + h; j++) {
            for (int i = x; i < x + w; i++) {
                img.setRGB(i, j, argb);
            }
        }
    }

    private static void outline(BufferedImage img, int x, int y, int w, int h, int argb) {
        for (int i = x; i < x + w; i++) {
            blend(img, i, y, argb);
            blend(img, i, y + h - 1, argb);
        }
        for (int j = y; j < y + h; j++) {
            blend(img, x, j, argb);
            blend(img, x + w - 1, j, argb);
        }
    }

    private static void draw(BufferedImage screen, GlyphImage g, int sx, int sy, int rgb) {
        for (int y = 0; y < g.h(); y++) {
            for (int x = 0; x < g.w(); x++) {
                int px = g.image().getRGB(g.x() + x, g.y() + y);
                if ((px >>> 24) == 0) {
                    continue;
                }
                int r = (px >> 16 & 255) * (rgb >> 16 & 255) / 255;
                int gg = (px >> 8 & 255) * (rgb >> 8 & 255) / 255;
                int b = (px & 255) * (rgb & 255) / 255;
                int tx = sx + x;
                int ty = sy + y;
                if (tx >= 0 && ty >= 0 && tx < screen.getWidth() && ty < screen.getHeight()) {
                    blend(screen, tx, ty, (px & 0xff000000) | r << 16 | gg << 8 | b);
                }
            }
        }
    }

    private static void blend(BufferedImage img, int x, int y, int argb) {
        if (x < 0 || y < 0 || x >= img.getWidth() || y >= img.getHeight()) {
            return;
        }
        double a = (argb >>> 24) / 255.0;
        int dst = img.getRGB(x, y);
        int r = (int) Math.round((argb >> 16 & 255) * a + (dst >> 16 & 255) * (1 - a));
        int g = (int) Math.round((argb >> 8 & 255) * a + (dst >> 8 & 255) * (1 - a));
        int b = (int) Math.round((argb & 255) * a + (dst & 255) * (1 - a));
        img.setRGB(x, y, 0xff000000 | r << 16 | g << 8 | b);
    }
}
