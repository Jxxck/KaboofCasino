package de.tigges.kaboofcasino.deco.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.tigges.kaboofcasino.machine.Facing;
import de.tigges.kaboofcasino.util.Pos;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Every state of the mod's blockstate files of the display decoration shows the same model with the same {@code y}
 * turn as {@link DecoModels} picks: the sofa's 576 states, tables, rope post and bars by connection, every facing,
 * count, open/closed, on/off and face, the slab types and the stair keys with their random variants.
 */
class DecoBlockstateTest {

    private static final Pos AT = new Pos("minecraft:overworld", 3, 70, -5);

    private static DecoRecord record(DecoType type, Facing facing, int state) {
        return new DecoRecord(UUID.randomUUID(), type, AT, facing, state, new UUID(0, 0), 0);
    }

    private static Facing facing(String name) {
        return Facing.valueOf(name.toUpperCase(Locale.ROOT));
    }

    private static int connections(Map<String, String> p) {
        int mask = 0;
        String[] sides = {"north", "east", "south", "west"};
        for (int d = 0; d < 4; d++) {
            if (Boolean.parseBoolean(p.get(sides[d]))) {
                mask |= 1 << d;
            }
        }
        return mask;
    }

    private static void single(DecoRecord r, JsonObject expected, String what) {
        List<DecoModels.Part> parts = DecoModels.parts(r, null);
        assertEquals(1, parts.size(), what);
        assertEquals(ModAssets.displayKey(expected.get("model").getAsString()), parts.getFirst().key(), what);
        assertEquals(ModAssets.y(expected), parts.getFirst().yRot(), what);
        assertTrue(!expected.has("x"), what);
    }

    @Test
    void sofaEveryState() {
        Map<String, Integer> backs = Map.of("none", 0, "north", 1, "east", 2, "south", 4, "west", 8, "north_east", 3,
                "south_east", 6, "south_west", 12, "north_west", 9);
        JsonObject variants = ModAssets.blockstate("casinodeco:casino_sofa").getAsJsonObject("variants");
        assertEquals(576, variants.size());
        for (Map.Entry<String, JsonElement> e : variants.entrySet()) {
            Map<String, String> p = ModAssets.props(e.getKey());
            int state = DecoRecord.sofaState(Integer.parseInt(p.get("joins")), backs.get(p.get("back")));
            single(record(DecoType.CASINO_SOFA, facing(p.get("facing")), state), ModAssets.first(e.getValue()),
                    e.getKey());
        }
    }

    @Test
    void tablesAndRopePostByConnection() {
        for (DecoType t : List.of(DecoType.WOOD_COFFEE_TABLE, DecoType.GLASS_COFFEE_TABLE, DecoType.WOOD_TABLE,
                DecoType.GLASS_TABLE, DecoType.VELVET_ROPE_POST)) {
            JsonObject variants = ModAssets.blockstate(t.id()).getAsJsonObject("variants");
            assertEquals(16, variants.size(), t.id());
            for (Map.Entry<String, JsonElement> e : variants.entrySet()) {
                single(record(t, Facing.SOUTH, connections(ModAssets.props(e.getKey()))),
                        ModAssets.first(e.getValue()), t.id() + " " + e.getKey());
            }
        }
    }

    @Test
    void barsMultipart() {
        for (DecoType t : List.of(DecoType.WALNUT_BAR, DecoType.IVORY_MARBLE_BAR, DecoType.BLACK_MARBLE_BAR)) {
            JsonArray parts = ModAssets.blockstate(t.id()).getAsJsonArray("multipart");
            JsonArray tops = null;
            for (int mask = 0; mask < 16; mask++) {
                JsonObject base = null;
                for (JsonElement e : parts) {
                    JsonObject part = e.getAsJsonObject();
                    if (!part.has("when")) {
                        tops = part.getAsJsonArray("apply");
                        continue;
                    }
                    JsonObject when = part.getAsJsonObject("when");
                    Map<String, String> p = new java.util.HashMap<>();
                    when.entrySet().forEach(w -> p.put(w.getKey(), w.getValue().getAsString()));
                    if (connections(p) == mask) {
                        base = part.getAsJsonObject("apply");
                    }
                }
                assertTrue(base != null, t.id() + " mask " + mask);
                List<DecoModels.Part> out = DecoModels.parts(record(t, Facing.SOUTH, mask), null);
                assertEquals(ModAssets.displayKey(base.get("model").getAsString()), out.getFirst().key());
                assertEquals(ModAssets.y(base), out.getFirst().yRot(), t.id() + " mask " + mask);
                if (t == DecoType.WALNUT_BAR) {
                    assertEquals(1, out.size());
                } else {
                    assertEquals(2, out.size());
                    int v = DecoVariants.multipart(AT.x(), AT.y(), AT.z(), 4);
                    assertEquals(ModAssets.displayKey(tops.get(v).getAsJsonObject().get("model").getAsString()),
                            out.get(1).key());
                    assertEquals(0, out.get(1).yRot());
                }
            }
            assertEquals(t == DecoType.WALNUT_BAR ? null : 4, tops == null ? null : tops.size(), t.id());
        }
    }

