package de.tigges.kaboofcasino.packtool;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The full-block deco part of the pack: state table, blockstate overrides (evaluated state by state), files. */
class DecoFullPackTest {

    private static ModJar jar;
    private static PackTree tree;

    @BeforeAll
    static void build() {
        jar = ModJar.open(Path.of(System.getProperty("kaboofcasino.modJar")),
                System.getProperty("kaboofcasino.modJarSha1"));
        tree = new PackTree();
        DecoFullPack.add(jar, tree, Path.of(System.getProperty("kaboofcasino.packSrc")));
        Lang.filter(jar, tree, "casinodeco", DecoFullPack.LANG_PREFIXES);
    }

    @Test
    void tableMatchesTheChosenStatesFile() throws IOException {
        Path file = Path.of(System.getProperty("user.dir")).resolve("../tools/census/chosen-states.txt").normalize();
        List<String> expected = Files.readAllLines(file, StandardCharsets.UTF_8).stream()
                .filter(l -> !l.isBlank() && !l.startsWith("#")).toList();
        assertEquals(expected, DecoFullStates.ENTRIES.stream().map(DecoFullStates.Entry::line).toList());
    }

    @Test
    void tableShape() {
        assertEquals(80, DecoFullStates.ENTRIES.size());
        assertEquals(27, DecoFullStates.decoBlocks().size());
        assertEquals(30, DecoFullStates.of("mushroom_stem").size());
        assertEquals(30, DecoFullStates.of("red_mushroom_block").size());
        assertEquals(20, DecoFullStates.of("brown_mushroom_block").size());
        assertEquals(23, DecoFullStates.PILASTERS.size());
        Set<String> seen = new HashSet<>();
        for (DecoFullStates.Entry e : DecoFullStates.ENTRIES) {
            assertTrue(seen.add(e.block() + e.mask()), "duplicate " + e);
            assertTrue(e.mask() >= 0 && e.mask() < DecoFullStates.ALL, "all-true or invalid " + e);
            assertFalse(DecoFullStates.GENERATED.get(e.block()).contains(e.mask()), "world-generated " + e);
            assertEquals("true", e.vanillaProps().get("down"));
        }
        assertEquals(30, DecoFullStates.usableMasks("red_mushroom_block").size());
        assertEquals(22, DecoFullStates.usableMasks("brown_mushroom_block").size());
        assertEquals(30, DecoFullStates.usableMasks("mushroom_stem").size());
    }

    private static JsonArray parts(String block) {
        return Json.parse(tree.get("assets/minecraft/blockstates/" + block + ".json")).getAsJsonArray("multipart");
    }

    private static List<JsonElement> applied(JsonArray parts, int from, int to, Map<String, String> props) {
        List<JsonElement> out = new ArrayList<>();
        for (int i = from; i < to; i++) {
            JsonObject p = parts.get(i).getAsJsonObject();
            if (DecoFullConditions.matches(p.get("when"), props)) {
                out.add(p.get("apply"));
            }
        }
        return out;
    }

    /** What the mod's own blockstate file applies for a mod state. */
    private static List<JsonElement> modApplied(String deco, Map<String, String> modProps) {
        JsonObject mod = Json.parse(jar.bytes("assets/casinodeco/blockstates/" + deco + ".json"));
        List<JsonElement> out = new ArrayList<>();
        if (mod.has("variants")) {
            out.add(DecoFullPack.variant(mod.getAsJsonObject("variants"), modProps));
        } else {
            for (JsonElement p : mod.getAsJsonArray("multipart")) {
                if (DecoFullConditions.matches(p.getAsJsonObject().get("when"), modProps)) {
                    out.add(p.getAsJsonObject().get("apply"));
                }
            }
        }
        return out;
    }

