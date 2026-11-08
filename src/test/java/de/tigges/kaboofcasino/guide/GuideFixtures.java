package de.tigges.kaboofcasino.guide;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Test data of the guide: the book the plugin ships ({@code pack/guide.json} on the test classpath, built by the pack
 * tool), the mod's own guide and German texts read from the pinned mod jar, fixed texts, and a plain rendering of
 * components and views for comparisons.
 */
final class GuideFixtures {

    private static final String FALLBACK = "/root/Jack_DONT_TOUCH/plugins-src/Casino-work/mod/";
    private static GuideBook book;
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
        try (InputStream in = Files.newInputStream(Path.of("gradle.properties"))) {
            Properties p = new Properties();
            p.load(in);
            Path configured = Path.of(p.getProperty("casino.modJar"));
            if (Files.isRegularFile(configured)) {
                return configured;
            }
            Path fallback = Path.of(FALLBACK).resolve(configured.getFileName());
            if (Files.isRegularFile(fallback)) {
                return fallback;
            }
            throw new IllegalStateException("mod jar not found: " + configured.toAbsolutePath() + " or " + fallback);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Fixed plugin texts; a vanilla item's name is the translatable key {@code <id>} (the server has the real key). */
    static GuideTexts texts() {
        return new GuideTexts(Component.text("Rezept"), Component.text(" (beliebige Sorte)"),
                Component.text("CREDITS"), page -> Component.text("Zur Seite " + page),
                id -> Component.translatable(id));
    }

    static GuideView view(int page, boolean history) {
        return new GuideDialogs(book()).view(page, history, texts());
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
        if (c instanceof TextComponent t) {
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

    /** Every translatable key in a component tree. */
    static List<TranslatableComponent> translatables(Component c) {
        List<TranslatableComponent> out = new ArrayList<>();
        collect(c, out);
        return out;
    }

    private static void collect(Component c, List<TranslatableComponent> out) {
        if (c instanceof TranslatableComponent t) {
            out.add(t);
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

    /** A view as text lines (one per body element, then the buttons and the exit button). */
    static String describe(GuideView v) {
        StringBuilder out = new StringBuilder();
        out.append("title ").append(plain(v.title())).append('\n');
        for (GuideView.Body b : v.body()) {
            switch (b) {
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
