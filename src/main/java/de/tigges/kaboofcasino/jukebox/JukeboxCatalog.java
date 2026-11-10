package de.tigges.kaboofcasino.jukebox;

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
import java.util.Optional;
import net.kyori.adventure.key.Key;

/**
 * What the jukebox can play ({@code jukebox/tracks.json} in the plugin jar, the same file the pack tool builds the
 * sound events from): nine genres in tab order and their tracks in list order. A vanilla track plays the client's own
 * file through {@code kaboofcasino:jukebox.<id>} (every player with the casino pack hears it); a lounge track is the
 * music pack's {@code casinosound:<id>} (only players who loaded the music pack hear it). Lengths are the exact file
 * lengths. Pure.
 */
public final class JukeboxCatalog {

    /** One genre: id, German name, the item its tab shows. */
    public record Genre(String id, String name, String icon) {
    }

    /** One track: its sound, length, the genre, the title and the disc its list entry shows. */
    public record Track(String id, String genre, String title, Key sound, long millis, String icon,
                        boolean needsMusicPack) {
    }

    private final List<Genre> genres;
    private final List<Track> tracks;
    private final Map<String, Track> byId = new LinkedHashMap<>();
    private final Map<String, List<Track>> byGenre = new LinkedHashMap<>();

    JukeboxCatalog(List<Genre> genres, List<Track> tracks) {
        this.genres = List.copyOf(genres);
        this.tracks = List.copyOf(tracks);
        for (Genre g : genres) {
            byGenre.put(g.id(), new ArrayList<>());
        }
        for (Track t : tracks) {
            if (byId.put(t.id(), t) != null) {
                throw new IllegalArgumentException("duplicate jukebox track " + t.id());
            }
            List<Track> list = byGenre.get(t.genre());
            if (list == null) {
                throw new IllegalArgumentException("track " + t.id() + " has no genre " + t.genre());
            }
            list.add(t);
        }
        byGenre.replaceAll((k, v) -> List.copyOf(v));
    }

    /** The catalog from the plugin jar. */
    public static JukeboxCatalog load(ClassLoader loader) {
        try (InputStream in = loader.getResourceAsStream("jukebox/tracks.json")) {
            if (in == null) {
                throw new IllegalStateException("jukebox/tracks.json missing from the plugin jar");
            }
            return parse(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static JukeboxCatalog parse(InputStream in) {
        JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        List<Genre> genres = new ArrayList<>();
        for (JsonElement e : root.getAsJsonArray("genres")) {
            JsonObject g = e.getAsJsonObject();
            genres.add(new Genre(g.get("id").getAsString(), g.get("name").getAsString(), g.get("icon").getAsString()));
        }
        List<Track> tracks = new ArrayList<>();
        for (JsonElement e : root.getAsJsonArray("tracks")) {
            JsonObject t = e.getAsJsonObject();
            String id = t.get("id").getAsString();
            boolean lounge = t.has("sound");
            Key sound = lounge ? Key.key(t.get("sound").getAsString()) : Key.key("kaboofcasino", "jukebox." + id);
            long millis = Math.round(t.get("seconds").getAsDouble() * 1000);
            tracks.add(new Track(id, t.get("genre").getAsString(), t.get("title").getAsString(), sound, millis,
                    t.get("icon").getAsString(), lounge));
        }
        return new JukeboxCatalog(genres, tracks);
    }

    public List<Genre> genres() {
        return genres;
    }

    public List<Track> tracks() {
        return tracks;
    }

    public Optional<Track> track(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Optional<Genre> genre(String id) {
        return genres.stream().filter(g -> g.id().equals(id)).findFirst();
    }

    public List<Track> of(String genre) {
        return byGenre.getOrDefault(genre, Collections.emptyList());
    }

    public int genreIndex(String genre) {
        for (int i = 0; i < genres.size(); i++) {
            if (genres.get(i).id().equals(genre)) {
                return i;
            }
        }
        return -1;
    }
}