    /**
     * All 64 states of each block: a non-reserved state gets exactly the vanilla parts (as the unmodified vanilla
     * file would give them) and no casino part; a reserved state gets no vanilla part and exactly the models the mod
     * gives its block state (marble: the 4-model random list, stars: model + y, pilaster: caps + 4 faces).
     */
    @Test
    void everyStateRendersVanillaOrExactlyItsCasinoBlock() {
        int reservedSeen = 0;
        for (String block : DecoFullStates.BLOCKS) {
            JsonArray parts = parts(block);
            JsonArray vanilla = DecoFullPack.vanillaParts(block);
            assertEquals(12, vanilla.size());
            for (int i = 0; i < 12; i++) {
                assertEquals(vanilla.get(i).getAsJsonObject().get("apply"), parts.get(i).getAsJsonObject().get("apply"));
            }
            for (boolean down : new boolean[] {false, true}) {
                for (int mask = 0; mask <= DecoFullStates.ALL; mask++) {
                    Map<String, String> props = DecoFullStates.Entry.vanillaProps(mask, down);
                    int m = mask;
                    DecoFullStates.Entry entry = !down ? null : DecoFullStates.of(block).stream()
                            .filter(e -> e.mask() == m).findFirst().orElse(null);
                    List<JsonElement> vanillaApplied = applied(parts, 0, 12, props);
                    List<JsonElement> casinoApplied = applied(parts, 12, parts.size(), props);
                    if (entry == null) {
                        assertEquals(applied(vanilla, 0, 12, props), vanillaApplied, block + props);
                        assertEquals(6, vanillaApplied.size(), block + props);
                        assertTrue(casinoApplied.isEmpty(), "casino part on " + block + props);
                    } else {
                        reservedSeen++;
                        assertTrue(vanillaApplied.isEmpty(), "vanilla part on reserved " + entry);
                        assertEquals(modApplied(entry.deco(), entry.modProps()), casinoApplied, entry.line());
                    }
                }
            }
        }
        assertEquals(80, reservedSeen);
    }

    /** An appended baseboard corner shows the band stub on the two faces next to its walls, like the mod. */
    @Test
    void cornerStatesShowTheModsFaces() {
        DecoFullStates.Entry ne = DecoFullStates.ENTRIES.get(72);
        assertEquals("dark_oak_pilaster", ne.deco());
        assertEquals(Map.of("north", "1", "east", "1", "south", "0", "west", "0"), ne.modProps());
        JsonArray parts = parts(ne.block());
        List<String> models = new ArrayList<>();
        applied(parts, 12, parts.size(), ne.vanillaProps()).forEach(a -> models.addAll(DecoFullPack.modelsOf(a)));
        assertEquals(List.of("casinodeco:block/dark_oak_pilaster_caps", "casinodeco:block/dark_oak_pilaster_north_1_0",
                "casinodeco:block/dark_oak_pilaster_south_0_1", "casinodeco:block/dark_oak_pilaster_east_0_1",
                "casinodeco:block/dark_oak_pilaster_west_1_0"), models);
    }

    @Test
    void marbleKeepsItsFourRandomModelsAndStarsTheirRotation() {
        JsonArray stem = parts("mushroom_stem");
        DecoFullStates.Entry marble = DecoFullStates.of("mushroom_stem").stream()
                .filter(e -> e.deco().equals("ivory_marble")).findFirst().orElseThrow();
        List<JsonElement> a = applied(stem, 12, stem.size(), marble.vanillaProps());
        assertEquals(1, a.size());
        assertEquals(4, a.getFirst().getAsJsonArray().size());
        assertEquals("casinodeco:block/ivory_marble_v3",
                a.getFirst().getAsJsonArray().get(3).getAsJsonObject().get("model").getAsString());
        DecoFullStates.Entry west = DecoFullStates.of("mushroom_stem").stream()
                .filter(e -> e.deco().equals("emerald_stars") && e.modProps().get("facing").equals("west"))
                .findFirst().orElseThrow();
        JsonObject star = applied(stem, 12, stem.size(), west.vanillaProps()).getFirst().getAsJsonObject();
        assertEquals("casinodeco:block/emerald_stars", star.get("model").getAsString());
        assertEquals(270, star.get("y").getAsInt());
    }

