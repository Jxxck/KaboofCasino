package de.tigges.kaboofcasino.packtool;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;

/**
 * Copies the mod's lang files of a namespace, reduced to the keys the shipped parts use (subtitles of the shipped
 * sounds above all). Keys keep the mod's order. All 12 languages are kept, so every client gets its own subtitles.
 * The plugin's own texts (the chip names) go to the {@code kaboofcasino} namespace in the same 12 languages
 * ({@link #own}).
 */
final class Lang {

    private Lang() {
    }

    static void filter(ModJar jar, PackTree tree, String namespace, List<String> prefixes) {
        String dir = "assets/" + namespace + "/lang/";
        for (String path : jar.list(dir)) {
            if (!path.endsWith(".json")) {
                continue;
            }
            JsonObject all = Json.parse(jar.bytes(path));
            JsonObject kept = tree.has(path) ? Json.parse(tree.get(path)) : new JsonObject();
            for (Map.Entry<String, JsonElement> e : all.entrySet()) {
                // "key=" names one exact key, anything else is a prefix
                if (prefixes.stream().anyMatch(p -> p.endsWith("=") ? e.getKey().equals(p.substring(0,
                        p.length() - 1)) : e.getKey().startsWith(p))) {
                    kept.add(e.getKey(), e.getValue());
                }
            }
            if (tree.has(path)) {
                tree.replace(path, Json.bytes(kept));
            } else {
                tree.put(path, Json.bytes(kept));
            }
        }
    }

    /**
     * Adds the plugin's own keys (texts the mod does not have) to {@code assets/kaboofcasino/lang/<code>.json} for each
     * of the mod's languages: the German text for {@code de_de}, the English one for every other language (what the
     * client shows anyway, as it loads {@code en_us} under every language; written out so all 12 files have every
     * key). Keys keep the given order.
     */
    static void own(ModJar jar, PackTree tree, Map<String, String> german, Map<String, String> english) {
        if (!german.keySet().equals(english.keySet())) {
            throw new IllegalArgumentException("German and English keys differ: " + german.keySet() + " / "
                    + english.keySet());
        }
        String dir = "assets/roulettecasino/lang/";
        for (String modPath : jar.list(dir)) {
            if (!modPath.endsWith(".json")) {
                continue;
            }
            String code = modPath.substring(dir.length(), modPath.length() - ".json".length());
            String path = "assets/kaboofcasino/lang/" + code + ".json";
            JsonObject kept = tree.has(path) ? Json.parse(tree.get(path)) : new JsonObject();
            (code.equals("de_de") ? german : english).forEach(kept::addProperty);
            if (tree.has(path)) {
                tree.replace(path, Json.bytes(kept));
            } else {
                tree.put(path, Json.bytes(kept));
            }
        }
    }
}
