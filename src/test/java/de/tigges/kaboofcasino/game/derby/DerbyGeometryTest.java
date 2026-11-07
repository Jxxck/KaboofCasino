package de.tigges.kaboofcasino.game.derby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.tigges.kaboofcasino.machine.Facing;
import de.tigges.kaboofcasino.machine.MachineType;
import de.tigges.kaboofcasino.pack.PackFiles;
import de.tigges.kaboofcasino.util.Pos;
import de.tigges.kaboofcasino.visual.RigMath;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/**
 * The derby rig against an independent evaluation of {@code DuckDerbyRenderer} and the machine's blockstate, in all
 * four facings: the twelve tub models land where the blockstate puts them ({@code "y": 90 * turns} in the cell
 * {@code rotate(facing, p % 3 - 1, p / 3 - 3)}); every duck, gate and sign quad of the built pack, placed by the rig's
 * transformations, lands on the vertex the renderer's pose stack gives ({@code translate(0.5, 0, 0.5)},
 * {@code rotationY(-turns pi / 2)}, then the part's own translation/rotation and {@code (data + offset) / 16}; typed
 * here from the mod source with the mod's quad arrays {@code DuckDerbyGeometry.DUCK}/{@code GATE}) for whole races
 * of every winner; the culling box holds everything; interpolated rotation steps stay small. World positions are
 * blocks from the anchor block's corner, like the mod's block-entity pose.
 */
class DerbyGeometryTest {

    private static final double EPS = 2e-4;
    /** A world far from 0. */
    private static final long START = 33_389_712L;

    // ---- the mod, typed independently ------------------------------------------------------------------------------

    /** A PoseStack entry in double precision: {@code p -> m * p + t}. */
    private static final class Pose {
        final double[] m = {1, 0, 0, 0, 1, 0, 0, 0, 1};
        final double[] t = {0, 0, 0};

        void translate(double x, double y, double z) {
            t[0] += m[0] * x + m[1] * y + m[2] * z;
            t[1] += m[3] * x + m[4] * y + m[5] * z;
            t[2] += m[6] * x + m[7] * y + m[8] * z;
        }

