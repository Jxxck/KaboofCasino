package de.tigges.kaboofcasino.packtool;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Map;

/**
 * Multipart {@code when} conditions as the 26.3 client reads them ({@code Condition.CODEC}, recursive):
 * {@code {"AND": [...]}} / {@code {"OR": [...]}} or property terms; a term list {@code a|b} matches any term, a term
 * {@code !a} matches every other value ({@code KeyValueCondition.Terms}). Used to translate the mod's blockstates and
 * by the tests to evaluate the generated ones.
 */
final class DecoFullConditions {

    private DecoFullConditions() {
    }

    static boolean matches(JsonElement when, Map<String, String> props) {
        if (when == null) {
            return true;
        }
        JsonObject o = when.getAsJsonObject();
        if (o.size() == 1 && (o.has("AND") || o.has("OR"))) {
            boolean and = o.has("AND");
            JsonArray list = o.getAsJsonArray(and ? "AND" : "OR");
            for (JsonElement c : list) {
                boolean m = matches(c, props);
                if (and && !m) {
                    return false;
                }
                if (!and && m) {
                    return true;
                }
            }
            return and;
        }
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            String value = props.get(e.getKey());
            if (value == null) {
                throw new IllegalArgumentException("unknown property " + e.getKey() + " in " + when);
            }
            boolean any = false;
            for (String term : e.getValue().getAsString().split("\\|", -1)) {
                boolean negated = term.startsWith("!");
                String t = negated ? term.substring(1) : term;
                if (t.isEmpty()) {
                    throw new IllegalArgumentException("empty term in " + when);
                }
                if (t.equals(value) ^ negated) {
                    any = true;
                    break;
                }
            }
            if (!any) {
                return false;
            }
        }
        return true;
    }

    /** {@code {"down":"true","east":...}}: exactly one vanilla state. */
    static JsonObject state(Map<String, String> vanillaProps) {
        JsonObject o = new JsonObject();
        vanillaProps.forEach(o::addProperty);
        return o;
    }
}
