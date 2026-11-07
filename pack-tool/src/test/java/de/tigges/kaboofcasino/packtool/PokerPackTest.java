package de.tigges.kaboofcasino.packtool;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The poker table's pack part: the twelve block models of the mod's blockstate, byte for byte, each with an item
 * definition that names it (one display per part), every texture they use, the table item; deterministic.
 */
class PokerPackTest {

    private static ModJar jar;
    private static PackTree tree;

    private static PackTree build() {
        PackTree t = new PackTree();
        PokerPack.add(jar, t, Path.of(System.getProperty("kaboofcasino.packSrc")));
        return t;
    }

    @BeforeAll
    static void open() {
        jar = ModJar.open(Path.of(System.getProperty("kaboofcasino.modJar")),
                System.getProperty("kaboofcasino.modJarSha1"));
        tree = build();
    }

    /** The models {@code blockstates/poker_table.json} names, for every facing and part. */
    private static TreeSet<String> blockstateModels() {
        JsonObject variants = Json.parse(jar.bytes("assets/roulettecasino/blockstates/poker_table.json"))
                .getAsJsonObject("variants");
        TreeSet<String> out = new TreeSet<>();
        for (Map.Entry<String, JsonElement> v : variants.entrySet()) {
            JsonObject variant = v.getValue().getAsJsonObject();
            String model = variant.get("model").getAsString();
            out.add(model);
            // facing=south is unturned, west 90, north 180, east 270: the rig's turn per Facing.turns()
            int y = variant.has("y") ? variant.get("y").getAsInt() : 0;
            String facing = v.getKey().substring("facing=".length(), v.getKey().indexOf(','));
            int turns = switch (facing) {
                case "south" -> 0;
                case "west" -> 1;
                case "north" -> 2;
                default -> 3;
            };
            assertEquals(90 * turns, y, v.getKey());
            int part = Integer.parseInt(v.getKey().substring(v.getKey().indexOf("part=") + 5));
            assertEquals(PokerPack.bodyModel(part), model, v.getKey());
            assertTrue(!variant.has("x") && !variant.has("uvlock"), v.getKey());
        }
        return out;
    }

    @Test
    void theTwelvePartModelsAreTheModsVerbatim() {
        TreeSet<String> models = blockstateModels();
        assertEquals(PokerPack.PARTS, models.size());
        for (int part = 0; part < PokerPack.PARTS; part++) {
            String key = PokerPack.bodyModel(part);
            assertTrue(models.contains(key), key);
            String path = "assets/roulettecasino/models/block/poker_" + part + ".json";
            assertArrayEquals(jar.bytes(path), tree.get(path), path);
            JsonObject item = Json.parse(tree.get("assets/roulettecasino/items/block/poker_" + part + ".json"));
            assertEquals(key, item.getAsJsonObject("model").get("model").getAsString());
            JsonObject model = Json.parse(tree.get(path));
            for (JsonElement t : model.getAsJsonObject("textures").asMap().values()) {
                String tex = t.getAsString();
                String file = "assets/roulettecasino/textures/" + tex.substring(tex.indexOf(':') + 1) + ".png";
                assertTrue(tree.has(file), file);
            }
            for (JsonElement e : model.getAsJsonArray("elements")) {
                for (String end : new String[] {"from", "to"}) {
                    for (JsonElement c : e.getAsJsonObject().getAsJsonArray(end)) {
                        assertTrue(c.getAsDouble() >= -16 && c.getAsDouble() <= 32, path);
                    }
                }
            }
        }
        assertTrue(tree.has("assets/roulettecasino/items/poker_table.json"));
    }

    @Test
    void deterministic() {
        PackTree second = build();
        assertEquals(tree.files().keySet(), second.files().keySet());
        for (Map.Entry<String, byte[]> e : tree.files().entrySet()) {
            assertArrayEquals(e.getValue(), second.get(e.getKey()), e.getKey());
        }
    }
}
