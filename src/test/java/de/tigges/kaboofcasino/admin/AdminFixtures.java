package de.tigges.kaboofcasino.admin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;

/**
 * Test data of the croupier's hand: the mod's German texts from the pinned jar ({@code casino.modJar}, read as a zip),
 * and a plain rendering of components as a German client without the pack's keys shows them (fallback with its
 * {@code %s} arguments; vanilla keys as {@code {key}}).
 */
final class AdminFixtures {

    private static final String FALLBACK = "/root/Jack_DONT_TOUCH/plugins-src/Casino-work/mod/";
    private static Map<String, String> german;

    private AdminFixtures() {
    }

    static synchronized Map<String, String> modGerman() {
        if (german == null) {
            Map<String, String> out = new TreeMap<>();
            try (ZipFile zip = new ZipFile(jar().toFile())) {
                ZipEntry e = zip.getEntry("assets/roulettecasino/lang/de_de.json");
                try (InputStream in = zip.getInputStream(e)) {
                    JsonObject json = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                            .getAsJsonObject();
                    for (Map.Entry<String, JsonElement> kv : json.entrySet()) {
                        out.put(kv.getKey(), kv.getValue().getAsString());
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            german = out;
        }
        return german;
    }

    private static Path jar() {
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
            throw new IllegalStateException("mod jar not found: " + configured.toAbsolutePath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The text a German client without the key shows: fallbacks formatted, children appended. */
    static String plain(Component c) {
        StringBuilder out = new StringBuilder();
        plain(c, out);
        return out.toString();
    }

    private static void plain(Component c, StringBuilder out) {
        switch (c) {
            case TextComponent t -> out.append(t.content());
            case TranslatableComponent t -> {
                if (t.fallback() == null) {
                    out.append('{').append(t.key()).append('}');
                } else {
                    String format = t.fallback();
                    List<TranslationArgument> args = t.arguments();
                    int next = 0;
                    for (int i = 0; i < format.length(); i++) {
                        char ch = format.charAt(i);
                        if (ch == '%' && i + 1 < format.length() && format.charAt(i + 1) == 's') {
                            out.append(plain(args.get(next++).asComponent()));
                            i++;
                        } else if (ch == '%' && i + 1 < format.length() && format.charAt(i + 1) == '%') {
                            out.append('%');
                            i++;
                        } else {
                            out.append(ch);
                        }
                    }
                }
            }
            default -> throw new IllegalArgumentException("unexpected component " + c);
        }
        for (Component child : c.children()) {
            plain(child, out);
        }
    }

    /** A view as text lines (for snapshots). */
    static String describe(AdminView v) {
        StringBuilder out = new StringBuilder();
        out.append("title ").append(plain(v.title())).append('\n');
        for (AdminView.Text t : v.body()) {
            out.append("text ").append(plain(t.text()).replace("\n", " / ")).append('\n');
        }
        for (AdminView.Input in : v.inputs()) {
            switch (in) {
                case AdminView.TextInput t -> out.append("text-input ").append(t.key()).append(" [")
                        .append(plain(t.label())).append("] '").append(t.initial()).append("' max ")
                        .append(t.maxLength()).append('\n');
                case AdminView.BoolInput b -> out.append("checkbox ").append(b.key()).append(" [")
                        .append(plain(b.label())).append("] ").append(b.initial()).append('\n');
                case AdminView.RangeInput r -> out.append("slider ").append(r.key()).append(" [")
                        .append(plain(r.label())).append("] ").append(r.labelFormat()).append(' ').append(r.start())
                        .append("..").append(r.end()).append(" = ").append(r.initial()).append('\n');
            }
        }
        for (AdminView.Button b : v.buttons()) {
            out.append("button ").append(plain(b.label())).append(" -> ").append(b.action().snbt());
            if (b.tooltip() != null) {
                out.append(" (").append(plain(b.tooltip())).append(')');
            }
            out.append('\n');
        }
        out.append("exit ").append(plain(v.exit().label())).append(" -> ").append(v.exit().action().snbt());
        return out.toString();
    }
}
