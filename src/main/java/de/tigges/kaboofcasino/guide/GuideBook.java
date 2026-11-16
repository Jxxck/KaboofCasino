package de.tigges.kaboofcasino.guide;

import com.google.gson.JsonArray;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

/**
 * The casino guide book of MineVegas Casino (games.md 11) as the mod ships it: {@code casino_guide.json} format 2
 * (34 spreads; each JSON page is one two-page spread, left half x &lt; 180), plus the German text of every lang key it
 * uses and the name key of every casino item it shows. Read from {@value #FILE} of the language, which the pack tool's
 * {@code GuideData} builds from the pinned mod jar. Page numbers are 1-based, as in the JSON's {@code tabs},
 * {@code index} and tile {@code page} fields. Pure (no server); immutable after {@link #load}.
 *
 * <p>Widgets keep the JSON order, which reads left page then right page, top to bottom (an item before the labels
 * beside it). Variant groups (the mod's chip selector: TV sizes, wood/glass tables, the three carpets of a colour)
 * keep all their variants; {@code chips} and the hover {@code preview} are kept as widgets but have no content.
 * A {@code scene} (a rotating 3D block scene) is reduced to its main items ({@link #sceneItems}).
 */
public final class GuideBook {

    /** The guide's file name; the plugin jar has one per language ({@code Language.packResource}). */
    public static final String FILE = "guide.json";

    /** The guide of the casino's language inside the plugin jar. */
    public static String resource() {
        return de.tigges.kaboofcasino.config.Language.current().packResource(FILE);
    }
    /** The mod's split between the two pages of a spread ({@code CasinoGuideScreen}: left half x &lt; 180). */
    public static final int RIGHT_PAGE_X = 180;
    /** Tag ingredients the mod cycles through, shown as their first member (the tag files' first entry). */
    static final Map<String, String> TAG_SHOWN = Map.of(
            "#minecraft:planks", "minecraft:oak_planks",
            "#minecraft:signs", "minecraft:oak_sign",
            "#minecraft:wooden_slabs", "minecraft:oak_slab");

    /** The mod's text colours ({@code CasinoGuideScreen.color}). */
    public enum Color {
        GOLD(0xF2C65A), CREAM(0xF7EDD2), MUTED(0xA8C6B0), INK(0x173528);

        private final int rgb;

        Color(int rgb) {
            this.rgb = rgb;
        }

        public int rgb() {
            return rgb;
        }

        static Color of(String name) {
            return switch (name) {
                case "gold" -> GOLD;
                case "muted" -> MUTED;
                case "ink" -> INK;
                default -> CREAM;
            };
        }
    }

    /** One widget of a spread; {@code x}, {@code y} are the mod's book coordinates (360 x 224). */
    public sealed interface Widget permits Title, Label, Text, Tile, Item, Recipe, Scene, Entity, Arrow, Rule, Chips,
            Preview {
        int x();

        int y();

        /** True on the right page of the spread. */
        default boolean right() {
            return x() >= RIGHT_PAGE_X;
        }
    }

    /** Page banner: bold gold, centred. */
    public record Title(int x, int y, String key) implements Widget {
    }

    public record Label(int x, int y, String key, Color color, boolean bold) implements Widget {
    }

    /** Wrapped text. */
    public record Text(int x, int y, String key, Color color) implements Widget {
    }

    /**
     * A button to another spread: icon {@code ref}, label {@code key} (null: icon only, the item name is its tooltip).
     */
    public record Tile(int x, int y, String ref, String key, int page) implements Widget {
    }

    public record Item(int x, int y, String ref, int count, int size) implements Widget {
    }

    /**
     * A crafting grid: {@code cells} are the nine slots row by row (null = empty; an id or a {@code #tag}).
     */
    public record Recipe(int x, int y, String result, int count, List<String> cells, boolean shapeless)
            implements Widget {
        public Recipe {
            cells = Collections.unmodifiableList(new ArrayList<>(cells));
        }
    }

    /**
     * A rotating 3D scene, reduced to its main items; {@code hiring} is the animated croupier hiring (a player and a
     * croupier, no item).
     */
    public record Scene(int x, int y, List<String> items, boolean hiring) implements Widget {
        public Scene {
            items = List.copyOf(items);
        }
    }

    /** A villager figure; {@code croupier} = with the croupier profession. */
    public record Entity(int x, int y, boolean croupier) implements Widget {
    }

    public record Arrow(int x, int y) implements Widget {
    }

    /** Divider line. */
    public record Rule(int x, int y) implements Widget {
    }

    /** The variant selector of a group (the dialogs show every variant, so it has no content). */
    public record Chips(int x, int y, List<String> refs) implements Widget {
        public Chips {
            refs = List.copyOf(refs);
        }
    }

    /** The hovered tile's scene (no hover in dialogs, no content). */
    public record Preview(int x, int y) implements Widget {
    }

