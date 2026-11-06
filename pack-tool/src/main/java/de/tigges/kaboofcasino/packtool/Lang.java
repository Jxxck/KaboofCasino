package de.tigges.kaboofcasino.packtool;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;

/**
 * Copies the mod's lang files of a namespace, reduced to the keys the shipped parts use (subtitles of the shipped
 * sounds above all). Keys keep the mod's order. All 12 languages are kept, so every client gets its own subtitles.
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
                if (prefixes.stream().anyMatch(e.getKey()::startsWith)) {
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
}
