package de.tigges.kaboofcasino.packtool;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;

/** The mod's {@code sounds.json} reduced to the events of the shipped games, plus their .ogg files. */
final class Sounds {

    private Sounds() {
    }

    static void write(ModJar jar, PackTree tree, List<String> events) {
        JsonObject all = Json.parse(jar.bytes("assets/roulettecasino/sounds.json"));
        JsonObject out = new JsonObject();
        for (String event : events) {
            JsonObject entry = all.getAsJsonObject(event);
            if (entry == null) {
                throw new IllegalStateException("sounds.json has no " + event);
            }
            out.add(event, entry);
            for (JsonElement s : entry.getAsJsonArray("sounds")) {
                String name = s.isJsonPrimitive() ? s.getAsString() : s.getAsJsonObject().get("name").getAsString();
                ModelCopier.Id id = ModelCopier.Id.parse(name);
                String path = "assets/" + id.namespace() + "/sounds/" + id.path() + ".ogg";
                tree.put(path, jar.bytes(path));
            }
        }
        tree.put("assets/roulettecasino/sounds.json", Json.bytes(out));
    }
}
