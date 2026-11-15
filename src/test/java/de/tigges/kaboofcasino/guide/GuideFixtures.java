package de.tigges.kaboofcasino.guide;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Test data of the guide: the book the plugin ships ({@code pack/guide.json} on the test classpath, built by the pack
 * tool), the mod's own guide and German texts read from the pinned mod jar, fixed texts, and a plain rendering of
 * components and views for comparisons.
 */
final class GuideFixtures {

    private static GuideBook book;
    private static RecipePictures pictures;
    private static Map<String, String> german;
    private static JsonObject modGuide;

    private GuideFixtures() {
    }

    static synchronized GuideBook book() {
        if (book == null) {
            book = GuideBook.load(GuideFixtures.class.getClassLoader().getResourceAsStream(GuideBook.RESOURCE));
        }
        return book;
    }

    /** The recipe pictures the plugin ships ({@code pack/guide-pictures.json}, built by the pack tool). */
    static synchronized RecipePictures pictures() {
        if (pictures == null) {
            pictures = RecipePictures.load(
                    GuideFixtures.class.getClassLoader().getResourceAsStream(RecipePictures.RESOURCE));
        }
        return pictures;
    }

    /** The mod's de_de.json of its three namespaces, merged. */
    static synchronized Map<String, String> modGerman() {
        if (german == null) {
            Map<String, String> out = new TreeMap<>();
            for (String ns : List.of("roulettecasino", "casinodeco", "casinosound")) {
                JsonObject lang = modJson("assets/" + ns + "/lang/de_de.json");
                for (Map.Entry<String, JsonElement> e : lang.entrySet()) {
                    out.put(e.getKey(), e.getValue().getAsString());
                }
            }
            german = out;
        }
        return german;
    }

    /** The mod's casino_guide.json, straight from the jar. */
    static synchronized JsonObject modGuide() {
        if (modGuide == null) {
            modGuide = modJson("assets/roulettecasino/casino_guide.json");
        }
        return modGuide;
    }