    /** One spread. */
    public record Page(int number, String chapter, List<Widget> widgets) {
        public Page {
            widgets = List.copyOf(widgets);
        }
    }

    /** A bookmark: label key {@code guide.roulettecasino.<label>}, target spread, icon. */
    public record Tab(String label, int page, String ref) {
        public String key() {
            return "guide.roulettecasino." + label;
        }
    }

    private final List<Page> pages;
    private final List<Tab> tabs;
    private final Map<String, Integer> index;
    private final Map<String, String> names;
    private final Map<String, String> german;

    private GuideBook(List<Page> pages, List<Tab> tabs, Map<String, Integer> index, Map<String, String> names,
                      Map<String, String> german) {
        this.pages = List.copyOf(pages);
        this.tabs = List.copyOf(tabs);
        this.index = Collections.unmodifiableMap(index);
        this.names = Collections.unmodifiableMap(names);
        this.german = Collections.unmodifiableMap(german);
    }

    public static GuideBook load(InputStream in) {
        Objects.requireNonNull(in, FILE + " is missing in the plugin jar");
        try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return parse(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + FILE, e);
        }
    }

    static GuideBook parse(JsonObject root) {
        JsonObject book = root.getAsJsonObject("book");
        if (book.get("format").getAsInt() != 2) {
            throw new IllegalArgumentException("casino guide format " + book.get("format") + ", expected 2");
        }
        Map<String, String> names = strings(root.getAsJsonObject("names"));
        Map<String, String> german = strings(root.getAsJsonObject("texts"));
        List<Tab> tabs = new ArrayList<>();
        for (JsonElement e : book.getAsJsonArray("tabs")) {
            JsonObject t = e.getAsJsonObject();
            tabs.add(new Tab(t.get("label").getAsString(), t.get("page").getAsInt(), t.get("ref").getAsString()));
        }
        Map<String, Integer> index = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : book.getAsJsonObject("index").entrySet()) {
            JsonElement v = e.getValue();
            index.put(e.getKey(), v.isJsonArray() ? v.getAsJsonArray().get(0).getAsInt() : v.getAsInt());
        }
        List<Page> pages = new ArrayList<>();
        JsonArray raw = book.getAsJsonArray("pages");
        for (int i = 0; i < raw.size(); i++) {
            JsonObject p = raw.get(i).getAsJsonObject();
            JsonArray widgets = p.getAsJsonArray("widgets");
            List<Widget> out = new ArrayList<>();
            for (int w = 0; w < widgets.size(); w++) {
                out.add(widget(widgets, w));
            }
            pages.add(new Page(i + 1, p.has("chapter") ? p.get("chapter").getAsString() : "", out));
        }
        return new GuideBook(pages, tabs, index, names, german);
    }

    private static Map<String, String> strings(JsonObject o) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            out.put(e.getKey(), e.getValue().getAsString());
        }
        return out;
    }

    private static Widget widget(JsonArray widgets, int i) {
        JsonObject w = widgets.get(i).getAsJsonObject();
        int x = w.get("x").getAsInt();
        int y = w.get("y").getAsInt();
        String kind = w.get("kind").getAsString();
        return switch (kind) {
            case "title" -> new Title(x, y, w.get("key").getAsString());
            case "label" -> new Label(x, y, w.get("key").getAsString(), Color.of(string(w, "color", "cream")),
                    w.has("bold") && w.get("bold").getAsBoolean());
            case "text" -> new Text(x, y, w.get("key").getAsString(), Color.of(string(w, "color", "cream")));
            case "tile" -> new Tile(x, y, w.get("ref").getAsString(), w.has("key") ? w.get("key").getAsString() : null,
                    w.get("page").getAsInt());
            case "item" -> new Item(x, y, w.get("ref").getAsString(), integer(w, "count", 1), integer(w, "size", 16));
            case "recipe" -> {
                List<String> cells = new ArrayList<>();
                for (JsonElement c : w.getAsJsonArray("cells")) {
                    cells.add(c.isJsonNull() ? null : c.getAsString());
                }
                if (cells.size() != 9) {
                    throw new IllegalArgumentException("recipe " + w.get("result") + " has " + cells.size() + " cells");
                }
                yield new Recipe(x, y, w.get("result").getAsString(), integer(w, "count", 1), cells,
                        w.has("shapeless") && w.get("shapeless").getAsBoolean());
            }
            case "scene" -> {
                JsonObject scene = w.getAsJsonObject("scene");
                boolean hiring = scene.has("hiring") && scene.get("hiring").getAsBoolean();
                yield new Scene(x, y, hiring ? List.of() : sceneItems(scene, nextRecipeResult(widgets, i)), hiring);
            }
            case "entity" -> new Entity(x, y, w.get("profession").getAsBoolean());
            case "arrow" -> new Arrow(x, y);
            case "rule" -> new Rule(x, y);
            case "chips" -> {
                List<String> refs = new ArrayList<>();
                for (JsonElement r : w.getAsJsonArray("refs")) {
                    refs.add(r.getAsString());
                }
                yield new Chips(x, y, refs);
            }
            case "preview" -> new Preview(x, y);
            default -> throw new IllegalArgumentException("unknown guide widget kind " + kind);
        };
    }

    /** The result of the recipe that follows a scene in the same variant (before the next scene), or null. */
    private static String nextRecipeResult(JsonArray widgets, int scene) {
        JsonObject s = widgets.get(scene).getAsJsonObject();
        for (int i = scene + 1; i < widgets.size(); i++) {
            JsonObject w = widgets.get(i).getAsJsonObject();
            String kind = w.get("kind").getAsString();
            if (kind.equals("scene")) {
                return null;
            }
            if (kind.equals("recipe") && Objects.equals(w.get("group"), s.get("group"))
                    && Objects.equals(w.get("variant"), s.get("variant"))) {
                return w.get("result").getAsString();
            }
        }
        return null;
    }

    /**
     * The main items of a scene (the 3D scene itself cannot be shown in a dialog): the item whose recipe follows the
     * scene if the scene contains it (the decoration spreads: the piece on its floor or wall); else the machines;
     * else the sofa; else the topmost single blocks, or the topmost filled areas (the carpets and floors).
     */
    static List<String> sceneItems(JsonObject scene, String recipeResult) {
        List<String> machines = new ArrayList<>();
        Set<String> all = new LinkedHashSet<>();
        boolean sofa = false;
        List<JsonObject> singles = new ArrayList<>();
        List<JsonObject> areas = new ArrayList<>();
        for (JsonElement e : scene.getAsJsonArray("blocks")) {
            JsonObject b = e.getAsJsonObject();
            if (b.has("machine")) {
                String id = b.get("machine").getAsString();
                if (!machines.contains(id)) {
                    machines.add(id);
                }
                all.add(id);
            } else if (b.has("sofa")) {
                sofa = true;
                all.add("casinodeco:casino_sofa");
            } else if (b.has("state")) {
                all.add(blockId(b.get("state").getAsString()));
                (b.has("to") ? areas : singles).add(b);
            }
        }
        if (recipeResult != null && all.contains(recipeResult)) {
            return List.of(recipeResult);
        }
        if (!machines.isEmpty()) {
            return machines;
        }
        if (sofa) {
            return List.of("casinodeco:casino_sofa");
        }
        List<JsonObject> pool = singles.isEmpty() ? areas : singles;
        int top = Integer.MIN_VALUE;
        for (JsonObject b : pool) {
            top = Math.max(top, b.getAsJsonArray("at").get(1).getAsInt());
        }
        Set<String> out = new LinkedHashSet<>();
        for (JsonObject b : pool) {
            if (b.getAsJsonArray("at").get(1).getAsInt() == top) {
                out.add(blockId(b.get("state").getAsString()));
            }
        }
        return List.copyOf(out);
    }

    private static String blockId(String state) {
        int bracket = state.indexOf('[');
        return bracket < 0 ? state : state.substring(0, bracket);
    }

    private static String string(JsonObject o, String name, String fallback) {
        return o.has(name) ? o.get(name).getAsString() : fallback;
    }

    private static int integer(JsonObject o, String name, int fallback) {
        return o.has(name) ? o.get(name).getAsInt() : fallback;
    }

    // ---- queries ---------------------------------------------------------------------------------------------------

    /** Number of spreads (34). */
    public int size() {
        return pages.size();
    }

    public boolean valid(int page) {
        return page >= 1 && page <= pages.size();
    }

    /** Spread {@code number} (1-based). */
    public Page page(int number) {
        if (!valid(number)) {
            throw new IllegalArgumentException("no guide page " + number);
        }
        return pages.get(number - 1);
    }

    public List<Page> pages() {
        return pages;
    }

    public List<Tab> tabs() {
        return tabs;
    }

    /** The spread the mod jumps to when the item is clicked elsewhere ({@code index}), if any. */
    public OptionalInt indexPage(String id) {
        Integer page = index.get(id);
        return page == null ? OptionalInt.empty() : OptionalInt.of(page);
    }

    /** The mod's German text of a lang key, or null if the guide data has none (vanilla keys). */
    public String text(String key) {
        return german.get(key);
    }

    /** Every lang key with German text (tests). */
    public Set<String> germanKeys() {
        return german.keySet();
    }

    /** The lang key of a casino item's name ({@code block.<ns>.<id>} or {@code item.<ns>.<id>}), null for others. */
    public String nameKey(String id) {
        return names.get(id);
    }

    /** The item an ingredient shows: a tag's first member, else the id itself. */
    public static String shown(String ref) {
        if (!ref.startsWith("#")) {
            return ref;
        }
        String member = TAG_SHOWN.get(ref);
        if (member == null) {
            throw new IllegalArgumentException("no item to show for tag " + ref);
        }
        return member;
    }
}
