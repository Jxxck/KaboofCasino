package de.tigges.kaboofcasino.croupier;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import de.tigges.kaboofcasino.machine.Facing;
import de.tigges.kaboofcasino.util.Pos;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * {@link CroupierRecord} <-> one JSON string of the chunk's list (version {@value #VERSION}). Fields that may be absent
 * (counter, villager, snapshot, items, near) default to "none"; a record of a newer version ({@link NewerVersion}) or
 * one that cannot be read is never decoded, and the chunk store keeps it verbatim. Pure.
 */
public final class CroupierCodec {

    /** The record version this code writes and reads. */
    public static final int VERSION = 1;

    /** A record written by a newer plugin version: kept unchanged, the croupier stays inactive. */
    public static final class NewerVersion extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        NewerVersion(int version) {
            super("croupier record version " + version + " is newer than " + VERSION);
        }
    }

    private CroupierCodec() {
    }

    public static String encode(CroupierRecord r) {
        JsonObject o = new JsonObject();
        o.addProperty("v", VERSION);
        o.addProperty("id", r.id().toString());
        o.addProperty("world", r.stand().world());
        o.addProperty("x", r.stand().x());
        o.addProperty("y", r.stand().y());
        o.addProperty("z", r.stand().z());
        o.addProperty("lift", r.lift());
        o.addProperty("facing", r.facing().name());
        if (r.hasCounter()) {
            o.addProperty("counter", r.counter().toString());
            o.addProperty("cx", r.counterPos().x());
            o.addProperty("cy", r.counterPos().y());
            o.addProperty("cz", r.counterPos().z());
        }
        if (r.villager() != null) {
            o.addProperty("villager", r.villager().toString());
        }
        if (r.snapshot() != null) {
            o.addProperty("snapshot", r.snapshot());
        }
        if (r.snapshotVersion() != null) {
            o.addProperty("mc", r.snapshotVersion());
        }
        if (!r.items().isEmpty()) {
            JsonArray items = new JsonArray();
            for (String item : r.items()) {
                items.add(item);
            }
            o.add("items", items);
        }
        o.addProperty("uses", r.uses());
        o.addProperty("xp", r.xp());
        o.addProperty("restockDay", r.restockDay());
        o.addProperty("created", r.createdAt());
        if (r.createdNear() != null) {
            o.addProperty("near", r.createdNear().toString());
        }
        return o.toString();
    }

    /**
     * @throws NewerVersion             for a record of a newer version
     * @throws IllegalArgumentException for anything unreadable
     */
    public static CroupierRecord decode(String json) {
        JsonObject o = object(json);
        int version = integer(o, "v");
        if (version > VERSION) {
            throw new NewerVersion(version);
        }
        if (version < 1) {
            throw new IllegalArgumentException("croupier record version " + version);
        }
        String world = string(o, "world");
        Pos stand = new Pos(world, integer(o, "x"), integer(o, "y"), integer(o, "z"));
        UUID counter = uuid(o, "counter");
        Pos counterPos = counter == null ? null : new Pos(world, integer(o, "cx"), integer(o, "cy"), integer(o, "cz"));
        List<String> items = new ArrayList<>();
        JsonElement list = o.get("items");
        if (list != null && !list.isJsonNull()) {
            for (JsonElement e : list.getAsJsonArray()) {
                items.add(e.getAsString());
            }
        }
        Facing facing;
        try {
            facing = Facing.valueOf(string(o, "facing"));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("croupier record: facing " + o.get("facing"), e);
        }
        int uses = Math.clamp(whole(o, "uses"), 0, Integer.MAX_VALUE);
        int xp = Math.clamp(whole(o, "xp"), 0, Integer.MAX_VALUE);
        return new CroupierRecord(UUID.fromString(string(o, "id")), stand, number(o, "lift", 0.0), facing, counter,
                counterPos, uuid(o, "villager"), optional(o, "snapshot"), optional(o, "mc"), items, uses, xp,
                whole(o, "restockDay"), whole(o, "created"), uuid(o, "near"));
    }

    /** The id of an encoded record, also of a newer version; null if even that cannot be read. */
    public static String idOf(String json) {
        try {
            JsonElement id = object(json).get("id");
            return id != null && id.isJsonPrimitive() ? id.getAsString() : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static JsonObject object(String json) {
        if (json == null) {
            throw new IllegalArgumentException("croupier record: null");
        }
        try {
            JsonElement e = JsonParser.parseString(json);
            if (!e.isJsonObject()) {
                throw new IllegalArgumentException("croupier record: not an object");
            }
            return e.getAsJsonObject();
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("croupier record: " + e.getMessage(), e);
        }
    }

    private static JsonPrimitive primitive(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) {
            throw new IllegalArgumentException("croupier record: " + key + " missing");
        }
        return e.getAsJsonPrimitive();
    }

    private static String string(JsonObject o, String key) {
        return primitive(o, key).getAsString();
    }

    private static int integer(JsonObject o, String key) {
        JsonPrimitive p = primitive(o, key);
        if (!p.isNumber()) {
            throw new IllegalArgumentException("croupier record: " + key + " is not a number");
        }
        double d = p.getAsDouble();
        if (d != Math.rint(d) || d < Integer.MIN_VALUE || d > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("croupier record: " + key + " is not an int");
        }
        return (int) d;
    }

    private static double number(JsonObject o, String key, double fallback) {
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) {
            return fallback;
        }
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("croupier record: " + key + " is not a number");
        }
        return e.getAsDouble();
    }

    /** An optional whole number (0 if absent), read exactly (epoch millis exceed a double's integers only in 2^53). */
    private static long whole(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) {
            return 0L;
        }
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("croupier record: " + key + " is not a number");
        }
        try {
            return e.getAsJsonPrimitive().getAsBigDecimal().longValueExact();
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("croupier record: " + key + " is not a whole number", ex);
        }
    }

    private static String optional(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    private static UUID uuid(JsonObject o, String key) {
        String s = optional(o, key);
        return s == null ? null : UUID.fromString(s);
    }
}
