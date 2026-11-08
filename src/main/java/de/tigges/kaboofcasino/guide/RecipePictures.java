package de.tigges.kaboofcasino.guide;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.kyori.adventure.key.Key;

/**
 * The recipe pictures of the guide (Jack's request: a picture of the crafting grid instead of a text listing), as the
 * pack tool's {@code GuidePictures} lays them out: per recipe widget ("page:index" of the mod's book) the lines of one
 * dialog text, in the font {@link #font()} (the card with the mod's slots, arrow and output slot, the icons, the
 * count). Each line is a list of segments; the plugin only adds what the pack cannot know: the caption, the tooltips
 * and the links. Read from {@value #RESOURCE}, which the build embeds next to the pack it belongs to. Pure, immutable.
 */
public final class RecipePictures {

    public static final String RESOURCE = "pack/guide-pictures.json";

    /** What a segment shows. */
    public enum Kind {
        /** Card, spaces and the hidden copies (no tooltip). */
        PLAIN,
        /** An icon of ingredient cell {@link Segment#cell()} (0-8, row by row as in the recipe). */
        CELL,
        /** The result's icon. */
        RESULT,
        /** The mod's shapeless sign above the arrow. */
        HINT,
        /** The result's count, drawn with the vanilla text shadow like an item stack's. */
        COUNT,
        /** Where the caption ("Rezept · Name") goes, between the line's runs of negative space. */
        CAPTION
    }

    /** One run of a line; {@code text} is empty for the caption, {@code cell} is -1 unless {@link Kind#CELL}. */
    public record Segment(String text, Kind kind, int cell) {
        public Segment {
            Objects.requireNonNull(text);
            Objects.requireNonNull(kind);
        }
    }

    /** The lines of one recipe's text. */
    public record Picture(List<List<Segment>> lines) {
        public Picture {
            List<List<Segment>> copy = new ArrayList<>();
            for (List<Segment> line : lines) {
                copy.add(List.copyOf(line));
            }
            lines = List.copyOf(copy);
        }
    }

    private final Key font;
    private final int width;
    private final Map<String, Picture> pictures;

    private RecipePictures(Key font, int width, Map<String, Picture> pictures) {
        this.font = font;
        this.width = width;
        this.pictures = pictures;
    }

    public static RecipePictures load(InputStream in) {
        Objects.requireNonNull(in, RESOURCE + " is missing in the plugin jar");
        try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return parse(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + RESOURCE, e);
        }
    }

    static RecipePictures parse(JsonObject root) {
        if (root.get("format").getAsInt() != 1) {
            throw new IllegalArgumentException("recipe pictures format " + root.get("format") + ", expected 1");
        }
        Map<String, Picture> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("pictures").entrySet()) {
            List<List<Segment>> lines = new ArrayList<>();
            for (JsonElement l : e.getValue().getAsJsonArray()) {
                List<Segment> line = new ArrayList<>();
                for (JsonElement s : l.getAsJsonArray()) {
                    line.add(segment(s.getAsJsonObject()));
                }
                lines.add(line);
            }
            out.put(e.getKey(), new Picture(lines));
        }
        return new RecipePictures(Key.key(root.get("font").getAsString()), root.get("width").getAsInt(),
                Collections.unmodifiableMap(out));
    }

    private static Segment segment(JsonObject s) {
        if (s.has("caption")) {
            return new Segment("", Kind.CAPTION, -1);
        }
        String text = s.get("t").getAsString();
        if (s.has("cell")) {
            return new Segment(text, Kind.CELL, s.get("cell").getAsInt());
        }
        Kind kind = s.has("result") ? Kind.RESULT : s.has("hint") ? Kind.HINT : s.has("count") ? Kind.COUNT
                : Kind.PLAIN;
        return new Segment(text, kind, -1);
    }

    /** The font of every picture glyph and space. */
    public Key font() {
        return font;
    }

    /** Width of a picture line in px (the text body must be wider by its padding). */
    public int width() {
        return width;
    }

    /** The picture of the recipe widget {@code widget} (index in the spread's widget list) of spread {@code page}. */
    public Optional<Picture> picture(int page, int widget) {
        return Optional.ofNullable(pictures.get(page + ":" + widget));
    }

    /** Every picture, keyed "page:index" (tests). */
    public Map<String, Picture> all() {
        return pictures;
    }

    /** The segments of all lines of a picture, in order (tests and checks). */
    static List<Segment> segments(Picture p) {
        List<Segment> out = new ArrayList<>();
        p.lines().forEach(out::addAll);
        return out;
    }
}
