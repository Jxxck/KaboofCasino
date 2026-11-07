package de.tigges.kaboofcasino.machine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Slot-exact matching of a casino recipe over a crafting grid (pure; {@code RecipeMatchTest}). Bukkit only matches
 * materials, and casino items share materials (all machine items are paper, many deco items the same mushroom or
 * carrier block), so the result is checked once more here: every casino ingredient needs a stack with exactly its
 * item id, every vanilla ingredient a stack without any casino id. Shaped recipes are placed like vanilla (empty
 * rows and columns trimmed, every offset, also mirrored, everything outside the pattern empty); shapeless ones need a
 * one-to-one assignment of stacks to ingredients.
 */
public final class RecipeMatch {

    /** One grid stack: its material key ({@code minecraft:paper}) and its casino id ({@code null} for vanilla). */
    public record Slot(String material, String casinoId) {
    }

    private RecipeMatch() {
    }

    /** {@code grid} is row-major with {@code width} columns; {@code null} = empty. */
    public static boolean shaped(List<String> pattern, Map<Character, Predicate<Slot>> key, Slot[] grid, int width) {
        List<String> rows = trim(pattern);
        int h = rows.size();
        int w = rows.isEmpty() ? 0 : rows.getFirst().length();
        int height = grid.length / width;
        for (boolean mirror : new boolean[] {false, true}) {
            for (int y0 = 0; y0 + h <= height; y0++) {
                for (int x0 = 0; x0 + w <= width; x0++) {
                    if (fits(rows, key, grid, width, height, x0, y0, mirror)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean fits(List<String> rows, Map<Character, Predicate<Slot>> key, Slot[] grid, int width,
                                int height, int x0, int y0, boolean mirror) {
        int h = rows.size();
        int w = rows.getFirst().length();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Slot s = grid[y * width + x];
                int px = x - x0;
                int py = y - y0;
                char c = px >= 0 && px < w && py >= 0 && py < h ? rows.get(py).charAt(mirror ? w - 1 - px : px) : ' ';
                if (c == ' ') {
                    if (s != null) {
                        return false;
                    }
                } else {
                    Predicate<Slot> p = key.get(c);
                    if (s == null || p == null || !p.test(s)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** Vanilla's {@code ShapedRecipePattern.shrink}: drops empty leading/trailing rows and columns. */
    static List<String> trim(List<String> pattern) {
        int top = 0;
        while (top < pattern.size() && pattern.get(top).isBlank()) {
            top++;
        }
        int bottom = pattern.size() - 1;
        while (bottom >= top && pattern.get(bottom).isBlank()) {
            bottom--;
        }
        if (top > bottom) {
            return List.of();
        }
        int width = 0;
        for (String row : pattern) {
            width = Math.max(width, row.length());
        }
        int left = width;
        int right = -1;
        for (int y = top; y <= bottom; y++) {
            String row = pattern.get(y);
            for (int x = 0; x < row.length(); x++) {
                if (row.charAt(x) != ' ') {
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                }
            }
        }
        List<String> out = new ArrayList<>();
        for (int y = top; y <= bottom; y++) {
            String row = String.format("%-" + width + "s", pattern.get(y));
            out.add(row.substring(left, right + 1));
        }
        return out;
    }

    /** Every non-empty stack assigned to exactly one ingredient and every ingredient used once. */
    public static boolean shapeless(List<Predicate<Slot>> ingredients, Slot[] grid) {
        List<Slot> stacks = new ArrayList<>();
        for (Slot s : grid) {
            if (s != null) {
                stacks.add(s);
            }
        }
        if (stacks.size() != ingredients.size()) {
            return false;
        }
        return assign(ingredients, stacks, 0, new boolean[stacks.size()]);
    }

    private static boolean assign(List<Predicate<Slot>> ingredients, List<Slot> stacks, int i, boolean[] used) {
        if (i == ingredients.size()) {
            return true;
        }
        for (int j = 0; j < stacks.size(); j++) {
            if (!used[j] && ingredients.get(i).test(stacks.get(j))) {
                used[j] = true;
                if (assign(ingredients, stacks, i + 1, used)) {
                    return true;
                }
                used[j] = false;
            }
        }
        return false;
    }
}