    @Test
    void facedBlocks() {
        for (DecoType t : List.of(DecoType.CASINO_CHAIR, DecoType.CASINO_STOOL, DecoType.SMALL_CASINO_SIGN,
                DecoType.CHANDELIER_WALL_SCONCE, DecoType.CASINO_CHIP_PILE, DecoType.RECEPTION_PLAQUE,
                DecoType.CASH_REGISTER, DecoType.SERVING_TRAY, DecoType.VIP_ONLY_PLAQUE,
                DecoType.BOTTLE_DISPLAY_SHELF, DecoType.GLASS_DISPLAY_SHELF, DecoType.CASINO_BOTTLES,
                DecoType.CHAMPAGNE_COUPES, DecoType.SHOT_GLASSES, DecoType.CHIP_CASE, DecoType.CASINO_SPEAKER)) {
            JsonObject variants = ModAssets.blockstate(t.id()).getAsJsonObject("variants");
            for (Map.Entry<String, JsonElement> e : variants.entrySet()) {
                Map<String, String> p = ModAssets.props(e.getKey());
                int state = switch (t) {
                    case CASINO_BOTTLES, CHAMPAGNE_COUPES, SHOT_GLASSES -> Integer.parseInt(p.get("count"));
                    case CHIP_CASE -> Boolean.parseBoolean(p.get("open")) ? 1 : 0;
                    case CASINO_SPEAKER -> DecoRecord.speakerState(Boolean.parseBoolean(p.get("on")),
                            switch (p.get("face")) {
                                case "wall" -> DecoRecord.FACE_WALL;
                                case "ceiling" -> DecoRecord.FACE_CEILING;
                                default -> DecoRecord.FACE_FLOOR;
                            });
                    default -> 0;
                };
                single(record(t, facing(p.get("facing")), state), ModAssets.first(e.getValue()),
                        t.id() + " " + e.getKey());
            }
        }
    }

    /** The 3 x 2 sign: every part turns with the facing, and the merged model puts each part on its cell. */
    @Test
    void largeSignParts() {
        JsonObject variants = ModAssets.blockstate("casinodeco:casino_sign").getAsJsonObject("variants");
        assertEquals(24, variants.size());
        for (Map.Entry<String, JsonElement> e : variants.entrySet()) {
            Map<String, String> p = ModAssets.props(e.getKey());
            Facing f = facing(p.get("facing"));
            int part = Integer.parseInt(p.get("part"));
            JsonObject entry = ModAssets.first(e.getValue());
            assertEquals("casinodeco:block/sign_" + part, entry.get("model").getAsString());
            List<DecoModels.Part> out = DecoModels.parts(record(DecoType.CASINO_SIGN, f, 0), null);
            assertEquals(1, out.size());
            DecoModels.Part merged = out.getFirst();
            assertEquals(ModAssets.y(entry), merged.yRot(), e.getKey());
            // part p sits at ((p % 3 - 1) * 16, (p / 3) * 16, 0) px in the merged model around the middle column
            double[] centre = DecoGeometry.turnPoint(new double[] {(part % 3 - 1) * 16 + 8, 0, 8}, f.turns());
            Pos middle = AT.offset(merged.dx(), merged.dy(), merged.dz());
            Pos cell = DecoGeometry.signPart(AT, f, part);
            assertEquals(cell.x() - middle.x(), (int) Math.floor(centre[0] / 16.0), e.getKey());
            assertEquals(cell.z() - middle.z(), (int) Math.floor(centre[2] / 16.0), e.getKey());
            assertEquals(cell.y() - middle.y(), part / 3, e.getKey());
        }
    }

