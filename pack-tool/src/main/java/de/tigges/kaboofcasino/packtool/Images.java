package de.tigges.kaboofcasino.packtool;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;

/**
 * PNG helpers on {@code TYPE_INT_ARGB} images. The JDK PNG writer adds no time chunks, so equal pixels give equal
 * bytes.
 */
final class Images {

    private Images() {
    }

    static BufferedImage read(byte[] png) {
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(png));
            if (source == null) {
                throw new IllegalStateException("not an image");
            }
            return argb(source);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot decode a PNG", e);
        }
    }

    static byte[] write(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(argb(image), "png", out)) {
                throw new IllegalStateException("no PNG writer");
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot encode a PNG", e);
        }
    }

    static BufferedImage argb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_ARGB) {
            return source;
        }
        BufferedImage out = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                out.setRGB(x, y, source.getRGB(x, y));
            }
        }
        return out;
    }

    static BufferedImage blank(int width, int height) {
        return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    }

    static BufferedImage crop(BufferedImage source, int x, int y, int width, int height) {
        BufferedImage out = blank(width, height);
        copy(source, x, y, width, height, out, 0, 0);
        return out;
    }

    /** Copies a rectangle pixel by pixel (no blending). */
    static void copy(BufferedImage from, int fx, int fy, int width, int height, BufferedImage to, int tx, int ty) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                to.setRGB(tx + x, ty + y, from.getRGB(fx + x, fy + y));
            }
        }
    }

    static void fill(BufferedImage image, int x, int y, int width, int height, int argb) {
        for (int j = y; j < y + height; j++) {
            for (int i = x; i < x + width; i++) {
                image.setRGB(i, j, argb);
            }
        }
    }

    /** Source-over blend of one pixel. */
    static void blend(BufferedImage image, int x, int y, int argb) {
        int a = argb >>> 24;
        if (a == 0) {
            return;
        }
        if (a == 255) {
            image.setRGB(x, y, argb);
            return;
        }
        int dst = image.getRGB(x, y);
        int da = dst >>> 24;
        double sa = a / 255.0;
        double outA = sa + da / 255.0 * (1 - sa);
        int r = mix(argb >> 16 & 255, dst >> 16 & 255, sa, da, outA);
        int g = mix(argb >> 8 & 255, dst >> 8 & 255, sa, da, outA);
        int b = mix(argb & 255, dst & 255, sa, da, outA);
        image.setRGB(x, y, (int) Math.round(outA * 255) << 24 | r << 16 | g << 8 | b);
    }

    private static int mix(int s, int d, double sa, int da, double outA) {
        if (outA <= 0) {
            return 0;
        }
        return (int) Math.round((s * sa + d * (da / 255.0) * (1 - sa)) / outA);
    }

    /**
     * The client's bitmap-font glyph width ({@code BitmapProvider.getActualGlyphWidth}): index of the rightmost column
     * with a non-zero alpha, plus one; 0 for an empty cell.
     */
    static int actualGlyphWidth(BufferedImage image, int cellX, int cellY, int cellWidth, int cellHeight) {
        for (int w = cellWidth - 1; w >= 0; w--) {
            for (int y = 0; y < cellHeight; y++) {
                if ((image.getRGB(cellX + w, cellY + y) >>> 24) != 0) {
                    return w + 1;
                }
            }
        }
        return 0;
    }
}