        void mul(double[] r) {
            double[] o = new double[9];
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    o[3 * i + j] = m[3 * i] * r[j] + m[3 * i + 1] * r[3 + j] + m[3 * i + 2] * r[6 + j];
                }
            }
            System.arraycopy(o, 0, m, 0, 9);
        }

        void rotateX(double a) {
            mul(new double[] {1, 0, 0, 0, Math.cos(a), -Math.sin(a), 0, Math.sin(a), Math.cos(a)});
        }

        void rotateY(double a) {
            mul(new double[] {Math.cos(a), 0, Math.sin(a), 0, 1, 0, -Math.sin(a), 0, Math.cos(a)});
        }

        Vector3f apply(double x, double y, double z) {
            return new Vector3f((float) (m[0] * x + m[1] * y + m[2] * z + t[0]),
                    (float) (m[3] * x + m[4] * y + m[5] * z + t[1]), (float) (m[6] * x + m[7] * y + m[8] * z + t[2]));
        }
    }

    /** {@code submit} 165-167: the machine frame. */
    private static Pose machine(Facing facing) {
        Pose p = new Pose();
        p.translate(0.5, 0.0, 0.5);
        p.rotateY(-facing.turns() * Math.PI / 2.0);
        return p;
    }

    /** {@code DuckDerbyGeometry.LANE_CENTERS}, {@code GATE_X} typed from the source. */
    private static final double[] LANE_X = {6.0, 18.0, 30.0, 42.0};
    private static final double[] GATE_X = {1.0, 13.0, 25.0, 37.0};
    private static final float[] LANE_UV = {0.0F, 0.0F, 0.5F, 0.0F, 0.0F, 0.454545F, 0.5F, 0.454545F};

    /** {@code extractRenderState} 86-103: elapsed in the window {@code 0 .. endTick + 1}, else the rest pose. */
    private static double[] modPose(DuckDerbyRace.Plan plan, long raceStart, int lane, double time) {
        double elapsed = -1.0;
        if (plan != null && raceStart >= 0L) {
            elapsed = time - (double) raceStart;
            if (elapsed < 0.0 || elapsed > (double) (plan.endTick() + 1)) {
                elapsed = -1.0;
            }
        }
        if (elapsed < 0.0) {
            return new double[] {56.5, 0.0, 0.0};
        }
        DuckDerbyRace.Pose p = new DuckDerbyRace.Pose();
        plan.pose(lane, elapsed, p);
        return new double[] {p.z, p.pitch, p.lift};
    }

    private static float modGate(DuckDerbyRace.Plan plan, long raceStart, double time) {
        double elapsed = time - (double) raceStart;
        if (plan == null || raceStart < 0 || elapsed < 0.0 || elapsed > (double) (plan.endTick() + 1)) {
            return 0.0F;
        }
        return (float) plan.gate(elapsed);
    }

    /** The mod's 4 vertices (blocks) of quad q of {@code data} under {@code pose}, drawn with offset (ox, oy, oz). */
    private static List<Vector3f> modQuad(Pose pose, float[] data, int q, float ox, float oy, float oz) {
        List<Vector3f> out = new ArrayList<>();
        for (int v = 0; v < 4; v++) {
            int o = q * 23 + v * 5;
            out.add(pose.apply((data[o] + ox) / 16.0F, (data[o + 1] + oy) / 16.0F, (data[o + 2] + oz) / 16.0F));
        }
        return out;
    }

    /** The renderer's duck quad q of {@code lane} at the pose (z, pitch, lift). */
    private static List<Vector3f> modDuck(Facing facing, int lane, double[] p, int q) {
        Pose pose = machine(facing);
        pose.translate((LANE_X[lane] - 24.0) / 16.0, (13.0 + p[2]) / 16.0, (p[0] - 56.0) / 16.0);
        if (p[1] != 0.0) {
            pose.rotateX((float) p[1]);
        }
        return modQuad(pose, DuckDerbyGeometry.DUCK, q, 0.0F, -0.5F, 0.0F);
    }

    private static List<Vector3f> modGate(Facing facing, int lane, float gate, int q) {
        float sink = (float) ((double) gate * 3.6);
        return modQuad(machine(facing), DuckDerbyGeometry.GATE, q, (float) (GATE_X[lane] - 24.0), -sink, -56.0F);
    }

    /** {@code signVertex}: ((x - 24) / 16, y / 16, (z - 56) / 16) under the machine pose. */
    private static Vector3f modSign(Facing facing, double x, double y, double z) {
        return machine(facing).apply((float) ((x - 24.0) / 16.0), (float) (y / 16.0), (float) ((z - 56.0) / 16.0));
    }

    /** The blockstate's placement of part p's model point (px): cell {@code rotate(facing, p % 3 - 1, p / 3 - 3)}
     * ({@code DuckDerbyBlock} 63-78) and the model turned by {@code "y": 90 * turns} about the block centre. */
    private static Vector3f modBlockPoint(Facing facing, int part, double mx, double my, double mz) {
        int u = part % 3 - 1;
        int v = part / 3 - 3;
        double dx = mx / 16.0 - 0.5;
        double dz = mz / 16.0 - 0.5;
        for (int i = 0; i < facing.turns(); i++) {
            int nu = -v;
            v = u;
            u = nu;
            double nx = -dz;
            dz = dx;
            dx = nx;
        }
        return new Vector3f((float) (u + 0.5 + dx), (float) (my / 16.0), (float) (v + 0.5 + dz));
    }

    // ---- the rig + the built pack ------------------------------------------------------------------------------------

    private static final Map<String, JsonObject> MODELS = new ConcurrentHashMap<>();

    static JsonObject model(String key) {
        return MODELS.computeIfAbsent(key, k -> {
            String ns = k.substring(0, k.indexOf(':'));
            String path = k.substring(k.indexOf(':') + 1);
            byte[] json = PackFiles.all().get("assets/" + ns + "/models/" + path + ".json");
            assertNotNull(json, k);
            return JsonParser.parseString(new String(json, StandardCharsets.UTF_8)).getAsJsonObject();
        });
    }

    private static final Map<String, List<JsonObject>> ELEMENTS = new ConcurrentHashMap<>();

    private static List<JsonObject> elements(String key) {
        return ELEMENTS.computeIfAbsent(key, k -> {
            List<JsonObject> out = new ArrayList<>();
            for (JsonElement e : model(k).getAsJsonArray("elements")) {
                out.add(e.getAsJsonObject());
            }
            return List.copyOf(out);
        });
    }

    private static double[] vec(JsonArray a) {
        return new double[] {a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()};
    }

    /** Where model point m ends up, from the anchor's corner (the rig stands at the anchor's bottom centre). */
    private static Vector3f world(Transformation t, double x, double y, double z) {
        return RigMath.apply(t, x, y, z).add(0.5F, 0F, 0.5F);
    }

    private static final Map<JsonObject, List<double[]>> CORNERS = new java.util.IdentityHashMap<>();

    /** An element's distinct box corners (px), cached per element; none of the derby's elements is rotated. */
    private static synchronized List<double[]> corners(JsonObject element) {
        return CORNERS.computeIfAbsent(element, DerbyGeometryTest::readCorners);
    }

    private static List<double[]> readCorners(JsonObject element) {
        assertTrue(!element.has("rotation"), element.toString());
        double[] f = vec(element.getAsJsonArray("from"));
        double[] to = vec(element.getAsJsonArray("to"));
        List<double[]> out = new ArrayList<>();
        for (int k = 0; k < 8; k++) {
            double[] p = {(k & 1) == 0 ? f[0] : to[0], (k & 2) == 0 ? f[1] : to[1], (k & 4) == 0 ? f[2] : to[2]};
            boolean dup = false;
            for (double[] o : out) {
                dup |= o[0] == p[0] && o[1] == p[1] && o[2] == p[2];
            }
            if (!dup) {
                out.add(p);
            }
        }
        return out;
    }

    private static List<Vector3f> rigCorners(Transformation t, JsonObject element) {
        List<Vector3f> out = new ArrayList<>();
        for (double[] p : corners(element)) {
            out.add(world(t, p[0], p[1], p[2]));
        }
        return out;
    }

    private static void assertSameSet(List<Vector3f> want, List<Vector3f> got, String what) {
        assertEquals(want.size(), got.size(), what);
        for (Vector3f w : want) {
            boolean found = false;
            for (Vector3f g : got) {
                found |= g.distance(w) < EPS;
            }
            assertTrue(found, what + ": " + w + " not in " + got);
        }
    }

    /** Races of every winner (and a few seeds each), as the controller's view shows them. */
    static List<DerbyView> races() {
        List<DerbyView> out = new ArrayList<>();
        long seed = 0x5EEDL;
        for (int winner = 0; winner < 4; winner++) {
            for (int k = 0; k < 2; k++) {
                DuckDerbyRace.Plan plan = DuckDerbyRace.plan(winner, seed + 977L * k + winner);
                assertNotNull(plan);
                out.add(new DerbyView(START, plan, false, false, -1, 1, List.of(), List.of()));
            }
        }
        return out;
    }

    // ---- the tub --------------------------------------------------------------------------------------------------

    @Test
    void tubModelsLandWhereTheBlockstatePutsThem() {
        int checked = 0;
        for (Facing facing : Facing.values()) {
            for (int part = 0; part < DerbyRig.PARTS; part++) {
                Transformation t = DerbyRig.bodyTransform(facing, part);
                for (JsonObject e : elements(DerbyRig.bodyModel(part))) {
                    List<Vector3f> want = new ArrayList<>();
                    for (double[] p : corners(e)) {
                        want.add(modBlockPoint(facing, part, p[0], p[1], p[2]));
                    }
                    assertSameSet(want, rigCorners(t, e), facing + " part " + part);
                    checked++;
                }
            }
        }
        assertTrue(checked >= 4 * 89, "checked " + checked);
    }

    @Test
    void theTubCellsAreTheFootprint() {
        for (Facing facing : Facing.values()) {
            List<Pos> carriers = MachineType.DUCK_DERBY.carriers(new Pos("w", 0, 0, 0), facing);
            assertEquals(DerbyRig.PARTS, carriers.size());
            for (int part = 0; part < DerbyRig.PARTS; part++) {
                Vector3f centre = world(DerbyRig.bodyTransform(facing, part), 8, 8, 8);
                Pos cell = carriers.get(part);
                assertEquals(cell.x() + 0.5, centre.x, 1e-5, facing + " part " + part);
                assertEquals(0.5, centre.y, 1e-5);
                assertEquals(cell.z() + 0.5, centre.z, 1e-5, facing + " part " + part);
            }
        }
    }

    // ---- the ducks, gates and sign ------------------------------------------------------------------------------------

    @Test
    void restPoseOutsideTheRaceWindow() {
        DerbyView race = races().get(0);
        int end = race.plan().endTick();
        for (double t : new double[] {START - 1, START + end + 1.5, START + end + 2, START + 10_000}) {
            DuckDerbyRace.Pose p = DerbyRig.pose(race, 2, t);
            assertEquals(56.5, p.z);
            assertEquals(0.0, p.pitch);
            assertEquals(0.0, p.lift);
            assertEquals(0.0, DerbyRig.gate(race, t));
        }
        DuckDerbyRace.Pose idle = DerbyRig.pose(DerbyView.idle(), 0, START);
        assertEquals(56.5, idle.z);
        // the race window's ends are the rest pose too (ducks back, gate down), so nothing jumps
        for (int lane = 0; lane < 4; lane++) {
            DuckDerbyRace.Pose first = DerbyRig.pose(race, lane, START);
            DuckDerbyRace.Pose last = DerbyRig.pose(race, lane, START + end + 1);
            assertEquals(56.5, first.z, 1e-9);
            assertEquals(56.5, last.z, 1e-9);
            assertEquals(0.0, last.pitch, 1e-9);
        }
        assertEquals(0.0, DerbyRig.gate(race, START + end + 1), 1e-6);
    }

    @Test
    void ducksAndGatesFollowTheRenderer() {
        int checked = 0;
        for (DerbyView race : races()) {
            DuckDerbyRace.Plan plan = race.plan();
            for (long tick = START - 2; tick <= START + plan.endTick() + 3; tick++) {
                for (Facing facing : Facing.values()) {
                    for (int lane = 0; lane < 4; lane++) {
                        double[] p = modPose(plan, START, lane, tick);
                        DuckDerbyRace.Pose got = DerbyRig.pose(race, lane, tick);
                        assertEquals(p[0], got.z, 0.0);
                        assertEquals(p[1], got.pitch, 0.0);
                        assertEquals(p[2], got.lift, 0.0);
                        if ((tick - START) % 5 != 0) {
                            continue;
                        }
                        Transformation t = DerbyRig.duckTransform(facing, lane, got);
                        List<JsonObject> els = elements(DerbyRig.duckModel(lane));
                        assertEquals(48, els.size());
                        for (int q = 0; q < els.size(); q++) {
                            assertSameSet(modDuck(facing, lane, p, q), rigCorners(t, els.get(q)),
                                    facing + " lane " + lane + " quad " + q + " at " + (tick - START));
                            checked++;
                        }
                    }
                    float g = modGate(plan, START, tick);
                    assertEquals(g, (float) DerbyRig.gate(race, tick), 0F);
                    Transformation t = DerbyRig.gateTransform(facing, DerbyRig.gate(race, tick));
                    List<JsonObject> els = elements(DerbyRig.GATES);
                    assertEquals(16, els.size());
                    for (int lane = 0; lane < 4; lane++) {
                        for (int q = 0; q < 4; q++) {
                            assertSameSet(modGate(facing, lane, g, q), rigCorners(t, els.get(4 * lane + q)),
                                    facing + " gate " + lane + " quad " + q + " at " + (tick - START));
                            checked++;
                        }
                    }
                }
            }
        }
        assertTrue(checked > 400_000, "checked " + checked);
    }

    /** Lane l's duck is lane 0's mesh with every UV moved by {@code LANE_UV[l]} (the renderer's du, dv). */
    @Test
    void duckUvIsLaneZeroPlusTheLaneOffset() {
        List<JsonObject> base = elements(DerbyRig.duckModel(0));
        for (int lane = 1; lane < 4; lane++) {
            List<JsonObject> els = elements(DerbyRig.duckModel(lane));
            for (int q = 0; q < base.size(); q++) {
                JsonObject a = base.get(q).getAsJsonObject("faces");
                JsonObject b = els.get(q).getAsJsonObject("faces");
                assertEquals(a.keySet(), b.keySet());
                for (String dir : a.keySet()) {
                    double[] ua = uv(a.getAsJsonObject(dir));
                    double[] ub = uv(b.getAsJsonObject(dir));
                    for (int k = 0; k < 4; k++) {
                        double off = 16.0 * LANE_UV[2 * lane + (k % 2)];
                        assertEquals(ua[k] + off, ub[k], 1e-4, "lane " + lane + " quad " + q);
                    }
                    assertEquals(a.getAsJsonObject(dir).has("rotation"), b.getAsJsonObject(dir).has("rotation"));
                }
                // and lane 0's UV rectangle is the mod's quad UV range (sheet fraction x 16)
                if (lane == 1) {
                    double[] u = uv(a.getAsJsonObject(a.keySet().iterator().next()));
                    double uMin = Double.MAX_VALUE;
                    double uMax = -Double.MAX_VALUE;
                    for (int v = 0; v < 4; v++) {
                        double s = DuckDerbyGeometry.DUCK[q * 23 + v * 5 + 3] * 16.0;
                        uMin = Math.min(uMin, s);
                        uMax = Math.max(uMax, s);
                    }
                    assertEquals(uMin, Math.min(u[0], u[2]), 1e-3, "quad " + q);
                    assertEquals(uMax, Math.max(u[0], u[2]), 1e-3, "quad " + q);
                }
            }
        }
    }

    private static double[] uv(JsonObject face) {
        JsonArray a = face.getAsJsonArray("uv");
        return new double[] {a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble(),
            a.get(3).getAsDouble()};
    }

    @Test
    void signOverlayAndBulbsAreTheRenderers() {
        List<JsonObject> els = elements(DerbyRig.SIGN);
        assertEquals(1 + DuckDerbySignLights.BULBS, els.size());
        for (Facing facing : Facing.values()) {
            Transformation t = DerbyRig.signTransform(facing);
            // signQuad(4, 17, 44, 31, 3.025, ...)
            List<Vector3f> want = List.of(modSign(facing, 4, 31, 3.025), modSign(facing, 4, 17, 3.025),
                    modSign(facing, 44, 17, 3.025), modSign(facing, 44, 31, 3.025));
            assertSameSet(want, rigCorners(t, els.get(0)), facing + " sign");
            for (int i = 0; i < DuckDerbySignLights.BULBS; i++) {
                double x = DuckDerbySignLights.x(i);
                double y = DuckDerbySignLights.y(i);
                double h = 0.375;
                List<Vector3f> bulb = List.of(modSign(facing, x - h, y + h, 3.08), modSign(facing, x - h, y - h, 3.08),
                        modSign(facing, x + h, y - h, 3.08), modSign(facing, x + h, y + h, 3.08));
                assertSameSet(bulb, rigCorners(t, els.get(1 + i)), facing + " bulb " + i);
            }
        }
        // overlay: dd_sign u 0..1, v 0..0.875, untinted; bulbs: the slot sheet's BULB_UV, tint = index; all self-lit
        JsonObject overlay = els.get(0).getAsJsonObject("faces").getAsJsonObject("south");
        assertEquals("#sign", overlay.get("texture").getAsString());
        assertEquals(List.of(0.0, 0.0, 16.0, 14.0), list(overlay.getAsJsonArray("uv")));
        assertTrue(!overlay.has("tintindex"));
        for (int i = 0; i < DuckDerbySignLights.BULBS; i++) {
            JsonObject e = els.get(1 + i);
            JsonObject face = e.getAsJsonObject("faces").getAsJsonObject("south");
            assertEquals(i, face.get("tintindex").getAsInt());
            assertEquals("#bulb", face.get("texture").getAsString());
            // SlotMachineRenderGeometry.BULB_UV {0.73438, 0.04545, 0.75, 0.05303} x 16
            assertEquals(List.of(round5(0.73438 * 16), round5(0.04545 * 16), round5(0.75 * 16),
                    round5(0.05303 * 16)), list(face.getAsJsonArray("uv")));
        }
        for (JsonObject e : els) {
            assertEquals(15, e.get("light_emission").getAsInt());
        }
        JsonObject textures = model(DerbyRig.SIGN).getAsJsonObject("textures");
        assertEquals("roulettecasino:block/dd_sign", textures.get("sign").getAsString());
        assertEquals("roulettecasino:block/casino_entity/slot_machine", textures.get("bulb").getAsString());
    }

    private static double round5(double v) {
        return Math.round(v * 100_000.0) / 100_000.0;
    }

    private static List<Double> list(JsonArray a) {
        List<Double> out = new ArrayList<>();
        a.forEach(e -> out.add(e.getAsDouble()));
        return out;
    }

    // ---- culling box, model extents, rotation steps -----------------------------------------------------------------

    private static void assertInBox(Vector3f p, String what) {
        float half = DerbyRig.WIDTH / 2F;
        assertTrue(Math.abs(p.x - 0.5F) <= half && Math.abs(p.z - 0.5F) <= half, what + " " + p);
        assertTrue(p.y >= -1e-5F && p.y <= DerbyRig.HEIGHT, what + " " + p);
    }

    @Test
    void cullingBoxHoldsEverythingInEveryFacing() {
        double[] reach = {0, 0, 0};
        for (Facing facing : Facing.values()) {
            for (int part = 0; part < DerbyRig.PARTS; part++) {
                for (JsonObject e : elements(DerbyRig.bodyModel(part))) {
                    for (Vector3f p : rigCorners(DerbyRig.bodyTransform(facing, part), e)) {
                        assertInBox(p, facing + " part " + part);
                        reach[0] = Math.max(reach[0], Math.abs(p.x - 0.5));
                        reach[1] = Math.max(reach[1], p.y);
                    }
                }
            }
            for (JsonObject e : elements(DerbyRig.SIGN)) {
                for (Vector3f p : rigCorners(DerbyRig.signTransform(facing), e)) {
                    assertInBox(p, facing + " sign");
                }
            }
            for (DerbyView race : races()) {
                for (long tick = START; tick <= START + race.plan().endTick() + 1; tick += 2) {
                    for (int lane = 0; lane < 4; lane++) {
                        Transformation t = DerbyRig.duckTransform(facing, lane, DerbyRig.pose(race, lane, tick));
                        for (JsonObject e : elements(DerbyRig.duckModel(lane))) {
                            for (Vector3f p : rigCorners(t, e)) {
                                assertInBox(p, facing + " duck " + lane);
                                reach[2] = Math.max(reach[2], p.y);
                            }
                        }
                    }
                    for (JsonObject e : elements(DerbyRig.GATES)) {
                        for (Vector3f p : rigCorners(DerbyRig.gateTransform(facing, DerbyRig.gate(race, tick)), e)) {
                            assertInBox(p, facing + " gate");
                        }
                    }
                }
            }
        }
        // the tub reaches 3.5 blocks from the anchor centre (rows -3..0) and 31 px up (the sign)
        assertEquals(3.5, reach[0], 1e-5);
        assertEquals(31.0 / 16.0, reach[1], 1e-5);
        assertTrue(reach[2] < 1.4, "duck top " + reach[2]);
    }

    @Test
    void generatedModelsStayWithinTheModelLimits() {
        List<String> keys = new ArrayList<>();
        for (int lane = 0; lane < 4; lane++) {
            keys.add(DerbyRig.duckModel(lane));
        }
        keys.add(DerbyRig.GATES);
        keys.add(DerbyRig.SIGN);
        for (int part = 0; part < DerbyRig.PARTS; part++) {
            keys.add(DerbyRig.bodyModel(part));
        }
        for (String key : keys) {
            for (JsonObject e : elements(key)) {
                for (String end : new String[] {"from", "to"}) {
                    for (double c : vec(e.getAsJsonArray(end))) {
                        assertTrue(c >= -16 && c <= 32, key + " " + e);
                    }
                }
            }
        }
    }

    /** Angle (degrees) between two rotations, the way slerp takes it (shortest path). */
    private static double angle(Quaternionf a, Quaternionf b) {
        double dot = Math.abs(a.x * b.x + a.y * b.y + a.z * b.z + a.w * b.w);
        return Math.toDegrees(2.0 * Math.acos(Math.min(1.0, dot)));
    }

    @Test
    void everyInterpolatedRotationStepIsSmall() {
        double worst = 0;
        for (Facing facing : Facing.values()) {
            for (DerbyView race : races()) {
                for (int lane = 0; lane < 4; lane++) {
                    // what the rig sends: the pose of now + 1 each tick, starting from the rest pose
                    Quaternionf last = DerbyRig.duckTransform(facing, lane, DerbyRig.pose(race, lane, START - 1))
                            .getLeftRotation();
                    for (long now = START - 1; now <= START + race.plan().endTick() + 2; now++) {
                        Quaternionf next = DerbyRig.duckTransform(facing, lane,
                                DerbyRig.pose(race, lane, now + DerbyRig.DURATION)).getLeftRotation();
                        worst = Math.max(worst, angle(last, next));
                        last = next;
                    }
                }
            }
        }
        // the racing wobble (0.3 rad amplitude) and the winner's tilt: a few degrees per tick, far below 180
        assertTrue(worst > 0.5 && worst < 20.0, "worst step " + worst);
    }

    /**
     * The motion traffic of a race for a player nearby, as the rig sends it (the pose of now + 1 every tick, only
     * changed transformations): one metadata update per changed display; nothing before the gate moves and at rest.
     */
    @Test
    void raceTrafficIsOnlyTheMovingParts() {
        long total = 0;
        long ticks = 0;
        for (DerbyView race : races()) {
            Transformation[] sent = new Transformation[5];
            for (int lane = 0; lane < 4; lane++) {
                sent[lane] = DerbyRig.duckTransform(Facing.NORTH, lane, DerbyRig.pose(race, lane, START - 1));
            }
            sent[4] = DerbyRig.gateTransform(Facing.NORTH, DerbyRig.gate(race, START - 1));
            long packets = 0;
            long firstSend = -1;
            long end = START + race.plan().endTick() + 5;
            for (long now = START - 1; now <= end; now++) {
                Transformation[] t = new Transformation[5];
                for (int lane = 0; lane < 4; lane++) {
                    t[lane] = DerbyRig.duckTransform(Facing.NORTH, lane,
                            DerbyRig.pose(race, lane, now + DerbyRig.DURATION));
                }
                t[4] = DerbyRig.gateTransform(Facing.NORTH, DerbyRig.gate(race, now + DerbyRig.DURATION));
                for (int i = 0; i < 5; i++) {
                    if (!t[i].equals(sent[i])) {
                        sent[i] = t[i];
                        packets++;
                        firstSend = firstSend < 0 ? now - START : firstSend;
                    }
                }
            }
            // the gate starts at tick 6 (sent one tick ahead); the last send brings everything back to rest
            assertEquals(6, firstSend);
            for (int lane = 0; lane < 4; lane++) {
                assertEquals(DerbyRig.duckTransform(Facing.NORTH, lane, DerbyRig.pose(DerbyView.idle(), lane, 0)),
                        sent[lane]);
            }
            total += packets;
            ticks += race.plan().endTick();
        }
        double perTick = (double) total / ticks;
        System.out.printf("derby race: %.2f display updates per tick (%d over %d race ticks)%n", perTick, total, ticks);
        assertTrue(perTick > 2.5 && perTick <= 5.0, "per tick " + perTick);
    }

    @Test
    void panelCentreIsTheRenderers() {
        for (Facing facing : Facing.values()) {
            double[] c = DerbyRig.panelCentre(facing);
            Vector3f want = machine(facing).apply(0.0, 0.0, (32.0 - 56.0) / 16.0);
            assertEquals(want.x, c[0], 1e-6, facing.toString());
            assertEquals(want.z, c[2], 1e-6, facing.toString());
        }
    }

    @Test
    void entityBudget() {
        // render.md 2.5: 21 idle / 21 peak with label and panel; here 12 tub + 4 ducks + gates + sign + panel
        assertEquals(18, DerbyRig.modelKeys().size());
        assertTrue(DerbyRig.modelKeys().size() + 1 <= 21);
    }
}
