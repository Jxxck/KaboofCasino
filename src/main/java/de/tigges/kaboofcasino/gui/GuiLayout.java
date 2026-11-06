package de.tigges.kaboofcasino.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.key.Key;

/**
 * The pack's {@code gui-layout.json} (written by the pack tool next to the zip, embedded under {@code pack/}): glyph
 * characters, advances and positions of the GUI art, the space characters, and the advances of every text glyph.
 * Plugin and pack come from the same build, so they always agree.
 */
public final class GuiLayout {

    /** A named position of a screen: window x (left, centre or right edge per align) and y (cap top). */
    public record Anchor(String name, int x, int y, TitleBuilder.Align align) {
    }

    /** One art glyph; {@code x}/{@code y} = intended window position of its top-left corner. */
    public record Glyph(String name, int codePoint, int advance, int width, int height, int x, int y) {
    }

    private final Key guiFont;
    private final int negativeFirst;
    private final int positiveFirst;
    private final int bits;
    private final Map<String, Glyph> glyphs;
    private final String textPrefix;
    private final java.util.Set<Integer> rows;
    private final Map<String, Anchor> anchors;
    private final Map<Integer, Integer> advances;

    private GuiLayout(JsonObject root) {
        this.guiFont = Key.key(root.get("guiFont").getAsString());
        JsonObject spaces = root.getAsJsonObject("spaces");
        this.negativeFirst = spaces.get("negativeFirst").getAsInt();
        this.positiveFirst = spaces.get("positiveFirst").getAsInt();
        this.bits = spaces.get("bits").getAsInt();
        Map<String, Glyph> g = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("glyphs").entrySet()) {
            JsonObject o = e.getValue().getAsJsonObject();
            g.put(e.getKey(), new Glyph(e.getKey(), o.get("codePoint").getAsInt(), o.get("advance").getAsInt(),
                    o.get("width").getAsInt(), o.get("height").getAsInt(), o.get("x").getAsInt(),
                    o.get("y").getAsInt()));
        }
        this.glyphs = Map.copyOf(g);
        Map<String, Anchor> a = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("anchors").entrySet()) {
            JsonObject o = e.getValue().getAsJsonObject();
            a.put(e.getKey(), new Anchor(e.getKey(), o.get("x").getAsInt(), o.get("y").getAsInt(),
                    TitleBuilder.Align.valueOf(o.get("align").getAsString().toUpperCase(java.util.Locale.ROOT))));
        }
        this.anchors = Map.copyOf(a);
        JsonObject text = root.getAsJsonObject("text");
        this.textPrefix = text.get("fontPrefix").getAsString();
        java.util.Set<Integer> r = new java.util.HashSet<>();
        text.getAsJsonArray("rows").forEach(v -> r.add(v.getAsInt()));
        this.rows = java.util.Set.copyOf(r);
        Map<Integer, Integer> adv = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : text.getAsJsonObject("advances").entrySet()) {
            adv.put(e.getKey().codePointAt(0), e.getValue().getAsInt());
        }
        this.advances = Map.copyOf(adv);
    }

    public static GuiLayout load(InputStream json) {
        try (InputStreamReader reader = new InputStreamReader(json, StandardCharsets.UTF_8)) {
            return new GuiLayout(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read gui-layout.json", e);
        }
    }

    public Key guiFont() {
        return guiFont;
    }

    public Glyph glyph(String name) {
        Glyph g = glyphs.get(name);
        if (g == null) {
            throw new IllegalArgumentException("the pack has no GUI glyph " + name);
        }
        return g;
    }

    public Optional<Glyph> find(String name) {
        return Optional.ofNullable(glyphs.get(name));
    }

    /** Space characters (gui font) moving the pen by {@code delta} px. */
    public String spaces(int delta) {
        StringBuilder out = new StringBuilder();
        int first = delta < 0 ? negativeFirst : positiveFirst;
        int rest = Math.abs(delta);
        for (int bit = bits - 1; bit >= 0; bit--) {
            while (rest >= 1 << bit) {
                out.appendCodePoint(first + bit);
                rest -= 1 << bit;
            }
        }
        return out.toString();
    }

    public Anchor anchor(String name) {
        Anchor a = anchors.get(name);
        if (a == null) {
            throw new IllegalArgumentException("the pack has no GUI anchor " + name);
        }
        return a;
    }

    /** Font for a text row whose cap tops sit at window y (only rows a GUI anchor declares exist in the pack). */
    public Key textFont(int y) {
        if (!rows.contains(y)) {
            throw new IllegalArgumentException("the pack has no text font for window y " + y + " " + rows);
        }
        return Key.key(textPrefix + (y < 0 ? "m" + -y : Integer.toString(y)));
    }

    /** Width of {@code text} in the text fonts; characters the fonts lack count 6 (they render as a box). */
    public int width(String text) {
        int w = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            w += advances.getOrDefault(cp, 6);
            i += Character.charCount(cp);
        }
        return w;
    }

    public boolean hasGlyph(int codePoint) {
        return advances.containsKey(codePoint);
    }
}
