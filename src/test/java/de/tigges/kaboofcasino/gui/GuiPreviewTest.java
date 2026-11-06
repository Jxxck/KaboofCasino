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
 * spaces, glyph colour = texture x text colour) from the embedded pack, onto a 480x270 screen (1920x1080 at GUI scale
 * 4, chest at 152/24), and writes {@code build/reports/gui/slot-preview.png} for a visual check. Asserts that the art
 * stays on screen and that the bet slot frame lies around grid slot (4, 3).
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

    @Test
    void slotTitlePreview() throws IOException {
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
            t.art("slot_reel_" + k + "_" + 0, 25 + 44 * k);
        }
        TextColor cream = TextColor.color(0xF3E7BF);
        t.text("Spielautomat", 89, -12, cream, TitleBuilder.Align.CENTER);
        t.text("Gewinne", 268, -10, cream, TitleBuilder.Align.CENTER);
        t.text("EINSATZ", 89, 58, cream, TitleBuilder.Align.CENTER);
        int[] mult = {100, 30, 15, 10, 6, 4, 3, 2};
        for (int row = 0; row < 8; row++) {
            t.text("×" + mult[row], 320, 22 + 21 * row, row == 0 ? TextColor.color(0xFFD770) : cream,
                    TitleBuilder.Align.RIGHT);
        }
        Component title = t.build();

        int left = 152;
        int top = 24;
        BufferedImage screen = new BufferedImage(480, 270, BufferedImage.TYPE_INT_ARGB);
        fill(screen, 0, 0, 480, 270, 0xff202020);
        fill(screen, left, top, 176, 222, 0xffc6c6c6); // vanilla chest background stand-in
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
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
                    minY = Math.min(minY, gy);
                    maxY = Math.max(maxY, gy + g.h());
                }
                pen += g.advance();
            }
        }
        // grid slots of the 9x6 chest (item areas), the bet slot (4,3) and the button row in red
        for (int r = 0; r < 6; r++) {
            for (int col = 0; col < 9; col++) {
                outline(screen, left + 8 + 18 * col, top + 18 + 18 * r, 16, 16,
                        r == 3 && col == 4 || r == 4 && col >= 3 && col <= 5 ? 0xffff3030 : 0x60ffffff);
            }
        }
        // the SPIN button item (72x18 at window 53, 97) as the client would draw it over slot (4, 4)
        BufferedImage button = ImageIO.read(new ByteArrayInputStream(pack.get(
                "assets/kaboofcasino/textures/item/gui/spin_on.png")));
        for (int y = 0; y < 18; y++) {
            for (int x = 0; x < 72; x++) {
                blend(screen, left + 53 + x, top + 97 + y, button.getRGB(x, y));
            }
        }
        Path out = Path.of("build/reports/gui/slot-preview.png");
        Files.createDirectories(out.getParent());
        BufferedImage big = new BufferedImage(960, 540, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 540; y++) {
            for (int x = 0; x < 960; x++) {
                big.setRGB(x, y, screen.getRGB(x / 2, y / 2));
            }
        }
        ImageIO.write(big, "png", out.toFile());
        assertTrue(minY >= 0, "art above the screen at GUI scale 4 / 1080p: " + minY);
        assertTrue(maxY <= 270, "art below the screen: " + maxY);
        assertTrue(minX >= 0, "art left of the screen: " + minX);
        assertTrue(maxX - 480 <= 8, "only the payout panel's outer border may be clipped: " + maxX);
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
