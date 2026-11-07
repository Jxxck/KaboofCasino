package de.tigges.kaboofcasino.deco.display;

import java.util.Random;

/**
 * Which of the mod's random model variants (the marble textures {@code _v1.._v3}) the client would draw at a position:
 * the block renderer seeds a legacy random with {@code Mth.getSeed(pos)} (java.util.Random's generator) and a list of
 * equally weighted variants takes {@code nextInt(n)}; a multipart blockstate first draws {@code nextLong()} and seeds
 * every part with it. Displays use the same choice, so a casino floor keeps the mod's pattern. Pure.
 */
public final class DecoVariants {

    private DecoVariants() {
    }

    /** {@code Mth.getSeed(x, y, z)}. */
    public static long seed(int x, int y, int z) {
        long l = (long) (x * 3129871) ^ (long) z * 116129781L ^ (long) y;
        l = l * l * 42317861L + l * 11L;
        return l >> 16;
    }

    /** A {@code variants} entry with {@code n} equal models. */
    public static int simple(int x, int y, int z, int n) {
        return new Random(seed(x, y, z)).nextInt(n);
    }

    /** A {@code multipart} part with {@code n} equal models. */
    public static int multipart(int x, int y, int z, int n) {
        long partSeed = new Random(seed(x, y, z)).nextLong();
        return new Random(partSeed).nextInt(n);
    }

    /** The model name suffix of variant {@code i}: {@code ""}, {@code _v1}, {@code _v2}, {@code _v3}. */
    public static String suffix(int i) {
        return i == 0 ? "" : "_v" + i;
    }
}
