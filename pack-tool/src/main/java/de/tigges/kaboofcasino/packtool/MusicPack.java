package de.tigges.kaboofcasino.packtool;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The optional second pack (render.md 6, PLAN decision 5): the mod's lounge music ({@code casinosound}), six streamed
 * Ogg tracks by Kevin MacLeod under CC BY 4.0. Contents, all from the pinned mod jar:
 * <ul>
 *   <li>{@code assets/casinosound/sounds.json} unchanged (six events {@code casinosound:<id>}, one streamed file
 *       each) and {@code assets/casinosound/sounds/music/<id>.ogg} unchanged (stereo, 44.1 kHz; already converted
 *       and loudness-normalised by the mod);</li>
 *   <li>attribution: {@code pack.mcmeta} description, {@code credits/CREDITS.md} (titles, author, licence link,
 *       changes, source links), the mod's {@code MUSIC-CREDITS.md}, the CC BY 4.0 legal code, the mod's MIT licence
 *       and its {@code music-credits.json};</li>
 *   <li>{@code pack.png}: the speaker's icon.</li>
 * </ul>
 * Nothing else: the casino pack never contains the music, and this pack never touches a casino asset, so each hash
 * changes only with its own content. Deterministic like the casino pack ({@link PackTree} + {@link ZipOut}; the
 * oggs are stored).
 */
final class MusicPack {

    static final String ZIP = "KaboofCasino-music.zip";
    static final String CC_BY = "https://creativecommons.org/licenses/by/4.0/";
    /** {@code MusicTimeline.IDS}, in the mod's order. */
    static final List<String> TRACKS = List.of("airport_lounge", "backbay_lounge", "jazz_brunch",
            "george_street_shuffle", "cool_vibes", "bossa_antigua");

    /** One build. */
    record Result(byte[] zip, String sha1, int files) {
    }

    /** A track as listed in the mod's {@code music-credits.json}. */
    record Track(String id, String title, String artist, String source, String license) {
    }

    private MusicPack() {
    }

    static Result build(ModJar jar) {
        PackTree tree = new PackTree();
        byte[] soundsJson = jar.bytes("assets/casinosound/sounds.json");
        checkSounds(Json.parse(soundsJson));
        tree.put("assets/casinosound/sounds.json", soundsJson);
        for (String id : TRACKS) {
            String path = "assets/casinosound/sounds/music/" + id + ".ogg";
            byte[] ogg = jar.bytes(path);
            if (ogg.length < 4 || ogg[0] != 'O' || ogg[1] != 'g' || ogg[2] != 'g' || ogg[3] != 'S') {
                throw new IllegalStateException(path + " is not an Ogg stream");
            }
            tree.put(path, ogg);
        }
        byte[] creditsJson = jar.bytes("assets/casinosound/music-credits.json");
        List<Track> tracks = tracks(Json.parse(creditsJson));
        tree.put("assets/casinosound/music-credits.json", creditsJson);
        tree.put("credits/MUSIC-CREDITS.md", jar.bytes("MUSIC-CREDITS.md"));
        tree.put("credits/LICENSE-MUSIC-CC-BY-4.0.txt", jar.bytes("LICENSE-MUSIC-CC-BY-4.0.txt"));
        tree.put("credits/LICENSE.txt", jar.bytes("LICENSE.txt"));
        tree.putText("credits/CREDITS.md", credits(tracks));
        tree.put("pack.png", jar.bytes("assets/casinosound/icon.png"));
        tree.put("pack.mcmeta", Json.bytes(mcmeta(tracks)));
        byte[] zip = ZipOut.write(tree.files());
        return new Result(zip, ModJar.sha1Hex(zip), tree.size());
    }