    @Test
    void everyModelAndTextureIsInThePack() {
        List<String> missing = new ArrayList<>();
        Set<String> models = new HashSet<>();
        for (String block : DecoFullStates.BLOCKS) {
            for (JsonElement p : parts(block)) {
                models.addAll(DecoFullPack.modelsOf(p.getAsJsonObject().get("apply")));
            }
        }
        for (String deco : DecoFullStates.decoBlocks()) {
            String item = "assets/casinodeco/items/" + deco + ".json";
            assertTrue(tree.has(item), item);
            models.add(Json.parse(tree.get(item)).getAsJsonObject("model").get("model").getAsString());
        }
        List<String> todo = new ArrayList<>(models);
        while (!todo.isEmpty()) {
            String id = todo.removeLast();
            if (id.startsWith("minecraft:")) {
                continue;
            }
            String ns = id.substring(0, id.indexOf(':'));
            String path = "assets/" + ns + "/models/" + id.substring(id.indexOf(':') + 1) + ".json";
            if (!tree.has(path)) {
                missing.add(path);
                continue;
            }
            JsonObject model = Json.parse(tree.get(path));
            if (model.has("parent")) {
                todo.add(model.get("parent").getAsString());
            }
            if (model.has("textures")) {
                for (Map.Entry<String, JsonElement> t : model.getAsJsonObject("textures").entrySet()) {
                    String tex = t.getValue().getAsString();
                    if (!tex.startsWith("#") && tex.startsWith("casinodeco:")) {
                        String texPath = "assets/casinodeco/textures/" + tex.substring(11) + ".png";
                        if (!tree.has(texPath)) {
                            missing.add(texPath);
                        }
                    }
                }
            }
        }
        assertTrue(missing.isEmpty(), "missing " + missing);
        assertTrue(tree.has("assets/casinodeco/models/block/dark_oak_pilaster_west_3_3.json"));
        assertTrue(tree.has("assets/casinodeco/textures/block/classic_dark_oak_pilaster_join_2_0.png"));
        // two different bands on one face is not among the 19 pilaster states: not shipped
        assertFalse(tree.has("assets/casinodeco/textures/block/classic_dark_oak_pilaster_join_2_1.png"));
    }

    @Test
    void langHasTheTwentySevenNamesInEveryLanguage() {
        JsonObject de = Json.parse(tree.get("assets/casinodeco/lang/de_de.json"));
        assertEquals(27, de.size(), de.keySet().toString());
        assertEquals("Geäderter Elfenbeinmarmor", de.get("block.casinodeco.ivory_marble").getAsString());
        assertFalse(de.has("block.casinodeco.ivory_marble_slab"));
        assertEquals(27, Json.parse(tree.get("assets/casinodeco/lang/en_us.json")).size());
        assertEquals(12, tree.files().keySet().stream().filter(p -> p.startsWith("assets/casinodeco/lang/")).count());
    }

    @Test
    void deterministic() {
        PackTree again = new PackTree();
        DecoFullPack.add(jar, again, Path.of(System.getProperty("kaboofcasino.packSrc")));
        for (String f : DecoFullPack.BLOCKSTATE_FILES) {
            assertArrayEquals(tree.get(f), again.get(f), f);
        }
    }

    /** The kept vanilla parts are the 26.3 client's own (checked when the client jar is on this machine). */
    @Test
    void vanillaPartsMatchTheClientJar() throws IOException {
        String given = System.getProperty("kaboofcasino.clientJar", "");
        assumeTrue(!given.isEmpty(), "no client jar given (-Pcasino.clientJar=...)");
        Path client = Path.of(given);
        assumeTrue(Files.isRegularFile(client), "no client jar at " + client);
        try (ZipFile zip = new ZipFile(client.toFile())) {
            for (String block : DecoFullStates.BLOCKS) {
                try (InputStream in = zip.getInputStream(zip.getEntry("assets/minecraft/blockstates/" + block
                        + ".json"))) {
                    JsonArray original = Json.parse(in.readAllBytes()).getAsJsonArray("multipart");
                    assertEquals(original, DecoFullPack.vanillaParts(block), block);
                }
            }
        }
    }
}
