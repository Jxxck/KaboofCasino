package de.tigges.kaboofcasino.packtool;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the KaboofCasino resource pack.
 *
 * <pre>
 * PackTool --mod-jar &lt;jar&gt; --mod-sha1 &lt;hex&gt; --src &lt;pack-src&gt; --out &lt;dir&gt;
 * </pre>
 * Writes {@code KaboofCasino-pack.zip}, {@code KaboofCasino-pack.zip.sha1} and {@code gui-layout.json} into the
 * output directory. Same inputs give byte-identical output (sorted entries, fixed timestamps, deterministic PNG and
 * JSON writers), so the SHA-1 only changes when the content does and clients keep their cached copy.
 */
public final class PackTool {

    static final String ZIP = "KaboofCasino-pack.zip";
    static final String LAYOUT = "gui-layout.json";

    private PackTool() {
    }

    /** The result of one build. */
    record Result(byte[] zip, String sha1, byte[] layout, int files, List<TexturePadding.Padded> padded) {
    }

    public static void main(String[] args) {
        Map<String, String> opts = new HashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) {
            opts.put(args[i], args[i + 1]);
        }
        Path jar = Path.of(required(opts, "--mod-jar"));
        Path src = Path.of(required(opts, "--src"));
        Path out = Path.of(required(opts, "--out"));
        Result result = build(jar, required(opts, "--mod-sha1"), src);
        try {
            Files.createDirectories(out);
            Files.write(out.resolve(ZIP), result.zip());
            Files.writeString(out.resolve(ZIP + ".sha1"), result.sha1() + "\n", StandardCharsets.UTF_8);
            Files.write(out.resolve(LAYOUT), result.layout());
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write to " + out, e);
        }
        System.out.println("KaboofCasino pack: " + result.files() + " files, " + result.zip().length + " bytes, sha1 "
                + result.sha1() + (result.padded().isEmpty() ? "" : ", padded " + result.padded()));
    }

    private static String required(Map<String, String> opts, String key) {
        String value = opts.get(key);
        if (value == null) {
            throw new IllegalArgumentException("missing " + key);
        }
        return value;
    }

    /** Builds the whole pack in memory. */
    static Result build(Path modJar, String modSha1, Path packSrc) {
        ModJar jar = ModJar.open(modJar, modSha1);
        PackTree tree = new PackTree();
        TextGlyphs text = TextGlyphs.load(packSrc.resolve("vanilla"));
        GuiFont font = new GuiFont(tree);

        Credits.add(jar, tree);
        SlotMachinePack.add(jar, tree, packSrc);
        SlotMachineGui.add(jar, tree, font, text);
        tree.put("assets/kaboofcasino/items/gui/blank.json",
                Json.bytes(Json.object("model", Json.object("type", new com.google.gson.JsonPrimitive(
                        "minecraft:empty")))));
        text.writeTo(tree, font.textRows());
        font.write();
        List<TexturePadding.Padded> padded = TexturePadding.apply(tree);

        byte[] zip = ZipOut.write(tree.files());
        JsonObject layout = font.manifest(text);
        return new Result(zip, ModJar.sha1Hex(zip), Json.bytes(layout), tree.size(), padded);
    }
}
