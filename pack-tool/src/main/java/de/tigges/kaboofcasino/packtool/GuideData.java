package de.tigges.kaboofcasino.packtool;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The casino guide for the plugin (games.md 11): {@code guide.json} next to the pack (embedded in the plugin jar as
 * {@code pack/guide.json}), read by {@code de.tigges.kaboofcasino.guide.GuideBook}. It holds
 * <ul>
 *   <li>{@code book}: the mod's {@code assets/roulettecasino/casino_guide.json} (format 2) unchanged;</li>
 *   <li>{@code names}: for every casino item id the guide shows (tiles, items, chips, recipe cells and results, scene
 *       blocks, tabs, index) the lang key of its name ({@code block.<ns>.<id>}, else {@code item.<ns>.<id>});</li>
 *   <li>{@code de}: the German text (the mod's {@code de_de.json} of its three namespaces) of every lang key the
 *       dialogs use: widget keys, tab labels, the screen's buttons, the shapeless hint, the guide's own name, the
 *       croupier and the item names above. The dialogs send these keys as translatable components with the German
 *       text as fallback, so the pack's language files win.</li>
 * </ul>
 * Sorted maps, compact JSON: same jar, same bytes. A key without German text or an id without a name fails the build.
 */
final class GuideData {

    static final String FILE = "guide.json";
    static final String BOOK = "assets/roulettecasino/casino_guide.json";
    /** The mod's namespaces (only their ids get names; {@code minecraft:} names come from the client). */
    static final List<String> NAMESPACES = List.of("roulettecasino", "casinodeco", "casinosound");
    /** Keys the guide screen itself uses (CasinoGuideScreen: buttons, title, shapeless tooltip), and the croupier. */
    static final List<String> SCREEN_KEYS = List.of("item.roulettecasino.casino_guide", "guide.roulettecasino.previous",
            "guide.roulettecasino.next", "guide.roulettecasino.contents", "guide.roulettecasino.close",
            "guide.roulettecasino.expansion.shapeless", "entity.roulettecasino.villager.croupier");

    private GuideData() {
    }

    static byte[] build(ModJar jar) {
        return Json.bytes(data(jar));
    }

    static JsonObject data(ModJar jar) {
        JsonObject book = Json.parse(jar.bytes(BOOK));
        if (!book.has("format") || book.get("format").getAsInt() != 2) {
            throw new IllegalStateException(BOOK + " is not format 2");
        }
        Map<String, String> german = german(jar);
        Set<String> keys = new TreeSet<>(SCREEN_KEYS);
        Set<String> ids = new TreeSet<>();
        for (JsonElement t : book.getAsJsonArray("tabs")) {
            JsonObject tab = t.getAsJsonObject();
            keys.add("guide.roulettecasino." + tab.get("label").getAsString());
            ids.add(tab.get("ref").getAsString());
        }
        ids.addAll(book.getAsJsonObject("index").keySet());
        for (JsonElement p : book.getAsJsonArray("pages")) {
            for (JsonElement w : p.getAsJsonObject().getAsJsonArray("widgets")) {
                collect(w.getAsJsonObject(), keys, ids);
            }
        }
        JsonObject names = new JsonObject();
        for (String id : ids) {
            int colon = id.indexOf(':');
            String ns = id.substring(0, colon);
            if (!NAMESPACES.contains(ns)) {
                continue;
            }
            String path = id.substring(colon + 1);
            String key = german.containsKey("block." + ns + "." + path) ? "block." + ns + "." + path
                    : "item." + ns + "." + path;
            if (!german.containsKey(key)) {
                throw new IllegalStateException("the guide shows " + id + ", which has no name in de_de.json");
            }
            names.addProperty(id, key);
            keys.add(key);
        }
        JsonObject de = new JsonObject();
        for (String key : keys) {
            String text = german.get(key);
            if (text == null) {
                throw new IllegalStateException("the guide uses " + key + ", which de_de.json does not have");
            }
            de.addProperty(key, text);
        }
        JsonObject out = new JsonObject();
        out.addProperty("format", 1);
        out.add("book", book);
        out.add("names", names);
        out.add("de", de);
        return out;
    }

    /** The lang keys and item ids one widget uses (tags such as {@code #minecraft:planks} are no ids). */
    private static void collect(JsonObject widget, Set<String> keys, Set<String> ids) {
        if (widget.has("key")) {
            keys.add(widget.get("key").getAsString());
        }
        for (String field : List.of("ref", "result")) {
            if (widget.has(field)) {
                id(widget.get(field), ids);
            }
        }
        for (String field : List.of("refs", "cells")) {
            if (widget.has(field)) {
                for (JsonElement e : widget.getAsJsonArray(field)) {
                    id(e, ids);
                }
            }
        }
        if (widget.has("scene")) {
            JsonArray blocks = widget.getAsJsonObject("scene").getAsJsonArray("blocks");
            for (JsonElement b : blocks) {
                JsonObject block = b.getAsJsonObject();
                for (String field : List.of("state", "machine")) {
                    if (block.has(field)) {
                        String state = block.get(field).getAsString();
                        int bracket = state.indexOf('[');
                        ids.add(bracket < 0 ? state : state.substring(0, bracket));
                    }
                }
                if (block.has("sofa")) {
                    ids.add("casinodeco:casino_sofa");
                }
            }
        }
    }

    private static void id(JsonElement e, Set<String> ids) {
        if (e != null && !e.isJsonNull() && !e.getAsString().startsWith("#")) {
            ids.add(e.getAsString());
        }
    }

    /** The mod's German texts of its three namespaces. */
    static Map<String, String> german(ModJar jar) {
        Map<String, String> out = new TreeMap<>();
        for (String ns : NAMESPACES) {
            String path = "assets/" + ns + "/lang/de_de.json";
            for (Map.Entry<String, JsonElement> e : Json.parse(jar.bytes(path)).entrySet()) {
                out.put(e.getKey(), e.getValue().getAsString());
            }
        }
        return out;
    }
}