    /** The mod's sounds.json must be exactly the six streamed events the plugin plays. */
    static void checkSounds(JsonObject sounds) {
        Set<String> keys = new TreeSet<>(sounds.keySet());
        if (!keys.equals(new TreeSet<>(TRACKS))) {
            throw new IllegalStateException("casinosound sounds.json has " + keys + ", expected " + TRACKS);
        }
        for (String id : TRACKS) {
            JsonArray list = sounds.getAsJsonObject(id).getAsJsonArray("sounds");
            if (list.size() != 1) {
                throw new IllegalStateException(id + ": expected one file");
            }
            JsonObject s = list.get(0).getAsJsonObject();
            if (!s.get("name").getAsString().equals("casinosound:music/" + id) || !s.has("stream")
                    || !s.get("stream").getAsBoolean()) {
                throw new IllegalStateException(id + ": expected the streamed file casinosound:music/" + id);
            }
        }
    }

    /** The six tracks from music-credits.json, in the mod's order, checked against {@link #TRACKS}. */
    static List<Track> tracks(JsonObject credits) {
        List<Track> out = new ArrayList<>();
        for (JsonElement e : credits.getAsJsonArray("tracks")) {
            JsonObject t = e.getAsJsonObject();
            out.add(new Track(t.get("id").getAsString(), t.get("title").getAsString(), t.get("artist").getAsString(),
                    t.get("source").getAsString(), t.get("license").getAsString()));
        }
        if (!out.stream().map(Track::id).toList().equals(TRACKS)) {
            throw new IllegalStateException("music-credits.json lists " + out + ", expected " + TRACKS);
        }
        for (Track t : out) {
            if (!t.license().equals(CC_BY)) {
                throw new IllegalStateException(t.id() + " is not CC BY 4.0: " + t.license());
            }
        }
        return out;
    }

    static JsonObject mcmeta(List<Track> tracks) {
        Set<String> artists = new TreeSet<>();
        tracks.forEach(t -> artists.add(t.artist()));
        JsonObject title = new JsonObject();
        title.addProperty("text", "KaboofCasino Musik");
        title.addProperty("color", "gold");
        JsonObject rest = new JsonObject();
        rest.addProperty("text", " - Lounge-Musik: " + String.join(", ", artists)
                + " (incompetech.com), CC BY 4.0, " + CC_BY + " - aus MineVegas Casino von byMehd, " + Credits.MOD_URL);
        rest.addProperty("color", "gray");
        JsonArray extra = new JsonArray();
        extra.add(rest);
        title.add("extra", extra);
        JsonObject pack = new JsonObject();
        pack.add("description", title);
        pack.addProperty("min_format", 97);
        pack.addProperty("max_format", 97);
        return Json.object("pack", pack);
    }

    static String credits(List<Track> tracks) {
        StringBuilder list = new StringBuilder();
        for (Track t : tracks) {
            list.append("- \"").append(t.title()).append("\" - ").append(t.artist())
                    .append(" (incompetech.com), licensed under CC BY 4.0 (").append(t.license()).append("), source: ")
                    .append(t.source()).append('\n');
        }
        return """
                # KaboofCasino music pack

                Optional second resource pack of the Paper plugin KaboofCasino by %s. It contains the lounge music
                that the Minecraft mod **MineVegas Casino** 1.1.0 by byMehd plays near switched-on casino speakers:

                %s

                Music:

                %s
                Licence: Creative Commons Attribution 4.0 International, %s (full legal code in
                LICENSE-MUSIC-CC-BY-4.0.txt in this folder). No endorsement by the composer is implied.

                Changes: made by MineVegas Casino and taken over unchanged: the MP3 files were converted to Ogg Vorbis
                and their loudness normalised to -20 LUFS; no composition edits. KaboofCasino copies the Ogg files and
                the mod's sounds.json without any further change.

                Details per track (ISRC, original file): MUSIC-CREDITS.md and assets/casinosound/music-credits.json
                (both from the mod). The mod's own licence (MIT with the condition to link the page above):
                LICENSE.txt.
                """.formatted(Credits.AUTHOR, Credits.MOD_URL, list, CC_BY);
    }
}