    @Test
    void chandelierParts() {
        JsonObject variants = ModAssets.blockstate("casinodeco:casino_chandelier").getAsJsonObject("variants");
        assertEquals(11, variants.size());
        for (int part = 0; part < 9; part++) {
            assertEquals("casinodeco:block/chandelier_" + part,
                    ModAssets.first(variants.get("part=" + part)).get("model").getAsString());
            Pos cell = DecoGeometry.chandelierBody(AT, part);
            assertEquals(part % 3 - 1, cell.x() - AT.x());
            assertEquals(part / 3 - 1, cell.z() - AT.z());
        }
        List<DecoModels.Part> one = DecoModels.parts(record(DecoType.CASINO_CHANDELIER, Facing.SOUTH, 1), null);
        assertEquals(List.of("chandelier_body", "chandelier_10"), one.stream().map(p -> p.key().substring(
                DecoModels.PREFIX.length())).toList());
        List<DecoModels.Part> two = DecoModels.parts(record(DecoType.CASINO_CHANDELIER, Facing.SOUTH, 2), null);
        assertEquals(List.of("chandelier_body", "chandelier_9", "chandelier_10"), two.stream().map(p -> p.key()
                .substring(DecoModels.PREFIX.length())).toList());
        assertEquals(List.of(0, 1, 2), two.stream().map(DecoModels.Part::dy).toList());
    }

    @Test
    void slabsAndStairs() {
        for (DecoType t : DecoType.values()) {
            if (t.carrier() == DecoType.Carrier.SLAB) {
                JsonObject variants = ModAssets.blockstate(t.id()).getAsJsonObject("variants");
                for (String type : List.of("bottom", "top", "double")) {
                    JsonElement v = variants.get("type=" + type);
                    int n = v.isJsonArray() ? v.getAsJsonArray().size() : 1;
                    assertEquals(t.variants() ? 4 : 1, n, t.id());
                    int i = t.variants() ? DecoVariants.simple(AT.x(), AT.y(), AT.z(), 4) : 0;
                    JsonObject entry = v.isJsonArray() ? v.getAsJsonArray().get(i).getAsJsonObject()
                            : v.getAsJsonObject();
                    List<DecoModels.Part> out = DecoModels.parts(record(t, Facing.SOUTH, 0),
                            DecoModels.Carrier.slab(type));
                    assertEquals(ModAssets.displayKey(entry.get("model").getAsString()), out.getFirst().key(),
                            t.id() + " " + type);
                    assertEquals(0, out.getFirst().yRot());
                }
            } else if (t.carrier() == DecoType.Carrier.STAIRS) {
                JsonObject variants = ModAssets.blockstate(t.id()).getAsJsonObject("variants");
                assertEquals(40, variants.size());
                for (Map.Entry<String, JsonElement> e : variants.entrySet()) {
                    Map<String, String> p = ModAssets.props(e.getKey());
                    int n = e.getValue().isJsonArray() ? e.getValue().getAsJsonArray().size() : 1;
                    assertEquals(t.variants() ? 4 : 1, n, t.id());
                    int i = t.variants() ? DecoVariants.simple(AT.x(), AT.y(), AT.z(), 4) : 0;
                    DecoModels.Carrier c = DecoModels.Carrier.stairs(p.get("facing"), p.get("half"), p.get("shape"));
                    List<DecoModels.Part> out = DecoModels.parts(record(t, Facing.SOUTH, 0), c);
                    assertEquals(DecoModels.stairsKey(t.family(), c, i), out.getFirst().key());
                    assertEquals(0, out.getFirst().yRot());
                }
            }
        }
    }

    /** Every type is a block of the mod (blockstate present) and every item model the inventory items use exists. */
    @Test
    void everyTypeIsAModBlock() {
        for (DecoType t : DecoType.values()) {
            assertTrue(ModAssets.blockstate(t.id()) != null, t.id());
            int colon = t.id().indexOf(':');
            assertTrue(ModAssets.all().containsKey("assets/" + t.id().substring(0, colon) + "/items/"
                    + t.id().substring(colon + 1) + ".json"), t.id());
        }
        assertEquals(41, DecoType.values().length);
    }
}