    private static JsonObject modJson(String path) {
        try (ZipFile zip = new ZipFile(modJar().toFile())) {
            ZipEntry e = zip.getEntry(path);
            if (e == null) {
                throw new IllegalStateException("not in the mod jar: " + path);
            }
            try (InputStream in = zip.getInputStream(e)) {
                return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** {@code casino.modJar} of gradle.properties, else the same file in the research checkout. */
    private static Path modJar() {
        return de.tigges.kaboofcasino.TestModJar.path();
    }

    /**
     * Fixed plugin texts; a vanilla item's name is the translatable key {@code <id>} (the server has the real key); an
     * item's tooltip is its name as a text tooltip (the server sends the item stack itself).
     */
    static GuideTexts texts() {
        return new GuideTexts(Component.text("Rezept"), Component.text(" (beliebige Sorte)"),
                Component.text("CREDITS"), page -> Component.text("Zur Seite " + page),
                id -> Component.translatable(id), id -> HoverEvent.showText(itemName(id)),
                java.util.Map.of("guide.roulettecasino.text.043", Component.text("NOTE-043"),
                        "guide.roulettecasino.text.213", Component.text("NOTE-213")));
    }

    /** An item's name as the dialogs name it: the casino item's key with the mod's German text, else {@code <id>}. */
    static Component itemName(String id) {
        String key = book().nameKey(id);
        if (key == null) {
            return Component.translatable(id);
        }
        String text = book().german(key);
        return text == null ? Component.translatable(key) : Component.translatable(key, text);
    }

    static GuideView view(int page, boolean history) {
        return new GuideDialogs(book(), pictures()).view(page, history, texts());
    }

    /** True if the component draws glyphs of the recipe picture font. */
    static boolean picture(Component c) {
        return pictures().font().equals(c.font());
    }

    /**
     * Plain text of a component: texts as they are, translatables as {@code {key}}, links as {@code [text](do:to)},
     * bold as {@code *text*}.
     */
    static String plain(Component c) {
        StringBuilder out = new StringBuilder();
        plain(c, out);
        return out.toString();
    }

    private static void plain(Component c, StringBuilder out) {
        StringBuilder own = new StringBuilder();
        if (picture(c)) {
            // picture glyphs: an icon is its tooltip in angle brackets, the count "x<digits>", the rest nothing
            if (c.hoverEvent() != null && c.hoverEvent().action() == HoverEvent.Action.SHOW_TEXT) {
                own.append('<').append(plain((Component) c.hoverEvent().value())).append('>');
            } else if (c.shadowColor() == null) {
                own.append('x').append(count(((TextComponent) c).content()));
            }
        } else if (c instanceof TextComponent t) {
            own.append(t.content());
        } else if (c instanceof TranslatableComponent t) {
            own.append('{').append(t.key()).append('}');
        } else {
            own.append('?').append(c.getClass().getSimpleName());
        }
        for (Component child : c.children()) {
            plain(child, own);
        }
        String text = own.toString();
        if (c.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE) {
            text = "*" + text + "*";
        }
        ClickEvent<?> click = c.clickEvent();
        if (click != null && click.payload() instanceof ClickEvent.Payload.Custom custom) {
            text = "[" + text + "](" + custom.key().asString() + " " + custom.nbt().string() + ")";
        } else if (click != null) {
            text = "[" + text + "](" + click.action() + ")";
        }
        out.append(text);
    }

    /**
     * The digits a count glyph run stands for: the pack's count glyphs are cells of the text sheet, whose characters a
     * text font of the pack names.
     */
    static synchronized String count(String glyphs) {
        if (countDigits == null) {
            Map<String, Character> sheet = new java.util.HashMap<>();
            Map<Integer, Character> digits = new java.util.HashMap<>();
            Map<String, byte[]> pack = de.tigges.kaboofcasino.pack.PackFiles.all();
            for (Map.Entry<String, byte[]> e : pack.entrySet()) {
                if (e.getKey().startsWith("assets/kaboofcasino/font/t_")) {
                    for (JsonElement p : json(e.getValue()).getAsJsonArray("providers")) {
                        cells(p.getAsJsonObject(), (cell, cp) -> sheet.putIfAbsent(cell, (char) (int) cp));
                    }
                }
            }
            for (JsonElement p : json(pack.get("assets/kaboofcasino/font/guide_recipe.json"))
                    .getAsJsonArray("providers")) {
                cells(p.getAsJsonObject(), (cell, cp) -> {
                    Character c = sheet.get(cell);
                    if (c != null && cell.startsWith("kaboofcasino:font/text_h16")) {
                        digits.put(cp, c);
                    }
                });
            }
            countDigits = digits;
        }
        StringBuilder out = new StringBuilder();
        glyphs.codePoints().forEach(cp -> out.append(countDigits.getOrDefault(cp, '?')));
        return out.toString();
    }

    private static Map<Integer, Character> countDigits;

    private static JsonObject json(byte[] bytes) {
        return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    /** Every char of a bitmap provider as "file|row,col" -> codepoint. */
    private static void cells(JsonObject provider, java.util.function.BiConsumer<String, Integer> out) {
        if (!provider.get("type").getAsString().equals("bitmap")) {
            return;
        }
        String file = provider.get("file").getAsString().replace(".png", "");
        var rows = provider.getAsJsonArray("chars");
        for (int r = 0; r < rows.size(); r++) {
            int[] cps = rows.get(r).getAsString().codePoints().toArray();
            for (int c = 0; c < cps.length; c++) {
                if (cps[c] != 0) {
                    out.accept(file + "|" + r + "," + c, cps[c]);
                }
            }
        }
    }

    /** Every translatable key in a component tree (text tooltips included). */
    static List<TranslatableComponent> translatables(Component c) {
        List<TranslatableComponent> out = new ArrayList<>();
        collect(c, out);
        return out;
    }

    private static void collect(Component c, List<TranslatableComponent> out) {
        if (c instanceof TranslatableComponent t) {
            out.add(t);
        }
        if (c.hoverEvent() != null && c.hoverEvent().action() == HoverEvent.Action.SHOW_TEXT) {
            collect((Component) c.hoverEvent().value(), out);
        }
        for (Component child : c.children()) {
            collect(child, out);
        }
    }

    /** Every custom click in a component tree, as payload strings. */
    static List<String> clicks(Component c) {
        List<String> out = new ArrayList<>();
        clicks(c, out);
        return out;
    }

    private static void clicks(Component c, List<String> out) {
        if (c.clickEvent() != null && c.clickEvent().payload() instanceof ClickEvent.Payload.Custom custom) {
            if (!custom.key().asString().equals(GuideAction.ID)) {
                throw new AssertionError("foreign click key " + custom.key());
            }
            out.add(custom.nbt().string());
        } else if (c.clickEvent() != null) {
            throw new AssertionError("non-custom click " + c.clickEvent());
        }
        for (Component child : c.children()) {
            clicks(child, out);
        }
    }

    /** True if a text body is a recipe picture. */
    static boolean picture(GuideView.TextBody t) {
        return t.text().children().stream().anyMatch(line -> line.children().stream()
                .anyMatch(GuideFixtures::picture));
    }

    /**
     * A recipe picture as text: the caption, then its icons and the count in drawing order (line by line, as the
     * client draws them), e.g. {@code Rezept · {name} | <{minecraft:paper}> ... x2}.
     */
    static String describePicture(Component picture) {
        String caption = null;
        List<String> marks = new ArrayList<>();
        for (Component line : picture.children()) {
            for (Component run : line.children()) {
                if (!picture(run)) {
                    caption = plain(run);
                } else {
                    String mark = plain(run);
                    if (!mark.isEmpty()) {
                        marks.add(mark);
                    }
                }
            }
        }
        return caption + " | " + String.join(" ", marks);
    }

    /** A view as text lines (one per body element, then the buttons and the exit button). */
    static String describe(GuideView v) {
        StringBuilder out = new StringBuilder();
        out.append("title ").append(plain(v.title())).append('\n');
        for (GuideView.Body b : v.body()) {
            switch (b) {
                case GuideView.TextBody t when picture(t) -> out.append("recipe(").append(t.width()).append(") ")
                        .append(describePicture(t.text())).append('\n');
                case GuideView.TextBody t -> out.append("text(").append(t.width()).append(") ")
                        .append(plain(t.text()).replace("\n", " / ")).append('\n');
                case GuideView.ItemBody i -> {
                    out.append("item ").append(i.item());
                    if (i.count() != 1) {
                        out.append(" x").append(i.count());
                    }
                    if (i.description() != null) {
                        out.append(" | ").append(plain(i.description()).replace("\n", " / "));
                    }
                    out.append('\n');
                }
            }
        }
        for (GuideView.Button b : v.buttons()) {
            out.append("button ").append(plain(b.label())).append(" -> ").append(b.action().snbt()).append('\n');
        }
        out.append("exit ").append(plain(v.exit().label())).append(" -> ").append(v.exit().action().snbt());
        return out.toString();
    }
}
